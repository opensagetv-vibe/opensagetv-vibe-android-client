package opensagetv.vibe.miniclient.dvd;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.SubtitleTrack;

/** Maps a DVD physical SPU ordinal to its converted bitmap, never a CEA slot. */
public final class DvdTransformedSubpicturePolicy {
    public static final int NO_MATCH = -1;
    private DvdTransformedSubpicturePolicy() { }

    /** Null is the deployed legacy dense contract; malformed explicit maps fail closed. */
    public static int[] physicalIds(String url) {
        if (url == null || !url.contains("spu_ids=")) return null;
        if (url.length() > 1024) return new int[0];
        String found = null;
        for (String entry : url.substring(url.indexOf('?') + 1).split("&", -1)) {
            if (!entry.startsWith("spu_ids=")) continue;
            if (found != null) return new int[0];
            found = entry.substring(8);
        }
        if (found == null || found.isEmpty()) return new int[0];
        String[] values = found.split(",", -1);
        if (values.length > 32) return new int[0];
        int[] ids = new int[values.length]; int previous = 31;
        for (int i = 0; i < values.length; i++) {
            if (!values[i].matches("[0-9]{2}")) return new int[0];
            int id = Integer.parseInt(values[i]);
            if (id <= previous || id > 63) return new int[0];
            ids[i] = id; previous = id;
        }
        return ids;
    }

    public static int resolve(int command, SubtitleTrack[] tracks) {
        return resolve(command, tracks, null);
    }
    /** FFmpeg's VOB copy muxer densely reassigns physical SPU IDs. Keep the
     * server's original enable/disable flags, translating only an explicitly
     * negotiated Copy map. Ordinary Native DVD commands are never rewritten.
     */
    public static int copyCommand(int command, int[] physicalIds) {
        if (physicalIds == null || command == 62 || (command & 0x80) != 0 || (command & 0x40) == 0)
            return command;
        int ordinal = java.util.Arrays.binarySearch(physicalIds, 32 + (command & 0x1f));
        return ordinal < 0 ? 62 : (command & ~0x1f) | ordinal;
    }
    public static int resolve(int command, SubtitleTrack[] tracks, int[] physicalIds) {
        if (command == 62 || (command & 0x80) != 0 || (command & 0x40) == 0)
            return MiniPlayerPlugin.DISABLE_TRACK;
        int ordinal = command & 0x1f;
        if (physicalIds != null) {
            ordinal = java.util.Arrays.binarySearch(physicalIds, 32 + ordinal);
            if (ordinal < 0) return NO_MATCH;
            int bitmapCount = 0;
            if (tracks != null) for (SubtitleTrack track : tracks)
                if (track != null && track.isSupported() && track.getSubtitleCodec() == SubtitleCodec.DVB)
                    bitmapCount++;
            if (bitmapCount != physicalIds.length) return NO_MATCH;
        }
        if (tracks != null) for (SubtitleTrack track : tracks) {
            if (track == null || !track.isSupported()
                    || track.getSubtitleCodec() != SubtitleCodec.DVB) continue;
            if (ordinal-- == 0) return track.getIndex();
        }
        return NO_MATCH;
    }
}
