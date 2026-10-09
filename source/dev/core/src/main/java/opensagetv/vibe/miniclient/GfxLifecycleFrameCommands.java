/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import opensagetv.vibe.miniclient.uibridge.UIRenderer;

/** Executes the INIT/DEINIT and frame-boundary GFX command family. */
final class GfxLifecycleFrameCommands {
    private final MiniClient client;
    private final UIRenderer<?> renderer;

    GfxLifecycleFrameCommands(MiniClient client, UIRenderer<?> renderer) {
        this.client = client;
        this.renderer = renderer;
    }

    int execute(int command, int[] hasReturn) {
        switch (command) {
            case GFXCMD2.GFXCMD_INIT:
                hasReturn[0] = 1;
                renderer.GFXCMD_INIT();
                return 1;
            case GFXCMD2.GFXCMD_DEINIT:
                renderer.GFXCMD_DEINIT();
                return 0;
            case GFXCMD2.GFXCMD_FLIPBUFFER:
                hasReturn[0] = 1;
                renderer.flipBuffer();
                client.getImageCache().flushMissingDrawRepaint();
                return 0;
            case GFXCMD2.GFXCMD_STARTFRAME:
                renderer.startFrame();
                return 0;
            default:
                throw new IllegalArgumentException("Not a lifecycle/frame command: " + command);
        }
    }
}
