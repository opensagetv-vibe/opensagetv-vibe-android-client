package opensagetv.vibe.miniclient.android.ui.keymaps;

import org.junit.Test;
import static org.junit.Assert.*;
import opensagetv.vibe.miniclient.SageCommand;

public class DvdTimeScrollPolicyTest
{
    @Test public void repeatedAndOppositeSkipsDoNotToggleTimeScroll()
    {
        DvdTimeScrollPolicy policy = new DvdTimeScrollPolicy();
        assertArrayEquals(new SageCommand[]{SageCommand.PLAY, SageCommand.TIME_SCROLL, SageCommand.FF}, policy.move(true));
        assertTrue(policy.isActive());
        assertArrayEquals(new SageCommand[]{SageCommand.FF}, policy.move(true));
        assertArrayEquals(new SageCommand[]{SageCommand.REW}, policy.move(false));
        assertArrayEquals(new SageCommand[]{SageCommand.TIME_SCROLL}, policy.accept());
        assertFalse(policy.isActive());
        assertEquals(0, policy.accept().length);
    }

    @Test public void cancelAndBackgroundAbandonNeverCommitTheCursor()
    {
        DvdTimeScrollPolicy policy = new DvdTimeScrollPolicy();
        policy.move(false);
        assertArrayEquals(new SageCommand[]{SageCommand.PLAY}, policy.cancel());
        assertEquals(0, policy.cancel().length);
        policy.move(true);
        policy.abandon();
        assertFalse(policy.isActive());
        assertEquals(0, policy.accept().length);
    }
}
