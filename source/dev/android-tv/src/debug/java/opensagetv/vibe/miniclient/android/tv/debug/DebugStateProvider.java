package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.ageMs;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.safe;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.text;

import android.content.Context;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.ConnectionLifecycleDiagnostics;
import opensagetv.vibe.miniclient.GFXCMD2;
import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.dvd.DvdDiagnostics;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.video.ActivePlayerSessionOverrides;
import opensagetv.vibe.miniclient.android.ActivePlayerProcessOverlay;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.DisplayRefreshController;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;
import opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.gsy.GSYPlayerEngine;
import opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.RectangleF;
import opensagetv.vibe.miniclient.video.HasVideoInfo;
import opensagetv.vibe.miniclient.video.DiscPlaybackPolicy;
import opensagetv.vibe.miniclient.video.VideoInfoResponse;

/** Builds bounded point-in-time debug state without subscribing to player callbacks. */
final class DebugStateProvider
{
    private DebugStateProvider()
    {
    }

    static String snapshot(Context context, int maxRecoveryWatchdogMs)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        StringBuilder out = new StringBuilder("op=snapshot;");
        out.append(configuredValues(prefs));
        boolean connected = client.isConnected();
        out.append(";connected=").append(connected);
        out.append(";legacyCaptionCallbacksNegotiated=")
                .append(client.getCurrentConnection() != null
                        && client.getCurrentConnection().isSubtitleCallbackEnabled());
        out.append(";legacyCaptionWireEventCount=")
                .append(client.getCurrentConnection() == null ? 0
                        : client.getCurrentConnection().getSubtitleCallbackEventCount());
        out.append(";legacyCaptionWireBytes=")
                .append(client.getCurrentConnection() == null ? 0
                        : client.getCurrentConnection().getSubtitleCallbackByteCount());
        GFXCMD2 gfx = client.getCurrentConnection() == null ? null
                : client.getCurrentConnection().getGfxCmd();
        out.append(";imageAllocationRecoveryAttempts=")
                .append(gfx == null ? 0 : gfx.getImageAllocationRecoveryAttempts());
        out.append(";imageAllocationRecoveryEvictions=")
                .append(gfx == null ? 0 : gfx.getImageAllocationRecoveryEvictions());
        out.append(";imageAllocationRecoverySuccesses=")
                .append(gfx == null ? 0 : gfx.getImageAllocationRecoverySuccesses());
        out.append(";imageAllocationRecoveryFailures=")
                .append(gfx == null ? 0 : gfx.getImageAllocationRecoveryFailures());
        String configuredClientId = clean(prefs.getString(PrefStore.Keys.client_id));
        String activeClientId = configuredClientId;
        if (client.getCurrentConnection() != null)
            activeClientId = clean(client.getCurrentConnection().getClientID());
        out.append(";clientId=").append(safe(activeClientId));
        out.append(";configuredClientId=").append(safe(configuredClientId));
        out.append(";uiContextHint=").append(safe(activeClientId.replace(":", "")));
        ServerInfo connectedServer = client.getConnectedServerInfo();
        if (connectedServer != null)
        {
            out.append(";serverName=").append(safe(connectedServer.name));
            out.append(";serverAddress=").append(safe(connectedServer.address));
            out.append(";serverPort=").append(connectedServer.port);
        }

        String menuName = "";
        String popupName = "";
        boolean hasTextInput = false;
        if (client.getCurrentConnection() != null)
        {
            MenuHint hint = client.getCurrentConnection().getMenuHint();
            if (hint != null)
            {
                menuName = text(hint.menuName);
                popupName = text(hint.popupName);
                hasTextInput = hint.hasTextInput;
                out.append(";menuName=").append(safe(menuName));
                out.append(";popupName=").append(safe(popupName));
                out.append(";hasTextInput=").append(hasTextInput);
            }
        }

        MediaCmd mediaCmd = null;
        MiniPlayerPlugin player = null;
        if (connected && client.getCurrentConnection() != null)
        {
            mediaCmd = client.getCurrentConnection().getMediaCmd();
            if (mediaCmd != null)
                player = mediaCmd.getPlaya();
        }
        boolean playerActive = player != null;
        out.append(";playerActive=").append(playerActive);

        String uiState;
        if (!connected)
            uiState = "disconnected";
        else if (playerActive)
            uiState = "playback";
        else if (hasTextInput)
            uiState = "text_input";
        else if ("Main Menu".equalsIgnoreCase(menuName) && popupName.isEmpty())
            uiState = "main_menu";
        else if (menuName.isEmpty())
            uiState = "loading";
        else
            uiState = "menu";
        boolean automationReady = connected
                && !playerActive
                && !hasTextInput
                && popupName.isEmpty()
                && "Main Menu".equalsIgnoreCase(menuName);
        int imeVisibility = UIActivityLifeCycleHandler.getImeVisibilityForDebug();
        out.append(";debugStatusVersion=21");
        out.append(";maxRecoveryWatchdogMs=").append(maxRecoveryWatchdogMs);
        out.append(";uiState=").append(uiState);
        out.append(";automationReady=").append(automationReady);
        out.append(";imeRequested=").append(UIActivityLifeCycleHandler.isKeyboardRequestedForDebug());
        out.append(";imeSuppressedForDebug=").append(UIActivityLifeCycleHandler.isKeyboardSuppressedForDebug());
        out.append(";imeVisibleKnown=").append(imeVisibility >= 0);
        out.append(";imeVisible=").append(imeVisibility > 0);
        out.append(';').append(PlayerRuntimeTuning.compactWire());
        out.append(";activePlayerOverrides=")
                .append(safe(ActivePlayerSessionOverrides.compactSummary()));
        out.append(";activePlayerProcessOverlayVisible=")
                .append(ActivePlayerProcessOverlay.isVisible());
        out.append(";activePlayerRefreshSettleMs=").append(
                ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                        prefs.getInt(PrefStore.Keys.playback_refresh_settle_ms, 0)));
        out.append(";activePlayerRefreshReloadPendingMs=")
                .append(DisplayRefreshController.getPendingReloadMs());
        out.append(';').append(ConnectionLifecycleDiagnostics.latestCompactWire());
        out.append(';').append(MiniclientApplication.get().getBackgroundSessionOwner().compactWire());

        // The DVD server VM can wait or loop before a player exists. Keep the
        // handshake visible in that exact failure state rather than hiding it
        // behind playerActive.
        if (mediaCmd != null)
        {
            out.append(";sageTvCaptionStateReceived=")
                    .append(mediaCmd.hasSageTvClosedCaptionState());
            out.append(";sageTvCaptionState=")
                    .append(mediaCmd.getSageTvClosedCaptionState());
            out.append(";legacyServerCaptionFallbackActive=")
                    .append(mediaCmd.isLegacyServerCaptionFallbackActive());
            out.append(";lastPushPayloadBytes=").append(mediaCmd.getLastPushPayloadBytes());
            out.append(";lastPushFlags=").append(mediaCmd.getLastPushFlags());
            out.append(";lastPushReply=").append(mediaCmd.getLastPushReply());
            out.append(";dvdSessionPending=").append(mediaCmd.isDvdSessionPending());
            out.append(";dvdPushedBytes=").append(mediaCmd.getDvdPushedBytes());
            out.append(";dvdEpochPushedBytes=").append(mediaCmd.getDvdEpochPushedBytes());
            out.append(";dvdLastReadBytes=").append(mediaCmd.getDvdLastReadBytes());
            out.append(";dvdDrainPollCount=").append(mediaCmd.getDvdDrainPollCount());
            out.append(";dvdDrainReadyCount=").append(mediaCmd.getDvdDrainReadyCount());
            out.append(";dvdDecoderBufferedAheadMs=").append(mediaCmd.getDvdDecoderBufferedAheadMs());
            out.append(";dvdInitCount=").append(mediaCmd.getDvdInitCount());
            out.append(";dvdPushCommandCount=").append(mediaCmd.getDvdPushCommandCount());
            out.append(";dvdPushMediaCount=").append(mediaCmd.getDvdPushMediaCount());
            out.append(";dvdNewCellCount=").append(mediaCmd.getDvdNewCellCount());
            out.append(";dvdClutCount=").append(mediaCmd.getDvdClutCount());
            out.append(";dvdSpuControlCount=").append(mediaCmd.getDvdSpuControlCount());
            out.append(";dvdStcCount=").append(mediaCmd.getDvdStcCount());
            out.append(";dvdStreamCount=").append(mediaCmd.getDvdStreamCount());
            out.append(";dvdLastStreamType=").append(mediaCmd.getDvdLastStreamType());
            out.append(";dvdLastStreamPosition=").append(mediaCmd.getDvdLastStreamPosition());
            out.append(";dvdLastAudioStreamPosition=")
                    .append(mediaCmd.getDvdLastAudioStreamPosition());
            out.append(";dvdLastSubtitleStreamPosition=")
                    .append(mediaCmd.getDvdLastSubtitleStreamPosition());
            out.append(";dvdFormatCount=").append(mediaCmd.getDvdFormatCount());
            out.append(";dvdTransientEosCount=").append(mediaCmd.getDvdTransientEosCount());
            DiscPlaybackPolicy.Resolution disc = DiscPlaybackPolicy.resolve(
                    prefs.getString(PrefStore.Keys.disc_playback_policy, "auto"),
                    prefs.getBoolean(PrefStore.Keys.disc_compatibility_fallback, true),
                    false);
            boolean oldServerNativeFallback = disc.effective
                    == DiscPlaybackPolicy.Effective.UNAVAILABLE
                    && (mediaCmd.getDvdPushMediaCount() > 0
                            || mediaCmd.getDvdNewCellCount() > 0);
            boolean mimRuntimeFallback = mediaCmd.isDvdMimRuntimeFallback();
            out.append(";discOldServerNativeFallback=").append(oldServerNativeFallback);
            out.append(";discMimRuntimeFallback=").append(mimRuntimeFallback);
            out.append(";discCompatibilityReason=").append(safe(
                    mimRuntimeFallback
                            ? "FFmpeg/MIM failed after Hybrid DVD startup; native SageTV DVD playback was restored."
                            : oldServerNativeFallback ? disc.reason : ""));
        }

        if (!playerActive || mediaCmd == null)
            return out.toString();

        long serverSeekMonotonicMs = mediaCmd.getLastServerSeekMonotonicMs();
        long serverFlushMonotonicMs = mediaCmd.getLastServerFlushMonotonicMs();
        long serverAnchorMonotonicMs = mediaCmd.getLastServerAnchorMonotonicMs();
        long nowMonotonicMs = System.nanoTime() / 1000000L;
        long sageTimelineMs = player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition());
        out.append(";playerClass=").append(safe(player.getClass().getName()));
        out.append(";state=").append(player.getState());
        out.append(";mediaTimeMs=").append(sageTimelineMs);
        out.append(";sageTimelineMs=").append(sageTimelineMs);
        out.append(";playbackRate=").append(player.getPlaybackRate());
        out.append(";textSubtitlePresentationSupported=")
                .append(player.supportsTextSubtitlePresentation());
        out.append(";textSubtitleSafeAreaPercent=")
                .append(player.getTextSubtitleSafeAreaPercent());
        out.append(";textSubtitleScalePercent=")
                .append(player.getTextSubtitleScalePercent());
        out.append(";textSubtitleStyle=").append(safe(player.getTextSubtitleStyle()));
        out.append(";timelineSource=MEDIACMD_GETMEDIATIME");
        out.append(";serverAnchorMs=").append(mediaCmd.getLastServerStartPosition());
        out.append(";serverRequestedSeekMs=").append(mediaCmd.getLastServerRequestedSeekMs());
        out.append(";serverSeekSequence=").append(mediaCmd.getLastServerSeekSequence());
        out.append(";serverSeekMonotonicMs=").append(serverSeekMonotonicMs);
        out.append(";serverSeekWallMs=").append(mediaCmd.getLastServerSeekWallMs());
        out.append(";serverSeekAgeMs=").append(ageMs(nowMonotonicMs, serverSeekMonotonicMs));
        out.append(";serverFlushSequence=").append(mediaCmd.getLastServerFlushSequence());
        out.append(";serverFlushMonotonicMs=").append(serverFlushMonotonicMs);
        out.append(";serverFlushAgeMs=").append(ageMs(nowMonotonicMs, serverFlushMonotonicMs));
        out.append(";serverAnchorSequence=").append(mediaCmd.getLastServerAnchorSequence());
        out.append(";serverAnchorMonotonicMs=").append(serverAnchorMonotonicMs);
        out.append(";serverAnchorAgeMs=").append(ageMs(nowMonotonicMs, serverAnchorMonotonicMs));
        long detailedPushMonotonicMs = mediaCmd.getDetailedPushSampleMonotonicMs();
        out.append(";serverChannelBandwidthKbps=").append(mediaCmd.getServerChannelBandwidthKbps());
        out.append(";serverStreamBandwidthKbps=").append(mediaCmd.getServerStreamBandwidthKbps());
        out.append(";serverTargetBandwidthKbps=").append(mediaCmd.getServerTargetBandwidthKbps());
        out.append(";serverMuxTimeMs=").append(mediaCmd.getServerMuxTimeMs());
        out.append(";clientBufferTimeMs=").append(mediaCmd.getClientBufferTimeMs());
        out.append(";clientBufferAvailableBytes=").append(mediaCmd.getClientBufferAvailableBytes());
        out.append(";lastPushPayloadBytes=").append(mediaCmd.getLastPushPayloadBytes());
        out.append(";lastPushFlags=").append(mediaCmd.getLastPushFlags());
        out.append(";lastPushReply=").append(mediaCmd.getLastPushReply());
        out.append(";dvdSessionPending=").append(mediaCmd.isDvdSessionPending());
        out.append(";dvdPushedBytes=").append(mediaCmd.getDvdPushedBytes());
        out.append(";dvdEpochPushedBytes=").append(mediaCmd.getDvdEpochPushedBytes());
        out.append(";dvdLastReadBytes=").append(mediaCmd.getDvdLastReadBytes());
        out.append(";dvdDrainPollCount=").append(mediaCmd.getDvdDrainPollCount());
        out.append(";dvdDrainReadyCount=").append(mediaCmd.getDvdDrainReadyCount());
        out.append(";dvdDecoderBufferedAheadMs=").append(mediaCmd.getDvdDecoderBufferedAheadMs());
        out.append(";dvdTransientEosCount=").append(mediaCmd.getDvdTransientEosCount());
        out.append(";detailedPushSampleSequence=").append(mediaCmd.getDetailedPushSampleSequence());
        out.append(";detailedPushSampleMonotonicMs=").append(detailedPushMonotonicMs);
        out.append(";detailedPushSampleWallMs=").append(mediaCmd.getDetailedPushSampleWallMs());
        out.append(";detailedPushSampleAgeMs=").append(ageMs(nowMonotonicMs, detailedPushMonotonicMs));
        out.append(";bufferLeft=").append(player.getBufferLeft());
        out.append(";lastFileReadPos=").append(player.getLastFileReadPos());
        out.append(";mediaUri=").append(safe(mediaCmd.getLastOpenUrlForDebug()));
        String mediaChannel = clean(mediaCmd.getLastOpenChannelForDebug());
        if (mediaChannel.isEmpty() && client.getCurrentConnection() != null)
            mediaChannel = clean(client.getCurrentConnection().getVibeCurrentChannel());
        out.append(";mediaChannel=").append(safe(mediaChannel));
        out.append(";vibeChannelAckSequence=").append(
                client.getCurrentConnection() == null ? 0L :
                        client.getCurrentConnection().getVibeChannelAckSequence());
        appendSubtitleState(out, player);
        appendVideoLayoutState(out, player);
        appendFastSwitchState(out, player);
        appendDvdTimestampState(out, player);
        if (player instanceof GSYMediaPlayerImpl)
        {
            GSYMediaPlayerImpl gsy = (GSYMediaPlayerImpl) player;
            out.append(";gsyResolvedEngine=").append(safe(gsy.getResolvedEngineForDebug()));
            out.append(";gsySystemFallbackCount=").append(
                    gsy.getSystemFallbackCountForDebug());
            out.append(";gsySystemFallbackReason=").append(
                    safe(gsy.getSystemFallbackReasonForDebug()));
        }

        Dimension video = player.getVideoDimensions();
        if (video != null)
        {
            out.append(";videoWidth=").append(video.width);
            out.append(";videoHeight=").append(video.height);
        }
        PlaybackHealthProbe.Snapshot health = PlaybackHealthProbe.capture(player);
        out.append(";playbackSource=").append(safe(health.playbackSource));
        out.append(";sageOriginalPath=").append(safe(health.sageOriginalPath));
        out.append(";smbMappedPath=").append(safe(health.smbMappedPath));
        out.append(";smbConnected=").append(health.smbConnected);
        out.append(";shadowMediaServerConnected=").append(health.shadowMediaServerConnected);
        out.append(";shadowOpenSent=").append(health.shadowOpenSent);
        out.append(";shadowSizeSent=").append(health.shadowSizeSent);
        out.append(";shadowReadBytes=").append(health.shadowReadBytes);
        out.append(";smbBytesRead=").append(health.smbBytesRead);
        out.append(";smbReadCount=").append(health.smbReadCount);
        out.append(";smbSeekCount=").append(health.smbSeekCount);
        out.append(";smbLastReadLatencyMs=").append(health.smbLastReadLatencyMs);
        out.append(";smbCacheHitBytes=").append(health.smbCacheHitBytes);
        out.append(";smbCacheMissCount=").append(health.smbCacheMissCount);
        out.append(";smbReadAheadBytes=").append(health.smbReadAheadBytes);
        out.append(";smbFallbackCount=").append(health.smbFallbackCount);
        out.append(";smbFallbackReason=").append(safe(health.smbFallbackReason));
        out.append(";sourceOpenMonotonicMs=").append(health.dataSourceLastOpenMonotonicMs);
        out.append(";sourceFirstReadMonotonicMs=").append(health.dataSourceFirstReadAfterOpenMonotonicMs);
        out.append(";sourceFirstReadPosition=").append(health.dataSourceFirstReadAfterOpenPosition);
        out.append(health.compactWire("health_"));
        out.append(';').append(PlaybackEventTraps.compactWire());
        return out.toString();
    }

    private static void appendVideoLayoutState(StringBuilder out, MiniPlayerPlugin player)
    {
        try
        {
            MiniPlayerPlugin layoutPlayer = player;
            if (player instanceof GSYMediaPlayerImpl)
            {
                MiniPlayerPlugin delegate = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
                if (delegate != null)
                    layoutPlayer = delegate;
            }
            if (!(layoutPlayer instanceof HasVideoInfo))
                return;

            VideoInfoResponse response = ((HasVideoInfo) layoutPlayer).getVideoInfo();
            if (response == null || response.videoInfo == null)
                return;
            RectangleF dest = response.videoInfo.destRect;
            RectangleF screen = response.uiScreenSizePixels;
            if (dest != null)
            {
                out.append(";videoDestX=").append(Math.round(dest.x));
                out.append(";videoDestY=").append(Math.round(dest.y));
                out.append(";videoDestWidth=").append(Math.round(dest.width));
                out.append(";videoDestHeight=").append(Math.round(dest.height));
            }
            if (screen != null)
            {
                out.append(";videoUiWidth=").append(Math.round(screen.width));
                out.append(";videoUiHeight=").append(Math.round(screen.height));
            }
        }
        catch (Throwable t)
        {
            out.append(";videoLayoutStateError=").append(safe(t.getClass().getSimpleName()));
        }
    }

    private static void appendFastSwitchState(StringBuilder out, MiniPlayerPlugin player)
    {
        MiniPlayerPlugin telemetryPlayer = player;
        if (player instanceof GSYMediaPlayerImpl)
        {
            MiniPlayerPlugin delegate = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
            if (delegate != null)
                telemetryPlayer = delegate;
        }
        if (!(telemetryPlayer instanceof Media3MediaPlayerImpl))
            return;
        Media3MediaPlayerImpl media3 = (Media3MediaPlayerImpl) telemetryPlayer;
        out.append(";fastSwitchAttemptCount=")
                .append(media3.getFastSwitchAttemptCountForDebug());
        out.append(";fastSwitchSuccessCount=")
                .append(media3.getFastSwitchSuccessCountForDebug());
        out.append(";fastSwitchFallbackCount=")
                .append(media3.getFastSwitchFallbackCountForDebug());
        out.append(";fastSwitchAwaitingFirstFrame=")
                .append(media3.isFastSwitchAwaitingFirstFrameForDebug());
        out.append(";fastSwitchLastReason=")
                .append(safe(media3.getFastSwitchLastReasonForDebug()));
        out.append(";fastSwitchTargetUrl=")
                .append(safe(media3.getFastSwitchTargetUrlForDebug()));
    }

    private static void appendDvdTimestampState(StringBuilder out, MiniPlayerPlugin player)
    {
        MiniPlayerPlugin telemetryPlayer = player;
        if (player instanceof GSYMediaPlayerImpl)
        {
            MiniPlayerPlugin delegate = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
            if (delegate != null)
                telemetryPlayer = delegate;
        }
        if (!(telemetryPlayer instanceof Media3MediaPlayerImpl))
            return;
        Media3MediaPlayerImpl media3 = (Media3MediaPlayerImpl) telemetryPlayer;
        out.append(";dvdLatestVideoSampleUs=")
                .append(media3.getDvdLatestVideoSampleUsForDebug());
        out.append(";dvdLatestAudioSampleUs=")
                .append(media3.getDvdLatestAudioSampleUsForDebug());
        out.append(";dvdAvSampleDeltaUs=")
                .append(media3.getDvdAvSampleDeltaUsForDebug());
        out.append(";dvdVideoTimestampCorrectionCount=")
                .append(media3.getDvdVideoTimestampCorrectionCountForDebug());
        out.append(";dvdMpeg2TimestampRepairEnabled=")
                .append(media3.isDvdMpeg2TimestampRepairEnabledForDebug());
        out.append(";dvdMpeg2ReportedFrameRateHz=")
                .append(media3.getDvdMpeg2ReportedFrameRateHzForDebug());
        out.append(";dvdMpeg2SequenceFrameRateHz=")
                .append(media3.getDvdMpeg2SequenceFrameRateHzForDebug());
        out.append(";dvdMpeg2EffectiveFieldDurationUs=")
                .append(media3.getDvdMpeg2EffectiveFieldDurationUsForDebug());
        out.append(";dvdMpeg2TelecineCadenceSeen=")
                .append(media3.isDvdMpeg2TelecineCadenceSeenForDebug());
        out.append(";dvdDiscontinuityRebaseCount=")
                .append(media3.getDvdDiscontinuityRebaseCountForDebug());
        out.append(";dvdPtsTrace=").append(safe(media3.getDvdPtsTraceForDebug()));
        out.append(";dvdFrameMetadataCount=")
                .append(media3.getDvdFrameMetadataCountForDebug());
        out.append(";dvdStc45Khz=").append(media3.getDvdStcForDebug());
        out.append(";dvdLogicalClockBaseMs=")
                .append(media3.getDvdLogicalClockBaseMsForDebug());
        out.append(";dvdRenderedVideoClockDeltaUs=")
                .append(media3.getDvdRenderedVideoClockDeltaUsForDebug());
        out.append(";dvdLastFramePresentationDeltaUs=")
                .append(media3.getDvdLastFramePresentationDeltaUsForDebug());
        out.append(";dvdLastFrameReleaseDeltaUs=")
                .append(media3.getDvdLastFrameReleaseDeltaUsForDebug());
        out.append(";dvdMaxFrameReleaseDeltaUs=")
                .append(media3.getDvdMaxFrameReleaseDeltaUsForDebug());
        out.append(";dvdFrameReleaseGapCount=")
                .append(media3.getDvdFrameReleaseGapCountForDebug());
        out.append(";dvdFrameReleaseNonPositiveCount=")
                .append(media3.getDvdFrameReleaseNonPositiveCountForDebug());
        out.append(";dvdFrameReleaseUnder10MsCount=")
                .append(media3.getDvdFrameReleaseUnder10MsCountForDebug());
        out.append(";dvdFrameRelease10To25MsCount=")
                .append(media3.getDvdFrameRelease10To25MsCountForDebug());
        out.append(";dvdFrameRelease25To45MsCount=")
                .append(media3.getDvdFrameRelease25To45MsCountForDebug());
        out.append(";dvdFrameRelease45To75MsCount=")
                .append(media3.getDvdFrameRelease45To75MsCountForDebug());
        out.append(";dvdFrameRelease75To100MsCount=")
                .append(media3.getDvdFrameRelease75To100MsCountForDebug());
        out.append(";dvdFrameCadenceTrace=")
                .append(media3.getDvdFrameCadenceTraceForDebug());
        out.append(";dvdRequestedAudioStream=")
                .append(media3.getRequestedDvdAudioStreamForDebug());
        out.append(";dvdAppliedAudioStream=")
                .append(media3.getAppliedDvdAudioStreamForDebug());
        out.append(";dvdSelectedAudioFormatId=")
                .append(safe(media3.getSelectedDvdAudioFormatIdForDebug()));
        out.append(";dvdAvailableAudioFormatIds=")
                .append(safe(media3.getAvailableDvdAudioFormatIdsForDebug()));
        DvdDiagnostics spu = media3.getDvdSubpictureDiagnosticsForDebug();
        out.append(";dvdSpuFragments=").append(spu.spuFragments);
        out.append(";dvdCompletedSpuPackets=").append(spu.completedSpuPackets);
        out.append(";dvdMalformedSpuPackets=").append(spu.malformedSpuPackets);
        out.append(";dvdDecodedSpuEvents=").append(spu.decodedSpuEvents);
        out.append(";dvdDroppedSpuEvents=").append(spu.droppedSpuEvents);
        out.append(";dvdOverlaysPresented=").append(spu.overlaysPresented);
        out.append(";dvdOverlaysCleared=").append(spu.overlaysCleared);
        out.append(";dvdOverlayEventsScheduled=").append(spu.overlayEventsScheduled);
        out.append(";dvdOverlayEventsApplied=").append(spu.overlayEventsApplied);
        out.append(";dvdOverlayEventsStale=").append(spu.overlayEventsStale);
        out.append(";dvdLastOverlayEventUs=").append(spu.lastOverlayEventUs);
        out.append(";dvdLastOverlayClockUs=").append(spu.lastOverlayClockUs);
        out.append(";dvdLastOverlayOpaquePixels=").append(spu.lastOverlayOpaquePixels);
        out.append(";dvdHighlightVisible=").append(spu.highlightVisible);
        out.append(";dvdHighlightX1=").append(spu.highlightX1);
        out.append(";dvdHighlightY1=").append(spu.highlightY1);
        out.append(";dvdHighlightX2=").append(spu.highlightX2);
        out.append(";dvdHighlightY2=").append(spu.highlightY2);
        out.append(";dvdHighlightPaletteWord=").append(spu.highlightPaletteWord);
    }

    private static void appendSubtitleState(StringBuilder out, MiniPlayerPlugin player)
    {
        try
        {
            SubtitleTrack[] tracks = player.getSubtitleTracks();
            out.append(";subtitleTrackCount=").append(tracks.length);
            int selectedTrack = player.getSelectedSubtitleTrack();
            out.append(";selectedSubtitleTrack=")
                    .append(selectedTrack == MiniPlayerPlugin.DISABLE_TRACK ? -1 : selectedTrack);
            out.append(";selectedSubtitleTrackRaw=").append(selectedTrack);
            StringBuilder compactTracks = new StringBuilder();
            for (SubtitleTrack track : tracks)
            {
                if (compactTracks.length() > 0)
                    compactTracks.append('|');
                compactTracks.append(track.getIndex()).append(':')
                        .append(track.getSubtitleCodec()).append(':')
                        .append(track.getLanguage()).append(':')
                        .append(track.getLabel()).append(':')
                        .append(track.getAccessibilityChannel()).append(':')
                        .append(track.isSupported());
            }
            out.append(";subtitleTracks=").append(safe(compactTracks.toString()));
            MiniPlayerPlugin telemetryPlayer = player;
            if (player instanceof GSYMediaPlayerImpl)
            {
                MiniPlayerPlugin delegate = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
                if (delegate != null)
                    telemetryPlayer = delegate;
            }
            if (telemetryPlayer instanceof Media3MediaPlayerImpl)
            {
                Media3MediaPlayerImpl media3 = (Media3MediaPlayerImpl) telemetryPlayer;
                out.append(";subtitleCueUpdateCount=").append(media3.getSubtitleCueUpdateCountForDebug());
                out.append(";subtitleNonEmptyCueCount=").append(media3.getSubtitleNonEmptyCueCountForDebug());
                out.append(";lastSubtitleCueText=").append(safe(media3.getLastSubtitleCueTextForDebug()));
                out.append(";currentSubtitleCueText=").append(safe(media3.getCurrentSubtitleCueTextForDebug()));
                out.append(";subtitleOverlayAttached=").append(media3.isSubtitleOverlayAttachedForDebug());
                out.append(";legacyCaptionCallbackActive=").append(media3.isLegacyCaptionCallbackActiveForDebug());
                out.append(";legacyCaptionCallbackCount=").append(media3.getLegacyCaptionCallbackCountForDebug());
                out.append(";legacyCaptionCallbackBytes=").append(media3.getLegacyCaptionCallbackBytesForDebug());
            }
            else if (telemetryPlayer instanceof Exo2MediaPlayerImpl)
            {
                Exo2MediaPlayerImpl exo2 = (Exo2MediaPlayerImpl) telemetryPlayer;
                out.append(";subtitleCueUpdateCount=").append(exo2.getSubtitleCueUpdateCountForDebug());
                out.append(";subtitleNonEmptyCueCount=").append(exo2.getSubtitleNonEmptyCueCountForDebug());
                out.append(";lastSubtitleCueText=").append(safe(exo2.getLastSubtitleCueTextForDebug()));
                out.append(";currentSubtitleCueText=").append(safe(exo2.getCurrentSubtitleCueTextForDebug()));
                out.append(";subtitleOverlayAttached=").append(exo2.isSubtitleOverlayAttachedForDebug());
                out.append(";legacyCaptionCallbackActive=").append(exo2.isLegacyCaptionCallbackActiveForDebug());
                out.append(";legacyCaptionCallbackCount=").append(exo2.getLegacyCaptionCallbackCountForDebug());
                out.append(";legacyCaptionCallbackBytes=").append(exo2.getLegacyCaptionCallbackBytesForDebug());
            }
        }
        catch (Throwable t)
        {
            out.append(";subtitleStateError=").append(safe(t.getClass().getSimpleName()));
        }
    }

    static String configuredValues(PrefStore prefs)
    {
        String audioChannels = prefs.getFixedEncodingAudioChannels();
        if (audioChannels == null || audioChannels.isEmpty())
            audioChannels = "source";
        return "player=" + safe(prefs.getString(PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE))
                + ";streaming=" + safe(prefs.getStreamingMode())
                + ";decoding=" + safe(prefs.getString(PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE))
                + ";gsyEngine=" + safe(prefs.getString(PrefStore.Keys.gsy_player_engine, GSYPlayerEngine.DEFAULT_PREFERENCE))
                + ";gsySystemProbe="
                + prefs.getBoolean(PrefStore.Keys.gsy_system_probe_enabled, false)
                + ";preferredAudioLanguage=" + safe(prefs.getString(PrefStore.Keys.preferred_audio_language, ""))
                + ";preferredSubtitleLanguage=" + safe(prefs.getString(PrefStore.Keys.preferred_subtitle_language, ""))
                + ";preferredCaptionStandard=" + safe(prefs.getString(PrefStore.Keys.preferred_caption_standard, "auto"))
                + ";preferredCaptionService=" + safe(prefs.getString(PrefStore.Keys.preferred_caption_service, "1"))
                + ";legacyServerCaptionMode="
                + safe(prefs.getString(PrefStore.Keys.legacy_server_caption_mode, "stv"))
                + ";fixedEncodingPreference=" + safe(prefs.getFixedEncodingPreference())
                + ";fixedEncodingFormat=" + safe(prefs.getFixedEncodingContainerFormat())
                + ";fixedVideoBitrateKbps=" + prefs.getFixedEncodingVideoBitrateKBPS()
                + ";fixedVideoFps=" + safe(prefs.getFixedEncodingFPS())
                + ";fixedKeyFrameInterval=" + prefs.getFixedEncodingKeyFrameInterval()
                + ";fixedUseBFrames=" + prefs.getFixedEncodingUseBFrames()
                + ";fixedVideoResolution=" + safe(prefs.getFixedEncodingVideoResolution())
                + ";fixedAudioCodec=" + safe(prefs.getFixedEncodingAudioCodec())
                + ";fixedAudioBitrateKbps=" + prefs.getFixedEncodingAudioBitrateKBPS()
                + ";fixedAudioChannels=" + safe(audioChannels)
                + ";fixedRemuxingPreference=" + safe(prefs.getFixedRemuxingPreference())
                + ";fixedRemuxingFormat=" + safe(prefs.getFixedRemuxingFormat())
                + ";keepSessionInBackground="
                + prefs.getBoolean(PrefStore.Keys.keep_session_in_background, false)
                + ";resumeBackgroundPlayback="
                + prefs.getBoolean(PrefStore.Keys.resume_background_playback, true)
                + ";backgroundSessionTimeoutSeconds="
                + prefs.getInt(PrefStore.Keys.background_session_timeout_seconds, 300)
                + ";discPlaybackPolicy="
                + safe(prefs.getString(PrefStore.Keys.disc_playback_policy, "auto"))
                + ";discSkipMenus="
                + prefs.getBoolean(PrefStore.Keys.disc_skip_menus, false)
                + ";discSkipPreviews="
                + prefs.getBoolean(PrefStore.Keys.disc_skip_previews, false)
                + ";discCompatibilityFallback="
                + prefs.getBoolean(PrefStore.Keys.disc_compatibility_fallback, true)
                + ";discMpeg2TimestampRepair="
                + safe(prefs.getString(PrefStore.Keys.disc_mpeg2_timestamp_repair, "auto"));
    }

    private static MiniClient requireClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        return app.getClient();
    }
}
