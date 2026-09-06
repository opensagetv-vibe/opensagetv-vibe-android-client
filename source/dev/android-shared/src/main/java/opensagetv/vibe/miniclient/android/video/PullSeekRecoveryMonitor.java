package opensagetv.vibe.miniclient.android.video;

import android.os.Handler;

/** Owns one cancellable, generation-guarded Pull seek recovery timer. */
public final class PullSeekRecoveryMonitor
{
    public interface Guard
    {
        boolean shouldRecover();
    }

    public interface Action
    {
        void recover();
    }

    private long generation;
    private Handler scheduledHandler;
    private Runnable pending;

    public synchronized void cancel()
    {
        generation++;
        if (scheduledHandler != null && pending != null)
            scheduledHandler.removeCallbacks(pending);
        scheduledHandler = null;
        pending = null;
    }

    public synchronized boolean arm(final Handler handler, long delayMs,
                                    final Guard guard, final Action action)
    {
        if (handler == null || guard == null || action == null || delayMs < 0L)
            return false;
        cancel();
        final long armedGeneration = generation;
        scheduledHandler = handler;
        pending = new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (PullSeekRecoveryMonitor.this)
                {
                    if (armedGeneration != generation || pending != this) return;
                    scheduledHandler = null;
                    pending = null;
                }
                if (guard.shouldRecover()) action.recover();
            }
        };
        if (!handler.postDelayed(pending, delayMs))
        {
            scheduledHandler = null;
            pending = null;
            generation++;
            return false;
        }
        return true;
    }

    public synchronized boolean isArmed()
    {
        return pending != null;
    }
}
