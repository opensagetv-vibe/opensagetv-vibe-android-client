package opensagetv.vibe.miniclient.android.tv.debug;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaDrm;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.Collections;

/**
 * Isolated debug-only secure-decoder gate using Axinom's expressly published
 * DRM test vector and entitlement header. This Activity is never packaged in
 * a release APK and never handles ordinary SageTV media.
 */
public final class DebugDrmValidationActivity extends Activity
{
    private static final String TAG = "VIBE_EXT003_DRM";
    private static final String DEFAULT_MANIFEST =
            "https://media.axprod.net/TestVectors/Dash/protected_dash_1080p_h264_singlekey/manifest.mpd";
    private static final String DEFAULT_LICENSE =
            "https://drm-widevine-licensing.axprod.net/AcquireLicense";
    // Public Axinom H.264 DASH single-key test entitlement, published with the
    // vector. It protects no private or production content.
    private static final String DEFAULT_AXDRM_MESSAGE =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.ewogICJ2ZXJzaW9uIjogMSwKICAiY29tX2tleV9pZCI6ICI2OWU1NDA4OC1lOWUwLTQ1MzAtOGMxYS0xZWI2ZGNkMGQxNGUiLAogICJtZXNzYWdlIjogewogICAgInR5cGUiOiAiZW50aXRsZW1lbnRfbWVzc2FnZSIsCiAgICAidmVyc2lvbiI6IDIsCiAgICAibGljZW5zZSI6IHsKICAgICAgImFsbG93X3BlcnNpc3RlbmNlIjogdHJ1ZQogICAgfSwKICAgICJjb250ZW50X2tleXNfc291cmNlIjogewogICAgICAiaW5saW5lIjogWwogICAgICAgIHsKICAgICAgICAgICJpZCI6ICI0MDYwYTg2NS04ODc4LTQyNjctOWNiZi05MWFlNWJhZTFlNzIiLAogICAgICAgICAgImVuY3J5cHRlZF9rZXkiOiAid3QzRW51dVI1UkFybjZBRGYxNkNCQT09IiwKICAgICAgICAgICJ1c2FnZV9wb2xpY3kiOiAiUG9saWN5IEEiCiAgICAgICAgfQogICAgICBdCiAgICB9LAogICAgImNvbnRlbnRfa2V5X3VzYWdlX3BvbGljaWVzIjogWwogICAgICB7CiAgICAgICAgIm5hbWUiOiAiUG9saWN5IEEiLAogICAgICAgICJwbGF5cmVhZHkiOiB7CiAgICAgICAgICAibWluX2RldmljZV9zZWN1cml0eV9sZXZlbCI6IDE1MCwKICAgICAgICAgICJwbGF5X2VuYWJsZXJzIjogWwogICAgICAgICAgICAiNzg2NjI3RDgtQzJBNi00NEJFLThGODgtMDhBRTI1NUIwMUE3IgogICAgICAgICAgXQogICAgICAgIH0KICAgICAgfQogICAgXQogIH0KfQ.l8PnZznspJ6lnNmfAE9UQV532Ypzt1JXQkvrk8gFSRw";

    private ExoPlayer player;
    private TextView status;
    private String videoDecoder = "pending";
    private String audioDecoder = "pending";
    private String securityLevel = "unknown";
    private String secureAvcRequired = "not-probed";

    @Override protected void onCreate(Bundle state)
    {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        SurfaceView videoSurface = new SurfaceView(this);
        root.addView(videoSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xB0000000);
        status.setTextSize(16);
        status.setPadding(24, 16, 24, 16);
        FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(status, label);
        setContentView(root);

        if (getIntent().getBooleanExtra("probe_only", false))
        {
            probeWidevine();
            Log.i(TAG, "RESULT=PROBE securityLevel=" + securityLevel
                    + " secureAvcRequired=" + secureAvcRequired);
            finish();
            return;
        }
        // This gate qualifies the secure video decoder. Disable audio so a
        // protected audio sample cannot encounter the HDMI protection boundary
        // before the video decoder identity has been reported.
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true));
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build();
        player.setVideoSurfaceView(videoSurface);
        player.addAnalyticsListener(new AnalyticsListener()
        {
            @Override public void onVideoDecoderInitialized(EventTime eventTime,
                    String decoderName, long initializedTimestampMs,
                    long initializationDurationMs)
            {
                videoDecoder = decoderName;
                report("VIDEO_DECODER");
            }

            @Override public void onAudioDecoderInitialized(EventTime eventTime,
                    String decoderName, long initializedTimestampMs,
                    long initializationDurationMs)
            {
                audioDecoder = decoderName;
                report("AUDIO_DECODER");
            }

            @Override public void onRenderedFirstFrame(EventTime eventTime,
                    Object output, long renderTimeMs)
            {
                report("FIRST_FRAME");
            }

            @Override public void onPlayerError(EventTime eventTime, PlaybackException error)
            {
                if (videoDecoder.endsWith(".secure")
                        && causeContains(error, "Required output protections are not active"))
                {
                    // A capture/splitter without the required HDCP level must
                    // be rejected. Reaching this exact boundary proves license
                    // acquisition and secure-codec selection without claiming
                    // that protected presentation itself passed.
                    Log.i(TAG, "RESULT=SECURE_PATH_CONFIRMED_OUTPUT_PROTECTED"
                            + " videoDecoder=" + videoDecoder
                            + " code=" + error.getErrorCodeName());
                    status.setText("Secure decoder confirmed\nProtected output correctly blocked");
                }
                else
                {
                    Log.e(TAG, "RESULT=FAIL code=" + error.getErrorCodeName()
                            + " securityLevel=" + securityLevel
                            + " secureAvcRequired=" + secureAvcRequired, error);
                    status.setText("DRM validation failed: " + error.getErrorCodeName());
                }
            }
        });
        player.addListener(new Player.Listener()
        {
            @Override public void onPlaybackStateChanged(int playbackState)
            {
                if (playbackState == Player.STATE_READY)
                    report("READY");
            }
        });

        String manifest = stringExtra("manifest", DEFAULT_MANIFEST);
        String license = stringExtra("license", DEFAULT_LICENSE);
        String message = stringExtra("axdrm_message", DEFAULT_AXDRM_MESSAGE);
        MediaItem.DrmConfiguration drm = new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(license)
                .setLicenseRequestHeaders(Collections.singletonMap("X-AxDRM-Message", message))
                .build();
        MediaItem item = new MediaItem.Builder()
                .setUri(manifest)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setDrmConfiguration(drm)
                .build();
        status.setText("Authorized Widevine test starting\nSecurity: " + securityLevel);
        Log.i(TAG, "START scheme=Widevine securityLevel=" + securityLevel
                + " secureAvcRequired=" + secureAvcRequired);
        player.setMediaItem(item);
        player.prepare();
        player.play();
    }

    private String stringExtra(String name, String fallback)
    {
        String value = getIntent().getStringExtra(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void probeWidevine()
    {
        MediaDrm drm = null;
        try
        {
            drm = new MediaDrm(C.WIDEVINE_UUID);
            securityLevel = drm.getPropertyString("securityLevel");
            secureAvcRequired = String.valueOf(requiresSecureDecoder(drm, MimeTypes.VIDEO_H264));
        }
        catch (Exception error)
        {
            securityLevel = "probe-error:" + error.getClass().getSimpleName();
            Log.w(TAG, "Widevine capability probe failed", error);
        }
        finally
        {
            if (drm != null) drm.release();
        }
    }

    private static boolean requiresSecureDecoder(MediaDrm drm, String mimeType)
    {
        // The method name changed across Android platform levels. Reflection
        // keeps this debug probe buildable at Vibe's older compile SDK while
        // still measuring the actual runtime implementation.
        for (String method : new String[] {"requiresSecureDecoder", "requiresSecureDecoderComponent"})
        {
            try
            {
                Object result = MediaDrm.class.getMethod(method, String.class).invoke(drm, mimeType);
                if (result instanceof Boolean) return (Boolean) result;
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }
        return false;
    }

    private void report(String event)
    {
        long position = player == null ? -1 : player.getCurrentPosition();
        String text = "event=" + event + " securityLevel=" + securityLevel
                + " secureAvcRequired=" + secureAvcRequired
                + " videoDecoder=" + videoDecoder + " audioDecoder=" + audioDecoder
                + " positionMs=" + position;
        Log.i(TAG, text);
        if ("FIRST_FRAME".equals(event))
            status.setText("DRM validation PASS\n" + text);
    }

    private static boolean causeContains(Throwable error, String text)
    {
        for (Throwable current = error; current != null; current = current.getCause())
        {
            String message = current.getMessage();
            if (message != null && message.contains(text)) return true;
        }
        return false;
    }

    @Override protected void onStop()
    {
        if (player != null)
        {
            player.stop();
            player.release();
            player = null;
        }
        super.onStop();
    }
}
