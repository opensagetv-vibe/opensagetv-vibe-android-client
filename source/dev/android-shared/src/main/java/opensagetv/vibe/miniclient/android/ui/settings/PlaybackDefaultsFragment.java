package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

public final class PlaybackDefaultsFragment extends PreferenceFragmentCompat {
    @Override public void onCreatePreferences(Bundle state,String rootKey){
        PreferenceScreen screen=getPreferenceManager().createPreferenceScreen(requireContext());setPreferenceScreen(screen);
        Preference note=new Preference(requireContext());note.setTitle("Saved Device Defaults");
        note.setSummary("These settings apply to subsequent playback. Current session overrides are not changed.");note.setSelectable(false);screen.addPreference(note);
        PlaybackSettingPreferences.append(screen,"filters","Video Filters");
        PlaybackSettingPreferences.append(screen,"display","Display Settings");
    }
}
