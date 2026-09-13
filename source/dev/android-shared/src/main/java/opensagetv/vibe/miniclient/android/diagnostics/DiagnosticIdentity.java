package opensagetv.vibe.miniclient.android.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.UUID;

/** Random support-artifact namespace; deliberately unrelated to MiniClient ID. */
final class DiagnosticIdentity
{
    private static final String KEY = "diagnostics/installation_token";
    private DiagnosticIdentity() { }

    static synchronized String token(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString(KEY, "");
        if (value.matches("[0-9a-f]{12}")) return value;
        value = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.US);
        preferences.edit().putString(KEY, value).apply();
        return value;
    }
}
