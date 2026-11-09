package opensagetv.vibe.miniclient.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class PushPresentationClockTest
{
    @Test public void freshPreparedNativeOriginDoesNotLoseInitialDecodePreroll()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.beginFreshNativeEpoch(1000);
        assertEquals(630663, c.position(0, 630663, 1100));
        assertEquals(634663, c.position(4000, 630663, 1200));
        assertEquals(635163, c.position(4500, 630663, 1700));
        assertEquals(635163, c.position(4500, 630663, 10000));
        assertEquals(635163, c.position(100000, 630663, 10100));
    }

    @Test public void retainedPlayerFlushMustRevokeFreshOriginProof()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.beginFreshNativeEpoch(1000);
        c.begin(3456, 1100);
        assertEquals(354, c.position(878795, 354, 1200));
        assertEquals(854, c.position(879295, 354, 1700));
        c.clear();
        assertFalse(c.isActive());
    }

    @Test public void freshOriginDoesNotCountSmallAudioClockCorrectionTwice()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.beginFreshNativeEpoch(1000);
        assertEquals(736973, c.position(837, 736136, 1100));
        assertEquals(736973, c.position(0, 736136, 1200));
        assertEquals(737136, c.position(1000, 736136, 2200));
        assertEquals(748982, c.position(12846, 736136, 14046));
        assertEquals(748982, c.position(11850, 736136, 15046));
        assertEquals(749032, c.position(12896, 736136, 16046));
    }

    @Test public void countsNativeProgressNotWallTimeAndFreezesOnPause()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(900000, 1000);
        assertEquals(120000, c.position(900000, 120000, 1000));
        assertEquals(120500, c.position(900500, 120000, 1500));
        assertEquals(120500, c.position(900500, 120000, 30000));
        assertEquals(121000, c.position(901000, 120000, 30500));
    }

    @Test public void reproducedIjkOldResumeAndNativeJumpCannotPolluteNewFile()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(3456, 1000);
        assertEquals(354, c.position(878795, 354, 1100));
        assertEquals(854, c.position(879295, 354, 1600));
        assertEquals(1000, c.position(879795, 0, 2100));
    }

    @Test public void nativeCounterResetIsNotASeekBackInTheServerFile()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(900000, 1000);
        assertEquals(4500000, c.position(0, 4500000, 1100));
        assertEquals(4500500, c.position(500, 4500000, 1600));
        c.begin(500, 1700);
        assertEquals(15000, c.position(0, 15000, 1800));
        assertEquals(15500, c.position(500, 15000, 2300));
    }

    @Test public void delayedQueriesAndUnknownClockDoNotInventDrift()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(-1, 1000);
        assertEquals(60000, c.position(-1, 60000, 2000));
        assertEquals(60000, c.position(0, 60000, 3000));
        assertEquals(90000, c.position(30000, 60000, 33000));
        c.clear();
        assertFalse(c.isActive());
    }

    @Test public void forwardTimestampDiscontinuityDoesNotSkipTheProgramme()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(1000, 100);
        assertEquals(10500, c.position(1500, 10000, 600));
        assertEquals(10500, c.position(3600000, 10000, 700));
        assertEquals(11000, c.position(3600500, 10000, 1200));
    }

    @Test public void explicitEpochReplacementOwnsBothForwardAndBackwardSeek()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(30000, 1000);
        assertEquals(60500, c.position(30500, 60000, 1500));
        c.begin(30500, 1500);
        assertEquals(600000, c.position(30500, 600000, 1500));
        c.begin(30500, 1500);
        assertEquals(500, c.position(31000, 0, 2000));
    }

    @Test public void backwardsMonotonicSampleCannotAuthorizeNativeProgress()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(1000, 10000);
        assertEquals(100, c.position(1500, 100, 9000));
        assertEquals(100, c.position(2000, 100, 9500));
        assertEquals(1100, c.position(2000, 100, 10500));
    }

    @Test public void lateObservationCannotDuplicateProgressOrCrossAFlush()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.beginFreshNativeEpoch(1000);
        long beforeRead = c.generation();
        assertEquals(635663, c.position(beforeRead, 5000, 630663, 2000));
        assertEquals(635663, c.position(beforeRead, 4163, 630663, 1500));
        assertEquals(635763, c.position(beforeRead, 5100, 630663, 2100));
        c.begin(5100, 2200);
        assertEquals(-1, c.position(beforeRead, 5200, 630663, 2250));
        assertEquals(100, c.position(c.generation(), 0, 100, 2300));
        assertEquals(600, c.position(c.generation(), 500, 100, 2800));
    }

    @Test public void unknownSamplesAndLongPauseDoNotInventPlayback()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(1000, 1000);
        assertEquals(5000, c.position(-1, 5000, 86400000));
        assertEquals(5000, c.position(1000, 5000, 86401000));
        assertEquals(5500, c.position(1500, 5000, 86401500));
    }

    @Test public void longRunningPlaybackAndAnchorAdditionCannotOverflow()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(0, 0);
        assertEquals(3_000_000_000L, c.position(3_000_000_000L, 0, 3_000_000_000L));
        assertEquals(Long.MAX_VALUE, c.position(3_000_000_500L,
                Long.MAX_VALUE - 1, 3_000_000_500L));
    }

    @Test public void extremeNativeJumpDoesNotWrapIntoNormalTime()
    {
        PushPresentationClock c = new PushPresentationClock();
        c.begin(0, 0);
        assertEquals(100, c.position(Long.MAX_VALUE, 100, 1));
        assertEquals(100, c.position(0, 100, 2));
        assertEquals(600, c.position(500, 100, 502));
    }
}
