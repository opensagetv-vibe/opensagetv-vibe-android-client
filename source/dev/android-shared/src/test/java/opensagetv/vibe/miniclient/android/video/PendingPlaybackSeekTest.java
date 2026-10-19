package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import static org.junit.Assert.assertEquals;

public class PendingPlaybackSeekTest
{
    @Test public void emptyQueueDoesNotInventAStartPosition()
    {
        assertEquals(PendingPlaybackSeek.NONE, new PendingPlaybackSeek().take());
    }

    @Test public void queuedScheduledStartSurvivesDeferredPlayerConstruction()
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        seek.reset(); // synchronous OPENURL; UI setup has not run yet
        seek.set(180000L);
        assertEquals(180000L, seek.take());
        assertEquals(PendingPlaybackSeek.NONE, seek.take());
    }

    @Test public void explicitZeroSupersedesAnEarlierResume()
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        seek.set(240000L);
        seek.set(0L);
        assertEquals(0L, seek.take());
    }

    @Test public void newestBookmarkWinsWithoutTruncatingLongMillis()
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        seek.set(180000L);
        seek.set(3500000000L);
        assertEquals(3500000000L, seek.take());
    }

    @Test public void newLoadDoesNotInheritThePreviousFilesBookmark()
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        seek.set(180000L);
        seek.reset();
        assertEquals(PendingPlaybackSeek.NONE, seek.take());
        seek.set(90000L);
        assertEquals(90000L, seek.take());
    }

    @Test public void invalidNegativeRequestDoesNotEraseAnAcceptedBookmark()
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        seek.set(180000L);
        seek.set(-1L);
        assertEquals(180000L, seek.take());
    }

    @Test public void takingAnOlderRequestCannotClearANewerMediaThreadRequest()
            throws Exception
    {
        PendingPlaybackSeek seek = new PendingPlaybackSeek();
        CountDownLatch firstTaken = new CountDownLatch(1);
        Thread mediaThread = new Thread(() -> {
            try { firstTaken.await(); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            seek.set(0L);
        });
        seek.set(180000L);
        mediaThread.start();
        long appliedToFirstSource = seek.take();
        firstTaken.countDown();
        mediaThread.join(2000L);
        assertEquals(180000L, appliedToFirstSource);
        assertEquals(0L, seek.take());
    }
}
