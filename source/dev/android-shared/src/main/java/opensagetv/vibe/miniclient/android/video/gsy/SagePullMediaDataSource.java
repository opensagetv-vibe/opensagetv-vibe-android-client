package opensagetv.vibe.miniclient.android.video.gsy;

import android.media.MediaDataSource;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.BufferedPullDataSource;
import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;
import opensagetv.vibe.miniclient.android.video.GrowingPlaybackSourcePolicy;

/** Android MediaPlayer data-source bridge for SageTV PULL streaming. */
final class SagePullMediaDataSource extends MediaDataSource
{
    private static final long GROWING_EDGE_WAIT_MS = 10_000L;
    private final String host;
    private ISageTVDataSource source;
    private String url;
    private boolean closed;
    private final GrowingPlaybackSourcePolicy growthPolicy;
    private boolean effectivelyGrowing;

    SagePullMediaDataSource(String host)
    {
        this(host, false);
    }

    SagePullMediaDataSource(String host, boolean potentiallyGrowing)
    {
        this(host, potentiallyGrowing, true);
    }

    SagePullMediaDataSource(String host, boolean potentiallyGrowing,
                            boolean metadataExplicit)
    {
        this.host = host;
        this.growthPolicy = new GrowingPlaybackSourcePolicy(
                potentiallyGrowing, metadataExplicit);
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
            long size = source.open(url);
            effectivelyGrowing = growthPolicy.resolve(source, size);
        }
        return source;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException
    {
        ISageTVDataSource active = ensureOpen();
        if (effectivelyGrowing && position >= active.size())
        {
            long refreshedSize = active instanceof GrowingDataSource
                    ? ((GrowingDataSource) active).waitForGrowth(position,
                    GROWING_EDGE_WAIT_MS)
                    : active.size();
            if (refreshedSize <= position) return -1;
        }
        return active.read(position, buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException
    {
        ISageTVDataSource active = ensureOpen();
        return effectivelyGrowing ? -1L : active.size();
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
