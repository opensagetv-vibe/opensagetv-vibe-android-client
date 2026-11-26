package opensagetv.vibe.miniclient.android.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import opensagetv.vibe.miniclient.video.DeinterlacePolicy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opensagetv.vibe.miniclient.MimDirectTransportPolicy;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Client for the Standard FFmpeg plugin's optional owned media sessions. */
public final class MimDirectSessionClient
{
    private static final Logger log = LoggerFactory.getLogger(MimDirectSessionClient.class);
    private static final String IJK_PLAYER_CLASS =
            "opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl";
    private static final int DEFAULT_PORT = 31910;
    private static final int MAX_REPLY_BYTES = 256 * 1024;
    private static final long REDUNDANT_STARTUP_SEEK_NS = 5_000_000_000L;
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\\\"%s\\\"\\s*:\\s*\\\"([^\\\"\\r\\n]*)\\\"");
    private static final Pattern LONG_FIELD = Pattern.compile(
            "\\\"%s\\\"\\s*:\\s*(-?[0-9]+)");

    private final Object lock = new Object();
    private boolean hlsSelected;
    private boolean responsiveHlsNegotiated;
    private boolean responsiveHlsActive;
    private boolean completedResponsiveHlsActive;
    /**
     * SageTV can issue closely spaced seek notifications from different
     * control paths. A Direct replacement retires its input token, so only
     * one replacement request may use that token at a time. Lifecycle state
     * remains protected independently by {@link #lock}; a concurrent stop is
     * still allowed to invalidate and clean up a late replacement.
     */
    private final Object restartRequestLock = new Object();
    private final ExecutorService teardownExecutor =
            Executors.newSingleThreadExecutor(new ThreadFactory()
            {
                @Override public Thread newThread(Runnable task)
                {
                    Thread thread = new Thread(task, "vibe-mim-direct-teardown");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private String baseUrl = "";
    private String activeMode = MimDirectTransportPolicy.OFF;
    private String sessionToken = "";
    private String activeMediaUrl="",activeCaptionToken="";
    private int acceptedVideoKbps=6000;
    private int qualityCeilingKbps=6000,requestedVideoKbps=6000;
    private int acceptedBufferAheadMs;
    private boolean qualityRecoverySupported;
    private long qualityInitialPositionMs;
    private long qualityClockHoldMs=-1;
    private boolean qualitySupported;
    private boolean previewSupported;
    // Explicit image requests cannot queue behind playback control or block
    // the UI thread. There is only one finite preview job, never a timer.
    private final java.util.concurrent.ThreadPoolExecutor previewWorker=new java.util.concurrent.ThreadPoolExecutor(
            0,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>(),
            task->{Thread t=new Thread(task,"vibe-source-preview");t.setDaemon(true);return t;});
    private final MimScrubPreviewClient previews=new MimScrubPreviewClient(previewWorker,new MimScrubPreviewHttp());
    private final ExecutorService qualityWorker=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"vibe-direct-quality");t.setDaemon(true);return t;});
    private final java.util.concurrent.ScheduledExecutorService qualityTimer=Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"vibe-direct-quality-deadline");t.setDaemon(true);return t;});
    private final MimDirectQualityClient quality=new MimDirectQualityClient(qualityWorker,teardownExecutor,
        new MimDirectQualityClient.Http(){
            public Response post(String base,String path)throws Exception{return request("POST",base,path,1500,path.contains("/prepare?")?20000:3000);}
            public boolean ready(MimDirectQualityClient.Source candidate,long join)throws Exception{
                Response playlist=request("GET",candidate.base,candidate.media.substring(candidate.base.length()),750,1000);
                return playlist.status==200&&MimDirectQualityClient.playlistCovers(playlist.body,Math.max(0,join-candidate.offset));
            }
        },
        (ms,expired)->qualityTimer.schedule(expired,ms,java.util.concurrent.TimeUnit.MILLISECONDS),
        ()->System.nanoTime()/1000000L);
    private boolean bufferAdaptiveNegotiated,bufferReportBusy;
    private boolean bufferAdaptiveAvailable,runtimeAvailable;
    private int adaptiveMinimumKbps=250;
    private long bufferReportAt=-1,bufferReportId;
    private String bufferObservation="inactive";
    private boolean bufferNeedsReset;
    private boolean runtimeNegotiated,runtimeActive,runtimeBusy;
    private long runtimeRequestId;
    private String runtimeState="off";
    /** Accepted producer request, not a newly changed preference or a per-frame filter claim. */
    private String acceptedDeinterlace = "";
    private Object owner;
    private long startOffsetMs;
    private boolean lastRestartClamped;
    private long openedAtNanos;
    private long lifecycleGeneration;
    /** One connection-scoped escape hatch; never changes the saved preference. */
    private boolean suppressNextPrepareForStockFixed;
    private boolean lateFallbackReconnectClaimed;
    /** Debug-receiver-only, one-shot proof of the two-stage startup fallback. */
    private boolean debugForceNextDirectStartFailure;
    private boolean debugForceFallbackPullFailure;
    private boolean pluginWatchRecoveryRequired;
    private final MimDirectWatchRecoveryClient watchRecovery=new MimDirectWatchRecoveryClient(
            teardownExecutor,(base,path) -> request("POST",base,path,1500,3000),System::nanoTime);
    private volatile String state = "off";
    private MimOutputSettings outputSettings;
    private boolean outputNegotiated;
    private boolean encoderPoliciesNegotiated;
    private boolean hardwareBackendsNegotiated;
    private boolean hlsBufferNegotiated;
    private volatile String configurationError="",outputFailure="";
    private String outputToken="",outputStatus="";
    private boolean outputReadBusy;
    public String serverOutputForDiagnostics(){synchronized(lock){
        if("copy_original_pull_no_remux".equals(state))return "Server video encoding: None (original-source Copy)";
        return owner!=null&&!sessionToken.isEmpty()&&sessionToken.equals(outputToken)?MimOutputStatus.summary(outputStatus):
            "Server video encoding: Unknown\nServer decoding: Unknown\nEffective output: Unknown (not reported by this playback path)";
    }}
    /** Explicit diagnostic refresh or source-commit callback, never a polling timer. */
    public void refreshServerOutput(java.util.function.Consumer<String> callback){
        final String base,token;final long generation;
        synchronized(lock){
            if(owner==null||sessionToken.isEmpty()||outputReadBusy){if(callback!=null)callback.accept(serverOutputForDiagnostics());return;}
            base=baseUrl;token=sessionToken;generation=lifecycleGeneration;outputReadBusy=true;
        }
        qualityWorker.execute(()->{
            String body="";try{Response response=request("GET",base,"/v1/direct/status?token="+token,750,1500);if(response.status==200)body=response.body;}catch(Exception unavailable){}
            synchronized(lock){if(generation==lifecycleGeneration){outputReadBusy=false;if(sessionToken.equals(token)&&owner!=null){outputToken=token;outputStatus=body;}}}
            if(callback!=null)callback.accept(serverOutputForDiagnostics());
        });
    }
    public String configurationError(){return configurationError;}
    public String blockingOutputFailure(){return outputFailure;}

    /** Probe before MiniClient capability negotiation. Returns the active mode. */
    public String prepare(ServerInfo server, PrefStore preferences)
    {
        return prepare(server, preferences, "");
    }

    public String prepare(ServerInfo server, PrefStore preferences, String nativeVideoCodecs)
    {
        final boolean suppressForStockFixed;
        synchronized (lock)
        {
            suppressForStockFixed = suppressNextPrepareForStockFixed;
            suppressNextPrepareForStockFixed = false;
            lateFallbackReconnectClaimed = false;
        }
        if (suppressForStockFixed && watchRecovery.pending()) stopTransportOnly();
        else stop();
        configurationError="";outputFailure="";
        if (suppressForStockFixed)
        {
            state = "late_failure_stock_fixed_reconnect";
            return MimDirectTransportPolicy.OFF;
        }
        if (server == null || preferences == null || server.address == null)
            return MimDirectTransportPolicy.OFF;
        final String selection = preferences.getString(PrefStore.Keys.mim_direct_mode, "off");
        final boolean requestedHls = selection != null
                && MimDirectTransportPolicy.HLS.equalsIgnoreCase(selection.trim());
        String requested = MimDirectTransportPolicy.activeMode(
                preferences.getStreamingMode(), selection, true);
        if (!MimDirectTransportPolicy.isActive(requested))
        {
            if("fixed".equalsIgnoreCase(preferences.getStreamingMode())&&"off".equals(selection)
                &&("source".equals(preferences.getFixedEncodingAudioCodec())||"source".equals(preferences.getString("fixed_encoding/audio_bitrate_kbps","128"))))
                configurationError="Use Source audio encoding requires the optional MIM output API; select an explicit audio codec/bitrate for Stock SageTV.";
            return MimDirectTransportPolicy.OFF;
        }
        outputSettings=new MimOutputSettings(preferences,requested);
        configurationError=outputSettings.validationError(MimDirectTransportPolicy.COPY.equals(requested));
        if(!configurationError.isEmpty())return MimDirectTransportPolicy.OFF;
        if(MimDirectTransportPolicy.COPY.equals(requested)&&"off".equals(outputSettings.remux)){
            synchronized(lock){activeMode=requested;}
            state="copy_original_pull_no_remux";
            return requested; // Original source: no plugin, muxer or encode required.
        }
        // The long-press player control and the MCP physical-test harness can
        // select a backend for only the active playback session. Resolve that
        // override exactly as the renderers do; consulting only the persisted
        // preference would incorrectly negotiate Direct Transcode for IJK
        // while the active player is already IJK.
        PlayerBackend backend = PlayerBackend.fromPreference(
                ActivePlayerSessionOverrides.resolveBackend(preferences.getString(
                        PrefStore.Keys.default_player,
                        PlayerBackend.DEFAULT_PREFERENCE)));
        if (!supportsMode(backend, requested))
        {
            // IJK 0.8.8 Direct Copy is physically proven, but its MediaCodec
            // path enters a persistent illegal state on the plugin's encoded
            // Direct Transcode output. Keep the explicit request fail-safe by
            // retaining ordinary SageTV Fixed rather than opening a stream
            // that cannot produce advancing A/V.
            state = "unsupported_player_stock_fixed";
            if(outputSettings.strict())configurationError="This player cannot honor the selected transcoding output. Choose a supported player or supported defaults.";
            return MimDirectTransportPolicy.OFF;
        }
        final boolean needsWatchRecovery=MimDirectTransportPolicy.TRANSCODE.equals(requested)
                && !MimDirectTransportPolicy.supportsTranscodePullFallback(nativeVideoCodecs);
        String host = server.address.trim();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
        int port = boundedPort(preferences.getString(
                PrefStore.Keys.fixed_caption_side_channel_port,
                String.valueOf(DEFAULT_PORT)));
        String candidateBase = "http://" + host + ":" + port;
        try
        {
            Response capabilities = request("GET", candidateBase,
                    "/v1/capabilities", 750, 900);
            outputNegotiated=supportsOutputRevision(capabilities.body,"outputSettingsRevision");
            encoderPoliciesNegotiated=supportsOutputRevision(capabilities.body,"encoderPolicyRevision");
            hardwareBackendsNegotiated=supportsOutputRevision(capabilities.body,"hardwareBackendRevision");
            hlsBufferNegotiated=supportsOutputRevision(capabilities.body,"hlsBufferRevision");
            if(!"auto".equals(outputSettings.hardwareBackend)&&!hardwareBackendsNegotiated){
                configurationError="The selected Hardware Encoder requires an updated FFmpeg/MIM plugin and runtime. Choose Auto for the existing server path.";
                return MimDirectTransportPolicy.OFF;
            }
            if((!outputNegotiated&&outputSettings.strict())||(!"server".equals(outputSettings.encoder)&&!supportsOutputRevision(capabilities.body,"encoderPolicyRevision"))){
                configurationError="The server plugin/runtime does not support the selected output or Video Encoder policy. Update it or choose supported defaults.";
                return MimDirectTransportPolicy.OFF;
            }
            if (capabilities.status != 200 || !supportsDirect(capabilities.body, requested)
                    || (requestedHls && !supportsHls(capabilities.body)))
            {
                state = "unavailable_stock_fixed";
                if(outputSettings.strict())configurationError="The selected output requires an available compatible FFmpeg/MIM service.";
                return MimDirectTransportPolicy.OFF;
            }
            if (needsWatchRecovery && (!MimDirectTransportPolicy.supportsH264Output(nativeVideoCodecs)
                    || !MimDirectWatchRecoveryClient.supported(capabilities.body)))
            {
                state="unsupported_video_stock_fixed";
                if(outputSettings.strict())configurationError="This player cannot honor the selected output codec.";
                return MimDirectTransportPolicy.OFF;
            }
            watchRecovery.configure(candidateBase,needsWatchRecovery);
            synchronized (lock)
            {
                baseUrl = candidateBase;
                activeMode = requested;
                hlsSelected = requestedHls;
                responsiveHlsNegotiated = requestedHls && supportsResponsiveHls(capabilities.body);
                pluginWatchRecoveryRequired=needsWatchRecovery;
                qualitySupported=MimDirectTransportPolicy.TRANSCODE.equals(requested)&&MimDirectQualityClient.supported(capabilities.body);
                qualityRecoverySupported=qualitySupported&&MimDirectQualityClient.recoverySupported(capabilities.body);
                runtimeAvailable=qualitySupported&&supportsRuntimeBitrate(capabilities.body);
                runtimeNegotiated=runtimeAvailable
                    &&preferences.getBoolean(PrefStore.Keys.mim_direct_runtime_rate,false);
                previewSupported=MimScrubPreviewClient.supported(capabilities.body);
                requestedVideoKbps=Math.max(250,Math.min(50000,preferences.getFixedEncodingVideoBitrateKBPS()));
                bufferAdaptiveAvailable=requestedHls&&longValue(capabilities.body,"bufferAdaptationRevision",0)==1;
                bufferAdaptiveNegotiated=bufferAdaptiveAvailable&&preferences.getBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false);
                if(requestedHls&&preferences.getBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false)&&!bufferAdaptiveNegotiated){
                    state="adjusted_unavailable_stock_fixed";return MimDirectTransportPolicy.OFF;
                }
                try{adaptiveMinimumKbps=Integer.parseInt(preferences.getString(PrefStore.Keys.mim_direct_minimum_kbps,"250"));}
                catch(NumberFormatException invalid){adaptiveMinimumKbps=250;}
                adaptiveMinimumKbps=Math.max(250,Math.min(requestedVideoKbps,adaptiveMinimumKbps));
            }
            state = "ready_" + requested;
            return requested;
        }
        catch (Exception unavailable)
        {
            if(outputSettings!=null&&outputSettings.strict())configurationError="Selected transcoding output requires an available compatible FFmpeg/MIM provider.";
            state = "unavailable_stock_fixed";
            return MimDirectTransportPolicy.OFF;
        }
    }

    static boolean supportsMode(PlayerBackend backend, String mode)
    {
        return backend != PlayerBackend.IJKPLAYER ||
                !MimDirectTransportPolicy.TRANSCODE.equals(mode);
    }

    /** Create an owned stream for an original SageTV source path. */
    public String open(Object playbackOwner, String sourceUrl)
    {
        return open(playbackOwner, sourceUrl, false);
    }

    /** Create an owned stream and preserve SageTV's growing-file declaration. */
    public String open(Object playbackOwner, String sourceUrl, boolean active)
    {
        outputFailure="";
        String source = serverPath(sourceUrl);
        final String base;
        final String mode;
        final boolean responsive;
        synchronized (lock)
        {
            base = baseUrl; mode = activeMode;
            responsive = responsiveHlsNegotiated;
        }
        if (!supportsPlaybackOwnerClass(playbackOwner == null ? "" :
                playbackOwner.getClass().getName(), mode))
        {
            // prepare() may run before a one-time/debug player selection is
            // applied. The concrete media player is authoritative here, at
            // the final boundary before a server-owned stream is created.
            state = "unsupported_player_stock_fixed";
            if(outputSettings!=null&&outputSettings.strict())outputFailure="This player cannot honor the selected transcoding output.";
            return null;
        }
        if (playbackOwner == null || source.isEmpty() || base.isEmpty() ||
                !MimDirectTransportPolicy.isActive(mode))
            return null;
        // Output defaults apply to the next original OPENURL, not midstream
        // seeks/quality replacements and not only the next server connection.
        PrefStore nextPreferences=MiniclientApplication.get().getClient().properties();
        MimOutputSettings next=new MimOutputSettings(nextPreferences,mode);
        String invalid=next.validationError(MimDirectTransportPolicy.COPY.equals(mode));
        if(!invalid.isEmpty()||!outputNegotiated&&next.strict()||!"server".equals(next.encoder)&&!encoderPoliciesNegotiated||!"auto".equals(next.hardwareBackend)&&!hardwareBackendsNegotiated){
            releaseInternal(null,false);outputFailure=invalid.isEmpty()?"Selected output or Video Encoder requires a compatible FFmpeg/MIM provider.":invalid;return null;
        }
        outputSettings=next;
        boolean nextAdjusted=hlsSelected&&nextPreferences.getBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false);
        if(nextAdjusted&&!bufferAdaptiveAvailable){
            releaseInternal(null,false);outputFailure="Automatic bitrate adjustment requires a compatible FFmpeg/MIM provider.";return null;
        }
        requestedVideoKbps=Math.max(250,Math.min(50000,nextPreferences.getFixedEncodingVideoBitrateKBPS()));
        try{adaptiveMinimumKbps=Integer.parseInt(nextPreferences.getString(PrefStore.Keys.mim_direct_minimum_kbps,"250"));}
        catch(NumberFormatException invalidMinimum){adaptiveMinimumKbps=250;}
        adaptiveMinimumKbps=Math.max(250,Math.min(requestedVideoKbps,adaptiveMinimumKbps));
        bufferAdaptiveNegotiated=nextAdjusted;
        runtimeNegotiated=runtimeAvailable&&nextPreferences.getBoolean(PrefStore.Keys.mim_direct_runtime_rate,false);
        if(MimDirectTransportPolicy.COPY.equals(mode)&&"off".equals(next.remux)){
            releaseInternal(null,false);state="copy_original_pull_no_remux";return null;
        }
        // OPENURL establishes the one authoritative owned-media session. A
        // replacement player may be a different Java object, so an
        // owner-scoped release here could orphan the preceding server job.
        // This OPENURL already runs on the MiniClient media worker. Finish
        // releasing the preceding producer before reserving another server
        // slot so rapid file switches cannot exhaust the bounded session pool.
        releaseInternal(null, false);
        final long requestGeneration;
        synchronized (lock) { requestGeneration = lifecycleGeneration; }
        if (pluginWatchRecoveryRequired)
        {
            opensagetv.vibe.miniclient.MiniClientConnection connection=MiniclientApplication.get()
                    .getClient().getCurrentConnection();
            watchRecovery.source(playbackOwner,source,connection ==null ? "" : connection.getClientID());
        }
        try
        {
            synchronized (lock)
            {
                if (debugForceNextDirectStartFailure)
                {
                    debugForceNextDirectStartFailure = false;
                    throw new IOException("debug forced direct start failure");
                }
            }
            String deinterlace = currentDeinterlace();
            int requestedBufferAheadMs=0;
            String path = startRequestPath(source, mode, deinterlace, active);
            if (responsive) path += "&hlsProfile=responsive";
            if (responsive && hlsBufferNegotiated) {
                long target = playbackOwner instanceof opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl
                        ? ((opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl)playbackOwner).getConfiguredHttpBufferTargetMillis()
                        : playbackOwner instanceof opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl
                            ? ((opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl)playbackOwner).getConfiguredHttpBufferTargetMillis() : 0;
                if(target>0){requestedBufferAheadMs=(int)Math.min(20000,target);path += "&bufferAheadMs=" + requestedBufferAheadMs;}
            }
            if(runtimeNegotiated)path+="&runtimeRateControl=true";
            // New options are sent only after explicit negotiation. Older
            // providers keep their original start/default behavior unchanged.
            if(qualityRecoverySupported)path+="&videoKbps="+requestedVideoKbps;
            if(bufferAdaptiveNegotiated)path+="&adaptiveMinimumKbps="+adaptiveMinimumKbps;
            if(outputNegotiated&&outputSettings!=null)path+=outputSettings.query(MimDirectTransportPolicy.COPY.equals(mode));
            Response response = request("POST", base, path, 1500, 20000);
            if (response.status != 200) throw new IOException("direct start rejected");
            String token = token(response.body, "sessionToken");
            String media = stringValue(response.body, "mediaUrl", "");
            String captionToken = optionalToken(response.body,
                    "captionSessionToken");
            if (responsive && !acceptedResponsiveHls(response.body,requestedBufferAheadMs)) {
                teardown(base, token);
                throw new IOException("Responsive HLS policy not confirmed");
            }
            if(qualityRecoverySupported&&(longValue(response.body,"videoTargetKbps",-1)!=requestedVideoKbps
                    ||longValue(response.body,"videoCeilingKbps",-1)!=requestedVideoKbps)){
                teardown(base,token);
                throw new IOException("Direct initial quality not confirmed");
            }
            if (!media.startsWith("/v1/direct/media/"))
                throw new IOException("invalid direct media URL");
            boolean accepted;
            synchronized (lock)
            {
                accepted = lifecycleGeneration == requestGeneration &&
                        sessionToken.isEmpty();
                if (accepted)
                {
                    owner = playbackOwner;
                    sessionToken = token;
                    outputToken=token;outputStatus=response.body;
                    runtimeActive=runtimeNegotiated&&runtimePart(response.body).contains("\"available\":true");
                    runtimeBusy=false;runtimeRequestId=0;runtimeState=runtimeActive?"ready":"unavailable";
                    responsiveHlsActive = responsive;
                    // Stock Core's timeshifted hint also describes Fixed
                    // transport for completed recordings. The provider corrects
                    // that hint only after proving no writer; its positive
                    // burst is the authoritative completed-source evidence.
                    completedResponsiveHlsActive = HlsPlaybackPolicy.confirmedCompleted(
                            responsive, longValue(response.body,"startupBurstSeconds",0));
                    acceptedBufferAheadMs=requestedBufferAheadMs;
                    acceptedDeinterlace = deinterlace;
                    startOffsetMs = 0L;
                    openedAtNanos = System.nanoTime();
                    activeMediaUrl=base+media;activeCaptionToken=captionToken;
                    acceptedVideoKbps=(int)longValue(response.body,"videoTargetKbps",6000);
                    qualityCeilingKbps=qualityRecoverySupported?(int)longValue(response.body,"videoCeilingKbps",acceptedVideoKbps):acceptedVideoKbps;
                    qualityInitialPositionMs=0;qualityClockHoldMs=-1;bufferReportAt=-1;bufferReportId=0;bufferReportBusy=false;bufferObservation="warming-up";
                    quality.source(new MimDirectQualityClient.Source(owner,base,token,activeMediaUrl,captionToken,0,acceptedVideoKbps,qualityCeilingKbps,qualityRecoverySupported),qualitySupported);
                }
            }
            if (!accepted)
            {
                teardown(base, token);
                return null;
            }
            if (!captionToken.isEmpty())
                MiniclientApplication.get().getFixedCaptionSideChannel()
                        .startClaimed(base, captionToken);
            state = "active_" + mode;
            return base + media;
        }
        catch (Exception failure)
        {
            final boolean blocked=blocksOriginalPullAfterStartFailure(mode,outputSettings!=null&&outputSettings.strict());
            if(blocked){
                outputFailure="Server transcoding could not start. Original-file Pull fallback was not used. Check server transcoding diagnostics or select Stock SageTV Pipeline.";
            }
            synchronized (lock)
            {
                lateFallbackReconnectClaimed = false;
                state = blocked?"start_failed_transcode_stopped":"start_failed_pull_fallback";
            }
            log.debug("Optional MIM Direct start failed ({})",
                    failure.getClass().getSimpleName());
            return null;
        }
    }

    static boolean blocksOriginalPullAfterStartFailure(String mode,boolean strictOutput){
        // A present provider accepting an explicit Transcode/HLS request must
        // not silently substitute the untouched source on startup failure.
        // Missing providers still negotiate ordinary stock Fixed in prepare();
        // non-strict Direct Copy retains its original-file compatibility path.
        return MimDirectTransportPolicy.TRANSCODE.equals(mode)||strictOutput;
    }

    /**
     * Claim the only automatic recovery allowed after Direct startup failed.
     * The caller must also prove the original Pull source failed before its
     * first frame. The next connection advertises ordinary Fixed capabilities;
     * subsequent connections use the unchanged saved Direct preference again.
     */
    public boolean requestStockFixedReconnectForUnplayablePull()
    {
        if(outputSettings!=null&&outputSettings.strict())return false;
        if (pluginWatchRecoveryRequired)
        {
            if (watchRecovery.active()) return true;
            if (!lateFallbackStateAllowsReconnect(state)) return false;
            return watchRecovery.request(() -> {
                synchronized(lock) {
                    lateFallbackReconnectClaimed=true; suppressNextPrepareForStockFixed=true;
                    state="plugin_watch_stock_fixed_reconnect_requested";
                }
                MiniclientApplication.get().getClient().eventbus().post(
                        new opensagetv.vibe.miniclient.android.events.MimDirectFallbackReconnectEvent(
                                "plugin_watch_recovery",true));
            },() -> {
                state="plugin_watch_recovery_unavailable";
                opensagetv.vibe.miniclient.MiniClient client=MiniclientApplication.get().getClient();
                final opensagetv.vibe.miniclient.MiniPlayerPlugin currentPlayer=client.getPlayer();
                boolean currentSource=currentPlayer !=null && watchRecovery.sourceMatches(source ->
                        source ==currentPlayer || (currentPlayer instanceof
                                opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl
                                && ((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)
                                currentPlayer).isPlaybackOwner(source)));
                if (client.getCurrentConnection() !=null && currentSource)
                    opensagetv.vibe.miniclient.uibridge.EventRouter.postCommand(client,
                            opensagetv.vibe.miniclient.SageCommand.STOP);
                log.warn("Optional stock watch recovery unavailable; no Watch replay, current source stop={}", currentSource);
                opensagetv.vibe.miniclient.android.AppUtil.message(
                        "Automatic playback recovery unavailable. Select the recording again to use ordinary Fixed playback.");
            });
        }
        synchronized (lock)
        {
            if (!lateFallbackStateAllowsReconnect(state)
                    || lateFallbackReconnectClaimed
                    || suppressNextPrepareForStockFixed)
                return false;
            lateFallbackReconnectClaimed = true;
            suppressNextPrepareForStockFixed = true;
            state = "late_failure_stock_fixed_reconnect_requested";
            return true;
        }
    }

    public boolean requiresPluginWatchRecovery() { return pluginWatchRecoveryRequired; }
    public boolean hasPendingPluginWatchRecovery() { return watchRecovery.pending(); }
    public boolean commitPluginWatchRecoveryHandoff() { return watchRecovery.commitHandoff(); }
    public interface RecoveryConnectionReady { boolean ready(); }

    /** Record actual server seek intent without HTTP/decoder-state reads. */
    public void recordRecoveryPosition(Object playbackOwner,long target,boolean playing)
    {
        if (pluginWatchRecoveryRequired) watchRecovery.position(playbackOwner,target,playing);
    }
    public void recordRecoveryPlaying(Object playbackOwner,boolean playing)
    {
        if (pluginWatchRecoveryRequired) watchRecovery.playing(playbackOwner,playing);
    }

    public void onFreshConnectionForWatchRecovery(Object connection,
            RecoveryConnectionReady ready) { watchRecovery.connected(connection,ready::ready); }

    public void onRecoveryVideoFrame(Object owner,Object connection) { watchRecovery.video(owner,connection); }

    /** Explicit user controls cancel custody before their normal server command. */
    public void onUserPlaybackCommand(opensagetv.vibe.miniclient.SageCommand command)
    {
        if (command ==null) return;
        switch (command) {
            case STOP: case PLAY: case PAUSE: case PLAY_PAUSE: case FF: case REW:
            case FF_2: case REW_2: case RIGHT_FF: case LEFT_REW:
                if (!"ready".equals(watchRecovery.state()) && !"off".equals(watchRecovery.state()))
                    watchRecovery.cancel();
                break;
            default: break;
        }
    }

    /** Only the deliberate fresh-session handoff retains its scoped ticket. */
    public void stopForActivityReplacement(boolean replacing)
    {
        if (replacing && watchRecovery.pending()) stopTransportOnly();
        else stop();
    }

    /** The existing MiniClient connection accepted its native reconnect path. */
    public void useInPlaceStockFixedReconnect()
    {
        synchronized (lock)
        {
            suppressNextPrepareForStockFixed = false;
            baseUrl = "";
            activeMode = MimDirectTransportPolicy.OFF;
            hlsSelected = false;
            responsiveHlsNegotiated = responsiveHlsActive = false;
            qualitySupported=false;
            state = "late_failure_stock_fixed_reconnect";
        }
    }

    static boolean lateFallbackStateAllowsReconnect(String value)
    {
        return "start_failed_pull_fallback".equals(value);
    }

    /**
     * Arm or clear the debug APK's deterministic two-stage startup failure.
     * The exported receiver exists only in the debug manifest; normal builds
     * have no caller for this test seam. Both failures are consumed once.
     */
    public String setDebugLateFallbackFailure(boolean enabled)
    {
        return setDebugLateFallbackFailure(enabled, true);
    }

    public String setDebugLateFallbackFailure(boolean enabled, boolean failPull)
    {
        synchronized (lock)
        {
            debugForceNextDirectStartFailure = enabled;
            debugForceFallbackPullFailure = enabled && failPull;
            return "armed=" + enabled + ";state=" + state;
        }
    }

    /** Replace only the Pull attempt following the injected Direct failure. */
    public String debugFallbackPullUrl(String originalUrl)
    {
        synchronized (lock)
        {
            if (!debugForceFallbackPullFailure
                    || !lateFallbackStateAllowsReconnect(state))
                return originalUrl;
            debugForceFallbackPullFailure = false;
            return "http://127.0.0.1:1/vibe-debug-unplayable-pull.ts";
        }
    }

    static boolean supportsPlaybackOwnerClass(String className, String mode)
    {
        return !MimDirectTransportPolicy.TRANSCODE.equals(mode) ||
                !IJK_PLAYER_CLASS.equals(className);
    }

    static String normalizedDeinterlace(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return "on".equals(normalized) || "off".equals(normalized)
                ? normalized : "auto";
    }

    static String startRequestPath(String source, String mode,
                                   String deinterlace, boolean active)
            throws IOException
    {
        return "/v1/direct/start?source=" +
                URLEncoder.encode(source, "UTF-8") + "&mode=" + mode +
                "&deinterlace=" + deinterlace + "&active=" + active +
                "&startMs=0";
    }

    private static String currentDeinterlace()
    {
        return DeinterlacePolicy.serverFilter(
                ActivePlayerSessionOverrides.resolveVideoDeinterlaceMode(
                        MiniclientApplication.get().getClient().properties().getString(
                                PrefStore.Keys.video_deinterlace_mode, "")), normalizedDeinterlace(
                ActivePlayerSessionOverrides.resolveMimDirectDeinterlace(
                        MiniclientApplication.get().getClient().properties().getString(
                                PrefStore.Keys.mim_direct_deinterlace, "auto"))));
    }

    public void release(Object playbackOwner)
    {
        // Player free/activity destruction normally runs on Android's main
        // thread. HttpURLConnection is forbidden there and used to fail with
        // NetworkOnMainThreadException, leaving an orphan server producer.
        // Capture and invalidate ownership synchronously, then perform only
        // the HTTP teardown on this ordered daemon worker.
        releaseInternal(playbackOwner, true);
    }

    private void releaseInternal(Object playbackOwner, boolean asynchronous)
    {
        final String base;
        final String token;
        final long releaseGeneration;
        final MimDirectQualityClient.Source previousQuality;
        synchronized (lock)
        {
            // A null playback owner is the lifecycle-level forced teardown
            // used when the application/session stops. Only reject a release
            // from a different concrete player.
            if (!canRelease(owner, playbackOwner)) return;
            previousQuality=quality.original(owner);
            quality.source(null,false);
            previews.source(null,"","",false);
            base = baseUrl; token = sessionToken;
            owner = null; sessionToken = ""; startOffsetMs = 0L;acceptedBufferAheadMs=0;
            completedResponsiveHlsActive = false;
            outputToken="";outputStatus="";outputReadBusy=false;
            acceptedDeinterlace = "";
            activeMediaUrl="";activeCaptionToken="";qualityInitialPositionMs=0;qualityClockHoldMs=-1;
            lastRestartClamped = false;
            openedAtNanos = 0L;
            lifecycleGeneration++;
            releaseGeneration = lifecycleGeneration;
        }
        if (!token.isEmpty())
        {
            state = "release_pending";
            Runnable releaseTask = new Runnable()
            {
                @Override public void run()
                {
                    int status = teardown(base, token);
                    if(previousQuality!=null&&!previousQuality.token.equals(token))teardown(previousQuality.base,previousQuality.token);
                    log.debug("MIM Direct release status={} token={}", status,
                            token.substring(0, Math.min(8, token.length())));
                    synchronized (lock)
                    {
                        if (lifecycleGeneration == releaseGeneration &&
                                sessionToken.isEmpty())
                            state = status == 200 ? "released" : "release_failed_" + status;
                    }
                }
            };
            if (asynchronous) teardownExecutor.execute(releaseTask);
            else releaseTask.run();
        }
    }

    public String restart(Object playbackOwner, long requestedStartMs)
    {
        final MimDirectQualityClient.Lease qualitySeek=quality.seekLineage(playbackOwner);
        // Restore a still-uncommitted representation before the explicit seek.
        // Its provider request supersedes quality warmup using the old token.
        // An ambiguous commit is never blindly rolled back to a retired token.
        if(qualitySeek!=null)quality.cancel(false);
        else if(quality.pending(playbackOwner))quality.cancel(true);
        synchronized (restartRequestLock)
        {
            final String base;
            final String token;
            final long requestGeneration;
            synchronized (lock)
            {
                if (owner != playbackOwner || sessionToken.isEmpty()) return null;
                base = baseUrl; token = sessionToken;
                requestGeneration = lifecycleGeneration;
            }
            String failureTag = "";
            try
            {
                String deinterlace = currentDeinterlace();
                String route=qualitySeek==null?"/v1/direct/restart?token="+token:
                    "/v1/direct/quality/seek?token="+qualitySeek.original.token+"&replacementToken="+qualitySeek.candidate.token;
                Response response = request("POST", base,
                        route + "&startMs=" +
                                Math.max(0L, requestedStartMs) + "&deinterlace=" +
                                deinterlace, 1500, 20000);
                if (response.status != 200)
                {
                    failureTag = restartRejectionTag(response.status, response.body);
                    throw new IOException("direct restart rejected");
                }
                String replacementToken = token(response.body, "sessionToken");
                String media = stringValue(response.body, "mediaUrl", "");
                long effectiveStartMs = longValue(response.body, "startMs",
                        Math.max(0L, requestedStartMs));
                String captionToken = optionalToken(response.body,
                        "captionSessionToken");
                if (!media.startsWith("/v1/direct/media/"))
                    throw new IOException("invalid direct restart URL");
                boolean accepted;
                synchronized (lock)
                {
                    accepted = lifecycleGeneration == requestGeneration &&
                            owner == playbackOwner && sessionToken.equals(token);
                    if (accepted)
                    {
                        sessionToken = replacementToken;
                        outputToken=replacementToken;outputStatus=response.body;
                        acceptedDeinterlace = deinterlace;
                        startOffsetMs = Math.max(0L, effectiveStartMs);
                        lastRestartClamped = startOffsetMs != Math.max(0L, requestedStartMs);
                        openedAtNanos = System.nanoTime();
                        activeMediaUrl=base+media;activeCaptionToken=captionToken;qualityInitialPositionMs=0;qualityClockHoldMs=-1;
                        // An atomic seek can choose the original rather than
                        // an uncommitted candidate. Trust the returned owner,
                        // not the target cached while that candidate was bound.
                        acceptedVideoKbps=(int)longValue(response.body,"videoTargetKbps",acceptedVideoKbps);
                        runtimeActive=runtimeNegotiated&&runtimePart(response.body).contains("\"available\":true");
                        runtimeBusy=false;runtimeRequestId=0;runtimeState=runtimeActive?"ready":"unavailable";
                        responsiveHlsActive = responsiveHlsNegotiated && acceptedResponsiveHls(response.body,acceptedBufferAheadMs);
                        completedResponsiveHlsActive = completedResponsiveHlsActive
                                && responsiveHlsActive && longValue(response.body,"startupBurstSeconds",0)>0;
                        qualityCeilingKbps=(int)longValue(response.body,"videoCeilingKbps",qualityCeilingKbps);
                        bufferReportAt=-1;bufferReportBusy=false;bufferObservation="seek-warming-up";
                        quality.seekSource(new MimDirectQualityClient.Source(owner,base,replacementToken,activeMediaUrl,captionToken,startOffsetMs,acceptedVideoKbps,qualityCeilingKbps,qualityRecoverySupported),qualitySupported);
                    }
                }
                if (!accepted)
                {
                    // The server may already have created the replacement before
                    // STOP/FREE invalidated this request. Never leave that late
                    // replacement running without a client owner.
                    teardown(base, replacementToken);
                    return null;
                }
                if (!captionToken.isEmpty())
                    MiniclientApplication.get().getFixedCaptionSideChannel()
                            .startClaimed(base, captionToken);
                state = "active_" + activeMode;
                return base + media;
            }
            catch (Exception failure)
            {
                state = "restart_failed_session_retained_" +
                        (failureTag.isEmpty() ? failure.getClass().getSimpleName() : failureTag);
                log.warn("MIM Direct restart failed; retaining the active session ({})",
                        failureTag.isEmpty() ? failure.getClass().getSimpleName() : failureTag);
                return null;
            }
        }
    }

    /**
     * A bounded, closed diagnostic vocabulary. Never export a server response,
     * request URL, token or arbitrary error string into the client log/state.
     * This only distinguishes API rejection from an I/O failure; it does not
     * retry a seek or relabel retained old playback as successful replacement.
     */
    static String restartRejectionTag(int status, String body)
    {
        String code = stringValue(body, "error", "");
        switch (code)
        {
            case "unknown_or_finished_session":
            case "direct_session_limit":
            case "direct_caption_slot_unavailable":
            case "mim_direct_disabled":
            case "mim_direct_restart_failed":
                break;
            default:
                code = "unclassified";
        }
        return "http_" + (status >= 100 && status <= 599 ? status : 0) + "_" + code;
    }

    public void stop()
    {
        watchRecovery.cancel();
        pluginWatchRecoveryRequired=false;
        stopTransportOnly();
    }

    private void stopTransportOnly()
    {
        release(null);
        synchronized (lock)
        {
            baseUrl = "";
            activeMode = MimDirectTransportPolicy.OFF;
            hlsSelected = false;
            responsiveHlsNegotiated = responsiveHlsActive = false;
            qualitySupported=false;
            previewSupported=false;
            outputSettings=null;outputNegotiated=false;encoderPoliciesNegotiated=false;hardwareBackendsNegotiated=false;hlsBufferNegotiated=false;outputReadBusy=false;outputStatus="";outputToken="";
            runtimeActive=runtimeNegotiated=runtimeBusy=false;runtimeState="off";bufferReportBusy=false;bufferReportAt=-1;bufferObservation="inactive";
            runtimeAvailable=false;bufferAdaptiveAvailable=false;bufferAdaptiveNegotiated=false;
        }
        state = "off";
    }

    /**
     * Consume redundant same-position seeks SageTV sends during the bounded
     * OPENURL startup window (or immediately after a direct-session restart).
     * Restarting the producer for those no-op seeks can retire the playlist
     * while the decoder is still opening it, so leave the already-correct
     * producer running. A different target or any later seek is never hidden.
     */
    public boolean consumeRedundantStartupSeek(Object playbackOwner, long requestedStartMs)
    {
        synchronized (lock)
        {
            long ageNs = openedAtNanos == 0L ? Long.MAX_VALUE
                    : System.nanoTime() - openedAtNanos;
            if (owner != playbackOwner || sessionToken.isEmpty() ||
                    requestedStartMs != startOffsetMs ||
                    !isStartupSeekAge(ageNs)) return false;
            state = "active_" + activeMode + "_startup_seek_suppressed";
            return true;
        }
    }

    static boolean canRelease(Object currentOwner, Object requestedOwner)
    {
        return requestedOwner == null || currentOwner == null ||
                currentOwner == requestedOwner;
    }

    static boolean isStartupSeekAge(long ageNs)
    {
        return ageNs >= 0L && ageNs <= REDUNDANT_STARTUP_SEEK_NS;
    }

    public boolean owns(Object playbackOwner)
    {
        synchronized (lock) { return owner == playbackOwner && !sessionToken.isEmpty(); }
    }

    /** Actual owned HLS output, not merely a saved preference or fallback. */
    public boolean ownsHls(Object playbackOwner)
    {
        synchronized (lock) {
            return hlsSelected && owner == playbackOwner && !sessionToken.isEmpty();
        }
    }

    public boolean ownsResponsiveHls(Object playbackOwner)
    {
        synchronized (lock) {
            return responsiveHlsActive && hlsSelected && owner == playbackOwner && !sessionToken.isEmpty();
        }
    }

    /** Only server-confirmed completed HLS receives the near-full refill policy. */
    public boolean ownsCompletedResponsiveHls(Object playbackOwner)
    {
        synchronized (lock) {
            return completedResponsiveHlsActive && responsiveHlsActive && hlsSelected
                    && owner == playbackOwner && !sessionToken.isEmpty();
        }
    }

    public String hlsProfileForDiagnostics()
    {
        synchronized (lock) {
            return sessionToken.isEmpty() || !hlsSelected ? "off"
                    : responsiveHlsActive ? "responsive" : "legacy";
        }
    }

    /** Opaque source custody for a read-only dialog; never a playback token
     * exposed to the view. A seek/quality swap/file change invalidates it. */
    public static final class PreviewHandle {
        private final Object owner;private final String base,token;
        private PreviewHandle(Object owner,String base,String token){this.owner=owner;this.base=base;this.token=token;}
    }
    public PreviewHandle previewHandle(){synchronized(lock){
        return previewSupported&&owner!=null&&!sessionToken.isEmpty()&&!quality.pending(owner)
                ?new PreviewHandle(owner,baseUrl,sessionToken):null;
    }}
    private boolean previewCurrent(PreviewHandle handle){
        return handle!=null&&previewSupported&&owner==handle.owner&&baseUrl.equals(handle.base)
                &&sessionToken.equals(handle.token)&&!quality.pending(owner);
    }
    public boolean previewCurrent(PreviewHandle handle,MimScrubPreviewClient.Result result){synchronized(lock){
        return previewCurrent(handle)&&previews.current(handle.owner,handle.base,handle.token,result);
    }}
    public boolean requestPreview(PreviewHandle handle,long positionMs,MimScrubPreviewClient.Callback callback){synchronized(lock){
        if(!previewCurrent(handle))return false;
        previews.source(handle.owner,handle.base,handle.token,true);
        return previews.request(handle.owner,positionMs,callback);
    }}
    public void cancelPreview(PreviewHandle handle){synchronized(lock){
        if(previewCurrent(handle))previews.source(null,"","",false);
    }}

    public long consumeQualityInitialPosition(Object playbackOwner){synchronized(lock){long value=owner==playbackOwner?qualityInitialPositionMs:0;if(owner==playbackOwner)qualityInitialPositionMs=0;return value;}}
    boolean qualityClockHeld(Object playbackOwner){synchronized(lock){return owner==playbackOwner&&qualityClockHoldMs>=0;}}
    boolean qualityPending(Object playbackOwner){return quality.pending(playbackOwner);}
    boolean qualityAvailable(Object playbackOwner){synchronized(lock){return owner==playbackOwner&&qualitySupported;}}
    public String qualityStateForDiagnostics(){synchronized(lock){return owner!=null&&(runtimeActive||runtimeBusy||runtimeState.startsWith("unconfirmed"))?"in_place_"+runtimeState:quality.state();}}
    public void requestBitrateSelfTest(boolean start,java.util.function.Consumer<String> completion){
        // Stop correctly retires the playback base URL. Resolve the current
        // connected server independently so an idle settings menu can still
        // run/read this diagnostic without starting media just to discover it.
        opensagetv.vibe.miniclient.android.MiniclientApplication app=opensagetv.vibe.miniclient.android.MiniclientApplication.get();
        opensagetv.vibe.miniclient.MiniClient client=app==null?null:app.getClient();
        ServerInfo connected=client==null?null:client.getConnectedServerInfo();
        final String base=diagnosticBase(connected,client==null?null:client.properties());
        if(base.isEmpty()){completion.accept("Connect to a server with MIM HLS/Direct support first.");return;}
        qualityWorker.execute(()->{
            String summary;
            try{Response r=request(start?"POST":"GET",base,"/v1/diagnostics/bitrate-test",1500,3000);
                summary=r.status==200?"Bitrate test: "+stringValue(r.body,"state","unknown")+(r.body.contains("measuredKbps")?" (measured result saved on server)":""):
                    "Test refused: stop playback/recordings first, or update the plugin.";
            }catch(Exception e){summary="Bitrate test unavailable; playback unchanged.";}
            completion.accept(summary);
        });
    }
    static String diagnosticBase(ServerInfo server,PrefStore prefs){
        if(server==null||prefs==null||server.address==null||server.address.trim().isEmpty())return "";
        String host=server.address.trim();if(host.indexOf(':')>=0&&!host.startsWith("["))host="["+host+"]";
        return "http://"+host+":"+boundedPort(prefs.getString(PrefStore.Keys.fixed_caption_side_channel_port,String.valueOf(DEFAULT_PORT)));
    }
    public String qualityFailureForDiagnostics(){return quality.failure();}
    /** On-demand closed numeric state from existing opted-in read callbacks;
     * no URLs, player polling or new traffic are added for diagnostics. */
    public String qualityObservationForDiagnostics(){synchronized(lock){return owner!=null&&qualitySupported?
        bufferObservation:"inactive";}}
    public int qualityVideoTargetForDiagnostics(){synchronized(lock){return owner!=null&&qualitySupported?acceptedVideoKbps:0;}}
    public int qualityRecoveryTarget(){synchronized(lock){return owner!=null&&qualityRecoverySupported&&acceptedVideoKbps<qualityCeilingKbps?Math.min(qualityCeilingKbps,acceptedVideoKbps*5/4):0;}}

    /** Existing published-segment reads provide a bounded observation, never
     * a bandwidth-test request or a decoder getter on a loader thread. Caller
     * must opt in and prove current Direct Transcode/recent buffering. */
    void observeQualityTransfer(Object playbackOwner,String uri,long bytes,long readMs,long position,boolean recentlyBuffering,long reserveMs,MimDirectQualityClient.Renderer renderer){
        // Transfer timing is diagnostic only; it must never decide adaptation.
    }
    void suspendQualityBuffer(Object playbackOwner){synchronized(lock){if(owner==playbackOwner){bufferNeedsReset=true;bufferObservation="paused-or-starting";}}}
    void observeQualityBuffer(Object playbackOwner,long position,boolean recentlyBuffering,long reserveMs,int minimum,MimDirectQualityClient.Renderer renderer){
        final String base,token;final long generation,report,local;final boolean eligible;
        synchronized(lock){
            if(owner!=playbackOwner||!bufferAdaptiveNegotiated||!qualitySupported||runtimeBusy||bufferReportBusy||quality.pending(playbackOwner)||runtimeState.startsWith("unconfirmed"))return;
            long now=System.nanoTime()/1000000L;
            if(bufferReportAt>=0&&now-bufferReportAt<3000)return;
            bufferReportAt=now;bufferReportBusy=true;base=baseUrl;token=sessionToken;generation=lifecycleGeneration;report=++bufferReportId;local=position-startOffsetMs;
            eligible=!bufferNeedsReset;bufferNeedsReset=false;
        }
        qualityWorker.execute(()->{
            try{
                synchronized(lock){if(owner!=playbackOwner||generation!=lifecycleGeneration||!sessionToken.equals(token))return;}
                if(!renderer.eligible())return;
                Response response=request("POST",base,"/v1/direct/buffer?token="+token+"&reportId="+report+
                    "&positionMs="+Math.max(0,local)+"&bufferMs="+reserveMs+"&buffering="+recentlyBuffering+"&eligible="+eligible,1500,3000);
                if(response.status!=200||longValue(response.body,"revision",0)!=1||longValue(response.body,"reportId",0)!=report)throw new IOException("buffer_report_unavailable");
                int target=(int)longValue(response.body,"targetKbps",0);boolean constrained;
                synchronized(lock){
                    if(owner!=playbackOwner||generation!=lifecycleGeneration||!sessionToken.equals(token))return;
                    String reason=stringValue(response.body,"reason","unknown");
                    bufferObservation="MIM buffer-only: "+(reason.matches("[a-z-]{1,48}")?reason:"unknown");
                    if(!eligible||bufferNeedsReset||target==0||target<adaptiveMinimumKbps||target>qualityCeilingKbps)return;
                    constrained=target<acceptedVideoKbps;
                }
                if(renderer.eligible())requestQuality(playbackOwner,renderer.position(),target,renderer,constrained);
            }catch(Exception unavailable){synchronized(lock){if(owner==playbackOwner&&generation==lifecycleGeneration&&sessionToken.equals(token))bufferObservation="MIM report unavailable; bitrate held";}}
            finally{synchronized(lock){if(owner==playbackOwner&&generation==lifecycleGeneration&&sessionToken.equals(token))bufferReportBusy=false;}}
        });
    }
    boolean requestQuality(Object playbackOwner,long position,int kbps,MimDirectQualityClient.Renderer renderer){
        return requestQuality(playbackOwner,position,kbps,renderer,false);
    }
    private boolean requestQuality(Object playbackOwner,long position,int kbps,MimDirectQualityClient.Renderer renderer,boolean constrained){
        synchronized(lock){
            // An uncertain mutation can have reached the encoder even if the
            // response was lost. Keep playing but hold adaptation until a new
            // source/seek establishes a fresh owner; never use stale target
            // metadata to launch a replacement or replay a relative command.
            if(runtimeState.startsWith("unconfirmed"))return false;
            if(runtimeActive)return requestRuntimeRate(playbackOwner,kbps);
        }
        return quality.request(playbackOwner,position,kbps,new MimDirectQualityClient.Renderer(){
            public long position(){return renderer.position();}
            // Sustained measured limitation can finish a chunk just as the
            // player becomes READY. It still cannot reliably play through a
            // future5s lead; use current-position warmup for this downshift.
            public boolean stalled(){return constrained||renderer.stalled();}
            public void ready(MimDirectQualityClient.Lease lease){renderer.ready(lease);}
            public void rollback(MimDirectQualityClient.Lease lease){
                synchronized(lock){
                    if(owner!=playbackOwner||!sessionToken.equals(lease.candidate.token))return;
                    sessionToken=lease.original.token;startOffsetMs=lease.original.offset;activeMediaUrl=lease.original.media;
                    activeCaptionToken=lease.original.caption;acceptedVideoKbps=lease.original.kbps;
                    qualityInitialPositionMs=Math.max(0,lease.join-startOffsetMs);
                    qualityClockHoldMs=lease.join;
                }
                claimQualityCaptions(lease.original);renderer.rollback(lease);
            }
            public void committed(MimDirectQualityClient.Lease lease){synchronized(lock){if(owner==playbackOwner&&sessionToken.equals(lease.candidate.token))qualityInitialPositionMs=0;}refreshServerOutput(null);renderer.committed(lease);}
        });
    }
    static String runtimePart(String json){
        Matcher part=Pattern.compile("\"runtimeBitrateControl\"\\s*:\\s*(\\{[^{}]*\\})").matcher(json==null?"":json);
        return part.find()?part.group(1).replaceAll("\\s+",""):"";
    }
    private boolean requestRuntimeRate(Object playbackOwner,int target){
        final String base,token;final long generation,id;
        synchronized(lock){
            if(owner!=playbackOwner||!runtimeActive||runtimeBusy||quality.pending(playbackOwner)||target<250||target>qualityCeilingKbps)return false;
            runtimeBusy=true;runtimeState="pending";base=baseUrl;token=sessionToken;generation=lifecycleGeneration;id=++runtimeRequestId;
        }
        qualityWorker.execute(()->{
            try{
                Response response=request("POST",base,"/v1/direct/rate?token="+token+"&requestId="+id+"&videoKbps="+target,1500,3000);
                if(response.status!=200)throw new IOException("runtime_rate_rejected");
                long deadline=System.nanoTime()+16000000000L;
                while(true){
                    synchronized(lock){if(owner!=playbackOwner||lifecycleGeneration!=generation||!sessionToken.equals(token))return;}
                    if(longValue(response.body,"requestId",-1)!=id)throw new IOException("runtime_rate_lineage_mismatch");
                    String result=stringValue(response.body,"state","unconfirmed");
                    if("applied".equals(result)){
                        int applied=(int)longValue(response.body,"appliedKbps",0);
                        if(applied!=target)throw new IOException("runtime_rate_target_mismatch");
                        synchronized(lock){
                            if(owner!=playbackOwner||lifecycleGeneration!=generation||!sessionToken.equals(token))return;
                            acceptedVideoKbps=applied;runtimeState="applied";
                            quality.seekSource(new MimDirectQualityClient.Source(owner,base,token,activeMediaUrl,activeCaptionToken,startOffsetMs,
                                applied,qualityCeilingKbps,qualityRecoverySupported),qualitySupported);
                        }
                        return;
                    }
                    if(!"pending".equals(result)||System.nanoTime()>=deadline)throw new IOException("runtime_rate_unconfirmed");
                    Thread.sleep(500);
                    response=request("GET",base,"/v1/direct/rate?token="+token,1500,3000);
                    if(response.status!=200)throw new IOException("runtime_rate_status_unavailable");
                }
            }catch(Exception failed){synchronized(lock){if(owner==playbackOwner&&lifecycleGeneration==generation&&sessionToken.equals(token)){
                runtimeActive=false;runtimeState="unconfirmed_retained_stream";
            }}}
            finally{synchronized(lock){if(owner==playbackOwner&&lifecycleGeneration==generation&&sessionToken.equals(token))runtimeBusy=false;}}
        });
        return true;
    }
    boolean bindQuality(Object playbackOwner,MimDirectQualityClient.Lease lease,long latestPosition){
        synchronized(lock){
            if(owner!=playbackOwner||!sessionToken.equals(lease.original.token)||!quality.bind(lease,latestPosition))return false;
            bufferReportAt=-1;bufferReportBusy=false;bufferObservation="replacement-warming-up";
            sessionToken=lease.candidate.token;startOffsetMs=lease.candidate.offset;activeMediaUrl=lease.candidate.media;
            activeCaptionToken=lease.candidate.caption;acceptedVideoKbps=lease.candidate.kbps;
            qualityInitialPositionMs=Math.max(0,lease.join-startOffsetMs);openedAtNanos=System.nanoTime();
            qualityClockHoldMs=lease.join;
        }
        claimQualityCaptions(lease.candidate);return true;
    }
    void qualityPresented(MimDirectQualityClient.Lease lease,long localPosition){
        long absolutePosition=lease.candidate.offset+Math.max(0,localPosition);
        synchronized(lock){if(owner!=lease.original.owner||!sessionToken.equals(lease.candidate.token))return;if(absolutePosition>=lease.join)qualityClockHoldMs=-1;}
        quality.presented(lease,absolutePosition);
    }
    void qualityRendererRestored(Object playbackOwner,long localPosition){synchronized(lock){if(owner==playbackOwner&&startOffsetMs+Math.max(0,localPosition)>=qualityClockHoldMs)qualityClockHoldMs=-1;}}
    boolean qualityFailed(MimDirectQualityClient.Lease lease){return quality.abort(lease,true);}
    private void claimQualityCaptions(MimDirectQualityClient.Source source){
        if(source.caption.isEmpty())return;
        // Caption HTTP commissioning belongs off Android's main thread. The
        // existing side-channel controller generation guards stale claims.
        qualityWorker.execute(()->{synchronized(lock){if(owner!=source.owner||!sessionToken.equals(source.token))return;}
            MiniclientApplication.get().getFixedCaptionSideChannel().startClaimed(source.base,source.caption);});
    }

    public boolean lastRestartWasClamped(Object playbackOwner)
    {
        synchronized (lock) { return owner == playbackOwner && lastRestartClamped; }
    }

    public long adjustPosition(Object playbackOwner, long playerPositionMs)
    {
        synchronized (lock)
        {
            return owner == playbackOwner ? qualityClockHoldMs>=0?qualityClockHoldMs:startOffsetMs + playerPositionMs : playerPositionMs;
        }
    }

    /** On-demand debug attribution only. Never returns an endpoint or token. */
    public String mediaUriStateForDiagnostics(String observedUri)
    {
        final String base;
        final String token;
        synchronized (lock) { base = baseUrl; token = sessionToken; }
        return compareMediaUri(base, token, observedUri);
    }

    static String compareMediaUri(String base, String token, String observedUri)
    {
        if (token == null || token.isEmpty()) return "inactive";
        if (observedUri == null || observedUri.isEmpty()) return "unavailable";
        try
        {
            URI expected = URI.create(base);
            URI actual = URI.create(observedUri);
            if (!expected.getScheme().equalsIgnoreCase(actual.getScheme())
                    || !expected.getRawAuthority().equalsIgnoreCase(actual.getRawAuthority()))
                return "foreign";
            String path = actual.getPath();
            if (path == null || !path.startsWith("/v1/direct/media/")) return "foreign";
            return path.startsWith("/v1/direct/media/" + token + "/")
                    ? "current" : "retired";
        }
        catch (RuntimeException malformed) { return "unavailable"; }
    }

    /** Closed HTTP-media vocabulary; arbitrary response bodies remain private. */
    public static String mediaResponseTagForDiagnostics(int status, byte[] body)
    {
        String prefix = status >= 100 && status <= 599 ? "http_" + status : "http_unknown";
        if (body != null && body.length <= 4096)
        {
            String code = stringValue(new String(body, StandardCharsets.UTF_8), "error", "");
            if ("media_not_ready".equals(code) || "unknown_media".equals(code))
                return prefix + "_" + code;
        }
        return prefix + "_unclassified";
    }

    /** Caption tap PTS is relative to the active Direct FFmpeg invocation. */
    public long captionClockPosition(Object playbackOwner, long sageMediaTimeMs)
    {
        synchronized (lock)
        {
            return owner == playbackOwner
                    ? relativeCaptionClock(sageMediaTimeMs, startOffsetMs)
                    : sageMediaTimeMs;
        }
    }

    static long relativeCaptionClock(long sageMediaTimeMs, long directStartMs)
    {
        return Math.max(0L, sageMediaTimeMs - directStartMs);
    }

    public String stateForDiagnostics() { return state; }
    public String acceptedDeinterlaceForDiagnostics()
    {
        synchronized (lock)
        {
            return !sessionToken.isEmpty() && MimDirectTransportPolicy.TRANSCODE.equals(activeMode)
                    ? acceptedDeinterlace : "";
        }
    }
    public String recoveryStateForDiagnostics() { return watchRecovery.state(); }

    static String serverPath(String url)
    {
        if (url == null) return "";
        String value = url.trim();
        if (value.isEmpty() || value.startsWith("push:") ||
                value.startsWith("http://") || value.startsWith("https://")) return "";
        try
        {
            if (value.startsWith("stv://"))
            {
                // Stock Windows SageTV sends paths such as
                // stv://server/V:\\recordings\\show.ts. Backslashes make the
                // complete value an invalid java.net.URI, so split the stv
                // authority first and preserve the server-native path. This
                // also preserves a Unix absolute path because its wire form
                // contains two slashes after the authority.
                int pathStart = value.indexOf('/', "stv://".length());
                if (pathStart < 0 || pathStart + 1 >= value.length()) return "";
                value = value.substring(pathStart + 1);
            }
            if (value.startsWith("file:"))
                value = new URI(value).getPath();
        }
        catch (Exception invalid) { return ""; }
        if (value == null) return "";
        // URI#getPath represents a Windows file URI as /C:/path. The FFmpeg
        // plugin runs on that Windows server and therefore needs C:/path.
        if (value.matches("^/[A-Za-z]:[\\\\/].*")) value = value.substring(1);
        return value.startsWith("/") || value.matches("^[A-Za-z]:[\\\\/].*")
                ? value : "";
    }

    static boolean supportsHls(String body)
    {
        Matcher section = Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})")
                .matcher(body == null ? "" : body);
        if (!section.find()) return false;
        String compact = section.group(1).replaceAll("\\s+", "");
        return supportsDirect(body, MimDirectTransportPolicy.TRANSCODE)
                && Pattern.compile("\"contractVersion\":1(?=[,}])").matcher(compact).find()
                && compact.contains("\"container\":\"hls-mpegts\"");
    }

    static boolean supportsResponsiveHls(String body)
    {
        Matcher section = Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})")
                .matcher(body == null ? "" : body);
        return supportsHls(body) && section.find()
                && Pattern.compile("\"responsiveHlsRevision\"\\s*:\\s*1\\s*[,}]")
                        .matcher(section.group(1)).find();
    }
    static boolean supportsRuntimeBitrate(String body){
        Matcher section=Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})").matcher(body==null?"":body);
        return section.find()&&Pattern.compile("\"runtimeBitrateControlRevision\"\\s*:\\s*1\\s*[,}]").matcher(section.group(1)).find();
    }
    static boolean supportsOutputRevision(String body,String name){
        if(!"outputSettingsRevision".equals(name)&&!"encoderPolicyRevision".equals(name)&&!"hardwareBackendRevision".equals(name)&&!"hlsBufferRevision".equals(name))return false;
        Matcher section=Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})").matcher(body==null?"":body);
        return section.find()&&Pattern.compile("\""+name+"\"\\s*:\\s*1\\s*[,}]").matcher(section.group(1)).find();
    }

    static boolean acceptedResponsiveHls(String body)
    {
        return acceptedResponsiveHls(body,0);
    }

    static boolean acceptedResponsiveHls(String body,int requestedBufferAheadMs)
    {
        if(requestedBufferAheadMs<0||requestedBufferAheadMs>20000)return false;
        int expected=requestedBufferAheadMs>0?Math.max(6,(requestedBufferAheadMs+999)/1000+3):6;
        String json=body==null?"":body;
        if(!Pattern.compile("\"startupBurstSeconds\"\\s*:\\s*(?:0|"+expected+")\\s*[,}]").matcher(json).find())return false;
        if(requestedBufferAheadMs>0&&!Pattern.compile("\"bufferAheadTargetMs\"\\s*:\\s*"+requestedBufferAheadMs+"\\s*[,}]").matcher(json).find())return false;
        return "responsive".equals(stringValue(body, "hlsProfile", ""))
                && Pattern.compile("\"segmentTargetMs\"\\s*:\\s*1000\\s*[,}]")
                        .matcher(body == null ? "" : body).find();
    }

    private static boolean supportsDirect(String body, String mode)
    {
        Matcher section=Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})")
                .matcher(body==null ? "" : body);
        if (!section.find()) return false;
        String compact = section.group(1).replaceAll("\\s+", "");
        return
                compact.contains("\"contractVersion\":1") &&
                compact.contains("\"available\":true") &&
                compact.contains("\"modes\":[\"copy\",\"transcode\"]") &&
                compact.contains("\"" + mode + "\"");
    }

    private static int boundedPort(String value)
    {
        try
        {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port >= 1024 && port <= 65535 ? port : DEFAULT_PORT;
        }
        catch (NumberFormatException invalid) { return DEFAULT_PORT; }
    }

    static String token(String body, String name)
    {
        String value = stringValue(body, name, "");
        if (value.length() != 64 || !value.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("invalid direct token");
        return value;
    }

    static String optionalToken(String body, String name)
    {
        String value = stringValue(body, name, "").trim();
        if (value.isEmpty()) return "";
        if (value.length() < 24 || value.length() > 128 ||
                !value.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("invalid optional direct token");
        return value;
    }

    static String stringValue(String body, String name, String fallback)
    {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(),
                Pattern.quote(name))).matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    static long longValue(String body, String name, long fallback)
    {
        Matcher matcher = Pattern.compile(String.format(LONG_FIELD.pattern(),
                Pattern.quote(name))).matcher(body == null ? "" : body);
        if (!matcher.find()) return fallback;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    static Response request(String method, String base, String path,
                                    int connectMs, int readMs) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(connectMs);
        connection.setReadTimeout(readMs);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method))
        {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.getOutputStream().close();
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = input == null ? "" : readBounded(input);
        connection.disconnect();
        return new Response(status, body);
    }

    private static String readBounded(InputStream input) throws IOException
    {
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0)
            {
                if (output.size() + count > MAX_REPLY_BYTES)
                    throw new IOException("direct API reply exceeds limit");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        finally { input.close(); }
    }

    private static int teardown(String base, String token)
    {
        try { return request("POST", base, "/v1/direct/teardown?token=" + token,
                1500, 5000).status; }
        catch (Exception ignored) { return -1; }
    }

    static final class Response
    {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
}
