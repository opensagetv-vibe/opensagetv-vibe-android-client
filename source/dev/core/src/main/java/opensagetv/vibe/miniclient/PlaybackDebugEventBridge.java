package opensagetv.vibe.miniclient;

import java.lang.reflect.Method;

/**
 * Optional core-to-debug bridge for exact SageTV protocol event traps.
 *
 * The Android debug implementation is loaded reflectively when present. Desktop/release
 * runtimes do not contain it, so this becomes a cached no-op and cannot change playback.
 */
final class PlaybackDebugEventBridge
{
    private static final String TRAP_CLASS = "opensagetv.vibe.miniclient.android.tv.debug.PlaybackEventTraps";
    private static volatile boolean lookupComplete;
    private static volatile Method recordAsyncMethod;

    private PlaybackDebugEventBridge()
    {
    }

    static void recordAsync(String event, MiniPlayerPlugin player)
    {
        Method method = resolve();
        if (method == null)
            return;
        try
        {
            method.invoke(null, event, player);
        }
        catch (Throwable ignored)
        {
            // Debug evidence must never interfere with the SageTV media-command thread.
        }
    }

    private static Method resolve()
    {
        if (lookupComplete)
            return recordAsyncMethod;
        synchronized (PlaybackDebugEventBridge.class)
        {
            if (lookupComplete)
                return recordAsyncMethod;
            try
            {
                Class<?> trapClass = Class.forName(TRAP_CLASS);
                recordAsyncMethod = trapClass.getMethod("recordAsync", String.class, MiniPlayerPlugin.class);
            }
            catch (Throwable ignored)
            {
                recordAsyncMethod = null;
            }
            lookupComplete = true;
            return recordAsyncMethod;
        }
    }
}
