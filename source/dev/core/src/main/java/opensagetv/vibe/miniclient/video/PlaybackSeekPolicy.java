package opensagetv.vibe.miniclient.video;

/** Shared seek bounds for completed recordings and growing/timeshifted media. */
public final class PlaybackSeekPolicy
{
    // MPEG-TS duration can end inside the final GOP. A half-second clamp still
    // landed after the last independently decodable picture on the commissioned
    // Fire TV and caused repeated Exo read timeouts. Five seconds retains at
    // least one ordinary broadcast GOP while remaining a small end-of-file
    // guard relative to a recording.
    public static final long COMPLETED_MEDIA_TAIL_MARGIN_MS = 5000;
    private PlaybackSeekPolicy()
    {
    }

    /**
     * Keeps seeks away from a transport stream's unsafe exact end. Container
     * duration can extend slightly beyond the final playable sample.
     */
    public static long clamp(long requestedPositionMs, long durationMs, boolean growingMedia)
    {
        // A duration exposed while seeking a growing TS is only a point-in-time
        // file-size/PCR snapshot. SageTV owns the live timeline and may request
        // a newer position than that stale snapshot on the next command.
        if (requestedPositionMs < 0 || durationMs <= 0 || growingMedia)
        {
            return requestedPositionMs;
        }

        long latestSafePositionMs = Math.max(0,
                durationMs - COMPLETED_MEDIA_TAIL_MARGIN_MS);
        return Math.min(requestedPositionMs, latestSafePositionMs);
    }

}
