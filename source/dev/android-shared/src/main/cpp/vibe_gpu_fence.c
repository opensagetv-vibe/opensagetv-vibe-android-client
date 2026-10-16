/* Independently written standard EGL KHR bridge. No codec/vendor code.
 * EGL wait inserts a GPU command dependency; never a CPU wait or readback.
 * All functions require the caller's existing, matching current display.
 */
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <stdint.h>
#include <string.h>
#include <pthread.h>

static PFNEGLCREATESYNCKHRPROC create_sync;
static PFNEGLWAITSYNCKHRPROC wait_sync;
static PFNEGLDESTROYSYNCKHRPROC destroy_sync;
static pthread_once_t resolved=PTHREAD_ONCE_INIT;
static void resolve(void) {
    create_sync=(PFNEGLCREATESYNCKHRPROC)eglGetProcAddress("eglCreateSyncKHR");
    wait_sync=(PFNEGLWAITSYNCKHRPROC)eglGetProcAddress("eglWaitSyncKHR");
    destroy_sync=(PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
}
static int extension(const char *list,const char *name) {
    if(!list)return 0;
    size_t size=strlen(name);const char *found=list;
    while((found=strstr(found,name))!=NULL) {
        if((found==list || found[-1]==' ') && (found[size]==' ' || found[size]=='\0'))return 1;
        found+=size;
    }
    return 0;
}
static int current(EGLDisplay display) {
    return display!=EGL_NO_DISPLAY && eglGetCurrentDisplay()==display
        && eglGetCurrentContext()!=EGL_NO_CONTEXT;
}
JNIEXPORT jboolean JNICALL
Java_opensagetv_vibe_miniclient_android_video_GpuEglFence_nativeAvailable(JNIEnv *env,jclass type,jlong handle) {
    (void)env;(void)type;pthread_once(&resolved,resolve);
    EGLDisplay display=(EGLDisplay)(uintptr_t)handle;
    const char *extensions=current(display)?eglQueryString(display,EGL_EXTENSIONS):NULL;
    return create_sync && wait_sync && destroy_sync
        && extension(extensions,"EGL_KHR_fence_sync") && extension(extensions,"EGL_KHR_wait_sync")
        && extension((const char *)glGetString(GL_EXTENSIONS),"GL_OES_EGL_sync")
        ? JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jlong JNICALL
Java_opensagetv_vibe_miniclient_android_video_GpuEglFence_nativeCreate(JNIEnv *env,jclass type,jlong handle) {
    (void)env;(void)type;pthread_once(&resolved,resolve);
    EGLDisplay display=(EGLDisplay)(uintptr_t)handle;
    const EGLint attributes[]={EGL_NONE};
    return current(display) && create_sync
        ? (jlong)(uintptr_t)create_sync(display,EGL_SYNC_FENCE_KHR,attributes):0;
}
JNIEXPORT jboolean JNICALL
Java_opensagetv_vibe_miniclient_android_video_GpuEglFence_nativeWait(JNIEnv *env,jclass type,jlong handle,jlong sync) {
    (void)env;(void)type;pthread_once(&resolved,resolve);
    EGLDisplay display=(EGLDisplay)(uintptr_t)handle;
    return current(display) && sync!=0 && wait_sync
        && wait_sync(display,(EGLSyncKHR)(uintptr_t)sync,0)==EGL_TRUE?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_opensagetv_vibe_miniclient_android_video_GpuEglFence_nativeDestroy(JNIEnv *env,jclass type,jlong handle,jlong sync) {
    (void)env;(void)type;pthread_once(&resolved,resolve);
    EGLDisplay display=(EGLDisplay)(uintptr_t)handle;
    return current(display) && sync!=0 && destroy_sync
        && destroy_sync(display,(EGLSyncKHR)(uintptr_t)sync)==EGL_TRUE?JNI_TRUE:JNI_FALSE;
}
