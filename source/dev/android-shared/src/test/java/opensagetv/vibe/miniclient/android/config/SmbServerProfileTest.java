package opensagetv.vibe.miniclient.android.config;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SmbServerProfileTest
{
    @Test public void endpointDefaultsTo445AndAcceptsExplicitPort()
    {
        SmbServerProfile.Endpoint standard = SmbServerProfile.parseEndpoint("media.local");
        assertEquals("media.local", standard.host);
        assertEquals(445, standard.port);
        SmbServerProfile.Endpoint custom = SmbServerProfile.parseEndpoint("192.168.10.175:446");
        assertEquals("192.168.10.175", custom.host);
        assertEquals(446, custom.port);
    }

    @Test public void endpointSupportsBracketedIpv6Port()
    {
        SmbServerProfile.Endpoint endpoint = SmbServerProfile.parseEndpoint("[fe80::1]:1445");
        assertEquals("fe80::1", endpoint.host);
        assertEquals(1445, endpoint.port);
    }

    @Test(expected = IllegalArgumentException.class)
    public void endpointRejectsOutOfRangePort()
    {
        SmbServerProfile.parseEndpoint("nas:70000");
    }

    @Test public void buildsRootAndNestedUrls()
    {
        SmbServerProfile profile = new SmbServerProfile("nas", "Media NAS", "192.168.10.175",
                445, "sagemedia", false, "sage", "secret".toCharArray(), "WORKGROUP");
        assertEquals("smb://192.168.10.175/sagemedia/", profile.directoryUrl(""));
        assertEquals("smb://192.168.10.175/sagemedia/config/client profiles/",
                profile.directoryUrl("/config\\client profiles/"));
        profile.clear();
        assertArrayEquals(new char[]{'\0','\0','\0','\0','\0','\0'}, profile.copyPassword());
    }

    @Test public void supportsCustomPortAndIpv6()
    {
        SmbServerProfile profile = new SmbServerProfile("ipv6", "IPv6 NAS", "fd00::175",
                1445, "media", true, "ignored", "ignored".toCharArray(), "ignored");
        assertEquals("smb://[fd00::175]:1445/media/tv/", profile.directoryUrl("tv"));
        assertEquals("", profile.getUsername());
        profile.clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSchemeInHost()
    {
        new SmbServerProfile("bad", "Bad", "smb://nas", 445, "media",
                true, "", new char[0], "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSharePathInsteadOfShareName()
    {
        new SmbServerProfile("bad", "Bad", "nas", 445, "media/videos",
                true, "", new char[0], "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFolderTraversal()
    {
        SmbServerProfile profile = new SmbServerProfile("nas", "NAS", "nas", 445, "media",
                true, "", new char[0], "");
        profile.directoryUrl("videos/../private");
    }
}
