package opensagetv.vibe.miniclient.media;

/**
 * Maps SageTV's detailed Push mux timestamp to the start of the current
 * decoder byte epoch.
 *
 * <p>The stock server documents the mux timestamp as the time at the
 * <em>end</em> of the data in the current PUSHBUFFER command. Media3 and
 * legacy ExoPlayer expose a position relative to the beginning of the stream
 * following FLUSH. Adding that relative position directly to the buffer-end
 * timestamp advances the reported playhead by the queued MPEG-TS duration and
 * can make an STV choose the wrong seek or commercial marker. This bounded
 * parser measures the first-to-last PES PTS span and subtracts it from the
 * server timestamp. If the payload is not recognizable 188-byte MPEG-TS, no
 * estimate is returned and the historical raw anchor remains in use.</p>
 */
public final class PushTimelineAnchorEstimator
{
    private static final int TS_PACKET_BYTES = 188;
    private static final long PTS_WRAP = 1L << 33;
    private static final long PTS_HALF_WRAP = PTS_WRAP >> 1;
    private static final long MIN_PROVEN_SPAN_MS = 250L;
    private static final long MAX_PROVEN_SPAN_MS = 120_000L;

    private byte[] carry = new byte[0];
    private long firstPts90Khz = -1L;
    private long latestPts90Khz = -1L;
    private long lastRawPts90Khz = -1L;
    private long wrapOffset90Khz;
    private int ptsCount;

    public void reset()
    {
        carry = new byte[0];
        firstPts90Khz = -1L;
        latestPts90Khz = -1L;
        lastRawPts90Khz = -1L;
        wrapOffset90Khz = 0L;
        ptsCount = 0;
    }

    public void observe(byte[] bytes, int offset, int length)
    {
        if (bytes == null || length <= 0 || offset < 0
                || offset > bytes.length - length)
            return;

        byte[] combined = new byte[carry.length + length];
        System.arraycopy(carry, 0, combined, 0, carry.length);
        System.arraycopy(bytes, offset, combined, carry.length, length);

        int cursor = findPacketStart(combined, 0);
        while (cursor >= 0 && cursor + TS_PACKET_BYTES <= combined.length)
        {
            parsePacket(combined, cursor);
            int next = cursor + TS_PACKET_BYTES;
            if (next + TS_PACKET_BYTES <= combined.length
                    && (combined[next] & 0xFF) != 0x47)
                cursor = findPacketStart(combined, next);
            else
                cursor = next;
        }

        int remainingStart = cursor < 0
                ? Math.max(0, combined.length - (TS_PACKET_BYTES - 1))
                : Math.max(0, Math.min(cursor, combined.length));
        int remaining = combined.length - remainingStart;
        if (remaining > TS_PACKET_BYTES - 1)
        {
            remainingStart = combined.length - (TS_PACKET_BYTES - 1);
            remaining = TS_PACKET_BYTES - 1;
        }
        carry = new byte[remaining];
        if (remaining > 0)
            System.arraycopy(combined, remainingStart, carry, 0, remaining);
    }

    /** Returns a proven epoch-start time, or {@code -1} for safe fallback. */
    public long estimateStartMs(long serverMuxEndMs)
    {
        long spanMs = getPtsSpanMs();
        if (serverMuxEndMs <= 0L || ptsCount < 2
                || spanMs < MIN_PROVEN_SPAN_MS
                || spanMs > MAX_PROVEN_SPAN_MS
                || serverMuxEndMs < spanMs)
            return -1L;
        return serverMuxEndMs - spanMs;
    }

    public long getPtsSpanMs()
    {
        if (firstPts90Khz < 0L || latestPts90Khz < firstPts90Khz)
            return -1L;
        return (latestPts90Khz - firstPts90Khz) / 90L;
    }

    public int getPtsCount()
    {
        return ptsCount;
    }

    private static int findPacketStart(byte[] bytes, int start)
    {
        int limit = bytes.length - TS_PACKET_BYTES;
        for (int i = Math.max(0, start); i <= limit; i++)
        {
            if ((bytes[i] & 0xFF) != 0x47)
                continue;
            int next = i + TS_PACKET_BYTES;
            if (next >= bytes.length || (bytes[next] & 0xFF) == 0x47)
                return i;
        }
        return -1;
    }

    private void parsePacket(byte[] bytes, int packetStart)
    {
        if ((bytes[packetStart] & 0xFF) != 0x47)
            return;
        boolean payloadStart = (bytes[packetStart + 1] & 0x40) != 0;
        int adaptationControl = (bytes[packetStart + 3] >> 4) & 0x03;
        if (!payloadStart || adaptationControl == 0 || adaptationControl == 2)
            return;

        int payload = packetStart + 4;
        int packetEnd = packetStart + TS_PACKET_BYTES;
        if (adaptationControl == 3)
        {
            if (payload >= packetEnd)
                return;
            payload += 1 + (bytes[payload] & 0xFF);
        }
        if (payload + 14 > packetEnd
                || bytes[payload] != 0 || bytes[payload + 1] != 0
                || bytes[payload + 2] != 1)
            return;

        int streamId = bytes[payload + 3] & 0xFF;
        boolean timedElementaryStream = (streamId >= 0xC0 && streamId <= 0xEF)
                || streamId == 0xBD;
        if (!timedElementaryStream || (bytes[payload + 7] & 0x80) == 0
                || (bytes[payload + 8] & 0xFF) < 5)
            return;

        long rawPts = parsePts(bytes, payload + 9);
        if (rawPts < 0L)
            return;
        if (lastRawPts90Khz >= 0L)
        {
            long delta = rawPts - lastRawPts90Khz;
            if (delta < -PTS_HALF_WRAP)
                wrapOffset90Khz += PTS_WRAP;
            else if (delta > PTS_HALF_WRAP)
                wrapOffset90Khz -= PTS_WRAP;
        }
        lastRawPts90Khz = rawPts;
        long unwrapped = rawPts + wrapOffset90Khz;
        if (firstPts90Khz < 0L)
            firstPts90Khz = unwrapped;
        if (latestPts90Khz < unwrapped)
            latestPts90Khz = unwrapped;
        ptsCount++;
    }

    private static long parsePts(byte[] bytes, int offset)
    {
        if (offset < 0 || offset + 5 > bytes.length)
            return -1L;
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        int b2 = bytes[offset + 2] & 0xFF;
        int b3 = bytes[offset + 3] & 0xFF;
        int b4 = bytes[offset + 4] & 0xFF;
        if ((b0 & 1) == 0 || (b2 & 1) == 0 || (b4 & 1) == 0)
            return -1L;
        return ((long) (b0 & 0x0E) << 29)
                | ((long) b1 << 22)
                | ((long) (b2 & 0xFE) << 14)
                | ((long) b3 << 7)
                | ((long) (b4 & 0xFE) >> 1);
    }
}
