package opensagetv.vibe.miniclient.android.video;

import java.util.concurrent.atomic.AtomicLong;

/** Passive media-body counters shared by one HTTP source factory/session.
 * Playlist/EOF reads add nothing. No timer, polling, URL or bandwidth estimate. */
public final class HttpMediaCounters {
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicLong reads = new AtomicLong();
    public void read(boolean media, int count) {
        if (media && count > 0) { bytes.addAndGet(count); reads.incrementAndGet(); }
    }
    public long bytes() { return bytes.get(); }
    public long reads() { return reads.get(); }
}
