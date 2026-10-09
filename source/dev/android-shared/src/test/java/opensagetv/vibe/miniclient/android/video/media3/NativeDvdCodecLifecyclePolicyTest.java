package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeDvdCodecLifecyclePolicyTest
{
    @Test public void restartRequiresQueuedSamplesNoOutputAndBoundedElapsedTime()
    {
        String codec = "OMX.MTK.VIDEO.DECODER.MPEG2";
        assertTrue(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, false, true, false, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 7, false, true, false, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, false, true, false, 100, 4099));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, true, true, false, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, false, false, false, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, false, true, true, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", codec, 8, false, true, false, -1, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/avc", codec, 8, false, true, false, 100, 4100));
        assertFalse(NativeDvdCodecLifecyclePolicy.restartNoOutput("video/mpeg2", "OMX.Nvidia.mpeg2v.decode", 8, false, true, false, 100, 4100));
    }

    @Test public void provenNativeDvdMpeg2CodecReleasesBeforeDisable()
    {
        assertTrue(NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                true, "video/mpeg2", "OMX.MTK.VIDEO.DECODER.MPEG2"));
    }

    @Test public void ordinaryTvAndOtherCodecsKeepExistingLifecycle()
    {
        assertFalse(NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                false, "video/mpeg2", "OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                true, "video/avc", "OMX.MTK.VIDEO.DECODER.AVC"));
        assertFalse(NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                true, "video/mpeg2", "OMX.Nvidia.mpeg2v.decode"));
        assertFalse(NativeDvdCodecLifecyclePolicy.releaseBeforeDisable(
                true, "video/mpeg2", null));
    }
}
