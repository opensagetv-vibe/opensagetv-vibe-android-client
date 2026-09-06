package opensagetv.vibe.miniclient.android.video.media3;

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;

import java.util.List;
import java.util.Map;

import opensagetv.vibe.miniclient.android.video.DvdSubpictureDecoder;

/**
 * Supplies the DVD-aware MPEG program-stream extractor for SageTV DVD push sessions.
 *
 * <p>Some valid VOB streams contain an invalid 0x0B77 candidate between AC-3 frames. Media3
 * 1.11's AC-3 reader can throw an {@link ArrayIndexOutOfBoundsException} before it validates the
 * six-bit frame-size code. DVD private-stream AC-3 also requires substream separation and
 * first-access-unit alignment that Media3's generic PS extractor does not provide. This factory
 * is installed only after the SageTV protocol has positively selected a DVD push session, so the
 * input is unambiguously MPEG-PS and no generic extractor probing is required.</p>
 */
@UnstableApi
final class ResilientDvdPsExtractorsFactory implements ExtractorsFactory
{
    @SuppressWarnings("unused")
    private final ExtractorsFactory delegate;
    // A Media3 source replacement briefly overlaps the outgoing and incoming
    // extractor. Never let the outgoing extractor consume the incoming
    // epoch's one-shot timestamp rebase; publish a fresh state object for each
    // replacement generation instead.
    private volatile DvdPsExtractor.TimestampState timestampState =
            new DvdPsExtractor.TimestampState();
    private final DvdSubpictureDecoder subpictureDecoder;
    private final boolean repairMpeg2PictureTimestamps;

    ResilientDvdPsExtractorsFactory(ExtractorsFactory delegate,
            DvdSubpictureDecoder subpictureDecoder, boolean repairMpeg2PictureTimestamps)
    {
        this.delegate = delegate;
        this.subpictureDecoder = subpictureDecoder;
        this.repairMpeg2PictureTimestamps = repairMpeg2PictureTimestamps;
    }

    @Override
    public Extractor[] createExtractors()
    {
        DvdPsExtractor.TimestampState state = timestampState;
        return new Extractor[] { new DvdPsExtractor(state, subpictureDecoder,
                repairMpeg2PictureTimestamps) };
    }

    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders)
    {
        DvdPsExtractor.TimestampState state = timestampState;
        return new Extractor[] { new DvdPsExtractor(state, subpictureDecoder,
                repairMpeg2PictureTimestamps) };
    }

    void setPtsOffset90Khz(long ptsOffset90Khz)
    {
        timestampState.setPtsOffset90Khz(ptsOffset90Khz);
    }

    void beginFlushEpoch(long ptsOffset90Khz, long playbackAnchorUs)
    {
        timestampState.beginFlushEpoch(ptsOffset90Khz, playbackAnchorUs);
    }

    synchronized void beginRepreparedEpoch(long ptsOffset90Khz)
    {
        DvdPsExtractor.TimestampState nextState = new DvdPsExtractor.TimestampState();
        nextState.beginRepreparedEpoch(ptsOffset90Khz);
        timestampState = nextState;
    }

    void queuePtsOffset90Khz(long bytePosition, long ptsOffset90Khz)
    {
        timestampState.queuePtsOffset90Khz(bytePosition, ptsOffset90Khz);
    }

    long getLatestVideoSampleUs() { return timestampState.getLatestVideoSampleUs(); }
    long getLatestAudioSampleUs() { return timestampState.getLatestAudioSampleUs(); }
    long getVideoCorrectionCount() { return timestampState.getVideoCorrectionCount(); }
    long getDiscontinuityRebaseCount() { return timestampState.getDiscontinuityRebaseCount(); }
    long getLatestVideoRawPts90Khz() { return timestampState.getLatestVideoRawPts90Khz(); }
    long getLatestVideoOffsetPts90Khz() { return timestampState.getLatestVideoOffsetPts90Khz(); }
    long getLatestVideoPesUs() { return timestampState.getLatestVideoPesUs(); }
    long getLatestAudioRawPts90Khz() { return timestampState.getLatestAudioRawPts90Khz(); }
    long getLatestAudioOffsetPts90Khz() { return timestampState.getLatestAudioOffsetPts90Khz(); }
    long getLatestAudioPesUs() { return timestampState.getLatestAudioPesUs(); }
    String describeVideoTimestampNear(long outputTimeUs)
    {
        return timestampState.describeVideoTimestampNear(outputTimeUs);
    }
    double getMpeg2ReportedFrameRateHz() { return timestampState.getMpeg2ReportedFrameRateHz(); }
    double getMpeg2SequenceFrameRateHz() { return timestampState.getMpeg2SequenceFrameRateHz(); }
    double getMpeg2EffectiveFieldDurationUs()
    {
        return timestampState.getMpeg2EffectiveFieldDurationUs();
    }
    boolean isMpeg2TelecineCadenceSeen()
    {
        return timestampState.isMpeg2TelecineCadenceSeen();
    }

    String getMpeg2InterlaceObservation() { return timestampState.getMpeg2InterlaceObservation(); }
    boolean isMpeg2SequenceExtensionSeen() { return timestampState.isMpeg2SequenceExtensionSeen(); }
    long getMpeg2ProgressiveFrameCount() { return timestampState.getMpeg2ProgressiveFrameCount(); }
    long getMpeg2InterlacedFrameCount() { return timestampState.getMpeg2InterlacedFrameCount(); }
    long getMpeg2FieldPictureCount() { return timestampState.getMpeg2FieldPictureCount(); }
    boolean isMpeg2PictureTimestampRepairEnabled() { return repairMpeg2PictureTimestamps; }

}
