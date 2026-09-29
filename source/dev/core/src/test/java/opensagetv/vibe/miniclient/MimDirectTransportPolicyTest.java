package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MimDirectTransportPolicyTest
{
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
