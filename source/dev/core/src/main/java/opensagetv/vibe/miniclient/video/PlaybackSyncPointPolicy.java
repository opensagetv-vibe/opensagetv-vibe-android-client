package opensagetv.vibe.miniclient.video;

/**
 * Backend-neutral selection of the sync point used for a random-access seek.
 *
 * <p>The player adapters translate the result into their own ExoPlayer
 * {@code SeekParameters}. Keeping the decision here guarantees that Media3
 * and legacy ExoPlayer interpret the same runtime policy identically.</p>
 */
public final class PlaybackSyncPointPolicy
{
    public enum Target
    {
        CLOSEST,
        NEXT,
        PREVIOUS
    }

    private PlaybackSyncPointPolicy()
    {
    }

    public static Target choose(String policy, long currentPositionMs,
                                long targetPositionMs, long directionalMinDeltaMs)
    {
        String normalized = policy == null ? "" : policy.trim().toLowerCase();
        if ("next".equals(normalized)) return Target.NEXT;
        if ("previous".equals(normalized)) return Target.PREVIOUS;
        if ("directional".equals(normalized))
        {
            long deltaMs = targetPositionMs - currentPositionMs;
            if (absoluteDelta(deltaMs) < Math.max(0L, directionalMinDeltaMs))
                return Target.CLOSEST;
            return deltaMs > 0L ? Target.NEXT : Target.PREVIOUS;
        }
        return Target.CLOSEST;
    }

    public static String wireName(Target target)
    {
        if (target == Target.NEXT) return "next_sync";
        if (target == Target.PREVIOUS) return "previous_sync";
        return "closest_sync";
    }

    private static long absoluteDelta(long value)
    {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
    }
}
