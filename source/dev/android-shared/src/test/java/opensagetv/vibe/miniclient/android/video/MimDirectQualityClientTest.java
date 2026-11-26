package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Deterministic client custody/HTTP tests, not presented device/GPU proof. */
public class MimDirectQualityClientTest {
    static final String OLD=String.join("",Collections.nCopies(64,"a")),NEW=String.join("",Collections.nCopies(64,"b"));
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task){tasks.add(task);}
        void drain(){int n=0;while(!tasks.isEmpty()){assertTrue(++n<20);tasks.remove().run();}}
    }
    static final class Fixture implements MimDirectQualityClient.Renderer {
        final Queue io=new Queue(),cleanup=new Queue();final AtomicLong now=new AtomicLong(1000);
        final List<String> paths=new ArrayList<>();final Object owner=new Object();
        final MimDirectQualityClient client;Runnable timeout,duringPrepare;int ready,rollback,committed,commitFailures,readinessFailures;boolean failPrepare,badMedia,readinessAlwaysFails,stalled;
        MimDirectQualityClient.Lease lease;
        Fixture(){
            client=new MimDirectQualityClient(io,cleanup,new MimDirectQualityClient.Http(){
              public MimDirectSessionClient.Response post(String base,String path)throws Exception{
                assertEquals("http://server:31910",base);paths.add(path);
                if(path.contains("/prepare?")){
                    if(duringPrepare!=null)duringPrepare.run();
                    if(failPrepare)throw new java.io.IOException("private endpoint detail");
                    int requested=Integer.parseInt(path.split("videoKbps=")[1].split("&")[0]);
                    return new MimDirectSessionClient.Response(200,"{\"sessionToken\":\""+NEW+"\",\"mediaUrl\":\""+(badMedia?"/foreign":"/v1/direct/media/"+NEW+"/stream.m3u8")+"\",\"startMs\":4000,\"videoTargetKbps\":"+requested+"}");
                }
                if(path.contains("/commit?")&&commitFailures-->0)throw new java.io.IOException("lost reply");
                return new MimDirectSessionClient.Response(200,path.contains("/commit?")?"{\"sessionToken\":\""+NEW+"\"}":"{}");
              }
              public boolean ready(MimDirectQualityClient.Source candidate,long join)throws Exception{
                  if(readinessAlwaysFails){now.addAndGet(6000);throw new java.net.SocketTimeoutException("Private lease URL");}
                  if(readinessFailures-->0)throw new java.net.SocketTimeoutException("Private lease URL");
                  return true;
              }
            },(milliseconds,run)->{assertEquals(45000,milliseconds);timeout=run;},now::get);
            source(true);
        }
        void source(boolean enabled){client.source(new MimDirectQualityClient.Source(owner,"http://server:31910",OLD,"http://server:31910/v1/direct/media/"+OLD+"/stream.m3u8","",0,1000),enabled);}
        boolean request(){return client.request(owner,4000,700,this);}
        public long position(){return stalled?4000:6250;}
        public boolean stalled(){return stalled;}
        public void ready(MimDirectQualityClient.Lease value){ready++;lease=value;}
        public void rollback(MimDirectQualityClient.Lease value){assertSame(lease,value);rollback++;}
        public void committed(MimDirectQualityClient.Lease value){assertSame(lease,value);committed++;}
    }
    @Test public void prepareIsNotPresentationAndExactBoundFrameCommits(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();assertEquals(1,f.ready);
        assertTrue(f.paths.get(0).contains("startMs=9000"));
        assertFalse(f.client.bind(f.lease,3999));
        assertTrue(f.client.bind(f.lease,6250));assertEquals(0,f.committed);
        f.client.presented(f.lease,4001);f.io.drain();assertEquals(0,f.committed);
        f.client.presented(f.lease,6251);f.io.drain();assertEquals(1,f.committed);assertEquals("presented",f.client.state());
        f.timeout.run();f.cleanup.drain();assertEquals(0,f.rollback);assertFalse(f.paths.toString().contains("/abort?"));
    }
    @Test public void cancellationDuringPreparationCannotAttachOrResurrect(){
        Fixture f=new Fixture();f.duringPrepare=()->f.client.cancel(false);assertTrue(f.request());f.io.drain();f.cleanup.drain();
        assertEquals(0,f.ready);assertTrue(f.paths.stream().anyMatch(p->p.contains("/abort?")));assertEquals(0,f.rollback);
    }
    @Test public void cancelledQueuedPreparationDoesNotLaunch(){
        Fixture f=new Fixture();assertTrue(f.request());f.client.cancel(false);f.io.drain();assertTrue(f.paths.isEmpty());
    }
    @Test public void explicitSeekCapturesLineageBeforeCancellingQueuedRendererOrCommit(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();
        assertSame(f.lease,f.client.seekLineage(f.owner));
        f.client.cancel(false);assertFalse(f.client.bind(f.lease,5000));assertEquals(0,f.rollback);
        Fixture committing=new Fixture();assertTrue(committing.request());committing.io.drain();assertTrue(committing.client.bind(committing.lease,5000));
        committing.client.presented(committing.lease,5001);assertSame(committing.lease,committing.client.seekLineage(committing.owner));
        committing.client.cancel(false);committing.io.drain();assertEquals(0,committing.committed);assertEquals(0,committing.rollback);
    }
    @Test public void deadlineRestoresOnlyBoundCandidateAndNeverNewSource(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();assertTrue(f.client.bind(f.lease,5000));f.timeout.run();f.cleanup.drain();assertEquals(1,f.rollback);
        assertFalse(f.client.bind(f.lease,6000));f.client.presented(f.lease,7000);f.io.drain();assertEquals(0,f.committed);
        Fixture other=new Fixture();assertTrue(other.request());other.io.drain();other.source(true);other.timeout.run();other.cleanup.drain();assertEquals(0,other.rollback);
    }
    @Test public void unsupportedCopyForeignOwnerCooldownAndBoundsRefuseWithoutIo(){
        Fixture f=new Fixture();f.source(false);assertFalse(f.request());f.source(true);
        assertFalse(f.client.request(new Object(),4000,700,f));assertFalse(f.client.request(f.owner,4000,1001,f));assertFalse(f.client.request(f.owner,4000,249,f));
        assertTrue(f.request());assertFalse(f.request());f.io.drain();f.client.cancel(true);assertFalse(f.request());
        f.now.addAndGet(60001);assertTrue(f.request());f.io.drain();f.client.cancel(true);f.now.addAndGet(60001);assertFalse(f.request());
    }
    @Test public void providerConfirmedSeekKeepsCooldownAndTotalBudget(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();
        assertTrue(f.client.bind(f.lease,6250));f.client.presented(f.lease,6251);f.io.drain();
        MimDirectQualityClient.Source sought=new MimDirectQualityClient.Source(f.owner,"http://server:31910",NEW,"new","",42000,700,1000,true);
        f.client.seekSource(sought,true);
        assertFalse(f.client.request(f.owner,43000,875,f));
        f.now.addAndGet(60001);assertTrue(f.client.request(f.owner,43000,875,f));
        f.client.cancel(false);f.client.seekSource(sought,true);f.now.addAndGet(60001);
        assertFalse(f.client.request(f.owner,43000,500,f));
        // A genuine source load, unlike a seek, gets its own bounded budget.
        f.source(true);assertTrue(f.request());
    }
    @Test public void seekRebaseCannotEnableAnUnrelatedOwnerOrCeiling(){
        Fixture f=new Fixture();f.client.seekSource(new MimDirectQualityClient.Source(new Object(),"http://server:31910",NEW,"new","",42000,700,1000,true),true);
        assertEquals("unsupported",f.client.state());
        f.source(true);f.client.seekSource(new MimDirectQualityClient.Source(f.owner,"http://server:31910",NEW,"new","",42000,700,2000,true),true);
        assertEquals("unsupported",f.client.state());
    }
    @Test public void failedOrMalformedCandidateRetainsOriginalAndHidesSecrets(){
        Fixture f=new Fixture();f.failPrepare=true;assertTrue(f.request());f.io.drain();assertEquals("retained_original",f.client.state());assertEquals(0,f.ready);
        assertEquals("prepare_request_IOException",f.client.failure());
        assertFalse(f.client.failure().contains("private endpoint"));
        Fixture bad=new Fixture();bad.badMedia=true;assertTrue(bad.request());bad.io.drain();bad.cleanup.drain();assertEquals(0,bad.ready);assertTrue(bad.paths.stream().anyMatch(p->p.contains("/abort?")));
        assertEquals("prepare_contract_IOException",bad.client.failure());
    }
    @Test public void lostCommitReplyUsesExactIdempotentReconciliationNotBlindRollback(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();assertTrue(f.client.bind(f.lease,5000));f.commitFailures=1;
        f.client.presented(f.lease,5001);f.io.drain();assertEquals(1,f.committed);assertEquals(0,f.rollback);
        List<String> commits=new ArrayList<>();for(String p:f.paths)if(p.contains("/commit?"))commits.add(p);
        assertEquals(2,commits.size());assertEquals(commits.get(0),commits.get(1));
        Fixture unknown=new Fixture();assertTrue(unknown.request());unknown.io.drain();assertTrue(unknown.client.bind(unknown.lease,5000));unknown.commitFailures=10;
        unknown.client.presented(unknown.lease,5001);unknown.io.drain();unknown.timeout.run();unknown.io.drain();
        assertEquals("commit_unconfirmed",unknown.client.state());assertEquals(0,unknown.rollback);assertTrue(unknown.client.pending(unknown.owner));
    }
    @Test public void capabilityMustBeInsideAvailableDirectContract(){
        assertTrue(MimDirectQualityClient.supported("{\"mimDirect\":{\"contractVersion\":1,\"available\":true,\"qualityHandoffRevision\":1,\"qualitySeekRevision\":1}}"));
        assertFalse(MimDirectQualityClient.supported("{\"mimDirect\":{\"contractVersion\":1,\"available\":true,\"qualityHandoffRevision\":1}}"));
        assertFalse(MimDirectQualityClient.supported("{\"qualityHandoffRevision\":1}"));
        assertFalse(MimDirectQualityClient.supported("{\"mimDirect\":{\"contractVersion\":1,\"available\":false,\"qualityHandoffRevision\":1}}"));
    }
    @Test public void recoveryRequiresExplicitCapabilityAndPreservesCeiling(){
        Fixture f=new Fixture();
        f.client.source(new MimDirectQualityClient.Source(f.owner,"http://server:31910",OLD,"old","",0,700,1000,true),true);
        assertFalse(f.client.request(f.owner,4000,900,f));
        assertFalse(f.client.request(f.owner,4000,1001,f));
        assertTrue(f.client.request(f.owner,4000,875,f));f.io.drain();
        assertTrue(f.paths.get(0).endsWith("&recovery=true"));assertEquals(1000,f.lease.candidate.ceiling);
        assertTrue(f.client.bind(f.lease,5000));f.client.presented(f.lease,5001);f.io.drain();
        assertEquals(1,f.committed);assertFalse(f.client.request(f.owner,5001,1000,f));
        String base="{\"mimDirect\":{\"contractVersion\":1,\"available\":true,\"qualityHandoffRevision\":1,\"qualitySeekRevision\":1";
        assertFalse(MimDirectQualityClient.recoverySupported(base+"}}"));
        assertFalse(MimDirectQualityClient.recoverySupported(base+",\"qualityRecoveryRevision\":1}}"));
        assertTrue(MimDirectQualityClient.recoverySupported(base+",\"qualityStartRevision\":1,\"qualityRecoveryRevision\":1}}"));
    }
    @Test public void rendererFailureRollsBackOnceButStaleFramesCannotCommit(){
        Fixture f=new Fixture();assertTrue(f.request());f.io.drain();assertTrue(f.client.bind(f.lease,6200));
        assertTrue(f.client.abort(f.lease,true));assertFalse(f.client.abort(f.lease,true));
        f.client.presented(f.lease,6300);f.timeout.run();f.io.drain();f.cleanup.drain();
        assertEquals(1,f.rollback);assertEquals(0,f.committed);assertEquals("retained_original",f.client.state());
        assertEquals("renderer_aborted",f.client.failure());
    }
    @Test public void transientPublicationReadDoesNotReplayPrepareOrLoseOriginal(){
        Fixture f=new Fixture();f.readinessFailures=1;assertTrue(f.request());f.io.drain();
        assertEquals(1,f.ready);assertEquals("prepared",f.client.state());assertEquals("",f.client.failure());
        assertEquals(1,f.paths.stream().filter(p->p.contains("/prepare?")).count());
        assertEquals(0,f.rollback);assertFalse(f.paths.toString().contains("/commit?"));
    }
    @Test public void persistentPublicationFailureRemainsBoundedAndPrivate(){
        Fixture f=new Fixture();f.readinessAlwaysFails=true;assertTrue(f.request());f.io.drain();f.cleanup.drain();
        assertEquals(0,f.ready);assertEquals("retained_original",f.client.state());
        assertEquals("published_reserve_IOException",f.client.failure());assertFalse(f.client.failure().contains("Private"));
        assertEquals(1,f.paths.stream().filter(p->p.contains("/prepare?")).count());
    }
    @Test public void genuinelyStalledPlaybackDoesNotWaitForAnUnreachableFutureClock(){
        Fixture f=new Fixture();f.stalled=true;assertTrue(f.request());f.io.drain();
        assertTrue(f.paths.get(0).contains("startMs=4000"));assertEquals(0,f.lease.lead);
        assertTrue(f.client.bind(f.lease,4000));assertEquals(0,f.committed);
        f.client.presented(f.lease,4001);f.io.drain();assertEquals(1,f.committed);
    }
    @Test public void publicationMustCoverLatestOldClockWithReserve(){
        String playlist="#EXTM3U\n#EXTINF:2.0,\nseg_0.ts\n#EXTINF:2.0,\nseg_1.ts\n";
        assertTrue(MimDirectQualityClient.playlistCovers(playlist,2250));
        assertFalse(MimDirectQualityClient.playlistCovers(playlist,3500));
        assertTrue(MimDirectQualityClient.playlistCovers(playlist+"#EXT-X-ENDLIST\n",3800));
        assertFalse(MimDirectQualityClient.playlistCovers("#EXTM3U\n#EXTINF:NaN,\n",0));
        assertFalse(MimDirectQualityClient.playlistCovers("#EXTM3U\n#EXTINF:-2,\n",0));
    }
    @Test public void realTimePrerollWaitsForOldClockRatherThanSkippingAhead(){
        Queue work=new Queue(),cleanup=new Queue();AtomicLong now=new AtomicLong(1000),position=new AtomicLong(4000);
        Object owner=new Object();List<MimDirectQualityClient.Lease> prepared=new ArrayList<>();
        MimDirectQualityClient client=new MimDirectQualityClient(work,cleanup,new MimDirectQualityClient.Http(){
            public MimDirectSessionClient.Response post(String base,String path){
                assertTrue(path.contains("startMs=9000"));
                return new MimDirectSessionClient.Response(200,"{\"sessionToken\":\""+NEW+"\",\"mediaUrl\":\"/v1/direct/media/"+NEW+"/stream.m3u8\",\"startMs\":9000,\"videoTargetKbps\":700}");
            }
            public boolean ready(MimDirectQualityClient.Source source,long join){
                assertEquals(9000,source.offset);assertTrue(join>=9000);
                return MimDirectQualityClient.playlistCovers("#EXTM3U\n#EXTINF:2.0,\nseg_0.ts\n",join-source.offset);
            }
        },(ms,expired)->{},now::get);
        client.source(new MimDirectQualityClient.Source(owner,"http://server",OLD,"old","",0,1000),true);
        assertTrue(client.request(owner,4000,700,new MimDirectQualityClient.Renderer(){
            public long position(){now.addAndGet(1000);return position.getAndAdd(1000);}
            public void ready(MimDirectQualityClient.Lease value){prepared.add(value);}
            public void rollback(MimDirectQualityClient.Lease value){fail("No rollback before binding");}
            public void committed(MimDirectQualityClient.Lease value){fail("Readiness is not presentation");}
        }));
        work.drain();assertEquals(1,prepared.size());
        assertFalse(client.bind(prepared.get(0),8999));assertTrue(client.bind(prepared.get(0),9000));
        assertEquals("waiting_for_frame",client.state());
    }
}
