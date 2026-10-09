package opensagetv.vibe.miniclient.android.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;
import opensagetv.vibe.miniclient.video.CaptionClockCadence;
import opensagetv.vibe.miniclient.video.LegacySubtitleCallbackPolicy;

/**
 * Optional, connection-scoped consumer for the Standard FFmpeg plugin's
 * bounded Fixed-caption service.
 *
 * <p>The client is deliberately fail-safe. It starts only for explicit Fixed
 * mode and an explicit user opt-in. The service is scoped to the trusted LAN;
 * opaque reservation/session identifiers protect individual sessions. A
 * missing, old, disabled or malformed service leaves the established SageTV
 * Fixed transport and local caption paths untouched.</p>
 */
public final class FixedCaptionSideChannelClient
{
    private static final Logger log =
            LoggerFactory.getLogger(FixedCaptionSideChannelClient.class);
    private static final int CONTRACT_VERSION = 1;
    private static final int DEFAULT_PORT = 31910;
    private static final int CONNECT_TIMEOUT_MS = 750;
    private static final int READ_TIMEOUT_MS = 900;
    private static final int MAX_REPLY_BYTES = 1024 * 1024;
    private static final long RESERVATION_RETRY_MS = 1_000L;
    private static final long FAILURE_RETRY_MS = 5_000L;
    private static final long RESERVATION_EXPIRED_MS = 62_000L;
    private static final long NETWORK_POLL_INTERVAL_MS = 250L;
    private static final Pattern PACKET_PATTERN = Pattern.compile(
            "\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*\"([0-9a-fA-F]*)\"\\s*\\]");

    private final Object lock = new Object();
    private ScheduledExecutorService executor;
    private ScheduledExecutorService presentationExecutor;
    private long generation;
    private String baseUrl = "";
    private String reservationToken = "";
    private String sessionToken = "";
    private long reservationStartedMs;
    private volatile long nextActionMs;
    private long nextPollMs;
    private long cursor;
    private volatile Object playbackOwner;
    private volatile LegacyExtenderCaptionBridge bridge;
    private volatile long playbackClockMs = -1L;
    private volatile boolean paused;
    private volatile boolean skipBufferedRecords;
    private volatile String state = "disabled";
    private volatile long receivedPacketCount;
    private volatile long lastPacketPtsMs = -1L;
    private volatile long lastPollClockMs = -1L;

    public void start(ServerInfo server, PrefStore preferences)
    {
        stop();
        if (!isConfigured(server, preferences))
            return;

        String host = server.address.trim();
        if (host.indexOf(':') >= 0 && !host.startsWith("["))
            host = "[" + host + "]";
        int port = boundedPort(preferences.getString(
                PrefStore.Keys.fixed_caption_side_channel_port,
                String.valueOf(DEFAULT_PORT)));
        startConfigured(host, port);
    }

    void startForTest(String host, int port)
    {
        stop();
        startConfigured(host, port);
    }

    private void startConfigured(String host, int port)
    {
        startWorker("http://" + host + ":" + port, "");
    }

    /** Attach to the caption tap already reserved and claimed by MIM Direct. */
    void startClaimed(String endpointBase, String claimedSessionToken)
    {
        stop();
        String safeBase = endpointBase == null ? "" : endpointBase.trim();
        String safeToken = claimedSessionToken == null ? ""
                : claimedSessionToken.trim();
        if (!safeBase.matches("https?://[^/]+") ||
                safeToken.length() < 24 || safeToken.length() > 128 ||
                !safeToken.matches("[0-9a-fA-F]+"))
            return;
        startWorker(safeBase, safeToken);
    }

    private void startWorker(String endpointBase, String claimedSessionToken)
    {
        final long activeGeneration;
        synchronized (lock)
        {
            generation++;
            activeGeneration = generation;
            baseUrl = endpointBase;
            reservationToken = "";
            sessionToken = claimedSessionToken;
            cursor = 0L;
            receivedPacketCount = 0L;
            lastPacketPtsMs = -1L;
            lastPollClockMs = -1L;
            nextActionMs = 0L;
            nextPollMs = 0L;
            state = claimedSessionToken.isEmpty() ? "probing" : "active";
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory()
            {
                @Override public Thread newThread(Runnable runnable)
                {
                    Thread thread = new Thread(runnable, "vibe-fixed-caption");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            executor.schedule(new Runnable()
            {
                @Override public void run()
                {
                    tick(activeGeneration);
                    synchronized (lock)
                    {
                        if (generation != activeGeneration || executor == null)
                            return;
                        executor.schedule(this, NETWORK_POLL_INTERVAL_MS,
                                TimeUnit.MILLISECONDS);
                    }
                }
            }, 0L, TimeUnit.MILLISECONDS);
            // HTTP may block for its existing 900ms read budget. Even a fast
            // poll can take several picture intervals, so it must never own
            // presentation. Exactly ONE separate worker emits prefetched CEA
            // pairs; the player's UI thread only publishes the real clock.
            // The bridge serializes ingestion and flushes without allowing
            // two drainers to reorder SageTV's stateful 608 control words.
            presentationExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vibe-fixed-caption-clock");
                thread.setDaemon(true);
                return thread;
            });
            presentationExecutor.schedule(new Runnable()
            {
                @Override public void run()
                {
                    LegacyExtenderCaptionBridge current;
                    boolean presenting;
                    synchronized (lock)
                    {
                        if (generation != activeGeneration || presentationExecutor == null)
                            return;
                        current = bridge;
                        presenting = current != null && playbackClockMs >= 0L
                                && !paused && !skipBufferedRecords && !sessionToken.isEmpty();
                    }
                    if (current != null && !shouldForwardSourceCaptions())
                        current.clearPending();
                    else if (presenting)
                        current.drainTo(playbackClockMs * 1000L);
                    synchronized (lock)
                    {
                        if (generation == activeGeneration && presentationExecutor != null)
                            presentationExecutor.schedule(this, presenting
                                    ? CaptionClockCadence.ACTIVE_DELAY_MS
                                    : NETWORK_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                }
            }, 0L, TimeUnit.MILLISECONDS);
        }
    }

    public void stop()
    {
        ScheduledExecutorService previous;
        ScheduledExecutorService previousPresentation;
        String previousBase;
        String previousSession;
        String previousReservation;
        synchronized (lock)
        {
            generation++;
            previous = executor;
            previousPresentation = presentationExecutor;
            previousBase = baseUrl;
            previousSession = sessionToken;
            previousReservation = reservationToken;
            executor = null;
            presentationExecutor = null;
            baseUrl = "";
            reservationToken = "";
            sessionToken = "";
            cursor = 0L;
            nextPollMs = 0L;
            receivedPacketCount = 0L;
            lastPacketPtsMs = -1L;
            lastPollClockMs = -1L;
            playbackOwner = null;
            bridge = null;
            playbackClockMs = -1L;
            paused = false;
            skipBufferedRecords = false;
            state = "disabled";
        }
        if (previousPresentation != null)
            previousPresentation.shutdown();
        if (previous != null)
        {
            final String tokenToRelease = !previousSession.isEmpty()
                    ? previousSession : previousReservation;
            if (!tokenToRelease.isEmpty())
            {
                try
                {
                    previous.execute(new Runnable()
                    {
                        @Override public void run()
                        {
                            teardown(previousBase, tokenToRelease);
                        }
                    });
                }
                catch (RuntimeException ignored) { }
            }
            previous.shutdown();
        }
    }

    public boolean attach(Object owner, LegacyExtenderCaptionBridge captionBridge)
    {
        if (owner == null || captionBridge == null)
            return false;
        synchronized (lock)
        {
            if (executor == null)
                return false;
        }
        playbackOwner = owner;
        bridge = captionBridge;
        playbackClockMs = -1L;
        paused = false;
        skipBufferedRecords = false;
        captionBridge.clearPending();
        return true;
    }

    public void detach(Object owner, boolean releaseSession)
    {
        if (owner == null || playbackOwner != owner)
            return;
        LegacyExtenderCaptionBridge current = bridge;
        playbackOwner = null;
        bridge = null;
        playbackClockMs = -1L;
        paused = false;
        skipBufferedRecords = false;
        if (current != null)
            current.clearPending();
        if (releaseSession)
            releaseActiveSession();
    }

    public void updatePlaybackClock(Object owner, long positionMs)
    {
        if (playbackOwner != owner || positionMs < 0L)
            return;
        playbackClockMs = positionMs;
        // The single presentation worker owns delivery; the HTTP worker only
        // enqueues packets. Never perform MiniClient I/O on this UI callback.
    }

    public void setPaused(Object owner, boolean value)
    {
        if (playbackOwner == owner)
            paused = value;
    }

    public void discontinuity(Object owner)
    {
        if (playbackOwner != owner)
            return;
        LegacyExtenderCaptionBridge current = bridge;
        if (current != null)
            current.clearPending();
        skipBufferedRecords = true;
    }

    public String stateForDiagnostics()
    {
        return state;
    }

    private boolean shouldForwardSourceCaptions()
    {
        Object owner = playbackOwner;
        return LegacySubtitleCallbackPolicy.shouldForwardFixedSource(
                owner instanceof MiniPlayerPlugin
                        && ((MiniPlayerPlugin) owner).hasObservedCeaCaptionData());
    }

    public long receivedPacketCountForDiagnostics()
    {
        return receivedPacketCount;
    }

    public long lastPacketPtsMsForDiagnostics()
    {
        return lastPacketPtsMs;
    }

    public long lastPollClockMsForDiagnostics()
    {
        return lastPollClockMs;
    }

    private void tick(long expectedGeneration)
    {
        String activeBase;
        String activeReservation;
        String activeSession;
        synchronized (lock)
        {
            if (generation != expectedGeneration || executor == null)
                return;
            activeBase = baseUrl;
            activeReservation = reservationToken;
            activeSession = sessionToken;
        }
        long now = System.currentTimeMillis();
        LegacyExtenderCaptionBridge activeBridge = bridge;
        long clock = playbackClockMs;
        if (now < nextActionMs)
            return;

        try
        {
            if (activeSession.isEmpty())
            {
                if (activeReservation.isEmpty())
                {
                    Response capabilities = request("GET", activeBase,
                            "/v1/capabilities");
                    if (capabilities.status != 200 || !ready(capabilities.body))
                    {
                        state = "unavailable";
                        nextActionMs = now + FAILURE_RETRY_MS;
                        return;
                    }
                    Response reserved = request("POST", activeBase,
                            "/v1/sessions/reserve");
                    if (reserved.status != 200)
                    {
                        state = "waiting_for_slot";
                        nextActionMs = now + RESERVATION_RETRY_MS;
                        return;
                    }
                    String token = token(reserved.body, "reservationToken");
                    synchronized (lock)
                    {
                        if (generation != expectedGeneration) return;
                        reservationToken = token;
                        reservationStartedMs = now;
                    }
                    state = "reserved";
                    return;
                }

                Response claimed = request("POST", activeBase,
                        "/v1/sessions/claim?reservationToken=" + activeReservation);
                if (claimed.status == 200)
                {
                    String token = token(claimed.body, "sessionToken");
                    synchronized (lock)
                    {
                        if (generation != expectedGeneration) return;
                        sessionToken = token;
                        cursor = 0L;
                    }
                    state = "active";
                    return;
                }
                if (now - reservationStartedMs >= RESERVATION_EXPIRED_MS)
                {
                    synchronized (lock)
                    {
                        if (generation == expectedGeneration)
                            reservationToken = "";
                    }
                    state = "reservation_expired";
                    nextActionMs = now + RESERVATION_RETRY_MS;
                }
                return;
            }

            if (activeBridge == null || clock < 0L || paused)
                return;
            if (now < nextPollMs)
                return;
            nextPollMs = now + NETWORK_POLL_INTERVAL_MS;

            if (skipBufferedRecords)
            {
                Response status = request("GET", activeBase,
                        "/v1/sessions/status?token=" + activeSession);
                if (status.status == 404)
                {
                    sessionEnded(expectedGeneration);
                    return;
                }
                if (status.status == 200)
                {
                    long next = number(status.body, "nextCursor", 1L);
                    synchronized (lock)
                    {
                        if (generation == expectedGeneration)
                            cursor = Math.max(0L, next - 1L);
                    }
                    skipBufferedRecords = false;
                }
                return;
            }

            long requestCursor;
            synchronized (lock) { requestCursor = cursor; }
            Response captions = request("GET", activeBase,
                    "/v1/captions?token=" + activeSession + "&cursor=" +
                            requestCursor + "&untilMs=" + (clock + 5_000L) +
                            "&limit=256");
            if (captions.status == 404)
            {
                sessionEnded(expectedGeneration);
                return;
            }
            if (captions.status != 200)
            {
                nextActionMs = now + FAILURE_RETRY_MS;
                return;
            }
            CaptionBatch batch = parseCaptions(captions.body);
            lastPollClockMs = clock;
            receivedPacketCount += batch.packets.length;
            if (batch.packets.length > 0)
                lastPacketPtsMs = batch.packets[batch.packets.length - 1].ptsMs;
            synchronized (lock)
            {
                // A response may finish after stop/restart or discontinuity.
                // Reject it before it can pollute the replacement bridge.
                if (generation != expectedGeneration || bridge != activeBridge
                        || skipBufferedRecords)
                    return;
                if (batch.reset)
                    activeBridge.clearPending();
                // Poll/advance the cursor even when the encoded output owns
                // CEA, but never accumulate or emit duplicate stateful control
                // words. On a source/seek epoch without native packets, the
                // existing bounded tap remains the fallback.
                if (shouldForwardSourceCaptions())
                {
                    for (CaptionPacket packet : batch.packets)
                        activeBridge.onCeaSample(packet.ptsMs * 1000L, packet.data);
                }
                else activeBridge.clearPending();
                cursor = batch.cursor;
            }
        }
        catch (Exception failure)
        {
            // Optional service failures are diagnostic only and must never
            // fail or rebuild the active MiniPlayer session.
            state = "unavailable";
            nextActionMs = now + FAILURE_RETRY_MS;
            log.debug("Optional Fixed caption service is unavailable ({})",
                    failure.getClass().getSimpleName());
        }
    }

    private void releaseActiveSession()
    {
        ScheduledExecutorService activeExecutor;
        final String activeBase;
        final String activeSession;
        final String activeReservation;
        synchronized (lock)
        {
            activeExecutor = executor;
            activeBase = baseUrl;
            activeSession = sessionToken;
            activeReservation = reservationToken;
            sessionToken = "";
            reservationToken = "";
            cursor = 0L;
            nextActionMs = System.currentTimeMillis() + 500L;
            state = activeExecutor == null ? "disabled" : "ready";
        }
        final String tokenToRelease = !activeSession.isEmpty()
                ? activeSession : activeReservation;
        if (activeExecutor != null && !tokenToRelease.isEmpty())
        {
            try
            {
                activeExecutor.execute(new Runnable()
                {
                    @Override public void run()
                    {
                        teardown(activeBase, tokenToRelease);
                    }
                });
            }
            catch (RuntimeException ignored) { }
        }
    }

    private void sessionEnded(long expectedGeneration)
    {
        LegacyExtenderCaptionBridge current = bridge;
        if (current != null)
            current.clearPending();
        synchronized (lock)
        {
            if (generation != expectedGeneration) return;
            sessionToken = "";
            reservationToken = "";
            cursor = 0L;
            nextActionMs = System.currentTimeMillis() + 500L;
        }
        state = "session_ended";
    }

    private static boolean isConfigured(ServerInfo server, PrefStore preferences)
    {
        if (server == null || preferences == null || server.address == null ||
                server.address.trim().isEmpty())
            return false;
        if (!"fixed".equalsIgnoreCase(preferences.getStreamingMode()))
            return false;
        if (!preferences.getBoolean(
                PrefStore.Keys.fixed_caption_side_channel_enabled, false))
            return false;
        return true;
    }

    private static int boundedPort(String value)
    {
        try
        {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= 1024 && parsed <= 65535 ? parsed : DEFAULT_PORT;
        }
        catch (NumberFormatException invalid)
        {
            return DEFAULT_PORT;
        }
    }

    private static boolean ready(String body)
    {
        if (longValue(body, "contractVersion", -1L) != CONTRACT_VERSION)
            return false;
        String state = stringValue(body, "state", "");
        return ("ready".equals(state) || "degraded".equals(state)) &&
                booleanValue(body, "reservationAvailable", false);
    }

    private static String token(String body, String name)
    {
        String token = stringValue(body, name, "").trim();
        if (token.length() < 24 || token.length() > 128 || !token.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("invalid token");
        return token;
    }

    private static long number(String body, String name, long fallback)
    {
        long value = longValue(body, name, fallback);
        return value < 0L ? fallback : value;
    }

    static CaptionBatch parseCaptions(String body)
    {
        if (longValue(body, "contractVersion", -1L) != CONTRACT_VERSION)
            throw new IllegalArgumentException("unsupported caption contract");
        String packetArray = arrayValue(body, "packets");
        List<CaptionPacket> parsed = new ArrayList<CaptionPacket>();
        Matcher matcher = PACKET_PATTERN.matcher(packetArray);
        int consumed = 0;
        while (matcher.find())
        {
            if (!onlySeparators(packetArray.substring(consumed, matcher.start())))
                throw new IllegalArgumentException("invalid caption packet list");
            long sequence = parseNonNegative(matcher.group(1));
            long ptsMs = parseNonNegative(matcher.group(2));
            byte[] data = decodeHex(matcher.group(3));
            if (sequence < 1L || ptsMs < 0L || data.length < 3 ||
                    data.length > 93 || data.length % 3 != 0)
                throw new IllegalArgumentException("invalid caption packet values");
            parsed.add(new CaptionPacket(sequence, ptsMs, data));
            consumed = matcher.end();
        }
        if (!onlySeparators(packetArray.substring(consumed)))
            throw new IllegalArgumentException("invalid caption packet list");
        long cursor = longValue(body, "cursor", -1L);
        if (cursor < 0L)
            throw new IllegalArgumentException("invalid caption cursor");
        return new CaptionBatch(parsed.toArray(new CaptionPacket[parsed.size()]),
                cursor, booleanValue(body, "reset", false));
    }

    private static byte[] decodeHex(String value)
    {
        if (value == null || value.length() == 0 || (value.length() & 1) != 0 ||
                value.length() > 186)
            throw new IllegalArgumentException("invalid caption payload");
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++)
        {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0)
                throw new IllegalArgumentException("invalid caption payload");
            bytes[index] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private static String stringValue(String body, String name, String fallback)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) +
                "\\\"\\s*:\\s*\\\"([^\\\"\\r\\n]*)\\\"").matcher(safeBody(body));
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static long longValue(String body, String name, long fallback)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) +
                "\\\"\\s*:\\s*(\\d+)").matcher(safeBody(body));
        if (!matcher.find()) return fallback;
        return parseNonNegative(matcher.group(1));
    }

    private static boolean booleanValue(String body, String name, boolean fallback)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) +
                "\\\"\\s*:\\s*(true|false)").matcher(safeBody(body));
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static String arrayValue(String body, String name)
    {
        String json = safeBody(body);
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) +
                "\\\"\\s*:\\s*\\[").matcher(json);
        if (!matcher.find())
            throw new IllegalArgumentException("missing " + name);
        int start = matcher.end() - 1;
        int depth = 0;
        boolean quoted = false;
        for (int index = start; index < json.length(); index++)
        {
            char value = json.charAt(index);
            if (value == '"' && (index == 0 || json.charAt(index - 1) != '\\'))
                quoted = !quoted;
            if (quoted) continue;
            if (value == '[') depth++;
            else if (value == ']' && --depth == 0)
                return json.substring(start + 1, index);
        }
        throw new IllegalArgumentException("unterminated " + name);
    }

    private static boolean onlySeparators(String value)
    {
        return value == null || value.trim().isEmpty() || value.matches("[\\s,]*");
    }

    private static long parseNonNegative(String value)
    {
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid)
        {
            throw new IllegalArgumentException("invalid number", invalid);
        }
    }

    private static String safeBody(String body)
    {
        if (body == null || body.length() > MAX_REPLY_BYTES)
            throw new IllegalArgumentException("invalid caption API reply");
        return body;
    }

    private static Response request(String method, String base, String path) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
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
                    throw new IOException("caption API reply exceeds limit");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        finally
        {
            input.close();
        }
    }

    private static void teardown(String base, String session)
    {
        try
        {
            request("POST", base, "/v1/sessions/teardown?token=" + session);
        }
        catch (Exception ignored) { }
    }

    static final class CaptionBatch
    {
        final CaptionPacket[] packets;
        final long cursor;
        final boolean reset;
        CaptionBatch(CaptionPacket[] packets, long cursor, boolean reset)
        {
            this.packets = packets;
            this.cursor = cursor;
            this.reset = reset;
        }
    }

    static final class CaptionPacket
    {
        final long sequence;
        final long ptsMs;
        final byte[] data;
        CaptionPacket(long sequence, long ptsMs, byte[] data)
        {
            this.sequence = sequence;
            this.ptsMs = ptsMs;
            this.data = data;
        }
    }

    private static final class Response
    {
        final int status;
        final String body;
        Response(int status, String body)
        {
            this.status = status;
            this.body = body;
        }
    }
}
