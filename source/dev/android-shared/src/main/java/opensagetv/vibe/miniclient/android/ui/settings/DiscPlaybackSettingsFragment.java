package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Options specific to authored discs and DVD folders. */
public final class DiscPlaybackSettingsFragment extends PreferenceFragmentCompat
{
    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.disc_playback_prefs, rootKey);
        ListPreference policy = findPreference(PrefStore.Keys.disc_playback_policy);
        if (policy != null)
            policy.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
