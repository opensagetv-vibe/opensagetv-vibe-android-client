package opensagetv.vibe.miniclient.android.video.ijkplayer;

import android.media.session.PlaybackState;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.SurfaceView;

import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.android.video.BaseMediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.PlaybackDebugTrap;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.MediaSessionCallbackHandler;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.util.VerboseLogging;
import opensagetv.vibe.miniclient.video.PlaybackFrameStepPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSessionController;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;


import static opensagetv.vibe.miniclient.util.Utils.toHHMMSS;

/**
 * Created by seans on 06/10/15.
 */
public class IJKMediaPlayerImpl extends BaseMediaPlayerImpl<IMediaPlayer, IMediaDataSource>
{
    long preSeekPos = -1;
    long resumeTimeOffset = -1;
    boolean resumeMode = false;
    int initialAudioStreamPos = -1;
    int initialTextStreamPos = -1;
    long logTime = -1;
    MediaSessionCompat mediaSession;
    private boolean potentiallyGrowing;
    private boolean serverMediaMetadataExplicit;
    private boolean stoppedForResume;
    private volatile boolean firstVideoFrameRendered;

    public IJKMediaPlayerImpl(AndroidUIController activity)
    {
        super(activity, true, true);
    }

    @Override
    public boolean hasRenderedFirstVideoFrame()
    {
        return firstVideoFrameRendered;
    }

    @Override
    public void load(byte majorHint, byte minorHint, String encodingHint, String urlString,
                     String hostname, boolean timeshifted, long bufferSize)
    {
        potentiallyGrowing = timeshifted || bufferSize > 0;
        super.load(majorHint, minorHint, encodingHint, urlString, hostname,
                timeshifted, bufferSize);
    }

    @Override
    public void setServerMediaMetadataExplicit(boolean explicit)
    {
        serverMediaMetadataExplicit = explicit;
    }

    /**
     * @param serverStartTime
     * @return
     */
    @Override
    public long getPlayerMediaTimeMillis(long serverStartTime)
    {
        // A queued DVD/SPU clock probe can race a live-program player
        // replacement. All other backends already tolerate a released player;
        // IJK must do the same instead of crashing the complete connection.
        IMediaPlayer activePlayer = player;
        if (activePlayer == null) return 0L;
        long time = activePlayer.getCurrentPosition();

        if (pushMode)
        {
            // we haven't determined the "resume" time yet
            if (resumeTimeOffset < 0)
            {
                if (serverStartTime < 500)
                {
                    // this is start from beginning
                    resumeMode = false;
                    resumeTimeOffset = 0;
                }
                else
                {
                    // IJK's getMediaTime() is a little quirky for PS/TS streams.  Basically, when you start
                    // from 0, then seeking works fine.  But, when you resume from some other time, the
                    // players internal clock is reset to 0.  So, the stream plays at the right position, but
                    // the timescale is reset to 0.  For that reason, when pushMode is enabled, you can follow
                    // the logic to see what has to happen to ensure the correct media time.
                    resumeMode = true;
                    resumeTimeOffset = serverStartTime;
                    log.debug("IJK: getMediaTime(): RESUME from {}", resumeTimeOffset);
                }
            }

            if (time < 0)
            {
                // player is adjusting, after a push/seek.
                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.debug("IJK: getPlayerMediaTimeMillis(): player adjusting using 0 but time was {}", toHHMMSS(time, true));
                }

                return time;
            }

            if (resumeMode)
            {
                // when in resume mode, you go back before the start of the resume, player time
                // seems to do a PTS rollover of sorts
                long realTime = time;

                if (time + resumeTimeOffset > PTS_ROLLOVER)
                {
                    // need to adjust the time
                    time = time - PTS_ROLLOVER;
                }
                time = time + resumeTimeOffset;

                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    if (logTime != realTime / 1000)
                    {
                        logTime = realTime / 1000;
                        log.debug("IJK: getPlayerMediaTimeMillis(): resume: {}, player time: {}, total: {}", toHHMMSS(resumeTimeOffset, true), toHHMMSS(realTime, true), toHHMMSS(time, true));
                    }
                }
            }
            else
            {
                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    if (logTime != time / 1000)
                    {
                        logTime = time / 1000;
                        log.debug("IJK: getPlayerMediaTimeMillis(): time: {}", toHHMMSS(time, true));
                    }
                }
            }
        }


        return time;
    }

    @Override
    public void stop()
    {
        if(mediaSession != null)
        {
            mediaSession.setActive(false);
        }

        if (player != null)
        {
            try
            {
                // SageTV STOP is nonterminal; the server may issue PLAY/SEEK
                // against this same loaded player. IJK 0.8.8 can SIGSEGV when
                // prepareAsync() follows native stop(), so retain the decoder
                // and datasource in the paused/preparing state. free() remains
                // the terminal native release boundary.
                if (playerReady && player.isPlaying())
                    player.pause();
                stoppedForResume = true;
            }
            catch (Throwable t)
            {
                log.warn("IJKPlayer could not enter the stopped state", t);
            }
        }
        super.stop();
    }

    @Override
    public void pause()
    {


        if (state == PAUSE_STATE && !pushMode)
        {
            frameStep(1);
            return;
        }

        super.pause();

        if (player != null && player.isPlaying())
        {
            PlaybackDebugTrap.record("backend_pause_invoke", IJKMediaPlayerImpl.this);
            player.pause();
            PlaybackDebugTrap.record("backend_pause_return", IJKMediaPlayerImpl.this);
            updateMediaSessionPlaybackState(player.getCurrentPosition());
        }
    }

    @Override
    public boolean frameStep(final int amount)
    {
        final IMediaPlayer stepPlayer = player;
        if (!PlaybackFrameStepPolicy.canStep(getState(), pushMode, playerReady,
                stepPlayer != null, amount))
        {
            PlaybackDebugTrap.record("frame_step_unsupported", this);
            return false;
        }

        final PlaybackSessionController.Token session = beginPlaybackOperation(
                PlaybackSessionController.Operation.FRAME_STEP);
        final long targetMs = PlaybackFrameStepPolicy.targetPositionMs(
                stepPlayer.getCurrentPosition(), stepPlayer.getDuration(), amount,
                PlaybackFrameStepPolicy.FALLBACK_FRAME_RATE);
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isCurrentPlaybackSession(session) || player != stepPlayer
                        || getState() != PAUSE_STATE)
                {
                    PlaybackDebugTrap.record("frame_step_stale", IJKMediaPlayerImpl.this);
                    return;
                }
                PlaybackDebugTrap.record("frame_step_invoke", IJKMediaPlayerImpl.this);
                stepPlayer.seekTo(targetMs);
                PlaybackDebugTrap.record("frame_step_return", IJKMediaPlayerImpl.this);
            }
        });
        return true;
    }

    @Override
    public void play()
    {
        boolean prepareAfterStop = stoppedForResume;
        super.play();

        if (player == null) return;

        if (prepareAfterStop)
        {
            final IMediaPlayer playerToResume = player;
            final PlaybackSessionController.Token resumeSession = currentPlaybackSession();
            // setupVideoFrame changes SurfaceView visibility and must run on
            // Android's UI thread. Calling it directly from SageTV's media
            // command thread throws CalledFromWrongThreadException, tears down
            // that socket and leaves STOP -> PLAY at a black menu screen.
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if (!isCurrentPlaybackSession(resumeSession)
                            || player != playerToResume || !stoppedForResume)
                    {
                        PlaybackDebugTrap.record("play_prepare_from_stopped_stale",
                                IJKMediaPlayerImpl.this);
                        return;
                    }
                    try
                    {
                        context.setupVideoFrame();
                        playerToResume.setDisplay(
                                ((SurfaceView) context.getVideoView()).getHolder());
                        stoppedForResume = false;
                        PlaybackDebugTrap.record("play_resume_from_stopped",
                                IJKMediaPlayerImpl.this);
                        if (playerReady)
                        {
                            if (preSeekPos != -1)
                            {
                                seekToImpl(preSeekPos);
                                preSeekPos = -1;
                            }
                            playerToResume.start();
                            if (mediaSession != null)
                                mediaSession.setActive(true);
                            updateMediaSessionPlaybackState(playerToResume.getCurrentPosition());
                        }
                    }
                    catch (Throwable t)
                    {
                        log.error("IJKPlayer could not resume after server STOP", t);
                        playerFailed();
                    }
                }
            });
        }
        else if (!player.isPlaying())
        {
            PlaybackDebugTrap.record("backend_play_invoke", IJKMediaPlayerImpl.this);
            player.start();
            PlaybackDebugTrap.record("backend_play_return", IJKMediaPlayerImpl.this);
            updateMediaSessionPlaybackState(player.getCurrentPosition());
        }
    }

    @Override
    public void flush()
    {
        PlaybackDebugTrap.record("flush_before", IJKMediaPlayerImpl.this);
        super.flush();

        if (player != null)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("Flush Will force a seek to clear buffers");
            }

            seekToImpl(Long.MAX_VALUE);
        }
        PlaybackDebugTrap.record("flush_after", IJKMediaPlayerImpl.this);
    }

    @Override
    public Dimension getVideoDimensions()
    {
        if (player != null)
        {
            Dimension d = new Dimension(player.getVideoWidth(), player.getVideoHeight());
            if (VerboseLogging.DETAILED_PLAYER_LOGGING) log.debug("getVideoSize(): {}", d);
            return d;
        }
        return null;
    }

    protected void setupPlayer(String sageTVurl)
    {
        log.debug("Creating Player");

        preSeekPos = -1;
        resumeTimeOffset = -1;
        resumeMode = false;
        initialAudioStreamPos = -1;
        initialTextStreamPos = -1;
        logTime = -1;
        stoppedForResume = false;
        firstVideoFrameRendered = false;

        releasePlayer();
        try
        {
            if (player == null)
            {
                player = new IjkMediaPlayer();
                ((IjkMediaPlayer) player).setOnMediaCodecSelectListener(CodecSelector.sInstance);
            }
            final PlaybackSessionController.Token listenerSession = currentPlaybackSession();
            final IMediaPlayer listenerPlayer = player;
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_ERROR);

            player.setDisplay(((SurfaceView) context.getVideoView()).getHolder());

            PrefStore playerPrefs = MiniclientApplication.get().getClient().properties();
            DecodingMethod decodingMethod = IjkDecoderOptions.apply((IjkMediaPlayer) player, playerPrefs);
            log.info("IJKPlayer decoding method: {}", decodingMethod.displayName());

            player.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener()
            {
                @Override
                public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int width, int height, int sarNum, int sarDen)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                    if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                    {
                        log.debug("IJKPlayer.onVideoSizeChanged: {}x{}, {},{}", width, height, sarNum, sarDen);
                    }
                    setVideoSize(width, height, sarNum, sarDen);
                }
            });
            player.setOnInfoListener(new IMediaPlayer.OnInfoListener()
            {
                @Override
                public boolean onInfo(IMediaPlayer mp, int what, int extra)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer)
                        return false;
                    if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                    {
                        firstVideoFrameRendered = true;
                        PlaybackDebugTrap.record("first_video_frame", IJKMediaPlayerImpl.this);
                    }
                    return false;
                }
            });


            player.setOnErrorListener(new IMediaPlayer.OnErrorListener()
            {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return true;
                    PlaybackDebugTrap.record("player_error_" + what + "_" + extra, IJKMediaPlayerImpl.this);
                    log.error("IjkPlayer onERROR: {}, {}", what, extra);
                    playerFailed();
                    // We fully handle the error here. Returning false can cause IJK to also
                    // deliver completion after playerFailed(), producing a second stop path.
                    return true;
                }
            });


            log.debug("Sending {} to mediaplayer", sageTVurl);

            if (pushMode)
            {
                log.info("Playing URL {} PUSH mode", sageTVurl);
                dataSource = new IJKPushMediaSource();
                ((IJKPushMediaSource) dataSource).open(sageTVurl);
                player.setDataSource(dataSource);
            }
            else
            {
                if (httpls)
                {
                    log.info("Playing URL Using HTTPL: isPush:{}, sageTVUrl: {}", pushMode, sageTVurl);
                    player.setDataSource(sageTVurl);
                }
                else
                {
                    log.info("Playing URL Using DataSource: isPush:{}, sageTVUrl: {}", pushMode, sageTVurl);
                    dataSource = new IJKPullMediaSource(
                            MiniclientApplication.get().getClient().getConnectedServerInfo().address,
                            potentiallyGrowing, serverMediaMetadataExplicit);
                    ((IJKPullMediaSource) dataSource).open(sageTVurl);
                    player.setDataSource(dataSource);
                }
            }

            player.setOnCompletionListener(new IMediaPlayer.OnCompletionListener()
            {
                @Override
                public void onCompletion(IMediaPlayer iMediaPlayer)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                    PlaybackDebugTrap.record("playback_complete", IJKMediaPlayerImpl.this);
                    if (VerboseLogging.DETAILED_PLAYER_LOGGING) log.debug("MEDIA COMPLETE");
                    log.debug("OnCompletionListener fired.  Stoping playback and setting state to EOS");
                    stop();
                    state = EOS_STATE;
                    eos = true;
                }
            });

            player.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener()
            {
                @Override
                public void onSeekComplete(IMediaPlayer iMediaPlayer)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                    PlaybackDebugTrap.record("seek_complete", IJKMediaPlayerImpl.this);
                    seekPending = false;
                    if(player != null)
                    {
                        updateMediaSessionPlaybackState(player.getCurrentPosition());
                    }
                }
            });

            player.setOnPreparedListener(new IMediaPlayer.OnPreparedListener()
            {
                @Override
                public void onPrepared(IMediaPlayer mp)
                {
                    if (!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer) return;
                    PlaybackDebugTrap.record("player_prepared", IJKMediaPlayerImpl.this);
                    playerReady = true;
                    if (stoppedForResume || state == STOPPED_STATE)
                    {
                        PlaybackDebugTrap.record("player_prepared_while_stopped",
                                IJKMediaPlayerImpl.this);
                        return;
                    }
                    player.start();
                    state = PLAY_STATE;

                    if (!pushMode)
                    {
                        if (preSeekPos != -1)
                        {
                            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                                log.debug("Resuming At Position: {}", preSeekPos);
                            seekToImpl(preSeekPos);
                            preSeekPos = -1;
                        }
                        else
                        {
                            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                                log.debug("No Resume");
                        }
                    }

                    if (MiniclientApplication.get().getClient().properties().getBoolean(PrefStore.Keys.announce_software_decoder, false))
                    {
                        MediaInfo mi = player.getMediaInfo();
                        if (mi != null)
                        {
                            log.info("MEDIAINFO: video: {},{}", mi.mVideoDecoder, mi.mVideoDecoderImpl);
                            if (!"mediacodec".equalsIgnoreCase(mi.mVideoDecoder))
                            {
                                message("Using Software Decoder (" + (pushMode ? "PUSH MODE" : "PULL MODE") + ")");
                            }
                        }
                    }

                    // Create the MediaSession once and reactivate it after a server STOP.
                    if (IJKMediaPlayerImpl.this.mediaSession == null)
                    {
                        IJKMediaPlayerImpl.this.mediaSession = new MediaSessionCompat(IJKMediaPlayerImpl.this.context.getContext(), "SageTV Android TV Client");
                        mediaSession.setCallback(new MediaSessionCallbackHandler(IJKMediaPlayerImpl.this, context.getClient(), context.getContext()));
                        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
                    }

                    long duration;

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

                    updateMediaSessionPlaybackState(player.getCurrentPosition());

                    if (initialAudioStreamPos != -1)
                    {
                        setAudioTrack(initialAudioStreamPos);
                    }
                }
            });

            player.prepareAsync();
            log.debug("mediaplayer has our URL");
        }
        catch (Exception e)
        {
            log.error("Failed to create player", e);
            playerFailed();
        }
    }

    protected void seekToImpl(long timeInMillis)
    {
        seekPending = true;
        PlaybackDebugTrap.record("backend_seek_invoke", IJKMediaPlayerImpl.this);
        player.seekTo(timeInMillis);
        PlaybackDebugTrap.record("backend_seek_return", IJKMediaPlayerImpl.this);
    }

    @Override
    public void seek(long timeInMS)
    {
        super.seek(timeInMS);
        if (player == null || stoppedForResume || state == NO_STATE || state == LOADED_STATE || state == STOPPED_STATE)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("Setting Pre-Seek {}", timeInMS);
            }
            preSeekPos = timeInMS;
            return;
        }

        if (!pushMode)
        {
            if (player.isPlaying() || state == PAUSE_STATE || state == PLAY_STATE)
            {
                if (VerboseLogging.DETAILED_PLAYER_LOGGING)
                {
                    log.debug("Immediate Seek {}", timeInMS);
                }
                seekToImpl(timeInMS);
            }
            else
            {
                log.info("We Missed a Seek for {}: player.isPlaying {}; State: {}; playerReader: {}", timeInMS, player.isPlaying(), state, playerReady);
            }
        }
    }

    /**
     * This finds the correct track position in the IJKPlayer track list.  SageTV gives us a zero based index of all audio tracks.  IJKPlayer
     * uses a track position of all tracks in the file.  Video is is track 0.  If it was an audio only file, I assume track 0 would be the audio
     *
     * @param sageTVPosition Track position sage has requested us to change to
     * @return IJKPlayer track position
     */
    private int getAudioTrackPosition(int sageTVPosition)
    {
        if (player == null) return -1;

        ITrackInfo info[] = player.getTrackInfo();
        if (info == null || info.length == 0) return -1;

        int audioTrackCount = 0;

        for (int i = 0; i < info.length; i++)
        {
            if (info[i] != null && info[i].getTrackType() == IjkTrackInfo.MEDIA_TRACK_TYPE_AUDIO)
            {
                if (audioTrackCount == sageTVPosition)
                {
                    return i;
                }

                audioTrackCount++;
            }
        }

        return -1;
    }

    /**
     * This finds the correct track position in the IJKPlayer track list.  SageTV gives us a zero based index of all aubtitle tracks.  IJKPlayer
     * uses a track position of all tracks in the file.  Video is is track 0.  If it was an audio only file, I assume track 0 would be the audio
     *
     * @param sageTVPosition Track position sage has requested us to change to
     * @return IJKPlayer track position
     */
    private int getSubtitleTrackPosition(int sageTVPosition)
    {
        if (player == null) return -1;

        ITrackInfo info[] = player.getTrackInfo();
        if (info == null || info.length == 0) return -1;

        int subtitleTrackCount = 0;

        for (int i = 0; i < info.length; i++)
        {
            if (info[i] == null) continue;

            log.debug("Track Pos {}, TrackType {}, Track Info {}, Track Lang ", i, info[i].getTrackType(), info[i].getInfoInline(), info[i].getLanguage());

            if (info[i].getTrackType() == IjkTrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
            {
                if (subtitleTrackCount == sageTVPosition)
                {
                    return i;
                }

                subtitleTrackCount++;
            }
        }

        return -1;
    }

    @Override
    public void setAudioTrack(int streamPos)
    {
        //NOTE: Do not try to set the track position to a currently selected audio track. IJKPlayer really does not like that, and will crash.

        log.debug("setAudioTrack Called StreamPosition: {}", streamPos);

        if (playerReady)
        {
            int currentTrack = ((IjkMediaPlayer) player).getSelectedTrack(IjkTrackInfo.MEDIA_TRACK_TYPE_AUDIO);
            int setTrackTo = this.getAudioTrackPosition(streamPos);

            log.debug("Selected Audio Track Pos: {}", currentTrack);

            if (setTrackTo == -1)
            {
                log.warn("Unable to find audio track postion in IJKPlayer!");
                return;
            }

            if (currentTrack != setTrackTo)
            {
                log.debug("Setting audio track to IJKPlayer Track Position: {}", setTrackTo);
                ((IjkMediaPlayer) player).selectTrack(setTrackTo);
            }

        }
        else
        {
            log.debug("setAudioTrack player not ready.  Storing values for setting when player is initialized");

            this.initialAudioStreamPos = streamPos;
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
        //Displaying subtitle/timedtext does not appear to be supported at this time.
        log.debug("TODO: setSubtitleTrack Called StreamPosition: {}", streamPos);

        if (player == null)
        {
            this.initialTextStreamPos = streamPos;
        }
        else
        {
            // NOT IMPLEMENTED YET, let's comment out the code until it is
            // since it's causing problems in the playback.
//            int currentTrack = ((IjkMediaPlayer) player).getSelectedTrack(IjkTrackInfo.MEDIA_TRACK_TYPE_SUBTITLE);
//            int trackPos = this.getSubtitleTrackPosition(streamPos);
//
//            if (playerReady && currentTrack != trackPos && trackPos != -1) {
//                log.debug("FUNCTION NOT SUPPORTED (Setting subtitle to IJKPosition): {}", trackPos);
//                //((IjkMediaPlayer) player).selectTrack(trackPos);
//            }
        }
    }

    @Override
    public int getSelectedSubtitleTrack()
    {
        return DISABLE_TRACK;
    }

    protected void releasePlayer()
    {
        stoppedForResume = false;
        if(mediaSession != null)
        {
            log.debug("Media Session RELEASE");
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }

        if (player == null)
            return;
        log.debug("Releasing Player");

        try
        {
            try
            {
                if (player.isPlaying())
                {
                    try
                    {
                        player.pause();
                    }
                    catch (Throwable t)
                    {
                    }
                    try
                    {
                        player.stop();
                    }
                    catch (Throwable t)
                    {
                    }
                }
            }
            catch (Throwable t)
            {
            }

            // now release the datasource
            // https://github.com/OpenSageTV/sagetv-miniclient/issues/54
            try
            {
                releaseDataSource();
            }
            catch (Throwable t)
            {
            }

            try
            {
                player.reset();
            }
            catch (Throwable t)
            {
            }
            log.debug("Player Is Stopped");
        }
        catch (Throwable t)
        {
        }


        try
        {
            player.release();
        }
        catch (Throwable t)
        {
        }

        try
        {
            clearSurface();
        }
        catch (Throwable t)
        {
        }
        player = null;

        super.releasePlayer();
    }

    private void updateMediaSessionPlaybackState(long playbackPostion)
    {
        if(mediaSession != null)
        {
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
            stateBuilder.setActions(this.getMediaSessionActions());

            if (player != null && getState() == PLAY_STATE)
            {
                stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, playbackPostion, 1.0f);
            }
            else
            {
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
