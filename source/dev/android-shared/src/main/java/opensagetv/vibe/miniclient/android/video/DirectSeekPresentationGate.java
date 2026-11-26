package opensagetv.vibe.miniclient.android.video;

/** A Direct seek replaces the player rather than calling seekTo(), so Exo's
 * DISCONTINUITY_REASON_SEEK is not its completion witness. Arm only as the
 * current replacement is attached; its identity-fenced first-frame callback
 * may finish that seek. Old/duplicate callbacks and a new load cannot do so. */
final class DirectSeekPresentationGate {
    private long pending=-1;
    private long heldPosition=-1;
    synchronized void holdConfirmedPosition(long confirmed){heldPosition=confirmed>=0?confirmed:-1;}
    synchronized long heldPosition(){return heldPosition;}
    synchronized void arm(long generation){pending=generation;}
    synchronized void cancel(){pending=-1;heldPosition=-1;}
    synchronized boolean presented(long generation){
        if(pending<0||pending!=generation)return false;
        pending=-1;heldPosition=-1;return true;
    }
}
