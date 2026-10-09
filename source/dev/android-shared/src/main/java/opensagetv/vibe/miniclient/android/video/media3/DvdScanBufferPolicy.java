package opensagetv.vibe.miniclient.android.video.media3;

/** Scan-only byte backpressure; not a model-specific decoder profile. */
final class DvdScanBufferPolicy
{
    static final int MAX_QUEUED_BYTES = 1024 * 1024;

    private DvdScanBufferPolicy() { }

    static int available(int physicalAvailable, int capacity)
    {
        return available(physicalAvailable, capacity, false);
    }

    static int available(int physicalAvailable, int capacity, boolean skipPulse)
    {
        if (physicalAvailable < 0) return physicalAvailable;
        int occupied = Math.max(0, capacity - physicalAvailable);
        int budget = skipPulse ? 256 * 1024 : MAX_QUEUED_BYTES;
        return Math.min(physicalAvailable, Math.max(0, budget - occupied));
    }
}
