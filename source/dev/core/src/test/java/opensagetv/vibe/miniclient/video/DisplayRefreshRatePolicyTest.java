package opensagetv.vibe.miniclient.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class DisplayRefreshRatePolicyTest
{
    private static DisplayRefreshRatePolicy.Mode mode(int id, int width, int height, float hz)
    {
        return new DisplayRefreshRatePolicy.Mode(id, width, height, hz);
    }

    @Test
    public void retainsCurrentIntegerMultipleWithoutModeChange()
    {
        DisplayRefreshRatePolicy.Mode current = mode(1, 1920, 1080, 59.94f);
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(29.97f,
                current, Arrays.asList(current, mode(2, 1920, 1080, 29.97f)), false,
                DisplayRefreshRatePolicy.Preference.EXACT_FIRST);

        assertEquals(1, decision.mode.id);
        assertFalse(decision.changeRequired);
        assertEquals(2, decision.multiple);
        assertEquals("current_mode_matches", decision.reason);
    }

    @Test
    public void exactFirstSelectsFractionalFilmMode()
    {
        DisplayRefreshRatePolicy.Mode current = mode(1, 1920, 1080, 60.0f);
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(23.976f,
                current, Arrays.asList(current, mode(2, 1920, 1080, 23.976f),
                        mode(3, 1920, 1080, 47.952f), mode(4, 1920, 1080, 119.88f)), false,
                DisplayRefreshRatePolicy.Preference.EXACT_FIRST);

        assertEquals(2, decision.mode.id);
        assertTrue(decision.changeRequired);
        assertTrue(decision.exactMatch);
        assertEquals(1, decision.multiple);
    }

    @Test
    public void highestMultipleSelectsHighestCleanRefresh()
    {
        DisplayRefreshRatePolicy.Mode current = mode(1, 1920, 1080, 60.0f);
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(23.976f,
                current, Arrays.asList(mode(2, 1920, 1080, 23.976f),
                        mode(3, 1920, 1080, 47.952f), mode(4, 1920, 1080, 119.88f)), false,
                DisplayRefreshRatePolicy.Preference.HIGHEST_MULTIPLE);

        assertEquals(4, decision.mode.id);
        assertEquals(5, decision.multiple);
        assertFalse(decision.exactMatch);
    }

    @Test
    public void preservesResolutionByDefault()
    {
        DisplayRefreshRatePolicy.Mode current = mode(1, 3840, 2160, 60.0f);
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(24.0f,
                current, Arrays.asList(mode(2, 1920, 1080, 24.0f)), false,
                DisplayRefreshRatePolicy.Preference.EXACT_FIRST);

        assertEquals(1, decision.mode.id);
        assertFalse(decision.changeRequired);
        assertEquals("no_compatible_mode_at_current_resolution", decision.reason);
    }

    @Test
    public void rejectsUnknownContentRate()
    {
        DisplayRefreshRatePolicy.Mode current = mode(1, 1920, 1080, 60.0f);
        DisplayRefreshRatePolicy.Decision decision = DisplayRefreshRatePolicy.choose(-1.0f,
                current, Arrays.asList(current), false,
                DisplayRefreshRatePolicy.Preference.EXACT_FIRST);

        assertFalse(decision.changeRequired);
        assertEquals("invalid_or_unknown_content_rate", decision.reason);
    }
}
