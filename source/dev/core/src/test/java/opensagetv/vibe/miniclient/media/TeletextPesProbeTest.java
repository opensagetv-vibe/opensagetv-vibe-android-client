package opensagetv.vibe.miniclient.media;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeletextPesProbeTest
{
    @After
    public void stopProbe()
    {
        TeletextPesProbe.cancel();
    }

    @Test
    public void detectsSubtitleServiceAndTimedPesAcrossChunkBoundaries()
    {
        byte[] stream = concat(
                packet(0x0000, true, 0, psiPayload(patSection(0x0064))),
                packet(0x0064, true, 0, psiPayload(pmtSection(0x012c, true))),
                packet(0x012c, true, 0, teletextPes(90000L)),
                packet(0x1fff, false, 0, new byte[0]));

        TeletextPesProbe.begin();
        TeletextPesProbe.observe("pull", 0L, stream, 0, 117);
        TeletextPesProbe.observe("pull", 117L, stream, 117, stream.length - 117);
        TeletextPesProbe.Snapshot result = TeletextPesProbe.finish();

        assertEquals("preserved", result.status);
        assertTrue(result.isPreserved());
        assertEquals(188, result.packetSize);
        assertTrue(result.patSections > 0);
        assertTrue(result.pmtSections > 0);
        assertTrue(result.teletextDescriptors > 0);
        assertTrue(result.subtitleServices > 0);
        assertTrue(result.services.contains("pid=0x012c"));
        assertTrue(result.services.contains("lang=eng"));
        assertTrue(result.services.contains("page=888"));
        assertTrue(result.pesPackets > 0);
        assertTrue(result.teletextDataUnits > 0);
        assertEquals(1, result.ptsCount);
        assertEquals(90000L, result.firstPts90Khz);
        assertEquals(0, result.continuityErrors);
    }

    @Test
    public void reportsOrdinaryTransportStreamAsNotDetected()
    {
        byte[] stream = concat(
                packet(0x0000, true, 0, psiPayload(patSection(0x0064))),
                packet(0x0064, true, 0, psiPayload(pmtSection(0x0100, false))),
                packet(0x0100, false, 0, new byte[]{1, 2, 3}),
                packet(0x1fff, false, 0, new byte[0]));

        TeletextPesProbe.begin();
        TeletextPesProbe.observe("push", -1L, stream, 0, stream.length);
        TeletextPesProbe.Snapshot result = TeletextPesProbe.finish();

        assertEquals("not-detected", result.status);
        assertFalse(result.isTeletextDetected());
        assertTrue(result.transportPackets >= 4);
        assertEquals("push", result.sources);
    }

    @Test
    public void remainsDormantOutsideExplicitTest()
    {
        assertFalse(TeletextPesProbe.isActive());
        TeletextPesProbe.observe("pull", 0L, new byte[1024], 0, 1024);
        assertEquals("inactive", TeletextPesProbe.finish().status);
    }

    private static byte[] patSection(int pmtPid)
    {
        return new byte[]{
                0x00, (byte) 0xb0, 0x0d,
                0x00, 0x01, (byte) 0xc1, 0x00, 0x00,
                0x00, 0x01, (byte) (0xe0 | (pmtPid >> 8)), (byte) pmtPid,
                0x00, 0x00, 0x00, 0x00};
    }

    private static byte[] pmtSection(int elementaryPid, boolean teletext)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int descriptorLength = teletext ? 7 : 0;
        int sectionLength = 9 + 5 + descriptorLength + 4;
        write(out, 0x02, 0xb0 | (sectionLength >> 8), sectionLength,
                0x00, 0x01, 0xc1, 0x00, 0x00,
                0xe0 | (elementaryPid >> 8), elementaryPid,
                0xf0, 0x00,
                teletext ? 0x06 : 0x1b,
                0xe0 | (elementaryPid >> 8), elementaryPid,
                0xf0 | (descriptorLength >> 8), descriptorLength);
        if (teletext)
            write(out, 0x56, 0x05, 'e', 'n', 'g', 0x10, 0x88);
        write(out, 0, 0, 0, 0);
        return out.toByteArray();
    }

    private static byte[] teletextPes(long pts)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x00, 0x00, 0x01, 0xbd, 0x00, 0x36, 0x80, 0x80, 0x05);
        byte[] encodedPts = encodePts(pts);
        out.write(encodedPts, 0, encodedPts.length);
        write(out, 0x10, 0x02, 0x2c);
        for (int index = 0; index < 44; index++) out.write(index & 0xff);
        return out.toByteArray();
    }

    private static byte[] encodePts(long pts)
    {
        return new byte[]{
                (byte) (0x21 | (((pts >> 30) & 0x07) << 1)),
                (byte) (pts >> 22),
                (byte) ((((pts >> 15) & 0x7f) << 1) | 1),
                (byte) (pts >> 7),
                (byte) (((pts & 0x7f) << 1) | 1)};
    }

    private static byte[] psiPayload(byte[] section)
    {
        byte[] payload = new byte[section.length + 1];
        System.arraycopy(section, 0, payload, 1, section.length);
        return payload;
    }

    private static byte[] packet(int pid, boolean payloadStart, int continuity,
            byte[] payload)
    {
        byte[] packet = new byte[188];
        Arrays.fill(packet, (byte) 0xff);
        packet[0] = 0x47;
        packet[1] = (byte) ((payloadStart ? 0x40 : 0) | ((pid >> 8) & 0x1f));
        packet[2] = (byte) pid;
        packet[3] = (byte) (0x10 | (continuity & 0x0f));
        System.arraycopy(payload, 0, packet, 4, Math.min(payload.length, 184));
        return packet;
    }

    private static byte[] concat(byte[]... values)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.write(value, 0, value.length);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, int... values)
    {
        for (int value : values) out.write(value & 0xff);
    }
}
