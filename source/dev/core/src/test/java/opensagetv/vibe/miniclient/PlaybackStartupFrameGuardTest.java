package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackStartupFrameGuardTest
{
    private static MenuHint hint(String menu, String popup)
    {
        MenuHint hint = new MenuHint();
        hint.menuName = menu;
        hint.popupName = popup;
        return hint;
    }

    @Test
    public void disabledGuardNeverDefers()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), false, 100L);
        assertEquals(0L, guard.deferFrame(101L, false));
    }

    @Test
    public void sageMcAndStockPlaybackOsdNamesAreRecognized()
    {
        assertTrue(PlaybackStartupFrameGuard.isPlaybackOsd(
                hint("MC MediaPlayer OSD", null)));
        assertTrue(PlaybackStartupFrameGuard.isPlaybackOsd(
                hint("MediaPlayer OSD", null)));
        assertFalse(PlaybackStartupFrameGuard.isPlaybackOsd(
                hint("OSDOptions", null)));
        assertFalse(PlaybackStartupFrameGuard.isPlaybackOsd(
                hint("MC MediaPlayer OSD", "Options")));
    }

    @Test
    public void playbackOsdEntryDefersOnlyForBoundedWindow()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("Main Menu", null), true, 100L);
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), true, 200L);

        assertTrue(guard.isArmed());
        assertEquals(PlaybackStartupFrameGuard.READY_RECHECK_MS,
                guard.deferFrame(200L, false));
        assertFalse(guard.releaseIfReady(5_199L, false));
        assertTrue(guard.releaseIfReady(5_200L, false));
        assertFalse(guard.isArmed());
        assertEquals(0L, guard.deferFrame(5_201L, false));
    }

    @Test
    public void repeatedHintDoesNotExtendWindow()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), true, 1_000L);
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), true, 1_300L);
        assertEquals(PlaybackStartupFrameGuard.READY_RECHECK_MS,
                guard.deferFrame(2_400L, false));
    }

    @Test
    public void leavingMenuReleasesPendingFrame()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), true, 100L);
        assertEquals(PlaybackStartupFrameGuard.READY_RECHECK_MS,
                guard.deferFrame(101L, false));
        assertTrue(guard.onMenuHint(hint("Main Menu", null), true, 102L));
        assertFalse(guard.releaseIfReady(1_000L, false));
    }

    @Test
    public void playbackStartReleasesBeforeMaximumHold()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("MediaPlayer OSD", null), true, 100L);
        assertEquals(PlaybackStartupFrameGuard.READY_RECHECK_MS,
                guard.deferFrame(101L, false));
        assertTrue(guard.releaseIfReady(250L, true));
        assertEquals(0L, guard.deferFrame(251L, true));
    }

    @Test
    public void newLoadRearmsWhenPlaybackOsdMenuDoesNotChange()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("MC MediaPlayer OSD", null), true, 100L);
        assertTrue(guard.releaseIfReady(250L, true) == false);
        assertEquals(0L, guard.deferFrame(251L, true));

        guard.onPlaybackLoad(true, 1_000L);
        assertTrue(guard.isArmed());
        assertEquals(PlaybackStartupFrameGuard.READY_RECHECK_MS,
                guard.deferFrame(1_001L, false));
        assertTrue(guard.releaseIfReady(1_250L, true));
    }

    @Test
    public void newLoadOutsidePlaybackOsdDoesNotArm()
    {
        PlaybackStartupFrameGuard guard = new PlaybackStartupFrameGuard();
        guard.onMenuHint(hint("Recorded TV", null), true, 100L);
        guard.onPlaybackLoad(true, 200L);
        assertFalse(guard.isArmed());
        assertEquals(0L, guard.deferFrame(201L, false));
    }
}
