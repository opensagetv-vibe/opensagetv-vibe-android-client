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
    private long openedAtNanos;
    private long lifecycleGeneration;
    /** One connection-scoped escape hatch; never changes the saved preference. */
    private boolean suppressNextPrepareForStockFixed;
    private boolean lateFallbackReconnectClaimed;
    /** Debug-receiver-only, one-shot proof of the two-stage startup fallback. */
    private boolean debugForceNextDirectStartFailure;
    private boolean debugForceFallbackPullFailure;
    private volatile String state = "off";

    /** Probe before MiniClient capability negotiation. Returns the active mode. */
    public String prepare(ServerInfo server, PrefStore preferences)
    {
        final boolean suppressForStockFixed;
        synchronized (lock)
        {
            suppressForStockFixed = suppressNextPrepareForStockFixed;
            suppressNextPrepareForStockFixed = false;
            lateFallbackReconnectClaimed = false;
        }
        stop();
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
            synchronized (lock)
            {
                baseUrl = candidateBase;
                activeMode = requested;
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
            String path = "/v1/direct/start?source=" +
                    URLEncoder.encode(source, "UTF-8") + "&mode=" + mode +
                    "&deinterlace=" + deinterlace + "&startMs=0";
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
        synchronized (lock)
        {
            debugForceNextDirectStartFailure = enabled;
            debugForceFallbackPullFailure = enabled;
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
            try
            {
                Response response = request("POST", base,
                        "/v1/direct/restart?token=" + token + "&startMs=" +
                                Math.max(0L, requestedStartMs) + "&deinterlace=" +
                                currentDeinterlace(), 1500, 20000);
                if (response.status != 200)
                    throw new IOException("direct restart rejected");
                String replacementToken = token(response.body, "sessionToken");
                String media = stringValue(response.body, "mediaUrl", "");
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
                        startOffsetMs = Math.max(0L, requestedStartMs);
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
                        failure.getClass().getSimpleName();
                log.warn("MIM Direct restart failed; retaining the active session ({})",
                        failure.getClass().getSimpleName());
                return null;
            }
        }
    }

    public void stop()
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

    public long adjustPosition(Object playbackOwner, long playerPositionMs)
    {
        synchronized (lock)
        {
            return owner == playbackOwner ? startOffsetMs + playerPositionMs : playerPositionMs;
        }
    }

    public String stateForDiagnostics() { return state; }

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
        String compact = body == null ? "" : body.replaceAll("\\s+", "");
        return compact.contains("\"mimDirect\":{") &&
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

    private static Response request(String method, String base, String path,
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

    private static final class Response
    {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
}
