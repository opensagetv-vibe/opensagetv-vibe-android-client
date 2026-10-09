package opensagetv.vibe.miniclient.android.video.exoplayer2;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.text.SubtitleDecoderFactory;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import opensagetv.vibe.miniclient.video.Cea708MalformedPacketPolicy;

/** Legacy Exo's own checked subtitle-error path, not a Media3 dependency. */
final class Exo2Cea708DecoderFactory implements SubtitleDecoderFactory
{
    @Override public boolean supportsFormat(Format format) { return DEFAULT.supportsFormat(format); }
    @Override public SubtitleDecoder createDecoder(Format format)
    {
        SubtitleDecoder decoder = DEFAULT.createDecoder(format);
        return MimeTypes.APPLICATION_CEA708.equals(format.sampleMimeType)
                ? new GuardedDecoder(decoder) : decoder;
    }
    static final class GuardedDecoder implements SubtitleDecoder
    {
        private final SubtitleDecoder delegate;
        GuardedDecoder(SubtitleDecoder delegate) { this.delegate = delegate; }
        @Override public String getName() { return delegate.getName(); }
        @Override public SubtitleInputBuffer dequeueInputBuffer() throws SubtitleDecoderException
        { return delegate.dequeueInputBuffer(); }
        @Override public void queueInputBuffer(SubtitleInputBuffer input) throws SubtitleDecoderException
        { delegate.queueInputBuffer(input); }
        @Override public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException
        {
            try { return delegate.dequeueOutputBuffer(); }
            catch (IllegalStateException failure)
            {
                if (!Cea708MalformedPacketPolicy.isMalformedPacket(failure)) throw failure;
                // Legacy TextRenderer releases its held buffers and replaces
                // only this subtitle decoder when given a checked failure.
                throw new SubtitleDecoderException("Malformed CEA-708 packet boundary", failure);
            }
        }
        @Override public void setPositionUs(long value) { delegate.setPositionUs(value); }
        @Override public void flush() { delegate.flush(); }
        @Override public void release() { delegate.release(); }
    }
}
