package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import opensagetv.vibe.miniclient.ConnectionEventBackpressureException;

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

        assertEquals(1, sink.events.size());
        assertEquals(16, sink.events.get(0).data.length);
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

        assertEquals(1, sink.events.size());
        byte[] records = sink.events.get(0).data;
        assertEquals(24, records.length);
        assertEquals(45, records[3] & 255);
        assertEquals(47, records[11] & 255);
        assertEquals(49, records[19] & 255);
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
    public void concurrentClockAndPollDrainsKeepCaptionWordsInOrder() throws Exception
    {
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final List<Long> delivered = Collections.synchronizedList(new ArrayList<Long>());
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                new LegacyExtenderCaptionBridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts45Khz,
                            long duration45Khz, byte[] data, int flags)
                    {
                        if (pts45Khz == 45L)
                        {
                            firstEntered.countDown();
                            try { releaseFirst.await(2, TimeUnit.SECONDS); }
                            catch (InterruptedException interrupted)
                            { Thread.currentThread().interrupt(); }
                        }
                        delivered.add(pts45Khz);
                    }
                });
        assertTrue(bridge.onCeaSample(1_000L, new byte[] { 0x04, 0x21, 0x22 }));
        Thread first = new Thread(() -> bridge.drainTo(1_000L));
        first.start();
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        assertTrue(bridge.onCeaSample(2_000L, new byte[] { 0x04, 0x23, 0x24 }));
        Thread second = new Thread(() -> bridge.drainTo(2_000L));
        second.start();
        try
        {
            Thread.sleep(50L);
            assertTrue("later word must wait for earlier network callback",
                    delivered.isEmpty());
        }
        finally
        {
            releaseFirst.countDown();
            first.join(2_000L);
            second.join(2_000L);
        }
        assertEquals(2, delivered.size());
        assertEquals(Long.valueOf(45L), delivered.get(0));
        assertEquals(Long.valueOf(90L), delivered.get(1));
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

    @Test public void captionBurstUsesOneBoundedCallbackWithEveryRecordPreserved()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        for (int i=0; i<600; i++)
            assertTrue(bridge.onCeaSample(i*33000L, new byte[]{4,0x21,0x22}));
        bridge.drainTo(20000000L);
        assertEquals(1, sink.events.size());
        assertEquals(4800, sink.events.get(0).data.length);
        assertEquals(600, bridge.getForwardedSamples());
        assertEquals(0, bridge.getPendingSamples());
    }

    @Test public void fullEventQueueRetainsDataAndRetriesWithoutDuplicatingRecords()
    {
        final List<byte[]> accepted = new ArrayList<>();
        final boolean[] full = {true};
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge((pts,duration,data,flags) -> {
            if (full[0]) throw new ConnectionEventBackpressureException();
            accepted.add(data);
        });
        bridge.onCeaSample(1000,new byte[]{4,0x21,0x22});
        bridge.onCeaSample(2000,new byte[]{4,0x23,0x24});
        bridge.drainTo(3000);
        assertEquals(2, bridge.getPendingSamples()); assertEquals(0, bridge.getForwardedSamples());
        full[0]=false;
        bridge.drainTo(3000); bridge.drainTo(3000);
        assertEquals(1, accepted.size()); assertEquals(16, accepted.get(0).length);
        assertEquals(2, bridge.getForwardedSamples()); assertEquals(0, bridge.getPendingSamples());
    }

    @Test public void resetBackpressureRetainsResetAheadOfLaterCaptionData()
    {
        final List<Integer> flags = new ArrayList<>();
        final boolean[] full = {true};
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge((pts,duration,data,kind) -> {
            if (full[0]) throw new ConnectionEventBackpressureException();
            flags.add(kind);
        });
        bridge.postFlush();
        bridge.onCeaSample(1000,new byte[]{4,0x21,0x22});
        bridge.drainTo(2000);
        assertEquals(0,flags.size()); assertEquals(1,bridge.getPendingSamples());
        full[0]=false;
        bridge.drainTo(2000);
        assertEquals(2,flags.size());
        assertEquals(LegacyExtenderCaptionBridge.CC_SUBTITLE | LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE,
                flags.get(0).intValue());
    }

    @Test public void largePayloadSplitsAtRecordBoundariesBelowUnsigned16BitWireLimit()
    {
        RecordingSink sink = new RecordingSink();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(sink);
        byte[] data = new byte[5000*3];
        for(int i=0;i<data.length;i+=3){data[i]=(byte)(4+(i/3)%4);data[i+1]=0x21;data[i+2]=0x22;}
        bridge.onCeaSample(1000,data);
        int drains=0;
        while(bridge.getPendingSamples()>0 && drains++<10) bridge.drainTo(2000);
        int total=0;
        int record=0;
        for(Event event:sink.events){
            assertTrue(event.data.length<=16384);assertEquals(0,event.data.length%8);total+=event.data.length;
            for(int i=0;i<event.data.length;i+=8){
                assertEquals(record++%4,event.data[i+4]&255);
                assertEquals(0x21,event.data[i+5]&255);assertEquals(0x22,event.data[i+6]&255);
            }
        }
        assertEquals(40000,total); assertEquals(3,sink.events.size());
    }

    @Test(expected=IllegalArgumentException.class) public void unrelatedSinkErrorsRemainVisible()
    {
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge((pts,dur,data,flags) -> {
            throw new IllegalArgumentException("Not queue backpressure");
        });
        bridge.onCeaSample(1000,new byte[]{4,0x21,0x22});
        bridge.drainTo(2000);
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
