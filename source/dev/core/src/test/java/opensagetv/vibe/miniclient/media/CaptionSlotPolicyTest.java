package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CaptionSlotPolicyTest
{
    private static SubtitleTrack track(int id, SubtitleCodec codec, String language, int service)
    {
        return new SubtitleTrack(id, codec, language, "", true, service);
    }

    @Test public void automaticSlotsChooseDistinctDescribedServices()
    {
        SubtitleTrack[] tracks = {
                track(1, SubtitleCodec.CEA608, "eng", 1),
                track(2, SubtitleCodec.TELETEXT, "eng", -1),
                track(3, SubtitleCodec.DVB, "eng", -1)
        };
        // CEA-608 does not carry an authoritative language, so Auto prefers
        // the first language-described broadcast service (Teletext here).
        assertEquals(2, CaptionSlotPolicy.findTrack(tracks, 1, "auto", "").getIndex());
        assertEquals(2, CaptionSlotPolicy.findTrack(tracks, 2, "auto", "").getIndex());
    }

    @Test public void explicitTypeAndLanguageAreAuthoritative()
    {
        SubtitleTrack[] tracks = {
                track(7, SubtitleCodec.CEA708, "eng", 1),
                track(8, SubtitleCodec.CEA708, "spa", 2),
                track(9, SubtitleCodec.CEA608, "", 3)
        };
        assertEquals(8, CaptionSlotPolicy.findTrack(tracks, 2, "CEA-708", "spa").getIndex());
        assertNull(CaptionSlotPolicy.findTrack(tracks, 1, "DVB", "eng"));
    }

    @Test public void automaticCc1PrefersEnglishAcrossCaptionTypes()
    {
        SubtitleTrack[] tracks = {
                track(1, SubtitleCodec.CEA708, "spa", 1),
                track(2, SubtitleCodec.TELETEXT, "fra", -1),
                track(3, SubtitleCodec.DVB, "eng", -1)
        };
        assertEquals(3, CaptionSlotPolicy.findTrack(tracks, 1, "auto", "").getIndex());
        assertEquals(1, CaptionSlotPolicy.findTrack(tracks, 2, "auto", "").getIndex());
    }

    @Test public void unknownCea608LanguageIsNotInvented()
    {
        SubtitleTrack[] tracks = { track(4, SubtitleCodec.CEA608, "", 3) };
        assertNull(CaptionSlotPolicy.findTrack(tracks, 1, "cea608", "spa"));
        assertEquals(4, CaptionSlotPolicy.findTrack(tracks, 1, "cea608", "").getIndex());
    }

    @Test public void excludingCeaLeavesRealDvbServicesAvailable()
    {
        SubtitleTrack[] tracks = {
                track(1, SubtitleCodec.CEA708, "eng", 1),
                track(2, SubtitleCodec.TELETEXT, "eng", -1),
                track(3, SubtitleCodec.DVB, "eng", -1)
        };
        assertEquals(2, CaptionSlotPolicy.findTrack(tracks, 1, "auto", "", false).getIndex());
    }

    @Test public void virtualCcSlotsExcludeDvbBitmapServices()
    {
        SubtitleTrack[] tracks = {
                track(2, SubtitleCodec.TELETEXT, "eng", -1),
                track(3, SubtitleCodec.DVB, "eng", -1)
        };
        assertEquals(2, CaptionSlotPolicy.findTrack(
                tracks, 1, "auto", "", true, false).getIndex());
        assertNull(CaptionSlotPolicy.findTrack(
                tracks, 1, "dvb", "eng", true, false));
        assertEquals(3, CaptionSlotPolicy.findTrack(
                tracks, 1, "dvb", "eng", true, true).getIndex());
    }
}
