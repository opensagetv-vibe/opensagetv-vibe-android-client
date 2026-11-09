package opensagetv.vibe.miniclient.android.video;

import android.os.Handler;

import opensagetv.vibe.miniclient.video.PlaybackRatePolicy;

/**
 * Main-thread owner for bounded native forward rates and seek-based scan.
 * Player backends provide only their small affinity-specific operations.
 */
public final class PlaybackRateController
{
    interface Scheduler
    {
        void post(Runnable work);
        void postDelayed(Runnable work, long delayMs);
        void remove(Runnable work);
    }
    public interface Driver
    {
        boolean isAvailable();
        boolean isPlaying();
        long getPositionMs();
        long getDurationMs();
        void setNativeRate(float rate);
        void seekTo(long positionMs);
    }

    private final Scheduler scheduler;
    private volatile float requestedRate = 1.0f;
    private PlaybackRatePolicy.Mode mode = PlaybackRatePolicy.Mode.NORMAL;
    private Driver driver;
    private long generation;
    private Runnable scanTick;

    public PlaybackRateController(Handler handler)
    {
        this(handlerScheduler(handler));
    }

    PlaybackRateController(Scheduler scheduler)
    {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        this.scheduler = scheduler;
    }

    private static Scheduler handlerScheduler(final Handler handler)
    {
        if (handler == null) throw new IllegalArgumentException("handler must not be null");
        return new Scheduler()
        {
            @Override public void post(Runnable work) { handler.post(work); }
            @Override public void postDelayed(Runnable work, long delayMs)
            { handler.postDelayed(work, delayMs); }
            @Override public void remove(Runnable work) { handler.removeCallbacks(work); }
        };
    }

    private void cancelQueuedScan()
    {
        generation++;
        if (scanTick != null) scheduler.remove(scanTick);
        scanTick = null;
    }

    private synchronized boolean isCurrent(long expected, Driver active)
    {
        return generation == expected && driver == active;
    }

    private void postNativeRate(final Driver active, final float rate)
    {
        final long expected = generation;
        scheduler.post(new Runnable()
        {
            @Override public void run()
            {
                if (isCurrent(expected, active) && active.isAvailable())
                    active.setNativeRate(rate);
            }
        });
    }

    private void scheduleScan()
    {
        final long expected = generation;
        final Driver active = driver;
        final float rate = requestedRate;
        scanTick = new Runnable()
        {
            @Override public void run()
            {
                if (!isCurrent(expected, active) || !active.isAvailable()) return;
                if (active.isPlaying())
                {
                    long position = active.getPositionMs();
                    long target = PlaybackRatePolicy.seekScanTargetMs(position,
                            PlaybackRatePolicy.SCAN_INTERVAL_MS, rate, active.getDurationMs());
                    // A callback already dequeued by Handler can survive
                    // removeCallbacks. Never let it seek a replacement session.
                    if (!isCurrent(expected, active)) return;
                    if (target != position) active.seekTo(target);
                }
                synchronized (PlaybackRateController.this)
                {
                    if (isCurrent(expected, active))
                        scheduler.postDelayed(this, PlaybackRatePolicy.SCAN_INTERVAL_MS);
                }
            }
        };
        scheduler.postDelayed(scanTick, PlaybackRatePolicy.SCAN_INTERVAL_MS);
    }

    /** Returns the accepted rate, or retains the prior rate if unsupported. */
    public synchronized float setRate(float requested, Driver newDriver)
    {
        PlaybackRatePolicy.Mode selected = PlaybackRatePolicy.classify(requested);
        if (selected == PlaybackRatePolicy.Mode.UNSUPPORTED || newDriver == null)
            return requestedRate;

        cancelQueuedScan();
        requestedRate = requested;
        mode = selected;
        driver = newDriver;
        final Driver active = newDriver;
        final float nativeRate = selected == PlaybackRatePolicy.Mode.NATIVE
                ? requested : 1.0f;
        postNativeRate(active, nativeRate);
        if (selected == PlaybackRatePolicy.Mode.SEEK_SCAN)
            scheduleScan();
        return requestedRate;
    }

    public float getRate()
    {
        return requestedRate;
    }

    public synchronized void onPlay()
    {
        if (driver == null) return;
        cancelQueuedScan();
        if (mode == PlaybackRatePolicy.Mode.SEEK_SCAN)
        {
            postNativeRate(driver, 1.0f);
            scheduleScan();
        }
        else
        {
            postNativeRate(driver, requestedRate);
        }
    }

    public synchronized void onPause()
    {
        cancelQueuedScan();
    }

    public synchronized void reset()
    {
        cancelQueuedScan();
        final Driver active = driver;
        final long expected = generation;
        requestedRate = 1.0f;
        mode = PlaybackRatePolicy.Mode.NORMAL;
        driver = null;
        if (active != null)
        {
            scheduler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (isCurrent(expected, null) && active.isAvailable())
                        active.setNativeRate(1.0f);
                }
            });
        }
    }
}
