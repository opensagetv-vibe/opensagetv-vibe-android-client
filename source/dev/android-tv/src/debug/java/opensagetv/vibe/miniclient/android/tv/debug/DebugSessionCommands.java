package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoolean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoundedInt;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.safe;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.text;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.opengl.GLSurfaceView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;

import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.ActivePlayerAdjustmentsDialog;
import opensagetv.vibe.miniclient.android.ActivePlayerProcessOverlay;
import opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity;
import opensagetv.vibe.miniclient.android.opengl.MiniClientOpenGLActivity;
import opensagetv.vibe.miniclient.android.opengl.OpenGLRenderer;
import opensagetv.vibe.miniclient.android.tv.MainActivity;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import opensagetv.vibe.miniclient.uibridge.Keys;

/** Debug-only connection, Sage command, native-text, and IME operations. */
final class DebugSessionCommands
{
    private static final long RECONNECT_ACTIVITY_TEARDOWN_MS = 750L;

    private DebugSessionCommands()
    {
    }

    static String dvdArrowHold(Context context, Intent intent)
    {
        final MiniClient client = requireClient(context);
        final MiniClientConnection connection = client.getCurrentConnection();
        final Activity activity = UIActivityLifeCycleHandler.getResumedActivityForDebug();
        final opensagetv.vibe.miniclient.MiniPlayerPlugin player = client.getPlayer();
        if (activity == null || player == null || connection == null || connection.getMediaCmd() == null
                || !connection.getMediaCmd().isDvdSessionPending() || player.isDvdMenuNavigationActive())
            throw new IllegalStateException("foreground DVD title required");
        final int key = parseBoundedInt(intent.getStringExtra("keycode"), -1, 19, 90);
        if ((key < 19 || key > 22) && key != android.view.KeyEvent.KEYCODE_MEDIA_REWIND
                && key != android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            throw new IllegalArgumentException("DVD direction or dedicated FF/RW required");
        final int holdMs = parseBoundedInt(intent.getStringExtra("hold_ms"), 200, 20, 12000);
        final Handler main = new Handler(Looper.getMainLooper());
        // Always post instead of dispatching synchronously inside the broadcast
        // receiver. No View/IME dispatch or retry of an ordered key gesture.
        main.post(new Runnable()
        {
            @Override public void run()
            {
                if (client.getCurrentConnection() != connection || client.getPlayer() != player
                        || UIActivityLifeCycleHandler.getResumedActivityForDebug() != activity) return;
                final long downTime = android.os.SystemClock.uptimeMillis();
                activity.dispatchKeyEvent(new android.view.KeyEvent(downTime, downTime,
                        android.view.KeyEvent.ACTION_DOWN, key, 0));
                main.postDelayed(new Runnable()
                {
                    @Override public void run()
                    {
                        if (client.getCurrentConnection() != connection || client.getPlayer() != player
                                || UIActivityLifeCycleHandler.getResumedActivityForDebug() != activity) return;
                        activity.dispatchKeyEvent(new android.view.KeyEvent(downTime,
                                android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, key, 0));
                    }
                }, holdMs);
            }
        });
        return "op=dvd_arrow_hold;keycode=" + key + ";holdMs=" + holdMs
                + ";queued=true;inputPath=foreground_activity_dispatch";
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

        Activity resumedActivity = UIActivityLifeCycleHandler.getResumedActivityForDebug();
        boolean replacingMiniClientActivity = resumedActivity instanceof MiniClientOpenGLActivity
                || resumedActivity instanceof MiniClientGDXActivity;
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
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (replacingMiniClientActivity)
        {
            // Closing one debug session can deliver Activity.onPause/onDestroy after the
            // replacement connection has already published itself.  That stale teardown
            // then closes the new global MiniClient connection.  Retire the old Activity
            // first and launch after its lifecycle callbacks have completed.
            resumedActivity.finish();
            final Context appContext = context.getApplicationContext();
            final Intent delayedStart = new Intent(start);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
            {
                @Override public void run()
                {
                    appContext.startActivity(delayedStart);
                }
            }, RECONNECT_ACTIVITY_TEARDOWN_MS);
        }
        else
        {
            context.startActivity(start);
        }

        return "op=connect;source=" + safe(source)
                + ";serverName=" + safe(server.name)
                + ";serverAddress=" + safe(server.address)
                + ";serverPort=" + server.port
                + ";renderer=" + safe(renderer)
                + ";launchActivity=" + safe(activityClass.getName())
                + ";launchDelayed=" + replacingMiniClientActivity
                + ";teardownDelayMs="
                + (replacingMiniClientActivity ? RECONNECT_ACTIVITY_TEARDOWN_MS : 0L)
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

    static String showCurrentVideoTest(Context context)
    {
        final android.app.Activity activity =
                UIActivityLifeCycleHandler.getResumedActivityForDebug();
        if (activity == null)
            throw new IllegalStateException("no resumed MiniClient playback activity");
        requireConnectedClient(context);
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                ActivePlayerAdjustmentsDialog.testCurrentVideo(activity);
            }
        });
        return "op=test_current_video;confirmationShown=true";
    }

    static String showAvSyncTest(Context context)
    {
        final android.app.Activity activity =
                UIActivityLifeCycleHandler.getResumedActivityForDebug();
        if (activity == null)
            throw new IllegalStateException("no resumed MiniClient playback activity");
        requireConnectedClient(context);
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                ActivePlayerAdjustmentsDialog.showAvSyncTest(activity);
            }
        });
        return "op=av_sync_test;shown=true;fixture=embedded_common_clock";
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
        final String requestedMode = clean(intent.getStringExtra("mode")).toLowerCase(
                java.util.Locale.US);
        final boolean explicitMode = !requestedMode.isEmpty();
        if (explicitMode && !"toggle".equals(requestedMode) && !"off".equals(requestedMode)
                && !"compact".equals(requestedMode) && !"detailed".equals(requestedMode)
                && !"detailed_30s".equals(requestedMode))
            throw new IllegalArgumentException("mode must be toggle, off, compact, detailed, or detailed_30s");
        final boolean visible = parseBoolean(intent.getStringExtra("visible"), true);
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if (explicitMode)
                    ActivePlayerProcessOverlay.setMode(activity,
                            client.getCurrentConnection().getMediaCmd(), requestedMode);
                else
                    ActivePlayerProcessOverlay.setVisible(activity,
                            client.getCurrentConnection().getMediaCmd(), visible);
            }
        });
        return explicitMode
                ? "op=active_player_overlay;requestedMode=" + requestedMode
                : "op=active_player_overlay;requestedVisible=" + visible;
    }

    static String refreshVideoOutput(Context context)
    {
        MiniClient client = requireConnectedClient(context);
        if (client.getCurrentConnection() == null
                || client.getCurrentConnection().getMediaCmd() == null)
            throw new IllegalStateException("no active media session");
        boolean accepted = client.getCurrentConnection().getMediaCmd()
                .requestControlledPlayerReload();
        if (!accepted)
            throw new IllegalStateException(
                    "active transport/backend cannot refresh video output locally");
        return "op=refresh_video_output;accepted=true"
                + ";transportUnchanged=true;serverSeek=false";
    }

    /**
     * Debug APK only: close this session's graphics read socket so the normal
     * MiniClient type-5 reconnect path runs against an unchanged stock server.
     * Reflection keeps this fault injector out of release-client/Core APIs.
     */
    static String forceGfxReadFault(Context context, Intent intent) throws Exception
    {
        if (!"close_gfx_socket".equals(clean(intent.getStringExtra("confirm"))))
            throw new IllegalArgumentException("confirm=close_gfx_socket is required");
        String expectedServer = clean(intent.getStringExtra("expected_server"));
        if (expectedServer.isEmpty())
            throw new IllegalArgumentException("expected_server is required");
        MiniClientConnection connection = requireConnectedClient(context).getCurrentConnection();
        if (connection == null)
            throw new IllegalStateException("no active MiniClient connection");

        int rejectedReconnectAttempts = parseBoundedInt(
                intent.getStringExtra("reject_reconnect_attempts"), 0, 0, 1);
        if (rejectedReconnectAttempts > 0) {
            Method armReject = MiniClientConnection.class.getDeclaredMethod(
                    "armGfxReconnectRejectForTest", int.class);
            armReject.setAccessible(true);
            armReject.invoke(connection, rejectedReconnectAttempts);
        }

        Field serverField = MiniClientConnection.class.getDeclaredField("msi");
        serverField.setAccessible(true);
        ServerInfo server = (ServerInfo) serverField.get(connection);
        if (server == null || !expectedServer.equals(server.address))
            throw new IllegalStateException("active server does not match expected_server");

        Field workersField = MiniClientConnection.class.getDeclaredField("connectionWorkers");
        workersField.setAccessible(true);
        Object workers = workersField.get(connection);
        Method gfxSocket = workers.getClass().getDeclaredMethod("gfxSocket");
        gfxSocket.setAccessible(true);
        Socket socket = (Socket) gfxSocket.invoke(workers);
        if (socket == null || socket.isClosed())
            throw new IllegalStateException("no open graphics socket");
        int localPort = socket.getLocalPort();
        socket.close();
        return "op=gfx_read_fault;server=" + safe(server.address)
                + ";localPort=" + localPort + ";closed=true;mediaSocketUntouched=true"
                + ";rejectedReconnectAttempts=" + rejectedReconnectAttempts;
    }

    /** Debug-only lifecycle gate; does not change the server or media socket. */
    static String simulateGfxContextRecreation(Context context, Intent intent) throws Exception
    {
        if (!"recreate_gfx_context".equals(clean(intent.getStringExtra("confirm"))))
            throw new IllegalArgumentException("confirm=recreate_gfx_context is required");
        String expectedServer = clean(intent.getStringExtra("expected_server"));
        if (expectedServer.isEmpty())
            throw new IllegalArgumentException("expected_server is required");
        MiniClient client = requireConnectedClient(context);
        MiniClientConnection connection = client.getCurrentConnection();
        Field serverField = MiniClientConnection.class.getDeclaredField("msi");
        serverField.setAccessible(true);
        ServerInfo server = (ServerInfo) serverField.get(connection);
        if (server == null || !expectedServer.equals(server.address))
            throw new IllegalStateException("active server does not match expected_server");
        if (!(client.getUIRenderer() instanceof OpenGLRenderer))
            throw new IllegalStateException("OpenGL renderer is not active");
        final OpenGLRenderer renderer = (OpenGLRenderer) client.getUIRenderer();
        Field viewField = OpenGLRenderer.class.getDeclaredField("glView");
        viewField.setAccessible(true);
        GLSurfaceView view = (GLSurfaceView) viewField.get(renderer);
        if (view == null)
            throw new IllegalStateException("OpenGL view is not ready");
        view.queueEvent(new Runnable()
        {
            @Override public void run()
            {
                renderer.onSurfaceCreated(null, null);
            }
        });
        return "op=gfx_context_recreated;server=" + safe(server.address)
                + ";queued=true;serverUntouched=true";
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
