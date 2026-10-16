package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.preference.PreferenceManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.lang.ref.WeakReference;
import opensagetv.vibe.miniclient.*;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.ClientDiagnosticSnapshot;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Application-scoped diagnostic listener, independent of SageTV, SMB and
 * server uploads. Default off, foreground-only; Home/exit/disable closes all
 * accepted sockets. No wake lock, foreground service or playback mutation.
 */
public final class ClientDiagnosticWebService implements Application.ActivityLifecycleCallbacks,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static ClientDiagnosticWebService instance;
    private final Application app;
    private final SharedPreferences prefs;
    private final ExecutorService lifecycle = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vibe-diagnostic-web-owner"); t.setDaemon(true); return t;
    });
    private volatile int foreground;
    private volatile boolean terminated;
    private volatile DiagnosticWebServer server;
    private int boundPort;
    private byte[] cachedBundle;
    private long bundleTime;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile WeakReference<Activity> visibleActivity = new WeakReference<>(null);
    private final DiagnosticTimeline timeline = new DiagnosticTimeline();
    private Map<String,Object> cachedData;
    private long dataTime;
    private final DiagnosticHistory history;

    private ClientDiagnosticWebService(Application app) {
        this.app = app; prefs = PreferenceManager.getDefaultSharedPreferences(app);
        history = new DiagnosticHistory(app.getFilesDir(), AppUtil.getLogDir(app),
                new File(app.getFilesDir(),CurrentVideoTestStore.DIRECTORY));
    }
    public static synchronized void start(Application app) {
        if (instance != null) return;
        instance = new ClientDiagnosticWebService(app);
        app.registerActivityLifecycleCallbacks(instance);
        instance.prefs.registerOnSharedPreferenceChangeListener(instance);
        instance.reconfigure();
    }
    public static synchronized void shutdown() {
        ClientDiagnosticWebService owner = instance;
        if (owner == null) return;
        owner.terminated = true;
        owner.prefs.unregisterOnSharedPreferenceChangeListener(owner);
        owner.app.unregisterActivityLifecycleCallbacks(owner);
        owner.reconfigure(); owner.lifecycle.shutdown(); instance = null;
    }
    /** Playback activities call this before their existing player teardown.
     * Application's onActivityPaused is delivered only after Activity.onPause
     * returns, which can be delayed by legacy network/player shutdown. Closing
     * these diagnostic sockets must not wait for that unrelated work. */
    public static synchronized void pauseActivity(Activity activity) {
        ClientDiagnosticWebService owner = instance;
        if (owner != null) owner.onActivityPaused(activity);
    }
    /** Read only: a saved switch/address alone is not proof of a live listener. */
    public static synchronized String exportAddress() {
        ClientDiagnosticWebService owner=instance;
        if(owner==null || owner.terminated || owner.visibleActivity.get()==null) return "";
        DiagnosticWebServer active=owner.server;
        if(active==null || active.isClosed())return "";
        String url=DiagnosticExportPolicy.firstLanUrl(owner.prefs.getString(AndroidPrefStore.CLIENT_WEB_STATUS,""));
        try{return !url.isEmpty() && new URI(url).getPort()==active.port() ? url : "";}
        catch(Exception invalid){return "";}
    }
    private void reconfigure() {
        if (lifecycle.isShutdown()) return;
        lifecycle.execute(() -> {
            boolean enabled = prefs.getBoolean(AndroidPrefStore.CLIENT_WEB_ENABLED, false);
            if (terminated || !enabled || foreground <= 0 || visibleActivity.get() == null) {
                stopListener(); remember(enabled && !terminated ? "Paused: open Vibe to use the web server" : "Off"); return;
            }
            try {
                int port = Integer.parseInt(prefs.getString(AndroidPrefStore.CLIENT_WEB_PORT, "8272"));
                if (server != null && !server.isClosed() && port == boundPort) { remember(addresses(port)); return; }
                stopListener();
                server = new DiagnosticWebServer(port, new DiagnosticWebServer.Reports() {
                    @Override public byte[] statistics() { return snapshot().getBytes(StandardCharsets.UTF_8); }
                    @Override public byte[] logs() throws Exception {
                        byte[] text=DiagnosticBundleManager.createSessionLog(app, "web-view");
                        int limit=2*1024*1024;
                        return text.length<=limit ? text : Arrays.copyOfRange(text,text.length-limit,text.length);
                    }
                    @Override public byte[] bundle() throws Exception { return bundleBytes(); }
                    @Override public byte[] data() throws Exception { return DiagnosticJson.encode(dataSnapshot()).getBytes(StandardCharsets.UTF_8); }
                    @Override public byte[] incident(String id) throws Exception { return history.download(id); }
                    @Override public byte[] asset(String name) throws Exception {
                        if (!name.matches("index\\.html|diagnostics\\.js|diagnostics\\.css")) throw new FileNotFoundException();
                        try (InputStream input=app.getAssets().open("diagnostics/"+name)) {
                            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] bytes=new byte[8192];int read;
                            while((read=input.read(bytes))>=0){if(out.size()+read>128*1024)throw new IOException("Asset too large");out.write(bytes,0,read);}return out.toByteArray();
                        }
                    }
                });
                // Pause may race this bind on the lifecycle worker. Do not
                // publish a fresh background listener after the fast close.
                if (visibleActivity.get() == null || terminated || foreground <= 0) {
                    stopListener(); remember("Paused: open Vibe to use the web server"); return;
                }
                boundPort = port; remember(addresses(port));
            } catch (Exception failure) { stopListener(); remember("Unavailable: port is invalid or in use; playback unchanged"); }
        });
    }
    private void stopListener() {
        if (server != null) server.close(); server = null; boundPort = 0;
        synchronized (this) { cachedBundle = null; bundleTime = 0; cachedData=null;dataTime=0;timeline.clear(); }
    }
    private synchronized byte[] bundleBytes() throws Exception {
        // Repeated browser downloads within 30 seconds reuse the exact redacted
        // report, bounding ZIP/disk work without changing the normal retention.
        long now = SystemClock.elapsedRealtime();
        if (cachedBundle != null && now - bundleTime < 30_000L) return cachedBundle;
        DiagnosticBundleManager.Result bundle = DiagnosticBundleManager.create(app, snapshot()
                + "\n[web-diagnostics]\n" + DiagnosticJson.encode(dataSnapshot()));
        if (bundle.bytes > 8 * 1024 * 1024) throw new IOException("Report exceeds web limit");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = new FileInputStream(bundle.file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) {
                if (out.size() + count > 8 * 1024 * 1024) throw new IOException("Report exceeds web limit");
                out.write(buffer, 0, count);
            }
        }
        cachedBundle = out.toByteArray(); bundleTime = now; return cachedBundle;
    }
    private synchronized Map<String,Object> dataSnapshot() throws Exception {
        long now=SystemClock.elapsedRealtime();
        if(cachedData!=null && now-dataTime<2000L)return cachedData;
        // Single-flight shared 2s cache prevents simultaneous tabs causing
        // extra player polling. Only the original overlay's readonly capture
        // runs on main; disk/history work remains on this HTTP worker.
        dataTime=now;
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("schema",1);out.put("capturedAtMs",System.currentTimeMillis());
        MiniclientApplication application=MiniclientApplication.get();
        MiniClientConnection current=application==null||application.getClient()==null?null:application.getClient().getCurrentConnection();
        Activity activity=visibleActivity.get();
        if(application!=null && activity!=null && !activity.isFinishing()){
            FutureTask<Map<String,Object>> capture=new FutureTask<>(()->ClientDiagnosticSnapshot.capture(activity,application.getClient(),current));
            main.post(capture);
            try{out.putAll(capture.get(750,TimeUnit.MILLISECONDS));}
            catch(Exception unavailable){capture.cancel(false);out.put("samplingUnavailable","Player statistics unavailable or main thread busy; playback unchanged");}
        }else out.put("samplingUnavailable","No foreground activity");
        Map<String,Object> device=new LinkedHashMap<>();
        device.put("version",application==null?"unknown":application.getVersionName());device.put("sdk",Build.VERSION.SDK_INT);
        device.put("model",DiagnosticRedactor.redact(Build.MODEL));device.put("manufacturer",DiagnosticRedactor.redact(Build.MANUFACTURER));
        Runtime runtime=Runtime.getRuntime();device.put("heapUsedBytes",runtime.totalMemory()-runtime.freeMemory());device.put("heapMaxBytes",runtime.maxMemory());
        device.put("uptimeMs",SystemClock.elapsedRealtime());device.put("connected",current!=null&&current.isConnected());out.put("device",device);
        Map<String,Object> playback=out.get("playback") instanceof Map?(Map<String,Object>)out.get("playback"):Collections.emptyMap();
        long seq=value(out,"seekSequence",0),target=value(out,"seekTargetMs",-1),wall=value(out,"seekWallMs",0);
        Object owner=out.remove("_timelineOwner");
        out.put("timeline",timeline.sample(owner,seq,target,wall,value(playback,"positionMs",-1),
                Boolean.TRUE.equals(playback.get("seekPending")),Boolean.TRUE.equals(playback.get("buffering")),(int)value(playback,"retryCount",0),System.currentTimeMillis()));
        out.put("timelineNote","Browser-sampled observations only; exact landing, unsampled requests and physical A/V sync are not inferred.");
        out.put("settings",safeSettings());
        try{out.put("incidents",history.list());}catch(Exception unavailable){out.put("incidents",Collections.emptyList());out.put("historyUnavailable",true);}
        out.put("lifecycle",DiagnosticRedactor.redact(ConnectionLifecycleDiagnostics.latestCompactWire()));
        cachedData=out;return out;
    }
    private static long value(Map<String,Object> source,String key,long fallback){Object value=source.get(key);return value instanceof Number?((Number)value).longValue():fallback;}
    private Map<String,Object> safeSettings(){
        Map<String,Object> out=new LinkedHashMap<>();
        // Explicit allowlist: NEVER enumerate getAll(), export connection/SMB
        // paths, usernames/passwords, client IDs, tokens or raw preferences.
        for(String key:new String[]{PrefStore.Keys.log_level,PrefStore.Keys.caption_cc1_type,PrefStore.Keys.caption_cc1_language,
                PrefStore.Keys.caption_cc2_type,PrefStore.Keys.caption_cc2_language,AndroidPrefStore.SMB_DIAGNOSTICS_MODE}){
            try{out.put(key,DiagnosticRedactor.redact(prefs.getString(key,"default")));}catch(RuntimeException wrongType){out.put(key,"Unavailable");}
        }
        for(String key:new String[]{PrefStore.Keys.use_log_to_sdcard,AndroidPrefStore.CLIENT_WEB_ENABLED,AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED,
                PrefStore.Keys.disable_audio_passthrough,PrefStore.Keys.playback_passthrough_audio_offset_enabled}){
            try{out.put(key,prefs.getBoolean(key,false));}catch(RuntimeException wrongType){out.put(key,"Unavailable");}
        }
        out.put("uploadStatus",DiagnosticRedactor.redact(prefs.getString(AndroidPrefStore.SERVER_DIAGNOSTICS_STATUS,"No upload yet")));
        out.put("lastUploadTimeMs",prefs.getLong(AndroidPrefStore.SERVER_DIAGNOSTICS_LAST_TIME,0));
        File dir=new File(app.getFilesDir(),"diagnostics");File[] files=dir.listFiles((d,name)->name.matches("OpenSageTV-Vibe-diagnostics-.*\\.zip"));
        long bytes=0;int count=0;if(files!=null)for(File file:files){if(file.isFile()){bytes+=file.length();count++;}}
        out.put("localBundleCount",count);out.put("localBundleBytes",bytes);out.put("freeAppStorageBytes",app.getFilesDir().getUsableSpace());
        out.put("bundleRetention",4);out.put("pushStallCapture",prefs.getBoolean(PrefStore.Keys.automatic_push_stall_diagnostics,true));return out;
    }
    private String snapshot() {
        Runtime runtime = Runtime.getRuntime();
        MiniclientApplication application = MiniclientApplication.get();
        MiniClientConnection current = application == null || application.getClient() == null
                ? null : application.getClient().getCurrentConnection();
        StringBuilder text = new StringBuilder("[Android Vibe client statistics]\n")
                .append("version=").append(application == null ? "unknown" : application.getVersionName())
                .append("\nandroidSdk=").append(Build.VERSION.SDK_INT)
                .append("\nmodel=").append(Build.MODEL)
                .append("\nuptimeMs=").append(SystemClock.elapsedRealtime())
                .append("\nheapUsedBytes=").append(runtime.totalMemory() - runtime.freeMemory())
                .append("\nheapMaxBytes=").append(runtime.maxMemory())
                .append("\nconnection=").append(current != null && current.isConnected())
                .append("\n").append(ConnectionLifecycleDiagnostics.latestCompactWire());
        MiniPlayerPlugin player = current == null || current.getMediaCmd() == null ? null : current.getMediaCmd().getPlaya();
        if (player != null) text.append("\nbackend=").append(player.getClass().getSimpleName())
                .append("\nstate=").append(player.getState());
        text.append("\nDetailed playback/codec/test statistics: see Logs or download ZIP.\n")
                .append("File logging must be enabled separately to collect app logs.\n");
        return DiagnosticRedactor.redact(text.toString());
    }
    private String addresses(int port) throws SocketException {
        List<String> urls = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface nic = interfaces.nextElement();
            if (!nic.isUp() || nic.isLoopback()) continue;
            Enumeration<InetAddress> addresses = nic.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (DiagnosticWebServer.lan(address) && !address.isLoopbackAddress())
                    urls.add("http://" + address.getHostAddress() + ":" + port + "/");
            }
        }
        if (urls.isEmpty()) return "Listening; no LAN IPv4 address detected";
        StringBuilder value = new StringBuilder();
        for (String url : urls) { if (value.length() > 0) value.append("  "); value.append(url); }
        return value.toString();
    }
    private void remember(String value) {
        prefs.edit().putString(AndroidPrefStore.CLIENT_WEB_STATUS, value).apply();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
        if (AndroidPrefStore.CLIENT_WEB_ENABLED.equals(key) || AndroidPrefStore.CLIENT_WEB_PORT.equals(key)) reconfigure();
    }
    @Override public void onActivityStarted(Activity activity) { foreground++; reconfigure(); }
    @Override public void onActivityStopped(Activity activity) { foreground = Math.max(0, foreground - 1); reconfigure(); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { visibleActivity=new WeakReference<>(activity); reconfigure(); }
    @Override public void onActivityPaused(Activity activity) {
        if (visibleActivity.get() != activity) return;
        visibleActivity.clear();
        // Close sockets immediately, without waiting for the worker's cached
        // snapshot/bundle monitor or slow player teardown in onActivityStopped.
        // close() never joins a player/network task. Cache cleanup stays on IO.
        DiagnosticWebServer listening = server;
        if (listening != null) listening.close();
        reconfigure();
    }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
