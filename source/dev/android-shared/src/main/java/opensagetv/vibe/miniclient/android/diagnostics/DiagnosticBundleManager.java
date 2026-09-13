package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import opensagetv.vibe.miniclient.android.AppUtil;

/** Creates one bounded, redacted support bundle suitable for Issues or SMB. */
public final class DiagnosticBundleManager
{
    static final int MAX_LOG_FILES = 3;
    static final int MAX_LOG_BYTES = 512 * 1024;

    private DiagnosticBundleManager() { }

    public static Result create(Context context, String activePlayerText) throws Exception
    {
        Context app = context.getApplicationContext();
        // Keep bundles credential-protected. Users export through FileProvider;
        // debug automation can verify the same bytes through run-as.
        File directory = new File(app.getFilesDir(), "diagnostics");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("diagnostics directory unavailable");

        String stamp = utc("yyyyMMdd-HHmmss");
        String token = UUID.randomUUID().toString().substring(0, 8);
        File output = new File(directory,
                "OpenSageTV-Vibe-diagnostics-" + DiagnosticIdentity.token(app) + "-"
                        + stamp + "-" + token + ".zip");

        List<Entry> entries = new ArrayList<Entry>();
        entries.add(new Entry("device.txt", metadata(app)));
        if (activePlayerText != null && !activePlayerText.trim().isEmpty())
            entries.add(new Entry("active-player.txt", utf8(DiagnosticRedactor.redact(activePlayerText) + "\n")));
        addLogs(app, entries);
        addPlaybackTraces(app, entries);
        addCurrentVideoTests(app, entries);

        StringBuilder checksums = new StringBuilder();
        for (Entry entry : entries)
            checksums.append(sha256(entry.bytes)).append("  ").append(entry.name).append('\n');
        entries.add(new Entry("checksums.sha256", utf8(checksums.toString())));

        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
        try
        {
            for (Entry entry : entries)
            {
                ZipEntry item = new ZipEntry(entry.name);
                item.setTime(0L);
                zip.putNextEntry(item);
                zip.write(entry.bytes);
                zip.closeEntry();
            }
        }
        finally { zip.close(); }
        pruneLocal(directory, output, 4);
        return new Result(output, sha256(output), output.length());
    }

    /** Builds the bounded redacted text used by Always-mode durable spooling. */
    static byte[] createSessionLog(Context context, String reason) throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(utf8("# OpenSageTV Vibe diagnostic session\nreason="
                + DiagnosticRedactor.redact(reason) + "\n\n"));
        output.write(metadata(context.getApplicationContext()));
        File[] files = AppUtil.getLogDir(context).listFiles();
        if (files != null)
        {
            List<File> logs = new ArrayList<File>();
            for (File file : files)
                if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".txt")) logs.add(file);
            Collections.sort(logs, new Comparator<File>()
            {
                @Override public int compare(File left, File right)
                { return Long.compare(left.lastModified(), right.lastModified()); }
            });
            for (int index = Math.max(0, logs.size() - MAX_LOG_FILES); index < logs.size(); index++)
            {
                output.write(utf8("\n--- app log " + (index + 1) + " ---\n"));
                String text = new String(readTail(logs.get(index), MAX_LOG_BYTES), StandardCharsets.UTF_8);
                output.write(utf8(DiagnosticRedactor.redact(text)));
            }
        }
        File[] videoTests = CurrentVideoTestStore.reports(context);
        for (File videoTest : videoTests)
        {
            output.write(utf8("\n--- current-video diagnostic test ---\n"));
            String text = new String(readTail(videoTest, MAX_LOG_BYTES), StandardCharsets.UTF_8);
            output.write(utf8(DiagnosticRedactor.redact(text)));
        }
        return output.toByteArray();
    }

    public static void share(Activity activity, Result result)
    {
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", result.file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "OpenSageTV Vibe diagnostic bundle");
        intent.setClipData(ClipData.newRawUri("OpenSageTV Vibe diagnostics", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent, "Share diagnostic bundle"));
    }

    private static byte[] metadata(Context context) throws Exception
    {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        String value = "createdUtc=" + utc("yyyy-MM-dd'T'HH:mm:ss'Z'")
                + "\npackage=" + context.getPackageName()
                + "\nversionName=" + info.versionName
                + "\nversionCode=" + info.versionCode
                + "\nandroidSdk=" + Build.VERSION.SDK_INT
                + "\nmanufacturer=" + safe(Build.MANUFACTURER)
                + "\nmodel=" + safe(Build.MODEL)
                + "\nproduct=" + safe(Build.PRODUCT)
                + "\nabi=" + Arrays.toString(Build.SUPPORTED_ABIS)
                + "\n";
        return utf8(DiagnosticRedactor.redact(value));
    }

    private static void addLogs(Context context, List<Entry> entries) throws IOException
    {
        File[] files = AppUtil.getLogDir(context).listFiles();
        if (files == null) return;
        List<File> logs = new ArrayList<File>();
        for (File file : files)
            if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".txt")) logs.add(file);
        Collections.sort(logs, new Comparator<File>()
        {
            @Override public int compare(File left, File right)
            { return Long.compare(right.lastModified(), left.lastModified()); }
        });
        for (int index = 0; index < Math.min(MAX_LOG_FILES, logs.size()); index++)
        {
            String text = new String(readTail(logs.get(index), MAX_LOG_BYTES), StandardCharsets.UTF_8);
            entries.add(new Entry("logs/app-" + (index + 1) + ".log",
                    utf8(DiagnosticRedactor.redact(text))));
        }
    }

    private static void addPlaybackTraces(Context context, List<Entry> entries) throws IOException
    {
        File[] files = context.getFilesDir().listFiles();
        if (files == null) return;
        List<File> traces = new ArrayList<File>();
        for (File file : files)
            if (file.isFile() && file.getName().startsWith("playback-trace")
                    && file.getName().endsWith(".jsonl")) traces.add(file);
        Collections.sort(traces, new Comparator<File>()
        {
            @Override public int compare(File left, File right)
            { return Long.compare(left.lastModified(), right.lastModified()); }
        });
        for (int index = Math.max(0, traces.size() - 4); index < traces.size(); index++)
        {
            String text = new String(readTail(traces.get(index), MAX_LOG_BYTES), StandardCharsets.UTF_8);
            entries.add(new Entry("playback-trace/trace-" + (index + 1) + ".jsonl",
                    utf8(DiagnosticRedactor.redact(text))));
        }
    }

    private static void addCurrentVideoTests(Context context, List<Entry> entries) throws IOException
    {
        File[] reports = CurrentVideoTestStore.reports(context);
        for (int index = 0; index < reports.length; index++)
        {
            String text = new String(readTail(reports[index], MAX_LOG_BYTES), StandardCharsets.UTF_8);
            entries.add(new Entry("current-video-tests/test-" + (index + 1) + ".txt",
                    utf8(DiagnosticRedactor.redact(text))));
        }
    }

    private static byte[] readTail(File file, int maximum) throws IOException
    {
        long length = file.length();
        int count = (int) Math.min(maximum, length);
        byte[] value = new byte[count];
        FileInputStream input = new FileInputStream(file);
        try
        {
            long skip = length - count;
            while (skip > 0)
            {
                long advanced = input.skip(skip);
                if (advanced <= 0) break;
                skip -= advanced;
            }
            int offset = 0;
            while (offset < count)
            {
                int read = input.read(value, offset, count - offset);
                if (read < 0) break;
                offset += read;
            }
            return offset == count ? value : Arrays.copyOf(value, offset);
        }
        finally { input.close(); }
    }

    private static void pruneLocal(File directory, File preserve, int keep)
    {
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("OpenSageTV-Vibe-diagnostics-") && name.endsWith(".zip"));
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, new Comparator<File>()
        {
            @Override public int compare(File left, File right)
            { return Long.compare(left.lastModified(), right.lastModified()); }
        });
        int remove = files.length - keep;
        for (File file : files)
        {
            if (remove <= 0) break;
            if (!file.equals(preserve) && file.delete()) remove--;
        }
    }

    private static String sha256(File file) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        try
        {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        finally { input.close(); }
        return hex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String safe(String value) { return value == null ? "unknown" : value.replace('\n', ' '); }
    private static String utc(String pattern)
    {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class Entry
    {
        final String name;
        final byte[] bytes;
        Entry(String name, byte[] bytes) { this.name = name; this.bytes = bytes; }
    }

    public static final class Result
    {
        public final File file;
        public final String sha256;
        public final long bytes;
        Result(File file, String sha256, long bytes)
        { this.file = file; this.sha256 = sha256; this.bytes = bytes; }
    }
}
