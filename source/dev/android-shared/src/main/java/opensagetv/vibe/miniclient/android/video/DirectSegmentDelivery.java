package opensagetv.vibe.miniclient.android.video;

/** A closed published HLS segment only. Measure blocked read calls, not the
 * gaps when an extractor/decoder stops consuming, HTTP open/producer wait or
 * the first body read. Partial/cancelled and tiny/cached reads stay unknown. */
public final class DirectSegmentDelivery {
    private boolean first=true,complete;
    private long bytes,nanos,received;
    private final long expected;
    public DirectSegmentDelivery(){this(-1);}
    public DirectSegmentDelivery(long expected){this.expected=expected;}
    public void read(int count,long elapsedNs){
        if(count<0){complete=expected<0||received==expected;return;}
        if(count==0)return;
        received+=count;
        // HLS knows a published chunk's response/range length and may close
        // exactly there without another read(-1). That is complete, unlike
        // cancellation, a premature EOF or receiving beyond the declared body.
        if(expected>=0)complete=received==expected;
        if(first){first=false;return;}
        bytes+=count;nanos+=Math.max(0,elapsedNs);
    }
    public boolean measured(){return complete&&bytes>=16384&&nanos>=100000000L;}
    public long bytes(){return bytes;}
    public long readMs(){return nanos/1000000L;}
}
