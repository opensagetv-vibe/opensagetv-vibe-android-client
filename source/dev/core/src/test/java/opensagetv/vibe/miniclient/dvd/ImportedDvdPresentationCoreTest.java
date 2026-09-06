package opensagetv.vibe.miniclient.dvd;

import org.junit.Test;

/** Runs the imported presentation engine's deterministic regression corpus in Gradle. */
public final class ImportedDvdPresentationCoreTest
{
    @Test
    public void presentationCoreSelfTestsPass() throws Exception
    {
        String[] args = new String[0];
        DvdSpuAssemblerSelfTest.main(args);
        DvdSpuDecoderSelfTest.main(args);
        DvdRleRoundTripSelfTest.main(args);
        DvdSpuCompositorSelfTest.main(args);
        DvdAudioStreamCodeSelfTest.main(args);
        DvdPtsClockSelfTest.main(args);
    }
}
