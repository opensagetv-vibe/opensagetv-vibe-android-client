package opensagetv.vibe.miniclient.android.config;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

import opensagetv.vibe.miniclient.config.MiniClientProfile;

/** Explicit bridge between Android SharedPreferences and validated profiles. */
public final class MiniClientProfilePreferences
{
    private MiniClientProfilePreferences() { }

    public static MiniClientProfile snapshot(SharedPreferences preferences, String name)
    {
        return MiniClientProfile.create(name, System.currentTimeMillis(), preferences.getAll());
    }

    public static boolean applyOrdinarySettings(SharedPreferences preferences, MiniClientProfile profile)
    {
        return apply(preferences, profile.getSettings());
    }

    public static boolean applyClientIds(SharedPreferences preferences, MiniClientProfile profile)
    {
        return apply(preferences, profile.getClientIds());
    }

    private static boolean apply(SharedPreferences preferences, Map<String, Object> values)
    {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new IllegalArgumentException("Unsupported profile value for " + key);
        }
        return editor.commit();
    }
}
