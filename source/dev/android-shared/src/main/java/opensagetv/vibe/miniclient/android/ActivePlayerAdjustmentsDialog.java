package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.video.ActivePlayerSessionOverrides;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.DisplayRefreshController;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.android.video.TextSubtitlePresentation;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.media.SubtitleTrack;

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

    private MediaCmd media()
    {
        return client == null || client.getCurrentConnection() == null
                ? null : client.getCurrentConnection().getMediaCmd();
    }

    private void showMain()
    {
        final MediaCmd media = media();
        final String[] rows = new String[] {
                status(media),
                "Player backend",
                "Hardware decoding policy",
                "MediaCodec queueing",
                "DVD timestamp repair",
                "Source buffering preset",
                refreshStatus(player(media)),
                refreshSettleStatus(),
                "Aspect ratio (apply now)",
                "Closed captions (apply now)",
                textSubtitleStatus(player(media), media),
                audioTrackStatus(player(media)),
                offsetStatus("Caption/subtitle", player(media), true),
                offsetStatus("Audio", player(media), false),
                audioOutputStatus(player(media)),
                "Restart decoder at this position",
                "Reset current-session overrides",
                "Playback Stats overlay (" + ActivePlayerProcessOverlay.visibleMode() + ")",
                "Live playback diagnostics"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Active Player Adjustments")
                .setItems(rows, new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    {
                        switch (which)
                        {
                            case 0: showDiagnostics(media); break;
                            case 1: chooseBackend(media); break;
                            case 2: chooseDecoding(); break;
                            case 3: chooseCodecQueueing(); break;
                            case 4: chooseDvdRepair(); break;
                            case 5: chooseBufferPreset(media); break;
                            case 6: chooseRefreshRate(media); break;
                            case 7: chooseRefreshSettle(); break;
                            case 8:
                                client.eventbus().post(ToggleAspectRatioEvent.INSTANCE);
                                AppUtil.message("Aspect ratio changed for the active SageTV session");
                                break;
                            case 9: chooseCaptions(media); break;
                            case 10: chooseTextSubtitlePresentation(media); break;
                            case 11: chooseAudioTrack(media); break;
                            case 12:
                                chooseOffset(media, true);
                                break;
                            case 13:
                                chooseOffset(media, false);
                                break;
                            case 14: chooseAudioPassthrough(media); break;
                            case 15: reload(media); break;
                            case 16:
                                ActivePlayerSessionOverrides.reset();
                                DisplayRefreshController.cancelPendingReload();
                                MiniPlayerPlugin active = player(media);
                                if (active != null && active.supportsSubtitleOffset())
                                    active.setSubtitleOffsetMillis(client.properties().getInt(
                                            PrefStore.Keys.playback_subtitle_offset_ms, 0));
                                if (active != null && active.supportsAudioOffset())
                                    active.setAudioOffsetMillis(client.properties().getInt(
                                            PrefStore.Keys.playback_audio_offset_ms, 0));
                                if (active != null && active.supportsTextSubtitlePresentation())
                                    active.setTextSubtitlePresentation(
                                            client.properties().getInt(
                                                    PrefStore.Keys.playback_subtitle_safe_area_percent,
                                                    TextSubtitlePresentation.DEFAULT_SAFE_AREA_PERCENT),
                                            client.properties().getInt(
                                                    PrefStore.Keys.playback_subtitle_text_scale_percent,
                                                    TextSubtitlePresentation.DEFAULT_TEXT_SCALE_PERCENT),
                                            client.properties().getString(
                                                    PrefStore.Keys.playback_subtitle_text_style,
                                                    TextSubtitlePresentation.STYLE_SYSTEM));
                                AppUtil.message("Current-session player overrides cleared");
                                showMain();
                                break;
                            case 17: choosePlaybackStats(media); break;
                            case 18: showDiagnostics(media); break;
                            default: break;
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        return "NOW  " + backend + " | " + source + "\n"
                + ActivePlayerSessionOverrides.compactSummary();
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
        return "HDMI settle before DVD decoder reload (" + value + " ms)";
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
            return;
        }
        if (active == null || !active.supportsTextSubtitlePresentation())
        {
            AppUtil.message("The active backend does not expose local text-subtitle styling");
            return;
        }
        String[] rows = {
                "Bottom safe area: " + active.getTextSubtitleSafeAreaPercent() + "%",
                "Text size: " + active.getTextSubtitleScalePercent() + "%",
                "Style: " + active.getTextSubtitleStyle()
        };
        choose("Text subtitle presentation", rows, new ValueSetter()
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
        choose("Text subtitle bottom safe area", labels, new ValueSetter()
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
        choose("Text subtitle size", labels, new ValueSetter()
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
        choose("Text subtitle style", labels, new ValueSetter()
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
        new AlertDialog.Builder(activity)
                .setTitle("Apply text subtitle presentation")
                .setItems(new String[] { "This playback session",
                                "Save as this device's default" },
                        new DialogInterface.OnClickListener()
                        {
                            @Override public void onClick(DialogInterface dialog, int which)
                            {
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
                                        ? "Text subtitle presentation applied immediately"
                                        : "Text subtitle presentation was rejected");
                                showMain();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String audioOutputStatus(MiniPlayerPlugin player)
    {
        return "Audio output / passthrough (" + (player == null
                ? "no active player" : player.getAudioOutputSummary()) + ")";
    }

    private void chooseAudioTrack(MediaCmd media)
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
        choose("Active audio track", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                active.setAudioTrack(ids[selected]);
                AppUtil.message("Audio track applied: " + labels[selected]);
                showMain();
            }
        });
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

    private void chooseRefreshRate(final MediaCmd media)
    {
        final String[] labels = { "Off", "Seamless only", "Always (current resolution)" };
        final String[] values = { DisplayRefreshController.OFF,
                DisplayRefreshController.SEAMLESS, DisplayRefreshController.ALWAYS };
        choose("Refresh-rate matching", labels, new ValueSetter()
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
                                        && DisplayRefreshController.scheduleControlledDvdReload(
                                                activity, media, settleMs);
                                AppUtil.message((result.applied ? "Applied: " : "Not applied: ")
                                        + result.summary() + (scheduled
                                        ? "; DVD decoder reload in " + settleMs + " ms" : ""));
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
        choose("HDMI settle before DVD decoder reload", labels, new ValueSetter()
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
                PlayerBackend.IJKPLAYER, PlayerBackend.GSYPLAYER };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i].displayName();
        choose("Player backend", labels, new ValueSetter()
        {
            @Override public void set(String value)
            {
                PlayerBackend selected = values[Integer.parseInt(value)];
                final boolean pinnedDvd = media != null && media.isDvdSessionPending()
                        && selected != PlayerBackend.MEDIA3;
                chooseScope("Player backend", PrefStore.Keys.default_player,
                        selected.preferenceValue(), new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setBackend(selectedValue);
                                if (pinnedDvd)
                                    AppUtil.message("Native DVD remains on Media3; this backend applies to the next non-DVD video");
                            }
                        }, !pinnedDvd);
            }
        });
    }

    private void chooseDecoding()
    {
        final DecodingMethod[] values = DecodingMethod.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i].displayName();
        choose("Video decoding policy", labels, new ValueSetter()
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
        choose("Source buffering preset", labels, new ValueSetter()
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
        final SubtitleTrack[] tracks = active == null
                ? new SubtitleTrack[0] : active.getSubtitleTracks();
        List<String> labelList = new ArrayList<String>();
        labelList.add("Use SageTV STV");
        labelList.add("Off");
        labelList.add("Preferred configured track (this session)");
        labelList.add("CC1 (legacy server fallback)");
        labelList.add("CC2 (legacy server fallback)");
        for (SubtitleTrack track : tracks)
            labelList.add("Track: " + track.toString());
        final String[] labels = labelList.toArray(new String[labelList.size()]);
        choose("Closed captions / subtitle track", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                if (selected == 0)
                    media.setLegacyServerCaptionMode("stv");
                else if (selected == 1)
                {
                    media.setLegacyServerCaptionMode("off");
                    if (active != null) active.setSubtitleTrack(MiniPlayerPlugin.DISABLE_TRACK);
                }
                else if (selected == 2)
                {
                    media.setLegacyServerCaptionMode("stv");
                    if (active != null) active.setPreferredSubtitleTrack();
                }
                else if (selected == 3)
                    media.setLegacyServerCaptionMode("cc1");
                else if (selected == 4)
                    media.setLegacyServerCaptionMode("cc2");
                else if (active != null)
                    active.setSubtitleTrack(tracks[selected - 5].getIndex());
                if (selected == 0 && !media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_legacy_stv_unavailable));
                else if (media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_stv_authoritative));
                else
                    AppUtil.message("Caption selection applied: " + labels[selected]);
            }
        });
    }

    private void chooseScope(final String title, final String preferenceKey,
            final String value, final ValueSetter sessionSetter,
            final boolean reloadRequired)
    {
        new AlertDialog.Builder(activity)
                .setTitle(title + ": " + value)
                .setItems(new String[] { "This playback session", "Save as this device's default" },
                        new DialogInterface.OnClickListener()
                        {
                            @Override public void onClick(DialogInterface dialog, int which)
                            {
                                sessionSetter.set(value);
                                if (which == 1)
                                    client.properties().setString(preferenceKey, value);
                                if (reloadRequired)
                                    reload(media());
                                else
                                    showMain();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reload(MediaCmd media)
    {
        if (media != null && media.requestControlledPlayerReload())
            AppUtil.message("Rebuilding the DVD decoder at the same SageTV position");
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

    private void showDiagnostics(MediaCmd media)
    {
        final String text = diagnosticsText(media);
        new AlertDialog.Builder(activity)
                .setTitle("Live playback diagnostics")
                .setMessage(text)
                .setPositiveButton("Refresh", new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    { showDiagnostics(media()); }
                })
                .setNeutralButton("Export", new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    { exportDiagnostics(text); }
                })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private String diagnosticsText(MediaCmd media)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        DisplayRefreshController.Result display =
                DisplayRefreshController.inspect(activity, player);
        return "Player: " + (player == null ? "none" : player.getClass().getName())
                + "\nSource: " + (media != null && media.isDvdSessionPending()
                ? "SageTV native DVD Push" : client.properties().getStreamingMode())
                + "\nDecoder policy: " + ActivePlayerSessionOverrides.resolveDecodingMethod(
                client.properties().getString(PrefStore.Keys.decoding_method,
                        DecodingMethod.DEFAULT_PREFERENCE))
                + "\nDVD buffered ahead: " + (media == null ? -1 : media.getDvdDecoderBufferedAheadMs()) + " ms"
                + "\nPush bytes: " + (media == null ? -1 : media.getDvdPushedBytes())
                + "\nSubtitle offset: " + (player == null ? 0 : player.getSubtitleOffsetMillis()) + " ms"
                + "\nText subtitles: " + textSubtitleStatus(player, media)
                + "\nAudio offset: " + (player == null ? 0 : player.getAudioOffsetMillis()) + " ms"
                + "\nAudio track: " + audioTrackStatus(player)
                + "\nAudio output: " + (player == null ? "none" : player.getAudioOutputSummary())
                + "\nDisplay: " + display.summary()
                + "\nHDMI settle: " + ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                        client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0))
                        + " ms | pending reload: "
                        + DisplayRefreshController.getPendingReloadMs() + " ms"
                + "\nSession overrides: " + ActivePlayerSessionOverrides.compactSummary();
    }

    private void exportDiagnostics(String text)
    {
        try
        {
            File directory = activity.getExternalFilesDir("diagnostics");
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs()))
                throw new IllegalStateException("diagnostics directory unavailable");
            File output = new File(directory,
                    "active-player-" + System.currentTimeMillis() + ".txt");
            FileOutputStream stream = new FileOutputStream(output);
            try
            {
                stream.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            }
            finally
            {
                stream.close();
            }
            AppUtil.message("Diagnostics exported: " + output.getAbsolutePath());
        }
        catch (Exception e)
        {
            AppUtil.message("Unable to export diagnostics: " + e.getMessage());
        }
    }

    private String resolvedBackend()
    {
        return ActivePlayerSessionOverrides.resolveBackend(client.properties().getString(
                PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE));
    }

    private void chooseValues(String title, final String[] values, final ValueSetter setter)
    {
        choose(title, values, new ValueSetter()
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
