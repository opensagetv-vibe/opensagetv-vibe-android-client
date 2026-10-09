package opensagetv.vibe.miniclient.android.ui.keymaps;

import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.preferences.MediaMappingPreferences;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import java.util.ArrayDeque;

/** Foreground DVD-title cursor gestures; bounded ordered input, no Core/HTTP dependency. */
final class DvdTimeScrollController
{
    private final MiniClient client;
    private final MediaMappingPreferences prefs;
    private final UIActivityLifeCycleHandler ui;
    private final DvdTimeScrollPolicy policy = new DvdTimeScrollPolicy();
    private final SparseLongArray consumedUps = new SparseLongArray();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<SageCommand> commands = new ArrayDeque<>();
    private boolean dispatching;
    private MiniPlayerPlugin ownerPlayer;
    private MiniClientConnection ownerConnection;
    private long lastRepeat;
    private String action = "";
    private static volatile boolean activeForDebug;
    private static volatile long entryPositionForDebug;
    private static volatile int stepsForDebug;
    private final Runnable dispatch = new Runnable()
    {
        @Override public void run()
        {
            if (!client.isConnected() || client.getCurrentConnection() != ownerConnection
                    || client.getPlayer() != ownerPlayer || !client.isVideoVisible()
                    || ownerPlayer == null || ownerPlayer.isDvdMenuNavigationActive()
                    || !DvdInputContext.isFullscreen(client))
            { abandon(); return; }
            SageCommand command = commands.poll();
            if (command != null) EventRouter.postCommand(client, command);
            if (commands.isEmpty()) dispatching = false;
            else main.postDelayed(this, 150L);
        }
    };

    DvdTimeScrollController(MiniClient client, MediaMappingPreferences prefs, UIActivityLifeCycleHandler ui)
    { this.client = client; this.prefs = prefs; this.ui = ui; }

    String action() { return action; }
    static boolean isActiveForDebug() { return activeForDebug; }
    static long entryPositionForDebug() { return entryPositionForDebug; }
    static int stepsForDebug() { return stepsForDebug; }

    boolean handle(KeyMap keyMap, int key, KeyEvent event)
    {
        long ownedDown = consumedUps.get(key, -1L);
        if (ownedDown == event.getDownTime())
        {
            if (event.getAction() == KeyEvent.ACTION_UP)
            { consumedUps.delete(key); return true; }
            if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT)
                return true; // Never repeat an accept/cancel after key-down.
        }
        MiniPlayerPlugin player = client.getPlayer();
        MiniClientConnection connection = client.getCurrentConnection();
        boolean title = player != null && connection != null && connection.getMediaCmd() != null
                && connection.getMediaCmd().isDvdSessionPending() && client.isVideoVisible()
                && !player.isDvdMenuNavigationActive() && !ui.isKeyboardVisible()
                && DvdInputContext.isFullscreen(client);
        if (policy.isActive() && (!title || player != ownerPlayer || connection != ownerConnection))
            abandon();
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean arrow = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (title && prefs.isDvdHeldArrowControlsEnabled() && arrow
                && (policy.isActive() || keyMap instanceof VideoPlaybackKeyMap))
        {
            long now = SystemClock.elapsedRealtime();
            if (event.getRepeatCount() > 0 && now - lastRepeat < 250L) return true;
            lastRepeat = now;
            ownerPlayer = player;
            ownerConnection = connection;
            if (!policy.isActive())
            {
                entryPositionForDebug = player.getMediaTimeMillis(0L);
                stepsForDebug = 0;
            }
            stepsForDebug += key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            send(policy.move(key == KeyEvent.KEYCODE_DPAD_RIGHT));
            consumedUps.put(key, event.getDownTime());
            action = key == KeyEvent.KEYCODE_DPAD_RIGHT ? "DVD_TS_SKIP_FORWARD" : "DVD_TS_SKIP_BACKWARD";
            return true;
        }
        if (!policy.isActive()) return false;
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
        {
            send(policy.accept());
            consumedUps.put(key, event.getDownTime());
            action = "DVD_TS_ACCEPT";
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MEDIA_PLAY
                || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        {
            clearPendingCommands();
            send(policy.cancel());
            consumedUps.put(key, event.getDownTime());
            action = "DVD_TS_CANCEL";
            return true;
        }
        if (key == KeyEvent.KEYCODE_MEDIA_STOP || key == KeyEvent.KEYCODE_HOME)
            abandon(); // Stop/Home never inject a late Play.
        else
        {
            clearPendingCommands();
            send(policy.cancel());
        }
        // Dedicated FF/RW reaches the established scan mapping only after
        // cancelling the cursor; it cannot accidentally adjust that cursor.
        return false;
    }

    private void send(SageCommand[] commands)
    {
        // STV cursor setup/Refresh can complete asynchronously after Play.
        // Pace each ordered command once; never poll/retry TS as a keepalive.
        for (SageCommand command : commands)
        {
            if (this.commands.size() >= 32) break;
            this.commands.add(command);
        }
        if (!dispatching && !this.commands.isEmpty())
        {
            dispatching = true;
            dispatch.run();
        }
        activeForDebug = policy.isActive();
    }

    private void abandon()
    {
        clearPendingCommands();
        policy.abandon();
        activeForDebug = false;
        ownerPlayer = null;
        ownerConnection = null;
    }

    private void clearPendingCommands()
    {
        main.removeCallbacks(dispatch);
        commands.clear();
        dispatching = false;
    }

    void shutdown()
    {
        abandon();
        consumedUps.clear();
    }
}
