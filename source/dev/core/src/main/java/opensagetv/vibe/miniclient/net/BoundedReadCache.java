package opensagetv.vibe.miniclient.net;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small access-ordered byte-range cache used only for stable completed files. */
public final class BoundedReadCache
{
    private final long maximumBytes;
    private final LinkedHashMap<Long, byte[]> ranges =
            new LinkedHashMap<Long, byte[]>(16, 0.75f, true);
    private long residentBytes;
    private long hitBytes;
    private long missCount;

    public BoundedReadCache(long maximumBytes)
    {
        if (maximumBytes <= 0L) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    /** Returns copied bytes, or zero when no cached range contains position. */
    public synchronized int read(long position, byte[] destination, int offset, int length)
    {
        if (length <= 0) return 0;
        Long matchedKey = null;
        byte[] matched = null;
        for (Map.Entry<Long, byte[]> entry : ranges.entrySet())
        {
            long start = entry.getKey();
            byte[] bytes = entry.getValue();
            if (position >= start && position < start + bytes.length)
            {
                matchedKey = start;
                matched = bytes;
                break;
            }
        }
        if (matched == null)
        {
            missCount++;
            return 0;
        }

        // Touch the entry after iteration so access ordering cannot mutate the
        // map while its iterator is active.
        ranges.get(matchedKey);
        int sourceOffset = (int) (position - matchedKey);
        int copied = Math.min(length, matched.length - sourceOffset);
        System.arraycopy(matched, sourceOffset, destination, offset, copied);
        hitBytes += copied;
        return copied;
    }

    public synchronized void put(long position, byte[] source, int length)
    {
        if (position < 0L || length <= 0 || length > source.length) return;
        byte[] copy = Arrays.copyOf(source, length);
        byte[] previous = ranges.put(position, copy);
        if (previous != null) residentBytes -= previous.length;
        residentBytes += copy.length;
        Iterator<Map.Entry<Long, byte[]>> iterator = ranges.entrySet().iterator();
        while (residentBytes > maximumBytes && iterator.hasNext())
        {
            Map.Entry<Long, byte[]> eldest = iterator.next();
            residentBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    public synchronized void clear()
    {
        ranges.clear();
        residentBytes = 0L;
    }

    public synchronized long getResidentBytes() { return residentBytes; }
    public synchronized long getHitBytes() { return hitBytes; }
    public synchronized long getMissCount() { return missCount; }
}
