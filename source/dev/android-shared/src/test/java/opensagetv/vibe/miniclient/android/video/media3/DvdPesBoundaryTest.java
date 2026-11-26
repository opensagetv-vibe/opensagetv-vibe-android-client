package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;
import static org.junit.Assert.*;

/** Packet limit, not reusable backing-buffer capacity, owns PES validity. */
public class DvdPesBoundaryTest
{
    @Test public void truncatedFixedHeaderHasNoPayload()
    {
        for (int length = 0; length < 3; length++)
        {
            assertFalse(DvdPsExtractor.hasCompletePesPayload(new byte[512], 0, length));
        }
    }

    @Test public void optionalHeaderCannotReadStaleBackingBuffer()
    {
        byte[] packet = new byte[512];
        packet[0] = (byte) 0x80;
        packet[2] = (byte) 255;
        assertFalse(DvdPsExtractor.hasCompletePesPayload(packet, 0, 12));
    }

    @Test public void timestampFlagsRequireTheirActualHeaderBytes()
    {
        for (int flags : new int[] {0x80, 0xC0, 0x40})
        {
            byte[] packet = new byte[512];
            packet[0] = (byte) 0x80;
            packet[1] = (byte) flags;
            assertFalse(DvdPsExtractor.hasCompletePesPayload(packet, 0, 12));
        }
    }

    @Test public void maximumValidOptionalHeaderPreservesPayload()
    {
        byte[] packet = new byte[262];
        packet[0] = (byte) 0x80;
        packet[2] = (byte) 255;
        assertTrue(DvdPsExtractor.hasCompletePesPayload(packet, 0, packet.length));
    }

    @Test public void emptyPayloadIsNotAnElementaryAccessUnit()
    {
        assertFalse(DvdPsExtractor.hasCompletePesPayload(
                new byte[] {(byte) 0x80, 0, 0}, 0, 3));
    }

    @Test public void fixedHeaderOffsetAndTimestampMinimumsAreRespected()
    {
        byte[] packet = new byte[64];
        packet[6] = (byte) 0x80;
        packet[7] = (byte) 0xC0;
        packet[8] = 10;
        assertTrue(DvdPsExtractor.hasCompletePesPayload(packet, 6, 20));
        assertFalse(DvdPsExtractor.hasCompletePesPayload(packet, 6, 19));
        packet[8] = 9;
        assertFalse(DvdPsExtractor.hasCompletePesPayload(packet, 6, 20));
        packet[7] = (byte) 0x80;
        packet[8] = 5;
        assertTrue(DvdPsExtractor.hasCompletePesPayload(packet, 6, 15));
        packet[8] = 4;
        assertFalse(DvdPsExtractor.hasCompletePesPayload(packet, 6, 15));
    }
}
