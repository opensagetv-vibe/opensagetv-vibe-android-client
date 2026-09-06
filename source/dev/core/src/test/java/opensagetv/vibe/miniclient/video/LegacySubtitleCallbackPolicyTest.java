package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacySubtitleCallbackPolicyTest
{
    @Test public void advertisesOnlyExtractorBackedPlayers()
    {
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise("exoplayer"));
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise("media3"));
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise("gsyplayer"));
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise(null));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer"));
    }
}
