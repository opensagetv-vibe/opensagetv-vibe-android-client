package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import static org.junit.Assert.*;

public class SmbPathMapperTest
{
    @Test
    public void mapsWindowsPathCaseInsensitively()
    {
        SmbMappedPath mapped = SmbPathMapper.parse(
                "D:\\Recordings\\ => smb://192.168.10.20/Recordings/")
                .map("d:\\recordings\\TV\\Show-1.ts");
        assertNotNull(mapped);
        assertEquals("192.168.10.20", mapped.getServer());
        assertEquals("Recordings", mapped.getShare());
        assertEquals("TV/Show-1.ts", mapped.getPath());
        assertEquals("smb://192.168.10.20/Recordings/TV/Show-1.ts", mapped.getRedactedUrl());
    }

    @Test
    public void usesLongestPrefix()
    {
        SmbPathMapper mapper = SmbPathMapper.parse(
                "D:\\ => smb://nas/general/\n" +
                "D:\\Recordings\\ => smb://nas/tv/");
        SmbMappedPath mapped = mapper.map("D:\\Recordings\\News\\item.ts");
        assertNotNull(mapped);
        assertEquals("tv", mapped.getShare());
        assertEquals("News/item.ts", mapped.getPath());
    }

    @Test
    public void mapsUncWithoutConfiguredMapping()
    {
        SmbMappedPath mapped = SmbPathMapper.parse("").map("\\\\NAS\\Recordings\\TV\\Show.ts");
        assertNotNull(mapped);
        assertEquals("NAS", mapped.getServer());
        assertEquals("Recordings", mapped.getShare());
        assertEquals("TV/Show.ts", mapped.getPath());
    }

    @Test
    public void stripsStvWrapperAndMetadataFragment()
    {
        SmbMappedPath mapped = SmbPathMapper.parse(
                "/var/media/ => smb://tower/media/")
                .map("stv://192.168.10.232//var/media/tv/Show.ts#vibe=1");
        assertNotNull(mapped);
        assertEquals("tv/Show.ts", mapped.getPath());
    }

    @Test
    public void returnsNullWhenNoMappingMatches()
    {
        assertNull(SmbPathMapper.parse("D:\\TV\\ => smb://nas/tv/")
                .map("E:\\TV\\Show.ts"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCredentialsInMappingUrl()
    {
        SmbPathMapper.parse("D:\\ => smb://user:secret@nas/share/");
    }
}
