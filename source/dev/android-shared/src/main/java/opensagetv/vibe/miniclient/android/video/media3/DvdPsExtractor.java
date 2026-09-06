package opensagetv.vibe.miniclient.android.video.media3;

import android.util.SparseArray;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.ElementaryStreamReader;
import androidx.media3.extractor.ts.H262Reader;
import androidx.media3.extractor.ts.MpegAudioReader;
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;

import opensagetv.vibe.miniclient.android.video.DvdSubpictureDecoder;
import opensagetv.vibe.miniclient.video.Mpeg2PictureTimestampCompleter;

/** MPEG-2 program-stream extractor specialized for SageTV's non-seekable DVD push stream. */
@UnstableApi
final class DvdPsExtractor implements Extractor
{
    private static final int PACK_START_CODE = 0x000001BA;
    private static final int SYSTEM_HEADER_START_CODE = 0x000001BB;
    private static final int PROGRAM_END_CODE = 0x000001B9;
    private static final int START_CODE_PREFIX = 0x000001;
    private static final int PRIVATE_STREAM_1 = 0xBD;
    private static final int AUDIO_STREAM = 0xC0;
    private static final int AUDIO_STREAM_MASK = 0xE0;
    private static final int VIDEO_STREAM = 0xE0;
    private static final int VIDEO_STREAM_MASK = 0xF0;
    private static final int PES_DATA_ALIGNMENT_INDICATOR = 4;
    private static final int MAX_STREAM_ID_PLUS_ONE = 0x100;
    private static final long PTS_MASK = 0x1FFFFFFFFL;
    private static final String TAG = "VibeDvdPsExtractor";
    // DVD navigation VOB cells can be tiny and audio-only. Waiting for the
    // generic 1 MiB PS discovery window means output.endTracks() is never
    // called for a 180 KiB transition cell, so Media3 buffers forever and the
    // server-side DVD VM cannot advance. Normal VOB audio/video packs are
    // interleaved well inside 64 KiB; close discovery there when both tracks
    // have not appeared.
    private static final long MAX_AUDIO_ONLY_SEARCH_LENGTH = 64 * 1024;
    // A still-menu VOB can carry one large I-picture before its first AC-3
    // packet. The commissioned fixture starts audio at byte 110,606, so the
    // audio-only cutoff must not be applied when video was discovered first.
    private static final long MAX_VIDEO_ONLY_SEARCH_LENGTH = 256 * 1024;
    private static final long MAX_SEARCH_AFTER_AV = 8 * 1024;

    private final TimestampAdjuster timestampAdjuster = new TimestampAdjuster(0);
    private final TimestampState timestampState;
    private final DvdSubpictureDecoder subpictureDecoder;
    private final boolean repairMpeg2PictureTimestamps;
    private final StillFrameRepeater stillFrameRepeater = new StillFrameRepeater();
    private final SparseArray<PacketReader> readers = new SparseArray<>();
    private final ParsableByteArray packet = new ParsableByteArray(4096);
    @Nullable private ExtractorOutput output;
    private boolean foundAudio;
    private boolean foundVideo;
    private boolean foundAllTracks;
    private boolean seekMapSent;
    private long lastTrackPosition;
    private int discoveredPrivateAudioTrackCount;

    DvdPsExtractor()
    {
        this(new TimestampState(), null, false);
    }

    DvdPsExtractor(TimestampState timestampState, @Nullable DvdSubpictureDecoder subpictureDecoder)
    {
        this(timestampState, subpictureDecoder, false);
    }

    DvdPsExtractor(TimestampState timestampState, @Nullable DvdSubpictureDecoder subpictureDecoder,
            boolean repairMpeg2PictureTimestamps)
    {
        this.timestampState = timestampState;
        this.subpictureDecoder = subpictureDecoder;
        this.repairMpeg2PictureTimestamps = repairMpeg2PictureTimestamps;
    }

    static final class TimestampState
    {
        private static final long MAX_CONTINUOUS_AV_DELTA_US = 30_000_000L;
        private volatile long ptsOffset90Khz;
        private volatile long cellGeneration;
        private volatile long latestVideoSampleUs = C.TIME_UNSET;
        private volatile long latestAudioSampleUs = C.TIME_UNSET;
        private volatile long videoCorrectionCount;
        private volatile long discontinuityRebaseCount;
        private volatile long latestVideoRawPts90Khz = C.TIME_UNSET;
        private volatile long latestVideoOffsetPts90Khz = C.TIME_UNSET;
        private volatile long latestVideoPesUs = C.TIME_UNSET;
        private volatile long latestAudioRawPts90Khz = C.TIME_UNSET;
        private volatile long latestAudioOffsetPts90Khz = C.TIME_UNSET;
        private volatile long latestAudioPesUs = C.TIME_UNSET;
        private volatile double mpeg2ReportedFrameRateHz;
        private volatile double mpeg2SequenceFrameRateHz;
        private volatile double mpeg2EffectiveFieldDurationUs;
        private volatile boolean mpeg2TelecineCadenceSeen;
        private volatile String mpeg2InterlaceObservation = "unknown";
        private volatile boolean mpeg2SequenceExtensionSeen;
        private volatile long mpeg2ProgressiveFrameCount;
        private volatile long mpeg2InterlacedFrameCount;
        private volatile long mpeg2FieldPictureCount;
        private long presentationShiftUs;
        private long pendingCellAnchorUs = C.TIME_UNSET;
        private boolean pendingCellNormalization;
        private long inputEpochBasePosition;
        private boolean pendingInputEpochBase;
        private long flushPlaybackAnchorUs = C.TIME_UNSET;
        private final ArrayDeque<PtsBoundary> pendingPtsBoundaries = new ArrayDeque<>();

        void noteMpeg2Cadence(Mpeg2PictureTimestampCompleter completer)
        {
            mpeg2ReportedFrameRateHz = completer.getReportedFrameRateHz();
            mpeg2SequenceFrameRateHz = completer.getSequenceFrameRateHz();
            mpeg2EffectiveFieldDurationUs = completer.getEffectiveFieldDurationUs();
            mpeg2TelecineCadenceSeen = completer.isTelecineCadenceSeen();
            mpeg2InterlaceObservation = completer.getInterlaceObservation();
            mpeg2SequenceExtensionSeen = completer.isSequenceExtensionSeen();
            mpeg2ProgressiveFrameCount = completer.getProgressiveFrameCount();
            mpeg2InterlacedFrameCount = completer.getInterlacedFrameCount();
            mpeg2FieldPictureCount = completer.getFieldPictureCount();
        }

        double getMpeg2ReportedFrameRateHz() { return mpeg2ReportedFrameRateHz; }
        double getMpeg2SequenceFrameRateHz() { return mpeg2SequenceFrameRateHz; }
        double getMpeg2EffectiveFieldDurationUs() { return mpeg2EffectiveFieldDurationUs; }
        boolean isMpeg2TelecineCadenceSeen() { return mpeg2TelecineCadenceSeen; }
        String getMpeg2InterlaceObservation() { return mpeg2InterlaceObservation; }
        boolean isMpeg2SequenceExtensionSeen() { return mpeg2SequenceExtensionSeen; }
        long getMpeg2ProgressiveFrameCount() { return mpeg2ProgressiveFrameCount; }
        long getMpeg2InterlacedFrameCount() { return mpeg2InterlacedFrameCount; }
        long getMpeg2FieldPictureCount() { return mpeg2FieldPictureCount; }

        private static final class PtsBoundary
        {
            final long bytePosition;
            final long offset90Khz;

            PtsBoundary(long bytePosition, long offset90Khz)
            {
                this.bytePosition = bytePosition;
                this.offset90Khz = offset90Khz;
            }
        }

        synchronized void setPtsOffset90Khz(long ptsOffset90Khz)
        {
            // This is an absolute stream rebase (initial open or SageTV FLUSH),
            // not an in-buffer NEWCELL transition.  ExtractorInput positions
            // restart at zero after MediaSource reprepare, so boundaries from
            // the discarded byte epoch must never survive into the new stream.
            pendingPtsBoundaries.clear();
            this.ptsOffset90Khz = ptsOffset90Khz;
            presentationShiftUs = 0;
            pendingCellAnchorUs = C.TIME_UNSET;
            pendingCellNormalization = false;
            inputEpochBasePosition = 0;
            pendingInputEpochBase = false;
            flushPlaybackAnchorUs = C.TIME_UNSET;
            latestVideoSampleUs = C.TIME_UNSET;
            latestAudioSampleUs = C.TIME_UNSET;
            cellGeneration++;
        }

        synchronized void beginFlushEpoch(long ptsOffset90Khz, long playbackAnchorUs)
        {
            // FLUSH discards unread bytes but the active Media3 ExtractorInput
            // keeps its absolute position. Preserve the rendered A/V clock and
            // map the first PTS in the replacement byte epoch directly after
            // it, while rebasing future NEWCELL byte boundaries to the first
            // input position observed after FLUSH.
            long videoAnchorUs = latestVideoSampleUs;
            long audioAnchorUs = latestAudioSampleUs;
            long observedAnchorUs = Math.max(
                    videoAnchorUs == C.TIME_UNSET ? Long.MIN_VALUE : videoAnchorUs,
                    audioAnchorUs == C.TIME_UNSET ? Long.MIN_VALUE : audioAnchorUs);
            // A short/still menu cell can contain a valid PES PTS without
            // completing an H262 frame. Preserve that parsed timestamp too;
            // otherwise a following FLUSH loses the clock anchor even though
            // the extractor already consumed the first replacement PES.
            observedAnchorUs = Math.max(observedAnchorUs,
                    latestVideoPesUs == C.TIME_UNSET ? Long.MIN_VALUE : latestVideoPesUs);
            observedAnchorUs = Math.max(observedAnchorUs,
                    latestAudioPesUs == C.TIME_UNSET ? Long.MIN_VALUE : latestAudioPesUs);
            if (playbackAnchorUs >= 0 && (observedAnchorUs == Long.MIN_VALUE
                    || observedAnchorUs > playbackAnchorUs + MAX_CONTINUOUS_AV_DELTA_US
                    || observedAnchorUs < playbackAnchorUs - MAX_CONTINUOUS_AV_DELTA_US))
            {
                // The player clock reflects what has actually rendered. Do
                // not let a previously misordered absolute-title PTS become
                // the anchor propagated through every later DVD cell.
                observedAnchorUs = playbackAnchorUs;
                discontinuityRebaseCount++;
            }
            if (observedAnchorUs != Long.MIN_VALUE)
            {
                pendingCellAnchorUs = observedAnchorUs;
                pendingCellNormalization = true;
            }
            // MiniDVDPlayer can send two FLUSH commands back-to-back before
            // any replacement PES arrives. In that case retain the anchor
            // captured by the first FLUSH; otherwise the menu audio keeps its
            // absolute DVD PTS and can be scheduled minutes into the future.
            else if (!pendingCellNormalization)
            {
                pendingCellAnchorUs = C.TIME_UNSET;
            }
            pendingPtsBoundaries.clear();
            this.ptsOffset90Khz = ptsOffset90Khz;
            flushPlaybackAnchorUs = playbackAnchorUs >= 0
                    ? playbackAnchorUs : C.TIME_UNSET;
            pendingInputEpochBase = true;
            latestVideoSampleUs = C.TIME_UNSET;
            latestAudioSampleUs = C.TIME_UNSET;
            cellGeneration++;
        }

        synchronized void beginRepreparedEpoch(long ptsOffset90Khz)
        {
            // A MediaSource reprepare creates a new Media3 period whose clock
            // starts at zero. Force the first A/V PES in the replacement DVD
            // cell onto that same clock; carrying the prior cell's absolute
            // presentation time makes Media3 wait tens of seconds at a black
            // frame while audio/video are queued in the future.
            pendingPtsBoundaries.clear();
            this.ptsOffset90Khz = ptsOffset90Khz;
            presentationShiftUs = 0;
            pendingCellAnchorUs = 0;
            pendingCellNormalization = true;
            inputEpochBasePosition = 0;
            pendingInputEpochBase = true;
            flushPlaybackAnchorUs = 0;
            latestVideoSampleUs = C.TIME_UNSET;
            latestAudioSampleUs = C.TIME_UNSET;
            latestVideoRawPts90Khz = C.TIME_UNSET;
            latestVideoOffsetPts90Khz = C.TIME_UNSET;
            latestVideoPesUs = C.TIME_UNSET;
            latestAudioRawPts90Khz = C.TIME_UNSET;
            latestAudioOffsetPts90Khz = C.TIME_UNSET;
            latestAudioPesUs = C.TIME_UNSET;
            cellGeneration++;
        }

        synchronized void queuePtsOffset90Khz(long bytePosition, long ptsOffset90Khz)
        {
            pendingPtsBoundaries.addLast(new PtsBoundary(
                    Math.max(0L, bytePosition), ptsOffset90Khz));
        }

        synchronized void advanceToPosition(long bytePosition)
        {
            if (pendingInputEpochBase)
            {
                inputEpochBasePosition = bytePosition;
                pendingInputEpochBase = false;
            }
            long epochPosition = Math.max(0L, bytePosition - inputEpochBasePosition);
            while (!pendingPtsBoundaries.isEmpty()
                    && pendingPtsBoundaries.peekFirst().bytePosition <= epochPosition)
            {
                PtsBoundary boundary = pendingPtsBoundaries.removeFirst();
                long videoAnchorUs = latestVideoSampleUs;
                long audioAnchorUs = latestAudioSampleUs;
                long observedAnchorUs = Math.max(
                        videoAnchorUs == C.TIME_UNSET ? Long.MIN_VALUE : videoAnchorUs,
                        audioAnchorUs == C.TIME_UNSET ? Long.MIN_VALUE : audioAnchorUs);
                observedAnchorUs = Math.max(observedAnchorUs,
                        latestVideoPesUs == C.TIME_UNSET ? Long.MIN_VALUE : latestVideoPesUs);
                observedAnchorUs = Math.max(observedAnchorUs,
                        latestAudioPesUs == C.TIME_UNSET ? Long.MIN_VALUE : latestAudioPesUs);
                if (observedAnchorUs != Long.MIN_VALUE)
                {
                    pendingCellAnchorUs = observedAnchorUs;
                    pendingCellNormalization = true;
                }
                ptsOffset90Khz = boundary.offset90Khz;
                cellGeneration++;
            }
        }

        long applyPtsOffset(long pts90Khz)
        {
            return (pts90Khz + ptsOffset90Khz) & PTS_MASK;
        }

        synchronized long normalizeCellTimeUs(long adjustedTimeUs)
        {
            if (adjustedTimeUs == C.TIME_UNSET)
                return adjustedTimeUs;
            if (pendingCellNormalization)
            {
                // The historical extender jumps its STC on NEWCELL. Media3
                // has no external STC control, so map the new absolute DVD
                // cell clock directly after the last queued A/V sample.
                presentationShiftUs = pendingCellAnchorUs + 1L - adjustedTimeUs;
                pendingCellNormalization = false;
            }
            long outputTimeUs = adjustedTimeUs + presentationShiftUs;
            long referenceUs = latestContinuousAvTimeUs();
            if (referenceUs != C.TIME_UNSET
                    && (outputTimeUs > referenceUs + MAX_CONTINUOUS_AV_DELTA_US
                    || outputTimeUs < referenceUs - MAX_CONTINUOUS_AV_DELTA_US))
            {
                // MiniDVDPlayer's NEWCELL and repeated FLUSH commands can be
                // observed in either order relative to the first replacement
                // PES. If that ordering consumed the pending boundary, contain
                // the unmistakable absolute-title-clock jump here. Normal DVD
                // buffering and B-frame reordering stay far below 30 seconds.
                presentationShiftUs = referenceUs + 1L - adjustedTimeUs;
                outputTimeUs = adjustedTimeUs + presentationShiftUs;
                discontinuityRebaseCount++;
            }
            // notePes()/noteAudioSample()/noteVideoSample() immediately
            // publishes this normalized value for subsequent streams.
            flushPlaybackAnchorUs = C.TIME_UNSET;
            return outputTimeUs;
        }

        synchronized long previewNormalizedCellTimeUs(long adjustedTimeUs)
        {
            if (adjustedTimeUs == C.TIME_UNSET)
                return adjustedTimeUs;
            if (pendingCellNormalization && pendingCellAnchorUs != C.TIME_UNSET)
                return pendingCellAnchorUs + 1L;
            long outputTimeUs = adjustedTimeUs + presentationShiftUs;
            long referenceUs = latestContinuousAvTimeUs();
            if (referenceUs != C.TIME_UNSET
                    && (outputTimeUs > referenceUs + MAX_CONTINUOUS_AV_DELTA_US
                    || outputTimeUs < referenceUs - MAX_CONTINUOUS_AV_DELTA_US))
                return referenceUs + 1L;
            return outputTimeUs;
        }

        private long latestContinuousAvTimeUs()
        {
            long referenceUs = C.TIME_UNSET;
            long[] candidates = { latestVideoSampleUs, latestAudioSampleUs,
                    latestVideoPesUs, latestAudioPesUs, flushPlaybackAnchorUs };
            for (long candidate : candidates)
            {
                if (candidate != C.TIME_UNSET
                        && (referenceUs == C.TIME_UNSET || candidate > referenceUs))
                    referenceUs = candidate;
            }
            return referenceUs;
        }

        long getCellGeneration() { return cellGeneration; }

        void noteVideoSample(long timeUs, boolean corrected)
        {
            latestVideoSampleUs = timeUs;
            if (corrected)
                videoCorrectionCount++;
        }

        void noteAudioSample(long timeUs)
        {
            latestAudioSampleUs = timeUs;
        }

        long getLatestVideoSampleUs() { return latestVideoSampleUs; }
        long getLatestAudioSampleUs() { return latestAudioSampleUs; }
        long getVideoCorrectionCount() { return videoCorrectionCount; }
        long getDiscontinuityRebaseCount() { return discontinuityRebaseCount; }

        void notePes(boolean video, long rawPts90Khz, long offsetPts90Khz, long adjustedUs)
        {
            if (video)
            {
                latestVideoRawPts90Khz = rawPts90Khz;
                latestVideoOffsetPts90Khz = offsetPts90Khz;
                latestVideoPesUs = adjustedUs;
            }
            else
            {
                latestAudioRawPts90Khz = rawPts90Khz;
                latestAudioOffsetPts90Khz = offsetPts90Khz;
                latestAudioPesUs = adjustedUs;
            }
        }

        long getLatestVideoRawPts90Khz() { return latestVideoRawPts90Khz; }
        long getLatestVideoOffsetPts90Khz() { return latestVideoOffsetPts90Khz; }
        long getLatestVideoPesUs() { return latestVideoPesUs; }
        long getLatestAudioRawPts90Khz() { return latestAudioRawPts90Khz; }
        long getLatestAudioOffsetPts90Khz() { return latestAudioOffsetPts90Khz; }
        long getLatestAudioPesUs() { return latestAudioPesUs; }
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException
    {
        byte[] scratch = new byte[14];
        input.peekFully(scratch, 0, 14);
        int code = ((scratch[0] & 0xFF) << 24) | ((scratch[1] & 0xFF) << 16)
                | ((scratch[2] & 0xFF) << 8) | (scratch[3] & 0xFF);
        if (code != PACK_START_CODE || (scratch[4] & 0xC4) != 0x44
                || (scratch[6] & 0x04) != 0x04 || (scratch[8] & 0x04) != 0x04
                || (scratch[9] & 0x01) != 0x01 || (scratch[12] & 0x03) != 0x03)
            return false;
        input.advancePeekPosition(scratch[13] & 0x07);
        input.peekFully(scratch, 0, 3);
        return START_CODE_PREFIX == (((scratch[0] & 0xFF) << 16)
                | ((scratch[1] & 0xFF) << 8) | (scratch[2] & 0xFF));
    }

    @Override
    public void init(ExtractorOutput output)
    {
        this.output = output;
        Log.i(TAG, "Initialized DVD-aware MPEG-PS extractor");
    }

    @Override
    public void seek(long position, long timeUs)
    {
        if (timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET)
            timestampAdjuster.reset(timeUs);
        for (int i = 0; i < readers.size(); i++)
            readers.valueAt(i).seek();
    }

    @Override
    public void release()
    {
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException
    {
        if (output == null)
            throw new IllegalStateException("init must be called before read");
        timestampState.advanceToPosition(input.getPosition());
        if (!seekMapSent)
        {
            output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
            seekMapSent = true;
        }

        input.resetPeekPosition();
        if (!input.peekFully(packet.getData(), 0, 4, true))
        {
            endInput();
            return RESULT_END_OF_INPUT;
        }
        packet.setPosition(0);
        int startCode = packet.readInt();
        if (startCode == PROGRAM_END_CODE)
        {
            // Authored DVDs place MPEG program-end markers between VOB/title/menu
            // segments while SageTV keeps the same MiniDVD Push session alive.
            // Treat the marker as an in-band boundary; the Push DataSource itself
            // owns true session EOS/close.
            input.skipFully(4);
            return RESULT_CONTINUE;
        }
        if (startCode == PACK_START_CODE)
        {
            input.peekFully(packet.getData(), 0, 10);
            packet.setPosition(9);
            input.skipFully((packet.readUnsignedByte() & 0x07) + 14);
            return RESULT_CONTINUE;
        }
        if (startCode == SYSTEM_HEADER_START_CODE)
        {
            input.peekFully(packet.getData(), 0, 2);
            packet.setPosition(0);
            input.skipFully(packet.readUnsignedShort() + 6);
            return RESULT_CONTINUE;
        }
        if (((startCode & 0xFFFFFF00) >>> 8) != START_CODE_PREFIX)
        {
            input.skipFully(1);
            return RESULT_CONTINUE;
        }

        int streamId = startCode & 0xFF;
        PacketReader reader = readers.get(streamId);
        if (!foundAllTracks && reader == null)
        {
            ElementaryStreamReader elementary = null;
            if (streamId == PRIVATE_STREAM_1)
            {
                reader = new DvdPrivateStreamPesReader(output, timestampAdjuster, timestampState,
                        subpictureDecoder, stillFrameRepeater);
                readers.put(streamId, reader);
            }
            else if ((streamId & AUDIO_STREAM_MASK) == AUDIO_STREAM)
            {
                elementary = new MpegAudioReader(MimeTypes.VIDEO_PS);
                foundAudio = true;
            }
            else if ((streamId & VIDEO_STREAM_MASK) == VIDEO_STREAM)
            {
                elementary = new H262Reader(MimeTypes.VIDEO_PS);
                foundVideo = true;
            }
            if (elementary != null)
            {
                lastTrackPosition = input.getPosition();
                TrackIdGenerator ids = new TrackIdGenerator(streamId, MAX_STREAM_ID_PLUS_ONE);
                // Preserve raw PES PTS into H262Reader and preserve the final
                // sample PTS delivered by it. MPEG-2 B pictures are carried in
                // decode order but have intentionally non-monotonic display
                // timestamps; forcing those timestamps to be monotonic makes
                // video drift behind the unmodified AC-3 clock. Observe the
                // samples for telemetry only. TimestampState already contains
                // the DVD NEWCELL/FLUSH discontinuity handling at PES level.
                ExtractorOutput trackOutput = (streamId & VIDEO_STREAM_MASK) == VIDEO_STREAM
                        ? new ObservingVideoExtractorOutput(output, timestampState,
                                stillFrameRepeater, repairMpeg2PictureTimestamps) : output;
                elementary.createTracks(trackOutput, ids);
                reader = new PesReader(elementary, timestampAdjuster, timestampState,
                        (streamId & VIDEO_STREAM_MASK) == VIDEO_STREAM,
                        stillFrameRepeater);
                readers.put(streamId, reader);
            }
        }
        input.peekFully(packet.getData(), 0, 2);
        packet.setPosition(0);
        int pesLength = packet.readUnsignedShort() + 6;
        if (reader == null)
        {
            input.skipFully(pesLength);
        }
        else
        {
            packet.reset(pesLength);
            input.readFully(packet.getData(), 0, pesLength);
            packet.setPosition(6);
            reader.consume(packet);
            packet.setLimit(packet.capacity());
            if (reader instanceof DvdPrivateStreamPesReader)
            {
                int privateAudioTracks =
                        ((DvdPrivateStreamPesReader) reader).audioTrackCount();
                if (privateAudioTracks > discoveredPrivateAudioTrackCount)
                {
                    discoveredPrivateAudioTrackCount = privateAudioTracks;
                    foundAudio = true;
                    lastTrackPosition = input.getPosition();
                }
            }
        }
        // A private_stream_1 packet may contain only SPU data. Do not treat its
        // stream id as proof of audio and finalize TrackGroups before an actual
        // 0x80..0x87 AC-3 substream creates a TrackOutput. Check only after the
        // current PES has been consumed so late audio in video-first cells is
        // visible to Media3 before endTracks().
        if (!foundAllTracks)
        {
            long limit = foundAudio && foundVideo
                    ? lastTrackPosition + MAX_SEARCH_AFTER_AV
                    : (foundVideo ? MAX_VIDEO_ONLY_SEARCH_LENGTH
                    : MAX_AUDIO_ONLY_SEARCH_LENGTH);
            if (input.getPosition() > limit)
            {
                foundAllTracks = true;
                output.endTracks();
            }
        }
        return RESULT_CONTINUE;
    }

    private void endInput()
    {
        for (int i = 0; i < readers.size(); i++)
            readers.valueAt(i).endInput();
        stillFrameRepeater.ensureDecoderStartupWindow();
        // A DVD VM cell is a finite extractor generation and may legitimately
        // be smaller than either discovery window above.  Media3 will reject
        // that generation as PARSING_CONTAINER_MALFORMED ("Loading finished
        // before preparation is complete") if EOF arrives before endTracks().
        // Finish discovery from the streams actually present in the cell.  A
        // later authored cell receives a new extractor/output generation, so
        // this does not prevent that cell from advertising a different set of
        // streams.
        if (!foundAllTracks)
        {
            foundAllTracks = true;
            output.endTracks();
        }
    }

    private interface PacketReader
    {
        void seek();
        void endInput();
        void consume(ParsableByteArray data) throws ParserException;
    }

    private static final class ObservingVideoExtractorOutput implements ExtractorOutput
    {
        private final ExtractorOutput delegate;
        private final TimestampState timestampState;
        private final StillFrameRepeater stillFrameRepeater;
        private final boolean repairMpeg2PictureTimestamps;

        ObservingVideoExtractorOutput(ExtractorOutput delegate, TimestampState timestampState,
                StillFrameRepeater stillFrameRepeater, boolean repairMpeg2PictureTimestamps)
        {
            this.delegate = delegate;
            this.timestampState = timestampState;
            this.stillFrameRepeater = stillFrameRepeater;
            this.repairMpeg2PictureTimestamps = repairMpeg2PictureTimestamps;
        }

        @Override
        public TrackOutput track(int id, int type)
        {
            TrackOutput output = delegate.track(id, type);
            if (type != C.TRACK_TYPE_VIDEO)
                return output;
            ObservingVideoTrackOutput observing = new ObservingVideoTrackOutput(
                    output, timestampState, repairMpeg2PictureTimestamps);
            stillFrameRepeater.attach(observing);
            return observing;
        }

        @Override
        public void endTracks()
        {
            delegate.endTracks();
        }

        @Override
        public void seekMap(SeekMap seekMap)
        {
            delegate.seekMap(seekMap);
        }
    }

    /** Records and, when needed, restores the final H262 display clock. */
    private static final class ObservingVideoTrackOutput implements TrackOutput
    {
        private static final int MAX_CAPTURED_SAMPLE_BYTES = 2 * 1024 * 1024;
        private final TrackOutput delegate;
        private final TimestampState timestampState;
        private final boolean repairMpeg2PictureTimestamps;
        private final Mpeg2PictureTimestampCompleter timestampCompleter =
                new Mpeg2PictureTimestampCompleter();
        private final ByteArrayOutputStream pendingSampleData = new ByteArrayOutputStream();
        @Nullable private byte[] lastSampleData;
        private int lastSampleFlags;
        private long lastSampleTimeUs = C.TIME_UNSET;
        private int sequenceSampleCount;
        private boolean captureCandidate = true;
        private long totalBytesForwarded;

        ObservingVideoTrackOutput(TrackOutput delegate, TimestampState timestampState,
                boolean repairMpeg2PictureTimestamps)
        {
            this.delegate = delegate;
            this.timestampState = timestampState;
            this.repairMpeg2PictureTimestamps = repairMpeg2PictureTimestamps;
        }

        @Override
        public void format(Format format)
        {
            timestampCompleter.setFrameRate(format.frameRate);
            timestampState.noteMpeg2Cadence(timestampCompleter);
            delegate.format(format);
        }

        @Override
        public int sampleData(DataReader input, int length, boolean allowEndOfInput,
                int sampleDataPart) throws IOException
        {
            DataReader tee = new DataReader()
            {
                @Override
                public int read(byte[] buffer, int offset, int readLength) throws IOException
                {
                    int read = input.read(buffer, offset, readLength);
                    if (read > 0)
                    {
                        timestampCompleter.consume(buffer, offset, read);
                        if (captureCandidate)
                            appendCaptured(buffer, offset, read);
                    }
                    return read;
                }
            };
            int appended = delegate.sampleData(tee, length, allowEndOfInput, sampleDataPart);
            if (appended > 0)
                totalBytesForwarded += appended;
            return appended;
        }

        @Override
        public void sampleData(ParsableByteArray data, int length, int sampleDataPart)
        {
            timestampCompleter.consume(data.getData(), data.getPosition(), length);
            if (captureCandidate)
                appendCaptured(data.getData(), data.getPosition(), length);
            delegate.sampleData(data, length, sampleDataPart);
            totalBytesForwarded += length;
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset,
                @Nullable CryptoData cryptoData)
        {
            long sampleEnd = totalBytesForwarded - Math.max(0, offset);
            long sampleStart = sampleEnd - Math.max(0, size);
            Mpeg2PictureTimestampCompleter.PictureTiming timing = null;
            if (repairMpeg2PictureTimestamps)
                timing = timestampCompleter.observeSample(sampleStart, sampleEnd, timeUs);
            timestampState.noteMpeg2Cadence(timestampCompleter);
            if (captureCandidate)
            {
                // Only the first completed picture can be an authored still.
                // Copying every moving-video sample produces sustained large
                // allocations and periodic GC/frame-release stalls on Fire TV.
                rememberCompletedSample(timeUs, flags, size, offset);
                captureCandidate = false;
                pendingSampleData.reset();
            }
            sequenceSampleCount++;
            if (!repairMpeg2PictureTimestamps)
            {
                timestampState.noteVideoSample(timeUs, false);
                delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData);
                return;
            }
            long outputTimeUs = timestampCompleter.completeTimestamp(timing, timeUs);
            boolean corrected = timestampCompleter.wasLastTimestampCorrected();
            timestampState.noteVideoSample(outputTimeUs, corrected);
            delegate.sampleMetadata(outputTimeUs, flags, size, offset, cryptoData);
        }

        void beginSequence()
        {
            sequenceSampleCount = 0;
            pendingSampleData.reset();
            lastSampleData = null;
            lastSampleFlags = 0;
            lastSampleTimeUs = C.TIME_UNSET;
            captureCandidate = true;
            if (repairMpeg2PictureTimestamps)
                timestampCompleter.reset();
        }

        boolean isSinglePictureSequence()
        {
            return sequenceSampleCount == 1;
        }

        private void appendCaptured(byte[] bytes, int offset, int length)
        {
            if (length <= 0)
                return;
            if (pendingSampleData.size() + length > MAX_CAPTURED_SAMPLE_BYTES)
            {
                // Normal title video commits samples frequently. A malformed
                // or unexpectedly huge picture must not become an unbounded
                // diagnostic/player allocation.
                pendingSampleData.reset();
                return;
            }
            pendingSampleData.write(bytes, offset, length);
        }

        private void rememberCompletedSample(long timeUs, int flags, int size, int offset)
        {
            byte[] pending = pendingSampleData.toByteArray();
            int sampleEnd = pending.length - Math.max(0, offset);
            int sampleStart = sampleEnd - Math.max(0, size);
            if (size > 0 && sampleStart >= 0 && sampleEnd <= pending.length)
            {
                lastSampleData = Arrays.copyOfRange(pending, sampleStart, sampleEnd);
                lastSampleFlags = flags;
                lastSampleTimeUs = timeUs;
            }
            byte[] trailing = sampleEnd >= 0 && sampleEnd < pending.length
                    ? Arrays.copyOfRange(pending, sampleEnd, pending.length) : null;
            pendingSampleData.reset();
            if (trailing != null && trailing.length > 0)
                pendingSampleData.write(trailing, 0, trailing.length);
        }

        boolean hasRepeatableSample()
        {
            return lastSampleData != null && lastSampleData.length > 0
                    && lastSampleTimeUs != C.TIME_UNSET;
        }

        long getLastSampleTimeUs()
        {
            return lastSampleTimeUs;
        }

        void repeatLastSampleAt(long timeUs)
        {
            if (!hasRepeatableSample() || timeUs <= lastSampleTimeUs)
                return;
            ParsableByteArray copy = new ParsableByteArray(lastSampleData);
            delegate.sampleData(copy, lastSampleData.length);
            delegate.sampleMetadata(timeUs, lastSampleFlags | C.BUFFER_FLAG_KEY_FRAME,
                    lastSampleData.length, 0, null);
            lastSampleTimeUs = timeUs;
            timestampState.noteVideoSample(timeUs, false);
        }

        void notePacketTimestamp(boolean authored)
        {
            if (repairMpeg2PictureTimestamps)
                timestampCompleter.notePacketTimestamp(authored);
        }

    }

    /**
     * Keeps an authored MPEG-2 still menu decodable while its AC-3 menu loop
     * continues. Hardware MPEG-2 decoders commonly retain the only I-picture
     * until a following picture or EOS arrives. The DVD Push stream cannot use
     * permanent EOS because the server sends more cells after navigation.
     * Re-submit the completed still sequence at a sparse five-second cadence
     * through the audio clock instead; this both releases the first decoded
     * frame, gives Media3 a future video buffer edge, and avoids retaining
     * hundreds of copies of a large DVD I-picture on memory-limited sticks.
     */
    private static final class StillFrameRepeater
    {
        // A one-picture DVD menu relies on the decoder retaining that authored
        // background while AC-3 and SPU commands continue. Media3 treats a
        // multi-second gap to the next synthetic video sample as renderer
        // starvation and enters BUFFERING, which also stops menu audio. A
        // bounded 500 ms cadence keeps the video renderer ready without
        // changing moving title/program streams (only single-picture complete
        // MPEG sequences enter this repeater).
        private static final long REPEAT_INTERVAL_US = 500_000L;
        private static final long MIN_VIDEO_ONLY_STILL_DURATION_US = 2_000_000L;
        @Nullable private ObservingVideoTrackOutput videoOutput;
        private boolean sequenceComplete;

        void attach(ObservingVideoTrackOutput videoOutput)
        {
            this.videoOutput = videoOutput;
            sequenceComplete = false;
            videoOutput.beginSequence();
        }

        void beginSequence()
        {
            sequenceComplete = false;
            ObservingVideoTrackOutput output = videoOutput;
            if (output != null)
                output.beginSequence();
        }

        void completeSequence()
        {
            ObservingVideoTrackOutput output = videoOutput;
            // Normal moving MPEG-2 clips also carry sequence_end_code. Only a
            // one-picture sequence is an authored DVD still that needs a
            // synthetic successor to make hardware decoders release it.
            sequenceComplete = output != null && output.isSinglePictureSequence();
            if (sequenceComplete && output.hasRepeatableSample())
                extendDecoderStartupWindow(output);
        }

        void notePacketTimestamp(boolean authored)
        {
            ObservingVideoTrackOutput output = videoOutput;
            if (output != null)
                output.notePacketTimestamp(authored);
        }

        /**
         * A hardware MPEG-2 decoder can need longer than a 480-500 ms authored
         * menu cell to produce its first Surface frame. Extend only a proven
         * single-picture, video-only still at extractor EOF; the image is
         * unchanged and the bounded window lets Media3 render it before the
         * finite replacement period reaches ENDED.
         */
        void ensureDecoderStartupWindow()
        {
            ObservingVideoTrackOutput output = videoOutput;
            if (output == null)
                return;
            // Some authored cells deliver their sequence_end before the outer
            // PS extractor observes EOF. Re-evaluate the completed output here
            // instead of relying only on the earlier PES callback; otherwise
            // the first synthetic successor is emitted but the remaining
            // decoder-start window is accidentally omitted.
            sequenceComplete = sequenceComplete || output.isSinglePictureSequence();
            if (!sequenceComplete || !output.hasRepeatableSample())
                return;
            extendDecoderStartupWindow(output);
        }

        private static void extendDecoderStartupWindow(ObservingVideoTrackOutput output)
        {
            // Menu cells remain open while the DVD VM waits for remote input,
            // so extractor EOF is not guaranteed. Pad immediately after the
            // proven one-picture sequence completes rather than relying on an
            // EOF callback that may never arrive.
            while (output.getLastSampleTimeUs() < MIN_VIDEO_ONLY_STILL_DURATION_US)
                output.repeatLastSampleAt(output.getLastSampleTimeUs() + REPEAT_INTERVAL_US);
        }

        void repeatThrough(long audioTimeUs)
        {
            ObservingVideoTrackOutput output = videoOutput;
            if (!sequenceComplete || output == null || !output.hasRepeatableSample()
                    || audioTimeUs == C.TIME_UNSET)
                return;
            while (output.getLastSampleTimeUs() + REPEAT_INTERVAL_US <= audioTimeUs)
                output.repeatLastSampleAt(output.getLastSampleTimeUs() + REPEAT_INTERVAL_US);
        }
    }

    private static final class PesReader implements PacketReader
    {
        private final ElementaryStreamReader payloadReader;
        private final TimestampAdjuster timestampAdjuster;
        private final TimestampState timestampState;
        private final boolean video;
        private final ParsableBitArray scratch = new ParsableBitArray(new byte[64]);
        private boolean ptsFlag;
        private boolean dtsFlag;
        private boolean seenFirstDts;
        private int extendedHeaderLength;
        private long timeUs;
        private long observedCellGeneration = Long.MIN_VALUE;
        private boolean inputFinalized;
        @Nullable private final StillFrameRepeater stillFrameRepeater;

        PesReader(ElementaryStreamReader payloadReader, TimestampAdjuster timestampAdjuster,
                TimestampState timestampState, boolean video,
                StillFrameRepeater stillFrameRepeater)
        {
            this.payloadReader = payloadReader;
            this.timestampAdjuster = timestampAdjuster;
            this.timestampState = timestampState;
            this.video = video;
            this.stillFrameRepeater = video ? stillFrameRepeater : null;
        }

        @Override
        public void seek()
        {
            seenFirstDts = false;
            inputFinalized = false;
            payloadReader.seek();
        }

        @Override
        public void endInput()
        {
            if (inputFinalized)
                return;
            payloadReader.endOfInputReached();
            inputFinalized = true;
        }

        @Override
        public void consume(ParsableByteArray data) throws ParserException
        {
            long cellGeneration = timestampState.getCellGeneration();
            if (cellGeneration != observedCellGeneration)
            {
                observedCellGeneration = cellGeneration;
                payloadReader.seek();
                if (stillFrameRepeater != null)
                    stillFrameRepeater.beginSequence();
                seenFirstDts = false;
                inputFinalized = false;
            }
            else if (inputFinalized)
            {
                // A new MPEG sequence can follow an authored sequence-end
                // marker without replacing the outer PS stream.
                payloadReader.seek();
                if (stillFrameRepeater != null)
                    stillFrameRepeater.beginSequence();
                seenFirstDts = false;
                inputFinalized = false;
            }
            data.readBytes(scratch.data, 0, 3);
            scratch.setPosition(0);
            scratch.skipBits(8);
            ptsFlag = scratch.readBit();
            dtsFlag = scratch.readBit();
            scratch.skipBits(6);
            extendedHeaderLength = scratch.readBits(8);
            data.readBytes(scratch.data, 0, extendedHeaderLength);
            scratch.setPosition(0);
            parseTime();
            boolean completeMpegSequence = video && containsSequenceEndCode(
                    data.getData(), data.getPosition(), data.bytesLeft());
            if (stillFrameRepeater != null)
                stillFrameRepeater.notePacketTimestamp(ptsFlag && timeUs != C.TIME_UNSET);
            // Match Media3's stock PS extractor. This flag represents the PES
            // data-alignment indicator; FLAG_PAYLOAD_UNIT_START_INDICATOR (1)
            // is a transport-stream flag and is not correct for MPEG-PS PES.
            payloadReader.packetStarted(timeUs, PES_DATA_ALIGNMENT_INDICATOR);
            payloadReader.consume(data);
            payloadReader.packetFinished();
            // Authored DVD still menus commonly contain exactly one I-picture
            // followed by MPEG sequence_end_code. H262Reader otherwise keeps
            // that frame pending until another picture arrives, but the DVD VM
            // can remain paused on the still indefinitely. Finalize only an
            // explicitly complete sequence; normal title PES/B-frame behavior
            // remains untouched.
            if (completeMpegSequence)
            {
                payloadReader.endOfInputReached();
                inputFinalized = true;
                if (stillFrameRepeater != null)
                    stillFrameRepeater.completeSequence();
            }
        }

        private static boolean containsSequenceEndCode(byte[] bytes, int offset, int length)
        {
            int limit = Math.min(bytes.length, offset + Math.max(0, length));
            for (int i = Math.max(0, offset); i + 3 < limit; i++)
            {
                if (bytes[i] == 0 && bytes[i + 1] == 0
                        && bytes[i + 2] == 1 && (bytes[i + 3] & 0xFF) == 0xB7)
                    return true;
            }
            return false;
        }

        private void parseTime()
        {
            timeUs = C.TIME_UNSET;
            if (!ptsFlag)
                return;
            scratch.skipBits(4);
            long pts = (long) scratch.readBits(3) << 30;
            scratch.skipBits(1);
            pts |= scratch.readBits(15) << 15;
            scratch.skipBits(1);
            pts |= scratch.readBits(15);
            scratch.skipBits(1);
            if (!seenFirstDts && dtsFlag)
            {
                scratch.skipBits(4);
                long dts = (long) scratch.readBits(3) << 30;
                scratch.skipBits(1);
                dts |= scratch.readBits(15) << 15;
                scratch.skipBits(1);
                dts |= scratch.readBits(15);
                scratch.skipBits(1);
                timestampAdjuster.adjustTsTimestamp(timestampState.applyPtsOffset(dts));
                seenFirstDts = true;
            }
            long offsetPts = timestampState.applyPtsOffset(pts);
            long adjustedPtsUs = timestampState.normalizeCellTimeUs(
                    timestampAdjuster.adjustTsTimestamp(offsetPts));
            timestampState.notePes(video, pts, offsetPts, adjustedPtsUs);
            // Preserve the PES PTS exactly as Media3's stock PS extractor does.
            // B-frame PTS values are intentionally non-monotonic in decode
            // order; MediaCodec performs presentation reordering.
            timeUs = adjustedPtsUs;
        }
    }

    /** Routes each DVD private-stream AC-3 substream to an independent elementary reader. */
    private static final class DvdPrivateStreamPesReader implements PacketReader
    {
        private final ExtractorOutput output;
        private final TimestampAdjuster timestampAdjuster;
        private final TimestampState timestampState;
        @Nullable private final DvdSubpictureDecoder subpictureDecoder;
        private final StillFrameRepeater stillFrameRepeater;
        private final SparseArray<DvdAc3Reader> substreams = new SparseArray<>();
        private final ParsableBitArray scratch = new ParsableBitArray(new byte[64]);
        private boolean ptsFlag;
        private boolean dtsFlag;
        private boolean seenFirstDts;
        private int extendedHeaderLength;
        private long timeUs;
        private long rawPts90Khz = C.TIME_UNSET;
        private long offsetPts90Khz = C.TIME_UNSET;
        private long observedCellGeneration = Long.MIN_VALUE;

        DvdPrivateStreamPesReader(ExtractorOutput output, TimestampAdjuster timestampAdjuster,
                TimestampState timestampState, @Nullable DvdSubpictureDecoder subpictureDecoder,
                StillFrameRepeater stillFrameRepeater)
        {
            this.output = output;
            this.timestampAdjuster = timestampAdjuster;
            this.timestampState = timestampState;
            this.subpictureDecoder = subpictureDecoder;
            this.stillFrameRepeater = stillFrameRepeater;
        }

        int audioTrackCount()
        {
            return substreams.size();
        }

        @Override
        public void seek()
        {
            seenFirstDts = false;
            for (int i = 0; i < substreams.size(); i++)
                substreams.valueAt(i).seek();
        }

        @Override
        public void endInput()
        {
            for (int i = 0; i < substreams.size(); i++)
                substreams.valueAt(i).endOfInputReached();
        }

        @Override
        public void consume(ParsableByteArray data) throws ParserException
        {
            long cellGeneration = timestampState.getCellGeneration();
            if (cellGeneration != observedCellGeneration)
            {
                observedCellGeneration = cellGeneration;
                seenFirstDts = false;
                for (int i = 0; i < substreams.size(); i++)
                    substreams.valueAt(i).seek();
            }
            data.readBytes(scratch.data, 0, 3);
            scratch.setPosition(0);
            scratch.skipBits(8);
            ptsFlag = scratch.readBit();
            dtsFlag = scratch.readBit();
            scratch.skipBits(6);
            extendedHeaderLength = scratch.readBits(8);
            data.readBytes(scratch.data, 0, extendedHeaderLength);
            scratch.setPosition(0);
            parseTime();

            if (data.bytesLeft() < 4)
                return;
            int substreamId = data.readUnsignedByte();
            if (substreamId >= 0x20 && substreamId <= 0x3F)
            {
                // SPU/highlight packets can use a menu-display clock that is
                // discontinuous from the A/V clock. They must not consume the
                // pending NEWCELL/FLUSH presentation rebase intended for the
                // first actual audio or video PES.
                if (subpictureDecoder != null && data.bytesLeft() > 0)
                {
                    subpictureDecoder.pushFragment(substreamId, data.getData(), data.getPosition(),
                            data.bytesLeft(), timestampState.previewNormalizedCellTimeUs(timeUs));
                }
                data.skipBytes(data.bytesLeft());
                return;
            }
            // AC-3 DVD substreams are 0x80-0x87. The next byte is the number of frame headers,
            // followed by the 16-bit offset to the first access unit. Media3's stock PS reader
            // omitted this routing and mixed every DVD audio stream into one decoder input.
            if (substreamId < 0x80 || substreamId > 0x87)
                return;
            long audioTimeUs = timestampState.normalizeCellTimeUs(timeUs);
            timestampState.notePes(false, rawPts90Khz, offsetPts90Khz, audioTimeUs);
            int frameHeaderCount = data.readUnsignedByte();
            int firstAccessUnitPointer = data.readUnsignedShort();

            DvdAc3Reader reader = substreams.get(substreamId);
            if (reader == null)
            {
                reader = new DvdAc3Reader(MimeTypes.VIDEO_PS, timestampState);
                reader.createTracks(output, new TrackIdGenerator(0xBD00 + substreamId, 1));
                substreams.put(substreamId, reader);
                Log.i(TAG, "Created DVD AC-3 substream track id=0x"
                        + Integer.toHexString(substreamId));
            }
            reader.packetStarted(audioTimeUs, PES_DATA_ALIGNMENT_INDICATOR);
            int bytesBeforeFirstAccessUnit = Math.max(0, firstAccessUnitPointer - 1);
            if (frameHeaderCount > 0 && bytesBeforeFirstAccessUnit <= data.bytesLeft())
            {
                if (bytesBeforeFirstAccessUnit > 0)
                {
                    int oldLimit = data.limit();
                    data.setLimit(data.getPosition() + bytesBeforeFirstAccessUnit);
                    reader.consume(data);
                    data.setLimit(oldLimit);
                }
                // The DVD header gives an authoritative AC-3 frame boundary. Reset here so a
                // sync-looking byte pair in the preceding frame tail cannot reach MediaCodec.
                reader.alignToAccessUnit();
            }
            reader.consume(data);
            reader.packetFinished();
            stillFrameRepeater.repeatThrough(timestampState.getLatestAudioSampleUs());
        }

        private void parseTime()
        {
            timeUs = C.TIME_UNSET;
            rawPts90Khz = C.TIME_UNSET;
            offsetPts90Khz = C.TIME_UNSET;
            if (!ptsFlag)
                return;
            scratch.skipBits(4);
            long pts = (long) scratch.readBits(3) << 30;
            scratch.skipBits(1);
            pts |= scratch.readBits(15) << 15;
            scratch.skipBits(1);
            pts |= scratch.readBits(15);
            scratch.skipBits(1);
            if (!seenFirstDts && dtsFlag)
            {
                scratch.skipBits(4);
                long dts = (long) scratch.readBits(3) << 30;
                scratch.skipBits(1);
                dts |= scratch.readBits(15) << 15;
                scratch.skipBits(1);
                dts |= scratch.readBits(15);
                scratch.skipBits(1);
                timestampAdjuster.adjustTsTimestamp(timestampState.applyPtsOffset(dts));
                seenFirstDts = true;
            }
            rawPts90Khz = pts;
            offsetPts90Khz = timestampState.applyPtsOffset(pts);
            timeUs = timestampAdjuster.adjustTsTimestamp(offsetPts90Khz);
        }
    }
}
