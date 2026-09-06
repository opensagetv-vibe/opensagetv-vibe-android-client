package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;

/**
 * Opt-in, mode-aware playback statistics. Sampling exists only while the visible panel
 * is attached. MCP retains a bounded 30-second entry point; the user menu can keep the
 * panel visible until explicitly hidden.
 */
public final class ActivePlayerProcessOverlay
{
    static final long DISPLAY_MS = 30_000L;
    private static final long UPDATE_MS = 1_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static OverlayUpdate activeUpdate;

    private ActivePlayerProcessOverlay() { }

    public static synchronized boolean isVisible()
    {
        return activeUpdate != null && activeUpdate.isAttached();
    }

    public static synchronized String visibleMode()
    {
        if (!isVisible()) return "Off";
        return activeUpdate.detailed ? "Detailed" : "Compact";
    }

    /** Compatibility toggle now opens a persistent compact panel. */
    public static void toggle(Activity activity, MediaCmd media)
    {
        if (isVisible()) hide();
        else showPersistent(activity, media, false);
    }

    /** Deterministic, bounded entry point for debug/MCP automation. */
    public static void setVisible(Activity activity, MediaCmd media, boolean visible)
    {
        if (visible) show(activity, media);
        else hide();
    }

    /**
     * Apply the same explicit modes exposed by the Playback Stats submenu.
     * Callers must invoke this on the main thread. The legacy setVisible entry
     * point remains bounded for backward-compatible MCP automation.
     */
    public static void setMode(Activity activity, MediaCmd media, String mode)
    {
        if ("toggle".equals(mode))
        {
            if (isVisible()) hide();
            else showPersistent(activity, media, true);
        }
        else if ("off".equals(mode)) hide();
        else if ("compact".equals(mode)) showPersistent(activity, media, false);
        else if ("detailed".equals(mode)) showPersistent(activity, media, true);
        else if ("detailed_30s".equals(mode)) showForThirtySeconds(activity, media, true);
        else throw new IllegalArgumentException("unsupported Playback Stats mode: " + mode);
    }

    /** Preserve the original bounded behavior for callers outside the user submenu. */
    public static void show(Activity activity, MediaCmd media)
    {
        showInternal(activity, media, true, SystemClock.elapsedRealtime() + DISPLAY_MS);
    }

    public static void showPersistent(Activity activity, MediaCmd media, boolean detailed)
    {
        showInternal(activity, media, detailed, Long.MAX_VALUE);
    }

    public static void showForThirtySeconds(Activity activity, MediaCmd media, boolean detailed)
    {
        showInternal(activity, media, detailed, SystemClock.elapsedRealtime() + DISPLAY_MS);
    }

    private static synchronized void showInternal(Activity activity, MediaCmd media,
            boolean detailed, long deadlineMs)
    {
        hide();
        if (activity == null || activity.isFinishing()) return;
        StatsPanel panel = new StatsPanel(activity);
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        int margin = panel.dp(10);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                panel.dp(360), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.leftMargin = margin;
        params.topMargin = margin;
        root.addView(panel.root, params);
        panel.root.bringToFront();
        activeUpdate = new OverlayUpdate(activity, panel, detailed, deadlineMs);
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

    /** Export the current redacted detailed snapshot; returns the written path or an error. */
    public static synchronized String exportCurrent(Activity activity)
    {
        try
        {
            ActivePlayerStatsSnapshot snapshot = activeUpdate == null
                    ? capture(activity) : activeUpdate.latest;
            long activityKbps = activeUpdate == null ? -1L : activeUpdate.activityKbps;
            if (snapshot == null) snapshot = capture(activity);
            File directory = activity.getExternalFilesDir("diagnostics");
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs()))
                throw new IllegalStateException("diagnostics directory unavailable");
            File output = new File(directory,
                    "playback-stats-" + System.currentTimeMillis() + ".txt");
            FileOutputStream stream = new FileOutputStream(output);
            try
            {
                stream.write(snapshot.exportText(activityKbps).getBytes(StandardCharsets.UTF_8));
            }
            finally
            {
                stream.close();
            }
            return output.getAbsolutePath();
        }
        catch (Exception e)
        {
            return "ERROR: " + e.getMessage();
        }
    }

    private static ActivePlayerStatsSnapshot capture(Activity activity)
    {
        MiniClient client = MiniclientApplication.get().getClient();
        MediaCmd media = client == null || client.getCurrentConnection() == null
                ? null : client.getCurrentConnection().getMediaCmd();
        return ActivePlayerStatsSnapshot.capture(activity, client, media);
    }

    private static final class OverlayUpdate implements Runnable
    {
        private final WeakReference<Activity> activity;
        private final StatsPanel panel;
        private final boolean detailed;
        private final long deadlineMs;
        private long priorCapturedMs = -1L;
        private long priorMediaBytes = -1L;
        private long peakActivityKbps;
        private final CpuSampler cpuSampler = new CpuSampler();
        private ActivePlayerStatsSnapshot latest;
        private long activityKbps = -1L;

        OverlayUpdate(Activity activity, StatsPanel panel, boolean detailed, long deadlineMs)
        {
            this.activity = new WeakReference<Activity>(activity);
            this.panel = panel;
            this.detailed = detailed;
            this.deadlineMs = deadlineMs;
        }

        boolean isAttached() { return panel.root.getParent() != null; }

        @Override public void run()
        {
            Activity owner = activity.get();
            if (owner == null || owner.isFinishing() || owner.isDestroyed()
                    || !isAttached() || SystemClock.elapsedRealtime() >= deadlineMs)
            {
                finish(this);
                return;
            }
            latest = capture(owner);
            cpuSampler.sample(latest);
            activityKbps = rate(latest);
            peakActivityKbps = Math.max(peakActivityKbps, Math.max(0L, activityKbps));
            panel.update(latest, detailed, activityKbps, peakActivityKbps);
            priorCapturedMs = latest.capturedMs;
            priorMediaBytes = latest.mediaBytes;
            MAIN.postDelayed(this, UPDATE_MS);
        }

        private long rate(ActivePlayerStatsSnapshot current)
        {
            if (current.reportedActivityKbps >= 0) return current.reportedActivityKbps;
            if (current.mediaBytes < 0 || priorMediaBytes < 0
                    || current.mediaBytes < priorMediaBytes
                    || current.capturedMs <= priorCapturedMs)
                return -1L;
            // bytes * 8 / milliseconds is numerically kilobits per second.
            return (current.mediaBytes - priorMediaBytes) * 8L
                    / Math.max(1L, current.capturedMs - priorCapturedMs);
        }

        void removeView()
        {
            if (panel.root.getParent() instanceof ViewGroup)
                ((ViewGroup) panel.root.getParent()).removeView(panel.root);
        }
    }

    /** Interval sampler; it performs no work unless the user-visible overlay is active. */
    private static final class CpuSampler
    {
        private long priorTotalTicks = -1L;
        private long priorBusyTicks = -1L;
        private long priorAppCpuMs = -1L;
        private long priorWallMs = -1L;

        void sample(ActivePlayerStatsSnapshot snapshot)
        {
            long[] device = readDeviceCpuTicks();
            long appCpuMs = android.os.Process.getElapsedCpuTime();
            long wallMs = SystemClock.elapsedRealtime();
            if (device != null && priorTotalTicks >= 0 && device[0] > priorTotalTicks)
            {
                long totalDelta = device[0] - priorTotalTicks;
                long busyDelta = Math.max(0L, device[1] - priorBusyTicks);
                snapshot.deviceCpuPercent = Math.min(100f,
                        busyDelta * 100f / Math.max(1L, totalDelta));
            }
            if (priorAppCpuMs >= 0 && wallMs > priorWallMs)
            {
                long cpuDelta = Math.max(0L, appCpuMs - priorAppCpuMs);
                int availableCpus = Math.max(1, Runtime.getRuntime().availableProcessors());
                // Process.getElapsedCpuTime() sums CPU time across all process threads, while
                // aggregate /proc/stat utilization is expressed as a percentage of the device's
                // total multi-core capacity. Normalize both to that same 0-100% device scale.
                snapshot.appCpuPercent = Math.min(100f, cpuDelta * 100f
                        / Math.max(1L, (wallMs - priorWallMs) * availableCpus));
                // The samplers are read a few microseconds apart, so contain harmless interval
                // jitter instead of ever displaying an impossible negative "Other" segment.
                if (snapshot.deviceCpuPercent >= 0f)
                    snapshot.appCpuPercent = Math.min(snapshot.appCpuPercent,
                            snapshot.deviceCpuPercent);
            }
            if (device != null)
            {
                priorTotalTicks = device[0];
                priorBusyTicks = device[1];
            }
            priorAppCpuMs = appCpuMs;
            priorWallMs = wallMs;
        }

        private long[] readDeviceCpuTicks()
        {
            try
            {
                BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
                try
                {
                    String line = reader.readLine();
                    if (line == null || !line.startsWith("cpu ")) return null;
                    String[] values = line.trim().split("\\s+");
                    long total = 0L;
                    long idle = 0L;
                    for (int i = 1; i < values.length && i <= 8; i++)
                    {
                        long value = Long.parseLong(values[i]);
                        total += value;
                        if (i == 4 || i == 5) idle += value;
                    }
                    return new long[] { total, Math.max(0L, total - idle) };
                }
                finally
                {
                    reader.close();
                }
            }
            catch (Throwable ignored)
            {
                return null;
            }
        }
    }

    private static final class StatsPanel
    {
        final LinearLayout root;
        final TextView title;
        final BarRow activity;
        final BarRow buffer;
        final BarRow cpu;
        final TextView details;
        private final Activity owner;
        private int widestContentPx;

        StatsPanel(Activity activity)
        {
            owner = activity;
            root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(12), dp(9), dp(12), dp(10));
            root.setFocusable(false);
            root.setClickable(false);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xD9181818);
            background.setCornerRadius(dp(8));
            background.setStroke(dp(1), 0x80FFFFFF);
            root.setBackground(background);

            title = text(14f, Typeface.BOLD, 0xFFEEEEEE);
            root.addView(title, matchWrap());
            this.activity = new BarRow(activity);
            buffer = new BarRow(activity);
            cpu = new BarRow(activity);
            root.addView(this.activity.root, matchWrap());
            root.addView(buffer.root, matchWrap());
            root.addView(cpu.root, matchWrap());
            details = text(10.5f, Typeface.NORMAL, Color.WHITE);
            details.setLineSpacing(0f, 1.05f);
            LinearLayout.LayoutParams detailParams = matchWrap();
            detailParams.topMargin = dp(5);
            root.addView(details, detailParams);
        }

        void update(ActivePlayerStatsSnapshot snapshot, boolean detailed,
                long activityKbps, long peakActivityKbps)
        {
            title.setText("OpenSageTV Vibe Playback Stats  •  "
                    + (detailed ? "Detailed" : "Compact"));

            boolean activityAvailable = activityKbps >= 0;
            activity.setVisible(activityAvailable);
            if (activityAvailable)
            {
                long scale = Math.max(1L, peakActivityKbps);
                int progress = (int) Math.min(1_000L, activityKbps * 1_000L / scale);
                activity.update("Media network activity",
                        ActivePlayerStatsSnapshot.rate(activityKbps) + "  |  peak "
                                + ActivePlayerStatsSnapshot.rate(peakActivityKbps),
                        1_000, progress, 0xFF66BB6A);
            }

            boolean bufferAvailable = snapshot.bufferedAheadMs >= 0;
            buffer.setVisible(bufferAvailable);
            if (bufferAvailable)
            {
                long targetMs = snapshot.dvd ? 8_000L : 30_000L;
                int progress = (int) Math.min(1_000L,
                        snapshot.bufferedAheadMs * 1_000L / targetMs);
                int color = progress < 100 ? 0xFFEF5350
                        : progress < 300 ? 0xFFFFCA28 : 0xFF66BB6A;
                buffer.update("Buffer health",
                        ActivePlayerStatsSnapshot.durationValue(snapshot.bufferedAheadMs)
                                + (snapshot.bufferBytes >= 0 ? "  •  "
                                + ActivePlayerStatsSnapshot.bytes(snapshot.bufferBytes) : ""),
                        1_000, progress, color);
            }

            boolean cpuAvailable = snapshot.deviceCpuPercent >= 0f
                    || snapshot.appCpuPercent >= 0f;
            cpu.setVisible(cpuAvailable);
            if (cpuAvailable)
            {
                int total = Math.round(Math.max(0f,
                        Math.min(100f, snapshot.deviceCpuPercent)) * 10f);
                int vibe = Math.round(Math.max(0f,
                        Math.min(snapshot.appCpuPercent,
                                Math.max(0f, snapshot.deviceCpuPercent))) * 10f);
                cpu.updateCpu(snapshot.appCpuPercent,
                        Math.max(0f, snapshot.deviceCpuPercent
                                - Math.max(0f, snapshot.appCpuPercent)),
                        snapshot.deviceCpuPercent,
                        1_000, vibe, total, 0xFF42A5F5, 0xFFFFB74D);
            }

            String body = detailed ? snapshot.detailedText(activityKbps)
                    : snapshot.compactText(activityKbps);
            details.setText(body);
            resizeToContent(body);
        }

        private void resizeToContent(String body)
        {
            float width = title.getPaint().measureText(title.getText().toString());
            if (activity.root.getVisibility() == View.VISIBLE)
                width = Math.max(width, activity.label.getPaint().measureText(
                        activity.label.getText().toString()));
            if (buffer.root.getVisibility() == View.VISIBLE)
                width = Math.max(width, buffer.label.getPaint().measureText(
                        buffer.label.getText().toString()));
            if (cpu.root.getVisibility() == View.VISIBLE)
                width = Math.max(width, cpu.label.getPaint().measureText(
                        cpu.label.getText().toString()));
            for (String line : body.split("\\n", -1))
                width = Math.max(width, details.getPaint().measureText(line));

            int screenWidth = owner.getResources().getDisplayMetrics().widthPixels;
            int maximum = Math.min((int) (screenWidth * 0.65f), dp(900));
            int desired = Math.max(dp(300), Math.min(maximum,
                    (int) Math.ceil(width) + root.getPaddingLeft()
                            + root.getPaddingRight() + dp(8)));
            // Do not let changing counters make the panel and graphs twitch smaller.
            if (desired <= widestContentPx) return;
            widestContentPx = desired;
            ViewGroup.LayoutParams params = root.getLayoutParams();
            if (params != null && params.width != desired)
            {
                params.width = desired;
                root.setLayoutParams(params);
            }
        }

        int dp(int value)
        {
            return Math.max(1, (int) (value * owner.getResources()
                    .getDisplayMetrics().density + 0.5f));
        }

        private TextView text(float sp, int style, int color)
        {
            TextView view = new TextView(owner);
            view.setTextColor(color);
            view.setTextSize(sp);
            view.setTypeface(Typeface.DEFAULT, style);
            view.setIncludeFontPadding(false);
            return view;
        }

        private static LinearLayout.LayoutParams matchWrap()
        {
            return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private final class BarRow
        {
            final LinearLayout root;
            final TextView label;
            final ProgressBar bar;

            BarRow(Activity activity)
            {
                root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams rootParams = matchWrap();
                rootParams.topMargin = dp(4);
                root.setLayoutParams(rootParams);
                label = text(10.5f, Typeface.NORMAL, 0xFFE0E0E0);
                root.addView(label, matchWrap());
                bar = new ProgressBar(activity, null,
                        android.R.attr.progressBarStyleHorizontal);
                LinearLayout.LayoutParams barParams = matchWrap();
                barParams.height = dp(5);
                barParams.topMargin = dp(2);
                root.addView(bar, barParams);
            }

            void setVisible(boolean visible) { root.setVisibility(visible ? View.VISIBLE : View.GONE); }

            void update(String name, String value, int max, int progress, int color)
            {
                label.setText(name + "  " + value);
                bar.setMax(max);
                bar.setProgress(Math.max(0, Math.min(max, progress)));
                bar.setSecondaryProgress(0);
                bar.setProgressTintList(ColorStateList.valueOf(color));
                bar.setSecondaryProgressTintList(ColorStateList.valueOf(color));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF505050));
            }

            void updateCpu(float vibePercent, float otherPercent, float totalPercent,
                    int max, int vibeProgress, int totalProgress, int vibeColor,
                    int otherColor)
            {
                SpannableStringBuilder value = new SpannableStringBuilder("CPU usage  ");
                appendColored(value, "Vibe " + ActivePlayerStatsSnapshot.percent(vibePercent),
                        vibeColor);
                value.append("  |  ");
                appendColored(value, "Other " + ActivePlayerStatsSnapshot.percent(otherPercent),
                        otherColor);
                value.append("  |  Total ")
                        .append(ActivePlayerStatsSnapshot.percent(totalPercent));
                label.setText(value);
                bar.setMax(max);
                bar.setSecondaryProgress(Math.max(0, Math.min(max, totalProgress)));
                bar.setProgress(Math.max(0, Math.min(totalProgress, vibeProgress)));
                bar.setProgressTintList(ColorStateList.valueOf(vibeColor));
                bar.setSecondaryProgressTintList(ColorStateList.valueOf(otherColor));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF505050));
            }

            private void appendColored(SpannableStringBuilder text, String value, int color)
            {
                int start = text.length();
                text.append(value);
                text.setSpan(new ForegroundColorSpan(color), start, text.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static synchronized void finish(OverlayUpdate update)
    {
        if (activeUpdate == update) activeUpdate = null;
        MAIN.removeCallbacks(update);
        update.removeView();
    }
}
