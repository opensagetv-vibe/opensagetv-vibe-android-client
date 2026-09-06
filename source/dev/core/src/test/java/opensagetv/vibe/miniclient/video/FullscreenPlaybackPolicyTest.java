package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FullscreenPlaybackPolicyTest
{
    @Test
    public void detectsEmbeddedPreview()
    {
        assertTrue(FullscreenPlaybackPolicy.isEmbeddedPreview(441, 248, 1920, 1080));
    }

    @Test
    public void doesNotTreatFullscreenOrLetterboxingAsPreview()
    {
        assertFalse(FullscreenPlaybackPolicy.isEmbeddedPreview(1920, 1080, 1920, 1080));
        assertFalse(FullscreenPlaybackPolicy.isEmbeddedPreview(1440, 1080, 1920, 1080));
        assertFalse(FullscreenPlaybackPolicy.isEmbeddedPreview(1920, 800, 1920, 1080));
    }

    @Test
    public void rejectsUnknownDimensions()
    {
        assertFalse(FullscreenPlaybackPolicy.isEmbeddedPreview(0, 0, 1920, 1080));
        assertFalse(FullscreenPlaybackPolicy.isEmbeddedPreview(441, 248, 0, 0));
    }

    @Test
    public void playbackMenuNeverReceivesTogglePromotion()
    {
        assertTrue(FullscreenPlaybackPolicy.isPlaybackMenu("MediaPlayer OSD"));
        assertFalse(FullscreenPlaybackPolicy.shouldPromote(
                "MediaPlayer OSD", null, 441, 248, 1920, 1080));
    }

    @Test
    public void stableMainMenuPreviewMayUseCompatibilityPromotion()
    {
        assertTrue(FullscreenPlaybackPolicy.shouldPromote(
                "Main Menu", null, 441, 248, 1920, 1080));
        assertFalse(FullscreenPlaybackPolicy.shouldPromote(
                "Main Menu", "Resume or Restart", 441, 248, 1920, 1080));
    }
}
