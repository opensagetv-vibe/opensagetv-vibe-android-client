/*
 * Copyright 2026 OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0.
 */
package opensagetv.vibe.miniclient.graphics;

import java.nio.ByteBuffer;

/**
 * Bounded software bridge for SageTV's IMAGE_FORMAT_HIRESYUV wire payload.
 * SageTV sends one byte-per-pixel Y rows followed by rows containing
 * interleaved U/V bytes.  Keeping the conversion here makes both Android
 * renderers use the same, testable interpretation and avoids handing an
 * unknown payload to a GPU texture.
 */
public final class UnifiedYuvImage {
    private final int width;
    private final int height;
    private final byte[] y;
    private final byte[] uv;
    private final int[] argb;
    private int yRows;
    private int uvRows;

    public UnifiedYuvImage(int width, int height) {
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096)
            throw new IllegalArgumentException("invalid unified image size " + width + "x" + height);
        long pixels = (long) width * height;
        if (pixels > 16_777_216L)
            throw new IllegalArgumentException("unified image is too large");
        this.width = width;
        this.height = height;
        this.y = new byte[(int) pixels];
        this.uv = new byte[(int) pixels];
        this.argb = new int[(int) pixels];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void loadLine(int line, byte[] data, int offset, int length) {
        if (data == null || line < 0 || line >= height * 2 || offset < 0 || offset > data.length)
            return;
        int count = Math.min(width, Math.min(length, data.length - offset));
        if (count <= 0) return;
        if (line < height) {
            System.arraycopy(data, offset, y, line * width, count);
            yRows++;
        } else {
            int row = line - height;
            System.arraycopy(data, offset, uv, row * width, count);
            uvRows++;
            convertRow(row);
        }
    }

    public boolean isComplete() {
        return yRows >= height && uvRows >= height;
    }

    /** Copies one converted row as explicit R,G,B,A bytes for glTexSubImage2D. */
    public void copyRgbaRow(int row, ByteBuffer destination) {
        if (row < 0 || row >= height || destination == null) return;
        destination.clear();
        int start = row * width;
        for (int x = 0; x < width; x++) {
            int pixel = argb[start + x];
            destination.put((byte) ((pixel >>> 16) & 0xff));
            destination.put((byte) ((pixel >>> 8) & 0xff));
            destination.put((byte) (pixel & 0xff));
            destination.put((byte) ((pixel >>> 24) & 0xff));
        }
        destination.flip();
    }

    /** Copies a converted row into an Android Bitmap-compatible ARGB array. */
    public void copyArgbRow(int row, int[] destination) {
        if (row < 0 || row >= height || destination == null || destination.length < width)
            return;
        System.arraycopy(argb, row * width, destination, 0, width);
    }

    private void convertRow(int row) {
        int yOffset = row * width;
        for (int x = 0; x < width; x++) {
            int luma = y[yOffset + x] & 0xff;
            int chromaOffset = yOffset + (x & ~1);
            int u = uv[chromaOffset] & 0xff;
            int v = uv[Math.min(yOffset + (x & ~1) + 1, yOffset + width - 1)] & 0xff;
            int c = luma - 16;
            int d = u - 128;
            int e = v - 128;
            int red = clamp((298 * c + 409 * e + 128) >> 8);
            int green = clamp((298 * c - 100 * d - 208 * e + 128) >> 8);
            int blue = clamp((298 * c + 516 * d + 128) >> 8);
            argb[yOffset + x] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}
