package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FilenameFilter;

import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticExportController;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** One place for incident capture, app logging, and diagnostic export. */
public final class DiagnosticsSettingsFragment extends PreferenceFragmentCompat
{
    @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.diagnostics_prefs, rootKey);

        Preference fileLogging = findPreference(PrefStore.Keys.use_log_to_sdcard);
        if (fileLogging != null)
            fileLogging.setOnPreferenceChangeListener((preference, value) ->
            {
                AppUtil.initLogging(requireActivity(), (Boolean) value);
                return true;
            });

        final ListPreference logLevel = findPreference(PrefStore.Keys.log_level);
        if (logLevel != null)
        {
            logLevel.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            logLevel.setOnPreferenceChangeListener((preference, value) ->
            {
                AppUtil.setLogLevel(String.valueOf(value));
                return true;
            });
        }

        Preference shareLog = findPreference("share_log");
        if (shareLog != null)
            shareLog.setOnPreferenceClickListener(preference ->
            {
                shareNewestLog();
                return true;
            });

        Preference export = findPreference("export_diagnostics");
        if (export != null)
            export.setOnPreferenceClickListener(preference ->
            {
                DiagnosticExportController.show(requireActivity(), "");
                return true;
            });

        Preference smb = findPreference("diagnostics_smb_settings");
        if (smb != null)
            smb.setOnPreferenceClickListener(preference ->
            {
                startActivity(new Intent(requireActivity(), SmbProfileSettingsActivity.class));
                return true;
            });
    }

    @Override public void onResume()
    {
        super.onResume();
        Preference smb = findPreference("diagnostics_smb_settings");
        if (smb == null) return;
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_MODE,
                AndroidPrefStore.DIAGNOSTICS_MODE_OFF);
        String label = AndroidPrefStore.DIAGNOSTICS_MODE_ALWAYS.equals(mode) ? "Always"
                : AndroidPrefStore.DIAGNOSTICS_MODE_ON_REQUEST.equals(mode)
                ? "On request" : "Off";
        smb.setSummary(label + " — configure the independent diagnostics destination and test it");
    }

    private void shareNewestLog()
    {
        Context context = getActivity();
        if (context == null) return;
        File[] files = AppUtil.getLogDir(context).listFiles(new FilenameFilter()
        {
            @Override public boolean accept(File dir, String name)
            { return name.endsWith(".txt"); }
        });
        if (files == null || files.length == 0)
        {
            Log.i("MINICLIENT_LOG", "No app log is available to share");
            return;
        }
        File newest = files[0];
        for (int index = 1; index < files.length; index++)
            if (files[index].lastModified() > newest.lastModified()) newest = files[index];
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", newest);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("OpenSageTV Vibe app log", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_SUBJECT, "OpenSageTV Vibe app log");
        startActivity(Intent.createChooser(intent, "Share app log"));
    }
}
