package opensagetv.vibe.miniclient.android.video;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackRateControllerTest
{
    private static final class Queue implements PlaybackRateController.Scheduler
    {
        final List<Runnable> immediate = new ArrayList<>();
        final List<Runnable> delayed = new ArrayList<>();
        @Override public void post(Runnable work) { immediate.add(work); }
        @Override public void postDelayed(Runnable work, long delayMs)
        {
            assertEquals(3000, delayMs);
            delayed.add(work);
        }
        @Override public void remove(Runnable work) { delayed.remove(work); }
        void drain()
        {
            while (!immediate.isEmpty()) immediate.remove(0).run();
        }
    }

    private static final class Driver implements PlaybackRateController.Driver
    {
        boolean available = true, playing = true;
        long position = 100000, duration = 1000000;
        float rate = 1;
        int seeks, rateChanges;
        @Override public boolean isAvailable() { return available; }
        @Override public boolean isPlaying() { return playing; }
        @Override public long getPositionMs() { return position; }
        @Override public long getDurationMs() { return duration; }
        @Override public void setNativeRate(float value) { rate = value; rateChanges++; }
        @Override public void seekTo(long value) { position = value; seeks++; }
    }

    @Test public void queuedRateCannotChangeAReplacementSession()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver old = new Driver(), current = new Driver();
        c.setRate(2, old);
        c.setRate(0.5f, current);
        q.drain();
        assertEquals(0, old.rateChanges);
        assertEquals(0.5f, current.rate, 0);
    }

    @Test public void dequeuedScanCannotSeekAfterResetOrRescheduleItself()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        c.setRate(16, d);
        Runnable dequeued = q.delayed.remove(0);
        c.reset();
        dequeued.run();
        q.drain();
        assertEquals(0, d.seeks);
        assertTrue(q.delayed.isEmpty());
        assertEquals(1, d.rate, 0);
    }

    @Test public void pauseInvalidatesADequeuedScanAndPlayResumesOnlyTheCurrentOne()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        c.setRate(4, d);
        Runnable old = q.delayed.remove(0);
        c.onPause();
        c.onPlay();
        old.run();
        assertEquals(0, d.seeks);
        q.delayed.remove(0).run();
        assertEquals(109000, d.position);
        assertEquals(1, d.seeks);
        assertEquals(1, q.delayed.size());
    }

    @Test public void directionChangeInvalidatesTheOldForwardScan()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        c.setRate(16, d);
        Runnable forward = q.delayed.remove(0);
        c.setRate(-16, d);
        forward.run();
        assertEquals(0, d.seeks);
        q.delayed.remove(0).run();
        assertEquals(49000, d.position);
        assertEquals(1, d.seeks);
    }

    @Test public void normalRateStopsScanAndRestoresTheNativeClock()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        c.setRate(2, d);
        q.drain();
        assertEquals(2, d.rate, 0);
        c.setRate(16, d);
        Runnable scan = q.delayed.remove(0);
        c.setRate(1, d);
        scan.run();
        q.drain();
        assertEquals(1, d.rate, 0);
        assertEquals(0, d.seeks);
        assertEquals(1, c.getRate(), 0);
    }

    @Test public void endpointDoesNotGenerateRepeatedIdenticalSeeks()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        d.position = d.duration - 1;
        c.setRate(256, d);
        q.delayed.remove(0).run();
        assertEquals(0, d.seeks);
        d.position = 0;
        c.setRate(-256, d);
        q.delayed.remove(0).run();
        assertEquals(0, d.seeks);
    }

    @Test public void resetCannotApplyOneXOverANewerRateOnTheSameDriver()
    {
        Queue q = new Queue();
        PlaybackRateController c = new PlaybackRateController(q);
        Driver d = new Driver();
        c.setRate(2, d);
        c.reset();
        c.setRate(0.5f, d);
        q.drain();
        assertEquals(1, d.rateChanges);
        assertEquals(0.5f, d.rate, 0);
    }
}
