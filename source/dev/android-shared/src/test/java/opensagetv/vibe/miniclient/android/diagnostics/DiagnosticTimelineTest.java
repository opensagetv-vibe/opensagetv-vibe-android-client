package opensagetv.vibe.miniclient.android.diagnostics;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class DiagnosticTimelineTest {
    @Test public void separatesRequestedAndObservedAndCapturesBufferingRecovery() {
        DiagnosticTimeline t=new DiagnosticTimeline();Object owner=new Object();
        t.sample(owner,0,-1,0,1000,false,false,0,100);
        t.sample(owner,1,30000,200,2000,true,true,0,250);
        List<Map<String,Object>> rows=t.sample(owner,1,30000,200,31000,false,false,1,1200);
        Map<String,Object> observation=rows.stream().filter(r->"post-seek-observation".equals(r.get("kind"))).findFirst().get();
        assertEquals(30000L,observation.get("requestedMs"));assertEquals(31000L,observation.get("observedMs"));
        assertTrue(observation.get("detail").toString().contains("not proof"));
        assertTrue(rows.stream().anyMatch(r->"buffering-recovered".equals(r.get("kind"))));
        assertTrue(rows.stream().anyMatch(r->"recovery-counter".equals(r.get("kind"))));
    }
    @Test public void oldBaselineSeekIsNotClaimedAsNewLanding() {
        DiagnosticTimeline t=new DiagnosticTimeline();List<Map<String,Object>> rows=t.sample(new Object(),4,20000,100,50000,false,false,0,20000);
        assertTrue(rows.stream().anyMatch(r->"last-known-server-seek".equals(r.get("kind"))));
        assertFalse(rows.stream().anyMatch(r->"post-seek-observation".equals(r.get("kind"))));
    }
    @Test public void sequenceGapAndNewContextNeverInventMissingTargets() {
        DiagnosticTimeline t=new DiagnosticTimeline();Object owner=new Object();
        t.sample(owner,0,-1,0,0,false,false,0,10);t.sample(owner,1,3000,20,10,true,false,0,30);
        List<Map<String,Object>> rows=t.sample(owner,4,10000,40,10,true,false,0,50);
        assertTrue(rows.stream().anyMatch(r->"sampling-gap".equals(r.get("kind"))));
        rows=t.sample(new Object(),0,-1,0,500,false,false,0,60);
        assertFalse(rows.stream().anyMatch(r->"post-seek-observation".equals(r.get("kind"))));
    }
    @Test public void boundsAndClear() {
        DiagnosticTimeline t=new DiagnosticTimeline();Object owner=new Object();
        for(int i=0;i<100;i++)t.sample(owner,i,i*1000,i*10,i*1000,false,false,0,i*10+1);
        assertEquals(64,t.snapshot().size());t.clear();assertEquals(0,t.snapshot().size());
    }
    @Test public void jsonEscapesControlAndNonFiniteNumbers() {
        Map<String,Object> value=new LinkedHashMap<>();value.put("text","\"\n\t\\");value.put("unknown",Double.NaN);
        assertEquals("{\"text\":\"\\\"\\n\\t\\\\\",\"unknown\":null}",DiagnosticJson.encode(value));
    }
}
