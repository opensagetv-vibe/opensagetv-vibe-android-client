package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiscPlaybackPolicyTest
{
    @Test public void autoAndNativeStayOnCommissionedNativePath()
    {
        assertEquals(DiscPlaybackPolicy.Effective.NATIVE,
                DiscPlaybackPolicy.resolve("auto", true, false).effective);
        assertEquals(DiscPlaybackPolicy.Effective.NATIVE,
                DiscPlaybackPolicy.resolve("native", false, false).effective);
    }

    @Test public void unavailableMimModesFallbackOnlyWhenAllowed()
    {
        DiscPlaybackPolicy.Resolution fallback =
                DiscPlaybackPolicy.resolve("hybrid", true, false);
        assertEquals(DiscPlaybackPolicy.Effective.NATIVE, fallback.effective);
        assertTrue(fallback.fallback);
        assertTrue(fallback.advertisesRemoteNavigation());

        DiscPlaybackPolicy.Resolution closed =
                DiscPlaybackPolicy.resolve("mim_main_feature", false, false);
        assertEquals(DiscPlaybackPolicy.Effective.UNAVAILABLE, closed.effective);
        assertFalse(closed.fallback);
        assertFalse(closed.advertisesRemoteNavigation());
    }

    @Test public void provenMimCapabilityEnablesRequestedMode()
    {
        assertEquals(DiscPlaybackPolicy.Effective.HYBRID,
                DiscPlaybackPolicy.resolve("hybrid", true, true).effective);
        assertEquals(DiscPlaybackPolicy.Effective.MIM_MAIN_FEATURE,
                DiscPlaybackPolicy.resolve("mim_main_feature", true, true).effective);
    }
}
