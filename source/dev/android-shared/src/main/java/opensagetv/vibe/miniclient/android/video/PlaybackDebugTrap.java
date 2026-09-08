package opensagetv.vibe.miniclient.android.video;

import java.lang.reflect.Method;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;

/**
 * Optional bridge into the debug APK's exact-event playback trap recorder.
 *
 * The implementation lives only in android-tv/src/debug. Release builds therefore
 * keep the normal player code path and this bridge becomes a cached no-op after the
 * first failed lookup. Debug builds use reflection so android-shared does not have a
 * compile-time dependency on a debug source set.
 */
public final class PlaybackDebugTrap
{
    private static final String TRAP_CLASS = "opensagetv.vibe.miniclient.android.tv.debug.PlaybackEventTraps";
    private static volatile boolean lookupComplete;
    private static volatile Method recordMethod;
    private static volatile Method recordDetailedMethod;

    private PlaybackDebugTrap()
    {
    }

    public static void record(String event, MiniPlayerPlugin player)
    {
        Method method = resolveRecordMethod();
        if (method == null)
            return;
        try
        {
            method.invoke(null, event, player);
        }
        catch (Throwable ignored)
        {
            // Diagnostics must never alter normal playback behavior.
        }
    }

    public static void recordDetailed(String event, MiniPlayerPlugin player, String detail)
    {
        resolveRecordMethod();
        Method method = recordDetailedMethod;
        if (method == null)
        {
            record(event, player);
            return;
        }
        try
        {
            method.invoke(null, event, player, detail);
        }
        catch (Throwable ignored)
        {
            // Diagnostics must never alter normal playback behavior.
        }
    }

    private static Method resolveRecordMethod()
    {
        if (lookupComplete)
            return recordMethod;
        synchronized (PlaybackDebugTrap.class)
        {
            if (lookupComplete)
                return recordMethod;
            try
            {
                Class<?> trapClass = Class.forName(TRAP_CLASS);
                recordMethod = trapClass.getMethod("record", String.class, MiniPlayerPlugin.class);
                recordDetailedMethod = trapClass.getMethod("recordDetailed",
                        String.class, MiniPlayerPlugin.class, String.class);
            }
            catch (Throwable ignored)
            {
                recordMethod = null;
                recordDetailedMethod = null;
            }
            lookupComplete = true;
            return recordMethod;
        }
    }
}
