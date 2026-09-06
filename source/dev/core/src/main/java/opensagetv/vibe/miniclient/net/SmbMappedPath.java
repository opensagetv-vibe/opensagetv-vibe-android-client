package opensagetv.vibe.miniclient.net;

/** Immutable, credential-free SMB target produced by {@link SmbPathMapper}. */
public final class SmbMappedPath
{
    private final String originalPath;
    private final String server;
    private final int port;
    private final String share;
    private final String path;

    SmbMappedPath(String originalPath, String server, int port, String share, String path)
    {
        this.originalPath = originalPath;
        this.server = server;
        this.port = port;
        this.share = share;
        this.path = path;
    }

    public String getOriginalPath() { return originalPath; }
    public String getServer() { return server; }
    public int getPort() { return port; }
    public String getShare() { return share; }
    public String getPath() { return path; }

    public String getRedactedUrl()
    {
        StringBuilder value = new StringBuilder("smb://").append(server);
        if (port != 445) value.append(':').append(port);
        value.append('/').append(share);
        if (!path.isEmpty()) value.append('/').append(path.replace('\\', '/'));
        return value.toString();
    }
}
