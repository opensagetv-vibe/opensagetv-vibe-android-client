package opensagetv.vibe.miniclient.android.video;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.video.DisplayRefreshRatePolicy;

/** Applies only capability-proven, current-resolution display mode changes. */
public final class DisplayRefreshController
{
    public static final String OFF = "off";
    public static final String SEAMLESS = "seamless";
    public static final String ALWAYS = "always";

    private static volatile int restoreModeId;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingReload;
    private static volatile long pendingReloadDeadlineMs;

    private DisplayRefreshController() { }

    public static Result inspect(Activity activity, MiniPlayerPlugin player)
    {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Display.Mode current = Build.VERSION.SDK_INT >= 23 ? display.getMode() : null;
        float fps = player == null ? -1f : player.getContentFrameRateHz();
        return new Result(false, "inspection", fps,
                current == null ? display.getRefreshRate() : current.getRefreshRate(),
                current == null ? 0 : current.getModeId(), sameResolutionModes(display, current), false);
    }

    public static Result apply(Activity activity, MiniPlayerPlugin player, String policy)
    {
        if (Looper.myLooper() != Looper.getMainLooper())
        {
            MAIN.post(new Runnable()
            {
                @Override public void run()
                {
                    if (!activity.isFinishing())
                        apply(activity, player, policy);
                }
            });
            return new Result(false, "application_scheduled_on_main", -1f, -1f,
                    0, new Display.Mode[0], false);
        }
        // Restoring the display does not need media metadata. In particular,
        // releasePlayer() may originate on SageTV's GFX worker, so never query
        // a Media3/ExoPlayer instance as part of the OFF path.
        Result state = inspect(activity, OFF.equals(policy) ? null : player);
        if (Build.VERSION.SDK_INT < 23)
            return state.with(false, "display_mode_api_unavailable");
        WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
        if (OFF.equals(policy))
        {
            attrs.preferredDisplayModeId = restoreModeId;
            activity.getWindow().setAttributes(attrs);
            return state.with(true, "restored_previous_mode",
                    restoreModeId != 0 && restoreModeId != state.currentModeId);
        }
        if (state.contentFps <= 0f)
            return state.with(false, "content_fps_unknown");
        Display.Mode currentMode = null;
        List<DisplayRefreshRatePolicy.Mode> policyModes =
                new ArrayList<DisplayRefreshRatePolicy.Mode>();
        for (Display.Mode mode : state.modes)
        {
            DisplayRefreshRatePolicy.Mode policyMode = new DisplayRefreshRatePolicy.Mode(
                    mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight(),
                    mode.getRefreshRate());
            policyModes.add(policyMode);
            if (mode.getModeId() == state.currentModeId) currentMode = mode;
        }
        if (currentMode == null)
            return state.with(false, "no_current_display_mode");
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(
                state.contentFps,
                new DisplayRefreshRatePolicy.Mode(currentMode.getModeId(),
                        currentMode.getPhysicalWidth(), currentMode.getPhysicalHeight(),
                        currentMode.getRefreshRate()),
                policyModes, false, DisplayRefreshRatePolicy.Preference.EXACT_FIRST);
        Display.Mode target = null;
        if (decision.mode != null)
            for (Display.Mode mode : state.modes)
                if (mode.getModeId() == decision.mode.id) target = mode;
        if (target == null)
            return state.with(false, decision.reason);
        if (SEAMLESS.equals(policy) && !isSeamless(state.currentModeId, target, state.modes))
            return state.with(false, "seamless_switch_not_proven");
        if (restoreModeId == 0)
            restoreModeId = state.currentModeId;
        attrs.preferredDisplayModeId = target.getModeId();
        activity.getWindow().setAttributes(attrs);
        return new Result(true, decision.reason, state.contentFps,
                target.getRefreshRate(), target.getModeId(), state.modes,
                target.getModeId() != state.currentModeId);
    }

    /**
     * Delay only the server-owned DVD decoder replacement after a real HDMI
     * mode change. Ordinary playback is never locally paused or desynchronized.
     */
    public static synchronized boolean scheduleControlledDvdReload(
            Activity activity, MediaCmd media, int delayMs)
    {
        cancelPendingReload();
        final int bounded = Math.max(0, Math.min(1_500, delayMs));
        if (bounded <= 0 || activity == null || media == null
                || !media.isDvdSessionPending())
            return false;
        final WeakReference<Activity> owner = new WeakReference<Activity>(activity);
        final WeakReference<MediaCmd> command = new WeakReference<MediaCmd>(media);
        pendingReloadDeadlineMs = android.os.SystemClock.elapsedRealtime() + bounded;
        pendingReload = new Runnable()
        {
            @Override public void run()
            {
                Activity currentOwner = owner.get();
                MediaCmd currentMedia = command.get();
                synchronized (DisplayRefreshController.class)
                {
                    if (pendingReload != this)
                        return;
                    pendingReload = null;
                    pendingReloadDeadlineMs = 0L;
                }
                if (currentOwner == null || currentOwner.isFinishing()
                        || currentMedia == null || !currentMedia.isDvdSessionPending())
                    return;
                currentMedia.requestControlledPlayerReload();
            }
        };
        MAIN.postDelayed(pendingReload, bounded);
        return true;
    }

    public static synchronized void cancelPendingReload()
    {
        if (pendingReload != null)
            MAIN.removeCallbacks(pendingReload);
        pendingReload = null;
        pendingReloadDeadlineMs = 0L;
    }

    public static long getPendingReloadMs()
    {
        long deadline = pendingReloadDeadlineMs;
        return deadline <= 0L ? 0L : Math.max(0L,
                deadline - android.os.SystemClock.elapsedRealtime());
    }

    private static Display.Mode[] sameResolutionModes(Display display, Display.Mode current)
    {
        if (Build.VERSION.SDK_INT < 23 || current == null)
            return new Display.Mode[0];
        List<Display.Mode> result = new ArrayList<Display.Mode>();
        for (Display.Mode mode : display.getSupportedModes())
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight())
                result.add(mode);
        return result.toArray(new Display.Mode[result.size()]);
    }

    private static boolean isSeamless(int currentModeId, Display.Mode target, Display.Mode[] modes)
    {
        if (target.getModeId() == currentModeId)
            return true;
        if (Build.VERSION.SDK_INT < 31)
            return false;
        for (Display.Mode mode : modes)
        {
            if (mode.getModeId() != currentModeId)
                continue;
            for (float alternate : mode.getAlternativeRefreshRates())
                if (Math.abs(alternate - target.getRefreshRate()) < 0.01f)
                    return true;
        }
        return false;
    }

    public static final class Result
    {
        public final boolean applied;
        public final String reason;
        public final float contentFps;
        public final float displayHz;
        public final int currentModeId;
        public final Display.Mode[] modes;
        public final boolean modeChanged;

        Result(boolean applied, String reason, float contentFps, float displayHz,
                int currentModeId, Display.Mode[] modes, boolean modeChanged)
        {
            this.applied = applied;
            this.reason = reason;
            this.contentFps = contentFps;
            this.displayHz = displayHz;
            this.currentModeId = currentModeId;
            this.modes = modes;
            this.modeChanged = modeChanged;
        }

        Result with(boolean nextApplied, String nextReason)
        {
            return new Result(nextApplied, nextReason, contentFps, displayHz,
                    currentModeId, modes, false);
        }

        Result with(boolean nextApplied, String nextReason, boolean nextModeChanged)
        {
            return new Result(nextApplied, nextReason, contentFps, displayHz,
                    currentModeId, modes, nextModeChanged);
        }

        public String summary()
        {
            StringBuilder supported = new StringBuilder();
            for (Display.Mode mode : modes)
            {
                if (supported.length() > 0) supported.append(", ");
                supported.append(String.format(Locale.US, "%.3f", mode.getRefreshRate()));
            }
            return String.format(Locale.US,
                    "content %.3f fps | display %.3f Hz | modes [%s] | %s",
                    contentFps, displayHz, supported, reason);
        }
    }
}
