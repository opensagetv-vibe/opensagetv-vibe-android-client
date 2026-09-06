package opensagetv.vibe.miniclient.android.config;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import opensagetv.vibe.miniclient.config.MiniClientProfile;
import opensagetv.vibe.miniclient.net.SmbMappedPath;
import opensagetv.vibe.miniclient.net.SmbPathMapper;

/** Bounded SMB2/SMB3 repository for named MiniClient configuration profiles. */
public final class SmbProfileRepository
{
    private final String directoryUrl;
    private final String username;
    private final char[] password;
    private final String domain;

    public SmbProfileRepository(String directoryUrl, String username, char[] password, String domain)
    {
        this.directoryUrl = directoryUrl == null ? "" : directoryUrl.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? new char[0] : password.clone();
        this.domain = domain == null ? "" : domain.trim();
    }

    public List<String> list() throws IOException
    {
        return withShare(new ShareOperation<List<String>>()
        {
            @Override public List<String> run(DiskShare share, SmbMappedPath location) throws IOException
            {
                if (!location.getPath().isEmpty() && !share.folderExists(smbPath(location.getPath())))
                    throw new IOException("SMB configuration directory does not exist");
                List<String> names = new ArrayList<String>();
                for (FileIdBothDirectoryInformation item : share.list(smbPath(location.getPath()), "*.profile"))
                {
                    String name = item.getFileName();
                    if (name == null || name.startsWith(".")) continue;
                    try
                    {
                        if (MiniClientProfile.fileName(name).equals(name)) names.add(name);
                    }
                    catch (IllegalArgumentException ignored) { }
                }
                Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                return names;
            }
        });
    }

    public MiniClientProfile load(final String profileName) throws IOException
    {
        final String fileName = MiniClientProfile.fileName(profileName);
        return withShare(new ShareOperation<MiniClientProfile>()
        {
            @Override public MiniClientProfile run(DiskShare share, SmbMappedPath location) throws IOException
            {
                String remote = join(location.getPath(), fileName);
                com.hierynomus.smbj.share.File file = null;
                try
                {
                    file = share.openFile(remote,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
                    long length = file.getFileInformation().getStandardInformation().getEndOfFile();
                    if (length <= 0 || length > MiniClientProfile.MAX_PROFILE_BYTES)
                        throw new IOException("Remote profile is empty or exceeds the 1 MiB limit");
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) length);
                    byte[] buffer = new byte[16 * 1024];
                    long position = 0;
                    while (position < length)
                    {
                        int requested = (int) Math.min(buffer.length, length - position);
                        int read = file.read(buffer, position, 0, requested);
                        if (read <= 0) throw new IOException("Remote profile ended before its declared size");
                        out.write(buffer, 0, read);
                        position += read;
                    }
                    MiniClientProfile profile = MiniClientProfile.parse(out.toByteArray());
                    if (!MiniClientProfile.fileName(profile.getName()).equals(fileName))
                        throw new IOException("Profile filename and embedded name do not match");
                    return profile;
                }
                catch (RuntimeException e) { throw new IOException("Unable to read SMB configuration profile", e); }
                finally { closeQuietly(file); }
            }
        });
    }

    public void save(final MiniClientProfile profile, final boolean overwrite) throws IOException
    {
        final byte[] contents = profile.serialize();
        final String finalName = MiniClientProfile.fileName(profile.getName());
        withShare(new ShareOperation<Void>()
        {
            @Override public Void run(DiskShare share, SmbMappedPath location) throws IOException
            {
                String root = smbPath(location.getPath());
                if (!root.isEmpty() && !share.folderExists(root))
                    throw new IOException("SMB configuration directory does not exist");
                String destination = join(root, finalName);
                if (!overwrite && share.fileExists(destination))
                    throw new ProfileAlreadyExistsException(finalName);
                String temporary = join(root, "." + finalName + ".tmp-" + UUID.randomUUID().toString());
                com.hierynomus.smbj.share.File file = null;
                try
                {
                    file = share.openFile(temporary,
                            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_CREATE,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
                    long written = file.write(contents, 0, 0, contents.length);
                    if (written != contents.length) throw new IOException("Short SMB profile write");
                    file.flush();
                    file.rename(destination, overwrite);
                    return null;
                }
                catch (ProfileAlreadyExistsException e) { throw e; }
                catch (RuntimeException e)
                {
                    throw new IOException(overwrite
                            ? "Unable to replace SMB configuration profile"
                            : "Unable to save SMB configuration profile (it may already exist)", e);
                }
                finally
                {
                    closeQuietly(file);
                    try { if (share.fileExists(temporary)) share.rm(temporary); }
                    catch (RuntimeException ignored) { }
                }
            }
        });
    }

    public void delete(final String profileName) throws IOException
    {
        final String fileName = MiniClientProfile.fileName(profileName);
        withShare(new ShareOperation<Void>()
        {
            @Override public Void run(DiskShare share, SmbMappedPath location) throws IOException
            {
                String target = join(location.getPath(), fileName);
                if (share.fileExists(target)) share.rm(target);
                return null;
            }
        });
    }

    public void clear()
    {
        Arrays.fill(password, '\0');
    }

    private <T> T withShare(ShareOperation<T> operation) throws IOException
    {
        if (directoryUrl.isEmpty()) throw new IOException("SMB configuration directory is not configured");
        SmbMappedPath location;
        try { location = SmbPathMapper.parse("/ => " + directoryUrl).map("/"); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid SMB configuration directory", e); }
        if (location == null) throw new IOException("Invalid SMB configuration directory");

        SMBClient client = null;
        Connection connection = null;
        Session session = null;
        DiskShare share = null;
        char[] secret = password.clone();
        try
        {
            SmbConfig config = SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .withSoTimeout(10, TimeUnit.SECONDS)
                    .build();
            client = new SMBClient(config);
            connection = client.connect(location.getServer(), location.getPort());
            AuthenticationContext auth = username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(username, secret, domain);
            session = connection.authenticate(auth);
            share = (DiskShare) session.connectShare(location.getShare());
            return operation.run(share, location);
        }
        catch (IOException e) { throw e; }
        catch (RuntimeException e) { throw new IOException("SMB configuration share operation failed", e); }
        finally
        {
            Arrays.fill(secret, '\0');
            closeQuietly(share);
            closeQuietly(session);
            closeQuietly(connection);
            closeQuietly(client);
        }
    }

    private static String join(String root, String name)
    {
        String cleanRoot = smbPath(root);
        return cleanRoot.isEmpty() ? name : cleanRoot + "\\" + name;
    }

    private static String smbPath(String path)
    {
        if (path == null) return "";
        String value = path.replace('/', '\\');
        while (value.startsWith("\\")) value = value.substring(1);
        while (value.endsWith("\\")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static void closeQuietly(AutoCloseable value)
    {
        if (value == null) return;
        try { value.close(); }
        catch (Exception ignored) { }
    }

    private interface ShareOperation<T> { T run(DiskShare share, SmbMappedPath location) throws IOException; }

    public static final class ProfileAlreadyExistsException extends IOException
    {
        public ProfileAlreadyExistsException(String name) { super("Profile already exists: " + name); }
    }
}
