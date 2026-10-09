package opensagetv.vibe.miniclient.android.ui.keymaps;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Locale;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;

/** Foreground-only, non-interactive DVD position display, independent of STV menus. */
final class DvdNavigationOverlay implements Runnable
{
    private final MiniClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView view;
    private Activity activity;
    private MiniPlayerPlugin player;
    private MiniClientConnection connection;
    private boolean held;
    private long until;
    private String action;

    DvdNavigationOverlay(MiniClient client) { this.client = client; }

    void show(String action, boolean held)
    {
        Activity current = UIActivityLifeCycleHandler.getResumedActivityForDebug();
        MiniPlayerPlugin active = client.getPlayer();
        if (current == null || active == null || active.isDvdMenuNavigationActive()) return;
        if (activity != current || player != active || connection != client.getCurrentConnection()) hide();
        activity = current;
        player = active;
        connection = client.getCurrentConnection();
        this.action = action;
        this.held = held;
        until = SystemClock.elapsedRealtime() + 3_000L;
        if (view == null)
        {
            view = new TextView(activity);
            view.setTextColor(Color.WHITE);
            view.setBackgroundColor(0xDD202020);
            view.setTextSize(20f);
            view.setGravity(Gravity.CENTER_VERTICAL);
            int padding = Math.round(16f * activity.getResources().getDisplayMetrics().density);
            view.setPadding(padding, padding / 2, padding, padding / 2);
            view.setFocusable(false);
            view.setClickable(false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            params.setMargins(padding * 2, 0, padding * 2, padding * 2);
            ((ViewGroup) activity.getWindow().getDecorView()).addView(view, params);
        }
        main.removeCallbacks(this);
        run();
    }

    void release()
    {
        held = false;
        until = SystemClock.elapsedRealtime() + 3_000L;
    }

    @Override public void run()
    {
        if (view == null) return;
        if (UIActivityLifeCycleHandler.getResumedActivityForDebug() != activity
                || !client.isConnected() || client.getCurrentConnection() != connection
                || client.getPlayer() != player || !client.isVideoVisible()
                || player.isDvdMenuNavigationActive() || player.getState() == MiniPlayerPlugin.STOPPED_STATE
                || player.getState() == MiniPlayerPlugin.EOS_STATE
                || !DvdInputContext.isFullscreen(client))
        { hide(); return; }
        float rate = player.getPlaybackRate();
        if (rate != 1f) { hide(); return; } // No Android DVD/rate banner during FF/RW.
        if (held) until = SystemClock.elapsedRealtime() + 3_000L;
        if (SystemClock.elapsedRealtime() >= until) { hide(); return; }
        long seconds = Math.max(0L, player.getMediaTimeMillis(0L)) / 1000L;
        String status = held ? action : "Play";
        // Stock NEWCELL supplies an offset, not the title duration. Show the
        // real source position; never invent a percentage from decoder time.
        view.setText(String.format(Locale.US, "DVD   %02d:%02d:%02d    %s",
                seconds / 3600L, seconds / 60L % 60L, seconds % 60L, status));
        main.postDelayed(this, 250L);
    }

    void hide()
    {
        main.removeCallbacks(this);
        if (view != null && view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
        view = null;
        activity = null;
        player = null;
        connection = null;
        held = false;
    }
}
