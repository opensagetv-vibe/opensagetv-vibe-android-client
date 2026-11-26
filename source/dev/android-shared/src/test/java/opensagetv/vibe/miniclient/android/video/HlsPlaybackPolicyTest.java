package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.android.exoplayer2.DefaultLoadControl;

public class HlsPlaybackPolicyTest {
    @Test public void observedSlidingWindowMapsToTheSameMediaPeriod() {
        assertEquals(160000,HlsPlaybackPolicy.periodPositionMs(true,0,160000));
        assertEquals(160000,HlsPlaybackPolicy.periodPositionMs(true,20000,140000)); // paused as window moves
        assertEquals(180000,HlsPlaybackPolicy.periodPositionMs(true,20000,160000));
        assertEquals(200000,HlsPlaybackPolicy.periodPositionMs(true,40000,160000));
        assertEquals(0,HlsPlaybackPolicy.periodPositionMs(true,0,0)); // replacement epoch
        assertEquals(123,HlsPlaybackPolicy.periodPositionMs(false,20000,123));
        assertEquals(123,HlsPlaybackPolicy.periodPositionMs(true,-1,123));
        assertEquals(-1,HlsPlaybackPolicy.periodPositionMs(true,20000,-1));
        assertEquals(123,HlsPlaybackPolicy.periodPositionMs(true,Long.MAX_VALUE,123));
    }
    @Test public void onlyProviderConfirmedCompletedSourcesRefillEarly() {
        assertTrue(HlsPlaybackPolicy.confirmedCompleted(true,23));
        assertFalse(HlsPlaybackPolicy.confirmedCompleted(true,0));
        assertFalse(HlsPlaybackPolicy.confirmedCompleted(true,-1));
        assertFalse(HlsPlaybackPolicy.confirmedCompleted(false,23));
    }
    @Test public void legacyAndUserLowerReservesArePreserved() {
        assertEquals(500,HlsPlaybackPolicy.startBufferMs(500,false));
        assertEquals(2500,HlsPlaybackPolicy.startBufferMs(2500,false));
        assertEquals(100,HlsPlaybackPolicy.startBufferMs(100,true));
        assertEquals(0,HlsPlaybackPolicy.startBufferMs(0,true));
    }
    @Test public void onlyResponsiveStartupGetsShorterReserve() {
        assertEquals(250,HlsPlaybackPolicy.startBufferMs(500,true));
        assertEquals(250,HlsPlaybackPolicy.startBufferMs(2500,true));
        assertEquals(0,HlsPlaybackPolicy.startBufferMs(-1,true));
    }
    @Test public void responsiveRefillStartsNearTheSameMaximum() {
        assertEquals(16000,HlsPlaybackPolicy.refillBufferMs(5000,20000,true));
        assertEquals(24000,HlsPlaybackPolicy.refillBufferMs(5000,30000,true));
        assertEquals(19000,HlsPlaybackPolicy.refillBufferMs(19000,20000,true));
        assertEquals(20000,HlsPlaybackPolicy.refillBufferMs(20000,20000,true));
    }
    @Test public void otherTransportsAndUnknownTargetsAreUnchanged() {
        assertEquals(5000,HlsPlaybackPolicy.refillBufferMs(5000,20000,false));
        assertEquals(5000,HlsPlaybackPolicy.refillBufferMs(5000,0,true));
        assertEquals(5000,HlsPlaybackPolicy.refillBufferMs(5000,-1,true));
    }
    @Test public void refillArithmeticDoesNotOverflowOrExceedMaximum() {
        assertEquals(1717986917,HlsPlaybackPolicy.refillBufferMs(0,Integer.MAX_VALUE,true));
        assertEquals(800,HlsPlaybackPolicy.refillBufferMs(100,1000,true));
    }
    @Test public void realLoadControlRefillsEarlierWithoutIncreasingItsCeiling() {
        DefaultLoadControl previous = loadControl(5000);
        DefaultLoadControl completed = loadControl(HlsPlaybackPolicy.refillBufferMs(5000,20000,true));
        assertFalse(previous.shouldContinueLoading(0,20000000,1f));
        assertFalse(completed.shouldContinueLoading(0,20000000,1f));
        assertFalse(previous.shouldContinueLoading(0,15000000,1f));
        assertTrue(completed.shouldContinueLoading(0,15000000,1f));
        assertTrue(completed.shouldContinueLoading(0,19000000,1f));
        assertFalse(completed.shouldContinueLoading(0,20000000,1f));
        assertFalse(completed.shouldContinueLoading(0,17000000,1f));
        assertTrue(completed.shouldContinueLoading(0,15999000,1f));
    }
    private static DefaultLoadControl loadControl(int minimumMs) {
        return new DefaultLoadControl.Builder().setBufferDurationsMs(minimumMs,20000,500,1000)
                .setPrioritizeTimeOverSizeThresholds(true).build();
    }
}
