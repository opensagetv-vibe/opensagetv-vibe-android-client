package opensagetv.vibe.miniclient.media;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;

/** Decodes SageTV's legacy HD-extender MPEG-TS subpicture selection command. */
public final class LegacyExtenderSubpictureCommand
{
    public static final int NO_MATCH = -1;
    private static final int DISABLE_FLAG = 0x2000;
    private static final int PID_MASK = 0x1fff;

    private LegacyExtenderSubpictureCommand() { }

    public static boolean isDisabled(int command)
    {
        return (command & DISABLE_FLAG) != 0;
    }

    public static int sourcePid(int command)
    {
        return command & PID_MASK;
    }

    /** Returns a player track id, {@link MiniPlayerPlugin#DISABLE_TRACK}, or NO_MATCH. */
    public static int resolveTrackId(int command, SubtitleTrack[] tracks)
    {
        if (isDisabled(command)) return MiniPlayerPlugin.DISABLE_TRACK;
        if (tracks == null || tracks.length == 0) return NO_MATCH;
        int sourcePid = sourcePid(command);
        for (SubtitleTrack track : tracks)
            if (track != null && track.getSourceStreamId() == sourcePid)
                return track.getIndex();

        // Old probes without a preserved PID used the subpicture-list index.
        if (sourcePid < tracks.length && tracks[sourcePid] != null)
            return tracks[sourcePid].getIndex();
        return NO_MATCH;
    }
}
