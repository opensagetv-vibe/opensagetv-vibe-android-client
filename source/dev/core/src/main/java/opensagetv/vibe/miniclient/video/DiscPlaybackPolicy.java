package opensagetv.vibe.miniclient.video;

/** Resolves persisted DISC policy without weakening the stable native default. */
public final class DiscPlaybackPolicy
{
    public enum Effective { NATIVE, HYBRID, MIM_MAIN_FEATURE, UNAVAILABLE }

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
            boolean mimDiscAvailable)
    {
        String normalized = requested == null ? "auto" : requested.trim().toLowerCase();
        if ("native".equals(normalized))
            return new Resolution(normalized, Effective.NATIVE, false, "");
        if ("hybrid".equals(normalized))
        {
            if (mimDiscAvailable)
                return new Resolution(normalized, Effective.HYBRID, false, "");
            return unavailableOrNative(normalized, allowNativeFallback,
                    "Hybrid DISC playback requires a compatible server and MIM/FFmpeg");
        }
        if ("mim_main_feature".equals(normalized))
        {
            if (mimDiscAvailable)
                return new Resolution(normalized, Effective.MIM_MAIN_FEATURE, false, "");
            return unavailableOrNative(normalized, allowNativeFallback,
                    "MIM main-feature playback requires a compatible server and MIM/FFmpeg");
        }
        // Auto intentionally remains on the commissioned native path until the
        // live MIM gate is promoted. Unknown historical values are treated the
        // same way so an app update cannot strand ordinary DVD playback.
        return new Resolution("auto", Effective.NATIVE,
                !"auto".equals(normalized),
                "auto".equals(normalized) ? "" : "Unknown DISC policy; using native playback");
    }

    private static Resolution unavailableOrNative(String requested,
            boolean allowNativeFallback, String reason)
    {
        if (allowNativeFallback)
            return new Resolution(requested, Effective.NATIVE, true, reason);
        return new Resolution(requested, Effective.UNAVAILABLE, false, reason);
    }
}
