package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

/** Deterministic callback/ownership tests, not decoded device-image proof. */
public class MimScrubPreviewClientTest {
    private static final String TOKEN=String.join("",Collections.nCopies(64,"a"));
    private static byte[] jpeg(){return new byte[]{(byte)255,(byte)216,(byte)255,(byte)217};}
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable r){tasks.add(r);}
        void drain(){while(!tasks.isEmpty())tasks.remove().run();}
    }
    @Test public void optionalCapabilityIsScoped(){
        assertFalse(MimScrubPreviewClient.supported("{\"sourcePreviewRevision\":1}"));
        assertFalse(MimScrubPreviewClient.supported("{\"mimDirect\":{\"available\":false,\"contractVersion\":1,\"sourcePreviewRevision\":1}}"));
        assertFalse(MimScrubPreviewClient.supported("{\"mimDirect\":{\"available\":true,\"contractVersion\":1}}"));
        assertTrue(MimScrubPreviewClient.supported("{\"mimDirect\":{\"available\":true,\"contractVersion\":1,\"sourcePreviewRevision\":1}}"));
    }
    @Test public void oneRequestAndExactCurrentSource(){
        Queue q=new Queue();Object owner=new Object();int[] calls={0};MimScrubPreviewClient.Result[] result={null};
        MimScrubPreviewClient client=new MimScrubPreviewClient(q,new MimScrubPreviewClient.Http(){
            public MimScrubPreviewClient.Image fetch(String base,String token,long position,java.util.function.BooleanSupplier current){calls[0]++;assertEquals(TOKEN,token);return new MimScrubPreviewClient.Image(40000,jpeg(),false);}
            public void cancel(){}
        });
        client.source(owner,"http://test",TOKEN,true);
        assertFalse(client.request(new Object(),42000,r->result[0]=r));
        assertTrue(client.request(owner,42000,r->result[0]=r));
        assertFalse(client.request(owner,10000,r->result[0]=r));q.drain();
        assertEquals(1,calls[0]);assertTrue(result[0].available());assertTrue(client.current(owner,result[0]));
        client.source(null,"","",false);assertFalse(client.current(owner,result[0]));
    }
    @Test public void sourceReplacementCancelsQueuedAndStaleResults(){
        Queue q=new Queue();Object owner=new Object();int[] fetch={0},cancel={0};MimScrubPreviewClient.Result[] result={null};
        MimScrubPreviewClient client=new MimScrubPreviewClient(q,new MimScrubPreviewClient.Http(){
            public MimScrubPreviewClient.Image fetch(String b,String t,long p,java.util.function.BooleanSupplier current){fetch[0]++;return new MimScrubPreviewClient.Image(0,jpeg(),false);}
            public void cancel(){cancel[0]++;}
        });
        client.source(owner,"http://test",TOKEN,true);assertTrue(client.request(owner,0,r->result[0]=r));
        client.source(new Object(),"http://test",TOKEN,true);q.drain();
        assertEquals(0,fetch[0]);assertEquals(1,cancel[0]);assertFalse(result[0].available());
    }
    @Test public void optionalFailureDoesNotRetry(){
        Queue q=new Queue();Object owner=new Object();int[] calls={0};
        MimScrubPreviewClient client=new MimScrubPreviewClient(q,new MimScrubPreviewClient.Http(){
            public MimScrubPreviewClient.Image fetch(String b,String t,long p,java.util.function.BooleanSupplier current)throws Exception{calls[0]++;throw new Exception("private detail");}
            public void cancel(){}
        });
        client.source(owner,"http://test",TOKEN,true);assertTrue(client.request(owner,0,r->assertFalse(r.available())));q.drain();assertEquals(1,calls[0]);
    }
    @Test public void jpegAndPositionAreBounded(){
        byte[] bytes=jpeg();MimScrubPreviewClient.Image value=new MimScrubPreviewClient.Image(0,bytes,true);bytes[0]=0;assertEquals(255,value.jpeg[0]&255);
        for(byte[] invalid:new byte[][]{new byte[0],new byte[32769],new byte[]{1,2,3,4}}){
            try{new MimScrubPreviewClient.Image(0,invalid,false);fail("Bad image accepted");}catch(IllegalArgumentException expected){}
        }
        try{new MimScrubPreviewClient.Image(-1,jpeg(),false);fail("Negative position accepted");}catch(IllegalArgumentException expected){}
    }
}
