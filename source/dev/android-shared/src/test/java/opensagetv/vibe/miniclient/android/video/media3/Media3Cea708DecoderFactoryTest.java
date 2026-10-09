package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleDecoderException;
import androidx.media3.extractor.text.SubtitleInputBuffer;
import androidx.media3.extractor.text.SubtitleOutputBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class Media3Cea708DecoderFactoryTest
{
    private static class Fake implements SubtitleDecoder
    {
        IllegalStateException failure;
        SubtitleDecoderException checked;
        long position, start;
        int queued, flushed, released;
        @Override public String getName() { return "fake"; }
        @Override public SubtitleInputBuffer dequeueInputBuffer() { return null; }
        @Override public void queueInputBuffer(SubtitleInputBuffer value) { queued++; }
        @Override public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException
        {
            if (failure != null) throw failure;
            if (checked != null) throw checked;
            return null;
        }
        @Override public void setPositionUs(long value) { position = value; }
        @Override public void setOutputStartTimeUs(long value) { start = value; }
        @Override public void flush() { flushed++; }
        @Override public void release() { released++; }
    }

    @Test public void validCallsAndLifecycleRemainDelegated() throws Exception
    {
        Fake fake = new Fake();
        Media3Cea708DecoderFactory.GuardedDecoder guard = new Media3Cea708DecoderFactory.GuardedDecoder(fake);
        assertEquals("fake", guard.getName());
        assertNull(guard.dequeueInputBuffer());
        assertNull(guard.dequeueOutputBuffer());
        guard.queueInputBuffer(null);
        guard.setPositionUs(42L);
        guard.setOutputStartTimeUs(88L); assertEquals(88L, fake.start);
        guard.flush(); guard.release();
        assertEquals(1, fake.queued); assertEquals(42L, fake.position);
        assertEquals(1, fake.flushed); assertEquals(1, fake.released);
    }

    @Test public void malformedPacketUsesRenderersExistingCheckedErrorBoundary() throws Exception
    {
        Fake fake = new Fake();
        fake.failure = new IllegalStateException("packet");
        fake.failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("androidx.media3.extractor.text.cea.Cea708Decoder", "processCurrentPacket", "decoder.java", 358),
                new StackTraceElement("androidx.media3.common.util.ParsableBitArray", "assertValidOffset", "bits.java", 345)});
        try { new Media3Cea708DecoderFactory.GuardedDecoder(fake).dequeueOutputBuffer(); fail(); }
        catch (SubtitleDecoderException error) { assertSame(fake.failure, error.getCause()); }
        // The adapter must not flush a decoder behind a leased renderer buffer.
        assertEquals(0, fake.flushed); assertEquals(0, fake.released);
    }

    @Test public void unrelatedRuntimeAndCheckedFailuresAreUnchanged() throws Exception
    {
        Fake fake = new Fake();
        fake.failure = new IllegalStateException("unrelated");
        Media3Cea708DecoderFactory.GuardedDecoder guard = new Media3Cea708DecoderFactory.GuardedDecoder(fake);
        try { guard.dequeueOutputBuffer(); fail(); }
        catch (IllegalStateException error) { assertSame(fake.failure, error); }
        fake.failure = null; fake.checked = new SubtitleDecoderException("checked");
        try { guard.dequeueOutputBuffer(); fail(); }
        catch (SubtitleDecoderException error) { assertSame(fake.checked, error); }
    }
}
