package opensagetv.vibe.miniclient.video;

/**
 * Decides whether a recoverable player error should interrupt playback with a
 * user-visible message. The error is still logged and handled by the player.
 */
public final class PlaybackErrorPresentationPolicy
{
    public static final String PARSING_CONTAINER_UNSUPPORTED =
            "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED";

    private PlaybackErrorPresentationPolicy()
    {
    }

    public static boolean shouldShowFirstError(String errorCodeName,
            boolean firstVideoFrameRendered)
    {
        // During server-owned seek/FF/RW transitions, Push and Fixed streams
        // can briefly expose an empty or incomplete replacement segment. Both
        // Exo generations report this code and then recover after reprepare.
        // Keep genuine startup/container failures visible by suppressing it
        // only after this playback load has already rendered video.
        return !firstVideoFrameRendered
                || !PARSING_CONTAINER_UNSUPPORTED.equals(errorCodeName);
    }
}
