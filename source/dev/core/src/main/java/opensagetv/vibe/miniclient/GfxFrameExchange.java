/*
 * Copyright 2026 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */
package opensagetv.vibe.miniclient;

/**
 * One-frame handoff between the blocking GFX reader and serial dispatcher.
 * The reader may reuse its buffers only after the exact frame is completed.
 */
final class GfxFrameExchange
{
    static final class Frame
    {
        final byte[] header;
        final byte[] body;
        final Throwable failure;

        Frame(byte[] header, byte[] body, Throwable failure)
        {
            this.header = header;
            this.body = body;
            this.failure = failure;
        }
    }

    private final ConnectionLifecycleDiagnostics.Trace diagnostics;
    private Frame pending;

    GfxFrameExchange(ConnectionLifecycleDiagnostics.Trace diagnostics)
    {
        this.diagnostics = diagnostics;
    }

    synchronized void awaitWritable()
    {
        while (pending != null)
        {
            try
            {
                wait(5000L);
            }
            catch (InterruptedException ignored)
            {
                Thread.interrupted();
            }
        }
    }

    synchronized void publish(byte[] header, byte[] body, int command)
    {
        if (pending != null)
            throw new IllegalStateException("GFX frame published before prior completion");
        pending = new Frame(header, body, null);
        diagnostics.gfxRead(command, 1);
        notifyAll();
    }

    synchronized void publishFailure(Throwable failure)
    {
        if (pending != null)
            throw new IllegalStateException("GFX failure published before prior completion");
        pending = new Frame(null, null, failure);
        diagnostics.observeGfxQueue(1);
        notifyAll();
    }

    synchronized Frame awaitFrame()
    {
        while (pending == null)
        {
            try
            {
                wait(5000L);
            }
            catch (InterruptedException ignored)
            {
                Thread.interrupted();
            }
        }
        return pending;
    }

    synchronized void complete(Frame frame)
    {
        if (pending != frame)
            throw new IllegalStateException("Completed GFX frame is not current");
        pending = null;
        diagnostics.observeGfxQueue(0);
        notifyAll();
    }
}
