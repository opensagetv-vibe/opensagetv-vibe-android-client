package opensagetv.vibe.miniclient.android.video;

/**
 * Protects a growing recording from a decoder/extractor timeline refresh that
 * reports the beginning of the media again.  SageTV owns intentional seeks;
 * this helper only identifies an unexplained, large backward discontinuity.
 */
public final class GrowingPlaybackPositionGuard
{
    public static final long BACKWARD_JUMP_THRESHOLD_MS = 2000L;

    public enum Action
    {
        ACCEPT,
        PRESERVE_AND_RECOVER,
        PRESERVE_WAITING_FOR_RECOVERY
    }

    public static final class Decision
    {
        public final Action action;
        public final long positionMs;
        public final boolean backwardJump;

        private Decision(Action action, long positionMs, boolean backwardJump)
        {
            this.action = action;
            this.positionMs = positionMs;
            this.backwardJump = backwardJump;
        }
    }

    private GrowingPlaybackPositionGuard() { }

    public static Decision observe(long stablePositionMs, long reportedPositionMs,
                                   boolean growing, boolean explicitSeek,
                                   boolean recoveryPending)
    {
        long reported = Math.max(0L, reportedPositionMs);
        if (!growing || explicitSeek || stablePositionMs < 0L
                || reported + BACKWARD_JUMP_THRESHOLD_MS >= stablePositionMs)
        {
            return new Decision(Action.ACCEPT, reported, false);
        }
        if (recoveryPending)
        {
            return new Decision(Action.PRESERVE_WAITING_FOR_RECOVERY,
                    stablePositionMs, true);
        }
        return new Decision(Action.PRESERVE_AND_RECOVER,
                stablePositionMs, true);
    }
}
