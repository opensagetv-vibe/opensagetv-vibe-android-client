package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticBundleManager;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticRedactor;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticSessionSpool;
import opensagetv.vibe.miniclient.android.diagnostics.SmbDiagnosticsRepository;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;

/** Debug/MCP bridge for bounded diagnostic export acceptance tests. */
final class DebugDiagnosticCommands
{
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private DebugDiagnosticCommands() { }

    static void execute(Context context, Intent intent, BroadcastReceiver.PendingResult pending)
    {
        final Context app = context.getApplicationContext();
        final String action = DebugValueParser.clean(intent.getStringExtra("diagnostics_action"));
        IO.execute(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    String result;
                    if ("status".equals(action)) result = status(app);
                    else if ("test_smb".equals(action)) result = testSmb(app);
                    else if ("always_flush".equals(action))
                    {
                        DiagnosticSessionSpool.checkpoint("mcp-upload-now");
                        result = "action=always_flush;queued=true";
                    }
                    else if ("create".equals(action) || "upload".equals(action))
                        result = create(app, "upload".equals(action));
                    else throw new IllegalArgumentException("diagnostics_action must be status, create, upload, test_smb, or always_flush");
                    pending.setResultCode(1);
                    pending.setResultData("ok=true;op=diagnostics;" + result);
                }
                catch (Exception e)
                {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    pending.setResultCode(-1);
                    pending.setResultData("ok=false;op=diagnostics;error="
                            + DebugValueParser.safe(DiagnosticRedactor.redact(message)));
                }
                finally { pending.finish(); }
            }
        });
    }

    private static String create(Context context, boolean upload) throws Exception
    {
        DiagnosticBundleManager.Result bundle = DiagnosticBundleManager.create(context,
                DebugStateProvider.snapshot(context, 300000));
        if (!upload)
            return "action=create;filename=" + DebugValueParser.safe(bundle.file.getName())
                    + ";bytes=" + bundle.bytes + ";sha256=" + bundle.sha256;
        SmbDiagnosticsRepository repository = repository(context);
        try
        {
            SmbDiagnosticsRepository.UploadResult result = repository.upload(bundle.file, bundle.sha256);
            bundle.file.delete();
            return "action=upload;filename=" + DebugValueParser.safe(result.filename)
                    + ";bytes=" + result.bytes + ";sha256=" + result.sha256
                    + ";latencyMs=" + result.latencyMs
                    + ";stages=" + DebugValueParser.safe(result.stageSummary);
        }
        finally { repository.clear(); }
    }

    private static String testSmb(Context context) throws Exception
    {
        SmbDiagnosticsRepository repository = repository(context);
        try
        {
            SmbDiagnosticsRepository.TestResult result = repository.testConnection();
            return "action=test_smb;latencyMs=" + result.latencyMs
                    + ";hashVerified=true;cleanupVerified=" + result.cleanupVerified
                    + ";stages=" + DebugValueParser.safe(result.stageSummary);
        }
        finally { repository.clear(); }
    }

    private static String status(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return "action=status;mode=" + DebugValueParser.safe(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_MODE, AndroidPrefStore.DIAGNOSTICS_MODE_OFF))
                + ";directoryConfigured=" + !prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, "").isEmpty()
                + ";lastStatus=" + DebugValueParser.safe(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_LAST_STATUS, "none"))
                + ";lastFile=" + DebugValueParser.safe(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_LAST_FILE, ""))
                + ";lastSha256=" + DebugValueParser.safe(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_LAST_SHA256, ""))
                + ";lastBytes=" + prefs.getLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_BYTES, 0)
                + ";lastTimeEpochMs=" + prefs.getLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_TIME, 0);
    }

    private static SmbDiagnosticsRepository repository(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean anonymous = AndroidPrefStore.SMB_AUTH_ANONYMOUS.equals(prefs.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE, AndroidPrefStore.SMB_AUTH_ANONYMOUS));
        char[] password = prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD, "").toCharArray();
        SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, ""), anonymous,
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME, ""), password,
                prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN, ""));
        Arrays.fill(password, '\0');
        return repository;
    }
}
