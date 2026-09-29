package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MimDirectSessionClientTest
{
    @Test
    public void normalizesDeinterlacePolicy()
    {
        assertEquals("auto", MimDirectSessionClient.normalizedDeinterlace(null));
        assertEquals("auto", MimDirectSessionClient.normalizedDeinterlace("unknown"));
        assertEquals("on", MimDirectSessionClient.normalizedDeinterlace(" ON "));
        assertEquals("off", MimDirectSessionClient.normalizedDeinterlace("off"));
    }

    @Test public void extractsOnlyServerLocalSourcePaths()
    {
        assertEquals("/var/media/video.ts",
                MimDirectSessionClient.serverPath("/var/media/video.ts"));
        assertEquals("/var/media/video.ts",
                MimDirectSessionClient.serverPath("file:///var/media/video.ts"));
        assertEquals("/var/media/video.ts",
                MimDirectSessionClient.serverPath(
                        "stv://192.168.10.232/file:///var/media/video.ts"));
        assertEquals("V:\\OpenSageTV_Vibe_Tests\\video.ts",
                MimDirectSessionClient.serverPath(
                        "stv://192.168.10.185/V:\\OpenSageTV_Vibe_Tests\\video.ts"));
        assertEquals("V:/OpenSageTV_Vibe_Tests/video.ts",
                MimDirectSessionClient.serverPath(
                        "stv://192.168.10.185/file:/V:/OpenSageTV_Vibe_Tests/video.ts"));
        assertEquals("/var/media/video.ts",
                MimDirectSessionClient.serverPath(
                        "stv://192.168.10.232//var/media/video.ts"));
        assertEquals("C:\\Media\\video.ts",
                MimDirectSessionClient.serverPath("C:\\Media\\video.ts"));
        assertEquals("", MimDirectSessionClient.serverPath("push:"));
        assertEquals("", MimDirectSessionClient.serverPath(
                "http://192.168.10.232:7818/video.ts"));
    }

    @Test public void lifecycleStopCanForceReleaseOwnedSession()
    {
        Object owner = new Object();
        assertTrue(MimDirectSessionClient.canRelease(owner, null));
        assertTrue(MimDirectSessionClient.canRelease(owner, owner));
        assertFalse(MimDirectSessionClient.canRelease(owner, new Object()));
        assertTrue(MimDirectSessionClient.canRelease(null, new Object()));
    }

    @Test public void directStartPreservesGrowingMediaState() throws Exception
    {
        String growing = MimDirectSessionClient.startRequestPath(
                "/var/media/live.ts", "transcode", "off", true);
        assertTrue(growing.contains("mode=transcode"));
        assertTrue(growing.contains("deinterlace=off"));
        assertTrue(growing.contains("active=true"));
        assertTrue(growing.endsWith("startMs=0"));
        assertTrue(MimDirectSessionClient.startRequestPath(
                "/var/media/complete.ts", "copy", "auto", false)
                .contains("active=false"));
    }

    @Test public void onlyBoundedNonNegativeStartupSeekAgesAreAccepted()
    {
        assertTrue(MimDirectSessionClient.isStartupSeekAge(0L));
        assertTrue(MimDirectSessionClient.isStartupSeekAge(5_000_000_000L));
        assertFalse(MimDirectSessionClient.isStartupSeekAge(-1L));
        assertFalse(MimDirectSessionClient.isStartupSeekAge(5_000_000_001L));
    }

    @Test public void parsesEffectiveDirectSeekOffset()
    {
        assertEquals(56000L, MimDirectSessionClient.longValue(
                "{\"requestedStartMs\":86400000,\"startMs\":56000}",
                "startMs", -1L));
        assertEquals(7L, MimDirectSessionClient.longValue("{}", "startMs", 7L));
    }

    @Test public void legacyIjkRetainsCopyButFallsBackFromDirectTranscode()
    {
        assertTrue(MimDirectSessionClient.supportsMode(
                PlayerBackend.IJKPLAYER, "copy"));
        assertFalse(MimDirectSessionClient.supportsMode(
                PlayerBackend.IJKPLAYER, "transcode"));
        assertTrue(MimDirectSessionClient.supportsMode(
                PlayerBackend.EXOPLAYER, "transcode"));
        assertTrue(MimDirectSessionClient.supportsMode(
                PlayerBackend.MEDIA3, "transcode"));
        assertTrue(MimDirectSessionClient.supportsMode(
                PlayerBackend.GSYPLAYER, "transcode"));
        assertFalse(MimDirectSessionClient.supportsPlaybackOwnerClass(
                "opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl",
                "transcode"));
        assertTrue(MimDirectSessionClient.supportsPlaybackOwnerClass(
                "opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl",
                "copy"));
        assertTrue(MimDirectSessionClient.supportsPlaybackOwnerClass(
                "opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl",
                "transcode"));
    }

    @Test public void onlyDirectStartFailureCanRequestStockFixedReconnect()
    {
        assertTrue(MimDirectSessionClient.lateFallbackStateAllowsReconnect(
                "start_failed_pull_fallback"));
        assertFalse(MimDirectSessionClient.lateFallbackStateAllowsReconnect(
                "active_transcode"));
        assertFalse(MimDirectSessionClient.lateFallbackStateAllowsReconnect(
                "late_failure_stock_fixed_reconnect_requested"));
        assertFalse(MimDirectSessionClient.lateFallbackStateAllowsReconnect(null));
    }

    @Test public void debugPullFailureIsBoundedToTheInjectedDirectFailure()
    {
        MimDirectSessionClient client = new MimDirectSessionClient();
        String original = "stv://server/video.ts";
        assertEquals(original, client.debugFallbackPullUrl(original));
        assertTrue(client.setDebugLateFallbackFailure(true).contains("armed=true"));
        // The Pull URL is not changed before the Direct-start failure state.
        assertEquals(original, client.debugFallbackPullUrl(original));
        assertTrue(client.setDebugLateFallbackFailure(false).contains("armed=false"));
        assertEquals(original, client.debugFallbackPullUrl(original));
    }

    @Test public void inPlaceFallbackRetiresTheDirectEndpoint()
    {
        MimDirectSessionClient client = new MimDirectSessionClient();
        client.useInPlaceStockFixedReconnect();
        assertEquals("late_failure_stock_fixed_reconnect",
                client.stateForDiagnostics());
        assertEquals(null, client.open(new Object(), "/video.ts"));
    }
}
