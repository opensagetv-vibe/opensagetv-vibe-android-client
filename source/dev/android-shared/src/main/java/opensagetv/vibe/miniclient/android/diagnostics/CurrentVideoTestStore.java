package opensagetv.vibe.miniclient.android.diagnostics;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Bounded durable storage for user-initiated current-video diagnostic tests. */
public final class CurrentVideoTestStore
{
    static final String DIRECTORY = "current-video-tests";
    static final int MAX_REPORTS = 4;

    private CurrentVideoTestStore() { }

    public static File write(Context context, String report) throws Exception
    {
        File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IllegalStateException("current-video test directory unavailable");

        String name = "current-video-test-" + utc("yyyyMMdd-HHmmss") + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        File output = new File(directory, name);
        File temporary = new File(directory, name + ".tmp");
        byte[] bytes = (DiagnosticRedactor.redact(report) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream = new FileOutputStream(temporary);
        try
        {
            stream.write(bytes);
            stream.getFD().sync();
        }
        finally { stream.close(); }
        if (!temporary.renameTo(output))
            throw new IllegalStateException("unable to finalize current-video test report");
        prune(directory, output);
        return output;
    }

    static File[] reports(Context context)
    {
        File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("current-video-test-") && name.endsWith(".txt"));
        if (files == null) return new File[0];
        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        return files;
    }

    private static void prune(File directory, File preserve)
    {
        File[] files = reportsFrom(directory);
        if (files.length <= MAX_REPORTS) return;
        for (int index = 0; index < files.length - MAX_REPORTS; index++)
            if (!files[index].equals(preserve)) files[index].delete();
    }

    private static File[] reportsFrom(File directory)
    {
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("current-video-test-") && name.endsWith(".txt"));
        if (files == null) return new File[0];
        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        return files;
    }

    private static String utc(String pattern)
    {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
