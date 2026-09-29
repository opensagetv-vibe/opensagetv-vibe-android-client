package opensagetv.vibe.miniclient.android.video.media3;

/**
 * Selects the one Media3 stuck-player detector that is invalid for a SageTV
 * growing Pull source.
 *
 * <p>Media3's playing-not-ending detector compares the current position with
 * the period duration discovered when a progressive source was first opened.
 * A SageTV recording-in-progress deliberately keeps the Pull datasource open
 * and supplies appended MPEG-TS bytes, but Media3's original period duration
 * can remain at the earlier live edge. Playback is healthy and advancing when
 * it crosses that stale duration, so the default 60-second detector would
 * incorrectly raise {@code ERROR_CODE_TIMEOUT}. Other stuck-player detectors
 * still protect genuine buffering, no-progress, and suppression failures.</p>
 */
final class Media3GrowingPlaybackTimeoutPolicy
{
    /** Largest positive value accepted by Media3's builder (about 24.8 days). */
    static final int PLAYING_NOT_ENDING_TIMEOUT_MS = Integer.MAX_VALUE;

    private Media3GrowingPlaybackTimeoutPolicy() { }

    static boolean shouldRelaxPlayingNotEnding(boolean pushMode, boolean httpSource,
                                                boolean smbMode,
                                                boolean growthProven)
    {
        return !pushMode && !httpSource && !smbMode && growthProven;
    }
}
