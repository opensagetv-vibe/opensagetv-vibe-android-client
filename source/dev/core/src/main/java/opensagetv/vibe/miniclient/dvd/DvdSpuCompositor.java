package opensagetv.vibe.miniclient.dvd;

import java.util.HashMap;
import java.util.Map;

/** Converts decoded SPU slots to ARGB with CHG_COLCON and button highlighting. */
public final class DvdSpuCompositor {
    public DvdArgbFrame compose(DvdSpuFrame frame, int[] clutYCrCb,
                                boolean clutValid, DvdHighlight highlight) {
        if (frame == null || !frame.display || frame.width <= 0 || frame.height <= 0) {
            return new DvdArgbFrame(frame == null ? 0 : frame.serial,
                    0, 0, 0, 0, new int[0],
                    frame == null ? -1 : frame.event90k, false);
        }

        boolean useClut = clutValid && clutYCrCb != null && clutYCrCb.length >= 16;
        int[] output = new int[frame.width * frame.height];
        int[] normal = palette(frame.colorIndex, frame.alpha, clutYCrCb, useClut);
        Map<Integer, int[]> paletteCache = new HashMap<Integer, int[]>();

        int[] highlightPalette = null;
        if (highlight != null && highlight.visible) {
            highlightPalette = paletteForWord(highlight.paletteWord, clutYCrCb, useClut, paletteCache);
        }

        for (int y = 0; y < frame.height; y++) {
            int absoluteY = frame.y + y;
            int row = y * frame.width;
            for (int x = 0; x < frame.width; x++) {
                int absoluteX = frame.x + x;
                int slot = frame.pixels[row + x] & 0x03;
                int argb = normal[slot];

                int changedWord = frame.colorContrastTable.paletteWordAt(absoluteX, absoluteY);
                if (changedWord != -1) {
                    argb = paletteForWord(changedWord, clutYCrCb, useClut, paletteCache)[slot];
                }
                // Selected/activated button state has final precedence over authored line controls.
                if (highlightPalette != null && highlight.contains(absoluteX, absoluteY)) {
                    argb = highlightPalette[slot];
                }
                output[row + x] = argb;
            }
        }

        return new DvdArgbFrame(frame.serial, frame.x, frame.y, frame.width, frame.height,
                output, frame.event90k, true);
    }

    private static int[] paletteForWord(int word, int[] clut, boolean clutValid,
                                        Map<Integer, int[]> cache) {
        Integer key = word;
        int[] value = cache.get(key);
        if (value != null) return value;
        value = palette(DvdButtonPalette.colors(word), DvdButtonPalette.alpha(word), clut, clutValid);
        cache.put(key, value);
        return value;
    }

    private static int[] palette(int[] indices, int[] alpha, int[] clut, boolean clutValid) {
        int[] result = new int[4];
        for (int slot = 0; slot < 4; slot++) {
            int index = indices[slot] & 0x0f;
            result[slot] = clutValid
                    ? YuvPalette.toArgb(clut[index], alpha[slot])
                    : YuvPalette.fallbackArgb(slot, alpha[slot]);
        }
        return result;
    }
}

