package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GrowingSeekSnapshotTest
{
    @Test public void disabledSourceRemainsOpenEnded()
    {
        assertEquals(-1L, new GrowingSeekSnapshot().resolveExtent(true, 100L));
    }

    @Test public void growingPcrProbeDoesNotChaseTheLiveEdge()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        assertEquals(60000000L, snapshot.resolveExtent(true, 60000000L));
        assertEquals(60000000L, snapshot.resolveExtent(true, 61000000L));
        assertEquals(60000000L, snapshot.resolveExtent(true, 62000000L));
    }

    @Test public void temporarySizeRegressionDoesNotMoveAnAcceptedBoundary()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        snapshot.resolveExtent(true, 60000000L);
        assertEquals(60000000L, snapshot.resolveExtent(true, 59000000L));
    }

    @Test public void endRestoresUnknownLengthForLaterAppends()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        snapshot.resolveExtent(true, 60000000L);
        snapshot.end();
        assertEquals(-1L, snapshot.resolveExtent(true, 90000000L));
    }

    @Test public void anotherPreparationGetsItsOwnCurrentExtent()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        snapshot.resolveExtent(true, 100L);
        snapshot.begin();
        assertEquals(200L, snapshot.resolveExtent(true, 200L));
    }

    @Test public void unknownSizeWaitsForARealObservation()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        assertEquals(-1L, snapshot.resolveExtent(true, -1L));
        assertEquals(100L, snapshot.resolveExtent(true, 100L));
    }

    @Test public void completedAndEmptyFilesAreNotInventedGrowingData()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        assertEquals(-1L, snapshot.resolveExtent(false, 100L));
        assertEquals(0L, snapshot.resolveExtent(true, 0L));
    }

    @Test public void largeRecordingsKeepTheirFullLongExtent()
    {
        GrowingSeekSnapshot snapshot = new GrowingSeekSnapshot();
        snapshot.begin();
        assertEquals(3500000000L, snapshot.resolveExtent(true, 3500000000L));
    }
}
