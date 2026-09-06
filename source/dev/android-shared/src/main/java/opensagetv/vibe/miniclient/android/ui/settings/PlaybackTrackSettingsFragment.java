package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.media.TrackPreferencePolicy;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/**
 * Configures track selection. SageTV STV owns caption Off/On state through the
 * standard legacy subtitle callback on extractor-backed players, or through
 * VIDEO_CC_STATE as a compatibility extension. The explicit fallback remains
 * for players such as IJK that cannot return raw caption packets.
 */
public class PlaybackTrackSettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.playback_track_prefs, rootKey);

        EditTextPreference audioLanguage = findPreference(PrefStore.Keys.preferred_audio_language);
        EditTextPreference subtitleLanguage = findPreference(PrefStore.Keys.preferred_subtitle_language);
        ListPreference captionStandard = findPreference(PrefStore.Keys.preferred_caption_standard);
        EditTextPreference captionService = findPreference(PrefStore.Keys.preferred_caption_service);
        ListPreference legacyServerCaptionMode = findPreference(
                PrefStore.Keys.legacy_server_caption_mode);

        Preference.OnPreferenceChangeListener languageValidator =
                new Preference.OnPreferenceChangeListener()
                {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue)
                    {
                        if (!TrackPreferencePolicy.isValidLanguage(String.valueOf(newValue)))
                        {
                            Toast.makeText(requireContext(), R.string.invalid_language_tag,
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }
                        return true;
                    }
                };
        if (audioLanguage != null)
        {
            audioLanguage.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            audioLanguage.setOnPreferenceChangeListener(languageValidator);
        }
        if (subtitleLanguage != null)
        {
            subtitleLanguage.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            subtitleLanguage.setOnPreferenceChangeListener(languageValidator);
        }
        if (captionStandard != null)
        {
            captionStandard.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            captionStandard.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateServiceState(String.valueOf(newValue), captionService);
                    return true;
                }
            });
        }
        if (captionService != null)
        {
            captionService.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            captionService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    String standard = captionStandard == null ? TrackPreferencePolicy.CAPTION_STANDARD_AUTO
                            : captionStandard.getValue();
                    try
                    {
                        int service = Integer.parseInt(String.valueOf(newValue).trim());
                        int maximum = TrackPreferencePolicy.CAPTION_STANDARD_CEA608.equals(
                                TrackPreferencePolicy.normalizeCaptionStandard(standard)) ? 4 : 63;
                        if (service < 1 || service > maximum)
                            throw new NumberFormatException();
                        return true;
                    }
                    catch (NumberFormatException ignored)
                    {
                        Toast.makeText(requireContext(),
                                TrackPreferencePolicy.CAPTION_STANDARD_CEA608.equals(
                                        TrackPreferencePolicy.normalizeCaptionStandard(standard))
                                        ? R.string.invalid_cea608_channel
                                        : R.string.invalid_cea708_service,
                                Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            });
        }
        if (legacyServerCaptionMode != null)
            legacyServerCaptionMode.setSummaryProvider(
                    ListPreference.SimpleSummaryProvider.getInstance());
        updateServiceState(captionStandard == null ? TrackPreferencePolicy.CAPTION_STANDARD_AUTO
                : captionStandard.getValue(), captionService);
    }

    private void updateServiceState(String standard, EditTextPreference service)
    {
        if (service == null)
            return;
        String normalized = TrackPreferencePolicy.normalizeCaptionStandard(standard);
        service.setEnabled(!TrackPreferencePolicy.CAPTION_STANDARD_AUTO.equals(normalized));
        if (TrackPreferencePolicy.CAPTION_STANDARD_CEA608.equals(normalized))
        {
            try
            {
                if (Integer.parseInt(service.getText()) > 4)
                    service.setText("1");
            }
            catch (RuntimeException ignored)
            {
                service.setText("1");
            }
        }
    }
}
