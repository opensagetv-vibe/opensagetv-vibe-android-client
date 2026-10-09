package opensagetv.vibe.miniclient.android.video.exoplayer2;

import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class Exo2Cea708DecoderFactoryTest
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

        @Override public void flush() { flushed++; }
        @Override public void release() { released++; }
    }

    @Test public void validCallsAndLifecycleRemainDelegated() throws Exception
    {
        Fake fake = new Fake();
        Exo2Cea708DecoderFactory.GuardedDecoder guard = new Exo2Cea708DecoderFactory.GuardedDecoder(fake);
        assertEquals("fake", guard.getName());
        assertNull(guard.dequeueInputBuffer());
        assertNull(guard.dequeueOutputBuffer());
        guard.queueInputBuffer(null);
        guard.setPositionUs(42L);

        guard.flush(); guard.release();
        assertEquals(1, fake.queued); assertEquals(42L, fake.position);
        assertEquals(1, fake.flushed); assertEquals(1, fake.released);
    }

    @Test public void malformedPacketUsesRenderersExistingCheckedErrorBoundary() throws Exception
    {
        Fake fake = new Fake();
        fake.failure = new IllegalStateException("packet");
        fake.failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.google.android.exoplayer2.text.cea.Cea708Decoder", "processCurrentPacket", "decoder.java", 358),
                new StackTraceElement("com.google.android.exoplayer2.util.ParsableBitArray", "assertValidOffset", "bits.java", 345)});
        try { new Exo2Cea708DecoderFactory.GuardedDecoder(fake).dequeueOutputBuffer(); fail(); }
        catch (SubtitleDecoderException error) { assertSame(fake.failure, error.getCause()); }
        // The adapter must not flush a decoder behind a leased renderer buffer.
        assertEquals(0, fake.flushed); assertEquals(0, fake.released);
    }

    @Test public void unrelatedRuntimeAndCheckedFailuresAreUnchanged() throws Exception
    {
        Fake fake = new Fake();
        fake.failure = new IllegalStateException("unrelated");
        Exo2Cea708DecoderFactory.GuardedDecoder guard = new Exo2Cea708DecoderFactory.GuardedDecoder(fake);
        try { guard.dequeueOutputBuffer(); fail(); }
        catch (IllegalStateException error) { assertSame(fake.failure, error); }
        fake.failure = null; fake.checked = new SubtitleDecoderException("checked");
        try { guard.dequeueOutputBuffer(); fail(); }
        catch (SubtitleDecoderException error) { assertSame(fake.checked, error); }
    }
}
