package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyExtenderCaptionBridgeTest
{
    @Test
    public void convertsValid608And708TripletsToLegacyRecords()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);

        assertTrue(bridge.onCeaSample(2_000_000L, new byte[] {
                0x04, (byte) 0x94, 0x25,
                0x03, 0x11, 0x22, // invalid: must be ignored
                0x07, 0x45, 0x46
        }));

        assertEquals(1, sink.events.size());
        Event event = sink.events.get(0);
        assertEquals(90_000L, event.pts);
        assertEquals(LegacyExtenderCaptionBridge.CC_SUBTITLE
                | LegacyExtenderCaptionBridge.PTS_VALID, event.flags);
        assertArrayEquals(new byte[] {
                0x00, 0x01, 0x5f, (byte) 0x90, 0x00, (byte) 0x94, 0x25, 0x01,
                0x00, 0x01, 0x5f, (byte) 0x90, 0x03, 0x45, 0x46, 0x01
        }, event.data);
    }

    @Test
    public void suppressesDuplicate608And708TrackCopiesAndFlushes()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        byte[] sample = new byte[] { 0x04, 0x11, 0x22 };

        assertTrue(bridge.onCeaSample(1_000L, sample));
        assertFalse(bridge.onCeaSample(1_000L, sample));
        bridge.flush();
        assertTrue(bridge.onCeaSample(1_000L, sample));

        assertEquals(3, sink.events.size());
        assertEquals(LegacyExtenderCaptionBridge.CC_SUBTITLE
                | LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE,
                sink.events.get(1).flags);
        assertEquals(0, sink.events.get(1).data.length);
    }

    private static final class RecordingSink implements LegacyExtenderCaptionBridge.Sink
    {
        final List<Event> events = new ArrayList<Event>();

        @Override
        public void postSubtitleInfo(long pts45Khz, long duration45Khz, byte[] data, int flags)
        {
            events.add(new Event(pts45Khz, data, flags));
        }
    }

    private static final class Event
    {
        final long pts;
        final byte[] data;
        final int flags;

        Event(long pts, byte[] data, int flags)
        {
            this.pts = pts;
            this.data = data;
            this.flags = flags;
        }
    }
}
