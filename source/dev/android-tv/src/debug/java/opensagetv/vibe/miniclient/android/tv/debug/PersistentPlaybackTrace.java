package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import opensagetv.vibe.miniclient.android.MiniclientApplication;

/**
 * Debug-build-only, bounded JSON-lines playback trace.
 *
 * Writes run on one low-volume background worker so decoder/player callbacks never wait on
 * storage. The files live in credential-protected app storage, contain no media payloads or
 * credentials, and are exported explicitly through the MCP/ADB diagnostic tools.
 */
final class PersistentPlaybackTrace
{
    static final String FILE_NAME = "playback-trace.jsonl";
    private static final String PREFS_NAME = "vibe_playback_trace";
    private static final String KEY_ENABLED = "enabled";
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final int ROTATION_COUNT = 3;
    private static final Object FILE_LOCK = new Object();
    private static final AtomicLong TRACE_EPOCH = new AtomicLong();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "VibePlaybackTrace");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private PersistentPlaybackTrace()
    {
    }

    static void append(final String jsonLine)
    {
        if (jsonLine == null || jsonLine.isEmpty())
            return;
        if (!isEnabled(null))
            return;
        final long epoch = TRACE_EPOCH.get();
        WRITER.execute(() -> appendNow(jsonLine, epoch));
    }

    static String statusWire(Context context)
    {
        File file = traceFile(context);
        if (file == null)
            return "traceAvailable=false;traceReason=application_context_unavailable";
        int rotations = 0;
        long totalBytes = file.isFile() ? file.length() : 0L;
        for (int index = 1; index <= ROTATION_COUNT; index++)
        {
            File rotated = new File(file.getParentFile(), FILE_NAME + "." + index);
            if (rotated.isFile())
            {
                rotations++;
                totalBytes += rotated.length();
            }
        }
        return "traceAvailable=true;tracePath=" + file.getAbsolutePath()
                + ";traceEnabled=" + isEnabled(context)
                + ";traceCurrentBytes=" + (file.isFile() ? file.length() : 0L)
                + ";traceTotalBytes=" + totalBytes
                + ";traceRotations=" + rotations
                + ";traceMaxBytes=" + MAX_BYTES
                + ";traceRotationLimit=" + ROTATION_COUNT
                + ";traceFormat=jsonl";
    }

    static String setEnabledWire(Context context, boolean enabled)
    {
        if (context == null)
            return "traceEnabled=false;traceReason=application_context_unavailable";
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled)
            TRACE_EPOCH.incrementAndGet();
        return "traceEnabled=" + enabled + ";existingTracePreserved=true";
    }

    static String clearWire(Context context)
    {
        File file = traceFile(context);
        if (file == null)
            return "cleared=false;traceReason=application_context_unavailable";
        TRACE_EPOCH.incrementAndGet();
        int deleted = 0;
        synchronized (FILE_LOCK)
        {
            for (int index = ROTATION_COUNT; index >= 1; index--)
            {
                File rotated = new File(file.getParentFile(), FILE_NAME + "." + index);
                if (rotated.isFile() && rotated.delete())
                    deleted++;
            }
            if (file.isFile() && file.delete())
                deleted++;
        }
        return "cleared=true;traceFilesDeleted=" + deleted;
    }

    private static void appendNow(String jsonLine, long epoch)
    {
        if (epoch != TRACE_EPOCH.get())
            return;
        if (!isEnabled(null))
            return;
        File file = traceFile(null);
        if (file == null)
            return;
        byte[] bytes = (jsonLine + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (FILE_LOCK)
        {
            if (epoch != TRACE_EPOCH.get())
                return;
            try
            {
                File directory = file.getParentFile();
                if (directory == null || (!directory.isDirectory() && !directory.mkdirs()))
                    return;
                if (file.isFile() && file.length() + bytes.length > MAX_BYTES)
                    rotate(file);
                try (FileOutputStream output = new FileOutputStream(file, true))
                {
                    output.write(bytes);
                }
            }
            catch (IOException ignored)
            {
                // A diagnostic write must never change playback behavior.
            }
        }
    }

    private static void rotate(File current)
    {
        File directory = current.getParentFile();
        File oldest = new File(directory, FILE_NAME + "." + ROTATION_COUNT);
        if (oldest.isFile())
            oldest.delete();
        for (int index = ROTATION_COUNT - 1; index >= 1; index--)
        {
            File source = new File(directory, FILE_NAME + "." + index);
            if (source.isFile())
                source.renameTo(new File(directory, FILE_NAME + "." + (index + 1)));
        }
        current.renameTo(new File(directory, FILE_NAME + ".1"));
    }

    private static File traceFile(Context supplied)
    {
        Context context = supplied;
        if (context == null)
            context = MiniclientApplication.get();
        if (context == null)
            return null;
        return new File(new File(context.getFilesDir(), "diagnostics"), FILE_NAME);
    }

    private static boolean isEnabled(Context supplied)
    {
        Context context = supplied;
        if (context == null)
            context = MiniclientApplication.get();
        if (context == null)
            return false;
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getBoolean(KEY_ENABLED, true);
    }
}
