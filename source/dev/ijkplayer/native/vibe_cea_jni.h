/* LGPL-2.1-or-later Vibe native adaptation, pinned k0.8.8 Java member contract.
 * Invoke the EXISTING listener without the static gateway's per-packet INFO
 * logging. These are our pinned IJK members, not Android hidden APIs. All
 * normal HTTP/control invoke events retain their original gateway behaviour. */
static void vibe_deliver_raw_caption(JNIEnv *env, jobject weak, jobject bundle)
{
    jobject owner = NULL, listener = NULL;
    jclass weak_class = NULL, owner_class = NULL, listener_class = NULL;
    jmethodID get, invoke;
    jfieldID field;
    weak_class = (*env)->GetObjectClass(env, weak);
    if (!weak_class || (*env)->ExceptionCheck(env)) goto done;
    get = (*env)->GetMethodID(env, weak_class, "get", "()Ljava/lang/Object;");
    if (!get || (*env)->ExceptionCheck(env)) goto done;
    owner = (*env)->CallObjectMethod(env, weak, get);
    if (!owner || (*env)->ExceptionCheck(env)) goto done;
    owner_class = (*env)->GetObjectClass(env, owner);
    if (!owner_class || (*env)->ExceptionCheck(env)) goto done;
    field = (*env)->GetFieldID(env, owner_class, "mOnNativeInvokeListener",
            "Ltv/danmaku/ijk/media/player/IjkMediaPlayer$OnNativeInvokeListener;");
    if (!field || (*env)->ExceptionCheck(env)) goto done;
    listener = (*env)->GetObjectField(env, owner, field);
    if (!listener || (*env)->ExceptionCheck(env)) goto done;
    listener_class = (*env)->GetObjectClass(env, listener);
    if (!listener_class || (*env)->ExceptionCheck(env)) goto done;
    invoke = (*env)->GetMethodID(env, listener_class, "onNativeInvoke", "(ILandroid/os/Bundle;)Z");
    if (invoke && !(*env)->ExceptionCheck(env))
        (*env)->CallBooleanMethod(env, listener, invoke, VIBE_CEA_MESSAGE, bundle);
done:
    J4A_ExceptionCheck__catchAll(env);
    if (listener_class) (*env)->DeleteLocalRef(env, listener_class);
    if (listener) (*env)->DeleteLocalRef(env, listener);
    if (owner_class) (*env)->DeleteLocalRef(env, owner_class);
    if (owner) (*env)->DeleteLocalRef(env, owner);
    if (weak_class) (*env)->DeleteLocalRef(env, weak_class);
}
