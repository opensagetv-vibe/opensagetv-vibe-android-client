package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GfxFrameExchangeTest
{
    @Test
    public void preservesExactHeaderBodyUntilDispatchCompletes() throws Exception
    {
        final GfxFrameExchange exchange = new GfxFrameExchange(
                ConnectionLifecycleDiagnostics.begin("test"));
        final byte[] header = new byte[] {16, 0, 0, 2};
        final byte[] body = new byte[] {4, 2};
        exchange.publish(header, body, 16);

        GfxFrameExchange.Frame frame = exchange.awaitFrame();
        assertSame(header, frame.header);
        assertSame(body, frame.body);
        assertArrayEquals(new byte[] {4, 2}, frame.body);
        exchange.complete(frame);

        exchange.publish(header, body, 16);
        exchange.complete(exchange.awaitFrame());
    }

    @Test
    public void readerCannotReuseBuffersBeforeCompletion() throws Exception
    {
        final GfxFrameExchange exchange = new GfxFrameExchange(
                ConnectionLifecycleDiagnostics.begin("test"));
        exchange.publish(new byte[4], new byte[1], 16);
        final CountDownLatch returned = new CountDownLatch(1);
        Thread reader = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                exchange.awaitWritable();
                returned.countDown();
            }
        });
        reader.start();
        assertFalse(returned.await(25L, TimeUnit.MILLISECONDS));
        GfxFrameExchange.Frame frame = exchange.awaitFrame();
        exchange.complete(frame);
        assertTrue(returned.await(1L, TimeUnit.SECONDS));
        reader.join(1000L);
    }

    @Test
    public void failureUsesTheSameSerialHandoff()
    {
        GfxFrameExchange exchange = new GfxFrameExchange(
                ConnectionLifecycleDiagnostics.begin("test"));
        RuntimeException failure = new RuntimeException("test");
        exchange.publishFailure(failure);
        GfxFrameExchange.Frame frame = exchange.awaitFrame();
        assertSame(failure, frame.failure);
    }
}
