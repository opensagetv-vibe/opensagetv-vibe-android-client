package opensagetv.vibe.miniclient.android.video;

import android.os.Handler;

import opensagetv.vibe.miniclient.video.PlaybackRatePolicy;

/**
 * Main-thread owner for bounded native forward rates and seek-based scan.
 * Player backends provide only their small affinity-specific operations.
 */
public final class PlaybackRateController
{
    public interface Driver
    {
        boolean isAvailable();
        boolean isPlaying();
        long getPositionMs();
        long getDurationMs();
        void setNativeRate(float rate);
        void seekTo(long positionMs);
    }

    private final Handler handler;
    private volatile float requestedRate = 1.0f;
    private PlaybackRatePolicy.Mode mode = PlaybackRatePolicy.Mode.NORMAL;
    private Driver driver;

    private final Runnable scanTick = new Runnable()
    {
        @Override
        public void run()
        {
            Driver active;
            float rate;
            synchronized (PlaybackRateController.this)
            {
                if (mode != PlaybackRatePolicy.Mode.SEEK_SCAN || driver == null) return;
                active = driver;
                rate = requestedRate;
            }
            if (!active.isAvailable()) return;
            if (active.isPlaying())
            {
                long target = PlaybackRatePolicy.seekScanTargetMs(
                        active.getPositionMs(), PlaybackRatePolicy.SCAN_INTERVAL_MS,
                        rate, active.getDurationMs());
                active.seekTo(target);
            }
            handler.postDelayed(this, PlaybackRatePolicy.SCAN_INTERVAL_MS);
        }
    };

    public PlaybackRateController(Handler handler)
    {
        if (handler == null) throw new IllegalArgumentException("handler must not be null");
        this.handler = handler;
    }

    /** Returns the accepted rate, or retains the prior rate if unsupported. */
    public synchronized float setRate(float requested, Driver newDriver)
    {
        PlaybackRatePolicy.Mode selected = PlaybackRatePolicy.classify(requested);
        if (selected == PlaybackRatePolicy.Mode.UNSUPPORTED || newDriver == null)
            return requestedRate;

        handler.removeCallbacks(scanTick);
        requestedRate = requested;
        mode = selected;
        driver = newDriver;
        final Driver active = newDriver;
        final float nativeRate = selected == PlaybackRatePolicy.Mode.NATIVE
                ? requested : 1.0f;
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (active.isAvailable()) active.setNativeRate(nativeRate);
            }
        });
        if (selected == PlaybackRatePolicy.Mode.SEEK_SCAN)
            handler.postDelayed(scanTick, PlaybackRatePolicy.SCAN_INTERVAL_MS);
        return requestedRate;
    }

    public float getRate()
    {
        return requestedRate;
    }

    public synchronized void onPlay()
    {
        if (driver == null) return;
        if (mode == PlaybackRatePolicy.Mode.SEEK_SCAN)
        {
            handler.removeCallbacks(scanTick);
            handler.postDelayed(scanTick, PlaybackRatePolicy.SCAN_INTERVAL_MS);
        }
        else
        {
            final Driver active = driver;
            final float rate = requestedRate;
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (active.isAvailable()) active.setNativeRate(rate);
                }
            });
        }
    }

    public synchronized void onPause()
    {
        handler.removeCallbacks(scanTick);
    }

    public synchronized void reset()
    {
        handler.removeCallbacks(scanTick);
        final Driver active = driver;
        requestedRate = 1.0f;
        mode = PlaybackRatePolicy.Mode.NORMAL;
        driver = null;
        if (active != null)
        {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (active.isAvailable()) active.setNativeRate(1.0f);
                }
            });
        }
    }
}
