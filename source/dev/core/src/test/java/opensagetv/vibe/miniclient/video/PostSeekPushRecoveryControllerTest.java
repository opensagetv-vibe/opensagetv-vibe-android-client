package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PostSeekPushRecoveryControllerTest
{
    @Test
    public void requiresIntentFlushAndAnchorBeforeRecovery()
    {
        PostSeekPushRecoveryController controller =
                new PostSeekPushRecoveryController();
        controller.arm(1_000L);
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(2_000L));
        assertTrue(controller.onServerFlush(2_100L));
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(3_000L));
        assertTrue(controller.onServerAnchor(3_100L));
        controller.onBufferingChanged(true, 3_200L);
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(4_500L));
    }

    @Test
    public void recoversOnceWhenPlaybackRebuffersAfterFirstFrame()
    {
        PostSeekPushRecoveryController controller = confirmedController();
        controller.onFirstFrame(2_100L);
        controller.onBufferingChanged(true, 2_500L);
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(3_249L));
        assertEquals(PostSeekPushRecoveryController.Action.RECOVER,
                controller.evaluate(3_250L));
        assertTrue(controller.hasRecoveryAttempted());

        controller.onFirstFrame(3_500L);
        controller.onBufferingChanged(true, 3_600L);
        assertEquals(PostSeekPushRecoveryController.Action.EXPIRED,
                controller.evaluate(4_350L));
        assertFalse(controller.isActive());
    }

    @Test
    public void completesAfterSustainedPostAnchorPlayback()
    {
        PostSeekPushRecoveryController controller = confirmedController();
        controller.onFirstFrame(2_100L);
        controller.onBufferingChanged(false, 2_100L);
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(7_099L));
        assertEquals(PostSeekPushRecoveryController.Action.COMPLETE,
                controller.evaluate(7_100L));
        assertFalse(controller.isActive());
    }

    @Test
    public void recoversWhenConfirmedEpochNeverRendersAFrame()
    {
        PostSeekPushRecoveryController controller = confirmedController();
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(9_999L));
        assertEquals(PostSeekPushRecoveryController.Action.RECOVER,
                controller.evaluate(10_000L));
    }

    @Test
    public void staleIntentCannotArmAnUnrelatedFlush()
    {
        PostSeekPushRecoveryController controller =
                new PostSeekPushRecoveryController();
        controller.arm(100L);
        assertFalse(controller.onServerFlush(6_101L));
        assertFalse(controller.isActive());
    }

    @Test
    public void secondServerFlushRequiresTheReplacementAnchor()
    {
        PostSeekPushRecoveryController controller = confirmedController();
        controller.onFirstFrame(2_100L);
        assertTrue(controller.onServerFlush(2_200L));
        controller.onFirstFrame(2_300L);
        assertEquals(PostSeekPushRecoveryController.Action.NONE,
                controller.evaluate(8_000L));
        assertTrue(controller.onServerAnchor(8_100L));
    }

    private static PostSeekPushRecoveryController confirmedController()
    {
        PostSeekPushRecoveryController controller =
                new PostSeekPushRecoveryController();
        controller.arm(1_000L);
        assertTrue(controller.onServerFlush(1_500L));
        assertTrue(controller.onServerAnchor(2_000L));
        return controller;
    }
}
