package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    public void keepsGrowingRecordingBehindLiveEdge()
    {
        assertEquals(58_000, PlaybackSeekPolicy.clamp(60_000, 60_000, true));
    }

    @Test
    public void handlesShortMediaAndUnknownDuration()
    {
        assertEquals(0, PlaybackSeekPolicy.clamp(100, 100, false));
        assertEquals(100, PlaybackSeekPolicy.clamp(100, -1, false));
    }

    @Test
    public void growingUnknownDurationUsesBufferedLiveEdge()
    {
        assertEquals(58_000, PlaybackSeekPolicy.clamp(120_000, -1, 60_000, true));
    }

    @Test
    public void completedUnknownDurationDoesNotUseBufferedEdge()
    {
        assertEquals(120_000, PlaybackSeekPolicy.clamp(120_000, -1, 60_000, false));
    }

    @Test
    public void growingUnknownDurationWithoutBufferDoesNotInventAnEdge()
    {
        assertEquals(120_000, PlaybackSeekPolicy.clamp(120_000, -1, -1, true));
    }
}
