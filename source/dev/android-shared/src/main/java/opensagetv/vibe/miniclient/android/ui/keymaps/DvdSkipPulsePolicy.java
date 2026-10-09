package opensagetv.vibe.miniclient.android.ui.keymaps;

/** Bounded source-position target for a stock DVD scan-and-Play skip. */
final class DvdSkipPulsePolicy
{
    static final long SKIP_MS = 10_000L;
    static final long MAX_RUN_MS = 8_000L;
    private int direction;
    private long targetMs;
    private long deadlineMs;
    private final long totalDeadlineMs;

    DvdSkipPulsePolicy(long sourceMs, int direction, long nowMs)
    {
        if (direction != -1 && direction != 1)
            throw new IllegalArgumentException("DVD skip direction must be -1 or 1");
        this.direction = direction;
        targetMs = direction > 0 ? Math.max(0L, sourceMs) + SKIP_MS
                : Math.max(0L, sourceMs - SKIP_MS);
        deadlineMs = nowMs + MAX_RUN_MS;
        totalDeadlineMs = nowMs + 20_000L;
    }

    boolean reached(long sourceMs)
    {
        return sourceMs >= 0L && (direction > 0 ? sourceMs >= targetMs : sourceMs <= targetMs);
    }

    boolean expired(long nowMs) { return nowMs >= deadlineMs; }
    long targetMs() { return targetMs; }
    int direction() { return direction; }

    void enqueue(int step, long sourceMs, long nowMs)
    {
        if (step != -1 && step != 1) throw new IllegalArgumentException("Invalid skip direction");
        targetMs = Math.max(0L, targetMs + step * SKIP_MS);
        direction = targetMs >= sourceMs ? 1 : -1;
        deadlineMs = Math.min(totalDeadlineMs, nowMs + MAX_RUN_MS);
    }
}
