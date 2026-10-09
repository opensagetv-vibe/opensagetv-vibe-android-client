package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import opensagetv.vibe.miniclient.android.video.VideoSurfaceCodecPolicy;

/** Keep legacy Exo's own renderer lifecycle, adding only proven vendor exceptions. */
final class Exo2SurfaceVideoRenderer extends MediaCodecVideoRenderer {
    Exo2SurfaceVideoRenderer(Context context, MediaCodecAdapter.Factory factory,
            MediaCodecSelector selector, long joiningMs, boolean fallback,
            Handler handler, VideoRendererEventListener listener) {
        super(context, factory, selector, joiningMs, fallback, handler, listener, 50);
    }

    @Override protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        return VideoSurfaceCodecPolicy.recreateOnReplacement(Build.VERSION.SDK_INT, Build.DEVICE, name)
                || super.codecNeedsSetOutputSurfaceWorkaround(name);
    }
}
