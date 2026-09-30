package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

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
}
