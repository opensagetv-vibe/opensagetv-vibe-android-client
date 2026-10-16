package opensagetv.vibe.miniclient.android.video;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.Choreographer;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy;

/** Owns ONLY the final prepared-field display producer, not decoding or clocks.
 *
 * Potentially blocking OES acquisition remains on the input GL owner. Shared
 * GLES2 textures/standard EGL fences let this independent display looper submit one original
 * field per tick without waiting for that CPU-side decoder acquisition. A
 * server-side GL wait orders texture reads/writes; no CPU pixel readback,
 * glFinish, synthesized field, source-time shift or new audio clock is used.
 * The caller must return our read fence to its GL stream BEFORE reusing a slot.
 * Instances are experimental and require independent field-ID HDMI acceptance.
 */
final class GpuPreparedFieldPresenter implements AutoCloseable {
    interface Listener {
        boolean mayPresent();
        void presented(Field field,long measuredNs);
        void returned(Field field,long readFence);
        void startupDiscarded();
        void failed(String reason);
    }
    static final class Field {
        final int texture,slot,parity,epoch; final long deadlineNs,readyFence;
        final float fps; final boolean second;
        Field(int texture,int slot,int parity,int epoch,long deadlineNs,long readyFence,float fps,boolean second) {
            this.texture=texture;this.slot=slot;this.parity=parity;this.epoch=epoch;
            this.deadlineNs=deadlineNs;this.readyFence=readyFence;this.fps=fps;this.second=second;
        }
    }
    private final EGLDisplay display; private final EGLConfig config; private final EGLContext shared;
    private final Surface surface; private final int width,height; private final boolean motion;
    private final Listener listener;
    private final HandlerThread thread=new HandlerThread("VibeGpuPresent"); private final Handler handler;
    private final AtomicBoolean closed=new AtomicBoolean(),failed=new AtomicBoolean();
    private final AtomicInteger epoch=new AtomicInteger();
    private final ArrayDeque<Field> fields=new ArrayDeque<>();
    private final GpuDeinterlacePolicy.PresentationBudget budget=new GpuDeinterlacePolicy.PresentationBudget();
    private final FloatBuffer positions=floats(-1,-1,1,-1,-1,1,1,1),uvs=floats(0,0,1,0,0,1,1,1);
    private EGLContext context=EGL14.EGL_NO_CONTEXT; private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private Choreographer choreographer; private boolean tickPosted; private int program;
    private boolean readCommandsSinceClear;
    private volatile int pending; private volatile long oldestDeadline,outputs;
    private long startedNs; private int profileRemaining;
    private final Choreographer.FrameCallback tick=this::present;

    GpuPreparedFieldPresenter(EGLDisplay display,EGLConfig config,EGLContext shared,Surface surface,
            int width,int height,boolean motion,int profileBudget,Listener listener) {
        this.display=display;this.config=config;this.shared=shared;this.surface=surface;
        this.width=width;this.height=height;this.motion=motion;this.listener=listener;
        profileRemaining=Math.min(240,Math.max(0,profileBudget));
        thread.start();handler=new Handler(thread.getLooper());
        CountDownLatch ready=new CountDownLatch(1);
        handler.post(() -> {try {initialize();} catch(RuntimeException error) {fail("gpu_split_initialize");}
            finally {ready.countDown();}});
        try {
            await(ready,3000,"GPU display initialization");
            if(failed.get())throw new IllegalStateException("shared GPU display unavailable");
        } catch(RuntimeException error) {
            try {close();} catch(RuntimeException closeError) {error.addSuppressed(closeError);}
            throw error;
        }
    }
    int pendingFields() {return pending;}
    long oldestDeadlineNs() {return oldestDeadline;}
    void profile(int count) {handler.post(() -> profileRemaining=Math.min(240,Math.max(0,count)));}
    void submit(Field first,Field second) {
        handler.post(() -> {
            if(closed.get())return;
            if(failed.get() || first.epoch!=epoch.get()) {discard(first);discard(second);return;}
            if(fields.size()>2) {discard(first);discard(second);fail("gpu_original_field_queue_backpressure");return;}
            if(startedNs==0)startedNs=System.nanoTime();
            fields.addLast(first);fields.addLast(second);observeQueue();schedule();
        });
    }
    /** Lifecycle-only handoff. Invalidate pending submissions immediately,
     * then return a GPU ordering fence, not a CPU wait for all GPU commands.
     * Caller waits on this fence in its own GL stream before overwriting slots.
     */
    long clear(int nextEpoch) {
        epoch.set(nextEpoch);
        AtomicLong barrier=new AtomicLong();CountDownLatch done=new CountDownLatch(1);
        if(!handler.post(() -> {
            try {
                if(context!=EGL14.EGL_NO_CONTEXT) {
                    // No display read exists during initial allocation. Do
                    // not put a pointless window-context fence ahead of the
                    // first input copy. A real prior read (even a failed swap)
                    // still requires the ordering fence before texture reuse.
                    boolean needsBarrier=readCommandsSinceClear;
                    clearOnThread();
                    if(needsBarrier) {
                        barrier.set(GpuEglFence.create(display));
                        GLES20.glFlush();checkGl();
                    }
                }
            } catch(RuntimeException error) {fail("gpu_split_clear");}
            finally {done.countDown();}
        }))throw new IllegalStateException("GPU display looper stopped");
        await(done,500,"GPU display clear");return barrier.get();
    }
    private void initialize() {
        // This looper owns one real source field per display tick. Keep it at
        // Android's display priority so unrelated background work cannot turn
        // a schedulable 59.94 Hz field into an unrecoverable missed vblank.
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        context=EGL14.eglCreateContext(display,config,shared,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);
        require(context!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE);
        require(EGL14.eglMakeCurrent(display,window,window,context));
        require(GpuEglFence.available(display));
        int[] minimum=new int[1],maximum=new int[1];
        require(EGL14.eglGetConfigAttrib(display,config,EGL14.EGL_MIN_SWAP_INTERVAL,minimum,0));
        require(EGL14.eglGetConfigAttrib(display,config,EGL14.EGL_MAX_SWAP_INTERVAL,maximum,0));
        require(minimum[0]==0 && maximum[0]>=0 && EGL14.eglSwapInterval(display,0));
        int vertex=shader(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX);
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,motion ? GpuDeinterlaceShaders.COPY_PREPARED : GpuDeinterlaceShaders.PRESENT_FIELD);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);
        GLES20.glLinkProgram(program);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);require(linked[0]!=0);
        choreographer=Choreographer.getInstance();checkGl();
    }
    private void schedule() {
        if(!tickPosted && !closed.get() && !failed.get() && !fields.isEmpty()) {
            tickPosted=true;choreographer.postFrameCallback(tick);
        }
    }
    private void present(long frameTimeNs) {
        tickPosted=false;
        if(closed.get() || failed.get() || !listener.mayPresent())return;
        try {
            Field field=fields.peekFirst();if(field==null)return;
            // Arm the following vblank before draw/swap. Some API-25 window
            // swaps occasionally cross one refresh boundary; posting only
            // after that call loses another entire tick even though the next
            // field was already prepared. The looper still executes at most
            // one original field per callback.
            schedule();
            long lead=GpuDeinterlacePolicy.releaseLeadUs(field.fps)*1000;
            if(frameTimeNs-field.deadlineNs>lead*4) {
                if(outputs==0 && System.nanoTime()-startedNs<=2_000_000_000L) {
                    fields.removeFirst();discard(field);listener.startupDiscarded();observeQueue();schedule();return;
                }
                fail("gpu_original_field_queue_late");return;
            }
            if(field.deadlineNs<=frameTimeNs+lead) {
                long measured=profileRemaining>0 ? System.nanoTime() : 0;
                fields.removeFirst();
                // The producer flushed this shared fence after both field
                // copies. Queue a GPU-side dependency, never block the CPU.
                GpuEglFence.waitGpu(display,field.readyFence);
                GpuEglFence.destroy(display,field.readyFence);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,width,height);
                GLES20.glUseProgram(program);geometry("aPosition",positions);geometry("aUv",uvs);
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,IDENTITY,0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,field.texture);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
                if(!motion) {
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uHeight"),height);
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uParity"),field.parity);
                }
                readCommandsSinceClear=true;
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();
                require(EGLExt.eglPresentationTimeANDROID(display,window,Math.max(frameTimeNs,field.deadlineNs)));
                require(EGL14.eglSwapBuffers(display,window));
                long readFence=GpuEglFence.create(display);
                GLES20.glFlush();checkGl();outputs++;
                listener.presented(field,measured==0 ? 0 : System.nanoTime()-measured);
                if(measured!=0)profileRemaining--;
                listener.returned(field,readFence);
                if(budget.belowBudget(System.nanoTime(),outputs,field.fps*2)) {fail("gpu_presentation_budget");return;}
            }
            observeQueue();schedule();
        } catch(RuntimeException error) {fail("gpu_split_present");}
    }
    private void discard(Field field) {GpuEglFence.destroy(display,field.readyFence);listener.returned(field,0);}
    private void observeQueue() {pending=fields.size();oldestDeadline=fields.isEmpty()?0:fields.peekFirst().deadlineNs;}
    private void clearOnThread() {
        if(tickPosted)choreographer.removeFrameCallback(tick);tickPosted=false;
        while(!fields.isEmpty())discard(fields.removeFirst());
        observeQueue();outputs=startedNs=0;readCommandsSinceClear=false;budget.reset();
    }
    private void fail(String reason) {if(failed.compareAndSet(false,true))listener.failed(reason);}
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        CountDownLatch done=new CountDownLatch(1);
        if(!handler.post(() -> {
            try {
                if(context!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE
                        && EGL14.eglMakeCurrent(display,window,window,context)) {
                    if(choreographer!=null)clearOnThread();
                    if(program!=0)GLES20.glDeleteProgram(program);
                }
            } finally {
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
                if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
                window=EGL14.EGL_NO_SURFACE;context=EGL14.EGL_NO_CONTEXT;
                EGL14.eglReleaseThread();thread.quitSafely();done.countDown();
            }
        }))throw new IllegalStateException("GPU display close looper stopped");
        await(done,500,"GPU display close");
    }
    private void geometry(String name,FloatBuffer values) {
        int location=GLES20.glGetAttribLocation(program,name);values.position(0);
        GLES20.glEnableVertexAttribArray(location);GLES20.glVertexAttribPointer(location,2,GLES20.GL_FLOAT,false,0,values);
    }
    private static final float[] IDENTITY={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    private static FloatBuffer floats(float... values) {
        FloatBuffer data=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        data.put(values).position(0);return data;
    }
    private static int shader(int kind,String source) {
        int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] compiled=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,compiled,0);
        if(compiled[0]==0) {GLES20.glDeleteShader(shader);throw new IllegalStateException("GPU display shader");}
        return shader;
    }
    private static void await(CountDownLatch latch,int ms,String operation) {
        try {if(!latch.await(ms,TimeUnit.MILLISECONDS))throw new IllegalStateException(operation+" deadline");}
        catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException(operation+" interrupted",error);}
    }
    private static void require(boolean ok) {if(!ok)throw new IllegalStateException("GPU display EGL/sync failure");}
    private static void checkGl() {if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GPU display GL failure");}
}
