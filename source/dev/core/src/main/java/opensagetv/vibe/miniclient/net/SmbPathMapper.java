package opensagetv.vibe.miniclient.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Maps SageTV storage paths to credential-free SMB roots.
 *
 * The persisted form is one mapping per line:
 * {@code D:\Recordings\ => smb://server/Recordings/}. Blank lines and lines
 * beginning with {@code #} are ignored. Credentials are deliberately rejected;
 * they are configured separately and must never appear in logs or telemetry.
 */
public final class SmbPathMapper
{
    public static final String DELIMITER = "=>";

    private final List<Mapping> mappings;

    private SmbPathMapper(List<Mapping> mappings)
    {
        this.mappings = mappings;
    }

    public static SmbPathMapper parse(String configuredMappings)
    {
        List<Mapping> result = new ArrayList<Mapping>();
        if (configuredMappings != null)
        {
            String[] lines = configuredMappings.replace('\r', '\n').split("\\n");
            for (String rawLine : lines)
            {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int delimiter = line.indexOf(DELIMITER);
                if (delimiter <= 0 || delimiter == line.length() - DELIMITER.length())
                    throw new IllegalArgumentException("Invalid SMB mapping; expected '<SageTV prefix> => <smb:// root>'");
                String source = line.substring(0, delimiter).trim();
                String target = line.substring(delimiter + DELIMITER.length()).trim();
                result.add(new Mapping(source, target));
            }
        }
        Collections.sort(result, new Comparator<Mapping>()
        {
            @Override
            public int compare(Mapping left, Mapping right)
            {
                return Integer.compare(right.comparisonPrefix.length(), left.comparisonPrefix.length());
            }
        });
        return new SmbPathMapper(Collections.unmodifiableList(result));
    }

    public int size() { return mappings.size(); }

    public SmbMappedPath map(String sagePath)
    {
        String original = extractOriginalPath(sagePath);
        if (original == null || original.trim().isEmpty()) return null;

        for (Mapping mapping : mappings)
        {
            String comparison = mapping.caseInsensitive
                    ? normalizeSeparators(original).toLowerCase(Locale.US)
                    : normalizeSeparators(original);
            if (!comparison.startsWith(mapping.comparisonPrefix)) continue;
            String remainder = normalizeSeparators(original).substring(mapping.normalizedSource.length());
            return mapping.target.resolve(original, remainder);
        }

        if (original.startsWith("\\\\") || original.startsWith("//"))
            return mapUnc(original);
        return null;
    }

    public static String extractOriginalPath(String value)
    {
        if (value == null) return null;
        if (!value.regionMatches(true, 0, "stv://", 0, "stv://".length())) return value;
        int slash = value.indexOf('/', "stv://".length());
        if (slash < 0 || slash == value.length() - 1) return "";
        String path = value.substring(slash + 1);
        int fragment = path.indexOf('#');
        return fragment < 0 ? path : path.substring(0, fragment);
    }

    private static SmbMappedPath mapUnc(String original)
    {
        String normalized = normalizeSeparators(original);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] parts = normalized.split("/", 3);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
        String path = parts.length == 3 ? parts[2] : "";
        return new SmbMappedPath(original, parts[0], 445, parts[1], path);
    }

    private static String normalizeSeparators(String value)
    {
        return value.replace('\\', '/');
    }

    private static final class Mapping
    {
        final String normalizedSource;
        final String comparisonPrefix;
        final boolean caseInsensitive;
        final Target target;

        Mapping(String source, String targetRoot)
        {
            if (source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0)
                throw new IllegalArgumentException("Invalid newline in SMB source prefix");
            this.normalizedSource = normalizeSeparators(source);
            this.caseInsensitive = normalizedSource.matches("^[A-Za-z]:/.*") || normalizedSource.startsWith("//");
            this.comparisonPrefix = caseInsensitive
                    ? normalizedSource.toLowerCase(Locale.US) : normalizedSource;
            this.target = Target.parse(targetRoot);
        }
    }

    private static final class Target
    {
        final String server;
        final int port;
        final String share;
        final String rootPath;

        Target(String server, int port, String share, String rootPath)
        {
            this.server = server;
            this.port = port;
            this.share = share;
            this.rootPath = rootPath;
        }

        static Target parse(String value)
        {
            if (value == null || !value.regionMatches(true, 0, "smb://", 0, 6))
                throw new IllegalArgumentException("SMB mapping target must start with smb://");
            String remainder = value.substring(6).replace('\\', '/');
            int slash = remainder.indexOf('/');
            if (slash <= 0 || slash == remainder.length() - 1)
                throw new IllegalArgumentException("SMB mapping target must include a server and share");
            String authority = remainder.substring(0, slash);
            if (authority.indexOf('@') >= 0)
                throw new IllegalArgumentException("SMB credentials must not be embedded in mapping URLs");
            String targetPath = remainder.substring(slash + 1);
            while (targetPath.endsWith("/")) targetPath = targetPath.substring(0, targetPath.length() - 1);
            int nextSlash = targetPath.indexOf('/');
            String share = nextSlash < 0 ? targetPath : targetPath.substring(0, nextSlash);
            String root = nextSlash < 0 ? "" : targetPath.substring(nextSlash + 1);
            if (share.isEmpty()) throw new IllegalArgumentException("SMB mapping target share is empty");

            String server = authority;
            int port = 445;
            int colon = authority.lastIndexOf(':');
            if (colon > 0 && authority.indexOf(']') < colon)
            {
                server = authority.substring(0, colon);
                try { port = Integer.parseInt(authority.substring(colon + 1)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid SMB port", e); }
            }
            if (server.startsWith("[") && server.endsWith("]"))
                server = server.substring(1, server.length() - 1);
            if (server.isEmpty() || port < 1 || port > 65535)
                throw new IllegalArgumentException("Invalid SMB server or port");
            return new Target(server, port, share, root);
        }

        SmbMappedPath resolve(String original, String suffix)
        {
            String cleanSuffix = normalizeSeparators(suffix);
            while (cleanSuffix.startsWith("/")) cleanSuffix = cleanSuffix.substring(1);
            String joined;
            if (rootPath.isEmpty()) joined = cleanSuffix;
            else if (cleanSuffix.isEmpty()) joined = rootPath;
            else joined = rootPath + "/" + cleanSuffix;
            return new SmbMappedPath(original, server, port, share, joined);
        }
    }
}
