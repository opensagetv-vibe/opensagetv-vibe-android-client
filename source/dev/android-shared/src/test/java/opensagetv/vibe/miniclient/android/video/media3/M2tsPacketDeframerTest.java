package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class M2tsPacketDeframerTest
{
    @Test public void removesFourBytePrefixAcrossArbitraryReadSizes() throws Exception
    {
        byte[] source = packets(3);
        ChunkedInput input = new ChunkedInput(source, 7);
        M2tsPacketDeframer deframer = new M2tsPacketDeframer();
        byte[] output = new byte[188 * 3];
        int total = 0;
        while (total < output.length)
        {
            int count = deframer.read(input, output, total,
                    Math.min(113, output.length - total));
            if (count < 0) break;
            total += count;
        }
        assertEquals(output.length, total);

        byte[] expected = new byte[output.length];
        for (int packet = 0; packet < 3; packet++)
            System.arraycopy(source, packet * 192 + 4, expected, packet * 188, 188);
        assertArrayEquals(expected, output);
        assertEquals(-1, deframer.read(input, new byte[8], 0, 8));
    }

    @Test(expected = IOException.class)
    public void rejectsMissingTsSyncAtDeclaredPacketBoundary() throws Exception
    {
        byte[] source = packets(2);
        source[4] = 0;
        source[192 + 4] = 0;
        new M2tsPacketDeframer().read(new ChunkedInput(source, 192), new byte[188], 0, 188);
    }

    @Test
    public void relocksWhenReaderStartsBetweenM2tsPackets() throws Exception
    {
        byte[] source = packets(4);
        byte[] displaced = new byte[13 + source.length];
        Arrays.fill(displaced, 0, 13, (byte) 0x55);
        System.arraycopy(source, 0, displaced, 13, source.length);
        ChunkedInput input = new ChunkedInput(displaced, 17);
        M2tsPacketDeframer deframer = new M2tsPacketDeframer();
        byte[] output = new byte[188 * 4];
        int total = 0;
        while (total < output.length)
        {
            int count = deframer.read(input, output, total,
                    Math.min(73, output.length - total));
            if (count < 0) break;
            total += count;
        }
        assertEquals(output.length, total);

        byte[] expected = new byte[output.length];
        for (int packet = 0; packet < 4; packet++)
            System.arraycopy(source, packet * 192 + 4, expected, packet * 188, 188);
        assertArrayEquals(expected, output);
    }

    private static byte[] packets(int count)
    {
        byte[] result = new byte[count * 192];
        for (int packet = 0; packet < count; packet++)
        {
            int start = packet * 192;
            result[start] = (byte) packet;
            result[start + 4] = 0x47;
            for (int index = 5; index < 192; index++)
                result[start + index] = (byte) (packet * 31 + index);
        }
        return result;
    }

    private static final class ChunkedInput implements M2tsPacketDeframer.Input
    {
        private final byte[] source;
        private final int chunk;
        private int position;

        ChunkedInput(byte[] source, int chunk)
        {
            this.source = source;
            this.chunk = chunk;
        }

        @Override public int read(byte[] buffer, int offset, int length)
        {
            if (position >= source.length) return -1;
            int count = Math.min(Math.min(length, chunk), source.length - position);
            System.arraycopy(source, position, buffer, offset, count);
            position += count;
            return count;
        }
    }
}
