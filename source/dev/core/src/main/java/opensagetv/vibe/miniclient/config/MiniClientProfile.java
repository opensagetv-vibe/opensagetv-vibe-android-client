package opensagetv.vibe.miniclient.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Versioned, checksummed MiniClient settings profile.
 *
 * Credentials are rejected rather than merely omitted during import. Client ID
 * values are retained in a separate map so callers cannot accidentally apply
 * them with ordinary settings.
 */
public final class MiniClientProfile
{
    public static final String FORMAT = "opensagetv-vibe-miniclient-profile";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PROFILE_BYTES = 1024 * 1024;

    private final String name;
    private final long createdUtcMs;
    private final Map<String, Object> settings;
    private final Map<String, Object> clientIds;

    private MiniClientProfile(String name, long createdUtcMs,
                              Map<String, Object> settings,
                              Map<String, Object> clientIds)
    {
        this.name = name;
        this.createdUtcMs = createdUtcMs;
        this.settings = Collections.unmodifiableMap(settings);
        this.clientIds = Collections.unmodifiableMap(clientIds);
    }

    public static MiniClientProfile create(String name, long createdUtcMs,
                                           Map<String, ?> source)
    {
        validateName(name);
        LinkedHashMap<String, Object> settings = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> clientIds = new LinkedHashMap<String, Object>();
        if (source != null)
        {
            List<String> keys = new ArrayList<String>(source.keySet());
            Collections.sort(keys);
            for (String key : keys)
            {
                if (key == null || key.isEmpty() || isSensitiveKey(key)) continue;
                Object value = copySupportedValue(source.get(key));
                if (value == null) continue;
                if (isClientIdKey(key)) clientIds.put(key, value);
                else settings.put(key, value);
            }
        }
        return new MiniClientProfile(name, createdUtcMs, settings, clientIds);
    }

    public static MiniClientProfile parse(byte[] bytes) throws IOException
    {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PROFILE_BYTES)
            throw new IOException("Profile is empty or exceeds the 1 MiB limit");
        String text = new String(bytes, StandardCharsets.UTF_8);
        int checksumLine = text.lastIndexOf("\nsha256=");
        if (checksumLine < 0 || text.indexOf('\r') >= 0)
            throw new IOException("Profile checksum line or canonical line endings are invalid");
        String body = text.substring(0, checksumLine + 1);
        String expected = text.substring(checksumLine + "\nsha256=".length()).trim();
        if (!expected.matches("[0-9a-f]{64}") || !constantTimeEquals(expected, sha256(body)))
            throw new IOException("Profile checksum does not match");

        String format = null;
        Integer schema = null;
        String name = null;
        Long created = null;
        LinkedHashMap<String, Object> settings = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> clientIds = new LinkedHashMap<String, Object>();
        for (String line : body.split("\n"))
        {
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Malformed profile line");
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1);
            try
            {
                if ("format".equals(key)) format = decode(value);
                else if ("schema".equals(key)) schema = Integer.valueOf(value);
                else if ("name".equals(key)) name = decode(value);
                else if ("createdUtcMs".equals(key)) created = Long.valueOf(value);
                else if (key.startsWith("setting."))
                {
                    String settingKey = decode(key.substring("setting.".length()));
                    if (settings.containsKey(settingKey) || clientIds.containsKey(settingKey))
                        throw new IOException("Duplicate setting in profile");
                    if (isSensitiveKey(settingKey))
                        throw new IOException("Profile contains a forbidden credential setting");
                    Object decoded = decodeValue(value);
                    if (isClientIdKey(settingKey)) clientIds.put(settingKey, decoded);
                    else settings.put(settingKey, decoded);
                }
                else throw new IOException("Unknown profile field");
            }
            catch (NumberFormatException e)
            {
                throw new IOException("Invalid numeric profile field", e);
            }
        }
        if (!FORMAT.equals(format)) throw new IOException("Unsupported profile format");
        if (schema == null || schema != SCHEMA_VERSION)
            throw new IOException("Unsupported profile schema version");
        if (name == null || created == null) throw new IOException("Profile metadata is incomplete");
        validateName(name);
        return new MiniClientProfile(name, created, settings, clientIds);
    }

    public byte[] serialize() throws IOException
    {
        StringBuilder body = new StringBuilder();
        body.append("format=").append(encode(FORMAT)).append('\n');
        body.append("schema=").append(SCHEMA_VERSION).append('\n');
        body.append("name=").append(encode(name)).append('\n');
        body.append("createdUtcMs=").append(createdUtcMs).append('\n');
        appendSettings(body, settings);
        appendSettings(body, clientIds);
        String checksum = sha256(body.toString());
        body.append("sha256=").append(checksum).append('\n');
        byte[] result = body.toString().getBytes(StandardCharsets.UTF_8);
        if (result.length > MAX_PROFILE_BYTES) throw new IOException("Profile exceeds the 1 MiB limit");
        return result;
    }

    public String getName() { return name; }
    public long getCreatedUtcMs() { return createdUtcMs; }
    public Map<String, Object> getSettings() { return settings; }
    public Map<String, Object> getClientIds() { return clientIds; }
    public boolean hasClientIds() { return !clientIds.isEmpty(); }

    public static boolean isClientIdKey(String key)
    {
        String lower = key == null ? "" : key.toLowerCase(Locale.US);
        return "clientid".equals(lower) || (lower.startsWith("servers/") && lower.endsWith("/mac"));
    }

    public static boolean isSensitiveKey(String key)
    {
        String lower = key == null ? "" : key.toLowerCase(Locale.US);
        return lower.endsWith("/auth_block")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("secret")
                || lower.contains("token")
                || "smb_direct/username".equals(lower)
                || "smb_direct/domain".equals(lower)
                || lower.startsWith("smb_profiles/auth/");
    }

    public static String fileName(String name)
    {
        validateName(name);
        return name.endsWith(".profile") ? name : name + ".profile";
    }

    private static void validateName(String name)
    {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Profile name must be 1-64 letters, numbers, dot, underscore, or dash");
    }

    private static Object copySupportedValue(Object value)
    {
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Float) return value;
        if (value instanceof Set)
        {
            TreeSet<String> copy = new TreeSet<String>();
            for (Object item : (Set<?>) value)
            {
                if (!(item instanceof String)) return null;
                copy.add((String) item);
            }
            return Collections.unmodifiableSet(copy);
        }
        return null;
    }

    private static void appendSettings(StringBuilder body, Map<String, Object> values) throws IOException
    {
        List<Map.Entry<String, Object>> entries = new ArrayList<Map.Entry<String, Object>>(values.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Object>>()
        {
            @Override public int compare(Map.Entry<String, Object> a, Map.Entry<String, Object> b)
            { return a.getKey().compareTo(b.getKey()); }
        });
        for (Map.Entry<String, Object> entry : entries)
            body.append("setting.").append(encode(entry.getKey())).append('=')
                    .append(encodeValue(entry.getValue())).append('\n');
    }

    private static String encodeValue(Object value) throws IOException
    {
        if (value instanceof String) return "s:" + encode((String) value);
        if (value instanceof Boolean) return "b:" + value;
        if (value instanceof Integer) return "i:" + value;
        if (value instanceof Long) return "l:" + value;
        if (value instanceof Float) return "f:" + value;
        if (value instanceof Set)
        {
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            boolean first = true;
            for (Object item : (Set<?>) value)
            {
                if (!first) joined.write(0);
                byte[] encoded = ((String) item).getBytes(StandardCharsets.UTF_8);
                joined.write(encoded, 0, encoded.length);
                first = false;
            }
            return "x:" + encode(new String(joined.toByteArray(), StandardCharsets.UTF_8));
        }
        throw new IOException("Unsupported preference value type");
    }

    private static Object decodeValue(String encoded) throws IOException
    {
        if (encoded.length() < 2 || encoded.charAt(1) != ':') throw new IOException("Invalid setting type");
        String value = encoded.substring(2);
        try
        {
            switch (encoded.charAt(0))
            {
                case 's': return decode(value);
                case 'b':
                    if (!"true".equals(value) && !"false".equals(value)) throw new IOException("Invalid boolean");
                    return Boolean.valueOf(value);
                case 'i': return Integer.valueOf(value);
                case 'l': return Long.valueOf(value);
                case 'f': return Float.valueOf(value);
                case 'x':
                    String joined = decode(value);
                    TreeSet<String> set = new TreeSet<String>();
                    if (!joined.isEmpty()) Collections.addAll(set, joined.split("\\u0000", -1));
                    return Collections.unmodifiableSet(set);
                default: throw new IOException("Unsupported setting type");
            }
        }
        catch (NumberFormatException e) { throw new IOException("Invalid setting value", e); }
    }

    private static String encode(String value)
    {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    private static String decode(String value) throws IOException
    {
        try { return URLDecoder.decode(value, "UTF-8"); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid profile escaping", e); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    private static String sha256(String value) throws IOException
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
    }

    private static boolean constantTimeEquals(String a, String b)
    {
        if (a.length() != b.length()) return false;
        int mismatch = 0;
        for (int i = 0; i < a.length(); i++) mismatch |= a.charAt(i) ^ b.charAt(i);
        return mismatch == 0;
    }
}
