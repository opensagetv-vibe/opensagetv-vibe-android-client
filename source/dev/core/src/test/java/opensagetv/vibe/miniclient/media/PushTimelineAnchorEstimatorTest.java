package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PushTimelineAnchorEstimatorTest
{
    @Test
    public void subtractsThePushedPtsSpanFromTheServerMuxEnd()
    {
        PushTimelineAnchorEstimator estimator = new PushTimelineAnchorEstimator();
        byte[] first = packetWithPts(90_000L);
        byte[] second = packetWithPts(90_000L + 27L * 90_000L);
        byte[] payload = new byte[first.length + second.length];
        System.arraycopy(first, 0, payload, 0, first.length);
        System.arraycopy(second, 0, payload, first.length, second.length);

        estimator.observe(payload, 0, payload.length);

        assertEquals(27_000L, estimator.getPtsSpanMs());
        assertEquals(940_116L, estimator.estimateStartMs(967_116L));
    }

    @Test
    public void keepsFragmentedPacketsAndResetsAtFlush()
    {
        PushTimelineAnchorEstimator estimator = new PushTimelineAnchorEstimator();
        byte[] first = packetWithPts(450_000L);
        byte[] second = packetWithPts(540_000L);
        byte[] payload = new byte[first.length + second.length];
        System.arraycopy(first, 0, payload, 0, first.length);
        System.arraycopy(second, 0, payload, first.length, second.length);

        estimator.observe(payload, 0, 113);
        estimator.observe(payload, 113, payload.length - 113);
        assertEquals(1_000L, estimator.getPtsSpanMs());
        assertEquals(9_000L, estimator.estimateStartMs(10_000L));

        estimator.reset();
        assertEquals(-1L, estimator.getPtsSpanMs());
        assertEquals(-1L, estimator.estimateStartMs(10_000L));
    }

    @Test
    public void rejectsUnknownOrInsufficientPayload()
    {
        PushTimelineAnchorEstimator estimator = new PushTimelineAnchorEstimator();
        estimator.observe(new byte[1024], 0, 1024);
        assertEquals(-1L, estimator.estimateStartMs(50_000L));

        estimator.observe(packetWithPts(90_000L), 0, 188);
        assertEquals(-1L, estimator.estimateStartMs(50_000L));
    }

    private static byte[] packetWithPts(long pts)
    {
        byte[] packet = new byte[188];
        packet[0] = 0x47;
        packet[1] = 0x40;
        packet[2] = 0x20;
        packet[3] = 0x10;
        int p = 4;
        packet[p] = 0;
        packet[p + 1] = 0;
        packet[p + 2] = 1;
        packet[p + 3] = (byte) 0xE0;
        packet[p + 6] = (byte) 0x80;
        packet[p + 7] = (byte) 0x80;
        packet[p + 8] = 5;
        packet[p + 9] = (byte) (0x20 | (((pts >> 30) & 0x07) << 1) | 1);
        packet[p + 10] = (byte) (pts >> 22);
        packet[p + 11] = (byte) ((((pts >> 15) & 0x7F) << 1) | 1);
        packet[p + 12] = (byte) (pts >> 7);
        packet[p + 13] = (byte) (((pts & 0x7F) << 1) | 1);
        return packet;
    }
}
