package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.uibridge.EventRouter;

/** Long-running debug-only seek/Comskip checks with the existing MCP wire contract. */
final class DebugAsyncPlayerChecks
{
    private static final int RESULT_OK = 1;
    private static final int RESULT_ERROR = -1;

    private DebugAsyncPlayerChecks()
    {
    }

    static void run(BroadcastReceiver receiver, Context context, Intent intent, int maxRecoveryWatchdogMs)
    {
        final BroadcastReceiver.PendingResult pending = receiver.goAsync();
        final Context appContext = context.getApplicationContext();
        final String operation = clean(intent.getStringExtra("op"));
        final String requestedCommandsText = clean(intent.getStringExtra("commands"));
        final String requestedDirection = clean(intent.getStringExtra("direction"));
        final String requestedDeltasText = clean(intent.getStringExtra("deltas_ms"));
        final int delayMs = parseBoundedInt(intent.getStringExtra("delay_ms"), 350, 0, 10000);
        final int settleMs = parseBoundedInt(intent.getStringExtra("settle_ms"), 300, 0, 30000);
        final int recoveryTimeoutMs = parseBoundedInt(intent.getStringExtra("recovery_timeout_ms"), 8000, 250, maxRecoveryWatchdogMs);
        final int verifyPlaybackMs = parseBoundedInt(intent.getStringExtra("verify_playback_ms"), 2500, 250, 10000);
        final int healthPollMs = parseBoundedInt(intent.getStringExtra("health_poll_ms"), 250, 100, 2000);

        DebugAsyncExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    MiniClient client = requireConnectedClient(appContext);
                    boolean comskipCheck = "comskip_check".equals(operation);
                    boolean relativeSeekCheck = "relative_seek_check".equals(operation);
                    String commandsText = requestedCommandsText;
                    String deltasText = requestedDeltasText;
                    String arrowDirection = "";
                    String resolvedArrowCommand = "";
                    String inputPath = "direct_sagetv_command";
                    if (comskipCheck)
                    {
                        arrowDirection = normalizeArrowDirection(requestedDirection);
                        resolvedArrowCommand = arrowDirection;
                        commandsText = resolvedArrowCommand;
                        inputPath = "android_debug_direct_sage_event";
                    }
                    else if (relativeSeekCheck)
                    {
                        if (deltasText.isEmpty())
                            throw new IllegalArgumentException("deltas_ms is required");
                        commandsText = "";
                        inputPath = "android_debug_direct_player_seek";
                    }

                    MediaCmd mediaCmd = requireMediaCmd(client);
                    long beforeServerSeekSequence = mediaCmd.getLastServerSeekSequence();
                    MiniPlayerPlugin beforePlayer = requirePlayer(mediaCmd);
                    long beforeTimelineMs = currentSageTimelineMs(mediaCmd, beforePlayer);
                    int beforeState = beforePlayer.getState();
                    PlaybackHealthProbe.Snapshot healthBefore = PlaybackHealthProbe.capture(beforePlayer);
                    long startedMs = SystemClock.elapsedRealtime();
                    long trapSequenceBefore = PlaybackEventTraps.currentSequence();
                    int sentCount = 0;

                    long beforePlayerPositionMs = currentPlayerPositionMs(mediaCmd, beforePlayer);
                    long requestedNetMs = 0;
                    long requestedTargetPlayerMs = beforePlayerPositionMs;
                    String actionTokens = relativeSeekCheck ? deltasText : commandsText;
                    for (String token : actionTokens.split(","))
                    {
                        String value = clean(token);
                        if (value.isEmpty())
                            continue;
                        if (relativeSeekCheck)
                        {
                            long deltaMs = Long.parseLong(value);
                            requestedNetMs += deltaMs;
                            requestedTargetPlayerMs = Math.max(0, requestedTargetPlayerMs + deltaMs);
                            MediaCmd currentMediaCmd = requireMediaCmd(client);
                            MiniPlayerPlugin currentPlayer = requirePlayer(currentMediaCmd);
                            currentPlayer.seek(requestedTargetPlayerMs);
                        }
                        else
                        {
                            SageCommand command = SageCommand.parseByKey(value);
                            if (command == SageCommand.UNKNOWN || command == SageCommand.NONE)
                                throw new IllegalArgumentException("invalid Sage command: " + value);
                            EventRouter.postCommand(client, command);
                        }
                        sentCount++;
                        if (delayMs > 0)
                            SystemClock.sleep(delayMs);
                    }

                    if (sentCount == 0)
                        throw new IllegalArgumentException(relativeSeekCheck ? "deltas_ms is required" : "commands is required");

                    long commandsFinishedMs = SystemClock.elapsedRealtime();
                    MediaCmd postCommandMediaCmd = requireMediaCmd(client);
                    MiniPlayerPlugin postCommandPlayer = requirePlayer(postCommandMediaCmd);
                    PlaybackHealthProbe.Snapshot healthPostCommand = PlaybackHealthProbe.capture(postCommandPlayer);

                    if (settleMs > 0)
                        SystemClock.sleep(settleMs);

                    boolean healthCheckPerformed = beforeState == MiniPlayerPlugin.PLAY_STATE
                            && healthBefore.supported && healthPostCommand.supported;
                    boolean videoExpected = healthBefore.expectsVideo() || healthPostCommand.expectsVideo();
                    boolean audioExpected = healthBefore.expectsAudio() || healthPostCommand.expectsAudio();
                    // A Pull seek can temporarily detach the legacy Exo AudioTrack while the
                    // datasource is reprepared.  In that interval the immediate post-command
                    // snapshot has neither an AudioTrack nor decoder counters, so using it as
                    // the recovery baseline makes audio progress impossible to prove.  Fall
                    // back to the valid pre-command track; audioHeadAdvanced() already handles
                    // same-session flush/reset and replacement-session semantics.
                    PlaybackHealthProbe.Snapshot audioRecoveryBaseline =
                            healthPostCommand.audioTrackPresent || healthPostCommand.audioRendered >= 0
                                    ? healthPostCommand : healthBefore;
                    boolean bufferingSeen = PlaybackHealthProbe.isBuffering(healthPostCommand);
                    boolean loadingSeen = healthPostCommand.isLoading;
                    int healthPollCount = 0;
                    int bufferingPollCount = bufferingSeen ? 1 : 0;
                    boolean playerErrorSeen = !healthPostCommand.playerError.isEmpty();
                    String firstPlayerError = healthPostCommand.playerError;
                    long recoveryMs = -1;
                    long videoRecoveryTimelineMs = -1;
                    long audioRecoveryTimelineMs = -1;
                    long outputRecoveryTimelineMs = -1;
                    boolean videoRecovered = !videoExpected;
                    boolean audioRecovered = !audioExpected;
                    boolean stateRecovered = false;
                    boolean surfaceRecovered = !videoExpected;
                    long firstSourceReadAfterRequestMonotonicMs =
                            timestampAtOrAfter(healthPostCommand.dataSourceLastPhysicalReadMonotonicMs, startedMs);
                    long firstDecoderInputAfterRequestMonotonicMs =
                            decoderInputAdvanced(healthBefore, healthPostCommand)
                                    ? healthPostCommand.capturedMonotonicMs : -1;
                    PlaybackHealthProbe.Snapshot recovered = null;
                    PlaybackHealthProbe.Snapshot latestHealth = healthPostCommand;

                    if (healthCheckPerformed)
                    {
                        long recoveryDeadline = commandsFinishedMs + recoveryTimeoutMs;
                        while (SystemClock.elapsedRealtime() <= recoveryDeadline)
                        {
                            MediaCmd currentMediaCmd = requireMediaCmd(client);
                            MiniPlayerPlugin currentPlayer = requirePlayer(currentMediaCmd);
                            latestHealth = PlaybackHealthProbe.capture(currentPlayer);
                            healthPollCount++;
                            if (firstSourceReadAfterRequestMonotonicMs < 0)
                                firstSourceReadAfterRequestMonotonicMs = timestampAtOrAfter(
                                        latestHealth.dataSourceLastPhysicalReadMonotonicMs, startedMs);
                            if (firstDecoderInputAfterRequestMonotonicMs < 0
                                    && decoderInputAdvanced(healthPostCommand, latestHealth))
                                firstDecoderInputAfterRequestMonotonicMs = latestHealth.capturedMonotonicMs;
                            boolean bufferingNow = PlaybackHealthProbe.isBuffering(latestHealth);
                            bufferingSeen = bufferingSeen || bufferingNow;
                            loadingSeen = loadingSeen || latestHealth.isLoading;
                            if (bufferingNow)
                                bufferingPollCount++;
                            if (!latestHealth.playerError.isEmpty())
                            {
                                playerErrorSeen = true;
                                if (firstPlayerError.isEmpty())
                                    firstPlayerError = latestHealth.playerError;
                            }

                            videoRecovered = !videoExpected
                                    || PlaybackHealthProbe.videoOutputAdvanced(healthPostCommand, latestHealth);
                            audioRecovered = !audioExpected
                                    || PlaybackHealthProbe.audioOutputAdvanced(audioRecoveryBaseline, latestHealth);
                            stateRecovered = PlaybackHealthProbe.readyAndPlaying(latestHealth);
                            surfaceRecovered = PlaybackHealthProbe.surfaceHealthy(latestHealth);
                            long currentTimelineMs = currentSageTimelineMs(currentMediaCmd, currentPlayer);
                            if (videoRecovered && videoRecoveryTimelineMs < 0)
                                videoRecoveryTimelineMs = currentTimelineMs;
                            if (audioRecovered && audioRecoveryTimelineMs < 0)
                                audioRecoveryTimelineMs = currentTimelineMs;

                            if (videoRecovered && audioRecovered && stateRecovered && surfaceRecovered && !playerErrorSeen)
                            {
                                recovered = latestHealth;
                                outputRecoveryTimelineMs = currentTimelineMs;
                                recoveryMs = SystemClock.elapsedRealtime() - commandsFinishedMs;
                                break;
                            }
                            SystemClock.sleep(healthPollMs);
                        }
                    }

                    boolean stillPlaying = false;
                    boolean videoStillAdvancing = !videoExpected;
                    boolean audioStillAdvancing = !audioExpected;
                    boolean surfaceStillHealthy = !videoExpected;
                    PlaybackHealthProbe.Snapshot healthFinal;

                    if (healthCheckPerformed && recovered != null)
                    {
                        SystemClock.sleep(verifyPlaybackMs);
                        MediaCmd finalHealthMediaCmd = requireMediaCmd(client);
                        MiniPlayerPlugin finalHealthPlayer = requirePlayer(finalHealthMediaCmd);
                        healthFinal = PlaybackHealthProbe.capture(finalHealthPlayer);
                        boolean finalBuffering = PlaybackHealthProbe.isBuffering(healthFinal);
                        bufferingSeen = bufferingSeen || finalBuffering;
                        loadingSeen = loadingSeen || healthFinal.isLoading;
                        if (finalBuffering)
                            bufferingPollCount++;
                        if (!healthFinal.playerError.isEmpty())
                        {
                            playerErrorSeen = true;
                            if (firstPlayerError.isEmpty())
                                firstPlayerError = healthFinal.playerError;
                        }
                        stillPlaying = PlaybackHealthProbe.readyAndPlaying(healthFinal);
                        videoStillAdvancing = !videoExpected
                                || PlaybackHealthProbe.videoOutputAdvanced(recovered, healthFinal);
                        audioStillAdvancing = !audioExpected
                                || PlaybackHealthProbe.audioOutputAdvanced(recovered, healthFinal);
                        surfaceStillHealthy = PlaybackHealthProbe.surfaceHealthy(healthFinal);
                    }
                    else
                    {
                        healthFinal = latestHealth;
                        if (!healthCheckPerformed)
                        {
                            stillPlaying = beforeState != MiniPlayerPlugin.PLAY_STATE
                                    || healthFinal.miniState == MiniPlayerPlugin.PLAY_STATE;
                        }
                    }

                    boolean outputHealthy = !healthCheckPerformed
                            || (recovered != null
                                && stillPlaying
                                && videoStillAdvancing
                                && audioStillAdvancing
                                && surfaceStillHealthy
                                && !playerErrorSeen);

                    StringBuilder healthFailure = new StringBuilder();
                    if (healthCheckPerformed && !outputHealthy)
                    {
                        if (recovered == null) appendFailure(healthFailure, "recovery_timeout");
                        if (videoExpected && !videoRecovered) appendFailure(healthFailure, "video_not_rendering");
                        if (audioExpected && !audioRecovered) appendFailure(healthFailure, "audio_not_advancing");
                        if (!stateRecovered) appendFailure(healthFailure, "player_not_ready_playing");
                        if (videoExpected && !surfaceRecovered) appendFailure(healthFailure, "surface_not_valid");
                        if (recovered != null && !stillPlaying) appendFailure(healthFailure, "playback_did_not_stay_active");
                        if (recovered != null && videoExpected && !videoStillAdvancing) appendFailure(healthFailure, "video_stalled_after_recovery");
                        if (recovered != null && audioExpected && !audioStillAdvancing) appendFailure(healthFailure, "audio_stalled_after_recovery");
                        if (recovered != null && videoExpected && !surfaceStillHealthy) appendFailure(healthFailure, "surface_lost_after_recovery");
                        if (playerErrorSeen) appendFailure(healthFailure, "player_error");
                    }

                    MediaCmd afterMediaCmd = requireMediaCmd(client);
                    long afterServerSeekSequence = afterMediaCmd.getLastServerSeekSequence();
                    MiniPlayerPlugin afterPlayer = requirePlayer(afterMediaCmd);
                    long afterTimelineMs = currentSageTimelineMs(afterMediaCmd, afterPlayer);
                    int afterState = afterPlayer.getState();
                    long elapsedMs = SystemClock.elapsedRealtime() - startedMs;
                    long timelineDeltaMs = afterTimelineMs - beforeTimelineMs;
                    boolean continuouslyPlaying = beforeState == MiniPlayerPlugin.PLAY_STATE
                            && afterState == MiniPlayerPlugin.PLAY_STATE;
                    long playbackAdjustedDeltaMs = continuouslyPlaying
                            ? timelineDeltaMs - elapsedMs
                            : timelineDeltaMs;

                    long videoRecoveryFrames = countDelta(healthPostCommand.videoRendered,
                            recovered == null ? healthFinal.videoRendered : recovered.videoRendered);
                    long videoVerifyFrames = recovered == null ? -1
                            : countDelta(recovered.videoRendered, healthFinal.videoRendered);
                    long audioRecoveryFrames = countDelta(audioRecoveryBaseline.audioRendered,
                            recovered == null ? healthFinal.audioRendered : recovered.audioRendered);
                    long audioVerifyFrames = recovered == null ? -1
                            : countDelta(recovered.audioRendered, healthFinal.audioRendered);
                    long audioRecoveryHeadFrames = audioHeadDelta(audioRecoveryBaseline,
                            recovered == null ? healthFinal : recovered);
                    long audioVerifyHeadFrames = recovered == null ? -1 : audioHeadDelta(recovered, healthFinal);
                    boolean audioHeadResetDuringRecovery = PlaybackHealthProbe.audioHeadResetDetected(
                            audioRecoveryBaseline, recovered == null ? healthFinal : recovered);
                    boolean audioHeadResetDuringVerify = recovered != null
                            && PlaybackHealthProbe.audioHeadResetDetected(recovered, healthFinal);

                    long landingTimelineMs = outputRecoveryTimelineMs >= 0 ? outputRecoveryTimelineMs : afterTimelineMs;
                    long landingDeltaMs = landingTimelineMs - beforeTimelineMs;
                    long serverSeekCommandMonotonicMs = PlaybackEventTraps.firstEventMonotonicAfter(
                            trapSequenceBefore, "server_seek_command");
                    long seekRequestMonotonicMs = serverSeekCommandMonotonicMs >= 0
                            ? serverSeekCommandMonotonicMs : startedMs;
                    long firstRenderedFrameAfterRequestMonotonicMs = PlaybackEventTraps.firstEventMonotonicAfter(
                            trapSequenceBefore, "first_video_frame");

                    String data = "ok=true;op=" + safe(operation)
                            + ";commands=" + safe(commandsText)
                            + ";deltasMs=" + safe(deltasText)
                            + ";comskipCheck=" + comskipCheck
                            + ";relativeSeekCheck=" + relativeSeekCheck
                            + ";inputPath=" + safe(inputPath)
                            + ";requestedNetMs=" + requestedNetMs
                            + ";beforePlayerPositionMs=" + beforePlayerPositionMs
                            + ";requestedTargetPlayerMs=" + requestedTargetPlayerMs
                            + ";arrowDirection=" + safe(arrowDirection)
                            + ";resolvedArrowCommand=" + safe(resolvedArrowCommand)
                            + ";comskipMarkerDataAvailable=false"
                            + ";comskipMarkerSource=server_stv_marker_metadata_not_exposed_to_miniclient"
                            + ";sentCount=" + sentCount
                            + ";delayMs=" + delayMs
                            + ";settleMs=" + settleMs
                            + ";recoveryTimeoutMs=" + recoveryTimeoutMs
                            + ";verifyPlaybackMs=" + verifyPlaybackMs
                            + ";elapsedMs=" + elapsedMs
                            + ";seekRequestMonotonicMs=" + seekRequestMonotonicMs
                            + ";commandsFinishedMonotonicMs=" + commandsFinishedMs
                            + ";firstSourceReadAfterRequestMonotonicMs=" + firstSourceReadAfterRequestMonotonicMs
                            + ";firstDecoderInputAfterRequestMonotonicMs=" + firstDecoderInputAfterRequestMonotonicMs
                            + ";firstDecoderInputObservationPrecisionMs=" + healthPollMs
                            + ";firstRenderedFrameAfterRequestMonotonicMs=" + firstRenderedFrameAfterRequestMonotonicMs
                            + ";sourceReadAfterRequestMs=" + elapsedFrom(seekRequestMonotonicMs, firstSourceReadAfterRequestMonotonicMs)
                            + ";decoderInputAfterRequestMs=" + elapsedFrom(seekRequestMonotonicMs, firstDecoderInputAfterRequestMonotonicMs)
                            + ";renderedFrameAfterRequestMs=" + elapsedFrom(seekRequestMonotonicMs, firstRenderedFrameAfterRequestMonotonicMs)
                            + ";timelineSource=MEDIACMD_GETMEDIATIME"
                            + ";timelineBeforeMs=" + beforeTimelineMs
                            + ";timelineAfterMs=" + afterTimelineMs
                            + ";timelineDeltaMs=" + timelineDeltaMs
                            + ";serverSeekSequenceBefore=" + beforeServerSeekSequence
                            + ";serverSeekSequenceAfter=" + afterServerSeekSequence
                            + ";serverSeekObserved=" + (afterServerSeekSequence > beforeServerSeekSequence)
                            + ";serverRequestedSeekMs=" + afterMediaCmd.getLastServerRequestedSeekMs()
                            + ";videoRecoveryTimelineMs=" + videoRecoveryTimelineMs
                            + ";audioRecoveryTimelineMs=" + audioRecoveryTimelineMs
                            + ";outputRecoveryTimelineMs=" + outputRecoveryTimelineMs
                            + ";landingTimelineMs=" + landingTimelineMs
                            + ";landingDeltaMs=" + landingDeltaMs
                            + ";playbackAdjustedDeltaMs=" + playbackAdjustedDeltaMs
                            + ";beforeState=" + beforeState
                            + ";afterState=" + afterState
                            + ";healthCheckPerformed=" + healthCheckPerformed
                            + ";outputHealthy=" + outputHealthy
                            + ";recoveryMs=" + recoveryMs
                            + ";videoExpected=" + videoExpected
                            + ";audioExpected=" + audioExpected
                            + ";videoRecovered=" + videoRecovered
                            + ";audioRecovered=" + audioRecovered
                            + ";stateRecovered=" + stateRecovered
                            + ";surfaceRecovered=" + surfaceRecovered
                            + ";stillPlaying=" + stillPlaying
                            + ";videoStillAdvancing=" + videoStillAdvancing
                            + ";audioStillAdvancing=" + audioStillAdvancing
                            + ";surfaceStillHealthy=" + surfaceStillHealthy
                            + ";bufferingSeen=" + bufferingSeen
                            + ";loadingSeen=" + loadingSeen
                            + ";healthPollCount=" + healthPollCount
                            + ";bufferingPollCount=" + bufferingPollCount
                            + ";healthFailureReason=" + safe(healthFailure.toString())
                            + ";playerErrorSeen=" + playerErrorSeen
                            + ";firstPlayerError=" + safe(firstPlayerError)
                            + ";videoRecoveryFrames=" + videoRecoveryFrames
                            + ";videoVerifyFrames=" + videoVerifyFrames
                            + ";audioRecoveryBuffers=" + audioRecoveryFrames
                            + ";audioVerifyBuffers=" + audioVerifyFrames
                            + ";audioRecoveryHeadFrames=" + audioRecoveryHeadFrames
                            + ";audioVerifyHeadFrames=" + audioVerifyHeadFrames
                            + ";audioHeadResetDuringRecovery=" + audioHeadResetDuringRecovery
                            + ";audioHeadResetDuringVerify=" + audioHeadResetDuringVerify
                            + ";decoderChanged=" + (!healthBefore.videoDecoderName.equals(healthFinal.videoDecoderName))
                            + ";audioDecoderChanged=" + (!healthBefore.audioDecoderName.equals(healthFinal.audioDecoderName))
                            + ";dataSourceChanged=" + (!healthBefore.dataSourceClass.equals(healthFinal.dataSourceClass))
                            + ";videoDecoderInitDelta=" + countDelta(healthBefore.videoDecoderInitCount, healthFinal.videoDecoderInitCount)
                            + ";videoDecoderReleaseDelta=" + countDelta(healthBefore.videoDecoderReleaseCount, healthFinal.videoDecoderReleaseCount)
                            + ";audioDecoderInitDelta=" + countDelta(healthBefore.audioDecoderInitCount, healthFinal.audioDecoderInitCount)
                            + ";audioDecoderReleaseDelta=" + countDelta(healthBefore.audioDecoderReleaseCount, healthFinal.audioDecoderReleaseCount)
                            + ";fileReadPosDelta=" + countDelta(healthBefore.lastFileReadPos, healthFinal.lastFileReadPos)
                            + healthBefore.compactWire("before_")
                            + healthPostCommand.compactWire("post_")
                            + healthFinal.compactWire("final_");
                    pending.setResultCode(RESULT_OK);
                    pending.setResultData(data);
                }
                catch (Throwable t)
                {
                    pending.setResultCode(RESULT_ERROR);
                    pending.setResultData("ok=false;op=" + safe(operation) + ";exception="
                            + safe(t.getClass().getSimpleName()) + ";message=" + safe(t.getMessage()));
                }
                finally
                {
                    pending.finish();
                }
            }
        });
    }

    private static long timestampAtOrAfter(long candidate, long lowerBound)
    {
        return candidate >= lowerBound ? candidate : -1;
    }

    private static boolean decoderInputAdvanced(PlaybackHealthProbe.Snapshot before,
                                                PlaybackHealthProbe.Snapshot after)
    {
        if (after == null || after.videoQueuedInput < 0)
            return false;
        if (before == null || before.videoQueuedInput < 0)
            return after.videoQueuedInput > 0;
        return PlaybackHealthProbe.counterAdvanced(before.videoQueuedInput, after.videoQueuedInput);
    }

    private static long elapsedFrom(long start, long event)
    {
        return start >= 0 && event >= start ? event - start : -1;
    }


    private static String normalizeArrowDirection(String direction)
    {
        String value = clean(direction);
        if (value.isEmpty() || "right".equals(value) || "forward".equals(value) || "next".equals(value))
            return "right";
        if ("left".equals(value) || "backward".equals(value) || "previous".equals(value) || "prev".equals(value))
            return "left";
        throw new IllegalArgumentException("direction must be right/forward/next or left/backward/previous");
    }

    private static void appendFailure(StringBuilder out, String reason)
    {
        if (reason == null || reason.isEmpty())
            return;
        if (out.length() > 0)
            out.append(',');
        out.append(reason);
    }

    private static long countDelta(long before, long after)
    {
        if (before < 0 || after < 0)
            return -1;
        if (after >= before)
            return after - before;
        // Renderer reinitialization can replace/reset DecoderCounters.
        return after;
    }

    private static long audioHeadDelta(PlaybackHealthProbe.Snapshot before, PlaybackHealthProbe.Snapshot after)
    {
        if (before == null || after == null || !before.audioTrackPresent || !after.audioTrackPresent)
            return -1;
        if (before.audioSessionId != after.audioSessionId)
            return after.audioPlaybackHeadFrames;
        if (PlaybackHealthProbe.audioHeadResetDetected(before, after))
            return after.audioPlaybackHeadFrames;
        return (after.audioPlaybackHeadFrames - before.audioPlaybackHeadFrames) & 0xffffffffL;
    }

    private static MediaCmd requireMediaCmd(MiniClient client)
    {
        if (client.getCurrentConnection() == null || client.getCurrentConnection().getMediaCmd() == null)
            throw new IllegalStateException("no active MediaCmd");
        return client.getCurrentConnection().getMediaCmd();
    }

    private static MiniPlayerPlugin requirePlayer(MediaCmd mediaCmd)
    {
        MiniPlayerPlugin player = mediaCmd.getPlaya();
        if (player == null)
            throw new IllegalStateException("no active player");
        return player;
    }

    private static long currentSageTimelineMs(MediaCmd mediaCmd, MiniPlayerPlugin player)
    {
        // This is the same value returned by MediaCmd.MEDIACMD_GETMEDIATIME to SageTV,
        // which is what drives SageTV's displayed playback timeline/time bar.
        return player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition());
    }

    private static long currentPlayerPositionMs(MediaCmd mediaCmd, MiniPlayerPlugin player)
    {
        // MiniPlayerPlugin.seek() uses the backend-local media position, not SageTV's
        // server-anchored display timeline. Prefer the backend probe position and only
        // derive it from the SageTV timeline when the backend cannot expose it directly.
        PlaybackHealthProbe.Snapshot health = PlaybackHealthProbe.capture(player);
        if (health.playerPositionMs >= 0)
            return health.playerPositionMs;
        long timelineMs = currentSageTimelineMs(mediaCmd, player);
        long anchorMs = mediaCmd.getLastServerStartPosition();
        if (timelineMs >= 0 && anchorMs >= 0)
            return Math.max(0, timelineMs - anchorMs);
        return Math.max(0, timelineMs);
    }

    private static MiniClient requireClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        return app.getClient();
    }

    private static MiniClient requireConnectedClient(Context context)
    {
        MiniClient client = requireClient(context);
        if (!client.isConnected())
            throw new IllegalStateException("MiniClient is not connected");
        return client;
    }

}
