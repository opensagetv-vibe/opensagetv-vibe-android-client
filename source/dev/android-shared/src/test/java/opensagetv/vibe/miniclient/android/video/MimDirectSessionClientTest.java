package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MimDirectSessionClientTest
{
    @org.junit.Test public void explicitTranscodeFailureDoesNotBecomeOriginalFilePull(){
        org.junit.Assert.assertTrue(MimDirectSessionClient.blocksOriginalPullAfterStartFailure("transcode",false));
        org.junit.Assert.assertTrue(MimDirectSessionClient.blocksOriginalPullAfterStartFailure("transcode",true));
        org.junit.Assert.assertFalse(MimDirectSessionClient.blocksOriginalPullAfterStartFailure("copy",false));
        org.junit.Assert.assertTrue(MimDirectSessionClient.blocksOriginalPullAfterStartFailure("copy",true));
        org.junit.Assert.assertFalse(MimDirectSessionClient.blocksOriginalPullAfterStartFailure("",false));
    }
    @Test public void outputAndEncoderRevisionsAreExactScopedAndClosed(){
        for(String field:new String[]{"outputSettingsRevision","encoderPolicyRevision","hardwareBackendRevision","hlsBufferRevision"}){
            assertTrue(MimDirectSessionClient.supportsOutputRevision("{\"mimDirect\":{\""+field+"\":1}}",field));
            for(String invalid:new String[]{"0","2","10","1.5","\"1\"","null"})assertFalse(MimDirectSessionClient.supportsOutputRevision("{\"mimDirect\":{\""+field+"\":"+invalid+"}}",field));
            assertFalse(MimDirectSessionClient.supportsOutputRevision("{\"mimDirect\":{},\""+field+"\":1}",field));
        }
        assertFalse(MimDirectSessionClient.supportsOutputRevision("{}","invalid"));
    }
    @Test public void idleDiagnosticUsesTheConnectedServerWithoutAPlaybackLease(){
        opensagetv.vibe.miniclient.prefs.PrefStore prefs=(opensagetv.vibe.miniclient.prefs.PrefStore)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{opensagetv.vibe.miniclient.prefs.PrefStore.class},
            (proxy,method,args)->method.getName().equals("getString")?"31910":null);
        opensagetv.vibe.miniclient.ServerInfo server=new opensagetv.vibe.miniclient.ServerInfo();server.address="192.168.10.232";
        assertEquals("http://192.168.10.232:31910",MimDirectSessionClient.diagnosticBase(server,prefs));
        server.address="2001:db8::1";
        assertEquals("http://[2001:db8::1]:31910",MimDirectSessionClient.diagnosticBase(server,prefs));
        assertEquals("",MimDirectSessionClient.diagnosticBase(null,prefs));
        assertEquals("",MimDirectSessionClient.diagnosticBase(server,null));
    }
    @Test public void runtimeBitrateRevisionIsExactAndScoped(){
        assertTrue(MimDirectSessionClient.supportsRuntimeBitrate("{\"mimDirect\":{\"runtimeBitrateControlRevision\":1}}"));
        for(String revision:new String[]{"0","2","10","1.5","\"1\"","null"})
            assertFalse(MimDirectSessionClient.supportsRuntimeBitrate("{\"mimDirect\":{\"runtimeBitrateControlRevision\":"+revision+"}}"));
        assertFalse(MimDirectSessionClient.supportsRuntimeBitrate("{\"mimDirect\":{},\"runtimeBitrateControlRevision\":1}"));
    }
    @Test public void responsiveHlsIsScopedVersionedAndMustBeConfirmed() {
        String base="{\"mimDirect\":{\"contractVersion\":1,\"available\":true,\"modes\":[\"copy\",\"transcode\"],\"container\":\"hls-mpegts\"";
        assertFalse(MimDirectSessionClient.supportsResponsiveHls(base+"}}"));
        assertTrue(MimDirectSessionClient.supportsResponsiveHls(base+",\"responsiveHlsRevision\":1}}"));
        assertFalse(MimDirectSessionClient.supportsResponsiveHls(base+",\"responsiveHlsRevision\":10}}"));
        assertFalse(MimDirectSessionClient.supportsResponsiveHls(base+",\"responsiveHlsRevision\":1.5}}"));
        assertFalse(MimDirectSessionClient.supportsResponsiveHls(base+"},\"responsiveHlsRevision\":1}"));
        String accepted="{\"hlsProfile\":\"responsive\",\"segmentTargetMs\":1000,\"startupBurstSeconds\":6}";
        assertTrue(MimDirectSessionClient.acceptedResponsiveHls(accepted));
        assertTrue(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":6",":0")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":6",":60")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":6",":6.5")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":1000",":1000.5")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":1000",":2000")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace("responsive","legacy")));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls("{}"));
    }
    @Test public void responsiveReserveConfirmsExactBoundedRequestedPolicy() {
        String accepted="{\"hlsProfile\":\"responsive\",\"segmentTargetMs\":1000,\"startupBurstSeconds\":23,\"bufferAheadTargetMs\":20000}";
        assertTrue(MimDirectSessionClient.acceptedResponsiveHls(accepted,20000));
        assertTrue(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":23",":0"),20000));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted,10000));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted,20001));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted,-1));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":23",":24"),20000));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(":23",":23.5"),20000));
        assertFalse(MimDirectSessionClient.acceptedResponsiveHls(accepted.replace(",\"bufferAheadTargetMs\":20000",""),20000));
        String small=accepted.replace(":23",":6").replace(":20000",":1000");
        assertTrue(MimDirectSessionClient.acceptedResponsiveHls(small,1000));
    }
    @Test public void hlsRequiresScopedExactProviderContract() {
        String direct = "\"contractVersion\":1,\"available\":true,\"modes\":[\"copy\",\"transcode\"],\"container\":\"hls-mpegts\"";
        assertTrue(MimDirectSessionClient.supportsHls("{\"mimDirect\":{" + direct + "}}"));
        assertFalse(MimDirectSessionClient.supportsHls(null));
        assertFalse(MimDirectSessionClient.supportsHls("{" + direct + "}"));
        assertFalse(MimDirectSessionClient.supportsHls("{\"other\":{" + direct + "}}"));
        for (String invalid : new String[] {
                direct.replace("hls-mpegts", "mpegts"),
                direct.replace("\"container\":\"hls-mpegts\"", "\"other\":\"hls-mpegts\""),
                direct.replace("Version\":1", "Version\":10"),
                direct.replace("true", "false"),
                direct.replace("\"copy\",\"transcode\"", "\"copy\"") })
            assertFalse(MimDirectSessionClient.supportsHls("{\"mimDirect\":{" + invalid + "}}"));
    }

    @Test public void hlsLabelRequiresTheCurrentOwnedSessionAndClearsOnFallback() throws Exception {
        MimDirectSessionClient client = new MimDirectSessionClient(); Object owner = new Object();
        assertFalse(client.ownsHls(owner));
        for (String name : new String[] { "owner", "sessionToken", "hlsSelected" }) {
            java.lang.reflect.Field field = MimDirectSessionClient.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(client, "owner".equals(name) ? owner : "sessionToken".equals(name) ? "owned" : true);
        }
        assertTrue(client.ownsHls(owner));
        assertFalse(client.ownsHls(new Object()));
        client.useInPlaceStockFixedReconnect();
        assertFalse(client.ownsHls(owner));
    }
    @Test public void qualityBindingHoldsSourceClockUntilExactNewRenderer()throws Exception{
        MimDirectSessionClient client=new MimDirectSessionClient();Object owner=new Object();
        java.lang.reflect.Field identity=MimDirectSessionClient.class.getDeclaredField("owner");identity.setAccessible(true);identity.set(client,owner);
        java.lang.reflect.Field offset=MimDirectSessionClient.class.getDeclaredField("startOffsetMs");offset.setAccessible(true);offset.set(client,4000L);
        java.lang.reflect.Field hold=MimDirectSessionClient.class.getDeclaredField("qualityClockHoldMs");hold.setAccessible(true);hold.set(client,6250L);
        assertEquals(6250L,client.adjustPosition(owner,40000L));
        assertEquals(500L,client.adjustPosition(new Object(),500L));
        client.qualityRendererRestored(owner,1000L);assertEquals(6250L,client.adjustPosition(owner,40000L));
        client.qualityRendererRestored(owner,2251L);assertEquals(6251L,client.adjustPosition(owner,2251L));
    }
    @Test public void mediaHttpErrorsDoNotExposeResponseBodySecrets()
    {
        assertEquals("http_404_media_not_ready", MimDirectSessionClient.mediaResponseTagForDiagnostics(
                404, "{\"error\":\"media_not_ready\",\"token\":\"private\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("http_404_unknown_media", MimDirectSessionClient.mediaResponseTagForDiagnostics(
                404, "{\"error\":\"unknown_media\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("http_404_unclassified", MimDirectSessionClient.mediaResponseTagForDiagnostics(
                404, "{\"error\":\"private token or filename\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("http_unknown_unclassified", MimDirectSessionClient.mediaResponseTagForDiagnostics(-1, null));
        assertEquals("http_404_unclassified", MimDirectSessionClient.mediaResponseTagForDiagnostics(404, new byte[4097]));
    }
    @Test public void observedMediaSessionReportsOnlyClosedDiagnosticTags()
    {
        String base = "http://server:31910";
        assertEquals("current", MimDirectSessionClient.compareMediaUri(base, "new",
                base + "/v1/direct/media/new/seg_000001.ts"));
        assertEquals("current", MimDirectSessionClient.compareMediaUri(base, "new",
                base + "/v1/direct/media/new/stream.m3u8"));
        assertEquals("retired", MimDirectSessionClient.compareMediaUri(base, "new",
                base + "/v1/direct/media/old/seg_000001.ts"));
        assertEquals("foreign", MimDirectSessionClient.compareMediaUri(base, "new",
                "http://another:31910/v1/direct/media/new/seg_000001.ts"));
        assertEquals("foreign", MimDirectSessionClient.compareMediaUri(base, "new",
                base + "/ordinary.ts"));
        assertEquals("unavailable", MimDirectSessionClient.compareMediaUri(base, "new", ""));
        assertEquals("unavailable", MimDirectSessionClient.compareMediaUri(base, "new", "bad uri"));
        assertEquals("inactive", MimDirectSessionClient.compareMediaUri(base, "", base));
    }
    @Test public void restartRejectionDiagnosticsDoNotExposeResponseSecrets()
    {
        assertEquals("http_409_direct_caption_slot_unavailable",
                MimDirectSessionClient.restartRejectionTag(409,
                        "{\"error\":\"direct_caption_slot_unavailable\"}"));
        assertEquals("http_502_mim_direct_restart_failed",
                MimDirectSessionClient.restartRejectionTag(502,
                        "{\"error\":\"mim_direct_restart_failed\"}"));
        assertEquals("http_404_unknown_or_finished_session",
                MimDirectSessionClient.restartRejectionTag(404,
                        "{\"error\":\"unknown_or_finished_session\"}"));
        assertEquals("http_500_unclassified",
                MimDirectSessionClient.restartRejectionTag(500,
                        "{\"error\":\"private path or credential\",\"sessionToken\":\"secret\"}"));
        assertEquals("http_503_unclassified",
                MimDirectSessionClient.restartRejectionTag(503, "invalid reply"));
    }

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

    @Test public void captionClockUsesTheRestartedFfmpegTimeline()
    {
        assertEquals(12_000L, MimDirectSessionClient.relativeCaptionClock(
                42_000L, 30_000L));
        assertEquals(0L, MimDirectSessionClient.relativeCaptionClock(
                29_000L, 30_000L));
        assertEquals(12_000L, MimDirectSessionClient.relativeCaptionClock(
                12_000L, 0L));
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
