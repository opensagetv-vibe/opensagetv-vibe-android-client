package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Device-local transaction for automated tests that temporarily change preferences.
 * Values, including credentials, never leave private app storage or the response.
 */
final class DebugSettingsCheckpoint
{
    private static final String BACKUP_NAME =
            "opensagetv_vibe_debug_test_settings_checkpoint";
    private static final String VALUE_PREFIX = "value/";
    private static final String VALID = "checkpoint_valid";

    private DebugSettingsCheckpoint() { }

    static String checkpoint(Context context)
    {
        SharedPreferences backup = context.getSharedPreferences(BACKUP_NAME,
                Context.MODE_PRIVATE);
        if (backup.getBoolean(VALID, false))
            throw new IllegalStateException("an un-restored settings checkpoint already exists");

        Map<String, ?> current = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        SharedPreferences.Editor editor = backup.edit().clear();
        int count = 0;
        for (Map.Entry<String, ?> entry : current.entrySet())
        {
            put(editor, VALUE_PREFIX + entry.getKey(), entry.getValue());
            count++;
        }
        editor.putBoolean(VALID, true);
        if (!editor.commit())
            throw new IllegalStateException("could not persist settings checkpoint");
        return "op=settings_checkpoint;captured=true;count=" + count
                + ";valuesRemainPrivate=true";
    }

    static String restore(Context context)
    {
        SharedPreferences backup = context.getSharedPreferences(BACKUP_NAME,
                Context.MODE_PRIVATE);
        if (!backup.getBoolean(VALID, false))
            return "op=settings_restore;restored=false;count=0";

        SharedPreferences target = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = target.edit().clear();
        int count = 0;
        for (Map.Entry<String, ?> entry : backup.getAll().entrySet())
        {
            if (!entry.getKey().startsWith(VALUE_PREFIX)) continue;
            put(editor, entry.getKey().substring(VALUE_PREFIX.length()), entry.getValue());
            count++;
        }
        if (!editor.commit())
            throw new IllegalStateException("could not restore settings checkpoint");
        if (!backup.edit().clear().commit())
            throw new IllegalStateException("settings restored but checkpoint cleanup failed");
        return "op=settings_restore;restored=true;count=" + count
                + ";valuesRemainPrivate=true";
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value)
    {
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set)
            editor.putStringSet(key, new HashSet<String>((Set<String>) value));
        else throw new IllegalArgumentException("unsupported preference type for " + key);
    }
}
