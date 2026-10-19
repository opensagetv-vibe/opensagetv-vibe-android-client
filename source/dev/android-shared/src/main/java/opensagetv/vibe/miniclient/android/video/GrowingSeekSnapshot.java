package opensagetv.vibe.miniclient.android.video;

/**
 * One stable byte extent for a growing extractor's duration/seek-map probe.
 *
 * <p>A TS duration reader reopens at length minus its PCR search window. If
 * every reopen publishes the writer's newer SIZE, that target moves under the
 * reader. Freeze only the reported preparation extent, never readable bytes.
 * This object performs no I/O and supports UI begin/end with loader reads.</p>
 */
public final class GrowingSeekSnapshot
{
    private boolean enabled;
    private long extent = -1L;

    public synchronized void begin()
    {
        extent = -1L;
        enabled = true;
    }

    public synchronized long resolveExtent(boolean growing, long availableBytes)
    {
        if (!enabled || !growing) return -1L;
        if (extent < 0L && availableBytes >= 0L) extent = availableBytes;
        return extent;
    }

    public synchronized void end()
    {
        enabled = false;
        extent = -1L;
    }
}
