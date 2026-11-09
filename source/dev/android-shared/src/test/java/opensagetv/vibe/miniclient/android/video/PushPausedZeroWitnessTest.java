package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;

public class PushPausedZeroWitnessTest
{
    @Test public void initialOrUnknownZeroIsNotProof() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.rendered(20, true); assertFalse(w.confirmed());
        w.reset(-1); w.rendered(20, true); assertFalse(w.confirmed());
    }
    @Test public void newPeriodFrameAtZeroIsProof() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.reset(100); w.position(w.generation(), 0);
        w.rendered(101, true); assertTrue(w.confirmed());
    }
    @Test public void staleTimestampOrWrongPeriodNeverProvesOutput() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.reset(100); w.rendered(99, true); w.rendered(100, true);
        w.rendered(200, false); assertFalse(w.confirmed());
    }
    @Test public void laterUnseekableResetToZeroCannotReuseWitness() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.reset(100); w.rendered(101, true);
        w.position(w.generation(), 3000); w.position(w.generation(), 0);
        assertFalse(w.confirmed());
    }
    @Test public void oldPositionSnapshotCannotPoisonNewEpoch() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.reset(100); long old = w.generation();
        w.reset(200); w.position(old, 12000); w.rendered(201, true);
        assertTrue(w.confirmed());
    }
    @Test public void resetAndClearDiscardEarlierFrameProof() {
        PushPausedZeroWitness w = new PushPausedZeroWitness();
        w.reset(100); w.rendered(101, true); w.reset(200); assertFalse(w.confirmed());
        w.rendered(201, true); assertTrue(w.confirmed()); w.clear(); assertFalse(w.confirmed());
    }
}
