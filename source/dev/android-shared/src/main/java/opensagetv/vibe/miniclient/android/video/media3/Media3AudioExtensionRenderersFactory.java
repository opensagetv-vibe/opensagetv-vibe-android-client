package opensagetv.vibe.miniclient.android.video.media3;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;

import java.util.ArrayList;

/**
 * Enables optional audio renderers without registering FFmpeg's experimental video renderer.
 *
 * <p>The Media3 FFmpeg module contains an unfinished video renderer class even for an audio-only
 * native build. The default factory discovers it by reflection whenever extensions are enabled.
 * Disabling extensions for video here preserves the platform MediaCodec video path, while the
 * superclass still installs {@code FfmpegAudioRenderer} as an audio fallback.</p>
 */
@UnstableApi
final class Media3AudioExtensionRenderersFactory extends DefaultRenderersFactory
{
    private final boolean allowEncodedAudioPassthrough;
    private final Media3PcmAudioProcessor pcmAudioProcessor;
    private final boolean nativeDvd;
    private final boolean dvdDecoderRecovery;

    Media3AudioExtensionRenderersFactory(Context context,
                                         boolean allowEncodedAudioPassthrough,
                                         int audioOffsetMs)
    {
        this(context, allowEncodedAudioPassthrough, audioOffsetMs, false);
    }

    Media3AudioExtensionRenderersFactory(Context context,
                                         boolean allowEncodedAudioPassthrough,
                                         int audioOffsetMs, boolean nativeDvd)
    {
        this(context, allowEncodedAudioPassthrough, audioOffsetMs, nativeDvd, true);
    }

    Media3AudioExtensionRenderersFactory(Context context,
                                         boolean allowEncodedAudioPassthrough,
                                         int audioOffsetMs, boolean nativeDvd,
                                         boolean dvdDecoderRecovery)
    {
        super(context);
        this.allowEncodedAudioPassthrough = allowEncodedAudioPassthrough;
        this.nativeDvd = nativeDvd;
        this.dvdDecoderRecovery = dvdDecoderRecovery;
        pcmAudioProcessor = allowEncodedAudioPassthrough
                ? null : new Media3PcmAudioProcessor(audioOffsetMs);
    }

    Media3PcmAudioProcessor getPcmAudioProcessor() { return pcmAudioProcessor; }

    @Override protected void buildTextRenderers(Context context, TextOutput output,
            Looper looper, @ExtensionRendererMode int mode, ArrayList<Renderer> out)
    {
        out.add(new TextRenderer(output, looper, new Media3Cea708DecoderFactory()));
    }

    @Override
    protected AudioSink buildAudioSink(Context context,
                                       boolean enableFloatOutput,
                                       boolean enableAudioTrackPlaybackParams)
    {
        if (allowEncodedAudioPassthrough)
            return super.buildAudioSink(
                    context, enableFloatOutput, enableAudioTrackPlaybackParams);

        // DEFAULT_AUDIO_CAPABILITIES advertises PCM only.  This prevents
        // Media3 from treating an Android TV device's broad HDMI/raw claims as
        // proof that its current sink can actually consume the selected AC3,
        // E-AC3, or DTS stream.  MediaCodec or the isolated Media3 FFmpeg audio
        // renderer then decodes to PCM while the selected video MediaCodec
        // remains fully hardware accelerated.
        // Use the context-free builder deliberately. In Media3 1.11 the
        // context-aware builder delegates capability discovery to a dynamic
        // AudioOutputProvider and ignores the explicit Builder capabilities;
        // that would silently restore the faulty vendor passthrough route.
        return new DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setAudioProcessors(new AudioProcessor[] { pcmAudioProcessor })
                // The Vibe PCM processor intentionally operates on signed
                // 16-bit samples so downmix and live offset behavior are
                // identical across Android versions and vendor sinks.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build();
    }

    @Override
    protected void buildVideoRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            long allowedVideoJoiningTimeMs,
            ArrayList<Renderer> out)
    {
        if (nativeDvd && dvdDecoderRecovery)
        {
            out.add(new NativeDvdVideoRenderer(context, getCodecAdapterFactory(), mediaCodecSelector,
                    allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener));
            return;
        }
        super.buildVideoRenderers(context, EXTENSION_RENDERER_MODE_OFF, mediaCodecSelector,
                enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
        // Keep the established platform-only video policy and replace only its
        // platform renderer. The audio extension/fallback path stays untouched.
        for (int i = 0; i < out.size(); i++)
        {
            if (out.get(i).getClass() == MediaCodecVideoRenderer.class)
            {
                out.set(i, new SurfaceVideoRenderer(context, getCodecAdapterFactory(), mediaCodecSelector,
                        allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener));
                break;
            }
        }
    }
}
