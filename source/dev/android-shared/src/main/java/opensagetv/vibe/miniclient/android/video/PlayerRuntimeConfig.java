package opensagetv.vibe.miniclient.android.video;

/** Immutable runtime configuration captured for one player construction. */
public final class PlayerRuntimeConfig
{
    public enum Backend { MEDIA3, LEGACY_EXO }

    private final Backend backend;
    private final int tsSearchMultiplier;
    private final int pullReadBytes;
    private final int pullMinBufferMs;
    private final int pullMaxBufferMs;
    private final int pullPlaybackBufferMs;
    private final int pullRebufferMs;
    private final long directionalSyncMinDeltaMs;
    private final boolean seekRecoveryEnabled;
    private final long seekRecoveryDelayMs;
    private final String seekPolicy;
    private final String codecMode;

    private PlayerRuntimeConfig(Backend backend, int tsSearchMultiplier,
                                int pullReadBytes, int pullMinBufferMs,
                                int pullMaxBufferMs, int pullPlaybackBufferMs,
                                int pullRebufferMs, long directionalSyncMinDeltaMs,
                                boolean seekRecoveryEnabled, long seekRecoveryDelayMs,
                                String seekPolicy, String codecMode)
    {
        this.backend = backend;
        this.tsSearchMultiplier = tsSearchMultiplier;
        this.pullReadBytes = pullReadBytes;
        this.pullMinBufferMs = pullMinBufferMs;
        this.pullMaxBufferMs = pullMaxBufferMs;
        this.pullPlaybackBufferMs = pullPlaybackBufferMs;
        this.pullRebufferMs = pullRebufferMs;
        this.directionalSyncMinDeltaMs = directionalSyncMinDeltaMs;
        this.seekRecoveryEnabled = seekRecoveryEnabled;
        this.seekRecoveryDelayMs = seekRecoveryDelayMs;
        this.seekPolicy = seekPolicy;
        this.codecMode = codecMode;
    }

    /**
     * Capture one coherent backend view. Later debug tuning changes therefore
     * apply to the next player instead of changing an active datasource.
     */
    public static PlayerRuntimeConfig capture(Backend backend)
    {
        if (backend == null) throw new IllegalArgumentException("backend is required");
        boolean media3 = backend == Backend.MEDIA3;
        return new PlayerRuntimeConfig(
                backend,
                media3 ? PlayerRuntimeTuning.getMedia3TsSearchMultiplier() : PlayerRuntimeTuning.getExo2TsSearchMultiplier(),
                media3 ? PlayerRuntimeTuning.getMedia3PullReadBytes() : PlayerRuntimeTuning.getExo2PullReadBytes(),
                media3 ? PlayerRuntimeTuning.getMedia3PullMinBufferMs() : PlayerRuntimeTuning.getExo2PullMinBufferMs(),
                media3 ? PlayerRuntimeTuning.getMedia3PullMaxBufferMs() : PlayerRuntimeTuning.getExo2PullMaxBufferMs(),
                media3 ? PlayerRuntimeTuning.getMedia3PullPlaybackBufferMs() : PlayerRuntimeTuning.getExo2PullPlaybackBufferMs(),
                media3 ? PlayerRuntimeTuning.getMedia3PullRebufferMs() : PlayerRuntimeTuning.getExo2PullRebufferMs(),
                PlayerRuntimeTuning.getDirectionalSyncMinDeltaMs(),
                media3 ? PlayerRuntimeTuning.isMedia3SeekRecoveryEnabled() : PlayerRuntimeTuning.isExo2SeekRecoveryEnabled(),
                media3 ? PlayerRuntimeTuning.getMedia3SeekRecoveryDelayMs() : PlayerRuntimeTuning.getExo2SeekRecoveryDelayMs(),
                media3 ? PlayerRuntimeTuning.getMedia3SeekPolicy() : PlayerRuntimeTuning.getExo2SeekPolicy(),
                media3 ? PlayerRuntimeTuning.getMedia3CodecMode() : PlayerRuntimeTuning.getExo2CodecMode());
    }

    public Backend getBackend() { return backend; }
    public int getTsSearchMultiplier() { return tsSearchMultiplier; }
    public int getPullReadBytes() { return pullReadBytes; }
    public int getPullMinBufferMs() { return pullMinBufferMs; }
    public int getPullMaxBufferMs() { return pullMaxBufferMs; }
    public int getPullPlaybackBufferMs() { return pullPlaybackBufferMs; }
    public int getPullRebufferMs() { return pullRebufferMs; }
    public long getDirectionalSyncMinDeltaMs() { return directionalSyncMinDeltaMs; }
    public boolean isSeekRecoveryEnabled() { return seekRecoveryEnabled; }
    public long getSeekRecoveryDelayMs() { return seekRecoveryDelayMs; }
    public String getSeekPolicy() { return seekPolicy; }
    public String getCodecMode() { return codecMode; }

    public String compactLog()
    {
        return "backend=" + backend + ", tsSearchMultiplier=" + tsSearchMultiplier
                + ", pullReadBytes=" + pullReadBytes + ", bufferMs="
                + pullMinBufferMs + "/" + pullMaxBufferMs + "/"
                + pullPlaybackBufferMs + "/" + pullRebufferMs
                + ", seekPolicy=" + seekPolicy + ", seekRecovery="
                + seekRecoveryEnabled + "@" + seekRecoveryDelayMs
                + ", codecMode=" + codecMode;
    }
}
