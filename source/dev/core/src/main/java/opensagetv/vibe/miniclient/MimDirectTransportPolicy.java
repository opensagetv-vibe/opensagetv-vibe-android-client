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

    /**
     * Transcode currently depends on an original Pull path and a playable
     * native Pull fallback. Do not invent MPEG-2 decoding capabilities to
     * obtain that path: stock Core caches them across socket reconnects.
     * Until the plugin can restore a fresh ordinary Fixed watch session,
     * clients missing this fallback must retain ordinary Fixed from startup.
     */
    public static boolean supportsTranscodePullFallback(String nativeCodecs)
    {
        String codecs = nativeCodecs == null ? "" : nativeCodecs;
        return containsCodec(codecs, "H.264") && containsCodec(codecs, "MPEG2-VIDEO")
                && containsCodec(codecs, "MPEG2-VIDEO@HL");
    }

    public static boolean supportsH264Output(String nativeCodecs) {
        return containsCodec(nativeCodecs==null ? "" : nativeCodecs,"H.264");
    }

    /** Only the negotiated hosted transport accepts these input formats. */
    public static String sourceVideoCapabilities(String nativeCodecs,String mode,boolean watchRecovery) {
        String codecs=nativeCodecs==null ? "" : nativeCodecs;
        if (!TRANSCODE.equals(mode) || !watchRecovery || !supportsH264Output(codecs)) return codecs;
        if (!containsCodec(codecs,"MPEG2-VIDEO")) codecs+=",MPEG2-VIDEO";
        if (!containsCodec(codecs,"MPEG2-VIDEO@HL")) codecs+=",MPEG2-VIDEO@HL";
        return codecs;
    }

    private static boolean containsCodec(String codecs, String expected)
    {
        for (String codec : codecs.split(","))
            if (expected.equalsIgnoreCase(codec.trim())) return true;
        return false;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
