package opensagetv.vibe.miniclient.dvd;

final class DvdByteUtil {
    private DvdByteUtil() {}

    static int u8(byte[] b, int o) {
        return b[o] & 0xff;
    }

    static int u16be(byte[] b, int o) {
        return (u8(b, o) << 8) | u8(b, o + 1);
    }

    static int i32be(byte[] b, int o) {
        return (u8(b, o) << 24) | (u8(b, o + 1) << 16) |
                (u8(b, o + 2) << 8) | u8(b, o + 3);
    }

    static long u32be(byte[] b, int o) {
        return i32be(b, o) & 0xffffffffL;
    }

    static void putI32be(byte[] b, int o, int value) {
        b[o] = (byte) (value >>> 24);
        b[o + 1] = (byte) (value >>> 16);
        b[o + 2] = (byte) (value >>> 8);
        b[o + 3] = (byte) value;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

