package opensagetv.vibe.miniclient.video;

/** Negotiation for command29 seeking inside a decoder's existing Push cache. */
public final class PushBufferSeekPolicy
{
    // Local debug receiver only; never persisted or advertised as a capability.
    private static volatile boolean debugForwardCacheTrial;
    public static void setDebugForwardCacheTrial(boolean enabled) { debugForwardCacheTrial = enabled; }
    public static boolean isDebugForwardCacheTrial() { return debugForwardCacheTrial; }
    private PushBufferSeekPolicy() {}

    /**
     * This is not byte-ring capacity or ordinary server-owned FLUSH seeking.
     * IJK deliberately ignores it; Android System has no qualified Push cache.
     * Exo accepts the command, but a stock TS Push timeline is unseekable even
     * when samples are buffered: physical EXT-016 probe03 sought53572->53605ms
     * and the period immediately adjusted it to0. Thus method presence is not
     * a valid capability test. The separate pinned Media3/legacy Exo paths handle
     * forward selected-queue targets only, not the broader random-access or
     * raw-PTS decode-to contract. Keep this property conservative; stock Core's
     * transcoder branch can independently send its forward-only command29.
     *
     * Stock Core currently stores this flag, but its Fixed in-buffer branch
     * does not consult it. Consequently truthful negotiation alone cannot be
     * claimed to repair all Fixed seeks on IJK; that remains a separate gate.
     */
    public static boolean shouldAdvertise(String backend, String gsyEngine)
    {
        return false;
    }

    /** Never dispatch an ordinary live-FIFO seek into an unseekable decoder. */
    public static boolean canHandleServerSeek(boolean actualPush, boolean dvd)
    {
        return !actualPush || dvd;
    }

    /** Qualified extractor backends only; basic wire mode keeps producer seeking. */
    public static boolean useForwardCacheSource(boolean detailedStats)
    {
        return detailedStats || debugForwardCacheTrial;
    }

    /**
     * Stock MiniPlayer.seek() also has a transcoder-only cache-seek branch
     * gated by DETAILED_BUFFER_STATS, not PUSH_BUFFER_SEEKING. A decoder which
     * cannot seek the live FIFO must not opt into that branch. Use the existing
     * basic wire protocol ONLY when our negotiation guarantees transcoding:
     * Fixed/Always advertises no source video/audio/container support, remux is
     * explicitly off, and MIM Direct is not owning the source. Core then owns
     * every seek/FLUSH and its timestampOffset; decoder time starts at zero.
     *
     * Ordinary Push needs detailed timestamps to anchor original source PTS.
     * Fixed/When Needed, copy/remux, Direct and unknown negotiations must
     * not be inferred to have normalized timestamps. Retain their established
     * wire contract. MiniDVDPlayer already uses basic replies regardless of
     * this ordinary-player property. This is conservative fallback, not native
     * cache seeking.
     */
    public static boolean useDetailedBufferStats(String mode, String encoding,
            String remux, boolean directActive)
    {
        return debugForwardCacheTrial || directActive || !"fixed".equalsIgnoreCase(mode)
                || !"always".equalsIgnoreCase(encoding)
                || !"off".equalsIgnoreCase(remux);
    }
}
