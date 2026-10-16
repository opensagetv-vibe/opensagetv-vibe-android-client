package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.os.Build;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifecycle-bound bridge between Android 13 predictive Back and the existing
 * SageTV Back-command path.
 *
 * <p>The playback activities cannot inherit AndroidX {@code ComponentActivity}
 * because one is a libGDX {@code AndroidApplication}. Keep the API-33 types in
 * a version-isolated nested class so API 23-32 continue to load the outer
 * class safely. The callback returns whether SageTV consumed Back; startup and
 * disconnected states fall through to finishing the Android activity.</p>
 */
public final class PredictiveBackController
{
    public interface Callback
    {
        boolean onBackInvoked();
    }

    private static final AtomicLong INVOCATION_COUNT = new AtomicLong();
    private static volatile String lastActivity = "";
    private static volatile boolean lastHandled;

    private final Activity activity;
    private final Callback callback;
    private Object platformCallback;

    public PredictiveBackController(Activity activity, Callback callback)
    {
        this.activity = activity;
        this.callback = callback;
    }

    /** Register once from Activity.onCreate(). No-op below API 33. */
    public void register()
    {
        if (Build.VERSION.SDK_INT >= 33 && platformCallback == null)
            platformCallback = Api33.register(activity, this);
    }

    /** Unregister before the Activity is destroyed or replaced. */
    public void unregister()
    {
        if (Build.VERSION.SDK_INT >= 33 && platformCallback != null)
        {
            Api33.unregister(activity, platformCallback);
            platformCallback = null;
        }
    }

    /** Route Activity.onBackPressed() on API 23-32 through the same handler. */
    public void dispatchLegacy()
    {
        dispatch();
    }

    private void dispatch()
    {
        boolean handled = callback != null && callback.onBackInvoked();
        lastActivity = activity.getClass().getName();
        lastHandled = handled;
        INVOCATION_COUNT.incrementAndGet();
        android.util.Log.i("VibePredictiveBack", "Back invoked activity="
                + lastActivity + " handled=" + handled);
        if (!handled && !activity.isFinishing())
            activity.finishAfterTransition();
    }

    public static long invocationCount()
    {
        return INVOCATION_COUNT.get();
    }

    public static String lastActivity()
    {
        return lastActivity;
    }

    public static boolean lastHandled()
    {
        return lastHandled;
    }

    @android.annotation.TargetApi(33)
    private static final class Api33
    {
        private Api33()
        {
        }

        static Object register(Activity activity, final PredictiveBackController owner)
        {
            android.window.OnBackInvokedCallback callback = owner::dispatch;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        static void unregister(Activity activity, Object callback)
        {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) callback);
        }
    }
}
