package opensagetv.vibe.miniclient.android.video.media3;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.ExoPlaybackException;
import java.nio.ByteBuffer;

/** Native DVD only: avoid a physically reproduced vendor flush hang at cell replacement. */
@UnstableApi
final class NativeDvdVideoRenderer extends SurfaceVideoRenderer
{
    private final boolean debugFirstBuffers;
    private boolean firstInputRecorded;
    private boolean firstOutputRecorded;
    private int startupQueuedInputs;
    private long firstInputMs = -1L;
    private boolean decodedOutputSeen;
    private boolean startupRestartAttempted;
    private boolean faultWithholdOutput;

    NativeDvdVideoRenderer(Context context, MediaCodecAdapter.Factory adapterFactory,
            MediaCodecSelector selector, long joiningTimeMs,
            boolean fallback, Handler handler, VideoRendererEventListener listener)
    {
        super(context, adapterFactory, selector, joiningTimeMs, fallback, handler, listener);
        debugFirstBuffers = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override protected void onCodecInitialized(String name, MediaCodecAdapter.Configuration configuration,
            long initializedTimestampMs, long initializationDurationMs)
    {
        super.onCodecInitialized(name, configuration, initializedTimestampMs, initializationDurationMs);
        firstInputRecorded = false;
        firstOutputRecorded = false;
        startupQueuedInputs = 0;
        firstInputMs = -1L;
        decodedOutputSeen = false;
        MediaCodecInfo initializedInfo = getCodecInfo();
        faultWithholdOutput = initializedInfo != null && NativeDvdCodecStartupFault.consume(
                debugFirstBuffers, initializedInfo.mimeType, name);
        if (faultWithholdOutput)
            Log.w("VibeDvdCodec", "DEBUG FAULT: withholding first-epoch output; not a real firmware failure");
        // Do not clear startupRestartAttempted here: a retry initializes a
        // codec in the same stream epoch and must not become an infinite loop.
        if (debugFirstBuffers)
            Log.i("VibeDvdCodec", "init name=" + name + " durationMs=" + initializationDurationMs
                    + " realSurface=" + (configuration.surface == getSurface())
                    + " format=" + configuration.mediaFormat);
    }

    @Override protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException
    {
        if (!decodedOutputSeen && startupQueuedInputs < 8)
        {
            startupQueuedInputs++;
            if (firstInputMs < 0L) firstInputMs = SystemClock.elapsedRealtime();
        }
        // One header-only diagnostic per codec in debug APKs. Never read or
        // publish encoded payload, mutate buffer position, or sample continuously.
        if (debugFirstBuffers && !firstInputRecorded)
        {
            firstInputRecorded = true;
            Log.i("VibeDvdCodec", "firstInput ptsUs=" + buffer.timeUs
                    + " bytes=" + (buffer.data == null ? 0 : buffer.data.remaining())
                    + " key=" + buffer.isKeyFrame());
        }
        super.onQueueInputBuffer(buffer);
    }

    @Override protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
            MediaCodecAdapter codec, ByteBuffer buffer, int bufferIndex, int bufferFlags,
            int sampleCount, long presentationTimeUs, boolean decodeOnly, boolean last,
            Format format) throws ExoPlaybackException
    {
        if (faultWithholdOutput) return false;
        decodedOutputSeen = true;
        boolean record = debugFirstBuffers && !firstOutputRecorded;
        if (record) firstOutputRecorded = true;
        boolean processed = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer,
                bufferIndex, bufferFlags, sampleCount, presentationTimeUs, decodeOnly, last, format);
        if (record)
            Log.i("VibeDvdCodec", "firstOutput ptsUs=" + presentationTimeUs
                    + " positionUs=" + positionUs + " decodeOnly=" + decodeOnly
                    + " processed=" + processed);
        return processed;
    }

    @Override public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException
    {
        super.render(positionUs, elapsedRealtimeUs);
        if (decodedOutputSeen || startupRestartAttempted || startupQueuedInputs < 8)
            return;
        MediaCodecInfo info = getCodecInfo();
        if (info != null && NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(true, info.mimeType, info.name)
                && getCodec() != null && getStream() != null
                && getSurface() != null && getSurface().isValid()
                && NativeDvdCodecLifecyclePolicy.restartNoOutput(info.mimeType, info.name,
                        startupQueuedInputs, decodedOutputSeen, isSourceReady(),
                        startupRestartAttempted, firstInputMs, SystemClock.elapsedRealtime()))
        {
            startupRestartAttempted = true;
            faultWithholdOutput = false;
            Log.w("VibeDvdCodec", "no-output startup: one retained-sample codec restart; no server seek");
            // Keep Media3's period/sample queues, Push reader, Surface and A/V
            // clocks alive. Resume from the next locally queued key picture;
            // never re-read an exhausted Push source or fabricate a DVD STC.
            // The configured codec factory/selector still owns queueing and
            // Hardware/Software/Fallback policy. No timer or background probe.
            releaseCodec();
            maybeInitCodecOrBypass();
        }
    }

    @Override protected void onDisabled()
    {
        // A disabled renderer ends this startup epoch. releaseCodec() inside
        // the one-shot retry does not disable the renderer and cannot clear
        // its attempt fence.
        startupRestartAttempted = false;
        startupQueuedInputs = 0;
        firstInputMs = -1L;
        decodedOutputSeen = false;
        try
        {
            MediaCodecInfo info = getCodecInfo();
            if (info == null || !NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                    true, info.mimeType, info.name)) return;
            // Do this on the renderer's playback thread before super invokes
            // flushOrReleaseCodec. A timeout above MediaCodec cannot recover a
            // synchronous native flush already blocking that same thread.
            // Media3's supported release hook resets codec state and initializes
            // a fresh decoder on the replacement stream; no hidden API is used.
            releaseCodec();
        }
        finally
        {
            super.onDisabled();
        }
    }

    @Override protected void onPositionReset(long positionUs, boolean joining,
            boolean resetToKeyFrame) throws ExoPlaybackException
    {
        if (resetToKeyFrame)
        {
            startupRestartAttempted = false;
            startupQueuedInputs = 0;
            firstInputMs = -1L;
            decodedOutputSeen = false;
        }
        MediaCodecInfo info = getCodecInfo();
        if (resetToKeyFrame && info != null
                && NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(true, info.mimeType, info.name))
            releaseCodec();
        // Media3 1.11's video-specific flush-policy hooks are final. Use its
        // supported lifecycle hook before position reset, not reflection or
        // a library fork, to leave no stale codec for that reset to flush.
        super.onPositionReset(positionUs, joining, resetToKeyFrame);
    }
}
