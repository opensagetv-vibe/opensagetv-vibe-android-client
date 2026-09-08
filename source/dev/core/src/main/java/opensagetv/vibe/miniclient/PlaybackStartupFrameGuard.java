package opensagetv.vibe.miniclient;

/**
 * Bounded presentation guard for an STV's initial media-player OSD.
 *
 * <p>An STV can enter its media-player OSD before the new player has presented
 * a video frame and briefly draw retained timeline state. This guard coalesces
 * that short-lived graphics presentation until the first video frame renders.
 * It never changes playback time, media commands, or server-side STV state.</p>
 */
public final class PlaybackStartupFrameGuard
{
    public static final long MAX_HOLD_MS = 5000L;
    public static final long READY_RECHECK_MS = 50L;

    private boolean currentMenuIsPlaybackOsd;
    private boolean armed;
    private boolean deferredFrame;
    private long releaseAtMs;

    public static boolean isPlaybackOsd(MenuHint hint)
    {
        return hint != null && hint.popupName == null
                && hint.hasMenuLike("MediaPlayer OSD");
    }

    /**
     * Updates the current SageTV menu. Returns true when a deferred frame from
     * the old menu should be presented immediately.
     */
    public synchronized boolean onMenuHint(MenuHint hint, boolean enabled, long nowMs)
    {
        boolean nextMenuIsPlaybackOsd = isPlaybackOsd(hint);
        boolean enteringPlaybackOsd = enabled && nextMenuIsPlaybackOsd
                && !currentMenuIsPlaybackOsd;
        currentMenuIsPlaybackOsd = nextMenuIsPlaybackOsd;

        if (enteringPlaybackOsd)
        {
            armed = true;
            deferredFrame = false;
            releaseAtMs = nowMs + MAX_HOLD_MS;
            return false;
        }

        if (!enabled || !nextMenuIsPlaybackOsd)
        {
            boolean release = deferredFrame;
            armed = false;
            deferredFrame = false;
            releaseAtMs = 0L;
            return release;
        }
        return false;
    }

    /**
     * Re-arms the bounded hold when SageTV loads another item while the STV
     * retains the same playback-OSD menu. Some STVs do not emit a second menu
     * transition during a file switch, so menu entry alone is insufficient.
     */
    public synchronized void onPlaybackLoad(boolean enabled, long nowMs)
    {
        if (!enabled)
        {
            armed = false;
            deferredFrame = false;
            releaseAtMs = 0L;
            return;
        }
        if (currentMenuIsPlaybackOsd)
        {
            armed = true;
            deferredFrame = false;
            releaseAtMs = nowMs + MAX_HOLD_MS;
        }
    }

    /**
     * Returns a short recheck delay while waiting for playback to start, or
     * zero when this frame may render. The maximum hold remains bounded even
     * if playback fails to start.
     */
    public synchronized long deferFrame(long nowMs, boolean playbackStarted)
    {
        if (!armed)
            return 0L;

        long remaining = releaseAtMs - nowMs;
        if (playbackStarted || remaining <= 0L)
        {
            armed = false;
            return 0L;
        }
        deferredFrame = true;
        return Math.min(READY_RECHECK_MS, remaining);
    }

    /** Releases a frame when playback starts, or when the bounded hold expires. */
    public synchronized boolean releaseIfReady(long nowMs, boolean playbackStarted)
    {
        if (!deferredFrame || (!playbackStarted && nowMs < releaseAtMs))
            return false;
        armed = false;
        deferredFrame = false;
        releaseAtMs = 0L;
        return true;
    }

    /** True only during the short first-OSD presentation window. */
    public synchronized boolean isArmed()
    {
        return armed;
    }

    public synchronized void reset()
    {
        currentMenuIsPlaybackOsd = false;
        armed = false;
        deferredFrame = false;
        releaseAtMs = 0L;
    }
}
