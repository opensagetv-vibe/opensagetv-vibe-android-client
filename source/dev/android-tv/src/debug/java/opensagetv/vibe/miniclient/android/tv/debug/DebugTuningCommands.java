package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.kbToBytes;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.optionalBoolean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.optionalInt;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.optionalLong;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.optionalString;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoolean;

import android.content.Intent;

import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;

/** Implements the unchanged debug-only runtime tuning wire operation. */
final class DebugTuningCommands
{
    private DebugTuningCommands()
    {
    }

    static String configure(Intent intent)
    {
        if (parseBoolean(intent.getStringExtra("reset"), false))
        {
            PlayerRuntimeTuning.reset();
        }

        Integer media3Ts = optionalInt(intent, "media3_ts_search_multiplier");
        Integer exo2Ts = optionalInt(intent, "exo2_ts_search_multiplier");
        Integer media3ReadKb = optionalInt(intent, "media3_pull_read_kb");
        Integer exo2ReadKb = optionalInt(intent, "exo2_pull_read_kb");
        Integer media3Min = optionalInt(intent, "media3_min_buffer_ms");
        Integer media3Max = optionalInt(intent, "media3_max_buffer_ms");
        Integer media3Play = optionalInt(intent, "media3_playback_buffer_ms");
        Integer media3Rebuffer = optionalInt(intent, "media3_rebuffer_ms");
        Integer exo2Min = optionalInt(intent, "exo2_min_buffer_ms");
        Integer exo2Max = optionalInt(intent, "exo2_max_buffer_ms");
        Integer exo2Play = optionalInt(intent, "exo2_playback_buffer_ms");
        Integer exo2Rebuffer = optionalInt(intent, "exo2_rebuffer_ms");
        Long directionalMin = optionalLong(intent, "directional_sync_min_delta_ms");
        Boolean media3Recovery = optionalBoolean(intent, "media3_seek_recovery_enabled");
        Boolean exo2Recovery = optionalBoolean(intent, "exo2_seek_recovery_enabled");
        Long media3RecoveryMs = optionalLong(intent, "media3_seek_recovery_delay_ms");
        Long exo2RecoveryMs = optionalLong(intent, "exo2_seek_recovery_delay_ms");
        String media3SeekPolicy = optionalString(intent, "media3_seek_policy");
        String exo2SeekPolicy = optionalString(intent, "exo2_seek_policy");
        String media3CodecMode = optionalString(intent, "media3_codec_mode");
        String exo2CodecMode = optionalString(intent, "exo2_codec_mode");

        PlayerRuntimeTuning.configure(
                media3Ts, exo2Ts,
                kbToBytes(media3ReadKb), kbToBytes(exo2ReadKb),
                media3Min, media3Max, media3Play, media3Rebuffer,
                exo2Min, exo2Max, exo2Play, exo2Rebuffer,
                directionalMin, media3Recovery, exo2Recovery,
                media3RecoveryMs, exo2RecoveryMs,
                media3SeekPolicy, exo2SeekPolicy, media3CodecMode, exo2CodecMode);

        return "op=tuning;" + PlayerRuntimeTuning.compactWire() + ";appliesNextPlayback=true";
    }
}
