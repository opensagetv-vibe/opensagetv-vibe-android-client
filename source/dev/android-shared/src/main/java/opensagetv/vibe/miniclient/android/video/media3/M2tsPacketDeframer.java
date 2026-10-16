package opensagetv.vibe.miniclient.android.video.media3;

import java.io.IOException;

/**
 * Removes the four-byte arrival-time prefix from 192-byte Blu-ray M2TS
 * packets. Media3's TS extractor consumes ordinary 188-byte packets, while
 * SageTV's existing BluRayStreamer deliberately preserves the on-disc M2TS
 * packet size and declares it as {@code ps=192} in the Push URL.
 */
final class M2tsPacketDeframer
{
    interface Input
    {
        int read(byte[] buffer, int offset, int length) throws IOException;
    }

    private static final int M2TS_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;
    private static final int PREFIX_SIZE = M2TS_PACKET_SIZE - TS_PACKET_SIZE;
    private static final int TS_SYNC_BYTE = 0x47;

    private final byte[] raw = new byte[M2TS_PACKET_SIZE * 3];
    private final byte[] packet = new byte[M2TS_PACKET_SIZE];
    private int rawStart;
    private int rawEnd;
    private boolean aligned;
    private int payloadOffset = M2TS_PACKET_SIZE;

    int read(Input input, byte[] output, int offset, int length) throws IOException
    {
        if (length == 0)
            return 0;

        int written = 0;
        while (written < length)
        {
            if (payloadOffset < M2TS_PACKET_SIZE)
            {
                int copy = Math.min(length - written, M2TS_PACKET_SIZE - payloadOffset);
                System.arraycopy(packet, payloadOffset, output, offset + written, copy);
                payloadOffset += copy;
                written += copy;
                continue;
            }

            if (!align(input))
                return written == 0 ? -1 : written;
            if (!fill(input, M2TS_PACKET_SIZE))
            {
                if (rawEnd == rawStart)
                    return written == 0 ? -1 : written;
                throw new IOException("Truncated 192-byte M2TS packet at end of Push stream");
            }
            if ((raw[rawStart + PREFIX_SIZE] & 0xff) != TS_SYNC_BYTE)
            {
                // A SageTV Push reader can be replaced while its predecessor
                // still owns a partial output packet. Re-lock against two
                // consecutive M2TS sync bytes instead of treating that legal
                // reader boundary as corrupt media.
                aligned = false;
                continue;
            }
            System.arraycopy(raw, rawStart, packet, 0, M2TS_PACKET_SIZE);
            rawStart += M2TS_PACKET_SIZE;
            payloadOffset = PREFIX_SIZE;
        }
        return written;
    }

    private boolean align(Input input) throws IOException
    {
        if (aligned)
            return true;
        if (!fill(input, M2TS_PACKET_SIZE * 2))
        {
            if (rawEnd == rawStart)
                return false;
            throw new IOException("Unable to establish a complete 192-byte M2TS packet pair");
        }
        int lastSync = rawEnd - M2TS_PACKET_SIZE;
        for (int sync = rawStart + PREFIX_SIZE; sync < lastSync; sync++)
        {
            if ((raw[sync] & 0xff) == TS_SYNC_BYTE
                    && (raw[sync + M2TS_PACKET_SIZE] & 0xff) == TS_SYNC_BYTE)
            {
                rawStart = sync - PREFIX_SIZE;
                aligned = true;
                return true;
            }
        }
        throw new IOException("Invalid M2TS stream: two consecutive 192-byte packet syncs were not found");
    }

    private boolean fill(Input input, int required) throws IOException
    {
        while (rawEnd - rawStart < required)
        {
            if (rawStart != 0)
            {
                System.arraycopy(raw, rawStart, raw, 0, rawEnd - rawStart);
                rawEnd -= rawStart;
                rawStart = 0;
            }
            int count = input.read(raw, rawEnd, raw.length - rawEnd);
            if (count < 0)
                return false;
            if (count == 0)
                continue;
            rawEnd += count;
        }
        return true;
    }
}
