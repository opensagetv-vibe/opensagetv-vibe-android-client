package opensagetv.vibe.miniclient.android.diagnostics;

import java.util.*;

/** Read-only sampled history. No timers, player methods, seeks or Core hooks.
 * Sequence gaps are reported honestly; an observation is not frame-exact landing.
 */
public final class DiagnosticTimeline {
    private final ArrayDeque<Map<String,Object>> events = new ArrayDeque<>();
    private Object generation;
    private long sequence = -1, pendingTarget = -1, pendingTime, pendingSequence;
    private boolean hadSample, buffering;
    private long bufferingSince;
    private int retries;
    public synchronized List<Map<String,Object>> sample(Object owner, long seq, long target,
            long requestWallMs, long position, boolean seeking, boolean stalled, int retryCount, long now) {
        if (owner != generation) {
            generation = owner; sequence = -1; pendingTarget = -1; hadSample = false;
            buffering = false; retries = retryCount;
            add(now, "playback-context", null, null, "New connection/player context; previous observations are historical");
        }
        if (seq > 0 && seq != sequence) {
            boolean baseline = sequence < 0;
            if (sequence > 0 && seq > sequence + 1) add(now, "sampling-gap", null, null,
                    (seq - sequence - 1) + " seek requests occurred between browser samples; targets unavailable");
            if (pendingTarget >= 0) add(now, "seek-superseded", pendingTarget, null, "A newer request replaced the pending observation");
            sequence = seq; pendingSequence = seq; pendingTarget = baseline ? -1 : target;
            pendingTime = requestWallMs > 0 && requestWallMs <= now ? requestWallMs : now;
            add(pendingTime, baseline ? "last-known-server-seek" : "server-seek-request", target, null,
                    "Request #" + seq + (baseline ? "; observed as baseline, landing not measured" : "; stock server may translate Skip/Comskip into this request"));
        }
        if (seq == 0 && sequence < 0) sequence = 0;
        if (pendingTarget >= 0 && position >= 0 && !seeking && !stalled) {
            add(now, "post-seek-observation", pendingTarget, position,
                    "Request #" + pendingSequence + "; sampled after " + Math.max(0, now - pendingTime)
                    + " ms; not proof of exact landing"); pendingTarget = -1;
        }
        if (hadSample && stalled != buffering) {
            if (stalled) bufferingSince = now;
            add(now, stalled ? "buffering-observed" : "buffering-recovered", null, position >= 0 ? position : null,
                    stalled ? "Observed at browser sampling cadence" : "Observed duration " + Math.max(0, now - bufferingSince) + " ms");
        } else if (!hadSample && stalled) bufferingSince = now;
        if (hadSample && retryCount != retries) add(now, "recovery-counter", null, null,
                "Backend retries " + retries + " -> " + retryCount);
        hadSample = true; buffering = stalled; retries = retryCount;
        return snapshot();
    }
    private void add(long now, String kind, Long requested, Long actual, String detail) {
        Map<String,Object> row = new LinkedHashMap<>(); row.put("timeMs",now); row.put("kind",kind);
        row.put("requestedMs",requested); row.put("observedMs",actual); row.put("detail",detail);
        while (events.size() >= 64) events.removeFirst(); events.addLast(Collections.unmodifiableMap(row));
    }
    public synchronized List<Map<String,Object>> snapshot() { return new ArrayList<>(events); }
    public synchronized void clear() { events.clear(); generation = null; sequence = -1; pendingTarget = -1; hadSample = false; }
}
