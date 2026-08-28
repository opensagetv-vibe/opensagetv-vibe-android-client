package sagex.miniclient.android.video.gsy;

import android.media.MediaDataSource;

import java.io.IOException;

import sagex.miniclient.net.HasPushBuffer;
import sagex.miniclient.net.PushBufferDataSource;

/** Android MediaPlayer data-source bridge for SageTV PUSH streaming. */
final class SagePushMediaDataSource extends MediaDataSource implements HasPushBuffer
{
    private volatile PushBufferDataSource source;
    private String url;
    private volatile boolean released;

    synchronized void open(String url)
    {
        this.url = url;
        this.released = false;
    }

    private synchronized PushBufferDataSource ensureOpen() throws IOException
    {
        if (released)
        {
            throw new IOException("SageTV PUSH MediaDataSource is released");
        }
        if (source == null)
        {
            source = new PushBufferDataSource();
            source.open(url);
        }
        return source;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException
    {
        if (size == 0) return 0;

        // MediaDataSource permits 0 only when the requested size itself is zero.
        // PushBufferDataSource returns 0 while it is waiting for SageTV to feed more
        // bytes, so wait here until at least one byte, EOS, release, or interruption.
        while (!released)
        {
            PushBufferDataSource local = ensureOpen();
            int read = local.read(position, buffer, offset, size);
            if (read != 0)
            {
                return read;
            }
            try
            {
                Thread.sleep(10);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for SageTV PUSH data", e);
            }
        }
        return -1;
    }

    @Override
    public long getSize() throws IOException
    {
        return ensureOpen().size();
    }

    @Override
    public void close() throws IOException
    {
        PushBufferDataSource local = source;
        if (local != null) local.close();
    }

    @Override
    public void release()
    {
        PushBufferDataSource local;
        synchronized (this)
        {
            if (released) return;
            released = true;
            local = source;
            source = null;
        }
        if (local != null)
        {
            try { local.release(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void setEOS()
    {
        PushBufferDataSource local = source;
        if (local != null) local.setEOS();
    }

    @Override
    public int bufferAvailable()
    {
        PushBufferDataSource local = source;
        return local == null ? PushBufferDataSource.PIPE_SIZE : local.bufferAvailable();
    }

    @Override
    public void pushBytes(byte[] bytes, int offset, int len) throws IOException
    {
        if (released) return;
        ensureOpen().pushBytes(bytes, offset, len);
    }

    @Override
    public void flush()
    {
        PushBufferDataSource local = source;
        if (local != null) local.flush();
    }

    @Override
    public long getBytesRead()
    {
        PushBufferDataSource local = source;
        return local == null ? 0 : local.getBytesRead();
    }
}
