package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
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
import opensagetv.vibe.miniclient.android.config.SmbFolderBrowserRepository;
import opensagetv.vibe.miniclient.android.config.SmbServerProfile;
import opensagetv.vibe.miniclient.android.config.SmbServerProfileStore;
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
    private ListPreference selectedServer;
    private SmbServerProfileStore serverStore;
    private Preference serverSave;
    private Preference serverTest;
    private Preference serverDelete;
    private Preference manageServers;
    private Preference mediaBrowse;
    private Preference configurationBrowse;
    private Preference diagnosticsBrowse;
    private volatile boolean destroyed;

    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.smb_profile_prefs, rootKey);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        serverStore = new SmbServerProfileStore(preferences);
        selectedServer = findPreference(SmbServerProfileStore.SELECTED_KEY);
        serverSave = findPreference("smb_servers/save");
        serverTest = findPreference("smb_servers/test");
        serverDelete = findPreference("smb_servers/delete");
        manageServers = findPreference("smb_servers/manage");
        mediaBrowse = findPreference("smb_direct/browse");
        configurationBrowse = findPreference("smb_profiles/browse");
        diagnosticsBrowse = findPreference("smb_diagnostics/browse");
        selectedProfile = findPreference(AndroidPrefStore.SMB_PROFILE_SELECTED);
        refresh = findPreference("smb_profiles/refresh");
        save = findPreference("smb_profiles/save");
        load = findPreference("smb_profiles/load");
        diagnosticsTest = findPreference("smb_diagnostics/test");
        diagnosticsExport = findPreference("smb_diagnostics/export");
        diagnosticsStatus = findPreference("smb_diagnostics/status");
        mediaTest = findPreference("smb_direct/test");
        configurationTest = findPreference("smb_profiles/test");

        for (String key : new String[]{SmbServerProfileStore.SELECTED_KEY,
                "smb_servers/edit/name", "smb_servers/edit/host", "smb_servers/edit/share",
                "smb_servers/edit/port", "smb_servers/edit/auth/mode",
                "smb_servers/edit/username", "smb_servers/edit/password", "smb_servers/edit/domain",
                "smb_servers/save", "smb_servers/test", "smb_servers/delete",
                AndroidPrefStore.SMB_SOURCE_PREFIX, AndroidPrefStore.SMB_MAPPINGS,
                AndroidPrefStore.SMB_USERNAME, AndroidPrefStore.SMB_PASSWORD,
                AndroidPrefStore.SMB_DOMAIN, AndroidPrefStore.SMB_READ_AHEAD_KB,
                AndroidPrefStore.SMB_PROFILE_DIRECTORY, AndroidPrefStore.SMB_PROFILE_USERNAME,
                AndroidPrefStore.SMB_PROFILE_PASSWORD, AndroidPrefStore.SMB_PROFILE_DOMAIN,
                AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY,
                AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE,
                AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME,
                AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD,
                AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN,
                "smb_direct/test", "smb_profiles/test", "smb_diagnostics/test"})
        {
            Preference hidden = findPreference(key);
            if (hidden != null) hidden.setVisible(false);
        }

        selectedServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                String id = newValue == null ? "" : String.valueOf(newValue);
                preferences.edit().putString(SmbServerProfileStore.SELECTED_KEY, id).apply();
                SmbServerProfile profile = serverStore.find(id);
                if (profile == null) clearServerEditor();
                else
                {
                    populateServerEditor(profile);
                    profile.clear();
                }
                refreshServers(false);
                return false;
            }
        });
        serverSave.setOnPreferenceClickListener(preference -> { saveServer(); return true; });
        serverTest.setOnPreferenceClickListener(preference -> { testSelectedServer(); return true; });
        serverDelete.setOnPreferenceClickListener(preference -> { confirmDeleteServer(); return true; });
        manageServers.setOnPreferenceClickListener(preference -> { showServerMenu(); return true; });
        mediaBrowse.setOnPreferenceClickListener(preference -> { showMediaMappingMenu(); return true; });
        configurationBrowse.setOnPreferenceClickListener(preference -> { showDestinationMenu(Destination.CONFIGURATION); return true; });
        diagnosticsBrowse.setOnPreferenceClickListener(preference -> { showDestinationMenu(Destination.DIAGNOSTICS); return true; });

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
        refreshServers(true);
        updateServerEditorState();
        updateFolderSummaries();
        updateDiagnosticsState();
        refreshProfiles();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener()
            {
                @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
                {
                    if (key != null && key.startsWith("smb_diagnostics/")) updateDiagnosticsState();
                    if ("smb_servers/edit/auth/mode".equals(key)) updateServerEditorState();
                    if (key != null && (key.endsWith("/directory") || AndroidPrefStore.SMB_MAPPINGS.equals(key)))
                        updateFolderSummaries();
                }
            };

    private void refreshServers(boolean loadSelection)
    {
        List<SmbServerProfile> profiles = serverStore.list();
        CharSequence[] names = new CharSequence[profiles.size() + 1];
        CharSequence[] ids = new CharSequence[profiles.size() + 1];
        names[0] = "Add a new server...";
        ids[0] = "";
        for (int index = 0; index < profiles.size(); index++)
        {
            SmbServerProfile profile = profiles.get(index);
            names[index + 1] = profile.getName();
            ids[index + 1] = profile.getId();
        }
        selectedServer.setEntries(names);
        selectedServer.setEntryValues(ids);
        String selected = preferences.getString(SmbServerProfileStore.SELECTED_KEY, "");
        SmbServerProfile current = serverStore.find(selected);
        if (current == null) selected = "";
        selectedServer.setValue(selected);
        selectedServer.setSummary(current == null ? "Add or select an SMB server"
                : current.getName() + " — " + current.directoryUrl(""));
        serverTest.setEnabled(current != null);
        serverDelete.setEnabled(current != null);
        mediaBrowse.setEnabled(!profiles.isEmpty());
        configurationBrowse.setEnabled(!profiles.isEmpty());
        diagnosticsBrowse.setEnabled(!profiles.isEmpty());
        manageServers.setSummary(profiles.isEmpty()
                ? "No SMB servers configured"
                : profiles.size() + " SMB server" + (profiles.size() == 1 ? "" : "s") + " configured");
        if (loadSelection && current != null) populateServerEditor(current);
        if (current != null) current.clear();
        for (SmbServerProfile profile : profiles) profile.clear();
    }

    private void showServerMenu()
    {
        final List<SmbServerProfile> profiles = serverStore.list();
        CharSequence[] labels = new CharSequence[profiles.size() + 1];
        labels[0] = "Add SMB server";
        for (int index = 0; index < profiles.size(); index++)
            labels[index + 1] = profiles.get(index).getName() + " — " + profiles.get(index).getShare();
        new AlertDialog.Builder(requireContext())
                .setTitle("SMB servers")
                .setItems(labels, (dialog, which) ->
                {
                    if (which == 0)
                    {
                        clearProfiles(profiles);
                        showServerEditorDialog("");
                        return;
                    }
                    String id = profiles.get(which - 1).getId();
                    clearProfiles(profiles);
                    showServerActions(id);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> clearProfiles(profiles))
                .setOnCancelListener(dialog -> clearProfiles(profiles))
                .show();
    }

    private void showServerActions(final String profileId)
    {
        SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        String title = profile.getName() + " — " + profile.directoryUrl("");
        profile.clear();
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(new CharSequence[]{"Edit server", "Test connection", "Delete server"},
                        (dialog, which) ->
                        {
                            if (which == 0) showServerEditorDialog(profileId);
                            else if (which == 1) testServer(profileId);
                            else confirmDeleteServer(profileId);
                        })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> showServerMenu())
                .show();
    }

    private void showServerEditorDialog(final String profileId)
    {
        SmbServerProfile existing = serverStore.find(profileId);
        final EditText name = editorField("Server name", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.getName());
        final EditText endpoint = editorField("Server or IP (optional :port)", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.endpoint());
        final EditText share = editorField("Share name", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.getShare());
        final CheckBox credentials = new CheckBox(requireContext());
        credentials.setText("Use authentication");
        credentials.setChecked(existing != null && !existing.isAnonymous());
        final EditText username = editorField("Username", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.getUsername());
        char[] existingPassword = existing == null ? new char[0] : existing.copyPassword();
        final EditText password;
        try
        {
            password = editorField("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    new String(existingPassword));
        }
        finally { java.util.Arrays.fill(existingPassword, '\0'); }
        final EditText domain = editorField("Domain (optional)", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.getDomain());
        if (existing != null) existing.clear();

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setFocusable(false);
        int pad = dp(24);
        form.setPadding(pad, dp(8), pad, dp(8));
        addLabeledRow(form, "Server name", name);
        addLabeledRow(form, "Server or IP", endpoint);
        addLabeledRow(form, "Share name", share);
        addLabeledRow(form, "Authentication", credentials);
        addLabeledRow(form, "Username", username);
        addLabeledRow(form, "Password", password);
        addLabeledRow(form, "Domain", domain);
        setServerAuthenticationFieldsEnabled(credentials.isChecked(), username, password, domain);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFocusable(false);
        scroll.addView(form);

        AlertDialog editor = new AlertDialog.Builder(requireContext())
                .setTitle(profileId.isEmpty() ? "Add SMB server" : "Edit SMB server")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        editor.setOnShowListener(dialog ->
        {
            if (editor.getWindow() != null)
                editor.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            final Button saveButton = editor.getButton(AlertDialog.BUTTON_POSITIVE);
            final Button cancelButton = editor.getButton(AlertDialog.BUTTON_NEGATIVE);
            credentials.setOnCheckedChangeListener((button, checked) ->
            {
                setServerAuthenticationFieldsEnabled(checked, username, password, domain);
                configureServerEditorFocus(name, endpoint, share, credentials, username, password,
                        domain, saveButton, cancelButton);
            });
            configureServerEditorFocus(name, endpoint, share, credentials, username, password,
                    domain, saveButton, cancelButton);
            name.requestFocus();
            saveButton.setOnClickListener(view ->
                {
                    char[] secret = password.getText().toString().toCharArray();
                    try
                    {
                        SmbServerProfile.Endpoint parsed = SmbServerProfile.parseEndpoint(
                                endpoint.getText().toString());
                        SmbServerProfile saved = serverStore.save(profileId,
                                name.getText().toString(), parsed.host, parsed.port,
                                share.getText().toString(), !credentials.isChecked(),
                                username.getText().toString(), secret, domain.getText().toString());
                        synchronizeBoundDestinations(saved);
                        saved.clear();
                        refreshServers(false);
                        Toast.makeText(requireContext(), "SMB server saved", Toast.LENGTH_LONG).show();
                        editor.dismiss();
                        showServerMenu();
                    }
                    catch (Exception e) { showError(safeError(e)); }
                    finally { java.util.Arrays.fill(secret, '\0'); }
                });
        });
        editor.show();
    }

    private void setServerAuthenticationFieldsEnabled(boolean enabled, EditText username,
                                                       EditText password, EditText domain)
    {
        username.setEnabled(enabled);
        password.setEnabled(enabled);
        domain.setEnabled(enabled);
    }

    /** Keep TV-remote focus inside the editor fields instead of its layout or button panel. */
    private void configureServerEditorFocus(EditText name, EditText endpoint, EditText share,
                                            CheckBox credentials, EditText username,
                                            EditText password, EditText domain,
                                            Button save, Button cancel)
    {
        for (View view : new View[]{name, endpoint, share, credentials, username, password,
                domain, save, cancel}) ensureViewId(view);

        name.setNextFocusDownId(endpoint.getId());
        endpoint.setNextFocusUpId(name.getId());
        endpoint.setNextFocusDownId(share.getId());
        share.setNextFocusUpId(endpoint.getId());
        share.setNextFocusDownId(credentials.getId());
        credentials.setNextFocusUpId(share.getId());

        if (credentials.isChecked())
        {
            credentials.setNextFocusDownId(username.getId());
            username.setNextFocusUpId(credentials.getId());
            username.setNextFocusDownId(password.getId());
            password.setNextFocusUpId(username.getId());
            password.setNextFocusDownId(domain.getId());
            domain.setNextFocusUpId(password.getId());
            domain.setNextFocusDownId(save.getId());
            save.setNextFocusUpId(domain.getId());
            cancel.setNextFocusUpId(domain.getId());
        }
        else
        {
            credentials.setNextFocusDownId(save.getId());
            save.setNextFocusUpId(credentials.getId());
            cancel.setNextFocusUpId(credentials.getId());
        }
    }

    private static void ensureViewId(View view)
    {
        if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
    }

    private EditText editorField(String hint, int inputType, String value)
    {
        EditText field = new EditText(requireContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setText(value);
        field.setSelectAllOnFocus(false);
        return field;
    }

    private void addLabeledField(LinearLayout form, String label, EditText field)
    {
        TextView title = new TextView(requireContext());
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setPadding(0, dp(10), 0, 0);
        form.addView(title);
        form.addView(field);
    }

    private void addLabeledRow(LinearLayout form, String label, View field)
    {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setPadding(0, 0, dp(12), 0);
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.34f));
        row.addView(field, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.66f));
        form.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value)
    {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void testServer(final String profileId)
    {
        final SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        Toast.makeText(requireContext(), "Testing SMB connection...", Toast.LENGTH_SHORT).show();
        io.execute(() ->
        {
            String result;
            SmbFolderBrowserRepository repository = new SmbFolderBrowserRepository(profile);
            try { result = "Connected; " + repository.list("").size() + " folder(s) found"; }
            catch (Exception e) { result = "Failed: " + safeError(e); }
            finally { repository.clear(); }
            final String message = result;
            post(() ->
            {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                showServerActions(profileId);
            });
        });
    }

    private void confirmDeleteServer(final String profileId)
    {
        final SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete SMB server?")
                .setMessage("Delete " + profile.getName() + " from this device? Existing derived URLs remain available for compatibility.")
                .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                {
                    profile.clear();
                    showServerActions(profileId);
                })
                .setPositiveButton("Delete", (dialog, which) ->
                {
                    serverStore.delete(profile.getId());
                    profile.clear();
                    refreshServers(false);
                    showServerMenu();
                }).show();
    }

    private static void clearProfiles(List<SmbServerProfile> profiles)
    {
        for (SmbServerProfile profile : profiles) profile.clear();
    }

    private void clearServerEditor()
    {
        preferences.edit()
                .remove("smb_servers/edit/name")
                .remove("smb_servers/edit/host")
                .remove("smb_servers/edit/share")
                .putString("smb_servers/edit/port", "445")
                .putString("smb_servers/edit/auth/mode", AndroidPrefStore.SMB_AUTH_ANONYMOUS)
                .remove("smb_servers/edit/username")
                .remove("smb_servers/edit/password")
                .remove("smb_servers/edit/domain").apply();
        updateServerEditorState();
    }

    private void populateServerEditor(SmbServerProfile profile)
    {
        char[] password = profile.copyPassword();
        try
        {
            preferences.edit()
                    .putString("smb_servers/edit/name", profile.getName())
                    .putString("smb_servers/edit/host", profile.getHost())
                    .putString("smb_servers/edit/share", profile.getShare())
                    .putString("smb_servers/edit/port", String.valueOf(profile.getPort()))
                    .putString("smb_servers/edit/auth/mode", profile.isAnonymous()
                            ? AndroidPrefStore.SMB_AUTH_ANONYMOUS : AndroidPrefStore.SMB_AUTH_CREDENTIALS)
                    .putString("smb_servers/edit/username", profile.getUsername())
                    .putString("smb_servers/edit/password", new String(password))
                    .putString("smb_servers/edit/domain", profile.getDomain()).apply();
        }
        finally { java.util.Arrays.fill(password, '\0'); }
        updateServerEditorState();
    }

    private void updateServerEditorState()
    {
        boolean credentials = AndroidPrefStore.SMB_AUTH_CREDENTIALS.equals(preferences.getString(
                "smb_servers/edit/auth/mode", AndroidPrefStore.SMB_AUTH_ANONYMOUS));
        for (String key : new String[]{"smb_servers/edit/username", "smb_servers/edit/password", "smb_servers/edit/domain"})
        {
            Preference preference = findPreference(key);
            if (preference != null) preference.setEnabled(credentials);
        }
    }

    private void saveServer()
    {
        char[] password = preferences.getString("smb_servers/edit/password", "").toCharArray();
        try
        {
            int port = Integer.parseInt(preferences.getString("smb_servers/edit/port", "445").trim());
            boolean anonymous = !AndroidPrefStore.SMB_AUTH_CREDENTIALS.equals(preferences.getString(
                    "smb_servers/edit/auth/mode", AndroidPrefStore.SMB_AUTH_ANONYMOUS));
            SmbServerProfile saved = serverStore.save(
                    preferences.getString(SmbServerProfileStore.SELECTED_KEY, ""),
                    preferences.getString("smb_servers/edit/name", ""),
                    preferences.getString("smb_servers/edit/host", ""), port,
                    preferences.getString("smb_servers/edit/share", ""), anonymous,
                    preferences.getString("smb_servers/edit/username", ""), password,
                    preferences.getString("smb_servers/edit/domain", ""));
            synchronizeBoundDestinations(saved);
            Toast.makeText(requireContext(), "SMB server saved", Toast.LENGTH_LONG).show();
            refreshServers(true);
            saved.clear();
        }
        catch (Exception e) { showError(safeError(e)); }
        finally { java.util.Arrays.fill(password, '\0'); }
    }

    private void confirmDeleteServer()
    {
        final SmbServerProfile profile = selectedServerProfile();
        if (profile == null) { showError("Select an SMB server first"); return; }
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete SMB server?")
                .setMessage("Delete " + profile.getName() + " from this device? Existing derived URLs remain available for compatibility.")
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> profile.clear())
                .setPositiveButton("Delete", (dialog, which) ->
                {
                    serverStore.delete(profile.getId());
                    profile.clear();
                    clearServerEditor();
                    refreshServers(false);
                }).show();
    }

    private void testSelectedServer()
    {
        final SmbServerProfile profile = selectedServerProfile();
        if (profile == null) { showError("Select an SMB server first"); return; }
        serverTest.setEnabled(false);
        serverTest.setSummary("Connecting and listing the share root...");
        io.execute(() ->
        {
            String result;
            SmbFolderBrowserRepository repository = new SmbFolderBrowserRepository(profile);
            try { result = "Connected; " + repository.list("").size() + " folder(s) found"; }
            catch (Exception e) { result = "Failed: " + safeError(e); }
            finally { repository.clear(); }
            final String message = result;
            post(() ->
            {
                serverTest.setEnabled(true);
                serverTest.setSummary(message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private SmbServerProfile selectedServerProfile()
    {
        return serverStore.find(preferences.getString(SmbServerProfileStore.SELECTED_KEY, ""));
    }

    private void showMediaMappingMenu()
    {
        final List<MediaMapping> mappings = mediaMappings();
        CharSequence[] labels = new CharSequence[mappings.size() + 1];
        labels[0] = "Add media path mapping";
        for (int index = 0; index < mappings.size(); index++)
            labels[index + 1] = mappings.get(index).source + " → " + mappings.get(index).destination;
        new AlertDialog.Builder(requireContext())
                .setTitle("Media path mappings")
                .setItems(labels, (dialog, which) ->
                {
                    if (which == 0) showMediaMappingEditor(null);
                    else showMediaMappingActions(mappings.get(which - 1));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMediaMappingActions(final MediaMapping mapping)
    {
        new AlertDialog.Builder(requireContext())
                .setTitle(mapping.source + " → " + mapping.destination)
                .setItems(new CharSequence[]{"Change folder", "Test mappings", "Remove mapping"},
                        (dialog, which) ->
                        {
                            if (which == 0) showMediaMappingEditor(mapping);
                            else if (which == 1) testMediaSmb();
                            else confirmRemoveMapping(mapping);
                        })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> showMediaMappingMenu())
                .show();
    }

    private void confirmRemoveMapping(final MediaMapping mapping)
    {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove media path mapping?")
                .setMessage(mapping.source + " → " + mapping.destination)
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> showMediaMappingActions(mapping))
                .setPositiveButton("Remove", (dialog, which) ->
                {
                    preferences.edit().putString(AndroidPrefStore.SMB_MAPPINGS,
                            removeMapping(preferences.getString(AndroidPrefStore.SMB_MAPPINGS, ""),
                                    mapping.source)).apply();
                    updateFolderSummaries();
                    showMediaMappingMenu();
                }).show();
    }

    private List<MediaMapping> mediaMappings()
    {
        ArrayList<MediaMapping> result = new ArrayList<MediaMapping>();
        String encoded = preferences.getString(AndroidPrefStore.SMB_MAPPINGS, "");
        for (String raw : (encoded == null ? "" : encoded).split("[\\r\\n]+"))
        {
            int arrow = raw.indexOf("=>");
            if (arrow < 0) continue;
            String source = raw.substring(0, arrow).trim();
            String destination = raw.substring(arrow + 2).trim();
            if (!source.isEmpty() && !destination.isEmpty())
                result.add(new MediaMapping(source, destination));
        }
        return result;
    }

    private void showMediaMappingEditor(final MediaMapping existing)
    {
        final EditText source = editorField("Example: D:\\Media or /var/media",
                InputType.TYPE_CLASS_TEXT, existing == null ? "" : existing.source);
        MappingTarget existingTarget = mappingTarget(existing);
        final String[] selectedServerId = {existingTarget.profileId};
        final String[] selectedFolder = {existingTarget.folder};
        final Button server = new Button(requireContext());
        final Button folder = new Button(requireContext());
        updateMappingButtons(server, folder, selectedServerId[0], selectedFolder[0]);

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setFocusable(false);
        int pad = dp(24);
        form.setPadding(pad, dp(8), pad, dp(8));
        addLabeledRow(form, "SageTV server folder", source);
        addLabeledRow(form, "SMB server", server);
        addLabeledRow(form, "SMB folder", folder);

        AlertDialog mappingDialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add media path mapping" : "Edit media path mapping")
                .setView(form)
                .setPositiveButton("Apply", null)
                .setNeutralButton("Test", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        server.setOnClickListener(view -> chooseServer(profileId ->
        {
            selectedServerId[0] = profileId;
            selectedFolder[0] = "";
            updateMappingButtons(server, folder, profileId, "");
            configureMappingEditorFocus(source, server, folder,
                    mappingDialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    mappingDialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                    mappingDialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        }));
        folder.setOnClickListener(view ->
        {
            if (selectedServerId[0].isEmpty())
            {
                showError("Select an SMB server first");
                return;
            }
            loadFolder(selectedServerId[0], "", Destination.MEDIA, (profileId, selected) ->
            {
                selectedServerId[0] = profileId;
                selectedFolder[0] = selected;
                updateMappingButtons(server, folder, profileId, selected);
                configureMappingEditorFocus(source, server, folder,
                        mappingDialog.getButton(AlertDialog.BUTTON_POSITIVE),
                        mappingDialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                        mappingDialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            });
        });
        mappingDialog.setOnShowListener(dialog ->
        {
            if (mappingDialog.getWindow() != null)
                mappingDialog.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            final Button applyButton = mappingDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final Button testButton = mappingDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            final Button cancelButton = mappingDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            configureMappingEditorFocus(source, server, folder, applyButton, testButton,
                    cancelButton);
            source.requestFocus();
            applyButton.setOnClickListener(view ->
            {
                String value = source.getText().toString().trim();
                if (value.isEmpty() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
                {
                    showError("Enter one SageTV server path");
                    return;
                }
                if (selectedServerId[0].isEmpty())
                {
                    showError("Select an SMB server");
                    return;
                }
                if (existing != null && !existing.source.equalsIgnoreCase(value))
                    preferences.edit().putString(AndroidPrefStore.SMB_MAPPINGS,
                            removeMapping(preferences.getString(AndroidPrefStore.SMB_MAPPINGS, ""),
                                    existing.source)).apply();
                preferences.edit().putString(AndroidPrefStore.SMB_SOURCE_PREFIX, value).apply();
                applyFolder(selectedServerId[0], selectedFolder[0], Destination.MEDIA);
                mappingDialog.dismiss();
                showMediaMappingMenu();
            });
            testButton.setOnClickListener(view ->
            {
                if (selectedServerId[0].isEmpty())
                {
                    showError("Select an SMB server first");
                    return;
                }
                testFolderSelection(selectedServerId[0], selectedFolder[0]);
            });
        });
        mappingDialog.show();
    }

    private void configureMappingEditorFocus(EditText source, Button server, Button folder,
                                             Button apply, Button test, Button cancel)
    {
        if (apply == null || test == null || cancel == null) return;
        for (View focusView : new View[]{source, server, folder, apply, test, cancel})
            ensureViewId(focusView);
        source.setNextFocusDownId(server.getId());
        server.setNextFocusUpId(source.getId());
        server.setNextFocusDownId(folder.isEnabled() ? folder.getId() : apply.getId());
        folder.setNextFocusUpId(server.getId());
        folder.setNextFocusDownId(apply.getId());
        apply.setNextFocusUpId(folder.isEnabled() ? folder.getId() : server.getId());
        test.setNextFocusUpId(folder.isEnabled() ? folder.getId() : server.getId());
        cancel.setNextFocusUpId(folder.isEnabled() ? folder.getId() : server.getId());
    }

    private MappingTarget mappingTarget(MediaMapping mapping)
    {
        if (mapping == null) return new MappingTarget("", "");
        for (SmbServerProfile profile : serverStore.list())
        {
            try
            {
                String root = profile.directoryUrl("");
                if (!mapping.destination.regionMatches(true, 0, root, 0, root.length())) continue;
                String folder = mapping.destination.substring(root.length()).replace('\\', '/');
                while (folder.endsWith("/")) folder = folder.substring(0, folder.length() - 1);
                return new MappingTarget(profile.getId(), folder);
            }
            finally { profile.clear(); }
        }
        return new MappingTarget("", "");
    }

    private void updateMappingButtons(Button server, Button folder, String profileId, String selectedFolder)
    {
        SmbServerProfile profile = serverStore.find(profileId);
        server.setText(profile == null ? "Select server" : profile.getName() + " — " + profile.getShare());
        folder.setText(profile == null ? "Select a server first"
                : selectedFolder.isEmpty() ? "Share root" : selectedFolder);
        folder.setEnabled(profile != null);
        if (profile != null) profile.clear();
    }

    private void testFolderSelection(String profileId, String folder)
    {
        final SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        Toast.makeText(requireContext(), "Testing SMB folder...", Toast.LENGTH_SHORT).show();
        io.execute(() ->
        {
            String result;
            SmbFolderBrowserRepository repository = new SmbFolderBrowserRepository(profile);
            try { result = "Connected; " + repository.list(folder).size() + " folder(s) found"; }
            catch (Exception e) { result = "Failed: " + safeError(e); }
            finally { repository.clear(); }
            final String message = result;
            post(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show());
        });
    }

    private void showDestinationMenu(final Destination destination)
    {
        final boolean configuration = destination == Destination.CONFIGURATION;
        new AlertDialog.Builder(requireContext())
                .setTitle(configuration ? "Configuration folder" : "Diagnostics folder")
                .setItems(new CharSequence[]{"Browse or change folder", "Test connection"},
                        (dialog, which) ->
                        {
                            if (which == 0) browseFolder(destination);
                            else if (configuration) testConfigurationSmb();
                            else testDiagnosticsSmb();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void browseFolder(final Destination destination)
    {
        chooseServer(profileId -> loadFolder(profileId, "", destination));
    }

    private void chooseServer(final ServerSelection selection)
    {
        final List<SmbServerProfile> profiles = serverStore.list();
        if (profiles.isEmpty()) { showError("Create an SMB server first"); return; }
        CharSequence[] names = new CharSequence[profiles.size()];
        for (int index = 0; index < profiles.size(); index++)
            names[index] = profiles.get(index).getName() + " — " + profiles.get(index).getShare();
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose SMB server")
                .setItems(names, (dialog, which) ->
                {
                    String id = profiles.get(which).getId();
                    clearProfiles(profiles);
                    selection.selected(id);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                {
                    for (SmbServerProfile profile : profiles) profile.clear();
                })
                .setOnCancelListener(dialog -> clearProfiles(profiles))
                .show();
    }

    private void loadFolder(final String profileId, final String folder, final Destination destination)
    {
        loadFolder(profileId, folder, destination, null);
    }

    private void loadFolder(final String profileId, final String folder, final Destination destination,
                            final FolderSelection selection)
    {
        final SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        io.execute(() ->
        {
            List<String> folders = null;
            String failure = null;
            SmbFolderBrowserRepository repository = new SmbFolderBrowserRepository(profile);
            try { folders = repository.list(folder); }
            catch (Exception e) { failure = safeError(e); }
            finally { repository.clear(); }
            final List<String> result = folders;
            final String error = failure;
            post(() ->
            {
                if (error != null) showError(error);
                else showFolderDialog(profileId, folder, destination, result, selection);
            });
        });
    }

    private void showFolderDialog(final String profileId, final String folder,
                                  final Destination destination, List<String> folders)
    {
        showFolderDialog(profileId, folder, destination, folders, null);
    }

    private void showFolderDialog(final String profileId, final String folder,
                                  final Destination destination, List<String> folders,
                                  final FolderSelection selection)
    {
        SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) return;
        ArrayList<String> labels = new ArrayList<String>();
        labels.add("..");
        labels.addAll(folders);
        String location = profile.getName() + " / " + profile.getShare()
                + (folder.isEmpty() ? "" : " / " + folder);
        profile.clear();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, labels)
        {
            @Override public View getView(int position, View convertView, ViewGroup parent)
            {
                TextView row = (TextView) super.getView(position, convertView, parent);
                int icon = position == 0
                        ? R.drawable.ic_arrow_upward_white_24dp
                        : R.drawable.ic_folder_white_24dp;
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        ContextCompat.getDrawable(getContext(), icon), null, null, null);
                row.setCompoundDrawablePadding((int) (12 * getResources().getDisplayMetrics().density));
                return row;
            }
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(location)
                .setAdapter(adapter, new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    {
                        if (which == 0)
                        {
                            if (folder.isEmpty())
                            {
                                chooseServer(selectedProfileId -> loadFolder(
                                        selectedProfileId, "", destination, selection));
                                return;
                            }
                            int slash = folder.lastIndexOf('/');
                            loadFolder(profileId, slash < 0 ? "" : folder.substring(0, slash),
                                    destination, selection);
                            return;
                        }
                        int index = which - 1;
                        if (index < 0 || index >= folders.size()) return;
                        String child = folder.isEmpty() ? folders.get(index) : folder + "/" + folders.get(index);
                        loadFolder(profileId, child, destination, selection);
                    }
                })
                .setNeutralButton("Select this folder", (dialog, which) ->
                {
                    if (selection == null) applyFolder(profileId, folder, destination);
                    else selection.selected(profileId, folder);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyFolder(String profileId, String folder, Destination destination)
    {
        SmbServerProfile profile = serverStore.find(profileId);
        if (profile == null) { showError("The selected SMB server no longer exists"); return; }
        try
        {
            SharedPreferences.Editor editor = preferences.edit();
            if (destination == Destination.MEDIA)
            {
                String source = preferences.getString(AndroidPrefStore.SMB_SOURCE_PREFIX, "").trim();
                if (source.isEmpty() || source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Enter the SageTV path prefix before browsing the media folder");
                String line = source + " => " + profile.directoryUrl(folder);
                editor.putString(AndroidPrefStore.SMB_MAPPINGS, replaceMapping(
                                preferences.getString(AndroidPrefStore.SMB_MAPPINGS, ""), source, line))
                        .putString(AndroidPrefStore.SMB_MEDIA_SERVER_ID, profileId)
                        .putString(AndroidPrefStore.SMB_MEDIA_FOLDER, folder);
                putCredentials(editor, profile, AndroidPrefStore.SMB_USERNAME,
                        AndroidPrefStore.SMB_PASSWORD, AndroidPrefStore.SMB_DOMAIN, null);
            }
            else if (destination == Destination.CONFIGURATION)
            {
                editor.putString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, profile.directoryUrl(folder))
                        .putString(AndroidPrefStore.SMB_PROFILE_SERVER_ID, profileId)
                        .putString(AndroidPrefStore.SMB_PROFILE_FOLDER, folder);
                putCredentials(editor, profile, AndroidPrefStore.SMB_PROFILE_USERNAME,
                        AndroidPrefStore.SMB_PROFILE_PASSWORD, AndroidPrefStore.SMB_PROFILE_DOMAIN, null);
            }
            else
            {
                editor.putString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, profile.directoryUrl(folder))
                        .putString(AndroidPrefStore.SMB_DIAGNOSTICS_SERVER_ID, profileId)
                        .putString(AndroidPrefStore.SMB_DIAGNOSTICS_FOLDER, folder);
                putCredentials(editor, profile, AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME,
                        AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD, AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN,
                        AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE);
            }
            if (!editor.commit()) throw new IllegalStateException("Unable to save SMB folder selection");
            updateFolderSummaries();
            Toast.makeText(requireContext(), "Selected " + profile.directoryUrl(folder), Toast.LENGTH_LONG).show();
        }
        catch (Exception e) { showError(safeError(e)); }
        finally { profile.clear(); }
    }

    private void synchronizeBoundDestinations(SmbServerProfile profile)
    {
        String id = profile.getId();
        if (id.equals(preferences.getString(AndroidPrefStore.SMB_MEDIA_SERVER_ID, "")))
            applyFolder(id, preferences.getString(AndroidPrefStore.SMB_MEDIA_FOLDER, ""), Destination.MEDIA);
        if (id.equals(preferences.getString(AndroidPrefStore.SMB_PROFILE_SERVER_ID, "")))
            applyFolder(id, preferences.getString(AndroidPrefStore.SMB_PROFILE_FOLDER, ""), Destination.CONFIGURATION);
        if (id.equals(preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_SERVER_ID, "")))
            applyFolder(id, preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_FOLDER, ""), Destination.DIAGNOSTICS);
    }

    private static void putCredentials(SharedPreferences.Editor editor, SmbServerProfile profile,
                                       String usernameKey, String passwordKey, String domainKey,
                                       String authModeKey)
    {
        char[] password = profile.copyPassword();
        try
        {
            editor.putString(usernameKey, profile.getUsername())
                    .putString(passwordKey, new String(password))
                    .putString(domainKey, profile.getDomain());
            if (authModeKey != null) editor.putString(authModeKey, profile.isAnonymous()
                    ? AndroidPrefStore.SMB_AUTH_ANONYMOUS : AndroidPrefStore.SMB_AUTH_CREDENTIALS);
        }
        finally { java.util.Arrays.fill(password, '\0'); }
    }

    private static String replaceMapping(String mappings, String source, String replacement)
    {
        ArrayList<String> result = new ArrayList<String>();
        boolean replaced = false;
        for (String raw : (mappings == null ? "" : mappings).split("[\\r\\n]+"))
        {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int arrow = line.indexOf("=>");
            if (arrow >= 0 && line.substring(0, arrow).trim().equalsIgnoreCase(source))
            {
                if (!replaced) result.add(replacement);
                replaced = true;
            }
            else result.add(line);
        }
        if (!replaced) result.add(replacement);
        StringBuilder joined = new StringBuilder();
        for (String line : result)
        {
            if (joined.length() > 0) joined.append('\n');
            joined.append(line);
        }
        return joined.toString();
    }

    private static String removeMapping(String mappings, String source)
    {
        ArrayList<String> result = new ArrayList<String>();
        for (String raw : (mappings == null ? "" : mappings).split("[\\r\\n]+"))
        {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int arrow = line.indexOf("=>");
            if (arrow >= 0 && line.substring(0, arrow).trim().equalsIgnoreCase(source)) continue;
            result.add(line);
        }
        StringBuilder joined = new StringBuilder();
        for (String line : result)
        {
            if (joined.length() > 0) joined.append('\n');
            joined.append(line);
        }
        return joined.toString();
    }

    private void updateFolderSummaries()
    {
        if (mediaBrowse != null)
        {
            int count = mediaMappings().size();
            mediaBrowse.setSummary(count == 0 ? "No media path mappings configured"
                    : count + " media path mapping" + (count == 1 ? "" : "s") + " configured");
        }
        if (configurationBrowse != null) configurationBrowse.setSummary(summaryOr(
                preferences.getString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, ""),
                "Choose a folder on the selected SMB server"));
        if (diagnosticsBrowse != null) diagnosticsBrowse.setSummary(summaryOr(
                preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, ""),
                "Choose a folder on the selected SMB server"));
    }

    private static final class MediaMapping
    {
        final String source;
        final String destination;

        MediaMapping(String source, String destination)
        {
            this.source = source;
            this.destination = destination;
        }
    }

    private static final class MappingTarget
    {
        final String profileId;
        final String folder;

        MappingTarget(String profileId, String folder)
        {
            this.profileId = profileId;
            this.folder = folder;
        }
    }

    private static String summaryOr(String value, String fallback)
    { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }

    private enum Destination { MEDIA, CONFIGURATION, DIAGNOSTICS }

    private interface ServerSelection { void selected(String profileId); }

    private interface FolderSelection { void selected(String profileId, String folder); }

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
