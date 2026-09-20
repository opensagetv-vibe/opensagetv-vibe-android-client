package opensagetv.vibe.miniclient.android.video;

import android.graphics.Bitmap;
import android.app.Activity;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

import opensagetv.vibe.miniclient.MenuHint;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.dvd.DvdDiagnostics;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticSessionSpool;
import opensagetv.vibe.miniclient.events.VideoInfoRefresh;
import opensagetv.vibe.miniclient.events.VideoInfoShow;
import opensagetv.vibe.miniclient.net.HasPushBuffer;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.media.CaptionSlotPolicy;
import opensagetv.vibe.miniclient.media.LegacyExtenderSubpictureCommand;
import opensagetv.vibe.miniclient.media.TeletextSubtitleEngine;
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
import opensagetv.vibe.miniclient.video.TeletextCea608Bridge;
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
    private static final int NO_SERVER_SUBPICTURE_COMMAND = Integer.MIN_VALUE;
    private volatile int pendingServerSubpictureCommand = NO_SERVER_SUBPICTURE_COMMAND;
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
    private volatile boolean fullscreenPromotionSent;
    private volatile boolean fullscreenPromotionCheckScheduled;
    private volatile int fullscreenPromotionCheckCount;
    private volatile int fullscreenPromotionStablePreviewCount;
    private volatile int fullscreenPromotionStableFullscreenCount;
    private volatile int fullscreenPromotionCommandCount;
    private volatile String fullscreenPromotionLastDecision = "not_started";
    // Give the STV/Core time to perform its normal asynchronous transition.
    // TV is a toggle; an eager fallback can undo that transition and leave
    // playback on Main Menu in the embedded preview.
    // The TV event is guarded by an active player, an embedded destination,
    // no popup and a non-OSD menu. A long delay only exposes SageMC's preview
    // during normal playback startup; re-check shortly after the ready/rect
    // callbacks instead.
    private static final long FULLSCREEN_PROMOTION_DELAY_MS = 350;
    private static final int FULLSCREEN_PROMOTION_MAX_CHECKS = 24;
    private static final int FULLSCREEN_PROMOTION_STABLE_CHECKS = 6;
    private volatile DvdHighlightOverlay dvdHighlightOverlay;
    protected final DvdSubpictureDecoder dvdSubpictureDecoder;
    private volatile int dvdStc;
    private volatile boolean dvdTimingReceived;
    private volatile int dvdFormat;
    private volatile long dvdPtsOffset90Khz;
    private volatile int subtitleOffsetMs;
    private volatile TeletextSubtitleEngine.Service[] teletextServices =
            new TeletextSubtitleEngine.Service[0];
    private volatile int selectedTeletextTrack = DISABLE_TRACK;
    private volatile TeletextSubtitleOverlay teletextOverlay;
    private volatile long teletextCueUpdateCount;
    private volatile String currentTeletextCueText = "";
    private volatile long teletextOverlayGeneration;
    private volatile boolean teletextClockScheduled;
    private volatile long teletextClockServerBaseMs;
    private volatile long teletextClockDrainCount;
    private volatile long teletextClockLastMediaTimeMs = -1L;
    /** True while Teletext cues are being returned to SageTV through event 225. */
    private volatile boolean teletextLegacyBridgeEnabled = true;
    private static final long TELETEXT_CLOCK_INTERVAL_MS = 100L;
    private final TeletextCea608Bridge teletextLegacyBridge =
            new TeletextCea608Bridge(new TeletextCea608Bridge.Sink()
            {
                @Override public void postSubtitleInfo(long pts45Khz, long duration45Khz,
                        byte[] data, int flags)
                {
                    MiniClientConnection connection = BaseMediaPlayerImpl.this.context
                            .getClient().getCurrentConnection();
                    if (connection != null)
                        connection.postSubtitleInfo(pts45Khz, duration45Khz, data, flags);
                }
            });
    private final TeletextSubtitleEngine.Listener teletextListener =
            new TeletextSubtitleEngine.Listener()
            {
                @Override public void onTeletextServicesChanged(
                        TeletextSubtitleEngine.Service[] services)
                {
                    teletextServices = services == null
                            ? new TeletextSubtitleEngine.Service[0] : services.clone();
                    applyTeletextCcMappings();
                    scheduleTeletextClock();
                    log.info("Discovered {} DVB Teletext subtitle service(s)",
                            teletextServices.length);
                }

                @Override public void onTeletextCue(TeletextSubtitleEngine.Cue cue)
                {
                    // Teletext discovery can precede Media3/Exo native track
                    // publication. Re-resolve the two virtual slots when a
                    // cue arrives so a later DVB track changes the callback
                    // mapping from the provisional Teletext CC1 assignment
                    // to the final DVB CC1 / Teletext CC2 assignment.
                    applyTeletextCcMappings();
                    teletextLegacyBridge.enqueue(cue);
                    if (cue != null && cue.trackId == selectedTeletextTrack)
                        scheduleTeletextCue(cue);
                }
            };

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
        fullscreenPromotionCheckScheduled = false;
        fullscreenPromotionCheckCount = 0;
        fullscreenPromotionStablePreviewCount = 0;
        fullscreenPromotionStableFullscreenCount = 0;
        fullscreenPromotionCommandCount = 0;
        fullscreenPromotionLastDecision = "load_reset";
        // Readiness belongs to the player being opened, never the retained
        // value from the player this LOAD is about to release. Otherwise a
        // replacement can promote/accept the previous file's rectangle.
        playerReady = false;
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
        pendingServerSubpictureCommand = NO_SERVER_SUBPICTURE_COMMAND;
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
                        // Some backends release an asynchronously-owned prior
                        // player once more during setup. Activate only after
                        // that replacement step so the new subtitle session
                        // cannot be torn down by the old player.
                        startTeletextSession();

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
                startTeletextSession();

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

    private void startTeletextSession()
    {
        teletextOverlayGeneration++;
        teletextServices = new TeletextSubtitleEngine.Service[0];
        selectedTeletextTrack = DISABLE_TRACK;
        teletextCueUpdateCount = 0L;
        currentTeletextCueText = "";
        teletextClockScheduled = false;
        teletextClockServerBaseMs = 0L;
        teletextClockDrainCount = 0L;
        teletextClockLastMediaTimeMs = -1L;
        teletextLegacyBridgeEnabled = true;
        teletextLegacyBridge.clearPending();
        TeletextSubtitleEngine.activate(teletextListener);
    }

    /** Add decoder-owned Teletext services without changing native track ids. */
    protected SubtitleTrack[] appendTeletextSubtitleTracks(SubtitleTrack[] nativeTracks)
    {
        SubtitleTrack[] safe = nativeTracks == null ? new SubtitleTrack[0] : nativeTracks;
        TeletextSubtitleEngine.Service[] services = teletextServices;
        SubtitleTrack[] combined = Arrays.copyOf(safe, safe.length + services.length);
        for (int i = 0; i < services.length; i++)
        {
            TeletextSubtitleEngine.Service service = services[i];
            combined[safe.length + i] = new SubtitleTrack(service.trackId,
                    SubtitleCodec.TELETEXT, service.language, service.label(), true,
                    -1, service.pid);
        }
        return combined;
    }

    /**
     * Intercept decoder-owned track ids. A false result means the backend must
     * continue with its native subtitle selection.
     */
    protected boolean handleTeletextSubtitleSelection(int streamPos)
    {
        if (streamPos == DISABLE_TRACK)
        {
            selectedTeletextTrack = DISABLE_TRACK;
            TeletextSubtitleEngine.disable();
            clearTeletextOverlay();
            return false;
        }
        if (!TeletextSubtitleEngine.isTeletextTrack(streamPos))
        {
            selectedTeletextTrack = DISABLE_TRACK;
            TeletextSubtitleEngine.disable();
            clearTeletextOverlay();
            return false;
        }
        if (!TeletextSubtitleEngine.selectTrack(streamPos)) return true;
        selectedTeletextTrack = streamPos;
        ensureTeletextOverlay();
        return true;
    }

    protected int selectedTeletextTrackOr(int nativeTrack)
    {
        return selectedTeletextTrack != DISABLE_TRACK ? selectedTeletextTrack : nativeTrack;
    }

    protected int teletextTrackCount()
    {
        return teletextServices.length;
    }

    @Override
    public boolean mapSubtitleTrackToClosedCaptionChannel(int channel, int trackId)
    {
        if ((channel != 1 && channel != 2)
                || !TeletextSubtitleEngine.isTeletextTrack(trackId)) return false;
        TeletextSubtitleEngine.Service target = null;
        for (TeletextSubtitleEngine.Service service : teletextServices)
            if (service.trackId == trackId) { target = service; break; }
        if (target == null) return false;
        context.getClient().properties().setString(channel == 1
                        ? PrefStore.Keys.teletext_cc1_mapping
                        : PrefStore.Keys.teletext_cc2_mapping,
                teletextServiceSpec(target));
        applyTeletextCcMappings();
        return true;
    }

    @Override
    public int getMappedSubtitleTrackForClosedCaptionChannel(int channel)
    {
        if (channel != 1 && channel != 2) return DISABLE_TRACK;
        String spec = context.getClient().properties().getString(channel == 1
                ? PrefStore.Keys.teletext_cc1_mapping
                : PrefStore.Keys.teletext_cc2_mapping, "");
        int fallback = channel - 1;
        TeletextSubtitleEngine.Service[] services = teletextServices;
        if (!spec.isEmpty())
            for (TeletextSubtitleEngine.Service service : services)
                if (spec.equals(teletextServiceSpec(service))
                        || spec.equals(legacyTeletextServiceSpec(service)))
                    return service.trackId;
        return fallback < services.length ? services[fallback].trackId : DISABLE_TRACK;
    }

    @Override
    public boolean applyClosedCaptionSlot(int channel, String type, String language)
    {
        if (channel != 1 && channel != 2) return false;
        // A mode change can happen after the bridge has already painted a
        // caption through SageTV. Establish the new single-renderer owner and
        // flush that old screen before selecting the requested local track.
        applyTeletextCcMappings();
        SubtitleTrack track = resolveCaptionSlotTrack(channel, type, language);
        if (track == null) return false;
        if (track.getSubtitleCodec() == SubtitleCodec.TELETEXT)
        {
            if (!mapSubtitleTrackToClosedCaptionChannel(channel, track.getIndex())) return false;
            MiniClientConnection connection = context.getClient().getCurrentConnection();
            if (!isExplicitLocalCaptionAuthority(connection)
                    && connection != null && connection.isSubtitleCallbackEnabled()) return true;
        }
        setSubtitleTrack(track.getIndex());
        return true;
    }

    /**
     * Auto is evidence-based: extractor-advertised CEA compatibility tracks
     * are not selected until the decoder has observed actual CEA samples.
     * This keeps UK DVB/Teletext streams on their real broadcast service while
     * preserving explicit CEA selections and real ATSC CEA data.
     */
    private SubtitleTrack resolveCaptionSlotTrack(int channel, String type, String language)
    {
        SubtitleTrack[] tracks = getSubtitleTracks();
        String slotType = CaptionSlotPolicy.TYPE_DVB.equals(
                CaptionSlotPolicy.normalizeType(type))
                ? CaptionSlotPolicy.TYPE_AUTO : type;
        // CC1/CC2 are text-caption callback slots. DVB bitmap selection is a
        // separate top-level mode, matching the original extender's separate
        // STV Subtitles command rather than pretending DVB is a CC channel.
        SubtitleTrack track = CaptionSlotPolicy.findTrack(
                tracks, channel, slotType, language, true, false);
        if (track != null && CaptionSlotPolicy.TYPE_AUTO.equals(
                CaptionSlotPolicy.normalizeType(slotType))
                && !hasObservedCeaCaptionData()
                && (track.getSubtitleCodec() == SubtitleCodec.CEA608
                || track.getSubtitleCodec() == SubtitleCodec.CEA708))
        {
            SubtitleTrack broadcastFallback = CaptionSlotPolicy.findTrack(
                    tracks, channel, slotType, language, false, false);
            if (broadcastFallback != null)
                track = broadcastFallback;
        }
        return track;
    }

    @Override
    public boolean applyDvbCaptionTrack()
    {
        applyTeletextCcMappings();
        SubtitleTrack track = CaptionSlotPolicy.findTrack(getSubtitleTracks(), 1,
                CaptionSlotPolicy.TYPE_DVB, "");
        if (track == null)
        {
            setSubtitleTrack(DISABLE_TRACK);
            return false;
        }
        setSubtitleTrack(track.getIndex());
        return true;
    }

    /** Apply a persisted virtual CC slot after asynchronous track discovery. */
    protected boolean applyConfiguredClosedCaptionSlot()
    {
        MiniClientConnection connection = context.getClient().getCurrentConnection();
        if (connection == null || connection.getMediaCmd() == null) return false;
        PrefStore prefs = context.getClient().properties();
        String mode = prefs.getString(PrefStore.Keys.legacy_server_caption_mode, "stv");
        if ("dvb".equals(mode))
        {
            // Keep generic preferred-track resolution from accidentally
            // selecting Teletext while DVB tracks are still being discovered.
            applyDvbCaptionTrack();
            return true;
        }
        int channel = "cc1".equals(mode) ? 1 : "cc2".equals(mode) ? 2 : 0;
        if (connection.getMediaCmd().hasSageTvClosedCaptionState())
        {
            // STV owns the enabled/disabled state. Resolve every enabled
            // virtual slot (including Auto) only against broadcast caption
            // services. Do not fall through to the generic subtitle resolver;
            // SRT/PGS/DVD subtitle selection is a separate path.
            int serverState = connection.getMediaCmd().getSageTvClosedCaptionState();
            if (serverState == 1 || serverState == 2)
            {
                int serverChannel = serverState;
                String serverType = prefs.getString(serverChannel == 1
                        ? PrefStore.Keys.caption_cc1_type : PrefStore.Keys.caption_cc2_type,
                        CaptionSlotPolicy.TYPE_AUTO);
                channel = serverChannel;
                String serverLanguage = captionSlotLanguage(prefs, serverChannel);
                boolean applied = applyClosedCaptionSlot(channel, serverType, serverLanguage);
                if (!applied) setSubtitleTrack(DISABLE_TRACK);
                // Track publication is asynchronous. onTracksChanged() will
                // retry the same broadcast slot; returning true prevents a
                // temporary empty inventory from selecting an SRT/PGS track.
                return true;
            }
            return false;
        }
        if (channel == 0) return false;
        return applyClosedCaptionSlot(channel, prefs.getString(channel == 1
                        ? PrefStore.Keys.caption_cc1_type : PrefStore.Keys.caption_cc2_type,
                        CaptionSlotPolicy.TYPE_AUTO), captionSlotLanguage(prefs, channel));
    }

    private static String captionSlotLanguage(PrefStore prefs, int channel)
    {
        String language = prefs.getString(channel == 1 ? PrefStore.Keys.caption_cc1_language
                : PrefStore.Keys.caption_cc2_language, "");
        return language;
    }

    @Override
    public void setPreferredSubtitleTrack()
    {
        if (applyPendingServerSubpictureStream()) return;
        if (!applyConfiguredClosedCaptionSlot()) setSubtitleTrack(0);
    }

    /**
     * Applies the stock HD-extender MPEG-TS subpicture command. SageTV sends
     * command 36/type 1 with a 13-bit PID and bit 0x2000 as the disable flag.
     * The original HD300 selected its local DVB decoder with this command;
     * DVB pixels did not travel through the event-225 CC callback.
     */
    protected boolean applyPendingServerSubpictureStream()
    {
        MiniClientConnection connection = context.getClient().getCurrentConnection();
        if (connection != null && connection.getMediaCmd() != null)
        {
            String captionMode = connection.getMediaCmd().getLegacyServerCaptionMode();
            if ("cc1".equals(captionMode) || "cc2".equals(captionMode)
                    || "dvb".equals(captionMode))
            {
                // CC1/CC2/DVB are explicit Android-local modes on a stock
                // server. Do not let SageTV's independent Subtitles-off
                // command (normally 0x2000) erase that local virtual-slot
                // choice when tracks are published asynchronously. STV mode
                // still follows the original HD200/HD300 subpicture command.
                return false;
            }
        }
        int command = pendingServerSubpictureCommand;
        if (command == NO_SERVER_SUBPICTURE_COMMAND) return false;
        SubtitleTrack[] tracks = getSubtitleTracks();
        int trackId = LegacyExtenderSubpictureCommand.resolveTrackId(command, tracks);
        if (trackId != LegacyExtenderSubpictureCommand.NO_MATCH)
        {
            setSubtitleTrack(trackId);
            return true;
        }
        if (tracks == null || tracks.length == 0) return true;
        log.warn("No local subtitle track matches server subpicture PID/index {}",
                LegacyExtenderSubpictureCommand.sourcePid(command));
        setSubtitleTrack(DISABLE_TRACK);
        return true;
    }

    private void applyTeletextCcMappings()
    {
        /*
         * CC1 and CC2 are virtual caption slots, not Teletext slot numbers.
         * Resolve each slot against the complete stream inventory before
         * assigning a Teletext bridge channel. Otherwise a stream containing
         * one DVB service and one Teletext service implicitly maps that sole
         * Teletext service to CC1 as well as the requested CC2 slot. The
         * bridge then removes CC2 as a duplicate and SageTV never receives
         * field-one/channel-two records.
         */
        MiniClientConnection connection = context.getClient().getCurrentConnection();
        String mode = connection != null && connection.getMediaCmd() != null
                ? connection.getMediaCmd().getLegacyServerCaptionMode() : "stv";
        if ("off".equals(mode) || isExplicitLocalCaptionAuthority(connection))
        {
            if (teletextLegacyBridgeEnabled)
            {
                teletextLegacyBridgeEnabled = false;
                teletextLegacyBridge.clearPending();
                // Reset any caption already painted by SageTV before local
                // Teletext/DVB rendering takes ownership. Without this flush,
                // both the old STV caption and the local bitmap/text overlay
                // remain visible until SageTV happens to clear its screen.
                postTeletextFlush();
            }
            teletextLegacyBridge.setTrackMappings(DISABLE_TRACK, DISABLE_TRACK);
            return;
        }
        teletextLegacyBridgeEnabled = true;
        teletextLegacyBridge.setTrackMappings(
                resolvedTeletextTrackForSlot(1), resolvedTeletextTrackForSlot(2));
    }

    /**
     * Stock SageTV cannot publish the STV's current CC1/CC2 state. Therefore
     * an explicit Android CC1, CC2, or DVB choice must render locally and must
     * not simultaneously feed the legacy event-225 renderer. A compatible
     * server that publishes VIDEO_CC_STATE remains authoritative in STV mode.
     */
    private boolean isExplicitLocalCaptionAuthority(MiniClientConnection connection)
    {
        if (connection == null || connection.getMediaCmd() == null) return false;
        if (connection.getMediaCmd().hasSageTvClosedCaptionState()) return false;
        String mode = connection.getMediaCmd().getLegacyServerCaptionMode();
        return "cc1".equals(mode) || "cc2".equals(mode) || "dvb".equals(mode);
    }

    private int resolvedTeletextTrackForSlot(int channel)
    {
        PrefStore prefs = context.getClient().properties();
        SubtitleTrack track = resolveCaptionSlotTrack(channel,
                prefs.getString(channel == 1 ? PrefStore.Keys.caption_cc1_type
                                : PrefStore.Keys.caption_cc2_type,
                        CaptionSlotPolicy.TYPE_AUTO),
                prefs.getString(channel == 1 ? PrefStore.Keys.caption_cc1_language
                        : PrefStore.Keys.caption_cc2_language, ""));
        // Only Teletext can be emitted through the legacy CEA callback. Never
        // relabel an Auto/DVB bitmap selection as Teletext; explicit Android
        // DVB mode handles bitmap rendering locally.
        return track != null && track.getSubtitleCodec() == SubtitleCodec.TELETEXT
                ? track.getIndex() : DISABLE_TRACK;
    }

    private static String teletextServiceSpec(TeletextSubtitleEngine.Service service)
    {
        return service.pid + ":" + service.language.toLowerCase() + ":"
                + service.type + ":" + service.page;
    }

    private static String legacyTeletextServiceSpec(TeletextSubtitleEngine.Service service)
    {
        return service.language.toLowerCase() + ":" + service.type + ":" + service.page;
    }

    /** Select locally only when the negotiated stock callback cannot render CC1. */
    protected boolean selectPreferredTeletextWhenCallbackUnavailable()
    {
        MiniClientConnection connection = context.getClient().getCurrentConnection();
        if (connection != null && connection.isSubtitleCallbackEnabled()) return false;
        TeletextSubtitleEngine.Service[] services = teletextServices;
        return services.length > 0 && handleTeletextSubtitleSelection(services[0].trackId);
    }

    private void ensureTeletextOverlay()
    {
        context.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if (teletextOverlay == null)
                    teletextOverlay = new TeletextSubtitleOverlay(context.getContext());
                TeletextSubtitleOverlay.attach(context, teletextOverlay);
            }
        });
    }

    private void clearTeletextOverlay()
    {
        currentTeletextCueText = "";
        final TeletextSubtitleOverlay overlay = teletextOverlay;
        if (overlay == null) return;
        context.runOnUiThread(new Runnable()
        {
            @Override public void run() { overlay.setText(""); }
        });
    }

    /** Remove client-rendered subtitle state before SageTV exposes its menus. */
    protected final void endTeletextPresentation()
    {
        teletextOverlayGeneration++;
        teletextClockScheduled = false;
        selectedTeletextTrack = DISABLE_TRACK;
        currentTeletextCueText = "";
        teletextLegacyBridge.clearPending();
        TeletextSubtitleEngine.deactivate(teletextListener);
        final TeletextSubtitleOverlay overlay = teletextOverlay;
        teletextOverlay = null;
        if (overlay != null)
            TeletextSubtitleOverlay.detach(context, overlay);
    }

    private void scheduleTeletextCue(final TeletextSubtitleEngine.Cue cue)
    {
        final long overlayGeneration = teletextOverlayGeneration;
        context.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if (overlayGeneration != teletextOverlayGeneration
                        || cue.trackId != selectedTeletextTrack
                        || player == null || eos || state == EOS_STATE
                        || state == STOPPED_STATE) return;
                long targetMs = cue.presentationTimeMs + subtitleOffsetMs;
                long clockMs = Math.max(0L, getPlayerMediaTimeMillis(0L));
                long remaining = targetMs - clockMs;
                if (remaining > 40L)
                {
                    context.getVideoView().postDelayed(this, Math.max(10L,
                            Math.min(250L, remaining)));
                    return;
                }
                ensureTeletextOverlay();
                if (teletextOverlay != null)
                {
                    currentTeletextCueText = cue.text;
                    teletextCueUpdateCount++;
                    teletextOverlay.setText(cue.text);
                }
            }
        });
    }

    public final long getTeletextCueUpdateCountForDebug()
    {
        return teletextCueUpdateCount;
    }

    public final String getCurrentTeletextCueTextForDebug()
    {
        return currentTeletextCueText;
    }

    public final boolean isTeletextOverlayVisibleForDebug()
    {
        return teletextOverlay != null && !currentTeletextCueText.isEmpty();
    }

    public final long getTeletextClockDrainCountForDebug()
    {
        return teletextClockDrainCount;
    }

    public final long getTeletextClockLastMediaTimeMsForDebug()
    {
        return teletextClockLastMediaTimeMs;
    }

    /**
     * Drive queued Teletext independently of SageTV's GETMEDIATIME cadence.
     *
     * <p>Stock STVs stop polling media time once their OSD is hidden. Using
     * that request as the only caption clock therefore freezes Teletext until
     * another UI action wakes the OSD. The decoder's own player clock remains
     * authoritative and advances without an on-screen timeline.</p>
     */
    private synchronized void scheduleTeletextClock()
    {
        if (teletextClockScheduled || state != PLAY_STATE
                || teletextServices.length == 0 || context.getVideoView() == null)
            return;
        final long generation = teletextOverlayGeneration;
        teletextClockScheduled = true;
        context.getVideoView().postDelayed(new Runnable()
        {
            @Override public void run()
            {
                teletextClockScheduled = false;
                if (generation != teletextOverlayGeneration || state != PLAY_STATE
                        || player == null || eos || teletextServices.length == 0)
                    return;
                long mediaTimeMs = getPlayerMediaTimeMillis(teletextClockServerBaseMs);
                if (mediaTimeMs >= 0L)
                {
                    teletextLegacyBridge.drainTo(mediaTimeMs);
                    teletextClockDrainCount++;
                    teletextClockLastMediaTimeMs = mediaTimeMs;
                }
                scheduleTeletextClock();
            }
        }, TELETEXT_CLOCK_INTERVAL_MS);
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
        if (lastServerTime >= 0L)
            teletextClockServerBaseMs = lastServerTime;
        // OPENURL owns a new logical playback session before its UI-thread
        // player replacement completes. Never expose the old player's EOS
        // sentinel during that interval.
        if (loadTransitionToken != null)
            return 0;

        if (player != null)
            TeletextSubtitleEngine.activate(teletextListener);

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
        teletextLegacyBridge.drainTo(mt);
        teletextClockLastMediaTimeMs = mt;
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
        DiagnosticSessionSpool.checkpoint("playback-stop");
        loadTransitionToken = null;
        // SageTV STOP is not necessarily terminal. Stock servers legitimately
        // retain the loaded MiniPlayer and later issue SEEK/PLAY without a new
        // OPENURL (for example Stop followed by Restart). Keep the generation
        // active so that the backend's queued PLAY is not rejected as stale.
        // MEDIACMD_DEINIT -> free() remains the true session boundary.
        beginPlaybackOperation(PlaybackSessionController.Operation.STOP);
        state = STOPPED_STATE;
        endTeletextPresentation();
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
        scheduleTeletextClock();
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
        TeletextSubtitleEngine.setPlaybackAnchor(timeInMS);
        teletextLegacyBridge.clearPending();
        postTeletextFlush();
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
        TeletextSubtitleEngine.discontinuity("player-flush");
        teletextLegacyBridge.clearPending();
        postTeletextFlush();
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
        endTeletextPresentation();
        if (context.getContext() instanceof Activity)
            DisplayRefreshController.apply((Activity) context.getContext(), this,
                    DisplayRefreshController.OFF);
        DvdHighlightOverlay.detach(context, dvdHighlightOverlay);
        dvdHighlightOverlay = null;
        dvdSubpictureDecoder.reset();
        player = null;
        playerReady = false;
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

    private void postTeletextFlush()
    {
        try
        {
            context.getClient().getBackgroundService().execute(new Runnable()
            {
                @Override public void run() { teletextLegacyBridge.postFlush(); }
            });
        }
        catch (RuntimeException ignored)
        {
            // Connection shutdown owns the event channel; there is nothing to flush.
        }
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
            boolean dvdTransport = lastUri != null && lastUri.startsWith("push:dvd");
            if (!dvdTransport)
            {
                pendingServerSubpictureCommand = streamPosition;
                applyPendingServerSubpictureStream();
            }
            else if (dvdMimTransport)
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
        if (fullscreenPromotionSent || fullscreenPromotionCheckScheduled
                || fullscreenPromotionCheckCount >= FULLSCREEN_PROMOTION_MAX_CHECKS
                || state != PLAY_STATE || context.getVideoView() == null)
        {
            return;
        }

        final PlaybackSessionController.Token session = playbackSessions.currentSessionToken();
        fullscreenPromotionCheckScheduled = true;
        context.getVideoView().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                fullscreenPromotionCheckScheduled = false;
                fullscreenPromotionCheckCount++;
                if (!playbackSessions.isCurrentSession(session) || fullscreenPromotionSent
                        || state != PLAY_STATE)
                {
                    fullscreenPromotionLastDecision = !playbackSessions.isCurrentSession(session)
                            ? "stale_generation" : fullscreenPromotionSent
                            ? "already_sent_or_fullscreen" : "not_playing";
                    return;
                }
                if (!playerReady || player == null)
                {
                    // PLAY and the first SETVIDEORECT can both arrive before
                    // a slower device has created its decoder. Re-check the
                    // same playback generation instead of permanently losing
                    // the one guarded preview promotion opportunity.
                    fullscreenPromotionLastDecision = "waiting_for_player_ready";
                    scheduleFullscreenPromotionCheck();
                    return;
                }

                Dimension screen = context.getClient().getUIRenderer().getMaxScreenSize();
                // SageTV's SETVIDEORECT destination is authoritative. On
                // Shield firmware the SurfaceView itself can remain 1920x1080
                // while the server asks video to render in a 408x322 preview;
                // using view bounds therefore misclassifies that preview as
                // fullscreen. Fall back only before a destination arrives.
                int width = videoInfo != null && videoInfo.destRect.width > 0
                        ? Math.round(videoInfo.destRect.width)
                        : context.getVideoView().getWidth();
                int height = videoInfo != null && videoInfo.destRect.height > 0
                        ? Math.round(videoInfo.destRect.height)
                        : context.getVideoView().getHeight();
                MenuHint menuHint = null;
                if (context.getClient().getCurrentConnection() != null)
                {
                    menuHint = context.getClient().getCurrentConnection().getMenuHint();
                }
                String menuName = menuHint == null ? null : menuHint.menuName;
                String popupName = menuHint == null ? null : menuHint.popupName;
                boolean embeddedPreview = FullscreenPlaybackPolicy.isEmbeddedPreview(
                        width, height, screen.width, screen.height);
                if (FullscreenPlaybackPolicy.isPlaybackMenu(menuName) && !embeddedPreview)
                {
                    fullscreenPromotionStableFullscreenCount++;
                    if (fullscreenPromotionStableFullscreenCount
                            < FULLSCREEN_PROMOTION_STABLE_CHECKS)
                    {
                        fullscreenPromotionLastDecision = "waiting_for_stable_fullscreen";
                        scheduleFullscreenPromotionCheck();
                        return;
                    }
                    fullscreenPromotionSent = true;
                    fullscreenPromotionLastDecision = "fullscreen_observed";
                    log.debug("Skipping preview promotion because SageTV already entered {}", menuName);
                    return;
                }
                if (FullscreenPlaybackPolicy.isPlaybackMenu(menuName))
                {
                    // During file replacement the old MediaPlayer OSD can be
                    // visible briefly while the new destination is already a
                    // preview. Do not let that transient menu suppress the
                    // new playback generation's only promotion opportunity.
                    fullscreenPromotionLastDecision = "transient_osd_preview";
                    scheduleFullscreenPromotionCheck();
                    return;
                }

                if (FullscreenPlaybackPolicy.shouldPromote(menuName, popupName,
                        width, height, screen.width, screen.height))
                {
                    fullscreenPromotionStableFullscreenCount = 0;
                    fullscreenPromotionStablePreviewCount++;
                    if (fullscreenPromotionStablePreviewCount
                            < FULLSCREEN_PROMOTION_STABLE_CHECKS)
                    {
                        fullscreenPromotionLastDecision = "waiting_for_stable_preview";
                        scheduleFullscreenPromotionCheck();
                        return;
                    }
                    fullscreenPromotionSent = true;
                    fullscreenPromotionCommandCount++;
                    fullscreenPromotionLastDecision = "promotion_command_sent";
                    log.info("Promoting stable SageTV embedded preview ({}x{} on {}x{}, menu={}) to fullscreen",
                            width, height, screen.width, screen.height, menuName);
                    EventRouter.postCommand(context.getClient(), SageCommand.TV);
                }
                else
                {
                    fullscreenPromotionLastDecision = "not_embedded_or_popup_present";
                }
            }
        }, FULLSCREEN_PROMOTION_DELAY_MS);
    }

    public final boolean isFullscreenPromotionSentForDebug()
    {
        return fullscreenPromotionSent;
    }

    public final boolean isFullscreenPromotionCheckScheduledForDebug()
    {
        return fullscreenPromotionCheckScheduled;
    }

    public final int getFullscreenPromotionCheckCountForDebug()
    {
        return fullscreenPromotionCheckCount;
    }

    public final int getFullscreenPromotionCommandCountForDebug()
    {
        return fullscreenPromotionCommandCount;
    }

    public final int getFullscreenPromotionStablePreviewCountForDebug()
    {
        return fullscreenPromotionStablePreviewCount;
    }

    public final int getFullscreenPromotionStableFullscreenCountForDebug()
    {
        return fullscreenPromotionStableFullscreenCount;
    }

    public final String getFullscreenPromotionLastDecisionForDebug()
    {
        return fullscreenPromotionLastDecision;
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
