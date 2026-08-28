package sagex.miniclient.android.tv.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import sagex.miniclient.MediaCmd;
import sagex.miniclient.MenuHint;
import sagex.miniclient.ServerInfo;
import sagex.miniclient.MiniClient;
import sagex.miniclient.MiniPlayerPlugin;
import sagex.miniclient.SageCommand;
import sagex.miniclient.android.MiniclientApplication;
import sagex.miniclient.android.UIActivityLifeCycleHandler;
import sagex.miniclient.android.gdx.MiniClientGDXActivity;
import sagex.miniclient.android.opengl.MiniClientOpenGLActivity;
import sagex.miniclient.android.tv.MainActivity;
import sagex.miniclient.android.prefs.AndroidPrefStore;
import sagex.miniclient.android.video.DecodingMethod;
import sagex.miniclient.android.video.PlayerBackend;
import sagex.miniclient.android.video.PlayerRuntimeTuning;
import sagex.miniclient.android.video.gsy.GSYPlayerEngine;
import sagex.miniclient.prefs.PrefStore;
import sagex.miniclient.uibridge.Dimension;
import sagex.miniclient.uibridge.EventRouter;
import sagex.miniclient.uibridge.Keys;
import sagex.miniclient.util.ClientIDGenerator;

/**
 * Debug-build-only control surface used by the SageTV MCP/ADB regression tools.
 *
 * This receiver intentionally does not subscribe to player callbacks or emit continuous
 * telemetry.  It reads existing state only when ADB/MCP explicitly asks for a snapshot,
 * which avoids the playback regressions caused by the earlier callback telemetry experiment.
 */
public final class DevTestReceiver extends BroadcastReceiver
{
    public static final String ACTION = "org.opensagetv.miniclient.dev.DEBUG_CONTROL";

    private static final int RESULT_OK = 1;
    private static final int RESULT_ERROR = -1;
    private static final int MAX_RECOVERY_WATCHDOG_MS = 300000;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null || !ACTION.equals(intent.getAction()))
        {
            fail("invalid_action");
            return;
        }

        try
        {
            String op = clean(intent.getStringExtra("op"));
            if ("snapshot".equals(op))
            {
                ok(snapshot(context));
            }
            else if ("events".equals(op))
            {
                ok(PlaybackEventTraps.compactWire());
            }
            else if ("events_clear".equals(op))
            {
                ok(PlaybackEventTraps.clearWire());
            }
            else if ("config".equals(op))
            {
                ok(configure(context, intent));
            }
            else if ("tuning".equals(op))
            {
                ok(configureTuning(intent));
            }
            else if ("tuning_get".equals(op))
            {
                ok("op=tuning_get;" + PlayerRuntimeTuning.compactWire());
            }
            else if ("client_id_get".equals(op))
            {
                ok(clientIdStatus(context));
            }
            else if ("client_id_set".equals(op))
            {
                ok(configureClientId(context, intent));
            }
            else if ("connect".equals(op))
            {
                ok(connectServer(context, intent));
            }
            else if ("exit".equals(op))
            {
                ok(exitSession(context));
            }
            else if ("command".equals(op))
            {
                ok(sendCommand(context, intent));
            }
            else if ("input_text_native".equals(op))
            {
                ok(inputTextNative(context, intent));
            }
            else if ("ime_hide".equals(op))
            {
                ok(hideImeDirect());
            }
            else if ("ime_suppress".equals(op))
            {
                ok(setImeSuppression(intent));
            }
            else if ("player_control".equals(op))
            {
                ok(playerControl(context, intent));
            }
            else if ("seek_time".equals(op) || "seek_absolute".equals(op))
            {
                ok(seekTime(context, intent, op));
            }
            else if ("seek_relative".equals(op))
            {
                ok(seekRelative(context, intent));
            }
            else if ("comskip".equals(op))
            {
                ok(comskipDirect(context, intent));
            }
            else if ("skip_check".equals(op) || "comskip_check".equals(op) || "relative_seek_check".equals(op))
            {
                runSkipCheckAsync(context, intent);
            }
            else
            {
                fail("unsupported_op=" + safe(op));
            }
        }
        catch (Throwable t)
        {
            fail("exception=" + safe(t.getClass().getSimpleName()) + ";message=" + safe(t.getMessage()));
        }
    }

    private String clientIdStatus(Context context)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        String configured = clean(prefs.getString(PrefStore.Keys.client_id));
        String active = configured;
        if (client.getCurrentConnection() != null)
            active = clean(client.getCurrentConnection().getClientID());
        ClientIDGenerator gen = new ClientIDGenerator();
        return "op=client_id_get;configuredClientId=" + safe(configured)
                + ";configuredAscii=" + safe(clientIdAscii(gen, configured))
                + ";activeClientId=" + safe(active)
                + ";activeAscii=" + safe(clientIdAscii(gen, active));
    }

    private String configureClientId(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        ClientIDGenerator gen = new ClientIDGenerator();
        String requested = clean(intent.getStringExtra("value"));
        boolean generate = parseBoolean(intent.getStringExtra("generate"), false);
        String id;

        if (generate)
        {
            id = gen.generateId();
        }
        else
        {
            if (requested.isEmpty())
                throw new IllegalArgumentException("client ID value is required unless generate=true");
            if (requested.indexOf(':') >= 0)
            {
                if (!requested.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
                    throw new IllegalArgumentException("client ID must be six colon-separated hex bytes");
                id = requested.toLowerCase();
            }
            else
            {
                if (requested.length() > 6)
                    throw new IllegalArgumentException("text client ID must be 1 to 6 characters");
                id = gen.generateId(requested);
            }
        }

        prefs.setString(PrefStore.Keys.client_id, id);
        String active = client.getCurrentConnection() == null ? "" : clean(client.getCurrentConnection().getClientID());
        return "op=client_id_set;configuredClientId=" + safe(id)
                + ";configuredAscii=" + safe(clientIdAscii(gen, id))
                + ";activeClientId=" + safe(active)
                + ";restartRequired=" + (!active.isEmpty() && !active.equalsIgnoreCase(id));
    }

    private static String clientIdAscii(ClientIDGenerator gen, String id)
    {
        if (id == null || id.isEmpty())
            return "";
        try
        {
            return gen.id2string(id);
        }
        catch (Throwable ignored)
        {
            return "";
        }
    }

    private String configureTuning(Intent intent)
    {
        if (parseBoolean(intent.getStringExtra("reset"), false))
        {
            PlayerRuntimeTuning.reset();
        }

        Integer media3Ts = optionalInt(intent, "media3_ts_search_multiplier");
        Integer exo2Ts = optionalInt(intent, "exo2_ts_search_multiplier");
        Integer media3ReadKb = optionalInt(intent, "media3_pull_read_kb");
        Integer exo2ReadKb = optionalInt(intent, "exo2_pull_read_kb");
        Integer media3Min = optionalInt(intent, "media3_min_buffer_ms");
        Integer media3Max = optionalInt(intent, "media3_max_buffer_ms");
        Integer media3Play = optionalInt(intent, "media3_playback_buffer_ms");
        Integer media3Rebuffer = optionalInt(intent, "media3_rebuffer_ms");
        Integer exo2Min = optionalInt(intent, "exo2_min_buffer_ms");
        Integer exo2Max = optionalInt(intent, "exo2_max_buffer_ms");
        Integer exo2Play = optionalInt(intent, "exo2_playback_buffer_ms");
        Integer exo2Rebuffer = optionalInt(intent, "exo2_rebuffer_ms");
        Long directionalMin = optionalLong(intent, "directional_sync_min_delta_ms");
        Boolean media3Recovery = optionalBoolean(intent, "media3_seek_recovery_enabled");
        Boolean exo2Recovery = optionalBoolean(intent, "exo2_seek_recovery_enabled");
        Long media3RecoveryMs = optionalLong(intent, "media3_seek_recovery_delay_ms");
        Long exo2RecoveryMs = optionalLong(intent, "exo2_seek_recovery_delay_ms");
        String media3SeekPolicy = optionalString(intent, "media3_seek_policy");
        String exo2SeekPolicy = optionalString(intent, "exo2_seek_policy");
        String media3CodecMode = optionalString(intent, "media3_codec_mode");
        String exo2CodecMode = optionalString(intent, "exo2_codec_mode");

        PlayerRuntimeTuning.configure(
                media3Ts, exo2Ts,
                kbToBytes(media3ReadKb), kbToBytes(exo2ReadKb),
                media3Min, media3Max, media3Play, media3Rebuffer,
                exo2Min, exo2Max, exo2Play, exo2Rebuffer,
                directionalMin, media3Recovery, exo2Recovery,
                media3RecoveryMs, exo2RecoveryMs,
                media3SeekPolicy, exo2SeekPolicy, media3CodecMode, exo2CodecMode);

        return "op=tuning;" + PlayerRuntimeTuning.compactWire() + ";appliesNextPlayback=true";
    }

    private static Integer optionalInt(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static Long optionalLong(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Long.valueOf(value);
    }

    private static Boolean optionalBoolean(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Boolean.valueOf(parseBoolean(value, false));
    }

    private static String optionalString(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : value;
    }

    private static Integer kbToBytes(Integer valueKb)
    {
        return valueKb == null ? null : Integer.valueOf(Math.multiplyExact(valueKb, 1024));
    }

    private String configure(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();

        String player = clean(intent.getStringExtra("player"));
        String streaming = clean(intent.getStringExtra("streaming"));
        String decoding = clean(intent.getStringExtra("decoding"));
        String gsyEngine = clean(intent.getStringExtra("gsy_engine"));
        String fixedEncodingPreference = clean(intent.getStringExtra("fixed_encoding_preference"));
        String fixedEncodingFormat = clean(intent.getStringExtra("fixed_encoding_format"));
        String fixedVideoBitrateKbps = clean(intent.getStringExtra("fixed_video_bitrate_kbps"));
        String fixedVideoFps = clean(intent.getStringExtra("fixed_video_fps"));
        String fixedKeyFrameInterval = clean(intent.getStringExtra("fixed_key_frame_interval"));
        String fixedUseBFrames = clean(intent.getStringExtra("fixed_use_b_frames"));
        String fixedVideoResolution = clean(intent.getStringExtra("fixed_video_resolution"));
        String fixedAudioCodec = clean(intent.getStringExtra("fixed_audio_codec"));
        String fixedAudioBitrateKbps = clean(intent.getStringExtra("fixed_audio_bitrate_kbps"));
        String fixedAudioChannels = clean(intent.getStringExtra("fixed_audio_channels"));
        String fixedRemuxingPreference = clean(intent.getStringExtra("fixed_remuxing_preference"));
        String fixedRemuxingFormat = clean(intent.getStringExtra("fixed_remuxing_format"));

        if (!player.isEmpty())
        {
            PlayerBackend parsed = PlayerBackend.fromPreference(player);
            if (!parsed.preferenceValue().equalsIgnoreCase(player))
                throw new IllegalArgumentException("invalid player: " + player);
            prefs.setString(PrefStore.Keys.default_player, parsed.preferenceValue());
        }

        if (!streaming.isEmpty())
        {
            String streamingPreference = "push".equals(streaming) || "push/dynamic".equals(streaming)
                    ? "dynamic" : streaming;
            if (!("dynamic".equals(streamingPreference) || "pull".equals(streamingPreference) || "fixed".equals(streamingPreference)))
                throw new IllegalArgumentException("invalid streaming mode: " + streaming);
            prefs.setString(AndroidPrefStore.STREAMING_MODE, streamingPreference);
        }

        if (!decoding.isEmpty())
        {
            String decodingPreference = "fallback".equals(decoding) ? "hardware_preferred" : decoding;
            DecodingMethod parsed = DecodingMethod.fromPreference(decodingPreference);
            if (!parsed.preferenceValue().equalsIgnoreCase(decodingPreference))
                throw new IllegalArgumentException("invalid decoding method: " + decoding);
            prefs.setString(PrefStore.Keys.decoding_method, parsed.preferenceValue());
        }

        if (!gsyEngine.isEmpty())
        {
            GSYPlayerEngine parsed = GSYPlayerEngine.fromPreference(gsyEngine);
            if (!parsed.preferenceValue().equalsIgnoreCase(gsyEngine))
                throw new IllegalArgumentException("invalid GSY engine: " + gsyEngine);
            prefs.setString(PrefStore.Keys.gsy_player_engine, parsed.preferenceValue());
        }

        if (!fixedEncodingPreference.isEmpty())
        {
            String value = fixedEncodingPreference.toLowerCase();
            if (!("needed".equals(value) || "always".equals(value)))
                throw new IllegalArgumentException("invalid fixed encoding preference: " + fixedEncodingPreference);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_PREFERENCE, value);
        }
        if (!fixedEncodingFormat.isEmpty())
        {
            String value = fixedEncodingFormat.toLowerCase();
            if (!("matroska".equals(value) || "dvd".equals(value)))
                throw new IllegalArgumentException("invalid fixed encoding format: " + fixedEncodingFormat);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_FORMAT, value);
        }
        if (!fixedVideoBitrateKbps.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS, parseBoundedInt(fixedVideoBitrateKbps, 4000, 1, 100000));
        if (!fixedVideoFps.isEmpty())
        {
            String value = fixedVideoFps.toUpperCase();
            if (!("SOURCE".equals(value) || "24".equals(value) || "29.97".equals(value) || "59.94".equals(value)))
                throw new IllegalArgumentException("invalid fixed video fps: " + fixedVideoFps);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_FPS, value);
        }
        if (!fixedKeyFrameInterval.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_KEY_FRAME_INTERVAL, parseBoundedInt(fixedKeyFrameInterval, 10, 1, 600));
        if (!fixedUseBFrames.isEmpty())
            prefs.setBoolean(AndroidPrefStore.FIXED_ENCODING_USE_B_FRAMES, parseBoolean(fixedUseBFrames, true));
        if (!fixedVideoResolution.isEmpty())
        {
            String value = fixedVideoResolution.toUpperCase();
            if (!("SOURCE".equals(value) || "CIF".equals(value) || "D1".equals(value) || "720".equals(value) || "1080".equals(value)))
                throw new IllegalArgumentException("invalid fixed video resolution: " + fixedVideoResolution);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION, value);
        }
        if (!fixedAudioCodec.isEmpty())
        {
            String value = fixedAudioCodec.toLowerCase();
            if (!("ac3".equals(value) || "mp2".equals(value)))
                throw new IllegalArgumentException("invalid fixed audio codec: " + fixedAudioCodec);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC, value);
        }
        if (!fixedAudioBitrateKbps.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS, parseBoundedInt(fixedAudioBitrateKbps, 128, 1, 10000));
        if (!fixedAudioChannels.isEmpty())
        {
            String value = fixedAudioChannels.toLowerCase();
            if ("source".equals(value)) value = "";
            if (!("".equals(value) || "1".equals(value) || "2".equals(value) || "6".equals(value)))
                throw new IllegalArgumentException("invalid fixed audio channels: " + fixedAudioChannels);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS, value);
        }
        if (!fixedRemuxingPreference.isEmpty())
        {
            String value = fixedRemuxingPreference.toLowerCase();
            if (!("needed".equals(value) || "always".equals(value) || "off".equals(value)))
                throw new IllegalArgumentException("invalid fixed remuxing preference: " + fixedRemuxingPreference);
            prefs.setString(AndroidPrefStore.FIXED_REMUXING_PREFERENCE, value);
        }
        if (!fixedRemuxingFormat.isEmpty())
        {
            String value = fixedRemuxingFormat.toLowerCase();
            if (!("matroska".equals(value) || "dvd".equals(value) || "mpegts".equals(value)))
                throw new IllegalArgumentException("invalid fixed remuxing format: " + fixedRemuxingFormat);
            prefs.setString(AndroidPrefStore.FIXED_REMUXING_FORMAT, value);
        }

        return "op=config;" + configuredValues(prefs) + ";appliesNextPlayback=true";
    }

    private String connectServer(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        String serverName = text(intent.getStringExtra("server_name"));
        String address = text(intent.getStringExtra("address"));
        int port = parseBoundedInt(intent.getStringExtra("port"), 31099, 1, 65535);
        boolean save = parseBoolean(intent.getStringExtra("save"), true);

        ServerInfo server;
        String source;
        if (!address.isEmpty())
        {
            server = new ServerInfo();
            server.name = serverName.isEmpty() ? "MCP-" + address.replace(':', '_') : serverName;
            server.address = address;
            server.port = port;
            source = "direct_address";
        }
        else if (!serverName.isEmpty())
        {
            server = client.getServers().getServer(serverName);
            if (server == null)
                throw new IllegalArgumentException("saved server not found: " + serverName);
            source = "saved_name";
        }
        else
        {
            server = client.getServers().getLastConnectedServer();
            if (server == null)
                throw new IllegalStateException("no last connected server; provide server_name or address");
            source = "last_connected";
        }

        if (client.isConnected())
            client.closeConnection();

        server.lastConnectTime = System.currentTimeMillis();
        if (save || "saved_name".equals(source) || "last_connected".equals(source))
            server.save(client.properties());
        if (save || "saved_name".equals(source) || "last_connected".equals(source))
            client.getServers().setLastConnectedServer(server);

        Class<?> activityClass = MiniClientGDXActivity.class;
        if (client.properties().getBoolean(PrefStore.Keys.use_opengl_ui, true))
            activityClass = MiniClientOpenGLActivity.class;

        Intent start = new Intent(context, activityClass);
        start.putExtra(UIActivityLifeCycleHandler.ARG_SERVER_INFO, server);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(start);

        return "op=connect;source=" + safe(source)
                + ";serverName=" + safe(server.name)
                + ";serverAddress=" + safe(server.address)
                + ";serverPort=" + server.port
                + ";launchActivity=" + safe(activityClass.getName())
                + ";save=" + save;
    }

    private String exitSession(Context context)
    {
        MiniClient client = requireClient(context);
        boolean wasConnected = client.isConnected();
        if (wasConnected)
            client.closeConnection();
        UIActivityLifeCycleHandler.setKeyboardSuppressedForDebug(false);

        Intent main = new Intent(context, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(main);
        return "op=exit;wasConnected=" + wasConnected + ";destination=MainActivity";
    }

    private String sendCommand(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        String key = clean(intent.getStringExtra("command"));
        SageCommand command = SageCommand.parseByKey(key);
        if (command == SageCommand.UNKNOWN || command == SageCommand.NONE)
            throw new IllegalArgumentException("invalid Sage command: " + key);
        EventRouter.postCommand(client, command);
        return "op=command;command=" + safe(command.getKey()) + ";eventCode=" + command.getEventCode();
    }

    private String inputTextNative(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null)
            throw new IllegalStateException("no active connection");
        MenuHint hint = client.getCurrentConnection().getMenuHint();
        if (hint == null || !hint.hasTextInput)
            throw new IllegalStateException("SageTV UI is not accepting text input");

        String value = intent.getStringExtra("text");
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("text is required");

        int sent = 0;
        for (int i = 0; i < value.length(); i++)
        {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n')
                throw new IllegalArgumentException("text must be a single line");

            int keyCode;
            int modifiers = 0;
            if (ch >= 'a' && ch <= 'z')
            {
                // Match KeyMapProcessor.handleDefaultEvent(): SageTV expects the
                // java.awt VK code (uppercase ASCII) separately from keyChar.
                keyCode = Character.toUpperCase(ch);
            }
            else if (ch >= 'A' && ch <= 'Z')
            {
                keyCode = ch;
                modifiers = Keys.SHIFT_MASK;
            }
            else if (ch == ' ')
            {
                keyCode = Keys.VK_SPACE;
            }
            else if (ch == '\t')
            {
                keyCode = Keys.VK_TAB;
            }
            else
            {
                // This mirrors KeyMapProcessor for digits/punctuation where
                // event.getUnicodeChar() is used as the SageTV keyCode.
                keyCode = ch;
            }

            client.getCurrentConnection().postKeyEvent(keyCode, modifiers, ch);
            sent++;
        }

        return "op=input_text_native;charsSent=" + sent
                + ";inputPath=miniclient_native_key_event"
                + ";encoding=KeyMapProcessor_equivalent";
    }

    private String hideImeDirect()
    {
        boolean requested = UIActivityLifeCycleHandler.hideImeForDebug();
        return "op=ime_hide;requested=" + requested + ";inputPath=android_debug_direct_ime";
    }

    private String setImeSuppression(Intent intent)
    {
        boolean enabled = Boolean.parseBoolean(clean(intent.getStringExtra("enabled")));
        boolean applied = UIActivityLifeCycleHandler.setKeyboardSuppressedForDebug(enabled);
        return "op=ime_suppress;enabled=" + applied + ";scope=debug_mcp_native_text";
    }

    private String playerControl(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        MediaCmd mediaCmd = requireMediaCmd(client);
        MiniPlayerPlugin player = requirePlayer(mediaCmd);
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

    private String seekRelative(Context context, Intent intent)
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

    private String comskipDirect(Context context, Intent intent)
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

    private String seekTime(Context context, Intent intent, String operation)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null || client.getCurrentConnection().getMediaCmd() == null)
            throw new IllegalStateException("no active media command");
        MediaCmd mediaCmd = client.getCurrentConnection().getMediaCmd();
        MiniPlayerPlugin player = mediaCmd.getPlaya();
        if (player == null)
            throw new IllegalStateException("no active player");
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

    private void runSkipCheckAsync(Context context, Intent intent)
    {
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        final String operation = clean(intent.getStringExtra("op"));
        final String requestedCommandsText = clean(intent.getStringExtra("commands"));
        final String requestedDirection = clean(intent.getStringExtra("direction"));
        final String requestedDeltasText = clean(intent.getStringExtra("deltas_ms"));
        final int delayMs = parseBoundedInt(intent.getStringExtra("delay_ms"), 350, 0, 10000);
        final int settleMs = parseBoundedInt(intent.getStringExtra("settle_ms"), 300, 0, 30000);
        final int recoveryTimeoutMs = parseBoundedInt(intent.getStringExtra("recovery_timeout_ms"), 8000, 250, MAX_RECOVERY_WATCHDOG_MS);
        final int verifyPlaybackMs = parseBoundedInt(intent.getStringExtra("verify_playback_ms"), 2500, 250, 10000);
        final int healthPollMs = parseBoundedInt(intent.getStringExtra("health_poll_ms"), 250, 100, 2000);

        new Thread(new Runnable()
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
                    MiniPlayerPlugin beforePlayer = requirePlayer(mediaCmd);
                    long beforeTimelineMs = currentSageTimelineMs(mediaCmd, beforePlayer);
                    int beforeState = beforePlayer.getState();
                    PlaybackHealthProbe.Snapshot healthBefore = PlaybackHealthProbe.capture(beforePlayer);
                    long startedMs = SystemClock.elapsedRealtime();
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
                                    || PlaybackHealthProbe.audioOutputAdvanced(healthPostCommand, latestHealth);
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
                    long audioRecoveryFrames = countDelta(healthPostCommand.audioRendered,
                            recovered == null ? healthFinal.audioRendered : recovered.audioRendered);
                    long audioVerifyFrames = recovered == null ? -1
                            : countDelta(recovered.audioRendered, healthFinal.audioRendered);
                    long audioRecoveryHeadFrames = audioHeadDelta(healthPostCommand,
                            recovered == null ? healthFinal : recovered);
                    long audioVerifyHeadFrames = recovered == null ? -1 : audioHeadDelta(recovered, healthFinal);
                    boolean audioHeadResetDuringRecovery = PlaybackHealthProbe.audioHeadResetDetected(
                            healthPostCommand, recovered == null ? healthFinal : recovered);
                    boolean audioHeadResetDuringVerify = recovered != null
                            && PlaybackHealthProbe.audioHeadResetDetected(recovered, healthFinal);

                    long landingTimelineMs = outputRecoveryTimelineMs >= 0 ? outputRecoveryTimelineMs : afterTimelineMs;
                    long landingDeltaMs = landingTimelineMs - beforeTimelineMs;

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
                            + ";timelineSource=MEDIACMD_GETMEDIATIME"
                            + ";timelineBeforeMs=" + beforeTimelineMs
                            + ";timelineAfterMs=" + afterTimelineMs
                            + ";timelineDeltaMs=" + timelineDeltaMs
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
        }, "SageTV-MCP-SkipCheck").start();
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

    private MediaCmd requireMediaCmd(MiniClient client)
    {
        if (client.getCurrentConnection() == null || client.getCurrentConnection().getMediaCmd() == null)
            throw new IllegalStateException("no active MediaCmd");
        return client.getCurrentConnection().getMediaCmd();
    }

    private MiniPlayerPlugin requirePlayer(MediaCmd mediaCmd)
    {
        MiniPlayerPlugin player = mediaCmd.getPlaya();
        if (player == null)
            throw new IllegalStateException("no active player");
        return player;
    }

    private long currentSageTimelineMs(MediaCmd mediaCmd, MiniPlayerPlugin player)
    {
        // This is the same value returned by MediaCmd.MEDIACMD_GETMEDIATIME to SageTV,
        // which is what drives SageTV's displayed playback timeline/time bar.
        return player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition());
    }

    private long currentPlayerPositionMs(MediaCmd mediaCmd, MiniPlayerPlugin player)
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

    private static int parseBoundedInt(String value, int defaultValue, int minValue, int maxValue)
    {
        String cleaned = clean(value);
        if (cleaned.isEmpty())
            return defaultValue;
        int parsed = Integer.parseInt(cleaned);
        return Math.max(minValue, Math.min(maxValue, parsed));
    }

    private String snapshot(Context context)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        StringBuilder out = new StringBuilder("op=snapshot;");
        out.append(configuredValues(prefs));
        boolean connected = client.isConnected();
        out.append(";connected=").append(connected);
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
        boolean imeVisibleKnown = imeVisibility >= 0;
        boolean imeVisible = imeVisibility > 0;
        boolean imeRequested = UIActivityLifeCycleHandler.isKeyboardRequestedForDebug();
        boolean imeSuppressed = UIActivityLifeCycleHandler.isKeyboardSuppressedForDebug();
        out.append(";debugStatusVersion=14");
        out.append(";maxRecoveryWatchdogMs=").append(MAX_RECOVERY_WATCHDOG_MS);
        out.append(";uiState=").append(uiState);
        out.append(";automationReady=").append(automationReady);
        out.append(";imeRequested=").append(imeRequested);
        out.append(";imeSuppressedForDebug=").append(imeSuppressed);
        out.append(";imeVisibleKnown=").append(imeVisibleKnown);
        out.append(";imeVisible=").append(imeVisible);
        out.append(';').append(PlayerRuntimeTuning.compactWire());

        if (!playerActive || mediaCmd == null)
            return out.toString();

        long serverAnchorMs = mediaCmd.getLastServerStartPosition();
        long serverRequestedSeekMs = mediaCmd.getLastServerRequestedSeekMs();
        long serverSeekMonotonicMs = mediaCmd.getLastServerSeekMonotonicMs();
        long serverFlushMonotonicMs = mediaCmd.getLastServerFlushMonotonicMs();
        long serverAnchorMonotonicMs = mediaCmd.getLastServerAnchorMonotonicMs();
        long nowMonotonicMs = System.nanoTime() / 1000000L;
        long sageTimelineMs = currentSageTimelineMs(mediaCmd, player);
        out.append(";playerClass=").append(safe(player.getClass().getName()));
        out.append(";state=").append(player.getState());
        out.append(";mediaTimeMs=").append(sageTimelineMs);
        out.append(";sageTimelineMs=").append(sageTimelineMs);
        out.append(";timelineSource=MEDIACMD_GETMEDIATIME");
        out.append(";serverAnchorMs=").append(serverAnchorMs);
        out.append(";serverRequestedSeekMs=").append(serverRequestedSeekMs);
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
        out.append(";bufferLeft=").append(player.getBufferLeft());
        out.append(";lastFileReadPos=").append(player.getLastFileReadPos());

        Dimension video = player.getVideoDimensions();
        if (video != null)
        {
            out.append(";videoWidth=").append(video.width);
            out.append(";videoHeight=").append(video.height);
        }
        PlaybackHealthProbe.Snapshot health = PlaybackHealthProbe.capture(player);
        out.append(health.compactWire("health_"));
        out.append(';').append(PlaybackEventTraps.compactWire());
        return out.toString();
    }

    private String configuredValues(PrefStore prefs)
    {
        String audioChannels = prefs.getFixedEncodingAudioChannels();
        if (audioChannels == null || audioChannels.isEmpty())
            audioChannels = "source";
        return "player=" + safe(prefs.getString(PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE))
                + ";streaming=" + safe(prefs.getStreamingMode())
                + ";decoding=" + safe(prefs.getString(PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE))
                + ";gsyEngine=" + safe(prefs.getString(PrefStore.Keys.gsy_player_engine, GSYPlayerEngine.DEFAULT_PREFERENCE))
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
                + ";fixedRemuxingFormat=" + safe(prefs.getFixedRemuxingFormat());
    }

    private MiniClient requireClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        return app.getClient();
    }

    private MiniClient requireConnectedClient(Context context)
    {
        MiniClient client = requireClient(context);
        if (!client.isConnected())
            throw new IllegalStateException("MiniClient is not connected");
        return client;
    }

    private void ok(String data)
    {
        setResultCode(RESULT_OK);
        setResultData("ok=true;" + data);
    }

    private void fail(String data)
    {
        setResultCode(RESULT_ERROR);
        setResultData("ok=false;" + data);
    }

    private static boolean parseBoolean(String value, boolean defaultValue)
    {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) return defaultValue;
        if ("true".equals(cleaned) || "1".equals(cleaned) || "yes".equals(cleaned)) return true;
        if ("false".equals(cleaned) || "0".equals(cleaned) || "no".equals(cleaned)) return false;
        throw new IllegalArgumentException("invalid boolean: " + value);
    }

    private static String text(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static long ageMs(long nowMonotonicMs, long eventMonotonicMs)
    {
        if (eventMonotonicMs < 0)
            return -1;
        return Math.max(0L, nowMonotonicMs - eventMonotonicMs);
    }

    private static String safe(String value)
    {
        if (value == null) return "";
        return value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }
}
