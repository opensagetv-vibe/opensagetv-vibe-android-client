package opensagetv.vibe.miniclient;

/** Transport intent for MiniClient capability negotiation; persisted values stay compatible. */
public final class StreamingModePolicy
{
    private StreamingModePolicy() { }

    public static boolean isForcedPush(String mode)
    {
        // "dynamic" is the persisted value of the UI's Push selection. Merely
        // advertising Push alongside Pull lets Core prefer Pull and makes the
        // selected transport misleading (notably for Media3 MPEG-TS seeking).
        // Accept the explicit spelling too, without treating unknown/Fixed/SMB
        // values as Push or migrating existing user preferences.
        return "dynamic".equalsIgnoreCase(mode) || "push".equalsIgnoreCase(mode);
    }

    public static boolean isForcedPull(String mode)
    {
        return "pull".equalsIgnoreCase(mode)
                || "smb_direct".equalsIgnoreCase(mode)
                || "smb_auto".equalsIgnoreCase(mode);
    }
}
