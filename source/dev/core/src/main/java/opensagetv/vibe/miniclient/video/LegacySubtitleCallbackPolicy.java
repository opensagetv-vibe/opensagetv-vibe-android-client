package opensagetv.vibe.miniclient.video;

/** Capability gate for SageTV's legacy event-225 subtitle callback path. */
public final class LegacySubtitleCallbackPolicy
{
    private LegacySubtitleCallbackPolicy() {}

    /** A single source must own SageTV's stateful CEA character/control stream. */
    public static boolean shouldForwardFixedSource(boolean nativeCeaObserved)
    {
        // Decoded output retains source captions on some Fixed encoders. It
        // carries the player's exact presentation timestamps and is preferred
        // once real CEA is observed. Streams without retained CEA continue
        // consuming the source tap (including Direct Transcode).
        return !nativeCeaObserved;
    }

    public static boolean shouldAdvertise(String playerBackend)
    {
        if (playerBackend == null)
            return true; // the historical/default ExoPlayer backend
        return "exoplayer".equalsIgnoreCase(playerBackend)
                || "media3".equalsIgnoreCase(playerBackend)
                || "gsyplayer".equalsIgnoreCase(playerBackend);
    }

    /** Bundled ARM glue has a software A53 callback; opaque MediaCodec does not. */
    public static boolean shouldAdvertise(String playerBackend, String decoding, String architecture)
    {
        if (!"ijkplayer".equalsIgnoreCase(playerBackend)) return shouldAdvertise(playerBackend);
        if (!"software".equalsIgnoreCase(decoding) || architecture == null) return false;
        String arch = architecture.toLowerCase(java.util.Locale.US);
        return arch.equals("aarch64") || arch.equals("arm64") || arch.equals("arm64-v8a")
                || arch.equals("arm") || arch.startsWith("armv7") || arch.equals("armeabi-v7a");
    }
}
