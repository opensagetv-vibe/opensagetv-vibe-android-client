package opensagetv.vibe.miniclient.dvd;

/** DVD CLUT conversion and fallback palette behavior. */
public final class YuvPalette {
    private YuvPalette() {}

    /** Sage/DVD CLUT entries are 0x00YYCrCb. */
    public static int toArgb(int yCrCb, int alpha4) {
        int y = (yCrCb >>> 16) & 0xff;
        int cr = (yCrCb >>> 8) & 0xff;
        int cb = yCrCb & 0xff;
        int a = (alpha4 & 0x0f) * 17;

        int r = clamp((298 * y + 459 * cr - 63514) >> 8);
        int g = clamp((298 * y - 55 * cb - 136 * cr + 19681) >> 8);
        int b = clamp((298 * y + 541 * cb - 73988) >> 8);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Conservative white/gray/black fallback used before a CLUT arrives. */
    public static int fallbackArgb(int slot, int alpha4) {
        int[] levels = {0, 255, 128, 0};
        int level = levels[slot & 3];
        int a = (alpha4 & 0x0f) * 17;
        return (a << 24) | (level << 16) | (level << 8) | level;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }
}

