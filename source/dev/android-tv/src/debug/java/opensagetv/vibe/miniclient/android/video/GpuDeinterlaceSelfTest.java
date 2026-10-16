package opensagetv.vibe.miniclient.android.video;

import android.opengl.*;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import java.nio.*;

/** One-shot DEBUG-only offscreen pixel test, never playback readback. */
public final class GpuDeinterlaceSelfTest {
    private GpuDeinterlaceSelfTest() { }
    public static String run() {
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext context=EGL14.EGL_NO_CONTEXT;
        EGLSurface surface=EGL14.EGL_NO_SURFACE;
        int[] versions=new int[2],counts=new int[1]; EGLConfig[] config=new EGLConfig[1];
        try {
            require(EGL14.eglInitialize(display,versions,0,versions,1));
            require(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE},0,config,0,1,counts,0));
            context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,8,EGL14.EGL_HEIGHT,8,EGL14.EGL_NONE},0);
            require(EGL14.eglMakeCurrent(display,surface,surface,context));
            int program=GLES20.glCreateProgram(),vertex=compile(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX),
                fragment=compile(GLES20.GL_FRAGMENT_SHADER,GpuDeinterlaceShaders.FILTER);
            GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
            int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
            require(linked[0]!=0);GLES20.glUseProgram(program);
            attribute(program,"aPosition",new float[]{-1,-1,1,-1,-1,1,1,1});
            attribute(program,"aUv",new float[]{0,0,1,0,0,1,1,1});
            float[] identity=new float[16];Matrix.setIdentityM(identity,0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,identity,0);
            int[] textures=new int[2];GLES20.glGenTextures(2,textures,0);
            int checks=0;
            for(int field=0;field<2;field++) for(int enabled=0;enabled<2;enabled++) for(int motion=0;motion<2;motion++) {
                int[] rows={32,210,70,230,50,190,90,250};
                for(int texture=0;texture<2;texture++) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0+texture);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[texture]);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
                    ByteBuffer bytes=ByteBuffer.allocateDirect(256);
                    for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                        int value=texture==0?rows[y]:255-rows[y];
                        bytes.put((byte)value).put((byte)value).put((byte)value).put((byte)255);
                    }
                    bytes.position(0);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,8,8,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes);
                }
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uPrevious"),1);
                scalar(program,"uHeight",8);scalar(program,"uParity",field);scalar(program,"uEnabled",enabled);
                scalar(program,"uMotion",motion);scalar(program,"uHasPrevious",1);
                GLES20.glViewport(0,0,8,8);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                ByteBuffer pixels=ByteBuffer.allocateDirect(256);
                GLES20.glReadPixels(0,0,8,8,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                require(GLES20.glGetError()==GLES20.GL_NO_ERROR);
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int expected=rows[y];
                    if(enabled==1 && (7-y)%2!=field) {
                        int below=y==0?1:y-1,above=y==7?6:y+1;
                        expected=(rows[below]+rows[above])/2;
                    }
                    for(int rgb=0;rgb<3;rgb++) require(Math.abs((pixels.get((y*8+x)*4+rgb)&255)-expected)<=1);
                    checks++;
                }
            }
            GLES20.glDeleteTextures(2,textures,0);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);GLES20.glDeleteProgram(program);
            int externalChecks=testExternal(identity);
            return "op=gpu_filter_self_test;passed=true;pixelChecks="+checks+";externalPixelChecks="+externalChecks
                    +";topBottom=true;progressiveBypass=true;movingFields=true;textureMatrix=true;fieldInterpolation=true;playbackReadback=false";
        } finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        }
    }
    /** Exercise the actual OES sampler and producer transform, not a 2D proxy. */
    private static int testExternal(float[] identity) {
        int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        SurfaceTexture producerTexture=new SurfaceTexture(texture[0]);
        producerTexture.setDefaultBufferSize(8,8);
        Surface producer=new Surface(producerTexture);
        int program=0,vertex=0,fragment=0;
        try {
            int[] rows={32,210,70,230,50,190,90,250};
            Canvas canvas=producer.lockCanvas(null);
            try {
                Paint paint=new Paint();
                for(int y=0;y<8;y++) {
                    int gray=rows[7-y];paint.setColor(android.graphics.Color.rgb(gray,gray,gray));
                    canvas.drawRect(0,y,8,y+1,paint);
                }
            } finally {producer.unlockCanvasAndPost(canvas);}
            producerTexture.updateTexImage();
            float[] transform=new float[16];producerTexture.getTransformMatrix(transform);
            program=GLES20.glCreateProgram();
            vertex=compile(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX);
            fragment=compile(GLES20.GL_FRAGMENT_SHADER,GpuDeinterlaceShaders.FAST_EXTERNAL);
            GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
            int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);require(linked[0]!=0);
            GLES20.glUseProgram(program);
            attribute(program,"aPosition",new float[]{-1,-1,1,-1,-1,1,1,1});
            attribute(program,"aUv",new float[]{0,0,1,0,0,1,1,1});
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,identity,0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTextureTransform"),1,false,transform,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrent"),0);
            int checks=0;
            for(int field=0;field<2;field++)for(int enabled=0;enabled<2;enabled++) {
                scalar(program,"uHeight",8);scalar(program,"uParity",field);scalar(program,"uEnabled",enabled);
                GLES20.glViewport(0,0,8,8);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                ByteBuffer pixels=ByteBuffer.allocateDirect(256);
                GLES20.glReadPixels(0,0,8,8,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                require(GLES20.glGetError()==GLES20.GL_NO_ERROR);
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int expected=rows[y];
                    if(enabled==1 && (7-y)%2!=field) {
                        int below=y==0?1:y-1,above=y==7?6:y+1;
                        expected=(rows[below]+rows[above])/2;
                    }
                    for(int rgb=0;rgb<3;rgb++)require(Math.abs((pixels.get((y*8+x)*4+rgb)&255)-expected)<=1);
                    checks++;
                }
            }
            // Qualify the cheaper two-pass path against the very same spatial
            // oracle, including retained rows and both boundary rows.
            int extract=linked(GpuDeinterlaceShaders.EXTRACT_FIELD_EXTERNAL);
            int present=linked(GpuDeinterlaceShaders.PRESENT_FIELD);
            int[] half=new int[1],fbo=new int[1];GLES20.glGenTextures(1,half,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,half[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,8,4,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glGenFramebuffers(1,fbo,0);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,half[0],0);
            require(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE);
            try {
                for(int field=0;field<2;field++) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo[0]);GLES20.glViewport(0,0,8,4);
                    GLES20.glUseProgram(extract);geometry(extract,identity);
                    GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(extract,"uTextureTransform"),1,false,transform,0);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
                    GLES20.glUniform1i(GLES20.glGetUniformLocation(extract,"uCurrent"),0);
                    scalar(extract,"uHeight",8);scalar(extract,"uParity",field);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,8,8);
                    GLES20.glUseProgram(present);geometry(present,identity);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,half[0]);
                    GLES20.glUniform1i(GLES20.glGetUniformLocation(present,"uCurrent"),0);
                    scalar(present,"uHeight",8);scalar(present,"uParity",field);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                    ByteBuffer pixels=ByteBuffer.allocateDirect(256);
                    GLES20.glReadPixels(0,0,8,8,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                    require(GLES20.glGetError()==GLES20.GL_NO_ERROR);
                    for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                        int expected=rows[y];
                        if((7-y)%2!=field) {
                            int below=y==0?1:y-1,above=y==7?6:y+1;
                            expected=(rows[below]+rows[above])/2;
                        }
                        for(int rgb=0;rgb<3;rgb++)require(Math.abs((pixels.get((y*8+x)*4+rgb)&255)-expected)<=1);
                        checks++;
                    }
                }
            } finally {
                GLES20.glDeleteTextures(1,half,0);GLES20.glDeleteFramebuffers(1,fbo,0);
                GLES20.glDeleteProgram(extract);GLES20.glDeleteProgram(present);
            }
            return checks;
        } finally {
            producer.release();producerTexture.release();
            GLES20.glDeleteTextures(1,texture,0);
            GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);GLES20.glDeleteProgram(program);
        }
    }
    private static int linked(String source) {
        int program=GLES20.glCreateProgram(),vertex=compile(GLES20.GL_VERTEX_SHADER,GpuDeinterlaceShaders.VERTEX),
                fragment=compile(GLES20.GL_FRAGMENT_SHADER,source);
        GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);require(ok[0]!=0);return program;
    }
    private static void geometry(int program,float[] identity) {
        attribute(program,"aPosition",new float[]{-1,-1,1,-1,-1,1,1,1});
        attribute(program,"aUv",new float[]{0,0,1,0,0,1,1,1});
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,identity,0);
    }
    private static int compile(int type,String source) {
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));return shader;
    }
    private static void attribute(int program,String key,float[] values) {
        FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(values).position(0);
        int location=GLES20.glGetAttribLocation(program,key);GLES20.glEnableVertexAttribArray(location);
        GLES20.glVertexAttribPointer(location,2,GLES20.GL_FLOAT,false,0,buffer);
    }
    private static void scalar(int program,String key,float value) {GLES20.glUniform1f(GLES20.glGetUniformLocation(program,key),value);}
    private static void require(boolean condition) {if(!condition)throw new IllegalStateException("GPU shader pixel gate failed");}
}
