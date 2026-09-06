/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package opensagetv.vibe.miniclient;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/** Owns the replaceable GFX input and event-output stream wrappers. */
final class ConnectionProtocolStreams {
    static final class OpenStreams {
        final DataInputStream gfxInput;
        final DataOutputStream eventOutput;

        OpenStreams(DataInputStream gfxInput, DataOutputStream eventOutput) {
            this.gfxInput = gfxInput;
            this.eventOutput = eventOutput;
        }
    }

    private DataInputStream gfxInput;
    private DataOutputStream eventOutput;

    synchronized OpenStreams open(Socket socket) throws IOException {
        if (socket == null)
            throw new IOException("Cannot open protocol streams without a GFX socket");
        return open(socket.getInputStream(), socket.getOutputStream());
    }

    synchronized OpenStreams open(InputStream input, OutputStream output) throws IOException {
        if (input == null || output == null)
            throw new IOException("Cannot open protocol streams without both directions");
        close();
        gfxInput = new DataInputStream(input);
        eventOutput = new DataOutputStream(new BufferedOutputStream(output));
        return new OpenStreams(gfxInput, eventOutput);
    }

    synchronized boolean isOpen() {
        return gfxInput != null && eventOutput != null;
    }

    synchronized void close() {
        DataInputStream oldInput = gfxInput;
        DataOutputStream oldOutput = eventOutput;
        gfxInput = null;
        eventOutput = null;
        closeQuietly(oldInput);
        closeQuietly(oldOutput);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null)
            return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
