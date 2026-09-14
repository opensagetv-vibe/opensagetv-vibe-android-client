package opensagetv.vibe.miniclient.android.config;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Read-only SMB2/SMB3 folder enumerator rooted at one configured server share. */
public final class SmbFolderBrowserRepository
{
    private final SmbServerProfile profile;

    public SmbFolderBrowserRepository(SmbServerProfile profile)
    {
        if (profile == null) throw new IllegalArgumentException("SMB server profile is required");
        this.profile = profile;
    }

    public List<String> list(String folder) throws IOException
    {
        String root = smbPath(SmbServerProfile.normalizeFolder(folder));
        SMBClient client = null;
        Connection connection = null;
        Session session = null;
        DiskShare share = null;
        char[] password = profile.copyPassword();
        try
        {
            SmbConfig config = SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .withSoTimeout(10, TimeUnit.SECONDS).build();
            client = new SMBClient(config);
            connection = client.connect(profile.getHost(), profile.getPort());
            AuthenticationContext auth = profile.isAnonymous()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(profile.getUsername(), password, profile.getDomain());
            session = connection.authenticate(auth);
            share = (DiskShare) session.connectShare(profile.getShare());
            if (!root.isEmpty() && !share.folderExists(root))
                throw new IOException("The selected SMB folder no longer exists");
            ArrayList<String> folders = new ArrayList<String>();
            long directoryFlag = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue();
            for (FileIdBothDirectoryInformation item : share.list(root))
            {
                String name = item.getFileName();
                if (name == null || name.equals(".") || name.equals("..")) continue;
                if ((item.getFileAttributes() & directoryFlag) != 0) folders.add(name);
            }
            Collections.sort(folders, new Comparator<String>()
            {
                @Override public int compare(String left, String right)
                { return left.compareToIgnoreCase(right); }
            });
            return folders;
        }
        catch (IOException e) { throw e; }
        catch (Exception e)
        {
            String message = e.getMessage();
            throw new IOException("SMB folder browse failed"
                    + (message == null || message.trim().isEmpty() ? "" : ": " + message), e);
        }
        finally
        {
            Arrays.fill(password, '\0');
            closeQuietly(share); closeQuietly(session); closeQuietly(connection); closeQuietly(client);
        }
    }

    public void clear() { profile.clear(); }

    private static String smbPath(String value) { return value.replace('/', '\\'); }
    private static void closeQuietly(AutoCloseable value)
    { if (value != null) try { value.close(); } catch (Exception ignored) { } }
}
