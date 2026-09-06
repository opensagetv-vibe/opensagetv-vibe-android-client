package opensagetv.vibe.miniclient.uibridge;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-shot renderer readiness boundary used by the protocol GFX initializer.
 * Closing the renderer always releases a waiter, and readiness cannot be
 * published after cancellation.
 */
public final class RendererReadinessGate {
    private static final int WAITING = 0;
    private static final int READY = 1;
    private static final int CANCELLED = 2;

    private final CountDownLatch resolved = new CountDownLatch(1);
    private final AtomicInteger state = new AtomicInteger(WAITING);

    public void markReady() {
        if (state.compareAndSet(WAITING, READY)) {
            resolved.countDown();
        }
    }

    public void cancel() {
        state.set(CANCELLED);
        resolved.countDown();
    }

    public boolean awaitReady() {
        try {
            resolved.await();
            return state.get() == READY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean isReady() {
        return state.get() == READY;
    }
}
