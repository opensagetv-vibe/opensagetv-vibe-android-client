package sagex.miniclient.android.video.exoplayer2;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sagex.miniclient.net.BufferedPullDataSource;
import sagex.miniclient.net.HasClose;
import sagex.miniclient.android.video.PlayerRuntimeTuning;


public class Exo2PullDataSource implements DataSource, HasClose
{
    static final Logger log = LoggerFactory.getLogger(Exo2PullDataSource.class);
    // v0.5.69: isolated Exo2 Pull telemetry showed synchronous SageTV READ
    // wait dominating much of the remaining ~8.5 second Comskip recovery. Double
    // the block size to cut protocol round trips; Media3 remains at 256 KiB.
    static final int PULL_READ_BUFFER_BYTES = 512 * 1024;
    private String host = null;
    BufferedPullDataSource dataSource = null;
    private long startPos;
    private long bytesRemaining = C.LENGTH_UNSET;
    private Uri uri;

    private long closedNetworkReadCount;
    private long closedNetworkReadRequestedBytes;
    private long closedNetworkReadBytes;
    private long closedNetworkReadWaitMs;
    private long closedNetworkReadErrors;
    private long networkReadMaxRequestedBytes;
    private long openCount;
    private long openWaitMs;
    private long lastOpenPosition = -1;

    public Exo2PullDataSource(String host)
    {
        this.host=host;
    }
    
    
    @Override
    public void addTransferListener(TransferListener transferListener)
    {
    
    }
    
    @Override
    public long open(DataSpec dataSpec) throws IOException
    {
        if (dataSource != null)
        {
            accumulateAndCloseDataSource();
        }
        dataSource = new BufferedPullDataSource(host, PlayerRuntimeTuning.getExo2PullReadBytes());
        this.uri = dataSpec.uri;
        long openStartedNanos = System.nanoTime();
        long size;
        try
        {
            size = dataSource.open(dataSpec.uri.toString());
        }
        finally
        {
            openCount++;
            openWaitMs += (System.nanoTime() - openStartedNanos) / 1000000L;
            lastOpenPosition = dataSpec.position;
        }
        this.startPos = dataSpec.position;
        log.debug("Open: Offset: {}, Requested Length: {}, Size: {}", startPos, dataSpec.length, size);

        if (size >= 0 && dataSpec.position > size)
        {
            log.debug("IO_READ_POSITION_OUT_OF_RANGE: Offset: {}, Size: {}", dataSpec.position, size);
            DataSourceException ds = new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
            throw ds;
        }

        // Exo DataSource.open() returns the readable length of THIS request, not the
        // total resource size. Returning the total file size for a non-zero byte-range
        // open makes Exo believe the resource extends past its real end and can cause
        // a later reopen to fail with IO_READ_POSITION_OUT_OF_RANGE.
        if (dataSpec.length != C.LENGTH_UNSET)
        {
            bytesRemaining = dataSpec.length;
        }
        else if (size >= 0)
        {
            bytesRemaining = Math.max(0, size - dataSpec.position);
        }
        else
        {
            bytesRemaining = C.LENGTH_UNSET;
        }

        return bytesRemaining;
    }

    @Override
    public void close() throws IOException
    {
        if (dataSource != null)
        {
            accumulateAndCloseDataSource();
        }
        bytesRemaining = C.LENGTH_UNSET;
    }

    private void accumulateAndCloseDataSource()
    {
        BufferedPullDataSource current = dataSource;
        if (current == null) return;
        closedNetworkReadCount += current.getNetworkReadCount();
        closedNetworkReadRequestedBytes += current.getNetworkReadRequestedBytes();
        closedNetworkReadBytes += current.getNetworkReadBytes();
        closedNetworkReadWaitMs += current.getNetworkReadWaitMs();
        closedNetworkReadErrors += current.getNetworkReadErrors();
        networkReadMaxRequestedBytes = Math.max(networkReadMaxRequestedBytes, current.getNetworkReadMaxRequestedBytes());
        current.close();
        dataSource = null;
    }

    public long getOpenCount() { return openCount; }
    public long getOpenWaitMs() { return openWaitMs; }
    public long getLastOpenPosition() { return lastOpenPosition; }
    public long getNetworkReadCount() { return closedNetworkReadCount + (dataSource == null ? 0 : dataSource.getNetworkReadCount()); }
    public long getNetworkReadRequestedBytes() { return closedNetworkReadRequestedBytes + (dataSource == null ? 0 : dataSource.getNetworkReadRequestedBytes()); }
    public long getNetworkReadBytes() { return closedNetworkReadBytes + (dataSource == null ? 0 : dataSource.getNetworkReadBytes()); }
    public long getNetworkReadWaitMs() { return closedNetworkReadWaitMs + (dataSource == null ? 0 : dataSource.getNetworkReadWaitMs()); }
    public long getNetworkReadErrors() { return closedNetworkReadErrors + (dataSource == null ? 0 : dataSource.getNetworkReadErrors()); }
    public long getNetworkReadMaxRequestedBytes() { return Math.max(networkReadMaxRequestedBytes, dataSource == null ? 0 : dataSource.getNetworkReadMaxRequestedBytes()); }
    public long getNetworkLastReadPosition() { return dataSource == null ? -1 : dataSource.getNetworkLastReadPosition(); }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException
    {

        //log.debug("Byte buffer length: {}, Offset {}, readLength {}", buffer.length, offset, readLength);

        try
        {
            if (readLength == 0)
            {
                return 0;
            }
            if (bytesRemaining == 0)
            {
                return C.RESULT_END_OF_INPUT;
            }
            if (dataSource == null)
            {
                throw new IOException("Exo2 Pull datasource closed during non-zero read");
            }

            int bytesToRead = bytesRemaining == C.LENGTH_UNSET
                    ? readLength
                    : (int) Math.min((long) readLength, bytesRemaining);
            int bytes = dataSource.read(startPos, buffer, offset, bytesToRead);

            if (bytes == -1)
            {
                //log.debug("DATA SOURCE RETURNED -1");
                return C.RESULT_END_OF_INPUT;
            }
            if (bytes == 0)
            {
                throw new IOException("Exo2 Pull datasource returned zero bytes for non-zero read");
            }
            startPos += bytes;
            if (bytesRemaining != C.LENGTH_UNSET)
            {
                bytesRemaining -= bytes;
            }
            return bytes;
        }
        catch(Exception ex)
        {
            log.debug("Data source read error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }
    
    @Override
    public Map<String, List<String>> getResponseHeaders()
    {
        return new HashMap<String, List<String>>();
    }


//    @Override
//    public Map<String, List<String>> getResponseHeaders() {
//        return Collections.emptyMap();
//    }
//
//    @Override
//    public void addTransferListener(TransferListener transferListener) {
//    }
}
