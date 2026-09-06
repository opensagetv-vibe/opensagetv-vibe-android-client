package opensagetv.vibe.miniclient.video;

import java.util.ArrayDeque;

/**
 * Reconstructs display timestamps for untimestamped telecined MPEG-2 pictures.
 *
 * <p>DVD program streams commonly omit PTS on forward-predicted pictures. Media3's H262 reader
 * then extrapolates from the previously emitted sample, which can be an authored B-picture PTS.
 * On MediaTek decoders Media3's decode-order extrapolation creates an irregular release cadence
 * and visible judder. This bounded scanner preserves authored timestamps and replaces only
 * extrapolated picture values after a 3:2 repeat-field cadence has been observed.</p>
 */
public final class Mpeg2PictureTimestampCompleter
{
    public static final long TIME_UNSET = Long.MIN_VALUE;

    private static final int PICTURE_START_CODE = 0x00000100;
    private static final int SEQUENCE_HEADER_CODE = 0x000001B3;
    private static final int EXTENSION_START_CODE = 0x000001B5;
    private static final int GOP_START_CODE = 0x000001B8;
    private static final int SEQUENCE_EXTENSION_ID = 1;
    private static final int PICTURE_CODING_EXTENSION_ID = 8;
    private static final int PICTURE_TYPE_I = 1;
    private static final int PICTURE_TYPE_P = 2;
    private static final int PICTURE_TYPE_B = 3;
    private static final int MAX_QUEUED_HEADERS = 128;
    private static final int MAX_GOP_STATES = 4;
    private static final long MAX_REPAIR_DELTA_US = 1_000_000L;

    private final ArrayDeque<PictureHeader> headers = new ArrayDeque<PictureHeader>();
    private final ArrayDeque<GopTiming> gopTimings = new ArrayDeque<GopTiming>();
    private int rollingStartCode = -1;
    private long bytesConsumed;
    private long gopGeneration;
    private int pendingHeaderKind;
    private int pendingHeaderBytes;
    private int pendingByte0;
    private long pendingHeaderValue;
    private long pendingHeaderPosition;
    private long pendingHeaderGop;
    private PictureHeader lastPictureHeader;
    private boolean progressiveSequence;
    private boolean pendingTimestampMarker;
    private boolean pendingTimestampAuthored;
    private boolean telecineCadenceSeen;
    private double reportedFrameRateHz;
    private double frameRateHz;
    private double frameDurationUs;
    private boolean lastTimestampCorrected;
    private boolean sequenceExtensionSeen;
    private long pictureCodingExtensionCount;
    private long progressiveFrameCount;
    private long interlacedFrameCount;
    private long fieldPictureCount;

    /** Sets a container-reported frame rate if a sequence header has not supplied one. */
    public void setFrameRate(float frameRate)
    {
        if (frameDurationUs <= 0.0 && frameRate > 0.0f)
        {
            reportedFrameRateHz = frameRate;
            frameRateHz = frameRate;
            frameDurationUs = 1_000_000.0 / frameRate;
        }
        else if (frameRate > 0.0f)
        {
            reportedFrameRateHz = frameRate;
        }
    }

    public void reset()
    {
        headers.clear();
        gopTimings.clear();
        rollingStartCode = -1;
        bytesConsumed = 0L;
        gopGeneration++;
        pendingHeaderKind = 0;
        pendingHeaderBytes = 0;
        pendingByte0 = 0;
        pendingHeaderValue = 0L;
        pendingHeaderPosition = 0L;
        pendingHeaderGop = gopGeneration;
        lastPictureHeader = null;
        progressiveSequence = false;
        pendingTimestampMarker = false;
        pendingTimestampAuthored = false;
        telecineCadenceSeen = false;
        lastTimestampCorrected = false;
        sequenceExtensionSeen = false;
        pictureCodingExtensionCount = 0L;
        progressiveFrameCount = 0L;
        interlacedFrameCount = 0L;
        fieldPictureCount = 0L;
    }

    /** Marks whether the next MPEG-2 picture is covered by an authored PES PTS. */
    public void notePacketTimestamp(boolean authored)
    {
        pendingTimestampMarker = true;
        pendingTimestampAuthored = authored;
    }

    public long getBytesConsumed()
    {
        return bytesConsumed;
    }

    public boolean wasLastTimestampCorrected()
    {
        return lastTimestampCorrected;
    }

    public double getReportedFrameRateHz()
    {
        return reportedFrameRateHz;
    }

    public double getSequenceFrameRateHz()
    {
        return frameRateHz;
    }

    public double getEffectiveFieldDurationUs()
    {
        return frameDurationUs > 0.0 ? fieldDurationUs() : 0.0;
    }

    public boolean isTelecineCadenceSeen()
    {
        return telecineCadenceSeen;
    }

    public boolean isSequenceExtensionSeen() { return sequenceExtensionSeen; }
    public long getPictureCodingExtensionCount() { return pictureCodingExtensionCount; }
    public long getProgressiveFrameCount() { return progressiveFrameCount; }
    public long getInterlacedFrameCount() { return interlacedFrameCount; }
    public long getFieldPictureCount() { return fieldPictureCount; }

    /** Bitstream observation only; this does not imply Android deinterlacer control or quality. */
    public String getInterlaceObservation()
    {
        if (!sequenceExtensionSeen) return "unknown";
        if (progressiveSequence) return "progressive_sequence";
        if (telecineCadenceSeen) return "interlaced_sequence_telecine";
        if (fieldPictureCount > 0L) return "interlaced_sequence_field_pictures";
        if (interlacedFrameCount > 0L) return "interlaced_sequence_interlaced_frames";
        if (progressiveFrameCount > 0L) return "interlaced_sequence_progressive_frames";
        return "interlaced_sequence";
    }


    /** Scans elementary MPEG-2 bytes without retaining sample payloads. */
    public void consume(byte[] data, int offset, int length)
    {
        if (data == null || length <= 0)
            return;
        int end = Math.min(data.length, Math.max(0, offset) + length);
        for (int i = Math.max(0, offset); i < end; i++)
        {
            int value = data[i] & 0xFF;
            consumePendingHeaderByte(value);

            rollingStartCode = (rollingStartCode << 8) | value;
            bytesConsumed++;
            if (rollingStartCode == GOP_START_CODE)
            {
                gopGeneration++;
                gopTiming(gopGeneration, true);
                lastPictureHeader = null;
            }
            else if (rollingStartCode == PICTURE_START_CODE)
            {
                pendingHeaderKind = PICTURE_START_CODE;
                pendingHeaderBytes = 0;
                pendingHeaderPosition = bytesConsumed - 4L;
                pendingHeaderGop = gopGeneration;
            }
            else if (rollingStartCode == SEQUENCE_HEADER_CODE)
            {
                pendingHeaderKind = SEQUENCE_HEADER_CODE;
                pendingHeaderBytes = 0;
                pendingHeaderPosition = bytesConsumed - 4L;
            }
            else if (rollingStartCode == EXTENSION_START_CODE)
            {
                pendingHeaderKind = EXTENSION_START_CODE;
                pendingHeaderBytes = 0;
                pendingHeaderValue = 0L;
            }
        }
    }

    /** Immutable association between one emitted sample and its MPEG-2 picture header. */
    public static final class PictureTiming
    {
        private final PictureHeader header;

        private PictureTiming(PictureHeader header)
        {
            this.header = header;
        }
    }

    /**
     * Associates one emitted H.262 sample with its picture header and records
     * the authored repeat-first-field duration for later display-time repair.
     */
    public PictureTiming observeSample(long sampleStart, long sampleEnd, long suppliedTimeUs)
    {
        PictureHeader picture = takePicture(sampleStart, sampleEnd);
        if (picture == null)
            return null;
        GopTiming gop = gopTiming(picture.gopGeneration, true);
        gop.noteDuration(picture.temporalReference, picture.displayFieldCount(progressiveSequence));
        if (picture.pictureType == PICTURE_TYPE_I && suppliedTimeUs != TIME_UNSET)
            gop.anchor(picture.temporalReference, suppliedTimeUs);
        return new PictureTiming(picture);
    }

    /** Resolves an observed sample against its GOP display-time anchor. */
    public long completeTimestamp(PictureTiming timing, long suppliedTimeUs)
    {
        lastTimestampCorrected = false;
        if (timing == null || suppliedTimeUs == TIME_UNSET || frameDurationUs <= 0.0)
            return suppliedTimeUs;
        PictureHeader picture = timing.header;
        if (picture.pictureType == PICTURE_TYPE_I || picture.authoredTimestamp)
            return suppliedTimeUs;
        GopTiming gop = gopTiming(picture.gopGeneration, false);
        if (gop == null)
            return suppliedTimeUs;
        if (!telecineCadenceSeen)
            return suppliedTimeUs;
        long displayTimeUs = gop.resolve(picture.temporalReference, fieldDurationUs());
        if (displayTimeUs == TIME_UNSET)
            return suppliedTimeUs;
        long deltaUs = displayTimeUs - suppliedTimeUs;
        long minimumRepairDeltaUs = Math.max(1L, Math.round(frameDurationUs * 0.25));
        if (Math.abs(deltaUs) < minimumRepairDeltaUs
                || Math.abs(deltaUs) > MAX_REPAIR_DELTA_US)
            return suppliedTimeUs;
        lastTimestampCorrected = true;
        return displayTimeUs;
    }

    /**
     * Returns the corrected timestamp for one TrackOutput sample range.
     * Authored timestamps and already-plausible extrapolated timestamps are returned unchanged.
     */
    public long completeTimestamp(long sampleStart, long sampleEnd, long suppliedTimeUs)
    {
        PictureTiming timing = observeSample(sampleStart, sampleEnd, suppliedTimeUs);
        return completeTimestamp(timing, suppliedTimeUs);
    }

    private PictureHeader takePicture(long sampleStart, long sampleEnd)
    {
        PictureHeader picture = null;
        while (!headers.isEmpty() && headers.peekFirst().position < sampleEnd)
        {
            PictureHeader candidate = headers.removeFirst();
            if (picture == null && candidate.position >= sampleStart)
                picture = candidate;
        }
        return picture;
    }

    private void consumePendingHeaderByte(int value)
    {
        if (pendingHeaderKind == 0)
            return;
        if (pendingHeaderKind == PICTURE_START_CODE)
        {
            if (pendingHeaderBytes == 0)
            {
                pendingByte0 = value;
                pendingHeaderBytes = 1;
                return;
            }
            int temporalReference = (pendingByte0 << 2) | (value >>> 6);
            int pictureType = (value >>> 3) & 0x07;
            if (pictureType == PICTURE_TYPE_I || pictureType == PICTURE_TYPE_P
                    || pictureType == PICTURE_TYPE_B)
            {
                if (headers.size() >= MAX_QUEUED_HEADERS)
                    headers.removeFirst();
                boolean authoredTimestamp = pendingTimestampMarker && pendingTimestampAuthored;
                lastPictureHeader = new PictureHeader(pendingHeaderPosition, pendingHeaderGop,
                        temporalReference, pictureType, authoredTimestamp);
                headers.addLast(lastPictureHeader);
                pendingTimestampMarker = false;
                pendingTimestampAuthored = false;
            }
            pendingHeaderKind = 0;
            pendingHeaderBytes = 0;
            return;
        }

        if (pendingHeaderKind == EXTENSION_START_CODE)
        {
            pendingHeaderValue = (pendingHeaderValue << 8) | (value & 0xFFL);
            pendingHeaderBytes++;
            if (pendingHeaderBytes == 5)
            {
                int extensionId = (int) ((pendingHeaderValue >>> 36) & 0x0F);
                if (extensionId == SEQUENCE_EXTENSION_ID)
                {
                    sequenceExtensionSeen = true;
                    progressiveSequence = ((pendingHeaderValue >>> 27) & 1L) != 0;
                }
                else if (extensionId == PICTURE_CODING_EXTENSION_ID
                        && lastPictureHeader != null)
                {
                    lastPictureHeader.pictureStructure =
                            (int) ((pendingHeaderValue >>> 16) & 0x03);
                    lastPictureHeader.topFieldFirst =
                            ((pendingHeaderValue >>> 15) & 1L) != 0;
                    lastPictureHeader.repeatFirstField =
                            ((pendingHeaderValue >>> 9) & 1L) != 0;
                    lastPictureHeader.progressiveFrame =
                            ((pendingHeaderValue >>> 7) & 1L) != 0;
                    pictureCodingExtensionCount++;
                    if (lastPictureHeader.pictureStructure == 1
                            || lastPictureHeader.pictureStructure == 2)
                        fieldPictureCount++;
                    else if (lastPictureHeader.progressiveFrame)
                        progressiveFrameCount++;
                    else
                        interlacedFrameCount++;
                    if (!progressiveSequence && lastPictureHeader.progressiveFrame
                            && lastPictureHeader.repeatFirstField)
                        telecineCadenceSeen = true;
                }
                pendingHeaderKind = 0;
                pendingHeaderBytes = 0;
                pendingHeaderValue = 0L;
            }
            return;
        }

        // The fourth byte following sequence_header_code contains frame_rate_code in its low nibble.
        pendingHeaderBytes++;
        if (pendingHeaderBytes == 4)
        {
            double rate = frameRateForCode(value & 0x0F);
            if (rate > 0.0)
            {
                frameRateHz = rate;
                frameDurationUs = 1_000_000.0 / rate;
            }
            pendingHeaderKind = 0;
            pendingHeaderBytes = 0;
        }
    }

    private double fieldDurationUs()
    {
        // Some NTSC DVD film streams signal 60000/1001 in sequence_header
        // while each frame-picture uses 2/3-field repeat flags. For those
        // non-progressive sequences the signalled period is already the field
        // clock. Dividing it by two again produces a false 47.95-picture/s
        // timeline from authored 23.976 material. Ordinary 25/29.97 frame-rate
        // sequence headers still require the historical half-frame field
        // duration.
        if (!progressiveSequence && telecineCadenceSeen && reportedFrameRateHz >= 45.0)
            return 1_000_000.0 / reportedFrameRateHz;
        if (!progressiveSequence && frameRateHz >= 45.0)
            return frameDurationUs;
        return frameDurationUs / 2.0;
    }

    private static double frameRateForCode(int frameRateCode)
    {
        switch (frameRateCode)
        {
            case 1: return 24_000.0 / 1_001.0;
            case 2: return 24.0;
            case 3: return 25.0;
            case 4: return 30_000.0 / 1_001.0;
            case 5: return 30.0;
            case 6: return 50.0;
            case 7: return 60_000.0 / 1_001.0;
            case 8: return 60.0;
            default: return 0.0;
        }
    }

    private static final class PictureHeader
    {
        final long position;
        final long gopGeneration;
        final int temporalReference;
        final int pictureType;
        final boolean authoredTimestamp;
        int pictureStructure = 3;
        boolean topFieldFirst;
        boolean repeatFirstField;
        boolean progressiveFrame;

        PictureHeader(long position, long gopGeneration, int temporalReference, int pictureType,
                boolean authoredTimestamp)
        {
            this.position = position;
            this.gopGeneration = gopGeneration;
            this.temporalReference = temporalReference;
            this.pictureType = pictureType;
            this.authoredTimestamp = authoredTimestamp;
        }

        int displayFieldCount(boolean progressiveSequence)
        {
            if (pictureStructure == 1 || pictureStructure == 2)
                return 1;
            if (!repeatFirstField)
                return 2;
            // DVD film material normally uses a non-progressive sequence with
            // progressive frame pictures and 3:2 repeat flags: 2,3,2,3 fields.
            // Retain the H.262 progressive-sequence rule for completeness.
            return progressiveSequence ? (topFieldFirst ? 6 : 4) : 3;
        }
    }

    private GopTiming gopTiming(long generation, boolean create)
    {
        for (GopTiming timing : gopTimings)
            if (timing.generation == generation)
                return timing;
        if (!create)
            return null;
        while (gopTimings.size() >= MAX_GOP_STATES)
            gopTimings.removeFirst();
        GopTiming timing = new GopTiming(generation);
        gopTimings.addLast(timing);
        return timing;
    }

    private static final class GopTiming
    {
        final long generation;
        final byte[] displayFields = new byte[1024];
        int anchorReference = -1;
        long anchorTimeUs = TIME_UNSET;

        GopTiming(long generation)
        {
            this.generation = generation;
        }

        void noteDuration(int temporalReference, int fields)
        {
            if (temporalReference >= 0 && temporalReference < displayFields.length)
                displayFields[temporalReference] = (byte) Math.max(1, Math.min(fields, 6));
        }

        void anchor(int temporalReference, long timeUs)
        {
            anchorReference = temporalReference;
            anchorTimeUs = timeUs;
        }

        boolean hasAnchor()
        {
            return anchorReference >= 0 && anchorTimeUs != TIME_UNSET;
        }

        long resolve(int temporalReference, double fieldDurationUs)
        {
            if (!hasAnchor())
                return TIME_UNSET;
            long fields = 0;
            if (temporalReference >= anchorReference)
            {
                for (int ref = anchorReference; ref < temporalReference; ref++)
                {
                    int duration = displayFields[ref];
                    if (duration == 0)
                    {
                        int anchorFields = displayFields[anchorReference];
                        if (anchorFields != 2 && anchorFields != 3)
                            anchorFields = 2;
                        duration = ((ref - anchorReference) & 1) == 0
                                ? anchorFields : 5 - anchorFields;
                    }
                    if (duration == 0)
                        return TIME_UNSET;
                    fields += duration;
                }
                return anchorTimeUs + Math.round(fields * fieldDurationUs);
            }
            for (int ref = temporalReference; ref < anchorReference; ref++)
            {
                int duration = displayFields[ref];
                if (duration == 0)
                {
                    int anchorFields = displayFields[anchorReference];
                    if (anchorFields != 2 && anchorFields != 3)
                        anchorFields = 2;
                    duration = ((ref - anchorReference) & 1) == 0
                            ? anchorFields : 5 - anchorFields;
                }
                if (duration == 0)
                    return TIME_UNSET;
                fields += duration;
            }
            return anchorTimeUs - Math.round(fields * fieldDurationUs);
        }
    }
}
