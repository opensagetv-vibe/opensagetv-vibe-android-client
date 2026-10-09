package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MissingImageDrawRecoveryTest {
    @Test public void notifiesOncePerHandleAndCoalescesRepaint() {
        MissingImageDrawRecovery recovery = new MissingImageDrawRecovery();
        assertFalse(recovery.reportMissing(0, 1000));
        assertTrue(recovery.reportMissing(-54, 1000));
        assertTrue(recovery.reportMissing(-67, 1000));
        assertFalse(recovery.reportMissing(-54, 1001));
        assertTrue(recovery.flushRepaint(1001));
        assertFalse(recovery.flushRepaint(1002));
        assertFalse(recovery.reportMissing(-54, 2999));
        assertTrue(recovery.reportMissing(-54, 3000));
        assertTrue(recovery.flushRepaint(3000));
    }

    @Test public void restoredHandleCanBeReportedAgain() {
        MissingImageDrawRecovery recovery = new MissingImageDrawRecovery();
        assertTrue(recovery.reportMissing(-54, 1000));
        recovery.restored(-54);
        assertTrue(recovery.reportMissing(-54, 1001));
    }

    @Test public void trackedHandlesRemainBoundedAndReset() {
        MissingImageDrawRecovery recovery = new MissingImageDrawRecovery();
        for (int i = 1; i <= 200; i++)
            assertTrue(recovery.reportMissing(-i, 1000));
        assertEquals(128, recovery.trackedHandles());
        recovery.reset();
        assertEquals(0, recovery.trackedHandles());
        assertFalse(recovery.flushRepaint(1001));
        assertTrue(recovery.reportMissing(-54, 1001));
    }
}
