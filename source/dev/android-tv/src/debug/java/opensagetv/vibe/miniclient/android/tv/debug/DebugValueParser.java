package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.Intent;

/** Pure parsing/escaping helpers for the debug-only broadcast wire contract. */
final class DebugValueParser
{
    private DebugValueParser()
    {
    }

    static Integer optionalInt(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    static Long optionalLong(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Long.valueOf(value);
    }

    static Boolean optionalBoolean(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : Boolean.valueOf(parseBoolean(value, false));
    }

    static String optionalString(Intent intent, String key)
    {
        String value = clean(intent.getStringExtra(key));
        return value.isEmpty() ? null : value;
    }

    static Integer kbToBytes(Integer valueKb)
    {
        return valueKb == null ? null : Integer.valueOf(Math.multiplyExact(valueKb, 1024));
    }

    static int parseBoundedInt(String value, int defaultValue, int minValue, int maxValue)
    {
        String cleaned = clean(value);
        if (cleaned.isEmpty())
            return defaultValue;
        int parsed = Integer.parseInt(cleaned);
        return Math.max(minValue, Math.min(maxValue, parsed));
    }

    static boolean parseBoolean(String value, boolean defaultValue)
    {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) return defaultValue;
        if ("true".equals(cleaned) || "1".equals(cleaned) || "yes".equals(cleaned)) return true;
        if ("false".equals(cleaned) || "0".equals(cleaned) || "no".equals(cleaned)) return false;
        throw new IllegalArgumentException("invalid boolean: " + value);
    }

    static String text(String value)
    {
        return value == null ? "" : value.trim();
    }

    static String clean(String value)
    {
        return value == null ? "" : value.trim().toLowerCase();
    }

    static long ageMs(long nowMonotonicMs, long eventMonotonicMs)
    {
        if (eventMonotonicMs < 0)
            return -1;
        return Math.max(0L, nowMonotonicMs - eventMonotonicMs);
    }

    static String safe(String value)
    {
        if (value == null) return "";
        return value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }
}

