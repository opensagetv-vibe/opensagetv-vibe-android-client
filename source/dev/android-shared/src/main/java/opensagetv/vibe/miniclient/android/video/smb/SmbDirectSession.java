package opensagetv.vibe.miniclient.android.video.smb;

import android.os.Looper;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;
import opensagetv.vibe.miniclient.net.ShadowMediaServerSession;
import opensagetv.vibe.miniclient.net.SmbMappedPath;
import opensagetv.vibe.miniclient.net.SmbPathMapper;

/** One playback-owned SMB file plus its minimally active shadow MediaServer session. */
public final class SmbDirectSession implements ISageTVDataSource, GrowingDataSource
{
    private final String sageServerHost;
    private final SmbDirectConfig config;
    private final SmbPathMapper mapper;
    private final AtomicBoolean releaseRequested = new AtomicBoolean();
    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = new Thread(runnable, "SmbDirect-Cleanup");
            thread.setDaemon(true);
            return thread;
        }
    });

    private ShadowMediaServerSession shadow;
    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;
    private com.hierynomus.smbj.share.File file;
    private SmbMappedPath mappedPath;
    private String sageOriginalPath = "";
    private boolean open;
    private long size = -1;
    private long bytesRead;
    private long readCount;
    private long seekCount;
    private long lastReadEnd = -1;
    private long lastReadLatencyMs;

    public SmbDirectSession(String sageServerHost, SmbDirectConfig config)
    {
        this.sageServerHost = sageServerHost;
        this.config = config;
        this.mapper = SmbPathMapper.parse(config.getMappings());
    }

    @Override
    public synchronized long open(String sageUri) throws IOException
    {
        String original = SmbPathMapper.extractOriginalPath(sageUri);
        if (open)
        {
            if (!sageOriginalPath.equals(original))
                throw new IOException("SMB Direct session cannot switch files without release");
            return size;
        }

        SmbMappedPath mapped = mapper.map(original);
        if (mapped == null) throw new IOException("No SMB mapping matches the SageTV media path");
        mappedPath = mapped;
        sageOriginalPath = original;

        try
        {
            shadow = new ShadowMediaServerSession(sageServerHost);
            shadow.open(original);

            SmbConfig smbConfig = SmbConfig.builder()
                    // SMB playback is intentionally bursty: ExoPlayer fills its local
                    // buffer, then may issue no network request while that buffer drains.
                    // A finite SO_TIMEOUT treats that healthy idle period as a dead socket;
                    // SMBJ closes the tree and the next read fails with "DiskShare has
                    // already been closed". Keep the socket receiver blocking between
                    // bursts, while retaining bounded per-request timeouts.
                    .withTimeout(30, TimeUnit.SECONDS)
                    .withSoTimeout(0)
                    .build();
            client = new SMBClient(smbConfig);
            connection = client.connect(mapped.getServer(), mapped.getPort());
            char[] password = config.copyPassword();
            try
            {
                AuthenticationContext authentication = config.getUsername().isEmpty()
                        ? AuthenticationContext.guest()
                        : new AuthenticationContext(config.getUsername(), password, config.getDomain());
                session = connection.authenticate(authentication);
            }
            finally
            {
                java.util.Arrays.fill(password, '\0');
            }
            share = (DiskShare) session.connectShare(mapped.getShare());
            file = share.openFile(
                    mapped.getPath().replace('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
            size = refreshSmbSize();
            open = true;
            return size;
        }
        catch (Throwable t)
        {
            close();
            if (t instanceof IOException) throw (IOException) t;
            throw new IOException("SMB Direct open failed: " + t.getClass().getSimpleName(), t);
        }
    }

    @Override
    public synchronized int read(long position, byte[] buffer, int offset, int len) throws IOException
    {
        ensureOpen();
        if (len == 0) return 0;
        if (position >= size) return -1;
        int requested = (int) Math.min((long) len, size - position);
        boolean randomAccess = lastReadEnd < 0 || position != lastReadEnd;
        if (lastReadEnd >= 0 && randomAccess) seekCount++;
        long started = System.nanoTime();
        try
        {
            // Keep stock SageTV's MediaServer file position aligned for
            // STV-owned seek/Comskip decisions without transporting media:
            // exactly one shadow byte per non-sequential SMB access.
            if (randomAccess && shadow != null) shadow.probeRead(position);
            int read = file.read(buffer, position, offset, requested);
            readCount++;
            if (read > 0)
            {
                bytesRead += read;
                lastReadEnd = position + read;
            }
            return read <= 0 ? -1 : read;
        }
        catch (RuntimeException e)
        {
            throw new IOException("SMB Direct read failed", e);
        }
        finally
        {
            lastReadLatencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        }
    }

    @Override
    public synchronized long waitForGrowth(long position, long timeoutMs) throws IOException
    {
        ensureOpen();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
        do
        {
            size = Math.max(size, refreshSmbSize());
            if (shadow != null) size = Math.max(size, shadow.refreshSize());
            if (size > position || System.nanoTime() >= deadline) return size;
            try { wait(100L); }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return size;
            }
        }
        while (true);
    }

    private long refreshSmbSize() throws IOException
    {
        try { return file.getFileInformation().getStandardInformation().getEndOfFile(); }
        catch (RuntimeException e) { throw new IOException("SMB Direct size query failed", e); }
    }

    private void ensureOpen() throws IOException
    {
        if (!open || file == null) throw new IOException("SMB Direct session is not open");
    }

    @Override
    public synchronized long size() { return size; }

    /** Range close is intentionally a no-op; release() owns the playback session. */
    @Override
    public synchronized void close() { release(); }

    public void release()
    {
        if (!releaseRequested.compareAndSet(false, true)) return;
        if (Looper.myLooper() != Looper.getMainLooper())
        {
            cleanupAndShutdown();
            return;
        }

        cleanupExecutor.execute(new Runnable()
        {
            @Override public void run() { cleanupAndShutdown(); }
        });
    }

    private void cleanupAndShutdown()
    {
        try { releaseNow(); }
        finally { cleanupExecutor.shutdown(); }
    }

    private synchronized void releaseNow()
    {
        closeQuietly(file);
        closeQuietly(share);
        closeQuietly(session);
        closeQuietly(connection);
        closeQuietly(client);
        if (shadow != null) shadow.close();
        file = null;
        share = null;
        session = null;
        connection = null;
        client = null;
        shadow = null;
        open = false;
        config.clear();
    }

    private static void closeQuietly(AutoCloseable closeable)
    {
        if (closeable == null) return;
        try { closeable.close(); }
        catch (Exception ignored) { }
    }

    public synchronized String getPlaybackSource() { return "SMB_DIRECT"; }
    public synchronized String getSageOriginalPath() { return sageOriginalPath; }
    public synchronized String getSmbMappedPath() { return mappedPath == null ? "" : mappedPath.getRedactedUrl(); }
    public synchronized boolean isSmbConnected() { return open && connection != null && connection.isConnected(); }
    public synchronized boolean isShadowConnected() { return shadow != null && shadow.isConnected(); }
    public synchronized boolean isShadowOpenSent() { return shadow != null && shadow.isOpenSent(); }
    public synchronized boolean isShadowSizeSent() { return shadow != null && shadow.isSizeSent(); }
    public synchronized long getShadowReadBytes() { return shadow == null ? 0 : shadow.getReadBytes(); }
    public synchronized long getSmbBytesRead() { return bytesRead; }
    public synchronized long getSmbReadCount() { return readCount; }
    public synchronized long getSmbSeekCount() { return seekCount; }
    public synchronized long getSmbLastReadLatencyMs() { return lastReadLatencyMs; }
}
