package opensagetv.vibe.miniclient.android.diagnostics;

import java.util.regex.Pattern;

/** Redacts support-bundle text before it is persisted or leaves the device. */
public final class DiagnosticRedactor
{
    private static final Pattern SMB_CREDENTIALS = Pattern.compile(
            "(?i)(smb://)[^/@\\s]+@");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_ -]?key|authorization)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern CLIENT_ID = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b");
    private static final Pattern CLIENT_CONTEXT = Pattern.compile(
            "(?i)(clientId|configuredClientId|uiContextHint)(=)([0-9a-f:]{12,17})");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})(?:\\.(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})){3}(?![0-9])");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)(?:[a-z]:\\\\|\\\\\\\\)[^\\r\\n|;]+(?:\\\\[^\\r\\n|;]+)+");
    private static final Pattern UNIX_MEDIA_PATH = Pattern.compile(
            "(?i)(?:/var/media|/mnt/(?:user|disk[0-9]+)|/media)/[^\\r\\n|;]+" );

    private DiagnosticRedactor() { }

    public static String redact(String text)
    {
        if (text == null || text.isEmpty()) return "";
        String value = SMB_CREDENTIALS.matcher(text).replaceAll("$1[REDACTED]@");
        value = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1$2[REDACTED]");
        value = CLIENT_CONTEXT.matcher(value).replaceAll("$1$2[CLIENT_ID_REDACTED]");
        value = CLIENT_ID.matcher(value).replaceAll("[CLIENT_ID_REDACTED]");
        value = IPV4.matcher(value).replaceAll("[SERVER_ADDRESS_REDACTED]");
        value = WINDOWS_PATH.matcher(value).replaceAll("[MEDIA_PATH_REDACTED]");
        return UNIX_MEDIA_PATH.matcher(value).replaceAll("[MEDIA_PATH_REDACTED]");
    }
}
