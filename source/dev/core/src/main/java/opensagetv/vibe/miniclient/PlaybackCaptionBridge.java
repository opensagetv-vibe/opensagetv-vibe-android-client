package opensagetv.vibe.miniclient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional active-load control state, not diagnostics or a media transport.
 * Never blocks MiniClient's media/GFX thread. No missing-service retries, URLs
 * supplied by the server, persistent tokens, background idle polling or MCP.
 */
final class PlaybackCaptionBridge implements AutoCloseable {
    interface Target {
        boolean current();
        default boolean active() { return true; }
        void apply(int captionState);
        void unavailable();
    }
    private static final Semaphore SLOTS = new Semaphore(2);
    private static final Pattern STATE = Pattern.compile("\\s*\\{\\s*\"protocol\"\\s*:\\s*1\\s*,"
            + "\\s*\"challenge\"\\s*:\\s*\"([0-9a-f]{32})\"\\s*,\\s*\"cc\"\\s*:\\s*([012])\\s*\\}\\s*");
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Target target;
    private volatile HttpURLConnection active;
    private Thread worker;

    private PlaybackCaptionBridge(Target target) { this.target = target; }
    static PlaybackCaptionBridge start(String server, String client, Target target) {
        return start(server, client, target, 8273);
    }
    static PlaybackCaptionBridge start(String server, String client, Target target, int port) {
        PlaybackCaptionBridge session = new PlaybackCaptionBridge(target);
        if (!SLOTS.tryAcquire()) { target.unavailable(); return session; }
        session.worker = new Thread(() -> session.run(server, client, port), "Vibe-caption-state");
        session.worker.setDaemon(true);
        session.worker.start();
        return session;
    }
    private void run(String server, String client, int port) {
        try {
            String identity = DvdCompanionCapabilities.identity(client);
            InetAddress address = InetAddress.getByName(server);
            if (!address.isLoopbackAddress() && !address.isSiteLocalAddress()) return;
            URI base = new URI("http", null, address.getHostAddress(), port,
                    "/vibe/media/captions", "client=" + identity, null);
            String first = request(base, "GET");
            String nonce = nonce(first);
            if (nonce == null || !usable()) return;
            String accepted = request(new URI(base + "&challenge=" + nonce + "&revision=1"), "POST");
            if (accepted == null || !accepted.matches("\\s*\\{\\s*\"accepted\"\\s*:\\s*true\\s*\\}\\s*")) return;
            URI state = new URI(base + "&challenge=" + nonce);
            while (usable()) {
                if (!target.active()) { Thread.sleep(1000); continue; }
                String reply = request(state, "GET");
                int cc = caption(reply, nonce);
                if (cc < 0 || !usable()) return;
                target.apply(cc);
                Thread.sleep(1000);
            }
        } catch (Exception unavailable) {
            // Missing/old/disabled service and cancellation retain stock/local
            // behavior. Never reconnect playback to obtain an optional state.
        } finally {
            try { if (!closed.get() && target.current()) target.unavailable(); }
            catch (RuntimeException ignored) { /* Optional state cannot terminate Android playback. */ }
            SLOTS.release();
        }
    }
    private boolean usable() { return !closed.get() && target.current(); }
    static String nonce(String reply) {
        if (reply == null || reply.length() > 256) return null;
        Matcher matcher = STATE.matcher(reply);
        return matcher.matches() ? matcher.group(1) : null;
    }
    static int caption(String reply, String expected) {
        if (expected == null || !expected.equals(nonce(reply))) return -1;
        Matcher matcher = STATE.matcher(reply);
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : -1;
    }
    private String request(URI uri, String method) throws Exception {
        if (!usable()) return null;
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection(Proxy.NO_PROXY);
        active = connection;
        connection.setConnectTimeout(500); connection.setReadTimeout(500);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestMethod(method);
        long deadline = System.nanoTime() + 2000000000L;
        try {
            if (!usable() || connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                for (int value; (value = input.read()) != -1;) {
                    if (out.size() >= 256 || System.nanoTime() > deadline || !usable()) return null;
                    out.write(value);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            active = null;
            connection.disconnect();
        }
    }
    @Override public void close() {
        closed.set(true);
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
        if (worker != null) worker.interrupt();
    }
}
