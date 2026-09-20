package opensagetv.vibe.miniclient.android.video.media3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class Media3FfmpegAudioSupportTest
{
    @Test
    public void canonicalizesMedia3MpegLayerAliases()
    {
        assertEquals("audio/mpeg-L1",
                Media3FfmpegAudioSupport.canonicalMimeType("audio/mpeg-l1"));
        assertEquals("audio/mpeg-L2",
                Media3FfmpegAudioSupport.canonicalMimeType("audio/mpeg-l2"));
        assertEquals("audio/mpeg-L2",
                Media3FfmpegAudioSupport.canonicalMimeType("audio/mpeg-L2"));
    }

    @Test
    public void leavesOtherMimeTypesUnchanged()
    {
        assertEquals("audio/ac3",
                Media3FfmpegAudioSupport.canonicalMimeType("audio/ac3"));
        assertNull(Media3FfmpegAudioSupport.canonicalMimeType(null));
    }
}
