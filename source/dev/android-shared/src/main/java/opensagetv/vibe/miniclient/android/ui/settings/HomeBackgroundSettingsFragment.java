package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Options for bounded Home/background session preservation and recovery. */
public final class HomeBackgroundSettingsFragment extends PreferenceFragmentCompat
{
    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.home_background_prefs, rootKey);
        ListPreference timeout = findPreference(PrefStore.Keys.background_session_timeout_seconds);
        if (timeout != null)
            timeout.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
