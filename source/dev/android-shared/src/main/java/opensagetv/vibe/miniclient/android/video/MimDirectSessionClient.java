package opensagetv.vibe.miniclient.android.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opensagetv.vibe.miniclient.MimDirectTransportPolicy;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Client for the Standard FFmpeg plugin's optional owned media sessions. */
public final class MimDirectSessionClient
{
    private static final Logger log = LoggerFactory.getLogger(MimDirectSessionClient.class);
    private static final String IJK_PLAYER_CLASS =
            "opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl";
    private static final int DEFAULT_PORT = 31910;
    private static final int MAX_REPLY_BYTES = 256 * 1024;
    private static final long REDUNDANT_STARTUP_SEEK_NS = 5_000_000_000L;
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\\\"%s\\\"\\s*:\\s*\\\"([^\\\"\\r\\n]*)\\\"");
    private static final Pattern LONG_FIELD = Pattern.compile(
            "\\\"%s\\\"\\s*:\\s*(-?[0-9]+)");

    private final Object lock = new Object();
    /**
     * SageTV can issue closely spaced seek notifications from different
     * control paths. A Direct replacement retires its input token, so only
     * one replacement request may use that token at a time. Lifecycle state
     * remains protected independently by {@link #lock}; a concurrent stop is
     * still allowed to invalidate and clean up a late replacement.
     */
    private final Object restartRequestLock = new Object();
    private final ExecutorService teardownExecutor =
            Executors.newSingleThreadExecutor(new ThreadFactory()
            {
                @Override public Thread newThread(Runnable task)
                {
                    Thread thread = new Thread(task, "vibe-mim-direct-teardown");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private String baseUrl = "";
    private String activeMode = MimDirectTransportPolicy.OFF;
    private String sessionToken = "";
    private Object owner;
    private long startOffsetMs;
    private boolean lastRestartClamped;
    private long openedAtNanos;
    private long lifecycleGeneration;
    /** One connection-scoped escape hatch; never changes the saved preference. */
    private boolean suppressNextPrepareForStockFixed;
    private boolean lateFallbackReconnectClaimed;
    /** Debug-receiver-only, one-shot proof of the two-stage startup fallback. */
    private boolean debugForceNextDirectStartFailure;
    private boolean debugForceFallbackPullFailure;
    private boolean pluginWatchRecoveryRequired;
    private final MimDirectWatchRecoveryClient watchRecovery=new MimDirectWatchRecoveryClient(
            teardownExecutor,(base,path) -> request("POST",base,path,1500,3000),System::nanoTime);
    private volatile String state = "off";

    /** Probe before MiniClient capability negotiation. Returns the active mode. */
    public String prepare(ServerInfo server, PrefStore preferences)
    {
        return prepare(server, preferences, "");
    }

    public String prepare(ServerInfo server, PrefStore preferences, String nativeVideoCodecs)
    {
        final boolean suppressForStockFixed;
        synchronized (lock)
        {
            suppressForStockFixed = suppressNextPrepareForStockFixed;
            suppressNextPrepareForStockFixed = false;
            lateFallbackReconnectClaimed = false;
        }
        if (suppressForStockFixed && watchRecovery.pending()) stopTransportOnly();
        else stop();
        if (suppressForStockFixed)
        {
            state = "late_failure_stock_fixed_reconnect";
            return MimDirectTransportPolicy.OFF;
        }
        if (server == null || preferences == null || server.address == null)
            return MimDirectTransportPolicy.OFF;
        String requested = MimDirectTransportPolicy.activeMode(
                preferences.getStreamingMode(),
                preferences.getString(PrefStore.Keys.mim_direct_mode, "off"), true);
        if (!MimDirectTransportPolicy.isActive(requested))
            return MimDirectTransportPolicy.OFF;
        // The long-press player control and the MCP physical-test harness can
        // select a backend for only the active playback session. Resolve that
        // override exactly as the renderers do; consulting only the persisted
        // preference would incorrectly negotiate Direct Transcode for IJK
        // while the active player is already IJK.
        PlayerBackend backend = PlayerBackend.fromPreference(
                ActivePlayerSessionOverrides.resolveBackend(preferences.getString(
                        PrefStore.Keys.default_player,
                        PlayerBackend.DEFAULT_PREFERENCE)));
        if (!supportsMode(backend, requested))
        {
            // IJK 0.8.8 Direct Copy is physically proven, but its MediaCodec
            // path enters a persistent illegal state on the plugin's encoded
            // Direct Transcode output. Keep the explicit request fail-safe by
            // retaining ordinary SageTV Fixed rather than opening a stream
            // that cannot produce advancing A/V.
            state = "unsupported_player_stock_fixed";
            return MimDirectTransportPolicy.OFF;
        }
        final boolean needsWatchRecovery=MimDirectTransportPolicy.TRANSCODE.equals(requested)
                && !MimDirectTransportPolicy.supportsTranscodePullFallback(nativeVideoCodecs);
        String host = server.address.trim();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
        int port = boundedPort(preferences.getString(
                PrefStore.Keys.fixed_caption_side_channel_port,
                String.valueOf(DEFAULT_PORT)));
        String candidateBase = "http://" + host + ":" + port;
        try
        {
            Response capabilities = request("GET", candidateBase,
                    "/v1/capabilities", 750, 900);
            if (capabilities.status != 200 || !supportsDirect(capabilities.body, requested))
            {
                state = "unavailable_stock_fixed";
                return MimDirectTransportPolicy.OFF;
            }
            if (needsWatchRecovery && (!MimDirectTransportPolicy.supportsH264Output(nativeVideoCodecs)
                    || !MimDirectWatchRecoveryClient.supported(capabilities.body)))
            {
                state="unsupported_video_stock_fixed";
                return MimDirectTransportPolicy.OFF;
            }
            watchRecovery.configure(candidateBase,needsWatchRecovery);
            synchronized (lock)
            {
                baseUrl = candidateBase;
                activeMode = requested;
                pluginWatchRecoveryRequired=needsWatchRecovery;
            }
            state = "ready_" + requested;
            return requested;
        }
        catch (Exception unavailable)
        {
            state = "unavailable_stock_fixed";
            return MimDirectTransportPolicy.OFF;
        }
    }

    static boolean supportsMode(PlayerBackend backend, String mode)
    {
        return backend != PlayerBackend.IJKPLAYER ||
                !MimDirectTransportPolicy.TRANSCODE.equals(mode);
    }

    /** Create an owned stream for an original SageTV source path. */
    public String open(Object playbackOwner, String sourceUrl)
    {
        return open(playbackOwner, sourceUrl, false);
    }

    /** Create an owned stream and preserve SageTV's growing-file declaration. */
    public String open(Object playbackOwner, String sourceUrl, boolean active)
    {
        String source = serverPath(sourceUrl);
        final String base;
        final String mode;
        synchronized (lock)
        {
            base = baseUrl; mode = activeMode;
        }
        if (!supportsPlaybackOwnerClass(playbackOwner == null ? "" :
                playbackOwner.getClass().getName(), mode))
        {
            // prepare() may run before a one-time/debug player selection is
            // applied. The concrete media player is authoritative here, at
            // the final boundary before a server-owned stream is created.
            state = "unsupported_player_stock_fixed";
            return null;
        }
        if (playbackOwner == null || source.isEmpty() || base.isEmpty() ||
                !MimDirectTransportPolicy.isActive(mode))
            return null;
        // OPENURL establishes the one authoritative owned-media session. A
        // replacement player may be a different Java object, so an
        // owner-scoped release here could orphan the preceding server job.
        // This OPENURL already runs on the MiniClient media worker. Finish
        // releasing the preceding producer before reserving another server
        // slot so rapid file switches cannot exhaust the bounded session pool.
        releaseInternal(null, false);
        final long requestGeneration;
        synchronized (lock) { requestGeneration = lifecycleGeneration; }
        if (pluginWatchRecoveryRequired)
        {
            opensagetv.vibe.miniclient.MiniClientConnection connection=MiniclientApplication.get()
                    .getClient().getCurrentConnection();
            watchRecovery.source(playbackOwner,source,connection ==null ? "" : connection.getClientID());
        }
        try
        {
            synchronized (lock)
            {
                if (debugForceNextDirectStartFailure)
                {
                    debugForceNextDirectStartFailure = false;
                    throw new IOException("debug forced direct start failure");
                }
            }
            String deinterlace = currentDeinterlace();
            String path = startRequestPath(source, mode, deinterlace, active);
            Response response = request("POST", base, path, 1500, 20000);
            if (response.status != 200) throw new IOException("direct start rejected");
            String token = token(response.body, "sessionToken");
            String media = stringValue(response.body, "mediaUrl", "");
            String captionToken = optionalToken(response.body,
                    "captionSessionToken");
            if (!media.startsWith("/v1/direct/media/"))
                throw new IOException("invalid direct media URL");
            boolean accepted;
            synchronized (lock)
            {
                accepted = lifecycleGeneration == requestGeneration &&
                        sessionToken.isEmpty();
                if (accepted)
                {
                    owner = playbackOwner;
                    sessionToken = token;
                    startOffsetMs = 0L;
                    openedAtNanos = System.nanoTime();
                }
            }
            if (!accepted)
            {
                teardown(base, token);
                return null;
            }
            if (!captionToken.isEmpty())
                MiniclientApplication.get().getFixedCaptionSideChannel()
                        .startClaimed(base, captionToken);
            state = "active_" + mode;
            return base + media;
        }
        catch (Exception failure)
        {
            synchronized (lock)
            {
                lateFallbackReconnectClaimed = false;
                state = "start_failed_pull_fallback";
            }
            log.debug("Optional MIM Direct start failed ({})",
                    failure.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Claim the only automatic recovery allowed after Direct startup failed.
     * The caller must also prove the original Pull source failed before its
     * first frame. The next connection advertises ordinary Fixed capabilities;
     * subsequent connections use the unchanged saved Direct preference again.
     */
    public boolean requestStockFixedReconnectForUnplayablePull()
    {
        if (pluginWatchRecoveryRequired)
        {
            if (watchRecovery.active()) return true;
            if (!lateFallbackStateAllowsReconnect(state)) return false;
            return watchRecovery.request(() -> {
                synchronized(lock) {
                    lateFallbackReconnectClaimed=true; suppressNextPrepareForStockFixed=true;
                    state="plugin_watch_stock_fixed_reconnect_requested";
                }
                MiniclientApplication.get().getClient().eventbus().post(
                        new opensagetv.vibe.miniclient.android.events.MimDirectFallbackReconnectEvent(
                                "plugin_watch_recovery",true));
            },() -> {
                state="plugin_watch_recovery_unavailable";
                opensagetv.vibe.miniclient.MiniClient client=MiniclientApplication.get().getClient();
                final opensagetv.vibe.miniclient.MiniPlayerPlugin currentPlayer=client.getPlayer();
                boolean currentSource=currentPlayer !=null && watchRecovery.sourceMatches(source ->
                        source ==currentPlayer || (currentPlayer instanceof
                                opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl
                                && ((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)
                                currentPlayer).isPlaybackOwner(source)));
                if (client.getCurrentConnection() !=null && currentSource)
                    opensagetv.vibe.miniclient.uibridge.EventRouter.postCommand(client,
                            opensagetv.vibe.miniclient.SageCommand.STOP);
                log.warn("Optional stock watch recovery unavailable; no Watch replay, current source stop={}", currentSource);
                opensagetv.vibe.miniclient.android.AppUtil.message(
                        "Automatic playback recovery unavailable. Select the recording again to use ordinary Fixed playback.");
            });
        }
        synchronized (lock)
        {
            if (!lateFallbackStateAllowsReconnect(state)
                    || lateFallbackReconnectClaimed
                    || suppressNextPrepareForStockFixed)
                return false;
            lateFallbackReconnectClaimed = true;
            suppressNextPrepareForStockFixed = true;
            state = "late_failure_stock_fixed_reconnect_requested";
            return true;
        }
    }

    public boolean requiresPluginWatchRecovery() { return pluginWatchRecoveryRequired; }
    public boolean hasPendingPluginWatchRecovery() { return watchRecovery.pending(); }
    public boolean commitPluginWatchRecoveryHandoff() { return watchRecovery.commitHandoff(); }
    public interface RecoveryConnectionReady { boolean ready(); }

    /** Record actual server seek intent without HTTP/decoder-state reads. */
    public void recordRecoveryPosition(Object playbackOwner,long target,boolean playing)
    {
        if (pluginWatchRecoveryRequired) watchRecovery.position(playbackOwner,target,playing);
    }
    public void recordRecoveryPlaying(Object playbackOwner,boolean playing)
    {
        if (pluginWatchRecoveryRequired) watchRecovery.playing(playbackOwner,playing);
    }

    public void onFreshConnectionForWatchRecovery(Object connection,
            RecoveryConnectionReady ready) { watchRecovery.connected(connection,ready::ready); }

    public void onRecoveryVideoFrame(Object owner,Object connection) { watchRecovery.video(owner,connection); }

    /** Explicit user controls cancel custody before their normal server command. */
    public void onUserPlaybackCommand(opensagetv.vibe.miniclient.SageCommand command)
    {
        if (command ==null) return;
        switch (command) {
            case STOP: case PLAY: case PAUSE: case PLAY_PAUSE: case FF: case REW:
            case FF_2: case REW_2: case RIGHT_FF: case LEFT_REW:
                if (!"ready".equals(watchRecovery.state()) && !"off".equals(watchRecovery.state()))
                    watchRecovery.cancel();
                break;
            default: break;
        }
    }

    /** Only the deliberate fresh-session handoff retains its scoped ticket. */
    public void stopForActivityReplacement(boolean replacing)
    {
        if (replacing && watchRecovery.pending()) stopTransportOnly();
        else stop();
    }

    /** The existing MiniClient connection accepted its native reconnect path. */
    public void useInPlaceStockFixedReconnect()
    {
        synchronized (lock)
        {
            suppressNextPrepareForStockFixed = false;
            baseUrl = "";
            activeMode = MimDirectTransportPolicy.OFF;
            state = "late_failure_stock_fixed_reconnect";
        }
    }

    static boolean lateFallbackStateAllowsReconnect(String value)
    {
        return "start_failed_pull_fallback".equals(value);
    }

    /**
     * Arm or clear the debug APK's deterministic two-stage startup failure.
     * The exported receiver exists only in the debug manifest; normal builds
     * have no caller for this test seam. Both failures are consumed once.
     */
    public String setDebugLateFallbackFailure(boolean enabled)
    {
        return setDebugLateFallbackFailure(enabled, true);
    }

    public String setDebugLateFallbackFailure(boolean enabled, boolean failPull)
    {
        synchronized (lock)
        {
            debugForceNextDirectStartFailure = enabled;
            debugForceFallbackPullFailure = enabled && failPull;
            return "armed=" + enabled + ";state=" + state;
        }
    }

    /** Replace only the Pull attempt following the injected Direct failure. */
    public String debugFallbackPullUrl(String originalUrl)
    {
        synchronized (lock)
        {
            if (!debugForceFallbackPullFailure
                    || !lateFallbackStateAllowsReconnect(state))
                return originalUrl;
            debugForceFallbackPullFailure = false;
            return "http://127.0.0.1:1/vibe-debug-unplayable-pull.ts";
        }
    }

    static boolean supportsPlaybackOwnerClass(String className, String mode)
    {
        return !MimDirectTransportPolicy.TRANSCODE.equals(mode) ||
                !IJK_PLAYER_CLASS.equals(className);
    }

    static String normalizedDeinterlace(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return "on".equals(normalized) || "off".equals(normalized)
                ? normalized : "auto";
    }

    static String startRequestPath(String source, String mode,
                                   String deinterlace, boolean active)
            throws IOException
    {
        return "/v1/direct/start?source=" +
                URLEncoder.encode(source, "UTF-8") + "&mode=" + mode +
                "&deinterlace=" + deinterlace + "&active=" + active +
                "&startMs=0";
    }

    private static String currentDeinterlace()
    {
        return normalizedDeinterlace(
                ActivePlayerSessionOverrides.resolveMimDirectDeinterlace(
                        MiniclientApplication.get().getClient().properties().getString(
                                PrefStore.Keys.mim_direct_deinterlace, "auto")));
    }

    public void release(Object playbackOwner)
    {
        // Player free/activity destruction normally runs on Android's main
        // thread. HttpURLConnection is forbidden there and used to fail with
        // NetworkOnMainThreadException, leaving an orphan server producer.
        // Capture and invalidate ownership synchronously, then perform only
        // the HTTP teardown on this ordered daemon worker.
        releaseInternal(playbackOwner, true);
    }

    private void releaseInternal(Object playbackOwner, boolean asynchronous)
    {
        final String base;
        final String token;
        final long releaseGeneration;
        synchronized (lock)
        {
            // A null playback owner is the lifecycle-level forced teardown
            // used when the application/session stops. Only reject a release
            // from a different concrete player.
            if (!canRelease(owner, playbackOwner)) return;
            base = baseUrl; token = sessionToken;
            owner = null; sessionToken = ""; startOffsetMs = 0L;
            lastRestartClamped = false;
            openedAtNanos = 0L;
            lifecycleGeneration++;
            releaseGeneration = lifecycleGeneration;
        }
        if (!token.isEmpty())
        {
            state = "release_pending";
            Runnable releaseTask = new Runnable()
            {
                @Override public void run()
                {
                    int status = teardown(base, token);
                    log.debug("MIM Direct release status={} token={}", status,
                            token.substring(0, Math.min(8, token.length())));
                    synchronized (lock)
                    {
                        if (lifecycleGeneration == releaseGeneration &&
                                sessionToken.isEmpty())
                            state = status == 200 ? "released" : "release_failed_" + status;
                    }
                }
            };
            if (asynchronous) teardownExecutor.execute(releaseTask);
            else releaseTask.run();
        }
    }

    public String restart(Object playbackOwner, long requestedStartMs)
    {
        synchronized (restartRequestLock)
        {
            final String base;
            final String token;
            final long requestGeneration;
            synchronized (lock)
            {
                if (owner != playbackOwner || sessionToken.isEmpty()) return null;
                base = baseUrl; token = sessionToken;
                requestGeneration = lifecycleGeneration;
            }
            String failureTag = "";
            try
            {
                Response response = request("POST", base,
                        "/v1/direct/restart?token=" + token + "&startMs=" +
                                Math.max(0L, requestedStartMs) + "&deinterlace=" +
                                currentDeinterlace(), 1500, 20000);
                if (response.status != 200)
                {
                    failureTag = restartRejectionTag(response.status, response.body);
                    throw new IOException("direct restart rejected");
                }
                String replacementToken = token(response.body, "sessionToken");
                String media = stringValue(response.body, "mediaUrl", "");
                long effectiveStartMs = longValue(response.body, "startMs",
                        Math.max(0L, requestedStartMs));
                String captionToken = optionalToken(response.body,
                        "captionSessionToken");
                if (!media.startsWith("/v1/direct/media/"))
                    throw new IOException("invalid direct restart URL");
                boolean accepted;
                synchronized (lock)
                {
                    accepted = lifecycleGeneration == requestGeneration &&
                            owner == playbackOwner && sessionToken.equals(token);
                    if (accepted)
                    {
                        sessionToken = replacementToken;
                        startOffsetMs = Math.max(0L, effectiveStartMs);
                        lastRestartClamped = startOffsetMs != Math.max(0L, requestedStartMs);
                        openedAtNanos = System.nanoTime();
                    }
                }
                if (!accepted)
                {
                    // The server may already have created the replacement before
                    // STOP/FREE invalidated this request. Never leave that late
                    // replacement running without a client owner.
                    teardown(base, replacementToken);
                    return null;
                }
                if (!captionToken.isEmpty())
                    MiniclientApplication.get().getFixedCaptionSideChannel()
                            .startClaimed(base, captionToken);
                state = "active_" + activeMode;
                return base + media;
            }
            catch (Exception failure)
            {
                state = "restart_failed_session_retained_" +
                        (failureTag.isEmpty() ? failure.getClass().getSimpleName() : failureTag);
                log.warn("MIM Direct restart failed; retaining the active session ({})",
                        failureTag.isEmpty() ? failure.getClass().getSimpleName() : failureTag);
                return null;
            }
        }
    }

    /**
     * A bounded, closed diagnostic vocabulary. Never export a server response,
     * request URL, token or arbitrary error string into the client log/state.
     * This only distinguishes API rejection from an I/O failure; it does not
     * retry a seek or relabel retained old playback as successful replacement.
     */
    static String restartRejectionTag(int status, String body)
    {
        String code = stringValue(body, "error", "");
        switch (code)
        {
            case "unknown_or_finished_session":
            case "direct_session_limit":
            case "direct_caption_slot_unavailable":
            case "mim_direct_disabled":
            case "mim_direct_restart_failed":
                break;
            default:
                code = "unclassified";
        }
        return "http_" + (status >= 100 && status <= 599 ? status : 0) + "_" + code;
    }

    public void stop()
    {
        watchRecovery.cancel();
        pluginWatchRecoveryRequired=false;
        stopTransportOnly();
    }

    private void stopTransportOnly()
    {
        release(null);
        synchronized (lock)
        {
            baseUrl = "";
            activeMode = MimDirectTransportPolicy.OFF;
        }
        state = "off";
    }

    /**
     * Consume redundant same-position seeks SageTV sends during the bounded
     * OPENURL startup window (or immediately after a direct-session restart).
     * Restarting the producer for those no-op seeks can retire the playlist
     * while the decoder is still opening it, so leave the already-correct
     * producer running. A different target or any later seek is never hidden.
     */
    public boolean consumeRedundantStartupSeek(Object playbackOwner, long requestedStartMs)
    {
        synchronized (lock)
        {
            long ageNs = openedAtNanos == 0L ? Long.MAX_VALUE
                    : System.nanoTime() - openedAtNanos;
            if (owner != playbackOwner || sessionToken.isEmpty() ||
                    requestedStartMs != startOffsetMs ||
                    !isStartupSeekAge(ageNs)) return false;
            state = "active_" + activeMode + "_startup_seek_suppressed";
            return true;
        }
    }

    static boolean canRelease(Object currentOwner, Object requestedOwner)
    {
        return requestedOwner == null || currentOwner == null ||
                currentOwner == requestedOwner;
    }

    static boolean isStartupSeekAge(long ageNs)
    {
        return ageNs >= 0L && ageNs <= REDUNDANT_STARTUP_SEEK_NS;
    }

    public boolean owns(Object playbackOwner)
    {
        synchronized (lock) { return owner == playbackOwner && !sessionToken.isEmpty(); }
    }

    public boolean lastRestartWasClamped(Object playbackOwner)
    {
        synchronized (lock) { return owner == playbackOwner && lastRestartClamped; }
    }

    public long adjustPosition(Object playbackOwner, long playerPositionMs)
    {
        synchronized (lock)
        {
            return owner == playbackOwner ? startOffsetMs + playerPositionMs : playerPositionMs;
        }
    }

    /** On-demand debug attribution only. Never returns an endpoint or token. */
    public String mediaUriStateForDiagnostics(String observedUri)
    {
        final String base;
        final String token;
        synchronized (lock) { base = baseUrl; token = sessionToken; }
        return compareMediaUri(base, token, observedUri);
    }

    static String compareMediaUri(String base, String token, String observedUri)
    {
        if (token == null || token.isEmpty()) return "inactive";
        if (observedUri == null || observedUri.isEmpty()) return "unavailable";
        try
        {
            URI expected = URI.create(base);
            URI actual = URI.create(observedUri);
            if (!expected.getScheme().equalsIgnoreCase(actual.getScheme())
                    || !expected.getRawAuthority().equalsIgnoreCase(actual.getRawAuthority()))
                return "foreign";
            String path = actual.getPath();
            if (path == null || !path.startsWith("/v1/direct/media/")) return "foreign";
            return path.startsWith("/v1/direct/media/" + token + "/")
                    ? "current" : "retired";
        }
        catch (RuntimeException malformed) { return "unavailable"; }
    }

    /** Closed HTTP-media vocabulary; arbitrary response bodies remain private. */
    public static String mediaResponseTagForDiagnostics(int status, byte[] body)
    {
        String prefix = status >= 100 && status <= 599 ? "http_" + status : "http_unknown";
        if (body != null && body.length <= 4096)
        {
            String code = stringValue(new String(body, StandardCharsets.UTF_8), "error", "");
            if ("media_not_ready".equals(code) || "unknown_media".equals(code))
                return prefix + "_" + code;
        }
        return prefix + "_unclassified";
    }

    /** Caption tap PTS is relative to the active Direct FFmpeg invocation. */
    public long captionClockPosition(Object playbackOwner, long sageMediaTimeMs)
    {
        synchronized (lock)
        {
            return owner == playbackOwner
                    ? relativeCaptionClock(sageMediaTimeMs, startOffsetMs)
                    : sageMediaTimeMs;
        }
    }

    static long relativeCaptionClock(long sageMediaTimeMs, long directStartMs)
    {
        return Math.max(0L, sageMediaTimeMs - directStartMs);
    }

    public String stateForDiagnostics() { return state; }
    public String recoveryStateForDiagnostics() { return watchRecovery.state(); }

    static String serverPath(String url)
    {
        if (url == null) return "";
        String value = url.trim();
        if (value.isEmpty() || value.startsWith("push:") ||
                value.startsWith("http://") || value.startsWith("https://")) return "";
        try
        {
            if (value.startsWith("stv://"))
            {
                // Stock Windows SageTV sends paths such as
                // stv://server/V:\\recordings\\show.ts. Backslashes make the
                // complete value an invalid java.net.URI, so split the stv
                // authority first and preserve the server-native path. This
                // also preserves a Unix absolute path because its wire form
                // contains two slashes after the authority.
                int pathStart = value.indexOf('/', "stv://".length());
                if (pathStart < 0 || pathStart + 1 >= value.length()) return "";
                value = value.substring(pathStart + 1);
            }
            if (value.startsWith("file:"))
                value = new URI(value).getPath();
        }
        catch (Exception invalid) { return ""; }
        if (value == null) return "";
        // URI#getPath represents a Windows file URI as /C:/path. The FFmpeg
        // plugin runs on that Windows server and therefore needs C:/path.
        if (value.matches("^/[A-Za-z]:[\\\\/].*")) value = value.substring(1);
        return value.startsWith("/") || value.matches("^[A-Za-z]:[\\\\/].*")
                ? value : "";
    }

    private static boolean supportsDirect(String body, String mode)
    {
        Matcher section=Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})")
                .matcher(body==null ? "" : body);
        if (!section.find()) return false;
        String compact = section.group(1).replaceAll("\\s+", "");
        return
                compact.contains("\"contractVersion\":1") &&
                compact.contains("\"available\":true") &&
                compact.contains("\"modes\":[\"copy\",\"transcode\"]") &&
                compact.contains("\"" + mode + "\"");
    }

    private static int boundedPort(String value)
    {
        try
        {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port >= 1024 && port <= 65535 ? port : DEFAULT_PORT;
        }
        catch (NumberFormatException invalid) { return DEFAULT_PORT; }
    }

    private static String token(String body, String name)
    {
        String value = stringValue(body, name, "");
        if (value.length() != 64 || !value.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("invalid direct token");
        return value;
    }

    private static String optionalToken(String body, String name)
    {
        String value = stringValue(body, name, "").trim();
        if (value.isEmpty()) return "";
        if (value.length() < 24 || value.length() > 128 ||
                !value.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("invalid optional direct token");
        return value;
    }

    private static String stringValue(String body, String name, String fallback)
    {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(),
                Pattern.quote(name))).matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    static long longValue(String body, String name, long fallback)
    {
        Matcher matcher = Pattern.compile(String.format(LONG_FIELD.pattern(),
                Pattern.quote(name))).matcher(body == null ? "" : body);
        if (!matcher.find()) return fallback;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    static Response request(String method, String base, String path,
                                    int connectMs, int readMs) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(connectMs);
        connection.setReadTimeout(readMs);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method))
        {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.getOutputStream().close();
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = input == null ? "" : readBounded(input);
        connection.disconnect();
        return new Response(status, body);
    }

    private static String readBounded(InputStream input) throws IOException
    {
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0)
            {
                if (output.size() + count > MAX_REPLY_BYTES)
                    throw new IOException("direct API reply exceeds limit");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        finally { input.close(); }
    }

    private static int teardown(String base, String token)
    {
        try { return request("POST", base, "/v1/direct/teardown?token=" + token,
                1500, 5000).status; }
        catch (Exception ignored) { return -1; }
    }

    static final class Response
    {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
}
