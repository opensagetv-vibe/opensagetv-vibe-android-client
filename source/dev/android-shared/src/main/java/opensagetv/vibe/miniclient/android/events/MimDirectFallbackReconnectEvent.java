package opensagetv.vibe.miniclient.android.events;

/**
 * Requests one stock-compatible MiniClient reconnect after both optional MIM
 * Direct startup and the original Pull fallback fail before the first frame.
 */
public final class MimDirectFallbackReconnectEvent
{
    public final String reason;

    public MimDirectFallbackReconnectEvent(String reason)
    {
        this.reason = reason == null ? "unknown" : reason;
    }
}
