package opensagetv.vibe.miniclient.android.ui.keymaps;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.preferences.MediaMappingPreferences;
import opensagetv.vibe.miniclient.uibridge.EventRouter;

/** Dedicated DVD scan buttons. Owns one foreground gesture, never a background watchdog. */
final class DvdScanGestureController implements Runnable
{
    private final MiniClient client;
    private final UIActivityLifeCycleHandler ui;
    private final MediaMappingPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SparseLongArray consumedUps = new SparseLongArray();
    private MiniPlayerPlugin player;
    private MiniClientConnection connection;
    private Activity activity;
    private int key = KeyEvent.KEYCODE_UNKNOWN;
    private long downTime, started, lastStep, pendingSince;
    private float expectedRate;
    private boolean pending;
    private String action = "";

    DvdScanGestureController(MiniClient client, UIActivityLifeCycleHandler ui, MediaMappingPreferences prefs)
    { this.client = client; this.ui = ui; this.prefs = prefs; }

    String action() { return action; }

    boolean handle(KeyMap mapping, int code, KeyEvent event)
    {
        if (consumedUps.get(code, -1L) == event.getDownTime())
        {
            if (event.getAction() == KeyEvent.ACTION_UP)
            {
                consumedUps.delete(code);
                if (code == key && downTime == event.getDownTime())
                {
                    // A tap leaves its single rate change active. A deliberate
                    // hold resumes Play even if the final rate acknowledgement
                    // is still in flight. Wire rate zero is never sent.
                    boolean resume = DvdRemoteScanPolicy.isHold(SystemClock.elapsedRealtime() - started);
                    if (resume && validOwner()) EventRouter.postCommand(client, SageCommand.PLAY);
                    action = resume ? "DVD_SCAN_HOLD_RELEASE_PLAY" : "DVD_SCAN_TAP";
                    abandon();
                }
            }
            return true; // Repeated DOWN cannot duplicate the initial tap.
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        // A different gesture supersedes this timer. Play/Stop/Home and TS
        // keep their established handling; never resume an abandoned session.
        abandon();
        boolean scanKey = code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_MEDIA_REWIND;
        if (!scanKey || !prefs.isDvdScanHoldControlsEnabled()
                || event.getRepeatCount() != 0 || ui.isKeyboardVisible()
                || mapping.hasSageCommandOverride(code, false)) return false;
        SageCommand requested = mapping.getNormalPressCommand(code);
        if (requested != SageCommand.FF && requested != SageCommand.REW) return false;
        player = client.getPlayer();
        connection = client.getCurrentConnection();
        activity = UIActivityLifeCycleHandler.getResumedActivityForDebug();
        if (!validOwner()) { abandon(); return false; }
        key = code;
        downTime = event.getDownTime();
        started = lastStep = SystemClock.elapsedRealtime();
        consumedUps.put(code, downTime);
        float rate = player.getPlaybackRate();
        expectedRate = DvdRemoteScanPolicy.tapTarget(requested, rate);
        for (SageCommand command : DvdRemoteScanPolicy.sequence(requested, rate))
            if (command != SageCommand.NONE) EventRouter.postCommand(client, command);
        pending = expectedRate != rate;
        pendingSince = started;
        action = "DVD_SCAN_TAP";
        main.postDelayed(this, 50L);
        return true;
    }

    private boolean validOwner()
    {
        return player != null && connection != null && client.isConnected()
                && client.getPlayer() == player && client.getCurrentConnection() == connection
                && connection.getMediaCmd() != null && connection.getMediaCmd().isDvdSessionPending()
                && client.isVideoVisible() && DvdInputContext.isFullscreen(client) && activity != null
                && UIActivityLifeCycleHandler.getResumedActivityForDebug() == activity
                && !player.isDvdMenuNavigationActive() && player.supportsNativeDvdSkipPulse()
                && player.getState() != MiniPlayerPlugin.STOPPED_STATE
                && player.getState() != MiniPlayerPlugin.EOS_STATE
                && player.getState() != MiniPlayerPlugin.PAUSE_STATE;
    }

    @Override public void run()
    {
        if (key == KeyEvent.KEYCODE_UNKNOWN) return;
        if (!validOwner()) { abandon(); return; }
        long now = SystemClock.elapsedRealtime();
        float rate = player.getPlaybackRate();
        if (pending && Math.abs(rate - expectedRate) < .01f) pending = false;
        if (pending && now - pendingSince >= 4_000L)
        {
            // Bound a non-acknowledging hold rather than flooding Core's queue.
            EventRouter.postCommand(client, SageCommand.PLAY);
            action = "DVD_SCAN_ACK_TIMEOUT_PLAY";
            abandon();
            return;
        }
        if (!pending && now - lastStep >= DvdRemoteScanPolicy.HOLD_STEP_MS)
        {
            SageCommand direction = key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? SageCommand.FF : SageCommand.REW;
            lastStep = now;
            expectedRate = DvdRemoteScanPolicy.tapTarget(direction, rate);
            for (SageCommand command : DvdRemoteScanPolicy.sequence(direction, rate))
                if (command != SageCommand.NONE) EventRouter.postCommand(client, command);
            pending = expectedRate != rate;
            pendingSince = now;
            action = "DVD_SCAN_HOLD_RAMP";
        }
        main.postDelayed(this, 50L);
    }

    void abandon()
    {
        main.removeCallbacks(this);
        key = KeyEvent.KEYCODE_UNKNOWN;
        player = null;
        connection = null;
        activity = null;
        pending = false;
    }

    void shutdown() { abandon(); consumedUps.clear(); }
}
