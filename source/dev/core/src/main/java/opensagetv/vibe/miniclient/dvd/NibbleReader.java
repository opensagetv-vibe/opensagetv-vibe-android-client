package opensagetv.vibe.miniclient.dvd;

/** Bounds-checked nibble reader for DVD SPU 2-bit RLE fields. */
final class NibbleReader {
    private final byte[] data;
    private final int endNibble;
    private int nibbleOffset;

    NibbleReader(byte[] data, int byteOffset, int endByteExclusive) {
        this.data = data;
        this.nibbleOffset = byteOffset * 2;
        this.endNibble = Math.min(data.length, endByteExclusive) * 2;
    }

    int read() {
        if (nibbleOffset < 0 || nibbleOffset >= endNibble) return -1;
        int byteIndex = nibbleOffset >>> 1;
        int value = (nibbleOffset & 1) == 0
                ? (DvdByteUtil.u8(data, byteIndex) >>> 4)
                : (DvdByteUtil.u8(data, byteIndex) & 0x0f);
        nibbleOffset++;
        return value;
    }

    void alignByte() {
        if ((nibbleOffset & 1) != 0) nibbleOffset++;
    }

    int bytePosition() {
        return nibbleOffset >>> 1;
    }
}

