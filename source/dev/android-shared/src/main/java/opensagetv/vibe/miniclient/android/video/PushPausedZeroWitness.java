package opensagetv.vibe.miniclient.android.video;

/** A rendered, still-paused new decoder epoch may legitimately have time zero. */
public final class PushPausedZeroWitness
{
    private long generation;
    private long resetMs = -1L;
    private boolean freshFrame, advanced;
    private long lastRenderMs = -1L;
    private boolean lastPeriodMatched;

    public synchronized long generation() { return generation; }
    public synchronized void clear() { generation++; resetMs = -1L; freshFrame = advanced = false; }
    public synchronized void reset(long monotonicMs)
    {
        generation++;
        resetMs = monotonicMs;
        freshFrame = advanced = false;
        lastRenderMs = -1L;
        lastPeriodMatched = false;
    }
    public synchronized void rendered(long renderTimeMs, boolean currentPeriod)
    {
        lastRenderMs = renderTimeMs;
        lastPeriodMatched = currentPeriod;
        if (currentPeriod && resetMs >= 0L && renderTimeMs > resetMs) freshFrame = true;
    }
    public synchronized void position(long observedGeneration, long nativePositionMs)
    {
        if (observedGeneration == generation && nativePositionMs > 0L) advanced = true;
    }
    public synchronized boolean confirmed() { return resetMs >= 0L && freshFrame && !advanced; }
    public synchronized String diagnostics() {
        return "generation=" + generation + ",resetMs=" + resetMs
                + ",renderMs=" + lastRenderMs + ",periodMatched=" + lastPeriodMatched
                + ",advanced=" + advanced + ",confirmed=" + confirmed();
    }
}
