package opensagetv.vibe.miniclient.net;

import java.io.IOException;

/**
 * Playback-session-owned Pull source that reuses one MediaServer connection
 * and a bounded completed-file probe cache across Exo range opens.
 */
public final class RetainedBufferedPullDataSource extends BufferedPullDataSource
{
    private static final long MAXIMUM_CACHE_BYTES = 8L * 1024L * 1024L;

    private final BoundedReadCache cache = new BoundedReadCache(MAXIMUM_CACHE_BYTES);
    private String retainedUri;
    private long observedSize = -1L;
    private boolean cacheEnabled;
    private long rangeOpenCount;
    private long sessionReuseCount;
    private long lastFillEnd = -1L;

    public RetainedBufferedPullDataSource(String host, int bufferSize)
    {
        super(host, bufferSize);
    }

    @Override
    public synchronized long open(String uri) throws IOException
    {
        rangeOpenCount++;
        if (!isOpen())
        {
            cache.clear();
            retainedUri = uri;
            observedSize = super.open(uri);
            return observedSize;
        }
        if (retainedUri == null ? uri != null : !retainedUri.equals(uri))
        {
            release();
            retainedUri = uri;
            observedSize = super.open(uri);
            return observedSize;
        }

        sessionReuseCount++;
        long refreshedSize = refreshSize();
        if (refreshedSize != observedSize)
        {
            cache.clear();
            lastFillEnd = -1L;
            observedSize = refreshedSize;
        }
        return observedSize;
    }

    /** Exo closes each logical range; the playback owner performs final release. */
    @Override public synchronized void close() { flush(); }

    public synchronized void release()
    {
        super.close();
        cache.clear();
        retainedUri = null;
        observedSize = -1L;
        lastFillEnd = -1L;
    }

    public synchronized void setProbeCacheEnabled(boolean enabled)
    {
        cacheEnabled = enabled;
        if (!enabled) cache.clear();
    }

    @Override
    int fillBuffer(long position) throws IOException
    {
        boolean randomFill = lastFillEnd < 0L || position != lastFillEnd;
        if (cacheEnabled && randomFill)
        {
            int cached = cache.read(position, _buffer, 0, _buffer.length);
            if (cached > 0)
            {
                lastFillEnd = position + cached;
                return cached;
            }
        }
        int bytes = super.fillBuffer(position);
        if (bytes > 0)
        {
            if (cacheEnabled && randomFill) cache.put(position, _buffer, bytes);
            lastFillEnd = position + bytes;
        }
        return bytes;
    }

    public synchronized long getRangeOpenCount() { return rangeOpenCount; }
    public synchronized long getSessionReuseCount() { return sessionReuseCount; }
    public long getProbeCacheHitBytes() { return cache.getHitBytes(); }
    public long getProbeCacheMissCount() { return cache.getMissCount(); }
    public long getProbeCacheResidentBytes() { return cache.getResidentBytes(); }
}
