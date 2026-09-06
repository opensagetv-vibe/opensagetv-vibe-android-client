package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoolean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoundedInt;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.safe;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.text;

import android.content.Context;
import android.content.Intent;

import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.ActivePlayerAdjustmentsDialog;
import opensagetv.vibe.miniclient.android.ActivePlayerProcessOverlay;
import opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity;
import opensagetv.vibe.miniclient.android.opengl.MiniClientOpenGLActivity;
import opensagetv.vibe.miniclient.android.tv.MainActivity;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import opensagetv.vibe.miniclient.uibridge.Keys;

/** Debug-only connection, Sage command, native-text, and IME operations. */
final class DebugSessionCommands
{
    private DebugSessionCommands()
    {
    }

    static String connectServer(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        String serverName = text(intent.getStringExtra("server_name"));
        String address = text(intent.getStringExtra("address"));
        int port = parseBoundedInt(intent.getStringExtra("port"), 31099, 1, 65535);
        boolean save = parseBoolean(intent.getStringExtra("save"), true);
        String renderer = clean(intent.getStringExtra("renderer")).toLowerCase();
        if (renderer.isEmpty())
            renderer = client.properties().getBoolean(PrefStore.Keys.use_opengl_ui, true)
                    ? "opengl" : "gdx";
        if (!"opengl".equals(renderer) && !"gdx".equals(renderer))
            throw new IllegalArgumentException("renderer must be opengl or gdx");
        client.properties().setBoolean(PrefStore.Keys.use_opengl_ui, "opengl".equals(renderer));

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
                + ";renderer=" + safe(renderer)
                + ";launchActivity=" + safe(activityClass.getName())
                + ";save=" + save;
    }

    static String exitSession(Context context)
    {
        MiniclientApplication.get(context).getBackgroundSessionOwner().state().explicitExit();
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

    static String sendCommand(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        String key = clean(intent.getStringExtra("command"));
        SageCommand command = SageCommand.parseByKey(key);
        if (command == SageCommand.UNKNOWN || command == SageCommand.NONE)
            throw new IllegalArgumentException("invalid Sage command: " + key);
        EventRouter.postCommand(client, command);
        return "op=command;command=" + safe(command.getKey()) + ";eventCode=" + command.getEventCode();
    }

    static String showActivePlayerAdjustments(Context context)
    {
        final android.app.Activity activity =
                UIActivityLifeCycleHandler.getResumedActivityForDebug();
        if (activity == null)
            throw new IllegalStateException("no resumed MiniClient playback activity");
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                ActivePlayerAdjustmentsDialog.show(activity);
            }
        });
        return "op=active_player_adjustments;shown=true";
    }

    static String setActivePlayerOverlay(Context context, Intent intent)
    {
        final android.app.Activity activity =
                UIActivityLifeCycleHandler.getResumedActivityForDebug();
        if (activity == null)
            throw new IllegalStateException("no resumed MiniClient playback activity");
        final MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null)
            throw new IllegalStateException("no active connection");
        final boolean visible = parseBoolean(intent.getStringExtra("visible"), true);
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                ActivePlayerProcessOverlay.setVisible(activity,
                        client.getCurrentConnection().getMediaCmd(), visible);
            }
        });
        return "op=active_player_overlay;requestedVisible=" + visible;
    }

    static String watchServerFile(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null)
            throw new IllegalStateException("no active connection");
        String serverPath = text(intent.getStringExtra("server_path"));
        if (serverPath.isEmpty())
            throw new IllegalArgumentException("server_path is required");
        boolean fromBeginning = "true".equalsIgnoreCase(clean(
                intent.getStringExtra("restart_from_beginning")));
        boolean accepted = client.getCurrentConnection().postVibeWatchFileEvent(
                serverPath, fromBeginning);
        if (!accepted)
            throw new IllegalStateException("Vibe watch-file event could not be sent");
        return "op=watch_server_file;accepted=true"
                + ";serverPath=" + safe(serverPath)
                + ";restartFromBeginning=" + fromBeginning
                + ";inputPath=miniclient_vibe_watch_file_event"
                + ";requiresServerProperty=miniclient/enable_vibe_watch_file_event";
    }

    static String setLiveChannel(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null)
            throw new IllegalStateException("no active connection");
        String channel = text(intent.getStringExtra("channel"));
        if (!channel.matches("[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("valid dotted channel is required");
        boolean accepted = client.getCurrentConnection().postVibeChannelSetEvent(channel);
        if (!accepted)
            throw new IllegalStateException("Vibe channel-set event could not be sent");
        return "op=set_live_channel;accepted=true"
                + ";channel=" + safe(channel)
                + ";inputPath=miniclient_vibe_channel_set_event"
                + ";requiresServerProperty=miniclient/enable_vibe_channel_set_event";
    }

    static String seekServerTime(Context context, Intent intent)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null)
            throw new IllegalStateException("no active connection");
        String targetText = clean(intent.getStringExtra("target_ms"));
        if (targetText.isEmpty())
            throw new IllegalArgumentException("target_ms is required");
        long targetMs = Long.parseLong(targetText);
        if (targetMs < 0)
            throw new IllegalArgumentException("target_ms must be >= 0");
        boolean accepted = client.getCurrentConnection().postVibeSeekEvent(targetMs);
        if (!accepted)
            throw new IllegalStateException("Vibe server-seek event could not be sent");
        return "op=server_seek_time;accepted=true"
                + ";targetMs=" + targetMs
                + ";inputPath=miniclient_vibe_seek_event"
                + ";requiresServerProperty=miniclient/enable_vibe_watch_file_event";
    }

    static String inputTextNative(Context context, Intent intent)
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
                keyCode = Character.toUpperCase(ch);
            else if (ch >= 'A' && ch <= 'Z')
            {
                keyCode = ch;
                modifiers = Keys.SHIFT_MASK;
            }
            else if (ch == ' ')
                keyCode = Keys.VK_SPACE;
            else if (ch == '\t')
                keyCode = Keys.VK_TAB;
            else
                keyCode = ch;

            client.getCurrentConnection().postKeyEvent(keyCode, modifiers, ch);
            sent++;
        }

        return "op=input_text_native;charsSent=" + sent
                + ";inputPath=miniclient_native_key_event"
                + ";encoding=KeyMapProcessor_equivalent";
    }

    static String hideImeDirect()
    {
        boolean requested = UIActivityLifeCycleHandler.hideImeForDebug();
        return "op=ime_hide;requested=" + requested + ";inputPath=android_debug_direct_ime";
    }

    static String setImeSuppression(Intent intent)
    {
        boolean enabled = Boolean.parseBoolean(clean(intent.getStringExtra("enabled")));
        boolean applied = UIActivityLifeCycleHandler.setKeyboardSuppressedForDebug(enabled);
        return "op=ime_suppress;enabled=" + applied + ";scope=debug_mcp_native_text";
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
