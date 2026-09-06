package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import opensagetv.vibe.miniclient.android.R;

public class IJKPlayerSettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.ijkplayer_prefs, rootKey);

        final Preference decoders = findPreference("decoders");
        decoders.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                showHardwareDecoderInfo();
                return true;
            }

        });


    }

    private void showHardwareDecoderInfo()
    {
        CodecDialogFragment.showDialog(getParentFragmentManager());
    }

}
