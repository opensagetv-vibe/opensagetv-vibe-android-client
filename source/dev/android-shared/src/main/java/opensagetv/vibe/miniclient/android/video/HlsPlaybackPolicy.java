package opensagetv.vibe.miniclient.android.video;

/** Only explicitly negotiated responsive HLS may shorten the start reserve;
 * only confirmed completed HLS may receive a near-full refill threshold.
 * Actual playlist/decoder readiness still gates playback. No live-edge,
 * timeline or user play/pause decision is synthesized here. */
public final class HlsPlaybackPolicy {
    private HlsPlaybackPolicy() { }

    /** Positive negotiated burst is issued only for independently confirmed
     * non-recording sources. The stock timeshifted/Fixed hint is not a writer. */
    public static boolean confirmedCompleted(boolean responsive, long startupBurstSeconds) {
        return responsive && startupBurstSeconds > 0;
    }

    /** Exo clocks are relative to the sliding playlist window. Only a current
     * owned HLS source may map that observed position into its media period;
     * the Direct controller separately owns the recording/seek epoch. */
    public static long periodPositionMs(boolean ownedHls, long windowOffsetMs, long positionMs) {
        if (!ownedHls || windowOffsetMs <= 0 || positionMs < 0) return positionMs;
        return windowOffsetMs > Long.MAX_VALUE-positionMs ? positionMs : windowOffsetMs+positionMs;
    }

    public static int startBufferMs(int configuredMs, boolean responsive) {
        return responsive ? Math.max(0, Math.min(250, configuredMs)) : configuredMs;
    }

    /** Refill negotiated HLS near its existing ceiling rather than draining to
     * the ordinary Pull low-water mark. Does not increase storage, startup or
     * rebuffer requirements; unavailable live segments still cannot be fetched. */
    public static int refillBufferMs(int configuredMinMs, int configuredMaxMs,
            boolean responsive) {
        if (!responsive || configuredMaxMs <= 0) return configuredMinMs;
        int nearFullMs = (int) (configuredMaxMs * 4L / 5L);
        return Math.min(configuredMaxMs, Math.max(configuredMinMs, nearFullMs));
    }
}
