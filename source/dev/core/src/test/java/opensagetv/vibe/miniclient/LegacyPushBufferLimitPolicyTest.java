package opensagetv.vibe.miniclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LegacyPushBufferLimitPolicyTest
{
    @Test
    public void autoPreservesStockCoreDefault()
    {
        assertEquals("", LegacyPushBufferLimitPolicy.propertyValue("auto"));
        assertEquals(32768, LegacyPushBufferLimitPolicy.effectiveBytes("auto"));
    }

    @Test
    public void boundedChoicesAreAdvertisedExactly()
    {
        assertEquals("32768", LegacyPushBufferLimitPolicy.propertyValue("32768"));
        assertEquals("65536", LegacyPushBufferLimitPolicy.propertyValue("65536"));
        assertEquals("131072", LegacyPushBufferLimitPolicy.propertyValue("131072"));
        assertEquals(131072, LegacyPushBufferLimitPolicy.effectiveBytes("131072"));
    }

    @Test
    public void malformedAndExtenderSizedValuesFailSafeToAuto()
    {
        assertFalse(LegacyPushBufferLimitPolicy.isValidPreference("524288"));
        assertFalse(LegacyPushBufferLimitPolicy.isValidPreference("not-a-number"));
        assertTrue(LegacyPushBufferLimitPolicy.isValidPreference(" 65536 "));
        assertEquals("", LegacyPushBufferLimitPolicy.propertyValue("524288"));
        assertEquals(32768, LegacyPushBufferLimitPolicy.effectiveBytes("524288"));
    }
}
