package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Bounded same-origin image I/O, independent of Android and real network. */
public class MimScrubPreviewHttpTest {
    private static final String TOKEN=String.join("",Collections.nCopies(64,"a"));
    private static final class Connection extends HttpURLConnection {
        byte[] body={(byte)255,(byte)216,(byte)255,(byte)217};
        int status=200,length=-1;String mime="image/jpeg",position="40000";
        boolean disconnected;Runnable onRead=()->{};
        Connection()throws Exception{super(new URL("http://test"));}
        public void connect(){}public boolean usingProxy(){return false;}
        public void disconnect(){disconnected=true;}
        public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        public int getResponseCode(){return status;}
        public String getContentType(){return mime;}
        public int getContentLength(){return length;}
        public String getHeaderField(String name){return name.equals("X-Vibe-Preview-Position-Ms")?position:"true";}
        public InputStream getInputStream(){return new ByteArrayInputStream(body){
            public synchronized int read(byte[] b,int off,int len){onRead.run();return super.read(b,off,len);}
        };}
    }
    private void refused(Connection c)throws Exception{
        try{new MimScrubPreviewHttp(url->c).fetch("http://test",TOKEN,42000,()->true);fail("Invalid response accepted");}
        catch(IOException|IllegalArgumentException expected){}
        assertTrue(c.disconnected);
    }
    @Test public void exactPostAndActualBucket()throws Exception{
        Connection c=new Connection();
        MimScrubPreviewClient.Image image=new MimScrubPreviewHttp(url->{
            assertEquals("http://test/v1/direct/preview?token="+TOKEN+"&positionMs=42000",url.toString());return c;
        }).fetch("http://test",TOKEN,42000,()->true);
        assertEquals(40000,image.positionMs);assertTrue(image.cached);assertTrue(c.disconnected);
        assertEquals("POST",c.getRequestMethod());assertFalse(c.getInstanceFollowRedirects());
    }
    @Test public void malformedResponsesAreClosed()throws Exception{
        Connection c=new Connection();c.status=302;refused(c);
        c=new Connection();c.mime="text/html";refused(c);
        c=new Connection();c.length=32769;refused(c);
        c=new Connection();c.body=new byte[32769];refused(c);
        c=new Connection();c.position="unknown";refused(c);
        c=new Connection();c.position="999999999999";refused(c);
        c=new Connection();c.body=new byte[]{1,2,3,4};refused(c);
    }
    @Test public void staleAndInvalidRequestsNeverOpenConnection()throws Exception{
        int[] opened={0};MimScrubPreviewHttp http=new MimScrubPreviewHttp(url->{opened[0]++;throw new IOException();});
        for(String base:new String[]{"http://test/path","http://user@test","file:///tmp/test","http://test?query=1"}){
            try{http.fetch(base,TOKEN,0,()->true);fail();}catch(Exception expected){}
        }
        try{http.fetch("http://test",TOKEN,0,()->false);fail();}catch(IOException expected){}
        assertEquals(0,opened[0]);
    }
    @Test public void cancelAndSourceReplacementRejectInFlightImage()throws Exception{
        Connection c=new Connection();MimScrubPreviewHttp http=new MimScrubPreviewHttp(url->c);
        c.onRead=http::cancel;
        try{http.fetch("http://test",TOKEN,0,()->true);fail();}catch(IOException expected){}
        assertTrue(c.disconnected);
        Connection changed=new Connection();AtomicBoolean current=new AtomicBoolean(true);changed.onRead=()->current.set(false);
        try{new MimScrubPreviewHttp(url->changed).fetch("http://test",TOKEN,0,current::get);fail();}catch(IOException expected){}
        assertTrue(changed.disconnected);
    }
}
