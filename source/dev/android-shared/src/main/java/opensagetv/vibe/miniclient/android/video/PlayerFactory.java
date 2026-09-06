package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl;

/**
 * Single construction point for Android media-player backends.
 *
 * Backend construction stays centralized so the same preference behaves identically
 * in both GDX and OpenGL renderers. Legacy Exo remains the default A/B baseline.
 */
public final class PlayerFactory
{
    private PlayerFactory()
    {
    }

    public static MiniPlayerPlugin create(AndroidUIController activity, PlayerBackend backend)
    {
        switch (backend)
        {
            case MEDIA3:
                return new Media3MediaPlayerImpl(activity);
            case IJKPLAYER:
                return new IJKMediaPlayerImpl(activity);
            case GSYPLAYER:
                return new GSYMediaPlayerImpl(activity);
            case EXOPLAYER:
            default:
                return new Exo2MediaPlayerImpl(activity);
        }
    }

    /**
     * DVD PUSH contains server-owned cell discontinuities and private-stream
     * layout that are commissioned through the Media3 DVD extractor.  Do not
     * let a persisted experimental backend make authored-disc playback
     * unusable; resolve it to the proven backend before construction.
     */
    public static PlayerBackend resolveForUrl(PlayerBackend requested, String urlString)
    {
        if (urlString != null && ("push:dvd".equals(urlString)
                || urlString.startsWith("push:dvd")
                || urlString.endsWith("/push:dvd")))
            return PlayerBackend.MEDIA3;
        return requested == null ? PlayerBackend.EXOPLAYER : requested;
    }
}
