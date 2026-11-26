package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class PushRecoveryExtractorsFactoryTest
{
    static class PacketExtractor implements Extractor
    {
        boolean header = true, released, initialized;
        int sniffs, reads;
        long seekPosition = -1, seekTime = -1;
        @Override public boolean sniff(ExtractorInput input) { sniffs++; return header; }
        @Override public void init(ExtractorOutput output) { initialized = true; }
        @Override public int read(ExtractorInput input, PositionHolder holder) throws IOException
        { reads++; return RESULT_CONTINUE; }
        @Override public void seek(long position, long timeUs)
        { seekPosition = position; seekTime = timeUs; }
        @Override public void release() { released = true; }
    }
    static class OtherExtractor extends PacketExtractor {}
    static class Factory implements ExtractorsFactory
    {
        PacketExtractor packet;
        OtherExtractor other;
        boolean header = true, includePacket = true, invalidPacket;
        @Override public Extractor[] createExtractors()
        {
            packet = new PacketExtractor() {
                @Override public int read(ExtractorInput input, PositionHolder holder) throws IOException
                {
                    if (invalidPacket) throw new IOException("invalid subsequent packet");
                    return super.read(input, holder);
                }
            };
            packet.header = header;
            other = new OtherExtractor();
            return includePacket ? new Extractor[] {other, packet}
                    : new Extractor[] {other};
        }
    }
    private PushRecoveryExtractorsFactory wrap(Factory delegate)
    {
        return new PushRecoveryExtractorsFactory(delegate,
                extractor -> !(extractor instanceof OtherExtractor));
    }

    @Test public void recoveryRequiresSuccessfulEligibleSniff() throws Exception
    {
        Factory raw = new Factory();
        PushRecoveryExtractorsFactory factory = wrap(raw);
        assertFalse(factory.beginRecovery());
        Extractor[] initial = factory.createExtractors();
        assertTrue(initial[0].sniff(null)); // Ineligible format must not arm recovery.
        assertFalse(factory.beginRecovery());
        raw.packet.header = false;
        assertFalse(initial[1].sniff(null));
        assertFalse(factory.beginRecovery());
        raw.packet.header = true;
        assertTrue(initial[1].sniff(null));
        assertTrue(factory.beginRecovery());
    }

    @Test public void headerlessRetryUsesFreshIdentifiedReader() throws Exception
    {
        Factory raw = new Factory();
        PushRecoveryExtractorsFactory factory = wrap(raw);
        Extractor[] initial = factory.createExtractors();
        assertTrue(initial[1].sniff(null));
        PacketExtractor old = raw.packet;
        assertTrue(factory.beginRecovery());
        raw.header = false;
        // Ordinary generic sniff fails on the consumed header (failed-before condition).
        assertFalse(raw.createExtractors()[1].sniff(null));
        Extractor[] resumed = factory.createExtractors();
        assertEquals(1, resumed.length);
        assertNotSame(old, raw.packet);
        assertTrue(resumed[0].sniff(null));
        assertEquals(0, raw.packet.sniffs); // No read/peek of nonexistent initial header.
        assertTrue(raw.other.released);
        resumed[0].init(null);
        resumed[0].seek(0, 123000);
        assertEquals(Extractor.RESULT_CONTINUE, resumed[0].read(null, null));
        resumed[0].release();
        assertTrue(raw.packet.initialized);
        assertEquals(0, raw.packet.seekPosition);
        assertEquals(123000, raw.packet.seekTime);
        assertEquals(1, raw.packet.reads);
        assertTrue(raw.packet.released);
    }

    @Test public void missingIdentifiedTypeDoesNotGuessAnother() throws Exception
    {
        Factory raw = new Factory();
        PushRecoveryExtractorsFactory factory = wrap(raw);
        factory.createExtractors()[1].sniff(null);
        assertTrue(factory.beginRecovery());
        raw.includePacket = false;
        assertEquals(0, factory.createExtractors().length);
        assertTrue(raw.other.released);
    }

    @Test public void newSourceCannotInheritOldContainer() throws Exception
    {
        Factory raw = new Factory();
        PushRecoveryExtractorsFactory old = wrap(raw);
        old.createExtractors()[1].sniff(null);
        assertTrue(old.beginRecovery());
        PushRecoveryExtractorsFactory fresh = wrap(raw);
        assertFalse(fresh.beginRecovery());
        assertEquals(2, fresh.createExtractors().length);
    }

    @Test public void normalFactoryOrderAndSniffAreUnchanged() throws Exception
    {
        Factory raw = new Factory();
        Extractor[] initial = wrap(raw).createExtractors();
        assertEquals(2, initial.length);
        assertTrue(initial[0].sniff(null));
        assertTrue(initial[1].sniff(null));
        assertEquals(1, raw.other.sniffs);
        assertEquals(1, raw.packet.sniffs);
    }

    @Test public void resumedReaderStillRejectsInvalidPackets() throws Exception
    {
        Factory raw = new Factory();
        PushRecoveryExtractorsFactory factory = wrap(raw);
        factory.createExtractors()[1].sniff(null);
        assertTrue(factory.beginRecovery());
        raw.invalidPacket = true;
        Extractor resumed = factory.createExtractors()[0];
        assertTrue(resumed.sniff(null));
        try { resumed.read(null, null); fail("must not hide packet validation failure"); }
        catch (IOException expected) { assertEquals("invalid subsequent packet", expected.getMessage()); }
    }
}
