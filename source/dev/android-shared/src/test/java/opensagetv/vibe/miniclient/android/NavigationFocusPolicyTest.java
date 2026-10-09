package opensagetv.vibe.miniclient.android;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NavigationFocusPolicyTest
{
    @Test public void skipEmptyRowsRatherThanFirstIconInNearbyRow()
    {
        int[][] bounds = {{200,0,250,50}, {0,60,50,110}, {100,120,150,170}, {200,180,250,230}};
        assertEquals(3, NavigationFocusPolicy.verticalTarget(bounds, 0, true));
        assertEquals(0, NavigationFocusPolicy.verticalTarget(bounds, 3, false));
    }

    @Test public void noMatchingColumnFindsNearestIconAheadButDoesNotWrap()
    {
        int[][] bounds = {{200,0,250,50}, {0,60,50,110}, {250,120,300,170}};
        assertEquals(2, NavigationFocusPolicy.verticalTarget(bounds, 0, true));
        assertEquals(0, NavigationFocusPolicy.verticalTarget(bounds, 0, false));
    }

    @Test public void nearestVerticalTargetWinsBeforeHorizontalTieBreak()
    {
        int[][] bounds = {{200,0,250,50}, {205,60,255,110}, {200,120,250,170}};
        assertEquals(1, NavigationFocusPolicy.verticalTarget(bounds, 0, true));
    }

    @Test public void skipEmptyColumnsWithoutJumpingToAnotherRow()
    {
        int[][] bounds = {{0,200,50,250}, {60,0,110,50}, {120,100,170,150}, {180,200,230,250}};
        assertEquals(3, NavigationFocusPolicy.horizontalTarget(bounds, 0, true));
        assertEquals(0, NavigationFocusPolicy.horizontalTarget(bounds, 3, false));
    }

    @Test public void noMatchingRowFindsNearestIconAheadButDoesNotWrap()
    {
        int[][] bounds = {{0,200,50,250}, {60,0,110,50}, {120,250,170,300}};
        assertEquals(2, NavigationFocusPolicy.horizontalTarget(bounds, 0, true));
        assertEquals(0, NavigationFocusPolicy.horizontalTarget(bounds, 0, false));
    }

    @Test public void nearestHorizontalIconWinsAcrossSparseGaps()
    {
        int[][] bounds = {{0,200,50,250}, {60,205,110,255}, {120,200,170,250}};
        assertEquals(1, NavigationFocusPolicy.horizontalTarget(bounds, 0, true));
    }

    @Test public void matchingAxisStillWinsOverCloserDiagonalIcons()
    {
        int[][] horizontal = {{0,200,50,250}, {60,150,110,200}, {600,200,650,250}};
        assertEquals(2, NavigationFocusPolicy.horizontalTarget(horizontal, 0, true));
        int[][] vertical = {{200,0,250,50}, {150,60,200,110}, {200,600,250,650}};
        assertEquals(2, NavigationFocusPolicy.verticalTarget(vertical, 0, true));
    }

    @Test public void fallbackWorksForLeftAndUpAsWell()
    {
        int[][] bounds = {{200,200,250,250}, {100,150,150,200}, {150,100,200,150}};
        assertEquals(1, NavigationFocusPolicy.horizontalTarget(bounds, 0, false));
        assertEquals(1, NavigationFocusPolicy.verticalTarget(bounds, 0, false));
    }
}
