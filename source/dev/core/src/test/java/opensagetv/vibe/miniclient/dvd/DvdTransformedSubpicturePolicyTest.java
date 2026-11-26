package opensagetv.vibe.miniclient.dvd;

import org.junit.Test;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class DvdTransformedSubpicturePolicyTest {
    @Test public void copyRemuxUsesDenseOutputButNativeRetainsPhysicalIdentity() {
        int[] sparse = {32,34};
        assertEquals(65, DvdTransformedSubpicturePolicy.copyCommand(66, sparse));
        assertEquals(64, DvdTransformedSubpicturePolicy.copyCommand(64, sparse));
        assertEquals(62, DvdTransformedSubpicturePolicy.copyCommand(65, sparse));
        assertEquals(62, DvdTransformedSubpicturePolicy.copyCommand(66, new int[0]));
        assertEquals(128, DvdTransformedSubpicturePolicy.copyCommand(128, sparse));
        assertEquals(62, DvdTransformedSubpicturePolicy.copyCommand(62, sparse));
        assertEquals(66, DvdTransformedSubpicturePolicy.copyCommand(66, null));
    }
    @Test public void sparsePhysicalIdentityMapsToItsActualOutputOrdinal() {
        SubtitleTrack[] tracks = {
            new SubtitleTrack(0, SubtitleCodec.CEA608, "en", "", true),
            new SubtitleTrack(2, SubtitleCodec.DVB, "und", "", true),
            new SubtitleTrack(3, SubtitleCodec.DVB, "und", "", true)
        };
        int[] map = DvdTransformedSubpicturePolicy.physicalIds("push:dvd?clock=nav_v1&spu_ids=32,34");
        assertArrayEquals(new int[]{32,34}, map);
        assertEquals(3, DvdTransformedSubpicturePolicy.resolve(66, tracks, map));
        assertEquals(2, DvdTransformedSubpicturePolicy.resolve(64, tracks, map));
        assertEquals(-1, DvdTransformedSubpicturePolicy.resolve(65, tracks, map));
        assertEquals(-1, DvdTransformedSubpicturePolicy.resolve(66,
                new SubtitleTrack[]{tracks[1]}, map));
        assertEquals(MiniPlayerPlugin.DISABLE_TRACK,
                DvdTransformedSubpicturePolicy.resolve(128, tracks, map));
    }
    @Test public void invalidMapNeverRevertsToDenseGuess() {
        assertNull(DvdTransformedSubpicturePolicy.physicalIds("push:dvd?clock=nav_v1"));
        for (String map : new String[]{"34,32", "32,32", "31", "64", "32,", "32&spu_ids=34"})
            assertArrayEquals(new int[0], DvdTransformedSubpicturePolicy.physicalIds("push:dvd?spu_ids=" + map));
    }
    @Test public void physicalDvdOrdinalsSkipCaptionTracks() {
        SubtitleTrack[] tracks = {
            new SubtitleTrack(0, SubtitleCodec.CEA608, "en", "", true),
            new SubtitleTrack(1, SubtitleCodec.CEA708, "en", "", true),
            new SubtitleTrack(2, SubtitleCodec.DVB, "en", "", true),
            new SubtitleTrack(3, SubtitleCodec.DVB, "es", "", true)
        };
        assertEquals(2, DvdTransformedSubpicturePolicy.resolve(64, tracks));
        assertEquals(3, DvdTransformedSubpicturePolicy.resolve(65, tracks));
        assertEquals(-1, DvdTransformedSubpicturePolicy.resolve(66, tracks));
    }

    @Test public void disabledAndAbsentNeverGuessCaptionZero() {
        assertEquals(MiniPlayerPlugin.DISABLE_TRACK,
                DvdTransformedSubpicturePolicy.resolve(128, null));
        assertEquals(MiniPlayerPlugin.DISABLE_TRACK,
                DvdTransformedSubpicturePolicy.resolve(62, null));
        assertEquals(MiniPlayerPlugin.DISABLE_TRACK,
                DvdTransformedSubpicturePolicy.resolve(0, null));
        assertEquals(-1, DvdTransformedSubpicturePolicy.resolve(64, null));
    }
}
