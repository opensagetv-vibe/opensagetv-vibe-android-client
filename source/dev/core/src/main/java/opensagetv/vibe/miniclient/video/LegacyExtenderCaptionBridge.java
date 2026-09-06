package opensagetv.vibe.miniclient.video;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts extractor CEA cc_data triplets into the raw eight-byte records used
 * by SageTV's legacy extender subtitle callback (event 225).
 *
 * <p>This class deliberately forwards encoded caption packets, not rendered
 * text.  SageTV therefore remains responsible for CC1/CC2/DTVCC service
 * selection and for drawing the captions with the STV.</p>
 */
public final class LegacyExtenderCaptionBridge
{
    public static final int PTS_VALID = 0x01;
    public static final int FLUSH_SUBTITLE_QUEUE = 0x02;
    public static final int CC_SUBTITLE = 0x10;

    public interface Sink
    {
        void postSubtitleInfo(long pts45Khz, long duration45Khz, byte[] data, int flags);
    }

    private final Sink sink;
    private final ArrayDeque<PendingCaption> pending = new ArrayDeque<PendingCaption>();
    private final ArrayDeque<RecentSample> recentSamples = new ArrayDeque<RecentSample>();
    private static final int MAX_RECENT_SAMPLES = 256;
    private int forwardedSamples;
    private long forwardedBytes;
    private boolean forwardingCurrentStream;

    public LegacyExtenderCaptionBridge(Sink sink)
    {
        if (sink == null)
            throw new IllegalArgumentException("sink must not be null");
        this.sink = sink;
    }

    /**
     * Accept a CEA sample emitted by a TS extractor. Each unit is
     * {@code cc_valid/cc_type, cc_data_1, cc_data_2}.
     */
    public synchronized boolean onCeaSample(long timeUs, byte[] sample)
    {
        if (sample == null || sample.length < 3)
            return false;

        int payloadHash = Arrays.hashCode(sample);
        // CeaUtil publishes the same raw cc_data sample to the declared 608 and
        // 708 tracks. Extractor callbacks can interleave after a seek, so a
        // last-sample-only check is insufficient (A, B, duplicate A,
        // duplicate B). Keep a bounded exact history instead.
        for (RecentSample recent : recentSamples)
            if (recent.timeUs == timeUs && recent.payloadHash == payloadHash
                    && Arrays.equals(recent.payload, sample))
                return false;

        ByteArrayOutputStream records = new ByteArrayOutputStream((sample.length / 3) * 8);
        int pts45Khz = toPts45Khz(timeUs);
        for (int offset = 0; offset + 2 < sample.length; offset += 3)
        {
            int header = sample[offset] & 0xFF;
            if ((header & 0x04) == 0)
                continue;
            int type = header & 0x03;
            records.write((pts45Khz >>> 24) & 0xFF);
            records.write((pts45Khz >>> 16) & 0xFF);
            records.write((pts45Khz >>> 8) & 0xFF);
            records.write(pts45Khz & 0xFF);
            records.write(type);
            records.write(sample[offset + 1] & 0xFF);
            records.write(sample[offset + 2] & 0xFF);
            // The legacy 708 parser uses bit zero as cc_valid.  It is harmless
            // for 608 and preserves the native extender record contract.
            records.write(1);
        }

        byte[] payload = records.toByteArray();
        if (payload.length == 0)
            return false;

        recentSamples.addLast(new RecentSample(timeUs, payloadHash,
                Arrays.copyOf(sample, sample.length)));
        while (recentSamples.size() > MAX_RECENT_SAMPLES)
            recentSamples.removeFirst();
        forwardingCurrentStream = true;
        enqueueInPresentationOrder(new PendingCaption(timeUs, pts45Khz, payload));
        return true;
    }

    /** MPEG-2 B pictures may be extracted in decode order rather than PTS order. */
    private void enqueueInPresentationOrder(PendingCaption caption)
    {
        if (pending.isEmpty() || pending.peekLast().timeUs <= caption.timeUs)
        {
            pending.addLast(caption);
            return;
        }

        ArrayDeque<PendingCaption> ordered = new ArrayDeque<PendingCaption>(pending.size() + 1);
        boolean inserted = false;
        while (!pending.isEmpty())
        {
            PendingCaption existing = pending.removeFirst();
            if (!inserted && caption.timeUs < existing.timeUs)
            {
                ordered.addLast(caption);
                inserted = true;
            }
            ordered.addLast(existing);
        }
        if (!inserted)
            ordered.addLast(caption);
        pending.addAll(ordered);
    }

    /**
     * Forward only captions whose presentation time has reached the rendered
     * player clock. SageTV's legacy CC handler consumes callback records in
     * real time; sending extractor samples as soon as they are read makes CC
     * run ahead by the entire Push/read-ahead buffer.
     */
    public void drainTo(long playbackTimeUs)
    {
        List<PendingCaption> due = new ArrayList<PendingCaption>();
        synchronized (this)
        {
            while (!pending.isEmpty() && pending.peekFirst().timeUs <= playbackTimeUs)
            {
                PendingCaption caption = pending.removeFirst();
                due.add(caption);
                forwardedSamples++;
                forwardedBytes += caption.payload.length;
            }
        }
        for (PendingCaption caption : due)
            sink.postSubtitleInfo(caption.pts45Khz, 0, caption.payload,
                    CC_SUBTITLE | PTS_VALID);
    }

    public synchronized void flush()
    {
        clearPending();
        postFlush();
    }

    /**
     * Discard extractor read-ahead synchronously without performing network
     * I/O. Players use this before a seek, then enqueue {@link #postFlush()}
     * on the MiniClient background executor. This prevents pre-seek caption
     * packets from flooding SageTV when the playback clock jumps forward, or
     * blocking new packets behind a future timestamp after a backward seek.
     */
    public synchronized void clearPending()
    {
        pending.clear();
        recentSamples.clear();
        forwardingCurrentStream = false;
    }

    /** Send the legacy caption-screen reset after {@link #clearPending()}. */
    public void postFlush()
    {
        // Stock SageTV clears the visible ZCCLabel for FLUSH, but its
        // CCSubtitleHandler intentionally retains the 608 decoder's display,
        // memory, mode and cursor. After a random-access seek that retained
        // state can splice new characters into old rows or briefly move text
        // to a stale PAC location. Include standard, duplicated EDM and ENM
        // controls for both channels/fields. They erase decoder memory without
        // selecting roll-up/pop-on/paint-on or imposing a row; the source's
        // next real mode and PAC controls remain authoritative.
        sink.postSubtitleInfo(0, 0, buildCea608ResetRecords(),
                CC_SUBTITLE | FLUSH_SUBTITLE_QUEUE);
    }

    static byte[] buildCea608ResetRecords()
    {
        ByteArrayOutputStream records = new ByteArrayOutputStream(128);
        for (int type = 0; type <= 1; type++)
        {
            for (int channel = 0; channel <= 1; channel++)
            {
                int first = 0x14 | (type == 1 ? 0x01 : 0x00)
                        | (channel == 1 ? 0x08 : 0x00);
                writeDuplicated608Control(records, type, first, 0x2C); // EDM
                writeDuplicated608Control(records, type, first, 0x2E); // ENM
            }
        }
        return records.toByteArray();
    }

    private static void writeDuplicated608Control(ByteArrayOutputStream records,
            int type, int first, int second)
    {
        for (int duplicate = 0; duplicate < 2; duplicate++)
        {
            records.write(0);
            records.write(0);
            records.write(0);
            records.write(0);
            records.write(type);
            records.write(withOddParity(first));
            records.write(withOddParity(second));
            records.write(1);
        }
    }

    private static int withOddParity(int value)
    {
        int sevenBits = value & 0x7F;
        return (Integer.bitCount(sevenBits) & 1) == 0
                ? sevenBits | 0x80 : sevenBits;
    }

    public synchronized boolean isForwardingCurrentStream()
    {
        return forwardingCurrentStream;
    }

    public synchronized int getForwardedSamples()
    {
        return forwardedSamples;
    }

    public synchronized long getForwardedBytes()
    {
        return forwardedBytes;
    }

    public synchronized int getPendingSamples()
    {
        return pending.size();
    }

    private static final class PendingCaption
    {
        final long timeUs;
        final int pts45Khz;
        final byte[] payload;

        PendingCaption(long timeUs, int pts45Khz, byte[] payload)
        {
            this.timeUs = timeUs;
            this.pts45Khz = pts45Khz;
            this.payload = payload;
        }
    }

    private static final class RecentSample
    {
        final long timeUs;
        final int payloadHash;
        final byte[] payload;

        RecentSample(long timeUs, int payloadHash, byte[] payload)
        {
            this.timeUs = timeUs;
            this.payloadHash = payloadHash;
            this.payload = payload;
        }
    }

    private static int toPts45Khz(long timeUs)
    {
        if (timeUs <= 0)
            return 0;
        return (int) ((timeUs * 45L) / 1000L);
    }
}
