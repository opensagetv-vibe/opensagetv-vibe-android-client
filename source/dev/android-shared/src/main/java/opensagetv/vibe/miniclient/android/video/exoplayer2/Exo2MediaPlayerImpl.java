package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.app.Activity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.upstream.DataSource;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.android.util.Logger;
import opensagetv.vibe.miniclient.android.video.BaseMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.ActivePlayerSessionOverrides;
import opensagetv.vibe.miniclient.android.video.TextSubtitlePresentation;
import opensagetv.vibe.miniclient.android.video.PlaybackDebugTrap;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeConfig;
import opensagetv.vibe.miniclient.android.video.PullSeekRecoveryMonitor;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSource;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSnapshot;
import opensagetv.vibe.miniclient.android.video.PlaybackRateController;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.EncodedPassthroughOffsetController;
import opensagetv.vibe.miniclient.android.video.MediaSessionCallbackHandler;
import opensagetv.vibe.miniclient.android.video.smb.SmbDirectConfig;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.media.TrackPreferencePolicy;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.util.Utils;
import opensagetv.vibe.miniclient.util.VerboseLogging;
import opensagetv.vibe.miniclient.video.PlaybackMediaContext;
import opensagetv.vibe.miniclient.video.PlaybackFailureClassifier;
import opensagetv.vibe.miniclient.video.DecoderAttemptTelemetry;
import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;
import opensagetv.vibe.miniclient.video.Mpeg2PictureTimestampCompleter;
import opensagetv.vibe.miniclient.video.PlaybackFrameStepPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSeekPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSessionController;
import opensagetv.vibe.miniclient.video.PlaybackSyncPointPolicy;
import opensagetv.vibe.miniclient.video.PullSeekRecoveryPolicy;
import opensagetv.vibe.miniclient.net.SessionOwnedDataSource;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.RejectedExecutionException;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * Created by seans on 24/09/16.
 */

public class Exo2MediaPlayerImpl extends BaseMediaPlayerImpl<ExoPlayer, DataSource>
        implements PlaybackHealthSource
{
    static final Logger log = Logger.getLogger(Exo2MediaPlayerImpl.class);
    private final DecoderAttemptTelemetry decoderAttemptTelemetry =
            new DecoderAttemptTelemetry();
    private final Set<String> sessionDecoderExclusions = new HashSet<>();

    private final LegacyExtenderCaptionBridge legacyCaptionBridge =
            new LegacyExtenderCaptionBridge(new LegacyExtenderCaptionBridge.Sink()
            {
                @Override
                public void postSubtitleInfo(long pts45Khz, long duration45Khz,
                        byte[] data, int flags)
                {
                    MiniClientConnection connection = MiniclientApplication.get()
                            .getClient().getCurrentConnection();
                    if (connection != null)
                        connection.postSubtitleInfo(pts45Khz, duration45Khz, data, flags);
                }
            });
    private final AtomicLong legacyCaptionDrainClockUs = new AtomicLong(-1L);
    private final AtomicBoolean legacyCaptionDrainScheduled = new AtomicBoolean(false);
    private final Mpeg2PictureTimestampCompleter mpeg2InterlaceObserver =
            new Mpeg2PictureTimestampCompleter();

    @Override
    protected void onPlaybackLoadStarted()
    {
        firstVideoFrameRendered = false;
        decoderAttemptTelemetry.reset();
        sessionDecoderExclusions.clear();
        resetLegacyCaptionsForDiscontinuity();
        mpeg2InterlaceObserver.reset();
    }

    private void resetLegacyCaptionsForDiscontinuity()
    {
        legacyCaptionDrainClockUs.set(-1L);
        legacyCaptionBridge.clearPending();
        try
        {
            MiniclientApplication.get().getClient().getBackgroundService().execute(new Runnable()
            {
                @Override
                public void run()
                {
                    legacyCaptionBridge.postFlush();
                }
            });
        }
        catch (RejectedExecutionException ex)
        {
            log.logWarning("Skipping legacy-caption flush during client teardown");
        }
    }

    private void scheduleLegacyCaptionDrain(long playbackTimeUs)
    {
        legacyCaptionDrainClockUs.set(playbackTimeUs);
        if (!legacyCaptionDrainScheduled.compareAndSet(false, true))
            return;
        try
        {
            MiniclientApplication.get().getClient().getBackgroundService().execute(new Runnable()
            {
                @Override
                public void run()
                {
                    long drainedClockUs = -1L;
                    try
                    {
                        do
                        {
                            drainedClockUs = legacyCaptionDrainClockUs.get();
                            if (drainedClockUs >= 0L)
                                legacyCaptionBridge.drainTo(drainedClockUs);
                        }
                        while (drainedClockUs != legacyCaptionDrainClockUs.get());
                    }
                    finally
                    {
                        legacyCaptionDrainScheduled.set(false);
                        long newestClockUs = legacyCaptionDrainClockUs.get();
                        if (newestClockUs >= 0L && newestClockUs != drainedClockUs)
                            scheduleLegacyCaptionDrain(newestClockUs);
                    }
                }
            });
        }
        catch (RejectedExecutionException ex)
        {
            legacyCaptionDrainScheduled.set(false);
            log.logWarning("Skipping legacy-caption drain during client teardown");
        }
    }

    @Override
    protected void releaseDataSource()
    {
        if (dataSource instanceof SessionOwnedDataSource)
            ((SessionOwnedDataSource) dataSource).releaseSession();
        super.releaseDataSource();
    }

    /**
     * ExoPlayer 2.18 has no DefaultExtractorsFactory.setTsSubtitleFormats()
     * equivalent. Replace only its TS extractor so broadcast A/53 caption tracks
     * are declared explicitly in both Push and Pull while preserving the normal
     * extractor set/order for every other container. Pull-only seek tuning is not
     * applied to Push.
     */
    private ExtractorsFactory createCaptionAwareExtractorsFactory(boolean pullMode)
    {
        final int timestampSearchBytes = TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
                * (pullMode ? runtimeConfig.getTsSearchMultiplier() : 1);
        final int tsFlags = DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        String standard = prefs.getString(PrefStore.Keys.preferred_caption_standard,
                TrackPreferencePolicy.CAPTION_STANDARD_AUTO);
        int service = TrackPreferencePolicy.parseCaptionService(standard,
                prefs.getString(PrefStore.Keys.preferred_caption_service, "1"));
        final List<Format> captionFormats = new ArrayList<Format>();
        SubtitleCodec preferredCodec = TrackPreferencePolicy.preferredCaptionCodec(standard);
        if (preferredCodec == null || preferredCodec == SubtitleCodec.CEA608)
        {
            captionFormats.add(new Format.Builder()
                    .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                    .setLanguage("eng")
                    .setAccessibilityChannel(preferredCodec == SubtitleCodec.CEA608 ? service : 1)
                    .build());
        }
        if (preferredCodec == null || preferredCodec == SubtitleCodec.CEA708)
        {
            captionFormats.add(new Format.Builder()
                    .setSampleMimeType(MimeTypes.APPLICATION_CEA708)
                    .setLanguage("eng")
                    .setAccessibilityChannel(preferredCodec == SubtitleCodec.CEA708 ? service : 1)
                    .build());
        }
        final DefaultExtractorsFactory defaults = new DefaultExtractorsFactory()
                .setTsExtractorFlags(tsFlags);
        if (pullMode)
        {
            defaults.setTsExtractorTimestampSearchBytes(timestampSearchBytes)
                    .setConstantBitrateSeekingEnabled(true);
        }

        ExtractorsFactory captionFactory = new ExtractorsFactory()
        {
            @Override
            public Extractor[] createExtractors()
            {
                Extractor[] extractors = defaults.createExtractors();
                for (int i = 0; i < extractors.length; i++)
                {
                    if (extractors[i] instanceof TsExtractor)
                    {
                        extractors[i] = new TsExtractor(
                                TsExtractor.MODE_MULTI_PMT,
                                new TimestampAdjuster(0),
                                new DefaultTsPayloadReaderFactory(tsFlags, captionFormats),
                                timestampSearchBytes);
                    }
                }
                return extractors;
            }
        };
        return new LegacyCaptionExtractorsFactory(captionFactory, legacyCaptionBridge,
                mpeg2InterlaceObserver);
    }

    /**
     * A SageTV DVD PUSH epoch can begin on a VOB packet boundary that is not a
     * valid generic-container sniff point.  The server has already identified
     * this session as DVD MPEG-PS, so bypass generic probing and construct the
     * program-stream extractor directly.  A fresh extractor is returned for
     * every FLUSH/cell replacement.
     */
    private ExtractorsFactory createDvdExtractorsFactory()
    {
        ExtractorsFactory dvdFactory = new ExtractorsFactory()
        {
            @Override
            public Extractor[] createExtractors()
            {
                return new Extractor[] { new PsExtractor() };
            }
        };
        return new LegacyCaptionExtractorsFactory(dvdFactory, legacyCaptionBridge,
                mpeg2InterlaceObserver);
    }

    public String getMpeg2InterlaceObservationForDebug()
    {
        return mpeg2InterlaceObserver.getInterlaceObservation();
    }

    public boolean isMpeg2SequenceExtensionSeenForDebug()
    {
        return mpeg2InterlaceObserver.isSequenceExtensionSeen();
    }

    public long getMpeg2ProgressiveFrameCountForDebug()
    {
        return mpeg2InterlaceObserver.getProgressiveFrameCount();
    }

    public long getMpeg2InterlacedFrameCountForDebug()
    {
        return mpeg2InterlaceObserver.getInterlacedFrameCount();
    }

    public long getMpeg2FieldPictureCountForDebug()
    {
        return mpeg2InterlaceObserver.getFieldPictureCount();
    }
    private MediaSource mediaSource;
    private long playbackStartPosition = -1;
    private volatile boolean audioPassthroughEnabled;
    private volatile boolean audioOutputRebuildQueued;
    private volatile boolean exclusiveDiagnosticAudioSuspended;
    private EncodedPassthroughOffsetController passthroughOffsetController;
    private Runnable pendingPassthroughOffsetReanchor;
    private DataSource retainedAudioRebuildDataSource;
    private Exo2PcmAudioProcessor pcmAudioProcessor;
    private int initialAudioTrackIndex = -1;
    private long currentPlaybackPosition = 0;
    private ReentrantLock playbackPositionLock;
    private DefaultTrackSelector trackSelector;
    private int selectedSubtitleTrack = DISABLE_TRACK;
    private volatile int requestedSubtitleTrack = DISABLE_TRACK;
    private volatile boolean playRequested = true;
    private volatile boolean serverMuted;
    private volatile boolean dvdPushMode;

    private ExtractorsFactory withPassthroughOffset(ExtractorsFactory factory)
    {
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        return controller == null ? factory
                : new Exo2PassthroughOffsetExtractorsFactory(factory, controller);
    }

    private boolean errorState = false;
    private int retryCount = 0;
    private volatile boolean firstVideoFrameRendered;

    private boolean showCaptions = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private static final long GROWING_PULL_SEEK_COALESCE_MS = 300L;
    private static final long ACTIVE_PULL_IO_RECOVERY_GRACE_MS = 2500L;
    private long pendingGrowingPullSeekTargetMs = -1L;
    private ExoPlayer pendingGrowingPullSeekPlayer;
    private PlaybackSessionController.Token pendingGrowingPullSeekSession;
    private boolean pendingGrowingPullSeekResume;
    private boolean pendingGrowingPullSeekSmb;
    private final Runnable growingPullSeekRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            ExoPlayer expectedPlayer = pendingGrowingPullSeekPlayer;
            PlaybackSessionController.Token expectedSession = pendingGrowingPullSeekSession;
            long targetMs = pendingGrowingPullSeekTargetMs;
            boolean resume = pendingGrowingPullSeekResume;
            boolean smb = pendingGrowingPullSeekSmb;
            pendingGrowingPullSeekPlayer = null;
            pendingGrowingPullSeekSession = null;
            pendingGrowingPullSeekTargetMs = -1L;
            if (expectedPlayer == null || expectedPlayer != player
                    || !isCurrentPlaybackSession(expectedSession)
                    || !(dataSource instanceof Exo2PullDataSource)
                    || mediaSource == null)
                return;
            try
            {
                Exo2PullDataSource pull = (Exo2PullDataSource) dataSource;
                pull.beginSeekableSnapshotPreparation();
                PlaybackDebugTrap.recordDetailed(smb
                                ? "smb_seek_reprepare_before"
                                : "growing_pull_seek_reprepare_before",
                        Exo2MediaPlayerImpl.this, "coalescedTargetMs=" + targetMs);
                expectedPlayer.setMediaSource(mediaSource, targetMs);
                expectedPlayer.prepare();
                expectedPlayer.setPlayWhenReady(resume);
                PlaybackDebugTrap.recordDetailed(smb
                                ? "smb_seek_reprepare_after"
                                : "growing_pull_seek_reprepare_after",
                        Exo2MediaPlayerImpl.this, "coalescedTargetMs=" + targetMs);
            }
            catch (Exception ex)
            {
                PlaybackDebugTrap.record("growing_pull_seek_reprepare_error_"
                        + ex.getClass().getSimpleName(), Exo2MediaPlayerImpl.this);
                log.logError("Growing Pull seek reprepare failed at " + targetMs + "ms", ex);
            }
        }
    };
    private final PlaybackRateController playbackRateController =
            new PlaybackRateController(progressHandler);
    private final PlaybackRateController.Driver playbackRateDriver =
            new PlaybackRateController.Driver()
            {
                @Override
                public boolean isAvailable()
                {
                    return playerReady && player != null && !pushMode && !dvdPushMode;
                }

                @Override
                public boolean isPlaying()
                {
                    return getState() == MiniPlayerPlugin.PLAY_STATE;
                }

                @Override
                public long getPositionMs()
                {
                    return player == null ? 0L : player.getContentPosition();
                }

                @Override
                public long getDurationMs()
                {
                    return player == null ? 0L : player.getDuration();
                }

                @Override
                public void setNativeRate(float rate)
                {
                    if (player != null) player.setPlaybackSpeed(rate);
                }

                @Override
                public void seekTo(long positionMs)
                {
                    Exo2MediaPlayerImpl.this.seek(positionMs);
                }
            };
    private Runnable progressRunnable;
    private final PullSeekRecoveryMonitor pullSeekRecoveryMonitor = new PullSeekRecoveryMonitor();
    private final PlaybackMediaContext mediaContext = new PlaybackMediaContext();
    private PlayerRuntimeConfig runtimeConfig = PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.LEGACY_EXO);
    private String url;

    MediaSessionCompat mediaSession;

    private volatile SubtitleView subView;
    private volatile long subtitleCueUpdateCount;
    private volatile long subtitleNonEmptyCueCount;
    private volatile long subtitleBitmapCueCount;
    private volatile int currentSubtitleCueCount;
    private volatile String lastSubtitleCueText = "";
    private volatile String currentSubtitleCueText = "";
    private volatile boolean subtitleOverlayAttached;
    private volatile int subtitleSafeAreaPercent;
    private volatile int subtitleTextScalePercent;
    private volatile String subtitleTextStyle;

    public Exo2MediaPlayerImpl(AndroidUIController activity)
    {
        super(activity, true, false);
        playbackPositionLock = new ReentrantLock();
        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        subtitleSafeAreaPercent = TextSubtitlePresentation.safeAreaPercent(prefs);
        subtitleTextScalePercent = TextSubtitlePresentation.textScalePercent(prefs);
        subtitleTextStyle = TextSubtitlePresentation.style(prefs);
    }

    @Override
    public PlaybackHealthSnapshot capturePlaybackHealthSnapshot()
    {
        return new PlaybackHealthSnapshot(player, dataSource, pushMode,
                playerReady, seekPending, flushed, errorState, retryCount);
    }

    @Override
    public boolean hasRenderedFirstVideoFrame()
    {
        return firstVideoFrameRendered;
    }

    @Override
    public void load(byte majorHint, byte minorHint, String encodingHint, String urlString,
                     String hostname, boolean timeshifted, long bufferSize)
    {
        playbackRateController.reset();
        playRequested = true;
        subtitleCueUpdateCount = 0;
        subtitleNonEmptyCueCount = 0;
        subtitleBitmapCueCount = 0;
        currentSubtitleCueCount = 0;
        lastSubtitleCueText = "";
        currentSubtitleCueText = "";
        subtitleOverlayAttached = false;
        mediaContext.update(majorHint, minorHint, encodingHint, timeshifted, bufferSize);
        super.load(majorHint, minorHint, encodingHint, urlString, hostname, timeshifted, bufferSize);
    }

    @Override
    public void setServerMediaMetadataExplicit(boolean explicit)
    {
        mediaContext.setMetadataExplicit(explicit);
    }

    public long getPlaybackPosition()
    {
        long position = 0;

        try
        {
            playbackPositionLock.lock();
            position = this.currentPlaybackPosition;
        }
        catch (Exception ex)
        {
            log.logError("Unexpected error getting playback position", ex);
        }
        finally
        {
            playbackPositionLock.unlock();
        }

        return position;
    }

    public void setPlaybackPosition(long position)
    {
        try
        {
            playbackPositionLock.lock();

            if (position > 0)
            {
                currentPlaybackPosition = position;
            }
            else
            {
                //Set to zero if less than zero;
                currentPlaybackPosition = 0;
            }

        }
        catch (Exception ex)
        {
            log.logError("Unexpected error setting playback position", ex);

        }
        finally
        {
            playbackPositionLock.unlock();
        }
    }

    boolean ExoIsPlaying()
    {
        if (player == null)
        {
            return false;
        }

        return player.getPlayWhenReady();
    }

    void ExoPause()
    {
        if (player == null)
        {
            return;
        }

        log.logInfo("Pause was called");
        player.setPlayWhenReady(false);
    }

    void ExoStart()
    {
        if (player == null)
        {
            return;
        }
        log.logDebug("Start was called");
        if (player.getPlaybackState() == Player.STATE_IDLE)
        {
            // Legacy ExoPlayer has the same reusable stopped-item contract as
            // Media3: setPlayWhenReady alone is a no-op after stop(). Rebind
            // the SurfaceView and prepare at SageTV's already-applied seek.
            context.setupVideoFrame();
            player.setVideoSurfaceView((SurfaceView) context.getVideoView());
            PlaybackDebugTrap.record("play_prepare_from_idle", this);
            player.prepare();
        }
        if (mediaSession != null)
            mediaSession.setActive(true);
        player.setPlayWhenReady(true);
    }

    protected void releasePlayer()
    {
        if (pendingPassthroughOffsetReanchor != null)
        {
            progressHandler.removeCallbacks(pendingPassthroughOffsetReanchor);
            pendingPassthroughOffsetReanchor = null;
        }
        playbackRateController.reset();
        cancelProgressUpdates();
        cancelPullSeekRecovery();
        cancelGrowingPullSeek();
        final ExoPlayer playerToRelease = player;
        final MediaSessionCompat sessionToRelease = mediaSession;
        // Keep MediaSession teardown on Android's main thread. Calling into
        // MediaSessionCompat synchronously from SageTV's media-command thread
        // can race the STOP runnable and prevent a stock server from receiving
        // DEINIT before its replacement-player timeout expires.
        // Detach and release the datasource before another setup can replace
        // these fields. The previous implementation dereferenced mutable fields
        // later on the UI thread and could release a newly opened SMB session.
        Exo2MediaPlayerImpl.super.releasePlayer();
        mediaSession = null;

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (sessionToRelease != null)
                {
                    try
                    {
                        log.logDebug("Releasing Android Media Session");
                        sessionToRelease.setActive(false);
                        sessionToRelease.release();
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error releasing Android Media Session", ex);
                    }
                }
                if (playerToRelease != null)
                {
                    try
                    {
                        playerToRelease.setPlayWhenReady(false);
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error pausing video during player releasing", ex);
                    }

                    try
                    {
                        playerToRelease.release();
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error calling release on player", ex);
                    }
                }
            }
        });

        this.RemoveSubTitleView();
    }

    @Override
    public Dimension getVideoDimensions()
    {
        log.logDebug("getVideoDimensions");

        if (player != null)
        {
            if (player.getVideoFormat() != null)
            {
                Dimension d = new Dimension(player.getVideoFormat().width, player.getVideoFormat().height);
                log.logDebug("getVideoSize(): " + d);

                return d;
            }
            else
            {
                log.logDebug("getVideoDimensions: player.getFormat is null");
            }
        }
        else
        {
            log.logDebug("getVideoDimensions: player is null");
        }
        return null;
    }

    @Override
    public long getPlayerMediaTimeMillis(long lastServerTime)
    {
        long position = this.getPlaybackPosition();

        //log.debug("ExoLogging - getPlayerMediaTimeMillis Called lastServerTime=" + Utils.toHHMMSS(lastServerTime) + " position=" + Utils.toHHMMSS(position));

        if (lastServerTime < 0)
        {
            log.logDebug("getPlayerMediaTimeMillis(): Flush was called waiting for last serverTime to be > 0");
            return -1;
        }

        return lastServerTime + position;
    }

    @Override
    public void stop()
    {
        playbackRateController.reset();
        cancelGrowingPullSeek();
        final ExoPlayer playerToStop = player;
        final MediaSessionCompat sessionToStop = mediaSession;
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                log.logDebug("Stop called");
                if(playerToStop != null)
                {
                    playerToStop.stop();
                }
                if(sessionToStop != null)
                {
                    sessionToStop.setActive(false);
                }

                if (playerReady)
                {
                    if (playerToStop == null)
                    {
                        return;
                    }

                    playerToStop.setPlayWhenReady(false);
                }
            }
        });

        super.stop();
    }

    @Override
    public void pause()
    {
        playbackRateController.onPause();
        log.logDebug("Pause called");
        playRequested = false;

        if (this.getState() == MiniPlayerPlugin.PAUSE_STATE && !pushMode)
        {
            frameStep(1);
            return;
        }


        super.pause();
        final PlaybackSessionController.Token session = currentPlaybackSession();
        final ExoPlayer pausePlayer = player;

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isCurrentPlaybackSession(session) || player != pausePlayer) return;
                if (playerReady)
                {
                    PlaybackDebugTrap.record("backend_pause_invoke", Exo2MediaPlayerImpl.this);
                    ExoPause();
                    PlaybackDebugTrap.record("backend_pause_return", Exo2MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
    }

    @Override
    public boolean frameStep(final int amount)
    {
        final ExoPlayer stepPlayer = player;
        if (!PlaybackFrameStepPolicy.canStep(getState(), pushMode, playerReady,
                stepPlayer != null, amount))
        {
            PlaybackDebugTrap.record("frame_step_unsupported", this);
            return false;
        }

        final PlaybackSessionController.Token session = beginPlaybackOperation(
                PlaybackSessionController.Operation.FRAME_STEP);
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isCurrentPlaybackSession(session) || player != stepPlayer
                        || getState() != MiniPlayerPlugin.PAUSE_STATE)
                {
                    PlaybackDebugTrap.record("frame_step_stale", Exo2MediaPlayerImpl.this);
                    return;
                }
                Format videoFormat = stepPlayer.getVideoFormat();
                float frameRate = videoFormat == null ? Format.NO_VALUE : videoFormat.frameRate;
                long targetMs = PlaybackFrameStepPolicy.targetPositionMs(
                        stepPlayer.getContentPosition(), stepPlayer.getDuration(), amount, frameRate);
                PlaybackDebugTrap.record("frame_step_invoke", Exo2MediaPlayerImpl.this);
                stepPlayer.setSeekParameters(SeekParameters.EXACT);
                stepPlayer.seekTo(targetMs);
                stepPlayer.setPlayWhenReady(false);
                PlaybackDebugTrap.record("frame_step_return", Exo2MediaPlayerImpl.this);
            }
        });
        return true;
    }

    @Override
    public void play()
    {
        log.logDebug("Play called");
        playRequested = true;

        super.play();
        playbackRateController.onPlay();
        final PlaybackSessionController.Token session = currentPlaybackSession();
        final ExoPlayer playPlayer = player;

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isCurrentPlaybackSession(session) || player != playPlayer) return;
                if (playerReady)
                {
                    PlaybackDebugTrap.record("backend_play_invoke", Exo2MediaPlayerImpl.this);
                    ExoStart();
                    PlaybackDebugTrap.record("backend_play_return", Exo2MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
    }

    @Override
    public float getContentFrameRateHz()
    {
        ExoPlayer activePlayer = player;
        Format format = activePlayer == null ? null : activePlayer.getVideoFormat();
        return format == null ? -1f : format.frameRate;
    }

    @Override
    public float setPlaybackRate(float rate)
    {
        if (pushMode || dvdPushMode || !playerReady || player == null
                || mediaContext.isTimeshifted() || mediaContext.getBufferSize() > 0L)
            return playbackRateController.getRate();
        beginPlaybackOperation(PlaybackSessionController.Operation.PLAYBACK_RATE);
        float accepted = playbackRateController.setRate(rate, playbackRateDriver);
        PlaybackDebugTrap.record("playback_rate_" + accepted, this);
        return accepted;
    }

    @Override
    public float getPlaybackRate()
    {
        return playbackRateController.getRate();
    }

    @Override
    public void setMute(final boolean muted)
    {
        serverMuted = muted;
        progressHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (player != null) player.setVolume(muted ? 0.0f : 1.0f);
            }
        });
    }

    @Override
    public boolean isMuted()
    {
        return serverMuted;
    }

    private void cancelPullSeekRecovery()
    {
        pullSeekRecoveryMonitor.cancel();
    }

    private void cancelGrowingPullSeek()
    {
        progressHandler.removeCallbacks(growingPullSeekRunnable);
        pendingGrowingPullSeekPlayer = null;
        pendingGrowingPullSeekSession = null;
        pendingGrowingPullSeekTargetMs = -1L;
    }

    private void scheduleGrowingPullSeek(long targetMs, boolean resume, boolean smb,
                                         PlaybackSessionController.Token session)
    {
        pendingGrowingPullSeekTargetMs = targetMs;
        pendingGrowingPullSeekResume = resume;
        pendingGrowingPullSeekSmb = smb;
        pendingGrowingPullSeekPlayer = player;
        pendingGrowingPullSeekSession = session;
        progressHandler.removeCallbacks(growingPullSeekRunnable);
        progressHandler.postDelayed(growingPullSeekRunnable,
                GROWING_PULL_SEEK_COALESCE_MS);
        PlaybackDebugTrap.recordDetailed("growing_pull_seek_coalesced", this,
                "targetMs=" + targetMs + ";delayMs=" + GROWING_PULL_SEEK_COALESCE_MS);
    }

    private SeekParameters choosePullSeekParameters(long currentPositionMs, long targetPositionMs)
    {
        PlaybackSyncPointPolicy.Target target = PlaybackSyncPointPolicy.choose(
                runtimeConfig.getSeekPolicy(), currentPositionMs,
                targetPositionMs, runtimeConfig.getDirectionalSyncMinDeltaMs());
        if (target == PlaybackSyncPointPolicy.Target.NEXT) return SeekParameters.NEXT_SYNC;
        if (target == PlaybackSyncPointPolicy.Target.PREVIOUS) return SeekParameters.PREVIOUS_SYNC;
        return SeekParameters.CLOSEST_SYNC;
    }

    private void armPullSeekRecovery(final long targetPositionMs)
    {
        if (!runtimeConfig.isSeekRecoveryEnabled() || pushMode || player == null || mediaSource == null)
        {
            return;
        }

        final PlaybackSessionController.Token session = currentPlaybackSession();
        final boolean resumeWhenReady = player.getPlayWhenReady();
        final long networkReadCountAtArm = dataSource instanceof Exo2PullDataSource
                ? ((Exo2PullDataSource) dataSource).getNetworkReadCount() : -1L;
        PlaybackDebugTrap.record("pull_seek_recovery_armed", Exo2MediaPlayerImpl.this);

        pullSeekRecoveryMonitor.arm(progressHandler, runtimeConfig.getSeekRecoveryDelayMs(),
                new PullSeekRecoveryMonitor.Guard()
                {
                    @Override
                    public boolean shouldRecover()
                    {
                        return isCurrentPlaybackSession(session) && !pushMode
                                && player != null && mediaSource != null
                                && player.getPlaybackState() == Player.STATE_BUFFERING;
                    }
                },
                new PullSeekRecoveryMonitor.Action()
                {
                    @Override
                    public void recover()
                    {
                        try
                        {
                            if (dataSource instanceof Exo2PullDataSource)
                            {
                                Exo2PullDataSource pull = (Exo2PullDataSource) dataSource;
                                long lastReadMs = pull.getLastPhysicalReadMonotonicMs();
                                long nowMs = android.os.SystemClock.elapsedRealtime();
                                long readAgeMs = lastReadMs < 0L ? Long.MAX_VALUE
                                        : nowMs - lastReadMs;
                                if (PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                                        networkReadCountAtArm, pull.getNetworkReadCount(),
                                        lastReadMs, nowMs, ACTIVE_PULL_IO_RECOVERY_GRACE_MS))
                                {
                                    PlaybackDebugTrap.recordDetailed(
                                            "pull_seek_recovery_deferred_active_io",
                                            Exo2MediaPlayerImpl.this,
                                            "readAgeMs=" + readAgeMs + ";reads="
                                                    + pull.getNetworkReadCount());
                                    armPullSeekRecovery(targetPositionMs);
                                    return;
                                }
                            }
                            beginPlaybackOperation(PlaybackSessionController.Operation.RECOVERY);
                            seekPending = true;
                            PlaybackDebugTrap.record("pull_seek_reprepare_before", Exo2MediaPlayerImpl.this);
                            player.setMediaSource(mediaSource, targetPositionMs);
                            player.prepare();
                            player.setPlayWhenReady(resumeWhenReady);
                            PlaybackDebugTrap.record("pull_seek_reprepare_after", Exo2MediaPlayerImpl.this);
                            log.logDebug("Pull seek recovery reprepare at " + targetPositionMs + "ms after " + runtimeConfig.getSeekRecoveryDelayMs() + "ms buffering watchdog");
                        }
                        catch (Exception ex)
                        {
                            PlaybackDebugTrap.record("pull_seek_reprepare_error_" + ex.getClass().getSimpleName(), Exo2MediaPlayerImpl.this);
                            log.logError("Pull seek recovery reprepare failed at " + targetPositionMs + "ms", ex);
                        }
                    }
                });
    }

    private void seekToImpl(long timeInMillis)
    {
        if (timeInMillis >= 0)
        {
            final PlaybackSessionController.Token session = currentPlaybackSession();
            final ExoPlayer seekPlayer = player;
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if (!isCurrentPlaybackSession(session) || player != seekPlayer)
                    {
                        log.logDebug("Ignoring stale queued seek for a replaced playback session");
                        return;
                    }
                    try
                    {
                        long currentPositionMs = player.getContentPosition();
                        long durationMs = player.getDuration();
                        long bufferedPositionMs = player.getBufferedPosition();
                        // Exo's buffered position is only the end of its local
                        // playback cache. It is not the live edge of a growing
                        // SageTV file. Near startup it may be only a few seconds
                        // even when SageTV has already recorded much more media;
                        // clamping a server-owned skip to it incorrectly seeks
                        // back to zero. Clamp only when the extractor provides a
                        // real duration. With unknown duration, SageTV remains
                        // authoritative for the requested live/timeshift target.
                        long safePositionMs = PlaybackSeekPolicy.clamp(
                                timeInMillis, durationMs, mediaContext.isTimeshifted());
                        log.logDebug("Seek Called - Current Position: " + currentPositionMs + "  Seek Request: " + timeInMillis
                                + " Safe Position: " + safePositionMs + " Duration: " + durationMs
                                + " Buffered Edge: " + bufferedPositionMs
                                + " Difference: " + (currentPositionMs - safePositionMs));
                        if (safePositionMs != timeInMillis)
                        {
                            PlaybackDebugTrap.record(mediaContext.isTimeshifted() ? "seek_clamped_live_edge" : "seek_clamped_eof", Exo2MediaPlayerImpl.this);
                        }
                        if (!pushMode)
                        {
                            log.logDebug("Pull seek capability: seekable=" + player.isCurrentMediaItemSeekable()
                                    + ", durationMs=" + durationMs + ", bufferedPositionMs=" + player.getBufferedPosition());
                            SeekParameters seekParameters = choosePullSeekParameters(currentPositionMs, safePositionMs);
                            player.setSeekParameters(seekParameters);
                            String seekPolicy = PlaybackSyncPointPolicy.wireName(
                                    seekParameters == SeekParameters.NEXT_SYNC ? PlaybackSyncPointPolicy.Target.NEXT
                                            : (seekParameters == SeekParameters.PREVIOUS_SYNC
                                            ? PlaybackSyncPointPolicy.Target.PREVIOUS
                                            : PlaybackSyncPointPolicy.Target.CLOSEST));
                            PlaybackDebugTrap.record("pull_seek_policy_" + seekPolicy, Exo2MediaPlayerImpl.this);
                            log.logDebug("Pull directional seek policy=" + seekPolicy + ", deltaMs=" + (safePositionMs - currentPositionMs));
                        }
                        else
                        {
                            cancelPullSeekRecovery();
                        }

                        Exo2PullDataSource pullDataSource = !pushMode
                                && dataSource instanceof Exo2PullDataSource
                                ? (Exo2PullDataSource) dataSource : null;
                        boolean smbDirectSeek = pullDataSource != null
                                && pullDataSource.isSmbModeConfigured();
                        // Use the completed SIZE-growth probe, not the legacy
                        // OPENURL hint. Stock SageTV marks recordings as
                        // potentially growing even after they are complete.
                        // Treating that hint as proof forced every seek through
                        // a full source reprepare and could interrupt TS sniffing.
                        boolean growingPullSeek = pullDataSource != null
                                && pullDataSource.isEffectivelyGrowing();
                        boolean repreparePullSeek = smbDirectSeek || growingPullSeek;
                        PlaybackDebugTrap.recordDetailed("backend_seek_invoke",
                                Exo2MediaPlayerImpl.this,
                                "requestedMs=" + timeInMillis + ";appliedMs=" + safePositionMs
                                        + ";durationMs=" + durationMs
                                        + ";bufferedMs=" + bufferedPositionMs);
                        if (PlaybackSeekPolicy.isInitialZeroSeekNoOp(currentPositionMs,
                                safePositionMs, firstVideoFrameRendered))
                        {
                            cancelPullSeekRecovery();
                            cancelGrowingPullSeek();
                            PlaybackDebugTrap.record("initial_zero_seek_source_retained",
                                    Exo2MediaPlayerImpl.this);
                            PlaybackDebugTrap.record("backend_seek_return",
                                    Exo2MediaPlayerImpl.this);
                            return;
                        }
                        if (growingPullSeek)
                        {
                            cancelPullSeekRecovery();
                            scheduleGrowingPullSeek(safePositionMs,
                                    player.getPlayWhenReady(), smbDirectSeek, session);
                        }
                        else if (smbDirectSeek)
                        {
                            // On the tested Fire TV MPEG-2 decoder, seekTo() updates
                            // Exo's clock and SMB byte range but can keep presenting
                            // pre-seek queued frames. Rebinding the same MediaSource at
                            // the requested position flushes extractor/decoder queues
                            // while retaining the player, surface, tracks, and shadow
                            // MediaServer session.
                            boolean resumeWhenReady = player.getPlayWhenReady();
                            cancelPullSeekRecovery();
                            PlaybackDebugTrap.record("smb_seek_reprepare_before",
                                    Exo2MediaPlayerImpl.this);
                            player.setMediaSource(mediaSource, safePositionMs);
                            player.prepare();
                            player.setPlayWhenReady(resumeWhenReady);
                            PlaybackDebugTrap.record("smb_seek_reprepare_after",
                                    Exo2MediaPlayerImpl.this);
                        }
                        else
                        {
                            player.seekTo(safePositionMs);
                        }
                        PlaybackDebugTrap.record("backend_seek_return", Exo2MediaPlayerImpl.this);
                        if (!pushMode && !repreparePullSeek)
                        {
                            armPullSeekRecovery(safePositionMs);
                        }

                    }
                    catch (Exception ex)
                    {
                        log.logError("Error during seek request. Position MS: " + timeInMillis, ex);
                    }
                }
            });
        }
    }

    @Override
    public void seek(long timeInMS)
    {
        try
        {
            playbackPositionLock.lock();

            // The extractor may be tens of seconds ahead of rendered video.
            // Never carry that pre-seek caption queue across a clock jump.
            resetLegacyCaptionsForDiscontinuity();


            //currentPlaybackPosition = 0; //Set this to zero during seek.  Lock will hopefully keep it at zero unti we are completed

            log.logDebug("Seek - pushmode: " + pushMode + ", timeinMS " + timeInMS + ", playerReady " + playerReady);

            super.seek(timeInMS);

            if (playerReady)
            {
                if (!pushMode)
                {
                    if (player != null)
                    {
                        seekToImpl(timeInMS);
                    }
                    else
                    {

                        log.logDebug("Seek player is null storing position: " + timeInMS);

                        playbackStartPosition = timeInMS;
                    }
                }
                else
                {
                    if (player != null)
                    {
                        seekToImpl(timeInMS);
                    }
                }
            }
            else
            {

                log.logDebug("Seek Resume: " + timeInMS);
                playbackStartPosition = timeInMS;
            }
        }
        catch (Exception ex)
        {
            log.logError("Unexpected error during seek", ex);
            ex.printStackTrace();
        }
        finally
        {
            playbackPositionLock.unlock();
        }
    }

    @Override
    public void setSubtitleTrack(final int streamPos)
    {
        requestedSubtitleTrack = streamPos;
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                log.logDebug("Set Subtitle Track Called: " + streamPos);

                if (handleTeletextSubtitleSelection(streamPos))
                {
                    showCaptions = false;
                    RemoveSubTitleView();
                    changeTrack(C.TRACK_TYPE_TEXT, DISABLE_TRACK, 0);
                    return;
                }

                if (streamPos == Exo2MediaPlayerImpl.DISABLE_TRACK)
                {
                    showCaptions = false;
                    RemoveSubTitleView();
                }
                else
                {
                    showCaptions = true;
                    AddSubTitleView();
                }

                changeTrack(C.TRACK_TYPE_TEXT, streamPos, 0);
            }
        });
    }

    @Override
    public void setPreferredSubtitleTrack()
    {
        if (applyPendingServerSubpictureStream()) return;
        if (applyConfiguredClosedCaptionSlot()) return;
        requestedSubtitleTrack = PREFERRED_TRACK;
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                showCaptions = true;
                AddSubTitleView();
                int preferredTrack = resolvePreferredSubtitleTrack();
                if (preferredTrack < 0)
                {
                    log.logDebug("Preferred subtitle track is pending track discovery");
                    return;
                }
                if (selectedSubtitleTrack != preferredTrack)
                    changeTrack(C.TRACK_TYPE_TEXT, preferredTrack, 0);
            }
        });
    }

    private int resolvePreferredSubtitleTrack()
    {
        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        String standard = prefs.getString(PrefStore.Keys.preferred_caption_standard,
                TrackPreferencePolicy.CAPTION_STANDARD_AUTO);
        return TrackPreferencePolicy.findPreferredSubtitleTrack(getSubtitleTracks(),
                prefs.getString(PrefStore.Keys.preferred_subtitle_language, ""), standard,
                TrackPreferencePolicy.parseCaptionService(standard,
                        prefs.getString(PrefStore.Keys.preferred_caption_service, "1")));
    }

    @Override
    public int getSelectedSubtitleTrack()
    {
        return selectedTeletextTrackOr(this.selectedSubtitleTrack);
    }

    @Override
    public int getSubtitleTrackCount()
    {
        return getTrackCount(C.TRACK_TYPE_TEXT) + teletextTrackCount();
    }

    @Override
    public void setAudioTrack(final int streamPos)
    {
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!ExoIsPlaying())
                {
                    initialAudioTrackIndex = streamPos;
                }
                else
                {
                    initialAudioTrackIndex = -1;
                    changeTrack(C.TRACK_TYPE_AUDIO, streamPos, 0);
                }
            }
        });
    }

    @Override
    public int[] getAudioTrackIds()
    {
        ExoPlayer current = player;
        if (current == null) return new int[0];
        List<Integer> ids = new ArrayList<Integer>();
        int audioGroup = 0;
        for (Tracks.Group group : current.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            ids.add(audioGroup++);
        }
        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) result[i] = ids.get(i);
        return result;
    }

    @Override
    public String[] getAudioTrackLabels()
    {
        ExoPlayer current = player;
        if (current == null) return new String[0];
        List<String> labels = new ArrayList<String>();
        int audioGroup = 0;
        for (Tracks.Group group : current.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            Format format = group.getTrackFormat(0);
            StringBuilder label = new StringBuilder().append(audioGroup + 1);
            if (format.language != null && !format.language.isEmpty())
                label.append(" ").append(format.language);
            if (format.label != null && !format.label.isEmpty())
                label.append(" ").append(format.label);
            if (format.sampleMimeType != null && !format.sampleMimeType.isEmpty())
                label.append(" ").append(format.sampleMimeType.replace("audio/", ""));
            if (format.channelCount > 0)
                label.append(" ").append(format.channelCount).append("ch");
            labels.add(label.toString());
            audioGroup++;
        }
        return labels.toArray(new String[labels.size()]);
    }

    @Override
    public int getSelectedAudioTrack()
    {
        ExoPlayer current = player;
        if (current == null) return -1;
        int audioGroup = 0;
        for (Tracks.Group group : current.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++)
                if (group.isTrackSelected(i)) return audioGroup;
            audioGroup++;
        }
        return -1;
    }

    @Override
    public String getAudioOutputSummary()
    {
        ExoPlayer current = player;
        Format format = current == null ? null : current.getAudioFormat();
        String stream = format == null || format.sampleMimeType == null
                ? "audio not resolved" : format.sampleMimeType.replace("audio/", "");
        String sampleRate = format != null && format.sampleRate > 0
                ? ", " + format.sampleRate + " Hz" : "";
        return audioPassthroughEnabled
                ? "Encoded passthrough allowed; stream " + stream + sampleRate
                : "Decoded PCM stereo; stream " + stream + sampleRate;
    }

    @Override public boolean supportsAudioPassthroughControl() { return true; }

    @Override public boolean isAudioPassthroughEnabled()
    {
        return audioPassthroughEnabled;
    }

    @Override
    public boolean suspendAudioForExclusiveDiagnostic()
    {
        if (Looper.myLooper() != Looper.getMainLooper() || player == null
                || trackSelector == null || exclusiveDiagnosticAudioSuspended)
            return false;
        MappingTrackSelector.MappedTrackInfo trackInfo =
                trackSelector.getCurrentMappedTrackInfo();
        int rendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_AUDIO);
        DefaultTrackSelector.Parameters.Builder builder =
                trackSelector.buildUponParameters();
        if (rendererIndex != C.INDEX_UNSET)
            builder.setRendererDisabled(rendererIndex, true);
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true);
        trackSelector.setParameters(builder.build());
        exclusiveDiagnosticAudioSuspended = true;
        PlaybackDebugTrap.recordDetailed("diagnostic_audio_suspended", this,
                "backend=legacy_exo");
        return true;
    }

    @Override
    public void resumeAudioAfterExclusiveDiagnostic()
    {
        if (!exclusiveDiagnosticAudioSuspended) return;
        exclusiveDiagnosticAudioSuspended = false;
        Runnable restore = new Runnable()
        {
            @Override public void run()
            {
                if (trackSelector == null || player == null) return;
                MappingTrackSelector.MappedTrackInfo trackInfo =
                        trackSelector.getCurrentMappedTrackInfo();
                int rendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_AUDIO);
                DefaultTrackSelector.Parameters.Builder builder =
                        trackSelector.buildUponParameters();
                if (rendererIndex != C.INDEX_UNSET)
                    builder.setRendererDisabled(rendererIndex, false);
                builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false);
                trackSelector.setParameters(builder.build());
                PlaybackDebugTrap.recordDetailed("diagnostic_audio_resumed",
                        Exo2MediaPlayerImpl.this, "backend=legacy_exo");
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) restore.run();
        else context.runOnUiThread(restore);
    }

    @Override
    public boolean setAudioPassthroughEnabled(final boolean enabled)
    {
        ActivePlayerSessionOverrides.setAudioPassthroughEnabled(enabled);
        if (enabled == audioPassthroughEnabled)
            return true;
        return requestAudioOutputRebuild("passthrough=" + enabled);
    }

    private boolean requestAudioOutputRebuild(final String reason)
    {
        final ExoPlayer expectedPlayer = player;
        final DataSource expectedDataSource = dataSource;
        final String expectedUrl = url;
        if (expectedPlayer == null || (expectedDataSource == null && !httpls)
                || expectedUrl == null
                || audioOutputRebuildQueued)
            return false;
        audioOutputRebuildQueued = true;
        context.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    if (player != expectedPlayer || dataSource != expectedDataSource)
                        return;
                    long positionMs = Math.max(0L, expectedPlayer.getCurrentPosition());
                    boolean resume = expectedPlayer.getPlayWhenReady();
                    int selectedAudioTrack = getSelectedAudioTrack();
                    playbackRateController.reset();
                    cancelProgressUpdates();
                    cancelPullSeekRecovery();
                    cancelGrowingPullSeek();
                    MediaSessionCompat oldSession = mediaSession;
                    mediaSession = null;
                    player = null;
                    playerReady = false;
                    try { expectedPlayer.setPlayWhenReady(false); } catch (Exception ignored) { }
                    try { expectedPlayer.release(); } catch (Exception ex)
                    { log.logError("Error releasing legacy Exo for audio-output rebuild", ex); }
                    if (oldSession != null)
                    {
                        try { oldSession.setActive(false); oldSession.release(); }
                        catch (Exception ex)
                        { log.logError("Error releasing MediaSession for audio-output rebuild", ex); }
                    }
                    retainedAudioRebuildDataSource = expectedDataSource;
                    if (!pushMode)
                        playbackStartPosition = positionMs;
                    playRequested = resume;
                    eos = false;
                    state = resume ? PLAY_STATE : PAUSE_STATE;
                    context.setupVideoFrame();
                    PlaybackDebugTrap.recordDetailed("audio_output_live_rebuild",
                            Exo2MediaPlayerImpl.this,
                            "reason=" + reason + ", positionMs=" + positionMs
                                    + ", push=" + pushMode);
                    setupPlayer(expectedUrl);
                    if (selectedAudioTrack >= 0)
                        setAudioTrack(selectedAudioTrack);
                }
                finally
                {
                    audioOutputRebuildQueued = false;
                }
            }
        });
        return true;
    }

    @Override public boolean supportsAudioOffset()
    {
        return audioPassthroughEnabled
                ? passthroughOffsetController != null
                        && passthroughOffsetController.isEnabled()
                : pcmAudioProcessor != null;
    }

    @Override public boolean setAudioOffsetMillis(int offsetMs)
    {
        ActivePlayerSessionOverrides.setAudioOffsetMs(offsetMs);
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        if (audioPassthroughEnabled && controller != null && controller.isEnabled())
        {
            int bounded = ActivePlayerSessionOverrides.getAudioOffsetMs();
            controller.setOffsetMillis(bounded);
            schedulePassthroughOffsetReanchor("passthrough_offset=" + bounded);
            return true;
        }
        Exo2PcmAudioProcessor processor = pcmAudioProcessor;
        if (audioPassthroughEnabled || processor == null)
            return false;
        processor.setOffsetMillis(offsetMs);
        return true;
    }

    @Override public int getAudioOffsetMillis()
    {
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        if (audioPassthroughEnabled && controller != null)
        {
            Integer requested = ActivePlayerSessionOverrides.getAudioOffsetMs();
            return requested == null ? controller.getOffsetMillis() : requested;
        }
        Exo2PcmAudioProcessor processor = pcmAudioProcessor;
        return processor == null ? 0 : processor.getOffsetMillis();
    }

    @Override public boolean supportsPassthroughAudioOffset()
    {
        return audioPassthroughEnabled && passthroughOffsetController != null;
    }

    @Override public boolean setPassthroughAudioOffsetEnabled(boolean enabled)
    {
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        if (!audioPassthroughEnabled || controller == null) return false;
        ActivePlayerSessionOverrides.setPassthroughAudioOffsetEnabled(enabled);
        controller.setEnabled(enabled);
        schedulePassthroughOffsetReanchor("passthrough_offset_enabled=" + enabled);
        return true;
    }

    @Override public boolean isPassthroughAudioOffsetEnabled()
    {
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        if (!audioPassthroughEnabled || controller == null) return false;
        Boolean requested = ActivePlayerSessionOverrides
                .getPassthroughAudioOffsetEnabled();
        return requested == null ? controller.isEnabled() : requested;
    }

    @Override public String getAudioOffsetSummary()
    {
        EncodedPassthroughOffsetController controller = passthroughOffsetController;
        if (audioPassthroughEnabled)
        {
            if (controller == null) return "passthrough clock offset unavailable";
            Boolean requested = ActivePlayerSessionOverrides
                    .getPassthroughAudioOffsetEnabled();
            Integer requestedMs = ActivePlayerSessionOverrides.getAudioOffsetMs();
            if (requested != null || requestedMs != null)
                return "passthrough clock offset "
                        + (requested == null ? controller.isEnabled() : requested)
                        + ", requestedMs="
                        + (requestedMs == null ? controller.getOffsetMillis() : requestedMs)
                        + ", applied=" + controller.describe();
            return controller.describe();
        }
        Exo2PcmAudioProcessor processor = pcmAudioProcessor;
        return processor == null ? "decoded PCM offset unavailable"
                : "decoded PCM offset " + processor.getOffsetMillis() + " ms";
    }

    private void schedulePassthroughOffsetReanchor(final String reason)
    {
        if (pendingPassthroughOffsetReanchor != null)
            progressHandler.removeCallbacks(pendingPassthroughOffsetReanchor);
        pendingPassthroughOffsetReanchor = null;
        if (pushMode)
        {
            // Push is server-clocked and is not locally seekable. Let the
            // atomic controller affect new samples and allow the next server
            // discontinuity to flush the prior timestamp queue.
            PlaybackDebugTrap.recordDetailed("passthrough_offset_push_deferred",
                    this, "reason=" + reason);
            return;
        }
        final ExoPlayer expectedPlayer = player;
        pendingPassthroughOffsetReanchor = new Runnable()
        {
            @Override public void run()
            {
                pendingPassthroughOffsetReanchor = null;
                if (expectedPlayer == null || player != expectedPlayer) return;
                long positionMs = Math.max(0L, expectedPlayer.getCurrentPosition());
                try
                {
                    // The active extractor already references this atomic
                    // controller. Flush only pre-change timestamps; do not
                    // release encoded AudioTrack/player state on the UI thread.
                    expectedPlayer.seekTo(positionMs);
                    PlaybackDebugTrap.recordDetailed("passthrough_offset_live_reanchor",
                            Exo2MediaPlayerImpl.this,
                            "reason=" + reason + ", positionMs=" + positionMs);
                }
                catch (RuntimeException ex)
                {
                    log.logError("Unable to re-anchor legacy Exo passthrough offset", ex);
                }
            }
        };
        progressHandler.postDelayed(pendingPassthroughOffsetReanchor, 200L);
    }

    @Override
    public synchronized void flush()
    {
        log.logDebug("Flush called");
        resetLegacyCaptionsForDiscontinuity();

        // Pull-mode seeking is owned by Exo's seek/DataSource reopen path.  SageTV may
        // still issue FLUSH around a seek, but replacing the MediaSource here resets
        // the player to 0:00 after the requested seek has already been accepted.
        // Only PUSH mode needs the player pipeline reset because the server is
        // rebasing the byte stream underneath the player.
        if (!pushMode)
        {
            beginPlaybackOperation(PlaybackSessionController.Operation.FLUSH);
            flushed = false;
            log.logDebug("Ignoring destructive player reset for Pull-mode flush");
            return;
        }

        super.flush();
        final PlaybackSessionController.Token session = currentPlaybackSession();
        final ExoPlayer flushPlayer = player;

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isCurrentPlaybackSession(session) || player != flushPlayer) return;
                try
                {
                    playbackPositionLock.lock();

                    if (player == null)
                    {
                        return;
                    }

                    PlaybackDebugTrap.record("flush_reprepare_before", Exo2MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, true);
                    player.prepare();
                    PlaybackDebugTrap.record("flush_reprepare_after", Exo2MediaPlayerImpl.this);

                    log.logDebug("After Flush was called Current Playback Position: " + Utils.toHHMMSS(player.getCurrentPosition()));
                    Exo2MediaPlayerImpl.this.currentPlaybackPosition = player.getCurrentPosition();
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("flush_reprepare_error_" + ex.getClass().getSimpleName(), Exo2MediaPlayerImpl.this);
                    log.logError("Exo2 flush/reprepare failed", ex);
                }
                finally
                {
                    playbackPositionLock.unlock();
                }


            }
        });
    }

    @Override
    protected void setupPlayer(String sageTVurl)
    {
        initialAudioTrackIndex = -1;
        dvdPushMode = "push:dvd".equals(lastUri)
                || (sageTVurl != null && (sageTVurl.startsWith("push:dvd")
                || sageTVurl.endsWith("/push:dvd")));

        if (player != null)
        {
            releasePlayer();
        }

        this.url = sageTVurl;

        // VerboseLogUtil.setEnableAllTags(true);

        //if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        log.logDebug("Setting up the Exo2 media player for: " + sageTVurl);

        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        ActivePlayerSessionOverrides.applyRuntimeTuning(
                prefs, PlayerRuntimeConfig.Backend.LEGACY_EXO);
        PlayerRuntimeTuning.applyExo2CodecPreference(prefs.getString(
                PrefStore.Keys.exo2_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE));
        runtimeConfig = PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.LEGACY_EXO);
        log.logDebug("Runtime configuration: " + runtimeConfig.compactLog());

        if (retainedAudioRebuildDataSource != null)
        {
            dataSource = retainedAudioRebuildDataSource;
            retainedAudioRebuildDataSource = null;
            log.logDebug("Reusing active datasource for live audio-output rebuild");
        }
        else if (pushMode)
        {
            log.logDebug("Creating Exo2PushDataSource datasource");
            dataSource = new Exo2PushDataSource();
        }
        else
        {
            if (!httpls)
            {
                log.logDebug("Creating datasource");
                SmbDirectConfig smbConfig = AndroidPrefStore.isSmbStreamingMode(prefs.getStreamingMode())
                        ? SmbDirectConfig.from(prefs) : null;
                dataSource = new Exo2PullDataSource(
                        context.getClient().getConnectedServerInfo().address,
                        mediaContext.isTimeshifted(), mediaContext.isMetadataExplicit(),
                        smbConfig, runtimeConfig.getPullReadBytes());
            }
            else
            {
                log.logDebug("Creating null datasource");
                dataSource = null;
            }
        }

        audioPassthroughEnabled = ActivePlayerSessionOverrides
                .resolveAudioPassthroughEnabled(!prefs.getBoolean(
                        PrefStore.Keys.disable_audio_passthrough, true));
        boolean disableAudioPassthrough = !audioPassthroughEnabled;
        Integer sessionAudioOffset = ActivePlayerSessionOverrides.getAudioOffsetMs();
        int audioOffsetMs = sessionAudioOffset == null
                ? prefs.getInt(PrefStore.Keys.playback_audio_offset_ms, 0)
                : sessionAudioOffset;
        boolean passthroughOffsetEnabled = ActivePlayerSessionOverrides
                .resolvePassthroughAudioOffsetEnabled(prefs.getBoolean(
                        PrefStore.Keys.playback_passthrough_audio_offset_enabled, false));
        passthroughOffsetController = audioPassthroughEnabled
                ? new EncodedPassthroughOffsetController(
                        passthroughOffsetEnabled, audioOffsetMs) : null;
        Exo2AudioExtensionRenderersFactory audioRenderersFactory =
                new Exo2AudioExtensionRenderersFactory(
                        context.getContext(), audioPassthroughEnabled, audioOffsetMs);
        pcmAudioProcessor = audioRenderersFactory.getPcmAudioProcessor();
        DefaultRenderersFactory renderersFactory = audioRenderersFactory;
        String codecMode = runtimeConfig.getCodecMode();
        if ("async".equals(codecMode)) renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
        else if ("sync".equals(codecMode)) renderersFactory.forceDisableMediaCodecAsynchronousQueueing();
        log.logDebug("Legacy Exo codec adapter mode: " + codecMode);

        if (FfmpegLibrary.isAvailable() && disableAudioPassthrough)
        {
            // Prefer the isolated FFmpeg audio extension when encoded
            // passthrough is disabled. Video still uses the selected
            // MediaCodec path because this module contains audio renderers.
            renderersFactory.setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            log.logDebug("Legacy Exo audio policy: encoded passthrough disabled; "
                    + "prefer decoded PCM output");
        }
        else if (FfmpegLibrary.isAvailable())
        {
            final int preferExtensionDecoders = MiniclientApplication.get().getClient().properties().getInt(PrefStore.Keys.exoplayer_ffmpeg_extension_setting, 1);

            switch (preferExtensionDecoders)
            {
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER:
                    log.logDebug("Setting FFmpeg Extension to Prefer");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                    break;
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON:
                    log.logDebug("Setting FFmpeg Extension to On");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
                    break;
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF:
                    log.logDebug("Setting FFmpeg Extension to Off");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
                    break;
                default:
                    log.logDebug("Defaulting FFmpeg Extension to On");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
            }
        }
        else
        {
            log.logDebug("Legacy Exo audio policy: "
                    + (disableAudioPassthrough
                    ? "encoded passthrough disabled; platform PCM decode required"
                    : "encoded passthrough allowed"));
        }

        DecodingMethod decodingMethod = DecodingMethod.fromPreference(
                ActivePlayerSessionOverrides.resolveDecodingMethod(prefs.getString(
                        PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE)));
        CustomMediaCodecSelector mediaCodecSelector = new CustomMediaCodecSelector(decodingMethod,
                decoderAttemptTelemetry, sessionDecoderExclusions);
        renderersFactory.setMediaCodecSelector(mediaCodecSelector);
        // The selector has already constrained this ordered list to the chosen
        // policy. Let Exo try the next candidate when codec initialization
        // fails: Hardware stays hardware-only, Software stays software-only,
        // and Fallback remains hardware-then-software.
        renderersFactory.setEnableDecoderFallback(true);
        log.logDebug("Legacy Exo Decoding Method: " + decodingMethod.displayName());

        trackSelector = new DefaultTrackSelector(context.getContext());
        String preferredAudioLanguage = TrackPreferencePolicy.normalizeLanguage(
                prefs.getString(PrefStore.Keys.preferred_audio_language, ""));
        if (!preferredAudioLanguage.isEmpty())
        {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(preferredAudioLanguage));
            log.logDebug("Preferred audio language: " + preferredAudioLanguage);
        }


        ExoPlayer.Builder builder = new ExoPlayer.Builder(context.getContext(), renderersFactory);

        if (!pushMode && !httpls)
        {
            DefaultLoadControl pullLoadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            runtimeConfig.getPullMinBufferMs(),
                            runtimeConfig.getPullMaxBufferMs(),
                            runtimeConfig.getPullPlaybackBufferMs(),
                            runtimeConfig.getPullRebufferMs())
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            builder.setLoadControl(pullLoadControl);
            log.logDebug("Pull load control enabled: min=" + runtimeConfig.getPullMinBufferMs()
                    + "ms max=" + runtimeConfig.getPullMaxBufferMs()
                    + "ms seek=" + runtimeConfig.getPullPlaybackBufferMs()
                    + "ms rebuffer=" + runtimeConfig.getPullRebufferMs() + "ms");
        }

        builder.setTrackSelector(trackSelector);
        player = builder.build();
        player.setVolume(serverMuted ? 0.0f : 1.0f);
        final PlaybackSessionController.Token listenerSession = currentPlaybackSession();
        final ExoPlayer listenerPlayer = player;
        //player.addAnalyticsListener(new EventLogger(trackSelector));

        player.addAnalyticsListener(new AnalyticsListener()
        {
            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                    long initializedTimestampMs, long initializationDurationMs)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                    decoderAttemptTelemetry.recordSelectedVideo(decoderName);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName,
                    long initializedTimestampMs, long initializationDurationMs)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                    decoderAttemptTelemetry.recordSelectedAudio(decoderName);
            }

            @Override
            public void onAudioUnderrun(EventTime eventTime, int bufferSize,
                    long bufferSizeMs, long elapsedSinceLastFeedMs)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                    decoderAttemptTelemetry.recordAudioUnderrun();
            }

            @Override
            public void onAudioSinkError(EventTime eventTime, Exception audioSinkError)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                {
                    decoderAttemptTelemetry.recordAudioOutputError(
                            audioSinkError.getClass().getSimpleName());
                    PlaybackDebugTrap.recordDetailed("audio_sink_error",
                            Exo2MediaPlayerImpl.this,
                            audioSinkError.getClass().getSimpleName() + ":"
                                    + String.valueOf(audioSinkError.getMessage()));
                    log.logError("Legacy Exo audio sink error", audioSinkError);
                }
            }

            @Override
            public void onVideoCodecError(EventTime eventTime, Exception videoCodecError)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                    decoderAttemptTelemetry.recordCodecError(
                            videoCodecError.getClass().getSimpleName());
            }

            @Override
            public void onDownstreamFormatChanged(EventTime eventTime,
                    MediaLoadData mediaLoadData)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer)
                    return;
                Format format = mediaLoadData.trackFormat;
                decoderAttemptTelemetry.recordFormatChange(format == null
                        ? Integer.toString(mediaLoadData.trackType)
                        : mediaLoadData.trackType + ":" + format.sampleMimeType
                        + ":" + format.width + "x" + format.height);
            }
        });

        player.addListener(new Player.Listener()
        {
            @Override
            public void onTracksChanged(Tracks tracks)
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                {
                    decoderAttemptTelemetry.recordTrackChange();
                    // Caption tracks may arrive after prepare. Reapply the
                    // persisted virtual slot without requiring menu input.
                    applyConfiguredClosedCaptionSlot();
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer)
                        return;
                    log.logDebug("Track map changed - debugging available tracks in file");
                    debugAvailableTracks(listenerPlayer);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("player_error_" + error.getErrorCodeName(), Exo2MediaPlayerImpl.this);
                log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                error.printStackTrace();

                Throwable failureCause = error.getCause();
                PlaybackFailureClassifier.Decision failure =
                        PlaybackFailureClassifier.classify(
                                error.getErrorCodeName(),
                                failureCause == null ? "" : failureCause.getClass().getName(),
                                failureCause == null ? "" : failureCause.getMessage(),
                                firstVideoFrameRendered, seekPending, flushed, false);
                PlaybackDebugTrap.record("player_error_class_" + failure.kind.name()
                        + "_recovery_" + failure.recovery.name(), Exo2MediaPlayerImpl.this);
                if (failure.transitionStale)
                    PlaybackDebugTrap.record("player_error_transition_stale",
                            Exo2MediaPlayerImpl.this);
                log.logDebug("PLAYER ERROR CLASS: " + failure.kind
                        + ", recovery=" + failure.recovery
                        + ", maxAttempts=" + failure.maxAutomaticAttempts);
                if (failure.kind == PlaybackFailureClassifier.Kind.DECODER_INITIALIZATION
                        || failure.kind == PlaybackFailureClassifier.Kind.DECODER_RUNTIME)
                    decoderAttemptTelemetry.recordCodecError(error.getErrorCodeName());
                else if (failure.kind == PlaybackFailureClassifier.Kind.AUDIO_OUTPUT)
                    decoderAttemptTelemetry.recordAudioOutputError(error.getErrorCodeName());
                decoderAttemptTelemetry.recordFallback(failure.kind.name(),
                        failure.recovery.name());

                if (retryCount == 0 && failure.showToUser)
                {
                    //Show toast on first error
                    context.showErrorMessage(error.getErrorCodeName(), "Exo2MediaPlayer");
                }
                else if (retryCount == 0)
                {
                    PlaybackDebugTrap.record("recoverable_player_warning_suppressed_"
                            + error.getErrorCodeName(), Exo2MediaPlayerImpl.this);
                    log.logDebug("Suppressing recoverable post-start player warning: "
                            + error.getErrorCodeName());
                }

                if (failure.recovery == PlaybackFailureClassifier.Recovery.NONE)
                {
                    errorState = true;
                    PlaybackDebugTrap.record("player_error_no_automatic_recovery",
                            Exo2MediaPlayerImpl.this);
                    return;
                }

                if (failure.recovery == PlaybackFailureClassifier.Recovery
                        .REBUILD_PLAYER_WITH_DECODER_EXCLUSION)
                {
                    String failedDecoder = decoderAttemptTelemetry.selectedVideo();
                    if (failedDecoder.length() == 0
                            || !sessionDecoderExclusions.add(failedDecoder))
                    {
                        errorState = true;
                        decoderAttemptTelemetry.recordFallback(failure.kind.name(),
                                failedDecoder.length() == 0
                                        ? "no-selected-decoder" : "decoder-already-excluded");
                        PlaybackDebugTrap.record("decoder_quarantine_not_available",
                                Exo2MediaPlayerImpl.this);
                        return;
                    }
                    decoderAttemptTelemetry.recordExclusion(failedDecoder);
                    decoderAttemptTelemetry.recordFallback(failure.kind.name(),
                            "decoder-excluded-reprepare-started");
                    PlaybackDebugTrap.record("decoder_session_excluded_" + failedDecoder,
                            Exo2MediaPlayerImpl.this);
                }

                if (retryCount < failure.maxAutomaticAttempts)
                {
                    errorState = true;
                    retryCount++;
                    long recoveryPositionMs = Math.max(0L, player.getCurrentPosition());
                    boolean resumePlayback = player.getPlayWhenReady();
                    if (!pushMode && mediaSource != null)
                    {
                        player.setMediaSource(mediaSource, recoveryPositionMs);
                        player.prepare();
                        player.setPlayWhenReady(resumePlayback);
                        PlaybackDebugTrap.record("player_error_recovery_position_preserved",
                                Exo2MediaPlayerImpl.this);
                    }
                    else
                    {
                        // PUSH/FIXED seeks remain owned by the SageTV server.
                        player.seekTo(recoveryPositionMs + 100L);
                        player.prepare();
                    }
                }
                else
                {
                    decoderAttemptTelemetry.recordFallback(failure.kind.name(),
                            "recovery-limit-reached");
                    log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                    log.logError("Playback Exception: " + error.getErrorCodeName(), error);
                    context.showErrorMessage("Playback recovery limit reached ("
                            + failure.kind.name() + ")", "Exo2MediaPlayer");
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                PlaybackDebugTrap.record("playback_state_" + playbackState, Exo2MediaPlayerImpl.this);
                if (!pushMode && player != null)
                {
                    log.logDebug("Pull playback state=" + playbackState
                            + ", positionMs=" + player.getCurrentPosition()
                            + ", bufferedPositionMs=" + player.getBufferedPosition()
                            + ", playWhenReady=" + player.getPlayWhenReady());
                }
                if (playbackState == Player.STATE_ENDED)
                {
                    log.logDebug("Player.STATE_ENDED");
                    log.logDebug("Player Has Ended, set EOS");
                    //stop(); - JVL: Not sure if we will need to do this or not
                    //notifySageTVStop();
                    eos = true;
                    state = Exo2MediaPlayerImpl.EOS_STATE;
                    endTeletextPresentation();
                }
                if (playbackState == Player.STATE_READY)
                {
                    cancelPullSeekRecovery();
                    log.logDebug("Player.STATE_READY - Media loaded and ready for playback");
                    if (errorState)
                    {
                        decoderAttemptTelemetry.recordFallback(
                                decoderAttemptTelemetry.fallbackReason(), "ready");
                        errorState = false;
                        retryCount = 0;
                    }

                    log.logDebug("Player.STATE_READY - setAudioTrack getting called");
                    if (initialAudioTrackIndex != -1)
                    {
                        setAudioTrack(initialAudioTrackIndex);
                        initialAudioTrackIndex = -1;
                    }

                    // Apply persisted DVB/CC slots after the final track map
                    // is ready; no second menu selection should be needed.
                    boolean configuredCaptionApplied = applyConfiguredClosedCaptionSlot();
                    if (!configuredCaptionApplied && requestedSubtitleTrack == PREFERRED_TRACK)
                    {
                        setPreferredSubtitleTrack();
                    }
                    else if (!configuredCaptionApplied && requestedSubtitleTrack != DISABLE_TRACK
                            && selectedSubtitleTrack != requestedSubtitleTrack)
                    {
                        log.logDebug("Applying pending SageTV subtitle track: " + requestedSubtitleTrack);
                        setSubtitleTrack(requestedSubtitleTrack);
                    }

                    long duration = 0;

                    if(player.getDuration() < 0)
                    {
                        duration = -1;
                    }
                    else
                    {
                        duration = player.getDuration();
                    }
                    //Library files start with stc:// but do not have push in it
                    //Live TV has push: with a lot of other data in it

                    setMediaSessionMetadata(sageTVurl, duration);
                    mediaSession.setActive(true);
                    updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
                }
                if (playbackState == Player.STATE_IDLE)
                {
                    if (errorState)
                    {
                        log.logDebug("Player.STATE_IDLE - Error state is true, retry count: " + retryCount);
                    }
                }

            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                PlaybackDebugTrap.record("position_discontinuity_" + reason, Exo2MediaPlayerImpl.this);
                switch (reason)
                {
                    case Player.DISCONTINUITY_REASON_SEEK:
                        setPlaybackPosition(newPosition.positionMs);
                        updateMediaSessionPlaybackState(newPosition.positionMs);
                        seekPending = false;
                        break;

                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                PlaybackDebugTrap.record(isPlaying ? "is_playing_true" : "is_playing_false", Exo2MediaPlayerImpl.this);
            }

            @Override
            public void onRenderedFirstFrame()
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                firstVideoFrameRendered = true;
                if (dataSource instanceof Exo2PullDataSource)
                    ((Exo2PullDataSource) dataSource).endSeekableSnapshotPreparation();
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("first_video_frame", Exo2MediaPlayerImpl.this);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize)
            {
                applySavedRefreshRateIfPossible();
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                int width = videoSize.width;
                int height = videoSize.height;
                float pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio;

                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.logDebug("ExoPlayer.onVideoSizeChanged: " + width + "x" + height + ", pixel ratio: " + pixelWidthHeightRatio);
                }

                // note if pixel ratio is != 0 then calc the ar and apply it.
                if (pixelWidthHeightRatio != 0f)
                {
                    setVideoSize(width, height, pixelWidthHeightRatio * ((float) width / (float) height));
                }
                else
                {
                    setVideoSize(width, height, 0);
                }
            }
        });


        player.addListener(new Player.Listener()
        {
            @Override
            public void onCues(List<Cue> cues)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                subtitleCueUpdateCount++;
                StringBuilder text = new StringBuilder();
                int bitmapCues = 0;
                for (Cue cue : cues)
                {
                    if (cue.text != null && cue.text.length() > 0)
                    {
                        if (text.length() > 0)
                            text.append(' ');
                        text.append(cue.text);
                    }
                    if (cue.bitmap != null)
                        bitmapCues++;
                }
                currentSubtitleCueCount = cues.size();
                currentSubtitleCueText = text.toString();
                if (text.length() > 0 || bitmapCues > 0)
                {
                    subtitleNonEmptyCueCount++;
                    subtitleBitmapCueCount += bitmapCues;
                    if (text.length() > 0)
                        lastSubtitleCueText = text.toString();
                }
                MiniClientConnection subtitleConnection = MiniclientApplication.get()
                        .getClient().getCurrentConnection();
                boolean serverRendersCaptions = subtitleConnection != null
                        && subtitleConnection.isSubtitleCallbackEnabled()
                        && legacyCaptionBridge.isForwardingCurrentStream();
                if (showCaptions && !serverRendersCaptions && subView != null)
                    subView.setCues(cues);
                else if (serverRendersCaptions && subView != null)
                {
                    // The legacy extender callback path sends the decoded caption bytes
                    // back to SageTV, where the STV owns CC1/CC2/Off presentation.  Keep
                    // Exo's text renderer enabled so the extractor tap continues to
                    // receive CEA samples, but detach the Android SubtitleView itself.
                    // Merely clearing its current cues leaves an attached overlay and can
                    // race with the next Exo cue callback, producing duplicate captions.
                    subView.setCues(java.util.Collections.<Cue>emptyList());
                    RemoveSubTitleView();
                }
            }
        });


        final String sageTVurlFinal = sageTVurl;
        if (!httpls)
        {

            com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = new DataSource.Factory()
            {
                @Override
                public DataSource createDataSource()
                {
                    return dataSource;
                }
            };

            // Pull-mode SageTV recordings are commonly MPEG-TS. Exo's default TS timestamp
            // search window can be too small to build a usable seek map on broadcast/PVR files,
            // which makes seekTo() resolve back to the start of the item. Keep PUSH behavior
            // unchanged, but give Pull a wider PCR search window and sync-point seeking.
            if (!pushMode)
            {
                ExtractorsFactory extractorsFactory = withPassthroughOffset(
                        createCaptionAwareExtractorsFactory(true));
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                log.logDebug("Pull extractor seek tuning enabled. TS timestamp search bytes: "
                        + (TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * runtimeConfig.getTsSearchMultiplier()));
            }
            else
            {
                ExtractorsFactory pushExtractors = dvdPushMode
                        ? createDvdExtractorsFactory()
                        : createCaptionAwareExtractorsFactory(false);
                pushExtractors = withPassthroughOffset(pushExtractors);
                mediaSource = new ProgressiveMediaSource.Factory(
                        dataSourceFactory, pushExtractors)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
            }


            boolean haveStartPosition = (playbackStartPosition >= 0);
            long requestedStartPosition = playbackStartPosition;
            log.logDebug("ExoLogging - Preparing playback");
            if (haveStartPosition)
            {
                // Bind the queued resume seek to the new MediaSource timeline. A
                // standalone seekTo() before setMediaSource() is reset when the new
                // source is attached and caused resumed recordings to begin at zero.
                player.setMediaSource(mediaSource, requestedStartPosition);
                playbackStartPosition = -1;
                PlaybackDebugTrap.recordDetailed("initial_seek_attached_to_source", this,
                        "appliedMs=" + requestedStartPosition);
                log.logDebug("ExoLogging - Start Position: " + requestedStartPosition);
            }
            else
            {
                player.setMediaSource(mediaSource, true);
            }
            player.prepare();

        }
        else
        {
            ExtractorsFactory httpExtractors = withPassthroughOffset(
                    new DefaultExtractorsFactory());
            mediaSource = new DefaultMediaSourceFactory(
                    context.getContext(), httpExtractors)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
            player.setMediaSource(mediaSource, true);
            player.prepare();
        }

        //Set seek preferences
        //player.setSeekParameters(SeekParameters.CLOSEST_SYNC);

        // start playing
        // Bind the SurfaceView, not its current one-shot Surface. ExoPlayer then
        // owns the SurfaceHolder callbacks and detaches/rebinds across HOME,
        // visibility, and activity Surface recreation. Holding the raw Surface
        // made Fire OS retry OMX.MTK.VIDEO.DECODER.MPEG2 against an object that
        // had already been released.
        player.setVideoSurfaceView((SurfaceView) context.getVideoView());
        player.setPlayWhenReady(playRequested);

        //Create Media Session
        mediaSession = new MediaSessionCompat(this.context.getContext(), "SageTV Android TV Client");
        mediaSession.setCallback(new MediaSessionCallbackHandler(this, context.getClient(), context.getContext()));

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.logDebug("Video Player is online");
        }

        this.playerReady = true;
        if (playRequested)
            super.play();
        else
            super.pause();

        cancelProgressUpdates();


        final Runnable[] sessionProgress = new Runnable[1];
        sessionProgress[0] = new Runnable()
        {
            @Override
            public void run()
            {
                if (isCurrentPlaybackSession(listenerSession) && player == listenerPlayer)
                {
                    long currentPositionMs = listenerPlayer.getCurrentPosition();
                    Exo2MediaPlayerImpl.this.setPlaybackPosition(currentPositionMs);
                    scheduleLegacyCaptionDrain(currentPositionMs * 1000L);
                    progressHandler.postDelayed(sessionProgress[0], 500);
                }
                else if (isCurrentPlaybackSession(listenerSession))
                {
                    Exo2MediaPlayerImpl.this.setPlaybackPosition(0);
                }
            }
        };
        progressRunnable = sessionProgress[0];

        progressHandler.postDelayed(progressRunnable, 0);
    }

    private void cancelProgressUpdates()
    {
        if (progressRunnable != null)
        {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    public long getSubtitleCueUpdateCountForDebug()
    {
        return subtitleCueUpdateCount;
    }

    public int getLegacyCaptionCallbackCountForDebug()
    {
        return legacyCaptionBridge.getForwardedSamples();
    }

    @Override
    public boolean hasObservedCeaCaptionData()
    {
        return legacyCaptionBridge.isForwardingCurrentStream();
    }

    public long getLegacyCaptionCallbackBytesForDebug()
    {
        return legacyCaptionBridge.getForwardedBytes();
    }

    public boolean isLegacyCaptionCallbackActiveForDebug()
    {
        return legacyCaptionBridge.isForwardingCurrentStream();
    }

    public long getSubtitleNonEmptyCueCountForDebug()
    {
        return subtitleNonEmptyCueCount;
    }

    public long getSubtitleBitmapCueCountForDebug()
    {
        return subtitleBitmapCueCount;
    }

    public int getCurrentSubtitleCueCountForDebug()
    {
        return currentSubtitleCueCount;
    }

    public String getLastSubtitleCueTextForDebug()
    {
        return lastSubtitleCueText;
    }

    public String getCurrentSubtitleCueTextForDebug()
    {
        return currentSubtitleCueText;
    }

    public boolean isSubtitleOverlayAttachedForDebug()
    {
        return subtitleOverlayAttached;
    }

    public void changeTrack(int trackType, int groupIndex, int trackIndex)
    {
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                TrackSelectionOverride override;
                DefaultTrackSelector.Parameters.Builder parametersBuilder;

                if (trackSelector == null)
                {
                    log.logTrace("Track Selector Null");
                    return;
                }

                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();

                if (trackInfo == null)
                {
                    log.logTrace("Track info null");
                    return;
                }

                try
                {
                    int rendererIndex = findRendererIndex(trackInfo, trackType);
                    if (rendererIndex == C.INDEX_UNSET)
                    {
                        log.logWarning("No renderer found for track type " + trackType);
                        return;
                    }
                    TrackGroupArray trackGroup = trackInfo.getTrackGroups(rendererIndex);


                    if (groupIndex == Exo2MediaPlayerImpl.DISABLE_TRACK) //Disable trackType from rendering
                    {
                        parametersBuilder = trackSelector.buildUponParameters();
                        parametersBuilder.setRendererDisabled(rendererIndex, true);
                        parametersBuilder.setTrackTypeDisabled(trackType, true);

                        trackSelector.setParameters(parametersBuilder.build());

                        log.logDebug("JVL - Track change executed for disable: TrackType=" + trackType + " TrackGroup=" + trackGroup + " TrackIndex=" + trackIndex);
                        selectedSubtitleTrack = DISABLE_TRACK;
                    }
                    else
                    {
                        if (trackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex) == RendererCapabilities.FORMAT_HANDLED)
                        {
                            //Clear set new track selection
                            parametersBuilder = trackSelector.buildUponParameters();

                            //override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
                            override = new TrackSelectionOverride(trackGroup.get(groupIndex), trackIndex);

                            // setSubtitleTrack(DISABLE_TRACK) disables both this renderer
                            // and the text track type. Clear both switches when captions
                            // are selected again so Off -> CC1/CC2 works without restarting
                            // the media session.
                            parametersBuilder.setRendererDisabled(rendererIndex, false);
                            parametersBuilder.setTrackTypeDisabled(trackType, false);

                            //parametersBuilder.setSelectionOverride(trackType, trackGroup, override);
                            parametersBuilder.addOverride(override);


                            trackSelector.setParameters(parametersBuilder.build());
                            log.logDebug("JVL - Track change executed: TrackType=" + trackType + " TrackGroup=" + trackGroup + " TrackIndex=" + trackIndex);
                            selectedSubtitleTrack = groupIndex;
                        }
                        else
                        {
                            log.logDebug("Unable to render the track, TrackType= " + trackType + ", GroupIndex= " + groupIndex + ", TrackIndex= " + trackIndex);
                        }
                    }
                }
                catch (Exception ex)
                {
                    log.logError("Error render the track, TrackType= " + trackType + ", GroupIndex= " + groupIndex + ", TrackIndex= " + trackIndex, ex);
                    ex.printStackTrace();
                }
            }
        });
    }

    /**
     * Counts support tracks of the given track type
     *
     * @param RenderType The type of track (VIDEO, AUDIO, TEXT, ect...)
     */
    public int getTrackCount(int RenderType)
    {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        int count = 0;

        if (mappedTrackInfo == null)
        {
            log.logWarning("No Mapped Track Info found");
            return count;
        }

        int rendererIndex = findRendererIndex(mappedTrackInfo, RenderType);
        if (rendererIndex == C.INDEX_UNSET)
            return count;
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

        if (trackGroups.length != 0)
        {
            //This code is assuming one track to a group.  It will increment the count by one
            //if there is a supported track in the groun
            for (int j = 0; j < trackGroups.length; j++)
            {
                boolean supported = false;

                for (int k = 0; k < trackGroups.get(j).length; k++)
                {
                    if (mappedTrackInfo.getTrackSupport(rendererIndex, j, k) == C.FORMAT_HANDLED)
                    {
                        log.logDebug("Format is handled");
                        supported = true;
                    }
                    else
                    {
                        log.logDebug("Format IS NOT HANDLED");
                    }
                }

                if (supported)
                {
                    count++;
                }
            }
        }

        return count;
    }

    @Override
    public SubtitleTrack[] getSubtitleTracks()
    {
        if (trackSelector == null)
            return appendTeletextSubtitleTracks(new SubtitleTrack[0]);
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null)
            return appendTeletextSubtitleTracks(new SubtitleTrack[0]);
        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_TEXT);
        if (rendererIndex == C.INDEX_UNSET)
            return appendTeletextSubtitleTracks(new SubtitleTrack[0]);
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
        int trackCount = trackGroups.length;
        SubtitleTrack[] tracks = new SubtitleTrack[0];

        if (trackCount > 0)
        {
            tracks = new SubtitleTrack[trackCount];

            for (int i = 0; i < trackCount; i++)
            {
                TrackGroup trackGroup = trackGroups.get(i);

                SubtitleCodec codec = SubtitleCodec.parse(trackGroup.getFormat(0).sampleMimeType);
                String langugae = trackGroup.getFormat(0).language;
                String label = trackGroup.getFormat(0).label;
                int accessibilityChannel = trackGroup.getFormat(0).accessibilityChannel;
                boolean supported = (mappedTrackInfo.getTrackSupport(rendererIndex, i, 0) == C.FORMAT_HANDLED);

                int sourceStreamId = SubtitleTrack.parseSourceStreamId(
                        trackGroup.getFormat(0).id);
                SubtitleTrack track = new SubtitleTrack(i, codec, langugae, label, supported,
                        accessibilityChannel, sourceStreamId);
                tracks[i] = track;
            }
        }

        return appendTeletextSubtitleTracks(tracks);
    }

    private int findRendererIndex(MappingTrackSelector.MappedTrackInfo trackInfo, int trackType)
    {
        if (trackInfo == null)
            return C.INDEX_UNSET;
        for (int rendererIndex = 0; rendererIndex < trackInfo.getRendererCount(); rendererIndex++)
        {
            if (trackInfo.getRendererType(rendererIndex) == trackType)
                return rendererIndex;
        }
        return C.INDEX_UNSET;
    }

    public void debugAvailableTracks()
    {
        debugAvailableTracks(player);
    }

    private void debugAvailableTracks(ExoPlayer expectedPlayer)
    {
        if (expectedPlayer == null)
        {
            log.logDebug("Skipping track diagnostics because the player was released");
            return;
        }
        DefaultTrackSelector expectedTrackSelector = trackSelector;
        if (expectedTrackSelector == null)
        {
            log.logDebug("Skipping track diagnostics because the track selector was released");
            return;
        }
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
                expectedTrackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo == null)
        {
            log.logWarning("No Mapped Track Info");
            return;
        }

        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++)
        {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);

            log.logDebug("Track Render Group " + i);

            if (trackGroups.length != 0)
            {
                int rendererType = expectedPlayer.getRendererType(i);

                switch (rendererType)
                {
                    case C.TRACK_TYPE_AUDIO:

                        log.logDebug("TRACK_TYPE_AUDIO");
                        break;

                    case C.TRACK_TYPE_VIDEO:

                        log.logDebug("TRACK_TYPE_VIDEO");
                        break;

                    case C.TRACK_TYPE_TEXT:

                        log.logDebug("TRACK_TYPE_TEXT");
                        break;

                    default:
                        continue;
                }

                for (int j = 0; j < trackGroups.length; j++)
                {
                    log.logDebug("\t Track Group " + j);

                    for (int k = 0; k < trackGroups.get(j).length; k++)
                    {
                        Format format = trackGroups.get(j).getFormat(k);

                        log.logDebug("\t\tTrack : " + k);
                        log.logDebug("\t\tContainer MimeType: " + format.containerMimeType);
                        log.logDebug("\t\tSample MimeType: " + format.sampleMimeType);
                        log.logDebug("\t\tCodecs: " + format.codecs);
                        log.logDebug("\t\tLanguage: " + format.language);


                        if (rendererType == C.TRACK_TYPE_TEXT)
                        {

                            /*if((MimeTypes.APPLICATION_CEA708.equalsIgnoreCase(format.sampleMimeType) || MimeTypes.APPLICATION_CEA608.equalsIgnoreCase(format.sampleMimeType))
                                    && format.language.equalsIgnoreCase("en"))
                            {
                                log.debug("-----Setting Subtitle track to active");
                                //Enable this track.  This is just debugging
                                this.setSubtitleTrack(j);
                            }
                            else
                            {
                                log.debug("-----NOT Setting Subtitle track to active");
                            }*/
                        }


                        if (rendererType == C.TRACK_TYPE_AUDIO)
                        {
                            log.logDebug("\t\tChannel: " + format.channelCount);
                            log.logDebug("\t\tBitrate: " + format.bitrate);
                            log.logDebug("\t\tAverageBitrate: " + format.averageBitrate);
                            log.logDebug("\t\tPeakBitrate: " + format.peakBitrate);
                            log.logDebug("\t\tPCM Encoding: " + format.pcmEncoding);

                        }

                        if (rendererType == C.TRACK_TYPE_VIDEO)
                        {
                            if (format.colorInfo != null)
                            {
                                log.logDebug("\t\tColor: " + format.colorInfo.toString());
                            }
                            log.logDebug("\t\tBitrate: " + format.bitrate);
                            log.logDebug("\t\tAverageBitrate: " + format.averageBitrate);
                            log.logDebug("\t\tPeakBitrate: " + format.peakBitrate);
                            log.logDebug("\t\tFramerate: " + format.frameRate);
                            log.logDebug("\t\tHeight: " + format.height);
                            log.logDebug("\t\tWidth: " + format.width);

                        }

                        log.logDebug("\t\tID: " + format.id);
                        log.logDebug("\t\tLabel: " + format.label);
                        if (format.metadata != null)
                        {
                            log.logDebug("\t\t\tMetadata length: " + format.metadata.length());
                        }


                        if (mappedTrackInfo.getTrackSupport(i, j, k) == RendererCapabilities.FORMAT_HANDLED)
                        {
                            //Add debug info
                            log.logDebug("\t\t Format is handled");
                        }
                        else
                        {
                            //Add debug info
                            log.logDebug("\t\t Format IS NOT HANDLED");
                        }
                    }

                }

            }
            else
            {
                log.logDebug("Track Group Empty");
            }
        }
    }

    // cncb - Add and remove ExoPlayer2 SubTitleView for embedded PGS subtitles
    private void AddSubTitleView()
    {
        if (subView == null)
        {
            subView = new SubtitleView(context.getContext());
            // Keep TV captions above SageTV's bottom playback/timeline overlay.
            applyTextSubtitlePresentation(subView);
            subView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            subView.setVisibility(View.VISIBLE);
            subView.setElevation(100.0f);
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        ViewGroup layout = (ViewGroup) ((Activity) context.getContext())
                                .findViewById(android.R.id.content);
                        layout.addView(subView);
                        subView.bringToFront();
                        layout.invalidate();
                        subtitleOverlayAttached = true;
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error adding SubTitleView: ", ex);
                    }
                }
            });
        }
    }

    @Override
    public boolean supportsTextSubtitlePresentation()
    {
        return true;
    }

    @Override
    public boolean setTextSubtitlePresentation(int safeAreaPercent,
            int textScalePercent, String style)
    {
        subtitleSafeAreaPercent = TextSubtitlePresentation.clampSafeArea(safeAreaPercent);
        subtitleTextScalePercent = TextSubtitlePresentation.clampTextScale(textScalePercent);
        subtitleTextStyle = TextSubtitlePresentation.normalizeStyle(style);
        final SubtitleView current = subView;
        if (current != null)
            context.runOnUiThread(new Runnable()
            {
                @Override public void run() { applyTextSubtitlePresentation(current); }
            });
        return true;
    }

    @Override public int getTextSubtitleSafeAreaPercent() { return subtitleSafeAreaPercent; }
    @Override public int getTextSubtitleScalePercent() { return subtitleTextScalePercent; }
    @Override public String getTextSubtitleStyle() { return subtitleTextStyle; }

    private void applyTextSubtitlePresentation(SubtitleView view)
    {
        view.setBottomPaddingFraction(subtitleSafeAreaPercent / 100.0f);
        view.setApplyEmbeddedStyles(false);
        view.setApplyEmbeddedFontSizes(false);
        if (TextSubtitlePresentation.STYLE_OUTLINE.equals(subtitleTextStyle))
        {
            view.setStyle(new com.google.android.exoplayer2.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK, android.graphics.Typeface.DEFAULT));
        }
        else if (TextSubtitlePresentation.STYLE_BLACK_BOX.equals(subtitleTextStyle))
        {
            view.setStyle(new com.google.android.exoplayer2.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, android.graphics.Color.BLACK,
                    android.graphics.Color.TRANSPARENT,
                    com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                    android.graphics.Color.BLACK, android.graphics.Typeface.DEFAULT));
        }
        else
        {
            view.setUserDefaultStyle();
        }
        view.setFractionalTextSize(0.0533f * subtitleTextScalePercent / 100.0f);
    }

    private void RemoveSubTitleView()
    {
        if (subView != null)
        {
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        ViewParent parent = subView.getParent();
                        if (parent instanceof ViewGroup)
                            ((ViewGroup) parent).removeView(subView);
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error removing SubTitleView ",  ex);
                    }
                    finally
                    {
                        subtitleOverlayAttached = false;
                        subView = null;
                    }
                }
            });
        }
    }

    private void updateMediaSessionPlaybackState(long playbackPostion)
    {
        if(mediaSession != null && mediaSession.isActive()) {
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
            stateBuilder.setActions(this.getMediaSessionActions());

            if (player != null && getState() == PLAY_STATE) {
                stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, playbackPostion, 1.0f);
            } else {
                stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            }

            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private long getMediaSessionActions()
    {
        long actions = 0;

        if(player != null)
        {
            if (getState() == PLAY_STATE)
            {
                actions = PlaybackState.ACTION_STOP;
                actions |= PlaybackState.ACTION_PAUSE;
                actions |= PlaybackState.ACTION_FAST_FORWARD;
                actions |= PlaybackState.ACTION_REWIND;
                actions |= PlaybackState.ACTION_SEEK_TO;
                actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
                actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            }
            else
            {
                actions = PlaybackState.ACTION_PLAY;
            }

        }

        return actions;
    }

    public String getDecoderCandidatesForDebug() { return decoderAttemptTelemetry.candidates(); }
    public String getDecoderEventsForDebug() { return decoderAttemptTelemetry.events(); }
    public String getSelectedVideoDecoderForDebug() { return decoderAttemptTelemetry.selectedVideo(); }
    public String getSelectedAudioDecoderForDebug() { return decoderAttemptTelemetry.selectedAudio(); }
    public String getSessionDecoderExclusionsForDebug() { return decoderAttemptTelemetry.exclusions(); }
    public String getDecoderFallbackReasonForDebug() { return decoderAttemptTelemetry.fallbackReason(); }
    public String getDecoderRecoveryResultForDebug() { return decoderAttemptTelemetry.recoveryResult(); }
    public int getDecoderCodecErrorCountForDebug() { return decoderAttemptTelemetry.codecErrorCount(); }
    public int getAudioUnderrunCountForDebug() { return decoderAttemptTelemetry.audioUnderrunCount(); }
    public int getAudioOutputErrorCountForDebug() { return decoderAttemptTelemetry.audioOutputErrorCount(); }
    public int getDecoderFormatChangeCountForDebug() { return decoderAttemptTelemetry.formatChangeCount(); }
    public int getDecoderTrackChangeCountForDebug() { return decoderAttemptTelemetry.trackChangeCount(); }

    private void setMediaSessionMetadata(String displayTitle, long duration)
    {
        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder();

        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle);
        metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        mediaSession.setMetadata(metaDataBuilder.build());
    }
}
