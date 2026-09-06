package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionWorkerOwnerTest
{
    @Test
    public void closeBoundaryIsIdempotent()
    {
        ConnectionWorkerOwner owner = new ConnectionWorkerOwner();
        assertFalse(owner.isCloseStarted());
        assertTrue(owner.beginClose());
        assertTrue(owner.isCloseStarted());
        assertFalse(owner.beginClose());
    }

    @Test
    public void mediaReadinessIsExplicitAndOneShot() throws Exception
    {
        final ConnectionWorkerOwner owner = new ConnectionWorkerOwner();
        assertFalse(owner.awaitMediaWorkerStarted(1L));
        owner.markMediaWorkerStarted();
        assertTrue(owner.awaitMediaWorkerStarted(1L));
        assertTrue(owner.awaitMediaWorkerStarted(1L));
    }

    @Test
    public void eventRouterIsInterruptedAndJoined() throws Exception
    {
        final CountDownLatch started = new CountDownLatch(1);
        Thread worker = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                started.countDown();
                try
                {
                    Thread.sleep(30000L);
                }
                catch (InterruptedException expected)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-event-router");
        ConnectionWorkerOwner owner = new ConnectionWorkerOwner();
        owner.retainEventRouter(worker);
        worker.start();
        assertTrue(started.await(2L, TimeUnit.SECONDS));

        owner.interruptEventRouter();
        owner.awaitStopped(1000L);

        assertFalse(worker.isAlive());
    }

    @Test
    public void waitUsesOneBoundedDeadline() throws Exception
    {
        Thread worker = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(30000L);
                }
                catch (InterruptedException expected)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-blocked-worker");
        ConnectionWorkerOwner owner = new ConnectionWorkerOwner();
        owner.retainMedia(worker);
        worker.start();

        long startedNs = System.nanoTime();
        owner.awaitStopped(50L);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        worker.interrupt();
        worker.join(1000L);

        assertTrue("bounded wait returned too early", elapsedMs >= 25L);
        assertTrue("bounded wait exceeded its deadline", elapsedMs < 1000L);
    }

    @Test
    public void socketCloseAndClearOwnsProtocolResources() throws Exception
    {
        ServerSocket listener = new ServerSocket(0);
        Socket client = new Socket("127.0.0.1", listener.getLocalPort());
        Socket server = listener.accept();
        ConnectionWorkerOwner owner = new ConnectionWorkerOwner();
        owner.setMediaSocket(client);
        owner.setGfxSocket(server);

        owner.closeAndClearMediaSocket();
        assertTrue(client.isClosed());
        assertTrue(owner.mediaSocket() == null);
        owner.closeSockets();
        assertTrue(server.isClosed());
        listener.close();
    }
}
