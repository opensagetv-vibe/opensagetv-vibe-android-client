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

    @Test
    public void sourceStreamIdsAreParsedWithoutGuessingFromLabels()
    {
        assertEquals(5503, SubtitleTrack.parseSourceStreamId("1/5503"));
        assertEquals(0x157f, SubtitleTrack.parseSourceStreamId("pid=0x157f"));
        assertEquals(-1, SubtitleTrack.parseSourceStreamId("English 888"));
        SubtitleTrack track = new SubtitleTrack(4, SubtitleCodec.DVB, "eng", "DVB",
                true, -1, 0x120);
        assertEquals(0x120, track.getSourceStreamId());
    }
}
