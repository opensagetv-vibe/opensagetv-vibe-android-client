package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.prefs.PrefStore;

/**
 * Device-process-local playback overrides selected from the active-player UI.
 *
 * <p>These values deliberately do not travel in exported/imported profiles.
 * A saved default is written to {@link PrefStore}; a session choice lives here
 * until Reset is selected or the application process ends.</p>
 */
public final class ActivePlayerSessionOverrides
{
    public static final String BUFFER_LOW_LATENCY = "low_latency";
    public static final String BUFFER_BALANCED = "balanced";
    public static final String BUFFER_RESILIENT = "resilient";

    private static volatile String backend;
    private static volatile String decodingMethod;
    private static volatile String codecMode;
    private static volatile String dvdTimestampRepair;
    private static volatile String bufferPreset;
    private static volatile Integer subtitleOffsetMs;
    private static volatile Integer subtitleSafeAreaPercent;
    private static volatile Integer subtitleTextScalePercent;
    private static volatile String subtitleTextStyle;
    private static volatile Integer audioOffsetMs;
    private static volatile String refreshRatePolicy;
    private static volatile Integer refreshSettleMs;

    private ActivePlayerSessionOverrides() { }

    public static synchronized void reset()
    {
        backend = null;
        decodingMethod = null;
        codecMode = null;
        dvdTimestampRepair = null;
        bufferPreset = null;
        subtitleOffsetMs = null;
        subtitleSafeAreaPercent = null;
        subtitleTextScalePercent = null;
        subtitleTextStyle = null;
        audioOffsetMs = null;
        refreshRatePolicy = null;
        refreshSettleMs = null;
        PlayerRuntimeTuning.reset();
    }

    public static String getBackend() { return backend; }
    public static void setBackend(String value) { backend = normalize(value); }
    public static String getDecodingMethod() { return decodingMethod; }
    public static void setDecodingMethod(String value) { decodingMethod = normalize(value); }
    public static String getCodecMode() { return codecMode; }
    public static void setCodecMode(String value) { codecMode = normalize(value); }
    public static String getDvdTimestampRepair() { return dvdTimestampRepair; }
    public static void setDvdTimestampRepair(String value) { dvdTimestampRepair = normalize(value); }
    public static String getBufferPreset() { return bufferPreset; }
    public static void setBufferPreset(String value) { bufferPreset = normalize(value); }
    public static Integer getSubtitleOffsetMs() { return subtitleOffsetMs; }
    public static void setSubtitleOffsetMs(int value)
    { subtitleOffsetMs = Math.max(-2_000, Math.min(2_000, value)); }
    public static Integer getSubtitleSafeAreaPercent() { return subtitleSafeAreaPercent; }
    public static void setSubtitleSafeAreaPercent(int value)
    { subtitleSafeAreaPercent = Math.max(5, Math.min(35, value)); }
    public static Integer getSubtitleTextScalePercent() { return subtitleTextScalePercent; }
    public static void setSubtitleTextScalePercent(int value)
    { subtitleTextScalePercent = Math.max(75, Math.min(150, value)); }
    public static String getSubtitleTextStyle() { return subtitleTextStyle; }
    public static void setSubtitleTextStyle(String value)
    { subtitleTextStyle = normalizeSubtitleStyle(value); }
    public static Integer getAudioOffsetMs() { return audioOffsetMs; }
    public static void setAudioOffsetMs(int value)
    { audioOffsetMs = Math.max(-2_000, Math.min(2_000, value)); }
    public static String getRefreshRatePolicy() { return refreshRatePolicy; }
    public static void setRefreshRatePolicy(String value) { refreshRatePolicy = normalize(value); }
    public static String resolveRefreshRatePolicy(String persisted)
    { return refreshRatePolicy == null ? persisted : refreshRatePolicy; }
    public static Integer getRefreshSettleMs() { return refreshSettleMs; }
    public static void setRefreshSettleMs(int value)
    { refreshSettleMs = Math.max(0, Math.min(1_500, value)); }
    public static int resolveRefreshSettleMs(int persisted)
    { return refreshSettleMs == null ? Math.max(0, Math.min(1_500, persisted)) : refreshSettleMs; }

    public static String resolveBackend(String persisted)
    {
        return backend == null ? persisted : backend;
    }

    public static String resolveDecodingMethod(String persisted)
    {
        return decodingMethod == null ? persisted : decodingMethod;
    }

    public static String resolveDvdTimestampRepair(String persisted)
    {
        return dvdTimestampRepair == null ? persisted : dvdTimestampRepair;
    }

    /** Apply bounded preset/codec choices immediately before player capture. */
    public static synchronized void applyRuntimeTuning(PrefStore prefs,
            PlayerRuntimeConfig.Backend playerBackend)
    {
        boolean media3 = playerBackend == PlayerRuntimeConfig.Backend.MEDIA3;
        String preset = bufferPreset;
        if (preset == null && prefs != null && prefs.contains(PrefStore.Keys.playback_buffer_preset))
            preset = prefs.getString(PrefStore.Keys.playback_buffer_preset, BUFFER_BALANCED);
        // Only apply a process-local queueing override here. Persisted player
        // preferences are applied by each backend after this call, where the
        // runtime-tuning layer can correctly preserve an active MCP/debug
        // experiment instead of this helper accidentally overwriting it.
        String queueing = codecMode;

        Integer readBytes = null;
        Integer minMs = null;
        Integer maxMs = null;
        Integer playMs = null;
        Integer rebufferMs = null;
        if (BUFFER_LOW_LATENCY.equals(preset))
        {
            readBytes = 128 * 1024;
            minMs = 2000;
            maxMs = 8000;
            playMs = 250;
            rebufferMs = 500;
        }
        else if (BUFFER_RESILIENT.equals(preset))
        {
            readBytes = 512 * 1024;
            minMs = 10000;
            maxMs = 60000;
            playMs = 1500;
            rebufferMs = 3000;
        }
        else if (preset != null)
        {
            readBytes = media3 ? PlayerRuntimeTuning.DEFAULT_MEDIA3_PULL_READ_BYTES
                    : PlayerRuntimeTuning.DEFAULT_EXO2_PULL_READ_BYTES;
            minMs = PlayerRuntimeTuning.DEFAULT_PULL_MIN_BUFFER_MS;
            maxMs = PlayerRuntimeTuning.DEFAULT_PULL_MAX_BUFFER_MS;
            playMs = PlayerRuntimeTuning.DEFAULT_PULL_PLAYBACK_BUFFER_MS;
            rebufferMs = PlayerRuntimeTuning.DEFAULT_PULL_REBUFFER_MS;
        }

        PlayerRuntimeTuning.configure(
                null, null,
                media3 ? readBytes : null, media3 ? null : readBytes,
                media3 ? minMs : null, media3 ? maxMs : null,
                media3 ? playMs : null, media3 ? rebufferMs : null,
                media3 ? null : minMs, media3 ? null : maxMs,
                media3 ? null : playMs, media3 ? null : rebufferMs,
                null, null, null, null, null, null, null,
                media3 ? queueing : null, media3 ? null : queueing);
    }

    public static boolean isActive()
    {
        return backend != null || decodingMethod != null || codecMode != null
                || dvdTimestampRepair != null || bufferPreset != null
                || subtitleOffsetMs != null || subtitleSafeAreaPercent != null
                || subtitleTextScalePercent != null || subtitleTextStyle != null
                || audioOffsetMs != null
                || refreshRatePolicy != null || refreshSettleMs != null;
    }

    public static String compactSummary()
    {
        return "backend=" + value(backend) + ", decode=" + value(decodingMethod)
                + ", queue=" + value(codecMode) + ", dvdPts="
                + value(dvdTimestampRepair) + ", buffer=" + value(bufferPreset)
                + ", subtitleOffsetMs=" + value(subtitleOffsetMs)
                + ", subtitleSafeArea=" + value(subtitleSafeAreaPercent)
                + ", subtitleScale=" + value(subtitleTextScalePercent)
                + ", subtitleStyle=" + value(subtitleTextStyle)
                + ", audioOffsetMs=" + value(audioOffsetMs)
                + ", refresh=" + value(refreshRatePolicy)
                + ", refreshSettleMs=" + value(refreshSettleMs);
    }

    private static String normalize(String value)
    {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase();
        return normalized.length() == 0 ? null : normalized;
    }

    private static String normalizeSubtitleStyle(String value)
    {
        String normalized = normalize(value);
        if ("outline".equals(normalized) || "black_box".equals(normalized))
            return normalized;
        return "system";
    }

    private static String value(String value)
    {
        return value == null ? "default" : value;
    }

    private static String value(Integer value)
    {
        return value == null ? "default" : Integer.toString(value);
    }
}
