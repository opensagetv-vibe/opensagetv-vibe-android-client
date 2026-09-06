package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PushBufferDataSourceTelemetryTest
{
    @Test
    public void countsPushAndDecoderReadsWithoutChangingData() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:test");
        byte[] expected = new byte[] {1, 2, 3, 4};
        source.pushBytes(expected, 0, expected.length);
        byte[] actual = new byte[expected.length];

        assertEquals(expected.length, source.readBlocking(0, actual, 0, actual.length));
        assertArrayEquals(expected, actual);
        assertEquals(1, source.getPushCount());
        assertEquals(expected.length, source.getPushedBytes());
        assertEquals(1, source.getReadCount());
        assertEquals(expected.length, source.getReadRequestedBytes());
        assertEquals(expected.length, source.getBytesRead());
        assertTrue(source.getReadRateKbps() >= 0);
        source.release();
    }

    @Test
    public void flushStartsANewBoundedMeasurementWindow() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:test");
        source.pushBytes(new byte[] {1, 2}, 0, 2);
        source.readBlocking(0, new byte[2], 0, 2);
        source.flush();

        assertEquals(0, source.getPushCount());
        assertEquals(0, source.getPushedBytes());
        assertEquals(0, source.getReadCount());
        assertEquals(0, source.getReadRequestedBytes());
        assertEquals(0, source.getBytesRead());
        assertEquals(0, source.getReadWaitMs());
        source.release();
    }

    @Test
    public void flushClearsPreviousEndOfStreamForReplacementPush() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:test");
        source.setEOS();

        assertEquals(-1, source.readBlocking(0, new byte[1], 0, 1));

        source.flush();
        byte[] expected = new byte[] {9, 8, 7, 6};
        source.pushBytes(expected, 0, expected.length);
        byte[] actual = new byte[expected.length];

        assertEquals(expected.length, source.readBlocking(0, actual, 0, actual.length));
        assertArrayEquals(expected, actual);
        assertEquals(1, source.getPushCount());
        assertEquals(expected.length, source.getBytesRead());
        source.release();
    }

    @Test
    public void blockingReadWakesForDataWithoutPolling() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:test");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        byte[] actual = new byte[1];
        Future<Integer> read = executor.submit(() -> source.readBlocking(0, actual, 0, 1));

        source.pushBytes(new byte[] {42}, 0, 1);

        assertEquals(1, (int) read.get(1, TimeUnit.SECONDS));
        assertEquals(42, actual[0]);
        source.release();
        executor.shutdownNow();
    }

    @Test
    public void releaseWakesBlockedRead() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:test");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> read = executor.submit(() -> source.readBlocking(0, new byte[1], 0, 1));

        source.release();

        assertEquals(-1, (int) read.get(1, TimeUnit.SECONDS));
        executor.shutdownNow();
    }

    @Test
    public void pushBeforeOpenWakesWhenOpenCompletes() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> push = executor.submit(() -> {
            source.pushBytes(new byte[] {7}, 0, 1);
            return null;
        });

        source.open("push:test");

        push.get(1, TimeUnit.SECONDS);
        byte[] actual = new byte[1];
        assertEquals(1, source.readBlocking(0, actual, 0, 1));
        assertEquals(7, actual[0]);
        source.release();
        executor.shutdownNow();
    }

    @Test
    public void transientGenerationEndDrainsBytesThenAllowsNextSegment() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:dvd");
        source.activateReadGeneration(1);
        byte[] first = new byte[] {1, 2, 3};
        source.pushBytes(first, 0, first.length);
        source.signalActiveReadGenerationEnd();

        byte[] actual = new byte[first.length];
        assertEquals(first.length,
                source.readBlockingForGeneration(1, 0, actual, 0, actual.length));
        assertArrayEquals(first, actual);
        assertEquals(-1,
                source.readBlockingForGeneration(1, 0, new byte[1], 0, 1));

        source.activateReadGeneration(2);
        source.pushBytes(new byte[] {9}, 0, 1);
        byte[] next = new byte[1];
        assertEquals(1, source.readBlockingForGeneration(2, 0, next, 0, 1));
        assertEquals(9, next[0]);
        source.release();
    }

    @Test
    public void releasingOldReaderLeaseDoesNotCloseSharedDvdPushSession() throws Exception
    {
        PushBufferDataSource source = new PushBufferDataSource();
        source.open("push:dvd");
        source.activateReadGeneration(1);
        source.signalActiveReadGenerationEnd();
        assertEquals(-1,
                source.readBlockingForGeneration(1, 0, new byte[1], 0, 1));

        source.releaseReadGeneration(1);
        source.activateReadGeneration(2);
        source.pushBytes(new byte[] {42}, 0, 1);
        byte[] next = new byte[1];
        assertEquals(1, source.readBlockingForGeneration(2, 0, next, 0, 1));
        assertEquals(42, next[0]);
        source.release();
    }
}
