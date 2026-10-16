package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.preference.PreferenceManager;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import opensagetv.vibe.miniclient.*;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;

/** Default-off server export owner, separate from playback and SMB spooling.
 * Periodic work only while the app has a foreground activity; background/exit
 * requests one final best-effort checkpoint, never a blocking player operation.
 */
public final class ServerDiagnosticExporter implements Application.ActivityLifecycleCallbacks,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile ServerDiagnosticExporter instance;
    private final Application app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private int foreground;
    private long lastAttempt;
    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            checkpoint("periodic");
            if (enabled() && foreground > 0) main.postDelayed(this, 60_000L);
        }
    };

    private ServerDiagnosticExporter(Application application) {
        app = application; prefs = PreferenceManager.getDefaultSharedPreferences(app);
    }
    public static synchronized void start(Application app) {
        if (instance != null) return;
        instance = new ServerDiagnosticExporter(app);
        app.registerActivityLifecycleCallbacks(instance);
        instance.prefs.registerOnSharedPreferenceChangeListener(instance);
    }
    public static void checkpoint(String reason) {
        ServerDiagnosticExporter owner = instance;
        if (owner != null) owner.enqueue(reason, false);
    }
    public static void uploadNow() {
        ServerDiagnosticExporter owner = instance;
        if (owner != null) owner.enqueue("manual", true);
    }
    public interface Completion { void done(String status); }
    static MiniClientConnection exportSession() {
        ServerDiagnosticExporter owner=instance;
        MiniClientConnection current=owner==null?null:owner.connection();
        return current!=null && current.isConnected()?current:null;
    }
    static boolean receiverAvailable(MiniClientConnection expected) throws Exception {
        ServerDiagnosticExporter owner=instance;
        if(owner==null || expected==null || owner.connection()!=expected || !expected.isConnected())return false;
        int port=Integer.parseInt(owner.prefs.getString(AndroidPrefStore.SERVER_DIAGNOSTICS_PORT,"8272"));
        boolean available=ServerDiagnosticTransport.available(expected.getServerName(),port);
        return available && owner.connection()==expected && expected.isConnected();
    }
    /** Explicit, confirmed one-report opt-in; never enables periodic uploads. */
    static void uploadOnce(MiniClientConnection expected,String details,Completion completion) {
        ServerDiagnosticExporter owner=instance;
        if(owner==null){if(completion!=null)completion.done("Server export unavailable");return;}
        owner.enqueue("manual-once",true,details,true,expected,completion);
    }
    public static synchronized void shutdown() {
        ServerDiagnosticExporter owner = instance;
        if (owner == null) return;
        owner.enqueue("clean-exit", true);
        owner.main.removeCallbacks(owner.periodic);
        owner.prefs.unregisterOnSharedPreferenceChangeListener(owner);
        owner.app.unregisterActivityLifecycleCallbacks(owner);
        owner.io.shutdown(); instance = null;
    }
    private boolean enabled() { return prefs.getBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED, false); }
    private MiniClientConnection connection() {
        MiniclientApplication application = MiniclientApplication.get();
        return application == null || application.getClient() == null ? null : application.getClient().getCurrentConnection();
    }
    private synchronized void enqueue(String reason, boolean immediate) {
        enqueue(reason,immediate,"",false,null,null);
    }
    private synchronized void enqueue(String reason, boolean immediate,String details,
            boolean oneReport,MiniClientConnection expected,Completion completion) {
        if (!oneReport && !enabled()) return;
        MiniClientConnection current = connection();
        if (current == null || !current.isConnected() || (oneReport && current!=expected)) {
            remember("Waiting for the same connected SageTV server");
            complete(completion,"Server session changed or disconnected; reopen Export Diagnostics");return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!immediate && now - lastAttempt < 60_000L) return;
        if (!busy.compareAndSet(false, true)) {complete(completion,"An upload is already running; try again shortly");return;}
        lastAttempt = now;
        final String server = current.getServerName();
        // Capture identity now. Never send a previous server's report after
        // reconnect/switch, and recheck opt-in at each transfer boundary.
        io.execute(() -> {
            String result;
            try {
                remember("Uploading redacted report");
                DiagnosticBundleManager.Result bundle = DiagnosticBundleManager.create(app, snapshot(current, reason)+"\n"+details);
                int port = Integer.parseInt(prefs.getString(AndroidPrefStore.SERVER_DIAGNOSTICS_PORT, "8272"));
                ServerDiagnosticTransport.upload(server, port, DiagnosticIdentity.token(app), bundle.file,
                        bundle.sha256, () -> (oneReport || enabled()) && connection() == current && current.isConnected());
                // Keep the normal local bundle retention; upload failure never
                // destroys logs and success never changes SMB export state.
                result="Stored on server: " + bundle.bytes + " bytes; SHA-256 " + bundle.sha256;
                remember(result);
                prefs.edit().putLong(AndroidPrefStore.SERVER_DIAGNOSTICS_LAST_TIME, System.currentTimeMillis()).apply();
            } catch (Exception error) {
                result="Unavailable; local logs retained: " + DiagnosticRedactor.redact(
                        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                remember(result);
            } finally { busy.set(false); }
            complete(completion,result);
        });
    }
    private void complete(Completion completion,String status) {
        if(completion!=null)main.post(()->completion.done(status));
    }
    private String snapshot(MiniClientConnection current, String reason) {
        Runtime runtime = Runtime.getRuntime();
        StringBuilder out = new StringBuilder("[client-statistics]\ncheckpoint=").append(reason)
                .append("\nheapUsedBytes=").append(runtime.totalMemory() - runtime.freeMemory())
                .append("\nheapMaxBytes=").append(runtime.maxMemory())
                .append("\nconnection=").append(current.isConnected())
                .append("\n").append(ConnectionLifecycleDiagnostics.latestCompactWire());
        MiniPlayerPlugin player = current.getMediaCmd() == null ? null : current.getMediaCmd().getPlaya();
        if (player != null) out.append("\nbackend=").append(player.getClass().getSimpleName())
                .append("\nstate=").append(player.getState());
        // No new decoder/clock polling, key injection or player mutation. The
        // existing logs/traces/current-video tests supply detailed media stats.
        return out.append("\nDetailed codec/timing statistics: see collected logs/traces/current-video tests.\n").toString();
    }
    private void remember(String status) { prefs.edit().putString(AndroidPrefStore.SERVER_DIAGNOSTICS_STATUS, status).apply(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
        if (!AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED.equals(key)) return;
        main.removeCallbacks(periodic);
        if (enabled()) {
            lastAttempt = 0; enqueue("enabled", true);
            if (foreground > 0) main.postDelayed(periodic, 60_000L);
        } else remember("Off; no new uploads");
    }
    @Override public void onActivityCreated(Activity a, Bundle state) { }
    @Override public void onActivityStarted(Activity a) {
        if (++foreground == 1 && enabled()) { main.removeCallbacks(periodic); main.postDelayed(periodic, 5000L); }
    }
    @Override public void onActivityStopped(Activity a) {
        foreground = Math.max(0, foreground - 1);
        if (foreground == 0 && !a.isChangingConfigurations()) {
            main.removeCallbacks(periodic); enqueue("background-or-exit", true);
        }
    }
    @Override public void onActivityResumed(Activity a) { }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
