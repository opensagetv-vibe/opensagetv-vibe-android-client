package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

public class Media3PcmAudioProcessorTest
{
    @Test
    public void downmixesSixChannelsAndAppliesPositiveOffset() throws Exception
    {
        Media3PcmAudioProcessor processor = new Media3PcmAudioProcessor(10);
        AudioProcessor.AudioFormat output = processor.configure(
                new AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT));
        assertEquals(2, output.channelCount);
        processor.flush();

        ByteBuffer input = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
        input.putShort((short) 1000).putShort((short) -1000)
                .putShort((short) 500).putShort((short) 0)
                .putShort((short) 250).putShort((short) -250).flip();
        processor.queueInput(input);
        ByteBuffer rendered = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals((480 + 1) * 4, rendered.remaining());
        assertEquals(0, rendered.getShort());
        assertEquals(0, rendered.getShort());
        rendered.position(rendered.limit() - 4);
        // The final frame is real downmixed content rather than inserted silence.
        int left = rendered.getShort();
        int right = rendered.getShort();
        org.junit.Assert.assertTrue(left != 0 || right != 0);
    }
}
