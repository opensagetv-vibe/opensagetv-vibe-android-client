/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.uibridge.UIRenderer;

/** Decodes and executes the primitive drawing GFX command family. */
final class GfxDrawingCommands {
    private static final Logger log = LoggerFactory.getLogger(GfxDrawingCommands.class);

    private final MiniClient client;
    private final UIRenderer<?> renderer;

    GfxDrawingCommands(MiniClient client, UIRenderer<?> renderer) {
        this.client = client;
        this.renderer = renderer;
    }

    int execute(int command, int length, byte[] data) {
        switch (command) {
            case GFXCMD2.GFXCMD_DRAWRECT:
                if (length == 36) {
                    renderer.drawRect(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20), i(data, 24), i(data, 28), i(data, 32));
                } else {
                    log.warn("Invalid len for GFXCMD_DRAWRECT: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_FILLRECT:
                if (length == 32) {
                    renderer.fillRect(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20), i(data, 24), i(data, 28));
                } else {
                    log.warn("Invalid len for GFXCMD_FILLRECT: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_CLEARRECT:
                if (length == 32) {
                    renderer.clearRect(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20), i(data, 24), i(data, 28));
                } else {
                    log.warn("Invalid len for GFXCMD_CLEARRECT: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_DRAWOVAL:
                if (length == 52) {
                    renderer.drawOval(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20), i(data, 24), i(data, 28), i(data, 32),
                            i(data, 36), i(data, 40), i(data, 44), i(data, 48));
                } else {
                    log.warn("Invalid len for GFXCMD_DRAWOVAL: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_FILLOVAL:
                if (length == 48) {
                    renderer.fillOval(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20), i(data, 24), i(data, 28),
                            i(data, 32), i(data, 36), i(data, 40), i(data, 44));
                } else {
                    log.warn("Invalid len for GFXCMD_FILLOVAL: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_DRAWROUNDRECT:
                if (length == 56) {
                    renderer.drawRoundRect(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20) * 2, i(data, 24), i(data, 28),
                            i(data, 32), i(data, 36), i(data, 40), i(data, 44),
                            i(data, 48), i(data, 52));
                } else {
                    log.warn("Invalid len for GFXCMD_DRAWROUNDRECT: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_FILLROUNDRECT:
                if (length == 52) {
                    renderer.fillRoundRect(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16) * 2, i(data, 20), i(data, 24), i(data, 28),
                            i(data, 32), i(data, 36), i(data, 40), i(data, 44), i(data, 48));
                } else {
                    log.warn("Invalid len for GFXCMD_FILLROUNDRECT: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_DRAWTEXT:
                // The Java renderer path has historically never implemented this command.
                log.warn("DRAWTEXT Not Implemented");
                return 0;
            case GFXCMD2.GFXCMD_DRAWTEXTURED:
                if (length == 40) {
                    int handle = i(data, 16);
                    renderer.drawTexture(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            handle, client.getImageCache().get(handle), i(data, 20), i(data, 24),
                            i(data, 28), i(data, 32), i(data, 36));
                    client.getImageCache().registerImageAccess(handle);
                } else {
                    log.warn("Invalid len for GFXCMD_DRAWTEXTURED: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_DRAWLINE:
                if (length == 24) {
                    renderer.drawLine(i(data, 0), i(data, 4), i(data, 8), i(data, 12),
                            i(data, 16), i(data, 20));
                } else {
                    log.warn("Invalid len for GFXCMD_DRAWLINE: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_DRAWTEXTUREDDIFFUSE:
                log.error("GFXCMD Unhandled Command: {}", command);
                return -1;
            default:
                throw new IllegalArgumentException("Not a drawing command: " + command);
        }
    }

    private static int i(byte[] data, int offset) {
        return GFXCMD2.readInt(offset, data);
    }
}
