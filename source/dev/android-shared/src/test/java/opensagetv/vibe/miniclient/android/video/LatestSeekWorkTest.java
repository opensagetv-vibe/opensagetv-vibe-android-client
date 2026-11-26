package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class LatestSeekWorkTest {
    @Test public void coalescesBeforeWorkerAndNeverRunsHttpOnSubmittingThread(){
        List<Runnable> work=new ArrayList<>();List<Integer> seen=new ArrayList<>();
        LatestSeekWork queue=new LatestSeekWork(work::add);
        queue.submit(()->seen.add(1));queue.submit(()->seen.add(2));queue.submit(()->seen.add(3));
        assertTrue(seen.isEmpty());assertEquals(1,work.size());work.remove(0).run();
        assertEquals(Arrays.asList(3),seen);
    }
    @Test public void retainsOnlyLatestDuringActiveRequestAndCancelsPending(){
        List<Runnable> work=new ArrayList<>();List<Integer> seen=new ArrayList<>();
        LatestSeekWork queue=new LatestSeekWork(work::add);
        queue.submit(()->{seen.add(1);queue.submit(()->seen.add(2));queue.submit(()->seen.add(3));});
        work.remove(0).run();assertEquals(Arrays.asList(1,3),seen);
        queue.submit(()->seen.add(4));queue.cancelPending();work.remove(0).run();
        assertEquals(Arrays.asList(1,3),seen);
    }
}
