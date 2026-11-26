package opensagetv.vibe.miniclient;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackCompanionClientTest {
    private static final String STATE="{\"protocol\":1,\"clock_source\":\"stock_api\",\"media_id\":1,"
            +"\"sampled_at_ms\":100,\"raw_time_ms\":20,\"media_time_ms\":120,\"duration_ms\":200,"
            +"\"file_size\":300,\"file_start_ms\":100,\"file_end_ms\":300,"
            +"\"recording\":false,\"playing\":true,\"dvd\":false}";
    @Test public void typedObservationOnlyNeverClockAuthority() {
        String text=PlaybackCompanionClient.stateText(STATE);
        assertTrue(text.contains("not renderer clock"));assertTrue(text.contains("raw time ms: 20"));
        for(String invalid:new String[]{null,"{}",STATE+"x",STATE.replace("\"protocol\":1","\"protocol\":2"),
                STATE.replace("\"media_id\":1","\"media_id\":1,\"media_id\":2"),
                STATE.replace("\"raw_time_ms\":20","\"raw_time_ms\":\"bad\""),
                STATE.replace("\"dvd\":false","\"dvd\":true")}) {
            try { PlaybackCompanionClient.stateText(invalid);fail("Malformed observation accepted"); }
            catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void exactActionsOnlyAndInvalidChallengeFailsLocally() {
        assertTrue(PlaybackCompanionClient.validOperation("resume"));
        assertFalse(PlaybackCompanionClient.validOperation("seek"));
        AtomicInteger replies=new AtomicInteger();
        PlaybackCompanionClient.request("127.0.0.1","444556303031","controls","bad","resume",()->true,
                reply->{assertFalse(reply.available);replies.incrementAndGet();});
        assertEquals(1,replies.get());
    }
    @Test public void replacedLoadNeverRunsOrReceivesCallback() throws Exception {
        CountDownLatch callback=new CountDownLatch(1);
        PlaybackCompanionClient.request("127.0.0.1","444556303031","state",null,null,()->false,
                reply->callback.countDown());
        assertFalse(callback.await(150,TimeUnit.MILLISECONDS));
    }
    @Test public void httpIsBoundedAndRedirectIsNotFollowed() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),1);
        AtomicInteger followed=new AtomicInteger();
        server.createContext("/large",x->{byte[] b=new byte[2049];x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
        server.createContext("/redirect",x->{x.getResponseHeaders().set("Location","/target");x.sendResponseHeaders(302,-1);x.close();});
        server.createContext("/target",x->{followed.incrementAndGet();x.sendResponseHeaders(200,-1);x.close();});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            assertNull(PlaybackCompanionClient.exchange(new URI(base+"/large"),"GET"));
            assertNull(PlaybackCompanionClient.exchange(new URI(base+"/redirect"),"GET"));
            assertEquals(0,followed.get());
        } finally {server.stop(0);}
    }
}
