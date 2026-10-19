package opensagetv.vibe.miniclient.android.video;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Latest server seek waiting for a backend to finish constructing/preparing.
 *
 * <p>Reset synchronously at the new LOAD boundary, not inside deferred UI
 * setup: SEEK may already have arrived after OPENURL by the time setup runs.
 * Taking a request is atomic, so clearing an applied bookmark cannot erase a
 * newer request (including an explicit zero) arriving on the media thread.
 * Backend session/player guards still own stale callback rejection.</p>
 */
public final class PendingPlaybackSeek
{
    public static final long NONE = -1L;
    private final AtomicLong target = new AtomicLong(NONE);

    public void reset()
    {
        target.set(NONE);
    }

    public void set(long positionMs)
    {
        if (positionMs >= 0L) target.set(positionMs);
    }

    public long take()
    {
        return target.getAndSet(NONE);
    }
}
