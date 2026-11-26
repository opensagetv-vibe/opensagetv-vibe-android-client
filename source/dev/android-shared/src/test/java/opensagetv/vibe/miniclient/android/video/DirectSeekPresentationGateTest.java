package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectSeekPresentationGateTest {
    @Test public void replacementKeepsConfirmedClockUntilCurrentFrame(){
        DirectSeekPresentationGate gate=new DirectSeekPresentationGate();
        gate.holdConfirmedPosition(123456);assertEquals(123456,gate.heldPosition());
        gate.arm(4);assertFalse(gate.presented(3));assertEquals(123456,gate.heldPosition());
        assertTrue(gate.presented(4));assertEquals(-1,gate.heldPosition());
    }
    @Test public void sourceChangeNeverRetainsPreviousClockAndZeroIsValid(){
        DirectSeekPresentationGate gate=new DirectSeekPresentationGate();
        gate.holdConfirmedPosition(0);assertEquals(0,gate.heldPosition());gate.cancel();
        assertEquals(-1,gate.heldPosition());gate.holdConfirmedPosition(-1);assertEquals(-1,gate.heldPosition());
    }
    @Test public void onlyCurrentAttachedReplacementFrameFinishesSeek(){
        DirectSeekPresentationGate gate=new DirectSeekPresentationGate();
        assertFalse(gate.presented(0));gate.arm(1);
        assertFalse(gate.presented(0));assertTrue(gate.presented(1));assertFalse(gate.presented(1));
    }
    @Test public void sourceChangeAndSupersedingSeekRejectOldCompletion(){
        DirectSeekPresentationGate gate=new DirectSeekPresentationGate();gate.arm(1);gate.cancel();
        assertFalse(gate.presented(1));gate.arm(2);assertFalse(gate.presented(1));assertTrue(gate.presented(2));
    }
}
