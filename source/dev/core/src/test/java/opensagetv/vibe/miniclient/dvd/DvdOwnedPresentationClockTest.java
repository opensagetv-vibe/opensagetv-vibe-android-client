package opensagetv.vibe.miniclient.dvd;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvdOwnedPresentationClockTest {
    private static final String COPY = "push:dvd?disc_transport=dvd_mim_copy_v1&format=mpegps&clock=nav_v1";
    @Test public void sourceAnchorSurvivesZeroBasedReopenedDecoder() {
        DvdOwnedPresentationClock clock = new DvdOwnedPresentationClock();
        clock.open(COPY); assertTrue(clock.active());
        clock.anchor(30 * 45000);
        assertEquals(30000, clock.position(-1));
        assertEquals(30000, clock.position(0));
        assertEquals(32500, clock.position(2500));
        clock.flush();
        assertEquals(32500, clock.position(45000)); // stale previous decoder is not new progress
        clock.anchor(7 * 45000);
        assertEquals(7000, clock.position(-1));
        assertEquals(7750, clock.position(750));
    }
    @Test public void resetUnknownNativeAndUnsignedClockBoundaries() {
        DvdOwnedPresentationClock clock = new DvdOwnedPresentationClock();
        clock.open(COPY); clock.anchor(-1);
        assertEquals(0xffffffffL * 1000L / 45000L, clock.position(0));
        assertEquals(Long.MAX_VALUE, clock.position(Long.MAX_VALUE));
        clock.open("push:dvd?disc_transport=native&clock=nav_v1"); assertFalse(clock.active());
        clock.open(COPY.replace("nav_v1", "nav_v10")); assertFalse(clock.active());
        clock.open(COPY.replace("copy_v1", "copy_v10")); assertFalse(clock.active());
        clock.open(null); assertFalse(clock.active()); assertEquals(0, clock.position(999));
    }
}
