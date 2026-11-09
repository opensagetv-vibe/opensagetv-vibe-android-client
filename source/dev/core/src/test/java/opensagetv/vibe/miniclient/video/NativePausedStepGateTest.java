package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativePausedStepGateTest
{
    @Test public void onlyOneForwardFrameInEligiblePausedPipeline()
    {
        NativePausedStepGate gate = new NativePausedStepGate();
        assertEquals(0, gate.request(-1, true, true, true, 1000));
        assertEquals(0, gate.request(2, true, true, true, 1000));
        assertEquals(0, gate.request(1, false, true, true, 1000));
        assertEquals(0, gate.request(1, true, false, true, 1000));
        assertEquals(0, gate.request(1, true, true, false, 1000));
        assertEquals(0, gate.request(1, true, true, true, -1));
        int id = gate.request(1, true, true, true, 1000);
        assertTrue(id > 0);
        assertEquals(0, gate.completed());
        assertEquals(-1, gate.lastPresentedMs());
        assertEquals(0, gate.request(1, true, true, true, 1000));
        assertFalse(gate.complete(id, 1000));
        assertTrue(gate.complete(id, 1033));
        assertEquals(1, gate.completed());
        assertEquals(1033, gate.lastPresentedMs());
    }

    @Test public void flushAndReplacementRejectLateCompletionWithoutReusingId()
    {
        NativePausedStepGate gate = new NativePausedStepGate();
        int old = gate.request(1, true, true, true, 1000);
        gate.cancel();
        int current = gate.request(1, true, true, true, 500);
        assertNotEquals(old, current);
        assertFalse(gate.complete(old, 1033));
        assertFalse(gate.cancel(old));
        assertEquals(current, gate.pending());
        assertTrue(gate.complete(current, 533));
        assertFalse(gate.complete(current, 566));
        assertFalse(gate.cancel(current));
    }
}
