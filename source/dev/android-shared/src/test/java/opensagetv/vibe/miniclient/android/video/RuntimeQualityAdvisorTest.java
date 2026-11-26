package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuntimeQualityAdvisorTest {
    @Test public void passiveObservationReportsOnlyAcceptedRuntimeSamples(){RuntimeQualityAdvisor p=new RuntimeQualityAdvisor();
        assertTrue(p.observation().contains("runtime,samples=0"));
        p.observe(3000,2500,1000,6000,6000,false,4000);
        assertEquals("runtime,samples=1,fastKbps=2500,slowKbps=2500,low=1,high=0,reserveMs=4000",p.observation());
        p.observe(4000,1000,1000,6000,6000,false,2000);
        assertTrue(p.observation().contains("samples=1"));
        p.reset();assertTrue(p.observation().contains("samples=0"));}
    @Test public void fallingLowBufferAndSlowReadsReduce(){RuntimeQualityAdvisor p=new RuntimeQualityAdvisor();
        assertEquals(0,p.observe(3000,2500,1000,6000,6000,false,4000));
        assertEquals(0,p.observe(6000,2500,1000,6000,6000,false,3000));
        assertEquals(1650,p.observe(9000,2500,1000,6000,6000,true,1000));}
    @Test public void starvationWithFastDeliveryDoesNotBlameNetwork(){RuntimeQualityAdvisor p=new RuntimeQualityAdvisor();
        for(int i=1;i<=20;i++)assertEquals(0,p.observe(i*3000L,20000,1000,6000,6000,true,0));}
    @Test public void recoveryNeedsSpacedSamplesReserveAndCeiling(){RuntimeQualityAdvisor p=new RuntimeQualityAdvisor();
        for(int i=1;i<10;i++)assertEquals(0,p.observe(i*3000L,20000,1000,2000,6000,false,2000));
        assertEquals(2500,p.observe(30000,20000,1000,2000,6000,false,2000));
        for(int i=11;i<20;i++)assertEquals(0,p.observe(i*3000L,20000,1000,2500,6000,false,2000));}
    @Test public void unknownReserveOrInvalidReadAbstains(){RuntimeQualityAdvisor p=new RuntimeQualityAdvisor();
        for(int i=1;i<=20;i++){assertEquals(0,p.observe(i*3000L,Double.NaN,1000,6000,6000,true,0));
            assertEquals(0,p.observe(i*3000L,Double.POSITIVE_INFINITY,1000,6000,6000,true,0));
            assertEquals(0,p.observe(i*3000L,1000,99,6000,6000,true,0));
            assertEquals(0,p.observe(i*3000L,1000,1000,6000,6000,true,-1));}}
}
