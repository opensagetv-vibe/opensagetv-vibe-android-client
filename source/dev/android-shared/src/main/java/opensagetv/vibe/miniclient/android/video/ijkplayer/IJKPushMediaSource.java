package opensagetv.vibe.miniclient.android.video.ijkplayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.HasClose;
import opensagetv.vibe.miniclient.net.HasPushBuffer;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;
import opensagetv.vibe.miniclient.util.VerboseLogging;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

/**
 * Created by seans on 20/12/15.
 */
public class IJKPushMediaSource implements IMediaDataSource, HasPushBuffer, HasClose
{
    private static final Logger log = LoggerFactory.getLogger(IJKPushMediaSource.class);

    private final Object sourceMonitor = new Object();
    private volatile PushBufferDataSource dataSource;
    private volatile String url;
    private volatile boolean released = false;

    public IJKPushMediaSource()
    {
    }

    public void open(String url) throws IOException
    {
        this.url = url;
        released = false;
    }

    private void _open() throws IOException
    {
        synchronized (sourceMonitor)
        {
            if (released) throw new IOException("IJK PUSH datasource is released");
            if (dataSource != null) return;
            PushBufferDataSource opened = new PushBufferDataSource();
            opened.open(url);
            dataSource = opened;
            sourceMonitor.notifyAll();
        }
    }

    @Override
    public int readAt(long position, byte[] bytes, int offset, int size) throws IOException
    {

        // ijkmediasource does a zero len read on a seek to see if it was successful
        // we'll return 0 (ok) so that the player buffers get cleaned up, and the the player
        // can start reading from the new location.
        if (size == 0) return 0;

        try
        {
            if (dataSource == null)
            {
                log.debug("Data source was null.  Reopening the datasource");
                _open();
            }
            PushBufferDataSource local = dataSource;
            if (local.isServerEOS())
            {
                log.debug("Datasource thinks server is EOS");
                log.debug("readAt(): pos: {}, offset:{}, size: {}", position, offset, size);
            }

            // IMediaDataSource must not return zero for a non-zero request just
            // because SageTV's PUSH buffer is momentarily empty. During a live
            // channel flush that transient zero can be interpreted by IJK's
            // native FFmpeg demuxer as end-of-input, after which it never asks
            // for the new channel bytes. Wait for data, real EOS, or release.
            return local.readBlocking(position, bytes, offset, size);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            throw t;
        }
    }

    @Override
    public long getSize() throws IOException
    {
        PushBufferDataSource local = dataSource;
        if (local == null)
        {
            log.debug("Get Size Called: Data source was null.  Reopening the datasource");
            _open();
            local = dataSource;
        }
        if (local == null) throw new IOException("IJK PUSH datasource is released");
        return local.size();
    }

    @Override
    public void close() throws IOException
    {
        if (dataSource != null)
        {
            log.debug("Datasource close called");
            dataSource.close();
        }
    }

    @Override
    public void release()
    {
        log.debug("Datasource release called");
        PushBufferDataSource local;
        synchronized (sourceMonitor)
        {
            if (released) return;
            released = true;
            local = dataSource;
            dataSource = null;
            sourceMonitor.notifyAll();
        }
        if (local != null)
        {
            try
            {
                local.release();
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }

    }

    @Override
    public void setEOS()
    {
        PushBufferDataSource local = dataSource;
        if (local != null)
        {
            local.setEOS();
        }
    }

    @Override
    public int bufferAvailable()
    {
        PushBufferDataSource local = dataSource;
        if (local != null)
        {
            return local.bufferAvailable();
        }

        return PushBufferDataSource.PIPE_SIZE;
    }

    @Override
    public void pushBytes(byte[] bytes, int offset, int len) throws IOException
    {
        if (released)
        {
            log.info("DataSource is released, so ignoring push.");
            return;
        }
        PushBufferDataSource local;
        synchronized (sourceMonitor)
        {
            while (dataSource == null && !released)
            {
                try
                {
                    sourceMonitor.wait();
                    if (VerboseLogging.DATASOURCE_LOGGING) log.warn("Waiting for datasource...");
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for IJK PUSH open", e);
                }
            }
            if (released) return;
            local = dataSource;
        }
        local.pushBytes(bytes, offset, len);
    }

    @Override
    public void flush()
    {
        PushBufferDataSource local = dataSource;
        if (local != null)
        {
            local.flush();
        }
    }

    @Override
    public long getBytesRead()
    {
        PushBufferDataSource local = dataSource;
        if (local != null)
        {
            return local.getBytesRead();
        }

        return 0;
    }
}
