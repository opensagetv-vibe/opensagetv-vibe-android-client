package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.content.Context;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.AudioProcessor;

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
