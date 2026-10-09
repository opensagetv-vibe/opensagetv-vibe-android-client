package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerBackendCapabilityTest {
    @Test public void gsyUsesSelectedDelegateNotIjkAssumeAll() {
        assertEquals(PlayerBackend.EXOPLAYER, PlayerBackend.codecCapabilityBackend("gsyplayer","legacy_exo"));
        for (String engine : new String[]{"auto","media3","system",null,"unknown"}) {
            PlayerBackend resolved = PlayerBackend.codecCapabilityBackend("gsyplayer",engine);
            assertEquals(PlayerBackend.MEDIA3,resolved);
            assertTrue(resolved.usesPlatformCodecCapabilities());
            assertFalse(resolved.usesLegacyExoFfmpeg());
        }
    }
    @Test public void otherBackendCapabilitiesAreUnchanged() {
        for (PlayerBackend backend : new PlayerBackend[]{PlayerBackend.EXOPLAYER,PlayerBackend.MEDIA3,PlayerBackend.IJKPLAYER})
            assertEquals(backend, PlayerBackend.codecCapabilityBackend(backend.preferenceValue(),"legacy_exo"));
        assertFalse(PlayerBackend.codecCapabilityBackend("ijkplayer","media3").usesPlatformCodecCapabilities());
        assertEquals(PlayerBackend.EXOPLAYER, PlayerBackend.codecCapabilityBackend(null,null));
    }
}
