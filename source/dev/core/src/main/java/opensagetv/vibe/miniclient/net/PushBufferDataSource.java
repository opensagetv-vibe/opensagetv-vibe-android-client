package opensagetv.vibe.miniclient.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import opensagetv.vibe.miniclient.util.DataCollector;
import opensagetv.vibe.miniclient.util.VerboseLogging;

/**
 * used push:// is used, this is the media data source that feeds the video player
 */
public class PushBufferDataSource implements ISageTVDataSource, HasPushBuffer
{
    public enum State {Idle, Opened, Closed}

    public static final int PIPE_SIZE = 4 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(PushBufferDataSource.class);

    BoundedCircularByteBuffer circularByteBuffer = null;
    String uri;
    volatile long bytesRead = 0;
    volatile boolean eos = false;

    private final AtomicLong readCallCount = new AtomicLong();
    private final AtomicLong readRequestedBytes = new AtomicLong();
    private final AtomicLong readWaitNanos = new AtomicLong();
    private final AtomicLong pushCallCount = new AtomicLong();
    private final AtomicLong pushedBytes = new AtomicLong();
    private volatile long measurementStartNanos = System.nanoTime();

    volatile State state = State.Idle;

    // Player datasource adapters must block until at least one byte is available.
    // Keep the historical non-blocking read() for any remaining direct callers and
    // expose an opt-in blocking path for Exo/Media3/IJK adapters.
    private final Object dataAvailableMonitor = new Object();
    private volatile boolean readsSuspended;
    private volatile long activeReadGeneration;
    private volatile long endedReadGeneration = Long.MIN_VALUE;

    DataCollector dataCollector = null;

    public PushBufferDataSource()
    {

    }

    @Override
    public long open(String uri) throws IOException
    {
        // push:f=MPEG2-TS;dur=1851466;br=2500000;
        // [bf=vid;f=H.264;index=0;main=yes;tag=1011;fps=59.94006;fpsn=60000;fpsd=1001;ar=1.777778;arn=16;ard=9;w=1280;h=720;]
        // [bf=aud;f=AAC;index=1;main=yes;tag=1100;sr=48000;ch=2;at=ADTS-MPEG2;]

        if (state == State.Opened)
        {
            log.warn("opened called on an already opened push buffer, will ignore");
            return -1;
        }

        state = State.Idle;
        eos = false;
        readsSuspended = false;
        endedReadGeneration = Long.MIN_VALUE;

        this.uri = uri;
        log.debug("Open Called: {}", uri);
        resetMeasurements();
        if (circularByteBuffer != null)
        {
            circularByteBuffer.clear();
        }
        else
        {
            circularByteBuffer = new BoundedCircularByteBuffer(PIPE_SIZE);
        }

        if (VerboseLogging.LOG_DATASOURCE_BYTES_TO_FILE)
        {
            log.warn("DataCollector is enabled");
            String capturePath = VerboseLogging.DATASOURCE_CAPTURE_PATH;
            dataCollector = capturePath == null || capturePath.isEmpty()
                    ? new DataCollector() : new DataCollector(null,
                            new File(capturePath),
                            VerboseLogging.DATASOURCE_CAPTURE_MAX_BYTES);
        }
        state = State.Opened;
        if (dataCollector != null)
        {
            try
            {
                dataCollector.open();
            }
            catch (IOException e)
            {
                // Diagnostics must never make media playback fail.
                log.error("Unable to open datasource capture; continuing playback", e);
                dataCollector = null;
            }
        }
        signalDataAvailable();
        return -1;
    }

    public void close()
    {
        log.debug("close() for a push is ignored, until the release() is called.");
        //log.error("close() stacktrace", new Exception("PushBuffer.close()"));
    }

    @Override
    public void release() {
        log.debug("Release on PushBufferDataSource was called");
        try {
            circularByteBuffer.clear();
            circularByteBuffer.close();
        } catch (Throwable t) {
        }

        state = State.Closed;
        circularByteBuffer = null;
        eos = true;
        readsSuspended = false;
        signalDataAvailable();
        if (dataCollector != null) {
            dataCollector.close();
        }
    }

    @Override
    public void setEOS()
    {
        eos = true;
        signalDataAvailable();

        if (circularByteBuffer != null)
        {
            try
            {
                if (circularByteBuffer.available() == 0)
                {
                    log.debug("setEOS() called and there is no data the stream");
                }
                else
                {
                    log.debug("setEOS() called and we still have {} bytes of data",
                            circularByteBuffer.available());
                }
            }
            catch (Throwable t)
            {
                log.warn("Unable to inspect Push buffer while setting EOS", t);
            }
        }
    }

    @Override
    public void flush()
    {
        log.debug("FLUSH()");

        resetMeasurements();

        // A SageTV PUSH flush is also the boundary between the old byte stream
        // and a replacement stream (for example after a Fixed-mode seek).  The
        // previous FFmpeg process may already have reported EOS.  Keeping that
        // flag set makes a player reprepare observe immediate end-of-input and
        // stop reading before the replacement stream arrives.
        eos = false;

        if (circularByteBuffer!=null)
        {
            circularByteBuffer.clear();
        }
        signalDataAvailable();
    }

    @Override
    public int bufferAvailable()
    {
        if (circularByteBuffer == null)
        {
            return 0;
        }

        return circularByteBuffer.getSpaceLeft();
    }

    @Override
    public long size()
    {
        return -1;
    }

    public boolean isOpen()
    {
        return state == State.Opened;
    }

    @Override
    public int read(long readOffset, byte[] bytes, int offset, int len) throws IOException
    {
        readCallCount.incrementAndGet();
        readRequestedBytes.addAndGet(Math.max(0, len));
        return readAvailable(readOffset, bytes, offset, len);
    }

    private int readAvailable(long readOffset, byte[] bytes, int offset, int len) throws IOException
    {
        int read = 0;

        if (state != State.Opened)
        {
            throw new IOException("read() called on DataSource that is not opened: " + uri);
        }

        if (circularByteBuffer == null)
        {
            log.debug("Push buffer is null returning 0");
            return 0;
        }

        if(circularByteBuffer.available() > 0)
        {
            read = circularByteBuffer.read(bytes, offset,
                    Math.min(len, circularByteBuffer.available()));
        }
        else if(eos)
        {
            log.debug("in.available is 0 and EOS is true.  Returning -1");
            return -1;
        }
        else
        {
            return 0;
        }

        if(eos)
        {
            log.debug("ServerIsEOS: Read returned: " + read);
        }

        if (read >= 0)
        {
            bytesRead += read;
        }

        return read;
    }

    /**
     * Blocking read for ExoPlayer/Media3 DataSource adapters. Exo's DataReader
     * contract only permits a zero return when the requested length is zero;
     * otherwise the call must wait for at least one byte or return end-of-input.
     *
     * The legacy read() remains non-blocking so IJK/native behavior is unchanged.
     */
    public int readBlocking(long readOffset, byte[] bytes, int offset, int len) throws IOException
    {
        return readBlockingInternal(readOffset, bytes, offset, len, 0, false);
    }

    /**
     * Blocking read owned by one player-loader generation. DVD FLUSH rebuilds
     * Media3 on the same Push buffer; a cancelled loader may still be waiting
     * here when replacement bytes arrive. Returning EOF to an obsolete loader
     * prevents it from stealing the new cell before the replacement extractor.
     */
    public int readBlockingForGeneration(long generation, long readOffset,
            byte[] bytes, int offset, int len) throws IOException
    {
        return readBlockingInternal(readOffset, bytes, offset, len, generation, true);
    }

    private int readBlockingInternal(long readOffset, byte[] bytes, int offset, int len,
            long generation, boolean requireActiveGeneration) throws IOException
    {
        if (len == 0)
        {
            return 0;
        }

        readCallCount.incrementAndGet();
        readRequestedBytes.addAndGet(len);
        while (true)
        {
            synchronized (dataAvailableMonitor)
            {
                if (requireActiveGeneration && generation != activeReadGeneration)
                {
                    return -1;
                }
                if (state == State.Closed)
                {
                    return -1;
                }

                if (readsSuspended)
                {
                    long waitStarted = System.nanoTime();
                    try
                    {
                        dataAvailableMonitor.wait();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while DVD Push reads were suspended", e);
                    }
                    finally
                    {
                        readWaitNanos.addAndGet(Math.max(0, System.nanoTime() - waitStarted));
                    }
                    continue;
                }

                int read = readAvailable(readOffset, bytes, offset, len);
                if (read != 0)
                {
                    return read;
                }

                // A DVD cell boundary is end-of-input for the current
                // Media3 extractor/decoder generation, but not for the shared
                // PUSH session.  Drain every byte already queued before
                // returning EOF so the tail of the authored cell is rendered.
                if (requireActiveGeneration && generation == endedReadGeneration)
                {
                    return -1;
                }

                long waitStarted = System.nanoTime();
                try
                {
                    dataAvailableMonitor.wait();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for SageTV PUSH data", e);
                }
                finally
                {
                    readWaitNanos.addAndGet(Math.max(0, System.nanoTime() - waitStarted));
                }
            }
        }
    }

    public void activateReadGeneration(long generation)
    {
        synchronized (dataAvailableMonitor)
        {
            activeReadGeneration = generation;
            endedReadGeneration = Long.MIN_VALUE;
            dataAvailableMonitor.notifyAll();
        }
    }

    /**
     * Release one loader lease without closing the shared PUSH session.
     *
     * Media3 closes the old DataSource instance after setMediaSource(). DVD
     * segment replacement deliberately creates a new reader over the same
     * circular buffer, so closing an obsolete lease must not transition the
     * owner to {@link State#Closed}. Generation checks already make any late
     * read from that obsolete loader return EOF.
     */
    public void releaseReadGeneration(long generation)
    {
        synchronized (dataAvailableMonitor)
        {
            dataAvailableMonitor.notifyAll();
        }
    }

    /**
     * End only the currently active generation after its queued bytes drain.
     * The next generation is activated after the server's segment FLUSH and
     * continues using this same circular PUSH buffer.
     */
    public void signalActiveReadGenerationEnd()
    {
        synchronized (dataAvailableMonitor)
        {
            endedReadGeneration = activeReadGeneration;
            dataAvailableMonitor.notifyAll();
        }
    }

    /**
     * Temporarily prevent a player loader from consuming replacement Push
     * bytes while its MediaSource is being rebuilt at a stream boundary.
     */
    public void suspendReads()
    {
        synchronized (dataAvailableMonitor)
        {
            readsSuspended = true;
        }
    }

    /** Resume reads after the replacement player pipeline is prepared. */
    public void resumeReads()
    {
        synchronized (dataAvailableMonitor)
        {
            readsSuspended = false;
            dataAvailableMonitor.notifyAll();
        }
    }

    private void signalDataAvailable()
    {
        synchronized (dataAvailableMonitor)
        {
            dataAvailableMonitor.notifyAll();
        }
    }

    @Override
    public void pushBytes(byte[] bytes, int offset, int len) throws IOException
    {
        if (VerboseLogging.DATASOURCE_LOGGING && log.isDebugEnabled()) log.debug("PUSH: {}", len);

        synchronized (dataAvailableMonitor)
        {
            while (circularByteBuffer == null && state != State.Closed)
            {
                log.warn("PUSH: Waiting because the DataSource is not yet opened.");
                try
                {
                    dataAvailableMonitor.wait();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for SageTV PUSH open", e);
                }
            }
            if (state == State.Closed) return;
        }
        if (len > 0)
        {
            pushCallCount.incrementAndGet();
            pushedBytes.addAndGet(len);
            if (eos)
            {
                log.warn("We are getting data, even after EOS has been set.  Resetting EOS");
                if (state != State.Closed && circularByteBuffer != null)
                {
                    // we are not closed, so reset the eos, and then continue to receive the data
                    // this might happen if we were close to finishing the file, but, then
                    // we reseeked to an earlier position
                    eos = false;
                }
                else
                {
                    // we are closed so ignore the data
                    return;
                }
            }
            if (VerboseLogging.DATASOURCE_LOGGING)
            {
                if (bufferAvailable() < len)
                {
                    log.warn("BLOCKING: We have more data than we can store {}, need {}", bufferAvailable(), len);
                }
            }
            circularByteBuffer.write(bytes, offset, len);
            signalDataAvailable();
            if (dataCollector != null)
            {
                dataCollector.write(bytes, offset, len);
            }
        }
    }

    public boolean isServerEOS()
    {
        return this.eos;
    }

    @Override
    public long getBytesRead()
    {
        return bytesRead;
    }

    public long getReadCount() { return readCallCount.get(); }
    public long getReadRequestedBytes() { return readRequestedBytes.get(); }
    public long getReadWaitMs() { return readWaitNanos.get() / 1000000L; }
    public long getPushCount() { return pushCallCount.get(); }
    public long getPushedBytes() { return pushedBytes.get(); }
    public long getReadRateKbps()
    {
        long elapsedMs = Math.max(1, (System.nanoTime() - measurementStartNanos) / 1000000L);
        return (bytesRead * 8L) / elapsedMs;
    }

    private void resetMeasurements()
    {
        bytesRead = 0;
        readCallCount.set(0);
        readRequestedBytes.set(0);
        readWaitNanos.set(0);
        pushCallCount.set(0);
        pushedBytes.set(0);
        measurementStartNanos = System.nanoTime();
    }
}
