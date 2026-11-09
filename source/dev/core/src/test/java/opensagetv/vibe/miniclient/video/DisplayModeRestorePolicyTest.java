package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayModeRestorePolicyTest
{
    @Test public void restoresAutoRatherThanThePhysicalModeThatHappenedToBeActive()
    {
        DisplayModeRestorePolicy policy = new DisplayModeRestorePolicy();
        Object window = new Object();
        policy.applying(window, 0, 2);
        assertEquals(Integer.valueOf(0), policy.release(window, 2));
    }

    @Test public void repeatedSwitchRetainsOriginalPreference()
    {
        DisplayModeRestorePolicy policy = new DisplayModeRestorePolicy();
        Object window = new Object();
        policy.applying(window, 7, 2);
        policy.applying(window, 2, 3);
        assertEquals(Integer.valueOf(7), policy.release(window, 3));
        assertNull(policy.release(window, 3));
    }

    @Test public void oldActivityCannotRestoreIntoNewActivity()
    {
        DisplayModeRestorePolicy policy = new DisplayModeRestorePolicy();
        Object oldWindow = new Object(), newWindow = new Object();
        policy.applying(oldWindow, 7, 2);
        assertNull(policy.release(newWindow, 0));
        policy.applying(newWindow, 0, 3);
        assertEquals(Integer.valueOf(7), policy.release(oldWindow, 2));
        assertEquals(Integer.valueOf(0), policy.release(newWindow, 3));
    }

    @Test public void externalPreferenceChangeWinsOverStaleRestore()
    {
        DisplayModeRestorePolicy policy = new DisplayModeRestorePolicy();
        Object window = new Object();
        policy.applying(window, 0, 2);
        assertNull(policy.release(window, 9));
    }

    @Test public void laterExplicitApplyCapturesNewExternalBaseline()
    {
        DisplayModeRestorePolicy policy = new DisplayModeRestorePolicy();
        Object window = new Object();
        policy.applying(window, 0, 2);
        policy.applying(window, 9, 3);
        assertEquals(Integer.valueOf(9), policy.release(window, 3));
    }

    @Test public void offWithoutAnOwnedSwitchDoesNotSetAnyPreference()
    {
        assertNull(new DisplayModeRestorePolicy().release(new Object(), 0));
    }
}
