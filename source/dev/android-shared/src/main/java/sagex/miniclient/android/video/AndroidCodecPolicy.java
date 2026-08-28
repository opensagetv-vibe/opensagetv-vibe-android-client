package sagex.miniclient.android.video;

import java.util.Locale;

/** Shared codec-name classification used by the Exo, Media3 and IJK selectors. */
public final class AndroidCodecPolicy
{
    private AndroidCodecPolicy()
    {
    }

    public static boolean isVideoMimeType(String mimeType)
    {
        return mimeType != null && mimeType.toLowerCase(Locale.US).startsWith("video/");
    }

    public static boolean isSoftwareCodecName(String codecName)
    {
        if (codecName == null)
        {
            return true;
        }

        String lower = codecName.toLowerCase(Locale.US);
        return lower.startsWith("omx.google.")
                || lower.startsWith("c2.android.")
                || lower.startsWith("omx.ffmpeg.")
                || lower.startsWith("ffmpeg")
                || lower.contains(".sw.")
                || lower.contains("software");
    }
}
