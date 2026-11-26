package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectBufferingWitnessTest {
    @Test public void wholeTransferRemembersStallEvenAfterReadyAndRecencyExpiry(){
        DirectBufferingWitness w=new DirectBufferingWitness();long opened=w.stamp();
        assertFalse(w.during(opened));w.observe(true,1000);assertTrue(w.recent(60000));
        w.observe(false,60001);assertFalse(w.buffering());assertTrue(w.recent(60002));
        assertFalse(w.recent(80000));assertTrue(w.during(opened));
    }
    @Test public void repeatedReadyDoesNotManufactureRecentBuffering(){
        DirectBufferingWitness w=new DirectBufferingWitness();w.observe(true,1000);w.observe(false,2000);
        long opened=w.stamp();w.observe(false,20000);assertEquals(opened,w.stamp());
        assertFalse(w.recent(20000));assertFalse(w.during(opened));
    }
    @Test public void openingDuringBufferingAndSourceResetHaveExplicitState(){
        DirectBufferingWitness w=new DirectBufferingWitness();w.observe(true,1000);long opened=w.stamp();
        w.observe(false,2000);assertTrue(w.during(opened));w.reset();
        assertFalse(w.buffering());assertFalse(w.recent(3000));assertEquals(0,w.stamp());
        // Caller independently rejects a retired source URI/session; a stamp
        // is a buffering witness, never a replacement source/lease identity.
    }
}
