package sagex.miniclient.android.video.media3;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sagex.miniclient.net.BufferedPullDataSource;
import sagex.miniclient.net.HasClose;
import sagex.miniclient.android.video.PlayerRuntimeTuning;


public class Media3PullDataSource implements DataSource, HasClose
{
    static final Logger log = LoggerFactory.getLogger(Media3PullDataSource.class);
    // Keep the proven 256 KiB Pull buffer. The v0.5.32 1 MiB experiment did not
    // improve the slow large-seek case, so v0.5.33 restores the prior size and
    // adds diagnostic counters instead of making another tuning guess.
    static final int PULL_READ_BUFFER_BYTES = 256 * 1024;
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

    public Media3PullDataSource(String host)
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
        dataSource = new BufferedPullDataSource(host, PlayerRuntimeTuning.getMedia3PullReadBytes());
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

        // Media3 DataSource.open() returns the readable length of THIS request, not
        // the total resource size. Returning the total file size for a non-zero
        // byte-range open causes the logical end position to grow across reopens.
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
                throw new IOException("Media3 Pull datasource closed during non-zero read");
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
                throw new IOException("Media3 Pull datasource returned zero bytes for non-zero read");
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
