package opensagetv.vibe.miniclient.android.video.media3;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot local decoder-output fault; unavailable to non-debug applications. */
public final class NativeDvdCodecStartupFault
{
    private static final AtomicBoolean armed = new AtomicBoolean();
    private NativeDvdCodecStartupFault() { }

    public static boolean arm(Context context, boolean enabled)
    {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            return false;
        armed.set(enabled);
        return true;
    }

    static boolean consume(boolean debug, String mime, String codecName)
    {
        return debug && NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(true, mime, codecName)
                && armed.getAndSet(false);
    }
}
