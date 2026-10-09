package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CaptionClockCadenceTest {
    @Test public void playingCaptionsAreNotBatchedAtLineInterval() {
        long delay = CaptionClockCadence.delayMs(true, true);
        assertEquals(33L, delay);
        assertTrue(delay < 500L / 14L);
    }
    @Test public void ordinaryTimelineAndNonCaptionMediaKeepIdleBudget() {
        assertEquals(500L, CaptionClockCadence.delayMs(true, false));
    }
    @Test public void pausedCaptionsDoNotSpinOrAdvance() {
        assertEquals(500L, CaptionClockCadence.delayMs(false, true));
        assertEquals(500L, CaptionClockCadence.delayMs(false, false));
    }

    @Test public void captionTickDoesNotReleaseAWhole500msLineInOneBurst() {
        final AtomicInteger delivered = new AtomicInteger();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                (pts, duration, payload, flags) -> delivered.incrementAndGet());
        // The fixture has14 control/text pairs per line, one per29.97fps
        // picture. This test exercises real bridge delivery, not only a delay.
        for (int i = 0; i < 14; i++)
            bridge.onCeaSample(i * 1_001_000L / 30L,
                    new byte[]{(byte)0xfc, (byte)0x80, (byte)0x80});
        int previous = 0;
        for (long clockUs = 0; clockUs <= 500_000L; clockUs += 33_000L) {
            bridge.drainTo(clockUs);
            assertTrue("frame-sized delivery, not an entire caption line",
                    delivered.get() - previous <= 2);
            previous = delivered.get();
        }
        assertEquals(14, delivered.get());
    }

    @Test public void oldTimelineTickBatchesTheWholeCaptionLine() {
        final AtomicInteger delivered = new AtomicInteger();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                (pts, duration, payload, flags) -> delivered.incrementAndGet());
        for (int i = 0; i < 14; i++)
            bridge.onCeaSample(i * 1_001_000L / 30L,
                    new byte[]{(byte)0xfc, (byte)0x80, (byte)0x80});
        bridge.drainTo(500_000L);
        assertEquals(14, delivered.get());
    }
}
