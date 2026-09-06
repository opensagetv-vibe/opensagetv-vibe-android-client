package opensagetv.vibe.miniclient.android.tv.debug;

import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.video.AndroidCodecPolicy;
import opensagetv.vibe.miniclient.android.video.PlaybackDataSourceTelemetry;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSource;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSnapshot;

/**
 * Debug-build-only, on-demand inspection of the actual playback/output path.
 *
 * No listeners are installed and no continuous telemetry runs.  MCP asks for a snapshot,
 * this class briefly inspects the active backend on the player's application/main thread,
 * and then returns.  Exo2/Media3 renderer counters and the Android AudioTrack playback head
 * provide stronger evidence than the SageTV timeline that video/audio are really moving.
 */
final class PlaybackHealthProbe
{
    private static final int TRACK_TYPE_AUDIO = 1;
    private static final int TRACK_TYPE_VIDEO = 2;
    private static final int EXO_STATE_BUFFERING = 2;
    private static final int EXO_STATE_READY = 3;
    private static final long MAIN_THREAD_TIMEOUT_MS = 2500;

    private PlaybackHealthProbe()
    {
    }

    static Snapshot capture(final MiniPlayerPlugin topLevelPlayer)
    {
        if (Looper.myLooper() == Looper.getMainLooper())
            return captureOnMainThread(topLevelPlayer);

        final AtomicReference<Snapshot> result = new AtomicReference<Snapshot>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    result.set(captureOnMainThread(topLevelPlayer));
                }
                catch (Throwable t)
                {
                    error.set(t);
                }
                finally
                {
                    latch.countDown();
                }
            }
        });

        try
        {
            if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                return Snapshot.unsupported("main_thread_timeout");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return Snapshot.unsupported("main_thread_interrupted");
        }

        if (error.get() != null)
            return Snapshot.unsupported("probe_exception_" + safe(error.get().getClass().getSimpleName()));
        Snapshot snapshot = result.get();
        return snapshot == null ? Snapshot.unsupported("probe_no_result") : snapshot;
    }

    private static Snapshot captureOnMainThread(MiniPlayerPlugin topLevelPlayer)
    {
        Snapshot out = new Snapshot();
        if (topLevelPlayer == null)
        {
            out.reason = "no_player";
            return out;
        }

        out.topLevelPlayerClass = topLevelPlayer.getClass().getName();
        out.miniState = topLevelPlayer.getState();
        out.bufferLeft = topLevelPlayer.getBufferLeft();
        out.lastFileReadPos = topLevelPlayer.getLastFileReadPos();

        Object effective = unwrapDelegate(topLevelPlayer);
        if (effective == null)
        {
            out.reason = "no_delegate";
            return out;
        }
        out.backendClass = effective.getClass().getName();
        Object dataSource;
        Object backendPlayer;
        if (effective instanceof PlaybackHealthSource)
        {
            PlaybackHealthSnapshot health =
                    ((PlaybackHealthSource) effective).capturePlaybackHealthSnapshot();
            out.pushMode = health.isPushMode();
            out.playerReady = health.isPlayerReady();
            out.seekPending = health.isSeekPending();
            out.flushed = health.isFlushed();
            out.errorState = health.isErrorState();
            out.retryCount = health.getRetryCount();
            dataSource = health.getDataSource();
            backendPlayer = health.getBackendPlayer();
        }
        else
        {
            // Compatibility fallback for IJK/System until each backend adopts
            // the typed on-demand snapshot contract.
            out.pushMode = readBooleanField(effective, "pushMode", false);
            out.playerReady = readBooleanField(effective, "playerReady", false);
            out.seekPending = readBooleanField(effective, "seekPending", false);
            out.flushed = readBooleanField(effective, "flushed", false);
            out.errorState = readBooleanField(effective, "errorState", false);
            out.retryCount = readIntField(effective, "retryCount", -1);
            dataSource = readField(effective, "dataSource");
            backendPlayer = invokeOptional(effective, "getPlayer");
        }

        if (dataSource != null)
        {
            out.dataSourceClass = dataSource.getClass().getName();
            PlaybackDataSourceTelemetry typedDataSource =
                    dataSource instanceof PlaybackDataSourceTelemetry
                            ? (PlaybackDataSourceTelemetry) dataSource : null;
            if (typedDataSource != null)
                capturePullDataSource(typedDataSource, out);
            else
                captureLegacyDataSource(dataSource, out);
            out.dataSourceReadCount = invokeLongOptional(dataSource, "getReadCount", -1);
            out.dataSourceReadRequestedBytes = invokeLongOptional(dataSource, "getReadRequestedBytes", -1);
            out.dataSourceReadBytes = invokeLongOptional(dataSource, "getBytesRead", -1);
            out.dataSourceReadWaitMs = invokeLongOptional(dataSource, "getReadWaitMs", -1);
            out.dataSourceReadRateKbps = invokeLongOptional(dataSource, "getReadRateKbps", -1);
            out.dataSourcePushCount = invokeLongOptional(dataSource, "getPushCount", -1);
            out.dataSourcePushedBytes = invokeLongOptional(dataSource, "getPushedBytes", -1);
            if (typedDataSource != null)
                captureSmbDataSource(typedDataSource, out);
            else
                captureLegacySmbDataSource(dataSource, out);
        }
        // Push and Fixed/MIM datasources predate the Pull/SMB source telemetry
        // interface. The transport is nevertheless unambiguous from the player
        // contract, so do not report an active SageTV Push stream as UNKNOWN.
        if (out.pushMode && "UNKNOWN".equals(out.playbackSource))
            out.playbackSource = "SAGETV_PUSH";

        captureSurface(effective, out);
        out.mpeg2InterlaceObservation = invokeStringOptional(effective,
                "getMpeg2InterlaceObservationForDebug", "unknown");
        out.mpeg2SequenceExtensionSeen = invokeBooleanOptional(effective,
                "isMpeg2SequenceExtensionSeenForDebug", false);
        out.mpeg2ProgressiveFrameCount = invokeLongOptional(effective,
                "getMpeg2ProgressiveFrameCountForDebug", 0L);
        out.mpeg2InterlacedFrameCount = invokeLongOptional(effective,
                "getMpeg2InterlacedFrameCountForDebug", 0L);
        out.mpeg2FieldPictureCount = invokeLongOptional(effective,
                "getMpeg2FieldPictureCountForDebug", 0L);

        if (backendPlayer == null)
        {
            out.reason = "backend_player_null";
            return out;
        }
        out.backendPlayerClass = backendPlayer.getClass().getName();

        String className = out.backendPlayerClass;
        if (className.startsWith("com.google.android.exoplayer2.")
                || className.startsWith("androidx.media3.exoplayer."))
        {
            out.supported = true;
            out.provider = "exo_renderer_audio_track_reflection";
            captureExo(backendPlayer, out);
            return out;
        }

        // Useful fallback state for IJK/System while keeping strict output-health verdicts
        // limited to engines where we can see actual renderer/audio-output progress.
        out.provider = "basic_backend_reflection";
        out.basicIsPlaying = invokeBooleanOptional(backendPlayer, "isPlaying", false);
        out.playerPositionMs = invokeLongOptional(backendPlayer, "getCurrentPosition", -1);
        out.videoWidth = invokeIntOptional(backendPlayer, "getVideoWidth", out.videoWidth);
        out.videoHeight = invokeIntOptional(backendPlayer, "getVideoHeight", out.videoHeight);
        out.reason = "renderer_counters_not_available";
        return out;
    }

    private static void capturePullDataSource(PlaybackDataSourceTelemetry source, Snapshot out)
    {
        out.dataSourceOpenCount = source.getOpenCount();
        out.dataSourceOpenWaitMs = source.getOpenWaitMs();
        out.dataSourceLastOpenPosition = source.getLastOpenPosition();
        out.dataSourceLastOpenMonotonicMs = source.getLastSourceOpenMonotonicMs();
        out.dataSourceFirstReadAfterOpenMonotonicMs = source.getFirstReadAfterOpenMonotonicMs();
        out.dataSourceFirstReadAfterOpenPosition = source.getFirstReadAfterOpenPosition();
        out.dataSourceLastPhysicalReadMonotonicMs = source.getLastPhysicalReadMonotonicMs();
        out.dataSourceLastPhysicalReadPosition = source.getLastPhysicalReadPosition();
        out.dataSourceNetworkReadCount = source.getNetworkReadCount();
        out.dataSourceNetworkReadRequestedBytes = source.getNetworkReadRequestedBytes();
        out.dataSourceNetworkReadBytes = source.getNetworkReadBytes();
        out.dataSourceNetworkReadWaitMs = source.getNetworkReadWaitMs();
        out.dataSourceNetworkReadMaxRequestedBytes = source.getNetworkReadMaxRequestedBytes();
        out.dataSourceNetworkReadErrors = source.getNetworkReadErrors();
        out.dataSourceNetworkLastReadPosition = source.getNetworkLastReadPosition();
    }

    private static void captureLegacyDataSource(Object source, Snapshot out)
    {
        out.dataSourceOpenCount = invokeLongOptional(source, "getOpenCount", -1);
        out.dataSourceOpenWaitMs = invokeLongOptional(source, "getOpenWaitMs", -1);
        out.dataSourceLastOpenPosition = invokeLongOptional(source, "getLastOpenPosition", -1);
        out.dataSourceLastOpenMonotonicMs = invokeLongOptional(source, "getLastSourceOpenMonotonicMs", -1);
        out.dataSourceFirstReadAfterOpenMonotonicMs = invokeLongOptional(source, "getFirstReadAfterOpenMonotonicMs", -1);
        out.dataSourceFirstReadAfterOpenPosition = invokeLongOptional(source, "getFirstReadAfterOpenPosition", -1);
        out.dataSourceLastPhysicalReadMonotonicMs = invokeLongOptional(source, "getLastPhysicalReadMonotonicMs", -1);
        out.dataSourceLastPhysicalReadPosition = invokeLongOptional(source, "getLastPhysicalReadPosition", -1);
        out.dataSourceNetworkReadCount = invokeLongOptional(source, "getNetworkReadCount", -1);
        out.dataSourceNetworkReadRequestedBytes = invokeLongOptional(source, "getNetworkReadRequestedBytes", -1);
        out.dataSourceNetworkReadBytes = invokeLongOptional(source, "getNetworkReadBytes", -1);
        out.dataSourceNetworkReadWaitMs = invokeLongOptional(source, "getNetworkReadWaitMs", -1);
        out.dataSourceNetworkReadMaxRequestedBytes = invokeLongOptional(source, "getNetworkReadMaxRequestedBytes", -1);
        out.dataSourceNetworkReadErrors = invokeLongOptional(source, "getNetworkReadErrors", -1);
        out.dataSourceNetworkLastReadPosition = invokeLongOptional(source, "getNetworkLastReadPosition", -1);
    }

    private static void captureSmbDataSource(PlaybackDataSourceTelemetry source, Snapshot out)
    {
        out.playbackSource = source.getPlaybackSource();
        out.sageOriginalPath = source.getSageOriginalPath();
        out.smbMappedPath = source.getSmbMappedPath();
        out.smbConnected = source.isSmbConnected();
        out.shadowMediaServerConnected = source.isShadowConnected();
        out.shadowOpenSent = source.isShadowOpenSent();
        out.shadowSizeSent = source.isShadowSizeSent();
        out.shadowReadBytes = source.getShadowReadBytes();
        out.smbBytesRead = source.getSmbBytesRead();
        out.smbReadCount = source.getSmbReadCount();
        out.smbSeekCount = source.getSmbSeekCount();
        out.smbLastReadLatencyMs = source.getSmbLastReadLatencyMs();
        out.smbCacheHitBytes = source.getSmbCacheHitBytes();
        out.smbCacheMissCount = source.getSmbCacheMissCount();
        out.smbReadAheadBytes = source.getSmbReadAheadBytes();
        out.smbFallbackCount = source.getSmbFallbackCount();
        out.smbFallbackReason = source.getSmbFallbackReason();
    }

    private static void captureLegacySmbDataSource(Object source, Snapshot out)
    {
        out.playbackSource = invokeStringOptional(source, "getPlaybackSource", "UNKNOWN");
        out.sageOriginalPath = invokeStringOptional(source, "getSageOriginalPath", "");
        out.smbMappedPath = invokeStringOptional(source, "getSmbMappedPath", "");
        out.smbConnected = invokeBooleanOptional(source, "isSmbConnected", false);
        out.shadowMediaServerConnected = invokeBooleanOptional(source, "isShadowConnected", false);
        out.shadowOpenSent = invokeBooleanOptional(source, "isShadowOpenSent", false);
        out.shadowSizeSent = invokeBooleanOptional(source, "isShadowSizeSent", false);
        out.shadowReadBytes = invokeLongOptional(source, "getShadowReadBytes", -1);
        out.smbBytesRead = invokeLongOptional(source, "getSmbBytesRead", -1);
        out.smbReadCount = invokeLongOptional(source, "getSmbReadCount", -1);
        out.smbSeekCount = invokeLongOptional(source, "getSmbSeekCount", -1);
        out.smbLastReadLatencyMs = invokeLongOptional(source, "getSmbLastReadLatencyMs", -1);
        out.smbCacheHitBytes = invokeLongOptional(source, "getSmbCacheHitBytes", -1);
        out.smbCacheMissCount = invokeLongOptional(source, "getSmbCacheMissCount", -1);
        out.smbReadAheadBytes = invokeLongOptional(source, "getSmbReadAheadBytes", -1);
        out.smbFallbackCount = invokeLongOptional(source, "getSmbFallbackCount", -1);
        out.smbFallbackReason = invokeStringOptional(source, "getSmbFallbackReason", "");
    }

    private static Object unwrapDelegate(Object player)
    {
        Object current = player;
        for (int i = 0; i < 4 && current != null; i++)
        {
            Field delegateField = findField(current.getClass(), "delegate");
            if (delegateField == null)
                break;
            Object delegate = getField(delegateField, current);
            if (delegate == null)
                break;
            current = delegate;
        }
        return current;
    }

    private static void captureExo(Object exo, Snapshot out)
    {
        out.playbackState = invokeIntOptional(exo, "getPlaybackState", -1);
        out.playWhenReady = invokeBooleanOptional(exo, "getPlayWhenReady", false);
        out.isPlaying = invokeBooleanOptional(exo, "isPlaying", false);
        out.isLoading = invokeBooleanOptional(exo, "isLoading", false);
        out.playerPositionMs = invokeLongOptional(exo, "getCurrentPosition", -1);
        out.bufferedPositionMs = invokeLongOptional(exo, "getBufferedPosition", -1);
        out.durationMs = invokeLongOptional(exo, "getDuration", -1);
        Object playerError = invokeOptional(exo, "getPlayerError");
        if (playerError != null)
        {
            Object codeName = invokeOptional(playerError, "getErrorCodeName");
            out.playerError = codeName == null ? playerError.toString() : String.valueOf(codeName);
        }

        Object videoFormat = invokeOptional(exo, "getVideoFormat");
        Object audioFormat = invokeOptional(exo, "getAudioFormat");
        captureFormat(videoFormat, true, out);
        captureFormat(audioFormat, false, out);

        int rendererCount = invokeIntOptional(exo, "getRendererCount", 0);
        for (int i = 0; i < rendererCount; i++)
        {
            Object typeValue = invokeOptional(exo, "getRendererType", new Class<?>[] {int.class}, new Object[] {i});
            int type = typeValue instanceof Number ? ((Number) typeValue).intValue() : -1;
            Object renderer = invokeOptional(exo, "getRenderer", new Class<?>[] {int.class}, new Object[] {i});
            if (renderer == null)
                continue;

            if (type == TRACK_TYPE_VIDEO)
            {
                out.videoRendererPresent = true;
                captureRenderer(renderer, true, out);
            }
            else if (type == TRACK_TYPE_AUDIO)
            {
                out.audioRendererPresent = true;
                captureRenderer(renderer, false, out);
                captureAudioTrack(renderer, out);
            }
        }
    }

    private static void captureFormat(Object format, boolean video, Snapshot out)
    {
        if (format == null)
            return;
        String mime = readStringField(format, "sampleMimeType");
        String codecs = readStringField(format, "codecs");
        if (video)
        {
            out.videoMime = mime;
            out.videoCodecString = codecs;
            out.videoWidth = readIntField(format, "width", out.videoWidth);
            out.videoHeight = readIntField(format, "height", out.videoHeight);
        }
        else
        {
            out.audioMime = mime;
            out.audioCodecString = codecs;
            out.audioChannels = readIntField(format, "channelCount", -1);
            out.audioFormatSampleRate = readIntField(format, "sampleRate", -1);
        }
    }

    private static void captureRenderer(Object renderer, boolean video, Snapshot out)
    {
        Object codecInfo = readField(renderer, "codecInfo");
        String decoderName = codecInfo == null ? "" : readStringField(codecInfo, "name");
        Object counters = readField(renderer, "decoderCounters");
        if (counters != null)
            invokeOptional(counters, "ensureUpdated");

        long rendered = readLongField(counters, "renderedOutputBufferCount", -1);
        long skipped = readLongField(counters, "skippedOutputBufferCount", -1);
        long dropped = readLongField(counters, "droppedBufferCount", -1);
        long queued = readLongField(counters, "queuedInputBufferCount", -1);
        long initCount = readLongField(counters, "decoderInitCount", -1);
        long releaseCount = readLongField(counters, "decoderReleaseCount", -1);

        if (video)
        {
            out.videoDecoderName = decoderName;
            if (!decoderName.isEmpty())
                out.videoDecoderKind = AndroidCodecPolicy.isSoftwareCodecName(decoderName) ? "software" : "hardware";
            out.videoRendered = rendered;
            out.videoSkipped = skipped;
            out.videoDropped = dropped;
            out.videoQueuedInput = queued;
            out.videoDecoderInitCount = initCount;
            out.videoDecoderReleaseCount = releaseCount;
        }
        else
        {
            out.audioDecoderName = decoderName;
            if (!decoderName.isEmpty())
                out.audioDecoderKind = AndroidCodecPolicy.isSoftwareCodecName(decoderName) ? "software" : "hardware";
            out.audioRendered = rendered;
            out.audioSkipped = skipped;
            out.audioDropped = dropped;
            out.audioQueuedInput = queued;
            out.audioDecoderInitCount = initCount;
            out.audioDecoderReleaseCount = releaseCount;
        }
    }

    private static void captureAudioTrack(Object audioRenderer, Snapshot out)
    {
        Object audioSink = readField(audioRenderer, "audioSink");
        Object audioTrackObject = readField(audioSink, "audioTrack");
        if (!(audioTrackObject instanceof AudioTrack))
            return;

        AudioTrack audioTrack = (AudioTrack) audioTrackObject;
        out.audioTrackPresent = true;
        out.audioTrackState = audioTrack.getState();
        out.audioTrackPlayState = audioTrack.getPlayState();
        out.audioTrackSampleRate = audioTrack.getSampleRate();
        out.audioSessionId = audioTrack.getAudioSessionId();
        out.audioPlaybackHeadFrames = ((long) audioTrack.getPlaybackHeadPosition()) & 0xffffffffL;
    }

    private static void captureSurface(Object effective, Snapshot out)
    {
        Object uiController = readField(effective, "context");
        Object viewObject = invokeOptional(uiController, "getVideoView");
        if (!(viewObject instanceof View))
            return;

        View view = (View) viewObject;
        out.surfaceKnown = true;
        out.surfaceShown = view.isShown();
        out.surfaceWidth = view.getWidth();
        out.surfaceHeight = view.getHeight();
        if (view instanceof SurfaceView)
        {
            Surface surface = ((SurfaceView) view).getHolder().getSurface();
            out.surfaceValid = surface != null && surface.isValid();
        }
        else
        {
            // Non-SurfaceView backends can still provide useful visibility/dimension state.
            out.surfaceValid = view.getWindowToken() != null;
        }
    }

    static boolean counterAdvanced(long before, long after)
    {
        if (before < 0 || after < 0)
            return false;
        if (after > before)
            return true;
        // Decoder reinitialization can replace/reset DecoderCounters during a seek.
        return after < before && after > 0;
    }

    static boolean audioHeadAdvanced(Snapshot before, Snapshot after)
    {
        if (!before.audioTrackPresent || !after.audioTrackPresent)
            return false;
        if (before.audioSessionId != after.audioSessionId)
            return after.audioTrackPlayState == AudioTrack.PLAYSTATE_PLAYING && after.audioPlaybackHeadFrames > 0;

        // AudioTrack.flush()/seek can reset the playback-head counter to a smaller value while
        // reusing the same AudioTrack/session, especially for encoded AC-3 passthrough. Treat a
        // live non-zero post-reset head as output progress instead of waiting for it to climb past
        // the pre-seek counter (which can take minutes and caused false REW failures).
        if (audioHeadResetDetected(before, after))
            return true;

        long delta = (after.audioPlaybackHeadFrames - before.audioPlaybackHeadFrames) & 0xffffffffL;
        return delta > 0 && delta < 0x80000000L;
    }

    static boolean audioHeadResetDetected(Snapshot before, Snapshot after)
    {
        if (before == null || after == null || !before.audioTrackPresent || !after.audioTrackPresent)
            return false;
        if (before.audioSessionId != after.audioSessionId)
            return false;
        if (after.audioTrackPlayState != AudioTrack.PLAYSTATE_PLAYING || after.audioPlaybackHeadFrames <= 0)
            return false;
        if (after.audioPlaybackHeadFrames >= before.audioPlaybackHeadFrames)
            return false;

        long unsignedDelta = (after.audioPlaybackHeadFrames - before.audioPlaybackHeadFrames) & 0xffffffffL;
        // A genuine 32-bit playback-head wrap produces a small forward unsigned delta. A reset
        // produces the very large delta below, so report it explicitly for MCP diagnostics.
        return unsignedDelta >= 0x80000000L;
    }

    static boolean videoOutputAdvanced(Snapshot before, Snapshot after)
    {
        return counterAdvanced(before.videoRendered, after.videoRendered);
    }

    static boolean audioOutputAdvanced(Snapshot before, Snapshot after)
    {
        return audioHeadAdvanced(before, after) || counterAdvanced(before.audioRendered, after.audioRendered);
    }

    static boolean readyAndPlaying(Snapshot snapshot)
    {
        if (snapshot == null)
            return false;
        if (snapshot.miniState != MiniPlayerPlugin.PLAY_STATE)
            return false;
        if (!snapshot.supported)
            return snapshot.basicIsPlaying;
        return snapshot.playbackState == EXO_STATE_READY
                && snapshot.playWhenReady
                && snapshot.isPlaying
                && snapshot.playerError.isEmpty();
    }

    static boolean surfaceHealthy(Snapshot snapshot)
    {
        return snapshot != null && (!snapshot.expectsVideo() || !snapshot.surfaceKnown || snapshot.surfaceValid);
    }

    static boolean isBuffering(Snapshot snapshot)
    {
        return snapshot != null && snapshot.supported
                && snapshot.playbackState == EXO_STATE_BUFFERING;
    }

    static final class Snapshot
    {
        final long capturedMonotonicMs = SystemClock.elapsedRealtime();
        boolean supported;
        String reason = "";
        String provider = "";
        String topLevelPlayerClass = "";
        String backendClass = "";
        String backendPlayerClass = "";
        String dataSourceClass = "";
        long dataSourceOpenCount = -1;
        long dataSourceOpenWaitMs = -1;
        long dataSourceLastOpenPosition = -1;
        long dataSourceNetworkReadCount = -1;
        long dataSourceNetworkReadRequestedBytes = -1;
        long dataSourceNetworkReadBytes = -1;
        long dataSourceNetworkReadWaitMs = -1;
        long dataSourceNetworkReadMaxRequestedBytes = -1;
        long dataSourceNetworkReadErrors = -1;
        long dataSourceNetworkLastReadPosition = -1;
        long dataSourceLastOpenMonotonicMs = -1;
        long dataSourceFirstReadAfterOpenMonotonicMs = -1;
        long dataSourceFirstReadAfterOpenPosition = -1;
        long dataSourceLastPhysicalReadMonotonicMs = -1;
        long dataSourceLastPhysicalReadPosition = -1;
        long dataSourceReadCount = -1;
        long dataSourceReadRequestedBytes = -1;
        long dataSourceReadBytes = -1;
        long dataSourceReadWaitMs = -1;
        long dataSourceReadRateKbps = -1;
        long dataSourcePushCount = -1;
        long dataSourcePushedBytes = -1;
        String playbackSource = "UNKNOWN";
        String sageOriginalPath = "";
        String smbMappedPath = "";
        boolean smbConnected;
        boolean shadowMediaServerConnected;
        boolean shadowOpenSent;
        boolean shadowSizeSent;
        long shadowReadBytes = -1;
        long smbBytesRead = -1;
        long smbReadCount = -1;
        long smbSeekCount = -1;
        long smbLastReadLatencyMs = -1;
        long smbCacheHitBytes = -1;
        long smbCacheMissCount = -1;
        long smbReadAheadBytes = -1;
        long smbFallbackCount = -1;
        String smbFallbackReason = "";
        int miniState = -1;
        int bufferLeft = -1;
        long lastFileReadPos = -1;
        boolean pushMode;
        boolean playerReady;
        boolean seekPending;
        boolean flushed;
        boolean errorState;
        int retryCount = -1;

        int playbackState = -1;
        boolean playWhenReady;
        boolean isPlaying;
        boolean isLoading;
        boolean basicIsPlaying;
        long playerPositionMs = -1;
        long bufferedPositionMs = -1;
        long durationMs = -1;
        String playerError = "";

        boolean videoRendererPresent;
        String videoMime = "";
        String videoCodecString = "";
        String videoDecoderName = "";
        String videoDecoderKind = "unknown";
        int videoWidth = -1;
        int videoHeight = -1;
        long videoRendered = -1;
        long videoSkipped = -1;
        long videoDropped = -1;
        long videoQueuedInput = -1;
        long videoDecoderInitCount = -1;
        long videoDecoderReleaseCount = -1;
        String mpeg2InterlaceObservation = "unknown";
        boolean mpeg2SequenceExtensionSeen;
        long mpeg2ProgressiveFrameCount;
        long mpeg2InterlacedFrameCount;
        long mpeg2FieldPictureCount;

        boolean audioRendererPresent;
        String audioMime = "";
        String audioCodecString = "";
        String audioDecoderName = "";
        String audioDecoderKind = "unknown";
        int audioChannels = -1;
        int audioFormatSampleRate = -1;
        long audioRendered = -1;
        long audioSkipped = -1;
        long audioDropped = -1;
        long audioQueuedInput = -1;
        long audioDecoderInitCount = -1;
        long audioDecoderReleaseCount = -1;
        boolean audioTrackPresent;
        int audioTrackState = -1;
        int audioTrackPlayState = -1;
        int audioTrackSampleRate = -1;
        int audioSessionId = -1;
        long audioPlaybackHeadFrames = -1;

        boolean surfaceKnown;
        boolean surfaceValid;
        boolean surfaceShown;
        int surfaceWidth = -1;
        int surfaceHeight = -1;

        static Snapshot unsupported(String reason)
        {
            Snapshot out = new Snapshot();
            out.reason = reason;
            return out;
        }

        boolean expectsVideo()
        {
            return videoMime.startsWith("video/") || (videoRendererPresent && videoWidth > 0 && videoHeight > 0);
        }

        boolean expectsAudio()
        {
            return audioMime.startsWith("audio/") || audioTrackPresent;
        }

        String compactWire(String prefix)
        {
            String p = prefix == null ? "" : prefix;
            StringBuilder out = new StringBuilder();
            append(out, p + "capturedMonotonicMs", capturedMonotonicMs);
            append(out, p + "probeSupported", supported);
            append(out, p + "probeProvider", provider);
            append(out, p + "probeReason", reason);
            append(out, p + "topLevelPlayerClass", topLevelPlayerClass);
            append(out, p + "backendClass", backendClass);
            append(out, p + "backendPlayerClass", backendPlayerClass);
            append(out, p + "dataSourceClass", dataSourceClass);
            append(out, p + "dataSourceOpenCount", dataSourceOpenCount);
            append(out, p + "dataSourceOpenWaitMs", dataSourceOpenWaitMs);
            append(out, p + "dataSourceLastOpenPosition", dataSourceLastOpenPosition);
            append(out, p + "dataSourceLastOpenMonotonicMs", dataSourceLastOpenMonotonicMs);
            append(out, p + "dataSourceFirstReadAfterOpenMonotonicMs", dataSourceFirstReadAfterOpenMonotonicMs);
            append(out, p + "dataSourceFirstReadAfterOpenPosition", dataSourceFirstReadAfterOpenPosition);
            append(out, p + "dataSourceLastPhysicalReadMonotonicMs", dataSourceLastPhysicalReadMonotonicMs);
            append(out, p + "dataSourceLastPhysicalReadPosition", dataSourceLastPhysicalReadPosition);
            append(out, p + "dataSourceNetworkReadCount", dataSourceNetworkReadCount);
            append(out, p + "dataSourceNetworkReadRequestedBytes", dataSourceNetworkReadRequestedBytes);
            append(out, p + "dataSourceNetworkReadBytes", dataSourceNetworkReadBytes);
            append(out, p + "dataSourceNetworkReadWaitMs", dataSourceNetworkReadWaitMs);
            append(out, p + "dataSourceNetworkReadMaxRequestedBytes", dataSourceNetworkReadMaxRequestedBytes);
            append(out, p + "dataSourceNetworkReadErrors", dataSourceNetworkReadErrors);
            append(out, p + "dataSourceNetworkLastReadPosition", dataSourceNetworkLastReadPosition);
            append(out, p + "dataSourceReadCount", dataSourceReadCount);
            append(out, p + "dataSourceReadRequestedBytes", dataSourceReadRequestedBytes);
            append(out, p + "dataSourceReadBytes", dataSourceReadBytes);
            append(out, p + "dataSourceReadWaitMs", dataSourceReadWaitMs);
            append(out, p + "dataSourceReadRateKbps", dataSourceReadRateKbps);
            append(out, p + "dataSourcePushCount", dataSourcePushCount);
            append(out, p + "dataSourcePushedBytes", dataSourcePushedBytes);
            append(out, p + "playbackSource", playbackSource);
            append(out, p + "sageOriginalPath", sageOriginalPath);
            append(out, p + "smbMappedPath", smbMappedPath);
            append(out, p + "smbConnected", smbConnected);
            append(out, p + "shadowMediaServerConnected", shadowMediaServerConnected);
            append(out, p + "shadowOpenSent", shadowOpenSent);
            append(out, p + "shadowSizeSent", shadowSizeSent);
            append(out, p + "shadowReadBytes", shadowReadBytes);
            append(out, p + "smbBytesRead", smbBytesRead);
            append(out, p + "smbReadCount", smbReadCount);
            append(out, p + "smbSeekCount", smbSeekCount);
            append(out, p + "smbLastReadLatencyMs", smbLastReadLatencyMs);
            append(out, p + "smbCacheHitBytes", smbCacheHitBytes);
            append(out, p + "smbCacheMissCount", smbCacheMissCount);
            append(out, p + "smbReadAheadBytes", smbReadAheadBytes);
            append(out, p + "smbFallbackCount", smbFallbackCount);
            append(out, p + "smbFallbackReason", smbFallbackReason);
            append(out, p + "miniState", miniState);
            append(out, p + "bufferLeft", bufferLeft);
            append(out, p + "lastFileReadPos", lastFileReadPos);
            append(out, p + "pushMode", pushMode);
            append(out, p + "playerReady", playerReady);
            append(out, p + "seekPending", seekPending);
            append(out, p + "flushed", flushed);
            append(out, p + "errorState", errorState);
            append(out, p + "retryCount", retryCount);
            append(out, p + "playbackState", playbackState);
            append(out, p + "playWhenReady", playWhenReady);
            append(out, p + "isPlaying", isPlaying);
            append(out, p + "isLoading", isLoading);
            append(out, p + "basicIsPlaying", basicIsPlaying);
            append(out, p + "playerPositionMs", playerPositionMs);
            append(out, p + "bufferedPositionMs", bufferedPositionMs);
            append(out, p + "durationMs", durationMs);
            append(out, p + "playerError", playerError);
            append(out, p + "videoMime", videoMime);
            append(out, p + "videoCodecString", videoCodecString);
            append(out, p + "videoDecoder", videoDecoderName);
            append(out, p + "videoDecoderKind", videoDecoderKind);
            append(out, p + "videoWidth", videoWidth);
            append(out, p + "videoHeight", videoHeight);
            append(out, p + "videoRendered", videoRendered);
            append(out, p + "videoSkipped", videoSkipped);
            append(out, p + "videoDropped", videoDropped);
            append(out, p + "videoQueuedInput", videoQueuedInput);
            append(out, p + "videoDecoderInitCount", videoDecoderInitCount);
            append(out, p + "videoDecoderReleaseCount", videoDecoderReleaseCount);
            append(out, p + "mpeg2InterlaceObservation", mpeg2InterlaceObservation);
            append(out, p + "mpeg2SequenceExtensionSeen", mpeg2SequenceExtensionSeen);
            append(out, p + "mpeg2ProgressiveFrameCount", mpeg2ProgressiveFrameCount);
            append(out, p + "mpeg2InterlacedFrameCount", mpeg2InterlacedFrameCount);
            append(out, p + "mpeg2FieldPictureCount", mpeg2FieldPictureCount);
            append(out, p + "audioMime", audioMime);
            append(out, p + "audioCodecString", audioCodecString);
            append(out, p + "audioDecoder", audioDecoderName);
            append(out, p + "audioDecoderKind", audioDecoderKind);
            append(out, p + "audioChannels", audioChannels);
            append(out, p + "audioFormatSampleRate", audioFormatSampleRate);
            append(out, p + "audioRendered", audioRendered);
            append(out, p + "audioSkipped", audioSkipped);
            append(out, p + "audioDropped", audioDropped);
            append(out, p + "audioQueuedInput", audioQueuedInput);
            append(out, p + "audioDecoderInitCount", audioDecoderInitCount);
            append(out, p + "audioDecoderReleaseCount", audioDecoderReleaseCount);
            append(out, p + "audioTrackPresent", audioTrackPresent);
            append(out, p + "audioTrackState", audioTrackState);
            append(out, p + "audioTrackPlayState", audioTrackPlayState);
            append(out, p + "audioTrackSampleRate", audioTrackSampleRate);
            append(out, p + "audioSessionId", audioSessionId);
            append(out, p + "audioPlaybackHeadFrames", audioPlaybackHeadFrames);
            append(out, p + "surfaceKnown", surfaceKnown);
            append(out, p + "surfaceValid", surfaceValid);
            append(out, p + "surfaceShown", surfaceShown);
            append(out, p + "surfaceWidth", surfaceWidth);
            append(out, p + "surfaceHeight", surfaceHeight);
            return out.toString();
        }

        private static void append(StringBuilder out, String key, Object value)
        {
            out.append(';').append(key).append('=').append(safe(String.valueOf(value)));
        }
    }

    private static Field findField(Class<?> type, String name)
    {
        Class<?> current = type;
        while (current != null)
        {
            try
            {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                current = current.getSuperclass();
            }
            catch (Throwable ignored)
            {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String name)
    {
        if (target == null)
            return null;
        Field field = findField(target.getClass(), name);
        return field == null ? null : getField(field, target);
    }

    private static Object getField(Field field, Object target)
    {
        try
        {
            return field.get(target);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static boolean readBooleanField(Object target, String name, boolean defaultValue)
    {
        Object value = readField(target, name);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static int readIntField(Object target, String name, int defaultValue)
    {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static long readLongField(Object target, String name, long defaultValue)
    {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private static String readStringField(Object target, String name)
    {
        Object value = readField(target, name);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object invokeOptional(Object target, String methodName)
    {
        return invokeOptional(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invokeOptional(Object target, String methodName, Class<?>[] parameterTypes, Object[] args)
    {
        if (target == null)
            return null;
        try
        {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static int invokeIntOptional(Object target, String name, int defaultValue)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static long invokeLongOptional(Object target, String name, long defaultValue)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private static boolean invokeBooleanOptional(Object target, String name, boolean defaultValue)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static String invokeStringOptional(Object target, String name, String defaultValue)
    {
        Object value = invokeOptional(target, name);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String safe(String value)
    {
        if (value == null)
            return "";
        return value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }
}
