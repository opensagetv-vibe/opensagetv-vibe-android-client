package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import opensagetv.vibe.miniclient.android.R;

public final class PushSettingsFragment extends PreferenceFragmentCompat {
    @Override public void onCreatePreferences(Bundle state, String key) {
        setPreferencesFromResource(R.xml.push_prefs, key);
        ListPreference limit = findPreference("push_buffer_limit");
        limit.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
