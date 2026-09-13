package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Bounded local spool and periodic atomic SMB refresh for Always mode. */
public final class DiagnosticSessionSpool implements Application.ActivityLifecycleCallbacks,
        SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final long REFRESH_MS = 60_000L;
    private static final int MAX_PENDING_FILES = 4;
    private static volatile DiagnosticSessionSpool INSTANCE;

    private final Application application;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private final File spoolDirectory;
    private final String sessionName;
    private int startedActivities;

    private final Runnable periodic = new Runnable()
    {
        @Override public void run()
        {
            checkpoint("periodic");
            if (isAlways()) main.postDelayed(this, REFRESH_MS);
        }
    };

    private DiagnosticSessionSpool(Application application)
    {
        this.application = application;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(application);
        this.spoolDirectory = new File(application.getFilesDir(), "diagnostic-spool");
        String stamp = utc("yyyyMMdd-HHmmss");
        this.sessionName = "OpenSageTV-Vibe-session-" + DiagnosticIdentity.token(application)
                + "-" + stamp + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".log";
    }

    public static synchronized void start(Application application)
    {
        if (INSTANCE != null) return;
        DiagnosticSessionSpool spool = new DiagnosticSessionSpool(application);
        INSTANCE = spool;
        application.registerActivityLifecycleCallbacks(spool);
        spool.preferences.registerOnSharedPreferenceChangeListener(spool);
        spool.configure();
    }

    public static void checkpoint(String reason)
    {
        DiagnosticSessionSpool spool = INSTANCE;
        if (spool != null) spool.enqueue(reason);
    }

    public static void shutdown()
    {
        DiagnosticSessionSpool spool = INSTANCE;
        if (spool == null) return;
        spool.enqueue("clean-exit");
        spool.main.removeCallbacks(spool.periodic);
        spool.preferences.unregisterOnSharedPreferenceChangeListener(spool);
        spool.application.unregisterActivityLifecycleCallbacks(spool);
        INSTANCE = null;
    }

    private void configure()
    {
        main.removeCallbacks(periodic);
        if (!isAlways()) return;
        if (!preferences.getBoolean(PrefStore.Keys.use_log_to_sdcard, false))
        {
            preferences.edit().putBoolean(PrefStore.Keys.use_log_to_sdcard, true).apply();
            AppUtil.initLogging(application, true);
        }
        enqueue("launch-or-enable");
        main.postDelayed(periodic, REFRESH_MS);
    }

    private boolean isAlways()
    {
        return AndroidPrefStore.DIAGNOSTICS_MODE_ALWAYS.equals(preferences.getString(
                AndroidPrefStore.SMB_DIAGNOSTICS_MODE, AndroidPrefStore.DIAGNOSTICS_MODE_OFF));
    }

    private void enqueue(final String reason)
    {
        if (!isAlways() || !running.compareAndSet(false, true)) return;
        io.execute(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    if (!spoolDirectory.isDirectory() && !spoolDirectory.mkdirs())
                        throw new IllegalStateException("diagnostic spool directory unavailable");
                    File current = new File(spoolDirectory, sessionName + ".pending");
                    File temporary = new File(spoolDirectory, sessionName + ".tmp");
                    byte[] bytes = DiagnosticBundleManager.createSessionLog(application, reason);
                    FileOutputStream output = new FileOutputStream(temporary);
                    try { output.write(bytes); output.getFD().sync(); }
                    finally { output.close(); }
                    if (current.exists() && !current.delete())
                        throw new IllegalStateException("unable to replace local diagnostic spool");
                    if (!temporary.renameTo(current))
                        throw new IllegalStateException("unable to finalize local diagnostic spool");
                    uploadPending();
                    trimPending();
                }
                catch (Exception e)
                {
                    remember("Always-mode flush failed: " + DiagnosticRedactor.redact(
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), "", "", 0);
                }
                finally { running.set(false); }
            }
        });
    }

    private void uploadPending()
    {
        File[] pending = spoolDirectory.listFiles((dir, name) -> name.endsWith(".log.pending"));
        if (pending == null) return;
        Arrays.sort(pending, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : pending)
        {
            char[] password = preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_PASSWORD, "").toCharArray();
            boolean anonymous = AndroidPrefStore.SMB_AUTH_ANONYMOUS.equals(preferences.getString(
                    AndroidPrefStore.SMB_DIAGNOSTICS_AUTH_MODE, AndroidPrefStore.SMB_AUTH_ANONYMOUS));
            SmbDiagnosticsRepository repository = new SmbDiagnosticsRepository(
                    preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DIRECTORY, ""), anonymous,
                    preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_USERNAME, ""), password,
                    preferences.getString(AndroidPrefStore.SMB_DIAGNOSTICS_DOMAIN, ""));
            Arrays.fill(password, '\0');
            try
            {
                String remote = file.getName().substring(0, file.getName().length() - ".pending".length());
                SmbDiagnosticsRepository.UploadResult result = repository.refreshSessionLog(file, remote);
                remember("Always-mode SMB refresh succeeded", result.filename, result.sha256, result.bytes);
                if (!remote.equals(sessionName)) file.delete();
            }
            catch (Exception e)
            {
                remember("Always-mode SMB refresh pending: " + DiagnosticRedactor.redact(
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                        file.getName(), "", file.length());
                break;
            }
            finally { repository.clear(); }
        }
    }

    private void trimPending()
    {
        File[] pending = spoolDirectory.listFiles((dir, name) -> name.endsWith(".pending"));
        if (pending == null || pending.length <= MAX_PENDING_FILES) return;
        Arrays.sort(pending, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int index = 0; index < pending.length - MAX_PENDING_FILES; index++) pending[index].delete();
    }

    private void remember(String status, String filename, String sha256, long bytes)
    {
        preferences.edit().putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_STATUS, status)
                .putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_FILE, filename)
                .putString(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_SHA256, sha256)
                .putLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_BYTES, bytes)
                .putLong(AndroidPrefStore.SMB_DIAGNOSTICS_LAST_TIME, System.currentTimeMillis()).apply();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    { if (AndroidPrefStore.SMB_DIAGNOSTICS_MODE.equals(key)) configure(); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { startedActivities++; }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity)
    {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && !activity.isChangingConfigurations()) checkpoint("app-background-or-exit");
    }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }

    private static String utc(String pattern)
    {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
