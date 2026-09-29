package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Media3GrowingPlaybackTimeoutPolicyTest
{
    @Test
    public void relaxesOnlyProvenGrowingMediaServerPull()
    {
        assertTrue(Media3GrowingPlaybackTimeoutPolicy.shouldRelaxPlayingNotEnding(
                false, false, false, true));
        assertFalse(Media3GrowingPlaybackTimeoutPolicy.shouldRelaxPlayingNotEnding(
                false, false, false, false));
        assertFalse(Media3GrowingPlaybackTimeoutPolicy.shouldRelaxPlayingNotEnding(
                true, false, false, true));
        assertFalse(Media3GrowingPlaybackTimeoutPolicy.shouldRelaxPlayingNotEnding(
                false, true, false, true));
        assertFalse(Media3GrowingPlaybackTimeoutPolicy.shouldRelaxPlayingNotEnding(
                false, false, true, true));
    }

    @Test
    public void usesLargestPositiveMedia3Timeout()
    {
        assertEquals(Integer.MAX_VALUE,
                Media3GrowingPlaybackTimeoutPolicy.PLAYING_NOT_ENDING_TIMEOUT_MS);
    }
}
