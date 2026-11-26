package opensagetv.vibe.miniclient.video;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded lifecycle observations, not a playback clock or watchdog. No player
 * reads, I/O, paths, payloads, threads, timers or retries. A renderer callback
 * without a tagged frame timestamp is deliberately NOT exact seek landing. */
public final class PlaybackExperienceTrace {
    private final LongSupplier clock;
    private final ArrayDeque<Map<String,Object>> events=new ArrayDeque<>();
    private long generation=-1,seekSequence=-1,requested=-1,producerAccepted=-1,requestedAt=-1,preparedAfter=-1;
    private boolean active;
    public PlaybackExperienceTrace(){this(()->System.nanoTime()/1000000L);}
    public PlaybackExperienceTrace(LongSupplier clock){this.clock=clock;}
    public synchronized void begin(PlaybackSessionController.Token token){
        events.clear();generation=token.getSessionGeneration();seekSequence=-1;requested=producerAccepted=requestedAt=preparedAfter=-1;active=true;
        add("source-load",token,null);
    }
    public synchronized void operation(PlaybackSessionController.Token token){
        if(!current(token))return;
        add("operation-"+token.getOperation().name().toLowerCase(java.util.Locale.ROOT),token,null);
        if(token.getOperation()==PlaybackSessionController.Operation.STOP||token.getOperation()==PlaybackSessionController.Operation.FREE){
            seekSequence=-1;requested=producerAccepted=requestedAt=preparedAfter=-1;
            if(token.getOperation()==PlaybackSessionController.Operation.FREE)active=false;
        }
    }
    public synchronized void requested(PlaybackSessionController.Token token,long target){
        if(!current(token)||token.getOperation()!=PlaybackSessionController.Operation.SEEK)return;
        seekSequence=token.getOperationSequence();requested=Math.max(0,target);producerAccepted=-1;preparedAfter=-1;requestedAt=clock.getAsLong();
        add("seek-requested",token,requested);add("client-operation-accepted",token,requested);
    }
    public synchronized void producerPrepared(PlaybackSessionController.Token token,long position){
        if(!current(token)||token.getOperationSequence()!=seekSequence)return;
        producerAccepted=Math.max(0,position);preparedAfter=Math.max(0,clock.getAsLong()-requestedAt);
        add("owned-producer-prepared",token,producerAccepted);
    }
    public synchronized void rendererCallback(){
        if(!active)return;
        // Existing backend callback has no source-tagged presented PTS. Keep
        // its observation, never convert the requested/decoder time to proof.
        add("renderer-first-frame-callback-unattributed",null,null);
    }
    public synchronized Map<String,Object> snapshot(){
        Map<String,Object> out=new LinkedHashMap<>();out.put("schemaVersion",1);out.put("active",active);
        out.put("sourceGeneration",generation);out.put("seekSequence",seekSequence<0?null:seekSequence);
        out.put("requestedPositionMs",requested<0?null:requested);out.put("clientAcceptedPositionMs",requested<0?null:requested);
        out.put("producerAcceptedPositionMs",producerAccepted<0?null:producerAccepted);out.put("producerPrepareMs",preparedAfter<0?null:preparedAfter);
        out.put("presentedPositionMs",null);out.put("stockUiProcessedAtMs",null);
        out.put("presentationEvidence","Renderer callback is not source-tagged frame/timestamp landing proof");
        out.put("events",new ArrayList<>(events));return out;
    }
    private boolean current(PlaybackSessionController.Token token){return active&&token!=null&&token.getSessionGeneration()==generation;}
    private void add(String phase,PlaybackSessionController.Token token,Long position){
        Map<String,Object> row=new LinkedHashMap<>();row.put("monotonicMs",clock.getAsLong());row.put("phase",phase);
        row.put("operationSequence",token==null?null:token.getOperationSequence());row.put("positionMs",position);
        while(events.size()>=64)events.removeFirst();events.addLast(java.util.Collections.unmodifiableMap(row));
    }
}
