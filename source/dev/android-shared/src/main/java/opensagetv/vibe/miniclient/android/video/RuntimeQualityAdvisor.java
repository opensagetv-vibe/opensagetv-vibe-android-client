package opensagetv.vibe.miniclient.android.video;

/** Same policy as Web RuntimeQualityAdvisor. Original implementation using
 * hls.js fast/slow EWMA and Media3 buffer/hysteresis ideas, not track switching.
 * Only real completed-body reads enter this passive, default-off controller. */
final class RuntimeQualityAdvisor {
    private double fast,slow;private long last,reserve=-1,highWater,cooldown;private int low,high,samples;
    void reset(){fast=slow=0;last=highWater=cooldown=0;reserve=-1;low=high=samples=0;}
    String observation(){return "runtime,samples="+samples+",fastKbps="+(long)fast+",slowKbps="+(long)slow
        +",low="+low+",high="+high+",reserveMs="+reserve;}
    void attempted(long now,boolean up){cooldown=now+(up?60000:20000);low=high=0;}
    int observe(long now,double kbps,long sampleMs,int current,int ceiling,boolean buffering,long bufferMs){
        // Double.isFinite is API24; the client still supports Android6/API23.
        if(Double.isNaN(kbps)||Double.isInfinite(kbps)||kbps<=0||sampleMs<100||bufferMs<0||current<250||ceiling<current)return 0;
        if(last>0&&now-last<3000)return 0;
        if(last>0&&now-last>30000){fast=slow=0;low=high=0;reserve=-1;highWater=0;}
        double weight=Math.max(.1,Math.min(10,sampleMs/1000.0));
        fast=fast==0?kbps:fast*Math.pow(.5,weight/3)+kbps*(1-Math.pow(.5,weight/3));
        slow=slow==0?kbps:slow*Math.pow(.5,weight/10)+kbps*(1-Math.pow(.5,weight/10));
        double estimate=Math.min(fast,slow);
        boolean falling=reserve>=0&&bufferMs<reserve-250;
        boolean stable=reserve<0||bufferMs>=reserve-250;
        highWater=Math.max(highWater,bufferMs);reserve=bufferMs;last=now;samples++;
        low=(buffering||bufferMs<5000||falling&&bufferMs<10000)&&estimate<(current+192)*1.2?low+1:0;
        int next=Math.min(ceiling,(current*5/4/50)*50);
        high=!buffering&&stable&&bufferMs>=Math.min(12000,Math.max(1000,highWater*3/4))&&next>current
            &&estimate>(next+192)/.70?high+1:0;
        if(now<cooldown)return 0;
        if(low>=3){int target=Math.max(250,Math.min(current*3/4,((int)(estimate*.75-192)/50)*50));
            if(target<current*.85){attempted(now,false);return target;}}
        if(high>=10){attempted(now,true);return next;}
        return 0;
    }
}
