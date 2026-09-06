package opensagetv.vibe.miniclient.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

public class MiniClientProfileTest
{
    @Test
    public void roundTripsSettingsAndSeparatesClientIds() throws Exception
    {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("streaming_mode", "smb_auto");
        values.put("disable_sleep", true);
        values.put("count", 7);
        values.put("long", 9L);
        values.put("float", 1.5f);
        values.put("set", new HashSet<String>(Arrays.asList("two", "one")));
        values.put("clientid", "44:45:56:30:30:31");
        values.put("servers/Test/mac", "44:45:56:30:30:32");
        values.put("smb_direct/password", "must-not-leak");
        values.put("servers/Test/auth_block", "must-not-leak");

        MiniClientProfile parsed = MiniClientProfile.parse(
                MiniClientProfile.create("living-room", 1234L, values).serialize());

        assertEquals("living-room", parsed.getName());
        assertEquals("smb_auto", parsed.getSettings().get("streaming_mode"));
        assertEquals(true, parsed.getSettings().get("disable_sleep"));
        assertEquals(new HashSet<String>(Arrays.asList("one", "two")), parsed.getSettings().get("set"));
        assertEquals(2, parsed.getClientIds().size());
        assertFalse(parsed.getSettings().containsKey("smb_direct/password"));
        assertFalse(new String(parsed.serialize(), StandardCharsets.UTF_8).contains("must-not-leak"));
    }

    @Test(expected = IOException.class)
    public void rejectsTamperedChecksum() throws Exception
    {
        byte[] data = MiniClientProfile.create("test", 1L, CollectionsHelper.one("key", "value")).serialize();
        String changed = new String(data, StandardCharsets.UTF_8).replace("value", "other");
        MiniClientProfile.parse(changed.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IOException.class)
    public void rejectsIncompatibleSchemaEvenWithValidLookingContent() throws Exception
    {
        byte[] data = MiniClientProfile.create("test", 1L, CollectionsHelper.one("key", "value")).serialize();
        String original = new String(data, StandardCharsets.UTF_8);
        MiniClientProfile.parse(withValidChecksum(original.replace("schema=1", "schema=2")));
    }

    @Test(expected = IOException.class)
    public void rejectsForbiddenCredentialWithValidChecksum() throws Exception
    {
        byte[] data = MiniClientProfile.create("test", 1L, CollectionsHelper.one("key", "value")).serialize();
        String original = new String(data, StandardCharsets.UTF_8);
        String changed = original.replace("setting.key=s:value\n",
                "setting.smb_direct%2Fpassword=s:must-not-import\n");
        MiniClientProfile.parse(withValidChecksum(changed));
    }

    @Test(expected = IOException.class)
    public void rejectsPartialProfileWithoutChecksum() throws Exception
    {
        MiniClientProfile.parse(("format=" + MiniClientProfile.FORMAT + "\n"
                + "schema=1\nname=test\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsafeFileName()
    {
        MiniClientProfile.fileName("../escape");
    }

    @Test public void normalizesFileExtension()
    {
        assertEquals("family-room.profile", MiniClientProfile.fileName("family-room"));
        assertEquals("family-room.profile", MiniClientProfile.fileName("family-room.profile"));
    }

    private static byte[] withValidChecksum(String serialized) throws Exception
    {
        int checksumLine = serialized.lastIndexOf("sha256=");
        assertTrue(checksumLine >= 0);
        String body = serialized.substring(0, checksumLine);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder checksum = new StringBuilder(64);
        for (byte value : digest) checksum.append(String.format(Locale.US, "%02x", value & 0xff));
        return (body + "sha256=" + checksum + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static final class CollectionsHelper
    {
        static Map<String, Object> one(String key, Object value)
        {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put(key, value);
            return result;
        }
    }
}
