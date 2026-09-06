package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.Context;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.video.CodecRuntimeObservations;
import opensagetv.vibe.miniclient.android.video.DeviceAudioCapabilityProfile;
import opensagetv.vibe.miniclient.android.video.DeviceCodecCapabilityProfile;
import opensagetv.vibe.miniclient.android.video.PlayerRuntimeTuning;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** On-demand debug export; never polls MediaCodec state in production. */
final class DebugCodecCapabilityCommands
{
    private DebugCodecCapabilityCommands()
    {
    }

    static String snapshot(Context context)
    {
        PlaybackHealthProbe.Snapshot health = currentHealth(context);
        String backend = health.backendClass.contains("Exo2") ? "legacy_exo"
                : health.backendClass.contains("Media3") ? "media3" : "unknown";
        PrefStore prefs = MiniclientApplication.get(context).getClient().properties();
        String queueMode = "legacy_exo".equals(backend)
                ? prefs.getString(PrefStore.Keys.exo2_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE)
                : prefs.getString(PrefStore.Keys.media3_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE);
        return "op=codec_capabilities;"
                + DeviceCodecCapabilityProfile.current().compactWire() + ';'
                + DeviceAudioCapabilityProfile.current(context).compactWire() + ';'
                + "codecInterlaceObserved=" + health.mpeg2InterlaceObservation + ';'
                + "codecInterlaceSequenceExtensionSeen="
                + health.mpeg2SequenceExtensionSeen + ';'
                + "codecInterlaceProgressiveFrames="
                + health.mpeg2ProgressiveFrameCount + ';'
                + "codecInterlaceInterlacedFrames="
                + health.mpeg2InterlacedFrameCount + ';'
                + "codecInterlaceFieldPictures=" + health.mpeg2FieldPictureCount + ';'
                + "codecDeinterlaceControl=not_exposed_by_android;"
                + CodecRuntimeObservations.compactWire(
                        backend, health.videoDecoderName, health.videoDecoderKind,
                        queueMode, health.videoDecoderInitCount,
                        health.videoDecoderReleaseCount, health.playerError);
    }

    private static PlaybackHealthProbe.Snapshot currentHealth(Context context)
    {
        try
        {
            MiniClient client = MiniclientApplication.get(context).getClient();
            if (client.getCurrentConnection() != null
                    && client.getCurrentConnection().getMediaCmd() != null)
            {
                MiniPlayerPlugin player = client.getCurrentConnection().getMediaCmd().getPlaya();
                if (player != null) return PlaybackHealthProbe.capture(player);
            }
        }
        catch (Throwable ignored)
        {
        }
        return PlaybackHealthProbe.Snapshot.unsupported("no_active_player");
    }
}
