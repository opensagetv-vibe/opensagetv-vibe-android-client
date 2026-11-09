package opensagetv.vibe.miniclient.video;

/** FFmpeg wrappers use case-sensitive constants for these MIME aliases. */
public final class FfmpegAudioMimePolicy
{
    private FfmpegAudioMimePolicy() { }

    public static String canonicalMimeType(String mimeType)
    {
        if ("audio/mpeg-l1".equalsIgnoreCase(mimeType)) return "audio/mpeg-L1";
        if ("audio/mpeg-l2".equalsIgnoreCase(mimeType)) return "audio/mpeg-L2";
        return mimeType;
    }
}
