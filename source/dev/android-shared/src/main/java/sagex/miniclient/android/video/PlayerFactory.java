package sagex.miniclient.android.video;

import sagex.miniclient.MiniPlayerPlugin;
import sagex.miniclient.android.ui.AndroidUIController;
import sagex.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
import sagex.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl;
import sagex.miniclient.android.video.gsy.GSYMediaPlayerImpl;
import sagex.miniclient.android.video.media3.Media3MediaPlayerImpl;

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
}
