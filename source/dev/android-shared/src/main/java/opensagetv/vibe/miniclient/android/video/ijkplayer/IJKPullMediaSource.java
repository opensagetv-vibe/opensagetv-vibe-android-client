package opensagetv.vibe.miniclient.android.video.ijkplayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.BufferedPullDataSource;
import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.HasClose;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;
import opensagetv.vibe.miniclient.android.video.GrowingPlaybackSourcePolicy;
import opensagetv.vibe.miniclient.util.VerboseLogging;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

/**
 * Created by seans on 20/12/15.
 */
public class IJKPullMediaSource implements IMediaDataSource, HasClose {
    private static final Logger log = LoggerFactory.getLogger(IJKPullMediaSource.class);
    private static final long GROWING_EDGE_WAIT_MS = 10_000L;
    private String host=null;

    private ISageTVDataSource dataSource;
    private String url;
    private final GrowingPlaybackSourcePolicy growthPolicy;
    private volatile boolean effectivelyGrowing;

    public IJKPullMediaSource() {
        this(null, false);
    }

    public IJKPullMediaSource(String host) {
        this(host, false);
    }

    public IJKPullMediaSource(String host, boolean potentiallyGrowing) {
        this(host, potentiallyGrowing, true);
    }

    public IJKPullMediaSource(String host, boolean potentiallyGrowing,
                              boolean metadataExplicit) {
        this.host = host;
        this.growthPolicy = new GrowingPlaybackSourcePolicy(
                potentiallyGrowing, metadataExplicit);
    }

    public void open(String url) throws IOException {
        this.url = url;
    }

    private void _open() throws IOException {
        if (dataSource != null) return;
        //dataSource = new SimplePullDataSource(host);
        dataSource = new BufferedPullDataSource(host);
        long size = dataSource.open(url);
        effectivelyGrowing = growthPolicy.resolve(dataSource, size);
    }

    @Override
    public int readAt(long position, byte[] bytes, int offset, int size) throws IOException {
        if (VerboseLogging.DATASOURCE_LOGGING)
            log.debug("readAt(): pos: {}, offset:{}, size: {}", position, offset, size);
        try {
            if (dataSource == null) _open();
            if (effectivelyGrowing && position >= dataSource.size()) {
                long refreshedSize = dataSource instanceof GrowingDataSource
                        ? ((GrowingDataSource) dataSource).waitForGrowth(position,
                        GROWING_EDGE_WAIT_MS)
                        : dataSource.size();
                if (refreshedSize <= position) return -1;
            }
            return dataSource.read(position, bytes, offset, size);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    @Override
    public long getSize() throws IOException {
        if (dataSource == null) _open();
        // Returning -1 here makes IJK/FFmpeg cache the custom source as
        // permanently unseekable. The underlying MediaServer size is refreshed
        // on every call, while readAt() continues waiting at a growing edge.
        return dataSource.size();
    }

    public boolean isEffectivelyGrowing() {
        return effectivelyGrowing;
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            dataSource.close();
        }
        dataSource = null;
    }
}
