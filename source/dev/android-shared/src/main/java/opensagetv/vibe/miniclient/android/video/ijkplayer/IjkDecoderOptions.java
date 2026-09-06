package opensagetv.vibe.miniclient.android.video.ijkplayer;

import android.util.Log;

import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Applies decoding policy only to the original SageTV IJK 0.8.8 backend. */
public final class IjkDecoderOptions
{
    private static final String TAG = "IJKDecoderOptions";

    private IjkDecoderOptions() { }

    public static DecodingMethod apply(IjkMediaPlayer player, PrefStore prefs)
    {
        DecodingMethod method = DecodingMethod.fromPreference(
                prefs.getString(PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE));
        boolean hardware = method.allowHardware();
        boolean mpeg2Requested = prefs.getBoolean(PrefStore.Keys.ijk_mediacodec_mpeg2, false);
        boolean mpeg2Hardware = hardware && mpeg2Requested && !CodecSelector.isKnownBrokenMpeg2HardwareDevice();

        if (hardware && mpeg2Requested && !mpeg2Hardware)
        {
            Log.w(TAG, "Legacy IJK: MPEG-2 MediaCodec disabled on Amazon AFTMM/mantis; using bundled FFmpeg decoder");
        }

        // Keep this option set compatible with the original 0.8.8 native runtime.
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-avc", hardware ? 1 : 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", hardware ? 1 : 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-mpeg2", mpeg2Hardware ? 1 : 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer");
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 0);
        return method;
    }
}
