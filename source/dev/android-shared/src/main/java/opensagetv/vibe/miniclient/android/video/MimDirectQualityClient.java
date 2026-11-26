package opensagetv.vibe.miniclient.android.video;

import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Optional Direct handoff custody. The provider owns both producers; this
 * client never creates a rendition ladder or treats an HTTP reply as a frame.
 * I/O is injected and runs outside the state monitor so STOP/seek can cancel a
 * blocked preparation. Tokens/URLs stay internal, never diagnostic strings. */
final class MimDirectQualityClient {
    // Direct intentionally uses -re. Starting at the current old position
    // cannot catch a clock advancing at that same rate plus a buffer reserve.
    // Prepare ahead, but keep the original playing THROUGH that interval;
    // never bind until its real clock reaches the candidate's start. This is
    // bounded preroll, not a user seek or an invented presented timestamp.
    private static final long PREPARE_LEAD_MS=5000;
    interface Http { MimDirectSessionClient.Response post(String base,String path)throws Exception; default boolean ready(Source candidate,long join)throws Exception{return true;} }
    interface Deadline { void after(long milliseconds,Runnable expired); }
    interface Renderer {
        default boolean eligible(){return true;}
        default long position(){return -1;}
        default boolean stalled(){return false;}
        void ready(Lease lease);
        void rollback(Lease lease);
        void committed(Lease lease);
    }
    static final class Source {
        final Object owner;final String base,token,media,caption;final long offset;final int kbps,ceiling;final boolean recovery;
        Source(Object owner,String base,String token,String media,String caption,long offset,int kbps){
            this(owner,base,token,media,caption,offset,kbps,kbps,false);
        }
        Source(Object owner,String base,String token,String media,String caption,long offset,int kbps,int ceiling,boolean recovery){
            this.owner=owner;this.base=base;this.token=token;this.media=media;this.caption=caption;this.offset=offset;this.kbps=kbps;
            this.ceiling=ceiling;this.recovery=recovery;
        }
    }
    static final class Lease {
        final Source original;final long generation,target,deadline,lead;final int kbps;final Renderer renderer;
        Source candidate;boolean bound,committing;long join,presented;
        Lease(Source original,long generation,long target,int kbps,long deadline,Renderer renderer){
            this.original=original;this.generation=generation;this.target=target;this.kbps=kbps;this.deadline=deadline;this.renderer=renderer;
            // A continuously buffering player cannot play through a future
            // five-second preroll. At a real stall, prepare its current point;
            // readiness/presentation still require actual data/current frame.
            // Advancing playback retains the existing ahead-preroll policy.
            this.lead=renderer.stalled()?0:PREPARE_LEAD_MS;
        }
    }
    private final Executor work,cleanup;private final Http http;private final Deadline timer;private final LongSupplier clock;
    private Source source;private Lease pending;private long generation,lastAttempt;private int attempts;
    private boolean supported;private String state="off";
    private String failure="";
    MimDirectQualityClient(Executor work,Executor cleanup,Http http,Deadline timer,LongSupplier clock){
        this.work=work;this.cleanup=cleanup;this.http=http;this.timer=timer;this.clock=clock;
    }
    static boolean supported(String capabilities){
        java.util.regex.Matcher section=java.util.regex.Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})").matcher(capabilities==null?"":capabilities);
        return section.find()&&MimDirectSessionClient.longValue(section.group(1),"contractVersion",0)==1
            &&section.group(1).replaceAll("\\s+","").contains("\"available\":true")
            &&MimDirectSessionClient.longValue(section.group(1),"qualityHandoffRevision",0)==1
            &&MimDirectSessionClient.longValue(section.group(1),"qualitySeekRevision",0)==1;
    }
    static boolean recoverySupported(String capabilities){
        java.util.regex.Matcher section=java.util.regex.Pattern.compile("\"mimDirect\"\\s*:\\s*(\\{[^{}]*\\})").matcher(capabilities==null?"":capabilities);
        return supported(capabilities)&&section.find()
            &&MimDirectSessionClient.longValue(section.group(1),"qualityStartRevision",0)==1
            &&MimDirectSessionClient.longValue(section.group(1),"qualityRecoveryRevision",0)==1;
    }
    void source(Source next,boolean available){
        cancel(false);
        synchronized(this){source=next;supported=available;attempts=0;lastAttempt=0;failure="";state=available?"ready":"unsupported";}
    }
    /** A provider-confirmed seek changes its output token, not the source's
     * adaptive launch budget or cooldown. Only a real new load resets those.
     * Refuse adaptation if the verified lineage unexpectedly changes identity. */
    void seekSource(Source next,boolean available){
        cancel(false);
        synchronized(this){
            boolean same=source!=null&&next!=null&&source.owner==next.owner
                &&source.base.equals(next.base)&&source.ceiling==next.ceiling;
            source=next;supported=available&&same;failure="";state=supported?"ready":"unsupported";
        }
    }
    synchronized String state(){return state;}
    synchronized String failure(){return failure;}
    synchronized boolean pending(Object owner){return pending!=null&&pending.original.owner==owner;}
    synchronized Source original(Object owner){return pending!=null&&pending.original.owner==owner?pending.original:null;}
    synchronized Lease seekLineage(Object owner){return pending!=null&&pending.original.owner==owner&&pending.candidate!=null?pending:null;}
    boolean request(Object owner,long position,int kbps,Renderer renderer){
        final Lease lease;
        synchronized(this){
            long now=clock.getAsLong();
            if(!supported||source==null||source.owner!=owner||pending!=null||position<0||position>14L*86400000-PREPARE_LEAD_MS||kbps<250||kbps==source.kbps
                ||kbps>source.kbps&&(!source.recovery||kbps>source.ceiling||kbps>source.kbps*5/4)
                ||attempts>=2||attempts>0&&now-lastAttempt<60000)return false;
            attempts++;lastAttempt=now;failure="";lease=new Lease(source,++generation,position,kbps,now+45000,renderer);pending=lease;state="preparing";
        }
        timer.after(45000,()->expire(lease));
        work.execute(()->prepare(lease));return true;
    }
    private void prepare(Lease lease){
        Source candidate=null;
        String stage="prepare_request";
        try{
            synchronized(this){if(!current(lease))return;}
            long prepareTarget=lease.target+lease.lead;
            MimDirectSessionClient.Response response=http.post(lease.original.base,"/v1/direct/quality/prepare?token="+lease.original.token+"&startMs="+prepareTarget+"&videoKbps="+lease.kbps
                +(lease.kbps>lease.original.kbps?"&recovery=true":""));
            if(response.status!=200){stage="prepare_http_"+response.status;throw new java.io.IOException("Quality preparation rejected");}
            stage="prepare_contract";
            String token=MimDirectSessionClient.token(response.body,"sessionToken"),media=MimDirectSessionClient.stringValue(response.body,"mediaUrl","");
            candidate=new Source(lease.original.owner,lease.original.base,token,lease.original.base+media,
                MimDirectSessionClient.optionalToken(response.body,"captionSessionToken"),
                Math.max(0,MimDirectSessionClient.longValue(response.body,"startMs",prepareTarget)),lease.kbps,lease.original.ceiling,lease.original.recovery);
            if(!media.startsWith("/v1/direct/media/"+token+"/"))throw new java.io.IOException("Invalid quality media lease");
            if(MimDirectSessionClient.longValue(response.body,"videoTargetKbps",-1)!=lease.kbps)throw new java.io.IOException("Quality target not confirmed");
            // Wait for actual published media covering the advancing old
            // position, not just an allocated process or an HTTP200. This is
            // bounded preparation, never ongoing player instrumentation.
            long readinessDeadline=Math.min(lease.deadline,clock.getAsLong()+20000);
            stage="published_reserve";
            while(true){
                synchronized(this){if(!current(lease)){retire(lease,candidate);return;}}
                long oldPosition=lease.renderer.position();
                if(oldPosition>=candidate.offset){
                    // A publication GET is idempotent and can briefly time out
                    // while captions/segments are being published. Retry only
                    // that READ within this original readiness deadline. Never
                    // replay prepare/seek, allocate a third producer, extend
                    // the lease or retire original on one transient read.
                    try{if(http.ready(candidate,Math.max(lease.target,oldPosition)))break;}
                    catch(java.io.IOException transientRead){/* bounded below */}
                }
                if(clock.getAsLong()>=readinessDeadline)throw new java.io.IOException("Quality replacement not ready");
                Thread.sleep(100);
            }
            boolean accepted;
            synchronized(this){accepted=current(lease)&&clock.getAsLong()<lease.deadline;if(accepted){lease.candidate=candidate;state="prepared";}}
            if(!accepted){retire(lease,candidate);return;}
            lease.renderer.ready(lease);
        }catch(Exception failure){
            synchronized(this){if(current(lease))this.failure=stage+"_"+failure.getClass().getSimpleName();}
            // A successful but malformed response may still carry a valid
            // token. Abort the exact lineage where possible; server timeout
            // remains the final bound when a reply/token was lost.
            if(candidate!=null&&lease.candidate!=candidate)retire(lease,candidate);
            abort(lease,true);
        }
    }
    /** Called on the renderer's affinity thread immediately before attaching.
     * Returning false forbids a queued old source from rebuilding the player. */
    synchronized boolean bind(Lease lease,long latestPosition){
        if(!current(lease)||lease.candidate==null||lease.bound||clock.getAsLong()>=lease.deadline)return false;
        if(latestPosition<lease.candidate.offset)return false;
        lease.join=Math.max(lease.target,latestPosition);lease.bound=true;state="waiting_for_frame";return true;
    }
    /** Only an exact, current backend first-frame callback supplies this ACK.
     * The caller also guards its concrete player instance/source generation. */
    void presented(Lease lease,long absolutePosition){
        synchronized(this){
            if(!current(lease)||!lease.bound||lease.committing||absolutePosition<lease.join||clock.getAsLong()>=lease.deadline)return;
            lease.committing=true;lease.presented=absolutePosition;state="committing";
        }
        work.execute(()->commit(lease));
    }
    private void commit(Lease lease){
        for(int attempt=0;attempt<2;attempt++){
            synchronized(this){if(!current(lease))return;}
            try{
                // The provider explicitly defines exact-lineage commit as
                // idempotent. Resolve one lost reply without replaying a seek
                // or preparing another producer.
                MimDirectSessionClient.Response reply=http.post(lease.original.base,"/v1/direct/quality/commit?token="+lease.original.token+"&replacementToken="+lease.candidate.token+"&presentedMs="+lease.presented);
                if(reply.status!=200)throw new java.io.IOException("Quality commit rejected");
                if(!MimDirectSessionClient.token(reply.body,"sessionToken").equals(lease.candidate.token))throw new java.io.IOException("Quality commit lineage not confirmed");
                synchronized(this){if(!current(lease))return;source=lease.candidate;pending=null;state="presented";}
                lease.renderer.committed(lease);return;
            }catch(Exception failure){
                if(attempt==1)synchronized(this){if(current(lease)){state="commit_unconfirmed";this.failure="commit_reply_unconfirmed";}}
            }
        }
        // Never blindly restore old after an ambiguous commit: the provider
        // might already have retired it. Keep the currently decoded candidate
        // and inhibit further changes; expiry performs one final reconciliation.
    }
    private void expire(Lease lease){
        synchronized(this){if(!current(lease))return;if(lease.committing){work.execute(()->commit(lease));return;}failure=lease.bound?"presentation_timeout":"preparation_timeout";}
        abort(lease,true);
    }
    boolean abort(Lease lease,boolean restore){
        Source loser;
        synchronized(this){if(!current(lease))return false;if(restore&&lease.committing){state="commit_unconfirmed";return false;}if(restore&&failure.isEmpty())failure=lease.bound?"renderer_aborted":"preparation_aborted";pending=null;generation++;loser=lease.candidate;state=restore?"retained_original":"superseded";}
        // Restore local custody before scheduling independent provider cleanup.
        // A superseding source/seek uses cancel(false), never stale rollback.
        if(restore&&lease.bound)lease.renderer.rollback(lease);
        if(loser!=null)retire(lease,loser);
        return true;
    }
    void cancel(boolean restore){Lease lease;synchronized(this){lease=pending;}if(lease!=null)abort(lease,restore);}
    private boolean current(Lease lease){return pending==lease&&generation==lease.generation&&source==lease.original;}
    private void retire(Lease lease,Source loser){
        cleanup.execute(()->{try{http.post(lease.original.base,"/v1/direct/quality/abort?token="+lease.original.token+"&replacementToken="+loser.token);}catch(Exception ignored){}});
    }
    static boolean playlistCovers(String playlist,long localMs){
        if(playlist==null||!playlist.startsWith("#EXTM3U")||localMs<0)return false;
        double seconds=0;
        try{for(String line:playlist.split("\\r?\\n"))if(line.startsWith("#EXTINF:")){
            double duration=Double.parseDouble(line.substring(8).split(",",2)[0]);
            if(Double.isNaN(duration)||Double.isInfinite(duration)||duration<=0||duration>86400)return false;seconds+=duration;
        }}catch(RuntimeException invalid){return false;}
        return seconds*1000>=localMs+(playlist.contains("#EXT-X-ENDLIST")?100:1000);
    }
}
