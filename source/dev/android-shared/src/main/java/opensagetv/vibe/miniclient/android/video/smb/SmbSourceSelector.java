package opensagetv.vibe.miniclient.android.video.smb;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.BufferedPullDataSource;
import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;
import opensagetv.vibe.miniclient.net.ReadAheadDataSource;

/** Selects SMB bytes or, only in Auto mode, ordinary SageTV Pull bytes. */
public final class SmbSourceSelector implements ISageTVDataSource, GrowingDataSource, SmbTelemetrySource
{
    private final String sageServerHost;
    private final SmbDirectConfig config;
    private final int fallbackBufferBytes;

    private SmbDirectSession smb;
    private ReadAheadDataSource smbBuffered;
    private BufferedPullDataSource fallback;
    private boolean usingFallback;
    private long fallbackCount;
    private String fallbackReason = "";
    private String attemptedOriginalPath = "";
    private String attemptedMappedPath = "";
    private long closedFallbackReadCount;
    private long closedFallbackRequestedBytes;
    private long closedFallbackReadBytes;
    private long closedFallbackWaitMs;
    private long closedFallbackErrors;
    private long fallbackMaxRequestedBytes;
    private long fallbackLastReadPosition = -1;
    private String releasedOriginalPath = "";
    private String releasedMappedPath = "";
    private boolean releasedShadowOpenSent;
    private boolean releasedShadowSizeSent;
    private long releasedShadowReadBytes;
    private long releasedSmbBytesRead;
    private long releasedSmbReadCount;
    private long releasedSmbSeekCount;
    private long releasedSmbLastReadLatencyMs;
    private long releasedSmbCacheHitBytes;
    private long releasedSmbCacheMissCount;

    public SmbSourceSelector(String sageServerHost, SmbDirectConfig config, int fallbackBufferBytes)
    {
        this.sageServerHost = sageServerHost;
        this.config = config;
        this.fallbackBufferBytes = fallbackBufferBytes;
    }

    @Override
    public synchronized long open(String uri) throws IOException
    {
        if (!usingFallback)
        {
            try
            {
                if (smb == null)
                {
                    smb = new SmbDirectSession(sageServerHost, config);
                    smbBuffered = new ReadAheadDataSource(smb, config.getReadAheadBytes());
                }
                return smbBuffered.open(uri);
            }
            catch (Throwable failure)
            {
                if (smb != null)
                {
                    attemptedOriginalPath = smb.getSageOriginalPath();
                    attemptedMappedPath = smb.getSmbMappedPath();
                }
                fallbackReason = safeReason(failure);
                if (!config.isAutoFallback())
                {
                    if (failure instanceof IOException) throw (IOException) failure;
                    throw new IOException("SMB Direct startup failed", failure);
                }
                if (smb != null) smb.release();
                smb = null;
                usingFallback = true;
                fallbackCount++;
            }
        }

        fallback = new BufferedPullDataSource(sageServerHost, fallbackBufferBytes);
        return fallback.open(uri);
    }

    @Override
    public synchronized int read(long position, byte[] buffer, int offset, int len) throws IOException
    {
        if (usingFallback)
        {
            if (fallback == null) throw new IOException("Fallback Pull source is not open");
            return fallback.read(position, buffer, offset, len);
        }
        if (smb == null) throw new IOException("SMB Direct source is not open");
        return smbBuffered.read(position, buffer, offset, len);
    }

    @Override
    public synchronized long waitForGrowth(long position, long timeoutMs) throws IOException
    {
        if (usingFallback)
        {
            if (fallback == null) return -1;
            return fallback.waitForGrowth(position, timeoutMs);
        }
        if (smb == null) return -1;
        return smb.waitForGrowth(position, timeoutMs);
    }

    @Override
    public synchronized long size()
    {
        if (usingFallback) return fallback == null ? -1 : fallback.size();
        return smb == null ? -1 : smb.size();
    }

    /** Called after one Exo range. The SMB playback session intentionally stays open. */
    @Override
    public synchronized void close()
    {
        if (fallback != null)
        {
            closedFallbackReadCount += fallback.getNetworkReadCount();
            closedFallbackRequestedBytes += fallback.getNetworkReadRequestedBytes();
            closedFallbackReadBytes += fallback.getNetworkReadBytes();
            closedFallbackWaitMs += fallback.getNetworkReadWaitMs();
            closedFallbackErrors += fallback.getNetworkReadErrors();
            fallbackMaxRequestedBytes = Math.max(fallbackMaxRequestedBytes,
                    fallback.getNetworkReadMaxRequestedBytes());
            fallbackLastReadPosition = fallback.getNetworkLastReadPosition();
            fallback.close();
            fallback = null;
        }
    }

    public synchronized void release()
    {
        close();
        if (smb != null)
        {
            releasedOriginalPath = smb.getSageOriginalPath();
            releasedMappedPath = smb.getSmbMappedPath();
            releasedShadowOpenSent = smb.isShadowOpenSent();
            releasedShadowSizeSent = smb.isShadowSizeSent();
            releasedShadowReadBytes = smb.getShadowReadBytes();
            releasedSmbBytesRead = smb.getSmbBytesRead();
            releasedSmbReadCount = smb.getSmbReadCount();
            releasedSmbSeekCount = smb.getSmbSeekCount();
            releasedSmbLastReadLatencyMs = smb.getSmbLastReadLatencyMs();
            releasedSmbCacheHitBytes = smbBuffered == null ? 0 : smbBuffered.getCacheHitBytes();
            releasedSmbCacheMissCount = smbBuffered == null ? 0 : smbBuffered.getCacheMissCount();
            smb.release();
        }
        smb = null;
        smbBuffered = null;
        config.clear();
    }

    private static String safeReason(Throwable failure)
    {
        String name = failure == null ? "unknown" : failure.getClass().getSimpleName();
        String message = failure == null ? "" : failure.getMessage();
        if (message == null || message.trim().isEmpty()) return name;
        // Do not return a connection URL, username, or password-bearing cause.
        if (message.contains("smb://") || message.contains("@")) return name;
        return name + ":" + message.replace(';', '_').replace('\n', ' ').replace('\r', ' ');
    }

    public synchronized long getFallbackNetworkReadCount() { return closedFallbackReadCount + (fallback == null ? 0 : fallback.getNetworkReadCount()); }
    public synchronized long getFallbackNetworkReadRequestedBytes() { return closedFallbackRequestedBytes + (fallback == null ? 0 : fallback.getNetworkReadRequestedBytes()); }
    public synchronized long getFallbackNetworkReadBytes() { return closedFallbackReadBytes + (fallback == null ? 0 : fallback.getNetworkReadBytes()); }
    public synchronized long getFallbackNetworkReadWaitMs() { return closedFallbackWaitMs + (fallback == null ? 0 : fallback.getNetworkReadWaitMs()); }
    public synchronized long getFallbackNetworkReadErrors() { return closedFallbackErrors + (fallback == null ? 0 : fallback.getNetworkReadErrors()); }
    public synchronized long getFallbackNetworkReadMaxRequestedBytes() { return Math.max(fallbackMaxRequestedBytes, fallback == null ? 0 : fallback.getNetworkReadMaxRequestedBytes()); }
    public synchronized long getFallbackNetworkLastReadPosition() { return fallback == null ? fallbackLastReadPosition : fallback.getNetworkLastReadPosition(); }

    @Override public synchronized String getPlaybackSource() { return usingFallback ? "SAGETV_PULL" : "SMB_DIRECT"; }
    @Override public synchronized String getSageOriginalPath() { return smb == null ? firstNonEmpty(releasedOriginalPath, attemptedOriginalPath) : smb.getSageOriginalPath(); }
    @Override public synchronized String getSmbMappedPath() { return smb == null ? firstNonEmpty(releasedMappedPath, attemptedMappedPath) : smb.getSmbMappedPath(); }
    @Override public synchronized boolean isSmbConnected() { return smb != null && smb.isSmbConnected(); }
    @Override public synchronized boolean isShadowConnected() { return smb != null && smb.isShadowConnected(); }
    @Override public synchronized boolean isShadowOpenSent() { return smb == null ? releasedShadowOpenSent : smb.isShadowOpenSent(); }
    @Override public synchronized boolean isShadowSizeSent() { return smb == null ? releasedShadowSizeSent : smb.isShadowSizeSent(); }
    @Override public synchronized long getShadowReadBytes() { return smb == null ? releasedShadowReadBytes : smb.getShadowReadBytes(); }
    @Override public synchronized long getSmbBytesRead() { return smb == null ? releasedSmbBytesRead : smb.getSmbBytesRead(); }
    @Override public synchronized long getSmbReadCount() { return smb == null ? releasedSmbReadCount : smb.getSmbReadCount(); }
    @Override public synchronized long getSmbSeekCount() { return smb == null ? releasedSmbSeekCount : smb.getSmbSeekCount(); }
    @Override public synchronized long getSmbLastReadLatencyMs() { return smb == null ? releasedSmbLastReadLatencyMs : smb.getSmbLastReadLatencyMs(); }
    @Override public synchronized long getSmbCacheHitBytes() { return smbBuffered == null ? releasedSmbCacheHitBytes : smbBuffered.getCacheHitBytes(); }
    @Override public synchronized long getSmbCacheMissCount() { return smbBuffered == null ? releasedSmbCacheMissCount : smbBuffered.getCacheMissCount(); }
    @Override public synchronized int getSmbReadAheadBytes() { return config.getReadAheadBytes(); }
    @Override public synchronized long getSmbFallbackCount() { return fallbackCount; }
    @Override public synchronized String getSmbFallbackReason() { return fallbackReason; }

    private static String firstNonEmpty(String first, String second)
    {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }
}
