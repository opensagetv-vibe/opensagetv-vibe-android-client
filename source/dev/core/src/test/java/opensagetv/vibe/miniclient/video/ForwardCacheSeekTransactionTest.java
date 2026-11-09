package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForwardCacheSeekTransactionTest
{
    private static class Q implements ForwardCacheSeekTransaction.Queue
    {
        int index = 7, calls;
        boolean selected = true, required = true, succeeds = true, restores = true;
        public boolean selected() { return selected; }
        public boolean required() { return required; }
        public int readIndex() { return index; }
        public boolean seek(long target) { calls++; index = succeeds ? 12 : 0; return succeeds; }
        public boolean restore(int old) { index = old; return restores; }
    }

    @Test public void allRequiredAvQueuesSeekTogether()
    {
        Q video = new Q(), audio = new Q();
        assertTrue(ForwardCacheSeekTransaction.seek(new Q[]{video,audio}, 5000));
        assertEquals(12, video.index); assertEquals(12, audio.index);
    }

    @Test public void missingRequiredQueueRestoresAlreadyMovedAndFailedQueue()
    {
        Q video = new Q(), audio = new Q(), later = new Q();
        audio.succeeds = false;
        assertFalse(ForwardCacheSeekTransaction.seek(new Q[]{video,audio,later}, 5000));
        assertEquals(7, video.index); assertEquals(7, audio.index); assertEquals(0, later.calls);
    }

    @Test public void sparseTextDoesNotRejectValidAvAndUnselectedQueuesStayUntouched()
    {
        Q video = new Q(), text = new Q(), disabled = new Q();
        text.required = false; text.succeeds = false; disabled.selected = false;
        assertTrue(ForwardCacheSeekTransaction.seek(new Q[]{video,text,disabled}, 5000));
        assertEquals(12, video.index); assertEquals(0, disabled.calls);
        assertEquals(7, text.index);
    }

    @Test public void noSelectedAvDoesNotPretendSuccessfulSeek()
    {
        Q disabled = new Q(), text = new Q();
        disabled.selected = false; text.required = false;
        assertFalse(ForwardCacheSeekTransaction.seek(new Q[]{disabled,text}, 5000));
        assertEquals(0, text.calls);
        assertFalse(ForwardCacheSeekTransaction.seek(new Q[]{}, 5000));
    }

    @Test(expected=IllegalStateException.class) public void lostRollbackCannotPretendAtomicSuccess()
    {
        Q video = new Q(), audio = new Q();
        video.restores = false; audio.succeeds = false;
        ForwardCacheSeekTransaction.seek(new Q[]{video,audio}, 5000);
    }

    @Test public void onlyActualNonnegativeForwardWindowQualifies()
    {
        assertTrue(ForwardCacheSeekTransaction.containsForwardTarget(10,20,30));
        assertTrue(ForwardCacheSeekTransaction.containsForwardTarget(10,10,30));
        assertFalse(ForwardCacheSeekTransaction.containsForwardTarget(10,9,30));
        assertFalse(ForwardCacheSeekTransaction.containsForwardTarget(10,31,30));
        assertFalse(ForwardCacheSeekTransaction.containsForwardTarget(-1,20,30));
        assertFalse(ForwardCacheSeekTransaction.containsForwardTarget(10,20,-1));
        assertFalse(ForwardCacheSeekTransaction.containsForwardTarget(10,Long.MAX_VALUE,30));
    }
}
