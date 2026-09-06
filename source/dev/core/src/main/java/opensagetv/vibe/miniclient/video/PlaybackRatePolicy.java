package opensagetv.vibe.miniclient.video;

import opensagetv.vibe.miniclient.StreamingModePolicy;

/** Pure policy for negotiated native-rate and seek-scan playback. */
public final class PlaybackRatePolicy
{
    public static final float MIN_NATIVE_RATE = 0.5f;
    public static final float MAX_NATIVE_RATE = 2.0f;
    public static final float MIN_SCAN_RATE = 4.0f;
    public static final float MAX_SCAN_RATE = 256.0f;
    // A Pull/SMB seek must be allowed to rebuffer and render before another
    // discontinuity. Sub-second scan ticks permanently starved SMB playback
    // on the physical Fire TV even though every individual seek succeeded.
    public static final long SCAN_INTERVAL_MS = 3000L;

    public enum Mode
    {
        NORMAL,
        NATIVE,
        SEEK_SCAN,
        UNSUPPORTED
    }

    private PlaybackRatePolicy()
    {
    }

    /**
     * Rate control requires random access and a player with a proven Exo
     * timeline. Dynamic/Push/Fixed and GSY System remain on SageTV's existing
     * compatibility behavior.
     */
    public static boolean shouldAdvertise(String streamingMode, String backend,
            String gsyEngine)
    {
        if (!StreamingModePolicy.isForcedPull(streamingMode)) return false;
        if ("media3".equalsIgnoreCase(backend)
                || "exoplayer".equalsIgnoreCase(backend)) return true;
        return "gsyplayer".equalsIgnoreCase(backend)
                && !"system".equalsIgnoreCase(gsyEngine);
    }

    public static Mode classify(float rate)
    {
        if (Float.isNaN(rate) || Float.isInfinite(rate) || rate == 0.0f)
            return Mode.UNSUPPORTED;
        if (rate == 1.0f) return Mode.NORMAL;
        if (rate >= MIN_NATIVE_RATE && rate <= MAX_NATIVE_RATE)
            return Mode.NATIVE;
        float magnitude = Math.abs(rate);
        if (magnitude >= MIN_SCAN_RATE && magnitude <= MAX_SCAN_RATE)
            return Mode.SEEK_SCAN;
        return Mode.UNSUPPORTED;
    }

    /**
     * The decoder advances at 1x between scan ticks. Offset the next seek by
     * (rate - 1) * elapsed so the net media-clock movement approximates the
     * requested signed rate without pretending reverse decode is available.
     */
    public static long seekScanTargetMs(long currentPositionMs, long elapsedMs,
            float rate, long durationMs)
    {
        if (classify(rate) != Mode.SEEK_SCAN || elapsedMs <= 0L)
            return currentPositionMs;
        double requested = currentPositionMs + (rate - 1.0d) * elapsedMs;
        long target;
        if (requested >= Long.MAX_VALUE)
            target = Long.MAX_VALUE;
        else if (requested <= 0.0d)
            target = 0L;
        else
            target = Math.round(requested);
        if (durationMs > 0L && target >= durationMs)
            return Math.max(0L, durationMs - 1L);
        return target;
    }
}
