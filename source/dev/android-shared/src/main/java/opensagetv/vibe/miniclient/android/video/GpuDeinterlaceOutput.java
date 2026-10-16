package opensagetv.vibe.miniclient.android.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Choreographer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy;

/**
 * Opt-in hardware-decoder Surface -> GLES -> existing video SurfaceView.
 *
 * The decoder/Exo scheduler remains the master clock. There is no CPU frame
 * readback, encoded-data modification, alternate audio clock or periodic
 * diagnostic polling. SurfaceTexture operations belong to the input thread;
 * the positively qualified woven-pair experiment owns final prepared-field
 * display on a separate shared-context thread, with explicit GPU fences.
 * Per-frame metadata is bounded and paired with the actual decoded Surface
 * timestamp; unknown/progressive/film input passes through unchanged. Off and
 * Auto never construct this class. Owners detach the decoder BEFORE close.
 */
public final class GpuDeinterlaceOutput implements AutoCloseable {
    public interface Failure { void failed(String reason); }
    public interface Provider { GpuDeinterlaceOutput current(); }
    public interface SourcePictureLookup { long pictureTimeUs(long presentationUs); }
    public interface WovenEvidence { boolean confirmed(); }
    /** Optional finite DEBUG observer, implemented only in the debug source set.
     * Production playback never installs one or reads decoded pixels on the CPU.
     * Observe the original OES texture BEFORE filtering; compressed flags and
     * nominal FPS alone cannot establish decoded woven/progressive geometry.
     */
    public interface FrameProbe {
        void frame(int texture, float[] transform, int width, int height,
                   long presentationUs, long stampNs, long metadataStepUs);
        void presented(int field, boolean filtered, long deadlineNs, int width, int height);
        void completed();
    }
    private static final java.util.concurrent.atomic.AtomicReference<FrameProbe> nextDebugProbe =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final AtomicInteger nextDebugProfile=new AtomicInteger();
    /** Explicit one-stage DEBUG timing arm, never installed automatically. */
    public static void armDebugProfile() {nextDebugProfile.set(240);}
    /** Called only by the debug-build receiver; release output ignores it. */
    public static void armDebugProbe(FrameProbe probe) { nextDebugProbe.set(probe); }
    private static final String TAG = "VibeGpuFields";
    // Qualified only by decoded field IDs plus independent 720p60 HDMI capture
    // on API-25/mantis. Every other device/layout retains the existing bounded
    // native fallback; counters alone never broaden this admission.
    private static final boolean QUALIFIED_SPLIT_FIELD_PRESENTER=true;
    private final HandlerThread thread = new HandlerThread("VibeGpuFields");
    private final Handler gl;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SurfaceHolder holder;
    private final SurfaceView videoView;
    private final Failure failure;
    private final String algorithm, parity;
    private final boolean fieldRate;
    private final boolean inspect;
    private final boolean debug;
    private final boolean splitFieldContext;
    private final float displayHz;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final AtomicInteger attachedRenderers = new AtomicInteger();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicInteger decoderTransitions = new AtomicInteger();
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private volatile Surface input;
    private Surface displaySurface;
    private volatile boolean displayLayerChanged;
    private volatile boolean playing;
    private volatile String state = "Starting", reason = "awaiting_decoded_frame";
    private volatile String failureWitness = "";
    private volatile long inputFrames, outputFrames, filteredFrames, secondFields;
    private volatile long metadataFrames, metadataStepUs, surfaceStepNs;
    private volatile float codedFps;
    private volatile String renderer = "";
    private volatile int swapInterval = 1;
    private int minimumSwapInterval,maximumSwapInterval;
    private int profileRemaining;
    private volatile int profileFrames;
    private volatile long profileBindNs, profileUpdateNs, profileMaxUpdateNs, profileDrawNs, profileMaxDrawNs;
    private int profilePresentRemaining;
    private volatile int profilePresents;
    private volatile long profilePresentNs,profileMaxPresentNs;
    private int profilePhaseRemaining;
    private volatile int profilePhase0,profilePhase1,profilePhase1FirstStillDue;
    private volatile long profilePhase1FirstLeadNs;
    private volatile FrameProbe frameProbe;
    private int probeRemaining;
    private long previousMetadataUs;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig config;
    private EGLSurface pbuffer = EGL14.EGL_NO_SURFACE, window = EGL14.EGL_NO_SURFACE;
    private EGLSurface currentSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture decoderTexture;
    private int externalTexture, copyProgram, filterProgram, fastProgram, extractProgram, presentProgram;
    private int preparedProgram;
    private final int[] preparedTextures=new int[4],preparedFbos=new int[4];
    private final boolean[] preparedBusy=new boolean[4];
    private int preparedWidth,preparedHeight;
    private GpuPreparedFieldPresenter presenter;
    private boolean presenterAwaitingFreshInput;
    private int splitEpoch;
    private final ArrayDeque<PreparedField> pendingFields=new ArrayDeque<>();
    private Choreographer choreographer;
    private boolean fieldTickScheduled;
    private boolean insideFieldTick;
    private long queuedEpochStartedNs,queuedEpochOutputs;
    private volatile long startupFieldsDiscarded;
    private boolean pendingDecoderInput;
    private long decoderInputNotBeforeNs;
    private volatile boolean deferredWovenInput;
    private final Choreographer.FrameCallback fieldTick=frameTimeNs -> guarded(() -> consumeAndPresent(frameTimeNs));
    private final int[] textures = new int[2], fbos = new int[2];
    private int width, height, targetWidth, targetHeight, index;
    private int requestedBufferWidth,requestedBufferHeight;
    private int allocatedWidth, allocatedHeight;
    private boolean havePrevious;
    private boolean imageLatched;
    private final GpuDeinterlacePolicy.Cadence cadence=new GpuDeinterlacePolicy.Cadence();
    private final GpuDeinterlacePolicy.PresentationBudget budget=new GpuDeinterlacePolicy.PresentationBudget();
    private long previousInputNs;
    private long lastWovenPictureUs=Long.MIN_VALUE;
    private volatile long codecSubmittedPictureUs=Long.MIN_VALUE;
    private volatile SourcePictureLookup sourcePictureLookup;
    private volatile WovenEvidence wovenEvidence;
    private volatile int warmWidth,warmHeight;
    private volatile boolean formatWarm;
    private volatile long formatWarmStartedNs;
    private final float[] identity = new float[16], transform = new float[16];
    private final FloatBuffer positions = buffer(-1,-1, 1,-1, -1,1, 1,1);
    private final FloatBuffer uvs = buffer(0,0, 1,0, 0,1, 1,1);
    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override public void surfaceCreated(SurfaceHolder next) {
            attach(next.getSurface(), next.getSurfaceFrame().width(), next.getSurfaceFrame().height());
        }
        @Override public void surfaceChanged(SurfaceHolder next, int format, int w, int h) {
            attach(next.getSurface(), w, h);
        }
        @Override public void surfaceDestroyed(SurfaceHolder next) {
            gl.post(() -> guarded(() -> {
                destroyWindow(); havePrevious = false; previousInputNs = 0;
                clearQueuedFields();
                displaySurface = null;
                state = "Suspended"; reason = "display_surface_absent";
            }));
        }
    };

    public GpuDeinterlaceOutput(SurfaceView view, String algorithm, String parity,
            boolean fieldRate, Failure failure) {
        this.videoView=view; this.holder = view.getHolder(); this.failure = failure;
        // A finite debug gate may inspect the actual decoded texture before
        // filtering. Never expose this inspection override in release builds
        // or infer woven fields from compressed-stream metadata alone.
        debug = (view.getContext().getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        inspect = "inspect".equals(algorithm) && debug;
        if(debug) { frameProbe=nextDebugProbe.getAndSet(null);if(frameProbe!=null)probeRemaining=64;
            profileRemaining=profilePhaseRemaining=profilePresentRemaining=nextDebugProfile.getAndSet(0); }
        this.algorithm = GpuDeinterlacePolicy.filter(algorithm);
        this.parity = GpuDeinterlacePolicy.parity(parity);
        this.fieldRate = fieldRate;
        // Commissioned API25/mantis preserves its proven GLES2 input. Creation
        // of a display worker still requires positive actual
        // decoder/woven/source evidence; native/Auto/Off never reach here.
        splitFieldContext=QUALIFIED_SPLIT_FIELD_PRESENTER && fieldRate && !inspect && frameProbe==null
                && android.os.Build.VERSION.SDK_INT==25 && "mantis".equals(android.os.Build.DEVICE);
        displayHz = view.getDisplay() == null ? 0f : view.getDisplay().getRefreshRate();
        Matrix.setIdentityM(identity, 0);
        thread.start(); gl = new Handler(thread.getLooper());
        CountDownLatch ready = new CountDownLatch(1);
        gl.post(() -> { try { initialize(); } catch (RuntimeException e) { fail(e); }
            finally { ready.countDown(); } });
        try {
            if (!ready.await(3, TimeUnit.SECONDS)) {
                close();
                throw new IllegalStateException("GPU initialization deadline");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); close(); throw new IllegalStateException("GPU initialization interrupted", e);
        }
        if (input == null || failed.get()) { close(); throw new IllegalStateException("GPU output unavailable"); }
        holder.addCallback(surfaceCallback);
        if (holder.getSurface().isValid())
            attach(holder.getSurface(), holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
    }

    public Surface inputSurface() { return input; }
    /** Allocate/JIT the scoped field path BEFORE first codec output admission.
     * The decoder's existing render loop waits asynchronously,
     * never sleeps or moves the source/audio clock. Only the proven MTK woven
     * path requests this bounded preflight; unknown/native formats do not.
     * Offscreen warm draws are never swapped or counted as source fields.
     */
    public boolean prepareWovenFormat(int w,int h) {
        WovenEvidence evidence=wovenEvidence;
        if(inspect || !fieldRate || frameProbe!=null || closed.get() || failed.get()
                || evidence==null || !evidence.confirmed())return true;
        if(w<1 || h<4 || w>4096 || h>4096 || (h&1)!=0)return true;
        if(warmWidth!=w || warmHeight!=h) {
            warmWidth=w;warmHeight=h;formatWarm=false;formatWarmStartedNs=System.nanoTime();
            main.post(() -> {
                if(!closed.get() && !failed.get() && videoView instanceof PlayerSurfaceView)
                    ((PlayerSurfaceView)videoView).useGpuSourceBuffers(this,w,h);
            });
        }
        if(!formatWarm)gl.post(() -> guarded(() -> {
            if(formatWarm || warmWidth!=w || warmHeight!=h || targetWidth!=w || targetHeight!=h)return;
            if(!ensureWindow(true))return;
            width=w;height=h;
            if("motion".equals(algorithm))allocate(w,h);
            allocatePreparedFields();makeCurrent(window==EGL14.EGL_NO_SURFACE ? pbuffer : window);
            Matrix.setIdentityM(transform,0);
            for(int slot=0;slot<4;slot++)prepareOriginalField(slot,slot%2);
            // Exercise the final shader offscreen as well. No display buffer,
            // readback, first-frame notification or output counter is touched.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,preparedFbos[0]);
            GLES20.glViewport(0,0,w,preparedHeight);
            int program="motion".equals(algorithm) ? preparedProgram : presentProgram;
            GLES20.glUseProgram(program);bindGeometry(program,identity);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,preparedTextures[1]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
            if(program==presentProgram) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uHeight"),h);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uParity"),0);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();GLES20.glFlush();
            havePrevious=false;formatWarm=true;
        }));
        if(!formatWarm && System.nanoTime()-formatWarmStartedNs>2_000_000_000L) {
            gl.post(() -> fail("gpu_format_preflight_deadline",null));return true;
        }
        return formatWarm;
    }
    public void setSourcePictureLookup(SourcePictureLookup lookup) {sourcePictureLookup=lookup;}
    public void setWovenEvidence(WovenEvidence evidence) {wovenEvidence=evidence;}
    /** The proven MTK output repeats a woven picture at half-frame PTS. Once
     * its original pair is submitted, the duplicate codec buffer contains no
     * new field. Return that duplicate via the backend's normal skip hook,
     * instead of another blocking SurfaceTexture latch. Unknown source mapping,
     * finite geometry probes, frame mode and native/paused paths cannot skip.
     */
    public boolean isDuplicateWovenOutput(long presentationUs) {
        SourcePictureLookup lookup=sourcePictureLookup;
        if(inspect || !fieldRate || !playing || frameProbe!=null || closed.get() || failed.get()
                || decoderTransitions.get()>0 || lookup==null || codecSubmittedPictureUs==Long.MIN_VALUE)return false;
        return lookup.pictureTimeUs(presentationUs)==codecSubmittedPictureUs;
    }
    /** Admit a proven retained pair to the texture before its display deadline.
     * The backend already bounds admission to ONE field. SurfaceTexture is a
     * preprocessing consumer, not the final display; holding that producer
     * until its display time leaves no time to prepare both original fields.
     * Keep Frame.releaseNs immutable for actual GPU scanout scheduling and
     * associate the exact submitted codec timestamp separately. No boolean
     * release/codec adapter override, source PTS or audio-clock modification.
     */
    public long decoderTextureReleaseNs(long presentationUs,long deadlineNs) {
        if(inspect || !fieldRate || !playing || frameProbe!=null || closed.get() || failed.get()
                || decoderTransitions.get()>0)return deadlineNs;
        synchronized(frames) {
            for(Frame frame:frames)if(frame.presentationUs==presentationUs && frame.releaseNs==deadlineNs
                    && frame.interlaced && frame.duplicatedWoven && frame.sourceFieldPhase>=0
                    && frame.sourcePictureUs!=Long.MIN_VALUE && frame.nativeBypassReason.isEmpty()
                    && Math.abs(frame.speed-1f)<.01f) {
                frame.codecReleaseNs=Math.min(deadlineNs,System.nanoTime());return frame.codecReleaseNs;
            }
        }
        return deadlineNs;
    }
    /** Main-thread, one-shot refresh for the scoped MTK/Shield Tube
     * GPU->native boundary. EGL changes the native window's buffer format,
     * while SurfaceView still caches its original opaque format. Rebuild its
     * display layer using a DIFFERENT explicit opaque format before the native
     * renderer attaches; merely releasing EGL left a frozen HDMI front buffer.
     * No source/seek/audio reset or preference mutation. Other paths do not call
     * this method; the normal new Activity retains its XML/default format.
     */
    public void refreshNativeDisplayLayer() {
        if(videoView instanceof PlayerSurfaceView)
            ((PlayerSurfaceView)videoView).restoreAfterGpuBuffers(this);
        if (!displayLayerChanged) return;
        if(videoView instanceof PlayerSurfaceView)
            ((PlayerSurfaceView)videoView).refreshAfterGpuOutput();
        else holder.setFormat(android.graphics.PixelFormat.RGBX_8888);
    }
    /** Playback-thread output ownership. Application-thread release/retarget
     * can time out BEFORE the renderer has released its codec or old Surface.
     */
    public void rendererAttached() {attachedRenderers.incrementAndGet();}
    public void rendererDetached() {
        int previous;
        do {previous=attachedRenderers.get();}
        while(!attachedRenderers.compareAndSet(previous,Math.max(0,previous-1)));
        if(attachedRenderers.get()==0 && closeRequested.get())closeAfterDetach();
    }
    public String state() { return state; }
    public String reason() { return reason; }
    public String failureWitness() { return failureWitness; }
    public String activeFilter() {
        return "Active".equals(state) ? ("motion".equals(algorithm) ? "GPU Motion" : "GPU Fast") : "";
    }
    public long inputFrames() { return inputFrames; }
    public long outputFrames() { return outputFrames; }
    public long filteredFrames() { return filteredFrames; }
    public long secondFields() { return secondFields; }
    public long metadataFrames() { return metadataFrames; }
    public long metadataStepUs() { return metadataStepUs; }
    public long surfaceStepNs() { return surfaceStepNs; }
    public float codedFps() { return codedFps; }
    public String renderer() { return renderer; }
    public int swapInterval() { return swapInterval; }
    public String profileSummary() {
        int n=profileFrames;
        return "frames:"+n+",bindUs:"+(n==0?0:profileBindNs/n/1000)+",updateUs:"+(n==0?0:profileUpdateNs/n/1000)
                +",maxUpdateUs:"+profileMaxUpdateNs/1000
                +",drawSwapUs:"+(n==0?0:profileDrawNs/n/1000)+",maxDrawSwapUs:"+profileMaxDrawNs/1000
                +",phase0:"+profilePhase0+",phase1:"+profilePhase1
                +",phase1FirstStillDue:"+profilePhase1FirstStillDue
                +",phase1FirstLeadUs:"+(profilePhase1==0?0:profilePhase1FirstLeadNs/profilePhase1/1000)
                +",startupFieldsDiscarded:"+startupFieldsDiscarded
                +",presents:"+profilePresents+",presentUs:"+(profilePresents==0?0:profilePresentNs/profilePresents/1000)
                +",maxPresentUs:"+profileMaxPresentNs/1000
                +",inputDrawable:"+(window!=EGL14.EGL_NO_SURFACE ? "window" : "pbuffer")
                +",presentationContext:"+(presenter==null ? "single" : "shared");
    }
    /** Explicit DEBUG-only finite probe; never a recurring production sampler. */
    public boolean profileNextFrames(int count) {
        if(!debug || closed.get() || failed.get() || count<1 || count>240)return false;
        gl.post(() -> {profileFrames=0;profileBindNs=profileUpdateNs=profileMaxUpdateNs=profileDrawNs=profileMaxDrawNs=0;profileRemaining=count;
            profilePresents=0;profilePresentNs=profileMaxPresentNs=0;profilePresentRemaining=count;
            if(presenter!=null)presenter.profile(count);
            profilePhaseRemaining=count;profilePhase0=profilePhase1=profilePhase1FirstStillDue=0;profilePhase1FirstLeadNs=0;});
        return true;
    }
    /** Explicit test-fixture probe; no recurring sampler or preference change. */
    public boolean probeNextFrames(FrameProbe probe, int count) {
        if (!debug || probe == null || count < 1 || count > 64
                || closed.get() || failed.get()) return false;
        // Arm before Watch for this experiment: mid-stage readback must not
        // replace a live independent field presenter with a competing producer.
        if(presenter!=null)return false;
        gl.post(() -> {
            if (frameProbe != null) frameProbe.completed();
            frameProbe = probe; probeRemaining = count;
        });
        return true;
    }
    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean value) {
        boolean wasPlaying=playing;playing=value;
        if(!value) {flush();return;}
        if(!wasPlaying && splitFieldContext) {
            // MediaCodec can resume producing metadata after a long API-25
            // pause while SurfaceTexture coalesces the one notification that
            // would restart consumption. Give the qualified field presenter
            // one bounded acquisition opportunity only if the normal callback
            // has still not left the re-anchor state. This is not a poll and
            // does not shift a source/display timestamp or replay playback.
            gl.postDelayed(() -> {
                if(playing && !closed.get() && !failed.get() && presenter!=null
                        && "Pending".equals(state) && "reanchoring".equals(reason)
                        && !pendingDecoderInput && decoderTransitions.get()==0) {
                    pendingDecoderInput=true;decoderInputNotBeforeNs=System.nanoTime();
                    if(!fieldTickScheduled) {
                        fieldTickScheduled=true;choreographer.postFrameCallback(fieldTick);
                    }
                }
            },250);
        }
    }

    /** Return retained producer buffers BEFORE a synchronous codec flush/release.
     * Keep the input Surface/context alive until the decoder is detached. Nested
     * renderer operations cannot re-enable consumption inside an outer teardown.
     */
    public void beginDecoderTransition() {
        if(closed.get())return;
        decoderTransitions.incrementAndGet();
        codecSubmittedPictureUs=Long.MIN_VALUE;
        if(Looper.myLooper()==thread.getLooper()) {
            try {clearQueuedFields();makeCurrent(pbuffer);releaseHeldTexture();}
            catch(RuntimeException e){Log.w(TAG,"GPU decoder buffer return",e);}
            return;
        }
        CountDownLatch returned=new CountDownLatch(1);
        gl.post(() -> {try {clearQueuedFields();makeCurrent(pbuffer);releaseHeldTexture();}
            catch(RuntimeException e){Log.w(TAG,"GPU decoder buffer return",e);}
            finally{returned.countDown();}});
        try {
            if(!returned.await(500,TimeUnit.MILLISECONDS))Log.w(TAG,"GPU decoder buffer return deadline");
        } catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    public void endDecoderTransition() {
        // AtomicInteger.updateAndGet is API24; the APK also supports API23.
        int previous;
        do {previous=decoderTransitions.get();}
        while(!decoderTransitions.compareAndSet(previous,Math.max(0,previous-1)));
        if(decoderTransitions.get()==0)flush();
    }
    private void releaseHeldTexture() {
        if(decoderTexture!=null && imageLatched) {
            decoderTexture.releaseTexImage();imageLatched=false;
        }
    }

    /** Called by the backend's existing frame-release callback, never a poll. */
    public void frame(long presentationUs, long releaseNs, int w, int h, float fps,
            float speed, String mime, String scan, long progressive, boolean topFirst,
            boolean hardware, boolean duplicatedWoven, int sourceFieldPhase, long sourcePictureUs) {
        metadataStepUs = metadataFrames == 0 ? 0 : presentationUs-previousMetadataUs;
        previousMetadataUs=presentationUs; metadataFrames++;
        codedFps=fps;
        Frame frame = new Frame(presentationUs, releaseNs, metadataStepUs, w, h, fps, speed,
                hardware && GpuDeinterlacePolicy.wovenMpeg2(mime, scan, progressive),
                GpuDeinterlacePolicy.firstField(parity, topFirst), duplicatedWoven, sourceFieldPhase, sourcePictureUs,
                !hardware ? "decoder_not_hardware" : scan,
                !GpuDeinterlacePolicy.unsupportedInputReason(mime).isEmpty()
                    ? GpuDeinterlacePolicy.unsupportedInputReason(mime)
                    : GpuDeinterlacePolicy.nativeBypassReason(mime,scan,progressive));
        codecSubmittedPictureUs=!inspect && frame.interlaced && duplicatedWoven && frame.nativeBypassReason.isEmpty()
                ? sourcePictureUs : Long.MIN_VALUE;
        deferredWovenInput=!inspect && fieldRate && frame.interlaced && duplicatedWoven && frame.sourceFieldPhase>=0
                && frame.nativeBypassReason.isEmpty() && Math.abs(speed-1f)<.01f;
        synchronized (frames) {
            if (frames.size() == 32) frames.removeFirst();
            frames.addLast(frame);
        }
    }

    /** Re-anchor temporal history on seek/pause/format/session discontinuity. */
    public void flush() {
        codecSubmittedPictureUs=Long.MIN_VALUE;
        // The main-thread discontinuity/playing callback may arrive AFTER
        // the renderer has queued the new epoch's release metadata. Clearing
        // that FIFO here loses the fresh Surface timestamp and spuriously
        // disables GPU output after seek/pause. Match exact timestamps and
        // reset only temporal history; the bounded FIFO drains/ages normally.
        gl.post(() -> { havePrevious = false; previousInputNs = 0;
            // A discontinuity callback can be delivered after the new codec
            // epoch has already signalled an input image. Resetting the old
            // presentation FIFO must not consume that one-shot signal: there
            // may be no later SurfaceTexture callback to restart rendering.
            // Keep only the notification, never an old prepared output or
            // deadline, and let the next display tick acquire the fresh image.
            boolean freshDecoderInputPending=pendingDecoderInput;
            long freshDecoderInputNotBeforeNs=decoderInputNotBeforeNs;
            clearQueuedFields();
            if(freshDecoderInputPending && decoderTransitions.get()==0) {
                pendingDecoderInput=true;
                decoderInputNotBeforeNs=freshDecoderInputNotBeforeNs;
                if(!fieldTickScheduled) {
                    fieldTickScheduled=true;
                    choreographer.postFrameCallback(fieldTick);
                }
            }
            lastWovenPictureUs=Long.MIN_VALUE;
            cadence.reset();
            budget.reset();
            if (!failed.get() && !closed.get()) { state = "Pending"; reason = "reanchoring"; } });
    }

    private Frame frameFor(long stampNs) {
        synchronized (frames) {
            Frame found = null;
            long closest=Long.MAX_VALUE;
            for (Frame item : frames) {
                // Android Surface timestamps follow scheduled releaseOutputBuffer
                // nanoseconds. Some adapters preserve source PTS instead.
                long distance=Math.min(Math.abs(item.releaseNs-stampNs),Math.abs(item.codecReleaseNs-stampNs));
                if(item.presentationUs*1000L==stampNs)distance=0;
                if(distance<2_000_000L && distance<closest) {found=item;closest=distance;}
            }
            if (found != null) while (!frames.isEmpty()) {
                Frame first = frames.removeFirst(); if (first == found) break;
            }
            return found;
        }
    }

    private void initialize() {
        choreographer=Choreographer.getInstance();
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] versions = new int[2];
        require(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, versions, 0, versions, 1));
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        require(EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE},
                0, configs, 0, 1, count, 0) && count[0] > 0);
        config = configs[0];
        eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE}, 0);
        pbuffer = EGL14.eglCreatePbufferSurface(display, config,
                new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        require(eglContext != EGL14.EGL_NO_CONTEXT && pbuffer != EGL14.EGL_NO_SURFACE);
        makeCurrent(pbuffer);
        renderer=GLES20.glGetString(GLES20.GL_RENDERER);
        copyProgram = program(GpuDeinterlaceShaders.COPY); filterProgram = program(GpuDeinterlaceShaders.FILTER);
        fastProgram = program(GpuDeinterlaceShaders.FAST_EXTERNAL);
        extractProgram = program(GpuDeinterlaceShaders.EXTRACT_FIELD_EXTERNAL);
        presentProgram = program(GpuDeinterlaceShaders.PRESENT_FIELD);
        preparedProgram=program(GpuDeinterlaceShaders.COPY_PREPARED);
        int[] generated = new int[1]; GLES20.glGenTextures(1, generated, 0); externalTexture = generated[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexture);
        parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        decoderTexture = new SurfaceTexture(externalTexture);
        decoderTexture.setOnFrameAvailableListener(texture -> {
            // Continue returning producer buffers during asynchronous detach,
            // even when the normal filtering route has already failed.
            if(!closed.get() && decoderTransitions.get()>0) {
                try {makeCurrent(pbuffer);decoderTexture.updateTexImage();
                    imageLatched=true;releaseHeldTexture();}
                catch(RuntimeException e){Log.w(TAG,"GPU transition drain",e);}
            } else if(!closed.get() && !failed.get() && playing && deferredWovenInput && frameProbe==null) {
                // A frame-available notification need not mean its decoder
                // fence is ready for sampling. Latching it immediately blocks
                // this GL thread across display ticks on the qualified MTK.
                // Coalesce notifications (not source fields) and consume at
                // the next field boundary. Never postpone an already-pending
                // input again, spin, poll, or change its source/display PTS.
                if(!pendingDecoderInput) {
                    pendingDecoderInput=true;
                    // The listener itself may have waited behind GL work.
                    // Adding another full field to its delivery time compounds
                    // latency on every picture; the NEXT display tick already
                    // supplies the deferred acquisition boundary.
                    decoderInputNotBeforeNs=System.nanoTime();
                }
                if(!fieldTickScheduled) {fieldTickScheduled=true;choreographer.postFrameCallback(fieldTick);}
            } else guarded(() -> {pendingDecoderInput=false;render();});
        }, gl);
        input = new Surface(decoderTexture);
    }

    private void attach(Surface surface, int w, int h) {
        gl.post(() -> guarded(() -> {
            destroyWindow();
            clearQueuedFields();
            displaySurface = surface;
            targetWidth = Math.max(1, w); targetHeight = Math.max(1, h);
            havePrevious = false; previousInputNs = 0;
        }));
    }

    /** A pending decoder input must not claim the display's BufferQueue.
     * The first real format can be unsupported/progressive. Creating an EGL
     * window before that evidence altered SurfaceView's buffer format and
     * froze Tube AVC native fallback even with a scoped layer refresh. Keep
     * only the input texture/pbuffer until a frame actually needs the GPU.
     * No vendor guess or native decoder reset is required for a bypass.
     */
    private boolean ensureWindow(boolean provenWovenPair) {
        if(splitFieldContext && QUALIFIED_SPLIT_FIELD_PRESENTER && provenWovenPair) {
            if(displaySurface==null || !displaySurface.isValid())return false;
            if(presenter==null) {
                // A previously unknown format may have used the ordinary
                // window. Detach it before another EGL producer can claim it.
                if(window!=EGL14.EGL_NO_SURFACE)destroyWindow();
                displayLayerChanged=true;
                presenter=new GpuPreparedFieldPresenter(display,config,eglContext,displaySurface,
                    targetWidth,targetHeight,"motion".equals(algorithm),profilePresentRemaining,
                    new GpuPreparedFieldPresenter.Listener() {
                        @Override public boolean mayPresent() {
                            return playing && !closed.get() && !failed.get() && decoderTransitions.get()==0;
                        }
                        @Override public void presented(GpuPreparedFieldPresenter.Field field,long measuredNs) {
                            if(closed.get() || failed.get())return;
                            outputFrames++;filteredFrames++;queuedEpochOutputs++;if(field.second)secondFields++;
                            if(measuredNs>0) {
                                profilePresentNs+=measuredNs;profileMaxPresentNs=Math.max(profileMaxPresentNs,measuredNs);
                                profilePresents++;
                            }
                            state="Active";reason="shared_original_field_queue";
                        }
                        @Override public void returned(GpuPreparedFieldPresenter.Field field,long readFence) {
                            gl.post(() -> {
                                if(closed.get())return;
                                guarded(() -> {
                                    makeCurrent(pbuffer);
                                    if(readFence!=0) {GpuEglFence.waitGpu(display,readFence);GpuEglFence.destroy(display,readFence);}
                                    // A late old-epoch callback cannot mark a
                                    // freshly reused texture slot free.
                                    if(field.epoch==splitEpoch)preparedBusy[field.slot]=false;
                                    checkGl();
                                });
                            });
                        }
                        @Override public void startupDiscarded() {startupFieldsDiscarded++;}
                        @Override public void failed(String failureReason) {gl.post(() -> fail(failureReason,null));}
                    });
                presenterAwaitingFreshInput=true;
                swapInterval=0;
            }
            return true;
        }
        if(presenter!=null)throw new IllegalStateException("shared field layout changed; native output required");
        boolean created=window==EGL14.EGL_NO_SURFACE;
        if(created) {
            if (displaySurface == null || !displaySurface.isValid()) return false;
            displayLayerChanged = true;
            window = EGL14.eglCreateWindowSurface(display, config, displaySurface, new int[]{EGL14.EGL_NONE},0);
            require(window != EGL14.EGL_NO_SURFACE);
            makeCurrent(window);
            int[] value=new int[1];
            require(EGL14.eglGetConfigAttrib(display,config,EGL14.EGL_MIN_SWAP_INTERVAL,value,0));
            minimumSwapInterval=value[0];
            require(EGL14.eglGetConfigAttrib(display,config,EGL14.EGL_MAX_SWAP_INTERVAL,value,0));
            maximumSwapInterval=value[0];
        }
        int requested=GpuDeinterlacePolicy.swapInterval(fieldRate,minimumSwapInterval,maximumSwapInterval,
                android.os.Build.VERSION.SDK_INT,android.os.Build.DEVICE,renderer,provenWovenPair);
        if(requested<0)throw new IllegalStateException("paced original-field queue unsupported");
        if(created || swapInterval!=requested) {
            require(EGL14.eglSwapInterval(display,requested));swapInterval=requested;
        }
        return true;
    }

    private void allocate(int w, int h) {
        if (allocatedWidth == w && allocatedHeight == h) return;
        require(w > 0 && h > 0 && w <= 4096 && h <= 4096);
        GLES20.glDeleteTextures(2, textures, 0); GLES20.glDeleteFramebuffers(2, fbos, 0);
        GLES20.glGenTextures(2, textures, 0); GLES20.glGenFramebuffers(2, fbos, 0);
        for (int i=0; i<2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]); parameters(GLES20.GL_TEXTURE_2D);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, textures[i],0);
            require(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE);
        }
        allocatedWidth=w; allocatedHeight=h; havePrevious=false; previousInputNs=0;
    }

    private void render() {
        if(presenter!=null && availablePreparedSlots()<2) {
            // Keep the retained queue at four original fields. MediaCodec may
            // deliver a short startup burst, but acquiring another OES image
            // before two display-read fences return would force either an
            // unbounded texture pool or an artificial field drop. Leave the
            // producer image queued and retry on a later input tick instead.
            pendingDecoderInput=true;decoderInputNotBeforeNs=System.nanoTime();
            state="Pending";reason="awaiting_prepared_field_slots";
            if(!fieldTickScheduled) {fieldTickScheduled=true;choreographer.postFrameCallback(fieldTick);}
            return;
        }
        long profileStart=profileRemaining>0 ? System.nanoTime() : 0;
        // SurfaceTexture and FBO work need the context, not a pbuffer switch.
        // Keep the display current between frames; tile-based drivers can
        // otherwise synchronize their entire previous window on each bind.
        makeCurrent(window==EGL14.EGL_NO_SURFACE ? pbuffer : window);
        long afterBind=profileStart==0 ? 0 : System.nanoTime();
        decoderTexture.updateTexImage();
        imageLatched=true;
        long afterUpdate=profileStart==0 ? 0 : System.nanoTime();
        if(decoderTransitions.get()>0) {releaseHeldTexture();return;}
        long stamp = decoderTexture.getTimestamp();
        Frame frame = frameFor(stamp);
        inputFrames++;
        if (frame == null) {
            // Do not leave a black Surface while claiming Exo's first-frame
            // callback proves picture output. Restore native output once.
            fail(new IllegalStateException("decoded frame metadata unmatched")); return;
        }
        // Finite DEBUG-only deadline observation, armed with the existing
        // timing probe. No pixels, new scheduler, recurring diagnostic sampler
        // or producer release change. A coalesced phase-one callback can still
        // arrive BEFORE the first field's original deadline; establish that
        // boundary before changing which retained original fields are emitted.
        if(profilePhaseRemaining>0) {
            profilePhaseRemaining--;
            if(frame.duplicatedWoven && frame.sourceFieldPhase==0)profilePhase0++;
            if(frame.duplicatedWoven && frame.sourceFieldPhase==1 && frame.fps>0) {
                long lead=frame.releaseNs-System.nanoTime()-Math.round(500_000_000.0/frame.fps);
                profilePhase1++;profilePhase1FirstLeadNs+=lead;
                if(lead>=0)profilePhase1FirstStillDue++;
            }
        }
        if(!inspect && !frame.nativeBypassReason.isEmpty()) {
            // A confirmed progressive/film/separate-field source needs no
            // woven-frame filter. Return to native presentation rather than
            // paying GPU-copy cost or claiming a 38fps copy is normal 60p.
            fail(frame.nativeBypassReason,null);return;
        }
        boolean motion="motion".equals(algorithm);
        require(frame.width > 0 && frame.height >= 4 && frame.width <= 4096 && frame.height <= 4096);
        if(!inspect && frame.interlaced && frame.duplicatedWoven && videoView instanceof PlayerSurfaceView
                && (requestedBufferWidth!=frame.width || requestedBufferHeight!=frame.height)) {
            requestedBufferWidth=frame.width;requestedBufferHeight=frame.height;
            final int bufferWidth=frame.width,bufferHeight=frame.height;
            main.post(() -> {
                if(!closed.get() && !failed.get())
                    ((PlayerSurfaceView)videoView).useGpuSourceBuffers(this,bufferWidth,bufferHeight);
            });
        }
        if(!inspect && frame.interlaced && frame.duplicatedWoven && videoView instanceof PlayerSurfaceView
                && (targetWidth!=frame.width || targetHeight!=frame.height)) {
            // setFixedSize is asynchronous. Do not create a full-display EGL
            // window and queue fields while its source-sized replacement is
            // still pending: surfaceChanged will destroy that queue. Keep
            // returning decoder input normally until the real holder reports
            // the requested dimensions, without re-anchoring source/audio.
            releaseHeldTexture();state="Pending";reason="awaiting_source_sized_display";return;
        }
        if (!ensureWindow(!inspect && frame.interlaced && frame.duplicatedWoven)) {
            releaseHeldTexture();state="Suspended";reason="display_surface_absent";return;
        }
        if(presenterAwaitingFreshInput) {
            // Shared-window creation is deliberately synchronous and can use
            // most of this picture's release budget. Never present that pair
            // late or shift its deadlines: omit only this startup prefix and
            // begin with the next untouched decoded picture.
            presenterAwaitingFreshInput=false;startupFieldsDiscarded+=2;
            releaseHeldTexture();state="Pending";reason="awaiting_fresh_original_field";return;
        }
        if(presenter!=null && !(fieldRate && playing && frame.duplicatedWoven && frameProbe==null)) {
            // The first decoded picture can precede Exo's play transition. A
            // split presenter has no input-thread window by design, so the
            // legacy immediate-draw branch must never target its pbuffer.
            // Return this producer image and wait for an actively playing
            // woven picture; do not synthesize output or alter source timing.
            releaseHeldTexture();state="Pending";reason="awaiting_active_field_output";return;
        }
        if (width != frame.width || height != frame.height) {
            clearQueuedFields();havePrevious=false;previousInputNs=0;cadence.reset();lastWovenPictureUs=Long.MIN_VALUE;
        }
        width=frame.width; height=frame.height;
        boolean filter = frame.interlaced && !inspect;
        if(filter && fieldRate && frame.duplicatedWoven && frame.sourcePictureUs!=Long.MIN_VALUE) {
            // Native emits the same woven picture again at its second-field
            // PTS. Both original fields are already in the retained texture:
            // process that picture once, never shade its duplicate again.
            if(frame.sourcePictureUs==lastWovenPictureUs)return;
            lastWovenPictureUs=frame.sourcePictureUs;
        }
        if (motion) allocate(width,height);
        else if(filter && !(fieldRate && playing && frame.duplicatedWoven && frameProbe==null))allocate(width,height/2);
        decoderTexture.getTransformMatrix(transform);
        if (frameProbe != null && probeRemaining > 0) {
            frameProbe.frame(externalTexture, transform, width, height,
                    frame.presentationUs, stamp, frame.metadataStepUs);
        }
        if (motion) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbos[index]);
            GLES20.glViewport(0,0,width,height);
            GLES20.glUseProgram(copyProgram); bindGeometry(copyProgram,transform);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgram,"uCurrent"),0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); checkGl();
        }
        if(filter && fieldRate && playing && frame.duplicatedWoven && frameProbe==null
                && GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,0,frame.fps,frame.speed,displayHz)>0) {
            if(frame.sourceFieldPhase<0) {fail("gpu_source_field_phase_unavailable",null);return;}
            // The texture retains BOTH original source fields even when its
            // duplicate callback coalesces. Copy them on the GPU, then submit
            // ONE at each display tick. Posting both immediately lets the
            // compositor discard the older already-due buffer even with a
            // synchronous producer. These are existing source deadlines, not
            // a second master/audio clock, repeated image or CPU readback.
            long half=GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,0,frame.fps,frame.speed,displayHz);
            long firstDue=frame.releaseNs-frame.sourceFieldPhase*half;
            queueOriginalFields(frame,firstDue,half);
            // BOTH original fields now live in independent GPU textures, so
            // display ticks no longer sample this decoder image. Submit their
            // copies and return only this completed input ownership instead
            // of retaining a vendor decoder buffer until the next latch.
            // This differs from the withdrawn per-field-return experiment:
            // immediate/probe/native paths still retain their OES image.
            GLES20.glFlush();releaseHeldTexture();
            previousInputNs=stamp;havePrevious=true;index=1-index;
            if(profileStart!=0) {
                long prepareNs=System.nanoTime()-afterUpdate;
                profileBindNs+=afterBind-profileStart;profileUpdateNs+=afterUpdate-afterBind;
                profileMaxUpdateNs=Math.max(profileMaxUpdateNs,afterUpdate-afterBind);
                profileDrawNs+=prepareNs;profileMaxDrawNs=Math.max(profileMaxDrawNs,prepareNs);
                profileFrames++;profileRemaining--;
            }
            return;
        }
        // Probe/paused/trick/frame-mode output cannot inherit queued fields
        // from the prior field-rate epoch. The finite pixel probe deliberately
        // qualifies geometry, not the uninstrumented presentation scheduler.
        if(!pendingFields.isEmpty())clearQueuedFields();
        makeCurrent(window);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        long delta=previousInputNs == 0 ? 0 : stamp-previousInputNs;
        surfaceStepNs=delta;
        // EGL timestamps are display deadlines from the EXISTING backend
        // scheduler, never stream PTS or a new audio/master clock.
        long now=System.nanoTime();
        long due=Math.max(now,Math.min(frame.releaseNs,now+100_000_000L));
        boolean wholeFrameDelivery=cadence.allowSecondField(frame.fps,frame.metadataStepUs*1000L);
        int displayedField=frame.firstField;
        if (filter && fieldRate && frame.duplicatedWoven) {
            if (frame.sourceFieldPhase<0) {
                fail("gpu_source_field_phase_unavailable",null);return;
            }
            // Select the field belonging to this SOURCE timestamp. Toggling
            // callback parity would go wrong after a missed Surface callback,
            // seek or B-picture reorder. No new field or timestamp is created.
            displayedField ^= frame.sourceFieldPhase;
        }
        draw(displayedField,filter,due);
        outputFrames++;
        long half = filter && fieldRate && playing && wholeFrameDelivery
                ? GpuDeinterlacePolicy.secondFieldDelayNs(frame.fps,frame.metadataStepUs*1000L,frame.speed,displayHz) : 0;
        if(filter && fieldRate && playing && frame.duplicatedWoven && frame.sourceFieldPhase==0) {
            // Pixel-qualified woven output retains a REAL second field even
            // when the codec emits at half-frame intervals. Schedule it once
            // at its existing source field deadline; not a synthesized image.
            half=GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,frame.sourceFieldPhase,
                    frame.fps,frame.speed,displayHz);
        }
        if (half > 0) { draw(1-frame.firstField,true,due+half); outputFrames++; secondFields++; }
        if (filter) { filteredFrames++; state="Active";
            reason=fieldRate && cadence.alreadyFieldRate()
                    ? frame.duplicatedWoven ? "woven_field_pts" : "decoder_field_rate"
                    : fieldRate && half==0 ? "frame_rate_until_valid_field_delivery" : half>0 ? "field_rate" : "frame_rate"; }
        else { state="Bypass"; reason=inspect ? "decoder_output_inspection" : frame.reason; }
        // Do not keep claiming usable field processing while dropping motion
        // indefinitely. A submission deficit cannot meet display cadence;
        // healthy submissions alone still require separate picture evidence.
        // Reuse existing frame callbacks/time, never a diagnostic polling loop.
        if(filter && playing && Math.abs(frame.speed-1f)<.01f) {
            float required=frame.fps*(fieldRate ? 2f : 1f);
            if(budget.belowBudget(System.nanoTime(),outputFrames,required)) {
                fail("gpu_presentation_budget",new IllegalStateException("GPU output below requested frame/field budget"));
                return;
            }
        } else budget.reset();
        previousInputNs=stamp; havePrevious=true; index=1-index;
        if (frameProbe != null && --probeRemaining == 0) {
            frameProbe.completed(); frameProbe = null;
        }
        if(profileStart!=0) {
            long drawNs=System.nanoTime()-afterUpdate;
            profileBindNs+=afterBind-profileStart;
            profileUpdateNs+=afterUpdate-afterBind;
            profileMaxUpdateNs=Math.max(profileMaxUpdateNs,afterUpdate-afterBind);
            profileDrawNs+=drawNs;
            profileMaxDrawNs=Math.max(profileMaxDrawNs,drawNs);profileFrames++;profileRemaining--;
        }
    }

    private void draw(int field, boolean enabled, long deadlineNs) {
        drawImage(field,enabled,0,targetWidth,targetHeight);
        if(frameProbe!=null)frameProbe.presented(field,enabled,deadlineNs,targetWidth,targetHeight);
        require(EGLExt.eglPresentationTimeANDROID(display,window,deadlineNs));
        require(EGL14.eglSwapBuffers(display,window));
    }

    /** Shared filtering shader into either the window or a retained GPU field. */
    private void drawImage(int field,boolean enabled,int destination,int destinationWidth,int destinationHeight) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,destination);
        GLES20.glViewport(0,0,destinationWidth,destinationHeight);
        if (!"motion".equals(algorithm)) {
            if(enabled && (height&1)==0) {
                // FBO rendering uses the same context/current window. Do not
                // add an EGL surface switch for each field extraction.
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbos[index]);
                GLES20.glViewport(0,0,width,height/2);
                GLES20.glUseProgram(extractProgram);bindGeometry(extractProgram,identity);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalTexture);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(extractProgram,"uCurrent"),0);
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(extractProgram,"uTextureTransform"),1,false,transform,0);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(extractProgram,"uHeight"),height);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(extractProgram,"uParity"),field);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,destination);
                GLES20.glViewport(0,0,destinationWidth,destinationHeight);
                GLES20.glUseProgram(presentProgram);bindGeometry(presentProgram,identity);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[index]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(presentProgram,"uCurrent"),0);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(presentProgram,"uHeight"),height);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(presentProgram,"uParity"),field);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();
                return;
            }
            GLES20.glUseProgram(fastProgram); bindGeometry(fastProgram,identity);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(fastProgram,"uCurrent"),0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(fastProgram,"uTextureTransform"),1,false,transform,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(fastProgram,"uHeight"),height);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(fastProgram,"uParity"),field);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(fastProgram,"uEnabled"),enabled?1:0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); checkGl();
            return;
        }
        GLES20.glUseProgram(filterProgram); bindGeometry(filterProgram,identity);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[index]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[1-index]);
        uniform("uCurrent",0); uniform("uPrevious",1);
        scalar("uHeight",height); scalar("uParity",field); scalar("uEnabled",enabled?1:0);
        scalar("uMotion","motion".equals(algorithm)?1:0); scalar("uHasPrevious",havePrevious?1:0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); checkGl();
    }

    private void clearQueuedFields() {
        if(presenter!=null) {
            long barrier=presenter.clear(++splitEpoch);
            makeCurrent(pbuffer);
            if(barrier!=0) {GpuEglFence.waitGpu(display,barrier);GpuEglFence.destroy(display,barrier);checkGl();}
        }
        if(choreographer!=null && fieldTickScheduled)choreographer.removeFrameCallback(fieldTick);
        fieldTickScheduled=false;pendingFields.clear();
        pendingDecoderInput=false;
        java.util.Arrays.fill(preparedBusy,false);
        queuedEpochStartedNs=queuedEpochOutputs=0;
    }
    private void allocatePreparedFields() {
        int storedHeight="motion".equals(algorithm) ? height : height/2;
        if(preparedWidth==width && preparedHeight==storedHeight)return;
        clearQueuedFields();
        GLES20.glDeleteTextures(4,preparedTextures,0);GLES20.glDeleteFramebuffers(4,preparedFbos,0);
        GLES20.glGenTextures(4,preparedTextures,0);GLES20.glGenFramebuffers(4,preparedFbos,0);
        for(int slot=0;slot<4;slot++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,preparedTextures[slot]);parameters(GLES20.GL_TEXTURE_2D);
            if(!"motion".equals(algorithm)) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            }
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,storedHeight,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,preparedFbos[slot]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,preparedTextures[slot],0);
            require(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE);
        }
        preparedWidth=width;preparedHeight=storedHeight;
    }
    private int availablePreparedSlots() {
        int available=0;
        for(boolean busy:preparedBusy)if(!busy)available++;
        return available;
    }
    /** Fast stores ONLY genuine same-parity rows at half height. The display
     * interpolates these retained rows once, avoiding a full-height prepared
     * bob texture plus a second full-height copy. Motion retains its existing
     * full-resolution temporal algorithm. Neither path loses horizontal pixels
     * or reads/decodes video on the CPU; the existing parity math is shared.
     */
    private void prepareOriginalField(int slot,int field) {
        if("motion".equals(algorithm)) {drawImage(field,true,preparedFbos[slot],width,height);return;}
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,preparedFbos[slot]);GLES20.glViewport(0,0,width,height/2);
        GLES20.glUseProgram(extractProgram);bindGeometry(extractProgram,identity);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(extractProgram,"uCurrent"),0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(extractProgram,"uTextureTransform"),1,false,transform,0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(extractProgram,"uHeight"),height);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(extractProgram,"uParity"),field);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();
    }
    private void queueOriginalFields(Frame frame,long firstDue,long half) {
        allocatePreparedFields();
        if(queuedEpochStartedNs==0)queuedEpochStartedNs=System.nanoTime();
        int first=-1,second=-1;
        for(int slot=0;slot<4;slot++)if(!preparedBusy[slot]) {if(first<0)first=slot;else {second=slot;break;}}
        if(second<0) {fail("gpu_original_field_queue_backpressure",null);return;}
        preparedBusy[first]=preparedBusy[second]=true;
        prepareOriginalField(first,frame.firstField);
        prepareOriginalField(second,1-frame.firstField);
        if(System.nanoTime()-firstDue>half*4 && queuedEpochOutputs==0) {
            // Shader/FBO/window allocation can consume the initial deadlines.
            // Do not replay already-expired startup fields against live audio.
            // Omit only this unpresented prefix, report it, and accept fresh
            // original source deadlines for at most two seconds. Once any
            // queued output is presented, ALL late/backpressure failures retain
            // their strict fallback; this cannot hide a sustained deficit.
            preparedBusy[first]=preparedBusy[second]=false;startupFieldsDiscarded+=2;
            if(System.nanoTime()-queuedEpochStartedNs>2_000_000_000L)fail("gpu_original_field_startup_deadline",null);
            else {state="Pending";reason="awaiting_fresh_original_field";}
            return;
        }
        if(presenter!=null) {
            // Each ready fence is shared with the display context. Its read
            // fence returns through our GL handler before a slot is reused.
            long firstReady=GpuEglFence.create(display);
            long secondReady=GpuEglFence.create(display);
            require(firstReady!=0 && secondReady!=0);GLES20.glFlush();checkGl();
            presenter.submit(new GpuPreparedFieldPresenter.Field(preparedTextures[first],first,frame.firstField,
                    splitEpoch,firstDue,firstReady,frame.fps,false),
                new GpuPreparedFieldPresenter.Field(preparedTextures[second],second,1-frame.firstField,
                    splitEpoch,firstDue+half,secondReady,frame.fps,true));
            return;
        }
        pendingFields.addLast(new PreparedField(first,frame.firstField,firstDue,frame.fps,false));
        pendingFields.addLast(new PreparedField(second,1-frame.firstField,firstDue+half,frame.fps,true));
        if(!fieldTickScheduled && !insideFieldTick) {fieldTickScheduled=true;choreographer.postFrameCallback(fieldTick);}
    }
    private void consumeAndPresent(long frameTimeNs) {
        // Prevent nested render/queue calls from posting a second callback at
        // this same tick. Exactly one display callback owns each next tick.
        fieldTickScheduled=true;insideFieldTick=true;
        try {
            boolean inputDue=pendingDecoderInput && frameTimeNs>=decoderInputNotBeforeNs;
            // The already-retained field owns this display deadline. Acquiring
            // the next decoder image can wait on its producer fence; doing
            // that first makes this field miss its tick despite being ready.
            // Return one prepared slot before acquiring, without shifting
            // any original deadline or allowing two swaps at the same tick.
            long beforeOutput=outputFrames;
            if(presenter==null && !pendingFields.isEmpty())presentQueuedField(frameTimeNs);
            // Surface recreation may invalidate the old output queue, not the
            // newly-notified decoder image needed to recreate that window.
            if(inputDue && !closed.get() && !failed.get()
                    && decoderTransitions.get()==0) {pendingDecoderInput=false;render();}
            // Initial acquisition can populate an empty FIFO at this tick.
            // Only that no-swap case may use the remaining display budget.
            // The split path's tick owns input acquisition ONLY. Calling the
            // old window presenter here sees no input-thread window and would
            // clear newly submitted shared fields as though the display were
            // absent. Its independent presenter owns all final display ticks.
            if(presenter==null && outputFrames==beforeOutput)presentQueuedField(frameTimeNs);
        } finally {insideFieldTick=false;}
        fieldTickScheduled=false;
        if(!closed.get() && !failed.get() && (pendingDecoderInput || !pendingFields.isEmpty())) {
            fieldTickScheduled=true;choreographer.postFrameCallback(fieldTick);
        }
    }
    private void presentQueuedField(long frameTimeNs) {
        if(closed.get() || failed.get() || !playing || decoderTransitions.get()>0
                || window==EGL14.EGL_NO_SURFACE) {clearQueuedFields();return;}
        PreparedField next=pendingFields.peekFirst();
        if(next==null)return;
        long lead=GpuDeinterlacePolicy.releaseLeadUs(next.fps)*1000;
        if(frameTimeNs-next.deadlineNs>lead*4) {
            if(queuedEpochOutputs==0 && System.nanoTime()-queuedEpochStartedNs<=2_000_000_000L) {
                startupFieldsDiscarded+=pendingFields.size();pendingFields.clear();
                java.util.Arrays.fill(preparedBusy,false);
                state="Pending";reason="awaiting_fresh_original_field";return;
            }
            fail("gpu_original_field_queue_late",null);return;
        }
        if(next.deadlineNs<=frameTimeNs+lead) {
            long presentStarted=profilePresentRemaining>0 ? System.nanoTime() : 0;
            pendingFields.removeFirst();makeCurrent(window);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,targetWidth,targetHeight);
            int program="motion".equals(algorithm) ? preparedProgram : presentProgram;
            GLES20.glUseProgram(program);bindGeometry(program,identity);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,preparedTextures[next.slot]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
            if(program==presentProgram) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uHeight"),height);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uParity"),next.field);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);checkGl();
            require(EGLExt.eglPresentationTimeANDROID(display,window,Math.max(frameTimeNs,next.deadlineNs)));
            require(EGL14.eglSwapBuffers(display,window));
            if(presentStarted!=0) {
                long duration=System.nanoTime()-presentStarted;profilePresentNs+=duration;
                profileMaxPresentNs=Math.max(profileMaxPresentNs,duration);profilePresents++;profilePresentRemaining--;
            }
            preparedBusy[next.slot]=false;queuedEpochOutputs++;outputFrames++;filteredFrames++;if(next.second)secondFields++;
            state="Active";reason="original_field_queue";
            if(budget.belowBudget(System.nanoTime(),outputFrames,next.fps*2)) {fail("gpu_presentation_budget",null);return;}
        }
    }

    private static final class PreparedField {
        final int slot,field;final long deadlineNs;final float fps;final boolean second;
        PreparedField(int slot,int field,long deadlineNs,float fps,boolean second) {
            this.slot=slot;this.field=field;this.deadlineNs=deadlineNs;this.fps=fps;this.second=second;
        }
    }

    private void bindGeometry(int program, float[] matrix) {
        int pos=GLES20.glGetAttribLocation(program,"aPosition"),uv=GLES20.glGetAttribLocation(program,"aUv");
        positions.position(0); uvs.position(0);
        GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,positions);
        GLES20.glEnableVertexAttribArray(uv); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,uvs);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,matrix,0);
    }
    private void uniform(String name,int value) { GLES20.glUniform1i(GLES20.glGetUniformLocation(filterProgram,name),value); }
    private void scalar(String name,float value) { GLES20.glUniform1f(GLES20.glGetUniformLocation(filterProgram,name),value); }
    private void makeCurrent(EGLSurface surface) {
        if(currentSurface==surface)return;
        require(EGL14.eglMakeCurrent(display,surface,surface,eglContext));currentSurface=surface;
    }
    private void destroyWindow() {
        if(presenter!=null) {presenter.close();presenter=null;}
        if(window!=EGL14.EGL_NO_SURFACE) {
            makeCurrent(pbuffer);
            require(EGL14.eglDestroySurface(display,window));
            window=EGL14.EGL_NO_SURFACE;
        }
    }
    private void guarded(Runnable work) {
        if (closed.get() || failed.get()) return;
        try { work.run(); } catch (RuntimeException e) { fail(e); }
    }
    private void fail(RuntimeException error) {
        fail("gpu_"+error.getClass().getSimpleName(),error);
    }
    private void fail(String failureReason,RuntimeException error) {
        if (!failed.compareAndSet(false,true)) return;
        int pendingAtFailure=presenter==null ? pendingFields.size() : presenter.pendingFields();
        PreparedField oldestAtFailure=pendingFields.peekFirst();
        long oldestDeadline=presenter==null ? oldestAtFailure==null ? 0 : oldestAtFailure.deadlineNs
                : presenter.oldestDeadlineNs();
        long oldestLateUs=oldestDeadline==0 ? 0 : (System.nanoTime()-oldestDeadline)/1000;
        clearQueuedFields();
        boolean nativeBypass=failureReason.startsWith("gpu_native_bypass_");
        state=nativeBypass ? "Bypass" : "Unavailable"; reason=failureReason;
        failureWitness="reason:"+reason+",input:"+inputFrames+",output:"+outputFrames
                +",metadata:"+metadataFrames+",lastWindowFps:"+budget.lastRate()
                +",codedFps:"+codedFps+",swap:"+swapInterval+",metadataStepUs:"+metadataStepUs
                +",surfaceStepNs:"+surfaceStepNs+",queuedFields:"+pendingAtFailure
                +",oldestLateUs:"+oldestLateUs+",display:"+targetWidth+"x"+targetHeight+",profile:"+profileSummary();
        if(!nativeBypass)Log.w(TAG,"GPU failure witness input="+inputFrames+",output="+outputFrames
                +",metadata="+metadataFrames+",codedFps="+codedFps+",swap="+swapInterval
                +",metadataStepUs="+metadataStepUs+",surfaceStepNs="+surfaceStepNs
                +",profile="+profileSummary());
        if(nativeBypass)Log.i(TAG,"GPU filter bypass; restoring native output: "+reason);
        else Log.e(TAG,"GPU output unavailable; owner must detach and restore device output",error);
        // Return the held image and drain producer buffers BEFORE main asks
        // Exo to retarget. Waiting until setVideoSurfaceView returns can itself
        // strand a vendor codec waiting for the consumer during that retarget.
        beginDecoderTransition();
        // The display and decoder input are DIFFERENT BufferQueues. Release
        // our EGL display producer before native MediaCodec connects to the
        // same SurfaceView; retaining it until renderer ack can leave the
        // last GPU picture frozen even while native output counters advance.
        // Keep the input SurfaceTexture/context alive and draining until the
        // real codec detach ack. Ordinary seek/pause transitions do not drop
        // their display window. fail() runs only on this owned GL thread.
        try {destroyWindow();}
        catch(RuntimeException e){Log.w(TAG,"GPU display producer detach",e);}
        main.post(() -> { if (!closed.get() && failure!=null) failure.failed(reason); });
    }
    @Override public void close() {
        if(!closeRequested.compareAndSet(false,true))return;
        if(attachedRenderers.get()>0) {
            beginDecoderTransition();
            Log.d(TAG,"GPU close deferred until actual renderer/surface detach");
            if(attachedRenderers.get()>0)return;
        }
        closeAfterDetach();
    }
    private void closeAfterDetach() {
        if (!closed.compareAndSet(false,true)) return;
        main.post(() -> {
            holder.removeCallback(surfaceCallback);
            if(videoView instanceof PlayerSurfaceView)
                ((PlayerSurfaceView)videoView).restoreAfterGpuBuffers(this);
        });
        gl.post(() -> {
            try {
                synchronized(frames){frames.clear();}
                clearQueuedFields();
                destroyWindow();
                // Context loss must not strand the decoder's Surface, texture
                // consumer or EGL allocations. Delete GL objects only with a
                // valid current context, but release owned handles regardless.
                boolean current = display!=EGL14.EGL_NO_DISPLAY
                        && eglContext!=EGL14.EGL_NO_CONTEXT
                        && pbuffer!=EGL14.EGL_NO_SURFACE
                        && EGL14.eglMakeCurrent(display,pbuffer,pbuffer,eglContext);
                if (current) {
                    if (frameProbe != null) { frameProbe.completed(); frameProbe = null; }
                    releaseHeldTexture();
                    GLES20.glDeleteTextures(2,textures,0); GLES20.glDeleteFramebuffers(2,fbos,0);
                    GLES20.glDeleteTextures(1,new int[]{externalTexture},0);
                    GLES20.glDeleteProgram(copyProgram); GLES20.glDeleteProgram(filterProgram);
                    GLES20.glDeleteProgram(fastProgram);
                    GLES20.glDeleteProgram(extractProgram);GLES20.glDeleteProgram(presentProgram);
                    GLES20.glDeleteTextures(4,preparedTextures,0);GLES20.glDeleteFramebuffers(4,preparedFbos,0);
                    GLES20.glDeleteProgram(preparedProgram);
                }
            } catch(RuntimeException e){Log.w(TAG,"GPU close",e);}
            finally {
                releaseOwnedHandles();
                thread.quitSafely();
            }
        });
    }
    private void releaseOwnedHandles() {
        try { if(input!=null)input.release(); } catch(RuntimeException e){Log.w(TAG,"GPU Surface close",e);}
        finally { input=null; }
        try { if(decoderTexture!=null)decoderTexture.release(); }
        catch(RuntimeException e){Log.w(TAG,"GPU texture close",e);}
        finally { decoderTexture=null; }
        displaySurface=null;
        if(display!=EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
            if(pbuffer!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,pbuffer);
            if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext);
            EGL14.eglTerminate(display);
        }
        window=pbuffer=EGL14.EGL_NO_SURFACE;
        currentSurface=EGL14.EGL_NO_SURFACE;
        eglContext=EGL14.EGL_NO_CONTEXT;display=EGL14.EGL_NO_DISPLAY;
        EGL14.eglReleaseThread();
    }
    private static FloatBuffer buffer(float... values) {
        FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0); return buffer;
    }
    private static void parameters(int target) {
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    }
    private static int program(String fragment) {
        int vertex=shader(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX);
        int frag=shader(GLES20.GL_FRAGMENT_SHADER,fragment),program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,frag); GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(frag);
        if(ok[0]==0){String info=GLES20.glGetProgramInfoLog(program);GLES20.glDeleteProgram(program);throw new IllegalStateException(info);}
        return program;
    }
    private static int shader(int kind,String source) {
        int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String info=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(info);}
        return shader;
    }
    private static void require(boolean value) {if(!value)throw new IllegalStateException("EGL operation failed "+Integer.toHexString(EGL14.eglGetError()));}
    private static void checkGl(){int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GL error "+error);}
    private static final class Frame {
        final long presentationUs,releaseNs,metadataStepUs,sourcePictureUs; final int width,height,firstField;
        long codecReleaseNs;
        final float fps,speed;final boolean interlaced,duplicatedWoven;final int sourceFieldPhase;
        final String reason,nativeBypassReason;
        Frame(long pts,long release,long stepUs,int w,int h,float fps,float speed,boolean interlaced,int first,
                boolean duplicatedWoven,int sourceFieldPhase,long sourcePictureUs,String reason,String nativeBypassReason){
            presentationUs=pts;releaseNs=codecReleaseNs=release;metadataStepUs=stepUs;width=w;height=h;this.fps=fps;this.speed=speed;
            this.interlaced=interlaced;firstField=first;this.reason=reason;this.nativeBypassReason=nativeBypassReason;
            this.duplicatedWoven=duplicatedWoven;this.sourceFieldPhase=sourceFieldPhase;
            this.sourcePictureUs=sourcePictureUs;
        }
    }
}
