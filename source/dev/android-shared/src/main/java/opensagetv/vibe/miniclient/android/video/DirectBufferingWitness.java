package opensagetv.vibe.miniclient.android.video;

/** Existing identity-fenced backend callbacks only; no player poll. A transfer
 * captures a stamp before open and can prove a buffering interval even if the
 * player becomes READY just before that transfer closes. Odd stamps mean an
 * active interval; even stamps mean no current buffering. */
final class DirectBufferingWitness {
    private volatile long stamp;
    private volatile long changedAt=-1;
    synchronized void observe(boolean buffering,long now){
        if(buffering==buffering())return;
        stamp++;changedAt=now;
    }
    synchronized void reset(){stamp=0;changedAt=-1;}
    long stamp(){return stamp;}
    boolean buffering(){return (stamp&1)!=0;}
    boolean during(long opened){long current=stamp;return (opened&1)!=0||(current&1)!=0||current!=opened;}
    boolean recent(long now){return buffering()||changedAt>=0&&now>=changedAt&&now-changedAt<10000;}
}
