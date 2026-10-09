package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeDvdVideoSupportPolicyTest
{
    @Test public void rejectsProvenMissingNativeDecoder()
    {
        assertTrue(NativeDvdVideoSupportPolicy.shouldReject(true, false, 0));
    }
    @Test public void preservesOrdinaryMediaAndTransformedDvd()
    {
        assertFalse(NativeDvdVideoSupportPolicy.shouldReject(false, false, 0));
        assertFalse(NativeDvdVideoSupportPolicy.shouldReject(true, true, 0));
    }
    @Test public void preservesSelectedHardwareOrExplicitSoftwareCandidates()
    {
        assertFalse(NativeDvdVideoSupportPolicy.shouldReject(true, false, 1));
        assertFalse(NativeDvdVideoSupportPolicy.shouldReject(true, false, 2));
    }
    @Test public void unknownQueryIsNotMissingDecoderProof()
    {
        assertFalse(NativeDvdVideoSupportPolicy.shouldReject(true, false, -1));
    }
    @Test public void emptyOrAudioOnlyTrackDiscoveryIsNotNativeVideoProof()
    {
        assertFalse(NativeDvdVideoSupportPolicy.shouldRejectDiscoveredVideo(true, false, false));
    }
    @Test public void discoveredUnsupportedNativeMpeg2MustNotPlayAudioOnly()
    {
        assertTrue(NativeDvdVideoSupportPolicy.shouldRejectDiscoveredVideo(true, true, false));
        assertFalse(NativeDvdVideoSupportPolicy.shouldRejectDiscoveredVideo(false, true, false));
        assertFalse(NativeDvdVideoSupportPolicy.shouldRejectDiscoveredVideo(true, true, true));
    }
}
