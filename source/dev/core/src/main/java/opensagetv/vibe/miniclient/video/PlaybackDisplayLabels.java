package opensagetv.vibe.miniclient.video;

/** Human-facing labels only; never alter the machine protocol or saved mode. */
public final class PlaybackDisplayLabels
{
    private PlaybackDisplayLabels() { }
    /** Actual producer/pusher state is authoritative over next-playback preferences. */
    public static String transport(boolean owned, boolean push, String configured)
    {
        if (owned) return "Direct";
        if (!push) return "Pull";
        return "fixed".equalsIgnoreCase(configured) ? "Fixed" : "Push";
    }
    public static String decoderKind(String kind)
    {
        if ("software".equalsIgnoreCase(kind)) return "Software";
        if ("hardware".equalsIgnoreCase(kind)) return "Hardware";
        return kind == null ? "" : kind;
    }
}
