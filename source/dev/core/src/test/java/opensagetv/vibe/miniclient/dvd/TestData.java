package opensagetv.vibe.miniclient.dvd;

import java.io.ByteArrayOutputStream;

final class TestData {
    private TestData() {}

    static byte[] simpleSpu(boolean forced) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(0); // size patched later
        out.write(0); out.write(8); // control offset
        out.write(0x90); // top: run 2, slot 1
        out.write(0xa0); // bottom: run 2, slot 2
        out.write(0); out.write(0); // padding
        int control = out.size();
        out.write(0); out.write(0); // date
        out.write(control >>> 8); out.write(control); // next=self
        out.write(0x03); out.write(0x01); out.write(0x23);
        out.write(0x04); out.write(0xff); out.write(0xff);
        out.write(0x05); out.write(0); out.write(0); out.write(1); out.write(0); out.write(0); out.write(1);
        out.write(0x06); out.write(0); out.write(4); out.write(0); out.write(5);
        out.write(forced ? 0x00 : 0x01);
        out.write(0xff);
        return patchSize(out.toByteArray());
    }

    static byte[] paletteUpdateAndStopSpu() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(0);
        out.write(0); out.write(8);
        out.write(0x90); out.write(0xa0); out.write(0); out.write(0);

        int first = out.size();
        out.write(0); out.write(0);
        int nextPosition = out.size(); out.write(0); out.write(0);
        out.write(0x03); out.write(0x01); out.write(0x23);
        out.write(0x04); out.write(0xff); out.write(0xff);
        out.write(0x05); out.write(0); out.write(0); out.write(1); out.write(0); out.write(0); out.write(1);
        out.write(0x06); out.write(0); out.write(4); out.write(0); out.write(5);
        out.write(0x01); out.write(0xff);

        int second = out.size();
        byte[] partial = out.toByteArray();
        partial[nextPosition] = (byte) (second >>> 8);
        partial[nextPosition + 1] = (byte) second;
        out.reset();
        out.write(partial, 0, partial.length);
        out.write(0); out.write(10); // 10 * 1024 ticks
        int thirdPosition = out.size(); out.write(0); out.write(0);
        out.write(0x03); out.write(0x45); out.write(0x67); // visual update while active
        out.write(0xff);

        int third = out.size();
        partial = out.toByteArray();
        partial[thirdPosition] = (byte) (third >>> 8);
        partial[thirdPosition + 1] = (byte) third;
        out.reset(); out.write(partial, 0, partial.length);
        out.write(0); out.write(20);
        out.write(third >>> 8); out.write(third);
        out.write(0x02); out.write(0xff);
        return patchSize(out.toByteArray());
    }

    static byte[] chgColconSpu() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(0);
        out.write(0); out.write(8);
        out.write(0x90); out.write(0xa0); out.write(0); out.write(0);
        int control = out.size();
        out.write(0); out.write(0);
        out.write(control >>> 8); out.write(control);
        out.write(0x03); out.write(0x01); out.write(0x23);
        out.write(0x04); out.write(0xff); out.write(0xff);
        out.write(0x05); out.write(0); out.write(0); out.write(1); out.write(0); out.write(0); out.write(1);
        out.write(0x06); out.write(0); out.write(4); out.write(0); out.write(5);
        out.write(0x07);
        out.write(0); out.write(16); // includes these two size bytes
        out.write(0); out.write(0); out.write(0x10); out.write(0); // y=0..0, one change
        out.write(0); out.write(0); // x=0
        out.write(0x44); out.write(0x44); out.write(0xff); out.write(0xff);
        out.write(0x0f); out.write(0xff); out.write(0xff); out.write(0xff);
        out.write(0x01); out.write(0xff);
        return patchSize(out.toByteArray());
    }

    static byte[] privateStreamPes(int streamId, long pts90k, byte[] spu) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int payloadLength = 3 + 5 + 1 + spu.length;
        out.write(0); out.write(0); out.write(1); out.write(0xbd);
        out.write(payloadLength >>> 8); out.write(payloadLength);
        out.write(0x80); out.write(0x80); out.write(5);
        byte[] pts = new byte[] {0x21, 0, 1, 0, 1};
        MpegPsUtil.writePts(pts, 0, pts90k);
        out.write(pts, 0, pts.length);
        out.write(streamId);
        out.write(spu, 0, spu.length);
        return out.toByteArray();
    }

    static byte[] mpeg2PackHeader() {
        return new byte[] {
                0, 0, 1, (byte) 0xba,
                0x44, 0, 4, 0, 4, 1, (byte) 0x89, (byte) 0xc3, (byte) 0xf8, 0
        };
    }

    static byte[] mpeg1PackHeader() {
        return new byte[] {
                0, 0, 1, (byte) 0xba,
                0x21, 0, 1, 0, 1, (byte) 0x80, 0, 1
        };
    }

    static byte[] zeroLengthVideoPes() {
        return zeroLengthVideoPes(0);
    }

    static byte[] zeroLengthVideoPes(long pts90k) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(0); out.write(1); out.write(0xe0);
        out.write(0); out.write(0);
        out.write(0x80); out.write(0x80); out.write(5);
        byte[] pts = new byte[] {0x21, 0, 1, 0, 1};
        MpegPsUtil.writePts(pts, 0, pts90k);
        out.write(pts, 0, pts.length);
        out.write(0); out.write(0); out.write(1); out.write(0xb3);
        out.write(0x2d); out.write(1); out.write(0xe0);
        return out.toByteArray();
    }

    static byte[] programStreamMap() {
        return new byte[] {0, 0, 1, (byte) 0xbc, 0, 4, 1, 2, 3, 4};
    }

    static byte[] extendedStreamPacket() {
        return new byte[] {0, 0, 1, (byte) 0xfd, 0, 3, 5, 6, 7};
    }

    static byte[] systemHeader() {
        return new byte[] {0, 0, 1, (byte) 0xbb, 0, 4, 1, 2, 3, 4};
    }

    static byte[] padding() {
        return new byte[] {0, 0, 1, (byte) 0xbe, 0, 3, 9, 8, 7};
    }

    static byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }

    static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) out.write(array, 0, array.length);
        return out.toByteArray();
    }

    static byte[] patchSize(byte[] packet) {
        packet[0] = (byte) (packet.length >>> 8);
        packet[1] = (byte) packet.length;
        return packet;
    }

    static void putI32(byte[] b, int offset, int value) {
        DvdByteUtil.putI32be(b, offset, value);
    }

    static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

