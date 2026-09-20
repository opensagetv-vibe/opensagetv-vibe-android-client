/*
 * Copyright 2026 OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0.
 */
package opensagetv.vibe.miniclient.graphics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnifiedGraphicsCapabilityTest {
    @Test
    public void disabledModePreservesExistingHandshake() {
        assertEquals("", UnifiedGraphicsCapability.propertyValue(false));
        assertEquals("UNIFIED", UnifiedGraphicsCapability.propertyValue(true));
    }

    @Test
    public void imageFormatAndVideoMasksMatchSageTvCore() {
        assertTrue(UnifiedGraphicsCapability.isHiresYuvFormat(256));
        assertFalse(UnifiedGraphicsCapability.isHiresYuvFormat(0));
        assertEquals(1, UnifiedGraphicsCapability.VIDEO_MODE_SOURCE);
        assertEquals(2, UnifiedGraphicsCapability.VIDEO_MODE_OUTPUT);
        assertEquals(4, UnifiedGraphicsCapability.VIDEO_MODE_ALPHA);
        assertEquals(8, UnifiedGraphicsCapability.VIDEO_MODE_HANDLE);
        assertEquals(16, UnifiedGraphicsCapability.VIDEO_MODE_TOP_LEFT_ORIGIN);
    }

    @Test
    public void optionalFormatKeepsOldEightByteCommandsCompatible() {
        byte[] command = new byte[16];
        command[12] = 0;
        command[13] = 0;
        command[14] = 1;
        command[15] = 0;
        assertEquals(0, UnifiedGraphicsCapability.optionalImageFormat(command, 8, 8));
        assertEquals(256, UnifiedGraphicsCapability.optionalImageFormat(command, 8, 12));
    }
}
