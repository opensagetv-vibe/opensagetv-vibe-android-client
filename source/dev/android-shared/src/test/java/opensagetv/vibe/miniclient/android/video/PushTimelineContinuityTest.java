package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PushTimelineContinuityTest
{
    @Test public void realPausedZeroNeedsEveryOwnershipWitness()
    {
        assertTrue(PushTimelineContinuity.acceptsPausedZero(true, false, true, 0, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(false, false, true, 0, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, true, true, 0, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, false, 0, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, true, -1, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, true, 100, 0, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, true, 0, -1, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, true, 0, 10, true));
        assertFalse(PushTimelineContinuity.acceptsPausedZero(true, false, true, 0, 0, false));
    }
    @Test
    public void ordinaryPushRetainsLastProvenTimeDuringAnchorReplacement()
    {
        assertEquals(462_794L,
                PushTimelineContinuity.pendingMediaTime(
                        true, false, 0L, true, 462_794L));
    }

    @Test
    public void replacementAnchorImmediatelyWinsForRapidForwardSeek()
    {
        assertEquals(642_794L,
                PushTimelineContinuity.pendingMediaTime(
                        true, false, 642_794L, true, 462_794L));
    }

    @Test
    public void lowerReplacementAnchorPreservesIntentionalBackwardSeek()
    {
        assertEquals(442_794L,
                PushTimelineContinuity.pendingMediaTime(
                        true, false, 442_794L, true, 462_794L));
    }

    @Test
    public void initialPushHasNoPositionToRetain()
    {
        assertEquals(0L,
                PushTimelineContinuity.pendingMediaTime(
                        true, false, 0L, false, 462_794L));
        assertEquals(0L,
                PushTimelineContinuity.pendingMediaTime(
                        true, false, 0L, true, -1L));
    }

    @Test
    public void pullAndDvdPushKeepTheirExistingTimelineSemantics()
    {
        assertEquals(0L,
                PushTimelineContinuity.pendingMediaTime(
                        false, false, 642_794L, true, 462_794L));
        assertEquals(0L,
                PushTimelineContinuity.pendingMediaTime(
                        true, true, 642_794L, true, 462_794L));
    }
}
