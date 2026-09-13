package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PullSeekRecoveryPolicyTest
{
    @Test
    public void defersWhenReadsAdvancedAndLastReadIsRecent()
    {
        assertTrue(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                10, 12, 9_000, 10_000, 2_500));
    }

    @Test
    public void permitsRecoveryWhenIoIsStaleOrHasNotAdvanced()
    {
        assertFalse(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                10, 12, 7_000, 10_000, 2_500));
        assertFalse(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                10, 10, 9_000, 10_000, 2_500));
    }

    @Test
    public void rejectsUnknownAndNonMonotonicSamples()
    {
        assertFalse(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                -1, 12, 9_000, 10_000, 2_500));
        assertFalse(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                10, 12, -1, 10_000, 2_500));
        assertFalse(PullSeekRecoveryPolicy.shouldDeferForActiveIo(
                10, 12, 11_000, 10_000, 2_500));
    }
}
