package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DvdPsExtractorTrickModeTest
{
    @Test public void sparseScanDoesNotQueueMutedAudioOrItsSourceClock()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        assertTrue(state.shouldParseAudioPes());
        for (float rate : new float[] {2f, 16f, 64f, 256f, -2f, -64f, -256f})
        {
            state.queueTrickRate(0L, rate);
            state.advanceToPosition(0L);
            assertFalse(state.shouldParseAudioPes());
        }
        state.queueTrickRate(0L, 1f);
        state.advanceToPosition(0L);
        assertTrue(state.shouldParseAudioPes());
    }

    @Test public void normalClockUsesSourcePtsEvenWhenStockResumeOmitsStc()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.notePes(true, 90_000_000L, 90_000_000L, 0L);
        assertEquals(1_002_000L, state.normalSourceTimeMs(2_000_000L));
        state.beginRepreparedEpoch(0L);
        state.notePes(true, 45_000_000L, 45_000_000L, 0L);
        assertEquals(500_500L, state.normalSourceTimeMs(500_000L));
    }

    @Test public void queuedNextCellCannotReplaceCurrentPictureClock()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.notePes(true, 45_000_000L, 45_000_000L, 0L);
        state.notePes(true, 54_000_000L, 54_000_000L, 10_000_000L);
        assertEquals(509_000L, state.normalSourceTimeMs(9_000_000L));
        assertEquals(601_000L, state.normalSourceTimeMs(11_000_000L));
    }

    @Test public void highSpeedPreviewUsesActualAuthoredJumpDistance()
    {
        assertPreviewInterval(2f, 45_000_000L, 45_045_000L, 250_000L);
        assertPreviewInterval(4f, 45_000_000L, 45_045_000L, 125_000L);
        assertPreviewInterval(8f, 45_000_000L, 45_090_000L, 125_000L);
        assertPreviewInterval(16f, 45_000_000L, 45_180_000L, 125_000L);
        assertPreviewInterval(-2f, 45_045_000L, 45_000_000L, 250_000L);
        assertPreviewInterval(-4f, 45_045_000L, 45_000_000L, 125_000L);
        assertPreviewInterval(-8f, 45_090_000L, 45_000_000L, 125_000L);
        assertPreviewInterval(-16f, 45_180_000L, 45_000_000L, 125_000L);
    }

    @Test public void coarseScanDoesNotClampStockVobuJumpsToThirtyPreviewsPerSecond()
    {
        // Stock VM caps authored navigation jumps at fourteen VOBUs. A fixed
        // 33 ms dwell would cap the source search speed even with 256x flags.
        assertPreviewInterval(128f, 45_000_000L, 45_630_000L, 54_687L);
        assertPreviewInterval(256f, 45_000_000L, 45_630_000L, 27_343L);
        assertPreviewInterval(-256f, 45_630_000L, 45_000_000L, 27_343L);
        assertPreviewInterval(256f, 45_000_000L, 45_045_000L, 16_667L);
    }

    @Test public void lateScanPicturesAreReanchoredAheadOfPlayheadWithoutResettingSource()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.queueTrickRate(0, 16f);
        state.advanceToPosition(0);
        state.notePes(true, 45_000_000L, 45_000_000L, 0);
        state.nextScanVideoTimeUs();
        state.setScanPresentationFloorUs(231_033_333L);
        state.notePes(true, 45_180_000L, 45_180_000L, 0);
        long next = state.nextScanVideoTimeUs();
        assertEquals(231_033_333L, next);
        assertEquals(502_000L, state.scanSourceTimeMs(next));
    }

    @Test public void previewHandlesClockWrapAndBoundsInvalidJumps()
    {
        assertPreviewInterval(16f, (1L << 33) - 90_000L, 90_000L, 125_000L);
        assertPreviewInterval(-16f, 90_000L, (1L << 33) - 90_000L, 125_000L);
        assertPreviewInterval(16f, 45_000_000L, 45_000_000L, 33_333L);
        assertPreviewInterval(16f, 45_000_000L, 44_910_000L, 33_333L);
        assertPreviewInterval(16f, 45_000_000L, 46_800_000L, 166_667L);
        assertPreviewInterval(16f, 45_000_000L, 45_000_090L, 33_333L);
        assertPreviewInterval(2f, 45_000_000L, 45_180_000L, 500_000L);
    }

    private static void assertPreviewInterval(float rate, long from, long to, long expected)
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.queueTrickRate(0, rate);
        state.advanceToPosition(0);
        state.notePes(true, from, from, 0);
        long first = state.nextScanVideoTimeUs();
        state.notePes(true, to, to, 0);
        assertEquals(expected, state.nextScanVideoTimeUs() - first);
    }

    @Test
    public void scanClockTracksPresentedSourcePicturesRatherThanCompressedTime()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.noteVideoSample(1_000_000L, false);
        state.notePes(true, 90_000_000L, 45_000_000L, 1_000_000L);
        long first = state.nextScanVideoTimeUs();
        state.notePes(true, 90_240_000L, 45_240_000L, 1_166_667L);
        long second = state.nextScanVideoTimeUs();
        assertEquals(166_667L, second - first);
        assertEquals(500_000L, state.scanSourceTimeMs(first));
        assertEquals(500_000L, state.scanSourceTimeMs(second - 1L));
        assertEquals(502_666L, state.scanSourceTimeMs(second));
    }

    @Test
    public void forwardRateCompressesAuthoredPtsDistance()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.noteVideoSample(1_000_000L, false);
        state.queueTrickRate(0L, 4.0f);
        state.advanceToPosition(0L);

        long first = state.normalizeCellTimeUs(5_000_000L);
        state.noteVideoSample(first, false);
        long second = state.normalizeCellTimeUs(9_000_000L);

        assertEquals(1_000_001L, first);
        assertEquals(2_000_001L, second);
    }

    @Test
    public void reverseRateTurnsDecreasingPtsIntoForwardPresentationTime()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.noteVideoSample(2_000_000L, false);
        state.queueTrickRate(0L, -2.0f);
        state.advanceToPosition(0L);

        long first = state.normalizeCellTimeUs(10_000_000L);
        state.noteVideoSample(first, false);
        long second = state.normalizeCellTimeUs(8_000_000L);

        assertEquals(2_000_001L, first);
        assertEquals(3_000_001L, second);
    }
}
