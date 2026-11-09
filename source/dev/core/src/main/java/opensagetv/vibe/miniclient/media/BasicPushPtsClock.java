package opensagetv.vibe.miniclient.media;

import opensagetv.vibe.miniclient.dvd.ProgramStreamPtsProbe;

/**
 * Basic MiniClient GETMEDIATIME is native MPEG PTS, not a decoder-relative
 * counter. Stock MiniPlayer subtracts its parser's first PTS and adds its
 * transcoder offset. Reconstitute that clock from REAL received PES metadata;
 * do not alter detailed-mode file anchors, payload bytes or DVD VM timing.
 */
public final class BasicPushPtsClock
{
    private static final long PTS_WRAP = 1L << 33;
    private final PushTimelineAnchorEstimator transport = new PushTimelineAnchorEstimator();
    private final ProgramStreamPtsProbe program = new ProgramStreamPtsProbe();
    private long origin90Khz = -1L;

    public synchronized void reset()
    {
        transport.reset();
        program.reset();
        origin90Khz = -1L;
    }

    public synchronized void observe(byte[] bytes, int offset, int length)
    {
        if (origin90Khz >= 0L) return;
        transport.observe(bytes, offset, length);
        long first = transport.getFirstPts90Khz();
        if (first >= 0L) origin90Khz = first;
        else
        {
            program.observe(bytes, offset, length);
            origin90Khz = program.firstPts90Khz();
        }
    }

    public synchronized long originMs() { return origin90Khz < 0 ? -1 : origin90Khz / 90L; }

    public synchronized long wirePositionMs(long relativeMs)
    {
        if (origin90Khz < 0 || relativeMs < 0) return relativeMs;
        // Modulo before multiplying keeps arbitrary long positions bounded.
        // The wire clock rolls at MPEG's33-bit PTS boundary, not signed32ms.
        return (origin90Khz + (relativeMs % PTS_WRAP) * 90L) % PTS_WRAP / 90L;
    }
}
