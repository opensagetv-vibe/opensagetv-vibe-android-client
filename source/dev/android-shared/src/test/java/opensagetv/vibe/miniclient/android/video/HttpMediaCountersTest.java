package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class HttpMediaCountersTest {
    @Test public void excludesPlaylistEmptyAndEof(){
        HttpMediaCounters c=new HttpMediaCounters();
        c.read(false,1000);c.read(true,0);c.read(true,-1);
        assertEquals(0,c.bytes());assertEquals(0,c.reads());
    }
    @Test public void aggregatesSegmentsAndDoesNotResetOnObservation(){
        HttpMediaCounters c=new HttpMediaCounters();c.read(true,1024);c.read(true,2048);
        assertEquals(3072,c.bytes());assertEquals(3072,c.bytes());assertEquals(2,c.reads());
        HttpMediaCounters next=new HttpMediaCounters();assertEquals(0,next.bytes());
    }
}
