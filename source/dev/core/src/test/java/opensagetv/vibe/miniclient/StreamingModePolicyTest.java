package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingModePolicyTest
{
    @Test
    public void pushUsesLegacyPreferenceWithoutClaimingOtherModes()
    {
        assertTrue(StreamingModePolicy.isForcedPush("dynamic"));
        assertTrue(StreamingModePolicy.isForcedPush("DYNAMIC"));
        assertTrue(StreamingModePolicy.isForcedPush("push"));
        assertTrue(StreamingModePolicy.isForcedPush("PUSH"));
        for (String mode : new String[] { "pull", "smb_direct", "smb_auto", "fixed", "auto", "", null })
        {
            assertFalse(StreamingModePolicy.isForcedPush(mode));
        }
    }

    @Test
    public void smbModesNegotiateAsOrdinaryPull()
    {
        assertTrue(StreamingModePolicy.isForcedPull("pull"));
        assertTrue(StreamingModePolicy.isForcedPull("SMB_DIRECT"));
        assertTrue(StreamingModePolicy.isForcedPull("smb_auto"));
        assertFalse(StreamingModePolicy.isForcedPull("dynamic"));
        assertFalse(StreamingModePolicy.isForcedPull("fixed"));
        assertFalse(StreamingModePolicy.isForcedPull(null));
    }

    @Test
    public void explicitPushAndPullRemainMutuallyExclusiveForEveryPersistedMode()
    {
        // Growing-recording recovery must not advertise competing transports
        // merely because a backend can decode both. Fixed/unknown choices are
        // not silently migrated to Push; MIM's outer capability override stays
        // independently covered by the negotiation source contract.
        for (String mode : new String[] { "dynamic", "push", "pull", "smb_direct",
                "smb_auto", "fixed", "auto", "", null })
        {
            assertFalse(StreamingModePolicy.isForcedPush(mode)
                    && StreamingModePolicy.isForcedPull(mode));
        }
    }
}
