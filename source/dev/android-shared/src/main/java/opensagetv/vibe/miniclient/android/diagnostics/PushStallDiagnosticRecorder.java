package opensagetv.vibe.miniclient.android.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import opensagetv.vibe.miniclient.ConnectionLifecycleDiagnostics;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSnapshot;
import opensagetv.vibe.miniclient.android.video.PlaybackHealthSource;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/**
 * Captures a small incident record only after an already-playing Push stream
 * enters a meaningful rebuffer. Healthy playback has no timer and no file I/O.
 */
public final class PushStallDiagnosticRecorder
{
    public static final String DIRECTORY = "push-stall-diagnostics";
    private static final long SAMPLE_MS = 250L;
    private static final long MINIMUM_INCIDENT_MS = 1_500L;
    private static final long MAXIMUM_INCIDENT_MS = 30_000L;
    private static final long POST_RECOVERY_MS = 3_000L;
    private static final int MAX_SAMPLES = 140;
    private static final int MAX_FILES = 4;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context application;
    private final SharedPreferences preferences;
    private final MiniPlayerPlugin player;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<String> samples = new ArrayList<String>(MAX_SAMPLES);
    private PushBufferDataSource source;
    private boolean readySeen;
    private boolean active;
    private boolean recovering;
    private boolean postSeek;
    private long startedMs;
    private long recoveredMs;

    private final Runnable sampler = new Runnable()
    {
        @Override public void run()
        {
            if (!active || !enabled())
            {
                cancel();
                return;
            }
            capture();
            long now = SystemClock.elapsedRealtime();
            if (samples.size() >= MAX_SAMPLES || now - startedMs >= MAXIMUM_INCIDENT_MS
                    || (recovering && now - recoveredMs >= POST_RECOVERY_MS))
            {
                finish(recovering ? "ready" : "timeout");
                return;
            }
            main.postDelayed(this, SAMPLE_MS);
        }
    };

    public PushStallDiagnosticRecorder(Context context, MiniPlayerPlugin player)
    {
        this.application = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(application);
        this.player = player;
    }

    /** Reset at each LOAD/FREE so initial buffering is never reported as a stall. */
    public void resetSession()
    {
        cancel();
        readySeen = false;
        source = null;
    }

    /** Called from existing backend buffering callbacks on Android's main thread. */
    public void onBufferingChanged(boolean buffering, PushBufferDataSource current,
            boolean eligible, boolean expectedPostSeek)
    {
        if (!enabled() || !eligible || current == null)
        {
            if (!eligible) resetSession();
            return;
        }
        source = current;
        if (!buffering)
        {
            readySeen = true;
            if (active && !recovering)
            {
                recovering = true;
                recoveredMs = SystemClock.elapsedRealtime();
                capture();
            }
            return;
        }
        if (!readySeen || active) return;
        active = true;
        recovering = false;
        postSeek = expectedPostSeek;
        startedMs = SystemClock.elapsedRealtime();
        recoveredMs = 0L;
        samples.clear();
        capture();
        main.postDelayed(sampler, SAMPLE_MS);
    }

    private boolean enabled()
    {
        return preferences.getBoolean(PrefStore.Keys.automatic_push_stall_diagnostics, true);
    }

    private void capture()
    {
        if (!active || samples.size() >= MAX_SAMPLES || source == null) return;
        long now = SystemClock.elapsedRealtime();
        PushBufferDataSource.TelemetrySnapshot push = source.telemetrySnapshot();
        PlaybackHealthSnapshot health = player instanceof PlaybackHealthSource
                ? ((PlaybackHealthSource) player).capturePlaybackHealthSnapshot() : null;
        StringBuilder out = new StringBuilder(768);
        out.append('{');
        number(out, "elapsedMs", now - startedMs);
        bool(out, "recovering", recovering);
        bool(out, "postSeek", postSeek);
        number(out, "playerState", player.getState());
        number(out, "positionMs", safeMediaTime());
        number(out, "bufferedAheadMs", player.getBufferedPlaybackAheadMillis());
        number(out, "advertisedFreeBytes", player.getBufferLeft());
        text(out, "player", player.getClass().getSimpleName());
        text(out, "videoDecoder", optional("getSelectedVideoDecoderForDebug"));
        text(out, "audioDecoder", optional("getSelectedAudioDecoderForDebug"));
        if (health != null)
        {
            bool(out, "playerReady", health.isPlayerReady());
            bool(out, "seekPending", health.isSeekPending());
            bool(out, "flushed", health.isFlushed());
            bool(out, "errorState", health.isErrorState());
            number(out, "retryCount", health.getRetryCount());
        }
        number(out, "connectionGeneration", ConnectionLifecycleDiagnostics.latestGeneration());
        number(out, "connectionReconnects", ConnectionLifecycleDiagnostics.latestReconnectCount());
        number(out, "readCalls", push.readCalls);
        number(out, "readRequestedBytes", push.readRequestedBytes);
        number(out, "readWaitMs", push.readWaitMs);
        number(out, "bytesRead", push.bytesRead);
        number(out, "pushCalls", push.pushCalls);
        number(out, "pushedBytes", push.pushedBytes);
        number(out, "blockedPushCalls", push.blockedPushCalls);
        number(out, "bufferUsedBytes", push.bufferUsedBytes);
        number(out, "bufferFreeBytes", push.bufferFreeBytes);
        number(out, "lastReadAgeMs", push.lastReadAgeMs);
        number(out, "lastPushArrivalAgeMs", push.lastPushArrivalAgeMs);
        number(out, "lastPushCompletionAgeMs", push.lastPushCompletionAgeMs);
        number(out, "activeReadWaitMs", push.activeReadWaitMs);
        number(out, "activePushWriteMs", push.activePushWriteMs);
        number(out, "lastPushWriteMs", push.lastPushWriteMs);
        number(out, "maximumPushWriteMs", push.maximumPushWriteMs);
        number(out, "lastPushBytes", push.lastPushBytes);
        text(out, "pushState", push.state);
        bool(out, "eos", push.eos);
        bool(out, "readsSuspended", push.readsSuspended);
        if (out.charAt(out.length() - 1) == ',') out.setLength(out.length() - 1);
        out.append('}');
        samples.add(out.toString());
    }

    private void finish(final String outcome)
    {
        long totalDuration = SystemClock.elapsedRealtime() - startedMs;
        long stallDuration = recovering ? recoveredMs - startedMs : totalDuration;
        main.removeCallbacks(sampler);
        if (stallDuration < MINIMUM_INCIDENT_MS)
        {
            active = false;
            recovering = false;
            samples.clear();
            return;
        }
        final ArrayList<String> copy = new ArrayList<String>(samples);
        final long incidentDuration = stallDuration;
        final boolean incidentPostSeek = postSeek;
        active = false;
        recovering = false;
        samples.clear();
        IO.execute(new Runnable()
        {
            @Override public void run()
            {
                writeIncident(copy, outcome, incidentDuration, incidentPostSeek);
            }
        });
    }

    private void cancel()
    {
        main.removeCallbacks(sampler);
        active = false;
        recovering = false;
        samples.clear();
    }

    private void writeIncident(List<String> evidence, String outcome, long duration,
            boolean expectedPostSeek)
    {
        try
        {
            if (!enabled()) return;
            File directory = new File(application.getFilesDir(), DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File temporary = new File(directory, "push-stall.tmp");
            File target = new File(directory, "push-stall-" + utc() + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".jsonl");
            StringBuilder value = new StringBuilder(evidence.size() * 768 + 256);
            value.append("{\"schema\":1,\"outcome\":\"").append(outcome)
                    .append("\",\"durationMs\":").append(duration)
                    .append(",\"postSeek\":").append(expectedPostSeek)
                    .append(",\"sampleCount\":").append(evidence.size()).append("}\n");
            for (String sample : evidence) value.append(sample).append('\n');
            FileOutputStream output = new FileOutputStream(temporary);
            try
            {
                output.write(value.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            finally { output.close(); }
            if (!temporary.renameTo(target)) temporary.delete();
            prune(directory);
            DiagnosticSessionSpool.checkpoint("push-stall-incident");
        }
        catch (Exception ignored)
        {
            // Diagnostics must never affect playback or application lifetime.
        }
    }

    private long safeMediaTime()
    {
        try { return player.getMediaTimeMillis(0L); }
        catch (RuntimeException ignored) { return -1L; }
    }

    private String optional(String methodName)
    {
        try
        {
            Method method = player.getClass().getMethod(methodName);
            Object value = method.invoke(player);
            return value == null ? "" : String.valueOf(value);
        }
        catch (Exception ignored) { return ""; }
    }

    private static void prune(File directory)
    {
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("push-stall-") && name.endsWith(".jsonl"));
        if (files == null || files.length <= MAX_FILES) return;
        Arrays.sort(files, new Comparator<File>()
        {
            @Override public int compare(File left, File right)
            { return Long.compare(left.lastModified(), right.lastModified()); }
        });
        for (int index = 0; index < files.length - MAX_FILES; index++) files[index].delete();
    }

    private static void number(StringBuilder out, String name, long value)
    { out.append('\"').append(name).append("\":").append(value).append(','); }

    private static void bool(StringBuilder out, String name, boolean value)
    { out.append('\"').append(name).append("\":").append(value).append(','); }

    private static void text(StringBuilder out, String name, String value)
    {
        out.append('\"').append(name).append("\":\"")
                .append(escape(value == null ? "" : value)).append("\",");
    }

    private static String escape(String value)
    {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private static String utc()
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
