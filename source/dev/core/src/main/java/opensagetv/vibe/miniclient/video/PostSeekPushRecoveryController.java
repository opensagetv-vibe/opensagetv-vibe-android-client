package opensagetv.vibe.miniclient.video;

/**
 * Bounded state machine for recovering a stalled client-side Push reader after
 * a server-owned skip.
 *
 * <p>The controller deliberately requires three independent facts before it
 * can act: a client navigation intent, SageTV's subsequent FLUSH, and the first
 * payload/anchor of the replacement Push epoch. It never chooses a seek target
 * and permits only one local reader restart. The caller owns the actual backend
 * restart.</p>
 */
public final class PostSeekPushRecoveryController
{
    public enum Action
    {
        NONE,
        RECOVER,
        COMPLETE,
        EXPIRED
    }

    private enum State
    {
        IDLE,
        WAITING_FOR_FLUSH,
        WAITING_FOR_ANCHOR,
        SETTLING
    }

    static final long INTENT_WINDOW_MS = 6_000L;
    static final long ANCHOR_WINDOW_MS = 6_000L;
    static final long FIRST_FRAME_BUDGET_MS = 8_000L;
    static final long REBUFFER_GRACE_MS = 750L;
    static final long STABLE_PLAYBACK_MS = 5_000L;

    private State state = State.IDLE;
    private long stateStartedMs;
    private long bufferingStartedMs = -1L;
    private long stableStartedMs = -1L;
    private boolean firstFrameSeen;
    private boolean recoveryAttempted;

    public synchronized void arm(long nowMs)
    {
        state = State.WAITING_FOR_FLUSH;
        stateStartedMs = nowMs;
        bufferingStartedMs = -1L;
        stableStartedMs = -1L;
        firstFrameSeen = false;
        recoveryAttempted = false;
    }

    /** Returns true when this FLUSH belongs to the armed navigation window. */
    public synchronized boolean onServerFlush(long nowMs)
    {
        if (state == State.WAITING_FOR_FLUSH
                && elapsed(nowMs, stateStartedMs) <= INTENT_WINDOW_MS)
        {
            waitForAnchor(nowMs);
            return true;
        }

        // SageTV can issue a second FLUSH while committing one server-owned
        // reposition. Keep the same one-shot budget, but require its new anchor
        // before observing the backend again.
        if (state == State.WAITING_FOR_ANCHOR || state == State.SETTLING)
        {
            waitForAnchor(nowMs);
            return true;
        }

        if (state == State.WAITING_FOR_FLUSH)
            reset();
        return false;
    }

    /** Returns true when observation should begin for the confirmed epoch. */
    public synchronized boolean onServerAnchor(long nowMs)
    {
        if (state != State.WAITING_FOR_ANCHOR)
            return false;
        if (elapsed(nowMs, stateStartedMs) > ANCHOR_WINDOW_MS)
        {
            reset();
            return false;
        }
        beginSettling(nowMs);
        return true;
    }

    public synchronized void onFirstFrame(long nowMs)
    {
        if (state != State.SETTLING)
            return;
        firstFrameSeen = true;
        if (bufferingStartedMs < 0L)
            stableStartedMs = nowMs;
    }

    public synchronized void onBufferingChanged(boolean buffering, long nowMs)
    {
        if (state != State.SETTLING)
            return;
        if (buffering)
        {
            // Initial prepare normally reports BUFFERING. It becomes a stall
            // signal only after this epoch has rendered a frame.
            if (firstFrameSeen && bufferingStartedMs < 0L)
                bufferingStartedMs = nowMs;
            stableStartedMs = -1L;
        }
        else
        {
            bufferingStartedMs = -1L;
            if (firstFrameSeen && stableStartedMs < 0L)
                stableStartedMs = nowMs;
        }
    }

    public synchronized Action evaluate(long nowMs)
    {
        if (state == State.IDLE)
            return Action.NONE;
        if (state == State.WAITING_FOR_FLUSH)
        {
            if (elapsed(nowMs, stateStartedMs) > INTENT_WINDOW_MS)
                return expire();
            return Action.NONE;
        }
        if (state == State.WAITING_FOR_ANCHOR)
        {
            if (elapsed(nowMs, stateStartedMs) > ANCHOR_WINDOW_MS)
                return expire();
            return Action.NONE;
        }

        if (!firstFrameSeen
                && elapsed(nowMs, stateStartedMs) >= FIRST_FRAME_BUDGET_MS)
            return recoverOrExpire(nowMs);
        if (bufferingStartedMs >= 0L
                && elapsed(nowMs, bufferingStartedMs) >= REBUFFER_GRACE_MS)
            return recoverOrExpire(nowMs);
        if (firstFrameSeen && stableStartedMs >= 0L
                && elapsed(nowMs, stableStartedMs) >= STABLE_PLAYBACK_MS)
        {
            reset();
            return Action.COMPLETE;
        }
        return Action.NONE;
    }

    public synchronized boolean isActive()
    {
        return state != State.IDLE;
    }

    public synchronized boolean hasRecoveryAttempted()
    {
        return recoveryAttempted;
    }

    public synchronized String snapshot()
    {
        return "state=" + state.name().toLowerCase()
                + ";firstFrame=" + firstFrameSeen
                + ";buffering=" + (bufferingStartedMs >= 0L)
                + ";recoveryAttempted=" + recoveryAttempted;
    }

    public synchronized void reset()
    {
        state = State.IDLE;
        stateStartedMs = 0L;
        bufferingStartedMs = -1L;
        stableStartedMs = -1L;
        firstFrameSeen = false;
        recoveryAttempted = false;
    }

    private void waitForAnchor(long nowMs)
    {
        state = State.WAITING_FOR_ANCHOR;
        stateStartedMs = nowMs;
        bufferingStartedMs = -1L;
        stableStartedMs = -1L;
        firstFrameSeen = false;
    }

    private void beginSettling(long nowMs)
    {
        state = State.SETTLING;
        stateStartedMs = nowMs;
        bufferingStartedMs = -1L;
        stableStartedMs = -1L;
        firstFrameSeen = false;
    }

    private Action recoverOrExpire(long nowMs)
    {
        if (recoveryAttempted)
            return expire();
        recoveryAttempted = true;
        beginSettling(nowMs);
        return Action.RECOVER;
    }

    private Action expire()
    {
        reset();
        return Action.EXPIRED;
    }

    private static long elapsed(long nowMs, long thenMs)
    {
        return Math.max(0L, nowMs - thenMs);
    }
}
