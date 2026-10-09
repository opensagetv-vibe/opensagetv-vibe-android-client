package opensagetv.vibe.miniclient.net;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Diagnostics must not inherit the lifetime of a blocked MediaServer SIZE. */
public class RetainedPullTelemetryTest {
    @Test public void countersRemainReadableWhileIoOwnsSourceMonitor() throws Exception {
        final RetainedBufferedPullDataSource source = new RetainedBufferedPullDataSource("unused", 32768);
        final CountDownLatch ownsMonitor = new CountDownLatch(1);
        final CountDownLatch releaseMonitor = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> blockedIo = workers.submit(new Runnable() {
                @Override public void run() {
                    synchronized (source) {
                        ownsMonitor.countDown();
                        try { releaseMonitor.await(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            });
            assertTrue(ownsMonitor.await(2, TimeUnit.SECONDS));
            Future<Long> observation = workers.submit(() -> source.getRangeOpenCount() + source.getSessionReuseCount());
            assertEquals(Long.valueOf(0L), observation.get(1, TimeUnit.SECONDS));
            releaseMonitor.countDown();
            blockedIo.get(2, TimeUnit.SECONDS);
        } finally {
            releaseMonitor.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test public void publishedCountersAreAtomicLongsForArm32Readers() throws Exception {
        RetainedBufferedPullDataSource source = new RetainedBufferedPullDataSource("unused", 32768);
        for (String name : new String[] {"rangeOpenCount", "sessionReuseCount"}) {
            Field field = RetainedBufferedPullDataSource.class.getDeclaredField(name);
            assertTrue(Modifier.isVolatile(field.getModifiers()));
            field.setAccessible(true);
            field.setLong(source, 0x123456789abcdefL);
        }
        assertEquals(0x123456789abcdefL, source.getRangeOpenCount());
        assertEquals(0x123456789abcdefL, source.getSessionReuseCount());
    }
}
