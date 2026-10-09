package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MimDirectTransportPolicyTest
{
    @org.junit.Test public void hostedInputAcceptanceNeverChangesUnnegotiatedNativeInventory() {
        String nativeOnly="H.264,HEVC";
        org.junit.Assert.assertEquals(nativeOnly,MimDirectTransportPolicy.sourceVideoCapabilities(nativeOnly,"transcode",false));
        org.junit.Assert.assertEquals(nativeOnly,MimDirectTransportPolicy.sourceVideoCapabilities(nativeOnly,"copy",true));
        org.junit.Assert.assertEquals("HEVC",MimDirectTransportPolicy.sourceVideoCapabilities("HEVC","transcode",true));
        org.junit.Assert.assertEquals("H.264,HEVC,MPEG2-VIDEO,MPEG2-VIDEO@HL",
                MimDirectTransportPolicy.sourceVideoCapabilities(nativeOnly,"transcode",true));
    }
    @Test public void transcodeNeedsNativeMpeg2AndAvcFallback()
    {
        assertTrue(MimDirectTransportPolicy.supportsTranscodePullFallback(
                "H.264,HEVC,MPEG2-VIDEO,MPEG2-VIDEO@HL"));
    }

    @Test public void noMpeg2CannotSafelyUseOriginalPullFallback()
    {
        assertFalse(MimDirectTransportPolicy.supportsTranscodePullFallback("H.264,HEVC"));
        assertFalse(MimDirectTransportPolicy.supportsTranscodePullFallback("H.264,MPEG2-VIDEO"));
    }

    @Test public void noNativeAvcOutputDoesNotAdvertiseTransformInput()
    {
        assertFalse(MimDirectTransportPolicy.supportsTranscodePullFallback("HEVC"));
        assertFalse(MimDirectTransportPolicy.supportsTranscodePullFallback(null));
        assertFalse(MimDirectTransportPolicy.supportsTranscodePullFallback("NONE"));
    }

    @Test public void existingMpeg2DevicesRetainExactNegotiation()
    {
        String nativeCodecs = "MPEG2-VIDEO,MPEG2-VIDEO@HL,H.264,HEVC";
        assertTrue(MimDirectTransportPolicy.supportsTranscodePullFallback(nativeCodecs));
        assertTrue(MimDirectTransportPolicy.supportsTranscodePullFallback(
                " h.264, mpeg2-video, mpeg2-video@hl "));
    }

    @Test public void activatesOnlyExplicitFixedModesWithCapability()
    {
        assertEquals("copy", MimDirectTransportPolicy.activeMode("fixed", "copy", true));
        assertEquals("transcode", MimDirectTransportPolicy.activeMode(
                "FIXED", "TRANSCODE", true));
        assertEquals("", MimDirectTransportPolicy.activeMode("fixed", "off", true));
        assertEquals("", MimDirectTransportPolicy.activeMode("pull", "copy", true));
        assertEquals("", MimDirectTransportPolicy.activeMode("fixed", "copy", false));
        assertTrue(MimDirectTransportPolicy.isActive("copy"));
        assertFalse(MimDirectTransportPolicy.isActive("off"));
    }
}
