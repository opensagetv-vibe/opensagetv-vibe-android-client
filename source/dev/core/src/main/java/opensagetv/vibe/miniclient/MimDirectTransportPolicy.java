package opensagetv.vibe.miniclient;

/** Connection-scoped policy for the optional stock-compatible MIM Direct transport. */
public final class MimDirectTransportPolicy
{
    public static final String OFF = "";
    public static final String COPY = "copy";
    public static final String TRANSCODE = "transcode";

    private MimDirectTransportPolicy() { }

    public static String activeMode(String streamingMode, String requestedMode,
                                    boolean capabilityAvailable)
    {
        if (!capabilityAvailable || !"fixed".equalsIgnoreCase(clean(streamingMode)))
            return OFF;
        String mode = clean(requestedMode).toLowerCase(java.util.Locale.US);
        return COPY.equals(mode) || TRANSCODE.equals(mode) ? mode : OFF;
    }

    public static boolean isActive(String mode)
    {
        return COPY.equals(mode) || TRANSCODE.equals(mode);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
