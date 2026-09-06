package opensagetv.vibe.miniclient;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded connection-order observations used by the debug/MCP physical gate.
 *
 * <p>This deliberately records lifecycle landmarks and counters only. It does
 * not retain protocol payloads, media paths, credentials, or command bodies,
 * and it performs no I/O. Release builds pay only a few atomic increments and
 * bounded lifecycle records while a connection is active.</p>
 */
public final class ConnectionLifecycleDiagnostics
{
    private static final int MAX_EVENTS = 64;
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static volatile Trace latest;

    private ConnectionLifecycleDiagnostics()
    {
    }

    static Trace begin(String serverAddress)
    {
        Trace trace = new Trace(NEXT_GENERATION.incrementAndGet(), clean(serverAddress));
        latest = trace;
        trace.lifecycle("connection_created");
        return trace;
    }

    public static String latestCompactWire()
    {
        Trace trace = latest;
        return trace == null ? "connectionTraceAvailable=false" : trace.compactWire();
    }

    static final class Trace
    {
        private final long generation;
        private final String serverAddress;
        private final long createdMonotonicMs = monotonicMs();
        private final AtomicLong eventSequence = new AtomicLong();
        private final AtomicLong mediaCommandCount = new AtomicLong();
        private final AtomicLong mediaReplyCount = new AtomicLong();
        private final AtomicLong gfxReadCount = new AtomicLong();
        private final AtomicLong gfxDispatchCount = new AtomicLong();
        private final AtomicLong gfxReplyCount = new AtomicLong();
        private final AtomicLong eventQueuedCount = new AtomicLong();
        private final AtomicLong eventDequeuedCount = new AtomicLong();
        private final AtomicLong reconnectCount = new AtomicLong();
        private final ArrayDeque<String> recent = new ArrayDeque<String>(MAX_EVENTS);

        private volatile boolean alive;
        private volatile boolean mediaWorkerRunning;
        private volatile boolean gfxWorkerRunning;
        private volatile boolean gfxReadWorkerRunning;
        private volatile boolean eventRouterRunning;
        private volatile boolean closeRequested;
        private volatile boolean closeComplete;
        private volatile int gfxQueueDepth;
        private volatile int gfxQueueMaxDepth;
        private volatile int eventQueueDepth;
        private volatile int eventQueueMaxDepth;

        private Trace(long generation, String serverAddress)
        {
            this.generation = generation;
            this.serverAddress = serverAddress;
        }

        void lifecycle(String event)
        {
            long sequence = eventSequence.incrementAndGet();
            String record = sequence + "@" + clean(event) + "@" + monotonicMs()
                    + "@" + clean(Thread.currentThread().getName());
            synchronized (recent)
            {
                while (recent.size() >= MAX_EVENTS)
                    recent.removeFirst();
                recent.addLast(record);
            }
        }

        void alive(boolean value)
        {
            alive = value;
            lifecycle(value ? "connection_alive" : "connection_not_alive");
        }

        void workerStarted(String worker)
        {
            setWorker(worker, true);
            lifecycle(worker + "_worker_started");
        }

        void workerStopped(String worker)
        {
            setWorker(worker, false);
            lifecycle(worker + "_worker_stopped");
        }

        private void setWorker(String worker, boolean running)
        {
            if ("media".equals(worker)) mediaWorkerRunning = running;
            else if ("gfx".equals(worker)) gfxWorkerRunning = running;
            else if ("gfx_read".equals(worker)) gfxReadWorkerRunning = running;
            else if ("event_router".equals(worker)) eventRouterRunning = running;
        }

        void mediaCommand(int command)
        {
            if (mediaCommandCount.incrementAndGet() == 1L)
                lifecycle("media_command_first_" + command);
        }

        void mediaReply(int command)
        {
            if (mediaReplyCount.incrementAndGet() == 1L)
                lifecycle("media_reply_first_" + command);
        }

        void gfxRead(int command, int depth)
        {
            if (gfxReadCount.incrementAndGet() == 1L)
                lifecycle("gfx_read_first_" + command);
            observeGfxQueue(depth);
        }

        void gfxDispatch(int command)
        {
            if (gfxDispatchCount.incrementAndGet() == 1L)
                lifecycle("gfx_dispatch_first_" + command);
        }

        void gfxReply(int command)
        {
            if (gfxReplyCount.incrementAndGet() == 1L)
                lifecycle("gfx_reply_first_" + command);
        }

        void observeGfxQueue(int depth)
        {
            gfxQueueDepth = Math.max(0, depth);
            if (gfxQueueDepth > gfxQueueMaxDepth)
                gfxQueueMaxDepth = gfxQueueDepth;
        }

        synchronized void eventQueued(int depth)
        {
            eventQueuedCount.incrementAndGet();
            observeEventQueue(depth);
        }

        synchronized void eventDequeued(int depth)
        {
            eventDequeuedCount.incrementAndGet();
            observeEventQueue(depth);
        }

        void observeEventQueue(int depth)
        {
            eventQueueDepth = Math.max(0, depth);
            if (eventQueueDepth > eventQueueMaxDepth)
                eventQueueMaxDepth = eventQueueDepth;
        }

        void reconnect(String phase)
        {
            if ("started".equals(phase))
                reconnectCount.incrementAndGet();
            lifecycle("reconnect_" + phase);
        }

        synchronized void closeRequested()
        {
            if (closeRequested) return;
            closeRequested = true;
            lifecycle("close_requested");
        }

        synchronized void closeComplete()
        {
            if (closeComplete) return;
            closeComplete = true;
            lifecycle("close_complete");
        }

        private synchronized String compactWire()
        {
            StringBuilder out = new StringBuilder();
            out.append("connectionTraceAvailable=true");
            out.append(";connectionGeneration=").append(generation);
            out.append(";connectionServer=").append(serverAddress);
            out.append(";connectionCreatedMonotonicMs=").append(createdMonotonicMs);
            out.append(";connectionAlive=").append(alive);
            out.append(";connectionCloseRequested=").append(closeRequested);
            out.append(";connectionCloseComplete=").append(closeComplete);
            out.append(";connectionMediaWorkerRunning=").append(mediaWorkerRunning);
            out.append(";connectionGfxWorkerRunning=").append(gfxWorkerRunning);
            out.append(";connectionGfxReadWorkerRunning=").append(gfxReadWorkerRunning);
            out.append(";connectionEventRouterRunning=").append(eventRouterRunning);
            out.append(";connectionMediaCommandCount=").append(mediaCommandCount.get());
            out.append(";connectionMediaReplyCount=").append(mediaReplyCount.get());
            out.append(";connectionGfxReadCount=").append(gfxReadCount.get());
            out.append(";connectionGfxDispatchCount=").append(gfxDispatchCount.get());
            out.append(";connectionGfxReplyCount=").append(gfxReplyCount.get());
            out.append(";connectionEventQueuedCount=").append(eventQueuedCount.get());
            out.append(";connectionEventDequeuedCount=").append(eventDequeuedCount.get());
            out.append(";connectionReconnectCount=").append(reconnectCount.get());
            out.append(";connectionGfxQueueDepth=").append(gfxQueueDepth);
            out.append(";connectionGfxQueueMaxDepth=").append(gfxQueueMaxDepth);
            out.append(";connectionEventQueueDepth=").append(eventQueueDepth);
            out.append(";connectionEventQueueMaxDepth=").append(eventQueueMaxDepth);
            out.append(";connectionEventSequence=").append(eventSequence.get());
            out.append(";connectionRecent=");
            synchronized (recent)
            {
                Iterator<String> iterator = recent.iterator();
                while (iterator.hasNext())
                {
                    out.append(iterator.next());
                    if (iterator.hasNext()) out.append('|');
                }
            }
            return out.toString();
        }
    }

    private static long monotonicMs()
    {
        return System.nanoTime() / 1000000L;
    }

    private static String clean(String value)
    {
        if (value == null) return "";
        return value.replace(';', '_').replace('|', '_').replace('@', '_')
                .replace('\n', '_').replace('\r', '_');
    }
}
