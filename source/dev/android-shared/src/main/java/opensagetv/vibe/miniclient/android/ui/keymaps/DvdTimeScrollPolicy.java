package opensagetv.vibe.miniclient.android.ui.keymaps;

import opensagetv.vibe.miniclient.SageCommand;

/** Stock STV cursor ownership: enter once, adjust, commit once or cancel. */
final class DvdTimeScrollPolicy
{
    private boolean active;
    boolean isActive() { return active; }

    SageCommand[] move(boolean forward)
    {
        SageCommand skip = forward ? SageCommand.FF : SageCommand.REW;
        if (active) return new SageCommand[]{skip};
        active = true;
        // Play cancels a stale cursor before TS starts a new one. It does not
        // commit its position in SageMC, unlike toggling TS speculatively.
        return new SageCommand[]{SageCommand.PLAY, SageCommand.TIME_SCROLL, skip};
    }

    SageCommand[] accept()
    {
        if (!active) return new SageCommand[0];
        active = false;
        return new SageCommand[]{SageCommand.TIME_SCROLL};
    }

    SageCommand[] cancel()
    {
        if (!active) return new SageCommand[0];
        active = false;
        return new SageCommand[]{SageCommand.PLAY};
    }

    void abandon() { active = false; }
}
