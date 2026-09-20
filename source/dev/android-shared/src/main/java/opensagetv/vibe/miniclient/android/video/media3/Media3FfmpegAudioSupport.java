package opensagetv.vibe.miniclient.android.video.media3;

import android.util.Log;

import androidx.media3.decoder.ffmpeg.FfmpegLibrary;

/** Configures the Media3 FFmpeg audio extension without colliding with legacy Exo JNI. */
public final class Media3FfmpegAudioSupport
{
    private static final String TAG = "VibeMedia3Ffmpeg";
    private static final String NATIVE_LIBRARY = "media3ffmpegJNI";
    private static boolean configured;

    private Media3FfmpegAudioSupport()
    {
    }

    /** Must run before Media3 attempts to load its FFmpeg JNI library. */
    public static synchronized void configure()
    {
        if (configured)
            return;
        FfmpegLibrary.setLibraries(NATIVE_LIBRARY);
        configured = true;
        boolean available = FfmpegLibrary.isAvailable();
        Log.i(TAG, "configured library=" + NATIVE_LIBRARY
                + " available=" + available
                + " version=" + (available ? FfmpegLibrary.getVersion() : "unavailable")
                + " ac3=" + (available && FfmpegLibrary.supportsFormat("audio/ac3"))
                + " mp2=" + (available && FfmpegLibrary.supportsFormat("audio/mpeg-L2")));
    }

    public static boolean isAvailable()
    {
        configure();
        return FfmpegLibrary.isAvailable();
    }

    public static boolean supportsFormat(String mimeType)
    {
        configure();
        return FfmpegLibrary.supportsFormat(canonicalMimeType(mimeType));
    }

    /**
     * Media3's FFmpeg extension compares the MPEG layer MIME aliases with
     * case-sensitive constants even though MIME types themselves are
     * case-insensitive. SageTV and Android commonly report these aliases in
     * lower case. Canonicalize only the two affected aliases before capability
     * negotiation so a playable MP2/MP1 stream does not make a stock server
     * fall back to its low-resolution transcoder.
     */
    static String canonicalMimeType(String mimeType)
    {
        if (mimeType == null)
            return null;
        if ("audio/mpeg-l1".equalsIgnoreCase(mimeType))
            return "audio/mpeg-L1";
        if ("audio/mpeg-l2".equalsIgnoreCase(mimeType))
            return "audio/mpeg-L2";
        return mimeType;
    }
}
