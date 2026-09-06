package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackRatePolicyTest
{
    @Test
    public void advertisesOnlyRandomAccessExoTimelines()
    {
        assertTrue(PlaybackRatePolicy.shouldAdvertise("pull", "media3", "auto"));
        assertTrue(PlaybackRatePolicy.shouldAdvertise("smb_direct", "exoplayer", "auto"));
        assertTrue(PlaybackRatePolicy.shouldAdvertise("smb_auto", "gsyplayer", "media3"));
        assertFalse(PlaybackRatePolicy.shouldAdvertise("dynamic", "media3", "auto"));
        assertFalse(PlaybackRatePolicy.shouldAdvertise("fixed", "exoplayer", "auto"));
        assertFalse(PlaybackRatePolicy.shouldAdvertise("pull", "ijkplayer", "auto"));
        assertFalse(PlaybackRatePolicy.shouldAdvertise("pull", "gsyplayer", "system"));
    }

    @Test
    public void classifiesBoundedNativeAndScanRates()
    {
        assertEquals(PlaybackRatePolicy.Mode.NORMAL, PlaybackRatePolicy.classify(1.0f));
        assertEquals(PlaybackRatePolicy.Mode.NATIVE, PlaybackRatePolicy.classify(0.5f));
        assertEquals(PlaybackRatePolicy.Mode.NATIVE, PlaybackRatePolicy.classify(2.0f));
        assertEquals(PlaybackRatePolicy.Mode.SEEK_SCAN, PlaybackRatePolicy.classify(4.0f));
        assertEquals(PlaybackRatePolicy.Mode.SEEK_SCAN, PlaybackRatePolicy.classify(-16.0f));
        assertEquals(PlaybackRatePolicy.Mode.SEEK_SCAN, PlaybackRatePolicy.classify(256.0f));
        assertEquals(PlaybackRatePolicy.Mode.UNSUPPORTED, PlaybackRatePolicy.classify(0.0f));
        assertEquals(PlaybackRatePolicy.Mode.UNSUPPORTED, PlaybackRatePolicy.classify(-1.0f));
        assertEquals(PlaybackRatePolicy.Mode.UNSUPPORTED, PlaybackRatePolicy.classify(3.0f));
        assertEquals(PlaybackRatePolicy.Mode.UNSUPPORTED, PlaybackRatePolicy.classify(512.0f));
        assertEquals(PlaybackRatePolicy.Mode.UNSUPPORTED, PlaybackRatePolicy.classify(Float.NaN));
    }

    @Test
    public void calculatesSignedSeekScanTargetsAndBounds()
    {
        assertEquals(3250L, PlaybackRatePolicy.seekScanTargetMs(1000L, 750L, 4.0f, 10000L));
        assertEquals(0L, PlaybackRatePolicy.seekScanTargetMs(2000L, 750L, -4.0f, 10000L));
        assertEquals(9999L, PlaybackRatePolicy.seekScanTargetMs(9000L, 750L, 16.0f, 10000L));
        assertEquals(1000L, PlaybackRatePolicy.seekScanTargetMs(1000L, 750L, 2.0f, 10000L));
        assertEquals(1000L, PlaybackRatePolicy.seekScanTargetMs(1000L, 0L, 4.0f, 10000L));
    }
}
