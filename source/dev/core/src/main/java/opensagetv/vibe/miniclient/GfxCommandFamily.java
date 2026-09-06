/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

/** Stable command-family boundaries used to decompose {@link GFXCMD2}. */
final class GfxCommandFamily {
    enum Family {
        LIFECYCLE_FRAME,
        DRAWING,
        IMAGE_CACHE,
        FONT,
        SURFACE_VIDEO,
        TRANSFORM_BATCH,
        UNKNOWN
    }

    private GfxCommandFamily() {
    }

    static Family of(int command) {
        switch (command) {
            case GFXCMD2.GFXCMD_INIT:
            case GFXCMD2.GFXCMD_DEINIT:
            case GFXCMD2.GFXCMD_FLIPBUFFER:
            case GFXCMD2.GFXCMD_STARTFRAME:
                return Family.LIFECYCLE_FRAME;
            case GFXCMD2.GFXCMD_DRAWRECT:
            case GFXCMD2.GFXCMD_FILLRECT:
            case GFXCMD2.GFXCMD_CLEARRECT:
            case GFXCMD2.GFXCMD_DRAWOVAL:
            case GFXCMD2.GFXCMD_FILLOVAL:
            case GFXCMD2.GFXCMD_DRAWROUNDRECT:
            case GFXCMD2.GFXCMD_FILLROUNDRECT:
            case GFXCMD2.GFXCMD_DRAWTEXT:
            case GFXCMD2.GFXCMD_DRAWTEXTURED:
            case GFXCMD2.GFXCMD_DRAWLINE:
            case GFXCMD2.GFXCMD_DRAWTEXTUREDDIFFUSE:
                return Family.DRAWING;
            case GFXCMD2.GFXCMD_LOADIMAGE:
            case GFXCMD2.GFXCMD_UNLOADIMAGE:
            case GFXCMD2.GFXCMD_LOADIMAGELINE:
            case GFXCMD2.GFXCMD_PREPIMAGE:
            case GFXCMD2.GFXCMD_LOADIMAGECOMPRESSED:
            case GFXCMD2.GFXCMD_XFMIMAGE:
            case GFXCMD2.GFXCMD_LOADCACHEDIMAGE:
            case GFXCMD2.GFXCMD_LOADIMAGETARGETED:
            case GFXCMD2.GFXCMD_PREPIMAGETARGETED:
                return Family.IMAGE_CACHE;
            case GFXCMD2.GFXCMD_LOADFONT:
            case GFXCMD2.GFXCMD_UNLOADFONT:
            case GFXCMD2.GFXCMD_LOADFONTSTREAM:
                return Family.FONT;
            case GFXCMD2.GFXCMD_CREATESURFACE:
            case GFXCMD2.GFXCMD_SETTARGETSURFACE:
            case GFXCMD2.GFXCMD_SETVIDEOPROP:
                return Family.SURFACE_VIDEO;
            case GFXCMD2.GFXCMD_PUSHTRANSFORM:
            case GFXCMD2.GFXCMD_POPTRANSFORM:
            case GFXCMD2.GFXCMD_TEXTUREBATCH:
                return Family.TRANSFORM_BATCH;
            default:
                return Family.UNKNOWN;
        }
    }
}
