package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.text.SubtitleDecoderFactory;
import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleDecoderException;
import androidx.media3.extractor.text.SubtitleInputBuffer;
import androidx.media3.extractor.text.SubtitleOutputBuffer;
import opensagetv.vibe.miniclient.video.Cea708MalformedPacketPolicy;

/** Leaves all default decoders intact; contains the proven malformed-708 failure. */
@UnstableApi
final class Media3Cea708DecoderFactory implements SubtitleDecoderFactory
{
    @Override public boolean supportsFormat(Format format)
    {
        return DEFAULT.supportsFormat(format);
    }
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
                // TextRenderer already handles checked subtitle errors by
                // clearing/replacing ONLY its decoder and held buffers. Use
                // that path rather than rebuilding A/V or manually flushing
                // a decoder whose input buffer is still leased to the renderer.
                throw new SubtitleDecoderException("Malformed CEA-708 packet boundary", failure);
            }
        }
        @Override public void setPositionUs(long value) { delegate.setPositionUs(value); }
        @Override public void setOutputStartTimeUs(long value) { delegate.setOutputStartTimeUs(value); }
        @Override public void flush() { delegate.flush(); }
        @Override public void release() { delegate.release(); }
    }
}
