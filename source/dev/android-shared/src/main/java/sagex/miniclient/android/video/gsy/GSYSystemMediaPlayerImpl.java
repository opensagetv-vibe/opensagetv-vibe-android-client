package sagex.miniclient.android.video.gsy;

import android.media.MediaDataSource;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

import sagex.miniclient.MiniPlayerPlugin;
import sagex.miniclient.android.MiniclientApplication;
import sagex.miniclient.android.ui.AndroidUIController;
import sagex.miniclient.android.video.BaseMediaPlayerImpl;
import sagex.miniclient.android.video.PlaybackDebugTrap;
import sagex.miniclient.media.SubtitleTrack;
import sagex.miniclient.uibridge.Dimension;
import sagex.miniclient.util.VerboseLogging;

/**
 * SageTV adapter for GSY's Android/System player choice.
 *
 * This uses android.media.MediaPlayer directly and therefore has no dependency on
 * the legacy IJK native decoder or GSY's IJK native decoder.
 */
public final class GSYSystemMediaPlayerImpl extends BaseMediaPlayerImpl<MediaPlayer, MediaDataSource>
{
    private long preSeekPos = -1;
    private boolean prepareStarted = false;
    private SurfaceHolder.Callback surfaceCallback;

    public GSYSystemMediaPlayerImpl(AndroidUIController activity)
    {
        super(activity, true, true);
    }

    @Override
    protected long getPlayerMediaTimeMillis(long lastServerTime)
    {
        return player == null ? 0 : player.getCurrentPosition();
    }

    @Override
    protected void setupPlayer(final String sageTVurl)
    {
        // BaseMediaPlayerImpl.load() already released the previous player and made the
        // video frame visible immediately before calling setupPlayer().  Releasing here
        // again hides/destroys that freshly-created SurfaceView and can leave
        // MediaPlayer.setDisplay() with a released Surface.
        preSeekPos = -1;
        prepareStarted = false;
        try
        {
            player = new MediaPlayer();

            player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener()
            {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height)
                {
                    setVideoSize(width, height, 1, 1);
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener()
            {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra)
                {
                    PlaybackDebugTrap.record("player_error_" + what + "_" + extra, GSYSystemMediaPlayerImpl.this);
                    log.error("GSY/System MediaPlayer error: {}, {}", what, extra);
                    playerFailed();
                    return true;
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    PlaybackDebugTrap.record("playback_complete", GSYSystemMediaPlayerImpl.this);
                    state = EOS_STATE;
                    eos = true;
                }
            });
            player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener()
            {
                @Override
                public void onSeekComplete(MediaPlayer mp)
                {
                    PlaybackDebugTrap.record("seek_complete", GSYSystemMediaPlayerImpl.this);
                    seekPending = false;
                }
            });
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                @Override
                public void onPrepared(MediaPlayer mp)
                {
                    PlaybackDebugTrap.record("player_prepared", GSYSystemMediaPlayerImpl.this);
                    playerReady = true;
                    state = PLAY_STATE;
                    mp.start();
                    if (!pushMode && preSeekPos >= 0)
                    {
                        seekToImpl(preSeekPos);
                        preSeekPos = -1;
                    }
                }
            });

            if (pushMode)
            {
                SagePushMediaDataSource source = new SagePushMediaDataSource();
                source.open(sageTVurl);
                dataSource = source;
                player.setDataSource(source);
                log.info("GSY/System using SageTV PUSH MediaDataSource");
            }
            else if (httpls)
            {
                player.setDataSource(sageTVurl);
                log.info("GSY/System using HTTP data source: {}", sageTVurl);
            }
            else
            {
                SagePullMediaDataSource source = new SagePullMediaDataSource(
                        MiniclientApplication.get().getClient().getConnectedServerInfo().address);
                source.open(sageTVurl);
                dataSource = source;
                player.setDataSource(source);
                log.info("GSY/System using SageTV PULL MediaDataSource");
            }

            prepareWhenSurfaceReady();
        }
        catch (Exception e)
        {
            PlaybackDebugTrap.record("setup_error_" + e.getClass().getSimpleName(), GSYSystemMediaPlayerImpl.this);
            log.error("GSY/System failed to create player", e);
            playerFailed();
        }
    }


    private void prepareWhenSurfaceReady()
    {
        final SurfaceView surfaceView = (SurfaceView) context.getVideoView();
        final SurfaceHolder holder = surfaceView.getHolder();

        surfaceCallback = new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(SurfaceHolder createdHolder)
            {
                attachSurfaceAndPrepare(createdHolder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder changedHolder, int format, int width, int height)
            {
                attachSurfaceAndPrepare(changedHolder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder destroyedHolder)
            {
                if (player != null)
                {
                    try { player.setDisplay(null); } catch (Throwable ignored) { }
                }
            }
        };
        holder.addCallback(surfaceCallback);

        if (holder.getSurface() != null && holder.getSurface().isValid())
        {
            attachSurfaceAndPrepare(holder);
        }
        else
        {
            log.info("GSY/System waiting for video surface before prepare");
        }
    }

    private void attachSurfaceAndPrepare(SurfaceHolder holder)
    {
        if (player == null || holder == null || holder.getSurface() == null || !holder.getSurface().isValid())
        {
            return;
        }

        try
        {
            player.setDisplay(holder);
            if (!prepareStarted)
            {
                prepareStarted = true;
                player.prepareAsync();
            }
        }
        catch (Throwable t)
        {
            PlaybackDebugTrap.record("surface_prepare_error_" + t.getClass().getSimpleName(), GSYSystemMediaPlayerImpl.this);
            log.error("GSY/System failed to attach video surface", t);
            playerFailed();
        }
    }

    private void detachSurfaceCallback()
    {
        if (surfaceCallback == null)
        {
            return;
        }
        try
        {
            ((SurfaceView) context.getVideoView()).getHolder().removeCallback(surfaceCallback);
        }
        catch (Throwable ignored)
        {
        }
        surfaceCallback = null;
    }

    private void seekToImpl(long timeMs)
    {
        if (player == null) return;
        seekPending = true;
        int safeTimeMs = timeMs <= 0 ? 0 : (timeMs >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeMs);
        PlaybackDebugTrap.record("backend_seek_invoke", GSYSystemMediaPlayerImpl.this);
        player.seekTo(safeTimeMs);
        PlaybackDebugTrap.record("backend_seek_return", GSYSystemMediaPlayerImpl.this);
    }

    @Override
    public void seek(long timeMS)
    {
        super.seek(timeMS);
        if (player == null || !playerReady || state == NO_STATE || state == LOADED_STATE)
        {
            preSeekPos = timeMS;
            return;
        }
        seekToImpl(timeMS);
    }

    @Override
    public void stop()
    {
        if (player != null)
        {
            try { player.stop(); } catch (Throwable ignored) { }
        }
        super.stop();
    }

    @Override
    public void pause()
    {
        super.pause();
        if (player != null && playerReady)
        {
            PlaybackDebugTrap.record("backend_pause_invoke", GSYSystemMediaPlayerImpl.this);
            try { player.pause(); } catch (Throwable ignored) { }
            PlaybackDebugTrap.record("backend_pause_return", GSYSystemMediaPlayerImpl.this);
        }
    }

    @Override
    public void play()
    {
        super.play();
        if (player != null && playerReady)
        {
            PlaybackDebugTrap.record("backend_play_invoke", GSYSystemMediaPlayerImpl.this);
            try { player.start(); } catch (Throwable ignored) { }
            PlaybackDebugTrap.record("backend_play_return", GSYSystemMediaPlayerImpl.this);
        }
    }

    @Override
    public Dimension getVideoDimensions()
    {
        if (player == null) return null;
        return new Dimension(player.getVideoWidth(), player.getVideoHeight());
    }

    @Override
    public void setAudioTrack(int streamPos)
    {
        if (player == null || !playerReady || streamPos < 0) return;
        try
        {
            MediaPlayer.TrackInfo[] tracks = player.getTrackInfo();
            int audioIndex = 0;
            for (int i = 0; i < tracks.length; i++)
            {
                if (tracks[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)
                {
                    if (audioIndex++ == streamPos)
                    {
                        player.selectTrack(i);
                        return;
                    }
                }
            }
        }
        catch (Throwable t)
        {
            log.warn("GSY/System could not select audio track {}", streamPos, t);
        }
    }

    @Override
    public int getSubtitleTrackCount()
    {
        return 0;
    }

    @Override
    public SubtitleTrack[] getSubtitleTracks()
    {
        return new SubtitleTrack[0];
    }

    @Override
    public void setSubtitleTrack(int streamPos)
    {
        if (streamPos != MiniPlayerPlugin.DISABLE_TRACK && VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.debug("GSY/System subtitle selection is not exposed yet: {}", streamPos);
        }
    }

    @Override
    public int getSelectedSubtitleTrack()
    {
        return MiniPlayerPlugin.DISABLE_TRACK;
    }

    @Override
    protected void releaseDataSource()
    {
        if (dataSource != null)
        {
            try { dataSource.close(); } catch (IOException ignored) { }
        }
        super.releaseDataSource();
    }

    @Override
    protected void releasePlayer()
    {
        detachSurfaceCallback();
        prepareStarted = false;
        if (player != null)
        {
            try { player.setDisplay(null); } catch (Throwable ignored) { }
            try { player.reset(); } catch (Throwable ignored) { }
            try { player.release(); } catch (Throwable ignored) { }
            try { clearSurface(); } catch (Throwable ignored) { }
        }
        player = null;
        super.releasePlayer();
    }
}
