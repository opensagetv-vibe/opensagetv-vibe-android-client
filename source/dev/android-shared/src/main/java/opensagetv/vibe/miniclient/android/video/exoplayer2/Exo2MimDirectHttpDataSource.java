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
    interface CreationListener { void onCreated(DataSource dataSource); default void onBodyTransfer(String uri,long bytes,long readMs){} default boolean measureBody(){return false;} default long bufferingStamp(){return 0;} default void onBodyTransfer(String uri,long bytes,long readMs,long stamp){onBodyTransfer(uri,bytes,readMs);} }
    private static final String SOURCE = "mim-direct-http";
    private final DataSource delegate;
    private final opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters;
    private boolean observeTransportStream;
    private volatile String lastRequestedUri = "";
    private CreationListener observer;
    private opensagetv.vibe.miniclient.android.video.DirectSegmentDelivery delivery;
    private long bufferingStamp;

    private Exo2MimDirectHttpDataSource(DataSource delegate,
            opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters)
    { this.delegate = delegate; this.counters = counters; }

    static DataSource.Factory factory(final CreationListener listener)
    {
        final DataSource.Factory upstream = new DefaultHttpDataSource.Factory()
                .setUserAgent("OpenSageTV-Vibe");
        final opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters =
                new opensagetv.vibe.miniclient.android.video.HttpMediaCounters();
        return new DataSource.Factory()
        {
            @Override public DataSource createDataSource()
            {
                DataSource created = new Exo2MimDirectHttpDataSource(
                        upstream.createDataSource(), counters);
                ((Exo2MimDirectHttpDataSource)created).observer=listener;
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
        delivery=null;

        boolean measure=observeTransportStream&&observer!=null&&observer.measureBody();
        if(measure)bufferingStamp=observer.bufferingStamp();
        long expected=delegate.open(dataSpec);
        if(measure)delivery=new opensagetv.vibe.miniclient.android.video.DirectSegmentDelivery(expected);
        return expected;
    }

    /** Consumed internally by the debug probe; never exported as a raw URL. */
    public String getLastRequestedUriForDebug() { return lastRequestedUri; }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException
    {
        long began=delivery!=null?System.nanoTime():0;
        int read = delegate.read(buffer, offset, length);
        counters.read(observeTransportStream, read);

        if(delivery!=null)delivery.read(read,System.nanoTime()-began);
        if (observeTransportStream && read > 0)
        {
            TeletextPesProbe.observe(SOURCE, -1L, buffer, offset, read);
            TeletextSubtitleEngine.observe(SOURCE, -1L, buffer, offset, read);
        }
        return read;
    }

    public long getBytesRead() { return counters.bytes(); }
    public long getReadCount() { return counters.reads(); }
    public long getNetworkReadBytes() { return counters.bytes(); }
    public long getNetworkReadCount() { return counters.reads(); }

    @Override public Uri getUri() { return delegate.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders()
    { return delegate.getResponseHeaders(); }
    @Override public void addTransferListener(TransferListener listener)
    { delegate.addTransferListener(listener); }
    @Override public void close() throws IOException
    {
        if(delivery!=null&&delivery.measured())observer.onBodyTransfer(lastRequestedUri,delivery.bytes(),delivery.readMs(),bufferingStamp);
        delivery=null;
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
