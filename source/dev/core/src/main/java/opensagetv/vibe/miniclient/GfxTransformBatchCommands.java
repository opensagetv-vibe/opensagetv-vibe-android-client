/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes transform/batch commands while retaining historical support policy. */
final class GfxTransformBatchCommands {
    private static final Logger log = LoggerFactory.getLogger(GfxTransformBatchCommands.class);

    int execute(int command, int length, byte[] data) {
        switch (command) {
            case GFXCMD2.GFXCMD_TEXTUREBATCH:
                if (length >= 8) {
                    log.debug("Texture batch command received count={} size={}",
                            GFXCMD2.readInt(0, data), GFXCMD2.readInt(4, data));
                }
                return 0;
            case GFXCMD2.GFXCMD_PUSHTRANSFORM:
            case GFXCMD2.GFXCMD_POPTRANSFORM:
                log.error("GFXCMD Unhandled Command: {}", command);
                return -1;
            default:
                throw new IllegalArgumentException("Not a transform/batch command: " + command);
        }
    }
}
