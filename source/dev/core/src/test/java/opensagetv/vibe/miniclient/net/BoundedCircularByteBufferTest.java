package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BoundedCircularByteBufferTest
{
    @Test
    public void wrapsWithoutReorderingBytes() throws Exception
    {
        BoundedCircularByteBuffer buffer = new BoundedCircularByteBuffer(5);
        buffer.write(new byte[] {1, 2, 3, 4}, 0, 4);
        byte[] first = new byte[3];
        assertEquals(3, buffer.read(first, 0, first.length));
        assertArrayEquals(new byte[] {1, 2, 3}, first);

        buffer.write(new byte[] {5, 6, 7, 8}, 0, 4);
        byte[] wrapped = new byte[5];
        assertEquals(5, buffer.read(wrapped, 0, wrapped.length));
        assertArrayEquals(new byte[] {4, 5, 6, 7, 8}, wrapped);
    }

    @Test
    public void fullBufferBackpressuresWriterUntilRead() throws Exception
    {
        BoundedCircularByteBuffer buffer = new BoundedCircularByteBuffer(2);
        buffer.write(new byte[] {1, 2}, 0, 2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> writer = executor.submit(() -> {
            buffer.write(new byte[] {3}, 0, 1);
            return null;
        });

        Thread.sleep(50);
        assertFalse(writer.isDone());
        byte[] first = new byte[1];
        assertEquals(1, buffer.read(first, 0, 1));
        writer.get(1, TimeUnit.SECONDS);

        byte[] remaining = new byte[2];
        assertEquals(2, buffer.read(remaining, 0, 2));
        assertArrayEquals(new byte[] {2, 3}, remaining);
        executor.shutdownNow();
    }

    @Test
    public void clearReleasesCapacityAndCloseRejectsWrites() throws Exception
    {
        BoundedCircularByteBuffer buffer = new BoundedCircularByteBuffer(2);
        buffer.write(new byte[] {1, 2}, 0, 2);
        buffer.clear();
        assertEquals(2, buffer.getSpaceLeft());
        assertEquals(0, buffer.available());

        buffer.close();
        assertEquals(-1, buffer.read(new byte[1], 0, 1));
        try
        {
            buffer.write(new byte[] {3}, 0, 1);
            throw new AssertionError("closed buffer accepted a write");
        }
        catch (IOException expected)
        {
            // Expected.
        }
    }
}
