/*
 * Copyright 2026 OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0.
 */
package opensagetv.vibe.miniclient.graphics;

import java.nio.ByteBuffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnifiedYuvImageTest {
    @Test
    public void assemblesYAndInterleavedUvRows() {
        UnifiedYuvImage image = new UnifiedYuvImage(2, 1);
        image.loadLine(0, new byte[] { (byte) 235, (byte) 235 }, 0, 2);
        image.loadLine(1, new byte[] { (byte) 128, (byte) 128 }, 0, 2);
        assertTrue(image.isComplete());
        ByteBuffer rgba = ByteBuffer.allocate(8);
        image.copyRgbaRow(0, rgba);
        assertEquals(255, rgba.get(0) & 0xff);
        assertEquals(255, rgba.get(1) & 0xff);
        assertEquals(255, rgba.get(2) & 0xff);
        assertEquals(255, rgba.get(3) & 0xff);
    }
}
