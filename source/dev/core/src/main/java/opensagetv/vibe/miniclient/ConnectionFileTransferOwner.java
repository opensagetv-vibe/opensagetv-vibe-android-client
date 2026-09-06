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

import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import opensagetv.vibe.miniclient.logging.ILogger;

/** Owns concurrent remote filesystem transfers for exactly one connection. */
final class ConnectionFileTransferOwner
{
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final Object lock = new Object();
    private final Set<Transfer> active = new HashSet<Transfer>();
    private final ILogger log;
    private boolean closed;

    ConnectionFileTransferOwner(ILogger log)
    {
        this.log = log;
    }

    boolean start(boolean download, Socket socket, OutputStream output,
                  InputStream input, long fileSize, RandomAccessFile file)
    {
        Transfer transfer = new Transfer(download, socket, output, input, fileSize, file);
        synchronized (lock)
        {
            if (closed)
            {
                transfer.closeResources();
                return false;
            }
            active.add(transfer);
        }
        transfer.thread.start();
        return true;
    }

    void close()
    {
        ArrayList<Transfer> snapshot;
        synchronized (lock)
        {
            if (closed)
                return;
            closed = true;
            snapshot = new ArrayList<Transfer>(active);
        }
        for (Transfer transfer : snapshot)
        {
            transfer.closeResources();
            transfer.thread.interrupt();
        }
    }

    int activeCount()
    {
        synchronized (lock)
        {
            return active.size();
        }
    }

    private final class Transfer implements Runnable
    {
        final boolean download;
        final Socket socket;
        final OutputStream output;
        final InputStream input;
        final long fileSize;
        final RandomAccessFile file;
        final Thread thread;

        Transfer(boolean download, Socket socket, OutputStream output,
                 InputStream input, long fileSize, RandomAccessFile file)
        {
            this.download = download;
            this.socket = socket;
            this.output = output;
            this.input = input;
            this.fileSize = fileSize;
            this.file = file;
            thread = new Thread(this, "SageTV-FS-Xfer-" + NEXT_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run()
        {
            byte[] buffer = new byte[16384];
            try
            {
                long remaining = fileSize;
                while (remaining > 0L)
                {
                    int size = (int) Math.min(remaining, buffer.length);
                    if (!download)
                    {
                        file.readFully(buffer, 0, size);
                        output.write(buffer, 0, size);
                    }
                    else
                    {
                        size = input.read(buffer, 0, size);
                        if (size < 0)
                            throw new EOFException();
                        file.write(buffer, 0, size);
                    }
                    remaining -= size;
                }
                if (!download)
                    output.flush();
                else
                {
                    file.close();
                    output.write(new byte[] {0, 0, 0, 0});
                }
                log.logDebug("Finished Remote FS operation!");
            }
            catch (Exception e)
            {
                log.logError("ERROR w/ remote FS operation", e);
            }
            finally
            {
                closeResources();
                synchronized (lock)
                {
                    active.remove(this);
                }
            }
        }

        void closeResources()
        {
            closeQuietly(socket);
            closeQuietly(output);
            closeQuietly(input);
            closeQuietly(file);
        }
    }

    private static void closeQuietly(java.io.Closeable resource)
    {
        if (resource == null)
            return;
        try
        {
            resource.close();
        }
        catch (Exception ignored)
        {
        }
    }
}
