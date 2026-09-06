package opensagetv.vibe.miniclient.video;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

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
    private long lastTimeUs = Long.MIN_VALUE;
    private int lastPayloadHash;
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
        // 708 tracks. Forward it once so the SageTV decoder does not see every
        // control code twice.
        if (timeUs == lastTimeUs && payloadHash == lastPayloadHash)
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

        lastTimeUs = timeUs;
        lastPayloadHash = payloadHash;
        forwardedSamples++;
        forwardedBytes += payload.length;
        forwardingCurrentStream = true;
        sink.postSubtitleInfo(pts45Khz, 0, payload, CC_SUBTITLE | PTS_VALID);
        return true;
    }

    public synchronized void flush()
    {
        lastTimeUs = Long.MIN_VALUE;
        lastPayloadHash = 0;
        forwardingCurrentStream = false;
        sink.postSubtitleInfo(0, 0, new byte[0], CC_SUBTITLE | FLUSH_SUBTITLE_QUEUE);
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

    private static int toPts45Khz(long timeUs)
    {
        if (timeUs <= 0)
            return 0;
        return (int) ((timeUs * 45L) / 1000L);
    }
}
