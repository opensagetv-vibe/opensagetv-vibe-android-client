package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Mpeg2SoftTelecineNormalizerTest
{
    @Test
    public void rewritesSoftTelecineSyntaxWithoutChangingPayloadLength()
    {
        Mpeg2SoftTelecineNormalizer normalizer = new Mpeg2SoftTelecineNormalizer();
        byte[] bytes = concat(sequenceHeader(4), sequenceExtension(false),
                pictureCodingExtension(true, true, true));
        int length = bytes.length;

        normalizer.consumeAndNormalize(bytes, 0, bytes.length, true);

        assertEquals(length, bytes.length);
        assertEquals(1, bytes[7] & 0x0F);
        assertEquals(0x08, bytes[13] & 0x08);
        int picture = sequenceHeader(4).length + sequenceExtension(false).length;
        assertEquals(0, bytes[picture + 7] & 0x82);
        assertEquals(0x80, bytes[picture + 8] & 0x80);
        assertEquals(1L, normalizer.getRewrittenSequenceHeaders());
        assertEquals(1L, normalizer.getRewrittenSequenceExtensions());
        assertEquals(1L, normalizer.getRewrittenPictureExtensions());
    }

    @Test
    public void recognizesStartCodeSplitAcrossChunks()
    {
        Mpeg2SoftTelecineNormalizer normalizer = new Mpeg2SoftTelecineNormalizer();
        byte[] prefix = new byte[] { 0, 0 };
        byte[] suffix = new byte[] { 1, (byte) 0xB5, (byte) 0x80, 0, 0x03,
                (byte) 0x82, 0 };
        normalizer.consumeAndNormalize(prefix, 0, prefix.length, true);
        normalizer.consumeAndNormalize(suffix, 0, suffix.length, true);
        assertEquals(0, suffix[5] & 0x82);
        assertEquals(0x80, suffix[6] & 0x80);
        assertEquals(1L, normalizer.getRewrittenPictureExtensions());
    }

    @Test
    public void disabledScannerLeavesBytesUntouched()
    {
        Mpeg2SoftTelecineNormalizer normalizer = new Mpeg2SoftTelecineNormalizer();
        byte[] bytes = pictureCodingExtension(true, true, true);
        normalizer.consumeAndNormalize(bytes, 0, bytes.length, false);
        assertEquals(0x82, bytes[7] & 0x82);
        assertEquals(0x80, bytes[8] & 0x80);
        assertEquals(0L, normalizer.getRewrittenPictureExtensions());
    }

    private static byte[] sequenceHeader(int frameRateCode)
    {
        return new byte[] { 0, 0, 1, (byte) 0xB3, 0x2D, 0x01, (byte) 0xE0,
                (byte) frameRateCode };
    }

    private static byte[] sequenceExtension(boolean progressive)
    {
        return new byte[] { 0, 0, 1, (byte) 0xB5, 0x10,
                (byte) (progressive ? 0x08 : 0), 0, 0, 0 };
    }

    private static byte[] pictureCodingExtension(boolean topFieldFirst,
            boolean repeatFirstField, boolean progressiveFrame)
    {
        return new byte[] { 0, 0, 1, (byte) 0xB5, (byte) 0x80, 0, 0x03,
                (byte) ((topFieldFirst ? 0x80 : 0) | (repeatFirstField ? 0x02 : 0)),
                (byte) (progressiveFrame ? 0x80 : 0) };
    }

    private static byte[] concat(byte[]... arrays)
    {
        int size = 0;
        for (byte[] array : arrays) size += array.length;
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] array : arrays)
        {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
