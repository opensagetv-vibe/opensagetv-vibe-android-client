package opensagetv.vibe.miniclient;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OrdinaryPushCreditPolicyTest
{
    private final OrdinaryPushCreditPolicy policy = new OrdinaryPushCreditPolicy();
    private static final int RING = 4 * 1024 * 1024;

    @Test public void bootstrapsBeforeRealDataThenBoundsBothPayloadAndPollReplies()
    {
        assertEquals(RING, policy.available(RING, true, 0));
        assertEquals(131072, policy.available(RING, true, 32768));
        assertEquals(131072, policy.available(RING, true, 0));
        assertEquals(17, policy.available(17, true, 32768));
    }

    @Test public void preservesZeroAndEveryNegativeEosOrDrainSentinel()
    {
        for (int free : new int[]{0, -1, -2, Integer.MIN_VALUE})
            assertEquals(free, policy.available(free, true, 32768));
    }

    @Test public void doesNotChangeDvdPullOrPlayerlessProbeCapacity()
    {
        assertEquals(RING, policy.available(RING, false, 32768));
        assertEquals(-2, policy.available(-2, false, 0));
        assertEquals(RING, policy.available(RING, true, 0));
    }

    @Test public void handlesEveryStockPayloadAndNonstandardForcedPayloadWithoutDeadlock()
    {
        for (int size : new int[]{32768, 65536, 131072})
            assertEquals(131072, policy.available(RING, true, size));
        assertEquals(1048576, policy.available(RING, true, 1048576));
        assertEquals(1048576, policy.available(RING, true, 0));
        assertEquals(524288, policy.available(524288, true, 0));
        assertEquals(Integer.MAX_VALUE, policy.available(Integer.MAX_VALUE, true, Integer.MAX_VALUE));
    }

    @Test public void newSessionResetsObservedPayloadButNormalPollAndFlushDoNot()
    {
        assertEquals(1048576, policy.available(RING, true, 1048576));
        assertEquals(1048576, policy.available(RING, true, 0));
        policy.reset();
        assertEquals(RING, policy.available(RING, true, 0));
        assertEquals(131072, policy.available(RING, true, 32768));
    }
}
