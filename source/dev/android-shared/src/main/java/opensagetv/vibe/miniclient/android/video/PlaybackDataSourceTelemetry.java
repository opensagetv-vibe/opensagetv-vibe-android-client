package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.android.video.smb.SmbTelemetrySource;

/**
 * On-demand telemetry exposed by random-access Pull datasources.
 *
 * <p>The contract is deliberately backend-neutral so debug/MCP inspection does
 * not need reflection or knowledge of the Media3 and legacy ExoPlayer package
 * layouts. Implementations retain ownership of their counters; callers only
 * take a snapshot by invoking these inexpensive accessors.</p>
 */
public interface PlaybackDataSourceTelemetry extends SmbTelemetrySource
{
    long getOpenCount();
    long getOpenWaitMs();
    long getLastOpenPosition();
    long getLastSourceOpenMonotonicMs();
    long getFirstReadAfterOpenMonotonicMs();
    long getFirstReadAfterOpenPosition();
    long getLastPhysicalReadMonotonicMs();
    long getLastPhysicalReadPosition();
    long getNetworkReadCount();
    long getNetworkReadRequestedBytes();
    long getNetworkReadBytes();
    long getNetworkReadWaitMs();
    long getNetworkReadMaxRequestedBytes();
    long getNetworkReadErrors();
    long getNetworkLastReadPosition();
}
