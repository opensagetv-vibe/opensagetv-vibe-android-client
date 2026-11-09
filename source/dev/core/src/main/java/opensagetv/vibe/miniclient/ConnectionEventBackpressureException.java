package opensagetv.vibe.miniclient;

/** Explicit bounded-queue backpressure; caption producers may retain and retry. */
public final class ConnectionEventBackpressureException extends IllegalStateException
{
    public ConnectionEventBackpressureException() { super("Connection event queue full"); }
}
