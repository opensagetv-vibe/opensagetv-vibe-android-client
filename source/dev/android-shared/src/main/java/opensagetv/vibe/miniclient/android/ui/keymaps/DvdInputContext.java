package opensagetv.vibe.miniclient.android.ui.keymaps;

import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.uibridge.RectangleF;
import opensagetv.vibe.miniclient.video.FullscreenPlaybackPolicy;
import opensagetv.vibe.miniclient.video.HasVideoInfo;
import opensagetv.vibe.miniclient.video.VideoInfoResponse;

/** DVD input requires a full-screen STV playback context, not just visible video. */
public final class DvdInputContext
{
    private DvdInputContext() { }

    static boolean allowsControls(String menu, String popup, int width, int height, int screenWidth, int screenHeight)
    {
        // Check both signals: menu hints can lag SETVIDEORECT, and Android's
        // SurfaceView can cover the screen while SageTV requests a tiny preview.
        // Unknown geometry fails safely to ordinary STV menu navigation.
        return FullscreenPlaybackPolicy.isPlaybackMenu(menu)
                && (popup == null || popup.trim().isEmpty())
                && width > 0 && height > 0 && screenWidth > 0 && screenHeight > 0
                && !FullscreenPlaybackPolicy.isEmbeddedPreview(width, height, screenWidth, screenHeight);
    }

    public static boolean isFullscreen(MiniClient client)
    {
        try
        {
            if (client == null || !client.isVideoVisible() || client.getCurrentConnection() == null) return false;
            MiniPlayerPlugin player = client.getPlayer();
            if (!(player instanceof HasVideoInfo)) return false;
            MenuHint hint = client.getCurrentConnection().getMenuHint();
            // This is a copy of cached rendering metadata, not a decoder probe.
            VideoInfoResponse info = ((HasVideoInfo) player).getVideoInfo();
            if (hint == null || info == null || info.videoInfo == null) return false;
            RectangleF dest = info.videoInfo.destRect;
            RectangleF screen = info.uiScreenSizePixels;
            return dest != null && screen != null && allowsControls(hint.menuName, hint.popupName,
                    Math.round(dest.width), Math.round(dest.height), Math.round(screen.width), Math.round(screen.height));
        }
        catch (RuntimeException unavailable) { return false; }
    }
}
