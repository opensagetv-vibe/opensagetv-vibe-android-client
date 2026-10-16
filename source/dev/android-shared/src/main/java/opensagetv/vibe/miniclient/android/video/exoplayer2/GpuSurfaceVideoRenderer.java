package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import opensagetv.vibe.miniclient.android.video.GpuDeinterlaceOutput;

/** Legacy Exo's own GPU-surface handoff; Media3 is not substituted. */
final class GpuSurfaceVideoRenderer extends Exo2SurfaceVideoRenderer {
    private final GpuDeinterlaceOutput.Provider gpu;
    private GpuDeinterlaceOutput attachedGpu;
    private Object gpuTarget;
    private GpuDeinterlaceOutput activeGpu() {
        return attachedGpu!=null ? attachedGpu : gpu.current();
    }
    private void attachGpu(GpuDeinterlaceOutput next) {
        if(next==attachedGpu)return;
        GpuDeinterlaceOutput previous=attachedGpu;attachedGpu=next;
        if(next!=null)next.rendererAttached();
        if(previous!=null)previous.rendererDetached();
    }
    @Override public void handleMessage(int type,Object message) throws ExoPlaybackException {
        if(type!=Renderer.MSG_SET_VIDEO_OUTPUT) {super.handleMessage(type,message);return;}
        GpuDeinterlaceOutput next=gpu.current();
        if(next==null || next.inputSurface()==null || message!=next.inputSurface())next=null;
        gpuTarget=message;
        // Same actual API25 MTK GPU-surface replacement boundary as Media3.
        // Legacy retains its own decoder/source/clock; no Media3 substitution.
        if(attachedGpu!=null && attachedGpu!=next && getCodecInfo()!=null
                && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseBeforeTransition(
                    android.os.Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
        super.handleMessage(type,message);
        attachGpu(next);
    }
    @Override protected void onCodecInitialized(String name,MediaCodecAdapter.Configuration configuration,
            long initializedTimestampMs,long initializationDurationMs) {
        GpuDeinterlaceOutput next=gpu.current();
        if(next!=null && next.inputSurface()!=null && gpuTarget==next.inputSurface())attachGpu(next);
        super.onCodecInitialized(name,configuration,initializedTimestampMs,initializationDurationMs);
    }
    GpuSurfaceVideoRenderer(Context context,MediaCodecAdapter.Factory factory,
            MediaCodecSelector selector,long joiningMs,boolean fallback,Handler handler,
            VideoRendererEventListener listener,GpuDeinterlaceOutput.Provider gpu) {
        super(context,factory,selector,joiningMs,fallback,handler,listener);
        this.gpu=gpu;
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
                        android.os.Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
            super.onDisabled();
        }
        finally {
            if(output!=null)output.endDecoderTransition();
            attachGpu(null);
        }
    }
    @Override protected void onPositionReset(long positionUs,boolean joining) throws ExoPlaybackException {
        GpuDeinterlaceOutput output=activeGpu();
        if(output!=null)output.beginDecoderTransition();
        try {
            if(output!=null && getCodecInfo()!=null
                    && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseBeforeTransition(
                        android.os.Build.VERSION.SDK_INT,getCodecInfo().mimeType,getCodecInfo().name))releaseCodec();
            super.onPositionReset(positionUs,joining);
        }
        finally {if(output!=null)output.endDecoderTransition();}
    }
    @Override protected boolean processOutputBuffer(long positionUs,long elapsedRealtimeUs,
            MediaCodecAdapter codec,ByteBuffer buffer,int index,int flags,int count,
            long presentationUs,boolean decodeOnly,boolean last,Format format)
            throws ExoPlaybackException {
        GpuDeinterlaceOutput output=gpu.current();
        if(output!=null && getCodecInfo()!=null && !decodeOnly
                && opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.duplicatedWovenOutput(
                    android.os.Build.VERSION.SDK_INT,format.sampleMimeType,getCodecInfo().name)
                && !output.prepareWovenFormat(format.width,format.height))return false;
        if(output!=null && !decodeOnly && !last && Math.abs(getPlaybackSpeed()-1f)<.01f
                && output.isDuplicateWovenOutput(presentationUs-getOutputStreamOffsetUs())) {
            // Legacy Exo's own skip hook, same bounded mapped-duplicate rule.
            // Never suppress an unknown/new source picture or substitute Media3.
            skipOutputBuffer(codec,index,presentationUs-getOutputStreamOffsetUs());return true;
        }
        // BUFFERING can have STATE_STARTED with a frozen position. Preserve
        // legacy startup/rebuffer delivery until the actual clock is playing.
        if(output!=null && output.isPlaying() && getState()==STATE_STARTED && !decodeOnly) {
            long earlyUs=(long)((presentationUs-positionUs)/getPlaybackSpeed())
                    -(SystemClock.elapsedRealtime()*1000L-elapsedRealtimeUs);
            if(earlyUs>opensagetv.vibe.miniclient.video.GpuDeinterlacePolicy.releaseLeadUs(output.codedFps()))return false;
        }
        return super.processOutputBuffer(positionUs,elapsedRealtimeUs,codec,buffer,index,
                flags,count,presentationUs,decodeOnly,last,format);
    }
    @Override protected void renderOutputBufferV21(MediaCodecAdapter codec,int index,
            long presentationUs,long releaseTimeNs) {
        GpuDeinterlaceOutput output=gpu.current();
        long textureTime=output==null ? releaseTimeNs
                : output.decoderTextureReleaseNs(presentationUs,releaseTimeNs);
        super.renderOutputBufferV21(codec,index,presentationUs,textureTime);
    }
}
