package opensagetv.vibe.miniclient;

/** Protocol negotiation rules shared by ordinary Pull and shadow-Pull modes. */
public final class StreamingModePolicy
{
    private StreamingModePolicy() { }

    public static boolean isForcedPull(String mode)
    {
        return "pull".equalsIgnoreCase(mode)
                || "smb_direct".equalsIgnoreCase(mode)
                || "smb_auto".equalsIgnoreCase(mode);
    }
}
