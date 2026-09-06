package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;

/** Bounded, opt-in player diagnostics that tears itself down after 30 seconds. */
public final class ActivePlayerProcessOverlay
{
    private static final long DISPLAY_MS = 30_000L;
    private static final long UPDATE_MS = 1_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static OverlayUpdate activeUpdate;

    private ActivePlayerProcessOverlay() { }

    public static synchronized boolean isVisible()
    {
        return activeUpdate != null && activeUpdate.isAttached();
    }

    public static void toggle(Activity activity, MediaCmd media)
    {
        if (isVisible())
        {
            hide();
            return;
        }
        show(activity, media);
    }

    /** Deterministic entry point for debug/MCP automation. */
    public static void setVisible(Activity activity, MediaCmd media, boolean visible)
    {
        if (visible)
            show(activity, media);
        else
            hide();
    }

    public static synchronized void show(Activity activity, MediaCmd media)
    {
        hide();
        if (activity == null || activity.isFinishing())
            return;
        TextView view = new TextView(activity);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12.0f);
        view.setBackgroundColor(0xB0000000);
        int padding = Math.max(8, (int) (activity.getResources()
                .getDisplayMetrics().density * 8.0f));
        view.setPadding(padding, padding, padding, padding);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.leftMargin = padding;
        params.topMargin = padding;
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(view, params);
        view.bringToFront();
        activeUpdate = new OverlayUpdate(activity, media, view,
                SystemClock.elapsedRealtime() + DISPLAY_MS);
        MAIN.post(activeUpdate);
    }

    public static synchronized void hide()
    {
        OverlayUpdate update = activeUpdate;
        activeUpdate = null;
        if (update != null)
        {
            MAIN.removeCallbacks(update);
            update.removeView();
        }
    }

    private static final class OverlayUpdate implements Runnable
    {
        private final WeakReference<Activity> activity;
        private final WeakReference<MediaCmd> media;
        private final WeakReference<TextView> view;
        private final long deadlineMs;

        OverlayUpdate(Activity activity, MediaCmd media, TextView view, long deadlineMs)
        {
            this.activity = new WeakReference<Activity>(activity);
            this.media = new WeakReference<MediaCmd>(media);
            this.view = new WeakReference<TextView>(view);
            this.deadlineMs = deadlineMs;
        }

        boolean isAttached()
        {
            TextView current = view.get();
            return current != null && current.getParent() != null;
        }

        @Override public void run()
        {
            Activity owner = activity.get();
            MediaCmd currentMedia = media.get();
            TextView currentView = view.get();
            if (owner == null || owner.isFinishing() || currentView == null
                    || currentView.getParent() == null
                    || SystemClock.elapsedRealtime() >= deadlineMs)
            {
                finish(this);
                return;
            }
            MiniPlayerPlugin player = currentMedia == null ? null : currentMedia.getPlaya();
            if (player == null)
            {
                currentView.setText("No active player");
            }
            else
            {
                String source = currentMedia.isDvdSessionPending() ? "DVD Push" : "SageTV";
                currentView.setText(player.getClass().getSimpleName() + " | " + source
                        + " | state=" + player.getState()
                        + " | time=" + player.getMediaTimeMillis(0L) + " ms"
                        + "\nrate=" + player.getPlaybackRate()
                        + " | buffer=" + player.getBufferLeft()
                        + " | ahead=" + player.getBufferedPlaybackAheadMillis() + " ms"
                        + " | text=" + player.getTextSubtitleSafeAreaPercent() + "%/"
                        + player.getTextSubtitleScalePercent() + "%/"
                        + player.getTextSubtitleStyle());
            }
            MAIN.postDelayed(this, UPDATE_MS);
        }

        void removeView()
        {
            TextView current = view.get();
            if (current != null && current.getParent() instanceof ViewGroup)
                ((ViewGroup) current.getParent()).removeView(current);
        }
    }

    private static synchronized void finish(OverlayUpdate update)
    {
        if (activeUpdate == update)
            activeUpdate = null;
        MAIN.removeCallbacks(update);
        update.removeView();
    }
}
