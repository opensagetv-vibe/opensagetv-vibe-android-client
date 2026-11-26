package opensagetv.vibe.miniclient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, bounded companion menu requests. No polling or playback-clock authority. */
public final class PlaybackCompanionClient {
    public interface Current { boolean getAsBoolean(); }
    public interface Callback { void complete(Reply reply); }
    public static final class Reply {
        public final String text, challenge;
        public final boolean available;
        private Reply(String text,String challenge,boolean available) {
            this.text=text;this.challenge=challenge;this.available=available;
        }
    }
    private static final Reply UNAVAILABLE=new Reply("Unavailable (optional plugin service off, missing or load changed)",null,false);
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,2,15,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(4),r->{Thread t=new Thread(r,"Vibe-companion-menu");t.setDaemon(true);return t;},
            new ThreadPoolExecutor.AbortPolicy());
    private PlaybackCompanionClient() { }
    static void unavailable(Callback callback) { callback.complete(UNAVAILABLE); }

    static void request(String server,String client,String kind,String challenge,String operation,
            Current current,Callback callback) {
        try {
            final String identity=DvdCompanionCapabilities.identity(client);
            if (!("state".equals(kind)||"controls".equals(kind))) throw new IllegalArgumentException();
            if (operation!=null && (!"controls".equals(kind) || !validOperation(operation)
                    || challenge==null || !challenge.matches("[a-f0-9]{32}"))) throw new IllegalArgumentException();
            WORKER.execute(()->{
                Reply result=UNAVAILABLE;
                try {
                    if (!current.getAsBoolean()) return;
                    InetAddress address=InetAddress.getByName(server);
                    if (!address.isLoopbackAddress()&&!address.isSiteLocalAddress()) throw new IllegalArgumentException();
                    String path="state".equals(kind)?"/vibe/media/state":"/vibe/dvd/controls";
                    String query="client="+identity+(operation==null?"":"&challenge="+challenge+"&operation="+operation);
                    if (!current.getAsBoolean()) return;
                    String body=exchange(new URI("http",null,address.getHostAddress(),8273,path,query,null),operation==null?"GET":"POST");
                    if ("state".equals(kind)) result=new Reply(stateText(body),null,true);
                    else if (operation==null) {
                        String nonce=DvdCompanionCapabilities.challenge(body);
                        if (nonce!=null) result=new Reply("Current owned DVD ready",nonce,true);
                    } else if (body!=null && body.matches("\\s*\\{\\s*\"accepted\"\\s*:\\s*true\\s*\\}\\s*"))
                        result=new Reply("DVD action accepted",null,true);
                } catch (Exception unavailable) { /* Preserve stock playback; never retry ordered actions. */ }
                if (current.getAsBoolean()) callback.complete(result);
            });
        } catch (RuntimeException invalidOrBusy) { callback.complete(UNAVAILABLE); }
    }
    static boolean validOperation(String value) {
        return "save_resume".equals(value)||"resume".equals(value)||"beginning".equals(value)||"clear_resume".equals(value);
    }
    static String exchange(URI uri,String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(600);c.setReadTimeout(600);c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        try {
            if (c.getResponseCode()!=200) return null;
            try (InputStream input=c.getInputStream();ByteArrayOutputStream body=new ByteArrayOutputStream()) {
                for(int b;(b=input.read())!=-1;) {
                    if(body.size()>=2048||System.nanoTime()>deadline) return null;
                    body.write(b);
                }
                return new String(body.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
    static String stateText(String body) {
        if (body==null||body.length()>2048) throw new IllegalArgumentException();
        // Flat typed protocol only: reject trailing junk, duplicate members,
        // strings/paths and partial observations rather than displaying raw JSON.
        String value=body.trim();
        if (!value.startsWith("{")||!value.endsWith("}")) throw new IllegalArgumentException();
        Map<String,String> fields=new HashMap<String,String>();
        Pattern member=Pattern.compile("\\s*\"([a-z_]+)\"\\s*:\\s*(\"stock_api\"|true|false|-?[0-9]{1,19})\\s*");
        for(String part:value.substring(1,value.length()-1).split(",",-1)) {
            Matcher m=member.matcher(part);
            if(!m.matches()||fields.put(m.group(1),m.group(2))!=null) throw new IllegalArgumentException();
        }
        if (!"1".equals(fields.get("protocol"))||!"\"stock_api\"".equals(fields.get("clock_source"))) throw new IllegalArgumentException();
        StringBuilder text=new StringBuilder("Stock API observation (not renderer clock)");
        String[] names={"media_id","sampled_at_ms","raw_time_ms","media_time_ms","duration_ms","file_size","file_start_ms","file_end_ms"};
        for(String name:names) {
            String number=fields.get(name);
            if(number==null) throw new IllegalArgumentException();
            Long.parseLong(number);
            text.append('\n').append(name.replace('_',' ')).append(": ").append(number);
        }
        for(String name:new String[]{"recording","playing","dvd"}) {
            String flag=fields.get(name);
            if(!"true".equals(flag)&&!"false".equals(flag)) throw new IllegalArgumentException();
            text.append('\n').append(name).append(": ").append(flag);
        }
        if ("true".equals(fields.get("dvd"))) for(String name:new String[]{"dvd_title","dvd_chapter","dvd_domain"}) {
            String number=fields.get(name);if(number==null) throw new IllegalArgumentException();Long.parseLong(number);
            text.append('\n').append(name.replace('_',' ')).append(": ").append(number);
        }
        return text.toString();
    }
}
