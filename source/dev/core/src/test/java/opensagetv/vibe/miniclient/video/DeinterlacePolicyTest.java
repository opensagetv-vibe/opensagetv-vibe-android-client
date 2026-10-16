package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeinterlacePolicyTest
{
    @Test public void explicitGpuNeverEnablesCpuOrServerFiltering() {
        assertEquals("client_gpu",DeinterlacePolicy.normalize("client_gpu"));
        assertEquals("client_gpu",DeinterlacePolicy.effectiveMode("client_gpu",false));
        assertTrue(DeinterlacePolicy.requiresDeviceMpeg2("client_gpu","video/mpeg2"));
        assertFalse(DeinterlacePolicy.clientSoftwareEnabled("client_gpu","auto"));
        assertEquals("off",DeinterlacePolicy.serverFilter("client_gpu","on"));
    }
    @Test public void filterChoicePreservesAdaptiveDefaultAndRejectsInventedAlgorithms()
    {
        assertEquals(0, DeinterlacePolicy.nativeFilter(null));
        assertEquals(0, DeinterlacePolicy.nativeFilter("bwdif"));
        assertEquals(1, DeinterlacePolicy.nativeFilter("fast"));
        assertEquals(2, DeinterlacePolicy.nativeFilter("yadif"));
        assertEquals("auto", DeinterlacePolicy.normalizeFilter("unexpected"));
    }
    @Test public void savedMimIsUnavailableOutsideFixedWithoutDisablingClient()
    {
        assertEquals("auto", DeinterlacePolicy.effectiveMode("mim", false));
        assertEquals("mim", DeinterlacePolicy.effectiveMode("mim", true));
        assertEquals("", DeinterlacePolicy.effectiveMode("", false));
        assertTrue(DeinterlacePolicy.clientSoftwareEnabled(
                DeinterlacePolicy.effectiveMode("mim", false), "off"));
    }
    @Test public void explicitClientRequiresDeviceOnlyForMpeg2()
    {
        assertTrue(DeinterlacePolicy.requiresDeviceMpeg2("client", "video/mpeg2"));
        for (String mode : new String[] { "", "auto", "off", "mim" })
            assertFalse(DeinterlacePolicy.requiresDeviceMpeg2(mode, "video/mpeg2"));
        assertFalse(DeinterlacePolicy.requiresDeviceMpeg2("client", "audio/ac3"));
        assertFalse(DeinterlacePolicy.requiresDeviceMpeg2("client", "video/avc"));
    }
    @Test public void missingPolicyPreservesIndependentExistingDefaults()
    {
        assertFalse(DeinterlacePolicy.clientSoftwareEnabled("", "off"));
        assertTrue(DeinterlacePolicy.clientSoftwareEnabled("", "auto"));
        assertEquals("on", DeinterlacePolicy.serverFilter("", "on"));
    }
    @Test public void clientAndOffNeverAskForServerFieldProcessing()
    {
        assertTrue(DeinterlacePolicy.clientSoftwareEnabled("client", "off"));
        assertFalse(DeinterlacePolicy.clientSoftwareEnabled("off", "auto"));
        assertEquals("off", DeinterlacePolicy.serverFilter("client", "on"));
        assertEquals("off", DeinterlacePolicy.serverFilter("off", "on"));
    }
    @Test public void mimDoesNotDoubleProcessInSoftwareClient()
    {
        assertFalse(DeinterlacePolicy.clientSoftwareEnabled("mim", "auto"));
        assertEquals("on", DeinterlacePolicy.serverFilter("mim", "off"));
    }
    @Test public void automaticPolicyAndUnknownValueFailSafe()
    {
        assertTrue(DeinterlacePolicy.clientSoftwareEnabled("auto", "off"));
        assertEquals("auto", DeinterlacePolicy.serverFilter("auto", "on"));
        assertEquals("auto", DeinterlacePolicy.normalize("unexpected"));
    }
}
