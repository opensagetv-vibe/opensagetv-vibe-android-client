package opensagetv.vibe.miniclient.dvd;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

/** Random valid-SPU round trips covering variable-length RLE and field interlace. */
public final class DvdRleRoundTripSelfTest {
    public static void main(String[] args) {
        Random random = new Random(0x445644524c45L);
        DvdSpuDecoder decoder = new DvdSpuDecoder(new DvdDiagnostics());
        for (int test = 0; test < 250; test++) {
            int width = 1 + random.nextInt(120);
            int height = 1 + random.nextInt(40);
            byte[] expected = makePixels(random, width, height);
            byte[] packet = makePacket(expected, width, height, 13, 7);
            List<DvdSpuFrame> frames = decoder.decode(new DvdSpuPacket(0x20, 90000, packet));
            TestData.check(frames.size() == 1, "RLE frame count " + test);
            DvdSpuFrame frame = frames.get(0);
            TestData.check(frame.x == 13 && frame.y == 7
                            && frame.width == width && frame.height == height,
                    "RLE geometry " + test);
            TestData.check(java.util.Arrays.equals(frame.pixels, expected),
                    "RLE pixels " + test);
        }
        System.out.println("PASS DvdRleRoundTripSelfTest");
    }

    private static byte[] makePixels(Random random, int width, int height) {
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                int color = random.nextInt(4);
                int run = 1 + random.nextInt(Math.min(200, width - x));
                for (int i = 0; i < run; i++) pixels[y * width + x + i] = (byte) color;
                x += run;
            }
        }
        return pixels;
    }

    private static byte[] makePacket(byte[] pixels, int width, int height, int x, int y) {
        byte[] top = encodeField(pixels, width, height, 0);
        byte[] bottom = encodeField(pixels, width, height, 1);
        int topOffset = 4;
        int bottomOffset = topOffset + top.length;
        int controlOffset = bottomOffset + bottom.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(0); // packet size
        out.write(controlOffset >>> 8); out.write(controlOffset);
        out.write(top, 0, top.length);
        out.write(bottom, 0, bottom.length);

        out.write(0); out.write(0); // DCSQ date
        out.write(controlOffset >>> 8); out.write(controlOffset); // terminal self link
        out.write(0x03); out.write(0x01); out.write(0x23);
        out.write(0x04); out.write(0xff); out.write(0xff);
        out.write(0x05);
        writeArea(out, x, x + width - 1, y, y + height - 1);
        out.write(0x06);
        out.write(topOffset >>> 8); out.write(topOffset);
        out.write(bottomOffset >>> 8); out.write(bottomOffset);
        out.write(0x01);
        out.write(0xff);
        return TestData.patchSize(out.toByteArray());
    }

    private static byte[] encodeField(byte[] pixels, int width, int height, int firstLine) {
        NibbleWriter writer = new NibbleWriter();
        for (int y = firstLine; y < height; y += 2) {
            writer.alignByte();
            int x = 0;
            while (x < width) {
                int color = pixels[y * width + x] & 3;
                int run = 1;
                while (x + run < width && run < 255
                        && (pixels[y * width + x + run] & 3) == color) {
                    run++;
                }
                if (x + run == width) {
                    // Four-nibble zero-length code means fill the remainder of this line.
                    writer.write(0); writer.write(0); writer.write(0); writer.write(color);
                    x = width;
                } else {
                    writeRleCode(writer, (run << 2) | color);
                    x += run;
                }
            }
            writer.alignByte();
        }
        return writer.toByteArray();
    }

    private static void writeRleCode(NibbleWriter writer, int code) {
        if (code >= 0x100) {
            writer.write((code >>> 12) & 0x0f);
            writer.write((code >>> 8) & 0x0f);
            writer.write((code >>> 4) & 0x0f);
            writer.write(code & 0x0f);
        } else if (code >= 0x40) {
            writer.write((code >>> 8) & 0x0f);
            writer.write((code >>> 4) & 0x0f);
            writer.write(code & 0x0f);
        } else if (code >= 0x10) {
            writer.write((code >>> 4) & 0x0f);
            writer.write(code & 0x0f);
        } else {
            writer.write(code & 0x0f);
        }
    }

    private static void writeArea(ByteArrayOutputStream out, int x1, int x2, int y1, int y2) {
        out.write((x1 >>> 4) & 0xff);
        out.write(((x1 & 0x0f) << 4) | ((x2 >>> 8) & 0x0f));
        out.write(x2 & 0xff);
        out.write((y1 >>> 4) & 0xff);
        out.write(((y1 & 0x0f) << 4) | ((y2 >>> 8) & 0x0f));
        out.write(y2 & 0xff);
    }

    private static final class NibbleWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int high = -1;

        void write(int value) {
            int nibble = value & 0x0f;
            if (high < 0) {
                high = nibble;
            } else {
                out.write((high << 4) | nibble);
                high = -1;
            }
        }

        void alignByte() {
            if (high >= 0) write(0);
        }

        byte[] toByteArray() {
            alignByte();
            return out.toByteArray();
        }
    }
}

