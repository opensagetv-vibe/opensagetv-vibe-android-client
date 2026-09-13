package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubtitleCodecTest
{
    @Test
    public void parsesDvbSubtitleMimeType()
    {
        assertEquals(SubtitleCodec.DVB, SubtitleCodec.parse("application/dvbsubs"));
        assertEquals(SubtitleCodec.DVB, SubtitleCodec.parse("APPLICATION/DVBSUBS"));
        assertTrue(SubtitleCodec.DVB.hasAndroidMimeType("application/dvbsubs"));
    }

    @Test
    public void parseAndMimeChecksAreNullSafe()
    {
        assertNull(SubtitleCodec.parse(null));
        assertTrue(!SubtitleCodec.DVB.hasAndroidMimeType(null));
    }
}
