/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GfxCommandFamilyTest {
    @Test
    public void representativeCommandsHaveStableFamilies() {
        assertEquals(GfxCommandFamily.Family.LIFECYCLE_FRAME,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_STARTFRAME));
        assertEquals(GfxCommandFamily.Family.DRAWING,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_DRAWTEXT));
        assertEquals(GfxCommandFamily.Family.IMAGE_CACHE,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_LOADIMAGECOMPRESSED));
        assertEquals(GfxCommandFamily.Family.FONT,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_LOADFONTSTREAM));
        assertEquals(GfxCommandFamily.Family.SURFACE_VIDEO,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_SETVIDEOPROP));
        assertEquals(GfxCommandFamily.Family.TRANSFORM_BATCH,
                GfxCommandFamily.of(GFXCMD2.GFXCMD_TEXTUREBATCH));
        assertEquals(GfxCommandFamily.Family.UNKNOWN, GfxCommandFamily.of(999));
    }
}
