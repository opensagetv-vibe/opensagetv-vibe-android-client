package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackgroundSessionStateTest
{
    private static final int PLAY = 2;
    private static final int PAUSE = 3;

    @Test
    public void playingBackgroundResumesOnlySameSession()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        Object connection = new Object();
        Object player = new Object();
        assertEquals(BackgroundSessionState.Action.PAUSE,
                state.enterBackground(connection, true, player, PLAY, PLAY, PAUSE));
        assertEquals(BackgroundSessionState.Action.PLAY,
                state.enterForeground(connection, true, player));
        assertEquals(BackgroundSessionState.State.FOREGROUND, state.getState());
    }

    @Test
    public void manuallyPausedSessionRemainsPaused()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        Object connection = new Object();
        Object player = new Object();
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterBackground(connection, true, player, PAUSE, PLAY, PAUSE));
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterForeground(connection, true, player));
        assertTrue(state.compactWire().contains("backgroundPlayRequestCount=0"));
    }

    @Test
    public void platformPauseDuringBackgroundGraceRetainsAppResumeOwnership()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        Object connection = new Object();
        Object player = new Object();
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterBackground(connection, true, player, PAUSE, PLAY, PAUSE, true));
        assertEquals(BackgroundSessionState.State.BACKGROUND_APP_PAUSED, state.getState());
        assertTrue(state.compactWire().contains("backgroundPauseRequestCount=1"));
        assertTrue(state.compactWire().contains("backgroundCoalescedPauseCount=1"));
        assertEquals(BackgroundSessionState.Action.PLAY,
                state.enterForeground(connection, true, player));
    }

    @Test
    public void automaticResumeCanBeDisabledWithoutLosingTheSession()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        Object connection = new Object();
        Object player = new Object();
        assertEquals(BackgroundSessionState.Action.PAUSE,
                state.enterBackground(connection, true, player, PLAY, PLAY, PAUSE));
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterForeground(connection, true, player, false));
        assertEquals(BackgroundSessionState.State.FOREGROUND, state.getState());
        assertTrue(state.compactWire().contains("backgroundPlayRequestCount=0"));
        assertTrue(state.compactWire().contains("backgroundPreservedCount=1"));
    }

    @Test
    public void replacementConnectionIsNeverAutoResumed()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        Object player = new Object();
        state.enterBackground(new Object(), true, player, PLAY, PLAY, PAUSE);
        assertEquals(BackgroundSessionState.Action.SESSION_LOST,
                state.enterForeground(new Object(), true, player));
        assertEquals(BackgroundSessionState.State.BACKGROUND_SESSION_LOST, state.getState());
    }

    @Test
    public void explicitExitSuppressesPauseAndResume()
    {
        BackgroundSessionState state = new BackgroundSessionState();
        state.explicitExit();
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterBackground(new Object(), true, new Object(), PLAY, PLAY, PAUSE));
        assertEquals(BackgroundSessionState.Action.NONE,
                state.enterForeground(new Object(), true, new Object()));
    }
}
