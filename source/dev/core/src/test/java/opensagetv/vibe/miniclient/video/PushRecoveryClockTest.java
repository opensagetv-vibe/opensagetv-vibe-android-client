package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class PushRecoveryClockTest
{
    @Test public void decoderRestartDoesNotResetSameStreamClock()
    {
        PushRecoveryClock clock = new PushRecoveryClock();
        assertEquals(30759, clock.position(30759));
        clock.restartDecoder(30759);
        assertEquals(30759, clock.position(0));
        assertEquals(34757, clock.position(3998));
        clock.restartDecoder(3998);
        assertEquals(34757, clock.position(0));
        assertEquals(35757, clock.position(1000));
    }

    @Test public void actualServerFlushStartsNewEpochWithoutInventingFileOffset()
    {
        PushRecoveryClock clock = new PushRecoveryClock();
        clock.restartDecoder(30759);
        clock.reset();
        assertEquals(500, clock.position(500));
    }

    @Test public void unknownPositionAndZeroProgressRetryStayUnknownOrUnchanged()
    {
        PushRecoveryClock clock = new PushRecoveryClock();
        clock.restartDecoder(30759);
        clock.restartDecoder(0);
        clock.restartDecoder(-1);
        assertEquals(30759, clock.position(0));
        assertEquals(-1, clock.position(-1));
    }

    @Test public void overflowCannotWrapClockNegative()
    {
        PushRecoveryClock clock = new PushRecoveryClock();
        clock.restartDecoder(Long.MAX_VALUE - 2);
        assertEquals(Long.MAX_VALUE, clock.position(10));
        clock.restartDecoder(10);
        assertEquals(Long.MAX_VALUE, clock.position(0));
    }

    @Test public void cachedSeekTargetUsesTheCurrentDecoderEpochOnly()
    {
        PushRecoveryClock clock = new PushRecoveryClock();
        assertEquals(42000, clock.decoderTarget(42000));
        clock.restartDecoder(30000);
        assertEquals(12000, clock.decoderTarget(42000));
        assertEquals(-1, clock.decoderTarget(20000));
        clock.reset();
        assertEquals(42000, clock.decoderTarget(42000));
    }
}
