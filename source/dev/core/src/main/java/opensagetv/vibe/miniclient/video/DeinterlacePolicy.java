package opensagetv.vibe.miniclient.video;

/** Location policy, not a claim that Android exposes a hardware deinterlace switch. */
public final class DeinterlacePolicy
{
    private DeinterlacePolicy() { }
    /** Only algorithms implemented by our software-frame hook are selectable. */
    public static String normalizeFilter(String value)
    { return "fast".equals(value) || "yadif".equals(value) ? value : "auto"; }
    public static int nativeFilter(String value)
    { return "fast".equals(normalizeFilter(value)) ? 1 : "yadif".equals(normalizeFilter(value)) ? 2 : 0; }
    public static String normalize(String value)
    {
        if ("off".equals(value) || "client".equals(value) || "client_gpu".equals(value) || "mim".equals(value)) return value;
        return "auto";
    }
    /** A saved Fixed-only choice must not disable native client processing after a transport change. */
    public static String effectiveMode(String requested, boolean fixed)
    {
        if (requested == null || requested.isEmpty()) return "";
        String value = normalize(requested);
        return "mim".equals(value) && !fixed ? "auto" : value;
    }
    /**
     * Media3 and legacy Exo have no software field filter. Explicit Client for
     * MPEG-2 therefore requires their device MediaCodec path, rather than
     * silently falling back to an unfiltered software decoder. Auto preserves
     * the existing decoding policy; audio and unrelated progressive codecs
     * must never be affected. This is NOT a vendor deinterlace on/off switch.
     */
    public static boolean requiresDeviceMpeg2(String requested, String mime)
    {
        String mode=normalize(requested);
        return ("client".equals(mode) || "client_gpu".equals(mode)) && "video/mpeg2".equals(mime);
    }
    public static boolean clientSoftwareEnabled(String requested, String legacyIjk)
    {
        if (requested == null || requested.isEmpty()) return !"off".equals(legacyIjk);
        String mode = normalize(requested);
        return "auto".equals(mode) || "client".equals(mode);
    }
    public static String serverFilter(String requested, String legacyMim)
    {
        if (requested == null || requested.isEmpty()) return legacyMim;
        String mode = normalize(requested);
        if ("client".equals(mode) || "client_gpu".equals(mode) || "off".equals(mode)) return "off";
        return "mim".equals(mode) ? "on" : "auto";
    }
}
