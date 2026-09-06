package opensagetv.vibe.miniclient.android.video;

/** Formats an explicit on-demand decoder snapshot without adding player callbacks. */
public final class CodecRuntimeObservations
{
    private CodecRuntimeObservations()
    {
    }

    public static String compactWire(String engine, String codecName, String codecKind,
                                     String queueMode, long initCount, long releaseCount,
                                     String playerError)
    {
        boolean active = codecName != null && !codecName.trim().isEmpty();
        return "codecObservationCount=" + (active ? 1 : 0)
                + ";codecObservations=" + (active
                ? clean(engine) + ',' + clean(codecName) + ',' + clean(codecKind)
                    + ',' + clean(queueMode) + ",init=" + initCount
                    + ",release=" + releaseCount + ",playerError=" + cleanEmpty(playerError)
                : "");
    }

    private static String clean(String value)
    {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.trim().replace(';', '_').replace('|', '_').replace(',', '_');
    }

    private static String cleanEmpty(String value)
    {
        return value == null || value.trim().isEmpty() ? "none" : clean(value);
    }
}
