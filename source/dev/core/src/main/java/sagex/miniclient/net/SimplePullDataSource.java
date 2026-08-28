package sagex.miniclient.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

import sagex.miniclient.MiniClientConnection;
import sagex.miniclient.util.DataCollector;
import sagex.miniclient.util.VerboseLogging;

/**
 * Created by seans on 20/12/15.
 */
public class SimplePullDataSource implements ISageTVDataSource {
    Logger log = LoggerFactory.getLogger(SimplePullDataSource.class);

    Socket remoteServer;
    String uri;
    String host;
    DataInputStream remoteReader;
    OutputStream remoteWriter;

    DataCollector dataCollector = null;

    boolean opened = false;
    long size = 0;

    // Diagnostic-only counters for synchronous SageTV READ traffic. These do not
    // change the pull protocol; they let debug builds distinguish network READ
    // latency from extractor/decoder recovery time after large seeks.
    private final AtomicLong networkReadCount = new AtomicLong();
    private final AtomicLong networkReadRequestedBytes = new AtomicLong();
    private final AtomicLong networkReadBytes = new AtomicLong();
    private final AtomicLong networkReadWaitNanos = new AtomicLong();
    private final AtomicLong networkReadMaxRequestedBytes = new AtomicLong();
    private final AtomicLong networkReadErrors = new AtomicLong();
    private final AtomicLong networkLastReadPosition = new AtomicLong(-1);

    public SimplePullDataSource() {
    }

    public SimplePullDataSource(String host) {
        this.host=host;
    }


    public String getUri() {
        return uri;
    }

    @Override
    public long open(String uri) throws IOException {
        if (opened) {
            throw new IOException("Attempting to re-open an OPENED datasource for uri " + uri);
        }
        try {
            String host = getHost(uri);
            this.uri = uri;

            log.debug("Open(): {} on host {}", uri, host);

            remoteServer = new Socket();
            remoteServer.connect(new java.net.InetSocketAddress(host, 7818), 2000);
            this.remoteReader = new DataInputStream(remoteServer.getInputStream());
            this.remoteWriter = remoteServer.getOutputStream();

            sendStringCommandWithReply("OPEN " + getPath(uri));
            String strSize = sendStringCommandWithReply("SIZE");
            if (strSize != null) {
                try {
                    size = Long.parseLong(strSize.split(" ")[0]);
                } catch (Throwable t) {
                    log.error("Failed to get Size", t);
                    size = -1;
                }
            }
            log.debug("SIZE got {} for {}", size, uri);
            opened = true;

            if (VerboseLogging.LOG_DATASOURCE_BYTES_TO_FILE) {
                try {
                    dataCollector = new DataCollector();
                    dataCollector.open();
                } catch (Throwable t) {
                    log.error("Failed to open the DataCollector", t);
                    dataCollector = null;
                }
            }
        } catch (Throwable t) {
            log.error("Unable to open: {}", uri, t);
        }

        return size;
    }

    String getPath(String uri) {
        if (uri == null) return null;
        if (!uri.contains("stv://")) return uri;
        int pos = uri.indexOf("/", "stv://".length());
        log.debug("PATH: {}({})", uri, pos);
        return uri.substring(pos + 1);
    }

    String getHost(String uri) {
        if (uri == null) return null;
        if (uri.startsWith("stv:")) {
            int s = "stv://".length();
            int pos = uri.indexOf("/", s);
            if (pos != -1) {
                return uri.substring(s, pos);
            }
        }
        return host;
    }

    @Override
    public synchronized void close() {
        log.debug("Close()");
        try {
            if (remoteServer != null) {
                try {
                    sendStringCommandWithReply("CLOSE");
                } catch (Throwable t) {
                }
                remoteServer.close();

            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
        remoteServer = null;
        remoteReader = null;
        remoteWriter = null;

        opened = false;

        if (dataCollector != null) {
            dataCollector.close();
        }
    }

    @Override
    public long size() {
        return size;
    }

    public boolean isOpen() {
        return opened;
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int len) throws IOException {
        if (!opened) {
            throw new IOException("read() called on DataSource that is not opened: " + uri);
        }

        return fetch(position, buffer, offset, len);
    }

    public int fetch(long position, byte[] buffer, int offset, int len) throws IOException {
        if (!opened) {
            throw new IOException("read() called on DataSource that is not opened: " + uri);
        }

        if (len < 0) return -1;
        if (position > size) return -1;

        // just the case where we are being asked to read 0 bytes.
        if (len == 0) return 0;

        networkReadCount.incrementAndGet();
        networkReadRequestedBytes.addAndGet(len);
        networkLastReadPosition.set(position);
        updateMax(networkReadMaxRequestedBytes, len);
        long readStartedNanos = System.nanoTime();
        try {
            String cmd = ("READ " + String.valueOf(position) + " " + String.valueOf(len));
            if (VerboseLogging.DATASOURCE_LOGGING)
                log.debug("read(): position:{}, offset:{}, len: {}, buffersize: {}; COMMAND: {}", position, offset, len, buffer.length, cmd);
            remoteWriter.write((cmd + "\r\n").getBytes());
            remoteWriter.flush();
            int bytes = readBuffer(buffer, offset, len);
            if (bytes == -1) {
                log.debug("EOF for {}", uri);
                return -1;
            }
            if (bytes > 0) networkReadBytes.addAndGet(bytes);
            if (dataCollector != null) {
                try {
                    dataCollector.write(buffer, offset, bytes);
                } catch (Throwable t) {
                    log.error("Failed to write to the data collector", t);
                    dataCollector = null;
                }
            }
            return bytes;
        } catch (IOException e) {
            networkReadErrors.incrementAndGet();
            throw e;
        } finally {
            networkReadWaitNanos.addAndGet(System.nanoTime() - readStartedNanos);
        }
    }

    private static void updateMax(AtomicLong target, long value) {
        long current = target.get();
        while (value > current && !target.compareAndSet(current, value)) {
            current = target.get();
        }
    }

    public long getNetworkReadCount() { return networkReadCount.get(); }
    public long getNetworkReadRequestedBytes() { return networkReadRequestedBytes.get(); }
    public long getNetworkReadBytes() { return networkReadBytes.get(); }
    public long getNetworkReadWaitMs() { return networkReadWaitNanos.get() / 1000000L; }
    public long getNetworkReadMaxRequestedBytes() { return networkReadMaxRequestedBytes.get(); }
    public long getNetworkReadErrors() { return networkReadErrors.get(); }
    public long getNetworkLastReadPosition() { return networkLastReadPosition.get(); }

    int readBuffer(byte[] buffer, int offset, int len) throws IOException {
        int total = 0;
        int read = 0;
        while (total < len) {
            if (VerboseLogging.DATASOURCE_LOGGING && log.isDebugEnabled())
                log.debug("read packet: total: {}, len: {}, delta: {}", total, len, (len - total));
            read = remoteReader.read(buffer, offset + total, len - total);
            if (read == -1) {
                if (total == 0) {
                    log.warn("End of File reached for {}", uri);
                    return -1;
                } else {
                    return total;
                }
            }
            total += read;
        }
        if (VerboseLogging.DATASOURCE_LOGGING) log.debug("Filled buffer with {} bytes", total);
        return total;
    }

    private String sendStringCommandWithReply(String cmd) throws IOException {
        remoteWriter.write((cmd + "\r\n").getBytes());
        remoteWriter.flush();
        byte buf[] = new byte[1024];
        int total = 0;
        while (true) {
            byte b = remoteReader.readByte();
            if (b == '\r') continue;
            if (b == '\n') break;
            buf[total++] = b;
        }
        String val = new String(buf, 0, total);
        log.debug("Send Command: {}, Got Bytes {} Back with data [{}]", cmd, total, val);
        return val;
    }
}
