package sagex.miniclient.android.video;

/**
 * In-memory playback tuning overrides used only by the Dev debug control surface.
 *
 * <p>Release/default behavior is represented by the DEFAULT_* values below. The debug APK may
 * update these values at runtime before a player is created. A process restart restores defaults,
 * so tuning cannot silently persist into a later test session.</p>
 */
public final class PlayerRuntimeTuning
{
    public static final int DEFAULT_TS_SEARCH_MULTIPLIER = 8;
    public static final int DEFAULT_MEDIA3_PULL_READ_BYTES = 256 * 1024;
    public static final int DEFAULT_EXO2_PULL_READ_BYTES = 512 * 1024;
    public static final int DEFAULT_PULL_MIN_BUFFER_MS = 5000;
    public static final int DEFAULT_PULL_MAX_BUFFER_MS = 20000;
    public static final int DEFAULT_PULL_PLAYBACK_BUFFER_MS = 500;
    public static final int DEFAULT_PULL_REBUFFER_MS = 1000;
    public static final long DEFAULT_DIRECTIONAL_SYNC_MIN_DELTA_MS = 2000L;
    public static final boolean DEFAULT_MEDIA3_SEEK_RECOVERY_ENABLED = false;
    public static final boolean DEFAULT_EXO2_SEEK_RECOVERY_ENABLED = true;
    public static final long DEFAULT_SEEK_RECOVERY_DELAY_MS = 10000L;
    public static final String DEFAULT_MEDIA3_SEEK_POLICY = "closest";
    public static final String DEFAULT_EXO2_SEEK_POLICY = "directional";
    public static final String DEFAULT_CODEC_MODE = "sync";

    private static volatile int media3TsSearchMultiplier = DEFAULT_TS_SEARCH_MULTIPLIER;
    private static volatile int exo2TsSearchMultiplier = DEFAULT_TS_SEARCH_MULTIPLIER;
    private static volatile int media3PullReadBytes = DEFAULT_MEDIA3_PULL_READ_BYTES;
    private static volatile int exo2PullReadBytes = DEFAULT_EXO2_PULL_READ_BYTES;
    private static volatile int media3PullMinBufferMs = DEFAULT_PULL_MIN_BUFFER_MS;
    private static volatile int media3PullMaxBufferMs = DEFAULT_PULL_MAX_BUFFER_MS;
    private static volatile int media3PullPlaybackBufferMs = DEFAULT_PULL_PLAYBACK_BUFFER_MS;
    private static volatile int media3PullRebufferMs = DEFAULT_PULL_REBUFFER_MS;
    private static volatile int exo2PullMinBufferMs = DEFAULT_PULL_MIN_BUFFER_MS;
    private static volatile int exo2PullMaxBufferMs = DEFAULT_PULL_MAX_BUFFER_MS;
    private static volatile int exo2PullPlaybackBufferMs = DEFAULT_PULL_PLAYBACK_BUFFER_MS;
    private static volatile int exo2PullRebufferMs = DEFAULT_PULL_REBUFFER_MS;
    private static volatile long directionalSyncMinDeltaMs = DEFAULT_DIRECTIONAL_SYNC_MIN_DELTA_MS;
    private static volatile boolean media3SeekRecoveryEnabled = DEFAULT_MEDIA3_SEEK_RECOVERY_ENABLED;
    private static volatile boolean exo2SeekRecoveryEnabled = DEFAULT_EXO2_SEEK_RECOVERY_ENABLED;
    private static volatile long media3SeekRecoveryDelayMs = DEFAULT_SEEK_RECOVERY_DELAY_MS;
    private static volatile long exo2SeekRecoveryDelayMs = DEFAULT_SEEK_RECOVERY_DELAY_MS;
    private static volatile String media3SeekPolicy = DEFAULT_MEDIA3_SEEK_POLICY;
    private static volatile String exo2SeekPolicy = DEFAULT_EXO2_SEEK_POLICY;
    private static volatile String media3CodecMode = DEFAULT_CODEC_MODE;
    private static volatile String exo2CodecMode = DEFAULT_CODEC_MODE;
    private static volatile boolean media3CodecModeOverridden;
    private static volatile boolean exo2CodecModeOverridden;

    private PlayerRuntimeTuning() {}

    public static synchronized void reset()
    {
        media3TsSearchMultiplier = DEFAULT_TS_SEARCH_MULTIPLIER;
        exo2TsSearchMultiplier = DEFAULT_TS_SEARCH_MULTIPLIER;
        media3PullReadBytes = DEFAULT_MEDIA3_PULL_READ_BYTES;
        exo2PullReadBytes = DEFAULT_EXO2_PULL_READ_BYTES;
        media3PullMinBufferMs = DEFAULT_PULL_MIN_BUFFER_MS;
        media3PullMaxBufferMs = DEFAULT_PULL_MAX_BUFFER_MS;
        media3PullPlaybackBufferMs = DEFAULT_PULL_PLAYBACK_BUFFER_MS;
        media3PullRebufferMs = DEFAULT_PULL_REBUFFER_MS;
        exo2PullMinBufferMs = DEFAULT_PULL_MIN_BUFFER_MS;
        exo2PullMaxBufferMs = DEFAULT_PULL_MAX_BUFFER_MS;
        exo2PullPlaybackBufferMs = DEFAULT_PULL_PLAYBACK_BUFFER_MS;
        exo2PullRebufferMs = DEFAULT_PULL_REBUFFER_MS;
        directionalSyncMinDeltaMs = DEFAULT_DIRECTIONAL_SYNC_MIN_DELTA_MS;
        media3SeekRecoveryEnabled = DEFAULT_MEDIA3_SEEK_RECOVERY_ENABLED;
        exo2SeekRecoveryEnabled = DEFAULT_EXO2_SEEK_RECOVERY_ENABLED;
        media3SeekRecoveryDelayMs = DEFAULT_SEEK_RECOVERY_DELAY_MS;
        exo2SeekRecoveryDelayMs = DEFAULT_SEEK_RECOVERY_DELAY_MS;
        media3SeekPolicy = DEFAULT_MEDIA3_SEEK_POLICY;
        exo2SeekPolicy = DEFAULT_EXO2_SEEK_POLICY;
        media3CodecMode = DEFAULT_CODEC_MODE;
        exo2CodecMode = DEFAULT_CODEC_MODE;
        media3CodecModeOverridden = false;
        exo2CodecModeOverridden = false;
    }

    public static synchronized void configure(
            Integer newMedia3TsSearchMultiplier,
            Integer newExo2TsSearchMultiplier,
            Integer newMedia3PullReadBytes,
            Integer newExo2PullReadBytes,
            Integer newMedia3PullMinBufferMs,
            Integer newMedia3PullMaxBufferMs,
            Integer newMedia3PullPlaybackBufferMs,
            Integer newMedia3PullRebufferMs,
            Integer newExo2PullMinBufferMs,
            Integer newExo2PullMaxBufferMs,
            Integer newExo2PullPlaybackBufferMs,
            Integer newExo2PullRebufferMs,
            Long newDirectionalSyncMinDeltaMs,
            Boolean newMedia3SeekRecoveryEnabled,
            Boolean newExo2SeekRecoveryEnabled,
            Long newMedia3SeekRecoveryDelayMs,
            Long newExo2SeekRecoveryDelayMs,
            String newMedia3SeekPolicy,
            String newExo2SeekPolicy,
            String newMedia3CodecMode,
            String newExo2CodecMode)
    {
        int m3Min = valueOr(newMedia3PullMinBufferMs, media3PullMinBufferMs);
        int m3Max = valueOr(newMedia3PullMaxBufferMs, media3PullMaxBufferMs);
        int m3Play = valueOr(newMedia3PullPlaybackBufferMs, media3PullPlaybackBufferMs);
        int m3Rebuffer = valueOr(newMedia3PullRebufferMs, media3PullRebufferMs);
        int exoMin = valueOr(newExo2PullMinBufferMs, exo2PullMinBufferMs);
        int exoMax = valueOr(newExo2PullMaxBufferMs, exo2PullMaxBufferMs);
        int exoPlay = valueOr(newExo2PullPlaybackBufferMs, exo2PullPlaybackBufferMs);
        int exoRebuffer = valueOr(newExo2PullRebufferMs, exo2PullRebufferMs);

        validateBufferDurations("media3", m3Min, m3Max, m3Play, m3Rebuffer);
        validateBufferDurations("exo2", exoMin, exoMax, exoPlay, exoRebuffer);

        if (newMedia3TsSearchMultiplier != null) media3TsSearchMultiplier = validateMultiplier(newMedia3TsSearchMultiplier);
        if (newExo2TsSearchMultiplier != null) exo2TsSearchMultiplier = validateMultiplier(newExo2TsSearchMultiplier);
        if (newMedia3PullReadBytes != null) media3PullReadBytes = validateReadBytes(newMedia3PullReadBytes);
        if (newExo2PullReadBytes != null) exo2PullReadBytes = validateReadBytes(newExo2PullReadBytes);
        media3PullMinBufferMs = m3Min;
        media3PullMaxBufferMs = m3Max;
        media3PullPlaybackBufferMs = m3Play;
        media3PullRebufferMs = m3Rebuffer;
        exo2PullMinBufferMs = exoMin;
        exo2PullMaxBufferMs = exoMax;
        exo2PullPlaybackBufferMs = exoPlay;
        exo2PullRebufferMs = exoRebuffer;
        if (newDirectionalSyncMinDeltaMs != null) directionalSyncMinDeltaMs = validateLong(newDirectionalSyncMinDeltaMs, 0L, 60000L, "directional sync min delta");
        if (newMedia3SeekRecoveryEnabled != null) media3SeekRecoveryEnabled = newMedia3SeekRecoveryEnabled;
        if (newExo2SeekRecoveryEnabled != null) exo2SeekRecoveryEnabled = newExo2SeekRecoveryEnabled;
        if (newMedia3SeekRecoveryDelayMs != null) media3SeekRecoveryDelayMs = validateLong(newMedia3SeekRecoveryDelayMs, 1000L, 120000L, "Media3 seek recovery delay");
        if (newExo2SeekRecoveryDelayMs != null) exo2SeekRecoveryDelayMs = validateLong(newExo2SeekRecoveryDelayMs, 1000L, 120000L, "Exo2 seek recovery delay");
        if (newMedia3SeekPolicy != null) media3SeekPolicy = validateSeekPolicy(newMedia3SeekPolicy);
        if (newExo2SeekPolicy != null) exo2SeekPolicy = validateSeekPolicy(newExo2SeekPolicy);
        if (newMedia3CodecMode != null) {
            media3CodecMode = validateCodecMode(newMedia3CodecMode);
            media3CodecModeOverridden = true;
        }
        if (newExo2CodecMode != null) {
            exo2CodecMode = validateCodecMode(newExo2CodecMode);
            exo2CodecModeOverridden = true;
        }
    }

    private static int valueOr(Integer value, int fallback) { return value == null ? fallback : value; }

    private static int validateMultiplier(int value)
    {
        if (!(value == 1 || value == 2 || value == 4 || value == 8 || value == 16))
            throw new IllegalArgumentException("TS search multiplier must be 1,2,4,8,16");
        return value;
    }

    private static int validateReadBytes(int value)
    {
        if (value < 64 * 1024 || value > 4 * 1024 * 1024 || value % 1024 != 0)
            throw new IllegalArgumentException("Pull read bytes must be 64 KiB..4096 KiB in KiB increments");
        return value;
    }

    private static void validateBufferDurations(String backend, int min, int max, int playback, int rebuffer)
    {
        if (min < 0 || max < min || max > 120000)
            throw new IllegalArgumentException(backend + " buffer min/max invalid");
        if (playback < 0 || playback > min)
            throw new IllegalArgumentException(backend + " playback buffer must be <= min buffer");
        if (rebuffer < 0 || rebuffer > min)
            throw new IllegalArgumentException(backend + " rebuffer must be <= min buffer");
    }

    private static long validateLong(long value, long min, long max, String label)
    {
        if (value < min || value > max) throw new IllegalArgumentException(label + " out of range");
        return value;
    }

    private static String validateSeekPolicy(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!("closest".equals(normalized) || "next".equals(normalized) || "previous".equals(normalized) || "directional".equals(normalized)))
            throw new IllegalArgumentException("seek policy must be closest,next,previous,directional");
        return normalized;
    }

    private static String validateCodecMode(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!("auto".equals(normalized) || "async".equals(normalized) || "sync".equals(normalized)))
            throw new IllegalArgumentException("codec mode must be auto,async,sync");
        return normalized;
    }

    /** Apply the persisted Media3 preference unless an MCP/debug override is active. */
    public static synchronized void applyMedia3CodecPreference(String value)
    {
        if (!media3CodecModeOverridden) media3CodecMode = validateCodecModeOrDefault(value);
    }

    /** Apply the persisted legacy ExoPlayer preference unless an MCP/debug override is active. */
    public static synchronized void applyExo2CodecPreference(String value)
    {
        if (!exo2CodecModeOverridden) exo2CodecMode = validateCodecModeOrDefault(value);
    }

    private static String validateCodecModeOrDefault(String value)
    {
        try { return validateCodecMode(value); }
        catch (IllegalArgumentException ignored) { return DEFAULT_CODEC_MODE; }
    }

    public static boolean isMedia3CodecModeOverridden() { return media3CodecModeOverridden; }
    public static boolean isExo2CodecModeOverridden() { return exo2CodecModeOverridden; }

    public static boolean isActive()
    {
        return media3TsSearchMultiplier != DEFAULT_TS_SEARCH_MULTIPLIER
                || exo2TsSearchMultiplier != DEFAULT_TS_SEARCH_MULTIPLIER
                || media3PullReadBytes != DEFAULT_MEDIA3_PULL_READ_BYTES
                || exo2PullReadBytes != DEFAULT_EXO2_PULL_READ_BYTES
                || media3PullMinBufferMs != DEFAULT_PULL_MIN_BUFFER_MS
                || media3PullMaxBufferMs != DEFAULT_PULL_MAX_BUFFER_MS
                || media3PullPlaybackBufferMs != DEFAULT_PULL_PLAYBACK_BUFFER_MS
                || media3PullRebufferMs != DEFAULT_PULL_REBUFFER_MS
                || exo2PullMinBufferMs != DEFAULT_PULL_MIN_BUFFER_MS
                || exo2PullMaxBufferMs != DEFAULT_PULL_MAX_BUFFER_MS
                || exo2PullPlaybackBufferMs != DEFAULT_PULL_PLAYBACK_BUFFER_MS
                || exo2PullRebufferMs != DEFAULT_PULL_REBUFFER_MS
                || directionalSyncMinDeltaMs != DEFAULT_DIRECTIONAL_SYNC_MIN_DELTA_MS
                || media3SeekRecoveryEnabled != DEFAULT_MEDIA3_SEEK_RECOVERY_ENABLED
                || exo2SeekRecoveryEnabled != DEFAULT_EXO2_SEEK_RECOVERY_ENABLED
                || media3SeekRecoveryDelayMs != DEFAULT_SEEK_RECOVERY_DELAY_MS
                || exo2SeekRecoveryDelayMs != DEFAULT_SEEK_RECOVERY_DELAY_MS
                || !DEFAULT_MEDIA3_SEEK_POLICY.equals(media3SeekPolicy)
                || !DEFAULT_EXO2_SEEK_POLICY.equals(exo2SeekPolicy)
                || media3CodecModeOverridden
                || exo2CodecModeOverridden
                || !DEFAULT_CODEC_MODE.equals(media3CodecMode)
                || !DEFAULT_CODEC_MODE.equals(exo2CodecMode);
    }

    public static int getMedia3TsSearchMultiplier() { return media3TsSearchMultiplier; }
    public static int getExo2TsSearchMultiplier() { return exo2TsSearchMultiplier; }
    public static int getMedia3PullReadBytes() { return media3PullReadBytes; }
    public static int getExo2PullReadBytes() { return exo2PullReadBytes; }
    public static int getMedia3PullMinBufferMs() { return media3PullMinBufferMs; }
    public static int getMedia3PullMaxBufferMs() { return media3PullMaxBufferMs; }
    public static int getMedia3PullPlaybackBufferMs() { return media3PullPlaybackBufferMs; }
    public static int getMedia3PullRebufferMs() { return media3PullRebufferMs; }
    public static int getExo2PullMinBufferMs() { return exo2PullMinBufferMs; }
    public static int getExo2PullMaxBufferMs() { return exo2PullMaxBufferMs; }
    public static int getExo2PullPlaybackBufferMs() { return exo2PullPlaybackBufferMs; }
    public static int getExo2PullRebufferMs() { return exo2PullRebufferMs; }
    public static long getDirectionalSyncMinDeltaMs() { return directionalSyncMinDeltaMs; }
    public static boolean isMedia3SeekRecoveryEnabled() { return media3SeekRecoveryEnabled; }
    public static boolean isExo2SeekRecoveryEnabled() { return exo2SeekRecoveryEnabled; }
    public static long getMedia3SeekRecoveryDelayMs() { return media3SeekRecoveryDelayMs; }
    public static long getExo2SeekRecoveryDelayMs() { return exo2SeekRecoveryDelayMs; }
    public static String getMedia3SeekPolicy() { return media3SeekPolicy; }
    public static String getExo2SeekPolicy() { return exo2SeekPolicy; }
    public static String getMedia3CodecMode() { return media3CodecMode; }
    public static String getExo2CodecMode() { return exo2CodecMode; }

    public static String compactWire()
    {
        return "tuningActive=" + isActive()
                + ";tuningMedia3TsSearchMultiplier=" + media3TsSearchMultiplier
                + ";tuningExo2TsSearchMultiplier=" + exo2TsSearchMultiplier
                + ";tuningMedia3PullReadKb=" + (media3PullReadBytes / 1024)
                + ";tuningExo2PullReadKb=" + (exo2PullReadBytes / 1024)
                + ";tuningMedia3MinBufferMs=" + media3PullMinBufferMs
                + ";tuningMedia3MaxBufferMs=" + media3PullMaxBufferMs
                + ";tuningMedia3PlaybackBufferMs=" + media3PullPlaybackBufferMs
                + ";tuningMedia3RebufferMs=" + media3PullRebufferMs
                + ";tuningExo2MinBufferMs=" + exo2PullMinBufferMs
                + ";tuningExo2MaxBufferMs=" + exo2PullMaxBufferMs
                + ";tuningExo2PlaybackBufferMs=" + exo2PullPlaybackBufferMs
                + ";tuningExo2RebufferMs=" + exo2PullRebufferMs
                + ";tuningDirectionalSyncMinDeltaMs=" + directionalSyncMinDeltaMs
                + ";tuningMedia3SeekRecoveryEnabled=" + media3SeekRecoveryEnabled
                + ";tuningExo2SeekRecoveryEnabled=" + exo2SeekRecoveryEnabled
                + ";tuningMedia3SeekRecoveryDelayMs=" + media3SeekRecoveryDelayMs
                + ";tuningExo2SeekRecoveryDelayMs=" + exo2SeekRecoveryDelayMs
                + ";tuningMedia3SeekPolicy=" + media3SeekPolicy
                + ";tuningExo2SeekPolicy=" + exo2SeekPolicy
                + ";tuningMedia3CodecMode=" + media3CodecMode
                + ";tuningExo2CodecMode=" + exo2CodecMode
                + ";tuningMedia3CodecModeOverride=" + media3CodecModeOverridden
                + ";tuningExo2CodecModeOverride=" + exo2CodecModeOverridden;
    }
}
