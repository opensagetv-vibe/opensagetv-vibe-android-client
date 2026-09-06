package opensagetv.vibe.miniclient;

/**
 * Small identity-based state machine for a bounded Android background session.
 *
 * <p>The owner supplies the exact connection and player objects. No reconnect,
 * player creation, timer, or Android lifecycle is hidden here; the state machine
 * only decides whether one server PAUSE or PLAY belongs to background policy.</p>
 */
public final class BackgroundSessionState
{
    public enum State
    {
        FOREGROUND,
        BACKGROUND_APP_PAUSED,
        BACKGROUND_USER_PAUSED,
        BACKGROUND_SESSION_LOST,
        EXPLICIT_EXIT
    }

    public enum Action { NONE, PAUSE, PLAY, SESSION_LOST }

    private State state = State.FOREGROUND;
    private Object connectionIdentity;
    private Object playerIdentity;
    private boolean wasPlayingBeforeBackground;
    private long transitionGeneration;
    private long backgroundMonotonicMs = -1;
    private long foregroundMonotonicMs = -1;
    private long pauseRequestCount;
    private long coalescedPauseCount;
    private long playRequestCount;
    private long preservedCount;
    private long lostCount;

    public synchronized Action enterBackground(Object connection, boolean connected,
                                               Object player, int playerState,
                                               int playState, int pauseState)
    {
        return enterBackground(connection, connected, player, playerState,
                playState, pauseState, false);
    }

    public synchronized Action enterBackground(Object connection, boolean connected,
                                               Object player, int playerState,
                                               int playState, int pauseState,
                                               boolean wasPlayingBeforeTransition)
    {
        transitionGeneration++;
        backgroundMonotonicMs = monotonicMs();
        if (state == State.EXPLICIT_EXIT)
            return Action.NONE;
        if (!connected || connection == null || player == null)
            return loseSession();

        connectionIdentity = connection;
        playerIdentity = player;
        wasPlayingBeforeBackground = playerState == playState || wasPlayingBeforeTransition;
        if (wasPlayingBeforeBackground)
        {
            state = State.BACKGROUND_APP_PAUSED;
            pauseRequestCount++;
            if (playerState == playState)
                return Action.PAUSE;
            // Some Fire TV versions deliver their MediaSession PAUSE between
            // Activity.onPause and the application-background grace period.
            // Own that already-applied pause instead of sending a duplicate.
            coalescedPauseCount++;
            return Action.NONE;
        }
        if (playerState == pauseState)
        {
            state = State.BACKGROUND_USER_PAUSED;
            return Action.NONE;
        }
        clearIdentities();
        state = State.FOREGROUND;
        return Action.NONE;
    }

    public synchronized Action enterForeground(Object connection, boolean connected, Object player)
    {
        return enterForeground(connection, connected, player, true);
    }

    public synchronized Action enterForeground(Object connection, boolean connected, Object player,
                                               boolean resumeBackgroundPlayback)
    {
        transitionGeneration++;
        foregroundMonotonicMs = monotonicMs();
        if (state == State.EXPLICIT_EXIT)
            return Action.NONE;
        if (state != State.BACKGROUND_APP_PAUSED && state != State.BACKGROUND_USER_PAUSED)
            return state == State.BACKGROUND_SESSION_LOST ? Action.SESSION_LOST : Action.NONE;
        if (!connected || connectionIdentity != connection || playerIdentity != player)
            return loseSession();

        Action action = Action.NONE;
        if (state == State.BACKGROUND_APP_PAUSED && wasPlayingBeforeBackground
                && resumeBackgroundPlayback)
        {
            playRequestCount++;
            action = Action.PLAY;
        }
        preservedCount++;
        state = State.FOREGROUND;
        clearIdentities();
        return action;
    }

    public synchronized void explicitExit()
    {
        transitionGeneration++;
        state = State.EXPLICIT_EXIT;
        clearIdentities();
    }

    public synchronized void sessionLost()
    {
        if (state == State.EXPLICIT_EXIT) return;
        transitionGeneration++;
        loseSession();
    }

    public synchronized void resetAfterExplicitExit()
    {
        if (state == State.EXPLICIT_EXIT)
        {
            transitionGeneration++;
            state = State.FOREGROUND;
        }
    }

    public synchronized String compactWire()
    {
        return "appVisibilityState=" + state
                + ";backgroundTransitionGeneration=" + transitionGeneration
                + ";pausedByBackground=" + (state == State.BACKGROUND_APP_PAUSED)
                + ";wasPlayingBeforeBackground=" + wasPlayingBeforeBackground
                + ";backgroundMonotonicMs=" + backgroundMonotonicMs
                + ";foregroundMonotonicMs=" + foregroundMonotonicMs
                + ";backgroundPauseRequestCount=" + pauseRequestCount
                + ";backgroundCoalescedPauseCount=" + coalescedPauseCount
                + ";backgroundPlayRequestCount=" + playRequestCount
                + ";backgroundPreservedCount=" + preservedCount
                + ";backgroundLostCount=" + lostCount;
    }

    public synchronized State getState() { return state; }

    private Action loseSession()
    {
        if (state != State.BACKGROUND_SESSION_LOST)
            lostCount++;
        state = State.BACKGROUND_SESSION_LOST;
        clearIdentities();
        return Action.SESSION_LOST;
    }

    private void clearIdentities()
    {
        connectionIdentity = null;
        playerIdentity = null;
        wasPlayingBeforeBackground = false;
    }

    private static long monotonicMs()
    {
        return System.nanoTime() / 1000000L;
    }
}
