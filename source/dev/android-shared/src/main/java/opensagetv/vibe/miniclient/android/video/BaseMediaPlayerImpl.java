package opensagetv.vibe.miniclient.android.video;

import android.graphics.Bitmap;
import android.app.Activity;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.dvd.DvdDiagnostics;
import opensagetv.vibe.miniclient.events.VideoInfoRefresh;
import opensagetv.vibe.miniclient.events.VideoInfoShow;
import opensagetv.vibe.miniclient.net.HasPushBuffer;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import opensagetv.vibe.miniclient.uibridge.Rectangle;
import opensagetv.vibe.miniclient.uibridge.RectangleF;
import opensagetv.vibe.miniclient.util.AspectModeManager;
import opensagetv.vibe.miniclient.util.Utils;
import opensagetv.vibe.miniclient.util.VerboseLogging;
import opensagetv.vibe.miniclient.util.VideoInfo;
import opensagetv.vibe.miniclient.video.HasVideoInfo;
import opensagetv.vibe.miniclient.video.FullscreenPlaybackPolicy;
import opensagetv.vibe.miniclient.video.PlaybackSessionController;
import opensagetv.vibe.miniclient.video.VideoInfoResponse;

//import org.videolan.libvlc.LibVLC;

/**
 * Created by seans on 06/10/15.
 */
public abstract class BaseMediaPlayerImpl<TPlayer, TDataSource> implements MiniPlayerPlugin, HasVideoInfo
{


    protected static final long PTS_ROLLOVER = 0x200000000L * 1000000L / 90000L / 1000L;


    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected TPlayer player;
    protected TDataSource dataSource;
    protected final AndroidUIController context;
    protected boolean pushMode;
    protected boolean playerReady;
    protected boolean createPlayerOnUI = true;
    protected boolean waitForPlayer = true;
    protected int state;
    protected boolean eos = false;
    protected boolean seekPending = false;
    protected String lastUri;
    /** Server DVD VM is pushing a MIM-produced MPEG-TS representation. */
    protected boolean dvdMimTransport;
    protected long lastMediaTime = -1;
    protected boolean flushed = false;
    protected VideoInfo videoInfo = null;

    // this is mainly for testing... when we force a different UI aspect than what we really have
    private boolean uiAspectChanged = true;
    Rectangle lastVidSrc = null, lastVidDest = null;

    AspectModeManager aspectModeManager = new AspectModeManager();
    boolean debug_ar = false;
    protected boolean httpls = false;

    // Stock SageTV STVs can initially open a recording in an embedded preview.
    // Promote that preview with the normal TV user event after playback starts.
    // Keep this client-side so it also works with an unmodified SageTV server.
    private final PlaybackSessionController playbackSessions = new PlaybackSessionController();
    protected volatile PlaybackSessionController.Token lastPlaybackOperation;
    /**
     * Non-null while a replacement player is being released and configured.
     *
     * <p>{@link #releasePlayer()} normally exposes EOS. During OPENURL that
     * release belongs to the previous player, however, and must not leak into
     * the new session. A concurrent GETMEDIATIME would otherwise serialize
     * {@code -1}; stock SageTV treats {@code 0xFFFFFFFF} as EOS and briefly
     * paints the timeline at the end before the first frame resets it to zero.</p>
     */
    private volatile PlaybackSessionController.Token loadTransitionToken;
    private boolean fullscreenPromotionSent;
    // Give the STV/Core time to perform its normal asynchronous transition.
    // TV is a toggle; an eager fallback can undo that transition and leave
    // playback on Main Menu in the embedded preview.
    private static final long FULLSCREEN_PROMOTION_DELAY_MS = 2500;
    private volatile DvdHighlightOverlay dvdHighlightOverlay;
    protected final DvdSubpictureDecoder dvdSubpictureDecoder;
    private volatile int dvdStc;
    private volatile boolean dvdTimingReceived;
    private volatile int dvdFormat;
    private volatile long dvdPtsOffset90Khz;
    private volatile int subtitleOffsetMs;

    public BaseMediaPlayerImpl(AndroidUIController activity, boolean createPlayerOnUI, boolean waitForPlayer)
    {
        this.context = activity;
        this.createPlayerOnUI = createPlayerOnUI;
        this.waitForPlayer = waitForPlayer;
        state = NO_STATE;
        videoInfo = new VideoInfo();
        debug_ar = context.getClient().properties().getBoolean(PrefStore.Keys.debug_ar, false);
        Integer sessionSubtitleOffset = ActivePlayerSessionOverrides.getSubtitleOffsetMs();
        subtitleOffsetMs = sessionSubtitleOffset == null
                ? Math.max(-2_000, Math.min(2_000, context.getClient().properties().getInt(
                        PrefStore.Keys.playback_subtitle_offset_ms, 0)))
                : sessionSubtitleOffset;
        dvdSubpictureDecoder = new DvdSubpictureDecoder(new DvdSubpictureDecoder.Listener()
        {
            @Override
            public void onSubpicture(final Bitmap bitmap, final boolean visible,
                    final long presentationTimeUs, final long eventGeneration)
            {
                scheduleDvdSubpicture(bitmap, visible, presentationTimeUs, eventGeneration);
            }
        });
    }

    private void scheduleDvdSubpicture(final Bitmap bitmap, final boolean visible,
            final long presentationTimeUs, final long eventGeneration)
    {
        dvdSubpictureDecoder.noteOverlayScheduled(presentationTimeUs);
        BaseMediaPlayerImpl.this.context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                final Runnable clockedUpdate = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        long clockUs = getDvdPresentationPositionUs();
                        if (!dvdSubpictureDecoder.isEventCurrent(eventGeneration))
                        {
                            dvdSubpictureDecoder.noteOverlayStale(presentationTimeUs, clockUs);
                            return;
                        }
                        long adjustedPresentationTimeUs = presentationTimeUs ==
                                DvdSubpictureDecoder.TIME_UNSET
                                ? presentationTimeUs
                                : presentationTimeUs + subtitleOffsetMs * 1_000L;
                        if (adjustedPresentationTimeUs != DvdSubpictureDecoder.TIME_UNSET)
                        {
                            long remainingUs = adjustedPresentationTimeUs - clockUs;
                            if (remainingUs > 40_000L)
                            {
                                long delayMs = Math.max(10L, Math.min(250L, remainingUs / 1_000L));
                                context.getVideoView().postDelayed(this, delayMs);
                                return;
                            }
                        }
                        dvdSubpictureDecoder.noteOverlayApplied(presentationTimeUs, clockUs);
                        if (dvdHighlightOverlay == null && !visible)
                            return;
                        if (dvdHighlightOverlay == null)
                        {
                            dvdHighlightOverlay = new DvdHighlightOverlay(
                                    BaseMediaPlayerImpl.this.context.getContext());
                            DvdHighlightOverlay.attach(BaseMediaPlayerImpl.this.context,
                                    dvdHighlightOverlay);
                        }
                        dvdHighlightOverlay.applySubpicture(bitmap, visible);
                    }
                };
                clockedUpdate.run();
            }
        });
    }

    /** Current decoder presentation clock for scheduling DVD SPU events. */
    protected long getDvdPresentationPositionUs()
    {
        return Math.max(0L, getPlayerMediaTimeMillis(0L)) * 1_000L;
    }

    public TPlayer getPlayer()
    {
        return player;
    }

    @Override
    public void free()
    {
        loadTransitionToken = null;
        playbackSessions.endSession(PlaybackSessionController.Operation.FREE);
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.info("Freeing Media Player");
        }

        releasePlayer();
    }

    @Override
    public void setPushMode(boolean b)
    {
        this.pushMode = b;
    }

    @Override
    public void load(byte majorHint, byte minorHint, String encodingHint, final String urlString, String hostname, boolean timeshifted, long buffersize)
    {
        final PlaybackSessionController.Token loadSession = playbackSessions.beginSession();
        loadTransitionToken = loadSession;
        onPlaybackLoadStarted();
        lastPlaybackOperation = loadSession;
        fullscreenPromotionSent = false;
        final String finalUrl;

        lastUri = urlString;
        // OPENURL is the authoritative transport declaration. A DVD Hybrid
        // transition can replace the native DVD player while the previous
        // player is still being released on the UI thread; do not let the
        // mutable MediaCmd flag leave a push:dvd URL configured as Pull.
        if (urlString != null && urlString.startsWith("push:"))
        {
            pushMode = true;
        }
        dvdMimTransport = urlString != null && urlString.startsWith("push:dvd")
                && urlString.contains("vibe_transport=mim_ts_v1");
        lastMediaTime = -1;
        eos = false;
        seekPending = false;
        flushed = false;

        String url = urlString;
        httpls = urlString.startsWith("http://");
        if (httpls)
        {
            if (url.contains("HOSTNAME"))
            {
                url = url.replace("HOSTNAME", context.getClient().getConnectedServerInfo().address + ":" + context.getClient().getConnectedServerInfo().port);
            }
        }
        else
        {
            if (!urlString.startsWith("stv://"))
            {
                url = "stv://" + context.getClient().getConnectedServerInfo().address + "/" + urlString;
            }
        }
        finalUrl = url;
        log.debug("load(): url: {}", url);

        if (createPlayerOnUI)
        {
            context.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if (!playbackSessions.isCurrentSession(loadSession))
                    {
                        log.debug("Ignoring stale queued load for {}", finalUrl);
                        return;
                    }
                    try
                    {
                        releasePlayer();
                        // releasePlayer() belongs to the previous session. The
                        // transition token prevents it from publishing stale EOS.
                        eos = false;
                        state = LOADED_STATE;
                        context.setupVideoFrame();
                        setupPlayer(finalUrl);

                        if (dataSource == null && !httpls)
                        {
                            throw new RuntimeException("setupPlayer must create a datasource");
                        }
                    }
                    finally
                    {
                        finishLoadTransition(loadSession);
                    }
                }
            });
        }
        else
        {
            Looper.prepare();
            if (!playbackSessions.isCurrentSession(loadSession))
            {
                log.debug("Ignoring stale direct load for {}", finalUrl);
                return;
            }
            try
            {
                releasePlayer();
                // Match the UI-thread load path: release belongs to the old session,
                // while the new session must start in LOADED rather than EOS state.
                eos = false;
                state = LOADED_STATE;
                log.debug("JVL - Creating player on thread ->", Thread.currentThread().getName());
                setupPlayer(finalUrl);

                if (dataSource == null && !httpls)
                {
                    throw new RuntimeException("setupPlayer must create a datasource");
                }
            }
            finally
            {
                finishLoadTransition(loadSession);
            }
        }

        if (debug_ar)
        {
            // if debug AR is turned on then show the AR UI immediately
            context.getClient().eventbus().post(new VideoInfoShow());
        }
    }

    /** Optional backend hook for clearing per-stream extractor state. */
    protected void onPlaybackLoadStarted()
    {
    }

    protected abstract void setupPlayer(String sageTVurl);

    public void message(final String msg)
    {
        AppUtil.message(msg);
    }

    protected void playerFailed()
    {
        stop();
        state = EOS_STATE;
        eos = true;
        releasePlayer();
        notifySageTVStop();
        message(context.getContext().getString(R.string.msg_player_failed, lastUri));
    }

    protected void notifySageTVStop()
    {
        // This causes queded up items to fail to play.. so we can't really do this.
        // EventRouter.post(MiniclientApplication.get().getClient(), EventRouter.MEDIA_STOP);
    }

    /**
     * Delegatest the media time to the actual player implementation.  lastServerTime is passed
     * so that if the player needs to adjust the time based on the last time the buffer had dispose
     * then it can use this value.
     *
     * @param lastServerTime
     * @return
     */
    protected abstract long getPlayerMediaTimeMillis(long lastServerTime);

    @Override
    public long getMediaTimeMillis(long lastServerTime)
    {
        // OPENURL owns a new logical playback session before its UI-thread
        // player replacement completes. Never expose the old player's EOS
        // sentinel during that interval.
        if (loadTransitionToken != null)
            return 0;

        if (lastMediaTime == -1) lastMediaTime = lastServerTime;

        if (!playerReady || player == null)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("getMediaTimeMillis(): Player not ready, returning 0");
            }
            // NOTE: SageTV generally expects 0 during seek/flush calls
            return 0;
        }

        // NOTE: when using seekPending check here, ijkplayer tends to send back
        // wrong values, so we removed seekPending checks.
        if (flushed)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("getMediaTimeMillis(): Player seeking or waiting for data, returning 0");
            }
            // NOTE: SageTV generally expects 0 during seek/flush calls
            return 0;
        }
        //JVL - Removing this state so that you can seek on pause || state == PAUSE_STAT
        if (state == STOPPED_STATE || state == EOS_STATE)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("getMediaTimeMillis(): Player State {} returning last time {}", state, Utils.toHHMMSS(lastMediaTime, true));
            }

            return lastMediaTime;
        }

        if (state == NO_STATE || state == LOADED_STATE)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("getMediaTimeMillis(): Player State Not Ready {} returning 0", state);
            }

            return 0;
        }

        long mt = getPlayerMediaTimeMillis(lastServerTime);
        if (mt <= 0)
        {
            if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            {
                log.debug("getMediaTimeMillis() is {} after a flush/seek.  Using 0, until data shows up.", mt);
            }

            return 0;
        }
        // we have some data, so we are not flushing/seeking
        lastMediaTime = mt;
        return mt;
    }

    @Override
    public int getState()
    {
        if (eos) return MiniPlayerPlugin.EOS_STATE;
        return state;
    }

    @Override
    public void setMute(boolean b)
    {

    }

    @Override
    public void stop()
    {
        loadTransitionToken = null;
        // SageTV STOP is not necessarily terminal. Stock servers legitimately
        // retain the loaded MiniPlayer and later issue SEEK/PLAY without a new
        // OPENURL (for example Stop followed by Restart). Keep the generation
        // active so that the backend's queued PLAY is not rejected as stale.
        // MEDIACMD_DEINIT -> free() remains the true session boundary.
        beginPlaybackOperation(PlaybackSessionController.Operation.STOP);
        state = STOPPED_STATE;
        context.removeVideoFrame();
    }

    protected void clearSurface()
    {
//        if (mSurface != null) {
//            context.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        log.debug("Clearing Canvas");
//                        Canvas canvas = mSurface.getHolder().lockCanvas(null);
//                        canvas.drawColor(Color.BLACK);
//                        mSurface.getHolder().unlockCanvasAndPost(canvas);
//                    } catch (Throwable t) {
//                        log.debug("Failed to clear canvas");
//                    }
//                }
//            });
//        }
    }

    @Override
    public void pause()
    {
        beginPlaybackOperation(PlaybackSessionController.Operation.PAUSE);
        state = PAUSE_STATE;
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.debug("Pause was called");
        }
    }

    @Override
    public void play()
    {
        beginPlaybackOperation(PlaybackSessionController.Operation.PLAY);
        state = PLAY_STATE;
        scheduleFullscreenPromotionCheck();
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.debug("Play was called");
        }
    }

    @Override
    public void seek(long timeInMS)
    {
        beginPlaybackOperation(PlaybackSessionController.Operation.SEEK);
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.debug("SEEK: {}", timeInMS);
        }
        seekPending = true;
    }

    @Override
    public void setServerEOS()
    {
        beginPlaybackOperation(PlaybackSessionController.Operation.INACTIVE_FILE);
        // tell the datasource that we have all the data
        if (dataSource != null && dataSource instanceof HasPushBuffer)
        {
            ((HasPushBuffer) dataSource).setEOS();
        }
        // we don't set our EOS until AFTER the player stream has ended
        //eos=true;
        //state=EOS_STATE;
        log.debug("Server sent us EOS");
    }

    @Override
    public long getLastFileReadPos()
    {
        if (dataSource instanceof HasPushBuffer)
        {
            return ((HasPushBuffer) dataSource).getBytesRead();
        }
        else
        {
            // return (long)player.getPosition();
            return 0;
        }
    }

    @Override
    public int getVolume()
    {
        return 0;
    }

    @Override
    public int setVolume(float v)
    {
        return 0;
    }

    @Override
    public void setVideoRectangles(final Rectangle srcRect, final Rectangle destRect, boolean hideCursor)
    {
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            log.debug("setVideoRectangles: SRC: {}, DEST: {}", srcRect, destRect);
        if (srcRect == null || destRect == null) return;

        if (debug_ar)
        {
            lastVidSrc = srcRect.copy();
            lastVidDest = destRect.copy();
        }

        // we are using our our aspect modes
        if (srcRect.width == 0)
        {
            // need to translate the destRect from 0,0,4096,4096 where x,y is center not bottom right
            Rectangle rect = destRect.copy();
            rect.x = destRect.x - (destRect.width / 2);
            rect.y = destRect.y - (destRect.height / 2);
            Dimension screen = context.getClient().getUIRenderer().getMaxScreenSize();
            rect.x = (int) (screen.getWidth() * ((float) rect.x / 4096f));
            rect.y = (int) (screen.getHeight() * ((float) rect.y / 4096f));
            rect.width = (int) (screen.getWidth() * ((float) destRect.width / 4096f));
            rect.height = (int) (screen.getHeight() * ((float) destRect.height / 4096f));

            if (uiAspectChanged || videoInfo.changed || !videoInfo.destRect.equals(rect))
            {
                if (rect.x == 0)
                {
                    Rectangle arRect = aspectModeManager.doMeasure(videoInfo, rect.asFloatRect(), context.getClient().getUIRenderer().getUIAspectRatio()).asIntRect();
                    videoInfo.updateDestRect(arRect.asFloatRect());
                    if (uiAspectChanged || videoInfo.changed)
                    {
                        videoInfo.changed = false;
                        updatePlayerView(arRect);
                        log.debug("Updating Full Screen Video View from {} to {} adjusted with AR: {}", destRect, rect, arRect);
                    }
                }
                else
                {
                    RectangleF vid = aspectModeManager.doMeasure(videoInfo, rect.asFloatRect().position(0, 0), context.getClient().getUIRenderer().getUIAspectRatio());
                    log.debug("Updating Window Video View Video in View {}", vid);
                    // adust video for the dest rect offset
                    vid.x = vid.x + rect.x;
                    vid.y = vid.y + rect.y;
                    videoInfo.updateDestRect(vid);
                    if (uiAspectChanged || videoInfo.changed)
                    {
                        videoInfo.changed = false;
                        updatePlayerView(vid.asIntRect());
                        log.debug("Updating Window Video View from {} to {} with video: {}", destRect, rect, vid);
                    }
                }
                uiAspectChanged = false;
            }
            return;
        }

        // aspect modes coming from sagetv server if we get here
        if (uiAspectChanged || videoInfo.changed || !videoInfo.size.equals(srcRect) || !videoInfo.destRect.equals(destRect))
        {
            uiAspectChanged = false;
            // need to adjust video size/position
            videoInfo.size.update(srcRect);
            videoInfo.destRect.update(destRect);
            videoInfo.changed = false;
            updatePlayerView(videoInfo.destRect.asIntRect());
        }
    }

    @Override
    public Dimension getVideoDimensions()
    {
        return videoInfo.size.getDimension().asIntDimension();
    }

    @Override
    public void pushData(byte[] cmddata, int bufDataOffset, int buffSize) throws IOException
    {
        //log.debug("pushData()");
        if (dataSource instanceof HasPushBuffer)
        {
            ((HasPushBuffer) dataSource).pushBytes(cmddata, bufDataOffset, buffSize);
        }
        flushed = false;
    }

    @Override
    public void flush()
    {
        beginPlaybackOperation(PlaybackSessionController.Operation.FLUSH);
        log.debug("JVL - flush called!");
        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
        {
            log.debug("dispose()");
        }

        if (dataSource instanceof HasPushBuffer)
        {
            log.debug("JVL - HasPushBuffer flush called!");
            ((HasPushBuffer) dataSource).flush();
        }

        flushed = true;
    }

    @Override
    public int getBufferLeft()
    {
        if (dataSource instanceof HasPushBuffer)
        {
            if (state == EOS_STATE || eos)
            {
                log.debug("------------------------- Telling SageTV that EOS was reached in client --------------------------------");
                return -1;
            }

            return ((HasPushBuffer) dataSource).bufferAvailable();
        }
        else
        {
            return PushBufferDataSource.PIPE_SIZE;
        }
    }

    @Override
    public void run()
    {

    }


    protected void setVideoSize(int width, int height, float ar)
    {
        videoInfo.update(width, height, ar);
        updatePlayerView();
    }

    protected void setVideoSize(int width, int height, int sarNum, int sarDen)
    {
        videoInfo.update(width, height, sarNum, sarDen);
        updatePlayerView();
    }

    protected void releasePlayer()
    {
        log.debug("Releasing Player");
        if (context.getContext() instanceof Activity)
            DisplayRefreshController.apply((Activity) context.getContext(), this,
                    DisplayRefreshController.OFF);
        DvdHighlightOverlay.detach(context, dvdHighlightOverlay);
        dvdHighlightOverlay = null;
        dvdSubpictureDecoder.reset();
        player = null;
        videoInfo.reset();
        releaseDataSource();
        dataSource = null;
        if (loadTransitionToken == null)
        {
            state = EOS_STATE;
            eos = true;
        }
        uiAspectChanged = true;
        context.removeVideoFrame();
    }

    private void finishLoadTransition(PlaybackSessionController.Token loadSession)
    {
        // A newer queued OPENURL owns its own token. Do not let completion of
        // an older runnable clear the newer transition guard.
        if (loadTransitionToken == loadSession)
            loadTransitionToken = null;
    }

    protected void applySavedRefreshRateIfPossible()
    {
        if (!(context.getContext() instanceof Activity))
            return;
        String policy = ActivePlayerSessionOverrides.resolveRefreshRatePolicy(
                context.getClient().properties().getString(
                        PrefStore.Keys.playback_refresh_rate_matching,
                        DisplayRefreshController.OFF));
        if (!DisplayRefreshController.OFF.equals(policy))
            DisplayRefreshController.apply((Activity) context.getContext(), this, policy);
    }

    protected void releaseDataSource()
    {
        if (dataSource instanceof HasPushBuffer)
        {
            ((HasPushBuffer) dataSource).release();
        }
    }

    @Override
    public void setVideoAdvancedAspect(String aspectMode)
    {
        this.videoInfo.updateARMode(aspectMode);
        updatePlayerView();
    }

    @Override
    public void dvdNewCell(int payloadSize, byte[] payload)
    {
        if (payload == null || payloadSize < 4 || payload.length < 4)
            return;
        dvdSubpictureDecoder.clearHighlight();
        int offset45Khz = ((payload[0] & 0xFF) << 24)
                | ((payload[1] & 0xFF) << 16)
                | ((payload[2] & 0xFF) << 8)
                | (payload[3] & 0xFF);
        // The historical SageTV hardware MiniClient applies NEWCELL's signed
        // offset in 45 kHz units to each 90 kHz PES PTS/DTS value.
        dvdPtsOffset90Khz = (long) offset45Khz * 2L;
    }

    @Override
    public void dvdSetClut(int payloadSize, byte[] payload)
    {
        if (payload != null && payloadSize >= 64)
            dvdSubpictureDecoder.setClut(payload);
    }

    @Override
    public void dvdSetSpuControl(int payloadSize, byte[] payload)
    {
        final byte[] highlight = payload == null ? null : payload.clone();
        dvdSubpictureDecoder.setHighlight(highlight);
    }

    @Override
    public boolean isDvdMenuNavigationActive()
    {
        return dvdSubpictureDecoder.isHighlightActive();
    }

    @Override
    public void dvdSetStc(int stc)
    {
        dvdStc = stc;
        dvdTimingReceived = true;
    }

    @Override
    public boolean supportsSubtitleOffset()
    {
        // Native DVD SPUs are decoded ahead of their presentation clock, so
        // both positive and negative bounded offsets can be honored. Text
        // callbacks on the other paths arrive at presentation time and must
        // not claim an unsupported negative/early offset.
        return dvdTimingReceived && !dvdMimTransport;
    }

    @Override
    public boolean setSubtitleOffsetMillis(int offsetMs)
    {
        if (!supportsSubtitleOffset())
            return false;
        subtitleOffsetMs = Math.max(-2_000, Math.min(2_000, offsetMs));
        return true;
    }

    @Override
    public int getSubtitleOffsetMillis()
    {
        return subtitleOffsetMs;
    }

    @Override
    public void dvdSetFormat(int format)
    {
        dvdFormat = format;
    }

    @Override
    public void dvdSetStream(int streamType, int streamPosition)
    {
        // MiniPlayer's DVD stream type 1 is the authored subpicture stream.
        // Audio stream selection continues through setAudioTrack().
        if (streamType == 1)
        {
            if (dvdMimTransport)
                setSubtitleTrack(streamPosition == 62 || (streamPosition & 0x80) != 0
                        ? DISABLE_TRACK : (streamPosition & 0x1f));
            else
                dvdSubpictureDecoder.setSubpictureStream(streamPosition);
        }
    }

    public int getDvdStcForDebug()
    {
        return dvdStc;
    }

    public int getDvdFormatForDebug()
    {
        return dvdFormat;
    }

    public long getDvdPtsOffset90KhzForDebug()
    {
        return dvdPtsOffset90Khz;
    }

    public DvdDiagnostics getDvdSubpictureDiagnosticsForDebug()
    {
        return dvdSubpictureDecoder.diagnosticsForDebug();
    }

    public void updatePlayerView()
    {
        Dimension screen = context.getClient().getUIRenderer().getMaxScreenSize();
        Rectangle rect = aspectModeManager.doMeasure(videoInfo, new RectangleF(0, 0, screen.width, screen.height), context.getClient().getUIRenderer().getUIAspectRatio()).asIntRect();

        if (VerboseLogging.DETAILED_PLAYER_LOGGING)
            log.debug("updatePlayerView: Video Size {}, Screen Size {}, Calculated: {}", videoInfo, videoInfo.destRect, rect);

        updatePlayerView(rect);
    }

    public void updatePlayerView(final Rectangle rect)
    {
        final PlaybackSessionController.Token session = beginPlaybackOperation(
                PlaybackSessionController.Operation.SURFACE_REPLACEMENT);
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!playbackSessions.isCurrentSession(session))
                {
                    log.debug("Ignoring stale surface update for a replaced playback session");
                    return;
                }
                log.debug("updatePlayerView: Video Size {}", rect);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) context.getVideoView().getLayoutParams();
                lp.width = rect.width;
                lp.height = rect.height;
                lp.leftMargin = rect.x;
                lp.topMargin = rect.y;
                context.getVideoView().setLayoutParams(lp);
                context.getVideoView().requestLayout();
                if (dvdHighlightOverlay != null)
                    dvdHighlightOverlay.matchVideoView(context.getVideoView());

                scheduleFullscreenPromotionCheck();

                if (debug_ar)
                {
                    context.getClient().eventbus().post(VideoInfoRefresh.INSTANCE);
                }
            }
        });
    }

    /**
     * A recording selected from a stock SageTV recordings screen may begin in
     * its embedded preview. Once active playback is confirmed, send the same
     * TV event a user would press to enter the MediaPlayer OSD. The generation
     * check prevents an old delayed callback from affecting a later file.
     */
    private void scheduleFullscreenPromotionCheck()
    {
        if (fullscreenPromotionSent || state != PLAY_STATE || context.getVideoView() == null)
        {
            return;
        }

        final PlaybackSessionController.Token session = playbackSessions.currentSessionToken();
        context.getVideoView().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                if (!playbackSessions.isCurrentSession(session) || fullscreenPromotionSent
                        || state != PLAY_STATE || !playerReady || player == null)
                {
                    return;
                }

                Dimension screen = context.getClient().getUIRenderer().getMaxScreenSize();
                int width = context.getVideoView().getWidth();
                int height = context.getVideoView().getHeight();
                MenuHint menuHint = null;
                if (context.getClient().getCurrentConnection() != null)
                {
                    menuHint = context.getClient().getCurrentConnection().getMenuHint();
                }
                String menuName = menuHint == null ? null : menuHint.menuName;
                String popupName = menuHint == null ? null : menuHint.popupName;
                if (FullscreenPlaybackPolicy.isPlaybackMenu(menuName))
                {
                    fullscreenPromotionSent = true;
                    log.debug("Skipping preview promotion because SageTV already entered {}", menuName);
                    return;
                }

                if (FullscreenPlaybackPolicy.shouldPromote(menuName, popupName,
                        width, height, screen.width, screen.height))
                {
                    fullscreenPromotionSent = true;
                    log.info("Promoting stable SageTV embedded preview ({}x{} on {}x{}, menu={}) to fullscreen",
                            width, height, screen.width, screen.height, menuName);
                    EventRouter.postCommand(context.getClient(), SageCommand.TV);
                }
            }
        }, FULLSCREEN_PROMOTION_DELAY_MS);
    }

    protected final PlaybackSessionController.Token currentPlaybackSession()
    {
        return playbackSessions.currentSessionToken();
    }

    protected final PlaybackSessionController.Token beginPlaybackOperation(
            PlaybackSessionController.Operation operation)
    {
        PlaybackSessionController.Token token = playbackSessions.beginOperation(operation);
        lastPlaybackOperation = token;
        return token;
    }

    protected final boolean isCurrentPlaybackSession(PlaybackSessionController.Token token)
    {
        return playbackSessions.isCurrentSession(token);
    }

    protected final boolean isLatestPlaybackOperation(PlaybackSessionController.Token token)
    {
        return playbackSessions.isLatestOperation(token);
    }

    @Override
    public VideoInfoResponse getVideoInfo()
    {
        if (player != null)
        {
            VideoInfoResponse vi = new VideoInfoResponse();
            vi.videoInfo = videoInfo.copy();
            vi.uiAspectRatio = context.getClient().getUIRenderer().getUIAspectRatio();
            vi.uiScreenSizePixels = new Rectangle(0, 0, context.getClient().getUIRenderer().getMaxScreenSize().width, context.getClient().getUIRenderer().getMaxScreenSize().height).asFloatRect();
            vi.uri = lastUri;
            vi.mediaTime = lastMediaTime;
            vi.state = state;
            vi.pushMode = pushMode;
            return vi;
        }
        return null;
    }

    public void notifyUIAspectChanged()
    {
        uiAspectChanged = true;
        if (debug_ar)
        {
            log.debug("UI Aspect Ratio Changed:  Notifying Video.");
            // only do this if we are debugging AR, since that's really when this would happen
            setVideoRectangles(lastVidSrc, lastVidDest, true);
        }
    }
}
