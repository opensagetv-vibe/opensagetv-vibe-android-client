package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import opensagetv.vibe.miniclient.logging.ILogger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ConnectionEventRouterTest
{
    @Test
    public void dispatchesOneBoundedFifoAndStopsOnInterrupt() throws Exception
    {
        final List<Integer> order = Collections.synchronizedList(new ArrayList<Integer>());
        final CountDownLatch completed = new CountDownLatch(3);
        ConnectionLifecycleDiagnostics.Trace trace = ConnectionLifecycleDiagnostics.begin("test");
        ConnectionEventRouter router = new ConnectionEventRouter(
                "test-event-router", trace, new ConnectionEventRouter.DispatchState()
        {
            @Override
            public boolean shouldDispatch()
            {
                return true;
            }
        }, mock(ILogger.class));
        router.start();
        for (int value = 1; value <= 3; value++)
        {
            final int queued = value;
            router.enqueue(new Runnable()
            {
                @Override
                public void run()
                {
                    order.add(queued);
                    completed.countDown();
                }
            });
        }

        assertTrue(completed.await(2L, TimeUnit.SECONDS));
        router.interrupt();
        router.join(1000L);
        assertFalse(router.isAlive());
        assertEquals(Arrays.asList(1, 2, 3), order);
        assertEquals(100, router.queue.remainingCapacity() + router.queue.size());
    }

    @Test
    public void reconnectSuppressionDropsDequeuedEvent() throws Exception
    {
        final CountDownLatch policyChecked = new CountDownLatch(1);
        final CountDownLatch dispatched = new CountDownLatch(1);
        ConnectionEventRouter router = new ConnectionEventRouter(
                "test-suppressed-router", ConnectionLifecycleDiagnostics.begin("test"),
                new ConnectionEventRouter.DispatchState()
        {
            @Override
            public boolean shouldDispatch()
            {
                policyChecked.countDown();
                return false;
            }
        }, mock(ILogger.class));
        router.start();
        router.enqueue(new Runnable()
        {
            @Override
            public void run()
            {
                dispatched.countDown();
            }
        });

        assertTrue(policyChecked.await(2L, TimeUnit.SECONDS));
        assertFalse(dispatched.await(25L, TimeUnit.MILLISECONDS));
        router.interrupt();
        router.join(1000L);
    }
}
