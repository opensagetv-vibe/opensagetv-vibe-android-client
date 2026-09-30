package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GrowingPlaybackSourcePolicyTest
{
    @Test public void explicitGrowingMetadataNeedsNoDataSourceProbe()
            throws Exception
    {
        GrowingPlaybackSourcePolicy policy =
                new GrowingPlaybackSourcePolicy(true, true);
        assertTrue(policy.resolve(null, -1L));
    }

    @Test public void explicitCompletedMetadataRemainsCompleted()
            throws Exception
    {
        GrowingPlaybackSourcePolicy policy =
                new GrowingPlaybackSourcePolicy(false, true);
        assertFalse(policy.resolve(null, -1L));
    }

    @Test public void legacyGrowingCandidatePreparesPlayerWithoutIo()
            throws Exception
    {
        GrowingPlaybackSourcePolicy policy =
                new GrowingPlaybackSourcePolicy(true, false);
        assertTrue(policy.shouldPreparePlayerForGrowth());
        assertTrue(policy.resolve(new GrowingSource(1024L), 512L));
    }

    @Test public void completedCandidateDoesNotRelaxPlayerPolicy()
    {
        GrowingPlaybackSourcePolicy policy =
                new GrowingPlaybackSourcePolicy(false, false);
        assertFalse(policy.shouldPreparePlayerForGrowth());
    }

    private static final class GrowingSource
            implements ISageTVDataSource, GrowingDataSource
    {
        private final long grownSize;

        GrowingSource(long grownSize)
        {
            this.grownSize = grownSize;
        }

        @Override public long waitForGrowth(long position, long timeoutMs)
        {
            return grownSize;
        }

        @Override public long open(String uri) { return grownSize; }
        @Override public void close() { }
        @Override public long size() { return grownSize; }
        @Override public int read(long position, byte[] buffer, int offset, int len)
                throws IOException
        {
            return -1;
        }
    }
}
