package opensagetv.vibe.miniclient.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class PushStreamHandoffTest
{
    private static boolean data(PushStreamHandoff p, long time)
    {
        return p.observe(true, 32768, true, time, 0);
    }

    private static void seed(PushStreamHandoff p, long time)
    {
        assertFalse(data(p, time));
        assertFalse(data(p, time));
    }

    @Test public void reproducedSeventyFiveMinuteClockToNewRecordingResetsOnce()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 4_538_676L);
        assertTrue(data(p, 6_360L));
        assertFalse(data(p, 6_460L));
    }

    @Test public void flushAndOpenUrlBoundariesDoNotDoubleFlushBackwardsSeeks()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 900000);
        p.reset();
        assertFalse(data(p, 60000));
        assertFalse(data(p, 61000));
        p.reset();
        assertFalse(data(p, 0));
    }

    @Test public void normalZeroClockIsAValidNewFileDomain()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 900000);
        assertTrue(p.observe(true, 32768, true, 0, 0x12));
    }

    @Test public void reorderingAndForwardGapsDoNotFlush()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        assertFalse(data(p, 60000));
        assertFalse(data(p, 59000));
        assertFalse(data(p, 58000));
        assertFalse(data(p, 900000));
        assertFalse(data(p, 899500));
    }

    @Test public void emptyAndUnknownTimestampPollsDoNotReplaceDataClock()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 900000);
        assertFalse(p.observe(true, 0, true, 0, 0));
        assertFalse(p.observe(true, 32768, false, 0, 0));
        assertFalse(p.observe(true, 32768, true, -1, 0));
        assertTrue(data(p, 1000));
    }

    @Test public void trickPlaybackAndReturnCannotMasqueradeAsHandoff()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 900000);
        assertFalse(p.observe(true, 32768, true, 500000, 0x04000009));
        assertFalse(data(p, 400000));
        assertFalse(p.observe(true, 32768, true, 200000, 0x09));
        assertFalse(data(p, 100000));
    }

    @Test public void dvdPullAndEosPollsAreNotFileHandoffs()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 900000);
        assertFalse(p.observe(false, 32768, true, 0, 0));
        assertFalse(data(p, 60000));
        assertFalse(p.observe(true, 32768, true, 0, 0x80));
        assertFalse(p.observe(true, 32768, true, 0, 0x100));
        assertEquals(60000, p.getPreviousMuxMs());
    }

    @Test public void explicitSeekMayPublishOneStalePreTransferHeader()
    {
        PushStreamHandoff p = new PushStreamHandoff();
        seed(p, 4541000);
        p.reset();
        assertFalse(data(p, 4539977));
        assertFalse(data(p, 4499403));
        assertFalse(data(p, 4499550));
        // A later ordinary fast-load still requires the local reset.
        assertTrue(data(p, 354));
    }
}
