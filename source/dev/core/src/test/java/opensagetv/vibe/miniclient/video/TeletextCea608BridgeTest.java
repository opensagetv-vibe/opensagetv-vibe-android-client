package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import opensagetv.vibe.miniclient.media.TeletextSubtitleEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeletextCea608BridgeTest
{
    @Test
    public void producesParityCorrectCc1AndCc2Records()
    {
        byte[] cc1 = TeletextCea608Bridge.encode("HELLO", 0, 45000);
        byte[] cc2 = TeletextCea608Bridge.encode("BONJOUR", 1, 45000);
        assertTrue(cc1.length > 8);
        assertTrue(cc2.length > 8);
        assertEquals(0x14, cc1[5] & 0x7f);
        assertEquals(0x1c, cc2[5] & 0x7f);
        for (byte[] records : new byte[][]{cc1, cc2})
            for (int offset = 0; offset + 7 < records.length; offset += 8)
            {
                assertEquals(0, records[offset + 4]);
                assertEquals(1, Integer.bitCount(records[offset + 5] & 0xff) & 1);
                assertEquals(1, Integer.bitCount(records[offset + 6] & 0xff) & 1);
                assertEquals(1, records[offset + 7]);
            }
    }

    @Test
    public void waitsForPlaybackClockBeforePostingReadAheadCue() throws Exception
    {
        final List<byte[]> posted = new ArrayList<byte[]>();
        TeletextCea608Bridge bridge = new TeletextCea608Bridge(
                new TeletextCea608Bridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts, long duration,
                            byte[] data, int flags)
                    {
                        posted.add(data);
                    }
                });
        TeletextSubtitleEngine.Service service = service(0);
        bridge.setTrackMappings(service.trackId, -1);
        bridge.enqueue(cue(service.trackId, 5000L));
        bridge.drainTo(4900L);
        assertTrue(posted.isEmpty());
        bridge.drainTo(5000L);
        assertEquals(1, posted.size());
    }

    @Test
    public void mirrorsOneTeletextServiceToBothStockSageTvChannels() throws Exception
    {
        final List<byte[]> posted = new ArrayList<byte[]>();
        TeletextCea608Bridge bridge = new TeletextCea608Bridge(
                new TeletextCea608Bridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts, long duration,
                            byte[] data, int flags)
                    {
                        posted.add(data);
                    }
                });
        TeletextSubtitleEngine.Service service = service(0);
        bridge.setTrackMappings(service.trackId, service.trackId);
        bridge.enqueue(cue(service.trackId, 1000L));
        bridge.drainTo(1000L);

        assertEquals(2, posted.size());
        assertEquals(0x14, posted.get(0)[5] & 0x7f);
        assertEquals(0x1c, posted.get(1)[5] & 0x7f);
    }

    @Test
    public void reflowsTeletextWithoutCuttingWordsAtCeaColumnBoundary()
    {
        List<String> lines = TeletextCea608Bridge.formatLines(
                "for the people who are lucky enough\nto see them.");
        assertEquals(2, lines.size());
        assertEquals("for the people who are lucky", lines.get(0));
        assertEquals("enough to see them.", lines.get(1));
        for (String line : lines) assertTrue(line.length() <= 32);
    }

    @Test
    public void serializesConcurrentClockAndServerDrains() throws Exception
    {
        final List<Long> posted = Collections.synchronizedList(new ArrayList<Long>());
        final CountDownLatch firstSinkEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirstSink = new CountDownLatch(1);
        final CountDownLatch secondSinkEntered = new CountDownLatch(1);
        final TeletextCea608Bridge bridge = new TeletextCea608Bridge(
                new TeletextCea608Bridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts, long duration,
                            byte[] data, int flags)
                    {
                        if (pts == 45000L)
                        {
                            firstSinkEntered.countDown();
                            try
                            {
                                assertTrue(releaseFirstSink.await(2, TimeUnit.SECONDS));
                            }
                            catch (InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                        }
                        else secondSinkEntered.countDown();
                        posted.add(pts);
                    }
                });
        TeletextSubtitleEngine.Service service = service(0);
        bridge.setTrackMappings(service.trackId, -1);
        bridge.enqueue(cue(service.trackId, 1000L));
        bridge.enqueue(cue(service.trackId, 2000L));

        Thread playerClock = new Thread(new Runnable()
        {
            @Override public void run() { bridge.drainTo(1000L); }
        });
        Thread serverClock = new Thread(new Runnable()
        {
            @Override public void run() { bridge.drainTo(2000L); }
        });
        playerClock.start();
        assertTrue(firstSinkEntered.await(2, TimeUnit.SECONDS));
        serverClock.start();
        assertFalse("later drain overtook the first sink submission",
                secondSinkEntered.await(200, TimeUnit.MILLISECONDS));
        releaseFirstSink.countDown();
        playerClock.join(2000L);
        serverClock.join(2000L);

        assertFalse(playerClock.isAlive());
        assertFalse(serverClock.isAlive());
        assertEquals(2, posted.size());
        assertEquals(Long.valueOf(45000L), posted.get(0));
        assertEquals(Long.valueOf(90000L), posted.get(1));
    }

    private static TeletextSubtitleEngine.Service service(int offset) throws Exception
    {
        java.lang.reflect.Constructor<TeletextSubtitleEngine.Service> constructor =
                TeletextSubtitleEngine.Service.class.getDeclaredConstructor(
                        int.class, int.class, String.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(TeletextSubtitleEngine.TRACK_BASE + offset,
                300, "eng", 2, 888);
    }

    private static TeletextSubtitleEngine.Cue cue(int track, long time) throws Exception
    {
        java.lang.reflect.Constructor<TeletextSubtitleEngine.Cue> constructor =
                TeletextSubtitleEngine.Cue.class.getDeclaredConstructor(
                        long.class, long.class, String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1L, time, "TEST", track);
    }
}
