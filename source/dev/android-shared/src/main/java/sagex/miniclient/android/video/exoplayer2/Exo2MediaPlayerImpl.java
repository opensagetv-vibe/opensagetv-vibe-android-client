package sagex.miniclient.android.video.exoplayer2;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.upstream.DataSource;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.HashSet;
import java.util.List;

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

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * Created by seans on 24/09/16.
 */

public class Exo2MediaPlayerImpl extends BaseMediaPlayerImpl<ExoPlayer, DataSource>
{
    static final Logger log = Logger.getLogger(Exo2MediaPlayerImpl.class);
    static final int MAX_PLAYBACK_RETRY_COUNT = 12;
    static final int PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER = 8;
    static final int PULL_MIN_BUFFER_MS = 5000;
    static final int PULL_MAX_BUFFER_MS = 20000;
    static final int PULL_BUFFER_FOR_PLAYBACK_MS = 500;
    static final int PULL_BUFFER_AFTER_REBUFFER_MS = 1000;
    static final long PULL_DIRECTIONAL_SYNC_MIN_DELTA_MS = 2000L;
    static final long PULL_SEEK_RECOVERY_DELAY_MS = 10000L;

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
    private String url;

    MediaSessionCompat mediaSession;

    private SubtitleView subView;

    public Exo2MediaPlayerImpl(AndroidUIController activity)
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
                    Exo2MediaPlayerImpl.super.releasePlayer();
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

        //log.debug("ExoLogging - getPlayerMediaTimeMillis Called lastServerTime=" + Utils.toHHMMSS(lastServerTime) + " position=" + Utils.toHHMMSS(position));

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
                    PlaybackDebugTrap.record("backend_pause_invoke", Exo2MediaPlayerImpl.this);
                    ExoPause();
                    PlaybackDebugTrap.record("backend_pause_return", Exo2MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
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
                    PlaybackDebugTrap.record("backend_play_invoke", Exo2MediaPlayerImpl.this);
                    ExoStart();
                    PlaybackDebugTrap.record("backend_play_return", Exo2MediaPlayerImpl.this);
                }
            }
        });

        updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
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
        String policy = PlayerRuntimeTuning.getExo2SeekPolicy();
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
        if (!PlayerRuntimeTuning.isExo2SeekRecoveryEnabled() || pushMode || handler == null || player == null || mediaSource == null)
        {
            return;
        }

        if (pullSeekRecoveryRunnable != null)
        {
            handler.removeCallbacks(pullSeekRecoveryRunnable);
        }
        final long generation = ++pullSeekGeneration;
        final boolean resumeWhenReady = player.getPlayWhenReady();
        PlaybackDebugTrap.record("pull_seek_recovery_armed", Exo2MediaPlayerImpl.this);

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
                    PlaybackDebugTrap.record("pull_seek_reprepare_before", Exo2MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, targetPositionMs);
                    player.prepare();
                    player.setPlayWhenReady(resumeWhenReady);
                    PlaybackDebugTrap.record("pull_seek_reprepare_after", Exo2MediaPlayerImpl.this);
                    log.logDebug("Pull seek recovery reprepare at " + targetPositionMs + "ms after " + PlayerRuntimeTuning.getExo2SeekRecoveryDelayMs() + "ms buffering watchdog");
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("pull_seek_reprepare_error_" + ex.getClass().getSimpleName(), Exo2MediaPlayerImpl.this);
                    log.logError("Pull seek recovery reprepare failed at " + targetPositionMs + "ms", ex);
                }
            }
        };
        handler.postDelayed(pullSeekRecoveryRunnable, PlayerRuntimeTuning.getExo2SeekRecoveryDelayMs());
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
                            PlaybackDebugTrap.record("pull_seek_policy_" + seekPolicy, Exo2MediaPlayerImpl.this);
                            log.logDebug("Pull directional seek policy=" + seekPolicy + ", deltaMs=" + (timeInMillis - currentPositionMs));
                        }
                        else
                        {
                            cancelPullSeekRecovery();
                        }

                        PlaybackDebugTrap.record("backend_seek_invoke", Exo2MediaPlayerImpl.this);
                        player.seekTo(timeInMillis);
                        PlaybackDebugTrap.record("backend_seek_return", Exo2MediaPlayerImpl.this);
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

        if (streamPos == Exo2MediaPlayerImpl.DISABLE_TRACK)
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

                    PlaybackDebugTrap.record("flush_reprepare_before", Exo2MediaPlayerImpl.this);
                    player.setMediaSource(mediaSource, true);
                    player.prepare();
                    PlaybackDebugTrap.record("flush_reprepare_after", Exo2MediaPlayerImpl.this);

                    log.logDebug("After Flush was called Current Playback Position: " + Utils.toHHMMSS(player.getCurrentPosition()));
                    Exo2MediaPlayerImpl.this.currentPlaybackPosition = player.getCurrentPosition();
                }
                catch (Exception ex)
                {
                    PlaybackDebugTrap.record("flush_reprepare_error_" + ex.getClass().getSimpleName(), Exo2MediaPlayerImpl.this);
                    log.logError("Exo2 flush/reprepare failed", ex);
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

        this.url = sageTVurl;

        // VerboseLogUtil.setEnableAllTags(true);

        //if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        log.logDebug("Setting up the Exo2 media player for: " + sageTVurl);

        if (pushMode)
        {
            log.logDebug("Creating Exo2PushDataSource datasource");
            dataSource = new Exo2PushDataSource();
        }
        else
        {
            if (!httpls)
            {
                log.logDebug("Creating datasource");
                dataSource = new Exo2PullDataSource(context.getClient().getConnectedServerInfo().address);
            }
            else
            {
                log.logDebug("Creating null datasource");
                dataSource = null;
            }
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context.getContext());
        PlayerRuntimeTuning.applyExo2CodecPreference(
                MiniclientApplication.get().getClient().properties().getString(
                        PrefStore.Keys.exo2_codec_mode, PlayerRuntimeTuning.DEFAULT_CODEC_MODE));
        String codecMode = PlayerRuntimeTuning.getExo2CodecMode();
        if ("async".equals(codecMode)) renderersFactory.forceEnableMediaCodecAsynchronousQueueing();
        else if ("sync".equals(codecMode)) renderersFactory.forceDisableMediaCodecAsynchronousQueueing();
        log.logDebug("Legacy Exo codec adapter mode: " + codecMode);

        if (FfmpegLibrary.isAvailable())
        {
            final int preferExtensionDecoders = MiniclientApplication.get().getClient().properties().getInt(PrefStore.Keys.exoplayer_ffmpeg_extension_setting, 1);

            switch (preferExtensionDecoders)
            {
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER:
                    log.logDebug("Setting FFmpeg Extension to Prefer");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                    break;
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON:
                    log.logDebug("Setting FFmpeg Extension to On");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
                    break;
                case DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF:
                    log.logDebug("Setting FFmpeg Extension to Off");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
                    break;
                default:
                    log.logDebug("Defaulting FFmpeg Extension to On");
                    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
            }
        }

        DecodingMethod decodingMethod = DecodingMethod.fromPreference(
                MiniclientApplication.get().getClient().properties().getString(
                        PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE));
        CustomMediaCodecSelector mediaCodecSelector = new CustomMediaCodecSelector(decodingMethod);
        renderersFactory.setMediaCodecSelector(mediaCodecSelector);
        renderersFactory.setEnableDecoderFallback(decodingMethod.hardwarePreferred());
        log.logDebug("Legacy Exo Decoding Method: " + decodingMethod.displayName());

        trackSelector = new DefaultTrackSelector(context.getContext());


        ExoPlayer.Builder builder = new ExoPlayer.Builder(context.getContext(), renderersFactory);

        if (!pushMode && !httpls)
        {
            DefaultLoadControl pullLoadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            PlayerRuntimeTuning.getExo2PullMinBufferMs(),
                            PlayerRuntimeTuning.getExo2PullMaxBufferMs(),
                            PlayerRuntimeTuning.getExo2PullPlaybackBufferMs(),
                            PlayerRuntimeTuning.getExo2PullRebufferMs())
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            builder.setLoadControl(pullLoadControl);
            log.logDebug("Pull load control enabled: min=" + PlayerRuntimeTuning.getExo2PullMinBufferMs()
                    + "ms max=" + PlayerRuntimeTuning.getExo2PullMaxBufferMs()
                    + "ms seek=" + PlayerRuntimeTuning.getExo2PullPlaybackBufferMs()
                    + "ms rebuffer=" + PlayerRuntimeTuning.getExo2PullRebufferMs() + "ms");
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
                PlaybackDebugTrap.record("player_error_" + error.getErrorCodeName(), Exo2MediaPlayerImpl.this);
                log.logDebug("PLAYER ERROR: " + error.getErrorCodeName());
                error.printStackTrace();

                if (retryCount == 0)
                {
                    //Show toast on first error
                    context.showErrorMessage(error.getErrorCodeName(), "Exo2MediaPlayer");
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
                    context.showErrorMessage("Max playback retry reached!", "Exo2MediaPlayer");
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState)
            {
                PlaybackDebugTrap.record("playback_state_" + playbackState, Exo2MediaPlayerImpl.this);
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
                    state = Exo2MediaPlayerImpl.EOS_STATE;
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
                    updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
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
                updateMediaSessionPlaybackState(Exo2MediaPlayerImpl.this.getPlaybackPosition());
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason)
            {
                PlaybackDebugTrap.record("position_discontinuity_" + reason, Exo2MediaPlayerImpl.this);
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
                PlaybackDebugTrap.record(isPlaying ? "is_playing_true" : "is_playing_false", Exo2MediaPlayerImpl.this);
            }

            @Override
            public void onRenderedFirstFrame()
            {
                cancelPullSeekRecovery();
                PlaybackDebugTrap.record("first_video_frame", Exo2MediaPlayerImpl.this);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize)
            {
                int width = videoSize.width;
                int height = videoSize.height;
                float pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio;

                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.logDebug("ExoPlayer.onVideoSizeChanged: " + width + "x" + height + ", pixel ratio: " + pixelWidthHeightRatio);
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
            public void onCues(List<Cue> cues)
            {
                if (showCaptions && subView != null)

                    subView.setCues(cues);
            }
        });


        final String sageTVurlFinal = sageTVurl;
        if (!httpls)
        {

            com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = new DataSource.Factory()
            {
                @Override
                public DataSource createDataSource()
                {
                    return dataSource;
                }
            };

            // Pull-mode SageTV recordings are commonly MPEG-TS. Exo's default TS timestamp
            // search window can be too small to build a usable seek map on broadcast/PVR files,
            // which makes seekTo() resolve back to the start of the item. Keep PUSH behavior
            // unchanged, but give Pull a wider PCR search window and sync-point seeking.
            if (!pushMode)
            {
                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                        .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * PlayerRuntimeTuning.getExo2TsSearchMultiplier())
                        .setConstantBitrateSeekingEnabled(true);
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(sageTVurl)));
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                log.logDebug("Pull extractor seek tuning enabled. TS timestamp search bytes: "
                        + (TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * PlayerRuntimeTuning.getExo2TsSearchMultiplier()));
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
                log.logDebug("ExoLogging - Have start position");
                log.logDebug("ExoLogging - Start Position: " + playbackStartPosition);
            }

            log.logDebug("ExoLogging - Preparing playback");
            //player.prepare(mediaSource, !haveStartPosition, false);
            player.setMediaSource(mediaSource, !haveStartPosition);
            player.prepare();

        }

        //Set seek preferences
        //player.setSeekParameters(SeekParameters.CLOSEST_SYNC);

        // start playing
        player.setVideoSurface(((SurfaceView) context.getVideoView()).getHolder().getSurface());
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
                    Exo2MediaPlayerImpl.this.setPlaybackPosition(player.getCurrentPosition());
                    handler.postDelayed(progressRunnable, 500);
                }
                else
                {
                    Exo2MediaPlayerImpl.this.setPlaybackPosition(0);
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
                    TrackGroupArray trackGroup = trackInfo.getTrackGroups(trackType);


                    if (groupIndex == Exo2MediaPlayerImpl.DISABLE_TRACK) //Disable trackType from rendering
                    {
                        parametersBuilder = trackSelector.buildUponParameters();
                        parametersBuilder.setRendererDisabled(trackType, true);
                        parametersBuilder.setTrackTypeDisabled(trackType, true);

                        trackSelector.setParameters(parametersBuilder.build());

                        log.logDebug("JVL - Track change executed for disable: TrackType=" + trackType + " TrackGroup=" + trackGroup + " TrackIndex=" + trackIndex);
                        selectedSubtitleTrack = DISABLE_TRACK;
                    }
                    else
                    {
                        if (trackInfo.getTrackSupport(trackType, groupIndex, trackIndex) == RendererCapabilities.FORMAT_HANDLED)
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

        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(RenderType);

        if (trackGroups.length != 0)
        {
            //This code is assuming one track to a group.  It will increment the count by one
            //if there is a supported track in the groun
            for (int j = 0; j < trackGroups.length; j++)
            {
                boolean supported = false;

                for (int k = 0; k < trackGroups.get(j).length; k++)
                {
                    if (mappedTrackInfo.getTrackSupport(RenderType, j, k) == C.FORMAT_HANDLED)
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
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(C.TRACK_TYPE_TEXT);
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
                boolean supported = (mappedTrackInfo.getTrackSupport(C.TRACK_TYPE_TEXT, i, 0) == C.FORMAT_HANDLED);

                SubtitleTrack track = new SubtitleTrack(i, codec, langugae, label, supported);
                tracks[i] = track;
            }
        }

        return tracks;
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
                int label;

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


                        if (mappedTrackInfo.getTrackSupport(i, j, k) == RendererCapabilities.FORMAT_HANDLED)
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
