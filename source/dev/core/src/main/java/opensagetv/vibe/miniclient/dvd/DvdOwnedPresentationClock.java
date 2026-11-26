package opensagetv.vibe.miniclient.dvd;

/**
 * Explicit owned-stream clock contract: actual destination NAV/STC plus the
 * current decoder epoch's rendered position. No wall-clock extrapolation,
 * recording/file anchor, requested-seek guess or stale pre-FLUSH sample.
 */
public final class DvdOwnedPresentationClock {
    private boolean active;
    private long anchorMs = -1, lastMs;
    public synchronized void open(String url) {
        active = url != null && url.startsWith("push:dvd?") && flag(url, "clock=nav_v1")
                && (flag(url, "disc_transport=dvd_mim_copy_v1") ||
                    flag(url, "disc_transport=dvd_mim_transcode_v1"));
        anchorMs = -1; lastMs = 0;
    }
    public synchronized boolean active() { return active; }
    public synchronized void flush() { anchorMs = -1; }
    public synchronized void anchor(int unsigned45Khz) {
        if (active) lastMs = anchorMs = (unsigned45Khz & 0xffffffffL) * 1000L / 45000L;
    }
    public synchronized long position(long presentedRelativeMs) {
        if (!active || anchorMs < 0 || presentedRelativeMs < 0) return lastMs;
        lastMs = presentedRelativeMs > Long.MAX_VALUE - anchorMs ? Long.MAX_VALUE : anchorMs + presentedRelativeMs;
        return lastMs;
    }
    private static boolean flag(String url, String expected) {
        for (String field : url.substring(url.indexOf('?') + 1).split("&"))
            if (field.equals(expected)) return true;
        return false;
    }
}
