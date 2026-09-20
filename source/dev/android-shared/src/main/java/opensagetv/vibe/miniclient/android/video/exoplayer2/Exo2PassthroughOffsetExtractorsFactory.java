package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.net.Uri;

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
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import opensagetv.vibe.miniclient.android.video.EncodedPassthroughOffsetController;

/** Legacy-Exo counterpart of the encoded-passthrough timestamp policy. */
final class Exo2PassthroughOffsetExtractorsFactory implements ExtractorsFactory
{
    private final ExtractorsFactory delegate;
    private final EncodedPassthroughOffsetController controller;

    Exo2PassthroughOffsetExtractorsFactory(ExtractorsFactory delegate,
            EncodedPassthroughOffsetController controller)
    {
        if (delegate == null) throw new IllegalArgumentException("delegate");
        if (controller == null) throw new IllegalArgumentException("controller");
        this.delegate = delegate;
        this.controller = controller;
    }

    @Override public Extractor[] createExtractors()
    {
        return wrap(delegate.createExtractors());
    }

    @Override public Extractor[] createExtractors(Uri uri,
            Map<String, List<String>> responseHeaders)
    {
        return wrap(delegate.createExtractors(uri, responseHeaders));
    }

    private Extractor[] wrap(Extractor[] extractors)
    {
        Extractor[] wrapped = new Extractor[extractors.length];
        for (int i = 0; i < extractors.length; i++)
            wrapped[i] = new OffsetExtractor(extractors[i], controller);
        return wrapped;
    }

    private static final class OffsetExtractor implements Extractor
    {
        private final Extractor delegate;
        private final EncodedPassthroughOffsetController controller;

        OffsetExtractor(Extractor delegate, EncodedPassthroughOffsetController controller)
        {
            this.delegate = delegate;
            this.controller = controller;
        }

        @Override public boolean sniff(ExtractorInput input) throws IOException
        {
            return delegate.sniff(input);
        }

        @Override public void init(final ExtractorOutput output)
        {
            delegate.init(new ExtractorOutput()
            {
                @Override public TrackOutput track(int id, int type)
                {
                    return new OffsetTrackOutput(output.track(id, type), type, controller);
                }

                @Override public void endTracks() { output.endTracks(); }
                @Override public void seekMap(SeekMap seekMap) { output.seekMap(seekMap); }
            });
        }

        @Override public int read(ExtractorInput input, PositionHolder seekPosition)
                throws IOException
        {
            return delegate.read(input, seekPosition);
        }

        @Override public void seek(long position, long timeUs)
        {
            delegate.seek(position, timeUs);
        }

        @Override public void release() { delegate.release(); }
    }

    private static final class OffsetTrackOutput implements TrackOutput
    {
        private final TrackOutput delegate;
        private final int trackType;
        private final EncodedPassthroughOffsetController controller;

        OffsetTrackOutput(TrackOutput delegate, int trackType,
                EncodedPassthroughOffsetController controller)
        {
            this.delegate = delegate;
            this.trackType = trackType;
            this.controller = controller;
        }

        @Override public void format(Format format) { delegate.format(format); }

        @Override public int sampleData(DataReader input, int length,
                boolean allowEndOfInput, int sampleDataPart) throws IOException
        {
            return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart);
        }

        @Override public void sampleData(ParsableByteArray data, int length, int sampleDataPart)
        {
            delegate.sampleData(data, length, sampleDataPart);
        }

        @Override public void sampleMetadata(long timeUs, int flags, int size,
                int offset, CryptoData cryptoData)
        {
            long shifted = trackType == C.TRACK_TYPE_AUDIO
                    ? controller.shiftAudioSampleTimeUs(timeUs)
                    : trackType == C.TRACK_TYPE_VIDEO
                    ? controller.shiftVideoSampleTimeUs(timeUs) : timeUs;
            delegate.sampleMetadata(shifted, flags, size, offset, cryptoData);
        }
    }
}
