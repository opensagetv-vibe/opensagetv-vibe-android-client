package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectSegmentDeliveryTest {
    @Test public void exactDeclaredLengthDoesNotNeedAnExtraEofRead(){
        DirectSegmentDelivery d=new DirectSegmentDelivery(24576);d.read(8192,1000000000L);
        d.read(16384,200000000L);assertTrue(d.measured());assertEquals(16384,d.bytes());assertEquals(200,d.readMs());
    }
    @Test public void declaredPartialOrPrematureEofNeverCountsAsCompleted(){
        DirectSegmentDelivery d=new DirectSegmentDelivery(65536);d.read(8192,1);d.read(32768,200000000L);
        assertFalse(d.measured());d.read(-1,0);assertFalse(d.measured());
    }
    @Test public void declaredOverrunIsNotACompletedChunk(){
        DirectSegmentDelivery d=new DirectSegmentDelivery(20000);d.read(8192,1);d.read(32768,200000000L);
        assertFalse(d.measured());d.read(-1,0);assertFalse(d.measured());
    }
    @Test public void excludesFirstReadAndConsumerGapsAndRequiresEof(){
        DirectSegmentDelivery d=new DirectSegmentDelivery();d.read(8192,10000000000L);
        d.read(16384,200000000L);d.read(0,1000000000L);
        assertFalse(d.measured());d.read(-1,0);assertTrue(d.measured());assertEquals(16384,d.bytes());assertEquals(200,d.readMs());
    }
    @Test public void fastCachedTinyAndPartialReadsAreUnknown(){
        DirectSegmentDelivery fast=new DirectSegmentDelivery();fast.read(8192,1000000000L);fast.read(32768,1000000L);fast.read(-1,0);assertFalse(fast.measured());
        DirectSegmentDelivery tiny=new DirectSegmentDelivery();tiny.read(8192,1000000L);tiny.read(100,1000000000L);tiny.read(-1,0);assertFalse(tiny.measured());
        DirectSegmentDelivery partial=new DirectSegmentDelivery();partial.read(8192,1);partial.read(32768,1000000000L);assertFalse(partial.measured());
    }
}
