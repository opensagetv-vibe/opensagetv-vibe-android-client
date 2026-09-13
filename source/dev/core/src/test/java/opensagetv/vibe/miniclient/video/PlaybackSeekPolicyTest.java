package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSeekPolicyTest
{
    @Test
    public void leavesOrdinarySeekUnchanged()
    {
        assertEquals(10_000, PlaybackSeekPolicy.clamp(10_000, 60_000, false));
    }

    @Test
    public void keepsCompletedRecordingAwayFromUnsafeExactEnd()
    {
        assertEquals(55_000, PlaybackSeekPolicy.clamp(60_000, 60_000, false));
    }

    @Test
    public void preservesServerOwnedGrowingTargetWhenSnapshotDurationIsStale()
    {
        assertEquals(65_000, PlaybackSeekPolicy.clamp(65_000, 60_000, true));
    }

    @Test
    public void handlesShortMediaAndUnknownDuration()
    {
        assertEquals(0, PlaybackSeekPolicy.clamp(100, 100, false));
        assertEquals(100, PlaybackSeekPolicy.clamp(100, -1, false));
    }

    @Test
    public void growingUnknownDurationPreservesServerTarget()
    {
        assertEquals(120_000, PlaybackSeekPolicy.clamp(120_000, -1, true));
    }

    @Test
    public void initialZeroSeekKeepsSourceOpenUntilFirstFrame()
    {
        assertTrue(PlaybackSeekPolicy.isInitialZeroSeekNoOp(0, 0, false));
        assertFalse(PlaybackSeekPolicy.isInitialZeroSeekNoOp(0, 0, true));
        assertFalse(PlaybackSeekPolicy.isInitialZeroSeekNoOp(1, 0, false));
        assertFalse(PlaybackSeekPolicy.isInitialZeroSeekNoOp(0, 1, false));
    }
}
