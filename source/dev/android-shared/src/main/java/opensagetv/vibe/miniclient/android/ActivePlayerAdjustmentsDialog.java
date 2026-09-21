package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticExportController;
import opensagetv.vibe.miniclient.android.video.ActivePlayerSessionOverrides;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.DisplayRefreshController;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.android.video.TextSubtitlePresentation;
import opensagetv.vibe.miniclient.android.video.gsy.GSYPlayerEngine;
import opensagetv.vibe.miniclient.android.video.media3.Media3AvSyncTestDialog;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.CaptionSlotPolicy;
import opensagetv.vibe.miniclient.media.TrackPreferencePolicy;

/** Runtime-safe playback controls reachable from the long-press navigation UI. */
public final class ActivePlayerAdjustmentsDialog
{
    private interface ValueSetter { void set(String value); }

    private final Activity activity;
    private final MiniClient client;

    private ActivePlayerAdjustmentsDialog(Activity activity)
    {
        this.activity = activity;
        this.client = MiniclientApplication.get().getClient();
    }

    public static void show(Activity activity)
    {
        new ActivePlayerAdjustmentsDialog(activity).showMain();
    }

    /** Open the complete live caption selector directly from the CC icon. */
    public static void showCaptions(Activity activity)
    {
        ActivePlayerAdjustmentsDialog dialog = new ActivePlayerAdjustmentsDialog(activity);
        dialog.chooseCaptions(dialog.media());
    }

    /** Open decoded-audio and A/V sync controls directly from the audio icon. */
    public static void showAudio(Activity activity)
    {
        new ActivePlayerAdjustmentsDialog(activity).showAudioMenu();
    }

    public static void testCurrentVideo(Activity activity)
    {
        ActivePlayerAdjustmentsDialog dialog = new ActivePlayerAdjustmentsDialog(activity);
        dialog.testCurrentVideo(dialog.media());
    }

    private MediaCmd media()
    {
        return client == null || client.getCurrentConnection() == null
                ? null : client.getCurrentConnection().getMediaCmd();
    }

    private void showMain()
    {
        final MediaCmd media = media();
        final MiniPlayerPlugin active = player(media);
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();

        String decoding = ActivePlayerSessionOverrides.resolveDecodingMethod(
                client.properties().getString(PrefStore.Keys.decoding_method,
                        DecodingMethod.DEFAULT_PREFERENCE));
        addVideoRow(panel, holder, "Player",
                playerValue(), new Runnable()
                { @Override public void run() { chooseBackend(media); } });
        addVideoRow(panel, holder, "Decoding",
                DecodingMethod.fromPreference(decoding).displayName(), new Runnable()
                { @Override public void run() { chooseDecoding(); } });
        addVideoRow(panel, holder, "Codec Queueing", codecQueueingValue(), new Runnable()
                { @Override public void run() { chooseCodecQueueing(); } });
        addVideoRow(panel, holder, "Source buffering", bufferPresetValue(), new Runnable()
                { @Override public void run() { chooseBufferPreset(media); } });
        addVideoRow(panel, holder, "Display", refreshValue(active), new Runnable()
                { @Override public void run() { showVideoDisplayMenu(media); } });
        addVideoRow(panel, holder, "DVD playback",
                dvdRepairValue() + " / settle " + refreshSettleValue(), new Runnable()
                { @Override public void run() { showVideoDvdMenu(); } });
        addVideoRow(panel, holder, "Restart video decoder", "Current position", new Runnable()
                {
                    @Override public void run()
                    {
                        reload(media);
                        reopenVideoMenu();
                    }
                });
        addVideoRow(panel, holder, "Reset video overrides", "Device defaults", new Runnable()
                {
                    @Override public void run()
                    {
                        ActivePlayerSessionOverrides.resetVideo();
                        DisplayRefreshController.cancelPendingRefresh();
                        AppUtil.message("Current-session video overrides cleared");
                        reload(media);
                        reopenVideoMenu();
                    }
                });

        LinearLayout actions = captionActions();
        Button close = captionButton("Close");
        close.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view) { holder[0].dismiss(); }
        });
        actions.addView(close);
        panel.addView(actions);
        final String[] sizingRows = {
                "Player  " + playerValue(),
                "Decoding  " + DecodingMethod.fromPreference(decoding).displayName(),
                "Codec Queueing  " + codecQueueingValue(),
                "Source buffering  " + bufferPresetValue(),
                "Display  " + refreshValue(active),
                "DVD playback  " + dvdRepairValue() + " / settle " + refreshSettleValue(),
                "Restart video decoder  Current position",
                "Reset video overrides  Device defaults"
        };
        holder[0] = showSettingsPanel("Video settings", panel, sizingRows);
    }

    private void showVideoDisplayMenu(final MediaCmd media)
    {
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        addVideoRow(panel, holder, "Refresh-rate matching", refreshValue(player(media)),
                new Runnable()
                { @Override public void run() { chooseRefreshRate(media); } });
        panel.addView(captionRow("Aspect ratio remains a quick button in the playback menu.",
                false));
        addVideoBack(panel, holder);
        holder[0] = showSettingsPanel("Display", panel, new String[] {
                "Refresh-rate matching  " + refreshValue(player(media)),
                "Aspect ratio remains a quick button"
        });
        holder[0].setOnCancelListener(new DialogInterface.OnCancelListener()
        { @Override public void onCancel(DialogInterface dialog) { showMain(); } });
    }

    private void showVideoDvdMenu()
    {
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        addVideoRow(panel, holder, "Timestamp repair", dvdRepairValue(), new Runnable()
                { @Override public void run() { chooseDvdRepair(); } });
        addVideoRow(panel, holder, "HDMI settle", refreshSettleValue(), new Runnable()
                { @Override public void run() { chooseRefreshSettle(); } });
        addVideoBack(panel, holder);
        holder[0] = showSettingsPanel("DVD playback", panel, new String[] {
                "Timestamp repair  " + dvdRepairValue(),
                "HDMI settle  " + refreshSettleValue()
        });
        holder[0].setOnCancelListener(new DialogInterface.OnCancelListener()
        { @Override public void onCancel(DialogInterface dialog) { showMain(); } });
    }

    private void addVideoBack(LinearLayout panel, final Dialog[] holder)
    {
        LinearLayout actions = captionActions();
        Button back = captionButton("Back");
        back.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (holder[0] != null) holder[0].dismiss();
                showMain();
            }
        });
        actions.addView(back);
        panel.addView(actions);
    }

    private void addVideoRow(LinearLayout panel, final Dialog[] holder,
            String label, String value, final Runnable action)
    {
        LinearLayout row = audioSettingRow(label, value);
        row.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (holder[0] != null) holder[0].dismiss();
                action.run();
            }
        });
        panel.addView(row);
    }

    private void choosePlaybackStats(final MediaCmd media)
    {
        final boolean wasVisible = ActivePlayerProcessOverlay.isVisible();
        final String[] labels = {
                wasVisible ? "☑ Playback Stats enabled" : "☐ Playback Stats disabled",
                "Show compact until turned off",
                "Show detailed until turned off",
                "Show detailed for 30 seconds",
                "Hide Playback Stats",
                "Export redacted detailed snapshot"
        };
        choose("Playback Stats", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                switch (Integer.parseInt(index))
                {
                    case 0:
                        if (wasVisible)
                        {
                            ActivePlayerProcessOverlay.hide();
                            AppUtil.message("Playback Stats disabled");
                        }
                        else
                        {
                            ActivePlayerProcessOverlay.showPersistent(activity, media, true);
                            AppUtil.message("Detailed Playback Stats enabled");
                        }
                        break;
                    case 1:
                        ActivePlayerProcessOverlay.showPersistent(activity, media, false);
                        AppUtil.message("Compact Playback Stats enabled");
                        break;
                    case 2:
                        ActivePlayerProcessOverlay.showPersistent(activity, media, true);
                        AppUtil.message("Detailed Playback Stats enabled");
                        break;
                    case 3:
                        ActivePlayerProcessOverlay.showForThirtySeconds(activity, media, true);
                        AppUtil.message("Detailed Playback Stats enabled for 30 seconds");
                        break;
                    case 4:
                        ActivePlayerProcessOverlay.hide();
                        AppUtil.message("Playback Stats disabled");
                        break;
                    case 5:
                        String result = ActivePlayerProcessOverlay.exportCurrent(activity);
                        AppUtil.message(result.startsWith("ERROR:")
                                ? result : "Playback Stats exported: " + result);
                        break;
                    default: break;
                }
            }
        });
    }

    private String status(MediaCmd media)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        String backend = player == null ? "No active player" : player.getClass().getSimpleName();
        String source = media != null && media.isDvdSessionPending() ? "DVD Push"
                : client.properties().getStreamingMode();
        return "Now: " + backend + " / " + source;
    }

    private MiniPlayerPlugin player(MediaCmd media)
    {
        return media == null ? null : media.getPlaya();
    }

    private String offsetStatus(String label, MiniPlayerPlugin player, boolean subtitle)
    {
        boolean supported = player != null && (subtitle
                ? player.supportsSubtitleOffset() : player.supportsAudioOffset());
        int value = player == null ? 0 : (subtitle
                ? player.getSubtitleOffsetMillis() : player.getAudioOffsetMillis());
        return label + " sync offset (" + (supported ? signed(value) + " ms" : "unsupported") + ")";
    }

    private String refreshStatus(MiniPlayerPlugin player)
    {
        DisplayRefreshController.Result result = DisplayRefreshController.inspect(activity, player);
        return "Refresh-rate matching (" + String.format(java.util.Locale.US,
                "%.3f fps / %.3f Hz", result.contentFps, result.displayHz) + ")";
    }

    private String refreshSettleStatus()
    {
        int value = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        return "HDMI settle before local DVD video refresh (" + value + " ms)";
    }

    private String codecQueueingValue()
    {
        String value = ActivePlayerSessionOverrides.getCodecMode();
        if (value != null) return value;
        String key = PlayerBackend.EXOPLAYER.preferenceValue().equals(resolvedBackend())
                ? PrefStore.Keys.exo2_codec_mode : PrefStore.Keys.media3_codec_mode;
        return client.properties().getString(key, "auto");
    }

    private String playerValue()
    {
        PlayerBackend backend = PlayerBackend.fromPreference(resolvedBackend());
        if (backend != PlayerBackend.GSYPLAYER) return backend.displayName();
        String configured = ActivePlayerSessionOverrides.resolveGsyEngine(
                client.properties().getString(PrefStore.Keys.gsy_player_engine,
                        GSYPlayerEngine.DEFAULT_PREFERENCE));
        return backend.displayName() + " ("
                + shortGsyEngineName(GSYPlayerEngine.fromPreference(configured)) + ")";
    }

    private static String shortGsyEngineName(GSYPlayerEngine engine)
    {
        if (engine == GSYPlayerEngine.MEDIA3) return "Media3";
        if (engine == GSYPlayerEngine.SYSTEM) return "Android System";
        if (engine == GSYPlayerEngine.LEGACY_EXO) return "Legacy ExoPlayer";
        return "Auto";
    }

    private String dvdRepairValue()
    {
        return ActivePlayerSessionOverrides.resolveDvdTimestampRepair(
                client.properties().getString(
                        PrefStore.Keys.disc_mpeg2_timestamp_repair, "auto"));
    }

    private String bufferPresetValue()
    {
        String value = ActivePlayerSessionOverrides.getBufferPreset();
        if (value == null)
            value = client.properties().getString(PrefStore.Keys.playback_buffer_preset,
                    ActivePlayerSessionOverrides.BUFFER_BALANCED);
        if (ActivePlayerSessionOverrides.BUFFER_LOW_LATENCY.equals(value)) return "Low latency";
        if (ActivePlayerSessionOverrides.BUFFER_RESILIENT.equals(value)) return "Resilient";
        return "Balanced";
    }

    private String refreshValue(MiniPlayerPlugin active)
    {
        DisplayRefreshController.Result result = DisplayRefreshController.inspect(activity, active);
        String policy = ActivePlayerSessionOverrides.resolveRefreshRatePolicy(
                client.properties().getString(PrefStore.Keys.playback_refresh_rate_matching,
                        DisplayRefreshController.OFF));
        return policy + " / " + String.format(Locale.US, "%.3f Hz", result.displayHz);
    }

    private String refreshSettleValue()
    {
        int value = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        return value == 0 ? "Off" : value + " ms";
    }

    private void reopenVideoMenu()
    {
        activity.getWindow().getDecorView().postDelayed(new Runnable()
        {
            @Override public void run() { showMain(); }
        }, 75L);
    }

    private String audioTrackStatus(MiniPlayerPlugin player)
    {
        if (player == null) return "Audio track (no active player)";
        int[] ids = player.getAudioTrackIds();
        String[] labels = player.getAudioTrackLabels();
        int selected = player.getSelectedAudioTrack();
        for (int i = 0; i < ids.length && i < labels.length; i++)
            if (ids[i] == selected) return "Audio track (" + labels[i] + ")";
        return "Audio track (" + (ids.length == 0 ? "unavailable" : "automatic") + ")";
    }

    private String textSubtitleStatus(MiniPlayerPlugin player, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending())
            return "Text subtitle presentation (DVD bitmap subtitles unaffected)";
        if (player == null || !player.supportsTextSubtitlePresentation())
            return "Text subtitle presentation (unsupported)";
        return "Text subtitle presentation (safe "
                + player.getTextSubtitleSafeAreaPercent() + "%, size "
                + player.getTextSubtitleScalePercent() + "%, "
                + player.getTextSubtitleStyle() + ")";
    }

    private void chooseTextSubtitlePresentation(final MediaCmd media)
    {
        final MiniPlayerPlugin active = player(media);
        if (media != null && media.isDvdSessionPending())
        {
            AppUtil.message("DVD SPU subtitles are authored bitmaps; text style was not changed");
            reopenCaptionMenu();
            return;
        }
        if (active == null || !active.supportsTextSubtitlePresentation())
        {
            AppUtil.message("The active backend does not expose local text-subtitle styling");
            reopenCaptionMenu();
            return;
        }
        String[] rows = {
                "Bottom safe area: " + active.getTextSubtitleSafeAreaPercent() + "%",
                "Text size: " + active.getTextSubtitleScalePercent() + "%",
                "Style: " + active.getTextSubtitleStyle()
        };
        chooseCaptionChoice("Text subtitle appearance", rows, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                if (selected == 0) chooseTextSubtitleSafeArea(active);
                else if (selected == 1) chooseTextSubtitleScale(active);
                else chooseTextSubtitleStyle(active);
            }
        });
    }

    private void chooseTextSubtitleSafeArea(final MiniPlayerPlugin active)
    {
        final int[] values = { 8, 12, 18, 25, 32 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "% above bottom";
        chooseCaptionChoice("Text subtitle bottom safe area", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, value,
                        active.getTextSubtitleScalePercent(), active.getTextSubtitleStyle());
            }
        });
    }

    private void chooseTextSubtitleScale(final MiniPlayerPlugin active)
    {
        final int[] values = { 75, 100, 125, 150 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "%";
        chooseCaptionChoice("Text subtitle size", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, active.getTextSubtitleSafeAreaPercent(),
                        value, active.getTextSubtitleStyle());
            }
        });
    }

    private void chooseTextSubtitleStyle(final MiniPlayerPlugin active)
    {
        final String[] labels = { "Android system caption style", "White outline",
                "White on black box" };
        final String[] values = { TextSubtitlePresentation.STYLE_SYSTEM,
                TextSubtitlePresentation.STYLE_OUTLINE,
                TextSubtitlePresentation.STYLE_BLACK_BOX };
        chooseCaptionChoice("Text subtitle style", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                String value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, active.getTextSubtitleSafeAreaPercent(),
                        active.getTextSubtitleScalePercent(), value);
            }
        });
    }

    private void chooseTextPresentationScope(final MiniPlayerPlugin active,
            final int safeArea, final int scale, final String style)
    {
        chooseCaptionChoice("Apply text subtitle appearance",
                new String[] { "This playback session",
                        "Save as this device's default" }, new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        int which = Integer.parseInt(index);
                        ActivePlayerSessionOverrides.setSubtitleSafeAreaPercent(safeArea);
                        ActivePlayerSessionOverrides.setSubtitleTextScalePercent(scale);
                        ActivePlayerSessionOverrides.setSubtitleTextStyle(style);
                        if (which == 1)
                        {
                            client.properties().setInt(
                                    PrefStore.Keys.playback_subtitle_safe_area_percent,
                                    safeArea);
                            client.properties().setInt(
                                    PrefStore.Keys.playback_subtitle_text_scale_percent,
                                    scale);
                            client.properties().setString(
                                    PrefStore.Keys.playback_subtitle_text_style, style);
                        }
                        AppUtil.message(active.setTextSubtitlePresentation(
                                safeArea, scale, style)
                                ? "Text subtitle appearance applied immediately"
                                : "Text subtitle appearance was rejected");
                        reopenCaptionMenu();
                    }
                });
    }

    private String audioOutputStatus(MiniPlayerPlugin player)
    {
        return "Audio output / passthrough (" + (player == null
                ? "no active player" : player.getAudioOutputSummary()) + ")";
    }

    private void chooseAudioTrack(MediaCmd media)
    {
        chooseAudioTrack(media, false);
    }

    private void chooseAudioTrack(MediaCmd media, final boolean returnToAudioMenu)
    {
        final MiniPlayerPlugin active = player(media);
        if (active == null)
        {
            AppUtil.message("Audio-track selection requires an active player");
            return;
        }
        final int[] ids = active.getAudioTrackIds();
        final String[] labels = active.getAudioTrackLabels();
        if (ids.length == 0 || labels.length != ids.length)
        {
            AppUtil.message("This active stream does not expose selectable audio tracks");
            return;
        }
        ValueSetter setter = new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                active.setAudioTrack(ids[selected]);
                AppUtil.message("Audio track applied: " + labels[selected]);
                if (returnToAudioMenu) showAudioMenu();
                else showMain();
            }
        };
        if (returnToAudioMenu)
            chooseAudioChoice("Active audio track", labels, setter, new Runnable()
            {
                @Override public void run() { showAudioMenu(); }
            });
        else
            choose("Active audio track", labels, setter);
    }

    private void chooseAudioPassthrough(MediaCmd media)
    {
        final MiniPlayerPlugin active = player(media);
        if (active == null || !active.supportsAudioPassthroughControl())
        {
            AppUtil.message("Passthrough cannot be changed safely for "
                    + (active == null ? "this inactive player" : active.getAudioOutputSummary())
                    + "; no output mode was changed");
            return;
        }
        choose("Audio passthrough", new String[] { "Off", "On" }, new ValueSetter()
        {
            @Override public void set(String index)
            {
                boolean enabled = Integer.parseInt(index) == 1;
                AppUtil.message(active.setAudioPassthroughEnabled(enabled)
                        ? "Audio passthrough changed for this playback session"
                        : "Audio passthrough change was rejected by the active output");
                showMain();
            }
        });
    }

    private void showAudioMenu()
    {
        final MediaCmd media = media();
        final MiniPlayerPlugin active = player(media);
        final Dialog[] holder = new Dialog[1];
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable menuBackground = new GradientDrawable();
        menuBackground.setColor(0xee1b1b1b);
        menuBackground.setCornerRadius(dp(4));
        menuBackground.setStroke(dp(1), 0xff707070);
        root.setBackground(menuBackground);

        TextView heading = new TextView(activity);
        heading.setText("Audio settings");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(22.0f);
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(20), 0, dp(20), 0);
        heading.setBackgroundColor(Color.TRANSPARENT);
        root.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout settings = new LinearLayout(activity);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setBackgroundColor(0xdd111111);

        LinearLayout output = audioSettingRow("Audio output",
                active == null ? "Unavailable"
                        : active.isAudioPassthroughEnabled()
                        ? "Encoded passthrough" : "Decoded PCM stereo");
        output.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                chooseDecodedAudio(media, active);
            }
        });
        settings.addView(output);

        LinearLayout passthroughOffset = audioSettingRow("Passthrough offset",
                active == null ? "Unavailable"
                        : !active.isAudioPassthroughEnabled() ? "Not used"
                        : !active.supportsPassthroughAudioOffset() ? "Unavailable"
                        : active.isPassthroughAudioOffsetEnabled() ? "On" : "Off");
        passthroughOffset.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                choosePassthroughAudioOffset(active);
            }
        });
        settings.addView(passthroughOffset);

        LinearLayout offset = audioSettingRow("Audio offset",
                active == null || !active.supportsAudioOffset()
                        ? "Unavailable" : audioOffsetValue(active.getAudioOffsetMillis()));
        offset.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                chooseAudioOffset(media, active);
            }
        });
        settings.addView(offset);

        boolean syncTestAvailable = active != null && (active.supportsAudioOffset()
                || active.supportsPassthroughAudioOffset());
        LinearLayout syncTest = audioSettingRow("A/V sync test",
                !syncTestAvailable ? "Unavailable"
                        : active.isAudioPassthroughEnabled()
                        ? "Bouncing ball / encoded" : "Bouncing ball / PCM");
        syncTest.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (active == null || (!active.supportsAudioOffset()
                        && !active.supportsPassthroughAudioOffset()))
                {
                    AppUtil.message("The active backend cannot apply a calibrated audio offset");
                    return;
                }
                holder[0].dismiss();
                Media3AvSyncTestDialog.show(activity, active,
                        new Media3AvSyncTestDialog.Listener()
                        {
                            @Override public void onFinished(int offsetMs)
                            {
                                ActivePlayerSessionOverrides.setAudioOffsetMs(offsetMs);
                                if (active.isAudioPassthroughEnabled()
                                        && offsetMs != 0
                                        && !active.isPassthroughAudioOffsetEnabled())
                                {
                                    ActivePlayerSessionOverrides
                                            .setPassthroughAudioOffsetEnabled(true);
                                    active.setPassthroughAudioOffsetEnabled(true);
                                }
                                active.setAudioOffsetMillis(offsetMs);
                                showAudioMenu();
                            }
                        });
            }
        });
        settings.addView(syncTest);

        LinearLayout track = audioSettingRow("Audio stream", audioTrackValue(active));
        track.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                chooseAudioTrack(media, true);
            }
        });
        settings.addView(track);

        View divider = new View(activity);
        divider.setBackgroundColor(0xff606060);
        settings.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout saveDefault = audioSettingRow("Set as default for all media", "");
        saveDefault.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (active == null)
                {
                    AppUtil.message("No active audio settings to save");
                    return;
                }
                client.properties().setBoolean(PrefStore.Keys.disable_audio_passthrough,
                        !active.isAudioPassthroughEnabled());
                client.properties().setBoolean(
                        PrefStore.Keys.playback_passthrough_audio_offset_enabled,
                        active.isPassthroughAudioOffsetEnabled());
                if (active.supportsAudioOffset())
                    client.properties().setInt(PrefStore.Keys.playback_audio_offset_ms,
                            active.getAudioOffsetMillis());
                AppUtil.message("Audio settings saved as this device's default");
            }
        });
        settings.addView(saveDefault);
        body.addView(settings, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout side = new LinearLayout(activity);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(10), 0, 0, 0);
        Button close = captionButton("Close");
        close.setTextSize(15.0f);
        close.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view) { holder[0].dismiss(); }
        });
        side.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        body.addView(side, new LinearLayout.LayoutParams(dp(124),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(body);

        holder[0] = new Dialog(activity);
        holder[0].requestWindowFeature(Window.FEATURE_NO_TITLE);
        holder[0].setContentView(root);
        holder[0].show();
        Window window = holder[0].getWindow();
        if (window != null)
        {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.35f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(Math.min((int) (metrics.widthPixels * 0.66f), dp(820)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        root.post(new Runnable()
        {
            @Override public void run() { output.requestFocus(); }
        });
    }

    private void choosePassthroughAudioOffset(final MiniPlayerPlugin active)
    {
        if (active == null || !active.isAudioPassthroughEnabled()
                || !active.supportsPassthroughAudioOffset())
        {
            AppUtil.message(active == null ? "No active audio output"
                    : !active.isAudioPassthroughEnabled()
                    ? "Select encoded passthrough before enabling its clock offset"
                    : "Passthrough clock offset is unavailable in this player");
            showAudioMenu();
            return;
        }
        final boolean previous = active.isPassthroughAudioOffsetEnabled();
        final String[] labels = {
                previous ? "Off" : "Off  [active]",
                previous ? "On  [active]" : "On"
        };
        chooseAudioChoice("Passthrough audio offset", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final boolean enabled = Integer.parseInt(index) == 1;
                boolean accepted = active.setPassthroughAudioOffsetEnabled(enabled);
                AppUtil.message(accepted
                        ? (enabled ? "Enabling passthrough clock offset"
                        : "Disabling passthrough clock offset")
                        : "The active output rejected this setting");
                activity.getWindow().getDecorView().postDelayed(new Runnable()
                {
                    @Override public void run() { showAudioMenu(); }
                }, accepted ? 700L : 0L);
            }
        }, new Runnable()
        {
            @Override public void run() { showAudioMenu(); }
        });
    }

    private void chooseDecodedAudio(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (active == null || !active.supportsAudioPassthroughControl())
        {
            AppUtil.message(active == null ? "No active audio output"
                    : "This player cannot enforce decoded PCM: "
                    + active.getAudioOutputSummary());
            showAudioMenu();
            return;
        }
        final boolean previousPassthrough = active.isAudioPassthroughEnabled();
        final String[] labels = {
                previousPassthrough ? "Decoded PCM stereo" : "Decoded PCM stereo  [active]",
                previousPassthrough ? "Encoded passthrough  [active]" : "Encoded passthrough"
        };
        chooseAudioChoice("Audio output mode", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final boolean passthrough = Integer.parseInt(index) == 1;
                ActivePlayerSessionOverrides.setAudioPassthroughEnabled(passthrough);
                boolean accepted = active.setAudioPassthroughEnabled(passthrough);
                if (!accepted)
                    ActivePlayerSessionOverrides.setAudioPassthroughEnabled(previousPassthrough);
                AppUtil.message(accepted
                        ? (passthrough
                        ? "Switching this program to encoded passthrough"
                        : "Switching this program to decoded PCM stereo")
                        : "The active output rejected this mode; nothing changed");
                activity.getWindow().getDecorView().postDelayed(new Runnable()
                {
                    @Override public void run() { showAudioMenu(); }
                }, accepted ? 700L : 0L);
            }
        }, new Runnable()
        {
            @Override public void run() { showAudioMenu(); }
        });
    }

    private LinearLayout audioSettingRow(String label, String value)
    {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setClickable(true);
        TypedValue selectable = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, selectable, true)
                && selectable.resourceId != 0)
            row.setBackgroundResource(selectable.resourceId);

        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17.0f);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                name, 11, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1.6f));
        TextView current = new TextView(activity);
        current.setText(value);
        current.setTextColor(Color.WHITE);
        current.setTextSize(16.0f);
        current.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        current.setIncludeFontPadding(false);
        current.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                current, 11, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        currentParams.leftMargin = dp(24);
        row.addView(current, currentParams);
        return row;
    }

    private String audioTrackValue(MiniPlayerPlugin active)
    {
        String status = audioTrackStatus(active);
        int open = status.indexOf('(');
        return open >= 0 && status.endsWith(")")
                ? status.substring(open + 1, status.length() - 1) : status;
    }

    private String audioOffsetValue(int valueMs)
    {
        return String.format(Locale.US, "%+.3f s", valueMs / 1000.0f)
                .replace("+0.000", "0.000");
    }

    private void chooseAudioOffset(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (active == null || !active.supportsAudioOffset())
        {
            AppUtil.message("Audio offset requires decoded PCM, or enabled passthrough "
                    + "clock offset on Media3 or legacy ExoPlayer");
            showAudioMenu();
            return;
        }
        showAudioOffsetSlider(active);
    }

    private void showAudioOffsetSlider(final MiniPlayerPlugin active)
    {
        final int minMs = -4_000;
        final int maxMs = 4_000;
        final int stepMs = 25;
        int currentMs = Math.max(minMs, Math.min(maxMs, active.getAudioOffsetMillis()));
        currentMs = Math.round(currentMs / (float) stepMs) * stepMs;
        final int[] valueMs = { currentMs };
        final boolean[] finished = { false };

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setFocusableInTouchMode(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xe61b1b1b);
        background.setCornerRadius(dp(3));
        background.setStroke(dp(1), 0xff505050);
        root.setBackground(background);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Audio offset");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20.0f);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        final TextView status = new TextView(activity);
        status.setText(audioOffsetSliderLabel(currentMs));
        status.setTextColor(Color.WHITE);
        status.setTextSize(18.0f);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setIncludeFontPadding(false);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        root.addView(header);

        LinearLayout sliderRow = new LinearLayout(activity);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = audioOffsetArrow("\u2039");
        TextView right = audioOffsetArrow("\u203a");
        final SeekBar slider = new SeekBar(activity);
        slider.setMax((maxMs - minMs) / stepMs);
        slider.setProgress((currentMs - minMs) / stepMs);
        slider.setFocusable(false);
        sliderRow.addView(left, new LinearLayout.LayoutParams(dp(28), dp(38)));
        sliderRow.addView(slider, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        sliderRow.addView(right, new LinearLayout.LayoutParams(dp(28), dp(38)));
        root.addView(sliderRow);

        TextView help = new TextView(activity);
        help.setText("Left/Right: adjust 0.025 s     Back: save");
        help.setTextColor(Color.LTGRAY);
        help.setTextSize(12.0f);
        help.setGravity(Gravity.CENTER);
        help.setIncludeFontPadding(false);
        root.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser)
            {
                int requested = minMs + progress * stepMs;
                if (requested == valueMs[0]) return;
                valueMs[0] = requested;
                ActivePlayerSessionOverrides.setAudioOffsetMs(requested);
                if (!active.setAudioOffsetMillis(requested))
                {
                    AppUtil.message("Audio offset was not applied");
                    return;
                }
                status.setText(audioOffsetSliderLabel(requested));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
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
                if (keyCode == KeyEvent.KEYCODE_BACK)
                {
                    if (event.getAction() == KeyEvent.ACTION_UP && !finished[0])
                    {
                        finished[0] = true;
                        dialog.dismiss();
                        showAudioMenu();
                    }
                    return true;
                }
                return false;
            }
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null)
        {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.15f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = dp(12);
            window.setAttributes(params);
            window.setLayout(Math.min((int) (metrics.widthPixels * 0.44f), dp(760)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        root.requestFocus();
    }

    private TextView audioOffsetArrow(String label)
    {
        TextView arrow = new TextView(activity);
        arrow.setText(label);
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(34.0f);
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        return arrow;
    }

    private String audioOffsetSliderLabel(int valueMs)
    {
        if (valueMs == 0) return "0.000 s";
        return (valueMs > 0 ? "Later by: " : "Earlier by: ")
                + String.format(Locale.US, "%.3f s", Math.abs(valueMs) / 1000.0f);
    }

    private void chooseAudioChoice(String title, final String[] labels,
            final ValueSetter setter, final Runnable backAction)
    {
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        for (int i = 0; i < labels.length; i++)
        {
            final int selection = i;
            TextView row = captionRow(labels[i], true);
            row.setOnClickListener(new View.OnClickListener()
            {
                @Override public void onClick(View view)
                {
                    holder[0].dismiss();
                    setter.set(Integer.toString(selection));
                }
            });
            panel.addView(row);
        }
        LinearLayout actions = captionActions();
        Button back = captionButton("Back");
        back.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                backAction.run();
            }
        });
        actions.addView(back);
        panel.addView(actions);
        holder[0] = showCaptionPanel(title, panel, labels);
        holder[0].setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override public void onCancel(DialogInterface dialog) { backAction.run(); }
        });
    }

    private void chooseVideoChoice(String title, String[] labels, ValueSetter setter)
    {
        chooseAudioChoice(title, labels, setter, new Runnable()
        {
            @Override public void run() { showMain(); }
        });
    }

    private void chooseRefreshRate(final MediaCmd media)
    {
        final String[] labels = { "Off", "Seamless only", "Always (current resolution)" };
        final String[] values = { DisplayRefreshController.OFF,
                DisplayRefreshController.SEAMLESS, DisplayRefreshController.ALWAYS };
        chooseVideoChoice("Refresh-rate matching", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final String value = values[Integer.parseInt(index)];
                chooseScope("Refresh-rate matching", PrefStore.Keys.playback_refresh_rate_matching,
                        value, new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setRefreshRatePolicy(selectedValue);
                                DisplayRefreshController.Result result = DisplayRefreshController.apply(
                                        activity, player(media), selectedValue);
                                int settleMs = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                                        client.properties().getInt(
                                                PrefStore.Keys.playback_refresh_settle_ms, 0));
                                boolean scheduled = result.applied && result.modeChanged
                                        && DisplayRefreshController.scheduleLocalDvdOutputRefresh(
                                                activity, media, settleMs);
                                AppUtil.message((result.applied ? "Applied: " : "Not applied: ")
                                        + result.summary() + (scheduled
                                        ? "; local DVD video refresh in " + settleMs + " ms" : ""));
                            }
                        }, false);
            }
        });
    }

    private void chooseRefreshSettle()
    {
        final int[] values = { 0, 250, 500, 1_000, 1_500 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++)
            labels[i] = values[i] == 0 ? "Off" : values[i] + " ms";
        chooseVideoChoice("HDMI settle before local DVD video refresh", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final int value = values[Integer.parseInt(index)];
                chooseScope("HDMI settle", PrefStore.Keys.playback_refresh_settle_ms,
                        Integer.toString(value), new ValueSetter()
                        {
                            @Override public void set(String ignored)
                            {
                                ActivePlayerSessionOverrides.setRefreshSettleMs(value);
                                AppUtil.message("HDMI settle applies to the next active DVD display-mode change");
                            }
                        }, false);
            }
        });
    }

    private void chooseBackend(final MediaCmd media)
    {
        final PlayerBackend[] values = new PlayerBackend[] {
                PlayerBackend.MEDIA3, PlayerBackend.EXOPLAYER,
                PlayerBackend.IJKPLAYER, PlayerBackend.GSYPLAYER,
                PlayerBackend.GSYPLAYER, PlayerBackend.GSYPLAYER,
                PlayerBackend.GSYPLAYER };
        final GSYPlayerEngine[] gsyEngines = new GSYPlayerEngine[] {
                null, null, null, GSYPlayerEngine.AUTO, GSYPlayerEngine.MEDIA3,
                GSYPlayerEngine.SYSTEM, GSYPlayerEngine.LEGACY_EXO };
        final String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++)
            labels[i] = gsyEngines[i] == null ? values[i].displayName()
                    : values[i].displayName() + " (" + shortGsyEngineName(gsyEngines[i]) + ")";
        chooseVideoChoice("Player", labels, new ValueSetter()
        {
            @Override public void set(String value)
            {
                final int selectedIndex = Integer.parseInt(value);
                final PlayerBackend selected = values[selectedIndex];
                final GSYPlayerEngine gsyEngine = gsyEngines[selectedIndex];
                final boolean pinnedDvd = media != null && media.isDvdSessionPending()
                        && selected != PlayerBackend.MEDIA3;
                chooseVideoChoice("Player: " + labels[selectedIndex],
                        new String[] { "This playback session",
                                "Save as this device's default" }, new ValueSetter()
                {
                    @Override public void set(String scope)
                    {
                        ActivePlayerSessionOverrides.setBackend(selected.preferenceValue());
                        if (gsyEngine != null)
                            ActivePlayerSessionOverrides.setGsyEngine(
                                    gsyEngine.preferenceValue());
                        if (Integer.parseInt(scope) == 1)
                        {
                            client.properties().setString(PrefStore.Keys.default_player,
                                    selected.preferenceValue());
                            if (gsyEngine != null)
                                client.properties().setString(PrefStore.Keys.gsy_player_engine,
                                        gsyEngine.preferenceValue());
                        }
                        if (pinnedDvd)
                            AppUtil.message("Native DVD remains on Media3; this player applies to the next non-DVD video");
                        else
                        {
                            reload(media());
                        }
                        reopenVideoMenu();
                    }
                });
            }
        });
    }

    private void chooseDecoding()
    {
        final DecodingMethod[] values = DecodingMethod.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i].displayName();
        chooseVideoChoice("Video decoding policy", labels, new ValueSetter()
        {
            @Override public void set(String value)
            {
                DecodingMethod selected = values[Integer.parseInt(value)];
                chooseScope("Video decoding policy", PrefStore.Keys.decoding_method,
                        selected.preferenceValue(), new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            { ActivePlayerSessionOverrides.setDecodingMethod(selectedValue); }
                        }, true);
            }
        });
    }

    private void chooseCodecQueueing()
    {
        final String[] values = { "sync", "auto", "async" };
        chooseValues("MediaCodec queueing", values, new ValueSetter()
        {
            @Override public void set(String value)
            {
                String backend = resolvedBackend();
                String key = PlayerBackend.EXOPLAYER.preferenceValue().equals(backend)
                        ? PrefStore.Keys.exo2_codec_mode : PrefStore.Keys.media3_codec_mode;
                chooseScope("MediaCodec queueing", key, value, new ValueSetter()
                {
                    @Override public void set(String selectedValue)
                    { ActivePlayerSessionOverrides.setCodecMode(selectedValue); }
                }, true);
            }
        });
    }

    private void chooseDvdRepair()
    {
        final String[] values = { "auto", "on", "off" };
        chooseValues("DVD MPEG-2 timestamp repair", values, new ValueSetter()
        {
            @Override public void set(String value)
            {
                chooseScope("DVD timestamp repair", PrefStore.Keys.disc_mpeg2_timestamp_repair,
                        value, new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            { ActivePlayerSessionOverrides.setDvdTimestampRepair(selectedValue); }
                        }, true);
            }
        });
    }

    private void chooseBufferPreset(final MediaCmd media)
    {
        final String[] labels = { "Low latency", "Balanced", "Resilient" };
        final String[] values = { ActivePlayerSessionOverrides.BUFFER_LOW_LATENCY,
                ActivePlayerSessionOverrides.BUFFER_BALANCED,
                ActivePlayerSessionOverrides.BUFFER_RESILIENT };
        chooseVideoChoice("Source buffering preset", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                chooseScope("Source buffering preset", PrefStore.Keys.playback_buffer_preset,
                        values[Integer.parseInt(index)], new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setBufferPreset(selectedValue);
                                if (media != null && media.isDvdSessionPending())
                                    AppUtil.message("Saved for Pull/SMB; native DVD uses its bounded interactive buffer");
                            }
                        }, media == null || !media.isDvdSessionPending());
            }
        });
    }

    private void chooseCaptions(final MediaCmd media)
    {
        if (media == null)
        {
            AppUtil.message("Caption control requires an active SageTV connection");
            return;
        }
        final MiniPlayerPlugin active = player(media);
        final List<String> rowList = new ArrayList<String>();
        rowList.add("Available broadcast CC");
        addAvailableCaptionRows(rowList, media, active);
        final int readOnlyRows = rowList.size();
        rowList.add("Broadcast CC: " + captionModeLabel(media.getLegacyServerCaptionMode()));
        rowList.add("CC1 Type: " + captionTypeLabel(captionType(1)));
        rowList.add("CC1 Language: " + captionLanguageLabel(captionLanguage(1)));
        rowList.add("CC2 Type: " + captionTypeLabel(captionType(2)));
        rowList.add("CC2 Language: " + captionLanguageLabel(captionLanguage(2)));
        rowList.add("Subtitle stream: " + subtitleTrackValue(active, media));
        rowList.add("Subtitle appearance: " + subtitleAppearanceValue(active, media));
        final String[] rows = rowList.toArray(new String[rowList.size()]);
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        for (int i = 0; i < rows.length; i++)
        {
            final int setting = i - readOnlyRows;
            TextView row = captionRow(rows[i], i >= readOnlyRows);
            if (i >= readOnlyRows)
                row.setOnClickListener(new View.OnClickListener()
                {
                    @Override public void onClick(View view)
                    {
                        holder[0].dismiss();
                        if (setting == 0) chooseCaptionMode(media, active);
                        else if (setting == 1) chooseCaptionType(media, active, 1);
                        else if (setting == 2) chooseCaptionLanguage(media, active, 1);
                        else if (setting == 3) chooseCaptionType(media, active, 2);
                        else if (setting == 4) chooseCaptionLanguage(media, active, 2);
                        else if (setting == 5) chooseSubtitleTrack(media, active);
                        else if (setting == 6) chooseTextSubtitlePresentation(media);
                    }
                });
            panel.addView(row);
        }
        LinearLayout actions = captionActions();
        Button refresh = captionButton("Refresh");
        refresh.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                chooseCaptions(media());
            }
        });
        Button cancel = captionButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view) { holder[0].dismiss(); }
        });
        actions.addView(refresh);
        actions.addView(cancel);
        panel.addView(actions);
        holder[0] = showCaptionPanel("Subtitles / broadcast CC", panel, rows);
    }

    private String subtitleTrackValue(MiniPlayerPlugin active, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending()) return "DVD/STV controlled";
        if (active == null) return "Unavailable";
        int selected = active.getSelectedSubtitleTrack();
        if (selected == MiniPlayerPlugin.DISABLE_TRACK) return "Off";
        for (SubtitleTrack track : active.getSubtitleTracks())
            if (track != null && track.getIndex() == selected
                    && !CaptionSlotPolicy.isBroadcastCaption(track))
                return compactSubtitleTrackLabel(track);
        return "Broadcast CC controls active";
    }

    private String subtitleAppearanceValue(MiniPlayerPlugin active, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending()) return "DVD bitmap";
        if (active == null || !active.supportsTextSubtitlePresentation()) return "Unavailable";
        return "safe " + active.getTextSubtitleSafeAreaPercent() + "% / size "
                + active.getTextSubtitleScalePercent() + "%";
    }

    private void chooseSubtitleTrack(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (media != null && media.isDvdSessionPending())
        {
            AppUtil.message("DVD subtitle streams are selected by the SageTV/DVD subtitle menu");
            reopenCaptionMenu();
            return;
        }
        if (active == null)
        {
            AppUtil.message("Subtitle selection requires an active player");
            reopenCaptionMenu();
            return;
        }
        final List<Integer> ids = new ArrayList<Integer>();
        final List<String> labels = new ArrayList<String>();
        ids.add(MiniPlayerPlugin.DISABLE_TRACK);
        labels.add(active.getSelectedSubtitleTrack() == MiniPlayerPlugin.DISABLE_TRACK
                ? "Off  [active]" : "Off");
        for (SubtitleTrack track : active.getSubtitleTracks())
        {
            if (track == null || CaptionSlotPolicy.isBroadcastCaption(track)) continue;
            ids.add(track.getIndex());
            labels.add(compactSubtitleTrackLabel(track)
                    + (track.getIndex() == active.getSelectedSubtitleTrack()
                    ? "  [active]" : ""));
        }
        chooseCaptionChoice("Subtitle stream (SRT/PGS/DVD)",
                labels.toArray(new String[labels.size()]), new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        int id = ids.get(Integer.parseInt(index));
                        active.setSubtitleTrack(id);
                        AppUtil.message(id == MiniPlayerPlugin.DISABLE_TRACK
                                ? "Subtitles off" : "Subtitle stream selected");
                        reopenCaptionMenu();
                    }
                });
    }

    private static String compactSubtitleTrackLabel(SubtitleTrack track)
    {
        String language = CaptionSlotPolicy.reliableLanguage(track);
        String prefix = language.isEmpty() ? "Unknown" : captionLanguageLabel(language);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.SUBRIP) return prefix + " SRT";
        if (codec == SubtitleCodec.PGS) return prefix + " PGS";
        return prefix + " " + (codec == null ? "subtitle" : codec.getName());
    }

    private void chooseCaptionMode(final MediaCmd media, final MiniPlayerPlugin active)
    {
        final String[] labels = { "OFF", "CC1", "CC2", "STV", "DVB" };
        final String[] values = { "off", "cc1", "cc2", "stv", "dvb" };
        chooseCaptionChoice("Broadcast captions (CC)", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                String value = values[Integer.parseInt(index)];
                media.setLegacyServerCaptionMode(value);
                if ("off".equals(value) && active != null)
                    active.setSubtitleTrack(MiniPlayerPlugin.DISABLE_TRACK);
                if ("dvb".equals(value) && active != null
                        && !active.applyDvbCaptionTrack())
                    AppUtil.message("No DVB bitmap subtitle service is available in this video");
                if ("stv".equals(value) && !media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_legacy_stv_unavailable));
                else if ("stv".equals(value) && media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_stv_authoritative));
                else
                    AppUtil.message("Broadcast CC: " + captionModeLabel(value));
                reopenCaptionMenu();
            }
        });
    }

    private void chooseCaptionType(final MediaCmd media, final MiniPlayerPlugin active,
            final int channel)
    {
        final List<String> values = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        addCaptionType(values, labels, CaptionSlotPolicy.TYPE_AUTO);
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        // CC1/CC2 are callback/text-caption slots. DVB bitmap selection is
        // deliberately available only through the top-level DVB mode.
        String[] preferredTypes = {
                CaptionSlotPolicy.TYPE_CEA708,
                CaptionSlotPolicy.TYPE_CEA608,
                CaptionSlotPolicy.TYPE_TELETEXT
        };
        for (String preferredType : preferredTypes)
        {
            SubtitleCodec preferredCodec = CaptionSlotPolicy.codecForType(preferredType);
            for (SubtitleTrack track : tracks)
            {
                if (track != null && track.getSubtitleCodec() == preferredCodec)
                {
                    addCaptionType(values, labels, preferredType);
                    break;
                }
            }
        }
        String current = captionType(channel);
        addCaptionType(values, labels, current);
        chooseCaptionChoice("CC" + channel + " Type",
                labels.toArray(new String[labels.size()]),
                new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        client.properties().setString(captionTypeKey(channel),
                                values.get(Integer.parseInt(index)));
                        reapplyCaptionSlot(media, channel);
                        reopenCaptionMenu();
                    }
                });
    }

    private static void addCaptionType(List<String> values, List<String> labels, String type)
    {
        String normalized = CaptionSlotPolicy.normalizeType(type);
        if (values.contains(normalized)) return;
        values.add(normalized);
        labels.add(captionTypeLabel(normalized));
    }

    private void chooseCaptionLanguage(final MediaCmd media, MiniPlayerPlugin active,
            final int channel)
    {
        final List<String> values = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        values.add("");
        labels.add("Auto");
        SubtitleCodec requestedCodec = CaptionSlotPolicy.codecForType(captionType(channel));
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        List<String> discoveredLanguages = new ArrayList<String>();
        for (SubtitleTrack track : tracks)
        {
            if (!CaptionSlotPolicy.isBroadcastCaption(track)
                    || track.getSubtitleCodec() == SubtitleCodec.DVB
                    || (requestedCodec != null && requestedCodec != track.getSubtitleCodec()))
                continue;
            String language = CaptionSlotPolicy.reliableLanguage(track);
            if (language.isEmpty() || discoveredLanguages.contains(language)) continue;
            discoveredLanguages.add(language);
        }
        Collections.sort(discoveredLanguages, new Comparator<String>()
        {
            @Override public int compare(String left, String right)
            {
                boolean leftEnglish = isEnglishLanguage(left);
                boolean rightEnglish = isEnglishLanguage(right);
                if (leftEnglish != rightEnglish) return leftEnglish ? -1 : 1;
                return captionLanguageLabel(left).compareToIgnoreCase(captionLanguageLabel(right));
            }
        });
        for (String language : discoveredLanguages)
        {
            values.add(language);
            labels.add(captionLanguageLabel(language));
        }
        String current = TrackPreferencePolicy.normalizeLanguage(captionLanguage(channel));
        if (!current.isEmpty() && !values.contains(current))
        {
            values.add(current);
            labels.add(captionLanguageLabel(current) + " (not in current video)");
        }
        chooseCaptionChoice("CC" + channel + " Language",
                labels.toArray(new String[labels.size()]), new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        client.properties().setString(captionLanguageKey(channel),
                                values.get(Integer.parseInt(index)));
                        reapplyCaptionSlot(media, channel);
                        reopenCaptionMenu();
                    }
                });
    }

    private LinearLayout captionPanel()
    {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), 0, dp(20), dp(6));
        return panel;
    }

    private TextView captionRow(String label, boolean selectable)
    {
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextColor(selectable ? Color.WHITE : Color.LTGRAY);
        text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        text.setIncludeFontPadding(false);
        text.setHorizontallyScrolling(false);
        text.setAlpha(selectable ? 1.0f : 0.68f);
        text.setTextSize(selectable ? 16.0f : 13.5f);
        text.setSingleLine(selectable);
        text.setMaxLines(selectable ? 1 : 2);
        text.setPadding(dp(8), selectable ? dp(2) : 0, dp(8),
                selectable ? dp(2) : 0);
        text.setFocusable(selectable);
        text.setClickable(selectable);
        if (selectable)
        {
            TypedValue value = new TypedValue();
            if (activity.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, value, true)
                    && value.resourceId != 0)
                text.setBackgroundResource(value.resourceId);
        }
        text.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                selectable ? dp(28) : LinearLayout.LayoutParams.WRAP_CONTENT));
        return text;
    }

    private LinearLayout captionActions()
    {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(6), 0, 0);
        actions.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return actions;
    }

    private Button captionButton(String label)
    {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void chooseCaptionChoice(String title, final String[] labels,
            final ValueSetter setter)
    {
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        for (int i = 0; i < labels.length; i++)
        {
            final int selection = i;
            TextView row = captionRow(labels[i], true);
            row.setOnClickListener(new View.OnClickListener()
            {
                @Override public void onClick(View view)
                {
                    holder[0].dismiss();
                    setter.set(Integer.toString(selection));
                }
            });
            panel.addView(row);
        }
        LinearLayout actions = captionActions();
        Button back = captionButton("Back");
        back.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                holder[0].dismiss();
                reopenCaptionMenu();
            }
        });
        actions.addView(back);
        panel.addView(actions);
        holder[0] = showCaptionPanel(title, panel, labels);
        holder[0].setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override public void onCancel(DialogInterface dialog) { reopenCaptionMenu(); }
        });
    }

    private Dialog showCaptionPanel(String title, LinearLayout panel, String[] rows)
    {
        return showCompactPanel(title, panel, rows, false);
    }

    private Dialog showSettingsPanel(String title, LinearLayout panel, String[] rows)
    {
        return showCompactPanel(title, panel, rows, true);
    }

    private Dialog showCompactPanel(String title, LinearLayout panel, String[] rows,
            boolean leftHeading)
    {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xee1b1b1b);
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), 0xff707070);
        root.setBackground(background);

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(21.0f);
        heading.setGravity(leftHeading
                ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        if (leftHeading) heading.setPadding(dp(20), 0, dp(20), 0);
        heading.setIncludeFontPadding(false);
        heading.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        root.addView(heading);
        root.addView(panel);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.show();
        compactPanelDialog(dialog, rows, leftHeading);
        return dialog;
    }

    private void compactPanelDialog(Dialog dialog, String[] rows, boolean settingsPanel)
    {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.35f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(settingsPanel ? settingsDialogWidth(rows) : captionDialogWidth(rows),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private int settingsDialogWidth(String[] rows)
    {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int desired = Math.max(captionDialogWidth(rows), dp(520));
        return Math.min(desired,
                Math.min((int) (metrics.widthPixels * 0.54f), dp(900)));
    }

    private int captionDialogWidth(String[] rows)
    {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int longest = 0;
        if (rows != null)
            for (String row : rows)
                if (row != null) longest = Math.max(longest, row.length());
        int desired = dp(Math.max(300, 64 + Math.min(longest, 64) * 9));
        return Math.min(desired,
                Math.min((int) (metrics.widthPixels * 0.42f), dp(700)));
    }

    private int dp(int value)
    {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void reopenCaptionMenu()
    {
        activity.getWindow().getDecorView().postDelayed(new Runnable()
        {
            @Override public void run() { chooseCaptions(media()); }
        }, 75L);
    }

    private void reapplyCaptionSlot(MediaCmd media, int channel)
    {
        if (media != null && ("cc" + channel).equals(media.getLegacyServerCaptionMode()))
            media.setLegacyServerCaptionMode("cc" + channel);
        else if (media != null && "dvb".equals(media.getLegacyServerCaptionMode()))
            media.setLegacyServerCaptionMode("dvb");
        else if (media != null && media.hasSageTvClosedCaptionState())
            // Keep STV as the authority for enabled/disabled state, but
            // immediately re-resolve the newly edited CEA/Teletext CC1/CC2
            // mapping in the current playback session.
            media.setSageTvClosedCaptionState(media.getSageTvClosedCaptionState());
        AppUtil.message("CC" + channel + " mapping saved");
    }

    private void addAvailableCaptionRows(List<String> rows, MediaCmd media,
            MiniPlayerPlugin active)
    {
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        String cc1 = captionSlotDetected(active, 1);
        String cc2 = captionSlotDetected(active, 2);
        int count = 0;
        for (SubtitleTrack track : tracks)
        {
            if (!CaptionSlotPolicy.isBroadcastCaption(track)) continue;
            String label = compactCaptionTrackLabel(track);
            StringBuilder row = new StringBuilder(label);
            if (track.getSubtitleCodec() == SubtitleCodec.DVB)
            {
                row.append("dvb".equals(media == null ? "" : media.getLegacyServerCaptionMode())
                        ? " (DVB selected)" : " (select DVB)");
            }
            else
            {
                if (label.equals(cc1)) row.append(" (CC1)");
                if (label.equals(cc2)) row.append(" (CC2)");
            }
            rows.add(row.toString());
            count++;
        }
        if (count == 0)
            rows.add(active == null ? "None - start playback and Refresh"
                    : "None detected yet - select Refresh after playback starts");
    }

    /**
     * Exclude extractor-declared CEA compatibility tracks until valid CEA data
     * has actually been observed. This prevents DVB/Teletext-only UK streams
     * from being mislabeled as CEA-608/708 in the current-video inventory.
     */
    private SubtitleTrack[] verifiedCaptionTracks(MiniPlayerPlugin active)
    {
        if (active == null) return new SubtitleTrack[0];
        SubtitleTrack[] tracks = active.getSubtitleTracks();
        if (active.hasObservedCeaCaptionData()) return tracks;
        List<SubtitleTrack> verified = new ArrayList<SubtitleTrack>();
        for (SubtitleTrack track : tracks)
        {
            SubtitleCodec codec = track == null ? null : track.getSubtitleCodec();
            if (codec != SubtitleCodec.CEA608 && codec != SubtitleCodec.CEA708)
                verified.add(track);
        }
        return verified.toArray(new SubtitleTrack[verified.size()]);
    }

    private String captionSlotDetected(MiniPlayerPlugin active, int channel)
    {
        if (active == null) return "Not detected";
        SubtitleTrack track = CaptionSlotPolicy.findTrack(verifiedCaptionTracks(active), channel,
                captionType(channel), captionLanguage(channel), true, false);
        return track == null ? "No matching service" : compactCaptionTrackLabel(track);
    }

    private static String compactCaptionTrackLabel(SubtitleTrack track)
    {
        String language = CaptionSlotPolicy.reliableLanguage(track);
        String suffix = language.isEmpty() ? "" : " " + captionLanguageLabel(language);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.TELETEXT)
            return track.getLabel().isEmpty() ? "Teletext" + suffix
                    : track.getLabel() + suffix;
        if (codec == SubtitleCodec.DVB) return "DVB" + suffix;
        if (codec == SubtitleCodec.CEA608)
            return "CEA-608" + (track.getAccessibilityChannel() > 0
                    ? " CC" + track.getAccessibilityChannel() : "");
        if (codec == SubtitleCodec.CEA708)
            return "CEA-708" + (track.getAccessibilityChannel() > 0
                    ? " Service " + track.getAccessibilityChannel() : "") + suffix;
        return captionTrackLabel(track);
    }

    private String captionType(int channel)
    {
        String value = CaptionSlotPolicy.normalizeType(client.properties().getString(
                captionTypeKey(channel), CaptionSlotPolicy.TYPE_AUTO));
        // Migrate the short-lived CC-slot DVB mapping in place. DVB is now an
        // explicit top-level mode and is never presented as CC1 or CC2.
        if (CaptionSlotPolicy.TYPE_DVB.equals(value))
        {
            client.properties().setString(captionTypeKey(channel),
                    CaptionSlotPolicy.TYPE_AUTO);
            return CaptionSlotPolicy.TYPE_AUTO;
        }
        return value;
    }

    private String captionLanguage(int channel)
    {
        return client.properties().getString(captionLanguageKey(channel), "");
    }

    private static String captionTypeKey(int channel)
    {
        return channel == 1 ? PrefStore.Keys.caption_cc1_type : PrefStore.Keys.caption_cc2_type;
    }

    private static String captionLanguageKey(int channel)
    {
        return channel == 1 ? PrefStore.Keys.caption_cc1_language
                : PrefStore.Keys.caption_cc2_language;
    }

    private static String captionModeLabel(String mode)
    {
        if ("off".equals(mode)) return "OFF";
        if ("cc1".equals(mode)) return "CC1";
        if ("cc2".equals(mode)) return "CC2";
        if ("dvb".equals(mode)) return "DVB";
        return "STV";
    }

    private static String captionTypeLabel(String type)
    {
        String value = CaptionSlotPolicy.normalizeType(type);
        if (CaptionSlotPolicy.TYPE_TELETEXT.equals(value)) return "Teletext";
        if (CaptionSlotPolicy.TYPE_DVB.equals(value)) return "DVB";
        if (CaptionSlotPolicy.TYPE_CEA608.equals(value)) return "CEA-608";
        if (CaptionSlotPolicy.TYPE_CEA708.equals(value)) return "CEA-708";
        return "Auto";
    }

    private static String typeForCodec(SubtitleCodec codec)
    {
        if (codec == SubtitleCodec.TELETEXT) return CaptionSlotPolicy.TYPE_TELETEXT;
        if (codec == SubtitleCodec.DVB) return CaptionSlotPolicy.TYPE_DVB;
        if (codec == SubtitleCodec.CEA608) return CaptionSlotPolicy.TYPE_CEA608;
        if (codec == SubtitleCodec.CEA708) return CaptionSlotPolicy.TYPE_CEA708;
        return "";
    }

    private static String captionLanguageLabel(String language)
    {
        String normalized = TrackPreferencePolicy.normalizeLanguage(language);
        if (normalized.isEmpty()) return "Auto";
        Locale locale = Locale.forLanguageTag(normalized);
        String display = locale.getDisplayLanguage();
        if (display == null || display.isEmpty()) display = normalized;
        return display + " (" + normalized + ")";
    }

    private static boolean isEnglishLanguage(String language)
    {
        String normalized = TrackPreferencePolicy.normalizeLanguage(language);
        return "en".equals(normalized) || "eng".equals(normalized)
                || normalized.startsWith("en-") || normalized.startsWith("eng-");
    }

    private static String captionStatus(MediaCmd media, MiniPlayerPlugin active)
    {
        if (media == null) return "no active playback";
        String mode = media.getLegacyServerCaptionMode();
        if ("cc1".equals(mode)) return "SageTV CC1";
        if ("cc2".equals(mode)) return "SageTV CC2";
        if ("dvb".equals(mode)) return "DVB bitmap broadcast CC";
        if ("off".equals(mode))
        {
            int selected = active == null ? MiniPlayerPlugin.DISABLE_TRACK
                    : active.getSelectedSubtitleTrack();
            if (selected != MiniPlayerPlugin.DISABLE_TRACK)
                return "device track " + selected;
            return "Off";
        }
        return media.hasSageTvClosedCaptionState()
                ? "following SageTV STV" : "following SageTV when available";
    }

    private static String captionTrackLabel(SubtitleTrack track)
    {
        String reliableLanguage = CaptionSlotPolicy.reliableLanguage(track);
        String language = reliableLanguage.isEmpty() ? "Unknown language"
                : captionLanguageLabel(reliableLanguage);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.TELETEXT)
            return "Teletext - " + language + " - " + track.getLabel();
        if (codec == SubtitleCodec.DVB)
            return "DVB bitmap - " + language;
        if (codec == SubtitleCodec.PGS)
            return "PGS bitmap - " + language;
        if (codec == SubtitleCodec.SUBRIP)
            return "Text subtitle - " + language;
        if (codec == SubtitleCodec.CEA608)
            return "CEA-608 - " + language
                    + (track.getAccessibilityChannel() > 0
                            ? " - CC" + track.getAccessibilityChannel() : "");
        if (codec == SubtitleCodec.CEA708)
            return "CEA-708 - " + language
                    + (track.getAccessibilityChannel() > 0
                            ? " - Service " + track.getAccessibilityChannel() : "");
        return track.toString();
    }

    private void chooseScope(final String title, final String preferenceKey,
            final String value, final ValueSetter sessionSetter,
            final boolean reloadRequired)
    {
        chooseVideoChoice(title + ": " + value,
                new String[] { "This playback session", "Save as this device's default" },
                new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        sessionSetter.set(value);
                        if (Integer.parseInt(index) == 1)
                            client.properties().setString(preferenceKey, value);
                        if (reloadRequired) reload(media());
                        reopenVideoMenu();
                    }
                });
    }

    private void reload(MediaCmd media)
    {
        if (media != null && media.requestControlledPlayerReload())
            AppUtil.message("Refreshing local DVD video without a server seek");
        else
            AppUtil.message("Saved. This transport cannot reload safely in place; it applies to the next video");
    }

    private void unsupportedOffset(String kind)
    {
        AppUtil.message(kind + " offset is unavailable on this backend; no false offset was applied");
    }

    private void chooseOffset(final MediaCmd media, final boolean subtitle)
    {
        final MiniPlayerPlugin player = player(media);
        boolean supported = player != null && (subtitle
                ? player.supportsSubtitleOffset() : player.supportsAudioOffset());
        final String label = subtitle ? "Caption/subtitle" : "Audio";
        if (!supported)
        {
            unsupportedOffset(label);
            return;
        }
        final int[] values = { -2000, -1000, -500, -250, 0, 250, 500, 1000, 2000 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++)
            labels[i] = signed(values[i]) + " ms";
        choose(label + " sync offset", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final int value = values[Integer.parseInt(index)];
                chooseScope(label + " sync offset",
                        subtitle ? PrefStore.Keys.playback_subtitle_offset_ms
                                : PrefStore.Keys.playback_audio_offset_ms,
                        Integer.toString(value), new ValueSetter()
                        {
                            @Override public void set(String ignored)
                            {
                                boolean applied;
                                if (subtitle)
                                {
                                    ActivePlayerSessionOverrides.setSubtitleOffsetMs(value);
                                    applied = player.setSubtitleOffsetMillis(value);
                                }
                                else
                                {
                                    ActivePlayerSessionOverrides.setAudioOffsetMs(value);
                                    applied = player.setAudioOffsetMillis(value);
                                }
                                AppUtil.message(applied
                                        ? label + " offset applied: " + signed(value) + " ms"
                                        : label + " offset is unsupported by this active output");
                            }
                        }, false);
            }
        });
    }

    private static String signed(int value)
    {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private void testCurrentVideo(final MediaCmd media)
    {
        CurrentVideoDiagnosticTest.confirmAndRun(activity, client, media,
                new CurrentVideoDiagnosticTest.Completion()
                {
                    @Override public void complete(final String summary, final String report)
                    {
                        new AlertDialog.Builder(activity)
                                .setTitle("Current Video Test")
                                .setMessage(summary)
                                .setPositiveButton("Export diagnostics",
                                        new DialogInterface.OnClickListener()
                                        {
                                            @Override public void onClick(DialogInterface dialog,
                                                    int which)
                                            {
                                                exportDiagnostics(diagnosticsText(media())
                                                        + "\n\n" + summary);
                                            }
                                        })
                                .setNeutralButton("View report",
                                        new DialogInterface.OnClickListener()
                                        {
                                            @Override public void onClick(DialogInterface dialog,
                                                    int which)
                                            { showCurrentVideoReport(report); }
                                        })
                                .setNegativeButton(android.R.string.ok, null)
                                .show();
                    }
                });
    }

    private void showCurrentVideoReport(final String report)
    {
        new AlertDialog.Builder(activity)
                .setTitle("Current Video Test Report")
                .setMessage(report)
                .setPositiveButton("Export diagnostics",
                        new DialogInterface.OnClickListener()
                        {
                            @Override public void onClick(DialogInterface dialog, int which)
                            { exportDiagnostics(diagnosticsText(media())); }
                        })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private String diagnosticsText(MediaCmd media)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        DisplayRefreshController.Result display =
                DisplayRefreshController.inspect(activity, player);
        boolean dvd = media != null && media.isDvdSessionPending();
        StringBuilder text = new StringBuilder();
        text.append("Player: ").append(player == null
                ? "none" : player.getClass().getSimpleName());
        text.append("\nSource: ").append(dvd
                ? "SageTV native DVD Push" : client.properties().getStreamingMode());
        text.append("\nDecoder: ").append(ActivePlayerSessionOverrides.resolveDecodingMethod(
                client.properties().getString(PrefStore.Keys.decoding_method,
                        DecodingMethod.DEFAULT_PREFERENCE)));
        if (dvd)
        {
            text.append("\nDVD buffer: ").append(media.getDvdDecoderBufferedAheadMs())
                    .append(" ms");
            text.append("\nDVD Push: ").append(media.getDvdPushedBytes()).append(" bytes");
        }
        text.append("\nCaptions: ").append(captionStatus(media, player));
        text.append("\nSubtitles: ").append(subtitleTrackValue(player, media));
        text.append("\nAudio: ").append(player == null
                ? "none" : player.getAudioOutputSummary());
        if (player != null)
            text.append(" / ").append(audioOffsetValue(player.getAudioOffsetMillis()));
        text.append("\nAudio stream: ").append(audioTrackValue(player));
        text.append("\nDisplay: ").append(display.summary());
        int settle = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        if (dvd || settle != 0 || DisplayRefreshController.getPendingRefreshMs() != 0)
            text.append("\nDVD HDMI settle: ").append(settle).append(" ms / pending ")
                    .append(DisplayRefreshController.getPendingRefreshMs()).append(" ms");
        return text.toString();
    }

    public static String diagnosticsTextForExport(Activity activity, MediaCmd media)
    {
        return new ActivePlayerAdjustmentsDialog(activity).diagnosticsText(media);
    }

    private void exportDiagnostics(String text)
    {
        DiagnosticExportController.show(activity, text);
    }

    private String resolvedBackend()
    {
        return ActivePlayerSessionOverrides.resolveBackend(client.properties().getString(
                PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE));
    }

    private void chooseValues(String title, final String[] values, final ValueSetter setter)
    {
        chooseVideoChoice(title, values, new ValueSetter()
        {
            @Override public void set(String index)
            { setter.set(values[Integer.parseInt(index)]); }
        });
    }

    private void choose(String title, String[] labels, final ValueSetter setter)
    {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    { setter.set(Integer.toString(which)); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
