package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

/**
 * DVD Push buffering that distinguishes an ordinary main-title underrun from
 * the intentional end of a short menu/cell generation.
 *
 * <p>Starting every rebuffer with zero queued media made bursty stock-server
 * DVD input oscillate between BUFFERING and READY on faster Android decoders.
 * A normal title now rebuilds a small safety reserve.  Once MiniDVDPlayer has
 * ended the current reader generation, the reserve is bypassed so a short
 * navigation cell or final GOP can still drain without deadlocking the DVD
 * VM.</p>
 */
final class DvdPushLoadControl implements LoadControl
{
    interface DrainState
    {
        boolean shouldDrainImmediately();
        default boolean isScanning() { return false; }
        default long scanBufferUs() { return 2_000_000L; }
    }

    static final int MIN_BUFFER_MS = 5_000;
    static final int MAX_BUFFER_MS = 12_000;
    // Stock-server DVD Push often arrives close to the authored bitrate. A
    // Short reserves let a fast hardware decoder repeatedly catch the stock
    // MiniDVDPlayer writer during the variable-bitrate opening of a title.
    // Physical telemetry showed that the same stream remained stable
    // once it accumulated roughly 6-8 seconds. Build a five-second reserve at
    // startup and after an underrun; short menu/cell generations bypass both
    // values through shouldDrainImmediately().
    static final int START_BUFFER_MS = MIN_BUFFER_MS;
    static final int REBUFFER_MS = MIN_BUFFER_MS;

    private final LoadControl delegate;
    private final DrainState drainState;

    DvdPushLoadControl(DrainState drainState)
    {
        this.drainState = drainState;
        delegate = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS,
                        START_BUFFER_MS, REBUFFER_MS)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    @Override public void onPrepared(PlayerId playerId)
    {
        delegate.onPrepared(playerId);
    }

    @Override public void onTracksSelected(Parameters parameters,
            TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections)
    {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections);
    }

    @Override public void onStopped(PlayerId playerId)
    {
        delegate.onStopped(playerId);
    }

    @Override public void onReleased(PlayerId playerId)
    {
        delegate.onReleased(playerId);
    }

    @Override public Allocator getAllocator(PlayerId playerId)
    {
        return delegate.getAllocator(playerId);
    }

    @Override public long getBackBufferDurationUs(PlayerId playerId)
    {
        return delegate.getBackBufferDurationUs(playerId);
    }

    @Override public boolean retainBackBufferFromKeyframe(PlayerId playerId)
    {
        return delegate.retainBackBufferFromKeyframe(playerId);
    }

    @Override public boolean shouldContinueLoading(Parameters parameters)
    {
        // Twelve seconds of previews represents 192 seconds of source at
        // 16x. Keep rate commands near the rendered NAV without rebuilding
        // or changing normal title/menu buffering.
        if (drainState != null && drainState.isScanning())
            // Some hardware MPEG-2 decoders retain several reference pictures.
            // A subsecond cap can stop loading before those pictures drain,
            // leaving BUFFERING with samples but no decoder output. Two
            // seconds covers that latency even at the bounded 500ms spacing.
            return parameters.bufferedDurationUs < drainState.scanBufferUs();
        return delegate.shouldContinueLoading(parameters);
    }

    @Override public boolean shouldStartPlayback(Parameters parameters)
    {
        if (drainState != null && drainState.shouldDrainImmediately())
            return true;
        return delegate.shouldStartPlayback(parameters);
    }

    @Override public boolean shouldContinuePreloading(PlayerId playerId,
            Timeline timeline, MediaSource.MediaPeriodId mediaPeriodId,
            long bufferedDurationUs)
    {
        return delegate.shouldContinuePreloading(playerId, timeline,
                mediaPeriodId, bufferedDurationUs);
    }
}
