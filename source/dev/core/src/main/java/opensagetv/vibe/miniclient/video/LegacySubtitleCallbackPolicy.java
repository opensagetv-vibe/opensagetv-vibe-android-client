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
}
