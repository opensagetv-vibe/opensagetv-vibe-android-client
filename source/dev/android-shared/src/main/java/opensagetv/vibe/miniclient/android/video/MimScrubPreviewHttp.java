package opensagetv.vibe.miniclient.android.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Bounded explicit preview I/O. No redirects, secondary URLs, retries or
 * connection work on cancel: UI-thread source retirement only invalidates the
 * generation. The one worker finishes/cancels its finite read independently.
 */
final class MimScrubPreviewHttp implements MimScrubPreviewClient.Http {
    interface Open { HttpURLConnection connection(URL url)throws IOException; }
    private final Open open;
    private final AtomicLong generation=new AtomicLong();
    MimScrubPreviewHttp(){this(url->(HttpURLConnection)url.openConnection());}
    MimScrubPreviewHttp(Open open){this.open=open;}
    public void cancel(){generation.incrementAndGet();}
    public MimScrubPreviewClient.Image fetch(String base,String token,long positionMs,BooleanSupplier current)throws Exception{
        long expected=generation.get();
        long deadline=System.nanoTime()+8_000_000_000L;
        if(!current.getAsBoolean())throw new IOException("preview_source_changed");
        URI origin=new URI(base);
        if(!("http".equals(origin.getScheme())||"https".equals(origin.getScheme()))||origin.getHost()==null
                ||origin.getRawUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null
                ||!(origin.getRawPath()==null||origin.getRawPath().isEmpty()||"/".equals(origin.getRawPath()))
                ||token==null||!token.matches("[a-f0-9]{64}")||positionMs<0||positionMs>14L*86400000L)
            throw new IOException("preview_invalid_origin_or_request");
        String normalized=base.endsWith("/")?base.substring(0,base.length()-1):base;
        HttpURLConnection connection=open.connection(new URL(normalized+"/v1/direct/preview?token="+token+"&positionMs="+positionMs));
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(2000);connection.setReadTimeout(4000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(0);
            connection.setRequestProperty("Accept","image/jpeg");
            if(generation.get()!=expected||!current.getAsBoolean())throw new IOException("preview_source_changed");
            connection.getOutputStream().close();
            if(connection.getResponseCode()!=200||!"image/jpeg".equalsIgnoreCase(connection.getContentType()))
                throw new IOException("preview_unavailable");
            if(connection.getContentLength()>32768)throw new IOException("preview_image_too_large");
            String header=connection.getHeaderField("X-Vibe-Preview-Position-Ms");
            if(header==null||!header.matches("[0-9]{1,12}"))throw new IOException("preview_position_unknown");
            long actual=Long.parseLong(header);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[2048];int count;
                while(true){
                    long remaining=(deadline-System.nanoTime())/1000000L;
                    if(remaining<=0)throw new IOException("preview_timeout");
                    connection.setReadTimeout((int)Math.max(1,Math.min(4000,remaining)));
                    count=input.read(buffer);
                    if(count==-1)break;
                    if(generation.get()!=expected||!current.getAsBoolean())throw new IOException("preview_source_changed");
                    if(bytes.size()+count>32768)throw new IOException("preview_image_too_large");
                    bytes.write(buffer,0,count);
                }
            }
            if(generation.get()!=expected||!current.getAsBoolean())throw new IOException("preview_source_changed");
            return new MimScrubPreviewClient.Image(actual,bytes.toByteArray(),
                    "true".equals(connection.getHeaderField("X-Vibe-Preview-Cached")));
        }finally{connection.disconnect();}
    }
}
