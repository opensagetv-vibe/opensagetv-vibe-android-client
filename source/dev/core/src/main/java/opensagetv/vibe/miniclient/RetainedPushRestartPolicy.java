package opensagetv.vibe.miniclient;

/**
 * One-shot stock reconnect after FLUSH of a retained, stopped/ended Push player.
 *
 * <p>Stock FastMpeg2Reader's seek(0) shortcut can retain a previous pending
 * seek target. Recreating the reader through SageTV's existing reconnect path
 * clears that state without a new protocol, server plugin or client seek.
 * This does NOT infer a target from FLUSH: stock VideoFrame owns the reload
 * position. Only a stopped/ended ordinary Push player qualifies, never an ordinary
 * playing/paused seek, DVD, Pull or Fixed. OPENURL/INIT delimit the one-shot.
 *
 * <p>Dispatch must immediately follow the successful FLUSH reply. Do not
 * schedule a timer/retry: stock MiniPlayer's fresh seek-position estimate has
 * a limited lifetime, so a delayed request must fail closed instead.
 */
final class RetainedPushRestartPolicy {
    private static final long DISPATCH_BUDGET_MS = 250L;
    private boolean used;
    private long pendingSinceMs = -1L;

    void reset() {
        used = false;
        pendingSinceMs = -1L;
    }

    void flush(boolean selectedPush, boolean actualPush, boolean dvd,
               boolean stopped, long nowMs) {
        pendingSinceMs = !used && selectedPush && actualPush && !dvd && stopped
                ? nowMs : -1L;
    }

    boolean take(long nowMs) {
        long ageMs = pendingSinceMs < 0L ? -1L : nowMs - pendingSinceMs;
        pendingSinceMs = -1L;
        if (used || ageMs < 0L || ageMs > DISPATCH_BUDGET_MS)
            return false;
        used = true;
        return true;
    }
}
