package opensagetv.vibe.miniclient.dvd;

final class MpegPsUtil {
    static final int INCOMPLETE = -1;
    static final int INVALID = -2;
    static final int ZERO_LENGTH = 0;

    private MpegPsUtil() {}

    static boolean hasPrefix(byte[] b, int p, int limit) {
        return p + 4 <= limit && b[p] == 0 && b[p + 1] == 0 && b[p + 2] == 1;
    }

    static boolean isProgramStreamStartCode(int id) {
        // MPEG video elementary start-code values stop at 0xb8. Values 0xb9
        // through 0xff are Program Stream/system/PES ids, including reserved and
        // extended ids that a robust scanner must still skip by declared length.
        return id >= 0xb9 && id <= 0xff;
    }

    static int findProgramStreamStart(byte[] b, int from, int limit) {
        for (int p = Math.max(0, from); p + 4 <= limit; p++) {
            if (hasPrefix(b, p, limit) && isProgramStreamStartCode(DvdByteUtil.u8(b, p + 3))) {
                return p;
            }
        }
        return -1;
    }

    /** Returns total packet bytes, ZERO_LENGTH, INCOMPLETE or INVALID. */
    static int packetLength(byte[] b, int start, int limit) {
        if (!hasPrefix(b, start, limit)) return INVALID;
        int id = DvdByteUtil.u8(b, start + 3);
        if (!isProgramStreamStartCode(id)) return INVALID;

        if (id == 0xb9) return 4;
        if (id == 0xba) {
            if (start + 5 > limit) return INCOMPLETE;
            int marker = DvdByteUtil.u8(b, start + 4);
            if ((marker & 0xc0) == 0x40) { // MPEG-2 pack header
                if (start + 14 > limit) return INCOMPLETE;
                return 14 + (DvdByteUtil.u8(b, start + 13) & 0x07);
            }
            if ((marker & 0xf0) == 0x20) { // MPEG-1 pack header
                return start + 12 <= limit ? 12 : INCOMPLETE;
            }
            return INVALID;
        }

        if (start + 6 > limit) return INCOMPLETE;
        int payloadLength = DvdByteUtil.u16be(b, start + 4);
        return payloadLength == 0 ? ZERO_LENGTH : 6 + payloadLength;
    }

    static long readPts(byte[] b, int offset, int limit) {
        if (offset < 0 || offset + 5 > limit) return -1;
        if ((b[offset] & 1) == 0 || (b[offset + 2] & 1) == 0 || (b[offset + 4] & 1) == 0) {
            return -1;
        }
        return (((long) (b[offset] & 0x0e)) << 29)
                | ((long) DvdByteUtil.u8(b, offset + 1) << 22)
                | ((long) (b[offset + 2] & 0xfe) << 14)
                | ((long) DvdByteUtil.u8(b, offset + 3) << 7)
                | ((long) (b[offset + 4] & 0xfe) >>> 1);
    }

    static void writePts(byte[] b, int offset, long pts90k) {
        long pts = DvdPtsClock.wrap33(pts90k);
        int prefix = b[offset] & 0xf0;
        b[offset] = (byte) (prefix | ((pts >>> 29) & 0x0e) | 1);
        b[offset + 1] = (byte) (pts >>> 22);
        b[offset + 2] = (byte) (((pts >>> 14) & 0xfe) | 1);
        b[offset + 3] = (byte) (pts >>> 7);
        b[offset + 4] = (byte) (((pts << 1) & 0xfe) | 1);
    }

    /** Returns the private-stream payload position and PTS, or null if malformed. */
    static PesPayload parsePesPayload(byte[] packet, int start, int end) {
        int p = start + 6;
        if (p >= end) return null;
        long pts = -1;

        if (p + 3 <= end && (DvdByteUtil.u8(packet, p) & 0xc0) == 0x80) {
            int flags = DvdByteUtil.u8(packet, p + 1);
            int headerLength = DvdByteUtil.u8(packet, p + 2);
            int headerStart = p + 3;
            int payload = headerStart + headerLength;
            if (payload > end) return null;
            if ((flags & 0x80) != 0) pts = readPts(packet, headerStart, end);
            return new PesPayload(payload, pts);
        }

        // MPEG-1 PES header.
        while (p < end && DvdByteUtil.u8(packet, p) == 0xff) p++;
        if (p + 2 <= end && (DvdByteUtil.u8(packet, p) & 0xc0) == 0x40) p += 2;
        if (p >= end) return null;
        int marker = DvdByteUtil.u8(packet, p) & 0xf0;
        if (marker == 0x20) {
            pts = readPts(packet, p, end);
            p += 5;
        } else if (marker == 0x30) {
            pts = readPts(packet, p, end);
            p += 10;
        } else if (DvdByteUtil.u8(packet, p) == 0x0f) {
            p++;
        } else {
            return null;
        }
        return p <= end ? new PesPayload(p, pts) : null;
    }

    static final class PesPayload {
        final int offset;
        final long pts90k;

        PesPayload(int offset, long pts90k) {
            this.offset = offset;
            this.pts90k = pts90k;
        }
    }
}

