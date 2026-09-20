/*
 * Copyright 2026 OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0.
 */
package opensagetv.vibe.miniclient.graphics;

import opensagetv.vibe.miniclient.prefs.PrefStore;

/**
 * The small, explicit part of the HD200/HD300 unified graphics contract that
 * Vibe can safely negotiate with a stock SageTV server.
 *
 * <p>The preference is deliberately opt-in.  An empty response when it is
 * disabled preserves the existing Android capability negotiation (regular
 * high-resolution surfaces remain separate).  The constants mirror the
 * values in SageTV's {@code MiniClientSageRenderer}; they are protocol values,
 * not an Android decoder selection.</p>
 */
public final class UnifiedGraphicsCapability {
    public static final String PROPERTY = "GFX_YUV_IMAGE_CACHE";
    public static final String VALUE_UNIFIED = "UNIFIED";

    /** SageTV IMAGE_FORMAT_HIRESYUV (Y plane followed by interleaved U/V). */
    public static final int IMAGE_FORMAT_HIRESYUV = 256;
    public static final int IMAGE_FORMAT_DEFAULT = 0;

    public static final int VIDEO_MODE_SOURCE = 1;
    public static final int VIDEO_MODE_OUTPUT = 2;
    public static final int VIDEO_MODE_ALPHA = 4;
    public static final int VIDEO_MODE_HANDLE = 8;
    public static final int VIDEO_MODE_TOP_LEFT_ORIGIN = 16;

    private UnifiedGraphicsCapability() {
    }

    public static boolean isEnabled(PrefStore prefs) {
        return prefs != null && prefs.getBoolean(
                PrefStore.Keys.unified_graphics_surfaces, false);
    }

    /** Returns the exact GetProperty reply; disabled intentionally means empty. */
    public static String propertyValue(PrefStore prefs) {
        return propertyValue(isEnabled(prefs));
    }

    public static String propertyValue(boolean enabled) {
        return enabled ? VALUE_UNIFIED : "";
    }

    public static boolean isHiresYuvFormat(int format) {
        return format == IMAGE_FORMAT_HIRESYUV;
    }

    /**
     * Reads an optional image-format field from a GFX image command.  Older
     * SageTV servers send only width/height, so absence is the ordinary format.
     */
    public static int optionalImageFormat(byte[] commandData, int offset,
                                          int payloadLength) {
        return commandData != null && payloadLength >= offset + 4
                ? readInt(commandData, offset) : IMAGE_FORMAT_DEFAULT;
    }

    private static int readInt(byte[] data, int offset) {
        int wire = offset + 4; // GFX command data includes its four-byte header
        if (wire < 0 || wire + 4 > data.length)
            return IMAGE_FORMAT_DEFAULT;
        return ((data[wire] & 0xff) << 24)
                | ((data[wire + 1] & 0xff) << 16)
                | ((data[wire + 2] & 0xff) << 8)
                | (data[wire + 3] & 0xff);
    }
}
