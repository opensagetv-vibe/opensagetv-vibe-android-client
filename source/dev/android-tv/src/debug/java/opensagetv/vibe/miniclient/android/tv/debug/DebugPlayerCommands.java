package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.safe;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.text;

import android.content.Context;
import android.content.Intent;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl;
import opensagetv.vibe.miniclient.uibridge.EventRouter;

/** Synchronous debug-only player controls with the existing wire contract. */
final class DebugPlayerCommands
{
    private DebugPlayerCommands()
    {
    }

    static String control(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MiniPlayerPlugin player = requirePlayer(requireMediaCmd(client));
        String action = clean(intent.getStringExtra("action"));
        int beforeState = player.getState();
        if ("play".equals(action))
            player.play();
        else if ("pause".equals(action))
            player.pause();
        else if ("stop".equals(action))
            player.stop();
        else
            throw new IllegalArgumentException("action must be play, pause, or stop");
        return "op=player_control;action=" + safe(action)
                + ";beforeState=" + beforeState
                + ";accepted=true;inputPath=android_debug_direct_player_api";
    }

    static String seekRelative(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MediaCmd mediaCmd = requireMediaCmd(client);
        MiniPlayerPlugin player = requirePlayer(mediaCmd);
        String deltaText = clean(intent.getStringExtra("delta_ms"));
        if (deltaText.isEmpty())
            throw new IllegalArgumentException("delta_ms is required");
        long deltaMs = Long.parseLong(deltaText);
        long beforePlayerMs = currentPlayerPositionMs(mediaCmd, player);
        long beforeTimelineMs = currentSageTimelineMs(mediaCmd, player);
        long targetPlayerMs = Math.max(0, beforePlayerMs + deltaMs);
        player.seek(targetPlayerMs);
        return "op=seek_relative;deltaMs=" + deltaMs
                + ";beforePlayerMs=" + beforePlayerMs
                + ";beforeTimelineMs=" + beforeTimelineMs
                + ";targetPlayerMs=" + targetPlayerMs
                + ";accepted=true;inputPath=android_debug_direct_player_seek";
    }

    static String frameStep(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MediaCmd mediaCmd = requireMediaCmd(client);
        MiniPlayerPlugin player = requirePlayer(mediaCmd);
        String amountText = clean(intent.getStringExtra("amount"));
        if (amountText.isEmpty())
            throw new IllegalArgumentException("amount is required");
        int amount = Integer.parseInt(amountText);
        long beforePlayerMs = currentPlayerPositionMs(mediaCmd, player);
        byte[] command = new byte[4];
        byte[] reply = new byte[4];
        MediaCmd.writeInt(amount, command, 0);
        int replyLength = mediaCmd.ExecuteMediaCommand(
                MediaCmd.MEDIACMD_FRAMESTEP, command.length, command, reply);
        boolean accepted = replyLength == 4 && MediaCmd.readInt(0, reply) != 0;
        return "op=frame_step;amount=" + amount
                + ";beforePlayerMs=" + beforePlayerMs
                + ";accepted=" + accepted
                + ";replyLength=" + replyLength
                + ";inputPath=android_debug_media_command_28";
    }

    static String playbackRate(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MediaCmd mediaCmd = requireMediaCmd(client);
        MiniPlayerPlugin player = requirePlayer(mediaCmd);
        String rateText = clean(intent.getStringExtra("rate"));
        if (rateText.isEmpty())
            throw new IllegalArgumentException("rate is required");
        float requested = Float.parseFloat(rateText);
        byte[] command = new byte[4];
        byte[] reply = new byte[4];
        MediaCmd.writeInt(Float.floatToIntBits(requested), command, 0);
        int replyLength = mediaCmd.ExecuteMediaCommand(
                MediaCmd.MEDIACMD_SETRATE, command.length, command, reply);
        float accepted = replyLength == 4
                ? Float.intBitsToFloat(MediaCmd.readInt(0, reply)) : 1.0f;
        return "op=playback_rate;requestedRate=" + requested
                + ";acceptedRate=" + accepted
                + ";accepted=" + (requested == accepted)
                + ";replyLength=" + replyLength
                + ";inputPath=android_debug_media_command_30";
    }

    static String comskip(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        String direction = normalizeArrowDirection(clean(intent.getStringExtra("direction")));
        SageCommand command = "right".equals(direction) ? SageCommand.RIGHT : SageCommand.LEFT;
        EventRouter.postCommand(client, command);
        return "op=comskip;direction=" + safe(direction)
                + ";sageCommand=" + safe(command.getKey())
                + ";accepted=true;inputPath=android_debug_direct_sage_event"
                + ";markerTargetSource=server_stv";
    }

    static String subtitle(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MiniPlayerPlugin player = requirePlayer(requireMediaCmd(client));
        String indexText = clean(intent.getStringExtra("index"));
        if (indexText.isEmpty())
            throw new IllegalArgumentException("index is required");
        int index = Integer.parseInt(indexText);
        int trackCount = player.getSubtitleTrackCount();
        if (index < -1 || index >= trackCount)
            throw new IllegalArgumentException("subtitle index must be -1 or a present track index");
        int playerIndex = index == -1 ? MiniPlayerPlugin.DISABLE_TRACK : index;
        int before = player.getSelectedSubtitleTrack();
        player.setSubtitleTrack(playerIndex);
        return "op=subtitle_control;index=" + index
                + ";beforeIndex=" + before
                + ";trackCount=" + trackCount
                + ";playerIndex=" + playerIndex
                + ";accepted=true;inputPath=android_debug_direct_player_api";
    }

    static String seekTime(Context context, Intent intent, String operation)
    {
        MiniClient client = requireConnectedClient(context);
        MediaCmd mediaCmd = requireMediaCmd(client);
        MiniPlayerPlugin player = requirePlayer(mediaCmd);
        String targetText = clean(intent.getStringExtra("target_ms"));
        if (targetText.isEmpty())
            throw new IllegalArgumentException("target_ms is required");
        long targetMs = Long.parseLong(targetText);
        if (targetMs < 0)
            throw new IllegalArgumentException("target_ms must be >= 0");
        long beforePlayerMs = currentPlayerPositionMs(mediaCmd, player);
        long beforeTimelineMs = currentSageTimelineMs(mediaCmd, player);
        player.seek(targetMs);
        return "op=" + safe(operation)
                + ";targetMs=" + targetMs
                + ";beforePlayerMs=" + beforePlayerMs
                + ";beforeTimelineMs=" + beforeTimelineMs
                + ";accepted=true"
                + ";note=debug_local_player_seek_time";
    }

    static String fastSwitchFile(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MiniPlayerPlugin player = requirePlayer(requireMediaCmd(client));
        if (!(player instanceof Media3MediaPlayerImpl))
            throw new IllegalStateException("fast switch requires an active Media3 player");
        // Server file paths are case-sensitive on Linux; clean() is only for
        // case-insensitive command tokens and must never normalize a path.
        String serverPath = text(intent.getStringExtra("server_path"));
        if (serverPath.isEmpty())
            throw new IllegalArgumentException("server_path is required");
        player.setPushMode(false);
        player.load((byte) 1, (byte) 32, "", serverPath,
                client.getConnectedServerInfo().address, false, 0);
        return "op=fast_switch_file;accepted=true;serverPath=" + safe(serverPath)
                + ";inputPath=android_debug_direct_media3_load"
                + ";note=physical_player_replacement_diagnostic_only";
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
        return player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition());
    }

    private static long currentPlayerPositionMs(MediaCmd mediaCmd, MiniPlayerPlugin player)
    {
        PlaybackHealthProbe.Snapshot health = PlaybackHealthProbe.capture(player);
        if (health.playerPositionMs >= 0)
            return health.playerPositionMs;
        long timelineMs = currentSageTimelineMs(mediaCmd, player);
        long anchorMs = mediaCmd.getLastServerStartPosition();
        if (timelineMs >= 0 && anchorMs >= 0)
            return Math.max(0, timelineMs - anchorMs);
        return Math.max(0, timelineMs);
    }

    private static MiniClient requireConnectedClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        MiniClient client = app.getClient();
        if (!client.isConnected())
            throw new IllegalStateException("MiniClient is not connected");
        return client;
    }
}
