package opensagetv.vibe.miniclient.video;

/** One outstanding real decoder step; acceptance never advances a media clock. */
public final class NativePausedStepGate
{
    private int sequence, pending;
    private long startMs, completed, lastPresentedMs = -1;

    public synchronized int request(int amount, boolean paused, boolean ready,
            boolean eligibleNativePush, long nativePositionMs)
    {
        if (amount != 1 || !paused || !ready || !eligibleNativePush
                || nativePositionMs < 0 || pending != 0 || sequence == Integer.MAX_VALUE)
            return 0;
        startMs = nativePositionMs;
        pending = ++sequence;
        return pending;
    }

    public synchronized boolean complete(int request, long actualNativePositionMs)
    {
        if (pending == 0 || pending != request || actualNativePositionMs <= startMs)
            return false;
        pending = 0;
        lastPresentedMs = actualNativePositionMs;
        completed++;
        return true;
    }

    /** Keep request IDs monotonic across flush/replacement to reject late events. */
    public synchronized void cancel() { pending = 0; }
    public synchronized boolean cancel(int request)
    {
        if (pending == 0 || pending != request) return false;
        pending = 0;
        return true;
    }
    public synchronized int pending() { return pending; }
    public synchronized long completed() { return completed; }
    public synchronized long lastPresentedMs() { return lastPresentedMs; }
}
