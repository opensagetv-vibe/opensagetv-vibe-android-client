package opensagetv.vibe.miniclient.android.ui.keymaps;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvdHeldArrowPolicyTest
{
    @Test public void chaptersRequireDeliberateHoldAndRepeatAtBoundedIntervals()
    {
        assertFalse(DvdHeldArrowPolicy.chapterDue(999, 10_000));
        assertTrue(DvdHeldArrowPolicy.chapterDue(1_000, 1_000));
        assertFalse(DvdHeldArrowPolicy.chapterDue(2_000, 999));
        assertTrue(DvdHeldArrowPolicy.chapterDue(2_000, 1_000));
    }

    @Test public void scanAcceleratesByDurationBeyondSixteenAndCaps()
    {
        assertEquals(2, DvdHeldArrowPolicy.scanMagnitude(0));
        assertEquals(2, DvdHeldArrowPolicy.scanMagnitude(999));
        assertEquals(4, DvdHeldArrowPolicy.scanMagnitude(1000));
        assertEquals(8, DvdHeldArrowPolicy.scanMagnitude(2000));
        assertEquals(16, DvdHeldArrowPolicy.scanMagnitude(3000));
        assertEquals(32, DvdHeldArrowPolicy.scanMagnitude(4000));
        assertEquals(64, DvdHeldArrowPolicy.scanMagnitude(5000));
        assertEquals(128, DvdHeldArrowPolicy.scanMagnitude(6000));
        assertEquals(256, DvdHeldArrowPolicy.scanMagnitude(7000));
        assertEquals(256, DvdHeldArrowPolicy.scanMagnitude(Long.MAX_VALUE));
        assertEquals(2, DvdHeldArrowPolicy.scanMagnitude(-1));
    }
}
