package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.config.MiniClientProfilePreferences;
import opensagetv.vibe.miniclient.android.config.SmbProfileRepository;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.config.MiniClientProfile;

/** TV-friendly list/save/import UI for versioned profiles on an SMB share. */
public final class SmbProfileSettingsFragment extends PreferenceFragmentCompat
{
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private ListPreference selectedProfile;
    private Preference refresh;
    private Preference save;
    private Preference load;
    private volatile boolean destroyed;

    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.smb_profile_prefs, rootKey);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        selectedProfile = findPreference(AndroidPrefStore.SMB_PROFILE_SELECTED);
        refresh = findPreference("smb_profiles/refresh");
        save = findPreference("smb_profiles/save");
        load = findPreference("smb_profiles/load");

        refresh.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                refreshProfiles();
                return true;
            }
        });
        save.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                saveProfile(false);
                return true;
            }
        });
        load.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                loadSelectedProfile();
                return true;
            }
        });
        refreshProfiles();
    }

    private void refreshProfiles()
    {
        runIo(new ProfileOperation()
        {
            @Override public void run(SmbProfileRepository repository) throws Exception
            {
                final List<String> profiles = repository.list();
                post(new Runnable()
                {
                    @Override public void run()
                    {
                        CharSequence[] entries = profiles.toArray(new CharSequence[0]);
                        selectedProfile.setEntries(entries);
                        selectedProfile.setEntryValues(entries);
                        if (profiles.isEmpty()) selectedProfile.setValue(null);
                        selectedProfile.setSummary(profiles.isEmpty()
                                ? getString(R.string.smb_profiles_none)
                                : getString(R.string.smb_profiles_count, profiles.size()));
                    }
                });
            }
        });
    }

    private void saveProfile(final boolean overwrite)
    {
        final String name = preferences.getString(AndroidPrefStore.SMB_PROFILE_NAME, "").trim();
        final MiniClientProfile profile;
        try { profile = MiniClientProfilePreferences.snapshot(preferences, name); }
        catch (IllegalArgumentException e)
        {
            showError(e.getMessage());
            return;
        }
        runIo(new ProfileOperation()
        {
            @Override public void run(SmbProfileRepository repository) throws Exception
            {
                try { repository.save(profile, overwrite); }
                catch (SmbProfileRepository.ProfileAlreadyExistsException e)
                {
                    post(new Runnable()
                    {
                        @Override public void run() { confirmOverwrite(profile.getName()); }
                    });
                    return;
                }
                post(new Runnable()
                {
                    @Override public void run()
                    {
                        Toast.makeText(requireContext(),
                                getString(R.string.smb_profile_saved, MiniClientProfile.fileName(profile.getName())),
                                Toast.LENGTH_LONG).show();
                        refreshProfiles();
                    }
                });
            }
        });
    }

    private void confirmOverwrite(final String name)
    {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.smb_profile_overwrite_title)
                .setMessage(getString(R.string.smb_profile_overwrite_warning, MiniClientProfile.fileName(name)))
                .setNegativeButton(R.string.keep_existing_profile, null)
                .setPositiveButton(R.string.overwrite_profile, (dialog, which) -> saveProfile(true))
                .show();
    }

    private void loadSelectedProfile()
    {
        final String selected = selectedProfile.getValue();
        if (selected == null || selected.trim().isEmpty())
        {
            showError(getString(R.string.smb_profile_select_first));
            return;
        }
        runIo(new ProfileOperation()
        {
            @Override public void run(SmbProfileRepository repository) throws Exception
            {
                final MiniClientProfile profile = repository.load(selected);
                post(new Runnable()
                {
                    @Override public void run() { confirmImport(profile); }
                });
            }
        });
    }

    private void confirmImport(final MiniClientProfile profile)
    {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.smb_profile_import_title)
                .setMessage(getString(R.string.smb_profile_import_summary,
                        MiniClientProfile.fileName(profile.getName()), profile.getSettings().size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.import_settings, (dialog, which) -> applyOrdinarySettings(profile))
                .show();
    }

    private void applyOrdinarySettings(final MiniClientProfile profile)
    {
        if (!MiniClientProfilePreferences.applyOrdinarySettings(preferences, profile))
        {
            showError(getString(R.string.smb_profile_apply_failed));
            return;
        }
        Toast.makeText(requireContext(), R.string.smb_profile_settings_imported, Toast.LENGTH_LONG).show();
        if (profile.hasClientIds() && hasChangedClientId(profile)) promptForStoredClientId(profile);
    }

    private boolean hasChangedClientId(MiniClientProfile profile)
    {
        for (Map.Entry<String, Object> entry : profile.getClientIds().entrySet())
        {
            Object current = preferences.getAll().get(entry.getKey());
            if (!entry.getValue().equals(current)) return true;
        }
        return false;
    }

    private void promptForStoredClientId(final MiniClientProfile profile)
    {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.smb_profile_client_id_title)
                .setMessage(R.string.smb_profile_client_id_warning)
                .setNegativeButton(R.string.keep_local_client_id, null)
                .setPositiveButton(R.string.review_client_id_overwrite,
                        (dialog, which) -> verifyClientIdOverwrite(profile))
                .show();
    }

    private void verifyClientIdOverwrite(final MiniClientProfile profile)
    {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.smb_profile_client_id_verify_title)
                .setMessage(getString(R.string.smb_profile_client_id_verify, profile.getClientIds().size()))
                .setNegativeButton(R.string.keep_local_client_id, null)
                .setPositiveButton(R.string.overwrite_client_id, (dialog, which) ->
                {
                    if (MiniClientProfilePreferences.applyClientIds(preferences, profile))
                        Toast.makeText(requireContext(), R.string.smb_profile_client_id_imported, Toast.LENGTH_LONG).show();
                    else showError(getString(R.string.smb_profile_apply_failed));
                })
                .show();
    }

    private void runIo(final ProfileOperation operation)
    {
        setBusy(true);
        io.execute(new Runnable()
        {
            @Override public void run()
            {
                char[] password = preferences.getString(AndroidPrefStore.SMB_PROFILE_PASSWORD, "").toCharArray();
                SmbProfileRepository repository = new SmbProfileRepository(
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, ""),
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_USERNAME, ""),
                        password,
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_DOMAIN, ""));
                java.util.Arrays.fill(password, '\0');
                try { operation.run(repository); }
                catch (final Exception e)
                {
                    post(new Runnable()
                    {
                        @Override public void run() { showError(safeError(e)); }
                    });
                }
                finally
                {
                    repository.clear();
                    post(new Runnable()
                    {
                        @Override public void run() { setBusy(false); }
                    });
                }
            }
        });
    }

    private String safeError(Exception e)
    {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? getString(R.string.smb_profile_operation_failed)
                : message.replaceAll("(?i)smb://[^/@\\s]+@", "smb://");
    }

    private void setBusy(boolean busy)
    {
        if (refresh != null) refresh.setEnabled(!busy);
        if (save != null) save.setEnabled(!busy);
        if (load != null) load.setEnabled(!busy);
    }

    private void post(Runnable action)
    {
        if (!destroyed) main.post(action);
    }

    private void showError(String message)
    {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override public void onDestroy()
    {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private interface ProfileOperation { void run(SmbProfileRepository repository) throws Exception; }
}
