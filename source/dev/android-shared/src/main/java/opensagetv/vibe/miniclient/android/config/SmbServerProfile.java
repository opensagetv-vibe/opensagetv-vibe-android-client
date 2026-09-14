package opensagetv.vibe.miniclient.android.config;

import java.util.Arrays;
import java.util.Locale;

/** One reusable SMB2/SMB3 server/share and its device-local authentication. */
public final class SmbServerProfile
{
    private final String id;
    private final String name;
    private final String host;
    private final int port;
    private final String share;
    private final boolean anonymous;
    private final String username;
    private final char[] password;
    private final String domain;

    public SmbServerProfile(String id, String name, String host, int port, String share,
                            boolean anonymous, String username, char[] password, String domain)
    {
        this.id = requiredId(id);
        this.name = requiredName(name);
        this.host = requiredHost(host);
        this.port = requiredPort(port);
        this.share = requiredShare(share);
        this.anonymous = anonymous;
        this.username = anonymous ? "" : clean(username);
        this.password = anonymous || password == null ? new char[0] : password.clone();
        this.domain = anonymous ? "" : clean(domain);
        if (!anonymous && this.username.isEmpty())
            throw new IllegalArgumentException("SMB username is required for credential authentication");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getShare() { return share; }
    public boolean isAnonymous() { return anonymous; }
    public String getUsername() { return username; }
    public char[] copyPassword() { return password.clone(); }
    public String getDomain() { return domain; }

    public String endpoint()
    {
        String value = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return port == 445 ? value : value + ":" + port;
    }

    /** Parses host[:port], with port 445 as the default and bracketed IPv6 support. */
    public static Endpoint parseEndpoint(String value)
    {
        String endpoint = clean(value);
        String parsedHost;
        int parsedPort = 445;
        if (endpoint.startsWith("["))
        {
            int close = endpoint.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("Bracketed IPv6 server is missing ]");
            parsedHost = endpoint.substring(1, close);
            String suffix = endpoint.substring(close + 1);
            if (!suffix.isEmpty())
            {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Invalid SMB server or port");
                parsedPort = parsePort(suffix.substring(1));
            }
        }
        else
        {
            int firstColon = endpoint.indexOf(':');
            int lastColon = endpoint.lastIndexOf(':');
            if (firstColon >= 0 && firstColon == lastColon)
            {
                parsedHost = endpoint.substring(0, lastColon);
                parsedPort = parsePort(endpoint.substring(lastColon + 1));
            }
            else parsedHost = endpoint;
        }
        parsedHost = requiredHost(parsedHost);
        return new Endpoint(parsedHost, requiredPort(parsedPort));
    }

    private static int parsePort(String value)
    {
        try { return requiredPort(Integer.parseInt(clean(value))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("SMB port must be 1-65535"); }
    }

    public static final class Endpoint
    {
        public final String host;
        public final int port;

        private Endpoint(String host, int port)
        {
            this.host = host;
            this.port = port;
        }
    }

    public String directoryUrl(String folder)
    {
        StringBuilder value = new StringBuilder("smb://");
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) value.append('[').append(host).append(']');
        else value.append(host);
        if (port != 445) value.append(':').append(port);
        value.append('/').append(share);
        String cleanFolder = normalizeFolder(folder);
        if (!cleanFolder.isEmpty()) value.append('/').append(cleanFolder);
        value.append('/');
        return value.toString();
    }

    public void clear() { Arrays.fill(password, '\0'); }

    static String normalizeFolder(String folder)
    {
        String value = clean(folder).replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.equals(".") || value.equals("..") || value.contains("/../")
                || value.startsWith("../") || value.endsWith("/.."))
            throw new IllegalArgumentException("SMB folder cannot contain parent traversal");
        return value;
    }

    private static String requiredId(String value)
    {
        String clean = clean(value);
        if (!clean.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid SMB server profile ID");
        return clean;
    }

    private static String requiredName(String value)
    {
        String clean = clean(value);
        if (clean.isEmpty() || clean.length() > 64)
            throw new IllegalArgumentException("SMB server name must be 1-64 characters");
        return clean;
    }

    private static String requiredHost(String value)
    {
        String clean = clean(value);
        if (clean.startsWith("[") && clean.endsWith("]")) clean = clean.substring(1, clean.length() - 1);
        if (clean.isEmpty() || clean.regionMatches(true, 0, "smb://", 0, 6)
                || clean.indexOf('/') >= 0 || clean.indexOf('\\') >= 0
                || clean.matches(".*\\s+.*"))
            throw new IllegalArgumentException("Enter only the SMB server hostname or IP address");
        return clean.toLowerCase(Locale.US);
    }

    private static int requiredPort(int value)
    {
        if (value < 1 || value > 65535) throw new IllegalArgumentException("SMB port must be 1-65535");
        return value;
    }

    private static String requiredShare(String value)
    {
        String clean = clean(value);
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")
                || clean.indexOf('/') >= 0 || clean.indexOf('\\') >= 0)
            throw new IllegalArgumentException("Enter one SMB share name");
        return clean;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
