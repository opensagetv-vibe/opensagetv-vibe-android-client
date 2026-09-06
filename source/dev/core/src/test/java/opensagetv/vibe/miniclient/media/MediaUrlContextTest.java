package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaUrlContextTest
{
    @Test
    public void parsesAndRemovesExplicitMediaStateFragment()
    {
        MediaUrlContext value = MediaUrlContext.parse(
                "stv://server//tv/a.ts#sagetv-media-v1;active=0;buffer=4096;major=1;minor=2;channel=5.1;encoding=MPEG2-TS%3Bvideo%3D1",
                true);

        assertEquals("stv://server//tv/a.ts", value.getUrl());
        assertFalse(value.isActive());
        assertEquals(4096, value.getBufferSize());
        assertEquals(1, value.getMajorTypeHint());
        assertEquals(2, value.getMinorTypeHint());
        assertEquals("5.1", value.getChannelHint());
        assertEquals("MPEG2-TS;video=1", value.getEncodingHint());
        assertTrue(value.isExplicit());
    }

    @Test
    public void preservesLegacyFallbackWithoutFragment()
    {
        MediaUrlContext value = MediaUrlContext.parse("stv://server//tv/a.ts", true);
        assertEquals("stv://server//tv/a.ts", value.getUrl());
        assertTrue(value.isActive());
        assertEquals("", value.getChannelHint());
        assertFalse(value.isExplicit());
    }
}
