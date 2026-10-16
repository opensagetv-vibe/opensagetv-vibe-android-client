package opensagetv.vibe.miniclient.android.diagnostics;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.*;

/** Small Android-compatible, read-only HTTP transport. No JDK HttpServer,
 * external library, arbitrary filesystem route, playback command or upload.
 * The Android lifecycle owner alone decides whether to open this listener.
 */
public final class DiagnosticWebServer implements AutoCloseable {
    public interface Reports {
        byte[] statistics() throws Exception;
        byte[] logs() throws Exception;
        byte[] bundle() throws Exception;
        default byte[] data() throws Exception { return bytes("{}"); }
        default byte[] asset(String name) throws Exception { throw new FileNotFoundException(); }
        default byte[] incident(String id) throws Exception { throw new FileNotFoundException(); }
    }
    private static final int MAX_HEADER = 8192;
    private static final int MAX_RESPONSE = 8 * 1024 * 1024;
    private final Reports reports;
    private final ServerSocket listener = new ServerSocket();
    private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(4),
            r -> daemon(r, "vibe-diagnostic-web"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            r -> daemon(r, "vibe-diagnostic-web-deadline"));
    private volatile boolean closed;

    public DiagnosticWebServer(int port, Reports reports) throws IOException {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Port must be 1024-65535");
        this.reports = reports;
        try {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress("0.0.0.0", port), 4);
            daemon(this::accept, "vibe-diagnostic-web-accept").start();
        } catch (IOException failure) { close(); throw failure; }
    }
    public int port() { return listener.getLocalPort(); }
    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
    }
    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                sockets.add(socket);
                if (closed) { finish(socket); break; }
                socket.setSoTimeout(2000);
                // This includes time spent waiting for a worker and bounds slow
                // headers and receivers, not just the next socket read.
                ScheduledFuture<?> expiry = deadlines.schedule(() -> finish(socket), 10, TimeUnit.SECONDS);
                try { workers.execute(() -> {
                    try { serve(socket); }
                    catch (Exception ignored) { /* Never expose exceptions/paths/secrets. */ }
                    finally { expiry.cancel(false); finish(socket); }
                }); } catch (RejectedExecutionException busy) { expiry.cancel(false); finish(socket); }
            } catch (IOException | RejectedExecutionException stopped) {
                if (!closed) close();
            }
        }
    }
    private void serve(Socket socket) throws Exception {
        if (!lan(socket.getInetAddress())) { send(socket, 403, "text/plain", bytes("LAN access only"), false); return; }
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (header.size() < MAX_HEADER) {
            int c = input.read();
            if (c < 0) return;
            header.write(c);
            // Require CRLF framing; no unbounded BufferedReader/readLine.
            if ((matched == 0 || matched == 2) && c == '\r') matched++;
            else if ((matched == 1 || matched == 3) && c == '\n') matched++;
            else matched = c == '\r' ? 1 : 0;
            if (matched == 4) break;
        }
        if (matched != 4) { send(socket, 431, "text/plain", bytes("Header too large"), false); return; }
        String[] lines = new String(header.toByteArray(), StandardCharsets.US_ASCII).split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3 || !request[2].matches("HTTP/1\\.[01]")) {
            send(socket, 400, "text/plain", bytes("Invalid request"), false); return;
        }
        if (!"GET".equals(request[0])) { send(socket, 405, "text/plain", bytes("Read-only: GET only"), false); return; }
        String host = null;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) { send(socket, 400, "text/plain", bytes("Invalid header"), false); return; }
            String key = lines[i].substring(0, colon).trim();
            if ("Host".equalsIgnoreCase(key)) {
                if (host != null) { send(socket, 400, "text/plain", bytes("Duplicate host"), false); return; }
                host = lines[i].substring(colon + 1).trim();
            }
            if ("Origin".equalsIgnoreCase(key) || "Sec-Fetch-Site".equalsIgnoreCase(key)) {
                String value = lines[i].substring(colon + 1).trim();
                if ("Sec-Fetch-Site".equalsIgnoreCase(key) && "cross-site".equalsIgnoreCase(value)) {
                    send(socket, 403, "text/plain", bytes("Open the device address directly"), false); return;
                }
            }
        }
        // Numeric local-address Host rejects DNS-rebinding hostnames. No DNS,
        // redirects, CORS, remote URLs or arbitrary path parameters are used.
        String local = socket.getLocalAddress().getHostAddress();
        if (!(host != null && (host.equals(local + ":" + port())
                || host.equals(local) || (socket.getInetAddress().isLoopbackAddress()
                && host.equals("localhost:" + port()))))) {
            send(socket, 403, "text/plain", bytes("Use the device IP address"), false); return;
        }
        String path = request[1];
        try {
            if ("/".equals(path)) send(socket, 200, "text/html", index(), false);
            else if ("/diagnostics.js".equals(path)) send(socket, 200, "application/javascript", bounded(reports.asset("diagnostics.js"),128*1024), false);
            else if ("/diagnostics.css".equals(path)) send(socket, 200, "text/css", bounded(reports.asset("diagnostics.css"),32*1024), false);
            else if ("/api/diagnostics".equals(path)) send(socket, 200, "application/json", bounded(reports.data(),512*1024), false);
            else if (path.matches("/incident\\.txt\\?id=[0-9a-f]{16}")) {
                send(socket,200,"text/plain",bounded(reports.incident(path.substring(path.indexOf('=')+1)),512*1024),false);
            }
            else if ("/stats".equals(path)) send(socket, 200, "text/html", preview("Client statistics", reports.statistics(), 64 * 1024), false);
            else if ("/logs".equals(path)) send(socket, 200, "text/html", preview("Client logs", reports.logs(), 2 * 1024 * 1024), false);
            else if ("/stats.txt".equals(path)) send(socket, 200, "text/plain", bounded(reports.statistics(), 64 * 1024), false);
            else if ("/logs.txt".equals(path)) send(socket, 200, "text/plain", bounded(reports.logs(), 2 * 1024 * 1024), false);
            else if ("/bundle.zip".equals(path)) send(socket, 200, "application/zip", bounded(reports.bundle(), MAX_RESPONSE), true);
            else send(socket, 404, "text/plain", bytes("Not found"), false);
        } catch (FileNotFoundException missing) { send(socket,404,"text/plain",bytes("Report or asset unavailable"),false); }
        catch (Exception unavailable) { send(socket, 503, "text/plain", bytes("Report unavailable; local logs retained"), false); }
    }
    private byte[] index() throws Exception {
        try { return bounded(reports.asset("index.html"),64*1024); }
        catch (FileNotFoundException absent) { return bytes(page("Android Vibe diagnostics",
                    "<p>Read-only diagnostics from this Android device. Refresh for a new snapshot.</p>"
                    + "<p><a href='/stats'>Statistics</a> | <a href='/logs'>Logs</a> | <a href='/bundle.zip'>Download diagnostic ZIP</a></p>"
                    + "<p>Enabled only while Vibe is in the foreground. No server plugin is needed."
                    + " Trusted LAN only: no login or encryption; do not expose this port to the Internet.</p>")); }
    }
    private static byte[] preview(String title, byte[] text, int maximum) throws IOException {
        String content = escape(new String(bounded(text, maximum), StandardCharsets.UTF_8));
        return bytes(page(title, "<p><a href='/'>Diagnostics</a> | <a href='/bundle.zip'>Download ZIP</a></p><pre>" + content + "</pre>"));
    }
    private static byte[] bounded(byte[] value, int max) throws IOException {
        if (value == null || value.length > max) throw new IOException("Report size limit"); return value;
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                + "<title>" + escape(title) + "</title><style>body{background:#17212b;color:#eee;font:16px sans-serif;margin:1em}"
                + "a{color:#84caff}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head><body><h1>"
                + escape(title) + "</h1>" + body + "</body></html>";
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    public boolean isClosed() { return closed; }
    static boolean lan(InetAddress peer) {
        return peer instanceof Inet4Address && (peer.isLoopbackAddress() || peer.isSiteLocalAddress());
    }
    private static void send(Socket socket, int code, String type, byte[] body, boolean zip) throws IOException {
        bounded(body, MAX_RESPONSE);
        String header = "HTTP/1.1 " + code + " Response\r\nContent-Type: " + type
                + (zip ? "" : "; charset=utf-8") + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n"
                + (code == 405 ? "Allow: GET\r\n" : "")
                + (zip ? "Content-Disposition: attachment; filename=\"OpenSageTV-Vibe-diagnostics.zip\"\r\n" : "") + "\r\n";
        OutputStream out = socket.getOutputStream(); out.write(bytes(header)); out.write(body); out.flush();
    }
    private void finish(Socket socket) { sockets.remove(socket); try { socket.close(); } catch (IOException ignored) { } }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket socket : sockets) finish(socket);
        workers.shutdownNow(); deadlines.shutdownNow();
    }
}
