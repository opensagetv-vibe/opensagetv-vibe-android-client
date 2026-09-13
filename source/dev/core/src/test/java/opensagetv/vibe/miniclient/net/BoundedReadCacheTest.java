package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BoundedReadCacheTest
{
    @Test
    public void readsContainedRangeAndTracksHits()
    {
        BoundedReadCache cache = new BoundedReadCache(16);
        cache.put(100, new byte[] {1, 2, 3, 4}, 4);
        byte[] out = new byte[2];
        assertEquals(2, cache.read(101, out, 0, 2));
        assertArrayEquals(new byte[] {2, 3}, out);
        assertEquals(2, cache.getHitBytes());
    }

    @Test
    public void missesAndEvictsLeastRecentlyUsedRanges()
    {
        BoundedReadCache cache = new BoundedReadCache(8);
        cache.put(0, new byte[] {1, 2, 3, 4}, 4);
        cache.put(10, new byte[] {5, 6, 7, 8}, 4);
        assertEquals(1, cache.read(0, new byte[1], 0, 1));
        cache.put(20, new byte[] {9, 10, 11, 12}, 4);
        assertEquals(0, cache.read(10, new byte[1], 0, 1));
        assertEquals(1, cache.read(0, new byte[1], 0, 1));
        assertEquals(8, cache.getResidentBytes());
        assertEquals(1, cache.getMissCount());
    }

    @Test
    public void clearDropsResidentDataWithoutRewritingHistory()
    {
        BoundedReadCache cache = new BoundedReadCache(8);
        cache.put(0, new byte[] {1, 2, 3, 4}, 4);
        assertEquals(1, cache.read(0, new byte[1], 0, 1));
        cache.clear();
        assertEquals(0, cache.getResidentBytes());
        assertEquals(1, cache.getHitBytes());
    }
}
