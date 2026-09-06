package opensagetv.vibe.miniclient.android.video;

/** Provides an on-demand, listener-free snapshot for debug health inspection. */
public interface PlaybackHealthSource
{
    PlaybackHealthSnapshot capturePlaybackHealthSnapshot();
}
