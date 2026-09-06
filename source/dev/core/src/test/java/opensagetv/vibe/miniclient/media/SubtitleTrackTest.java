package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubtitleTrackTest
{
    @Test
    public void nullLanguageAndLabelProduceStableDescription()
    {
        SubtitleTrack track = new SubtitleTrack(0, SubtitleCodec.CEA608, null, null, true);
        assertEquals("Unknown (CEA608)", track.toString());
    }

    @Test
    public void broadcastServiceIsIncludedInDescription()
    {
        SubtitleTrack track = new SubtitleTrack(0, SubtitleCodec.CEA708, "eng", null, true, 3);
        assertEquals("eng (CEA708) Service 3", track.toString());
        assertEquals(3, track.getAccessibilityChannel());
    }
}
