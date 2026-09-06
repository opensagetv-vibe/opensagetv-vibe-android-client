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

        assertEquals(0, sink.events.size());
        assertEquals(1, bridge.getPendingSamples());
        bridge.drainTo(2_000_000L);
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
        bridge.drainTo(1_000L);
        bridge.flush();
        assertTrue(bridge.onCeaSample(1_000L, sample));
        bridge.drainTo(1_000L);

        assertEquals(3, sink.events.size());
        assertEquals(LegacyExtenderCaptionBridge.CC_SUBTITLE
                | LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE,
                sink.events.get(1).flags);
        assertArrayEquals(LegacyExtenderCaptionBridge.buildCea608ResetRecords(),
                sink.events.get(1).data);
    }

    @Test
    public void suppressesInterleavedCaptionTrackCopies()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        byte[] first = new byte[] { 0x04, 0x11, 0x22 };
        byte[] second = new byte[] { 0x04, 0x33, 0x44 };

        assertTrue(bridge.onCeaSample(1_000L, first));
        assertTrue(bridge.onCeaSample(2_000L, second));
        assertFalse(bridge.onCeaSample(1_000L, first));
        assertFalse(bridge.onCeaSample(2_000L, second));
        bridge.drainTo(2_000L);

        assertEquals(2, sink.events.size());
    }

    @Test
    public void forwardsCaptionPacketsInPresentationTimestampOrder()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);

        // MPEG-2 B pictures can arrive in decode order: 1.0, 1.1, 1.05.
        assertTrue(bridge.onCeaSample(1_000L, new byte[] { 0x04, 0x21, 0x22 }));
        assertTrue(bridge.onCeaSample(1_100L, new byte[] { 0x04, 0x25, 0x26 }));
        assertTrue(bridge.onCeaSample(1_050L, new byte[] { 0x04, 0x23, 0x24 }));
        bridge.drainTo(1_100L);

        assertEquals(3, sink.events.size());
        assertEquals(45L, sink.events.get(0).pts);
        assertEquals(47L, sink.events.get(1).pts);
        assertEquals(49L, sink.events.get(2).pts);
    }

    @Test
    public void doesNotForwardReadAheadCaptionsBeforePlaybackClock()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        assertTrue(bridge.onCeaSample(40_000_000L,
                new byte[] { 0x04, (byte) 0x94, 0x25 }));

        bridge.drainTo(2_000_000L);
        assertEquals(0, sink.events.size());
        assertEquals(1, bridge.getPendingSamples());

        bridge.drainTo(40_000_000L);
        assertEquals(1, sink.events.size());
        assertEquals(0, bridge.getPendingSamples());
    }

    @Test
    public void seekResetDropsOldReadAheadBeforePostingScreenFlush()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        assertTrue(bridge.onCeaSample(40_000_000L,
                new byte[] { 0x04, (byte) 0x94, 0x25 }));

        bridge.clearPending();
        assertEquals(0, bridge.getPendingSamples());
        assertEquals(0, sink.events.size());

        bridge.postFlush();
        bridge.drainTo(60_000_000L);
        assertEquals(1, sink.events.size());
        assertEquals(LegacyExtenderCaptionBridge.CC_SUBTITLE
                        | LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE,
                sink.events.get(0).flags);
        assertArrayEquals(LegacyExtenderCaptionBridge.buildCea608ResetRecords(),
                sink.events.get(0).data);
    }

    @Test
    public void flushResetCoversBoth608ChannelsAndFieldsWithParityAndRedundancy()
    {
        byte[] records = LegacyExtenderCaptionBridge.buildCea608ResetRecords();
        assertEquals(128, records.length);
        for (int offset = 0; offset < records.length; offset += 8)
        {
            assertTrue((Integer.bitCount(records[offset + 5] & 0xFF) & 1) == 1);
            assertTrue((Integer.bitCount(records[offset + 6] & 0xFF) & 1) == 1);
            assertTrue((records[offset + 4] & 0xFF) <= 1);
            assertEquals(1, records[offset + 7]);
            if ((offset / 8 & 1) == 1)
            {
                assertEquals(records[offset - 3], records[offset + 5]);
                assertEquals(records[offset - 2], records[offset + 6]);
            }
        }
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
