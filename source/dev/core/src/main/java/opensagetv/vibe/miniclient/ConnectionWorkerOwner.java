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

import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Retains the long-lived protocol workers belonging to one connection.
 *
 * Socket closure remains the mechanism that unblocks protocol reads. This
 * owner adds one idempotent close boundary and a bounded wait; it deliberately
 * does not merge queues, change thread affinity, or interrupt Media/GFX work
 * before their sockets have been closed by {@link MiniClientConnection}.
 */
final class ConnectionWorkerOwner
{
    static final long DEFAULT_CLOSE_WAIT_MS = 2000L;

    private final AtomicBoolean closeStarted = new AtomicBoolean();
    private final CountDownLatch mediaWorkerStarted = new CountDownLatch(1);
    private volatile Thread mediaWorker;
    private volatile Thread gfxWorker;
    private volatile Thread gfxReadWorker;
    private volatile Thread eventRouterWorker;
    private volatile Socket mediaSocket;
    private volatile Socket gfxSocket;

    Socket mediaSocket()
    {
        return mediaSocket;
    }

    Socket gfxSocket()
    {
        return gfxSocket;
    }

    void setMediaSocket(Socket socket)
    {
        mediaSocket = socket;
    }

    void setGfxSocket(Socket socket)
    {
        gfxSocket = socket;
    }

    void closeAndClearMediaSocket()
    {
        Socket socket = mediaSocket;
        mediaSocket = null;
        closeQuietly(socket);
    }

    void closeSockets()
    {
        closeGfxSocket();
        closeMediaSocket();
    }

    void closeGfxSocket()
    {
        closeQuietly(gfxSocket);
    }

    void closeMediaSocket()
    {
        closeQuietly(mediaSocket);
    }

    private static void closeQuietly(Socket socket)
    {
        if (socket == null)
            return;
        try
        {
            socket.close();
        }
        catch (Exception ignored)
        {
        }
    }

    void retainMedia(Thread worker)
    {
        mediaWorker = worker;
    }

    void markMediaWorkerStarted()
    {
        mediaWorkerStarted.countDown();
    }

    boolean awaitMediaWorkerStarted(long timeoutMs)
    {
        try
        {
            return mediaWorkerStarted.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void retainGfx(Thread worker)
    {
        gfxWorker = worker;
    }

    void retainGfxRead(Thread worker)
    {
        gfxReadWorker = worker;
    }

    void retainEventRouter(Thread worker)
    {
        eventRouterWorker = worker;
    }

    boolean beginClose()
    {
        return closeStarted.compareAndSet(false, true);
    }

    boolean isCloseStarted()
    {
        return closeStarted.get();
    }

    void interruptEventRouter()
    {
        Thread worker = eventRouterWorker;
        if (worker != null)
            worker.interrupt();
    }

    /** Wait no longer than one shared deadline for every retained worker. */
    void awaitStopped(long timeoutMs)
    {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1000000L;
        Thread current = Thread.currentThread();
        Thread[] workers = new Thread[] {
                mediaWorker, gfxWorker, gfxReadWorker, eventRouterWorker
        };
        for (Thread worker : workers)
        {
            if (worker == null || worker == current || !worker.isAlive())
                continue;
            long remainingNs = deadline - System.nanoTime();
            if (remainingNs <= 0L)
                return;
            long waitMs = Math.max(1L, remainingNs / 1000000L);
            try
            {
                worker.join(waitMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
