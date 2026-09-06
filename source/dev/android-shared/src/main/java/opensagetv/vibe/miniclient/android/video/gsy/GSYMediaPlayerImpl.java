package opensagetv.vibe.miniclient.android.video.gsy;

import java.io.IOException;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.TransientPushSegmentPlayer;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
import opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.Rectangle;

/**
 * GSYVideoPlayer engine-selection adapter for SageTV.
 *
 * GSY's stock player managers are primarily URL-oriented, while SageTV also feeds
 * PUSH/PULL streams through custom data sources.  This adapter keeps the GSY engine
 * choice independent and routes each selection to a SageTV-aware implementation.
 * Legacy IJK is intentionally excluded: it remains its own top-level backend and
 * keeps the original 0.8.8 Java/JNI runtime.
 */
public final class GSYMediaPlayerImpl implements MiniPlayerPlugin, TransientPushSegmentPlayer
{
    private final AndroidUIController context;
    private MiniPlayerPlugin delegate;
    private boolean pushMode;
    private long loadGeneration;
    private byte lastMajorTypeHint;
    private byte lastMinorTypeHint;
    private String lastEncodingHint;
    private String lastUrlString;
    private String lastHostname;
    private boolean lastTimeshifted;
    private long lastBufferSize;
    private volatile GSYPlayerEngine resolvedEngine = GSYPlayerEngine.AUTO;
    private volatile int systemFallbackCount;
    private volatile String systemFallbackReason = "";

    public GSYMediaPlayerImpl(AndroidUIController activity)
    {
        context = activity;
    }

    private GSYPlayerEngine configuredEngine()
    {
        PrefStore prefs = MiniclientApplication.get().getClient().properties();
        return GSYPlayerEngine.fromPreference(
                prefs.getString(PrefStore.Keys.gsy_player_engine, GSYPlayerEngine.DEFAULT_PREFERENCE));
    }

    private MiniPlayerPlugin createDelegate(GSYPlayerEngine engine, String url)
    {
        GSYPlayerEngine resolved = engine;
        if (resolved == GSYPlayerEngine.AUTO)
        {
            // Media3 is currently the most reliable SageTV-aware engine for both
            // Dynamic/PUSH and Pull.  Keep System MediaPlayer available as an explicit
            // choice while its MediaDataSource compatibility is hardened.  AUTO must
            // prefer a working engine rather than selecting System solely because the
            // source is stv:// or PUSH.
            resolved = GSYPlayerEngine.MEDIA3;
        }

        switch (resolved)
        {
            case LEGACY_EXO:
                resolvedEngine = GSYPlayerEngine.LEGACY_EXO;
                return new Exo2MediaPlayerImpl(context);
            case MEDIA3:
                resolvedEngine = GSYPlayerEngine.MEDIA3;
                return new Media3MediaPlayerImpl(context);
            case SYSTEM:
                PrefStore prefs = MiniclientApplication.get().getClient().properties();
                if (prefs.getBoolean(PrefStore.Keys.gsy_system_probe_enabled, false)
                        && !pushMode)
                {
                    resolvedEngine = GSYPlayerEngine.SYSTEM;
                    final long expectedGeneration = loadGeneration;
                    return new GSYSystemMediaPlayerImpl(context,
                            new GSYSystemMediaPlayerImpl.FailureListener()
                            {
                                @Override
                                public void onSystemPlayerFailed(
                                        final GSYSystemMediaPlayerImpl failedPlayer)
                                {
                                    scheduleSystemFallback(failedPlayer, expectedGeneration);
                                }
                            });
                }
                resolvedEngine = GSYPlayerEngine.MEDIA3;
                return new Media3MediaPlayerImpl(context);
            default:
                resolvedEngine = GSYPlayerEngine.MEDIA3;
                return new Media3MediaPlayerImpl(context);
        }
    }

    private void scheduleSystemFallback(final GSYSystemMediaPlayerImpl failedPlayer,
            final long expectedGeneration)
    {
        context.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (delegate != failedPlayer || loadGeneration != expectedGeneration) return;
                systemFallbackCount++;
                systemFallbackReason = "android_system_player_error";
                resolvedEngine = GSYPlayerEngine.MEDIA3;
                delegate = new Media3MediaPlayerImpl(context);
                delegate.setPushMode(pushMode);
                delegate.load(lastMajorTypeHint, lastMinorTypeHint, lastEncodingHint,
                        lastUrlString, lastHostname, lastTimeshifted, lastBufferSize);
            }
        });
    }

    private MiniPlayerPlugin d()
    {
        return delegate;
    }

    /**
     * Returns the SageTV-aware backend selected by this adapter for bounded
     * debug/MCP inspection. Production playback must continue to use this
     * adapter's MiniPlayerPlugin contract rather than controlling the delegate
     * directly.
     */
    public MiniPlayerPlugin getDelegateForDebug()
    {
        return delegate;
    }

    public String getResolvedEngineForDebug() { return resolvedEngine.preferenceValue(); }
    public int getSystemFallbackCountForDebug() { return systemFallbackCount; }
    public String getSystemFallbackReasonForDebug() { return systemFallbackReason; }

    @Override public void free() { if (d() != null) d().free(); delegate = null; }
    @Override public void setPushMode(boolean b) { pushMode = b; if (d() != null) d().setPushMode(b); }

    @Override
    public void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, String urlString,
                     String hostname, boolean timeshifted, long bufferSize)
    {
        loadGeneration++;
        lastMajorTypeHint = majorTypeHint;
        lastMinorTypeHint = minorTypeHint;
        lastEncodingHint = encodingHint;
        lastUrlString = urlString;
        lastHostname = hostname;
        lastTimeshifted = timeshifted;
        lastBufferSize = bufferSize;
        systemFallbackReason = "";
        if (delegate != null) delegate.free();
        GSYPlayerEngine configured = configuredEngine();
        delegate = createDelegate(configured, urlString);
        delegate.setPushMode(pushMode);
        delegate.load(majorTypeHint, minorTypeHint, encodingHint, urlString, hostname, timeshifted, bufferSize);
    }

    @Override public long getMediaTimeMillis(long lastServerTime) { return d() == null ? 0 : d().getMediaTimeMillis(lastServerTime); }
    @Override public int getState() { return d() == null ? NO_STATE : d().getState(); }
    @Override public void setMute(boolean b) { if (d() != null) d().setMute(b); }
    @Override public void stop() { if (d() != null) d().stop(); }
    @Override public void pause() { if (d() != null) d().pause(); }
    @Override public void play() { if (d() != null) d().play(); }
    @Override public void seek(long timeMS) { if (d() != null) d().seek(timeMS); }
    @Override public float setPlaybackRate(float rate) { return d() == null ? 1.0f : d().setPlaybackRate(rate); }
    @Override public float getPlaybackRate() { return d() == null ? 1.0f : d().getPlaybackRate(); }
    @Override public boolean frameStep(int amount) { return d() != null && d().frameStep(amount); }
    @Override public void setServerEOS() { if (d() != null) d().setServerEOS(); }
    @Override public void signalPushSegmentEnd() {
        if (d() instanceof TransientPushSegmentPlayer)
            ((TransientPushSegmentPlayer) d()).signalPushSegmentEnd();
    }
    @Override public long getLastFileReadPos() { return d() == null ? 0 : d().getLastFileReadPos(); }
    @Override public int getVolume() { return d() == null ? 0 : d().getVolume(); }
    @Override public int setVolume(float v) { return d() == null ? 0 : d().setVolume(v); }
    @Override public void setAudioTrack(int streamPos) { if (d() != null) d().setAudioTrack(streamPos); }
    @Override public void setSubtitleTrack(int streamPos) { if (d() != null) d().setSubtitleTrack(streamPos); }
    @Override public int getSelectedSubtitleTrack() { return d() == null ? DISABLE_TRACK : d().getSelectedSubtitleTrack(); }
    @Override public int getSubtitleTrackCount() { return d() == null ? 0 : d().getSubtitleTrackCount(); }
    @Override public SubtitleTrack[] getSubtitleTracks() { return d() == null ? new SubtitleTrack[0] : d().getSubtitleTracks(); }
    @Override public void setVideoRectangles(Rectangle srcRect, Rectangle destRect, boolean hideCursor) { if (d() != null) d().setVideoRectangles(srcRect, destRect, hideCursor); }
    @Override public Dimension getVideoDimensions() { return d() == null ? null : d().getVideoDimensions(); }
    @Override public void pushData(byte[] cmddata, int bufDataOffset, int buffSize) throws IOException { if (d() != null) d().pushData(cmddata, bufDataOffset, buffSize); }
    @Override public void flush() { if (d() != null) d().flush(); }
    @Override public int getBufferLeft() { return d() == null ? 0 : d().getBufferLeft(); }
    @Override public void setVideoAdvancedAspect(String aspectMode) { if (d() != null) d().setVideoAdvancedAspect(aspectMode); }
    @Override public void run() { if (d() != null) d().run(); }
}
