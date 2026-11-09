package opensagetv.vibe.miniclient.video;
import org.junit.Test;
import static org.junit.Assert.*;

public class IjkSubtitleCapabilityTest
{
    @Test public void onlyPackagedSoftwareArmPathClaimsIJK() {
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer", "software", "armv7l"));
        assertTrue(LegacySubtitleCallbackPolicy.shouldAdvertise("IJKPLAYER", "Software", "aarch64"));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer", "hardware", "armv7l"));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer", "hardware_preferred", "arm64"));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer", "software", "x86_64"));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer", "software", null));
        assertFalse(LegacySubtitleCallbackPolicy.shouldAdvertise("ijkplayer"));
    }
    @Test public void existingBackendsKeepTheirContract() {
        for (String backend : new String[]{null, "exoplayer", "media3", "gsyplayer", "android", "unknown"})
            assertEquals(LegacySubtitleCallbackPolicy.shouldAdvertise(backend),
                    LegacySubtitleCallbackPolicy.shouldAdvertise(backend, "software", "x86"));
    }
}
