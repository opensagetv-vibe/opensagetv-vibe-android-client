package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackFrameStepPolicyTest
{
    @Test
    public void advertisesOnlyGuaranteedRandomAccessModes()
    {
        assertTrue(PlaybackFrameStepPolicy.shouldAdvertise("pull", "exoplayer", "auto"));
        assertTrue(PlaybackFrameStepPolicy.shouldAdvertise("smb_direct", "media3", "auto"));
        assertTrue(PlaybackFrameStepPolicy.shouldAdvertise("smb_auto", "gsyplayer", "media3"));
        assertFalse(PlaybackFrameStepPolicy.shouldAdvertise("dynamic", "exoplayer", "auto"));
        assertFalse(PlaybackFrameStepPolicy.shouldAdvertise("fixed", "media3", "auto"));
        assertFalse(PlaybackFrameStepPolicy.shouldAdvertise("pull", "gsyplayer", "system"));
    }

    @Test
    public void requiresPausedReadyPullPlayerAndNonzeroAmount()
    {
        assertTrue(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PAUSE_STATE,
                false, true, true, 1));
        assertFalse(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PLAY_STATE,
                false, true, true, 1));
        assertFalse(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PAUSE_STATE,
                true, true, true, 1));
        assertFalse(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PAUSE_STATE,
                false, false, true, 1));
        assertFalse(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PAUSE_STATE,
                false, true, false, 1));
        assertFalse(PlaybackFrameStepPolicy.canStep(MiniPlayerPlugin.PAUSE_STATE,
                false, true, true, 0));
    }

    @Test
    public void calculatesSignedFrameTargetsAndSafeBounds()
    {
        assertEquals(1033L, PlaybackFrameStepPolicy.targetPositionMs(1000, 5000, 1, 29.97f));
        assertEquals(967L, PlaybackFrameStepPolicy.targetPositionMs(1000, 5000, -1, 30.0f));
        assertEquals(1067L, PlaybackFrameStepPolicy.targetPositionMs(1000, 5000, 2, Float.NaN));
        assertEquals(0L, PlaybackFrameStepPolicy.targetPositionMs(10, 5000, -10, 30.0f));
        assertEquals(4999L, PlaybackFrameStepPolicy.targetPositionMs(4990, 5000, 2, 30.0f));
    }
}
