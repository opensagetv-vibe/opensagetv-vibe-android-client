package opensagetv.vibe.miniclient.android.video.smb;

public interface SmbTelemetrySource
{
    String getPlaybackSource();
    String getSageOriginalPath();
    String getSmbMappedPath();
    boolean isSmbConnected();
    boolean isShadowConnected();
    boolean isShadowOpenSent();
    boolean isShadowSizeSent();
    long getShadowReadBytes();
    long getSmbBytesRead();
    long getSmbReadCount();
    long getSmbSeekCount();
    long getSmbLastReadLatencyMs();
    long getSmbCacheHitBytes();
    long getSmbCacheMissCount();
    int getSmbReadAheadBytes();
    long getSmbFallbackCount();
    String getSmbFallbackReason();
}
