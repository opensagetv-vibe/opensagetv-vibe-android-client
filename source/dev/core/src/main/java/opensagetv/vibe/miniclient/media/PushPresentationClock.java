package opensagetv.vibe.miniclient.media;

/**
 * Per-byte-epoch presentation clock for a native decoder which keeps its own
 * counter across a Push FLUSH. It counts only observed presentation progress;
 * elapsed time is a plausibility bound, never a synthetic playback timer.
 *
 * IJK can preserve an old counter or jump it when new MPEG timestamps arrive.
 * Such a jump re-anchors the native origin instead of adding the old file's
 * resume offset to a new SageTV file. Pause remains frozen. Delayed queries
 * still accept legitimately elapsed native progress. OPENURL owns clear().
 */
public final class PushPresentationClock
{
    private boolean active;
    private long lastRawMs = -1L;
    private long lastSampleMs;
    private long presentedMs;
    private boolean freshNativeEpoch;
    private boolean nativeOriginKnown;
    private long generation;

    public synchronized void clear()
    {
        generation++;
        active = false;
        lastRawMs = -1L;
        presentedMs = 0L;
        freshNativeEpoch = false;
        nativeOriginKnown = false;
    }

    public synchronized void begin(long nativePositionMs, long monotonicMs)
    {
        generation++;
        active = true;
        lastRawMs = nativePositionMs >= 0L ? nativePositionMs : -1L;
        lastSampleMs = monotonicMs;
        presentedMs = 0L;
        freshNativeEpoch = false;
        nativeOriginKnown = false;
    }

    /**
     * A newly prepared native player has a newly normalized stream origin.
     * Its first nonzero observation can include valid pre-roll/decoder progress
     * exceeding a poll interval. Do not discard those seconds as an old-counter
     * discontinuity. This proof is not available on a retained-player FLUSH.
     */
    public synchronized void beginFreshNativeEpoch(long monotonicMs)
    {
        begin(-1L, monotonicMs);
        freshNativeEpoch = true;
        nativeOriginKnown = true;
    }

    public synchronized boolean isActive() { return active; }
    public synchronized long generation() { return generation; }
    public synchronized long position(long expectedGeneration, long nativePositionMs,
            long serverAnchorMs, long observedAtMs)
    {
        if (expectedGeneration != generation) return -1L;
        return position(nativePositionMs, serverAnchorMs, observedAtMs);
    }
    public synchronized String describe()
    {
        return "active=" + active + ",native=" + lastRawMs + ",presented=" + presentedMs
                + ",fresh=" + freshNativeEpoch + ",originKnown=" + nativeOriginKnown;
    }

    public synchronized long position(long nativePositionMs, long serverAnchorMs,
                                      long monotonicMs)
    {
        if (nativePositionMs >= 0L)
        {
            // Concurrent native queries can finish out of order. Such a
            // sample must not replace the origin and count the same progress
            // twice. The caller timestamps BEFORE reading native position.
            if (monotonicMs < lastSampleMs || (monotonicMs == lastSampleMs
                    && lastRawMs >= 0 && nativePositionMs < lastRawMs))
                return saturatingAdd(Math.max(0L, serverAnchorMs), presentedMs);
            if (freshNativeEpoch && nativePositionMs > 0L)
            {
                presentedMs = nativePositionMs;
                lastRawMs = nativePositionMs;
                lastSampleMs = monotonicMs;
                freshNativeEpoch = false;
                return saturatingAdd(Math.max(0L, serverAnchorMs), presentedMs);
            }
            // With a proven new normalized origin, small native audio-clock
            // corrections must not become a second origin and duplicate that
            // interval when the clock catches up. Retained FLUSH does NOT have
            // this proof and keeps the discontinuity-reanchor path below.
            if (nativeOriginKnown && lastRawMs >= 0L
                    && nativePositionMs <= lastRawMs
                    && lastRawMs - nativePositionMs <= 2_000L)
            {
                lastSampleMs = monotonicMs;
                return saturatingAdd(Math.max(0L, serverAnchorMs), presentedMs);
            }
            // Subtract only ordered, nonnegative native values. Invalid input
            // must not wrap a signed long into plausible presentation progress.
            long delta = lastRawMs >= 0L && nativePositionMs >= lastRawMs
                    ? nativePositionMs - lastRawMs : -1L;
            long elapsed = monotonicMs >= lastSampleMs
                    ? nonnegativeDifference(monotonicMs, lastSampleMs) : -1L;
            if (delta >= 0L && elapsed >= 0L
                    && delta <= saturatingAdd(elapsed, 2_000L))
                presentedMs = nativeOriginKnown ? nativePositionMs : saturatingAdd(presentedMs, delta);
            else
                nativeOriginKnown = false;
            // A backwards counter reset or a forward timestamp discontinuity
            // changes the native origin, not the current file's presentation.
            lastRawMs = nativePositionMs;
            lastSampleMs = monotonicMs;
        }
        return saturatingAdd(Math.max(0L, serverAnchorMs), presentedMs);
    }

    private static long saturatingAdd(long left, long right)
    {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long nonnegativeDifference(long newer, long older)
    {
        long difference = newer - older;
        return difference < 0L ? Long.MAX_VALUE : difference;
    }
}
