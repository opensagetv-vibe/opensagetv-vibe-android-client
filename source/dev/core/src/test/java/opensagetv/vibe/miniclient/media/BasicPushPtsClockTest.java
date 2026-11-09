package opensagetv.vibe.miniclient.media;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BasicPushPtsClockTest
{
    private static final long WRAP = 1L << 33;

    @Test public void wireClockRestoresRealTsOriginWithoutChangingRelativePosition()
    {
        BasicPushPtsClock clock = new BasicPushPtsClock();
        byte[] packet = ts(45000);
        clock.observe(packet, 0, 12);
        assertEquals(-1, clock.originMs());
        clock.observe(packet, 12, 68);
        // A complete real PES header can precede completion of its TS packet.
        assertEquals(500, clock.originMs());
        clock.observe(packet, 80, packet.length - 80);
        assertEquals(500, clock.originMs());
        assertEquals(2523, clock.wirePositionMs(2023));
        assertEquals(-1, clock.wirePositionMs(-1));
        clock.observe(ts(900000), 0, 188);
        assertEquals(500, clock.originMs()); // Stable first epoch, not latest pushed timestamp.
    }

    @Test public void fragmentedMpeg2ProgramAndMpeg1AudioHeadersUseExistingParser()
    {
        BasicPushPtsClock clock = new BasicPushPtsClock();
        byte[] packet = pes(126000);
        for (int i = 0; i < packet.length; i++) clock.observe(packet, i, 1);
        assertEquals(1400, clock.originMs());
        assertEquals(1500, clock.wirePositionMs(100));
        clock.reset();
        byte[] audio = new byte[11];
        audio[2] = 1; audio[3] = (byte)0xC0; audio[5] = 5;
        writePts(audio, 6, 90000);
        clock.observe(audio, 0, audio.length);
        assertEquals(1000, clock.originMs());
    }

    @Test public void declaredUntimedPayloadCannotSpoofAnotherPesHeader()
    {
        BasicPushPtsClock clock = new BasicPushPtsClock();
        byte[] stream = new byte[6 + 40 + 14];
        stream[2] = 1; stream[3] = (byte)0xBF; stream[5] = 40;
        System.arraycopy(pes(45000), 0, stream, 10, 14);
        System.arraycopy(pes(81000), 0, stream, 46, 14);
        clock.observe(stream, 0, stream.length);
        assertEquals(900, clock.originMs());
    }

    @Test public void flushUnknownAndMalformedHeadersDoNotInventAnOrigin()
    {
        BasicPushPtsClock clock = new BasicPushPtsClock();
        assertEquals(1000, clock.wirePositionMs(1000));
        byte[] invalid = pes(45000);
        invalid[9] &= ~1;
        clock.observe(invalid, 0, invalid.length);
        assertEquals(-1, clock.originMs());
        clock.observe(null, 0, 10);
        clock.observe(invalid, Integer.MAX_VALUE, 2);
        clock.observe(pes(45000), 0, 14);
        assertEquals(500, clock.originMs());
        clock.reset();
        assertEquals(-1, clock.originMs());
        assertEquals(2000, clock.wirePositionMs(2000));
    }

    @Test public void rawClockWrapAndExtremeDurationCannotOverflow()
    {
        BasicPushPtsClock clock = new BasicPushPtsClock();
        clock.observe(ts(WRAP - 45000), 0, 188);
        assertEquals(500, clock.wirePositionMs(1000));
        long expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(90))
                .add(BigInteger.valueOf(WRAP - 45000)).mod(BigInteger.valueOf(WRAP))
                .divide(BigInteger.valueOf(90)).longValue();
        assertEquals(expected, clock.wirePositionMs(Long.MAX_VALUE));
    }

    static byte[] ts(long pts)
    {
        byte[] packet = new byte[188];
        packet[0] = 0x47; packet[1] = 0x40; packet[2] = 0x20; packet[3] = 0x10;
        System.arraycopy(pes(pts), 0, packet, 4, 14);
        return packet;
    }

    static byte[] pes(long pts)
    {
        byte[] packet = new byte[14];
        packet[2] = 1; packet[3] = (byte)0xE0; packet[5] = 8;
        packet[6] = (byte)0x80; packet[7] = (byte)0x80; packet[8] = 5;
        writePts(packet, 9, pts);
        return packet;
    }

    private static void writePts(byte[] packet, int offset, long pts)
    {
        packet[offset] = (byte)(0x21 | (((pts >>> 30) & 7) << 1));
        packet[offset + 1] = (byte)(pts >>> 22);
        packet[offset + 2] = (byte)((((pts >>> 15) & 127) << 1) | 1);
        packet[offset + 3] = (byte)(pts >>> 7);
        packet[offset + 4] = (byte)(((pts & 127) << 1) | 1);
    }
}
