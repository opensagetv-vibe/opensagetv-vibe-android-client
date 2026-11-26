package opensagetv.vibe.miniclient;

import org.junit.Test;
import static org.junit.Assert.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaybackCaptionBridgeTest {
    private static final String NONCE="0123456789abcdef0123456789abcdef";
    private String state(int cc) { return "{\"protocol\":1,\"challenge\":\""+NONCE+"\",\"cc\":"+cc+"}"; }
    @Test public void exactCurrentLoadAndVirtualSlots() {
        for(int cc=0;cc<3;cc++) {
            assertEquals(NONCE,PlaybackCaptionBridge.nonce(state(cc)));
            assertEquals(cc,PlaybackCaptionBridge.caption(state(cc),NONCE));
        }
        assertEquals(-1,PlaybackCaptionBridge.caption(state(1),"abcdef0123456789abcdef0123456789"));
    }
    @Test public void failClosedInvalidUnknownDuplicateAndOversized() {
        for(String value : new String[]{null,"",state(3),state(-1),state(12),
                state(1).replace("\"protocol\":1","\"protocol\":2"),
                state(1).replace("\"cc\":1","\"cc\":1,\"cc\":2"),
                state(1).replace(NONCE,"bad"),new String(new char[257])}) {
            assertNull(PlaybackCaptionBridge.nonce(value));
            assertEquals(-1,PlaybackCaptionBridge.caption(value,NONCE));
        }
    }
    @Test public void liveHttpSelectionThenCloseStopsIdleRequests() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        AtomicInteger value=new AtomicInteger(), requests=new AtomicInteger();
        AtomicBoolean current=new AtomicBoolean(true);
        AtomicBoolean polling=new AtomicBoolean(true);
        CountDownLatch initial=new CountDownLatch(1), cc2=new CountDownLatch(1);
        server.createContext("/vibe/media/captions",exchange -> {
            requests.incrementAndGet();
            String body="POST".equals(exchange.getRequestMethod()) ? "{\"accepted\":true}" : state(value.get());
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        PlaybackCaptionBridge bridge=PlaybackCaptionBridge.start("127.0.0.1","444556303031",new PlaybackCaptionBridge.Target() {
            public boolean current() {return current.get();}
            public boolean active() {return polling.get();}
            public void apply(int cc) {if(cc==0)initial.countDown();if(cc==2)cc2.countDown();}
            public void unavailable() { }
        },server.getAddress().getPort());
        try {
            assertTrue(initial.await(3,TimeUnit.SECONDS));
            polling.set(false);Thread.sleep(200);
            int idle=requests.get();Thread.sleep(1200);assertEquals(idle,requests.get());
            polling.set(true);
            value.set(2);assertTrue(cc2.await(3,TimeUnit.SECONDS));
            bridge.close();current.set(false);
            int before=requests.get();Thread.sleep(1200);
            assertEquals(before,requests.get());
        } finally {bridge.close();server.stop(0);}
    }
    @Test public void missingServiceIsOneAttemptNotBackgroundRetry() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        AtomicInteger requests=new AtomicInteger();CountDownLatch unavailable=new CountDownLatch(1);
        server.createContext("/",exchange -> {requests.incrementAndGet();exchange.sendResponseHeaders(404,-1);exchange.close();});
        server.start();
        PlaybackCaptionBridge bridge=PlaybackCaptionBridge.start("127.0.0.1","444556303031",new PlaybackCaptionBridge.Target() {
            public boolean current() {return true;}
            public void apply(int cc) {fail("Missing service applied state");}
            public void unavailable() {unavailable.countDown();}
        },server.getAddress().getPort());
        try {
            assertTrue(unavailable.await(3,TimeUnit.SECONDS));Thread.sleep(1100);
            assertEquals(1,requests.get());
        } finally {bridge.close();server.stop(0);}
    }
}
