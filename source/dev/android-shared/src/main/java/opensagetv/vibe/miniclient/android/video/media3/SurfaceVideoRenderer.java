package opensagetv.vibe.miniclient.android.video.media3;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.ExoPlaybackException;
import opensagetv.vibe.miniclient.android.video.GpuDeinterlaceOutput;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import opensagetv.vibe.miniclient.android.video.VideoSurfaceCodecPolicy;

/** Surface replacement policy shared by ordinary Media3 and native DVD rendering. */
@UnstableApi
class SurfaceVideoRenderer extends MediaCodecVideoRenderer {
    private GpuDeinterlaceOutput.Provider gpu;
    private GpuDeinterlaceOutput attachedGpu;
    private Object gpuTarget;
    private GpuDeinterlaceOutput activeGpu() {
        return attachedGpu!=null ? attachedGpu : gpu==null ? null : gpu.current();
    }
    void setGpuOutputProvider(GpuDeinterlaceOutput.Provider gpu) { this.gpu=gpu; }
    private void attachGpu(GpuDeinterlaceOutput next) {
        if(next==attachedGpu)return;
        GpuDeinterlaceOutput previous=attachedGpu;attachedGpu=next;
        if(next!=null)next.rendererAttached();
        if(previous!=null)previous.rendererDetached();
    }
    @Override public void handleMessage(int type,Object message) throws ExoPlaybackException {
        if(type!=Renderer.MSG_SET_VIDEO_OUTPUT) {super.handleMessage(type,message);return;}
        GpuDeinterlaceOutput next=gpu==null ? null : gpu.current();
        if(next==null || next.inputSurface()==null || message!=next.inputSurface())next=null;
        // Keep the old owner attached until the REAL playback-thread retarget
        // returns. setVideoSurfaceView's application-thread deadline is not ack.
        gpuTarget=message;
        // Physically observed API25 MTK MPEG-2 texture->display retarget throws
        // IllegalArgumentException in MediaCodec.native_setSurface. Recreate
        // only that codec on an ACTUAL GPU output replacement; keep the source,
        // sample queues and audio/master clock, and never seek/replay Watch.
        if(attachedGpu!=null && attachedGpu!=next && getCodecInfo()!=null
                && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseBeforeTransition(
                    Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
        super.handleMessage(type,message);
        attachGpu(next);
    }
    @Override protected void onCodecInitialized(String name,MediaCodecAdapter.Configuration configuration,
            long initializedTimestampMs,long initializationDurationMs) {
        GpuDeinterlaceOutput next=gpu==null ? null : gpu.current();
        if(next!=null && next.inputSurface()!=null && gpuTarget==next.inputSurface())attachGpu(next);
        super.onCodecInitialized(name,configuration,initializedTimestampMs,initializationDurationMs);
    }
    SurfaceVideoRenderer(Context context, MediaCodecAdapter.Factory factory,
            MediaCodecSelector selector, long joiningMs, boolean fallback,
            Handler handler, VideoRendererEventListener listener) {
        super(context, factory, selector, joiningMs, fallback, handler, listener, 50);
    }

    @Override protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        return VideoSurfaceCodecPolicy.recreateOnReplacement(Build.VERSION.SDK_INT, Build.DEVICE, name)
                || super.codecNeedsSetOutputSurfaceWorkaround(name);
    }

    @Override protected void releaseCodec() {
        GpuDeinterlaceOutput output=activeGpu();
        if(output!=null)output.beginDecoderTransition();
        try {super.releaseCodec();}
        finally {if(output!=null)output.endDecoderTransition();}
    }
    @Override protected void onDisabled() {
        GpuDeinterlaceOutput output=activeGpu();
        if(output!=null)output.beginDecoderTransition();
        try {
            if(output!=null && getCodecInfo()!=null
                    && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseBeforeTransition(
                        Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
            super.onDisabled();
        }
        finally {
            if(output!=null)output.endDecoderTransition();
            attachGpu(null);
        }
    }
    @Override protected void onPositionReset(long positionUs,boolean joining,boolean resetToKeyFrame)
            throws ExoPlaybackException {
        GpuDeinterlaceOutput output=activeGpu();
        if(output!=null)output.beginDecoderTransition();
        try {
            if(output!=null && getCodecInfo()!=null
                    && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseBeforeTransition(
                        Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
            super.onPositionReset(positionUs,joining,resetToKeyFrame);
        }
        finally {if(output!=null)output.endDecoderTransition();}
    }

    @Override protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
            MediaCodecAdapter codec, ByteBuffer buffer, int index, int flags, int count,
            long presentationUs, boolean decodeOnly, boolean last, Format format)
            throws ExoPlaybackException {
        // A SurfaceTexture latches the latest queued picture, unlike a
        // timestamp-scheduled SurfaceView consumer. Do not submit the normal
        // several-frame early burst to our GPU stage. Reuse Exo's position,
        // elapsed time and speed; no sleep, alternate clock or codec flush.
        // STATE_STARTED alone also occurs while Exo is still BUFFERING. The
        // frozen startup clock must not hold the first output needed for READY.
        GpuDeinterlaceOutput output = gpu == null ? null : gpu.current();
        if(output!=null && getCodecInfo()!=null && !decodeOnly
                && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.duplicatedWovenOutput(
                    Build.VERSION.SDK_INT,format.sampleMimeType,getCodecInfo().name)
                && !output.prepareWovenFormat(format.width,format.height))return false;
        if(output!=null && !decodeOnly && !last && Math.abs(getPlaybackSpeed()-1f)<.01f
                && output.isDuplicateWovenOutput(presentationUs-getOutputStreamOffsetUs())) {
            // Only a mapped duplicate of an already-submitted, pixel-qualified
            // woven pair. The retained GPU pair owns BOTH original fields.
            // Use Media3's protected ordinary skip hook/counters, not a codec
            // adapter override or an earlier release of a distinct picture.
            skipOutputBuffer(codec,index,presentationUs-getOutputStreamOffsetUs());return true;
        }
        if (output!=null && output.isPlaying() && getState()==STATE_STARTED && !decodeOnly) {
            long earlyUs=(long)((presentationUs-positionUs)/getPlaybackSpeed())
                    -(SystemClock.elapsedRealtime()*1000L-elapsedRealtimeUs);
            if (earlyUs>opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseLeadUs(output.codedFps())) return false;
        }
        return super.processOutputBuffer(positionUs,elapsedRealtimeUs,codec,buffer,index,
                flags,count,presentationUs,decodeOnly,last,format);
    }
    @Override protected void renderOutputBufferV21(MediaCodecAdapter codec,int index,
            long presentationUs,long releaseTimeNs) {
        GpuDeinterlaceOutput output=gpu==null ? null : gpu.current();
        long textureTime=output==null ? releaseTimeNs
                : output.decoderTextureReleaseNs(presentationUs,releaseTimeNs);
        super.renderOutputBufferV21(codec,index,presentationUs,textureTime);
    }
}
