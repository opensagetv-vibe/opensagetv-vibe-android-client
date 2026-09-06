package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.video.AndroidCodecPolicy;
import opensagetv.vibe.miniclient.android.video.DisplayRefreshController;
import opensagetv.vibe.miniclient.android.video.PlaybackDataSourceTelemetry;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSnapshot;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSource;

/**
 * One inexpensive, on-demand snapshot for the user-visible playback statistics panel.
 * It installs no listeners and contains no media path, credentials, server address, or
 * client identifier. Optional sections are formatted only when their source is active.
 */
final class ActivePlayerStatsSnapshot
{
    private static final int TRACK_TYPE_AUDIO = 1;
    private static final int TRACK_TYPE_VIDEO = 2;

    final long capturedMs = SystemClock.elapsedRealtime();
    String backend = "None";
    String transport = "None";
    String state = "Idle";
    boolean active;
    boolean loading;
    boolean ready;
    boolean seekPending;
    boolean error;
    int retryCount;

    long positionMs = -1L;
    long durationMs = -1L;
    long bufferedAheadMs = -1L;
    long bufferBytes = -1L;
    long mediaBytes = -1L;
    long readCount = -1L;
    long readWaitMs = -1L;
    long readErrors = -1L;
    long reportedActivityKbps = -1L;
    long connectionCapacityKbps = -1L;
    boolean connectionCapacityFromServer;
    float deviceCpuPercent = -1f;
    float appCpuPercent = -1f;

    String videoMime = "";
    String videoCodec = "";
    String videoDecoder = "";
    String videoDecoderKind = "";
    int videoWidth = -1;
    int videoHeight = -1;
    float contentFps = -1f;
    String interlace = "";
    long videoRendered = -1L;
    long videoSkipped = -1L;
    long videoDropped = -1L;

    String audioMime = "";
    String audioCodec = "";
    String audioDecoder = "";
    int audioChannels = -1;
    int audioSampleRate = -1;
    long audioDropped = -1L;
    String audioOutput = "";

    int surfaceWidth = -1;
    int surfaceHeight = -1;
    boolean surfaceValid;
    float displayHz = -1f;
    int displayModeId;
    String refreshReason = "";

    float playbackRate = 1.0f;
    int subtitleTrack = MiniPlayerPlugin.DISABLE_TRACK;
    int subtitleOffsetMs;
    int audioOffsetMs;

    boolean smb;
    boolean smbConnected;
    boolean shadowConnected;
    long smbBytes = -1L;
    long smbReads = -1L;
    long smbSeeks = -1L;
    long smbLatencyMs = -1L;
    long smbCacheHitBytes = -1L;
    long smbCacheMisses = -1L;
    long smbReadAheadBytes = -1L;
    long shadowReadBytes = -1L;
    long smbFallbacks = -1L;
    String smbFallbackReason = "";

    boolean push;
    int serverChannelKbps;
    int serverStreamKbps;
    int serverTargetKbps;
    long serverMuxMs = -1L;
    long serverBufferMs = -1L;

    boolean dvd;
    long dvdPushedBytes;
    long dvdEpochBytes;
    long dvdCorrections;
    long dvdFrameGaps;
    long dvdNonPositiveFrames;
    long dvdTransientEos;
    long dvdAvDeltaUs = Long.MIN_VALUE;
    int dvdAudioStream = -1;
    int dvdSubtitleStream = -1;
    boolean dvdMimFallback;
    boolean dvdTimestampRepair;

    String playerError = "";

    private ActivePlayerStatsSnapshot() { }

    static ActivePlayerStatsSnapshot capture(Activity activity, MiniClient client, MediaCmd media)
    {
        ActivePlayerStatsSnapshot out = new ActivePlayerStatsSnapshot();
        MiniPlayerPlugin top = media == null ? null : media.getPlaya();
        if (top == null)
        {
            out.connectionCapacityKbps = linkCapacityKbps(activity);
            return out;
        }

        out.active = true;
        out.backend = friendlyBackend(top.getClass().getSimpleName());
        out.state = miniState(top.getState());
        out.positionMs = top.getMediaTimeMillis(media.getLastServerStartPosition());
        out.bufferedAheadMs = top.getBufferedPlaybackAheadMillis();
        out.bufferBytes = top.getBufferLeft();
        out.playbackRate = top.getPlaybackRate();
        out.videoWidth = top.getVideoDimensions() == null ? -1 : top.getVideoDimensions().width;
        out.videoHeight = top.getVideoDimensions() == null ? -1 : top.getVideoDimensions().height;
        out.contentFps = top.getContentFrameRateHz();
        out.audioOutput = clean(top.getAudioOutputSummary());
        out.subtitleTrack = top.getSelectedSubtitleTrack();
        out.subtitleOffsetMs = top.getSubtitleOffsetMillis();
        out.audioOffsetMs = top.getAudioOffsetMillis();

        Object effective = unwrapDelegate(top);
        Object backendPlayer = invokeOptional(effective, "getPlayer");
        Object dataSource = readField(effective, "dataSource");
        if (effective instanceof PlaybackHealthSource)
        {
            PlaybackHealthSnapshot health =
                    ((PlaybackHealthSource) effective).capturePlaybackHealthSnapshot();
            backendPlayer = health.getBackendPlayer();
            dataSource = health.getDataSource();
            out.push = health.isPushMode();
            out.ready = health.isPlayerReady();
            out.seekPending = health.isSeekPending();
            out.error = health.isErrorState();
            out.retryCount = health.getRetryCount();
        }

        capturePlayer(backendPlayer, out);
        captureDataSource(dataSource, out);
        captureSurface(effective, out);
        out.interlace = invokeStringOptional(effective,
                "getMpeg2InterlaceObservationForDebug", "");

        out.dvd = media.isDvdSessionPending();
        String configured = client == null ? "unknown" : clean(client.properties().getStreamingMode());
        if (out.dvd)
            out.transport = media.isDvdMimRuntimeFallback()
                    ? "DVD Native (MIM fallback)" : "DVD Native Push";
        else if (out.smb)
            out.transport = "SMB Direct";
        else if (out.push)
            out.transport = "fixed".equalsIgnoreCase(configured) ? "Fixed / MIM" : "SageTV Push";
        else
            out.transport = configured.length() == 0 ? "SageTV Pull" : displayToken(configured);

        out.serverChannelKbps = media.getServerChannelBandwidthKbps();
        out.serverStreamKbps = media.getServerStreamBandwidthKbps();
        out.serverTargetKbps = media.getServerTargetBandwidthKbps();
        out.serverMuxMs = media.getServerMuxTimeMs();
        out.serverBufferMs = media.getClientBufferTimeMs();
        if (out.serverStreamKbps > 0)
            out.reportedActivityKbps = out.serverStreamKbps;
        out.connectionCapacityKbps = out.serverChannelKbps > 0
                ? out.serverChannelKbps : linkCapacityKbps(activity);
        out.connectionCapacityFromServer = out.serverChannelKbps > 0;

        if (out.dvd)
        {
            out.dvdPushedBytes = media.getDvdPushedBytes();
            out.dvdEpochBytes = media.getDvdEpochPushedBytes();
            out.mediaBytes = out.dvdPushedBytes;
            out.dvdTransientEos = media.getDvdTransientEosCount();
            out.dvdAudioStream = media.getDvdLastAudioStreamPosition();
            out.dvdSubtitleStream = media.getDvdLastSubtitleStreamPosition();
            out.dvdMimFallback = media.isDvdMimRuntimeFallback();
            out.bufferedAheadMs = media.getDvdDecoderBufferedAheadMs() >= 0
                    ? media.getDvdDecoderBufferedAheadMs() : out.bufferedAheadMs;
            out.dvdCorrections = invokeLongOptional(effective,
                    "getDvdVideoTimestampCorrectionCountForDebug", -1L);
            out.dvdFrameGaps = invokeLongOptional(effective,
                    "getDvdFrameReleaseGapCountForDebug", -1L);
            out.dvdNonPositiveFrames = invokeLongOptional(effective,
                    "getDvdFrameReleaseNonPositiveCountForDebug", -1L);
            out.dvdAvDeltaUs = invokeLongOptional(effective,
                    "getDvdAvSampleDeltaUsForDebug", Long.MIN_VALUE);
            out.dvdTimestampRepair = invokeBooleanOptional(effective,
                    "isDvdMpeg2TimestampRepairEnabledForDebug", false);
        }

        DisplayRefreshController.Result display = DisplayRefreshController.inspect(activity, top);
        out.displayHz = display.displayHz;
        out.displayModeId = display.currentModeId;
        out.refreshReason = display.reason;
        return out;
    }

    String compactText(long activityKbps)
    {
        StringBuilder text = commonText();
        appendTransport(text, activityKbps, false);
        return trim(text);
    }

    private StringBuilder commonText()
    {
        StringBuilder text = new StringBuilder(512);
        line(text, backend + "  |  " + transport + "  |  " + stateLabel());
        line(text, "Video  " + videoSummary());
        line(text, "Decoder  " + value(videoDecoder, "unavailable")
                + (videoDecoderKind.length() == 0 ? "" : " (" + videoDecoderKind + ")"));
        line(text, "Display  " + displaySummary());
        line(text, "Timeline  " + time(positionMs) + durationSuffix()
                + "  |  " + String.format(Locale.US, "%.2fx", playbackRate));
        if (videoRendered >= 0 || videoSkipped >= 0 || videoDropped >= 0)
            line(text, "Frames  rendered " + number(videoRendered) + "  skipped "
                    + number(videoSkipped) + "  dropped " + number(videoDropped));
        if (audioMime.length() > 0 || audioDecoder.length() > 0)
            line(text, "Audio  " + audioSummary());
        if (error || playerError.length() > 0 || retryCount > 0)
            line(text, "Recovery  error=" + (error || playerError.length() > 0)
                    + "  retries=" + retryCount + errorSuffix());
        return text;
    }

    String detailedText(long activityKbps)
    {
        StringBuilder text = commonText();
        line(text, "Sync  subtitle " + signed(subtitleOffsetMs) + " ms  |  audio "
                + signed(audioOffsetMs) + " ms"
                + (dvdAvDeltaUs != Long.MIN_VALUE ? "  |  A/V samples "
                + signed(dvdAvDeltaUs / 1_000L) + " ms" : ""));
        if (subtitleTrack != MiniPlayerPlugin.DISABLE_TRACK || dvdSubtitleStream >= 0)
            line(text, "Captions  track=" + (subtitleTrack == MiniPlayerPlugin.DISABLE_TRACK
                    ? "off" : subtitleTrack)
                    + (dvdSubtitleStream >= 0 ? "  DVD stream=" + dvdSubtitleStream : ""));
        if (interlace.length() > 0 && !"unknown".equalsIgnoreCase(interlace))
            line(text, "Scan  " + displayToken(interlace));
        appendTransport(text, activityKbps, true);
        return trim(text);
    }

    String exportText(long activityKbps)
    {
        return "OpenSageTV Vibe Playback Stats\n"
                + redactForExport(detailedText(activityKbps))
                + "\n\nNo media path, server address, credentials, or client ID are included.\n";
    }

    private static String redactForExport(String value)
    {
        if (value == null || value.length() == 0) return "";
        return value
                .replaceAll("(?i)smb://\\S+", "<smb-path>")
                .replaceAll("\\\\\\\\[^\\s]+", "<unc-path>")
                .replaceAll("(?i)[a-z]:\\\\\\S+", "<media-path>")
                .replaceAll("(?i)/var/media/\\S+", "<media-path>")
                .replaceAll("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])",
                        "<server>");
    }

    private void appendTransport(StringBuilder text, long activityKbps, boolean detailed)
    {
        if (smb)
        {
            line(text, "SMB  " + (smbConnected ? "connected" : "disconnected")
                    + "  |  " + number(smbReads) + " reads  |  "
                    + number(smbSeeks) + " seeks  |  " + durationValue(smbLatencyMs));
            if (detailed)
            {
                line(text, "SMB cache  " + bytes(smbCacheHitBytes) + " hit  |  "
                        + number(smbCacheMisses) + " misses  |  ahead "
                        + bytes(smbReadAheadBytes));
                line(text, "Shadow  " + (shadowConnected ? "connected" : "disconnected")
                        + "  |  read " + bytes(shadowReadBytes));
                if (smbFallbacks > 0 || smbFallbackReason.length() > 0)
                    line(text, "Fallback  count=" + number(smbFallbacks) + "  "
                            + value(smbFallbackReason, "reason unavailable"));
            }
        }
        else if (push || dvd)
        {
            if (serverChannelKbps > 0 || serverStreamKbps > 0 || serverTargetKbps > 0)
                line(text, "Server  channel " + rate(serverChannelKbps)
                        + "  stream " + rate(serverStreamKbps)
                        + "  target " + rate(serverTargetKbps));
            if (detailed && serverMuxMs >= 0)
                line(text, "Server clock  mux=" + time(serverMuxMs)
                        + "  buffer=" + durationValue(serverBufferMs));
        }
        else if (readCount >= 0 || readWaitMs >= 0)
        {
            line(text, "Source  " + number(readCount) + " reads  |  wait "
                    + durationValue(readWaitMs) + "  |  errors " + number(readErrors));
        }

        if (dvd)
        {
            line(text, "DVD  pushed " + bytes(dvdPushedBytes) + "  |  epoch "
                    + bytes(dvdEpochBytes) + "  |  repair "
                    + (dvdTimestampRepair ? "on" : "off"));
            if (detailed)
            {
                line(text, "DVD cadence  corrections=" + number(dvdCorrections)
                        + "  gaps=" + number(dvdFrameGaps)
                        + "  non-positive=" + number(dvdNonPositiveFrames));
                line(text, "DVD streams  audio=" + number(dvdAudioStream)
                        + "  subtitle=" + number(dvdSubtitleStream)
                        + "  transient EOS=" + number(dvdTransientEos));
            }
        }
    }

    private String stateLabel()
    {
        StringBuilder value = new StringBuilder(state);
        if (loading) value.append(" / BUFFERING");
        if (seekPending) value.append(" / SEEKING");
        return value.toString();
    }

    private String videoSummary()
    {
        String resolution = videoWidth > 0 && videoHeight > 0
                ? videoWidth + "x" + videoHeight : "size unavailable";
        StringBuilder value = new StringBuilder(resolution);
        if (videoMime.length() > 0) value.append("  ").append(videoMime);
        else if (videoCodec.length() > 0) value.append("  ").append(videoCodec);
        if (contentFps > 0f)
            value.append("  ").append(String.format(Locale.US, "%.3f fps", contentFps));
        return value.toString();
    }

    private String displaySummary()
    {
        String size = surfaceWidth > 0 && surfaceHeight > 0
                ? surfaceWidth + "x" + surfaceHeight : "surface unavailable";
        if (displayHz > 0f)
            size += String.format(Locale.US, "  %.3f Hz", displayHz);
        return size + (surfaceWidth > 0 ? "  surface=" + (surfaceValid ? "valid" : "invalid") : "");
    }

    private String audioSummary()
    {
        StringBuilder value = new StringBuilder(value(audioMime, value(audioCodec, "format unavailable")));
        if (audioChannels > 0) value.append("  ").append(audioChannels).append("ch");
        if (audioSampleRate > 0) value.append("  ").append(audioSampleRate).append("Hz");
        if (audioDecoder.length() > 0) value.append("  ").append(audioDecoder);
        if (audioDropped >= 0) value.append("  dropped ").append(audioDropped);
        return value.toString();
    }

    private String durationSuffix()
    {
        return durationMs > 0 ? " / " + time(durationMs) : " / live or unknown";
    }

    private String errorSuffix()
    {
        return playerError.length() == 0 ? "" : "  " + playerError;
    }

    private static void captureDataSource(Object source, ActivePlayerStatsSnapshot out)
    {
        if (source == null) return;
        if (source instanceof PlaybackDataSourceTelemetry)
        {
            PlaybackDataSourceTelemetry telemetry = (PlaybackDataSourceTelemetry) source;
            out.readCount = telemetry.getNetworkReadCount();
            out.readWaitMs = telemetry.getNetworkReadWaitMs();
            out.readErrors = telemetry.getNetworkReadErrors();
            out.mediaBytes = telemetry.getNetworkReadBytes();
            String playbackSource = clean(telemetry.getPlaybackSource());
            out.smb = "SMB_DIRECT".equalsIgnoreCase(playbackSource);
            if (out.smb)
            {
                out.smbConnected = telemetry.isSmbConnected();
                out.shadowConnected = telemetry.isShadowConnected();
                out.shadowReadBytes = telemetry.getShadowReadBytes();
                out.smbBytes = telemetry.getSmbBytesRead();
                out.mediaBytes = out.smbBytes;
                out.smbReads = telemetry.getSmbReadCount();
                out.smbSeeks = telemetry.getSmbSeekCount();
                out.smbLatencyMs = telemetry.getSmbLastReadLatencyMs();
                out.smbCacheHitBytes = telemetry.getSmbCacheHitBytes();
                out.smbCacheMisses = telemetry.getSmbCacheMissCount();
                out.smbReadAheadBytes = telemetry.getSmbReadAheadBytes();
                out.smbFallbacks = telemetry.getSmbFallbackCount();
                out.smbFallbackReason = clean(telemetry.getSmbFallbackReason());
            }
        }
        else
        {
            out.readCount = invokeLongOptional(source, "getReadCount", -1L);
            out.readWaitMs = invokeLongOptional(source, "getReadWaitMs", -1L);
            out.mediaBytes = invokeLongOptional(source, "getBytesRead", -1L);
            long pushed = invokeLongOptional(source, "getPushedBytes", -1L);
            if (pushed >= 0) out.mediaBytes = pushed;
            out.reportedActivityKbps = invokeLongOptional(source, "getReadRateKbps", -1L);
        }
    }

    private static void capturePlayer(Object player, ActivePlayerStatsSnapshot out)
    {
        if (player == null) return;
        int playbackState = invokeIntOptional(player, "getPlaybackState", -1);
        out.loading = invokeBooleanOptional(player, "isLoading", false) || playbackState == 2;
        out.positionMs = invokeLongOptional(player, "getCurrentPosition", out.positionMs);
        long buffered = invokeLongOptional(player, "getBufferedPosition", -1L);
        if (buffered >= 0 && out.positionMs >= 0)
            out.bufferedAheadMs = Math.max(0L, buffered - out.positionMs);
        out.durationMs = invokeLongOptional(player, "getDuration", -1L);
        Object error = invokeOptional(player, "getPlayerError");
        if (error != null)
        {
            Object name = invokeOptional(error, "getErrorCodeName");
            out.playerError = clean(name == null ? error.toString() : String.valueOf(name));
        }
        captureFormat(invokeOptional(player, "getVideoFormat"), true, out);
        captureFormat(invokeOptional(player, "getAudioFormat"), false, out);
        int count = invokeIntOptional(player, "getRendererCount", 0);
        for (int i = 0; i < count; i++)
        {
            Object typeValue = invokeOptional(player, "getRendererType",
                    new Class<?>[] { int.class }, new Object[] { i });
            int type = typeValue instanceof Number ? ((Number) typeValue).intValue() : -1;
            Object renderer = invokeOptional(player, "getRenderer",
                    new Class<?>[] { int.class }, new Object[] { i });
            if (type == TRACK_TYPE_VIDEO) captureRenderer(renderer, true, out);
            else if (type == TRACK_TYPE_AUDIO) captureRenderer(renderer, false, out);
        }
    }

    private static void captureFormat(Object format, boolean video,
            ActivePlayerStatsSnapshot out)
    {
        if (format == null) return;
        if (video)
        {
            out.videoMime = readStringField(format, "sampleMimeType");
            out.videoCodec = readStringField(format, "codecs");
            out.videoWidth = readIntField(format, "width", out.videoWidth);
            out.videoHeight = readIntField(format, "height", out.videoHeight);
            float fps = readFloatField(format, "frameRate", -1f);
            if (fps > 0f) out.contentFps = fps;
        }
        else
        {
            out.audioMime = readStringField(format, "sampleMimeType");
            out.audioCodec = readStringField(format, "codecs");
            out.audioChannels = readIntField(format, "channelCount", -1);
            out.audioSampleRate = readIntField(format, "sampleRate", -1);
        }
    }

    private static void captureRenderer(Object renderer, boolean video,
            ActivePlayerStatsSnapshot out)
    {
        if (renderer == null) return;
        Object codecInfo = readField(renderer, "codecInfo");
        String decoder = codecInfo == null ? "" : readStringField(codecInfo, "name");
        Object counters = readField(renderer, "decoderCounters");
        if (counters != null) invokeOptional(counters, "ensureUpdated");
        if (video)
        {
            out.videoDecoder = decoder;
            if (decoder.length() > 0)
                out.videoDecoderKind = AndroidCodecPolicy.isSoftwareCodecName(decoder)
                        ? "software" : "hardware";
            out.videoRendered = readLongField(counters, "renderedOutputBufferCount", -1L);
            out.videoSkipped = readLongField(counters, "skippedOutputBufferCount", -1L);
            out.videoDropped = readLongField(counters, "droppedBufferCount", -1L);
        }
        else
        {
            out.audioDecoder = decoder;
            out.audioDropped = readLongField(counters, "droppedBufferCount", -1L);
        }
    }

    private static void captureSurface(Object effective, ActivePlayerStatsSnapshot out)
    {
        Object controller = readField(effective, "context");
        Object view = invokeOptional(controller, "getVideoView");
        if (!(view instanceof android.view.View)) return;
        android.view.View video = (android.view.View) view;
        out.surfaceWidth = video.getWidth();
        out.surfaceHeight = video.getHeight();
        out.surfaceValid = video.getWindowToken() != null;
        if (video instanceof android.view.SurfaceView)
        {
            android.view.Surface surface = ((android.view.SurfaceView) video)
                    .getHolder().getSurface();
            out.surfaceValid = surface != null && surface.isValid();
        }
    }

    private static long linkCapacityKbps(Activity activity)
    {
        if (activity == null) return -1L;
        try
        {
            ConnectivityManager manager = (ConnectivityManager) activity
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null || manager == null
                    ? null : manager.getNetworkCapabilities(active);
            int downstream = capabilities == null ? 0
                    : capabilities.getLinkDownstreamBandwidthKbps();
            return downstream > 0 ? downstream : -1L;
        }
        catch (Throwable ignored)
        {
            return -1L;
        }
    }

    private static Object unwrapDelegate(Object player)
    {
        Object current = player;
        for (int i = 0; i < 4 && current != null; i++)
        {
            Object next = readField(current, "delegate");
            if (next == null) break;
            current = next;
        }
        return current;
    }

    private static String friendlyBackend(String value)
    {
        if (value.contains("Media3")) return "Media3";
        if (value.contains("Exo2")) return "Legacy ExoPlayer";
        if (value.contains("IJK")) return "IJK";
        if (value.contains("GSY")) return "GSY";
        return value;
    }

    private static String miniState(int state)
    {
        switch (state)
        {
            case MiniPlayerPlugin.LOADED_STATE: return "Loaded";
            case MiniPlayerPlugin.PLAY_STATE: return "Playing";
            case MiniPlayerPlugin.PAUSE_STATE: return "Paused";
            case MiniPlayerPlugin.STOPPED_STATE: return "Stopped";
            case MiniPlayerPlugin.EOS_STATE: return "End of stream";
            default: return "Idle";
        }
    }

    private static String time(long millis)
    {
        if (millis < 0) return "—";
        long seconds = millis / 1_000L;
        return String.format(Locale.US, "%d:%02d:%02d", seconds / 3_600L,
                (seconds / 60L) % 60L, seconds % 60L);
    }

    static String percent(float value)
    {
        return value < 0f ? "unavailable" : String.format(Locale.US, "%.1f%%", value);
    }

    static String rate(long kbps)
    {
        if (kbps < 0) return "—";
        if (kbps >= 10_000) return String.format(Locale.US, "%.1f Mbps", kbps / 1_000.0);
        return kbps + " kbps";
    }

    static String durationValue(long millis)
    {
        if (millis < 0) return "—";
        if (millis >= 10_000) return String.format(Locale.US, "%.1f s", millis / 1_000.0);
        return millis + " ms";
    }

    static String bytes(long value)
    {
        if (value < 0) return "—";
        if (value >= 1_073_741_824L)
            return String.format(Locale.US, "%.2f GiB", value / 1_073_741_824.0);
        if (value >= 1_048_576L)
            return String.format(Locale.US, "%.1f MiB", value / 1_048_576.0);
        if (value >= 1_024L)
            return String.format(Locale.US, "%.1f KiB", value / 1_024.0);
        return value + " B";
    }

    private static String signed(long value) { return value > 0 ? "+" + value : Long.toString(value); }
    private static String number(long value) { return value < 0 ? "—" : Long.toString(value); }
    private static String value(String value, String fallback) { return value.length() == 0 ? fallback : value; }
    private static String displayToken(String value) { return value.replace('_', ' '); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void line(StringBuilder out, String value) { if (out.length() > 0) out.append('\n'); out.append(value); }
    private static String trim(StringBuilder value) { return value.toString().trim(); }

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
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
            catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private static Object readField(Object target, String name)
    {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try { return field.get(target); }
        catch (Throwable ignored) { return null; }
    }

    private static String readStringField(Object target, String name)
    {
        Object value = readField(target, name);
        return value == null ? "" : clean(String.valueOf(value));
    }

    private static int readIntField(Object target, String name, int fallback)
    {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long readLongField(Object target, String name, long fallback)
    {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static float readFloatField(Object target, String name, float fallback)
    {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static Object invokeOptional(Object target, String name)
    {
        return invokeOptional(target, name, new Class<?>[0], new Object[0]);
    }

    private static Object invokeOptional(Object target, String name, Class<?>[] types, Object[] args)
    {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null)
        {
            try
            {
                Method method = current.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            }
            catch (NoSuchMethodException ignored) { current = current.getSuperclass(); }
            catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private static long invokeLongOptional(Object target, String name, long fallback)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static int invokeIntOptional(Object target, String name, int fallback)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean invokeBooleanOptional(Object target, String name, boolean fallback)
    {
        Object value = invokeOptional(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static String invokeStringOptional(Object target, String name, String fallback)
    {
        Object value = invokeOptional(target, name);
        return value == null ? fallback : clean(String.valueOf(value));
    }
}
