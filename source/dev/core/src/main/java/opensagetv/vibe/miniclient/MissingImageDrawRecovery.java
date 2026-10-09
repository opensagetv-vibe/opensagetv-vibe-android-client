package opensagetv.vibe.miniclient;

import java.util.LinkedHashMap;

/** Bounds lost-image notifications after an STV draw names an absent handle. */
final class MissingImageDrawRecovery {
    private static final long RETRY_AFTER_MS = 2000L;
    private static final long REPAINT_AFTER_MS = 500L;
    private static final int MAX_TRACKED_HANDLES = 128;

    private final LinkedHashMap<Integer, Long> lastReportMs =
            new LinkedHashMap<Integer, Long>();
    private Long lastRepaintMs;
    private boolean repaintPending;

    boolean reportMissing(int handle, long nowMs) {
        if (handle == 0) return false;
        Long previous = lastReportMs.get(handle);
        if (previous != null && nowMs >= previous &&
                nowMs - previous < RETRY_AFTER_MS) return false;
        if (previous == null && lastReportMs.size() >= MAX_TRACKED_HANDLES) {
            Integer oldest = lastReportMs.keySet().iterator().next();
            lastReportMs.remove(oldest);
        }
        lastReportMs.put(handle, nowMs);
        repaintPending = true;
        return true;
    }

    boolean flushRepaint(long nowMs) {
        if (!repaintPending) return false;
        if (lastRepaintMs != null && nowMs >= lastRepaintMs &&
                nowMs - lastRepaintMs < REPAINT_AFTER_MS) return false;
        repaintPending = false;
        lastRepaintMs = nowMs;
        return true;
    }

    void restored(int handle) {
        lastReportMs.remove(handle);
    }

    void reset() {
        lastReportMs.clear();
        lastRepaintMs = null;
        repaintPending = false;
    }

    int trackedHandles() {
        return lastReportMs.size();
    }
}
