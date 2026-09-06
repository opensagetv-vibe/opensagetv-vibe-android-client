package opensagetv.vibe.miniclient.video;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.StreamingModePolicy;

/** Pure policy for the MiniPlayer frame-step capability and target clock. */
public final class PlaybackFrameStepPolicy
{
    public static final float FALLBACK_FRAME_RATE = 30.0f;

    private PlaybackFrameStepPolicy()
    {
    }

    /**
     * The protocol capability is session-wide, while Android frame stepping is
     * a random-access operation. Advertise it only for modes guaranteed to use
     * Pull/SMB and not for GSY's Android System compatibility engine.
     */
    public static boolean shouldAdvertise(String streamingMode, String backend, String gsyEngine)
    {
        if (!StreamingModePolicy.isForcedPull(streamingMode)) return false;
        return !"gsyplayer".equalsIgnoreCase(backend)
                || !"system".equalsIgnoreCase(gsyEngine);
    }

    public static boolean canStep(int state, boolean pushMode, boolean playerReady,
            boolean hasPlayer, int amount)
    {
        return state == MiniPlayerPlugin.PAUSE_STATE && !pushMode && playerReady
                && hasPlayer && amount != 0;
    }

    public static long targetPositionMs(long positionMs, long durationMs, int amount,
            float frameRate)
    {
        double safeRate = Float.isNaN(frameRate) || Float.isInfinite(frameRate)
                || frameRate <= 0.0f ? FALLBACK_FRAME_RATE : frameRate;
        double delta = amount * (1000.0d / safeRate);
        double requested = positionMs + delta;
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
