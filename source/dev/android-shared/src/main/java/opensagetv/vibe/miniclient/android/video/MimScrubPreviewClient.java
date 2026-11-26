package opensagetv.vibe.miniclient.android.video;

import java.util.concurrent.Executor;

/** Explicit optional source preview requests, never a playback/seek owner.
 * There is one pending request, no queue/timer/bandwidth probe or retry. Source
 * replacement invalidates stale results without waiting on network I/O. The
 * transport's cancel implementation MUST be nonblocking on the UI thread.
 */
public final class MimScrubPreviewClient {
    interface Http {
        Image fetch(String base,String token,long positionMs,java.util.function.BooleanSupplier current)throws Exception;
        void cancel();
    }
    public interface Callback { void complete(Result result); }
    public static final class Image {
        public final long positionMs;
        public final byte[] jpeg;
        public final boolean cached;
        Image(long positionMs,byte[] jpeg,boolean cached){
            if(positionMs<0||positionMs>14L*86400000L||jpeg==null||jpeg.length<4||jpeg.length>32768
                    ||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216
                    ||(jpeg[jpeg.length-2]&255)!=255||(jpeg[jpeg.length-1]&255)!=217)
                throw new IllegalArgumentException("Invalid bounded preview image");
            this.positionMs=positionMs;this.jpeg=jpeg.clone();this.cached=cached;
        }
    }
    public static final class Result {
        public final Image image;
        private final Source custody;
        Result(Source custody,Image image){this.custody=custody;this.image=image;}
        public boolean available(){return image!=null;}
    }
    private static final class Source {
        final Object owner;final String base,token;
        Source(Object owner,String base,String token){this.owner=owner;this.base=base;this.token=token;}
    }
    private final Executor worker;
    private final Http http;
    private Source source;
    private boolean busy;
    MimScrubPreviewClient(Executor worker,Http http){this.worker=worker;this.http=http;}

    static boolean supported(String capabilities){
        java.util.regex.Matcher section=java.util.regex.Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})")
                .matcher(capabilities==null?"":capabilities);
        return section.find()&&MimDirectSessionClient.longValue(section.group(1),"contractVersion",0)==1
                &&section.group(1).replaceAll("\\s+","").contains("\"available\":true")
                &&MimDirectSessionClient.longValue(section.group(1),"sourcePreviewRevision",0)==1;
    }

    void source(Object owner,String base,String token,boolean supported){
        boolean cancel;
        synchronized(this){
            boolean valid=supported&&owner!=null&&base!=null&&!base.isEmpty()&&token!=null&&token.matches("[a-f0-9]{64}");
            if(valid&&source!=null&&source.owner==owner&&source.base.equals(base)&&source.token.equals(token))return;
            cancel=busy;
            source=valid?new Source(owner,base,token):null;
        }
        if(cancel)http.cancel();
    }
    synchronized boolean current(Object owner,Result result){return source!=null&&source.owner==owner&&result!=null&&result.custody==source;}
    synchronized boolean current(Object owner,String base,String token,Result result){
        return current(owner,result)&&source.base.equals(base)&&source.token.equals(token);
    }
    boolean request(Object owner,long positionMs,Callback callback){
        final Source requested;
        synchronized(this){
            if(source==null||source.owner!=owner||busy||positionMs<0||positionMs>14L*86400000L)return false;
            requested=source;busy=true;
        }
        try{
            worker.execute(()->{
                Image image=null;
                try{
                    synchronized(MimScrubPreviewClient.this){if(source!=requested)return;}
                    image=http.fetch(requested.base,requested.token,positionMs,()->{
                        synchronized(MimScrubPreviewClient.this){return source==requested;}
                    });
                }catch(Exception unavailable){/* Optional image failure cannot affect playback. */}
                finally{
                    Result result;
                    synchronized(MimScrubPreviewClient.this){busy=false;result=new Result(requested,source==requested?image:null);}
                    callback.complete(result);
                }
            });
            return true;
        }catch(RuntimeException refused){synchronized(this){busy=false;}return false;}
    }
}
