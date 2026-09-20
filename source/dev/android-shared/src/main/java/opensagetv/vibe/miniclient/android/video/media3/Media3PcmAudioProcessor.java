package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decoded-PCM post processor used by Vibe's Media3 output policy.
 *
 * <p>It downmixes multichannel signed 16-bit PCM to stereo so Android cannot
 * silently route decoded 5.1 PCM back through a delayed Dolby MAT path. It
 * also applies a bounded live A/V correction by inserting silence (positive
 * offset: audio later) or dropping PCM frames (negative offset: audio
 * earlier). SageTV's reported media clock is deliberately unchanged.</p>
 */
final class Media3PcmAudioProcessor extends BaseAudioProcessor
{
    private volatile int requestedOffsetMs;
    private int appliedOffsetMs;
    private long pendingAdjustmentFrames;

    Media3PcmAudioProcessor(int initialOffsetMs)
    {
        requestedOffsetMs = clamp(initialOffsetMs);
    }

    synchronized void setOffsetMillis(int offsetMs)
    {
        int bounded = clamp(offsetMs);
        requestedOffsetMs = bounded;
        if (inputAudioFormat.sampleRate > 0)
        {
            pendingAdjustmentFrames += millisecondsToFrames(
                    bounded - appliedOffsetMs, inputAudioFormat.sampleRate);
            appliedOffsetMs = bounded;
        }
    }

    int getOffsetMillis()
    {
        return requestedOffsetMs;
    }

    @Override
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat input)
            throws AudioProcessor.UnhandledAudioFormatException
    {
        if (input.encoding != C.ENCODING_PCM_16BIT || input.channelCount <= 0)
            throw new AudioProcessor.UnhandledAudioFormatException(input);
        return new AudioProcessor.AudioFormat(input.sampleRate, 2, input.encoding);
    }

    @Override
    protected synchronized void onFlush()
    {
        appliedOffsetMs = requestedOffsetMs;
        pendingAdjustmentFrames = millisecondsToFrames(
                appliedOffsetMs, inputAudioFormat.sampleRate);
    }

    @Override
    protected synchronized void onReset()
    {
        appliedOffsetMs = 0;
        pendingAdjustmentFrames = 0L;
    }

    @Override
    public synchronized void queueInput(ByteBuffer inputBuffer)
    {
        inputBuffer.order(ByteOrder.nativeOrder());
        int inputChannels = inputAudioFormat.channelCount;
        int inputBytesPerFrame = inputChannels * 2;
        int inputFrames = inputBuffer.remaining() / inputBytesPerFrame;

        long dropFrames = Math.min(inputFrames, Math.max(0L, -pendingAdjustmentFrames));
        if (dropFrames > 0L)
        {
            inputBuffer.position(inputBuffer.position() + (int) dropFrames * inputBytesPerFrame);
            inputFrames -= (int) dropFrames;
            pendingAdjustmentFrames += dropFrames;
        }

        long silenceFrames = Math.max(0L, pendingAdjustmentFrames);
        int outputFrames = inputFrames + (int) Math.min(Integer.MAX_VALUE, silenceFrames);
        ByteBuffer output = replaceOutputBuffer(outputFrames * 4);
        output.order(ByteOrder.nativeOrder());
        for (long frame = 0; frame < silenceFrames; frame++)
        {
            output.putShort((short) 0);
            output.putShort((short) 0);
        }
        pendingAdjustmentFrames -= silenceFrames;

        while (inputBuffer.remaining() >= inputBytesPerFrame)
        {
            mixFrame(inputBuffer, output, inputChannels);
        }
        inputBuffer.position(inputBuffer.limit());
        output.flip();
    }

    private static void mixFrame(ByteBuffer input, ByteBuffer output, int channels)
    {
        if (channels == 1)
        {
            short mono = input.getShort();
            output.putShort(mono);
            output.putShort(mono);
            return;
        }

        float left = input.getShort();
        float right = input.getShort();
        float leftWeight = 1f;
        float rightWeight = 1f;
        for (int channel = 2; channel < channels; channel++)
        {
            float sample = input.getShort();
            if (channel == 2) // center
            {
                left += sample * 0.7071f;
                right += sample * 0.7071f;
                leftWeight += 0.7071f;
                rightWeight += 0.7071f;
            }
            else if (channel == 3) // LFE
            {
                left += sample * 0.5f;
                right += sample * 0.5f;
                leftWeight += 0.5f;
                rightWeight += 0.5f;
            }
            else if ((channel & 1) == 0)
            {
                left += sample * 0.7071f;
                leftWeight += 0.7071f;
            }
            else
            {
                right += sample * 0.7071f;
                rightWeight += 0.7071f;
            }
        }
        output.putShort(toPcm16(left / leftWeight));
        output.putShort(toPcm16(right / rightWeight));
    }

    private static short toPcm16(float value)
    {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
    }

    private static long millisecondsToFrames(int milliseconds, int sampleRate)
    {
        return sampleRate <= 0 ? 0L : Math.round(milliseconds * sampleRate / 1000.0d);
    }

    private static int clamp(int value)
    {
        return Math.max(-4_000, Math.min(4_000, value));
    }
}
