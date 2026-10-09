package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediaCmdDvdTrickFlagsTest
{
    @Test
    public void decodesForwardAndReverseSignedFixedPointRates()
    {
        assertEquals(2.0f, MediaCmd.decodeDvdTrickRate(
                0x09 | ((2 * 32) << 16)), 0.0f);
        assertEquals(16.0f, MediaCmd.decodeDvdTrickRate(
                0x09 | ((16 * 32) << 16)), 0.0f);
        int reverse = ((-4 * 32) & 0x7FFF) << 16;
        assertEquals(-4.0f, MediaCmd.decodeDvdTrickRate(0x09 | reverse), 0.0f);
    }

    @Test
    public void stopTrickFlagRestoresNormalAndDrainFlagDoesNothing()
    {
        assertEquals(1.0f, MediaCmd.decodeDvdTrickRate(0x02), 0.0f);
        assertTrue(Float.isNaN(MediaCmd.decodeDvdTrickRate(0x100)));
    }
}
