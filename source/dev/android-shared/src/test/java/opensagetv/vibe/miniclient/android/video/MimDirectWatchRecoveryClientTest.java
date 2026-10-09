package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class MimDirectWatchRecoveryClientTest {
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> work=new ArrayDeque<>();
        public void execute(Runnable task) { work.add(task); }
        void drain() { int count=0; while (!work.isEmpty()) { assertTrue(++count<30); work.remove().run(); } }
    }
    static final class Fixture {
        final Queue queue=new Queue(); final AtomicLong clock=new AtomicLong(1000);
        final List<String> requests=new ArrayList<>();
        final Object oldOwner=new Object(), newOwner=new Object(), connection=new Object();
        final MimDirectWatchRecoveryClient client;
        Runnable duringReserve, duringWatch;
        boolean failWatch;
        int accepted, unavailable;
        Fixture() {
            client=new MimDirectWatchRecoveryClient(queue,(base,path) -> {
                assertEquals("http://server:31910",base); requests.add(path);
                if (path.startsWith("/v1/direct/recovery/reserve?")) {
                    if (duringReserve !=null) { Runnable run=duringReserve; duringReserve=null; run.run(); }
                    Matcher match=Pattern.compile("&intent=([0-9]+)").matcher(path); assertTrue(match.find());
                    return new MimDirectSessionClient.Response(200,
                            "{\"contractVersion\":1,\"recoveryToken\":\"0123456789abcdef0123456789abcdef\","+
                                    "\"context\":\"444942585142\",\"mediaFileId\":123,\"intent\":"+
                                    match.group(1)+",\"expiresInMs\":120000}");
                }
                if (path.startsWith("/v1/direct/recovery/watch?")) {
                    if (failWatch) throw new java.io.IOException("private endpoint failure");
                    if (duringWatch !=null) duringWatch.run();
                    return new MimDirectSessionClient.Response(200,"{\"ok\":true,\"state\":\"watch_requested\"}");
                }
                if (path.startsWith("/v1/direct/recovery/seek?"))
                    return new MimDirectSessionClient.Response(200,"{\"ok\":true,\"state\":\"seek_requested\"}");
                assertTrue(path.startsWith("/v1/direct/recovery/cancel?"));
                return new MimDirectSessionClient.Response(200,"{\"ok\":true,\"state\":\"canceled\"}");
            },clock::get);
            client.configure("http://server:31910",true);
            client.source(oldOwner,"/known source.ts","44:49:42:58:51:42");
        }
        void reserve() {
            assertTrue(client.request(() -> accepted++,() -> unavailable++)); queue.drain();
        }
        long count(String operation) { return requests.stream().filter(path -> path.contains("/"+operation+"?")).count(); }
    }
    @Test public void contractMustBeExplicitScopedAndCurrent() {
        String reply="{\"contractVersion\":1,\"available\":true,\"mimDirect\":{\"available\":true},"+
                "\"directWatchRecovery\":{\"contractVersion\":1,\"available\":true,"+
                "\"sourceScope\":\"single-segment-non-DVD\",\"restoreStages\":\"fresh-watch-video-seek\"}}";
        assertTrue(MimDirectWatchRecoveryClient.supported(reply));
        assertFalse(MimDirectWatchRecoveryClient.supported(reply.replace("single-segment-non-DVD","any-source")));
        assertFalse(MimDirectWatchRecoveryClient.supported(reply.replace("\"contractVersion\":1","\"contractVersion\":2")));
        assertFalse(MimDirectWatchRecoveryClient.supported(reply.replace("\"available\":true","\"available\":false")));
        assertFalse(MimDirectWatchRecoveryClient.supported("{\"contractVersion\":1,\"available\":true}"));
    }
    @Test public void repliesRejectDuplicateNestedEscapedAndMalformedValues() {
        for (String reply:new String[]{"{\"intent\":1,\"intent\":2}","{\"intent\":{}}",
                "{\"state\":\"private\\\"data\"}","{\"intent\":1,}","{\"intent\":1.5}"}) {
            try { MimDirectWatchRecoveryClient.flat(reply); fail(reply); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void pausedLatestSourceTargetIsReservedAndSeekIsOneUse() {
        Fixture f=new Fixture(); f.client.position(f.oldOwner,42000,false); f.reserve();
        assertEquals(1,f.accepted); assertTrue(f.client.pending());
        assertTrue(f.requests.get(0).contains("startMs=42000&playing=false"));
        f.duringWatch=() -> {
            f.client.video(f.oldOwner,f.connection); // retired decoder cannot satisfy readiness
            f.client.video(f.newOwner,new Object()); // foreign connection cannot satisfy readiness
            f.client.video(f.newOwner,f.connection);
        };
        f.client.connected(f.connection,() -> true); f.queue.drain();
        f.client.video(f.newOwner,f.connection); f.queue.drain();
        assertEquals(1,f.count("watch")); assertEquals(1,f.count("seek"));
        assertEquals("seek_requested",f.client.state()); assertEquals(0,f.unavailable);
    }
    @Test public void newTargetDuringCaptureSupersedesReadOnlyReservation() {
        Fixture f=new Fixture(); f.duringReserve=() -> f.client.position(f.oldOwner,9000,true);
        f.reserve(); assertEquals(1,f.accepted); assertEquals(2,f.count("reserve"));
        assertEquals(1,f.count("cancel")); assertTrue(f.requests.get(2).contains("startMs=9000"));
        assertEquals(0,f.count("watch"));
    }
    @Test public void stopDuringCaptureCannotRestartPlayback() {
        Fixture f=new Fixture(); f.duringReserve=f.client::cancel; f.reserve();
        assertEquals(0,f.accepted); assertEquals(0,f.unavailable);
        assertEquals(1,f.count("cancel")); assertFalse(f.client.pending());
    }
    @Test public void lateInitialPlayOrBookmarkRefreshesCustodyBeforeTeardown() {
        Fixture f=new Fixture(); f.client.position(f.oldOwner,0,false); f.reserve();
        assertEquals(1,f.accepted);
        f.client.playing(f.oldOwner,true); f.queue.drain();
        assertEquals(2,f.accepted); assertEquals(1,f.count("cancel"));
        assertTrue(f.requests.get(2).contains("startMs=0&playing=true"));
        f.client.position(f.oldOwner,42000,true); f.queue.drain();
        assertEquals(3,f.accepted); assertEquals(0,f.count("watch"));
        assertTrue(f.requests.get(4).contains("startMs=42000&playing=true"));
    }
    @Test public void committedHandoffFreezesRetiredDecoderIntent() {
        Fixture f=new Fixture(); assertFalse(f.client.active());
        assertFalse(f.client.commitHandoff());
        f.client.position(f.oldOwner,42000,false); f.reserve();
        assertTrue(f.client.active()); assertTrue(f.client.commitHandoff());
        f.client.position(f.oldOwner,99000,true); f.client.playing(f.oldOwner,true);
        f.queue.drain(); assertEquals(1,f.count("reserve")); assertEquals(0,f.count("cancel"));
        f.duringWatch=() -> f.client.video(f.newOwner,f.connection);
        f.client.connected(f.connection,() -> true); f.queue.drain();
        assertEquals("seek_requested",f.client.state()); assertFalse(f.client.active());
    }
    @Test public void adapterIdentityGuardCannotStopAnUnrelatedOrCanceledSource() {
        Fixture f=new Fixture();
        assertTrue(f.client.sourceMatches(owner -> owner==f.oldOwner));
        assertFalse(f.client.sourceMatches(owner -> owner==f.newOwner));
        f.client.cancel();
        assertFalse(f.client.sourceMatches(owner -> true));
    }
    @Test public void delayedUiHandoffRetiresExpiredCustodyWithoutUiHttp() {
        Fixture f=new Fixture();f.reserve();f.clock.addAndGet(120_000_000_000L);
        assertFalse(f.client.commitHandoff());assertFalse(f.client.pending());
        assertFalse(f.client.active());assertEquals(0,f.count("cancel"));
        assertEquals(0,f.unavailable);assertEquals("handoff_budget_expired",f.client.state());
        f.queue.drain();assertEquals(1,f.count("cancel"));assertEquals(1,f.unavailable);
        assertEquals(0,f.count("watch"));assertEquals(0,f.count("seek"));
    }
    @Test public void canceledTicketCannotWatchAndMissingVideoCannotReplaySeek() {
        Fixture f=new Fixture(); f.reserve(); f.client.cancel();
        f.client.connected(f.connection,() -> true); f.queue.drain();
        assertEquals(0,f.count("watch")); assertEquals(0,f.count("seek"));
    }
    @Test public void ambiguousWatchFailureNeverRetriesAnOrderedApi() {
        Fixture f=new Fixture(); f.reserve(); f.failWatch=true;
        f.client.connected(f.connection,() -> true); f.queue.drain();
        assertEquals(1,f.count("watch")); assertEquals(0,f.count("seek"));
        assertEquals(1,f.unavailable); assertEquals("watch_failed",f.client.state());
        assertFalse(f.client.state().contains("private"));
    }
    @Test public void expiredCustodyCannotRestore() {
        Fixture f=new Fixture(); f.reserve(); f.clock.addAndGet(120_000_000_000L);
        f.client.connected(f.connection,() -> true); f.queue.drain();
        assertEquals(0,f.count("watch")); assertEquals(1,f.count("cancel"));
        assertEquals(1,f.unavailable);
    }
}
