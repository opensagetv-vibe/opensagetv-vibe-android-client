package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EncodedPassthroughOffsetControllerTest
{
    @Test
    public void disabledControllerDoesNotShiftEitherClock()
    {
        EncodedPassthroughOffsetController controller =
                new EncodedPassthroughOffsetController(false, 500);

        assertEquals(1_000_000L, controller.shiftAudioSampleTimeUs(1_000_000L));
        assertEquals(1_000_000L, controller.shiftVideoSampleTimeUs(1_000_000L));
        assertEquals(0L, controller.getAudioSamplesShifted());
        assertEquals(0L, controller.getVideoSamplesShifted());
    }

    @Test
    public void positiveOffsetDelaysOnlyEncodedAudioTimestamp()
    {
        EncodedPassthroughOffsetController controller =
                new EncodedPassthroughOffsetController(true, 500);

        assertEquals(1_500_000L, controller.shiftAudioSampleTimeUs(1_000_000L));
        assertEquals(1_000_000L, controller.shiftVideoSampleTimeUs(1_000_000L));
        assertEquals(1L, controller.getAudioSamplesShifted());
        assertEquals(0L, controller.getVideoSamplesShifted());
    }

    @Test
    public void negativeOffsetDelaysOnlyVideoTimestamp()
    {
        EncodedPassthroughOffsetController controller =
                new EncodedPassthroughOffsetController(true, -500);

        assertEquals(1_000_000L, controller.shiftAudioSampleTimeUs(1_000_000L));
        assertEquals(1_500_000L, controller.shiftVideoSampleTimeUs(1_000_000L));
        assertEquals(0L, controller.getAudioSamplesShifted());
        assertEquals(1L, controller.getVideoSamplesShifted());
    }

    @Test
    public void offsetIsClampedAndUnsetTimestampsRemainUnset()
    {
        EncodedPassthroughOffsetController controller =
                new EncodedPassthroughOffsetController(true, 9_000);

        assertEquals(4_000, controller.getOffsetMillis());
        assertEquals(Long.MIN_VALUE, controller.shiftAudioSampleTimeUs(Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE + 1L,
                controller.shiftAudioSampleTimeUs(Long.MIN_VALUE + 1L));
        controller.setOffsetMillis(-9_000);
        assertEquals(-4_000, controller.getOffsetMillis());
    }

    @Test
    public void enableStateAndDiagnosticDescriptionTrackSessionChanges()
    {
        EncodedPassthroughOffsetController controller =
                new EncodedPassthroughOffsetController(false, 25);

        assertFalse(controller.isEnabled());
        assertTrue(controller.describe().contains("disabled"));
        controller.setEnabled(true);
        assertTrue(controller.isEnabled());
        assertTrue(controller.describe().contains("25 ms"));
        assertTrue(controller.describe().contains("audio delayed"));
    }
}
