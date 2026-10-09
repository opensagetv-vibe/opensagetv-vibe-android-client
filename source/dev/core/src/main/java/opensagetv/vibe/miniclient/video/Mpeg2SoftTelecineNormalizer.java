package opensagetv.vibe.miniclient.video;

/**
 * Rewrites only the MPEG-2 syntax that asks a decoder to perform 3:2 field
 * repetition.  The caller must first prove that the stream is progressive
 * film carried as soft telecine; this class deliberately performs no content
 * classification of its own.
 *
 * <p>The scanner is stateful so a start code split between TrackOutput input
 * chunks is still recognized.  Payload bytes are changed in place before the
 * same buffer is forwarded to MediaCodec.  No pictures are decoded, dropped,
 * copied, or reordered.</p>
 */
public final class Mpeg2SoftTelecineNormalizer
{
    private static final int SEQUENCE_HEADER_CODE = 0x000001B3;
    private static final int EXTENSION_START_CODE = 0x000001B5;
    private static final int SEQUENCE_EXTENSION_ID = 1;
    private static final int PICTURE_CODING_EXTENSION_ID = 8;
    private static final int FILM_FRAME_RATE_CODE = 1; // 24000/1001

    private int rollingStartCode = -1;
    private int pendingCode;
    private int pendingBytes;
    private int extensionId = -1;
    private long rewrittenSequenceHeaders;
    private long rewrittenSequenceExtensions;
    private long rewrittenPictureExtensions;

    public void reset()
    {
        rollingStartCode = -1;
        pendingCode = 0;
        pendingBytes = 0;
        extensionId = -1;
        rewrittenSequenceHeaders = 0L;
        rewrittenSequenceExtensions = 0L;
        rewrittenPictureExtensions = 0L;
    }

    /** Scans every byte and conditionally rewrites confirmed soft telecine. */
    public void consumeAndNormalize(byte[] data, int offset, int length, boolean enabled)
    {
        if (data == null || length <= 0)
            return;
        int start = Math.max(0, offset);
        int end = Math.min(data.length, start + length);
        for (int i = start; i < end; i++)
        {
            int value = data[i] & 0xFF;

            if (pendingCode == SEQUENCE_HEADER_CODE)
            {
                pendingBytes++;
                if (pendingBytes == 4)
                {
                    if (enabled)
                    {
                        data[i] = (byte) ((value & 0xF0) | FILM_FRAME_RATE_CODE);
                        rewrittenSequenceHeaders++;
                        value = data[i] & 0xFF;
                    }
                    clearPending();
                }
            }
            else if (pendingCode == EXTENSION_START_CODE)
            {
                pendingBytes++;
                if (pendingBytes == 1)
                    extensionId = value >>> 4;
                else if (extensionId == SEQUENCE_EXTENSION_ID && pendingBytes == 2)
                {
                    if (enabled)
                    {
                        data[i] = (byte) (value | 0x08); // progressive_sequence
                        rewrittenSequenceExtensions++;
                        value = data[i] & 0xFF;
                    }
                    clearPending();
                }
                else if (extensionId == PICTURE_CODING_EXTENSION_ID)
                {
                    if (pendingBytes == 4 && enabled)
                    {
                        // top_field_first and repeat_first_field are in this byte.
                        data[i] = (byte) (value & ~0x82);
                        value = data[i] & 0xFF;
                    }
                    else if (pendingBytes == 5)
                    {
                        if (enabled)
                        {
                            data[i] = (byte) (value | 0x80); // progressive_frame
                            rewrittenPictureExtensions++;
                            value = data[i] & 0xFF;
                        }
                        clearPending();
                    }
                }
                else if (pendingBytes >= 5)
                {
                    clearPending();
                }
            }

            rollingStartCode = (rollingStartCode << 8) | value;
            if (rollingStartCode == SEQUENCE_HEADER_CODE
                    || rollingStartCode == EXTENSION_START_CODE)
            {
                pendingCode = rollingStartCode;
                pendingBytes = 0;
                extensionId = -1;
            }
        }
    }

    private void clearPending()
    {
        pendingCode = 0;
        pendingBytes = 0;
        extensionId = -1;
    }

    public long getRewrittenSequenceHeaders() { return rewrittenSequenceHeaders; }
    public long getRewrittenSequenceExtensions() { return rewrittenSequenceExtensions; }
    public long getRewrittenPictureExtensions() { return rewrittenPictureExtensions; }
}
