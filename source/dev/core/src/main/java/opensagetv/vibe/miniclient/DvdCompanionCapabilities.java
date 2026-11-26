package opensagetv.vibe.miniclient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional per-DVD-load handshake with the stock-compatible companion.
 * Never blocks the media thread or changes the saved player/transport. A
 * missing, old or disabled companion leaves ordinary native DVD untouched.
 * This acknowledges representation support only; it cannot control playback.
 */
final class DvdCompanionCapabilities {
    private static final Pattern CHALLENGE = Pattern.compile("\"challenge\"\\s*:\\s*\"([0-9a-f]{32})\"");
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 15,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2), runnable -> {
                Thread thread = new Thread(runnable, "Vibe-DVD-capability");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private DvdCompanionCapabilities() { }

    static void advertise(String server, String client) {
        final String identity;
        try { identity = identity(client); }
        catch (IllegalArgumentException invalid) { return; }
        WORKER.execute(() -> {
            try {
                InetAddress address = InetAddress.getByName(server);
                if (!address.isLoopbackAddress() && !address.isSiteLocalAddress()) return;
                URI uri = new URI("http", null, address.getHostAddress(), 8273,
                        "/vibe/dvd/client", "client=" + identity, null);
                String reply = request(uri, "GET");
                String nonce = challenge(reply);
                if (nonce == null) return;
                request(new URI(uri.toString() + "&revision=3&challenge=" + nonce), "POST");
            } catch (Exception unavailable) {
                // No retries, player rebuild, Core event or diagnostic/MCP
                // dependency. Native is the server's bounded safe fallback.
            }
        });
    }
    static String identity(String client) {
        if (client == null || !client.matches("(?i)([0-9a-f]{12}|([0-9a-f]{2}:){5}[0-9a-f]{2})"))
            throw new IllegalArgumentException("Exact client identity required");
        return client.replace(":", "").toLowerCase(Locale.ROOT);
    }
    static String challenge(String reply) {
        if (reply == null || reply.length() > 256 || !reply.matches("(?s).*\"protocol\"\\s*:\\s*1\\s*[,}].*")) return null;
        Matcher match = CHALLENGE.matcher(reply);
        return match.find() ? match.group(1) : null;
    }
    private static String request(URI uri, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(500); connection.setReadTimeout(500);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestMethod(method);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            if (connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                for (int value; (value = input.read()) != -1;) {
                    if (body.size() >= 256 || System.nanoTime() > deadline) return null;
                    body.write(value);
                }
                return new String(body.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
