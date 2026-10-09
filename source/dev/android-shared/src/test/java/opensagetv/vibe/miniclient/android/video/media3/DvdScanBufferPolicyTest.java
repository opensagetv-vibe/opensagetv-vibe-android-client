package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DvdScanBufferPolicyTest
{
    private static final int CAPACITY = 4 * 1024 * 1024;

    @Test public void capsEmptyPipeAndAccountsForQueuedBytes()
    {
        assertEquals(1024 * 1024, DvdScanBufferPolicy.available(CAPACITY, CAPACITY));
        assertEquals(896 * 1024,
                DvdScanBufferPolicy.available(CAPACITY - 128 * 1024, CAPACITY));
        assertEquals(0, DvdScanBufferPolicy.available(CAPACITY - 2 * 1024 * 1024, CAPACITY));
        assertEquals(0, DvdScanBufferPolicy.available(0, CAPACITY));
    }

    @Test public void preservesEosAndCannotExceedPhysicalSpace()
    {
        assertEquals(-1, DvdScanBufferPolicy.available(-1, CAPACITY));
        assertEquals(-2, DvdScanBufferPolicy.available(-2, CAPACITY));
        assertEquals(100, DvdScanBufferPolicy.available(100, 100));
    }

    @Test public void virtualSkipHasSmallerReserveThanSustainedScan()
    {
        assertEquals(256 * 1024, DvdScanBufferPolicy.available(CAPACITY, CAPACITY, true));
        assertEquals(0, DvdScanBufferPolicy.available(CAPACITY - 512 * 1024, CAPACITY, true));
        assertEquals(512 * 1024,
                DvdScanBufferPolicy.available(CAPACITY - 512 * 1024, CAPACITY, false));
    }


}
