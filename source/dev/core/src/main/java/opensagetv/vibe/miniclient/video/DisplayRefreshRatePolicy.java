package opensagetv.vibe.miniclient.video;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure selection policy for matching video frame rate to an Android display mode.
 *
 * <p>The Android integration owns applying a selected mode and waiting for HDMI to settle. This
 * class only chooses from modes the platform reported. It preserves the current resolution unless
 * the caller explicitly allows a resolution change.</p>
 */
public final class DisplayRefreshRatePolicy
{
    public static final float MIN_CONTENT_RATE_HZ = 10.0f;
    public static final float MAX_CONTENT_RATE_HZ = 240.0f;
    private static final double MAX_RATE_ERROR_HZ = 0.12;
    private static final int MAX_INTEGER_MULTIPLE = 8;

    public enum Preference
    {
        EXACT_FIRST,
        HIGHEST_MULTIPLE
    }

    public static final class Mode
    {
        public final int id;
        public final int width;
        public final int height;
        public final float refreshRateHz;

        public Mode(int id, int width, int height, float refreshRateHz)
        {
            this.id = id;
            this.width = width;
            this.height = height;
            this.refreshRateHz = refreshRateHz;
        }
    }

    public static final class Decision
    {
        public final Mode mode;
        public final boolean changeRequired;
        public final boolean exactMatch;
        public final int multiple;
        public final String reason;

        private Decision(Mode mode, boolean changeRequired, boolean exactMatch, int multiple,
                String reason)
        {
            this.mode = mode;
            this.changeRequired = changeRequired;
            this.exactMatch = exactMatch;
            this.multiple = multiple;
            this.reason = reason;
        }

        public static Decision unchanged(Mode current, String reason)
        {
            return new Decision(current, false, false, 0, reason);
        }
    }

    private DisplayRefreshRatePolicy() { }

    public static Decision choose(float contentRateHz, Mode current, List<Mode> supportedModes,
            boolean allowResolutionChange, Preference preference)
    {
        if (current == null)
            return Decision.unchanged(null, "no_current_display_mode");
        if (contentRateHz < MIN_CONTENT_RATE_HZ || contentRateHz > MAX_CONTENT_RATE_HZ
                || Float.isNaN(contentRateHz) || Float.isInfinite(contentRateHz))
            return Decision.unchanged(current, "invalid_or_unknown_content_rate");
        if (supportedModes == null || supportedModes.isEmpty())
            return Decision.unchanged(current, "no_supported_display_modes");

        Match currentMatch = match(current, contentRateHz);
        if (currentMatch != null)
            return new Decision(current, false, currentMatch.multiple == 1,
                    currentMatch.multiple, "current_mode_matches");

        List<Mode> candidates = new java.util.ArrayList<Mode>();
        for (Mode mode : supportedModes)
        {
            if (mode == null || mode.id <= 0 || mode.refreshRateHz <= 0.0f)
                continue;
            if (!allowResolutionChange
                    && (mode.width != current.width || mode.height != current.height))
                continue;
            if (match(mode, contentRateHz) != null)
                candidates.add(mode);
        }
        if (candidates.isEmpty())
            return Decision.unchanged(current, allowResolutionChange
                    ? "no_compatible_refresh_mode" : "no_compatible_mode_at_current_resolution");

        final Preference resolvedPreference = preference == null
                ? Preference.EXACT_FIRST : preference;
        Collections.sort(candidates, new Comparator<Mode>()
        {
            @Override
            public int compare(Mode left, Mode right)
            {
                Match leftMatch = match(left, contentRateHz);
                Match rightMatch = match(right, contentRateHz);
                if (resolvedPreference == Preference.EXACT_FIRST)
                {
                    int exact = Boolean.compare(rightMatch.multiple == 1,
                            leftMatch.multiple == 1);
                    if (exact != 0)
                        return exact;
                    int error = Double.compare(leftMatch.errorHz, rightMatch.errorHz);
                    if (error != 0)
                        return error;
                    int distance = Float.compare(
                            Math.abs(left.refreshRateHz - current.refreshRateHz),
                            Math.abs(right.refreshRateHz - current.refreshRateHz));
                    if (distance != 0)
                        return distance;
                }
                else
                {
                    int rate = Float.compare(right.refreshRateHz, left.refreshRateHz);
                    if (rate != 0)
                        return rate;
                    int error = Double.compare(leftMatch.errorHz, rightMatch.errorHz);
                    if (error != 0)
                        return error;
                }
                return Integer.compare(left.id, right.id);
            }
        });

        Mode selected = candidates.get(0);
        Match selectedMatch = match(selected, contentRateHz);
        return new Decision(selected, selected.id != current.id,
                selectedMatch.multiple == 1, selectedMatch.multiple,
                selectedMatch.multiple == 1 ? "exact_refresh_match" : "integer_multiple_match");
    }

    private static Match match(Mode mode, float contentRateHz)
    {
        int multiple = Math.max(1, Math.round(mode.refreshRateHz / contentRateHz));
        if (multiple > MAX_INTEGER_MULTIPLE)
            return null;
        double errorHz = Math.abs(mode.refreshRateHz - contentRateHz * multiple);
        if (errorHz > MAX_RATE_ERROR_HZ)
            return null;
        return new Match(multiple, errorHz);
    }

    private static final class Match
    {
        final int multiple;
        final double errorHz;

        Match(int multiple, double errorHz)
        {
            this.multiple = multiple;
            this.errorHz = errorHz;
        }
    }
}
