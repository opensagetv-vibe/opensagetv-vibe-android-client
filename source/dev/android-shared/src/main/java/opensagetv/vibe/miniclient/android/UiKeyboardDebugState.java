package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import java.lang.ref.WeakReference;

/**
 * Bounded debug observation/control state for the current MiniClient IME.
 * Production keyboard rendering remains owned by the Activity lifecycle.
 */
final class UiKeyboardDebugState
{
    private static volatile boolean requested;
    private static volatile boolean suppressed;
    private static volatile WeakReference<Activity> resumedActivity =
            new WeakReference<Activity>(null);

    private UiKeyboardDebugState() { }

    static void resumed(Activity activity)
    {
        resumedActivity = new WeakReference<Activity>(activity);
    }

    static void paused(Activity activity)
    {
        if (resumedActivity.get() == activity)
            resumedActivity = new WeakReference<Activity>(null);
        requested = false;
    }

    static int visibility()
    {
        Activity activity = resumedActivity.get();
        if (activity == null || activity.getWindow() == null)
            return -1;
        try
        {
            View decor = activity.getWindow().getDecorView();
            if (decor == null)
                return -1;
            if (Build.VERSION.SDK_INT >= 30)
            {
                WindowInsets insets = decor.getRootWindowInsets();
                if (insets != null)
                    return insets.isVisible(WindowInsets.Type.ime()) ? 1 : 0;
            }
            View root = decor.getRootView();
            int rootHeight = root == null ? 0 : root.getHeight();
            if (rootHeight <= 0)
                return -1;
            Rect visible = new Rect();
            decor.getWindowVisibleDisplayFrame(visible);
            int obscured = Math.max(0, rootHeight - visible.bottom);
            int threshold = Math.max(100, rootHeight * 15 / 100);
            return obscured > threshold ? 1 : 0;
        }
        catch (Throwable ignored)
        {
            return -1;
        }
    }

    static Activity activity()
    {
        return resumedActivity.get();
    }

    static boolean requested()
    {
        return requested;
    }

    static void requested(boolean value)
    {
        requested = value;
    }

    static boolean setSuppressed(boolean value)
    {
        suppressed = value;
        if (value)
        {
            requested = false;
            hide();
        }
        return suppressed;
    }

    static boolean suppressed()
    {
        return suppressed;
    }

    static boolean hide()
    {
        Activity activity = resumedActivity.get();
        if (activity == null || activity.getWindow() == null)
            return false;
        try
        {
            View target = activity.getCurrentFocus();
            if (target == null)
                target = activity.getWindow().getDecorView();
            if (target == null || target.getWindowToken() == null)
                return false;
            InputMethodManager input = (InputMethodManager) activity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input == null)
                return false;
            requested = false;
            return input.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
}
