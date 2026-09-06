package opensagetv.vibe.miniclient.android.tv.debug;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-lifetime debug executor that preserves concurrent ordered-broadcast checks. */
final class DebugAsyncExecutor
{
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = new Thread(runnable,
                    "SageTV-MCP-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private DebugAsyncExecutor()
    {
    }

    static void execute(Runnable runnable)
    {
        EXECUTOR.execute(runnable);
    }

    static void shutdownForProcessTeardown()
    {
        EXECUTOR.shutdownNow();
    }
}
