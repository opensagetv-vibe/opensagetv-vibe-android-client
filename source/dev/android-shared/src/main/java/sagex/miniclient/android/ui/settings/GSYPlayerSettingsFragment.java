package sagex.miniclient.android.ui.settings;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import sagex.miniclient.android.R;
import sagex.miniclient.android.video.PlayerBackend;
import sagex.miniclient.android.video.gsy.GSYPlayerEngine;

/** Settings for the independent GSYVideoPlayer engine selector. */
public class GSYPlayerSettingsFragment extends PreferenceFragment
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gsyplayer_prefs);

        Preference version = findPreference("gsy_version");
        if (version != null) version.setSummary(PlayerBackend.GSY_VERSION);

        final ListPreference engine = (ListPreference) findPreference("gsy_player_engine");
        if (engine != null)
        {
            if (engine.getValue() == null) engine.setValue(GSYPlayerEngine.DEFAULT_PREFERENCE);
            updateSummary(engine);
            engine.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    int index = engine.findIndexOfValue(String.valueOf(newValue));
                    engine.setSummary(index >= 0 ? engine.getEntries()[index] : String.valueOf(newValue));
                    return true;
                }
            });
        }
    }

    private static void updateSummary(ListPreference preference)
    {
        int index = preference.findIndexOfValue(preference.getValue());
        if (index >= 0) preference.setSummary(preference.getEntries()[index]);
    }
}
