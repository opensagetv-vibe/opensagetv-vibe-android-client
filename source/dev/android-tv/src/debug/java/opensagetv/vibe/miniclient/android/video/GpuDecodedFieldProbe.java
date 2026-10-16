package opensagetv.vibe.miniclient.android.video;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Finite DEBUG-only 8x4 samples from the generated field-ID fixture.
 * No screenshot/media export, polling, new clock or production readback.
 * Every source field carries an eight-bit counter in broad luma columns at
 * BOTH image edges. Four neighbouring decoded rows reveal whether that output
 * retains alternating field IDs, is already progressive, or is indeterminate.
 * This observes input to our filter; it is NOT proof of display scanout.
 */
public final class GpuDecodedFieldProbe implements GpuDeinterlaceOutput.FrameProbe {
    private static volatile GpuDecodedFieldProbe latest;
    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\nprecision highp float;"
          + "uniform samplerExternalOES uCurrent;uniform mat4 uTextureTransform;"
          + "uniform float uHeight;varying vec2 vUv;"
          + "void main(){vec2 p=vec2((floor(vUv.x*8.)+.5)/8.,"
          + "(16.+floor(vUv.y*4.)+.5)/uHeight);"
          + "gl_FragColor=texture2D(uCurrent,(uTextureTransform*vec4(p,0.,1.)).xy);}";
    private final JSONArray samples = new JSONArray();
    private boolean complete;
    private String error = "";
    private int program, texture, framebuffer;
    private JSONObject lastSample;
    private final FloatBuffer positions = floats(-1,-1,1,-1,-1,1,1,1);
    private final FloatBuffer uvs = floats(0,0,1,0,0,1,1,1);
    private final ByteBuffer pixels = ByteBuffer.allocateDirect(8*4*4);
    public static boolean start(GpuDeinterlaceOutput output) {
        if (output == null) return false;
        GpuDecodedFieldProbe probe = new GpuDecodedFieldProbe();
        boolean accepted = output.probeNextFrames(probe, 64);
        if (accepted) latest = probe;
        return accepted;
    }
    public static String arm() {
        GpuDecodedFieldProbe probe=new GpuDecodedFieldProbe();latest=probe;
        GpuDeinterlaceOutput.armDebugProbe(probe);
        return "op=gpu_field_probe_arm;accepted=true;maxFrames=64;debugFixturePixels=true";
    }
    public static String status() {
        GpuDecodedFieldProbe probe = latest;
        if (probe == null) return "op=gpu_field_probe;started=false";
        synchronized (probe) {
            return "op=gpu_field_probe;started=true;complete=" + probe.complete
                    + ";sampleCount=" + probe.samples.length() + ";error=" + probe.error
                    + ";samples=" + probe.samples.toString();
        }
    }
    @Override public void frame(int external, float[] transform, int width, int height,
            long pts, long stamp, long metadataStepUs) {
        int[] oldFbo = new int[1], oldViewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, oldFbo, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, oldViewport, 0);
        try {
            if (program == 0) initialize();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glViewport(0,0,8,4); GLES20.glUseProgram(program);
            geometry("aPosition",positions); geometry("aUv",uvs);
            float[] identity = new float[16]; Matrix.setIdentityM(identity,0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,identity,0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTextureTransform"),1,false,transform,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uHeight"),height);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            pixels.position(0);
            GLES20.glReadPixels(0,0,8,4,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
            if (GLES20.glGetError()!=GLES20.GL_NO_ERROR) throw new IllegalStateException("probe_gl_error");
            JSONArray rows=new JSONArray(), luma=new JSONArray();
            for(int y=0;y<4;y++) {
                int id=0; boolean certain=true; JSONArray values=new JSONArray();
                for(int x=0;x<8;x++) {
                    int value=pixels.get((y*8+x)*4)&255;
                    values.put(value);
                    if(value>=185) id|=1<<x;
                    else if(value>75) certain=false;
                }
                rows.put(certain ? Integer.valueOf(id) : JSONObject.NULL); luma.put(values);
            }
            JSONObject sample=new JSONObject().put("ptsUs",pts).put("surfaceNs",stamp)
                    .put("metadataStepUs",metadataStepUs).put("width",width).put("height",height)
                    .put("rowIds",rows).put("gray",luma);
            synchronized(this) { if(samples.length()<64) { samples.put(sample); lastSample=sample; } }
        } catch(Exception failure) {
            // A debug measurement failure must not alter playback/fallback.
            synchronized(this) { error=failure.getClass().getSimpleName(); }
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,oldFbo[0]);
            GLES20.glViewport(oldViewport[0],oldViewport[1],oldViewport[2],oldViewport[3]);
        }
    }
    @Override public void presented(int field,boolean filtered,long deadlineNs,int width,int height) {
        try {
            int id=0;boolean certain=true;JSONArray values=new JSONArray();
            for(int bit=0;bit<8;bit++) {
                pixels.position(0);
                GLES20.glReadPixels((int)((bit+.5)*width/8),16,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                int value=pixels.get(0)&255;values.put(value);
                if(value>=185)id|=1<<bit;else if(value>75)certain=false;
            }
            if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("probe_output_error");
            JSONObject output=new JSONObject().put("field",field).put("filtered",filtered)
                    .put("deadlineNs",deadlineNs).put("id",certain ? Integer.valueOf(id) : JSONObject.NULL)
                    .put("gray",values);
            synchronized(this) {
                if(lastSample!=null) {
                    JSONArray outputs=lastSample.optJSONArray("outputs");
                    if(outputs==null) {outputs=new JSONArray();lastSample.put("outputs",outputs);}
                    if(outputs.length()<2)outputs.put(output);
                }
            }
        } catch(Exception failure) { synchronized(this) {error=failure.getClass().getSimpleName();} }
    }
    @Override public void completed() {
        if(texture!=0) GLES20.glDeleteTextures(1,new int[]{texture},0);
        if(framebuffer!=0) GLES20.glDeleteFramebuffers(1,new int[]{framebuffer},0);
        if(program!=0) GLES20.glDeleteProgram(program);
        texture=framebuffer=program=0;
        synchronized(this) { complete=true; }
    }
    private void initialize() {
        int vertex=shader(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX);
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex);
        GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
        int[] ok=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException("probe_link_error");
        int[] made=new int[1]; GLES20.glGenTextures(1,made,0); texture=made[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,8,4,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
        GLES20.glGenFramebuffers(1,made,0); framebuffer=made[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0);
        if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("probe_fbo_error");
    }
    private void geometry(String name,FloatBuffer values) {
        int location=GLES20.glGetAttribLocation(program,name); values.position(0);
        GLES20.glEnableVertexAttribArray(location);
        GLES20.glVertexAttribPointer(location,2,GLES20.GL_FLOAT,false,0,values);
    }
    private static int shader(int type,String source) {
        int shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] ok=new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) { GLES20.glDeleteShader(shader); throw new IllegalStateException("probe_compile_error"); }
        return shader;
    }
    private static FloatBuffer floats(float... values) {
        FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0); return buffer;
    }
}
