package opensagetv.vibe.miniclient.android;

import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore.Keys;

/** Immutable Activity-session policy loaded once when the MiniClient UI starts. */
final class UiSessionConfiguration
{
    final boolean keepInBackground;
    final boolean resumePlayback;
    final long timeoutMs;

    private UiSessionConfiguration(boolean keepInBackground,
            boolean resumePlayback, long timeoutMs)
    {
        this.keepInBackground = keepInBackground;
        this.resumePlayback = resumePlayback;
        this.timeoutMs = timeoutMs;
    }

    static UiSessionConfiguration load(PrefStore preferences)
    {
        if (!preferences.contains(Keys.keep_session_in_background))
        {
            boolean legacyDestroy = preferences.getBoolean(Keys.app_destroy_on_pause, true);
            preferences.setBoolean(Keys.keep_session_in_background, !legacyDestroy);
        }
        boolean keep = preferences.getBoolean(Keys.keep_session_in_background, false);
        boolean resume = preferences.getBoolean(Keys.resume_background_playback, true);
        int timeoutSeconds = preferences.getInt(Keys.background_session_timeout_seconds, 300);
        return new UiSessionConfiguration(keep, resume,
                Math.max(0L, timeoutSeconds) * 1000L);
    }
}
