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
    public static final long GROWING_MEDIA_LIVE_EDGE_MARGIN_MS = 2000;

    private PlaybackSeekPolicy()
    {
    }

    /**
     * Keeps seeks away from a transport stream's unsafe exact end. Container
     * duration can extend slightly beyond the final playable sample.
     */
    public static long clamp(long requestedPositionMs, long durationMs, boolean growingMedia)
    {
        if (requestedPositionMs < 0 || durationMs <= 0)
        {
            return requestedPositionMs;
        }

        long marginMs = growingMedia
                ? GROWING_MEDIA_LIVE_EDGE_MARGIN_MS
                : COMPLETED_MEDIA_TAIL_MARGIN_MS;
        long latestSafePositionMs = Math.max(0, durationMs - marginMs);
        return Math.min(requestedPositionMs, latestSafePositionMs);
    }

    /**
     * Clamp using the decoder's buffered edge when a growing source has no
     * stable duration. Completed media must not infer an end from buffering.
     */
    public static long clamp(long requestedPositionMs, long durationMs,
                             long bufferedPositionMs, boolean growingMedia)
    {
        long effectiveDurationMs = durationMs;
        if (effectiveDurationMs <= 0 && growingMedia && bufferedPositionMs > 0)
        {
            effectiveDurationMs = bufferedPositionMs;
        }
        return clamp(requestedPositionMs, effectiveDurationMs, growingMedia);
    }
}
