package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;

/** UI-safe entry point shared by Settings and the long-press playback menu. */
public final class DiagnosticExportController
{
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DiagnosticExportController() { }

    public static void show(final Activity activity, final String activePlayerText)
    {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final String mode = prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_MODE,
                AndroidPrefStore.DIAGNOSTICS_MODE_OFF);
        String message = AndroidPrefStore.DIAGNOSTICS_MODE_OFF.equals(mode)
                ? "SMB export is Off. You can still create and share a redacted bundle locally."
                : AndroidPrefStore.DIAGNOSTICS_MODE_ALWAYS.equals(mode)
                ? "Always mode is active. Upload now refreshes the remote diagnostic artifact."
                : "Create one redacted bundle and choose local sharing or the independent diagnostics SMB destination.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle("Export Diagnostics")
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Create and share", (d, which) -> create(activity, activePlayerText, false));
        if (!AndroidPrefStore.DIAGNOSTICS_MODE_OFF.equals(mode))
            dialog.setNeutralButton(AndroidPrefStore.DIAGNOSTICS_MODE_ALWAYS.equals(mode)
                    ? "Upload now" : "Upload to SMB",
                    (d, which) ->
                    {
                        if (AndroidPrefStore.DIAGNOSTICS_MODE_ALWAYS.equals(mode))
                        {
                            DiagnosticSessionSpool.checkpoint("manual-upload-now");
                            Toast.makeText(activity, "Refreshing the Always-mode diagnostic session log...", Toast.LENGTH_LONG).show();
                        }
                        else confirmUpload(activity, activePlayerText);
                    });
        dialog.show();
    }

    private static void confirmUpload(final Activity activity, final String details)
    {
        new AlertDialog.Builder(activity)
                .setTitle("Upload diagnostic bundle?")
                .setMessage("A bounded redacted ZIP will be written to the configured diagnostics SMB directory. Media and configuration credentials are not used.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Upload", (dialog, which) -> create(activity, details, true))
                .show();
    }

    private static void create(final Activity activity, final String details, final boolean upload)
    {
        Toast.makeText(activity, "Creating redacted diagnostic bundle...", Toast.LENGTH_SHORT).show();
        IO.execute(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    final DiagnosticBundleManager.Result bundle = DiagnosticBundleManager.create(activity, details);
                    if (upload) upload(activity, bundle);
                    else MAIN.post(new Runnable()
                    {
                        @Override public void run()
                        {
                            remember(activity, "Created for sharing", bundle.file.getName(), bundle.sha256, bundle.bytes);
                            Toast.makeText(activity, summary("Created", bundle.file.getName(), bundle.sha256, bundle.bytes), Toast.LENGTH_LONG).show();
                            try { DiagnosticBundleManager.share(activity, bundle); }
                            catch (Exception e) { Toast.makeText(activity, "Bundle created, but no compatible share activity is available", Toast.LENGTH_LONG).show(); }
                        }
                    });
                }
                catch (final Exception e) { fail(activity, "create", e); }
            }
        });
    }

    private static void upload(final Activity activity, final DiagnosticBundleManager.Result bundle)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean anonymous = AndroidPrefStore.SMB_AUTH_ANONYMOUS.equals(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE, AndroidPrefStore.SMB_AUTH_ANONYMOUS));
        char[] password = prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD, "").toCharArray();
        SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, ""), anonymous,
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME, ""), password,
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN, ""));
        Arrays.fill(password, '\0');
        try
        {
            final SmbDiagnosticsRepository.UploadResult result = repository.upload(bundle.file, bundle.sha256);
            bundle.file.delete();
            MAIN.post(new Runnable()
            {
                @Override public void run()
                {
                    remember(activity, "SMB upload succeeded", result.filename, result.sha256, result.bytes);
                    Toast.makeText(activity, summary("Uploaded", result.filename, result.sha256, result.bytes)
                            + "\nSMB stages: " + result.stageSummary, Toast.LENGTH_LONG).show();
                }
            });
        }
        catch (final Exception e) { fail(activity, "SMB upload", e); }
        finally { repository.clear(); }
    }

    public static void testDiagnosticsSmb(final Activity activity, final Completion completion)
    {
        IO.execute(new Runnable()
        {
            @Override public void run()
            {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                boolean anonymous = AndroidPrefStore.SMB_AUTH_ANONYMOUS.equals(prefs.getString(
                        AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE, AndroidPrefStore.SMB_AUTH_ANONYMOUS));
                char[] password = prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD, "").toCharArray();
                SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(
                        prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, ""), anonymous,
                        prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME, ""), password,
                        prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN, ""));
                Arrays.fill(password, '\0');
                try
                {
                    SmbDiagnosticsRepository.TestResult result = repository.testConnection();
                    complete(completion, "Diagnostics SMB passed; write/read/hash/delete verified\n"
                            + result.stageSummary, null);
                }
                catch (Exception e) { complete(completion, null, safe(e)); }
                finally { repository.clear(); }
            }
        });
    }

    private static void complete(final Completion completion, final String success, final String failure)
    { MAIN.post(new Runnable() { @Override public void run() { completion.done(success, failure); } }); }

    private static void fail(final Activity activity, final String stage, final Exception error)
    {
        MAIN.post(new Runnable()
        {
            @Override public void run()
            {
                remember(activity, stage + " failed: " + safe(error), "", "", 0);
                Toast.makeText(activity, "Diagnostics " + stage + " failed: " + safe(error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void remember(Activity activity, String status, String filename, String sha256, long bytes)
    {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_STATUS, status)
                .putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_FILE, filename)
                .putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_SHA256, sha256)
                .putLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_BYTES, bytes)
                .putLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_TIME, System.currentTimeMillis()).apply();
    }

    private static String summary(String action, String filename, String sha256, long bytes)
    { return action + ": " + filename + "\nBytes: " + bytes + "\nSHA-256: " + sha256; }
    private static String safe(Exception error)
    {
        String message = error.getMessage();
        return DiagnosticRedactor.redact(message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message);
    }
    public interface Completion { void done(String success, String failure); }
}
