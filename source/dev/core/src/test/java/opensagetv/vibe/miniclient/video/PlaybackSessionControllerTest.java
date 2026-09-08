package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSessionControllerTest
{
    @Test
    public void operationsAreMonotonicWithinOneSession()
    {
        PlaybackSessionController controller = new PlaybackSessionController();
        PlaybackSessionController.Token load = controller.beginSession();
        PlaybackSessionController.Token seek = controller.beginOperation(
                PlaybackSessionController.Operation.SEEK);
        PlaybackSessionController.Token play = controller.beginOperation(
                PlaybackSessionController.Operation.PLAY);

        assertTrue(load.getSessionGeneration() == seek.getSessionGeneration());
        assertTrue(seek.getOperationSequence() > load.getOperationSequence());
        assertTrue(play.getOperationSequence() > seek.getOperationSequence());
        assertFalse(controller.isLatestOperation(seek));
        assertTrue(controller.isLatestOperation(play));
    }

    @Test
    public void replacementSessionRejectsOldCallbacks()
    {
        PlaybackSessionController controller = new PlaybackSessionController();
        PlaybackSessionController.Token oldSession = controller.beginSession();
        PlaybackSessionController.Token replacement = controller.beginSession();

        assertFalse(controller.isCurrentSession(oldSession));
        assertTrue(controller.isCurrentSession(replacement));
        assertTrue(replacement.getSessionGeneration() > oldSession.getSessionGeneration());
    }

    @Test
    public void stopPreservesLoadedSessionAndFreeInvalidatesIt()
    {
        PlaybackSessionController controller = new PlaybackSessionController();
        PlaybackSessionController.Token first = controller.beginSession();
        PlaybackSessionController.Token stopped = controller.beginOperation(
                PlaybackSessionController.Operation.STOP);
        PlaybackSessionController.Token restarted = controller.beginOperation(
                PlaybackSessionController.Operation.PLAY);
        assertTrue(controller.isCurrentSession(first));
        assertTrue(controller.isCurrentSession(stopped));
        assertTrue(controller.isCurrentSession(restarted));
        assertTrue(controller.isActive());

        controller.endSession(PlaybackSessionController.Operation.FREE);
        assertFalse(controller.isCurrentSession(first));
        assertFalse(controller.isCurrentSession(restarted));
        assertFalse(controller.isActive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonTerminalOperationCannotEndSession()
    {
        PlaybackSessionController controller = new PlaybackSessionController();
        controller.beginSession();
        controller.endSession(PlaybackSessionController.Operation.SEEK);
    }
}
