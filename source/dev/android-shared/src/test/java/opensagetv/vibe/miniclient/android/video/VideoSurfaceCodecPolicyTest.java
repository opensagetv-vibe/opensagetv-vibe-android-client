package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoSurfaceCodecPolicyTest {
    @Test public void legacyMiniMxMpeg2ReplacementIsNarrowlyScoped() {
        assertTrue(VideoSurfaceCodecPolicy.recreateOnReplacement(
                23, "AM2", "OMX.amlogic.mpeg2.decoder.awesome"));
        for (int sdk : new int[] {22, 24, 25, 33, 34, 35, 36})
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(
                    sdk, "AM2", "OMX.amlogic.mpeg2.decoder.awesome"));
        for (String device : new String[] {null, "", "MINIMX", "gxbaby", "gxl", "AFTMM", "other"})
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(
                    23, device, "OMX.amlogic.mpeg2.decoder.awesome"));
        for (String codec : new String[] {null, "", "OMX.amlogic.avc.decoder.awesome",
                "OMX.amlogic.hevc.decoder.awesome", "OMX.google.mpeg2.decoder",
                "c2.amlogic.mpeg2.decoder"})
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(23, "AM2", codec));
    }

    @Test public void onlyObservedCombinationUsesRecreation() {
        assertTrue(VideoSurfaceCodecPolicy.recreateOnReplacement(34, "sti6140d360", "c2.amlogic.avc.decoder"));
        assertTrue(VideoSurfaceCodecPolicy.recreateOnReplacement(34, "sti6140d360", "c2.amlogic.mpeg2.decoder"));
        assertTrue(VideoSurfaceCodecPolicy.recreateOnReplacement(34, "SNA", "c2.amlogic.mpeg2.decoder"));
        assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(34, "SNA", "c2.amlogic.avc.decoder"));
        for (int sdk : new int[] {23, 25, 30, 33, 35, 36})
        {
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(sdk, "sti6140d360", "c2.amlogic.avc.decoder"));
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(sdk, "SNA", "c2.amlogic.mpeg2.decoder"));
        }
        for (String device : new String[] {null, "", "foster", "AFTMM", "SNA", "other-onn"})
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(34, device, "c2.amlogic.avc.decoder"));
        for (String codec : new String[] {null, "", "c2.android.avc.decoder", "c2.amlogic.hevc.decoder", "OMX.amlogic.avc.decoder"})
            assertFalse(VideoSurfaceCodecPolicy.recreateOnReplacement(34, "sti6140d360", codec));
    }
}
