package opensagetv.vibe.miniclient.android.video.media3;

import android.app.Activity;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.datasource.DataSource;
import android.media.session.PlaybackState;
import android.media.MediaFormat;
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
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.text.Cue;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.ui.SubtitleView;
import androidx.media3.common.VideoSize;
import androidx.media3.common.MimeTypes;


import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.TransientPushSegmentPlayer;
import opensagetv.vibe.miniclient.dvd.DvdAudioStreamCode;
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
import opensagetv.vibe.miniclient.android.video.DeviceCodecCapabilityProfile;
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
import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;
import opensagetv.vibe.miniclient.video.MediaReplacementPolicy;
import opensagetv.vibe.miniclient.video.Mpeg2PictureTimestampCompleter;
import opensagetv.vibe.miniclient.video.PlaybackFrameStepPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSeekPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSessionController;
import opensagetv.vibe.miniclient.video.PlaybackSyncPointPolicy;
import opensagetv.vibe.miniclient.net.SessionOwnedDataSource;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.RejectedExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * Created by seans on 24/09/16.
 */

public class Media3MediaPlayerImpl extends BaseMediaPlayerImpl<ExoPlayer, DataSource>
        implements PlaybackHealthSource, TransientPushSegmentPlayer
{
    static final Logger log = Logger.getLogger(Media3MediaPlayerImpl.class);
    static final int MAX_PLAYBACK_RETRY_COUNT = 12;
    static final long FAST_SWITCH_FIRST_FRAME_TIMEOUT_MS = 8_000L;

    @Override
    protected void releaseDataSource()
    {
        if (dataSource instanceof SessionOwnedDataSource)
            ((SessionOwnedDataSource) dataSource).releaseSession();
        super.releaseDataSource();
    }

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
            // Caption reset is optional session cleanup. It must never reject
            // the SageTV OPENURL that is starting the next video.
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

    private ExtractorsFactory createCaptionAwareExtractorsFactory(boolean pullMode)
    {
        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        String standard = prefs.getString(PrefStore.Keys.preferred_caption_standard,
                TrackPreferencePolicy.CAPTION_STANDARD_AUTO);
        int service = TrackPreferencePolicy.parseCaptionService(standard,
                prefs.getString(PrefStore.Keys.preferred_caption_service, "1"));
        List<Format> captionFormats = new ArrayList<Format>();
        SubtitleCodec preferredCodec = TrackPreferencePolicy.preferredCaptionCodec(standard);
        if (preferredCodec == null || preferredCodec == SubtitleCodec.CEA608)
        {
            captionFormats.add(new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                    .setLanguage("eng").setAccessibilityChannel(
                            preferredCodec == SubtitleCodec.CEA608 ? service : 1).build());
        }
        if (preferredCodec == null || preferredCodec == SubtitleCodec.CEA708)
        {
            captionFormats.add(new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA708)
                    .setLanguage("eng").setAccessibilityChannel(
                            preferredCodec == SubtitleCodec.CEA708 ? service : 1).build());
        }
        DefaultExtractorsFactory factory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS)
                .setTsSubtitleFormats(captionFormats);
        if (pullMode)
        {
            factory.setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
                            * runtimeConfig.getTsSearchMultiplier())
                    .setConstantBitrateSeekingEnabled(true);
        }
        return new LegacyCaptionExtractorsFactory(factory, legacyCaptionBridge,
                mpeg2InterlaceObserver);
    }

    private boolean shouldRepairNativeDvdMpeg2Timestamps(PrefStore prefs)
    {
        String mode = ActivePlayerSessionOverrides.resolveDvdTimestampRepair(
                prefs.getString(PrefStore.Keys.disc_mpeg2_timestamp_repair, "auto"));
        if ("on".equalsIgnoreCase(mode))
            return true;
        if ("off".equalsIgnoreCase(mode))
            return false;
        return DeviceCodecCapabilityProfile.current()
                .hasMpeg2HardwareDecoderNeedingMissingPtsRepair();
    }
    private MediaSource mediaSource;
    private long playbackStartPosition = -1;
    private int initialAudioTrackIndex = -1;
    /** SageTV's packed DVD private-stream request, retained across cell replacements. */
    private volatile int requestedDvdAudioStream = -1;
    /** The request applied to the current Media3 track-group generation. */
    private volatile int appliedDvdAudioStream = -1;
    private long currentPlaybackPosition = 0;
    private volatile long currentBufferedPosition = 0;
    private ReentrantLock playbackPositionLock;
    private DefaultTrackSelector trackSelector;
    private int selectedSubtitleTrack = DISABLE_TRACK;
    private volatile int requestedSubtitleTrack = DISABLE_TRACK;
    private volatile boolean playRequested = true;
    private volatile boolean serverMuted;

    private boolean errorState = false;
    private int retryCount = 0;
    private volatile boolean firstVideoFrameRendered;

    private boolean showCaptions = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
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
                    Media3MediaPlayerImpl.this.seek(positionMs);
                }
            };
    private volatile boolean dvdPushMode;
    private volatile boolean dvdRepreparePending;
    private volatile boolean dvdReprepareScheduled;
    private volatile boolean dvdSegmentReaderEnded;
    private volatile boolean dvdRenderedFirstFrameInEpoch;
    private volatile boolean dvdPauseAfterFirstFrame;
    // Original SageTV extenders apply MEDIACMD_DVD_STC to their hardware
    // decoder clock and report that absolute clock to MiniDVDPlayer. Media3
    // periods are deliberately rebased to zero after a DVD FLUSH, so retain
    // the server-provided 45 kHz STC as the logical clock base separately.
    private volatile long dvdLogicalClockBaseMs;
    private volatile ResilientDvdPsExtractorsFactory dvdExtractorsFactory;
    // Every DVD byte epoch needs a fresh datasource lease and MediaSource.
    // Native playback uses the resilient MPEG-PS factory above, while MIM
    // uses Media3's ordinary MPEG-TS extractors. Keep the common factory
    // separately so MIM flush/seek does not accidentally reuse an ended lease.
    private volatile ExtractorsFactory dvdEpochExtractorsFactory;
    private long dvdReaderGeneration;
    private String dvdMediaUri;
    private volatile long dvdFrameMetadataCount;
    private volatile long dvdLastFramePresentationUs = C.TIME_UNSET;
    private volatile long dvdLastFrameReleaseNs = C.TIME_UNSET;
    private volatile long dvdLastFramePresentationDeltaUs = C.TIME_UNSET;
    private volatile long dvdLastFrameReleaseDeltaUs = C.TIME_UNSET;
    private volatile long dvdMaxFrameReleaseDeltaUs;
    private volatile long dvdFrameReleaseGapCount;
    private volatile long dvdFrameReleaseNonPositiveCount;
    private volatile long dvdFrameReleaseUnder10MsCount;
    private volatile long dvdFrameRelease10To25MsCount;
    private volatile long dvdFrameRelease25To45MsCount;
    private volatile long dvdFrameRelease45To75MsCount;
    private volatile long dvdFrameRelease75To100MsCount;
    private static final int DVD_FRAME_CADENCE_TRACE_SIZE = 64;
    private final long[] dvdFramePresentationDeltaTraceUs =
            new long[DVD_FRAME_CADENCE_TRACE_SIZE];
    private final long[] dvdFrameReleaseDeltaTraceUs =
            new long[DVD_FRAME_CADENCE_TRACE_SIZE];
    private int dvdFrameCadenceTraceIndex;
    private int dvdFrameCadenceTraceCount;
    private final CRC32 dvdEpochCrc32 = new CRC32();
    private volatile long dvdEpochFingerprintBytes;
    private volatile long dvdLastEpochCrc32;
    private volatile long dvdLastEpochFingerprintBytes;
    private Runnable progressRunnable;
    private final PullSeekRecoveryMonitor pullSeekRecoveryMonitor = new PullSeekRecoveryMonitor();
    private final PlaybackMediaContext mediaContext = new PlaybackMediaContext();
    private PlayerRuntimeConfig runtimeConfig = PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.MEDIA3);
    private boolean mediaContextInitialized;
    private volatile boolean fastSwitchRequested;
    private volatile boolean fastSwitchAwaitingFirstFrame;
    private volatile boolean fastSwitchFallbackPending;
    private volatile long fastSwitchAttemptCount;
    private volatile long fastSwitchSuccessCount;
    private volatile long fastSwitchFallbackCount;
    private volatile String fastSwitchLastReason = "not_attempted";
    private volatile String fastSwitchTargetUrl = "";
    private volatile long fastSwitchWatchdogGeneration;
    private Runnable fastSwitchWatchdogRunnable;

    MediaSessionCompat mediaSession;

    private volatile SubtitleView subView;
    private volatile long subtitleCueUpdateCount;
    private volatile long subtitleNonEmptyCueCount;
    private volatile String lastSubtitleCueText = "";
    private volatile String currentSubtitleCueText = "";
    private volatile boolean subtitleOverlayAttached;
    private volatile int subtitleSafeAreaPercent;
    private volatile int subtitleTextScalePercent;
    private volatile String subtitleTextStyle;

    public Media3MediaPlayerImpl(AndroidUIController activity)
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
        // A newer OPENURL supersedes any retained-source attempt still waiting
        // for its first frame. Never let that older watchdog rebuild the player
        // underneath the newer SageTV playback session.
        cancelFastSwitchWatchdog();
        fastSwitchFallbackPending = false;
        fastSwitchAwaitingFirstFrame = false;
        playbackRateController.reset();
        playRequested = true;
        subtitleCueUpdateCount = 0;
        subtitleNonEmptyCueCount = 0;
        lastSubtitleCueText = "";
        currentSubtitleCueText = "";
        subtitleOverlayAttached = false;
        MediaReplacementPolicy.Decision replacementDecision =
                MediaReplacementPolicy.evaluate(mediaContextInitialized,
                        player != null, playerReady, pushMode, dvdPushMode, httpls,
                        mediaContext, urlString, timeshifted, bufferSize);
        fastSwitchRequested = replacementDecision.isEligible();
        fastSwitchLastReason = replacementDecision.getReason();
        mediaContext.update(majorHint, minorHint, encodingHint, timeshifted, bufferSize);
        mediaContextInitialized = true;
        if (fastSwitchRequested)
        {
            final PlaybackSessionController.Token switchOperation = beginPlaybackOperation(
                    PlaybackSessionController.Operation.LOAD);
            lastUri = urlString;
            dvdMimTransport = false;
            lastMediaTime = -1;
            eos = false;
            seekPending = false;
            flushed = false;
            httpls = false;
            final String switchUrl = urlString.startsWith("stv://") ? urlString
                    : "stv://" + context.getClient().getConnectedServerInfo().address
                    + "/" + urlString;
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if (!isCurrentPlaybackSession(switchOperation))
                        return;
                    state = LOADED_STATE;
                    if (performFastLoad(switchUrl))
                    {
                        eos = false;
                        return;
                    }
                    fastSwitchRequested = false;
                    Media3MediaPlayerImpl.super.load(majorHint, minorHint, encodingHint,
                            urlString, hostname, timeshifted, bufferSize);
                }
            });
            return;
        }
        super.load(majorHint, minorHint, encodingHint, urlString, hostname, timeshifted, bufferSize);
    }

    @Override
    public void setServerMediaMetadataExplicit(boolean explicit)
    {
        mediaContext.setMetadataExplicit(explicit);
    }

    /**
     * The renderer calls this before replacing its backend instance. Media3 is
     * retained only for ordinary completed-file Pull playback. Media3 itself
     * decides whether the decoder can be reused after it sees the next Format;
     * an incompatible stream is allowed to reinitialize its decoder, and an
     * asynchronous load error receives one normal full-player fallback.
     */
    public boolean canRetainForNextUrl(String urlString)
    {
        if (player == null || !playerReady || pushMode || dvdPushMode || httpls)
            return false;
        // Live/growing recordings require a clean player and datasource
        // boundary when SageTV advances to the next program file. Retaining
        // this instance can allow an old finite timeline or late END callback
        // to leak into the replacement session.
        if (mediaContext.isTimeshifted() || mediaContext.getBufferSize() > 0)
            return false;
        if (urlString == null || urlString.length() == 0)
            return false;
        String lower = urlString.toLowerCase();
        return !lower.startsWith("push:") && !lower.startsWith("dvd:")
                && !lower.startsWith("http://") && !lower.startsWith("https://")
                && !lower.endsWith(".exlink");
    }

    private boolean performFastLoad(String sageTVurl)
    {
        if (!fastSwitchRequested || player == null || pushMode || dvdPushMode || httpls)
            return false;

        fastSwitchAttemptCount++;
        final DataSource previousDataSource = dataSource;
        try
        {
            cancelPullSeekRecovery();
            PrefStore prefs = MiniclientApplication.get().getClient().properties();
            SmbDirectConfig smbConfig = AndroidPrefStore.isSmbStreamingMode(
                    prefs.getStreamingMode()) ? SmbDirectConfig.from(prefs) : null;
            final DataSource replacementDataSource = new Media3PullDataSource(
                    context.getClient().getConnectedServerInfo().address,
                    false, smbConfig, runtimeConfig.getPullReadBytes());
            DataSource.Factory replacementFactory = new DataSource.Factory()
            {
                @Override
                public DataSource createDataSource()
                {
                    return replacementDataSource;
                }
            };
            MediaSource replacementSource = new ProgressiveMediaSource.Factory(
                    replacementFactory, createCaptionAwareExtractorsFactory(true))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));

            dataSource = replacementDataSource;
            mediaSource = replacementSource;
            currentPlaybackPosition = 0;
            currentBufferedPosition = 0;
            lastMediaTime = -1;
            seekPending = false;
            flushed = false;
            eos = false;
            errorState = false;
            retryCount = 0;
            fastSwitchTargetUrl = sageTVurl;
            fastSwitchFallbackPending = true;
            fastSwitchAwaitingFirstFrame = true;
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            player.setMediaSource(replacementSource, true);
            player.prepare();
            player.setPlayWhenReady(playRequested);
            armFastSwitchWatchdog();
            PlaybackDebugTrap.record("fast_switch_media_source_set", this);

            if (previousDataSource instanceof SessionOwnedDataSource)
                ((SessionOwnedDataSource) previousDataSource).releaseSession();
            fastSwitchLastReason = "media_source_replaced";
            return true;
        }
        catch (Throwable failure)
        {
            cancelFastSwitchWatchdog();
            fastSwitchFallbackCount++;
            fastSwitchFallbackPending = false;
            fastSwitchAwaitingFirstFrame = false;
            fastSwitchLastReason = "synchronous_" + failure.getClass().getSimpleName();
            log.logError("Fast media switch failed before prepare; using full load", failure);
            if (dataSource != previousDataSource)
                releaseDataSource();
            dataSource = previousDataSource;
            return false;
        }
    }

    private void armFastSwitchWatchdog()
    {
        cancelFastSwitchWatchdog();
        final long generation = ++fastSwitchWatchdogGeneration;
        fastSwitchWatchdogRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if (generation != fastSwitchWatchdogGeneration
                        || !fastSwitchFallbackPending
                        || !fastSwitchAwaitingFirstFrame)
                    return;
                fastSwitchWatchdogRunnable = null;
                fallbackFromFastSwitch("first_frame_timeout");
            }
        };
        progressHandler.postDelayed(fastSwitchWatchdogRunnable,
                FAST_SWITCH_FIRST_FRAME_TIMEOUT_MS);
    }

    private void cancelFastSwitchWatchdog()
    {
        fastSwitchWatchdogGeneration++;
        if (fastSwitchWatchdogRunnable != null)
        {
            progressHandler.removeCallbacks(fastSwitchWatchdogRunnable);
            fastSwitchWatchdogRunnable = null;
        }
    }

    /** Rebuild at most once when retained replacement cannot produce video. */
    private void fallbackFromFastSwitch(String reason)
    {
        if (!fastSwitchFallbackPending)
            return;
        cancelFastSwitchWatchdog();
        fastSwitchFallbackPending = false;
        fastSwitchAwaitingFirstFrame = false;
        fastSwitchRequested = false;
        fastSwitchFallbackCount++;
        fastSwitchLastReason = reason;
        PlaybackDebugTrap.record("fast_switch_full_load_fallback", this);
        log.logWarning("Fast media switch did not produce a frame (" + reason
                + "); rebuilding the Media3 player");
        setupPlayer(fastSwitchTargetUrl);
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

    @Override
    protected long getDvdPresentationPositionUs()
    {
        return Math.max(0L, getPlaybackPosition()) * 1_000L;
    }

    @Override
    public long getBufferedPlaybackAheadMillis()
    {
        long bufferedEdgeMs = currentBufferedPosition;
        if (dvdPushMode)
        {
            // Media3's public buffered position can transiently collapse to
            // the current position while the extractor has already queued
            // timestamped samples. MiniDVDPlayer uses this value to decide
            // when a DVD cell is decoder-drained; reporting zero too early
            // makes the server FLUSH bytes that are still several seconds
            // ahead of the renderer and creates a permanent A/V clock gap.
            ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
            if (factory != null)
            {
                long latestVideoUs = factory.getLatestVideoSampleUs();
                long latestAudioUs = factory.getLatestAudioSampleUs();
                if (latestVideoUs != C.TIME_UNSET)
                    bufferedEdgeMs = Math.max(bufferedEdgeMs, latestVideoUs / 1_000L);
                if (latestAudioUs != C.TIME_UNSET)
                    bufferedEdgeMs = Math.max(bufferedEdgeMs, latestAudioUs / 1_000L);
            }
        }
        return Math.max(0L, bufferedEdgeMs - getPlaybackPosition());
    }

    @Override
    public void pushData(byte[] cmddata, int bufDataOffset, int buffSize) throws IOException
    {
        if (dvdPushMode && dvdSegmentReaderEnded && !dvdRepreparePending
                && buffSize > 0)
        {
            // EMPTY without a discontinuity is not followed by a server FLUSH.
            // Rebuild a fresh Media3 reader generation when replacement bytes
            // arrive, while preserving the just-drained logical DVD clock.
            dvdLogicalClockBaseMs += getPlaybackPosition();
            Media3PushDataSource dvdSource = dataSource instanceof Media3PushDataSource
                    ? (Media3PushDataSource) dataSource : null;
            if (dvdSource != null)
                dvdSource.suspendReads();
            dvdRepreparePending = true;
            dvdSegmentReaderEnded = false;
            PlaybackDebugTrap.record("dvd_segment_reprepare_pending", this);
        }
        if (dvdPushMode && dvdRepreparePending && buffSize > 0)
            schedulePendingDvdReprepare();
        if (dvdPushMode && buffSize > 0)
        {
            synchronized (dvdEpochCrc32)
            {
                dvdEpochCrc32.update(cmddata, bufDataOffset, buffSize);
                dvdEpochFingerprintBytes += buffSize;
            }
        }
        super.pushData(cmddata, bufDataOffset, buffSize);
    }

    @Override
    public void signalPushSegmentEnd()
    {
        if (dvdPushMode && dataSource instanceof Media3PushDataSource)
        {
            synchronized (dvdEpochCrc32)
            {
                dvdLastEpochCrc32 = dvdEpochCrc32.getValue();
                dvdLastEpochFingerprintBytes = dvdEpochFingerprintBytes;
                log.logWarning("DVD epoch fingerprint bytes="
                        + dvdLastEpochFingerprintBytes + " crc32="
                        + Long.toHexString(dvdLastEpochCrc32));
                dvdEpochCrc32.reset();
                dvdEpochFingerprintBytes = 0L;
            }
            ((Media3PushDataSource) dataSource).signalActiveReadGenerationEnd();
            dvdSegmentReaderEnded = true;
            PlaybackDebugTrap.record("dvd_segment_eos", this);
            return;
        }
        // Preserve ordinary PUSH EOS behavior if this capability is invoked
        // outside the DVD segmented stream.
        setServerEOS();
    }

    private synchronized void schedulePendingDvdReprepare()
    {
        if (!dvdRepreparePending || dvdReprepareScheduled)
            return;
        dvdReprepareScheduled = true;
        final Media3PushDataSource dvdSource = dataSource instanceof Media3PushDataSource
                ? (Media3PushDataSource) dataSource : null;
        final PlaybackSessionController.Token dvdSession = currentPlaybackSession();
        final ExoPlayer dvdPlayer = player;
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    if (!isCurrentPlaybackSession(dvdSession) || player != dvdPlayer
                            || dvdPlayer == null)
                        return;
                    ResilientDvdPsExtractorsFactory nativeFactory = dvdExtractorsFactory;
                    if (nativeFactory != null)
                        nativeFactory.beginRepreparedEpoch(getDvdPtsOffset90KhzForDebug());
                    ExtractorsFactory epochFactory = dvdEpochExtractorsFactory;
                    MediaSource replacementSource = mediaSource;
                    if (dvdSource != null && epochFactory != null && dvdMediaUri != null)
                    {
                        long generation = ++dvdReaderGeneration;
                        dvdSource.activateReaderGeneration(generation);
                        Uri generationUri = Uri.parse(dvdMediaUri).buildUpon()
                                .appendQueryParameter("dvdGeneration",
                                        Long.toString(generation))
                                .build();
                        replacementSource = new ProgressiveMediaSource.Factory(
                                dvdSource.readerFactory(generation), epochFactory)
                                .createMediaSource(MediaItem.fromUri(generationUri));
                        mediaSource = replacementSource;
                    }
                    PlaybackDebugTrap.record("dvd_flush_reprepare_before", Media3MediaPlayerImpl.this);
                    dvdRenderedFirstFrameInEpoch = false;
                    dvdPauseAfterFirstFrame = !playRequested;
                    // A DVD menu cell is often a single MPEG-2 I-picture with
                    // the same URI, format and zero-based timeline as the cell
                    // before it. Media3 may otherwise retain the previous
                    // decoder output until this tiny replacement period has
                    // already ended. Flush renderer/codec queues without
                    // releasing the player or Surface so the authored submenu
                    // still becomes the next visible frame.
                    // stop() releases the old decoder while retaining the
                    // SurfaceView binding. Do not detach/re-attach the same
                    // Surface around each one-picture menu cell: Fire OS can
                    // report a decoded buffer as ready without ever latching
                    // it to that rapidly rebound Surface, leaving the prior
                    // menu frame visible on every other transition.
                    clearDvdAudioSelectionOverride();
                    dvdPlayer.stop();
                    appliedDvdAudioStream = -1;
                    dvdPlayer.setMediaSource(replacementSource, 0L);
                    dvdPlayer.prepare();
                    // MiniDVDPlayer pauses authored still menus before the
                    // replacement cell is prepared. A paused MediaCodec holds
                    // the only MPEG-2 I-picture forever. Run just long enough
                    // to render the new epoch's first frame, then the listener
                    // below reapplies the server-requested pause.
                    dvdPlayer.setPlayWhenReady(playRequested || dvdPauseAfterFirstFrame);
                    PlaybackDebugTrap.record("dvd_flush_reprepare_after", Media3MediaPlayerImpl.this);
                }
                finally
                {
                    synchronized (Media3MediaPlayerImpl.this)
                    {
                        dvdRepreparePending = false;
                        dvdReprepareScheduled = false;
                    }
                    if (dvdSource != null)
                        dvdSource.resumeReads();
                }
            }
        });
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
            // SageTV can issue STOP -> SEEK -> PLAY while retaining the same
            // MiniPlayer instance (notably while recovering a stock-server
            // session). ExoPlayer.stop() leaves the item reusable but requires
            // prepare() before PLAY can render again. Restore the SurfaceView
            // removed by BaseMediaPlayerImpl.stop() and prepare at the seek
            // position already selected by SageTV.
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
        cancelFastSwitchWatchdog();
        fastSwitchFallbackPending = false;
        fastSwitchAwaitingFirstFrame = false;
        playbackRateController.reset();
        cancelProgressUpdates();
        cancelPullSeekRecovery();
        final ExoPlayer playerToRelease = player;
        final MediaSessionCompat sessionToRelease = mediaSession;
        // MediaSessionCompat is owned by Android's main thread. In particular,
        // do not release it synchronously from SageTV's media-command thread:
        // STOP may already have queued a session update on main, and the two
        // Binder paths can block one another long enough that the stock server
        // never receives its DEINIT reply or replacement media socket.
        Media3MediaPlayerImpl.super.releasePlayer();
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

        //log.debug("Media3Logging - getPlayerMediaTimeMillis Called lastServerTime=" + Utils.toHHMMSS(lastServerTime) + " position=" + Utils.toHHMMSS(position));

        if (lastServerTime < 0)
        {
            log.logDebug("getPlayerMediaTimeMillis(): Flush was called waiting for last serverTime to be > 0");
            return -1;
        }

        if (dvdPushMode)
            return dvdLogicalClockBaseMs + position;
        return lastServerTime + position;
    }

    @Override
    public void dvdSetStc(int stc)
    {
        super.dvdSetStc(stc);
        if (dvdPushMode)
        {
            // Wire value is an unsigned 32-bit clock in 45 kHz units.
            dvdLogicalClockBaseMs = ((long) stc & 0xFFFFFFFFL) * 1_000L / 45_000L;
        }
    }

    @Override
    public void stop()
    {
        playbackRateController.reset();
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
                    if (dvdPushMode && !dvdRenderedFirstFrameInEpoch)
                    {
                        dvdPauseAfterFirstFrame = true;
                        pausePlayer.setPlayWhenReady(true);
                        PlaybackDebugTrap.record("dvd_pause_wait_first_frame",
                                Media3MediaPlayerImpl.this);
                        return;
                    }
                    PlaybackDebugTrap.record("backend_pause_invoke", Media3MediaPlayerImpl.this);
                    ExoPause();
                    PlaybackDebugTrap.record("backend_pause_return", Media3MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
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
                    PlaybackDebugTrap.record("frame_step_stale", Media3MediaPlayerImpl.this);
                    return;
                }
                Format videoFormat = stepPlayer.getVideoFormat();
                float frameRate = videoFormat == null ? Format.NO_VALUE : videoFormat.frameRate;
                long targetMs = PlaybackFrameStepPolicy.targetPositionMs(
                        stepPlayer.getContentPosition(), stepPlayer.getDuration(), amount, frameRate);
                PlaybackDebugTrap.record("frame_step_invoke", Media3MediaPlayerImpl.this);
                stepPlayer.setSeekParameters(SeekParameters.EXACT);
                stepPlayer.seekTo(targetMs);
                stepPlayer.setPlayWhenReady(false);
                PlaybackDebugTrap.record("frame_step_return", Media3MediaPlayerImpl.this);
            }
        });
        return true;
    }

    @Override
    public void play()
    {
        log.logDebug("Play called");
        playRequested = true;
        dvdPauseAfterFirstFrame = false;

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
                    PlaybackDebugTrap.record("backend_play_invoke", Media3MediaPlayerImpl.this);
                    ExoStart();
                    PlaybackDebugTrap.record("backend_play_return", Media3MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
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

    private void cancelPullSeekRecovery()
    {
        pullSeekRecoveryMonitor.cancel();
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
        if (!runtimeConfig.isSeekRecoveryEnabled()
                || pushMode || player == null || mediaSource == null)
        {
            return;
        }

        final PlaybackSessionController.Token session = currentPlaybackSession();
        final boolean resumeWhenReady = player.getPlayWhenReady();
        PlaybackDebugTrap.record("pull_seek_recovery_armed", Media3MediaPlayerImpl.this);

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
                            beginPlaybackOperation(PlaybackSessionController.Operation.RECOVERY);
                            seekPending = true;
                            PlaybackDebugTrap.record("pull_seek_reprepare_before", Media3MediaPlayerImpl.this);
                            player.setMediaSource(mediaSource, targetPositionMs);
                            player.prepare();
                            player.setPlayWhenReady(resumeWhenReady);
                            PlaybackDebugTrap.record("pull_seek_reprepare_after", Media3MediaPlayerImpl.this);
                            log.logDebug("Pull seek recovery reprepare at " + targetPositionMs + "ms after " + runtimeConfig.getSeekRecoveryDelayMs() + "ms buffering watchdog");
                        }
                        catch (Exception ex)
                        {
                            PlaybackDebugTrap.record("pull_seek_reprepare_error_" + ex.getClass().getSimpleName(), Media3MediaPlayerImpl.this);
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
                        long safePositionMs = PlaybackSeekPolicy.clamp(
                                timeInMillis, durationMs, bufferedPositionMs, mediaContext.isTimeshifted());
                        log.logDebug("Seek Called - Current Position: " + currentPositionMs + "  Seek Request: " + timeInMillis
                                + " Safe Position: " + safePositionMs + " Duration: " + durationMs
                                + " Buffered Edge: " + bufferedPositionMs
                                + " Difference: " + (currentPositionMs - safePositionMs));
                        if (safePositionMs != timeInMillis)
                        {
                            PlaybackDebugTrap.record(mediaContext.isTimeshifted() ? "seek_clamped_live_edge" : "seek_clamped_eof", Media3MediaPlayerImpl.this);
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
                            PlaybackDebugTrap.record("pull_seek_policy_" + seekPolicy, Media3MediaPlayerImpl.this);
                            log.logDebug("Pull directional seek policy=" + seekPolicy + ", deltaMs=" + (safePositionMs - currentPositionMs));
                        }
                        else
                        {
                            cancelPullSeekRecovery();
                        }

                        boolean smbDirectSeek = !pushMode
                                && dataSource instanceof Media3PullDataSource
                                && ((Media3PullDataSource) dataSource).isSmbModeConfigured();
                        PlaybackDebugTrap.recordDetailed("backend_seek_invoke",
                                Media3MediaPlayerImpl.this,
                                "requestedMs=" + timeInMillis + ";appliedMs=" + safePositionMs
                                        + ";durationMs=" + durationMs
                                        + ";bufferedMs=" + bufferedPositionMs);
                        if (smbDirectSeek)
                        {
                            boolean resumeWhenReady = player.getPlayWhenReady();
                            cancelPullSeekRecovery();
                            PlaybackDebugTrap.record("smb_seek_reprepare_before", Media3MediaPlayerImpl.this);
                            player.setMediaSource(mediaSource, safePositionMs);
                            player.prepare();
                            player.setPlayWhenReady(resumeWhenReady);
                            PlaybackDebugTrap.record("smb_seek_reprepare_after", Media3MediaPlayerImpl.this);
                        }
                        else
                        {
                            player.seekTo(safePositionMs);
                        }
                        PlaybackDebugTrap.record("backend_seek_return", Media3MediaPlayerImpl.this);
                        if (!pushMode && !smbDirectSeek)
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

                if (streamPos == Media3MediaPlayerImpl.DISABLE_TRACK)
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
        return this.selectedSubtitleTrack;
    }

    @Override
    public int getSubtitleTrackCount()
    {
        return getTrackCount(C.TRACK_TYPE_TEXT);
    }

    @Override
    public void setAudioTrack(final int streamPos)
    {
        final DvdAudioStreamCode dvdStreamCode = dvdPushMode
                ? DvdAudioStreamCode.decode(streamPos) : null;
        final boolean physicalDvdStream = dvdStreamCode != null
                && (dvdStreamCode.isPrivateStream1()
                || dvdStreamCode.codecFamily == DvdAudioStreamCode.CodecFamily.MPEG_AUDIO);
        final int normalizedStreamPos = physicalDvdStream
                ? dvdStreamCode.rawCode : streamPos;
        if (physicalDvdStream)
        {
            requestedDvdAudioStream = normalizedStreamPos;
            appliedDvdAudioStream = -1;
        }
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
                    int groupIndex = normalizedStreamPos;
                    if (physicalDvdStream)
                    {
                        groupIndex = resolveDvdAudioTrackGroup(normalizedStreamPos);
                        if (groupIndex == C.INDEX_UNSET)
                        {
                            // DVD_STREAM can arrive before Media3 publishes its
                            // mapped track groups. Keep the packed private-stream
                            // id and retry it when STATE_READY is reported.
                            initialAudioTrackIndex = normalizedStreamPos;
                            log.logDebug("DVD audio stream " + dvdStreamCode
                                    + " is not mapped yet; retaining the request");
                            return;
                        }
                        log.logDebug("Mapped DVD audio stream " + dvdStreamCode
                                + " to Media3 audio track group " + groupIndex);
                    }
                    initialAudioTrackIndex = -1;
                    changeTrack(C.TRACK_TYPE_AUDIO, groupIndex, 0);
                    if (physicalDvdStream && normalizedStreamPos == requestedDvdAudioStream)
                        appliedDvdAudioStream = normalizedStreamPos;
                }
            }
        });
    }

    /**
     * Applies the server-selected DVD AC-3 substream once its independently
     * extracted Media3 track group exists. A disc may expose private streams
     * in authored packet order (for example Portuguese 0x82 before English
     * 0x80), so accepting Media3's first/default group is not sufficient.
     */
    private void applyRequestedDvdAudioTrack()
    {
        final int streamPos = requestedDvdAudioStream;
        if (!dvdPushMode || streamPos < 0)
            return;
        Runnable apply = new Runnable()
        {
            @Override
            public void run()
            {
                // A replacement DVD cell publishes track groups incrementally.
                // Resolve and apply on the player application looper, and mark
                // the request applied only after the override actually exists.
                // The previous implementation queued changeTrack(), marked the
                // request applied immediately, and could permanently suppress
                // retry when that queued group became stale during reprepare.
                if (!dvdPushMode || requestedDvdAudioStream != streamPos
                        || appliedDvdAudioStream == streamPos || trackSelector == null)
                    return;
                int groupIndex = resolveDvdAudioTrackGroup(streamPos);
                if (groupIndex == C.INDEX_UNSET)
                {
                    initialAudioTrackIndex = streamPos;
                    return;
                }
                MappingTrackSelector.MappedTrackInfo trackInfo =
                        trackSelector.getCurrentMappedTrackInfo();
                int rendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_AUDIO);
                if (rendererIndex == C.INDEX_UNSET)
                    return;
                TrackGroupArray groups = trackInfo.getTrackGroups(rendererIndex);
                if (groupIndex < 0 || groupIndex >= groups.length
                        || trackInfo.getTrackSupport(rendererIndex, groupIndex, 0)
                        != C.FORMAT_HANDLED)
                    return;
                TrackGroup selectedGroup = groups.get(groupIndex);
                DefaultTrackSelector.Parameters.Builder builder =
                        trackSelector.buildUponParameters();
                builder.setRendererDisabled(rendererIndex, false);
                builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false);
                builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO);
                builder.addOverride(new TrackSelectionOverride(selectedGroup, 0));
                trackSelector.setParameters(builder.build());
                initialAudioTrackIndex = -1;
                appliedDvdAudioStream = streamPos;
                log.logDebug("Applied retained DVD audio stream " + streamPos
                        + " to Media3 audio track group " + groupIndex);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper())
            apply.run();
        else
            context.runOnUiThread(apply);
    }

    /** Removes an override tied to the outgoing DVD TrackGroup before reprepare. */
    private void clearDvdAudioSelectionOverride()
    {
        if (trackSelector == null)
            return;
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO);
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false);
        trackSelector.setParameters(builder.build());
    }

    /** Resolves SageTV's packed DVD private-stream id (for example 0xBD80). */
    private int resolveDvdAudioTrackGroup(int streamPos)
    {
        if (trackSelector == null)
            return C.INDEX_UNSET;
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        int rendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_AUDIO);
        if (rendererIndex == C.INDEX_UNSET)
            return C.INDEX_UNSET;
        TrackGroupArray groups = trackInfo.getTrackGroups(rendererIndex);
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++)
        {
            TrackGroup group = groups.get(groupIndex);
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++)
            {
                if (dvdFormatIdMatchesStream(group.getFormat(trackIndex).id, streamPos))
                    return groupIndex;
            }
        }
        if (dvdMimTransport)
        {
            DvdAudioStreamCode stream = DvdAudioStreamCode.decode(streamPos);
            int familyOrdinal = 0;
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++)
            {
                TrackGroup group = groups.get(groupIndex);
                boolean matchingFamily = false;
                for (int trackIndex = 0; trackIndex < group.length; trackIndex++)
                    matchingFamily |= stream.matchesTranscodedMime(
                            group.getFormat(trackIndex).sampleMimeType);
                if (matchingFamily && familyOrdinal++ == stream.familyOrdinal)
                    return groupIndex;
            }
        }
        return C.INDEX_UNSET;
    }

    static boolean dvdFormatIdMatchesStream(String formatId, int streamPos)
    {
        if (formatId == null)
            return false;
        DvdAudioStreamCode stream = DvdAudioStreamCode.decode(streamPos);
        if (formatIdMatchesNumericId(formatId, stream.rawCode))
            return true;
        // Media3's MPEG Program Stream extractor identifies MPEG audio by the
        // one-byte PES id (0xc0..0xdf), while MiniDVDPlayer's wire selector is
        // packed into the upper byte (0xc000..0xdf00).
        return stream.codecFamily == DvdAudioStreamCode.CodecFamily.MPEG_AUDIO
                && formatIdMatchesNumericId(formatId, stream.pesStreamId);
    }

    private static boolean formatIdMatchesNumericId(String formatId, int value)
    {
        String expected = Integer.toString(value);
        return expected.equals(formatId)
                || formatId.endsWith("/" + expected)
                || formatId.endsWith(":" + expected);
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

        if (dvdPushMode)
        {
            // MiniDVDPlayer waits for the decoder to drain before FLUSH. The
            // last decoded frame can be the background for a static authored
            // menu while only SPU highlight commands continue. Keep the same
            // player and Surface attached while safely rebuilding its media
            // pipeline at the byte-stream boundary below.
            final Media3PushDataSource dvdSource = dataSource instanceof Media3PushDataSource
                    ? (Media3PushDataSource) dataSource : null;
            // Preserve continuity when SageTV performs a non-discontinuous
            // FLUSH. For a discontinuity, the DVD VM sends a new STC after
            // FLUSH and before replacement MPEG bytes; dvdSetStc() then
            // replaces this fallback with the authoritative cell clock.
            if (!dvdRepreparePending)
                dvdLogicalClockBaseMs += getPlaybackPosition();
            if (dvdSource != null)
                dvdSource.suspendReads();
            super.flush();
            PlaybackDebugTrap.record("dvd_flush_preserve_surface", this);
            // Do not immediately reprepare here. Authored static menus can
            // send only SPU commands after FLUSH and rely on the last decoded
            // video frame remaining on the Surface. Keep reads suspended and
            // rebuild the zero-based MediaSource only when replacement MPEG
            // bytes actually arrive in pushData().
            dvdRepreparePending = true;
            dvdSegmentReaderEnded = false;
            return;
        }
        super.flush();
        final PlaybackSessionController.Token session = currentPlaybackSession();
        final ExoPlayer flushPlayer = player;

        Runnable reprepare = new Runnable()
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

                    PlaybackDebugTrap.record("flush_reprepare_before", Media3MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, true);
                    player.prepare();
                    PlaybackDebugTrap.record("flush_reprepare_after", Media3MediaPlayerImpl.this);

                    log.logDebug("After Flush was called Current Playback Position: " + Utils.toHHMMSS(player.getCurrentPosition()));
                    Media3MediaPlayerImpl.this.currentPlaybackPosition = player.getCurrentPosition();
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("flush_reprepare_error_" + ex.getClass().getSimpleName(), Media3MediaPlayerImpl.this);
                    log.logError("Media3 flush/reprepare failed", ex);
                }
                finally
                {
                    playbackPositionLock.unlock();
                }

            }
        };
        context.runOnUiThread(reprepare);
    }

    @Override
    protected void setupPlayer(String sageTVurl)
    {
        // BaseMediaPlayerImpl preserves the protocol URL in lastUri but wraps
        // non-HTTP inputs as stv://server/... before calling setupPlayer().
        // Detect the DVD session from the original MiniPlayer URL so the
        // DVD-aware MPEG-PS extractor cannot be bypassed by that wrapper.
        dvdPushMode = "push:dvd".equals(lastUri)
                || (lastUri != null && lastUri.startsWith("push:dvd"))
                || (sageTVurl != null && (sageTVurl.startsWith("push:dvd")
                || sageTVurl.endsWith("/push:dvd")));
        dvdMediaUri = dvdPushMode ? sageTVurl : null;
        dvdReaderGeneration = 0;
        dvdRepreparePending = false;
        dvdReprepareScheduled = false;
        dvdSegmentReaderEnded = false;
        dvdEpochExtractorsFactory = null;
        dvdRenderedFirstFrameInEpoch = false;
        dvdPauseAfterFirstFrame = false;
        dvdLogicalClockBaseMs = 0;
        initialAudioTrackIndex = -1;
        requestedDvdAudioStream = -1;
        appliedDvdAudioStream = -1;

        if (player != null)
        {
            releasePlayer();
        }

        // VerboseLogUtil.setEnableAllTags(true);

        //if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        log.logDebug("Setting up the Media3 media player for: " + sageTVurl);

        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        ActivePlayerSessionOverrides.applyRuntimeTuning(
                prefs, PlayerRuntimeConfig.Backend.MEDIA3);
        PlayerRuntimeTuning.applyMedia3CodecPreference(prefs.getString(
                PrefStore.Keys.media3_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE));
        runtimeConfig = PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.MEDIA3);
        log.logDebug("Runtime configuration: " + runtimeConfig.compactLog());

        if (pushMode)
        {
            log.logDebug("Creating Media3PushDataSource datasource");
            dataSource = new Media3PushDataSource();
        }
        else
        {
            if (!httpls)
            {
                log.logDebug("Creating datasource");
                SmbDirectConfig smbConfig = AndroidPrefStore.isSmbStreamingMode(prefs.getStreamingMode())
                        ? SmbDirectConfig.from(prefs) : null;
                dataSource = new Media3PullDataSource(
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

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context.getContext());
        String codecMode = runtimeConfig.getCodecMode();
        if ("async".equals(codecMode)) renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
        else if ("sync".equals(codecMode)) renderersFactory.forceDisableMediaCodecAsynchronousQueueing();
        log.logDebug("Media3 codec adapter mode: " + codecMode);

        // Media3 uses platform MediaCodec decoders in this first isolated backend.
        // The legacy ExoPlayer FFmpeg extension is intentionally not loaded here.

        DecodingMethod decodingMethod = DecodingMethod.fromPreference(
                ActivePlayerSessionOverrides.resolveDecodingMethod(prefs.getString(
                        PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE)));
        Media3CodecSelector mediaCodecSelector = new Media3CodecSelector(decodingMethod);
        renderersFactory.setMediaCodecSelector(mediaCodecSelector);
        renderersFactory.setEnableDecoderFallback(decodingMethod.hardwarePreferred());
        log.logDebug("Media3 Decoding Method: " + decodingMethod.displayName());

        trackSelector = new DefaultTrackSelector(context.getContext());
        // Kodi explicitly disables tunneled MediaCodec output for its Surface
        // renderer. Media3 also defaults to non-tunneled playback, but keep the
        // invariant explicit so a future preference/default change cannot put
        // SageTV's independent audio/video and DVD overlay clocks into a
        // tunneled session.
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTunnelingEnabled(false));
        String preferredAudioLanguage = TrackPreferencePolicy.normalizeLanguage(
                prefs.getString(PrefStore.Keys.preferred_audio_language, ""));
        if (!preferredAudioLanguage.isEmpty())
        {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(preferredAudioLanguage));
            log.logDebug("Preferred audio language: " + preferredAudioLanguage);
        }


        ExoPlayer.Builder builder = new ExoPlayer.Builder(context.getContext(), renderersFactory);

        if (dvdPushMode)
        {
            // The generic Media3 default queues roughly 50 seconds of a Push
            // stream. MiniDVDPlayer is an interactive server-side VM and sends
            // NEWCELL/STC/SPU commands in byte-stream order; allowing commands
            // to run tens of seconds ahead of rendering causes decoder cadence
            // stalls and makes menu transitions sluggish. Keep a modest time-
            // based buffer while retaining quick start and HDD/network margin.
            DefaultLoadControl dvdLoadControl = new DefaultLoadControl.Builder()
                    // A DVD drain poll deliberately stops server input. A
                    // positive rebuffer threshold can pause Media3 with a
                    // partial final queue and deadlock against that poll.
                    // Zero lets the queued cell/menu frames render to empty.
                    // Authored navigation cells can contain less than 500 ms
                    // of audio/video and MiniDVDPlayer waits for that cell to
                    // drain before sending the next one. Requiring a normal
                    // startup buffer here deadlocks both sides (observed with
                    // a 480 ms menu-transition cell). Start any valid queued
                    // sample immediately; the server-side DVD VM is already
                    // pacing cell boundaries.
                    .setBufferDurationsMs(2_000, 8_000, 0, 0)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            builder.setLoadControl(dvdLoadControl);
            log.logDebug("DVD Push load control enabled: min=2000ms max=8000ms playback=0ms rebuffer=0ms");
        }
        else if (!pushMode && !httpls)
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
        dvdFrameMetadataCount = 0;
        dvdLastFramePresentationUs = C.TIME_UNSET;
        dvdLastFrameReleaseNs = C.TIME_UNSET;
        dvdLastFramePresentationDeltaUs = C.TIME_UNSET;
        dvdLastFrameReleaseDeltaUs = C.TIME_UNSET;
        dvdMaxFrameReleaseDeltaUs = 0;
        dvdFrameReleaseGapCount = 0;
        dvdFrameReleaseNonPositiveCount = 0;
        dvdFrameReleaseUnder10MsCount = 0;
        dvdFrameRelease10To25MsCount = 0;
        dvdFrameRelease25To45MsCount = 0;
        dvdFrameRelease45To75MsCount = 0;
        dvdFrameRelease75To100MsCount = 0;
        synchronized (dvdFramePresentationDeltaTraceUs)
        {
            dvdFrameCadenceTraceIndex = 0;
            dvdFrameCadenceTraceCount = 0;
        }
        player.setVideoFrameMetadataListener(new VideoFrameMetadataListener()
        {
            @Override
            public void onVideoFrameAboutToBeRendered(long presentationTimeUs,
                    long releaseTimeNs, Format format, MediaFormat mediaFormat)
            {
                if (!dvdPushMode)
                    return;
                long previousPresentationUs = dvdLastFramePresentationUs;
                long previousReleaseNs = dvdLastFrameReleaseNs;
                dvdLastFramePresentationUs = presentationTimeUs;
                dvdLastFrameReleaseNs = releaseTimeNs;
                dvdFrameMetadataCount++;
                if (previousPresentationUs != C.TIME_UNSET)
                    dvdLastFramePresentationDeltaUs = presentationTimeUs
                            - previousPresentationUs;
                if (previousReleaseNs != C.TIME_UNSET)
                {
                    long deltaUs = (releaseTimeNs - previousReleaseNs) / 1_000L;
                    dvdLastFrameReleaseDeltaUs = deltaUs;
                    synchronized (dvdFramePresentationDeltaTraceUs)
                    {
                        dvdFramePresentationDeltaTraceUs[dvdFrameCadenceTraceIndex] =
                                dvdLastFramePresentationDeltaUs;
                        dvdFrameReleaseDeltaTraceUs[dvdFrameCadenceTraceIndex] = deltaUs;
                        dvdFrameCadenceTraceIndex = (dvdFrameCadenceTraceIndex + 1)
                                % DVD_FRAME_CADENCE_TRACE_SIZE;
                        if (dvdFrameCadenceTraceCount < DVD_FRAME_CADENCE_TRACE_SIZE)
                            dvdFrameCadenceTraceCount++;
                    }
                    if (deltaUs > dvdMaxFrameReleaseDeltaUs)
                        dvdMaxFrameReleaseDeltaUs = deltaUs;
                    if (deltaUs <= 0L)
                        dvdFrameReleaseNonPositiveCount++;
                    else if (deltaUs < 10_000L)
                        dvdFrameReleaseUnder10MsCount++;
                    else if (deltaUs < 25_000L)
                        dvdFrameRelease10To25MsCount++;
                    else if (deltaUs < 45_000L)
                        dvdFrameRelease25To45MsCount++;
                    else if (deltaUs < 75_000L)
                        dvdFrameRelease45To75MsCount++;
                    else if (deltaUs <= 100_000L)
                        dvdFrameRelease75To100MsCount++;
                    else
                    {
                        dvdFrameReleaseGapCount++;
                        if (dvdFrameReleaseGapCount <= 24
                                || (dvdFrameReleaseGapCount & (dvdFrameReleaseGapCount - 1)) == 0)
                        {
                            log.logWarning("DVD frame release gap count="
                                    + dvdFrameReleaseGapCount
                                    + " releaseDeltaUs=" + deltaUs
                                    + " presentationDeltaUs="
                                    + dvdLastFramePresentationDeltaUs
                                    + " presentationUs=" + presentationTimeUs
                                    + " timestampDecision={"
                                    + describeDvdTimestampDecision(presentationTimeUs) + "}");
                        }
                    }
                }
            }
        });
        final PlaybackSessionController.Token listenerSession = currentPlaybackSession();
        final ExoPlayer listenerPlayer = player;
        //player.addAnalyticsListener(new EventLogger(trackSelector));

        player.addListener(new Player.Listener()
        {
            @Override
            public void onTracksChanged(Tracks tracks)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                // Track discovery is incremental for DVD private_stream_1.
                // Retry here because STATE_READY may precede the requested
                // AC-3 substream becoming visible to the selector.
                applyRequestedDvdAudioTrack();
            }

            @Override
            public void onPlayerError(PlaybackException error)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("player_error_" + error.getErrorCodeName(), Media3MediaPlayerImpl.this);
                log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                error.printStackTrace();

                if (fastSwitchFallbackPending)
                {
                    fallbackFromFastSwitch("asynchronous_" + error.getErrorCodeName());
                    return;
                }

                if (retryCount == 0)
                {
                    //Show toast on first error
                    context.showErrorMessage(error.getErrorCodeName(), "Media3MediaPlayer");
                }

                if (retryCount <= MAX_PLAYBACK_RETRY_COUNT)
                {

                    errorState = true;
                    retryCount++;
                    long recoveryPositionMs = Math.max(0L, player.getCurrentPosition());
                    boolean resumePlayback = player.getPlayWhenReady();
                    if (!pushMode && mediaSource != null)
                    {
                        // Reattach the Pull/SMB source at the position captured
                        // by the error callback. Calling seekTo() on an errored
                        // player followed by prepare() allowed Media3 to restart
                        // growing live TV at zero after a stale-end timeout.
                        player.setMediaSource(mediaSource, recoveryPositionMs);
                        player.prepare();
                        player.setPlayWhenReady(resumePlayback);
                        PlaybackDebugTrap.record("player_error_recovery_position_preserved",
                                Media3MediaPlayerImpl.this);
                    }
                    else
                    {
                        // PUSH/FIXED transport position remains server-owned.
                        player.seekTo(recoveryPositionMs + 100L);
                        player.prepare();
                    }
                }
                else
                {
                    log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                    log.logError("Playback Exception: " + error.getErrorCodeName(), error);
                    context.showErrorMessage("Max playback retry reached!", "Media3MediaPlayer");
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                PlaybackDebugTrap.record("playback_state_" + playbackState, Media3MediaPlayerImpl.this);
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
                    if (dvdPushMode && (dvdSegmentReaderEnded
                            || dvdRepreparePending || dvdReprepareScheduled))
                    {
                        // The server-side DVD VM divides one playback session
                        // into many finite MPEG-PS reader generations. END for
                        // one generation is only a cell/title boundary. If it
                        // is promoted to session EOS, getBufferLeft() returns
                        // -1 and MiniDVDPlayer terminates its pusher before the
                        // replacement cell can arrive.
                        eos = false;
                        state = playRequested ? PLAY_STATE : PAUSE_STATE;
                        PlaybackDebugTrap.record("dvd_transient_ended_ignored",
                                Media3MediaPlayerImpl.this);
                        log.logDebug("DVD reader generation ended; retaining PUSH session");
                    }
                    else
                    {
                        log.logDebug("Player Has Ended, set EOS");
                        //stop(); - JVL: Not sure if we will need to do this or not
                        //notifySageTVStop();
                        eos = true;
                        state = Media3MediaPlayerImpl.EOS_STATE;
                    }
                }
                if (playbackState == Player.STATE_READY)
                {
                    cancelPullSeekRecovery();
                    log.logDebug("Player.STATE_READY - Media loaded and ready for playback");
                    if (errorState)
                    {
                        errorState = false;
                        retryCount = 0;
                    }

                    log.logDebug("Player.STATE_READY - setAudioTrack getting called");
                    if (initialAudioTrackIndex != -1)
                    {
                        int pendingAudioTrack = initialAudioTrackIndex;
                        initialAudioTrackIndex = -1;
                        setAudioTrack(pendingAudioTrack);
                    }
                    applyRequestedDvdAudioTrack();

                    log.logDebug("Player.STATE_READY - Debugging available tracks in file");
                    debugAvailableTracks();
                    if (requestedSubtitleTrack == PREFERRED_TRACK)
                    {
                        setPreferredSubtitleTrack();
                    }
                    else if (requestedSubtitleTrack != DISABLE_TRACK
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
                    updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
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
                updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                PlaybackDebugTrap.record("position_discontinuity_" + reason, Media3MediaPlayerImpl.this);
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
                PlaybackDebugTrap.record(isPlaying ? "is_playing_true" : "is_playing_false", Media3MediaPlayerImpl.this);
            }

            @Override
            public void onRenderedFirstFrame()
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                firstVideoFrameRendered = true;
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("first_video_frame", Media3MediaPlayerImpl.this);
                if (fastSwitchAwaitingFirstFrame)
                {
                    cancelFastSwitchWatchdog();
                    fastSwitchAwaitingFirstFrame = false;
                    fastSwitchFallbackPending = false;
                    fastSwitchSuccessCount++;
                    fastSwitchLastReason = "first_frame_rendered";
                    PlaybackDebugTrap.record("fast_switch_first_frame",
                            Media3MediaPlayerImpl.this);
                }
                dvdRenderedFirstFrameInEpoch = true;
                if (dvdPushMode && dvdPauseAfterFirstFrame && !playRequested)
                {
                    dvdPauseAfterFirstFrame = false;
                    listenerPlayer.setPlayWhenReady(false);
                    PlaybackDebugTrap.record("dvd_pause_after_first_frame",
                            Media3MediaPlayerImpl.this);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize)
            {
                applySavedRefreshRateIfPossible();
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                int width = videoSize.width;
                int height = videoSize.height;
                float pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio;

                if (dvdPushMode && !dvdMimTransport)
                    dvdSubpictureDecoder.setVideoHeight(height);

                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.logDebug("Media3.onVideoSizeChanged: " + width + "x" + height + ", pixel ratio: " + pixelWidthHeightRatio);
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
            public void onCues(CueGroup cueGroup)
            {
                if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                subtitleCueUpdateCount++;
                StringBuilder text = new StringBuilder();
                for (Cue cue : cueGroup.cues)
                {
                    if (cue.text != null && cue.text.length() > 0)
                    {
                        if (text.length() > 0)
                            text.append(' ');
                        text.append(cue.text);
                    }
                }
                currentSubtitleCueText = text.toString();
                if (text.length() > 0)
                {
                    subtitleNonEmptyCueCount++;
                    lastSubtitleCueText = text.toString();
                }
                MiniClientConnection subtitleConnection = MiniclientApplication.get()
                        .getClient().getCurrentConnection();
                boolean serverRendersCaptions = subtitleConnection != null
                        && subtitleConnection.isSubtitleCallbackEnabled()
                        && legacyCaptionBridge.isForwardingCurrentStream();
                if (showCaptions && !serverRendersCaptions && subView != null)
                {
                    subView.setCues(cueGroup.cues);
                }
                else if (serverRendersCaptions && subView != null)
                {
                    subView.setCues(java.util.Collections.<Cue>emptyList());
                    // SageTV owns presentation after the negotiated extender
                    // callback becomes active. Keep Media3's text renderer enabled
                    // for the extractor tap, but remove the Android view so a later
                    // cue callback cannot produce a duplicate local overlay.
                    RemoveSubTitleView();
                }
            }
        });


        if (!httpls)
        {

            androidx.media3.datasource.DataSource.Factory dataSourceFactory = new DataSource.Factory()
            {
                @Override
                public DataSource createDataSource()
                {
                    return dataSource;
                }
            };

            // Pull-mode SageTV recordings are commonly MPEG-TS. Media3's default TS
            // timestamp search window can be too small to build a usable seek map on
            // broadcast/PVR files, which makes seekTo() resolve back to the start.
            // Caption declarations are required in both Push and Pull because
            // many ATSC streams carry A/53 captions without a complete PMT
            // caption-service descriptor. Seek tuning remains Pull-only.
            if (!pushMode)
            {
                ExtractorsFactory extractorsFactory = createCaptionAwareExtractorsFactory(true);
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                log.logDebug("Pull extractor seek tuning enabled. TS timestamp search bytes: "
                        + (TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * runtimeConfig.getTsSearchMultiplier()));
            }
            else
            {
                ExtractorsFactory defaultPushExtractors =
                        createCaptionAwareExtractorsFactory(false);
                ExtractorsFactory pushExtractors;
                if (dvdPushMode)
                {
                    // Player/surface replacement can synchronously release the
                    // previous session while ExoPlayer is being constructed.
                    // Keep the transport selected by push:dvd authoritative and
                    // never dereference a datasource that an old release cleared.
                    if (!(dataSource instanceof Media3PushDataSource))
                    {
                        log.logWarning("DVD Push datasource was unavailable after player setup; recreating it");
                        dataSource = new Media3PushDataSource();
                    }
                    if (dvdMimTransport)
                    {
                        // MIM emits ordinary H.264/AC-3/DVB-sub MPEG-TS. The
                        // DVD PS/SPU extractor would misidentify this stream.
                        dvdExtractorsFactory = null;
                        pushExtractors = defaultPushExtractors;
                    }
                    else
                    {
                        dvdExtractorsFactory = new ResilientDvdPsExtractorsFactory(
                                defaultPushExtractors, dvdSubpictureDecoder,
                                shouldRepairNativeDvdMpeg2Timestamps(prefs));
                        log.logDebug("Native DVD MPEG-2 timestamp repair: "
                                + dvdExtractorsFactory.isMpeg2PictureTimestampRepairEnabled());
                        dvdExtractorsFactory.setPtsOffset90Khz(getDvdPtsOffset90KhzForDebug());
                        pushExtractors = dvdExtractorsFactory;
                    }
                    dvdEpochExtractorsFactory = pushExtractors;
                    final Media3PushDataSource dvdSource =
                            (Media3PushDataSource) dataSource;
                    long generation = ++dvdReaderGeneration;
                    dvdSource.activateReaderGeneration(generation);
                    dataSourceFactory = dvdSource.readerFactory(generation);
                }
                else
                {
                    dvdExtractorsFactory = null;
                    dvdEpochExtractorsFactory = null;
                    pushExtractors = defaultPushExtractors;
                }
                mediaSource = new ProgressiveMediaSource.Factory(
                        dataSourceFactory, pushExtractors)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
            }


            boolean haveStartPosition = (playbackStartPosition >= 0);
            long requestedStartPosition = playbackStartPosition;
            log.logDebug("Media3Logging - Preparing playback");
            if (haveStartPosition)
            {
                // A seek issued between OPENURL and player construction belongs to the
                // new MediaItem. Calling seekTo() before setMediaSource() addresses the
                // old/empty timeline and is discarded when the source is attached,
                // which made stock-server resume always start at zero.
                player.setMediaSource(mediaSource, requestedStartPosition);
                playbackStartPosition = -1;
                PlaybackDebugTrap.recordDetailed("initial_seek_attached_to_source", this,
                        "appliedMs=" + requestedStartPosition);
                log.logDebug("Media3Logging - Start Position: " + requestedStartPosition);
            }
            else
            {
                player.setMediaSource(mediaSource, true);
            }
            player.prepare();

        }
        else
        {
            // Let Media3's default MediaSourceFactory choose the appropriate source for HTTP.
            player.setMediaItem(MediaItem.fromUri(Uri.parse(sageTVurl)));
            player.prepare();
        }

        //Set seek preferences
        //player.setSeekParameters(SeekParameters.CLOSEST_SYNC);

        // start playing
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
                    Media3MediaPlayerImpl.this.setPlaybackPosition(currentPositionMs);
                    scheduleLegacyCaptionDrain(currentPositionMs * 1000L);
                    currentBufferedPosition = listenerPlayer.getBufferedPosition();
                    promoteConfirmedPullTailToEos();
                    progressHandler.postDelayed(sessionProgress[0], 500);
                }
                else if (isCurrentPlaybackSession(listenerSession))
                {
                    Media3MediaPlayerImpl.this.setPlaybackPosition(0);
                }
            }
        };
        progressRunnable = sessionProgress[0];

        progressHandler.postDelayed(progressRunnable, 0);
    }

    @Override
    public void dvdNewCell(int payloadSize, byte[] payload)
    {
        super.dvdNewCell(payloadSize, payload);
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        if (factory != null)
        {
            long boundaryPosition = dataSource instanceof PushBufferDataSource
                    ? ((PushBufferDataSource) dataSource).getPushedBytes() : 0L;
            factory.queuePtsOffset90Khz(boundaryPosition, getDvdPtsOffset90KhzForDebug());
            log.logDebug("DVD NEWCELL queued at byte=" + boundaryPosition
                    + " PTS offset90Khz=" + getDvdPtsOffset90KhzForDebug());
        }
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

    public long getLegacyCaptionCallbackBytesForDebug()
    {
        return legacyCaptionBridge.getForwardedBytes();
    }

    public boolean isLegacyCaptionCallbackActiveForDebug()
    {
        return legacyCaptionBridge.isForwardingCurrentStream();
    }

    public long getFastSwitchAttemptCountForDebug() { return fastSwitchAttemptCount; }
    public long getFastSwitchSuccessCountForDebug() { return fastSwitchSuccessCount; }
    public long getFastSwitchFallbackCountForDebug() { return fastSwitchFallbackCount; }
    public boolean isFastSwitchAwaitingFirstFrameForDebug()
    {
        return fastSwitchAwaitingFirstFrame;
    }
    public String getFastSwitchLastReasonForDebug() { return fastSwitchLastReason; }
    public String getFastSwitchTargetUrlForDebug() { return fastSwitchTargetUrl; }

    public long getDvdLatestVideoSampleUsForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? C.TIME_UNSET : factory.getLatestVideoSampleUs();
    }

    public long getDvdLatestAudioSampleUsForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? C.TIME_UNSET : factory.getLatestAudioSampleUs();
    }

    public long getDvdAvSampleDeltaUsForDebug()
    {
        long videoUs = getDvdLatestVideoSampleUsForDebug();
        long audioUs = getDvdLatestAudioSampleUsForDebug();
        return videoUs == C.TIME_UNSET || audioUs == C.TIME_UNSET
                ? C.TIME_UNSET : videoUs - audioUs;
    }

    public long getDvdVideoTimestampCorrectionCountForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? 0L : factory.getVideoCorrectionCount();
    }

    public double getDvdMpeg2ReportedFrameRateHzForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? 0.0 : factory.getMpeg2ReportedFrameRateHz();
    }

    public double getDvdMpeg2SequenceFrameRateHzForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? 0.0 : factory.getMpeg2SequenceFrameRateHz();
    }

    @Override
    public float getContentFrameRateHz()
    {
        double dvdRate = getDvdMpeg2SequenceFrameRateHzForDebug();
        if (dvdRate > 0.0)
            return (float) dvdRate;
        ExoPlayer activePlayer = player;
        Format format = activePlayer == null ? null : activePlayer.getVideoFormat();
        return format == null ? -1f : format.frameRate;
    }

    public double getDvdMpeg2EffectiveFieldDurationUsForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? 0.0 : factory.getMpeg2EffectiveFieldDurationUs();
    }

    public boolean isDvdMpeg2TelecineCadenceSeenForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory != null && factory.isMpeg2TelecineCadenceSeen();
    }

    public String getMpeg2InterlaceObservationForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        String dvd = factory == null ? "unknown" : factory.getMpeg2InterlaceObservation();
        return "unknown".equals(dvd) ? mpeg2InterlaceObserver.getInterlaceObservation() : dvd;
    }

    public boolean isMpeg2SequenceExtensionSeenForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory != null && factory.isMpeg2SequenceExtensionSeen()
                || mpeg2InterlaceObserver.isSequenceExtensionSeen();
    }

    public long getMpeg2ProgressiveFrameCountForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        long dvd = factory == null ? 0L : factory.getMpeg2ProgressiveFrameCount();
        return dvd > 0L ? dvd : mpeg2InterlaceObserver.getProgressiveFrameCount();
    }

    public long getMpeg2InterlacedFrameCountForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        long dvd = factory == null ? 0L : factory.getMpeg2InterlacedFrameCount();
        return dvd > 0L ? dvd : mpeg2InterlaceObserver.getInterlacedFrameCount();
    }

    public long getMpeg2FieldPictureCountForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        long dvd = factory == null ? 0L : factory.getMpeg2FieldPictureCount();
        return dvd > 0L ? dvd : mpeg2InterlaceObserver.getFieldPictureCount();
    }


    public boolean isDvdMpeg2TimestampRepairEnabledForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory != null && factory.isMpeg2PictureTimestampRepairEnabled();
    }

    public long getDvdDiscontinuityRebaseCountForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? 0L : factory.getDiscontinuityRebaseCount();
    }

    public String getDvdPtsTraceForDebug()
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        if (factory == null)
            return "";
        return "vRaw90=" + factory.getLatestVideoRawPts90Khz()
                + ",vOffset90=" + factory.getLatestVideoOffsetPts90Khz()
                + ",vUs=" + factory.getLatestVideoPesUs()
                + ",aRaw90=" + factory.getLatestAudioRawPts90Khz()
                + ",aOffset90=" + factory.getLatestAudioOffsetPts90Khz()
                + ",aUs=" + factory.getLatestAudioPesUs();
    }

    private String describeDvdTimestampDecision(long presentationTimeUs)
    {
        ResilientDvdPsExtractorsFactory factory = dvdExtractorsFactory;
        return factory == null ? "unavailable"
                : factory.describeVideoTimestampNear(presentationTimeUs);
    }

    public long getDvdFrameMetadataCountForDebug() { return dvdFrameMetadataCount; }
    public long getDvdLogicalClockBaseMsForDebug() { return dvdLogicalClockBaseMs; }
    public long getDvdRenderedVideoClockDeltaUsForDebug()
    {
        ExoPlayer currentPlayer = player;
        long presentationUs = dvdLastFramePresentationUs;
        if (currentPlayer == null || presentationUs == C.TIME_UNSET)
            return C.TIME_UNSET;
        return presentationUs - currentPlayer.getCurrentPosition() * 1_000L;
    }
    public long getDvdLastFramePresentationDeltaUsForDebug()
    {
        return dvdLastFramePresentationDeltaUs;
    }
    public long getDvdLastFrameReleaseDeltaUsForDebug() { return dvdLastFrameReleaseDeltaUs; }
    public long getDvdMaxFrameReleaseDeltaUsForDebug() { return dvdMaxFrameReleaseDeltaUs; }
    public long getDvdFrameReleaseGapCountForDebug() { return dvdFrameReleaseGapCount; }
    public long getDvdFrameReleaseNonPositiveCountForDebug()
    {
        return dvdFrameReleaseNonPositiveCount;
    }
    public long getDvdFrameReleaseUnder10MsCountForDebug()
    {
        return dvdFrameReleaseUnder10MsCount;
    }
    public long getDvdFrameRelease10To25MsCountForDebug()
    {
        return dvdFrameRelease10To25MsCount;
    }
    public long getDvdFrameRelease25To45MsCountForDebug()
    {
        return dvdFrameRelease25To45MsCount;
    }
    public long getDvdFrameRelease45To75MsCountForDebug()
    {
        return dvdFrameRelease45To75MsCount;
    }
    public long getDvdFrameRelease75To100MsCountForDebug()
    {
        return dvdFrameRelease75To100MsCount;
    }

    public String getDvdFrameCadenceTraceForDebug()
    {
        synchronized (dvdFramePresentationDeltaTraceUs)
        {
            StringBuilder out = new StringBuilder(dvdFrameCadenceTraceCount * 24);
            int start = (dvdFrameCadenceTraceIndex - dvdFrameCadenceTraceCount
                    + DVD_FRAME_CADENCE_TRACE_SIZE) % DVD_FRAME_CADENCE_TRACE_SIZE;
            for (int i = 0; i < dvdFrameCadenceTraceCount; i++)
            {
                if (i > 0)
                    out.append(',');
                int index = (start + i) % DVD_FRAME_CADENCE_TRACE_SIZE;
                out.append(dvdFramePresentationDeltaTraceUs[index]).append('/')
                        .append(dvdFrameReleaseDeltaTraceUs[index]);
            }
            return out.toString();
        }
    }

    public int getRequestedDvdAudioStreamForDebug() { return requestedDvdAudioStream; }
    public int getAppliedDvdAudioStreamForDebug() { return appliedDvdAudioStream; }

    @Override
    public int[] getAudioTrackIds()
    {
        ExoPlayer currentPlayer = player;
        if (currentPlayer == null) return new int[0];
        List<Integer> ids = new ArrayList<Integer>();
        int audioGroup = 0;
        for (Tracks.Group group : currentPlayer.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            Format format = group.getTrackFormat(0);
            ids.add(dvdPushMode ? parseAudioFormatId(format, audioGroup) : audioGroup);
            audioGroup++;
        }
        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) result[i] = ids.get(i);
        return result;
    }

    @Override
    public String[] getAudioTrackLabels()
    {
        ExoPlayer currentPlayer = player;
        if (currentPlayer == null) return new String[0];
        List<String> labels = new ArrayList<String>();
        int audioGroup = 0;
        for (Tracks.Group group : currentPlayer.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            Format format = group.getTrackFormat(0);
            StringBuilder label = new StringBuilder();
            label.append(audioGroup + 1);
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
        ExoPlayer currentPlayer = player;
        if (currentPlayer == null) return -1;
        int audioGroup = 0;
        for (Tracks.Group group : currentPlayer.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++)
                if (group.isTrackSelected(i))
                    return dvdPushMode
                            ? parseAudioFormatId(group.getTrackFormat(i), audioGroup)
                            : audioGroup;
            audioGroup++;
        }
        return -1;
    }

    private static int parseAudioFormatId(Format format, int fallback)
    {
        if (format != null && format.id != null)
        {
            try { return Integer.parseInt(format.id); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    @Override
    public String getAudioOutputSummary()
    {
        ExoPlayer currentPlayer = player;
        Format format = currentPlayer == null ? null : currentPlayer.getAudioFormat();
        if (format == null) return "not resolved";
        String mime = format.sampleMimeType == null ? "audio" : format.sampleMimeType;
        return "stream format " + mime + "; output mode is selected by Android AudioSink"
                + (format.channelCount > 0
                ? ", " + format.channelCount + "ch" : "");
    }

    public String getSelectedDvdAudioFormatIdForDebug()
    {
        ExoPlayer currentPlayer = player;
        if (currentPlayer == null)
            return "";
        for (Tracks.Group group : currentPlayer.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO)
                continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++)
            {
                if (group.isTrackSelected(trackIndex))
                {
                    String id = group.getTrackFormat(trackIndex).id;
                    return id == null ? "" : id;
                }
            }
        }
        return "";
    }

    public String getAvailableDvdAudioFormatIdsForDebug()
    {
        ExoPlayer currentPlayer = player;
        if (currentPlayer == null)
            return "";
        StringBuilder ids = new StringBuilder();
        for (Tracks.Group group : currentPlayer.getCurrentTracks().getGroups())
        {
            if (group.getType() != C.TRACK_TYPE_AUDIO)
                continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++)
            {
                if (ids.length() > 0)
                    ids.append('|');
                String id = group.getTrackFormat(trackIndex).id;
                ids.append(id == null ? "" : id);
            }
        }
        return ids.toString();
    }

    public long getSubtitleNonEmptyCueCountForDebug()
    {
        return subtitleNonEmptyCueCount;
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

    /**
     * Media3 can remain BUFFERING after consuming a completed MPEG-TS tail,
     * even though the Pull source has confirmed a stable EOF. Report SageTV
     * EOS instead of leaving the server and UI stuck indefinitely.
     */
    private void promoteConfirmedPullTailToEos()
    {
        if (eos || pushMode || !(dataSource instanceof Media3PullDataSource)
                || player == null || player.getPlaybackState() != Player.STATE_BUFFERING
                || player.isLoading() || !((Media3PullDataSource) dataSource).hasReachedEndOfInput())
        {
            return;
        }

        long durationMs = player.getDuration();
        long positionMs = player.getCurrentPosition();
        if (durationMs <= 0 || positionMs < Math.max(0, durationMs - 5000))
        {
            return;
        }

        cancelPullSeekRecovery();
        seekPending = false;
        eos = true;
        state = EOS_STATE;
        player.pause();
        PlaybackDebugTrap.record("pull_confirmed_eof_promoted", Media3MediaPlayerImpl.this);
        log.logDebug("Promoted confirmed Pull tail to SageTV EOS at " + positionMs + "ms of " + durationMs + "ms");
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

                    if (groupIndex == Media3MediaPlayerImpl.DISABLE_TRACK) //Disable trackType from rendering
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
                        if (groupIndex < 0 || groupIndex >= trackGroup.length)
                        {
                            log.logWarning("Track group is unavailable: TrackType=" + trackType
                                    + " GroupIndex=" + groupIndex + " GroupCount=" + trackGroup.length);
                            return;
                        }
                        TrackGroup selectedGroup = trackGroup.get(groupIndex);
                        if (trackIndex < 0 || trackIndex >= selectedGroup.length)
                        {
                            log.logWarning("Track index is unavailable: TrackType=" + trackType
                                    + " GroupIndex=" + groupIndex + " TrackIndex=" + trackIndex
                                    + " TrackCount=" + selectedGroup.length);
                            return;
                        }
                        if (trackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex) == C.FORMAT_HANDLED)
                        {
                            //Clear set new track selection
                            parametersBuilder = trackSelector.buildUponParameters();

                            //override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
                            override = new TrackSelectionOverride(selectedGroup, trackIndex);

                            // Disabling captions turns off both the text track type and
                            // its renderer. Re-enable both here; otherwise an Off -> CC1
                            // transition records the requested track as selected while
                            // the still-disabled renderer emits no cues until playback is
                            // recreated.
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
        {
            return count;
        }

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
            return new SubtitleTrack[0];
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null)
        {
            return new SubtitleTrack[0];
        }

        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_TEXT);
        if (rendererIndex == C.INDEX_UNSET)
        {
            return new SubtitleTrack[0];
        }

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

                SubtitleTrack track = new SubtitleTrack(i, codec, langugae, label, supported,
                        accessibilityChannel);
                tracks[i] = track;
            }
        }

        return tracks;
    }

    private int findRendererIndex(MappingTrackSelector.MappedTrackInfo trackInfo, int trackType)
    {
        if (trackInfo == null)
        {
            return C.INDEX_UNSET;
        }

        for (int rendererIndex = 0; rendererIndex < trackInfo.getRendererCount(); rendererIndex++)
        {
            if (trackInfo.getRendererType(rendererIndex) == trackType)
            {
                return rendererIndex;
            }
        }

        return C.INDEX_UNSET;
    }

    public void debugAvailableTracks()
    {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

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
                switch (player.getRendererType(i))
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


                        if (player.getRendererType(i) == C.TRACK_TYPE_TEXT)
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


                        if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO)
                        {
                            log.logDebug("\t\tChannel: " + format.channelCount);
                            log.logDebug("\t\tBitrate: " + format.bitrate);
                            log.logDebug("\t\tAverageBitrate: " + format.averageBitrate);
                            log.logDebug("\t\tPeakBitrate: " + format.peakBitrate);
                            log.logDebug("\t\tPCM Encoding: " + format.pcmEncoding);

                        }

                        if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO)
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


                        if (mappedTrackInfo.getTrackSupport(i, j, k) == C.FORMAT_HANDLED)
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
            // Media3's default bottom padding is intended for a standalone
            // player controller and sits too low when SageTV draws its own UI.
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
            view.setStyle(new androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK, android.graphics.Typeface.DEFAULT));
        }
        else if (TextSubtitlePresentation.STYLE_BLACK_BOX.equals(subtitleTextStyle))
        {
            view.setStyle(new androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, android.graphics.Color.BLACK,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
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

    private void setMediaSessionMetadata(String displayTitle, long duration)
    {
        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder();

        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle);
        metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        mediaSession.setMetadata(metaDataBuilder.build());
    }
}
