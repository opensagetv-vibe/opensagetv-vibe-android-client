package opensagetv.vibe.miniclient.android.video;

/** Passive, opt-in advice from real segment-body reads. This creates neither
 * traffic nor producers. Unknown/stale buffer reserve must never authorize an
 * increase; the handoff owner independently enforces cooldown and launch budget.
 * Keeping this pure makes synthetic bandwidth tests explicit, not WAN proof. */
final class DirectQualityAdvisor {
    // Direct is deliberately real-time paced, unlike the browser's burst HLS
    // producer. Its measured steady reserve can be only one published segment
    // (~1.1s in the42s fixture probe). Requiring Web's15s would prevent normal
    // recovery indefinitely. Use the existing handoff publication reserve,
    // but require ten spaced samples, 80% headroom on the HIGHER target and the
    // independently enforced60s cooldown. Never infer reserve from throughput.
    private static final long MIN_RECOVERY_RESERVE_MS=1000;
    private long lastSample;private int low,high;private double throughput;
    private int samples;private long lastReserve=-1;
    private boolean lastBuffering;private long gap;
    void reset(){lastSample=0;low=high=samples=0;throughput=0;lastReserve=-1;lastBuffering=false;gap=0;}
    String observation(){return "samples="+samples+",kbps="+Math.round(throughput)+",low="+low+",high="+high+",reserveMs="+lastReserve+",buffering="+lastBuffering+",gapMs="+gap;}
    int observe(long now,long bytes,long readMs,int current,int ceiling,boolean recovering,
                boolean buffering,long reserveMs){
        if(bytes<16384||readMs<100||current<250||now-lastSample<3000)return 0;
        // A seek, pause, source change or a long absence of eligible samples
        // invalidates the accumulated stability, not just its last sample.
        gap=lastSample==0?0:now-lastSample;
        // Slow full segments can themselves take more than10s. Preserve
        // consecutive constrained-transfer evidence up to30s apart; healthy
        // upshift stability remains stricter. Source/seek resets both.
        if(gap>10000)high=0;
        if(gap>30000){low=0;throughput=0;}
        lastSample=now;double measured=bytes*8.0/readMs;
        samples=Math.min(1000000,samples+1);lastReserve=reserveMs;lastBuffering=buffering;
        throughput=throughput==0?measured:throughput*.6+measured*.4;
        low=buffering&&throughput<(current+192)*1.25?low+1:0;
        high=!buffering&&reserveMs>=MIN_RECOVERY_RESERVE_MS&&recovering&&current<ceiling
                &&throughput>(Math.min(ceiling,current*5/4)+192)*1.8?high+1:0;
        if(low>=3){
            low=high=0;
            int target=Math.max(250,Math.min(current*7/10,(int)((throughput*.7-192)/50)*50));
            return target<current*.85?target:0;
        }
        if(high>=10){low=high=0;return Math.min(ceiling,current*5/4);}
        return 0;
    }
}
