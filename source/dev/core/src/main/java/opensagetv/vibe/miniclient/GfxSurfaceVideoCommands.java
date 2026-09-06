/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.uibridge.ImageHolder;
import opensagetv.vibe.miniclient.uibridge.Rectangle;
import opensagetv.vibe.miniclient.uibridge.UIRenderer;
import opensagetv.vibe.miniclient.util.VerboseLogging;

/** Executes surface allocation/selection and video-rectangle GFX commands. */
final class GfxSurfaceVideoCommands {
    private static final Logger log = LoggerFactory.getLogger(GfxSurfaceVideoCommands.class);

    private final MiniClient client;
    private final UIRenderer<?> renderer;
    private final GfxHandleAllocator handles;

    GfxSurfaceVideoCommands(MiniClient client, UIRenderer<?> renderer,
                            GfxHandleAllocator handles) {
        this.client = client;
        this.renderer = renderer;
        this.handles = handles;
    }

    int execute(int command, int length, byte[] data, int[] hasReturn) {
        switch (command) {
            case GFXCMD2.GFXCMD_CREATESURFACE:
                if (length >= 8) {
                    int width = i(data, 0);
                    int height = i(data, 4);
                    int handle = handles.next();
                    if (VerboseLogging.DETAILED_GFX) {
                        log.debug("GFXCMD=CREATESURFACE, handle:{}, {}x{}", handle, width, height);
                    }
                    ImageHolder<?> image = renderer.createSurface(handle, width, height);
                    image.setHandle(handle);
                    client.getImageCache().put(handle, image, width, height);
                    hasReturn[0] = 1;
                    return handle;
                }
                log.warn("Invalid len for GFXCMD_LOADIMAGE: {}", length);
                return 0;
            case GFXCMD2.GFXCMD_SETTARGETSURFACE:
                if (length == 4) {
                    int handle = i(data, 0);
                    if (VerboseLogging.DETAILED_GFX)
                        log.debug("GFXCMD=SETTARGETSURFACE, surface:{}", handle);
                    renderer.setTargetSurface(
                            handle, handle != 0 ? client.getImageCache().get(handle) : null);
                } else {
                    log.error("Invalid len for GFXCMD_SETTARGETSURFACE: {}", length);
                }
                return 0;
            case GFXCMD2.GFXCMD_SETVIDEOPROP:
                if (length >= 40) {
                    Rectangle source = new Rectangle(
                            i(data, 4), i(data, 8), i(data, 12), i(data, 16));
                    Rectangle destination = new Rectangle(
                            i(data, 20), i(data, 24), i(data, 28), i(data, 32));
                    MediaCmd media = client.getCurrentConnection().getMediaCmd();
                    if (media != null) {
                        MiniPlayerPlugin player = media.getPlaya();
                        if (player != null)
                            player.setVideoRectangles(source, destination, false);
                    }
                    renderer.setVideoBounds(source, destination);
                } else {
                    log.error("Invalid len for GFXCMD_SETVIDEOPROP {}", length);
                }
                return 0;
            default:
                throw new IllegalArgumentException("Not a surface/video command: " + command);
        }
    }

    private static int i(byte[] data, int offset) {
        return GFXCMD2.readInt(offset, data);
    }
}
