package opensagetv.vibe.miniclient.android.ui.keymaps;

import opensagetv.vibe.miniclient.SageCommand;

/** DVD title scan control expressed only through existing stock STV events. */
public final class DvdRemoteScanPolicy
{
    static final long HOLD_STEP_MS = 1_000L;
    static final int MAX_RATE = 256;
    private DvdRemoteScanPolicy() { }

    static boolean isHold(long heldMs) { return heldMs >= HOLD_STEP_MS; }

    static float tapTarget(SageCommand requested, float rate)
    {
        if (rate <= 1f && rate >= 0f) return requested == SageCommand.FF ? 2f : -2f;
        if ((requested == SageCommand.FF) != (rate > 0f))
            return Math.abs(rate) <= 2f ? 1f : rate / 2f;
        return Math.copySign(Math.min(MAX_RATE, Math.abs(rate) * 2f), rate);
    }

    static SageCommand resolve(SageCommand requested, float rate)
    {
        boolean scanning = rate > 1.0f || rate < 0.0f;
        if (scanning && (requested == SageCommand.PLAY_PAUSE
                || requested == SageCommand.PAUSE || requested == SageCommand.PLAY))
            return SageCommand.PLAY;
        if (requested != SageCommand.FF && requested != SageCommand.REW)
            return requested;
        if (!scanning)
            return requested;
        boolean sameDirection = (requested == SageCommand.FF) == (rate > 0.0f);
        if (!sameDirection)
            return Math.abs(rate) <= 2.0f ? SageCommand.PLAY
                    : Math.abs(rate) > 16f ? SageCommand.SLOWER : requested;
        return Math.abs(rate) >= MAX_RATE ? SageCommand.NONE
                : Math.abs(rate) >= 16f ? SageCommand.FASTER : requested;
    }

    public static SageCommand[] sequence(SageCommand requested, float rate)
    {
        boolean scanning = rate > 1.0f || rate < 0.0f;
        boolean directionKey = requested == SageCommand.FF || requested == SageCommand.REW;
        if (scanning && directionKey
                && ((requested == SageCommand.FF) != (rate > 0f)))
        {
            // Beyond SageMC's 16x label range, halve Core's signed rate without
            // a Play/reseek/rebuild. Its existing public Slower event handles
            // both directions. At 2x the logical zero/off state means Play1x.
            if (Math.abs(rate) > 16f) return new SageCommand[]{SageCommand.SLOWER};
            // SageMC maintains DVDPlaybackRate independently of Core. Using
            // Faster/Slower changes Core but leaves its image/label stale.
            // Re-enter the lower speed through its existing Play + FF/REW
            // listeners so both the stock DVD VM and STV agree. Core owns the
            // normal-play reseek; this sends no private events or guessed seek.
            int count = 0;
            for (float magnitude = Math.abs(rate) / 2f; magnitude >= 2f; magnitude /= 2f)
                count++;
            SageCommand[] sequence = new SageCommand[count + 1];
            sequence[0] = SageCommand.PLAY;
            SageCommand direction = rate > 0f ? SageCommand.FF : SageCommand.REW;
            for (int index = 1; index < sequence.length; index++) sequence[index] = direction;
            return sequence;
        }
        return new SageCommand[]{resolve(requested, rate)};
    }
}
