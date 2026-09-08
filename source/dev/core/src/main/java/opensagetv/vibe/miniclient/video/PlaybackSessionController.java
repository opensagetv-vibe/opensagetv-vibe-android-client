package opensagetv.vibe.miniclient.video;

/**
 * Assigns one generation to each loaded playback session and one ordered
 * sequence number to every operation within it. Player backends still execute
 * on their existing affinity thread; this class supplies the tokens that keep
 * queued work and callbacks from an older player from mutating a replacement.
 */
public final class PlaybackSessionController
{
    public enum Operation
    {
        LOAD,
        SEEK,
        FRAME_STEP,
        PLAYBACK_RATE,
        PLAY,
        PAUSE,
        FLUSH,
        RECONNECT,
        INACTIVE_FILE,
        SURFACE_REPLACEMENT,
        RECOVERY,
        STOP,
        FREE
    }

    public static final class Token
    {
        private final long sessionGeneration;
        private final long operationSequence;
        private final Operation operation;

        private Token(long sessionGeneration, long operationSequence, Operation operation)
        {
            this.sessionGeneration = sessionGeneration;
            this.operationSequence = operationSequence;
            this.operation = operation;
        }

        public long getSessionGeneration()
        {
            return sessionGeneration;
        }

        public long getOperationSequence()
        {
            return operationSequence;
        }

        public Operation getOperation()
        {
            return operation;
        }
    }

    private long sessionGeneration;
    private long operationSequence;
    private boolean active;
    private Token latest;

    public synchronized Token beginSession()
    {
        sessionGeneration++;
        active = true;
        latest = new Token(sessionGeneration, ++operationSequence, Operation.LOAD);
        return latest;
    }

    public synchronized Token beginOperation(Operation operation)
    {
        if (operation == null)
            throw new IllegalArgumentException("operation must not be null");
        latest = new Token(sessionGeneration, ++operationSequence, operation);
        return latest;
    }

    public synchronized Token currentSessionToken()
    {
        return new Token(sessionGeneration, operationSequence, Operation.LOAD);
    }

    public synchronized Token endSession(Operation operation)
    {
        if (operation != Operation.FREE)
            throw new IllegalArgumentException("Only FREE can end a playback session");
        Token ended = new Token(sessionGeneration, ++operationSequence, operation);
        latest = ended;
        active = false;
        sessionGeneration++;
        return ended;
    }

    public synchronized boolean isCurrentSession(Token token)
    {
        return token != null && active && token.sessionGeneration == sessionGeneration;
    }

    public synchronized boolean isLatestOperation(Token token)
    {
        return isCurrentSession(token) && latest != null
                && token.sessionGeneration == latest.sessionGeneration
                && token.operationSequence == latest.operationSequence;
    }

    public synchronized boolean isActive()
    {
        return active;
    }
}
