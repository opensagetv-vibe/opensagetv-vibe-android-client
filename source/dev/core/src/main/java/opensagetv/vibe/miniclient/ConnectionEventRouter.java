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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import opensagetv.vibe.miniclient.logging.ILogger;

/** One bounded FIFO for all queued client-to-server event writes. */
final class ConnectionEventRouter extends Thread
{
    interface DispatchState
    {
        boolean shouldDispatch();
    }

    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(100);
    private final ConnectionLifecycleDiagnostics.Trace diagnostics;
    private final DispatchState dispatchState;
    private final ILogger log;

    ConnectionEventRouter(String name, ConnectionLifecycleDiagnostics.Trace diagnostics,
                          DispatchState dispatchState, ILogger log)
    {
        super(name);
        this.diagnostics = diagnostics;
        this.dispatchState = dispatchState;
        this.log = log;
    }

    void enqueue(Runnable event)
    {
        queue.add(event);
        diagnostics.eventQueued(queue.size());
    }

    @Override
    public void run()
    {
        diagnostics.workerStarted("event_router");
        while (true)
        {
            try
            {
                Runnable event = queue.take();
                diagnostics.eventDequeued(queue.size());
                if (dispatchState.shouldDispatch())
                    event.run();
            }
            catch (InterruptedException e)
            {
                Thread.interrupted();
                log.logWarning("EventRouterThread is shutting down");
                diagnostics.workerStopped("event_router");
                return;
            }
            catch (Throwable t)
            {
                log.logWarning("Event Processing Error", t);
                // One bad event must not terminate later event delivery.
            }
        }
    }
}
