package opensagetv.vibe.miniclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvdCompanionCapabilitiesTest {
    @Test public void exactClientIdentityOnly() {
        assertEquals("444556303031", DvdCompanionCapabilities.identity("44:45:56:30:30:31"));
        assertEquals("aabbccddeeff", DvdCompanionCapabilities.identity("AABBCCDDEEFF"));
        for (String input : new String[]{null, "", "other-client", "0011&revision=1", "abcd"}) {
            try { DvdCompanionCapabilities.identity(input); fail("Invalid identity accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void onlyCurrentBoundedProtocolChallenge() {
        String nonce = "0123456789abcdef0123456789abcdef";
        assertEquals(nonce, DvdCompanionCapabilities.challenge("{\"protocol\":1,\"challenge\":\"" + nonce + "\"}"));
        assertNull(DvdCompanionCapabilities.challenge(null));
        assertNull(DvdCompanionCapabilities.challenge("{\"protocol\":2,\"challenge\":\"" + nonce + "\"}"));
        assertNull(DvdCompanionCapabilities.challenge("{\"protocol\":10,\"challenge\":\"" + nonce + "\"}"));
        assertNull(DvdCompanionCapabilities.challenge("{\"protocol\":1,\"challenge\":\"bad\"}"));
        assertNull(DvdCompanionCapabilities.challenge(new String(new char[257])));
    }
}
