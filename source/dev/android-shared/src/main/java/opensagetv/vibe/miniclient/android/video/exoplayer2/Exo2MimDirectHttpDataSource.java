package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import opensagetv.vibe.miniclient.media.TeletextPesProbe;
import opensagetv.vibe.miniclient.media.TeletextSubtitleEngine;

/**
 * Legacy-Exo counterpart of Media3's owned Direct HTTP observer. It does not
 * change HTTP reads, retry policy, playlist parsing or encoded media. Only real
 * Direct TS segment bytes feed the existing independent Teletext decoder; M3U8
 * bytes and ordinary HTTP playback are excluded. Unknown absolute byte offsets
 * avoid treating each HLS segment as an intentional seek. The player supplies
 * the actual presentation/discontinuity clock through its existing hooks.
 */
final class Exo2MimDirectHttpDataSource implements DataSource
{
    interface CreationListener { void onCreated(DataSource dataSource); }
    private static final String SOURCE = "mim-direct-http";
    private final DataSource delegate;
    private boolean observeTransportStream;
    private volatile String lastRequestedUri = "";

    private Exo2MimDirectHttpDataSource(DataSource delegate)
    { this.delegate = delegate; }

    static DataSource.Factory factory(final CreationListener listener)
    {
        final DataSource.Factory upstream = new DefaultHttpDataSource.Factory()
                .setUserAgent("OpenSageTV-Vibe");
        return new DataSource.Factory()
        {
            @Override public DataSource createDataSource()
            {
                DataSource created = new Exo2MimDirectHttpDataSource(
                        upstream.createDataSource());
                if (listener != null) listener.onCreated(created);
                return created;
            }
        };
    }

    @Override public long open(DataSpec dataSpec) throws IOException
    {
        lastRequestedUri = dataSpec == null || dataSpec.uri == null
                ? "" : dataSpec.uri.toString();
        observeTransportStream = shouldObservePath(dataSpec == null
                || dataSpec.uri == null ? null : dataSpec.uri.getPath());
        return delegate.open(dataSpec);
    }

    /** Consumed internally by the debug probe; never exported as a raw URL. */
    public String getLastRequestedUriForDebug() { return lastRequestedUri; }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException
    {
        int read = delegate.read(buffer, offset, length);
        if (observeTransportStream && read > 0)
        {
            TeletextPesProbe.observe(SOURCE, -1L, buffer, offset, read);
            TeletextSubtitleEngine.observe(SOURCE, -1L, buffer, offset, read);
        }
        return read;
    }

    @Override public Uri getUri() { return delegate.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders()
    { return delegate.getResponseHeaders(); }
    @Override public void addTransferListener(TransferListener listener)
    { delegate.addTransferListener(listener); }
    @Override public void close() throws IOException
    {
        observeTransportStream = false;
        delegate.close();
    }

    static boolean shouldObservePath(String path)
    {
        if (path == null) return false;
        String value = path.toLowerCase(java.util.Locale.US);
        return value.contains("/v1/direct/media/") && value.endsWith(".ts");
    }
}
