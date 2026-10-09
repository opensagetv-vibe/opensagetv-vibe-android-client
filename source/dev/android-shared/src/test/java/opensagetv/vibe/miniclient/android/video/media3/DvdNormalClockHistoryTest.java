package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.C;
import org.junit.Test;
import static org.junit.Assert.*;

public class DvdNormalClockHistoryTest
{
    @Test public void readAheadMustNotSelectAnUnreadFutureClock()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        // Changing source/presentation mappings model timestamp repair. Queue
        // Queue repaired PES metadata ahead of the first displayed second.
        for (int index = 0; index < 30; index++)
            state.notePes(true, 9_000_000L + index * 3000L,
                    9_000_000L + index * 3000L, index * 41_667L);
        // The most recent reached marker is index 23, never the unread tail.
        // Source 100766ms minus presentation 958ms, plus current 1000ms.
        assertEquals(100808L, state.normalSourceTimeMs(1_000_000L));
        assertTrue(state.describeNormalClock(1_000_000L).contains("count=30,capacity=64"));
    }

    @Test public void futureOnlyMetadataMustNotInventAClock()
    {
        DvdPsExtractor.TimestampState state = new DvdPsExtractor.TimestampState();
        state.notePes(true, 9_000_000L, 9_000_000L, 1_000_000L);
        assertEquals(C.TIME_UNSET, state.normalSourceTimeMs(0L));
    }
}
