package opensagetv.vibe.miniclient.android.diagnostics;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import opensagetv.vibe.miniclient.net.SmbMappedPath;
import opensagetv.vibe.miniclient.net.SmbPathMapper;

/** Independent SMB2/SMB3 destination for redacted diagnostic artifacts. */
public final class SmbDiagnosticsRepository
{
    private static final int MAX_BUNDLE_BYTES = 16 * 1024 * 1024;
    private final String directoryUrl;
    private final String username;
    private final char[] password;
    private final String domain;
    private final boolean anonymous;

    public SmbDiagnosticsRepository(String directoryUrl, boolean anonymous,
                                    String username, char[] password, String domain)
    {
        this.directoryUrl = directoryUrl == null ? "" : directoryUrl.trim();
        this.anonymous = anonymous;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? new char[0] : password.clone();
        this.domain = domain == null ? "" : domain.trim();
    }

    public UploadResult upload(final File localFile, final String expectedSha256) throws IOException
    {
        if (localFile == null || !localFile.isFile()) throw new IOException("Diagnostic bundle is unavailable");
        if (localFile.length() <= 0 || localFile.length() > MAX_BUNDLE_BYTES)
            throw new IOException("Diagnostic bundle is empty or exceeds the 16 MiB limit");
        final String name = safeFileName(localFile.getName());
        final byte[] contents = read(localFile, MAX_BUNDLE_BYTES);
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(sha256(contents)))
            throw new IOException("Diagnostic bundle changed before upload");
        final long started = System.nanoTime();
        return withShare(new ShareOperation<UploadResult>()
        {
            @Override public UploadResult run(DiskShare share, SmbMappedPath location,
                                              StageTimings timings) throws IOException
            {
                long operationStarted = System.nanoTime();
                String root = root(location);
                requireDirectory(share, root);
                String destination = join(root, name);
                if (share.fileExists(destination))
                    throw new IOException("A diagnostic artifact with this unique name already exists");
                String temporary = join(root, "." + name + ".tmp-" + UUID.randomUUID().toString());
                com.hierynomus.smbj.share.File remote = null;
                try
                {
                    remote = share.openFile(temporary,
                            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
                    long written = remote.write(contents, 0, 0, contents.length);
                    if (written != contents.length) throw new IOException("Short SMB diagnostic write");
                    remote.flush();
                    remote.rename(destination, false);
                    pruneOwnArtifacts(share, root, ownerPrefix(name, "diagnostics"), ".zip", 8);
                    long totalMs = elapsedMs(started);
                    return new UploadResult(name, contents.length, sha256(contents), totalMs,
                            timings.summary(elapsedMs(operationStarted), totalMs));
                }
                catch (RuntimeException e) { throw new IOException("SMB diagnostic upload failed", e); }
                finally
                {
                    closeQuietly(remote);
                    try { if (share.fileExists(temporary)) share.rm(temporary); }
                    catch (RuntimeException ignored) { }
                }
            }
        });
    }

    public UploadResult refreshSessionLog(final File localFile, final String remoteFileName) throws IOException
    {
        if (localFile == null || !localFile.isFile()) throw new IOException("Diagnostic session log is unavailable");
        if (localFile.length() <= 0 || localFile.length() > MAX_BUNDLE_BYTES)
            throw new IOException("Diagnostic session log is empty or exceeds the 16 MiB limit");
        if (remoteFileName == null || !remoteFileName.matches("OpenSageTV-Vibe-session-[A-Za-z0-9.-]+\\.log"))
            throw new IOException("Invalid diagnostic session filename");
        final byte[] contents = read(localFile, MAX_BUNDLE_BYTES);
        final long started = System.nanoTime();
        return withShare(new ShareOperation<UploadResult>()
        {
            @Override public UploadResult run(DiskShare share, SmbMappedPath location,
                                              StageTimings timings) throws IOException
            {
                long operationStarted = System.nanoTime();
                String root = root(location);
                requireDirectory(share, root);
                String destination = join(root, remoteFileName);
                String temporary = join(root, "." + remoteFileName + ".tmp-" + UUID.randomUUID().toString());
                com.hierynomus.smbj.share.File remote = null;
                try
                {
                    remote = share.openFile(temporary,
                            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_CREATE,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
                    long written = remote.write(contents, 0, 0, contents.length);
                    if (written != contents.length) throw new IOException("Short SMB diagnostic session write");
                    remote.flush();
                    remote.rename(destination, true);
                    pruneOwnArtifacts(share, root, ownerPrefix(remoteFileName, "session"), ".log", 4);
                    long totalMs = elapsedMs(started);
                    return new UploadResult(remoteFileName, contents.length, sha256(contents), totalMs,
                            timings.summary(elapsedMs(operationStarted), totalMs));
                }
                catch (RuntimeException e) { throw new IOException("SMB diagnostic session refresh failed", e); }
                finally
                {
                    closeQuietly(remote);
                    try { if (share.fileExists(temporary)) share.rm(temporary); }
                    catch (RuntimeException ignored) { }
                }
            }
        });
    }

    public TestResult testConnection() throws IOException
    {
        final byte[] contents = ("OpenSageTV Vibe diagnostic SMB test " + UUID.randomUUID())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final String expected = sha256(contents);
        final long started = System.nanoTime();
        return withShare(new ShareOperation<TestResult>()
        {
            @Override public TestResult run(DiskShare share, SmbMappedPath location,
                                            StageTimings timings) throws IOException
            {
                long operationStarted = System.nanoTime();
                String root = root(location);
                requireDirectory(share, root);
                String name = ".vibe-diagnostics-test-" + UUID.randomUUID() + ".tmp";
                String remoteName = join(root, name);
                com.hierynomus.smbj.share.File remote = null;
                boolean removed = false;
                try
                {
                    remote = share.openFile(remoteName,
                            EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
                    long written = remote.write(contents, 0, 0, contents.length);
                    if (written != contents.length) throw new IOException("Short SMB test write");
                    remote.flush();
                    byte[] read = new byte[contents.length];
                    int count = remote.read(read, 0, 0, read.length);
                    if (count != contents.length || !expected.equals(sha256(read)))
                        throw new IOException("SMB test read/hash verification failed");
                    closeQuietly(remote);
                    remote = null;
                    share.rm(remoteName);
                    removed = !share.fileExists(remoteName);
                    if (!removed) throw new IOException("SMB test file cleanup could not be verified: " + name);
                    long totalMs = elapsedMs(started);
                    return new TestResult(name, totalMs, true,
                            timings.summary(elapsedMs(operationStarted), totalMs));
                }
                catch (RuntimeException e)
                {
                    throw new IOException("SMB diagnostics connection test failed; cleanup target: " + name, e);
                }
                finally
                {
                    closeQuietly(remote);
                    if (!removed)
                    {
                        try { if (share.fileExists(remoteName)) share.rm(remoteName); }
                        catch (RuntimeException ignored) { }
                    }
                }
            }
        });
    }

    public TestResult testReadOnly() throws IOException
    {
        final long started = System.nanoTime();
        return withShare(new ShareOperation<TestResult>()
        {
            @Override public TestResult run(DiskShare share, SmbMappedPath location,
                                            StageTimings timings) throws IOException
            {
                long operationStarted = System.nanoTime();
                String root = root(location);
                requireDirectory(share, root);
                try { share.list(root, "*"); }
                catch (RuntimeException e) { throw new IOException("SMB directory read test failed", e); }
                long totalMs = elapsedMs(started);
                return new TestResult("", totalMs, true,
                        timings.summary(elapsedMs(operationStarted), totalMs));
            }
        });
    }

    public void clear() { Arrays.fill(password, '\0'); }

    private <T> T withShare(ShareOperation<T> operation) throws IOException
    {
        SMBClient client = null;
        Connection connection = null;
        Session session = null;
        DiskShare share = null;
        char[] secret = password.clone();
        StageTimings timings = new StageTimings();
        String stage = "URL validation";
        try
        {
            long stageStarted = System.nanoTime();
            if (directoryUrl.isEmpty()) throw new IOException("directory is not configured");
            SmbMappedPath location = SmbPathMapper.parse("/ => " + directoryUrl).map("/");
            if (location == null) throw new IOException("directory could not be mapped");
            timings.urlMs = elapsedMs(stageStarted);

            SmbConfig config = SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .withSoTimeout(10, TimeUnit.SECONDS).build();
            client = new SMBClient(config);

            stage = "DNS/connect";
            stageStarted = System.nanoTime();
            connection = client.connect(location.getServer(), location.getPort());
            timings.connectMs = elapsedMs(stageStarted);

            stage = "authentication";
            stageStarted = System.nanoTime();
            AuthenticationContext auth = anonymous
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(username, secret, domain);
            session = connection.authenticate(auth);
            timings.authMs = elapsedMs(stageStarted);

            stage = "share open";
            stageStarted = System.nanoTime();
            share = (DiskShare) session.connectShare(location.getShare());
            timings.shareMs = elapsedMs(stageStarted);

            stage = "path/read-write verification";
            return operation.run(share, location, timings);
        }
        catch (Exception e)
        {
            String detail = e.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = e.getClass().getSimpleName();
            throw new IOException("SMB diagnostics " + stage + " failed: " + detail, e);
        }
        finally
        {
            Arrays.fill(secret, '\0');
            closeQuietly(share); closeQuietly(session); closeQuietly(connection); closeQuietly(client);
        }
    }

    private static byte[] read(File file, int maximum) throws IOException
    {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream input = new FileInputStream(file);
        try
        {
            int offset = 0;
            while (offset < bytes.length)
            {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != bytes.length) throw new IOException("Diagnostic bundle ended early");
            return bytes;
        }
        finally { input.close(); }
    }

    private static String safeFileName(String name) throws IOException
    {
        if (name == null || !name.matches("OpenSageTV-Vibe-diagnostics-[A-Za-z0-9.-]+\\.zip"))
            throw new IOException("Invalid diagnostic bundle filename");
        return name;
    }
    private static String ownerPrefix(String name, String kind) throws IOException
    {
        String root = "OpenSageTV-Vibe-" + kind + "-";
        if (!name.startsWith(root)) throw new IOException("Invalid diagnostic artifact namespace");
        String remainder = name.substring(root.length());
        int separator = remainder.indexOf('-');
        String token = separator < 0 ? "" : remainder.substring(0, separator);
        if (!token.matches("[0-9a-f]{12}"))
            return name; // Older unique artifacts are never candidates for automated removal.
        return root + token + "-";
    }
    private static void pruneOwnArtifacts(DiskShare share, String root, String prefix,
                                          String suffix, int keep)
    {
        if (prefix == null || !prefix.endsWith("-")) return;
        try
        {
            List<String> names = new ArrayList<String>();
            for (FileIdBothDirectoryInformation item : share.list(root, prefix + "*" + suffix))
            {
                String name = item.getFileName();
                if (name != null && name.startsWith(prefix) && name.endsWith(suffix)) names.add(name);
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            for (int index = 0; index < names.size() - keep; index++) share.rm(join(root, names.get(index)));
        }
        catch (RuntimeException ignored)
        {
            // Retention must never turn an otherwise verified support upload into
            // a playback/UI failure; the next refresh retries bounded cleanup.
        }
    }
    private static String root(SmbMappedPath location) { return smbPath(location.getPath()); }
    private static void requireDirectory(DiskShare share, String root) throws IOException
    { if (!root.isEmpty() && !share.folderExists(root)) throw new IOException("SMB diagnostics directory does not exist"); }
    private static String join(String root, String name) { return root.isEmpty() ? name : root + "\\" + name; }
    private static String smbPath(String path)
    {
        if (path == null) return "";
        String value = path.replace('/', '\\');
        while (value.startsWith("\\")) value = value.substring(1);
        while (value.endsWith("\\")) value = value.substring(0, value.length() - 1);
        return value;
    }
    private static String sha256(byte[] bytes) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder out = new StringBuilder();
            for (byte value : digest.digest(bytes)) out.append(String.format(Locale.US, "%02x", value & 0xff));
            return out.toString();
        }
        catch (Exception e) { throw new IOException("SHA-256 unavailable", e); }
    }
    private static long elapsedMs(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private static void closeQuietly(AutoCloseable value)
    { if (value != null) try { value.close(); } catch (Exception ignored) { } }
    private interface ShareOperation<T>
    { T run(DiskShare share, SmbMappedPath location, StageTimings timings) throws IOException; }

    private static final class StageTimings
    {
        long urlMs;
        long connectMs;
        long authMs;
        long shareMs;

        String summary(long operationMs, long totalMs)
        {
            return "url=" + urlMs + " ms, DNS/connect=" + connectMs
                    + " ms, auth=" + authMs + " ms, share=" + shareMs
                    + " ms, operation=" + operationMs + " ms, total=" + totalMs + " ms";
        }
    }

    public static final class UploadResult
    {
        public final String filename; public final long bytes; public final String sha256;
        public final long latencyMs; public final String stageSummary;
        UploadResult(String filename, long bytes, String sha256, long latencyMs, String stageSummary)
        { this.filename = filename; this.bytes = bytes; this.sha256 = sha256;
          this.latencyMs = latencyMs; this.stageSummary = stageSummary; }
    }
    public static final class TestResult
    {
        public final String temporaryFilename; public final long latencyMs;
        public final boolean cleanupVerified; public final String stageSummary;
        TestResult(String name, long latencyMs, boolean cleanupVerified, String stageSummary)
        { this.temporaryFilename = name; this.latencyMs = latencyMs;
          this.cleanupVerified = cleanupVerified; this.stageSummary = stageSummary; }
    }
}
