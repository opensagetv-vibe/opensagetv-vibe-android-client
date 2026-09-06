package opensagetv.vibe.miniclient.dvd;

/** Decodes a DVD PCI 32-bit color/contrast word into four pixel-slot mappings. */
public final class DvdButtonPalette {
    private DvdButtonPalette() {}

    public static int[] colors(int word) {
        return new int[] {
                (word >>> 16) & 0x0f,
                (word >>> 20) & 0x0f,
                (word >>> 24) & 0x0f,
                (word >>> 28) & 0x0f
        };
    }

    public static int[] alpha(int word) {
        return new int[] {
                word & 0x0f,
                (word >>> 4) & 0x0f,
                (word >>> 8) & 0x0f,
                (word >>> 12) & 0x0f
        };
    }
}

