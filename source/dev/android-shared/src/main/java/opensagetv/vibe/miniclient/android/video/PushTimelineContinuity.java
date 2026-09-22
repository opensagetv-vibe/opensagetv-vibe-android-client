package opensagetv.vibe.miniclient.android.video;

/**
 * Preserves a trustworthy SageTV media time across an ordinary Push FLUSH.
 * Before the replacement mux timestamp arrives it retains the last proven
 * backend time; once the replacement anchor arrives it reports that anchor
 * immediately. Returning zero or retaining the old time too long makes a rapid
 * second seek or Commercial Skip calculate from the wrong location.
 *
 * <p>This policy deliberately excludes initial playback, Pull/SMB playback,
 * and DVD Push. Those paths either have no established Push timestamp or own
 * different timeline semantics.</p>
 */
public final class PushTimelineContinuity
{
    private PushTimelineContinuity() { }

    public static long pendingMediaTime(boolean pushMode, boolean dvdPush,
                                        long replacementServerAnchorMs,
                                        boolean hasStableMediaTime,
                                        long lastMediaTime)
    {
        if (!pushMode || dvdPush)
            return 0L;

        // The first detailed PUSHBUFFER after a seek contains SageTV's new
        // absolute mux-time anchor. Use it immediately, even while the decoder
        // is still preparing. Continuing to report the pre-seek time here can
        // make a second quick seek or Commercial Skip calculate from the old
        // position. A lower replacement anchor is valid for an intentional
        // backward seek, so this must not be a monotonic clamp.
        if (replacementServerAnchorMs > 0L)
            return replacementServerAnchorMs;

        if (!hasStableMediaTime || lastMediaTime <= 0L)
            return 0L;
        return lastMediaTime;
    }
}
