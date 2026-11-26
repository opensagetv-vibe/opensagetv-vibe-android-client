package opensagetv.vibe.miniclient.android.video.media3;

import android.net.Uri;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import opensagetv.vibe.miniclient.media.TeletextPesProbe;
import opensagetv.vibe.miniclient.media.TeletextSubtitleEngine;

/**
 * Observes the MPEG-TS segments of a plugin-owned Direct M3U8 while Media3
 * consumes them normally.
 *
 * <p>Media3 does not expose DVB Teletext as a selectable text renderer track.
 * Pull/Push/SMB already feed the player-independent Teletext decoder from
 * their datasource bytes; Direct HLS must do the same at the HTTP boundary.
 * Playlist reads are deliberately excluded, and segment boundaries keep an
 * unknown absolute position so ordinary HLS file changes do not look like a
 * user seek. The player session supplies the real discontinuity/clock anchor.
 * </p>
 */
final class Media3MimDirectHttpDataSource implements DataSource
{
    interface CreationListener
    {
        void onCreated(DataSource dataSource);
        default void onBodyTransfer(String uri,long bytes,long readMs){}
        default long bufferingStamp(){return 0;}
        default void onBodyTransfer(String uri,long bytes,long readMs,long stamp){onBodyTransfer(uri,bytes,readMs);}
        default boolean measureBody(){return false;}
    }

    private static final String SOURCE = "mim-direct-http";

    private final DataSource delegate;
    private final opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters;
    private boolean observeTransportStream;
    private volatile String lastRequestedUri = "";
    private CreationListener observer;
    private opensagetv.vibe.miniclient.android.video.DirectSegmentDelivery delivery;
    private long bufferingStamp;

    private Media3MimDirectHttpDataSource(DataSource delegate,
            opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters)
    {
        this.delegate = delegate;
        this.counters = counters;
    }

    static DataSource.Factory factory()
    {
        return factory(null);
    }

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
                DataSource created = new Media3MimDirectHttpDataSource(
                        upstream.createDataSource(), counters);
                ((Media3MimDirectHttpDataSource)created).observer=listener;
                if (listener != null) listener.onCreated(created);
                return created;
            }
        };
    }

    @Override public long open(DataSpec dataSpec) throws IOException
    {
        lastRequestedUri = dataSpec == null || dataSpec.uri == null
                ? "" : dataSpec.uri.toString();
        observeTransportStream = shouldObserve(dataSpec == null ? null : dataSpec.uri);
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

    @Override public Uri getUri()
    {
        return delegate.getUri();
    }

    @Override public Map<String, List<String>> getResponseHeaders()
    {
        return delegate.getResponseHeaders();
    }

    @Override public void close() throws IOException
    {
        if(delivery!=null&&delivery.measured())observer.onBodyTransfer(lastRequestedUri,delivery.bytes(),delivery.readMs(),bufferingStamp);
        delivery=null;
        observeTransportStream = false;
        delegate.close();
    }

    @Override public void addTransferListener(TransferListener transferListener)
    {
        delegate.addTransferListener(transferListener);
    }

    static boolean shouldObserve(Uri uri)
    {
        return uri != null && shouldObservePath(uri.getPath());
    }

    static boolean shouldObservePath(String path)
    {
        if (path == null) return false;
        String value = path.toLowerCase(java.util.Locale.US);
        return value.contains("/v1/direct/media/") && value.endsWith(".ts");
    }
}
