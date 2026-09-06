package opensagetv.vibe.miniclient.video;

/** Capability gate for SageTV's legacy event-225 subtitle callback path. */
public final class LegacySubtitleCallbackPolicy
{
    private LegacySubtitleCallbackPolicy() {}

    public static boolean shouldAdvertise(String playerBackend)
    {
        if (playerBackend == null)
            return true; // the historical/default ExoPlayer backend
        return "exoplayer".equalsIgnoreCase(playerBackend)
                || "media3".equalsIgnoreCase(playerBackend)
                || "gsyplayer".equalsIgnoreCase(playerBackend);
    }
}
