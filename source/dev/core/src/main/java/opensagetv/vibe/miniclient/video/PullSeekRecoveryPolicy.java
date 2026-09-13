package opensagetv.vibe.miniclient.video;

/** Pure decision policy for a Pull seek recovery watchdog. */
public final class PullSeekRecoveryPolicy
{
    private PullSeekRecoveryPolicy() {}

    /**
     * Returns true when the datasource has made progress since the watchdog was
     * armed and completed a physical read recently enough that replacing the
     * extractor would interrupt useful work.
     */
    public static boolean shouldDeferForActiveIo(long readsAtArm,
                                                  long currentReads,
                                                  long lastReadMonotonicMs,
                                                  long nowMonotonicMs,
                                                  long activeIoGraceMs)
    {
        if (readsAtArm < 0L || currentReads <= readsAtArm
                || lastReadMonotonicMs < 0L || nowMonotonicMs < lastReadMonotonicMs
                || activeIoGraceMs < 0L)
            return false;
        return nowMonotonicMs - lastReadMonotonicMs <= activeIoGraceMs;
    }
}
