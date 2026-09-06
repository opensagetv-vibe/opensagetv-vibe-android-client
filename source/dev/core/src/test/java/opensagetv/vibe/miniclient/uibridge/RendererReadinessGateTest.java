package opensagetv.vibe.miniclient.uibridge;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RendererReadinessGateTest {
    @Test
    public void readyReleasesWaiter() throws Exception {
        RendererReadinessGate gate = new RendererReadinessGate();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            result.set(gate.awaitReady());
            finished.countDown();
        });
        waiter.start();

        gate.markReady();

        assertTrue(finished.await(1, TimeUnit.SECONDS));
        assertTrue(result.get());
        assertTrue(gate.isReady());
    }

    @Test
    public void cancelReleasesWaiterWithoutReadiness() throws Exception {
        RendererReadinessGate gate = new RendererReadinessGate();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(true);
        Thread waiter = new Thread(() -> {
            result.set(gate.awaitReady());
            finished.countDown();
        });
        waiter.start();

        gate.cancel();

        assertTrue(finished.await(1, TimeUnit.SECONDS));
        assertFalse(result.get());
        assertFalse(gate.isReady());
    }

    @Test
    public void readinessCannotBeRepublishedAfterCancel() {
        RendererReadinessGate gate = new RendererReadinessGate();
        gate.cancel();
        gate.markReady();

        assertFalse(gate.awaitReady());
        assertFalse(gate.isReady());
    }

    @Test
    public void interruptionIsPreserved() throws Exception {
        RendererReadinessGate gate = new RendererReadinessGate();
        AtomicBoolean ready = new AtomicBoolean(true);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            ready.set(gate.awaitReady());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        waiter.interrupt();
        waiter.join(1000);

        assertFalse(waiter.isAlive());
        assertFalse(ready.get());
        assertTrue(interrupted.get());
    }
}
