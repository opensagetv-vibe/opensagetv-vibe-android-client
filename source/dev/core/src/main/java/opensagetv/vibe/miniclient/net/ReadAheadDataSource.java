package opensagetv.vibe.miniclient.net;

import java.io.IOException;

/**
 * Bounded synchronous read-ahead for random-access sources with expensive
 * network round trips. A non-sequential read discards the cache immediately;
 * no stale data is returned across seek/range opens.
 */
public final class ReadAheadDataSource implements ISageTVDataSource, GrowingDataSource
{
    private static final int RANDOM_READ_AHEAD_BYTES = 64 * 1024;

    private final ISageTVDataSource delegate;
    private final byte[] cache;
    private long cacheStart = -1;
    private int cacheLength;
    private long lastCallerReadEnd = -1;
    private long cacheHitBytes;
    private long cacheMissCount;

    public ReadAheadDataSource(ISageTVDataSource delegate, int readAheadBytes)
    {
        if (delegate == null) throw new IllegalArgumentException("delegate is required");
        if (readAheadBytes < 32 * 1024 || readAheadBytes > 8 * 1024 * 1024)
            throw new IllegalArgumentException("read-ahead must be between 32 KiB and 8 MiB");
        this.delegate = delegate;
        this.cache = new byte[readAheadBytes];
    }

    @Override public synchronized long open(String uri) throws IOException
    {
        flush();
        return delegate.open(uri);
    }

    @Override public synchronized int read(long position, byte[] destination, int offset, int length) throws IOException
    {
        if (length == 0) return 0;
        if (position < cacheStart || position >= cacheStart + cacheLength)
        {
            cacheMissCount++;
            // MPEG-TS extractors issue several small random probes while opening or
            // seeking. Fetching the entire configured read-ahead window for every
            // probe makes a large playback buffer actively hurt seek latency. Once
            // the caller consumes data sequentially, use the full window so steady
            // playback still benefits from fewer SMB round trips.
            boolean sequential = lastCallerReadEnd == position;
            int fillLength = sequential
                    ? cache.length
                    : Math.min(cache.length, Math.max(length, RANDOM_READ_AHEAD_BYTES));
            int filled = delegate.read(position, cache, 0, fillLength);
            if (filled < 0) return -1;
            if (filled == 0) throw new IOException("Read-ahead source returned zero bytes");
            cacheStart = position;
            cacheLength = filled;
        }

        // A datasource read is allowed to be partial. Return from at most one
        // physical network read so a large extractor request cannot chain
        // multiple SMB operations and block playback for their combined time.
        int cacheOffset = (int) (position - cacheStart);
        int available = cacheLength - cacheOffset;
        int copy = Math.min(length, available);
        System.arraycopy(cache, cacheOffset, destination, offset, copy);
        cacheHitBytes += copy;
        lastCallerReadEnd = position + copy;
        return copy;
    }

    @Override public synchronized long size() { return delegate.size(); }

    @Override public synchronized long waitForGrowth(long position, long timeoutMs) throws IOException
    {
        return delegate instanceof GrowingDataSource
                ? ((GrowingDataSource) delegate).waitForGrowth(position, timeoutMs)
                : delegate.size();
    }

    /** Range close flushes only the cache; the playback owner closes the delegate. */
    @Override public synchronized void close() { flush(); }

    public synchronized void flush()
    {
        cacheStart = -1;
        cacheLength = 0;
        lastCallerReadEnd = -1;
    }

    public synchronized long getCacheHitBytes() { return cacheHitBytes; }
    public synchronized long getCacheMissCount() { return cacheMissCount; }
    public int getReadAheadBytes() { return cache.length; }
}
