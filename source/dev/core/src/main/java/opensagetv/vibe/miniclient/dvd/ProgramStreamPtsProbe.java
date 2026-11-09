package opensagetv.vibe.miniclient.dvd;

import java.util.Arrays;

/** Bounded read-only PES-origin probe reusing the existing MPEG-1/2 PS parser. */
public final class ProgramStreamPtsProbe
{
    private byte[] carry = new byte[0];
    private int skip;
    private long firstPts = -1L;

    public void reset() { carry = new byte[0]; skip = 0; firstPts = -1L; }
    public long firstPts90Khz() { return firstPts; }

    public void observe(byte[] bytes, int offset, int length)
    {
        if (firstPts >= 0 || bytes == null || offset < 0 || length <= 0
                || offset > bytes.length - length) return;
        while (length > 0 && firstPts < 0)
        {
            int size = Math.min(8192, length);
            feed(bytes, offset, size);
            offset += size;
            length -= size;
        }
    }

    private void feed(byte[] bytes, int offset, int length)
    {
        int discard = Math.min(skip, length);
        skip -= discard;
        offset += discard;
        length -= discard;
        if (length == 0) return;
        byte[] data = new byte[carry.length + length];
        System.arraycopy(carry, 0, data, 0, carry.length);
        System.arraycopy(bytes, offset, data, carry.length, length);
        carry = new byte[0];
        int cursor = 0;
        while (cursor + 4 <= data.length)
        {
            int start = MpegPsUtil.findProgramStreamStart(data, cursor, data.length);
            if (start < 0) { retain(data, Math.max(cursor, data.length - 3)); return; }
            int size = MpegPsUtil.packetLength(data, start, data.length);
            if (size == MpegPsUtil.INCOMPLETE) { retain(data, start); return; }
            if (size == MpegPsUtil.INVALID) { cursor = start + 4; continue; }
            int id = data[start + 3] & 255;
            if ((id >= 0xC0 && id <= 0xEF) || id == 0xBD)
            {
                int end = size > 0 ? start + Math.min(size, data.length - start) : data.length;
                MpegPsUtil.PesPayload header = MpegPsUtil.parsePesPayload(data, start, end);
                if (header != null && header.pts90k >= 0)
                {
                    firstPts = header.pts90k;
                    return; // No ongoing payload inspection once the origin is known.
                }
                if (header == null && data.length - start < 272
                        && (size == 0 || size > data.length - start))
                {
                    retain(data, start);
                    return;
                }
            }
            if (size == 0) cursor = start + 6; // Unbounded video PES: find next system start.
            else if (size > data.length - start)
            {
                skip = size - (data.length - start);
                return; // Skip declared payload; never scan arbitrary contained bytes as headers.
            }
            else cursor = start + size;
        }
        retain(data, cursor);
    }

    private void retain(byte[] data, int start)
    {
        // MPEG-2 optional PES headers are at most264 bytes including prefix;
        // MPEG-1 stuffing is shorter. Malformed input cannot grow this buffer.
        if (data.length - start <= 272)
            carry = Arrays.copyOfRange(data, Math.max(0, start), data.length);
        else
            carry = Arrays.copyOfRange(data, Math.max(0, data.length - 3), data.length);
    }
}
