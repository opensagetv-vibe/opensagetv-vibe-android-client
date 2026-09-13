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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.ActivePlayerAdjustmentsDialog;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.android.config.MiniClientProfilePreferences;
import opensagetv.vibe.miniclient.android.config.SmbProfileRepository;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticExportController;
import opensagetv.vibe.miniclient.android.diagnostics.SmbDiagnosticsRepository;
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
    private Preference diagnosticsTest;
    private Preference diagnosticsExport;
    private Preference diagnosticsStatus;
    private Preference mediaTest;
    private Preference configurationTest;
    private volatile boolean destroyed;

    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.smb_profile_prefs, rootKey);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        selectedProfile = findPreference(AndroidPrefStore.SMB_PROFILE_SELECTED);
        refresh = findPreference("smb_profiles/refresh");
        save = findPreference("smb_profiles/save");
        load = findPreference("smb_profiles/load");
        diagnosticsTest = findPreference("smb_diagnostics/test");
        diagnosticsExport = findPreference("smb_diagnostics/export");
        diagnosticsStatus = findPreference("smb_diagnostics/status");
        mediaTest = findPreference("smb_direct/test");
        configurationTest = findPreference("smb_profiles/test");

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
        diagnosticsTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                testDiagnosticsSmb();
                return true;
            }
        });
        diagnosticsExport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                MediaCmd media = MiniclientApplication.get().getClient().getCurrentConnection() == null
                        ? null : MiniclientApplication.get().getClient().getCurrentConnection().getMediaCmd();
                DiagnosticExportController.show(requireActivity(),
                        ActivePlayerAdjustmentsDialog.diagnosticsTextForExport(requireActivity(), media));
                return true;
            }
        });
        mediaTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                testMediaSmb();
                return true;
            }
        });
        configurationTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override public boolean onPreferenceClick(Preference preference)
            {
                testConfigurationSmb();
                return true;
            }
        });
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        updateDiagnosticsState();
        refreshProfiles();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener()
            {
                @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
                {
                    if (key != null && key.startsWith("smb_diagnostics/")) updateDiagnosticsState();
                }
            };

    private void testDiagnosticsSmb()
    {
        diagnosticsTest.setEnabled(false);
        diagnosticsTest.setSummary("Testing DNS, connect, authentication, share, write, read, hash, and cleanup...");
        DiagnosticExportController.testDiagnosticsSmb(requireActivity(),
                new DiagnosticExportController.Completion()
                {
                    @Override public void done(String success, String failure)
                    {
                        if (!isAdded()) return;
                        diagnosticsTest.setEnabled(true);
                        String value = success != null ? success : "Failed: " + failure;
                        diagnosticsTest.setSummary(value);
                        Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void testMediaSmb()
    {
        mediaTest.setEnabled(false);
        mediaTest.setSummary("Testing configured mappings with read-only SMB operations...");
        io.execute(new Runnable()
        {
            @Override public void run()
            {
                String mappings = preferences.getString(AndroidPrefStore.SMB_MAPPINGS, "");
                String[] lines = mappings.split("[\\r\\n]+");
                int tested = 0;
                long latency = 0;
                String failure = null;
                StringBuilder stages = new StringBuilder();
                for (String line : lines)
                {
                    int arrow = line.indexOf("=>");
                    if (arrow < 0) continue;
                    String url = line.substring(arrow + 2).trim();
                    if (url.isEmpty()) continue;
                    char[] password = preferences.getString(AndroidPrefStore.SMB_PASSWORD, "").toCharArray();
                    SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(url,
                            preferences.getString(AndroidPrefStore.SMB_USERNAME, "").trim().isEmpty(),
                            preferences.getString(AndroidPrefStore.SMB_USERNAME, ""), password,
                            preferences.getString(AndroidPrefStore.SMB_DOMAIN, ""));
                    java.util.Arrays.fill(password, '\0');
                    try
                    {
                        SmbDiagnosticsRepository.TestResult test = repository.testReadOnly();
                        latency += test.latencyMs;
                        if (stages.length() > 0) stages.append("\n");
                        stages.append("Mapping ").append(tested + 1).append(": ")
                                .append(test.stageSummary);
                        tested++;
                    }
                    catch (Exception e) { failure = safeError(e); break; }
                    finally { repository.clear(); }
                }
                final String result = failure != null ? "Failed: " + failure
                        : tested == 0 ? "Failed: no valid SMB media mappings"
                        : "Media SMB passed: " + tested + " mapping(s), read-only, " + latency
                        + " ms total\n" + stages;
                post(new Runnable()
                {
                    @Override public void run()
                    {
                        mediaTest.setEnabled(true); mediaTest.setSummary(result);
                        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void testConfigurationSmb()
    {
        configurationTest.setEnabled(false);
        configurationTest.setSummary("Testing write, read, hash, and immediate cleanup...");
        io.execute(new Runnable()
        {
            @Override public void run()
            {
                char[] password = preferences.getString(AndroidPrefStore.SMB_PROFILE_PASSWORD, "").toCharArray();
                SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, ""),
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_USERNAME, "").trim().isEmpty(),
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_USERNAME, ""), password,
                        preferences.getString(AndroidPrefStore.SMB_PROFILE_DOMAIN, ""));
                java.util.Arrays.fill(password, '\0');
                String value;
                try
                {
                    SmbDiagnosticsRepository.TestResult result = repository.testConnection();
                    value = "Configuration SMB passed; write/read/hash/delete verified\n"
                            + result.stageSummary;
                }
                catch (Exception e) { value = "Failed: " + safeError(e); }
                finally { repository.clear(); }
                final String resultText = value;
                post(new Runnable()
                {
                    @Override public void run()
                    {
                        configurationTest.setEnabled(true); configurationTest.setSummary(resultText);
                        Toast.makeText(requireContext(), resultText, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void updateDiagnosticsState()
    {
        if (preferences == null) return;
        String mode = preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_MODE,
                AndroidPrefStore.DIAGNOSTICS_MODE_OFF);
        boolean enabled = !AndroidPrefStore.DIAGNOSTICS_MODE_OFF.equals(mode);
        if (diagnosticsTest != null) diagnosticsTest.setEnabled(enabled);
        boolean credentials = AndroidPrefStore.SMB_AUTH_CREDENTIALS.equals(preferences.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE, AndroidPrefStore.SMB_AUTH_ANONYMOUS));
        Preference username = findPreference(AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME);
        Preference password = findPreference(AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD);
        Preference domain = findPreference(AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN);
        if (username != null) username.setEnabled(credentials);
        if (password != null) password.setEnabled(credentials);
        if (domain != null) domain.setEnabled(credentials);
        String status = preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_STATUS,
                "No diagnostic bundle has been exported");
        String filename = preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_FILE, "");
        String sha256 = preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_SHA256, "");
        long bytes = preferences.getLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_BYTES, 0);
        long time = preferences.getLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_TIME, 0);
        String when = time <= 0 ? "" : DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(time));
        if (diagnosticsStatus != null) diagnosticsStatus.setSummary(status
                + (when.isEmpty() ? "" : "\nLast update: " + when)
                + (filename.isEmpty() ? "" : "\n" + filename + " (" + bytes + " bytes)"
                + (sha256.isEmpty() ? "" : "\nSHA-256: " + sha256)));
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
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private interface ProfileOperation { void run(SmbProfileRepository repository) throws Exception; }
}
