package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ReadAheadDataSourceTest
{
    @Test public void batchesSequentialSmallReadsAndRefillsAfterSeek() throws Exception
    {
        FakeSource source = new FakeSource(256 * 1024);
        ReadAheadDataSource buffered = new ReadAheadDataSource(source, 64 * 1024);
        buffered.open("test");
        byte[] result = new byte[1024];
        assertEquals(1024, buffered.read(0, result, 0, result.length));
        assertEquals(1024, buffered.read(1024, result, 0, result.length));
        assertEquals(1, source.reads);
        assertEquals(2048, buffered.getCacheHitBytes());
        assertEquals(1, buffered.getCacheMissCount());

        assertEquals(1024, buffered.read(200 * 1024, result, 0, result.length));
        assertEquals(2, source.reads);
        assertEquals(2, buffered.getCacheMissCount());
    }

    @Test public void neverReturnsBytesBeyondEnd() throws Exception
    {
        FakeSource source = new FakeSource(40 * 1024);
        ReadAheadDataSource buffered = new ReadAheadDataSource(source, 64 * 1024);
        buffered.open("test");
        byte[] result = new byte[8192];
        assertEquals(1024, buffered.read(39 * 1024, result, 0, result.length));
        assertEquals(-1, buffered.read(40 * 1024, result, 0, result.length));
    }

    @Test public void largeCallerReadPerformsOnlyOnePhysicalRead() throws Exception
    {
        FakeSource source = new FakeSource(512 * 1024);
        ReadAheadDataSource buffered = new ReadAheadDataSource(source, 64 * 1024);
        buffered.open("test");
        byte[] result = new byte[256 * 1024];

        assertEquals(64 * 1024, buffered.read(0, result, 0, result.length));
        assertEquals(1, source.reads);
        assertEquals(64 * 1024, buffered.read(64 * 1024, result, 0, result.length));
        assertEquals(2, source.reads);
    }

    @Test public void randomProbesStaySmallButSequentialRefillsUseConfiguredWindow() throws Exception
    {
        FakeSource source = new FakeSource(8 * 1024 * 1024);
        ReadAheadDataSource buffered = new ReadAheadDataSource(source, 1024 * 1024);
        buffered.open("test");
        byte[] result = new byte[64 * 1024];

        assertEquals(result.length, buffered.read(0, result, 0, result.length));
        assertEquals(64 * 1024, source.lastRequestedLength);

        assertEquals(result.length, buffered.read(64 * 1024, result, 0, result.length));
        assertEquals(1024 * 1024, source.lastRequestedLength);

        assertEquals(result.length, buffered.read(4 * 1024 * 1024, result, 0, result.length));
        assertEquals(64 * 1024, source.lastRequestedLength);
    }

    private static final class FakeSource implements ISageTVDataSource
    {
        final byte[] data;
        int reads;
        int lastRequestedLength;
        FakeSource(int size)
        {
            data = new byte[size];
            Arrays.fill(data, (byte) 7);
        }
        @Override public long open(String uri) { return data.length; }
        @Override public int read(long position, byte[] target, int offset, int length) throws IOException
        {
            reads++;
            lastRequestedLength = length;
            if (position >= data.length) return -1;
            int copy = Math.min(length, data.length - (int) position);
            System.arraycopy(data, (int) position, target, offset, copy);
            return copy;
        }
        @Override public long size() { return data.length; }
        @Override public void close() { }
    }
}
