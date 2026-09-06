package opensagetv.vibe.miniclient.events;

import opensagetv.vibe.miniclient.SageCommand;

public class DebugSageCommandEvent {
    public final SageCommand command;

    public DebugSageCommandEvent(SageCommand command) {
        this.command = command;
    }
}
