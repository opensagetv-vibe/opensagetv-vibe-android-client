package opensagetv.vibe.miniclient.video;

/**
 * Determines whether SageTV has placed active video in an embedded preview.
 *
 * <p>The test deliberately requires both dimensions to be substantially
 * smaller than the UI. This avoids treating ordinary letterboxing or a 4:3
 * video fitted to a 16:9 display as an embedded preview.</p>
 */
public final class FullscreenPlaybackPolicy
{
    private static final int PREVIEW_PERCENT = 75;

    private FullscreenPlaybackPolicy()
    {
    }

    public static boolean isEmbeddedPreview(int videoWidth, int videoHeight,
                                            int screenWidth, int screenHeight)
    {
        if (videoWidth <= 0 || videoHeight <= 0 || screenWidth <= 0 || screenHeight <= 0)
        {
            return false;
        }

        return (long) videoWidth * 100 < (long) screenWidth * PREVIEW_PERCENT
                && (long) videoHeight * 100 < (long) screenHeight * PREVIEW_PERCENT;
    }

    /**
     * Returns true when the server/STV has already entered its playback UI.
     * SageTV's TV command is a toggle, so sending it from this state returns
     * the user to the previous menu instead of promoting a preview.
     */
    public static boolean isPlaybackMenu(String menuName)
    {
        return menuName != null && menuName.toLowerCase(java.util.Locale.US).contains("osd");
    }

    /**
     * Decide whether the compatibility TV command may promote a stock-STV
     * preview. Popups and an existing playback menu always retain server/STV
     * ownership of navigation.
     */
    public static boolean shouldPromote(String menuName, String popupName,
                                        int videoWidth, int videoHeight,
                                        int screenWidth, int screenHeight)
    {
        return !isPlaybackMenu(menuName)
                && (popupName == null || popupName.trim().isEmpty())
                && isEmbeddedPreview(videoWidth, videoHeight, screenWidth, screenHeight);
    }
}
