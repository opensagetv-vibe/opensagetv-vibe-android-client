/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes the legacy font command family without changing support policy. */
final class GfxFontCommands {
    private static final Logger log = LoggerFactory.getLogger(GfxFontCommands.class);

    private final MiniClient client;
    private final MiniClientConnection connection;

    GfxFontCommands(MiniClient client, MiniClientConnection connection) {
        this.client = client;
        this.connection = connection;
    }

    int execute(int command, int length, byte[] data) {
        switch (command) {
            case GFXCMD2.GFXCMD_LOADFONT:
                log.error("LOADFONT Not Implemented");
                return 0;
            case GFXCMD2.GFXCMD_UNLOADFONT:
                log.error("UNLOADFONT Not Implemented");
                return 0;
            case GFXCMD2.GFXCMD_LOADFONTSTREAM:
                if (length >= 8) {
                    int nameLength = GFXCMD2.readInt(0, data);
                    StringBuffer name = new StringBuffer();
                    for (int index = 0; index < nameLength - 1; index++)
                        name.append((char) data[8 + index]);
                    int dataLength = GFXCMD2.readInt(4 + nameLength, data);
                    if (length >= dataLength + 8 + nameLength) {
                        log.debug("Saving font {} to cache", name);
                        client.getImageCache().saveCacheData(
                                name.toString() + "-" + connection.getServerName(),
                                data, 12 + nameLength, dataLength);
                    }
                } else {
                    log.error("Invalid len for GFXCMD_LOADFONTSTREAM: {}", length);
                }
                return 0;
            default:
                throw new IllegalArgumentException("Not a font command: " + command);
        }
    }
}
