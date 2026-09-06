package opensagetv.vibe.miniclient.android.video;

import java.util.Locale;

import opensagetv.vibe.miniclient.prefs.PrefStore;

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

    /**
     * Persistent decoder opt-out shared by every backend. This is the Vibe
     * equivalent of Kodi's explicit decoder filter: it is user-controlled and
     * must never be populated from transient runtime observations.
     */
    public static String disabledCodecKey(String codecName)
    {
        return "disabled_" + (codecName == null ? "" : codecName);
    }

    public static boolean isCodecDisabled(PrefStore prefs, String codecName)
    {
        return prefs != null && codecName != null
                && prefs.getBoolean(disabledCodecKey(codecName), false);
    }
}
