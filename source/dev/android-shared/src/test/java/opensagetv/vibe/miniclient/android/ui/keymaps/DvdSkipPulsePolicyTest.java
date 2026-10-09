package opensagetv.vibe.miniclient.android.ui.keymaps;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvdSkipPulsePolicyTest
{
    @Test public void forwardStopsAtSourceTargetNotWallTime()
    {
        DvdSkipPulsePolicy policy = new DvdSkipPulsePolicy(540000, 1, 1000);
        assertFalse(policy.reached(549999));
        assertTrue(policy.reached(550000));
        assertFalse(policy.expired(8999));
        assertTrue(policy.expired(9000));
    }

    @Test public void reverseClampsToBeginningAndRejectsUnknownClock()
    {
        DvdSkipPulsePolicy policy = new DvdSkipPulsePolicy(3000, -1, 0);
        assertEquals(0, policy.targetMs());
        assertFalse(policy.reached(-1));
        assertFalse(policy.reached(1));
        assertTrue(policy.reached(0));
    }

    @Test public void repeatedAndOppositeTapsAdjustOneTarget()
    {
        DvdSkipPulsePolicy policy = new DvdSkipPulsePolicy(540000, 1, 0);
        policy.enqueue(1, 543000, 1000);
        assertEquals(560000, policy.targetMs());
        policy.enqueue(-1, 544000, 2000);
        assertEquals(550000, policy.targetMs());
        assertEquals(1, policy.direction());
        policy.enqueue(-1, 545000, 3000);
        assertEquals(540000, policy.targetMs());
        assertEquals(-1, policy.direction());
        assertFalse(policy.reached(545000));
        assertTrue(policy.reached(540000));
        for (int index = 0; index < 50; index++) policy.enqueue(1, 545000, 15000);
        assertTrue(policy.expired(20000));
    }
}
