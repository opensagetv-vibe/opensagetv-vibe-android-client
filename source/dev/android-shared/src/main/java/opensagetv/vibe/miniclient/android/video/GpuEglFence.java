package opensagetv.vibe.miniclient.android.video;

import android.opengl.EGLDisplay;

/** Standard EGL KHR GPU ordering for GLES2 on API23+, not vendor controls.
 * Android's Java EGLExt does not expose these KHR functions on API25. This
 * small owned JNI bridge preserves the proven GLES2 decoder-image route;
 * unlike a GLES3 input upgrade, it changes neither pixel geometry nor clocks.
 * Unsupported/missing extensions/library fail closed. No CPU waits/readback.
 */
final class GpuEglFence {
    private static final boolean LOADED;
    static {
        boolean loaded;
        try {System.loadLibrary("vibe_gpu_fence");loaded=true;}
        catch(UnsatisfiedLinkError missing) {loaded=false;}
        LOADED=loaded;
    }
    private GpuEglFence() {}
    static boolean available(EGLDisplay display) {
        return LOADED && nativeAvailable(display.getNativeHandle());
    }
    static long create(EGLDisplay display) {
        long sync=LOADED ? nativeCreate(display.getNativeHandle()) : 0;
        if(sync==0)throw new IllegalStateException("EGL GPU fence unavailable");
        return sync;
    }
    static void waitGpu(EGLDisplay display,long sync) {
        if(!LOADED || !nativeWait(display.getNativeHandle(),sync))
            throw new IllegalStateException("EGL GPU wait failed");
    }
    static void destroy(EGLDisplay display,long sync) {
        if(!LOADED || !nativeDestroy(display.getNativeHandle(),sync))
            throw new IllegalStateException("EGL GPU fence release failed");
    }
    private static native boolean nativeAvailable(long display);
    private static native long nativeCreate(long display);
    private static native boolean nativeWait(long display,long sync);
    private static native boolean nativeDestroy(long display,long sync);
}
