package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyExtenderSubpictureCommandTest
{
    @Test
    public void resolvesBroadcastPidToPlayerTrack()
    {
        SubtitleTrack[] tracks = {
                new SubtitleTrack(3, SubtitleCodec.DVB, "eng", "DVB", true, -1, 0x120),
                new SubtitleTrack(7, SubtitleCodec.TELETEXT, "eng", "Page 888", true,
                        -1, 0x157f)
        };
        assertEquals(7, LegacyExtenderSubpictureCommand.resolveTrackId(0x157f, tracks));
    }

    @Test
    public void honorsDisableFlagWithoutConfusingPidBits()
    {
        assertTrue(LegacyExtenderSubpictureCommand.isDisabled(0x2000 | 0x120));
        assertEquals(MiniPlayerPlugin.DISABLE_TRACK,
                LegacyExtenderSubpictureCommand.resolveTrackId(0x2000 | 0x120,
                        new SubtitleTrack[0]));
        assertFalse(LegacyExtenderSubpictureCommand.isDisabled(0x157f));
    }

    @Test
    public void retainsIndexFallbackAndDefersMissingTracks()
    {
        SubtitleTrack[] tracks = {
                new SubtitleTrack(9, SubtitleCodec.DVB, "eng", "DVB", true)
        };
        assertEquals(9, LegacyExtenderSubpictureCommand.resolveTrackId(0, tracks));
        assertEquals(LegacyExtenderSubpictureCommand.NO_MATCH,
                LegacyExtenderSubpictureCommand.resolveTrackId(0x120, tracks));
        assertEquals(LegacyExtenderSubpictureCommand.NO_MATCH,
                LegacyExtenderSubpictureCommand.resolveTrackId(0x120, null));
    }
}
