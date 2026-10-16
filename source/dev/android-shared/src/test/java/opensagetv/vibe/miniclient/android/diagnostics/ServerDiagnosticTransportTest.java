package opensagetv.vibe.miniclient.android.diagnostics;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import static org.junit.Assert.*;

public class ServerDiagnosticTransportTest {
    // Android's test compiler uses the Android boot classpath and cannot see
    // JDK-specific com.sun HttpServer. This small real-loopback fixture uses
    // only portable java.net/io; no test library is added to the APK graph.
    private static final class Headers extends HashMap<String,String> {
        String getFirst(String name) { return get(name.toLowerCase(Locale.ROOT)); }
        void set(String name, String value) { put(name.toLowerCase(Locale.ROOT), value); }
    }
    private interface Handler { void run(Exchange exchange) throws IOException; }
    private static final class Exchange {
        final Socket socket; final InputStream input; final Headers request = new Headers(), response = new Headers();
        final String method, path;
        Exchange(Socket socket) throws IOException {
            this.socket = socket; input = socket.getInputStream();
            String[] first = line(input).split(" "); method = first[0]; path = first[1];
            String row;
            while (!(row = line(input)).isEmpty()) {
                int colon = row.indexOf(':'); request.set(row.substring(0, colon), row.substring(colon + 1).trim());
            }
        }
        String getRequestMethod() { return method; }
        Headers getRequestHeaders() { return request; }
        Headers getResponseHeaders() { return response; }
        InputStream getRequestBody() { return input; }
        OutputStream getResponseBody() throws IOException { return socket.getOutputStream(); }
        void sendResponseHeaders(int status, long length) throws IOException {
            StringBuilder text = new StringBuilder("HTTP/1.1 " + status + " Test\r\nConnection: close\r\nContent-Length: " + Math.max(0, length) + "\r\n");
            for (Map.Entry<String,String> header : response.entrySet()) text.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            socket.getOutputStream().write(text.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
        }
        void close() throws IOException { socket.close(); }
        static String line(InputStream input) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); int value;
            while ((value = input.read()) != -1 && value != '\n') {
                if (out.size() > 8192) throw new IOException("Test header too large");
                if (value != '\r') out.write(value);
            }
            if (value == -1) throw new EOFException();
            return new String(out.toByteArray(), StandardCharsets.US_ASCII);
        }
    }
    private static final class HttpServer {
        final ServerSocket socket; final Map<String,Handler> routes = new HashMap<>();
        volatile Throwable failure;
        private HttpServer(InetSocketAddress address) throws IOException { socket = new ServerSocket(); socket.bind(address); }
        static HttpServer create(InetSocketAddress address, int ignored) throws IOException { return new HttpServer(address); }
        void createContext(String path, Handler handler) { routes.put(path, handler); }
        InetSocketAddress getAddress() { return (InetSocketAddress) socket.getLocalSocketAddress(); }
        void start() {
            Thread thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket peer = socket.accept()) {
                        peer.setSoTimeout(2000); Exchange exchange = new Exchange(peer);
                        Handler handler = routes.get(exchange.path);
                        if (handler == null) exchange.sendResponseHeaders(404, -1); else handler.run(exchange);
                    } catch (Throwable error) { if (!socket.isClosed()) failure = error; }
                }
            }); thread.setDaemon(true); thread.start();
        }
        void stop(int ignored) throws IOException { socket.close(); if (failure != null) throw new AssertionError(failure); }
    }
    private static final String SHA = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    private static File fixture() throws IOException {
        Path path = Files.createTempFile("vibe-diagnostic-http-", ".zip");
        Files.write(path, new byte[]{'P', 'K', 3, 4}); return path.toFile();
    }
    @Test public void verifiedReceiptAndHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger uploads = new AtomicInteger(); File file = fixture();
        server.createContext("/vibe/diagnostics/capabilities", exchange -> {
            byte[] body = "{\"protocol\":1,\"upload\":true,\"maxBytes\":8388608}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/vibe/diagnostics/upload", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("1", exchange.getRequestHeaders().getFirst("X-Vibe-Diagnostics"));
            assertEquals("abcdef123456", exchange.getRequestHeaders().getFirst("X-Vibe-Client"));
            assertEquals(SHA, exchange.getRequestHeaders().getFirst("X-Content-SHA256"));
            assertEquals(4, exchange.getRequestBody().read(new byte[8])); uploads.incrementAndGet();
            byte[] body = ("{\"stored\":true,\"sha256\":\"" + SHA + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            assertTrue(ServerDiagnosticTransport.upload("127.0.0.1", server.getAddress().getPort(),
                    "abcdef123456", file, SHA, () -> true).contains(SHA));
            assertEquals(1, uploads.get());
        } finally { server.stop(0); Files.delete(file.toPath()); }
    }
    @Test public void availabilityIsGetOnlyAndRejectsUnsupportedReceiver() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicBoolean enabled=new AtomicBoolean(true);AtomicInteger calls=new AtomicInteger();
        server.createContext("/vibe/diagnostics/capabilities",exchange->{
            assertEquals("GET",exchange.getRequestMethod());calls.incrementAndGet();
            byte[] body=("{\"protocol\":1,\"upload\":"+enabled.get()+",\"maxBytes\":8388608}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try{assertTrue(ServerDiagnosticTransport.available("127.0.0.1",server.getAddress().getPort()));enabled.set(false);
            assertFalse(ServerDiagnosticTransport.available("127.0.0.1",server.getAddress().getPort()));assertEquals(2,calls.get());}
        finally{server.stop(0);}
    }
    @Test public void disableBetweenDiscoveryAndUploadStopsWrites() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean allowed = new AtomicBoolean(true); AtomicInteger uploads = new AtomicInteger(); File file = fixture();
        server.createContext("/vibe/diagnostics/capabilities", exchange -> {
            allowed.set(false);
            byte[] body = "{\"protocol\":1,\"upload\":true,\"maxBytes\":8388608}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/vibe/diagnostics/upload", exchange -> { uploads.incrementAndGet(); exchange.close(); });
        server.start();
        try {
            try { ServerDiagnosticTransport.upload("127.0.0.1", server.getAddress().getPort(), "abcdef123456", file, SHA, allowed::get); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("disabled")); }
            assertEquals(0, uploads.get()); assertTrue(file.isFile());
        } finally { server.stop(0); Files.delete(file.toPath()); }
    }
    @Test public void missingOrRedirectedCapabilityNeverUploads() throws Exception {
        for (int status : new int[]{404, 302}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicInteger uploads = new AtomicInteger(); File file = fixture();
            server.createContext("/vibe/diagnostics/capabilities", exchange -> {
                exchange.getResponseHeaders().set("Location", "/vibe/diagnostics/upload");
                exchange.sendResponseHeaders(status, -1); exchange.close();
            });
            server.createContext("/vibe/diagnostics/upload", exchange -> { uploads.incrementAndGet(); exchange.close(); }); server.start();
            try {
                // The chooser must keep missing or redirecting receivers gray,
                // not merely leave the later upload to fail. Discovery is GET
                // only and must not follow Location into the upload route.
                assertFalse(ServerDiagnosticTransport.available("127.0.0.1", server.getAddress().getPort()));
                try { ServerDiagnosticTransport.upload("127.0.0.1", server.getAddress().getPort(), "abcdef123456", file, SHA, () -> true); fail(); }
                catch (IOException expected) { assertTrue(expected.getMessage().contains("unavailable")); }
                assertEquals(0, uploads.get()); assertTrue(file.isFile());
            } finally { server.stop(0); Files.delete(file.toPath()); }
        }
    }
    @Test public void offPublicAddressAndInvalidInputsFailBeforeNetwork() throws Exception {
        File file = fixture();
        try {
            for (String address : new String[]{"127.0.0.1", "8.8.8.8"}) {
                try { ServerDiagnosticTransport.upload(address, 8272, "abcdef123456", file, SHA, () -> false); fail(); }
                catch (IOException expected) { assertTrue(file.isFile()); }
            }
            try { ServerDiagnosticTransport.upload("127.0.0.1", 1, "bad", file, SHA, () -> true); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("parameters")); }
        } finally { Files.delete(file.toPath()); }
    }
}
