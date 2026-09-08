package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;
import opensagetv.vibe.miniclient.util.VerboseLogging;

/**
 * Debug-build-only control surface used by the SageTV MCP/ADB regression tools.
 *
 * This receiver intentionally does not subscribe to player callbacks or emit continuous
 * telemetry.  It reads existing state only when ADB/MCP explicitly asks for a snapshot,
 * which avoids the playback regressions caused by the earlier callback telemetry experiment.
 */
public final class DevTestReceiver extends BroadcastReceiver
{
    public static final String ACTION = "opensagetv.vibe.miniclient.DEBUG_CONTROL";

    private static final int RESULT_OK = 1;
    private static final int RESULT_ERROR = -1;
    private static final int MAX_RECOVERY_WATCHDOG_MS = 300000;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null || !ACTION.equals(intent.getAction()))
        {
            fail("invalid_action");
            return;
        }

        try
        {
            String op = clean(intent.getStringExtra("op"));
            if ("snapshot".equals(op))
            {
                ok(DebugStateProvider.snapshot(context, MAX_RECOVERY_WATCHDOG_MS));
            }
            else if ("events".equals(op))
            {
                ok(PlaybackEventTraps.compactWire());
            }
            else if ("events_clear".equals(op))
            {
                ok(PlaybackEventTraps.clearWire());
            }
            else if ("trace_status".equals(op))
            {
                ok(PlaybackEventTraps.traceStatusWire(context));
            }
            else if ("trace_clear".equals(op))
            {
                ok(PlaybackEventTraps.clearTraceWire(context));
            }
            else if ("trace_enable".equals(op))
            {
                boolean enabled = parseBoolean(clean(intent.getStringExtra("enabled")), true);
                ok(PlaybackEventTraps.setTraceEnabledWire(context, enabled));
            }
            else if ("config".equals(op))
            {
                ok(DebugPlayerConfigCommands.configure(context, intent));
            }
            else if ("tuning".equals(op))
            {
                ok(DebugTuningCommands.configure(intent));
            }
            else if ("tuning_get".equals(op))
            {
                ok("op=tuning_get;" + PlayerRuntimeTuning.compactWire());
            }
            else if ("capture_datasource".equals(op))
            {
                boolean enabled = parseBoolean(clean(intent.getStringExtra("enabled")), false);
                VerboseLogging.LOG_DATASOURCE_BYTES_TO_FILE = enabled;
                // Keep debug captures in credential-protected app storage. On
                // Android 11/Fire OS, ADB cannot reliably read app-specific
                // external storage even through run-as; internal debug data is
                // deterministic and can be exported without storage permission.
                File captureDir = new File(context.getFilesDir(), "captures");
                boolean captureDirReady = captureDir.isDirectory() || captureDir.mkdirs();
                File captureFile = captureDirReady
                        ? new File(captureDir, "sagetv-push-capture.ts") : null;
                VerboseLogging.DATASOURCE_CAPTURE_PATH = enabled && captureFile != null
                        ? captureFile.getAbsolutePath() : "";
                ok("op=capture_datasource;enabled=" + enabled
                        + ";path=" + safe(VerboseLogging.DATASOURCE_CAPTURE_PATH)
                        + ";appliesNextPlayback=true;maxBytes="
                        + VerboseLogging.DATASOURCE_CAPTURE_MAX_BYTES);
            }
            else if ("codec_capabilities".equals(op))
            {
                ok(DebugCodecCapabilityCommands.snapshot(context));
            }
            else if ("client_id_get".equals(op))
            {
                ok(DebugClientIdCommands.status(context));
            }
            else if ("client_id_set".equals(op))
            {
                ok(DebugClientIdCommands.configure(context, intent));
            }
            else if ("connect".equals(op))
            {
                ok(DebugSessionCommands.connectServer(context, intent));
            }
            else if ("exit".equals(op))
            {
                ok(DebugSessionCommands.exitSession(context));
            }
            else if ("command".equals(op))
            {
                ok(DebugSessionCommands.sendCommand(context, intent));
            }
            else if ("active_player_adjustments".equals(op))
            {
                ok(DebugSessionCommands.showActivePlayerAdjustments(context));
            }
            else if ("active_player_overlay".equals(op))
            {
                ok(DebugSessionCommands.setActivePlayerOverlay(context, intent));
            }
            else if ("watch_server_file".equals(op))
            {
                ok(DebugSessionCommands.watchServerFile(context, intent));
            }
            else if ("set_live_channel".equals(op))
            {
                ok(DebugSessionCommands.setLiveChannel(context, intent));
            }
            else if ("server_seek_time".equals(op))
            {
                ok(DebugSessionCommands.seekServerTime(context, intent));
            }
            else if ("input_text_native".equals(op))
            {
                ok(DebugSessionCommands.inputTextNative(context, intent));
            }
            else if ("ime_hide".equals(op))
            {
                ok(DebugSessionCommands.hideImeDirect());
            }
            else if ("ime_suppress".equals(op))
            {
                ok(DebugSessionCommands.setImeSuppression(intent));
            }
            else if ("player_control".equals(op))
            {
                ok(DebugPlayerCommands.control(context, intent));
            }
            else if ("seek_time".equals(op) || "seek_absolute".equals(op))
            {
                ok(DebugPlayerCommands.seekTime(context, intent, op));
            }
            else if ("seek_relative".equals(op))
            {
                ok(DebugPlayerCommands.seekRelative(context, intent));
            }
            else if ("frame_step".equals(op))
            {
                ok(DebugPlayerCommands.frameStep(context, intent));
            }
            else if ("playback_rate".equals(op))
            {
                ok(DebugPlayerCommands.playbackRate(context, intent));
            }
            else if ("fast_switch_file".equals(op))
            {
                ok(DebugPlayerCommands.fastSwitchFile(context, intent));
            }
            else if ("comskip".equals(op))
            {
                ok(DebugPlayerCommands.comskip(context, intent));
            }
            else if ("subtitle_control".equals(op))
            {
                ok(DebugPlayerCommands.subtitle(context, intent));
            }
            else if ("audio_focus_request".equals(op))
            {
                ok(DebugAudioFocusCommands.request(context, intent));
            }
            else if ("audio_focus_abandon".equals(op))
            {
                ok(DebugAudioFocusCommands.abandon(context));
            }
            else if ("profile_list".equals(op) || "profile_save".equals(op)
                    || "profile_load".equals(op) || "profile_delete".equals(op))
            {
                DebugProfileCommands.run(this, context, intent);
            }
            else if ("profile_ui".equals(op))
            {
                ok(DebugProfileCommands.openUi(context));
            }
            else if ("skip_check".equals(op) || "comskip_check".equals(op) || "relative_seek_check".equals(op))
            {
                DebugAsyncPlayerChecks.run(this, context, intent, MAX_RECOVERY_WATCHDOG_MS);
            }
            else
            {
                fail("unsupported_op=" + safe(op));
            }
        }
        catch (Throwable t)
        {
            fail("exception=" + safe(t.getClass().getSimpleName()) + ";message=" + safe(t.getMessage()));
        }
    }

    private void ok(String data)
    {
        setResultCode(RESULT_OK);
        setResultData(DebugResponseFormatter.success(data));
    }

    private void fail(String data)
    {
        setResultCode(RESULT_ERROR);
        setResultData(DebugResponseFormatter.failure(data));
    }

}
