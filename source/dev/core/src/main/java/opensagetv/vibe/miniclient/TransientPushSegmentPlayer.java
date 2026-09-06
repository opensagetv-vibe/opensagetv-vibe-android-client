package opensagetv.vibe.miniclient;

/**
 * Optional capability for PUSH players whose byte stream is split into
 * independently drained segments.  Unlike {@link MiniPlayerPlugin#setServerEOS()},
 * this ends only the current reader generation; a later segment can continue
 * on the same player/session after the server's FLUSH boundary.
 */
public interface TransientPushSegmentPlayer
{
    void signalPushSegmentEnd();
}
