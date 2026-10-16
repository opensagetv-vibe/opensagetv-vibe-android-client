package opensagetv.vibe.miniclient.android.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Real sockets, not a simulated browser or physical-device claim. */
public class DiagnosticWebServerTest {
    private int freePort() throws Exception { try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } }
    private DiagnosticWebServer server(int port, AtomicInteger reads) throws Exception {
        return new DiagnosticWebServer(port, new DiagnosticWebServer.Reports() {
            public byte[] statistics() { reads.incrementAndGet(); return "heap=123\n".getBytes(StandardCharsets.UTF_8); }
            public byte[] logs() { reads.incrementAndGet(); return "<script>example</script>\n".getBytes(StandardCharsets.UTF_8); }
            public byte[] bundle() { reads.incrementAndGet(); return new byte[]{80, 75, 3, 4}; }
        });
    }
    private HttpURLConnection get(int port, String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL("http://127.0.0.1:" + port + path).openConnection();
        c.setConnectTimeout(2000); c.setReadTimeout(3000); return c;
    }
    private String text(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] data = new byte[1024]; int read;
        while ((read = in.read(data)) >= 0) out.write(data, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    @Test public void actualReadOnlyRoutesEscapeLogsAndDownload() throws Exception {
        AtomicInteger reads = new AtomicInteger(); int port = freePort();
        try (DiagnosticWebServer server = server(port, reads)) {
            HttpURLConnection c = get(port, "/");
            assertEquals(200, c.getResponseCode()); assertTrue(text(c.getInputStream()).contains("Android Vibe diagnostics")); c.disconnect();
            c = get(port, "/logs"); assertEquals(200, c.getResponseCode());
            String html = text(c.getInputStream()); assertTrue(html.contains("&lt;script&gt;")); assertFalse(html.contains("<script>"));
            assertEquals("no-store", c.getHeaderField("Cache-Control")); c.disconnect();
            c = get(port, "/stats.txt"); assertEquals(200, c.getResponseCode()); assertTrue(text(c.getInputStream()).contains("heap=123")); c.disconnect();
            c = get(port, "/bundle.zip"); assertEquals(200, c.getResponseCode());
            assertTrue(c.getHeaderField("Content-Disposition").contains("attachment")); assertEquals(4, c.getContentLength()); c.disconnect();
            assertEquals(3, reads.get());
        }
    }
    @Test public void rejectsControlArbitraryPathsAndHostnamesWithoutReadingReports() throws Exception {
        AtomicInteger reads = new AtomicInteger(); int port = freePort();
        try (DiagnosticWebServer server = server(port, reads)) {
            for (String path : new String[]{"/Sage.properties", "/logs?path=/etc/passwd", "/%2e%2e/secret", "/control"}) {
                HttpURLConnection c = get(port, path); assertEquals(404, c.getResponseCode()); c.disconnect();
            }
            HttpURLConnection c = get(port, "/bundle.zip"); c.setRequestMethod("POST");
            assertEquals(405, c.getResponseCode()); assertEquals("GET", c.getHeaderField("Allow")); c.disconnect();
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(3000);
                socket.getOutputStream().write(("GET /logs HTTP/1.1\r\nHost: attacker.example:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                assertTrue(text(socket.getInputStream()).startsWith("HTTP/1.1 403"));
            }
            assertEquals(0, reads.get());
            assertTrue(DiagnosticWebServer.lan(InetAddress.getByName("192.168.10.25")));
            assertFalse(DiagnosticWebServer.lan(InetAddress.getByName("8.8.8.8")));
        }
    }
    @Test public void stopClosesPartialRequestsAndReleasesPort() throws Exception {
        int port = freePort(); DiagnosticWebServer server = server(port, new AtomicInteger());
        Socket pending = new Socket("127.0.0.1", port); pending.setSoTimeout(3000);
        pending.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Thread.sleep(100); assertFalse(server.isClosed()); server.close(); assertTrue(server.isClosed());
        try { assertEquals(-1, pending.getInputStream().read()); } catch (SocketException reset) { }
        pending.close();
        try (DiagnosticWebServer replacement = server(port, new AtomicInteger())) { assertEquals(port, replacement.port()); }
    }
    @Test public void malformedAndOversizedHeadersAreBounded() throws Exception {
        int port = freePort(); AtomicInteger reads = new AtomicInteger();
        try (DiagnosticWebServer server = server(port, reads)) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(3000); byte[] large = new byte[8192]; java.util.Arrays.fill(large, (byte)'x');
                socket.getOutputStream().write(large); assertTrue(text(socket.getInputStream()).startsWith("HTTP/1.1 431"));
            }
            assertEquals(0, reads.get());
        }
    }
    @Test public void dashboardJsonAssetsAndOpaqueIncidentRoutesUseRealSockets() throws Exception {
        int port = freePort();
        try (DiagnosticWebServer server = new DiagnosticWebServer(port, new DiagnosticWebServer.Reports() {
            public byte[] statistics() { return new byte[0]; }
            public byte[] logs() { return new byte[0]; }
            public byte[] bundle() { return new byte[]{80,75}; }
            public byte[] data() { return "{\"playback\":{\"buffering\":true}}".getBytes(StandardCharsets.UTF_8); }
            public byte[] asset(String name) throws Exception {
                if (name.equals("index.html")) return "<html>dashboard</html>".getBytes(StandardCharsets.UTF_8);
                if (name.equals("diagnostics.js")) return "'use strict';".getBytes(StandardCharsets.UTF_8);
                throw new FileNotFoundException();
            }
            public byte[] incident(String id) throws Exception {
                if (!id.equals("1234567890abcdef")) throw new FileNotFoundException();
                return "redacted incident".getBytes(StandardCharsets.UTF_8);
            }
        })) {
            HttpURLConnection c=get(port,"/"); assertEquals(200,c.getResponseCode());
            assertEquals("<html>dashboard</html>",text(c.getInputStream())); c.disconnect();
            c=get(port,"/api/diagnostics"); assertEquals(200,c.getResponseCode());
            assertTrue(c.getContentType().startsWith("application/json"));
            assertTrue(text(c.getInputStream()).contains("\"buffering\":true")); c.disconnect();
            c=get(port,"/diagnostics.js"); assertEquals(200,c.getResponseCode());
            assertTrue(c.getHeaderField("Content-Security-Policy").contains("script-src 'self'")); c.disconnect();
            c=get(port,"/incident.txt?id=1234567890abcdef"); assertEquals(200,c.getResponseCode());
            assertEquals("redacted incident",text(c.getInputStream())); c.disconnect();
            for(String path:new String[]{"/incident.txt?id=ffffffffffffffff","/incident.txt?id=../../secret","/diagnostics.css"}) {
                c=get(port,path);assertEquals(404,c.getResponseCode());c.disconnect();
            }
        }
    }
}
