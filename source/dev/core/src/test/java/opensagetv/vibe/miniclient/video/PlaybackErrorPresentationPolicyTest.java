package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackErrorPresentationPolicyTest
{
    @Test
    public void keepsUnsupportedContainerStartupFailureVisible()
    {
        assertTrue(PlaybackErrorPresentationPolicy.shouldShowFirstError(
                PlaybackErrorPresentationPolicy.PARSING_CONTAINER_UNSUPPORTED,
                false));
    }

    @Test
    public void suppressesRecoverableUnsupportedContainerTransitionAfterVideo()
    {
        assertFalse(PlaybackErrorPresentationPolicy.shouldShowFirstError(
                PlaybackErrorPresentationPolicy.PARSING_CONTAINER_UNSUPPORTED,
                true));
    }

    @Test
    public void keepsOtherPlaybackErrorsVisibleAfterVideo()
    {
        assertTrue(PlaybackErrorPresentationPolicy.shouldShowFirstError(
                "ERROR_CODE_DECODING_FAILED", true));
    }
}
