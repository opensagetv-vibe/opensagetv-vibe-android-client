package sagex.miniclient.android.video.gsy;

import android.media.MediaDataSource;

import java.io.IOException;

import sagex.miniclient.net.BufferedPullDataSource;
import sagex.miniclient.net.ISageTVDataSource;

/** Android MediaPlayer data-source bridge for SageTV PULL streaming. */
final class SagePullMediaDataSource extends MediaDataSource
{
    private final String host;
    private ISageTVDataSource source;
    private String url;
    private boolean closed;

    SagePullMediaDataSource(String host)
    {
        this.host = host;
    }

    synchronized void open(String url)
    {
        this.url = url;
        this.closed = false;
    }

    private synchronized ISageTVDataSource ensureOpen() throws IOException
    {
        if (closed)
        {
            throw new IOException("SageTV PULL MediaDataSource is closed");
        }
        if (source == null)
        {
            source = new BufferedPullDataSource(host);
            source.open(url);
        }
        return source;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException
    {
        return ensureOpen().read(position, buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException
    {
        return ensureOpen().size();
    }

    @Override
    public void close() throws IOException
    {
        ISageTVDataSource local;
        synchronized (this)
        {
            if (closed) return;
            closed = true;
            local = source;
            source = null;
        }
        if (local != null) local.close();
    }
}
