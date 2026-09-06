package opensagetv.vibe.miniclient.video;

/**
 * Conservative eligibility policy for retaining a player across media-source changes.
 *
 * <p>Only completed, non-circular Pull/SMB files are eligible. Push, DVD, HTTP,
 * growing, and legacy-unknown media continue through the established full-load path.</p>
 */
public final class MediaReplacementPolicy
{
    private MediaReplacementPolicy()
    {
    }

    public static Decision evaluate(boolean contextInitialized,
                                    boolean playerPresent,
                                    boolean playerReady,
                                    boolean currentPush,
                                    boolean currentDvd,
                                    boolean currentHttp,
                                    PlaybackMediaContext current,
                                    String nextUrl,
                                    boolean nextTimeshifted,
                                    long nextBufferSize)
    {
        if (!contextInitialized) return Decision.reject("first_load");
        if (!playerPresent) return Decision.reject("no_player");
        if (!playerReady) return Decision.reject("player_not_ready");
        if (currentPush) return Decision.reject("current_push");
        if (currentDvd) return Decision.reject("current_dvd");
        if (currentHttp) return Decision.reject("current_http");
        if (current == null) return Decision.reject("no_media_context");
        if (current.isTimeshifted()) return Decision.reject("current_active_or_legacy_unknown");
        if (current.getBufferSize() > 0) return Decision.reject("current_circular_buffer");
        if (nextTimeshifted) return Decision.reject("next_active_or_legacy_unknown");
        if (nextBufferSize > 0) return Decision.reject("next_circular_buffer");
        if (nextUrl == null || nextUrl.trim().isEmpty()) return Decision.reject("empty_url");

        String lower = nextUrl.trim().toLowerCase(java.util.Locale.US);
        if (lower.startsWith("push:")) return Decision.reject("next_push");
        if (lower.startsWith("dvd:")) return Decision.reject("next_dvd");
        if (lower.startsWith("http://") || lower.startsWith("https://"))
            return Decision.reject("next_http");
        if (lower.endsWith(".exlink")) return Decision.reject("next_external_link");
        return Decision.allow("eligible_completed_pull");
    }

    public static final class Decision
    {
        private final boolean eligible;
        private final String reason;

        private Decision(boolean eligible, String reason)
        {
            this.eligible = eligible;
            this.reason = reason;
        }

        private static Decision allow(String reason)
        {
            return new Decision(true, reason);
        }

        private static Decision reject(String reason)
        {
            return new Decision(false, reason);
        }

        public boolean isEligible()
        {
            return eligible;
        }

        public String getReason()
        {
            return reason;
        }
    }
}
