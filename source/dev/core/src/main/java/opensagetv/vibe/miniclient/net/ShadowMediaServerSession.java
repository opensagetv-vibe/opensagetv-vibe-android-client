package opensagetv.vibe.miniclient.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Keeps SageTV's MediaServer file/session open while another datasource owns
 * media bytes. A single explicitly requested bounded probe read is supported
 * for stock servers that do not activate STV seek/Comskip control after only
 * OPEN + SIZE; it is never used as a media transport.
 */
public final class ShadowMediaServerSession
{
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private boolean connected;
    private boolean openSent;
    private boolean sizeSent;
    private long size = -1;
    private long readBytes;

    public ShadowMediaServerSession(String host)
    {
        this(host, 7818, 3000, 5000);
    }

    ShadowMediaServerSession(String host, int port, int connectTimeoutMs, int readTimeoutMs)
    {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public synchronized long open(String sageUri) throws IOException
    {
        if (connected) throw new IOException("Shadow MediaServer session already open");
        String originalPath = SmbPathMapper.extractOriginalPath(sageUri);
        if (originalPath == null || originalPath.isEmpty())
            throw new IOException("Shadow MediaServer path is empty");

        Socket newSocket = new Socket();
        try
        {
            newSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            newSocket.setSoTimeout(readTimeoutMs);
            socket = newSocket;
            input = socket.getInputStream();
            output = socket.getOutputStream();
            connected = true;
            command("OPEN " + originalPath);
            openSent = true;
            size = parseSize(command("SIZE"));
            sizeSent = true;
            return size;
        }
        catch (IOException e)
        {
            closeQuietly(false);
            throw e;
        }
    }

    public synchronized long refreshSize() throws IOException
    {
        ensureConnected();
        size = parseSize(command("SIZE"));
        sizeSent = true;
        return size;
    }

    /**
     * Performs one byte-position synchronization read. Callers invoke this
     * only for a non-sequential SMB read and must expose the accumulated byte
     * count; normal playback bytes still come from SMB.
     */
    public synchronized int probeRead(long position) throws IOException
    {
        ensureConnected();
        if (size <= 0) return 0;
        long safePosition = Math.max(0, Math.min(position, size - 1));
        byte[] command = ("READ " + safePosition + " 1\r\n").getBytes(StandardCharsets.UTF_8);
        output.write(command);
        output.flush();
        int value = input.read();
        if (value < 0) throw new IOException("MediaServer closed during shadow activation probe");
        readBytes += 1;
        return 1;
    }

    public synchronized void close()
    {
        closeQuietly(true);
    }

    private void closeQuietly(boolean sendClose)
    {
        if (sendClose && connected && output != null)
        {
            try { command("CLOSE"); }
            catch (IOException ignored) { }
        }
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) { }
        socket = null;
        input = null;
        output = null;
        connected = false;
    }

    private String command(String command) throws IOException
    {
        ensureConnected();
        output.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
        String response = readLine();
        if (response == null) throw new IOException("MediaServer closed after " + command.split(" ", 2)[0]);
        return response;
    }

    private void ensureConnected() throws IOException
    {
        if (!connected || socket == null || input == null || output == null)
            throw new IOException("Shadow MediaServer session is not connected");
    }

    private String readLine() throws IOException
    {
        StringBuilder line = new StringBuilder();
        while (true)
        {
            int value = input.read();
            if (value < 0) return line.length() == 0 ? null : line.toString();
            if (value == '\n') return line.toString();
            if (value != '\r') line.append((char) (value & 0xff));
            if (line.length() > 1024) throw new IOException("MediaServer response line is too long");
        }
    }

    private static long parseSize(String response) throws IOException
    {
        try { return Long.parseLong(response.trim().split(" ", 2)[0]); }
        catch (RuntimeException e) { throw new IOException("Invalid MediaServer SIZE response", e); }
    }

    public synchronized boolean isConnected() { return connected; }
    public synchronized boolean isOpenSent() { return openSent; }
    public synchronized boolean isSizeSent() { return sizeSent; }
    public synchronized long getSize() { return size; }
    public synchronized long getReadBytes() { return readBytes; }
}
