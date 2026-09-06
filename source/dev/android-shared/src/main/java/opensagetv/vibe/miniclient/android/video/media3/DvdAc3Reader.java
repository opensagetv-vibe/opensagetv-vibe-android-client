package opensagetv.vibe.miniclient.android.video.media3;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Ac3Util;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.ElementaryStreamReader;
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator;

import java.util.Objects;

/**
 * AC-3 elementary-stream reader that validates DVD sync candidates before emitting samples.
 *
 * <p>Media3's stock reader commits to every {@code 0B 77} byte pair, writes its 128-byte
 * candidate header to the decoder, and only then knows the complete frame size. DVD private
 * streams can contain false sync pairs. On older Fire TV Dolby decoders a single malformed
 * access unit is fatal. This reader rejects invalid AC-3 header fields and re-scans the already
 * buffered bytes without exposing them to {@link TrackOutput}.</p>
 */
@UnstableApi
final class DvdAc3Reader implements ElementaryStreamReader
{
    private static final String TAG = "VibeDvdAc3Reader";
    private static final int STATE_FINDING_SYNC = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_SAMPLE = 2;
    private static final int HEADER_SIZE = 128;

    private final ParsableBitArray headerBits = new ParsableBitArray(new byte[HEADER_SIZE]);
    private final ParsableByteArray headerBytes = new ParsableByteArray(headerBits.data);
    private final String containerMimeType;
    private final DvdPsExtractor.TimestampState timestampState;

    @Nullable private String formatId;
    @Nullable private TrackOutput output;
    @Nullable private Format format;
    private int state = STATE_FINDING_SYNC;
    private int bytesRead;
    private boolean lastByteWas0B;
    private int sampleSize;
    private long sampleDurationUs;
    private long timeUs = C.TIME_UNSET;
    private int rejectedSyncCount;
    private int emittedSampleCount;

    DvdAc3Reader(String containerMimeType, DvdPsExtractor.TimestampState timestampState)
    {
        this.containerMimeType = containerMimeType;
        this.timestampState = timestampState;
    }

    @Override
    public void seek()
    {
        state = STATE_FINDING_SYNC;
        bytesRead = 0;
        lastByteWas0B = false;
        timeUs = C.TIME_UNSET;
    }

    /** Discards an incomplete/false candidate at a DVD-declared access-unit boundary. */
    void alignToAccessUnit()
    {
        state = STATE_FINDING_SYNC;
        bytesRead = 0;
        lastByteWas0B = false;
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator)
    {
        idGenerator.generateNewId();
        formatId = idGenerator.getFormatId();
        output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
    }

    @Override
    public void packetStarted(long pesTimeUs, int flags)
    {
        // A PES packet is allowed to omit PTS. Preserve the running AC-3 frame
        // clock in that case instead of replacing it with TIME_UNSET/zero. DVD
        // NEWCELL applies the SageTV DVD VM's timeline offset before this method.
        // A large forward PTS is therefore an intentional title/cell position,
        // not corruption, and must be accepted just as the video PTS is. Ignore
        // only backwards values so an old packet cannot rewind AudioTrack.
        if (pesTimeUs == C.TIME_UNSET)
            return;
        if (timeUs == C.TIME_UNSET)
        {
            timeUs = pesTimeUs;
            return;
        }
        long deltaUs = pesTimeUs - timeUs;
        if (deltaUs > 0)
            timeUs = pesTimeUs;
    }

    @Override
    public void consume(ParsableByteArray data)
    {
        if (output == null)
            throw new IllegalStateException("createTracks must be called before consume");

        while (data.bytesLeft() > 0)
        {
            if (state == STATE_FINDING_SYNC)
            {
                if (skipToNextSync(data))
                {
                    state = STATE_READING_HEADER;
                    headerBytes.getData()[0] = 0x0B;
                    headerBytes.getData()[1] = 0x77;
                    bytesRead = 2;
                }
            }
            else if (state == STATE_READING_HEADER)
            {
                if (continueRead(data, headerBytes.getData(), HEADER_SIZE))
                {
                    if (!isValidAc3Header(headerBytes.getData()))
                    {
                        rejectCandidateAndRescan();
                        continue;
                    }
                    parseHeader();
                    headerBytes.setPosition(0);
                    output.sampleData(headerBytes, HEADER_SIZE);
                    state = STATE_READING_SAMPLE;
                }
            }
            else
            {
                int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
                output.sampleData(data, bytesToRead);
                bytesRead += bytesToRead;
                if (bytesRead == sampleSize)
                {
                    if (timeUs == C.TIME_UNSET)
                        timeUs = 0;
                    output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
                    timestampState.noteAudioSample(timeUs);
                    emittedSampleCount++;
                    if (emittedSampleCount <= 4)
                        Log.i(TAG, "Emitted DVD AC-3 sample=" + emittedSampleCount
                                + " size=" + sampleSize + " timeUs=" + timeUs);
                    timeUs += sampleDurationUs;
                    state = STATE_FINDING_SYNC;
                }
            }
        }
    }

    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength)
    {
        int count = Math.min(source.bytesLeft(), targetLength - bytesRead);
        source.readBytes(target, bytesRead, count);
        bytesRead += count;
        return bytesRead == targetLength;
    }

    private boolean skipToNextSync(ParsableByteArray data)
    {
        while (data.bytesLeft() > 0)
        {
            int value = data.readUnsignedByte();
            if (lastByteWas0B && value == 0x77)
            {
                lastByteWas0B = false;
                return true;
            }
            lastByteWas0B = value == 0x0B;
        }
        return false;
    }

    /** DVD-Video private streams carry AC-3, not E-AC-3. */
    static boolean isValidAc3Header(byte[] header)
    {
        if (header == null || header.length < 6 || header[0] != 0x0B || header[1] != 0x77)
            return false;
        int fscod = (header[4] & 0xC0) >>> 6;
        int frameSizeCode = header[4] & 0x3F;
        int bitstreamId = (header[5] & 0xF8) >>> 3;
        return fscod < 3 && frameSizeCode <= 37 && bitstreamId <= 10;
    }

    private void rejectCandidateAndRescan()
    {
        rejectedSyncCount++;
        if (rejectedSyncCount <= 4 || (rejectedSyncCount & (rejectedSyncCount - 1)) == 0)
            Log.w(TAG, "Rejected false DVD AC-3 sync candidate count=" + rejectedSyncCount);

        byte[] bytes = headerBytes.getData();
        int next = -1;
        for (int i = 2; i + 1 < HEADER_SIZE; i++)
        {
            if (bytes[i] == 0x0B && bytes[i + 1] == 0x77)
            {
                next = i;
                break;
            }
        }
        if (next >= 0)
        {
            int remaining = HEADER_SIZE - next;
            System.arraycopy(bytes, next, bytes, 0, remaining);
            bytesRead = remaining;
            state = STATE_READING_HEADER;
            lastByteWas0B = false;
        }
        else
        {
            lastByteWas0B = bytes[HEADER_SIZE - 1] == 0x0B;
            bytesRead = 0;
            state = STATE_FINDING_SYNC;
        }
    }

    private void parseHeader()
    {
        headerBits.setPosition(0);
        Ac3Util.SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(headerBits);
        if (format == null
                || frameInfo.channelCount != format.channelCount
                || frameInfo.sampleRate != format.sampleRate
                || !Objects.equals(frameInfo.mimeType, format.sampleMimeType))
        {
            Format.Builder builder = new Format.Builder()
                    .setId(formatId)
                    .setContainerMimeType(containerMimeType)
                    .setSampleMimeType(frameInfo.mimeType)
                    .setChannelCount(frameInfo.channelCount)
                    .setSampleRate(frameInfo.sampleRate)
                    .setPeakBitrate(frameInfo.bitrate);
            if (MimeTypes.AUDIO_AC3.equals(frameInfo.mimeType))
                builder.setAverageBitrate(frameInfo.bitrate);
            format = builder.build();
            output.format(format);
        }
        sampleSize = frameInfo.frameSize;
        sampleDurationUs = C.MICROS_PER_SECOND * frameInfo.sampleCount / format.sampleRate;
    }
}
