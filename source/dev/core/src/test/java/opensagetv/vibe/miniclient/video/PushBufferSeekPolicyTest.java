package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class PushBufferSeekPolicyTest
{
    @Test public void qualifiedMedia3SourceKeepsBasicProducerFallbackSeparate()
    {
        assertFalse(PushBufferSeekPolicy.useForwardCacheSource(false));
        assertTrue(PushBufferSeekPolicy.useForwardCacheSource(true));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("media3", "media3"));
    }

    @Test public void debugQueueTrialIsNotAnAdvertisedOrPersistedCapability()
    {
        try {
            PushBufferSeekPolicy.setDebugForwardCacheTrial(true);
            assertTrue(PushBufferSeekPolicy.useDetailedBufferStats("fixed", "always", "off", false));
            assertFalse(PushBufferSeekPolicy.shouldAdvertise("media3", "media3"));
        } finally { PushBufferSeekPolicy.setDebugForwardCacheTrial(false); }
        assertFalse(PushBufferSeekPolicy.isDebugForwardCacheTrial());
    }

    @Test public void commandDispatchUsesActualTransportNotSelectedMode()
    {
        assertFalse(PushBufferSeekPolicy.canHandleServerSeek(true, false));
        assertTrue(PushBufferSeekPolicy.canHandleServerSeek(false, false));
        assertTrue(PushBufferSeekPolicy.canHandleServerSeek(true, true));
        assertTrue(PushBufferSeekPolicy.canHandleServerSeek(false, true));
    }

    @Test public void guaranteedClassicFixedTranscodeUsesProducerSeekFallback()
    {
        assertFalse(PushBufferSeekPolicy.useDetailedBufferStats("fixed", "always", "off", false));
        assertFalse(PushBufferSeekPolicy.useDetailedBufferStats("FIXED", "ALWAYS", "OFF", false));
    }

    @Test public void sourceClockTransportsNeverLoseTheirDetailedAnchorContract()
    {
        for (String mode : new String[] {null, "", "dynamic", "push", "pull", "smb_direct"})
            assertTrue(PushBufferSeekPolicy.useDetailedBufferStats(mode, "always", "off", false));
        for (String encoding : new String[] {null, "", "needed", "off"})
            assertTrue(PushBufferSeekPolicy.useDetailedBufferStats("fixed", encoding, "off", false));
        for (String remux : new String[] {null, "", "always", "needed"})
            assertTrue(PushBufferSeekPolicy.useDetailedBufferStats("fixed", "always", remux, false));
        assertTrue(PushBufferSeekPolicy.useDetailedBufferStats("fixed", "always", "off", true));
    }

    @Test public void extractorMethodPresenceIsNotProofOfASeekablePushTimeline()
    {
        assertFalse(PushBufferSeekPolicy.shouldAdvertise(null, null));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("exoplayer", "system"));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("MEDIA3", "ijkplayer"));
    }

    @Test public void nativeAndUnknownBackendsDoNotPromiseIgnoredPushSeeks()
    {
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("ijkplayer", "auto"));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("android", "auto"));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("unknown", "auto"));
        assertFalse(PushBufferSeekPolicy.shouldAdvertise("", "auto"));
    }

    @Test public void noGsyDelegateCanInheritAnUnqualifiedCapability()
    {
        for (String engine : new String[] { null, "auto", "media3", "legacy_exo" })
            assertFalse(PushBufferSeekPolicy.shouldAdvertise("gsyplayer", engine));
        for (String engine : new String[] { "system", "ijk", "ijkplayer", "unknown", "" })
            assertFalse(PushBufferSeekPolicy.shouldAdvertise("gsyplayer", engine));
    }
}
