package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.ArrayList;

/**
 * Legacy ExoPlayer renderer factory with an explicit PCM-only audio sink policy.
 *
 * <p>Some Android TV devices advertise encoded HDMI formats that their active
 * output route cannot actually consume.  Legacy ExoPlayer otherwise selects
 * raw AC3/E-AC3 passthrough and can report healthy rendered-buffer counters
 * while the device emits digital silence.  The PCM-only capability set makes
 * a decoder handle the encoded stream while leaving video on MediaCodec.</p>
 */
final class Exo2AudioExtensionRenderersFactory extends DefaultRenderersFactory
{
    private final boolean allowEncodedAudioPassthrough;
    private final Exo2PcmAudioProcessor pcmAudioProcessor;

    Exo2AudioExtensionRenderersFactory(Context context,
                                       boolean allowEncodedAudioPassthrough,
                                       int audioOffsetMs)
    {
        super(context);
        this.allowEncodedAudioPassthrough = allowEncodedAudioPassthrough;
        pcmAudioProcessor = allowEncodedAudioPassthrough
                ? null : new Exo2PcmAudioProcessor(audioOffsetMs);
    }

    Exo2PcmAudioProcessor getPcmAudioProcessor() { return pcmAudioProcessor; }

    @Override protected void buildTextRenderers(Context context, TextOutput output,
            Looper looper, @ExtensionRendererMode int mode, ArrayList<Renderer> out)
    {
        out.add(new TextRenderer(output, looper, new Exo2Cea708DecoderFactory()));
    }

    @Override
    protected void buildVideoRenderers(Context context, @ExtensionRendererMode int mode,
            MediaCodecSelector selector, boolean fallback, Handler handler,
            VideoRendererEventListener listener, long joiningMs, ArrayList<Renderer> out)
    {
        // Preserve extension ordering and the configured selector/adapter/fallback.
        // Replace only the superclass's platform renderer, not optional software engines.
        super.buildVideoRenderers(context, mode, selector, fallback, handler, listener, joiningMs, out);
        for (int i = 0; i < out.size(); i++)
        {
            if (out.get(i).getClass() == MediaCodecVideoRenderer.class)
            {
                out.set(i, new Exo2SurfaceVideoRenderer(context, getCodecAdapterFactory(),
                        selector, joiningMs, fallback, handler, listener));
                break;
            }
        }
    }

    @Override
    protected AudioSink buildAudioSink(Context context,
                                       boolean enableAudioFloatOutput,
                                       boolean enableAudioTrackPlaybackParams,
                                       boolean enableOffload)
    {
        if (allowEncodedAudioPassthrough)
        {
            return super.buildAudioSink(
                    context,
                    enableAudioFloatOutput,
                    enableAudioTrackPlaybackParams,
                    enableOffload);
        }

        return new DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setAudioProcessors(new AudioProcessor[] { pcmAudioProcessor })
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                .build();
    }
}
