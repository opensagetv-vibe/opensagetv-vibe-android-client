package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class FfmpegAudioMimePolicyTest
{
    @Test public void bothMpegLayerAliasesMatchTheActualRendererConstants()
    {
        for (String value : new String[]{"audio/mpeg-l2", "audio/mpeg-L2", "AUDIO/MPEG-L2"})
            assertEquals("audio/mpeg-L2", FfmpegAudioMimePolicy.canonicalMimeType(value));
        assertEquals("audio/mpeg-L1", FfmpegAudioMimePolicy.canonicalMimeType("audio/mpeg-l1"));
    }

    @Test public void unrelatedUnknownAndNullAreNotInventedCapabilities()
    {
        assertNull(FfmpegAudioMimePolicy.canonicalMimeType(null));
        for (String value : new String[]{"", "audio/ac3", "audio/mp4a-latm", "unknown"})
            assertEquals(value, FfmpegAudioMimePolicy.canonicalMimeType(value));
    }
}
