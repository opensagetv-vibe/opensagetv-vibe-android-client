package sagex.miniclient.android.video.media3;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.datasource.DataSource;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.ui.SubtitleView;
import androidx.media3.common.VideoSize;


import sagex.miniclient.MiniPlayerPlugin;
import sagex.miniclient.android.MiniclientApplication;
import sagex.miniclient.android.ui.AndroidUIController;
import sagex.miniclient.android.util.Logger;
import sagex.miniclient.android.video.BaseMediaPlayerImpl;
import sagex.miniclient.android.video.PlaybackDebugTrap;
import sagex.miniclient.android.video.PlayerRuntimeTuning;
import sagex.miniclient.android.video.DecodingMethod;
import sagex.miniclient.android.video.MediaSessionCallbackHandler;
import sagex.miniclient.media.SubtitleCodec;
import sagex.miniclient.media.SubtitleTrack;
import sagex.miniclient.prefs.PrefStore;
import sagex.miniclient.uibridge.Dimension;
import sagex.miniclient.util.Utils;
import sagex.miniclient.util.VerboseLogging;

import java.util.concurrent.locks.ReentrantLock;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * Created by seans on 24/09/16.
 */

public class Media3MediaPlayerImpl extends BaseMediaPlayerImpl<ExoPlayer, DataSource>
{
    static final Logger log = Logger.getLogger(Media3MediaPlayerImpl.class);
    static final int MAX_PLAYBACK_RETRY_COUNT = 12;
    // v0.5.67 restores the proven 8x PCR search baseline. The v0.5.66 4x
    // experiment did not improve the ~35 second Pull Comskip stall, so the next
    // controlled change targets sync-point/preroll behavior instead.
    static final int PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER = 8;
    static final int PULL_MIN_BUFFER_MS = 5000;
    static final int PULL_MAX_BUFFER_MS = 20000;
    static final int PULL_BUFFER_FOR_PLAYBACK_MS = 500;
    static final int PULL_BUFFER_AFTER_REBUFFER_MS = 1000;
    // v0.5.69: Media3 did not benefit from direction-aware NEXT/PREVIOUS sync,
    // while the 10-second reprepare reset useful extractor/decoder progress and
    // stretched ~35 second Comskip recovery to ~44 seconds. Keep Media3 on its
    // original closest-sync policy and disable the blind reprepare fallback.
    static final long PULL_SEEK_RECOVERY_DELAY_MS = 10000L;
    static final boolean PULL_SEEK_RECOVERY_REPREPARE_ENABLED = false;

    private MediaSource mediaSource;
    private long playbackStartPosition = -1;
    private int initialAudioTrackIndex = -1;
    private long currentPlaybackPosition = 0;
    private ReentrantLock playbackPositionLock;
    private DefaultTrackSelector trackSelector;
    private int selectedSubtitleTrack = DISABLE_TRACK;

    private boolean errorState = false;
    private int retryCount = 0;

    private boolean showCaptions = false;
    private Handler handler;
    private Runnable progressRunnable;
    private Runnable pullSeekRecoveryRunnable;
    private long pullSeekGeneration;

    MediaSessionCompat mediaSession;

    private SubtitleView subView;

    public Media3MediaPlayerImpl(AndroidUIController activity)
    {
        super(activity, true, false);
        playbackPositionLock = new ReentrantLock();
    }

    public long getPlaybackPosition()
    {
        long position = 0;

        try
        {
            playbackPositionLock.lock();
            position = this.currentPlaybackPosition;
        }
        catch (Exception ex)
        {
            log.logError("Unexpected error getting playback position", ex);
        }
        finally
        {
            playbackPositionLock.unlock();
        }

        return position;
    }

    public void setPlaybackPosition(long position)
    {
        try
        {
            playbackPositionLock.lock();

            if (position > 0)
            {
                currentPlaybackPosition = position;
            }
            else
            {
                //Set to zero if less than zero;
                currentPlaybackPosition = 0;
            }

        }
        catch (Exception ex)
        {
            log.logError("Unexpected error setting playback position", ex);

        }
        finally
        {
            playbackPositionLock.unlock();
        }
    }

    boolean ExoIsPlaying()
    {
        if (player == null)
        {
            return false;
        }

        return player.getPlayWhenReady();
    }

    void ExoPause()
    {
        if (player == null)
        {
            return;
        }

        log.logInfo("Pause was called");
        player.setPlayWhenReady(false);
    }

    void ExoStart()
    {
        if (player == null)
        {
            return;
        }
        log.logDebug("Start was called");
        player.setPlayWhenReady(true);
    }

    protected void releasePlayer()
    {
        cancelPullSeekRecovery();
        if(mediaSession != null)
        {
            log.logDebug("Releaseing Android Media Session");
            mediaSession.setActive(false);
            mediaSession.release();
        }

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (player != null)
                {
                    try
                    {
                        if (ExoIsPlaying())
                        {
                            ExoPause();
                        }
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error pausing video during player releasing", ex);
                    }

                    try
                    {
                        player.release();
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error calling release on player", ex);
                    }

                    player = null;
                    Media3MediaPlayerImpl.super.releasePlayer();
                }
            }
        });

        this.RemoveSubTitleView();
    }

    @Override
    public Dimension getVideoDimensions()
    {
        log.logDebug("getVideoDimensions");

        if (player != null)
        {
            if (player.getVideoFormat() != null)
            {
                Dimension d = new Dimension(player.getVideoFormat().width, player.getVideoFormat().height);
                log.logDebug("getVideoSize(): " + d);

                return d;
            }
            else
            {
                log.logDebug("getVideoDimensions: player.getFormat is null");
            }
        }
        else
        {
            log.logDebug("getVideoDimensions: player is null");
        }
        return null;
    }

    @Override
    public long getPlayerMediaTimeMillis(long lastServerTime)
    {
        long position = this.getPlaybackPosition();

        //log.debug("Media3Logging - getPlayerMediaTimeMillis Called lastServerTime=" + Utils.toHHMMSS(lastServerTime) + " position=" + Utils.toHHMMSS(position));

        if (lastServerTime < 0)
        {
            log.logDebug("getPlayerMediaTimeMillis(): Flush was called waiting for last serverTime to be > 0");
            return -1;
        }

        return lastServerTime + position;
    }

    @Override
    public void stop()
    {
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                log.logDebug("Stop called");
                if(player != null)
                {
                    player.stop();
                }

                if(mediaSession != null)
                {
                    mediaSession.setActive(false);
                }

                if (playerReady)
                {
                    if (player == null)
                    {
                        return;
                    }

                    player.setPlayWhenReady(false);
                }
            }
        });

        super.stop();
    }

    @Override
    public void pause()
    {
        log.logDebug("Pause called");

        if (this.getState() == MiniPlayerPlugin.PAUSE_STATE && !pushMode)
        {
            log.logDebug("Already in pause state.  Seeking frame instead...");
            //TODO: Could not find the framerate in ExoPlayer.  Going to assume 30fps for now.
            this.seek(this.getPlaybackPosition() + Math.round(1000.0 / 30.0));
            return;
        }


        super.pause();

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (playerReady)
                {
                    PlaybackDebugTrap.record("backend_pause_invoke", Media3MediaPlayerImpl.this);
                    ExoPause();
                    PlaybackDebugTrap.record("backend_pause_return", Media3MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
    }

    @Override
    public void play()
    {
        log.logDebug("Play called");

        super.play();

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (playerReady)
                {
                    PlaybackDebugTrap.record("backend_play_invoke", Media3MediaPlayerImpl.this);
                    ExoStart();
                    PlaybackDebugTrap.record("backend_play_return", Media3MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
    }

    private void cancelPullSeekRecovery()
    {
        pullSeekGeneration++;
        if (handler != null && pullSeekRecoveryRunnable != null)
        {
            handler.removeCallbacks(pullSeekRecoveryRunnable);
        }
        pullSeekRecoveryRunnable = null;
    }

    private SeekParameters choosePullSeekParameters(long currentPositionMs, long targetPositionMs)
    {
        String policy = PlayerRuntimeTuning.getMedia3SeekPolicy();
        if ("next".equals(policy)) return SeekParameters.NEXT_SYNC;
        if ("previous".equals(policy)) return SeekParameters.PREVIOUS_SYNC;
        if ("directional".equals(policy))
        {
            long deltaMs = targetPositionMs - currentPositionMs;
            if (Math.abs(deltaMs) < PlayerRuntimeTuning.getDirectionalSyncMinDeltaMs()) return SeekParameters.CLOSEST_SYNC;
            return deltaMs > 0 ? SeekParameters.NEXT_SYNC : SeekParameters.PREVIOUS_SYNC;
        }
        return SeekParameters.CLOSEST_SYNC;
    }

    private void armPullSeekRecovery(final long targetPositionMs)
    {
        if (!PlayerRuntimeTuning.isMedia3SeekRecoveryEnabled()
                || pushMode || handler == null || player == null || mediaSource == null)
        {
            return;
        }

        if (pullSeekRecoveryRunnable != null)
        {
            handler.removeCallbacks(pullSeekRecoveryRunnable);
        }
        final long generation = ++pullSeekGeneration;
        final boolean resumeWhenReady = player.getPlayWhenReady();
        PlaybackDebugTrap.record("pull_seek_recovery_armed", Media3MediaPlayerImpl.this);

        pullSeekRecoveryRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if (generation != pullSeekGeneration || pushMode || player == null || mediaSource == null)
                {
                    return;
                }
                pullSeekRecoveryRunnable = null;
                if (player.getPlaybackState() != Player.STATE_BUFFERING)
                {
                    return;
                }

                try
                {
                    seekPending = true;
                    PlaybackDebugTrap.record("pull_seek_reprepare_before", Media3MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, targetPositionMs);
                    player.prepare();
                    player.setPlayWhenReady(resumeWhenReady);
                    PlaybackDebugTrap.record("pull_seek_reprepare_after", Media3MediaPlayerImpl.this);
                    log.logDebug("Pull seek recovery reprepare at " + targetPositionMs + "ms after " + PlayerRuntimeTuning.getMedia3SeekRecoveryDelayMs() + "ms buffering watchdog");
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("pull_seek_reprepare_error_" + ex.getClass().getSimpleName(), Media3MediaPlayerImpl.this);
                    log.logError("Pull seek recovery reprepare failed at " + targetPositionMs + "ms", ex);
                }
            }
        };
        handler.postDelayed(pullSeekRecoveryRunnable, PlayerRuntimeTuning.getMedia3SeekRecoveryDelayMs());
    }

    private void seekToImpl(long timeInMillis)
    {
        if (timeInMillis >= 0)
        {
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        long currentPositionMs = player.getContentPosition();
                        log.logDebug("Seek Called - Current Position: " + currentPositionMs + "  Seek Request: " + timeInMillis + " Difference: " + (currentPositionMs - timeInMillis));
                        if (!pushMode)
                        {
                            log.logDebug("Pull seek capability: seekable=" + player.isCurrentMediaItemSeekable()
                                    + ", durationMs=" + player.getDuration() + ", bufferedPositionMs=" + player.getBufferedPosition());
                            SeekParameters seekParameters = choosePullSeekParameters(currentPositionMs, timeInMillis);
                            player.setSeekParameters(seekParameters);
                            String seekPolicy = seekParameters == SeekParameters.NEXT_SYNC ? "next_sync"
                                    : (seekParameters == SeekParameters.PREVIOUS_SYNC ? "previous_sync" : "closest_sync");
                            PlaybackDebugTrap.record("pull_seek_policy_" + seekPolicy, Media3MediaPlayerImpl.this);
                            log.logDebug("Pull directional seek policy=" + seekPolicy + ", deltaMs=" + (timeInMillis - currentPositionMs));
                        }
                        else
                        {
                            cancelPullSeekRecovery();
                        }

                        PlaybackDebugTrap.record("backend_seek_invoke", Media3MediaPlayerImpl.this);
                        player.seekTo(timeInMillis);
                        PlaybackDebugTrap.record("backend_seek_return", Media3MediaPlayerImpl.this);
                        if (!pushMode)
                        {
                            armPullSeekRecovery(timeInMillis);
                        }

                    }
                    catch (Exception ex)
                    {
                        log.logError("Error during seek request. Position MS: " + timeInMillis, ex);
                    }
                }
            });
        }
    }

    @Override
    public void seek(long timeInMS)
    {
        try
        {
            playbackPositionLock.lock();


            //currentPlaybackPosition = 0; //Set this to zero during seek.  Lock will hopefully keep it at zero unti we are completed

            log.logDebug("Seek - pushmode: " + pushMode + ", timeinMS " + timeInMS + ", playerReady " + playerReady);

            super.seek(timeInMS);

            if (playerReady)
            {
                if (!pushMode)
                {
                    if (player != null)
                    {
                        seekToImpl(timeInMS);
                    }
                    else
                    {

                        log.logDebug("Seek player is null storing position: " + timeInMS);

                        playbackStartPosition = timeInMS;
                    }
                }
                else
                {
                    if (player != null)
                    {
                        seekToImpl(timeInMS);
                    }
                }
            }
            else
            {

                log.logDebug("Seek Resume: " + timeInMS);
                playbackStartPosition = timeInMS;
            }
        }
        catch (Exception ex)
        {
            log.logError("Unexpected error during seek", ex);
            ex.printStackTrace();
        }
        finally
        {
            playbackPositionLock.unlock();
        }
    }

    @Override
    public void setSubtitleTrack(int streamPos)
    {
        log.logDebug("Set Subtitle Track Called: " + streamPos);

        if (streamPos == Media3MediaPlayerImpl.DISABLE_TRACK)
        {
            this.showCaptions = false;
            this.RemoveSubTitleView();
        }
        else
        {
            this.showCaptions = true;
            this.AddSubTitleView();
        }

        changeTrack(C.TRACK_TYPE_TEXT, streamPos, 0);
    }

    @Override
    public int getSelectedSubtitleTrack()
    {
        return this.selectedSubtitleTrack;
    }

    @Override
    public int getSubtitleTrackCount()
    {
        return getTrackCount(C.TRACK_TYPE_TEXT);
    }

    @Override
    public void setAudioTrack(int streamPos)
    {
        if (!ExoIsPlaying())
        {
            initialAudioTrackIndex = streamPos;
        }
        else
        {
            initialAudioTrackIndex = -1;
            changeTrack(C.TRACK_TYPE_AUDIO, streamPos, 0);
        }
    }

    @Override
    public synchronized void flush()
    {
        log.logDebug("Flush called");

        // Pull-mode seeking is owned by Exo's seek/DataSource reopen path.  SageTV may
        // still issue FLUSH around a seek, but replacing the MediaSource here resets
        // the player to 0:00 after the requested seek has already been accepted.
        // Only PUSH mode needs the player pipeline reset because the server is
        // rebasing the byte stream underneath the player.
        if (!pushMode)
        {
            flushed = false;
            log.logDebug("Ignoring destructive player reset for Pull-mode flush");
            return;
        }

        super.flush();

        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    playbackPositionLock.lock();

                    if (player == null)
                    {
                        return;
                    }

                    PlaybackDebugTrap.record("flush_reprepare_before", Media3MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, true);
                    player.prepare();
                    PlaybackDebugTrap.record("flush_reprepare_after", Media3MediaPlayerImpl.this);

                    log.logDebug("After Flush was called Current Playback Position: " + Utils.toHHMMSS(player.getCurrentPosition()));
                    Media3MediaPlayerImpl.this.currentPlaybackPosition = player.getCurrentPosition();
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("flush_reprepare_error_" + ex.getClass().getSimpleName(), Media3MediaPlayerImpl.this);
                    log.logError("Media3 flush/reprepare failed", ex);
                }
                finally
                {
                    playbackPositionLock.unlock();
                }


            }
        });
    }

    @Override
    protected void setupPlayer(String sageTVurl)
    {
        initialAudioTrackIndex = -1;

        if (player != null)
        {
            releasePlayer();
        }

        // VerboseLogUtil.setEnableAllTags(true);

        //if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        log.logDebug("Setting up the Media3 media player for: " + sageTVurl);

        if (pushMode)
        {
            log.logDebug("Creating Media3PushDataSource datasource");
            dataSource = new Media3PushDataSource();
        }
        else
        {
            if (!httpls)
            {
                log.logDebug("Creating datasource");
                dataSource = new Media3PullDataSource(context.getClient().getConnectedServerInfo().address);
            }
            else
            {
                log.logDebug("Creating null datasource");
                dataSource = null;
            }
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context.getContext());
        PlayerRuntimeTuning.applyMedia3CodecPreference(
                MiniclientApplication.get().getClient().properties().getString(
                        PrefStore.Keys.media3_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE));
        String codecMode = PlayerRuntimeTuning.getMedia3CodecMode();
        if ("async".equals(codecMode)) renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
        else if ("sync".equals(codecMode)) renderersFactory.forceDisableMediaCodecAsynchronousQueueing();
        log.logDebug("Media3 codec adapter mode: " + codecMode);

        // Media3 uses platform MediaCodec decoders in this first isolated backend.
        // The legacy ExoPlayer FFmpeg extension is intentionally not loaded here.

        DecodingMethod decodingMethod = DecodingMethod.fromPreference(
                MiniclientApplication.get().getClient().properties().getString(
                        PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE));
        Media3CodecSelector mediaCodecSelector = new Media3CodecSelector(decodingMethod);
        renderersFactory.setMediaCodecSelector(mediaCodecSelector);
        renderersFactory.setEnableDecoderFallback(decodingMethod.hardwarePreferred());
        log.logDebug("Media3 Decoding Method: " + decodingMethod.displayName());

        trackSelector = new DefaultTrackSelector(context.getContext());


        ExoPlayer.Builder builder = new ExoPlayer.Builder(context.getContext(), renderersFactory);

        if (!pushMode && !httpls)
        {
            DefaultLoadControl pullLoadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            PlayerRuntimeTuning.getMedia3PullMinBufferMs(),
                            PlayerRuntimeTuning.getMedia3PullMaxBufferMs(),
                            PlayerRuntimeTuning.getMedia3PullPlaybackBufferMs(),
                            PlayerRuntimeTuning.getMedia3PullRebufferMs())
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            builder.setLoadControl(pullLoadControl);
            log.logDebug("Pull load control enabled: min=" + PlayerRuntimeTuning.getMedia3PullMinBufferMs()
                    + "ms max=" + PlayerRuntimeTuning.getMedia3PullMaxBufferMs()
                    + "ms seek=" + PlayerRuntimeTuning.getMedia3PullPlaybackBufferMs()
                    + "ms rebuffer=" + PlayerRuntimeTuning.getMedia3PullRebufferMs() + "ms");
        }

        builder.setTrackSelector(trackSelector);
        player = builder.build();
        //player.addAnalyticsListener(new EventLogger(trackSelector));

        player.addListener(new Player.Listener()
        {
            @Override
            public void onPlayerError(PlaybackException error)
            {
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("player_error_" + error.getErrorCodeName(), Media3MediaPlayerImpl.this);
                log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                error.printStackTrace();

                if (retryCount == 0)
                {
                    //Show toast on first error
                    context.showErrorMessage(error.getErrorCodeName(), "Media3MediaPlayer");
                }

                if (retryCount <= MAX_PLAYBACK_RETRY_COUNT)
                {

                    errorState = true;
                    retryCount++;

                    player.seekTo(player.getCurrentPosition() + 100);
                    player.prepare();
                }
                else
                {
                    log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                    log.logError("Playback Exception: " + error.getErrorCodeName(), error);
                    context.showErrorMessage("Max playback retry reached!", "Media3MediaPlayer");
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState)
            {
                PlaybackDebugTrap.record("playback_state_" + playbackState, Media3MediaPlayerImpl.this);
                if (!pushMode && player != null)
                {
                    log.logDebug("Pull playback state=" + playbackState
                            + ", positionMs=" + player.getCurrentPosition()
                            + ", bufferedPositionMs=" + player.getBufferedPosition()
                            + ", playWhenReady=" + player.getPlayWhenReady());
                }
                if (playbackState == Player.STATE_ENDED)
                {
                    log.logDebug("Player.STATE_ENDED");
                    log.logDebug("Player Has Ended, set EOS");
                    //stop(); - JVL: Not sure if we will need to do this or not
                    //notifySageTVStop();
                    eos = true;
                    state = Media3MediaPlayerImpl.EOS_STATE;
                }
                if (playbackState == Player.STATE_READY)
                {
                    cancelPullSeekRecovery();
                    log.logDebug("Player.STATE_READY - Media loaded and ready for playback");
                    if (errorState)
                    {
                        errorState = false;
                        retryCount = 0;
                    }

                    log.logDebug("Player.STATE_READY - setAudioTrack getting called");
                    if (initialAudioTrackIndex != -1)
                    {
                        setAudioTrack(initialAudioTrackIndex);
                        initialAudioTrackIndex = -1;
                    }

                    log.logDebug("Player.STATE_READY - Debugging available tracks in file");
                    debugAvailableTracks();

                    long duration = 0;

                    if(player.getDuration() < 0)
                    {
                        duration = -1;
                    }
                    else
                    {
                        duration = player.getDuration();
                    }
                    //Library files start with stc:// but do not have push in it
                    //Live TV has push: with a lot of other data in it

                    setMediaSessionMetadata(sageTVurl, duration);
                    mediaSession.setActive(true);
                    updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
                }
                if (playbackState == Player.STATE_IDLE)
                {
                    if (errorState)
                    {
                        log.logDebug("Player.STATE_IDLE - Error state is true, retry count: " + retryCount);
                    }
                }

            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason)
            {
                updateMediaSessionPlaybackState(Media3MediaPlayerImpl.this.getPlaybackPosition());
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason)
            {
                PlaybackDebugTrap.record("position_discontinuity_" + reason, Media3MediaPlayerImpl.this);
                switch (reason)
                {
                    case Player.DISCONTINUITY_REASON_SEEK:
                        setPlaybackPosition(newPosition.positionMs);
                        updateMediaSessionPlaybackState(newPosition.positionMs);
                        seekPending = false;
                        break;

                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying)
            {
                PlaybackDebugTrap.record(isPlaying ? "is_playing_true" : "is_playing_false", Media3MediaPlayerImpl.this);
            }

            @Override
            public void onRenderedFirstFrame()
            {
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("first_video_frame", Media3MediaPlayerImpl.this);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize)
            {
                int width = videoSize.width;
                int height = videoSize.height;
                float pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio;

                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.logDebug("Media3.onVideoSizeChanged: " + width + "x" + height + ", pixel ratio: " + pixelWidthHeightRatio);
                }

                // note if pixel ratio is != 0 then calc the ar and apply it.
                if (pixelWidthHeightRatio != 0f)
                {
                    setVideoSize(width, height, pixelWidthHeightRatio * ((float) width / (float) height));
                }
                else
                {
                    setVideoSize(width, height, 0);
                }
            }
        });


        player.addListener(new Player.Listener()
        {
            @Override
            public void onCues(CueGroup cueGroup)
            {
                if (showCaptions && subView != null)
                {
                    subView.setCues(cueGroup.cues);
                }
            }
        });


        if (!httpls)
        {

            androidx.media3.datasource.DataSource.Factory dataSourceFactory = new DataSource.Factory()
            {
                @Override
                public DataSource createDataSource()
                {
                    return dataSource;
                }
            };

            // Pull-mode SageTV recordings are commonly MPEG-TS. Media3's default TS
            // timestamp search window can be too small to build a usable seek map on
            // broadcast/PVR files, which makes seekTo() resolve back to the start.
            // Keep PUSH behavior unchanged and tune only the Pull extractor.
            if (!pushMode)
            {
                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                        .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * PlayerRuntimeTuning.getMedia3TsSearchMultiplier())
                        .setConstantBitrateSeekingEnabled(true);
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                log.logDebug("Pull extractor seek tuning enabled. TS timestamp search bytes: "
                        + (TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * PlayerRuntimeTuning.getMedia3TsSearchMultiplier()));
            }
            else
            {
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
            }


            boolean haveStartPosition = (playbackStartPosition >= 0);

            if (haveStartPosition)
            {
                player.seekTo(playbackStartPosition);
                log.logDebug("Media3Logging - Have start position");
                log.logDebug("Media3Logging - Start Position: " + playbackStartPosition);
            }

            log.logDebug("Media3Logging - Preparing playback");
            //player.prepare(mediaSource, !haveStartPosition, false);
            player.setMediaSource(mediaSource, !haveStartPosition);
            player.prepare();

        }
        else
        {
            // Let Media3's default MediaSourceFactory choose the appropriate source for HTTP.
            player.setMediaItem(MediaItem.fromUri(Uri.parse(sageTVurl)));
            player.prepare();
        }

        //Set seek preferences
        //player.setSeekParameters(SeekParameters.CLOSEST_SYNC);

        // start playing
        player.setVideoSurfaceView((SurfaceView) context.getVideoView());
        player.setPlayWhenReady(true);

        //Create Media Session
        mediaSession = new MediaSessionCompat(this.context.getContext(), "SageTV Android TV Client");
        mediaSession.setCallback(new MediaSessionCallbackHandler(this, context.getClient(), context.getContext()));

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.logDebug("Video Player is online");
        }

        this.playerReady = true;
        super.play();

        log.logDebug("Creating handler");
        handler = new Handler();


        context.runOnUiThread(progressRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if (player != null)
                {
                    Media3MediaPlayerImpl.this.setPlaybackPosition(player.getCurrentPosition());
                    handler.postDelayed(progressRunnable, 500);
                }
                else
                {
                    Media3MediaPlayerImpl.this.setPlaybackPosition(0);
                }
            }
        });

        handler.postDelayed(progressRunnable, 0);
    }

    public void changeTrack(int trackType, int groupIndex, int trackIndex)
    {
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                TrackSelectionOverride override;
                DefaultTrackSelector.Parameters.Builder parametersBuilder;

                if (trackSelector == null)
                {
                    log.logTrace("Track Selector Null");
                    return;
                }

                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();

                if (trackInfo == null)
                {
                    log.logTrace("Track info null");
                    return;
                }

                try
                {
                    int rendererIndex = findRendererIndex(trackInfo, trackType);
                    if (rendererIndex == C.INDEX_UNSET)
                    {
                        log.logWarning("No renderer found for track type " + trackType);
                        return;
                    }

                    TrackGroupArray trackGroup = trackInfo.getTrackGroups(rendererIndex);

                    if (groupIndex == Media3MediaPlayerImpl.DISABLE_TRACK) //Disable trackType from rendering
                    {
                        parametersBuilder = trackSelector.buildUponParameters();
                        parametersBuilder.setRendererDisabled(rendererIndex, true);
                        parametersBuilder.setTrackTypeDisabled(trackType, true);

                        trackSelector.setParameters(parametersBuilder.build());

                        log.logDebug("JVL - Track change executed for disable: TrackType=" + trackType + " TrackGroup=" + trackGroup + " TrackIndex=" + trackIndex);
                        selectedSubtitleTrack = DISABLE_TRACK;
                    }
                    else
                    {
                        if (trackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex) == C.FORMAT_HANDLED)
                        {
                            //Clear set new track selection
                            parametersBuilder = trackSelector.buildUponParameters();

                            //override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
                            override = new TrackSelectionOverride(trackGroup.get(groupIndex), trackIndex);

                            parametersBuilder.setTrackTypeDisabled(trackType, false);

                            //parametersBuilder.setSelectionOverride(trackType, trackGroup, override);
                            parametersBuilder.addOverride(override);


                            trackSelector.setParameters(parametersBuilder.build());
                            log.logDebug("JVL - Track change executed: TrackType=" + trackType + " TrackGroup=" + trackGroup + " TrackIndex=" + trackIndex);
                            selectedSubtitleTrack = groupIndex;
                        }
                        else
                        {
                            log.logDebug("Unable to render the track, TrackType= " + trackType + ", GroupIndex= " + groupIndex + ", TrackIndex= " + trackIndex);
                        }
                    }
                }
                catch (Exception ex)
                {
                    log.logError("Error render the track, TrackType= " + trackType + ", GroupIndex= " + groupIndex + ", TrackIndex= " + trackIndex, ex);
                    ex.printStackTrace();
                }
            }
        });
    }

    /**
     * Counts support tracks of the given track type
     *
     * @param RenderType The type of track (VIDEO, AUDIO, TEXT, ect...)
     */
    public int getTrackCount(int RenderType)
    {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        int count = 0;

        if (mappedTrackInfo == null)
        {
            log.logWarning("No Mapped Track Info found");
            return count;
        }

        int rendererIndex = findRendererIndex(mappedTrackInfo, RenderType);
        if (rendererIndex == C.INDEX_UNSET)
        {
            return count;
        }

        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

        if (trackGroups.length != 0)
        {
            //This code is assuming one track to a group.  It will increment the count by one
            //if there is a supported track in the groun
            for (int j = 0; j < trackGroups.length; j++)
            {
                boolean supported = false;

                for (int k = 0; k < trackGroups.get(j).length; k++)
                {
                    if (mappedTrackInfo.getTrackSupport(rendererIndex, j, k) == C.FORMAT_HANDLED)
                    {
                        log.logDebug("Format is handled");
                        supported = true;
                    }
                    else
                    {
                        log.logDebug("Format IS NOT HANDLED");
                    }
                }

                if (supported)
                {
                    count++;
                }
            }
        }

        return count;
    }

    @Override
    public SubtitleTrack[] getSubtitleTracks()
    {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null)
        {
            return new SubtitleTrack[0];
        }

        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_TEXT);
        if (rendererIndex == C.INDEX_UNSET)
        {
            return new SubtitleTrack[0];
        }

        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
        int trackCount = trackGroups.length;
        SubtitleTrack[] tracks = new SubtitleTrack[0];

        if (trackCount > 0)
        {
            tracks = new SubtitleTrack[trackCount];

            for (int i = 0; i < trackCount; i++)
            {
                TrackGroup trackGroup = trackGroups.get(i);

                SubtitleCodec codec = SubtitleCodec.parse(trackGroup.getFormat(0).sampleMimeType);
                String langugae = trackGroup.getFormat(0).language;
                String label = trackGroup.getFormat(0).label;
                boolean supported = (mappedTrackInfo.getTrackSupport(rendererIndex, i, 0) == C.FORMAT_HANDLED);

                SubtitleTrack track = new SubtitleTrack(i, codec, langugae, label, supported);
                tracks[i] = track;
            }
        }

        return tracks;
    }

    private int findRendererIndex(MappingTrackSelector.MappedTrackInfo trackInfo, int trackType)
    {
        if (trackInfo == null)
        {
            return C.INDEX_UNSET;
        }

        for (int rendererIndex = 0; rendererIndex < trackInfo.getRendererCount(); rendererIndex++)
        {
            if (trackInfo.getRendererType(rendererIndex) == trackType)
            {
                return rendererIndex;
            }
        }

        return C.INDEX_UNSET;
    }

    public void debugAvailableTracks()
    {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo == null)
        {
            log.logWarning("No Mapped Track Info");
            return;
        }

        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++)
        {
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);

            log.logDebug("Track Render Group " + i);

            if (trackGroups.length != 0)
            {
                switch (player.getRendererType(i))
                {
                    case C.TRACK_TYPE_AUDIO:

                        log.logDebug("TRACK_TYPE_AUDIO");
                        break;

                    case C.TRACK_TYPE_VIDEO:

                        log.logDebug("TRACK_TYPE_VIDEO");
                        break;

                    case C.TRACK_TYPE_TEXT:

                        log.logDebug("TRACK_TYPE_TEXT");
                        break;

                    default:
                        continue;
                }

                for (int j = 0; j < trackGroups.length; j++)
                {
                    log.logDebug("\t Track Group " + j);

                    for (int k = 0; k < trackGroups.get(j).length; k++)
                    {
                        Format format = trackGroups.get(j).getFormat(k);

                        log.logDebug("\t\tTrack : " + k);
                        log.logDebug("\t\tContainer MimeType: " + format.containerMimeType);
                        log.logDebug("\t\tSample MimeType: " + format.sampleMimeType);
                        log.logDebug("\t\tCodecs: " + format.codecs);
                        log.logDebug("\t\tLanguage: " + format.language);


                        if (player.getRendererType(i) == C.TRACK_TYPE_TEXT)
                        {

                            /*if((MimeTypes.APPLICATION_CEA708.equalsIgnoreCase(format.sampleMimeType) || MimeTypes.APPLICATION_CEA608.equalsIgnoreCase(format.sampleMimeType))
                                    && format.language.equalsIgnoreCase("en"))
                            {
                                log.debug("-----Setting Subtitle track to active");
                                //Enable this track.  This is just debugging
                                this.setSubtitleTrack(j);
                            }
                            else
                            {
                                log.debug("-----NOT Setting Subtitle track to active");
                            }*/
                        }


                        if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO)
                        {
                            log.logDebug("\t\tChannel: " + format.channelCount);
                            log.logDebug("\t\tBitrate: " + format.bitrate);
                            log.logDebug("\t\tAverageBitrate: " + format.averageBitrate);
                            log.logDebug("\t\tPeakBitrate: " + format.peakBitrate);
                            log.logDebug("\t\tPCM Encoding: " + format.pcmEncoding);

                        }

                        if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO)
                        {
                            if (format.colorInfo != null)
                            {
                                log.logDebug("\t\tColor: " + format.colorInfo.toString());
                            }
                            log.logDebug("\t\tBitrate: " + format.bitrate);
                            log.logDebug("\t\tAverageBitrate: " + format.averageBitrate);
                            log.logDebug("\t\tPeakBitrate: " + format.peakBitrate);
                            log.logDebug("\t\tFramerate: " + format.frameRate);
                            log.logDebug("\t\tHeight: " + format.height);
                            log.logDebug("\t\tWidth: " + format.width);

                        }

                        log.logDebug("\t\tID: " + format.id);
                        log.logDebug("\t\tLabel: " + format.label);
                        if (format.metadata != null)
                        {
                            log.logDebug("\t\t\tMetadata length: " + format.metadata.length());
                        }


                        if (mappedTrackInfo.getTrackSupport(i, j, k) == C.FORMAT_HANDLED)
                        {
                            //Add debug info
                            log.logDebug("\t\t Format is handled");
                        }
                        else
                        {
                            //Add debug info
                            log.logDebug("\t\t Format IS NOT HANDLED");
                        }
                    }

                }

            }
            else
            {
                log.logDebug("Track Group Empty");
            }
        }
    }

    // cncb - Add and remove ExoPlayer2 SubTitleView for embedded PGS subtitles
    private void AddSubTitleView()
    {
        if (subView == null)
        {
            subView = new SubtitleView(context.getContext());
            subView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        FrameLayout layout = (FrameLayout) context.getVideoView().getParent();
                        layout.addView(subView);
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error adding SubTitleView: ", ex);
                    }
                }
            });
        }
    }

    private void RemoveSubTitleView()
    {
        if (subView != null)
        {
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        FrameLayout layout = (FrameLayout) context.getVideoView().getParent();
                        layout.removeView(subView);
                    }
                    catch (Exception ex)
                    {
                        log.logError("Error removing SubTitleView ",  ex);
                    }
                    finally
                    {
                        subView = null;
                    }
                }
            });
        }
    }

    private void updateMediaSessionPlaybackState(long playbackPostion)
    {
        if(mediaSession != null && mediaSession.isActive()) {
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
            stateBuilder.setActions(this.getMediaSessionActions());

            if (player != null && getState() == PLAY_STATE) {
                stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, playbackPostion, 1.0f);
            } else {
                stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            }

            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private long getMediaSessionActions()
    {
        long actions = 0;

        if(player != null)
        {
            if (getState() == PLAY_STATE)
            {
                actions = PlaybackState.ACTION_STOP;
                actions |= PlaybackState.ACTION_PAUSE;
                actions |= PlaybackState.ACTION_FAST_FORWARD;
                actions |= PlaybackState.ACTION_REWIND;
                actions |= PlaybackState.ACTION_SEEK_TO;
                actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
                actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            }
            else
            {
                actions = PlaybackState.ACTION_PLAY;
            }

        }

        return actions;
    }

    private void setMediaSessionMetadata(String displayTitle, long duration)
    {
        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder();

        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle);
        metaDataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        mediaSession.setMetadata(metaDataBuilder.build());
    }
}
