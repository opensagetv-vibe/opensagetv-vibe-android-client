package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy;
import opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.gsy.GSYSystemMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * On-demand processing-path description shared by every backend and debug UI.
 * Device means vendor-controlled output, NOT proof of a deinterlaced frame.
 * Software Client is reported only after the native filter's activation event;
 * progressive input must not be mislabeled as actively filtered. No listeners,
 * sampling loops, transport changes, or decoder replacement occur here.
 */
public final class ClientDeinterlaceStatus
{
    private ClientDeinterlaceStatus() { }
    private static boolean gpuRequested() {
        MiniclientApplication app=MiniclientApplication.get();
        return app!=null && app.getClient()!=null && GpuDeinterlacePolicy.requested(
                ActivePlayerSessionOverrides.resolveVideoDeinterlaceMode(
                        app.getClient().properties().getString(PrefStore.Keys.video_deinterlace_mode,"auto")),false);
    }
    public static GpuVideoOutputOwner gpuOwner(MiniPlayerPlugin player) {
        if (player instanceof GSYMediaPlayerImpl) player=((GSYMediaPlayerImpl)player).getDelegateForDebug();
        return player instanceof GpuVideoOutputOwner ? (GpuVideoOutputOwner)player : null;
    }
    public static String gpuReason(MiniPlayerPlugin player) {
        GpuVideoOutputOwner owner=gpuOwner(player);
        if(owner==null)return gpuRequested() ? "backend_gpu_output_unavailable" : "";
        GpuDeinterlaceOutput gpu=owner.gpuVideoOutput();
        return gpu==null ? owner.gpuVideoUnavailableReason() : gpu.reason();
    }
    public static boolean supportsSoftwareSelection(MiniPlayerPlugin player)
    {
        if (player instanceof GSYMediaPlayerImpl)
            player = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
        if (!(player instanceof IJKMediaPlayerImpl)) return false;
        MediaInfo info = mediaInfo((IJKMediaPlayerImpl) player);
        // These algorithms operate on AVFrame software output. Merely using
        // the IJK backend does not expose them on its MediaCodec path.
        return info != null && "avcodec".equalsIgnoreCase(info.mVideoDecoder);
    }
    public static String filter(MiniPlayerPlugin player)
    {
        GpuVideoOutputOwner gpuOwner=gpuOwner(player);
        if(gpuOwner!=null && gpuOwner.gpuVideoOutput()!=null) return gpuOwner.gpuVideoOutput().activeFilter();
        if (player instanceof GSYMediaPlayerImpl)
            player = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
        return player instanceof IJKMediaPlayerImpl
                ? ((IJKMediaPlayerImpl) player).getActiveDeinterlaceFilter() : "";
    }

    public static String current(MiniPlayerPlugin player, MimDirectSessionClient mim)
    {
        String server = mim == null ? "" : mim.acceptedDeinterlaceForDiagnostics();
        if (!server.isEmpty())
            return "off".equals(server) ? client(player) : "auto".equals(server) ? "MIM Auto" : "MIM";
        return client(player);
    }

    /** Actual processing implementation; a hardware request is not evidence. */
    public static String implementation(MiniPlayerPlugin player, MimDirectSessionClient mim)
    {
        if (mim != null && !mim.acceptedDeinterlaceForDiagnostics().isEmpty()
                && !"off".equals(mim.acceptedDeinterlaceForDiagnostics()))
            return "Unknown"; // Accepted server policy does not prove its actual GPU filter.
        GpuVideoOutputOwner owner=gpuOwner(player);
        if(owner!=null && owner.gpuVideoOutput()!=null && !owner.gpuVideoOutput().activeFilter().isEmpty()) return "Hardware";
        // An unavailable GPU stage does not erase the independently observed
        // native fallback decoder identity (Hardware/Software). Keep those
        // dimensions separate instead of falsely reporting Unknown.
        if (player instanceof GSYMediaPlayerImpl)
            player = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
        if (player == null || !player.hasRenderedFirstVideoFrame()) return "Unknown";
        if (player instanceof IJKMediaPlayerImpl)
        {
            IJKMediaPlayerImpl ijk = (IJKMediaPlayerImpl) player;
            if (ijk.isSoftwareDeinterlaceActive()) return "Software";
            MediaInfo info = mediaInfo(ijk);
            if (info == null) return "Unknown";
            if ("avcodec".equalsIgnoreCase(info.mVideoDecoder)) return "Software";
            if ("mediacodec".equalsIgnoreCase(info.mVideoDecoder) && info.mVideoDecoderImpl != null
                    && !info.mVideoDecoderImpl.isEmpty())
                return AndroidCodecPolicy.isSoftwareCodecName(info.mVideoDecoderImpl) ? "Software" : "Hardware";
            return "Unknown";
        }
        String decoder = player instanceof Media3MediaPlayerImpl
                ? ((Media3MediaPlayerImpl) player).getSelectedVideoDecoderForDebug()
                : player instanceof Exo2MediaPlayerImpl
                ? ((Exo2MediaPlayerImpl) player).getSelectedVideoDecoderForDebug() : "";
        return decoder.isEmpty() ? "Unknown"
                : AndroidCodecPolicy.isSoftwareCodecName(decoder) ? "Software" : "Hardware";
    }

    private static String client(MiniPlayerPlugin player)
    {
        GpuVideoOutputOwner owner=gpuOwner(player);
        if(owner!=null) {
            GpuDeinterlaceOutput gpu=owner.gpuVideoOutput();
            if(gpu!=null) return "Active".equals(gpu.state()) ? "Client GPU" : "Bypass".equals(gpu.state()) ? "Bypass" : gpu.state();
            if(!owner.gpuVideoUnavailableReason().isEmpty())
                return owner.gpuVideoUnavailableReason().startsWith("gpu_native_bypass_") ? "Bypass" : "Unavailable";
        }
        if(player!=null && owner==null && gpuRequested())return "Unavailable";
        if (player instanceof GSYMediaPlayerImpl)
            player = ((GSYMediaPlayerImpl) player).getDelegateForDebug();
        if (player == null || !player.hasRenderedFirstVideoFrame()) return "Inactive";
        if (player instanceof IJKMediaPlayerImpl)
        {
            IJKMediaPlayerImpl ijk = (IJKMediaPlayerImpl) player;
            if (ijk.isSoftwareDeinterlaceActive()) return "Client";
            MediaInfo info = mediaInfo(ijk);
            return info != null && "MediaCodec".equalsIgnoreCase(info.mVideoDecoder)
                    ? "Device" : "Off / Progressive";
        }
        String decoder = player instanceof Media3MediaPlayerImpl
                ? ((Media3MediaPlayerImpl) player).getSelectedVideoDecoderForDebug()
                : player instanceof Exo2MediaPlayerImpl
                ? ((Exo2MediaPlayerImpl) player).getSelectedVideoDecoderForDebug() : "";
        if (!decoder.isEmpty())
            return AndroidCodecPolicy.isSoftwareCodecName(decoder) ? "Unavailable" : "Device";
        // Android MediaPlayer does not expose its codec identity. Do not infer
        // hardware success just because the configured GSY engine said System.
        return player instanceof GSYSystemMediaPlayerImpl ? "Device controlled" : "Unknown";
    }

    private static MediaInfo mediaInfo(IJKMediaPlayerImpl player)
    {
        IMediaPlayer nativePlayer = player.getPlayer();
        if (nativePlayer == null) return null;
        try { return nativePlayer.getMediaInfo(); }
        catch (RuntimeException releasedDuringSnapshot) { return null; }
    }
}
