package opensagetv.vibe.miniclient.android.video.exoplayer2;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

public class Exo2PcmAudioProcessorTest
{
    @Test
    public void changesOffsetLiveByDroppingDecodedFrames() throws Exception
    {
        Exo2PcmAudioProcessor processor = new Exo2PcmAudioProcessor(10);
        processor.configure(new AudioProcessor.AudioFormat(
                48_000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();

        ByteBuffer first = stereoFrames(1);
        processor.queueInput(first);
        assertEquals((480 + 1) * 4, processor.getOutput().remaining());

        processor.setOffsetMillis(0);
        ByteBuffer second = stereoFrames(481);
        processor.queueInput(second);
        // Removing the previous +10 ms delay drops 480 frames live.
        assertEquals(4, processor.getOutput().remaining());
        assertEquals(0, processor.getOffsetMillis());
    }

    private static ByteBuffer stereoFrames(int count)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++)
            buffer.putShort((short) 100).putShort((short) -100);
        return (ByteBuffer) buffer.flip();
    }
}
