package opensagetv.vibe.miniclient.android.video.media3;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import opensagetv.vibe.miniclient.android.video.VideoSurfaceCodecPolicy;

/** Surface replacement policy shared by ordinary Media3 and native DVD rendering. */
@UnstableApi
class SurfaceVideoRenderer extends MediaCodecVideoRenderer {
    SurfaceVideoRenderer(Context context, MediaCodecAdapter.Factory factory,
            MediaCodecSelector selector, long joiningMs, boolean fallback,
            Handler handler, VideoRendererEventListener listener) {
        super(context, factory, selector, joiningMs, fallback, handler, listener, 50);
    }

    @Override protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        return VideoSurfaceCodecPolicy.recreateOnReplacement(Build.VERSION.SDK_INT, Build.DEVICE, name)
                || super.codecNeedsSetOutputSurfaceWorkaround(name);
    }
}
