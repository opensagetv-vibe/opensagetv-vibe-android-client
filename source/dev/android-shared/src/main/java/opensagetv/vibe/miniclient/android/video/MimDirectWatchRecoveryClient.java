package opensagetv.vibe.miniclient.android.video;

import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One bounded stock-plugin Watch/Seek handoff, not a decoder watchdog or MCP
 * dependency. All HTTP runs on the supplied worker; callbacks only publish
 * lifecycle events. A STOP/new source invalidates local intent immediately.
 * Source path, ticket and actual context remain private, never diagnostics.
 */
final class MimDirectWatchRecoveryClient {
    interface Api { MimDirectSessionClient.Response post(String base,String path) throws Exception; }
    interface Ready { boolean ready(); }
    interface Clock { long nanoTime(); }
    interface SourceMatcher { boolean matches(Object owner); }
    private final Executor worker;
    private final Api api;
    private final Clock clock;
    private final Object lock=new Object();
    private String base="", source="", clientId="";
    private Object sourceOwner, freshConnection;
    private long sequence, positionMs;
    private boolean playing=true, enabled, videoReady;
    private boolean handoffCommitted;
    private Ticket ticket;
    private Runnable failureSignal;
    private Runnable acceptedSignal;
    private volatile String state="off";

    private static final class Ticket {
        final String base,token,context;
        final long mediaId,intent,expiresNs;
        Ticket(String base,String token,String context,long id,long intent,long expiresNs) {
            this.base=base; this.token=token; this.context=context;
            mediaId=id; this.intent=intent; this.expiresNs=expiresNs;
        }
        String binding() throws Exception {
            return "?recoveryToken="+token+"&context="+URLEncoder.encode(context,"UTF-8")+
                    "&mediaFileId="+mediaId+"&intent="+intent;
        }
    }

    MimDirectWatchRecoveryClient(Executor worker,Api api,Clock clock) {
        this.worker=worker; this.api=api; this.clock=clock;
    }

    void configure(String base,boolean enabled) {
        cancel();
        synchronized(lock) { this.base=base; this.enabled=enabled; state=enabled ? "ready" : "off"; }
    }

    void source(Object owner,String path,String id) {
        cancel();
        synchronized(lock) {
            sourceOwner=owner; source=path; clientId=normalize(id); positionMs=0; playing=true;
        }
    }

    void position(Object owner,long target,boolean playing) {
        Ticket superseded=null; Runnable accept=null, failure=null;
        synchronized(lock) {
            if (!enabled || sourceOwner !=owner || target<0 || target>14L*86400000L) return;
            if (handoffCommitted) return;
            if (!"ready".equals(state) && !"capturing".equals(state)
                    && !"awaiting_fresh_connection".equals(state)) return;
            if (positionMs==target && this.playing==playing) return;
            positionMs=target; this.playing=playing; sequence++;
            // A newer server target during capture supersedes only read-only
            // reservation. Before Activity teardown, refresh that custody and
            // publish a new event; the retired event fails the pending check.
            // After handoff old-decoder commands cannot overwrite the snapshot.
            if (ticket !=null) {
                superseded=ticket; ticket=null; state="ready";
                accept=acceptedSignal; failure=failureSignal;
            }
        }
        if (superseded !=null) {
            final Ticket retired=superseded;
            worker.execute(() -> cancelRemote(retired));
            if (accept !=null && failure !=null) request(accept,failure);
        }
    }
    void playing(Object owner,boolean playing) {
        final long target;
        synchronized(lock) { if (owner !=sourceOwner) return; target=positionMs; }
        position(owner,target,playing);
    }

    boolean request(final Runnable accepted,final Runnable unavailable) {
        synchronized(lock) {
            if (!enabled || sourceOwner ==null || source.isEmpty()
                    || !clientId.matches("[0-9a-f]{12}") || !"ready".equals(state)) return false;
            state="capturing";
            failureSignal=unavailable;
            acceptedSignal=accepted;
        }
        worker.execute(() -> {
            // Only read-only reserve is superseded. Ordered Watch/Seek is
            // never automatically repeated on an ambiguous HTTP outcome.
            for (int attempt=0;attempt<3;attempt++) {
                final String endpoint,path,id; final long intent,target; final boolean requestedPlaying;
                synchronized(lock) {
                    if (!"capturing".equals(state)) return;
                    endpoint=base; path=source; id=clientId; intent=sequence;
                    target=positionMs; requestedPlaying=playing;
                }
                Ticket received=null;
                try {
                    MimDirectSessionClient.Response response=api.post(endpoint,
                            "/v1/direct/recovery/reserve?clientId="+id+"&source="+
                                    URLEncoder.encode(path,"UTF-8")+"&startMs="+target+
                                    "&playing="+requestedPlaying+"&intent="+intent);
                    if (response.status !=200) throw new IllegalStateException("reserve_rejected");
                    Map<String,String> values=flat(response.body);
                    String token=values.get("recoveryToken"), context=values.get("context");
                    long mediaId=number(values,"mediaFileId"), lease=number(values,"expiresInMs");
                    if (number(values,"contractVersion") !=1 || number(values,"intent") !=intent
                            || token ==null || !token.matches("[0-9a-f]{32}")
                            || !id.equals(normalize(context)) || mediaId<=0 || lease<=0 || lease>120000)
                        throw new IllegalArgumentException("invalid_recovery_binding");
                    received=new Ticket(endpoint,token,context,mediaId,intent,
                            clock.nanoTime()+lease*1_000_000L);
                    synchronized(lock) {
                        if ("capturing".equals(state) && sequence==intent) {
                            ticket=received; state="awaiting_fresh_connection";
                            accepted.run(); return;
                        }
                    }
                    cancelRemote(received);
                } catch (Exception failed) {
                    if (received !=null) cancelRemote(received);
                    synchronized(lock) {
                        if (!"capturing".equals(state)) return;
                        state="capture_unavailable";
                    }
                    unavailable.run(); return;
                }
            }
            synchronized(lock) { if (!"capturing".equals(state)) return; state="capture_superseded"; }
            unavailable.run();
        });
        return true;
    }

    boolean pending() {
        synchronized(lock) { return ticket !=null && "awaiting_fresh_connection".equals(state); }
    }
    boolean active() {
        synchronized(lock) {
            return "capturing".equals(state) || pending() || "waiting_for_ui".equals(state)
                    || "requesting_watch".equals(state) || "waiting_for_video".equals(state)
                    || "requesting_seek".equals(state);
        }
    }
    boolean commitHandoff() {
        final Ticket selected;
        synchronized(lock) {
            if (!pending() || handoffCommitted) return false;
            selected=ticket;
            if (current(selected)) { handoffCommitted=true; return true; }
        }
        // A delayed UI event must retire expired custody, not leave an
        // eternal pending recovery that suppresses every later player error.
        // fail publishes local retirement immediately; HTTP/notification stay
        // on the worker even when this caller is Android's UI thread.
        fail(selected,"handoff_budget_expired");
        return false;
    }

    void connected(final Object connection,final Ready ready) {
        final Ticket selected;
        synchronized(lock) {
            if (!pending()) return;
            selected=ticket; freshConnection=connection; videoReady=false; state="waiting_for_ui";
        }
        worker.execute(() -> {
            long deadline=clock.nanoTime()+15_000_000_000L;
            try {
                while (current(selected) && clock.nanoTime()<deadline) {
                    if (!ready.ready()) { Thread.sleep(250); continue; }
                    synchronized(lock) { if (!current(selected)) return; state="requesting_watch"; }
                    MimDirectSessionClient.Response response=api.post(selected.base,
                            "/v1/direct/recovery/watch"+selected.binding());
                    String result=flat(response.body).get("state");
                    if (response.status==409 && "not_ready".equals(result)) {
                        // This explicit refusal performed no Watch; it is the
                        // only safe stage retry. Never retry I/O or API failure.
                        Thread.sleep(250); continue;
                    }
                    if (response.status !=200 || !"watch_requested".equals(result))
                        throw new IllegalStateException("watch_not_accepted");
                    synchronized(lock) {
                        if (!current(selected)) return;
                        state="waiting_for_video";
                        if (videoReady) seek(selected);
                    }
                    worker.execute(() -> {
                        long videoDeadline=clock.nanoTime()+30_000_000_000L;
                        try {
                            while (current(selected) && "waiting_for_video".equals(state)
                                    && clock.nanoTime()<videoDeadline) Thread.sleep(250);
                            if ("waiting_for_video".equals(state)) fail(selected,"video_budget_expired");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); fail(selected,"video_wait_interrupted");
                        }
                    });
                    return;
                }
                fail(selected,"watch_budget_expired");
            } catch (Exception failed) { fail(selected,"watch_failed"); }
        });
    }

    /** Event from the actual new decoder, not READY/a moving server clock. */
    void video(Object owner,Object connection) {
        synchronized(lock) {
            if (ticket ==null || owner==sourceOwner || connection !=freshConnection) return;
            if (!"requesting_watch".equals(state) && !"waiting_for_video".equals(state)) return;
            videoReady=true;
            if ("waiting_for_video".equals(state)) seek(ticket);
        }
    }

    private void seek(final Ticket selected) {
        if (!current(selected) || !"waiting_for_video".equals(state)) return;
        state="requesting_seek";
        worker.execute(() -> {
            if (!current(selected)) return;
            try {
                MimDirectSessionClient.Response response=api.post(selected.base,
                        "/v1/direct/recovery/seek"+selected.binding());
                if (response.status !=200 || !"seek_requested".equals(flat(response.body).get("state")))
                    throw new IllegalStateException("seek_not_accepted");
                synchronized(lock) {
                    if (current(selected)) { ticket=null; state="seek_requested"; }
                }
            } catch (Exception failed) { fail(selected,"seek_failed"); }
        });
    }

    private boolean current(Ticket selected) {
        synchronized(lock) {
            return ticket==selected && sequence==selected.intent && clock.nanoTime()<selected.expiresNs;
        }
    }
    private void fail(Ticket selected,String reason) {
        final Runnable notify;
        synchronized(lock) {
            if (ticket !=selected) return; ticket=null; sequence++; state=reason;
            notify=failureSignal; failureSignal=null;
        }
        worker.execute(() -> {
            cancelRemote(selected);
            if (notify !=null) notify.run();
        });
    }
    void cancel() {
        final Ticket retired;
        synchronized(lock) {
            retired=ticket; ticket=null; sourceOwner=null; source=""; freshConnection=null;
            failureSignal=null;
            acceptedSignal=null;
            sequence++; state=enabled ? "ready" : "off";
            handoffCommitted=false;
        }
        if (retired !=null) worker.execute(() -> cancelRemote(retired));
    }
    private void cancelRemote(Ticket selected) {
        try { api.post(selected.base,"/v1/direct/recovery/cancel"+selected.binding()); }
        catch (Exception ignored) { /* Server monotonic expiry still bounds custody. */ }
    }
    String state() { return state; }
    boolean sourceIsCurrent(Object owner) { synchronized(lock) { return sourceOwner==owner; } }
    boolean sourceMatches(SourceMatcher matcher) {
        synchronized(lock) { return sourceOwner !=null && matcher.matches(sourceOwner); }
    }

    /** Flat closed-schema candidate replies; duplicate/escaped/nested values fail closed. */
    static Map<String,String> flat(String body) {
        if (body==null || body.length()>4096) throw new IllegalArgumentException("invalid_reply");
        String value=body.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) throw new IllegalArgumentException("invalid_reply");
        value=value.substring(1,value.length()-1);
        Pattern field=Pattern.compile("\\G\\s*\"([A-Za-z][A-Za-z0-9]*)\"\\s*:\\s*(?:\"([^\"\\\\]*)\"|(true|false|-?[0-9]+))\\s*(,|$)");
        Matcher matcher=field.matcher(value); int end=0;
        Map<String,String> result=new LinkedHashMap<>();
        while (matcher.find()) {
            if (result.put(matcher.group(1),matcher.group(2)==null ? matcher.group(3) : matcher.group(2)) !=null)
                throw new IllegalArgumentException("duplicate_field");
            end=matcher.end();
            if (",".equals(matcher.group(4)) && end==value.length()) throw new IllegalArgumentException("trailing_comma");
        }
        if (end!=value.length()) throw new IllegalArgumentException("invalid_reply");
        return result;
    }
    private static long number(Map<String,String> values,String name) {
        try { return Long.parseLong(values.get(name)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("invalid_number"); }
    }
    static String normalize(String id) {
        return id==null ? "" : id.toLowerCase(java.util.Locale.US).replace(":","").replace("-","");
    }
    static boolean supported(String capabilities) {
        if (capabilities==null || capabilities.length()>256*1024) return false;
        Matcher match=Pattern.compile("\"directWatchRecovery\"\\s*:\\s*(\\{[^{}]*\\})").matcher(capabilities);
        if (!match.find()) return false;
        try {
            Map<String,String> values=flat(match.group(1));
            return number(values,"contractVersion")==1 && "true".equals(values.get("available"))
                    && "single-segment-non-DVD".equals(values.get("sourceScope"))
                    && "fresh-watch-video-seek".equals(values.get("restoreStages"));
        } catch (RuntimeException invalid) { return false; }
    }
}
