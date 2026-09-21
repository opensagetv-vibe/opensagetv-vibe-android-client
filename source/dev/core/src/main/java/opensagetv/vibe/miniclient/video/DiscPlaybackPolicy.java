package opensagetv.vibe.miniclient.video;

/** Resolves persisted DISC policy without weakening the stable native default. */
public final class DiscPlaybackPolicy
{
    public enum Effective { NATIVE, HYBRID, TRANSFORMED_MAIN_FEATURE, UNAVAILABLE }

    public static final class Resolution
    {
        public final String requested;
        public final Effective effective;
        public final boolean fallback;
        public final String reason;

        private Resolution(String requested, Effective effective, boolean fallback, String reason)
        {
            this.requested = requested;
            this.effective = effective;
            this.fallback = fallback;
            this.reason = reason;
        }

        public boolean advertisesRemoteNavigation()
        {
            return effective != Effective.UNAVAILABLE;
        }
    }

    private DiscPlaybackPolicy() {}

    public static Resolution resolve(String requested, boolean allowNativeFallback,
            boolean transformAvailable)
    {
        String normalized = normalizeRequested(requested);
        if ("native".equals(normalized))
            return new Resolution(normalized, Effective.NATIVE, false, "");
        if ("hybrid".equals(normalized))
        {
            if (transformAvailable)
                return new Resolution(normalized, Effective.HYBRID, false, "");
            return unavailableOrNative(normalized, allowNativeFallback,
                    "Hybrid DISC playback requires a compatible server transform provider");
        }
        if ("transformed_main_feature".equals(normalized))
        {
            if (transformAvailable)
                return new Resolution(normalized, Effective.TRANSFORMED_MAIN_FEATURE, false, "");
            return unavailableOrNative(normalized, allowNativeFallback,
                    "Transformed main-feature playback requires a compatible server provider");
        }
        // Auto intentionally remains on the commissioned native path until the
        // optional transform gate is promoted. Unknown historical values are treated the
        // same way so an app update cannot strand ordinary DVD playback.
        return new Resolution("auto", Effective.NATIVE,
                !"auto".equals(normalized),
                "auto".equals(normalized) ? "" : "Unknown DISC policy; using native playback");
    }

    /**
     * Migrates the former implementation-specific value while advertising
     * only the provider-neutral policy to updated servers.
     */
    public static String normalizeRequested(String requested)
    {
        String normalized = requested == null ? "auto" : requested.trim().toLowerCase();
        if ("mim_main_feature".equals(normalized))
            return "transformed_main_feature";
        return normalized;
    }

    private static Resolution unavailableOrNative(String requested,
            boolean allowNativeFallback, String reason)
    {
        if (allowNativeFallback)
            return new Resolution(requested, Effective.NATIVE, true, reason);
        return new Resolution(requested, Effective.UNAVAILABLE, false, reason);
    }
}
