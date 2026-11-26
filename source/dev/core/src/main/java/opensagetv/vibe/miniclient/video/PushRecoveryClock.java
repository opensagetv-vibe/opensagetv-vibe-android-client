package opensagetv.vibe.miniclient.video;

/** Decoder epochs within one server-owned Push stream, never a file seek anchor. */
public final class PushRecoveryClock
{
    private long elapsedEpochMs;

    public synchronized void restartDecoder(long observedDecoderPositionMs)
    {
        elapsedEpochMs = add(elapsedEpochMs, Math.max(0L, observedDecoderPositionMs));
    }

    public synchronized long position(long decoderPositionMs)
    {
        return decoderPositionMs < 0 ? decoderPositionMs : add(elapsedEpochMs, decoderPositionMs);
    }

    public synchronized void reset() { elapsedEpochMs = 0L; }

    /** Translate the same-stream logical target, not a stock file offset. */
    public synchronized long decoderTarget(long streamPositionMs)
    {
        return streamPositionMs < elapsedEpochMs ? -1L : streamPositionMs - elapsedEpochMs;
    }

    private static long add(long left, long right)
    {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
