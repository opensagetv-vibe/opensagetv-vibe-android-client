package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic observed-read policy tests, not real WAN or frame evidence. */
public class DirectQualityAdvisorTest {
    @Test public void slowCompletedTransfersKeepConsecutiveEvidenceButLongIdleDoesNot(){
        DirectQualityAdvisor p=new DirectQualityAdvisor();
        assertEquals(0,p.observe(3000,25000,1000,1000,1000,true,true,0));
        assertEquals(0,p.observe(18000,25000,1000,1000,1000,true,true,0));
        assertEquals(250,p.observe(33000,25000,1000,1000,1000,true,true,0));
        p.reset();p.observe(3000,25000,1000,1000,1000,true,true,0);
        assertEquals(0,p.observe(35000,25000,1000,1000,1000,true,true,0));
        assertTrue(p.observation().contains("low=1"));assertTrue(p.observation().contains("buffering=true"));
    }
    @Test public void threeSpacedLimitedSamplesReduceButFastReadsAloneDoNot(){
        DirectQualityAdvisor p=new DirectQualityAdvisor();
        assertEquals(0,p.observe(3000,25000,1000,1000,1000,true,true,0));
        assertEquals(0,p.observe(3001,25000,1000,1000,1000,true,true,0));
        assertEquals(0,p.observe(6000,25000,1000,1000,1000,true,true,0));
        assertEquals(250,p.observe(9000,25000,1000,1000,1000,true,true,0));
        p.reset();for(int i=1;i<=12;i++)assertEquals(0,p.observe(i*3000,1000000,1000,1000,1000,true,false,20000));
    }
    @Test public void recoveryRequiresTenSpacedStableSamplesRealReserveAndCapability(){
        DirectQualityAdvisor p=new DirectQualityAdvisor();
        for(int i=1;i<=9;i++)assertEquals(0,p.observe(i*3000,1000000,1000,700,1000,true,false,1000));
        assertEquals(875,p.observe(30000,1000000,1000,700,1000,true,false,1000));
        for(long reserve:new long[]{-1,0,999}){
            p.reset();for(int i=1;i<=12;i++)assertEquals(0,p.observe(i*3000,1000000,1000,700,1000,true,false,reserve));
        }
        p.reset();for(int i=1;i<=12;i++)assertEquals(0,p.observe(i*3000,1000000,1000,700,1000,false,false,20000));
    }
    @Test public void bufferingAndObservationGapResetRecoveryStability(){
        DirectQualityAdvisor p=new DirectQualityAdvisor();
        for(int i=1;i<=9;i++)p.observe(i*3000,1000000,1000,700,1000,true,false,20000);
        assertEquals(0,p.observe(30000,1000000,1000,700,1000,true,true,20000));
        for(int i=1;i<=9;i++)assertEquals(0,p.observe(30000+i*3000,1000000,1000,700,1000,true,false,20000));
        assertEquals(0,p.observe(75000,1000000,1000,700,1000,true,false,20000));
        p.reset();assertEquals(0,p.observe(78000,1000000,1000,700,1000,true,false,20000));
    }
    @Test public void recoveryNeverExceedsCeilingOrAcceptsTinyReads(){
        DirectQualityAdvisor p=new DirectQualityAdvisor();
        for(int i=1;i<=12;i++)assertEquals(0,p.observe(i*3000,1000,10,900,1000,true,false,20000));
        for(int i=1;i<=9;i++)assertEquals(0,p.observe(i*3000,1000000,1000,900,1000,true,false,20000));
        assertEquals(1000,p.observe(30000,1000000,1000,900,1000,true,false,20000));
    }
}
