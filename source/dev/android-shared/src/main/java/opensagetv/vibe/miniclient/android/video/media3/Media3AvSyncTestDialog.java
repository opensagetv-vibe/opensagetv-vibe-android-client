package opensagetv.vibe.miniclient.android.video.media3;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.video.EncodedPassthroughOffsetController;

/** Full-screen, server-independent bouncing-ball A/V synchronization test. */
@UnstableApi
public final class Media3AvSyncTestDialog
{
    private static final String TAG = "VibeAvSyncTest";

    public interface Listener
    {
        void onFinished(int offsetMs);
    }

    private static final int MIN_OFFSET_MS = -4_000;
    private static final int MAX_OFFSET_MS = 4_000;
    private static final int STEP_MS = 25;
    private static final long OFFSET_APPLY_DEBOUNCE_MS = 250L;

    private Media3AvSyncTestDialog() { }

    public static void show(Activity activity, MiniPlayerPlugin active, Listener listener)
    {
        new Session(activity, active, listener).show();
    }

    private static final class Session
    {
        private final Activity activity;
        private final MiniPlayerPlugin active;
        private final Listener listener;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final AtomicBoolean finished = new AtomicBoolean();
        private final boolean passthrough;
        private final boolean previouslyMuted;
        private final int[] offsetMs = new int[1];
        private boolean activeAudioSuspended;

        private ExoPlayer player;
        private EncodedPassthroughOffsetController timingController;
        private ExtractorsFactory fixtureExtractors;
        private Runnable pendingStart;
        private Runnable pendingOffsetApply;
        private Runnable pendingOffsetEvidence;
        private TextView status;
        private SeekBar slider;
        private Dialog dialog;

        Session(Activity activity, MiniPlayerPlugin active, Listener listener)
        {
            this.activity = activity;
            this.active = active;
            this.listener = listener;
            passthrough = active != null && active.isAudioPassthroughEnabled();
            previouslyMuted = active != null && active.isMuted();
            int initial = active == null ? 0 : active.getAudioOffsetMillis();
            offsetMs[0] = Math.round(clamp(initial) / (float) STEP_MS) * STEP_MS;
        }

        void show()
        {
            // Muting is insufficient for encoded playback: the active player
            // still owns the exclusive passthrough AudioTrack. Disable only
            // its audio renderer, leave video/transport running, and give the
            // platform a bounded interval to release the sink before opening
            // the standalone calibration player.
            if (active != null)
            {
                active.setMute(true);
                activeAudioSuspended = active.suspendAudioForExclusiveDiagnostic();
            }
            if (passthrough && !activeAudioSuspended)
            {
                Toast.makeText(activity,
                        "Encoded A/V sync test is unavailable for this active player",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            pendingStart = new Runnable()
            {
                @Override public void run()
                {
                    pendingStart = null;
                    startTest();
                }
            };
            handler.postDelayed(pendingStart, activeAudioSuspended ? 350L : 0L);
        }

        private void startTest()
        {
            if (finished.get()) return;
            try
            {
                buildPlayer();
                buildDialog();
            }
            catch (RuntimeException failure)
            {
                Log.e(TAG, "Unable to start the A/V synchronization test", failure);
                Toast.makeText(activity, "Unable to start the A/V sync test: "
                        + failure.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                finish();
            }
        }

        private void buildPlayer()
        {
            Media3FfmpegAudioSupport.configure();
            Media3AudioExtensionRenderersFactory renderers =
                    new Media3AudioExtensionRenderersFactory(
                            activity, passthrough, 0);
            renderers.setExtensionRendererMode(passthrough
                    ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            // The calibration must move media timestamps for both output
            // routes. Inserting/dropping decoded PCM alone also moves Media3's
            // audio master clock and can leave the ball and click apparently
            // aligned. Shifting source timestamps keeps video timing
            // independent and measures the real display/HDMI/receiver path.
            timingController = new EncodedPassthroughOffsetController(true, offsetMs[0]);
            fixtureExtractors = new DefaultExtractorsFactory();
            fixtureExtractors = new Media3PassthroughOffsetExtractorsFactory(
                    fixtureExtractors, timingController);

            player = new ExoPlayer.Builder(activity, renderers).build();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), false);
            player.addListener(new Player.Listener()
            {
                @Override public void onPlayerError(PlaybackException error)
                {
                    if (status != null)
                        status.setText("Test playback error: " + error.getErrorCodeName());
                }
            });
            player.setMediaSource(createFixtureSource());
            player.prepare();
            player.play();
        }

        private MediaSource createFixtureSource()
        {
            return new ProgressiveMediaSource.Factory(
                    new DefaultDataSource.Factory(activity), fixtureExtractors)
                    .createMediaSource(MediaItem.fromUri(
                            Uri.parse("asset:///vibe_av_sync_ball.ts")));
        }

        private void buildDialog()
        {
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(Color.BLACK);
            root.setFocusableInTouchMode(true);

            // Both legacy ExoPlayer UI and Media3 UI are packaged. Their
            // historical exo_player_view resource names collide during
            // Android resource merging, so Media3 PlayerView can inflate a
            // legacy AspectRatioFrameLayout and fail with a class cast. This
            // controller-free fixture needs only a platform SurfaceView.
            SurfaceView videoSurface = new SurfaceView(activity);
            videoSurface.setFocusable(false);
            player.setVideoSurfaceView(videoSurface);
            root.addView(videoSurface, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout controls = new LinearLayout(activity);
            controls.setOrientation(LinearLayout.VERTICAL);
            controls.setPadding(dp(16), dp(2), dp(16), dp(2));
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(0xe61b1b1b);
            panel.setCornerRadius(dp(4));
            panel.setStroke(dp(1), 0xff707070);
            controls.setBackground(panel);

            status = text(16.0f, Color.WHITE, Gravity.CENTER);
            controls.addView(status, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

            slider = new SeekBar(activity);
            slider.setMax((MAX_OFFSET_MS - MIN_OFFSET_MS) / STEP_MS);
            slider.setProgress((offsetMs[0] - MIN_OFFSET_MS) / STEP_MS);
            slider.setFocusable(false);
            controls.addView(slider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

            TextView help = text(12.0f, Color.LTGRAY, Gravity.CENTER);
            help.setText("Left/Right 0.025 s   Center reset   Back apply");
            controls.addView(help, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));
            updateStatus();

            FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                    Math.min((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.35f),
                            dp(600)),
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.RIGHT);
            controlParams.bottomMargin = dp(18);
            controlParams.rightMargin = dp(18);
            root.addView(controls, controlParams);

            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                @Override public void onProgressChanged(SeekBar seekBar, int progress,
                        boolean fromUser)
                {
                    int requested = MIN_OFFSET_MS + progress * STEP_MS;
                    if (requested == offsetMs[0]) return;
                    applyTestOffset(requested);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });

            dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(root);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener(new DialogInterface.OnKeyListener()
            {
                @Override public boolean onKey(DialogInterface ignored, int keyCode,
                        KeyEvent event)
                {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    {
                        if (event.getAction() == KeyEvent.ACTION_DOWN)
                        {
                            int delta = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                            slider.setProgress(Math.max(0,
                                    Math.min(slider.getMax(), slider.getProgress() + delta)));
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER)
                    {
                        if (event.getAction() == KeyEvent.ACTION_DOWN)
                            slider.setProgress((0 - MIN_OFFSET_MS) / STEP_MS);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK)
                    {
                        if (event.getAction() == KeyEvent.ACTION_UP) dialog.dismiss();
                        return true;
                    }
                    return false;
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
            {
                @Override public void onDismiss(DialogInterface ignored) { finish(); }
            });
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null)
            {
                DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.setLayout(metrics.widthPixels, metrics.heightPixels);
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                AppUtil.hideSystemUIOnTV(activity);
            }
            root.requestFocus();
        }

        private void applyTestOffset(int requested)
        {
            offsetMs[0] = clamp(requested);
            // A held remote key can generate an increment every few dozen
            // milliseconds. Reconfiguring the PCM processor or encoded clock
            // on every repeat creates timestamp discontinuities faster than
            // the renderer can settle and makes the diagnostic itself
            // stutter. Keep the displayed value responsive, then apply only
            // the final value after the key burst ends.
            if (pendingOffsetApply != null) handler.removeCallbacks(pendingOffsetApply);
            pendingOffsetApply = new Runnable()
            {
                @Override public void run()
                {
                    pendingOffsetApply = null;
                    if (timingController != null)
                    {
                        timingController.setOffsetMillis(offsetMs[0]);
                        if (player != null)
                        {
                            // This short local fixture is normally buffered in
                            // full. A seek inside that buffer reuses samples
                            // extracted with the old timestamps, so the label
                            // changes without moving the audible click. Replace
                            // the source at the current phase to flush those
                            // samples and re-extract them through the updated
                            // encoded offset controller.
                            long positionMs = player.getCurrentPosition();
                            player.setMediaSource(createFixtureSource(), positionMs);
                            player.prepare();
                            player.play();
                            if (pendingOffsetEvidence != null)
                                handler.removeCallbacks(pendingOffsetEvidence);
                            pendingOffsetEvidence = new Runnable()
                            {
                                @Override public void run()
                                {
                                    pendingOffsetEvidence = null;
                                    Log.i(TAG, "Calibration offset applied: "
                                            + timingController.describe());
                                }
                            };
                            handler.postDelayed(pendingOffsetEvidence, 750L);
                        }
                    }
                }
            };
            handler.postDelayed(pendingOffsetApply, OFFSET_APPLY_DEBOUNCE_MS);
            updateStatus();
        }

        private void updateStatus()
        {
            if (status == null) return;
            status.setText((passthrough ? "Encoded" : "Decoded PCM")
                    + "   Offset " + String.format(
                    Locale.US, "%+.3f s", offsetMs[0] / 1000.0f));
        }

        private void finish()
        {
            if (!finished.compareAndSet(false, true)) return;
            if (pendingStart != null) handler.removeCallbacks(pendingStart);
            if (pendingOffsetApply != null) handler.removeCallbacks(pendingOffsetApply);
            if (pendingOffsetEvidence != null) handler.removeCallbacks(pendingOffsetEvidence);
            if (player != null)
            {
                player.stop();
                player.release();
                player = null;
            }
            if (active != null)
            {
                if (activeAudioSuspended)
                    active.resumeAudioAfterExclusiveDiagnostic();
                active.setMute(previouslyMuted);
            }
            if (listener != null) listener.onFinished(offsetMs[0]);
        }

        private TextView text(float size, int color, int gravity)
        {
            TextView value = new TextView(activity);
            value.setTextSize(size);
            value.setTextColor(color);
            value.setGravity(gravity);
            value.setIncludeFontPadding(false);
            return value;
        }

        private int dp(int value)
        {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }
    }

    private static int clamp(int value)
    {
        return Math.max(MIN_OFFSET_MS, Math.min(MAX_OFFSET_MS, value));
    }
}
