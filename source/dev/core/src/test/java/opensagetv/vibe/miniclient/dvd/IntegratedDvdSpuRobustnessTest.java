package opensagetv.vibe.miniclient.dvd;

import java.util.Random;

import org.junit.Test;

/** Fuzz-style boundedness gate for the exact SPU core used by Android. */
public final class IntegratedDvdSpuRobustnessTest
{
    @Test
    public void malformedFragmentsRemainBoundedAndDoNotEscape() throws Exception
    {
        Random random = new Random(0x534147455456L);
        DvdDiagnostics diagnostics = new DvdDiagnostics();
        DvdSpuAssembler assembler = new DvdSpuAssembler(diagnostics);
        DvdSpuDecoder decoder = new DvdSpuDecoder(diagnostics);

        for (int i = 0; i < 5000; i++)
        {
            byte[] bytes = new byte[random.nextInt(1024)];
            random.nextBytes(bytes);
            int stream = 0x20 + random.nextInt(32);
            for (DvdSpuPacket packet : assembler.feed(
                    new DvdSpuPacket(stream, random.nextInt(100000), bytes)))
                decoder.decode(packet);
            if (bytes.length >= 4)
                decoder.decode(new DvdSpuPacket(stream, 0, bytes));
            TestData.check(assembler.pendingBytes() <= 32 * 128 * 1024,
                    "bounded SPU assembler");
        }
    }
}
