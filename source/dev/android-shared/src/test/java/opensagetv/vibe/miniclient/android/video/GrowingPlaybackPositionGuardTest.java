package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GrowingPlaybackPositionGuardTest
{
    @Test
    public void ignoresSmallBackwardJitter()
    {
        GrowingPlaybackPositionGuard.Decision d = GrowingPlaybackPositionGuard.observe(
                120_000L, 118_500L, true, false, false);
        assertEquals(GrowingPlaybackPositionGuard.Action.ACCEPT, d.action);
        assertEquals(118_500L, d.positionMs);
        assertFalse(d.backwardJump);
    }

    @Test
    public void requestsRecoveryForUnexpectedReset()
    {
        GrowingPlaybackPositionGuard.Decision d = GrowingPlaybackPositionGuard.observe(
                120_000L, 0L, true, false, false);
        assertEquals(GrowingPlaybackPositionGuard.Action.PRESERVE_AND_RECOVER, d.action);
        assertEquals(120_000L, d.positionMs);
        assertTrue(d.backwardJump);
    }

    @Test
    public void doesNotInterceptExplicitSeekOrCompletedFile()
    {
        assertEquals(GrowingPlaybackPositionGuard.Action.ACCEPT,
                GrowingPlaybackPositionGuard.observe(120_000L, 0L, true, true, false).action);
        assertEquals(GrowingPlaybackPositionGuard.Action.ACCEPT,
                GrowingPlaybackPositionGuard.observe(120_000L, 0L, false, false, false).action);
    }

    @Test
    public void waitsWhileRecoveryIsAlreadyPending()
    {
        assertEquals(GrowingPlaybackPositionGuard.Action.PRESERVE_WAITING_FOR_RECOVERY,
                GrowingPlaybackPositionGuard.observe(120_000L, 0L, true, false, true).action);
    }
}
