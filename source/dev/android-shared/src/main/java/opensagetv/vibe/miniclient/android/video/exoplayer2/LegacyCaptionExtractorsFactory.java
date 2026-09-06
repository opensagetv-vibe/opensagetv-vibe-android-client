package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;
import opensagetv.vibe.miniclient.video.Mpeg2PictureTimestampCompleter;

/** Legacy ExoPlayer equivalent of the Media3 raw-caption tap. */
final class LegacyCaptionExtractorsFactory implements ExtractorsFactory
{
    private final ExtractorsFactory delegate;
    private final LegacyExtenderCaptionBridge bridge;
    private final Mpeg2PictureTimestampCompleter mpeg2Observer;

    LegacyCaptionExtractorsFactory(ExtractorsFactory delegate,
            LegacyExtenderCaptionBridge bridge, Mpeg2PictureTimestampCompleter mpeg2Observer)
    {
        this.delegate = delegate;
        this.bridge = bridge;
        this.mpeg2Observer = mpeg2Observer;
    }

    @Override public Extractor[] createExtractors() { return wrap(delegate.createExtractors()); }
    @Override public Extractor[] createExtractors(Uri uri, Map<String, List<String>> headers)
    { return wrap(delegate.createExtractors(uri, headers)); }

    private Extractor[] wrap(Extractor[] extractors)
    {
        Extractor[] result = new Extractor[extractors.length];
        for (int i = 0; i < extractors.length; i++)
            result[i] = new ObservingExtractor(extractors[i], bridge, mpeg2Observer);
        return result;
    }

    private static final class ObservingExtractor implements Extractor
    {
        final Extractor delegate;
        final LegacyExtenderCaptionBridge bridge;
        final Mpeg2PictureTimestampCompleter mpeg2Observer;
        final List<ObservingTrackOutput> captionTracks =
                new ArrayList<ObservingTrackOutput>();
        ObservingExtractor(Extractor delegate, LegacyExtenderCaptionBridge bridge,
                Mpeg2PictureTimestampCompleter mpeg2Observer)
        { this.delegate = delegate; this.bridge = bridge; this.mpeg2Observer = mpeg2Observer; }
        @Override public boolean sniff(ExtractorInput input) throws IOException { return delegate.sniff(input); }
        @Override public int read(ExtractorInput input, PositionHolder holder) throws IOException { return delegate.read(input, holder); }
        @Override public void release() { delegate.release(); }
        @Override public void seek(long position, long timeUs)
        {
            // Never splice a partial sample retained before seek onto the
            // first CEA sample emitted at the new extractor position.
            for (ObservingTrackOutput track : captionTracks) track.resetPending();
            bridge.flush();
            delegate.seek(position, timeUs);
        }
        @Override public void init(final ExtractorOutput output)
        {
            captionTracks.clear();
            delegate.init(new ExtractorOutput()
            {
                @Override public TrackOutput track(int id, int type)
                {
                    TrackOutput track = output.track(id, type);
                    if (type == C.TRACK_TYPE_TEXT)
                    {
                        ObservingTrackOutput observing = new ObservingTrackOutput(track, bridge);
                        captionTracks.add(observing);
                        return observing;
                    }
                    if (type == C.TRACK_TYPE_VIDEO) return new ObservingVideoTrackOutput(track, mpeg2Observer);
                    return track;
                }
                @Override public void endTracks() { output.endTracks(); }
                @Override public void seekMap(SeekMap seekMap) { output.seekMap(seekMap); }
            });
        }
    }

    private static final class ObservingVideoTrackOutput implements TrackOutput
    {
        final TrackOutput delegate;
        final Mpeg2PictureTimestampCompleter observer;
        boolean mpeg2;
        ObservingVideoTrackOutput(TrackOutput delegate, Mpeg2PictureTimestampCompleter observer)
        { this.delegate = delegate; this.observer = observer; }
        @Override public void format(Format format)
        {
            mpeg2 = MimeTypes.VIDEO_MPEG2.equals(format.sampleMimeType);
            if (mpeg2) observer.setFrameRate(format.frameRate);
            delegate.format(format);
        }
        @Override public int sampleData(final DataReader input, int length,
                boolean allowEndOfInput, int part) throws IOException
        {
            if (!mpeg2 || part != SAMPLE_DATA_PART_MAIN)
                return delegate.sampleData(input, length, allowEndOfInput, part);
            return delegate.sampleData(new DataReader()
            {
                @Override public int read(byte[] buffer, int offset, int readLength) throws IOException
                {
                    int count = input.read(buffer, offset, readLength);
                    if (count > 0) observer.consume(buffer, offset, count);
                    return count;
                }
            }, length, allowEndOfInput, part);
        }
        @Override public void sampleData(ParsableByteArray data, int length, int part)
        {
            if (mpeg2 && part == SAMPLE_DATA_PART_MAIN)
                observer.consume(data.getData(), data.getPosition(), length);
            delegate.sampleData(data, length, part);
        }
        @Override public void sampleMetadata(long timeUs, int flags, int size, int offset,
                @Nullable CryptoData cryptoData)
        { delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData); }
    }

    private static final class ObservingTrackOutput implements TrackOutput
    {
        final TrackOutput delegate;
        final LegacyExtenderCaptionBridge bridge;
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        boolean cea;
        ObservingTrackOutput(TrackOutput delegate, LegacyExtenderCaptionBridge bridge)
        { this.delegate = delegate; this.bridge = bridge; }
        @Override public void format(Format format)
        {
            cea = MimeTypes.APPLICATION_CEA608.equals(format.sampleMimeType)
                    || MimeTypes.APPLICATION_CEA708.equals(format.sampleMimeType);
            delegate.format(format);
        }
        @Override public int sampleData(final DataReader input, int length,
                boolean allowEndOfInput, int part) throws IOException
        {
            if (!cea || part != SAMPLE_DATA_PART_MAIN)
                return delegate.sampleData(input, length, allowEndOfInput, part);
            return delegate.sampleData(new DataReader()
            {
                @Override public int read(byte[] buffer, int offset, int readLength) throws IOException
                {
                    int count = input.read(buffer, offset, readLength);
                    if (count > 0) pending.write(buffer, offset, count);
                    return count;
                }
            }, length, allowEndOfInput, part);
        }
        @Override public void sampleData(ParsableByteArray data, int length, int part)
        {
            if (cea && part == SAMPLE_DATA_PART_MAIN)
                pending.write(data.getData(), data.getPosition(), length);
            delegate.sampleData(data, length, part);
        }
        @Override public void sampleMetadata(long timeUs, int flags, int size, int offset,
                @Nullable CryptoData cryptoData)
        {
            if (cea) publish(timeUs, size, offset);
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData);
        }
        private void publish(long timeUs, int size, int offset)
        {
            byte[] all = pending.toByteArray();
            int end = Math.max(0, all.length - Math.max(0, offset));
            int start = Math.max(0, end - Math.max(0, size));
            if (end > start)
            {
                byte[] sample = new byte[end - start];
                System.arraycopy(all, start, sample, 0, sample.length);
                bridge.onCeaSample(timeUs, sample);
            }
            pending.reset();
            if (offset > 0 && end < all.length) pending.write(all, end, all.length - end);
        }
        void resetPending() { pending.reset(); }
    }
}
