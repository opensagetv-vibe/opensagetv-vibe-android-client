package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.diagnostics.CurrentVideoTestStore;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticSessionSpool;

/** Runs a bounded, user-initiated diagnostic against the currently loaded video. */
final class CurrentVideoDiagnosticTest
{
    private static final long CADENCE_MS = 3_000L;
    private static final long PAUSE_MS = 900L;
    private static final long VERIFY_MS = 2_000L;
    private static final long SEEK_DISTANCE_MS = 15_000L;
    private static final long SEEK_TIMEOUT_MS = 10_000L;
    private static final long POLL_MS = 250L;
    private static final long LANDING_TOLERANCE_MS = 4_000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    interface Completion
    {
        void complete(String summary, String report);
    }

    private CurrentVideoDiagnosticTest() { }

    static boolean isRunning() { return RUNNING.get(); }

    static void confirmAndRun(final Activity activity, final MiniClient client,
            final MediaCmd media, final Completion completion)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        if (player == null)
        {
            AppUtil.message("Load a video before running the test");
            return;
        }
        if (RUNNING.get())
        {
            AppUtil.message("The current-video diagnostic test is already running");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Test Current Video")
                .setMessage("This approximately 20-second test measures playback cadence, "
                        + "pause/resume, buffering, decoder output, and safe seek recovery. "
                        + "Playback may move briefly and will be restored to its current position. "
                        + "The redacted result is saved in the next diagnostic export.")
                .setPositiveButton("Run test", new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    { start(activity, client, media, completion); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void start(Activity activity, MiniClient client, MediaCmd media,
            Completion completion)
    {
        if (!RUNNING.compareAndSet(false, true)) return;
        AppUtil.message("Current-video diagnostic test started");
        new Runner(activity, client, media, completion).start();
    }

    private static final class Runner
    {
        private final Activity activity;
        private final MiniClient client;
        private final MediaCmd originalMedia;
        private final MiniPlayerPlugin originalPlayer;
        private final Completion completion;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final StringBuilder report = new StringBuilder(4096);
        private final long startedMs = SystemClock.elapsedRealtime();
        private final int originalState;
        private final long originalPositionMs;
        private ActivePlayerStatsSnapshot baseline;
        private long seekTargetMs = -1L;
        private int passed;
        private int warnings;
        private int skipped;

        Runner(Activity activity, MiniClient client, MediaCmd media, Completion completion)
        {
            this.activity = activity;
            this.client = client;
            this.originalMedia = media;
            this.originalPlayer = media.getPlaya();
            this.completion = completion;
            this.originalState = originalPlayer.getState();
            this.originalPositionMs = originalPlayer.getMediaTimeMillis(
                    media.getLastServerStartPosition());
        }

        void start()
        {
            try
            {
                report.append("# OpenSageTV Vibe current-video diagnostic test\n")
                        .append("schema=1\n")
                        .append("scope=current-loaded-video\n")
                        .append("mediaPathIncluded=false\n")
                        .append("originalState=").append(originalState).append('\n')
                        .append("originalPositionMs=").append(originalPositionMs).append('\n');
                baseline = capture("baseline");
                later(new Runnable()
                {
                    @Override public void run() { finishCadence(); }
                }, CADENCE_MS);
            }
            catch (Throwable error) { failSafely("start", error); }
        }

        private void finishCadence()
        {
            if (!sameSession()) { abort("player_session_changed_during_cadence"); return; }
            ActivePlayerStatsSnapshot after = capture("cadence-after");
            long positionDelta = delta(baseline.positionMs, after.positionMs);
            long renderedDelta = delta(baseline.videoRendered, after.videoRendered);
            long droppedDelta = delta(baseline.videoDropped, after.videoDropped);
            boolean expectedProgress = originalState == MiniPlayerPlugin.PLAY_STATE;
            boolean advanced = !expectedProgress || positionDelta >= 1_000L
                    || (renderedDelta >= 0 && renderedDelta > 0);
            result("sustainedPlaybackCadence", advanced, false,
                    "windowMs=" + CADENCE_MS + ";positionDeltaMs=" + positionDelta
                            + ";renderedDelta=" + renderedDelta + ";droppedDelta=" + droppedDelta
                            + ";bufferedAheadDeltaMs=" + delta(baseline.bufferedAheadMs,
                            after.bufferedAheadMs));
            if (originalState == MiniPlayerPlugin.PLAY_STATE)
                runPause(after.positionMs);
            else
            {
                skipped("pauseResume", "original_player_not_playing");
                prepareSeek();
            }
        }

        private void runPause(final long beforePauseMs)
        {
            originalPlayer.pause();
            later(new Runnable()
            {
                @Override public void run()
                {
                    if (!sameSession()) { abort("player_session_changed_during_pause"); return; }
                    ActivePlayerStatsSnapshot paused = capture("paused");
                    long heldDelta = delta(beforePauseMs, paused.positionMs);
                    boolean held = paused.state.equals("Paused") && (heldDelta < 0 || heldDelta <= 700L);
                    result("pause", held, false, "positionDeltaMs=" + heldDelta
                            + ";state=" + paused.state);
                    originalPlayer.play();
                    later(new Runnable()
                    {
                        @Override public void run() { finishResume(); }
                    }, VERIFY_MS);
                }
            }, PAUSE_MS);
        }

        private void finishResume()
        {
            if (!sameSession()) { abort("player_session_changed_during_resume"); return; }
            ActivePlayerStatsSnapshot resumed = capture("resumed");
            boolean recovered = resumed.state.equals("Playing") && !resumed.error
                    && (resumed.positionMs < 0 || resumed.positionMs > originalPositionMs);
            result("resume", recovered, false, "state=" + resumed.state
                    + ";positionMs=" + resumed.positionMs + ";error=" + resumed.error);
            prepareSeek();
        }

        private void prepareSeek()
        {
            ActivePlayerStatsSnapshot current = capture("pre-seek");
            if (originalPlayer.isDvdMenuNavigationActive())
            {
                skipped("seekRepeatRestore", "dvd_menu_navigation_active");
                restoreAndFinish();
                return;
            }
            if (originalPositionMs >= SEEK_DISTANCE_MS + 5_000L)
                seekTargetMs = originalPositionMs - SEEK_DISTANCE_MS;
            else if (current.durationMs > originalPositionMs + SEEK_DISTANCE_MS + 10_000L)
                seekTargetMs = originalPositionMs + SEEK_DISTANCE_MS;
            else
            {
                skipped("seekRepeatRestore", "no_safe_seek_window");
                restoreAndFinish();
                return;
            }
            runSeek("seek-first", seekTargetMs, new Runnable()
            {
                @Override public void run()
                {
                    runSeek("seek-away", originalPositionMs, new Runnable()
                    {
                        @Override public void run()
                        {
                            runSeek("seek-repeat", seekTargetMs, new Runnable()
                            {
                                @Override public void run() { restoreAndFinish(); }
                            });
                        }
                    });
                }
            });
        }

        private void runSeek(final String name, final long targetMs, final Runnable next)
        {
            if (!sameSession()) { abort("player_session_changed_before_" + name); return; }
            final ActivePlayerStatsSnapshot before = capture(name + "-before");
            final long requestedMs = SystemClock.elapsedRealtime();
            originalPlayer.seek(Math.max(0L, targetMs));
            pollSeek(name, targetMs, requestedMs, before, next);
        }

        private void pollSeek(final String name, final long targetMs, final long requestedMs,
                final ActivePlayerStatsSnapshot before, final Runnable next)
        {
            later(new Runnable()
            {
                @Override public void run()
                {
                    if (!sameSession()) { abort("player_session_changed_during_" + name); return; }
                    ActivePlayerStatsSnapshot now = capture(name + "-poll");
                    long elapsed = SystemClock.elapsedRealtime() - requestedMs;
                    boolean landed = now.positionMs >= 0
                            && Math.abs(now.positionMs - targetMs) <= LANDING_TOLERANCE_MS;
                    boolean frameAdvanced = before.videoRendered < 0 || now.videoRendered < 0
                            || now.videoRendered > before.videoRendered;
                    boolean healthy = landed && frameAdvanced && !now.error
                            && !now.seekPending && (originalState != MiniPlayerPlugin.PLAY_STATE
                            || now.state.equals("Playing"));
                    if (healthy || elapsed >= SEEK_TIMEOUT_MS)
                    {
                        result(name, healthy, false,
                                "targetMs=" + targetMs + ";landedMs=" + now.positionMs
                                        + ";landingErrorMs=" + (now.positionMs < 0 ? -1
                                        : now.positionMs - targetMs)
                                        + ";recoveryMs=" + elapsed
                                        + ";renderedDelta=" + delta(before.videoRendered,
                                        now.videoRendered)
                                        + ";droppedDelta=" + delta(before.videoDropped,
                                        now.videoDropped)
                                        + ";videoDecoderInitDelta=" + delta(
                                        before.videoDecoderInitCount,
                                        now.videoDecoderInitCount)
                                        + ";videoDecoderReleaseDelta=" + delta(
                                        before.videoDecoderReleaseCount,
                                        now.videoDecoderReleaseCount)
                                        + ";audioDecoderInitDelta=" + delta(
                                        before.audioDecoderInitCount,
                                        now.audioDecoderInitCount)
                                        + ";audioDecoderReleaseDelta=" + delta(
                                        before.audioDecoderReleaseCount,
                                        now.audioDecoderReleaseCount)
                                        + ";sourceBytesDelta=" + delta(before.mediaBytes,
                                        now.mediaBytes)
                                        + ";sourceReadsDelta=" + delta(before.readCount,
                                        now.readCount)
                                        + ";probeCacheHitDelta=" + delta(
                                        before.pullProbeCacheHitBytes,
                                        now.pullProbeCacheHitBytes)
                                        + ";loading=" + now.loading + ";error=" + now.error
                                        + ";playerError=" + clean(now.playerError));
                        next.run();
                    }
                    else pollSeek(name, targetMs, requestedMs, before, next);
                }
            }, POLL_MS);
        }

        private void restoreAndFinish()
        {
            if (sameSession())
            {
                originalPlayer.seek(Math.max(0L, originalPositionMs));
                if (originalState == MiniPlayerPlugin.PAUSE_STATE) originalPlayer.pause();
                else if (originalState == MiniPlayerPlugin.PLAY_STATE) originalPlayer.play();
            }
            later(new Runnable()
            {
                @Override public void run()
                {
                    if (sameSession()) capture("restored");
                    finish("complete");
                }
            }, 750L);
        }

        private ActivePlayerStatsSnapshot capture(String stage)
        {
            ActivePlayerStatsSnapshot snapshot =
                    ActivePlayerStatsSnapshot.capture(activity, client, originalMedia);
            report.append("\n[stage.").append(stage).append("]\n")
                    .append(snapshot.exportText(snapshot.reportedActivityKbps));
            return snapshot;
        }

        private boolean sameSession()
        {
            return originalMedia.getPlaya() == originalPlayer;
        }

        private void result(String name, boolean ok, boolean optional, String detail)
        {
            if (ok) passed++;
            else if (optional) skipped++;
            else warnings++;
            report.append("\n[result.").append(name).append("]\n")
                    .append("status=").append(ok ? "pass" : optional ? "skip" : "warning")
                    .append('\n').append(detail).append('\n');
        }

        private void skipped(String name, String reason)
        {
            result(name, false, true, "reason=" + reason);
        }

        private void abort(String reason)
        {
            warnings++;
            report.append("\n[result.aborted]\nstatus=warning\nreason=")
                    .append(reason).append('\n');
            restoreBestEffort();
            finish("aborted");
        }

        private void later(final Runnable action, long delayMs)
        {
            main.postDelayed(new Runnable()
            {
                @Override public void run()
                {
                    try { action.run(); }
                    catch (Throwable error) { failSafely("async-step", error); }
                }
            }, delayMs);
        }

        private void failSafely(String stage, Throwable error)
        {
            warnings++;
            report.append("\n[result.internalError]\nstatus=warning\nstage=")
                    .append(stage).append("\nerror=")
                    .append(clean(error.getClass().getSimpleName() + ": " + error.getMessage()))
                    .append('\n');
            restoreBestEffort();
            finish("failed");
        }

        private void restoreBestEffort()
        {
            try
            {
                if (!sameSession()) return;
                originalPlayer.seek(Math.max(0L, originalPositionMs));
                if (originalState == MiniPlayerPlugin.PAUSE_STATE) originalPlayer.pause();
                else if (originalState == MiniPlayerPlugin.PLAY_STATE) originalPlayer.play();
            }
            catch (Throwable ignored) { }
        }

        private void finish(String state)
        {
            report.append("\n[summary]\nstate=").append(state)
                    .append("\npassed=").append(passed)
                    .append("\nwarnings=").append(warnings)
                    .append("\nskipped=").append(skipped)
                    .append("\nelapsedMs=")
                    .append(SystemClock.elapsedRealtime() - startedMs).append('\n');
            String summary = String.format(Locale.US,
                    "Current-video test %s: %d passed, %d warning(s), %d skipped",
                    state, passed, warnings, skipped);
            try
            {
                File file = CurrentVideoTestStore.write(activity, report.toString());
                summary += "\nSaved for diagnostic export: " + file.getName();
                DiagnosticSessionSpool.checkpoint("current-video-test-complete");
            }
            catch (Exception error)
            {
                warnings++;
                summary += "\nUnable to save report: " + clean(error.getMessage());
            }
            RUNNING.set(false);
            AppUtil.log.info(summary.replace('\n', ' '));
            if (!activity.isFinishing() && completion != null)
                completion.complete(summary, report.toString());
        }

        private static long delta(long before, long after)
        {
            return before < 0 || after < 0 ? -1L : after - before;
        }

        private static String clean(String value)
        {
            if (value == null || value.length() == 0) return "none";
            return value.replace('\n', ' ').replace('\r', ' ').replace(';', ',');
        }
    }
}
