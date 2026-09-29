package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class FixedTranscodingFragment extends PreferenceFragmentCompat
{

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Must happen before AndroidX inflates its ListPreferences. Legacy and
        // debug builds stored the bitrate selections as Integer values.
        AndroidPrefStore.migrateFixedTranscodingListPreferenceTypes(
                PreferenceManager.getDefaultSharedPreferences(requireContext()));
        setPreferencesFromResource(R.xml.transcoding_prefs, rootKey);

        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_PREFERENCE), AndroidPrefStore.FIXED_ENCODING_PREFERENCE_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_FORMAT), AndroidPrefStore.FIXED_ENCODING_FORMAT_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS), AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS_DEFAULT + "");
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_FPS), AndroidPrefStore.FIXED_ENCODING_FPS_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION), AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC),AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS), AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS_DEFAULT + "");
        PreferenceUtils.setDefaultValue(findPreference(AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS), AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS_DEFAULT);
        PreferenceUtils.setDefaultValue(findPreference(
                PrefStore.Keys.mim_direct_deinterlace), "auto");

        ListPreference deinterlace = findPreference(
                PrefStore.Keys.mim_direct_deinterlace);
        if (deinterlace != null)
        {
            deinterlace.setSummaryProvider(
                    ListPreference.SimpleSummaryProvider.getInstance());
        }

        EditTextPreference captionPort = findPreference(
                PrefStore.Keys.fixed_caption_side_channel_port);
        if (captionPort != null)
        {
            captionPort.setOnBindEditTextListener(editText -> editText.setInputType(
                    InputType.TYPE_CLASS_NUMBER));
        }

    }



}
