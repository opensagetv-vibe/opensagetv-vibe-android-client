package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class PlaybackExperienceTraceTest {
    @Test public void acceptanceIsNotPresentation(){
        long[] now={100};PlaybackExperienceTrace trace=new PlaybackExperienceTrace(()->now[0]);
        PlaybackSessionController c=new PlaybackSessionController();trace.begin(c.beginSession());
        PlaybackSessionController.Token seek=c.beginOperation(PlaybackSessionController.Operation.SEEK);
        trace.requested(seek,30000);Map<String,Object> pending=trace.snapshot();
        assertEquals(30000L,pending.get("clientAcceptedPositionMs"));assertNull(pending.get("producerAcceptedPositionMs"));assertNull(pending.get("presentedPositionMs"));
        now[0]=140;trace.producerPrepared(seek,29750);trace.rendererCallback();Map<String,Object> ready=trace.snapshot();
        assertEquals(29750L,ready.get("producerAcceptedPositionMs"));assertEquals(40L,ready.get("producerPrepareMs"));
        assertNull(ready.get("presentedPositionMs"));assertNull(ready.get("stockUiProcessedAtMs"));
    }
    @Test public void newerSeekAndSourceRejectLateResponses(){
        PlaybackExperienceTrace trace=new PlaybackExperienceTrace();PlaybackSessionController c=new PlaybackSessionController();trace.begin(c.beginSession());
        PlaybackSessionController.Token old=c.beginOperation(PlaybackSessionController.Operation.SEEK);trace.requested(old,30000);
        PlaybackSessionController.Token newer=c.beginOperation(PlaybackSessionController.Operation.SEEK);trace.requested(newer,10000);trace.producerPrepared(old,30000);
        assertNull(trace.snapshot().get("producerAcceptedPositionMs"));assertEquals(10000L,trace.snapshot().get("requestedPositionMs"));
        trace.begin(c.beginSession());trace.producerPrepared(newer,10000);assertNull(trace.snapshot().get("requestedPositionMs"));assertNull(trace.snapshot().get("producerAcceptedPositionMs"));
    }
    @Test public void freeRetiresCallbacksAndHistoryIsBounded(){
        PlaybackExperienceTrace trace=new PlaybackExperienceTrace();PlaybackSessionController c=new PlaybackSessionController();trace.begin(c.beginSession());
        for(int i=0;i<100;i++)trace.operation(c.beginOperation(PlaybackSessionController.Operation.PAUSE));
        assertEquals(64,((List<?>)trace.snapshot().get("events")).size());
        trace.operation(c.endSession(PlaybackSessionController.Operation.FREE));int count=((List<?>)trace.snapshot().get("events")).size();trace.rendererCallback();
        assertEquals(count,((List<?>)trace.snapshot().get("events")).size());assertEquals(false,trace.snapshot().get("active"));
    }
}
