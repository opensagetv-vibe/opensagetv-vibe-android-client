package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class Cea708MalformedPacketPolicyTest
{
    private IllegalStateException failure(String parser, String bits)
    {
        IllegalStateException value = new IllegalStateException();
        value.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(parser, "processCurrentPacket", "decoder.java", 358),
                new StackTraceElement(bits, "assertValidOffset", "bits.java", 345)});
        return value;
    }

    @Test public void recognizesObservedMedia3Failure()
    {
        assertTrue(Cea708MalformedPacketPolicy.isMalformedPacket(failure(
                "androidx.media3.extractor.text.cea.Cea708Decoder",
                "androidx.media3.common.util.ParsableBitArray")));
    }
    @Test public void recognizesEquivalentLegacyFailure()
    {
        assertTrue(Cea708MalformedPacketPolicy.isMalformedPacket(failure(
                "com.google.android.exoplayer2.text.cea.Cea708Decoder",
                "com.google.android.exoplayer2.util.ParsableBitArray")));
    }
    @Test public void doesNotHideOtherDecodersOrProgrammingErrors()
    {
        assertFalse(Cea708MalformedPacketPolicy.isMalformedPacket(new IllegalStateException()));
        assertFalse(Cea708MalformedPacketPolicy.isMalformedPacket(failure(
                "androidx.media3.extractor.text.cea.Cea608Decoder",
                "androidx.media3.common.util.ParsableBitArray")));
        assertFalse(Cea708MalformedPacketPolicy.isMalformedPacket(failure(
                "androidx.media3.extractor.text.cea.Cea708Decoder", "application.BitReader")));
    }
    @Test public void doesNotCombineDifferentBackendStackFrames()
    {
        assertFalse(Cea708MalformedPacketPolicy.isMalformedPacket(failure(
                "androidx.media3.extractor.text.cea.Cea708Decoder",
                "com.google.android.exoplayer2.util.ParsableBitArray")));
    }
}
