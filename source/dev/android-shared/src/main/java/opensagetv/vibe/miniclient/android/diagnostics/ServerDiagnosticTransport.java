package opensagetv.vibe.miniclient.android.diagnostics;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.regex.*;
import java.util.concurrent.*;

/** Bounded LAN support upload. No SMB credentials, redirects or playback calls. */
public final class ServerDiagnosticTransport {
    public static final int DEFAULT_PORT = 8272;
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "server-diagnostic-deadline"); thread.setDaemon(true); return thread;
    });
    private ServerDiagnosticTransport() { }

    /** Read-only bounded discovery; does not upload or enable the receiver. */
    public static boolean available(String server, int port) throws IOException {
        if(port<1024 || port>65535)throw new IOException("Invalid diagnostic port");
        InetAddress address=InetAddress.getByName(server);
        if(!address.isSiteLocalAddress() && !address.isLoopbackAddress())throw new IOException("LAN server required");
        String host=address.getHostAddress();if(host.contains(":"))host="["+host+"]";
        HttpURLConnection request=open("http://"+host+":"+port+"/vibe/diagnostics/capabilities");
        ScheduledFuture<?> deadline=DEADLINES.schedule(request::disconnect,8,TimeUnit.SECONDS);
        try {
            if(request.getResponseCode()!=200)return false;
            String text=read(request.getInputStream());
            Matcher max=Pattern.compile("\"maxBytes\"\\s*:\\s*(\\d+)").matcher(text);
            return text.matches("(?s).*\"protocol\"\\s*:\\s*1\\s*[,}].*")
                && text.matches("(?s).*\"upload\"\\s*:\\s*true\\s*[,}].*")
                && max.find() && Long.parseLong(max.group(1))>0;
        } catch(NumberFormatException malformed){return false;}
        finally{deadline.cancel(false);request.disconnect();}
    }

    public static String upload(String server, int port, String identity, File bundle,
            String sha256, BooleanSupplier allowed) throws IOException {
        if (port < 1024 || port > 65535 || !identity.matches("[0-9a-f]{12}")
                || !sha256.matches("[0-9a-f]{64}") || bundle.length() <= 0 || bundle.length() > MAX_BYTES)
            throw new IOException("Invalid diagnostic upload parameters");
        InetAddress address = InetAddress.getByName(server);
        if (!address.isSiteLocalAddress() && !address.isLoopbackAddress())
            throw new IOException("Diagnostic uploads require a LAN server");
        // Pin the actual connected server's resolved LAN address for both
        // requests. An HTTP redirect must never move a support report elsewhere.
        String host = address.getHostAddress();
        if (host.contains(":")) host = "[" + host + "]";
        String base = "http://" + host + ":" + port;
        requireAllowed(allowed);
        HttpURLConnection capabilities = open(base + "/vibe/diagnostics/capabilities");
        try {
            if (capabilities.getResponseCode() != 200) throw new IOException("Server diagnostic plugin unavailable");
            String text = read(capabilities.getInputStream());
            if (!text.matches("(?s).*\"protocol\"\\s*:\\s*1\\s*[,}].*")
                    || !text.matches("(?s).*\"upload\"\\s*:\\s*true\\s*[,}].*"))
                throw new IOException("Server diagnostic capability unsupported");
            Matcher max = Pattern.compile("\"maxBytes\"\\s*:\\s*(\\d+)").matcher(text);
            if (!max.find() || bundle.length() > Long.parseLong(max.group(1)))
                throw new IOException("Server diagnostic upload limit exceeded");
        } catch (NumberFormatException error) { throw new IOException("Invalid server diagnostic capability"); }
        finally { capabilities.disconnect(); }
        requireAllowed(allowed);
        HttpURLConnection upload = open(base + "/vibe/diagnostics/upload");
        ScheduledFuture<?> timeout = DEADLINES.schedule(upload::disconnect, 15, TimeUnit.SECONDS);
        try {
            upload.setRequestMethod("POST"); upload.setDoOutput(true);
            upload.setRequestProperty("Content-Type", "application/zip");
            upload.setRequestProperty("X-Vibe-Diagnostics", "1");
            upload.setRequestProperty("X-Vibe-Client", identity);
            upload.setRequestProperty("X-Content-SHA256", sha256);
            upload.setFixedLengthStreamingMode(bundle.length());
            long deadline = System.nanoTime() + 15_000_000_000L;
            try (InputStream input = new FileInputStream(bundle); OutputStream output = upload.getOutputStream()) {
                byte[] buffer = new byte[16 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    requireAllowed(allowed);
                    if (System.nanoTime() > deadline) throw new IOException("Diagnostic upload deadline exceeded");
                    output.write(buffer, 0, count);
                }
            }
            requireAllowed(allowed);
            if (upload.getResponseCode() != 201) throw new IOException("Diagnostic upload not stored (HTTP " + upload.getResponseCode() + ")");
            String reply = read(upload.getInputStream());
            if (!reply.matches("(?s).*\"stored\"\\s*:\\s*true\\s*[,}].*")
                    || !reply.contains(sha256)) throw new IOException("Diagnostic receipt checksum unconfirmed");
            return reply;
        } finally { timeout.cancel(false); upload.disconnect(); }
    }

    private static void requireAllowed(BooleanSupplier allowed) throws IOException {
        if (!allowed.getAsBoolean()) throw new IOException("Diagnostic upload disabled or server session changed");
    }
    private static HttpURLConnection open(String uri) throws IOException {
        HttpURLConnection result = (HttpURLConnection) new URL(uri).openConnection();
        result.setConnectTimeout(3000); result.setReadTimeout(5000);
        result.setInstanceFollowRedirects(false); result.setUseCaches(false);
        return result;
    }
    private static String read(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 8192) throw new IOException("Diagnostic response exceeded limit");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
