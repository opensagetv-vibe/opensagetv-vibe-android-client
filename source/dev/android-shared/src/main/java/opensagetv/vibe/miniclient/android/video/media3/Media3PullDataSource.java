package opensagetv.vibe.miniclient.android.video.media3;

import android.net.Uri;
import android.os.SystemClock;

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

import opensagetv.vibe.miniclient.net.BufferedPullDataSource;
import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.HasClose;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;
import opensagetv.vibe.miniclient.net.SessionOwnedDataSource;
import opensagetv.vibe.miniclient.android.video.PlaybackDataSourceTelemetry;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;
import opensagetv.vibe.miniclient.android.video.smb.SmbDirectConfig;
import opensagetv.vibe.miniclient.android.video.smb.SmbSourceSelector;


public class Media3PullDataSource implements DataSource, HasClose, SessionOwnedDataSource, PlaybackDataSourceTelemetry
{
    static final Logger log = LoggerFactory.getLogger(Media3PullDataSource.class);
    // Keep the proven 256 KiB Pull buffer. The v0.5.32 1 MiB experiment did not
    // improve the slow large-seek case, so v0.5.33 restores the prior size and
    // adds diagnostic counters instead of making another tuning guess.
    // The immutable player configuration injects the proven 256 KiB default.
    private String host = null;
    ISageTVDataSource dataSource = null;
    private final SmbDirectConfig smbConfig;
    private final int pullReadBytes;
    private SmbSourceSelector smbSource;
    private long startPos;
    private long bytesRemaining = C.LENGTH_UNSET;
    private Uri uri;
    private final boolean potentiallyGrowing;
    private volatile boolean endOfInput;

    private long closedNetworkReadCount;
    private long closedNetworkReadRequestedBytes;
    private long closedNetworkReadBytes;
    private long closedNetworkReadWaitMs;
    private long closedNetworkReadErrors;
    private long networkReadMaxRequestedBytes;
    private long openCount;
    private long openWaitMs;
    private long lastOpenPosition = -1;
    private volatile long lastSourceOpenMonotonicMs = -1;
    private volatile long firstReadAfterOpenMonotonicMs = -1;
    private volatile long firstReadAfterOpenPosition = -1;
    private volatile long lastPhysicalReadMonotonicMs = -1;
    private volatile long lastPhysicalReadPosition = -1;

    public Media3PullDataSource(String host)
    {
        this(host, false);
    }

    public Media3PullDataSource(String host, boolean potentiallyGrowing)
    {
        this(host, potentiallyGrowing, null);
    }

    public Media3PullDataSource(String host, boolean potentiallyGrowing, SmbDirectConfig smbConfig)
    {
        this(host, potentiallyGrowing, smbConfig, PlayerRuntimeTuning.getMedia3PullReadBytes());
    }

    public Media3PullDataSource(String host, boolean potentiallyGrowing,
                                SmbDirectConfig smbConfig, int pullReadBytes)
    {
        this.host=host;
        this.potentiallyGrowing = potentiallyGrowing;
        this.smbConfig = smbConfig;
        if (pullReadBytes <= 0) throw new IllegalArgumentException("pullReadBytes must be positive");
        this.pullReadBytes = pullReadBytes;
    }
    
    
    @Override
    public void addTransferListener(TransferListener transferListener)
    {
    
    }
    
    @Override
    public long open(DataSpec dataSpec) throws IOException
    {
        lastSourceOpenMonotonicMs = SystemClock.elapsedRealtime();
        firstReadAfterOpenMonotonicMs = -1;
        firstReadAfterOpenPosition = -1;
        if (dataSource != null)
        {
            accumulateAndCloseDataSource();
        }
        if (smbConfig == null)
        {
            dataSource = new BufferedPullDataSource(host, pullReadBytes);
        }
        else
        {
            if (smbSource == null)
                smbSource = new SmbSourceSelector(host, smbConfig, pullReadBytes);
            dataSource = smbSource;
        }
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
        endOfInput = false;
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
        ISageTVDataSource current = dataSource;
        if (current == null) return;
        if (current instanceof BufferedPullDataSource)
        {
            BufferedPullDataSource pull = (BufferedPullDataSource) current;
            closedNetworkReadCount += pull.getNetworkReadCount();
            closedNetworkReadRequestedBytes += pull.getNetworkReadRequestedBytes();
            closedNetworkReadBytes += pull.getNetworkReadBytes();
            closedNetworkReadWaitMs += pull.getNetworkReadWaitMs();
            closedNetworkReadErrors += pull.getNetworkReadErrors();
            networkReadMaxRequestedBytes = Math.max(networkReadMaxRequestedBytes, pull.getNetworkReadMaxRequestedBytes());
        }
        current.close();
        dataSource = null;
    }

    public long getOpenCount() { return openCount; }
    public long getOpenWaitMs() { return openWaitMs; }
    public long getLastOpenPosition() { return lastOpenPosition; }
    public long getLastSourceOpenMonotonicMs() { return lastSourceOpenMonotonicMs; }
    public long getFirstReadAfterOpenMonotonicMs() { return firstReadAfterOpenMonotonicMs; }
    public long getFirstReadAfterOpenPosition() { return firstReadAfterOpenPosition; }
    public long getLastPhysicalReadMonotonicMs() { return lastPhysicalReadMonotonicMs; }
    public long getLastPhysicalReadPosition() { return lastPhysicalReadPosition; }
    public long getNetworkReadCount() { return closedNetworkReadCount + (smbSource == null ? activePullReadCount() : smbSource.getFallbackNetworkReadCount()); }
    public long getNetworkReadRequestedBytes() { return closedNetworkReadRequestedBytes + (smbSource == null ? activePullRequestedBytes() : smbSource.getFallbackNetworkReadRequestedBytes()); }
    public long getNetworkReadBytes() { return closedNetworkReadBytes + (smbSource == null ? activePullBytes() : smbSource.getFallbackNetworkReadBytes()); }
    public long getNetworkReadWaitMs() { return closedNetworkReadWaitMs + (smbSource == null ? activePullWaitMs() : smbSource.getFallbackNetworkReadWaitMs()); }
    public long getNetworkReadErrors() { return closedNetworkReadErrors + (smbSource == null ? activePullErrors() : smbSource.getFallbackNetworkReadErrors()); }
    public long getNetworkReadMaxRequestedBytes() { return Math.max(networkReadMaxRequestedBytes, smbSource == null ? activePullMaxRequested() : smbSource.getFallbackNetworkReadMaxRequestedBytes()); }
    public long getNetworkLastReadPosition() { return smbSource == null ? activePullLastPosition() : smbSource.getFallbackNetworkLastReadPosition(); }
    public boolean hasReachedEndOfInput() { return endOfInput; }

    private BufferedPullDataSource activePull() { return dataSource instanceof BufferedPullDataSource ? (BufferedPullDataSource) dataSource : null; }
    private long activePullReadCount() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadCount(); }
    private long activePullRequestedBytes() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadRequestedBytes(); }
    private long activePullBytes() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadBytes(); }
    private long activePullWaitMs() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadWaitMs(); }
    private long activePullErrors() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadErrors(); }
    private long activePullMaxRequested() { BufferedPullDataSource p = activePull(); return p == null ? 0 : p.getNetworkReadMaxRequestedBytes(); }
    private long activePullLastPosition() { BufferedPullDataSource p = activePull(); return p == null ? -1 : p.getNetworkLastReadPosition(); }

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
                if (potentiallyGrowing && dataSource != null)
                {
                    long refreshedSize = dataSource instanceof GrowingDataSource
                            ? ((GrowingDataSource) dataSource).waitForGrowth(startPos, 2000)
                            : dataSource.size();
                    if (refreshedSize > startPos)
                    {
                        bytesRemaining = refreshedSize - startPos;
                    }
                    else
                    {
                        endOfInput = true;
                        return C.RESULT_END_OF_INPUT;
                    }
                }
                else
                {
                    endOfInput = true;
                    return C.RESULT_END_OF_INPUT;
                }
            }
            if (dataSource == null)
            {
                throw new IOException("Media3 Pull datasource closed during non-zero read");
            }

            int bytesToRead = bytesRemaining == C.LENGTH_UNSET
                    ? readLength
                    : (int) Math.min((long) readLength, bytesRemaining);
            long physicalReadsBefore = currentPhysicalReadCount();
            long physicalReadStartedMs = SystemClock.elapsedRealtime();
            long requestedPosition = startPos;
            int bytes = dataSource.read(startPos, buffer, offset, bytesToRead);
            if (currentPhysicalReadCount() > physicalReadsBefore)
            {
                lastPhysicalReadMonotonicMs = physicalReadStartedMs;
                lastPhysicalReadPosition = requestedPosition;
            }

            if (bytes > 0 && firstReadAfterOpenMonotonicMs < 0)
            {
                firstReadAfterOpenPosition = startPos;
                firstReadAfterOpenMonotonicMs = SystemClock.elapsedRealtime();
            }

            if (bytes == -1)
            {
                //log.debug("DATA SOURCE RETURNED -1");
                endOfInput = true;
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

    @Override
    public void releaseSession()
    {
        try { close(); }
        catch (IOException ignored) { }
        if (smbSource != null) smbSource.release();
    }

    public boolean isSmbModeConfigured() { return smbConfig != null; }

    private long currentPhysicalReadCount()
    {
        if (smbSource == null) return getNetworkReadCount();
        return "SMB_DIRECT".equals(smbSource.getPlaybackSource())
                ? smbSource.getSmbReadCount() : smbSource.getFallbackNetworkReadCount();
    }

    @Override public String getPlaybackSource() { return smbSource == null ? "SAGETV_PULL" : smbSource.getPlaybackSource(); }
    @Override public String getSageOriginalPath() { return smbSource == null ? "" : smbSource.getSageOriginalPath(); }
    @Override public String getSmbMappedPath() { return smbSource == null ? "" : smbSource.getSmbMappedPath(); }
    @Override public boolean isSmbConnected() { return smbSource != null && smbSource.isSmbConnected(); }
    @Override public boolean isShadowConnected() { return smbSource != null && smbSource.isShadowConnected(); }
    @Override public boolean isShadowOpenSent() { return smbSource != null && smbSource.isShadowOpenSent(); }
    @Override public boolean isShadowSizeSent() { return smbSource != null && smbSource.isShadowSizeSent(); }
    @Override public long getShadowReadBytes() { return smbSource == null ? 0 : smbSource.getShadowReadBytes(); }
    @Override public long getSmbBytesRead() { return smbSource == null ? 0 : smbSource.getSmbBytesRead(); }
    @Override public long getSmbReadCount() { return smbSource == null ? 0 : smbSource.getSmbReadCount(); }
    @Override public long getSmbSeekCount() { return smbSource == null ? 0 : smbSource.getSmbSeekCount(); }
    @Override public long getSmbLastReadLatencyMs() { return smbSource == null ? 0 : smbSource.getSmbLastReadLatencyMs(); }
    @Override public long getSmbCacheHitBytes() { return smbSource == null ? 0 : smbSource.getSmbCacheHitBytes(); }
    @Override public long getSmbCacheMissCount() { return smbSource == null ? 0 : smbSource.getSmbCacheMissCount(); }
    @Override public int getSmbReadAheadBytes() { return smbSource == null ? 0 : smbSource.getSmbReadAheadBytes(); }
    @Override public long getSmbFallbackCount() { return smbSource == null ? 0 : smbSource.getSmbFallbackCount(); }
    @Override public String getSmbFallbackReason() { return smbSource == null ? "" : smbSource.getSmbFallbackReason(); }


//    @Override
//    public Map<String, List<String>> getResponseHeaders() {
//        return Collections.emptyMap();
//    }
//
//    @Override
//    public void addTransferListener(TransferListener transferListener) {
//    }
}
