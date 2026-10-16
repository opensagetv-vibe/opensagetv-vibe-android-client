package opensagetv.vibe.miniclient;

import opensagetv.vibe.miniclient.prefs.PrefStore;

/**
 * Bounded policy for SageTV's legacy {@code PUSH_BUFFER_LIMIT} capability.
 *
 * <p>Stock Core uses 32 KiB when the property is absent and clamps a client
 * value to 128 KiB.  Keep the user-facing choices inside that proven wire
 * contract: this controls the server's maximum Push write size, not the
 * Android player's multi-megabyte decode buffer.  Unknown, malformed, and
 * oversized values fail safely to the absent-property stock behavior.</p>
 */
public final class LegacyPushBufferLimitPolicy
{
    public static final String AUTO = "auto";
    public static final int STOCK_DEFAULT_BYTES = 32 * 1024;
    public static final int CORE_MAX_BYTES = 128 * 1024;

    private LegacyPushBufferLimitPolicy()
    {
    }

    public static boolean isValidPreference(String value)
    {
        String clean = clean(value);
        return AUTO.equals(clean) || "32768".equals(clean)
                || "65536".equals(clean) || "131072".equals(clean);
    }

    public static String normalize(String value)
    {
        String clean = clean(value);
        return isValidPreference(clean) ? clean : AUTO;
    }

    /** Value sent on the MiniClient property wire; empty preserves stock. */
    public static String propertyValue(String preference)
    {
        String normalized = normalize(preference);
        return AUTO.equals(normalized) ? "" : normalized;
    }

    public static String propertyValue(PrefStore preferences)
    {
        return propertyValue(preferences == null ? AUTO : preferences.getString(
                PrefStore.Keys.push_buffer_limit, AUTO));
    }

    /** Effective Core limit after applying the known stock default/clamp. */
    public static int effectiveBytes(String preference)
    {
        String property = propertyValue(preference);
        if (property.isEmpty()) return STOCK_DEFAULT_BYTES;
        try
        {
            return Math.min(CORE_MAX_BYTES, Integer.parseInt(property));
        }
        catch (NumberFormatException ignored)
        {
            return STOCK_DEFAULT_BYTES;
        }
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
    }
}
