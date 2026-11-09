package opensagetv.vibe.miniclient.media;

/**
 * Recognizes a stock MiniPlayer fast-load byte-epoch change without OPENURL.
 *
 * <p>MiniPlayer.fastLoad replaces its reader without sending FLUSH. Its detailed
 * PUSHBUFFER mux clock then restarts in the new file's time domain. Continuing
 * to add a previous file's decoder position/anchor makes SageTV clamp the
 * oversized clock to the new recording's live edge. A substantial backwards
 * mux jump during normal playback therefore needs a LOCAL decoder/anchor reset,
 * not a guessed server Seek(0), reconnect or bookmark override.</p>
 *
 * <p>Only data-bearing detailed-stat packets in ordinary Push are eligible.
 * Explicit OPENURL/FLUSH resets this observer, so a real backwards seek does
 * not cause a second flush. Empty/EOS polls, unknown clocks, DVD and trick
 * playback cannot establish a handoff. Small mux/DTS reordering is tolerated.
 * This intentionally does not infer a forward jump as a file boundary: that
 * could be legitimate sparse timestamps in the same stream.</p>
 */
public final class PushStreamHandoff
{
    static final long BACKWARD_JUMP_MS = 10_000L;
    private long previousMuxMs = -1L;
    private boolean baselineEstablished;

    public void reset()
    {
        previousMuxMs = -1L;
        baselineEstablished = false;
    }

    public long getPreviousMuxMs()
    {
        return previousMuxMs;
    }

    public boolean observe(boolean ordinaryPush, int payloadBytes,
                           boolean detailedStats, long muxMs, int flags)
    {
        if (!ordinaryPush)
        {
            reset();
            return false;
        }
        // Stock getFlags(): bit0 discards P/B frames, bit3 discards PTS and
        // the high word carries trick rate; normal first-push flags are0x12.
        if ((flags & 0x09) != 0 || (flags & 0xFFFF0000) != 0)
        {
            reset();
            return false;
        }
        if (!detailedStats || payloadBytes <= 0 || muxMs < 0L
                || (flags & 0x180) != 0)
            return false;
        // Stock pushBuffer0 builds its header BEFORE transferring/parsing that
        // packet. The first header after an explicit seek can therefore still
        // describe the old reader position; the second describes new bytes.
        // Do not compare that initial pair or flush the user's seek twice.
        // After two data-bearing packets the established stream can be checked
        // for a later unannounced fast-load transition. No time-based polling
        // or decoder-READY assumption is needed to establish this boundary.
        boolean handoff = baselineEstablished && previousMuxMs >= 0L
                && previousMuxMs - muxMs >= BACKWARD_JUMP_MS;
        if (previousMuxMs >= 0L)
            baselineEstablished = true;
        previousMuxMs = muxMs;
        return handoff;
    }
}
