package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingModePolicyTest
{
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
}
