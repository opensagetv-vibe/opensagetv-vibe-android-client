package opensagetv.vibe.miniclient.uibridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.events.DebugSageCommandEvent;
import opensagetv.vibe.miniclient.events.ShowKeyboardEvent;
import opensagetv.vibe.miniclient.events.ShowNavigationEvent;
import opensagetv.vibe.miniclient.prefs.PrefStore;


public class EventRouter
{
    public static final Logger log = LoggerFactory.getLogger(EventRouter.class);

    public static void postCommand(MiniClient client, int command)
    {
        log.debug("Post Command Called:  " + command);

        // Keep the historical low-latency repeated-Pause behavior. The player
        // contract now reports unsupported explicitly; in that case SageTV gets
        // the event and retains its normal fallback behavior.
        if (client.isVideoPaused() && SageCommand.parseByID(command) == SageCommand.PAUSE
                && client.getPlayer().frameStep(1))
        {
            return;
        }
        if ((client.isVideoPaused() || client.isVideoPlaying()) && SageCommand.parseByID(command) == SageCommand.STOP)
        {
            log.debug("Telling active player to stop playback");
            client.getPlayer().stop();
        }

        if (client.properties().getBoolean(PrefStore.Keys.debug_sage_commands, false))
        {
            client.eventbus().post(new DebugSageCommandEvent(SageCommand.parseByID(command)));
        }

        client.getCurrentConnection().postSageCommandEvent(command);
    }

    public static void postCommand(MiniClient client, SageCommand command)
    {
        log.debug("Post SageCommandCalled: " + command.getDisplayName() + " Key:" + command.getKey() + " EventCode:" + command.getEventCode());

        if (client.isVideoPaused() && command == SageCommand.PAUSE
                && client.getPlayer().frameStep(1))
        {
            return;
        }

        if ((client.isVideoPaused() || client.isVideoPlaying()) && command == SageCommand.STOP)
        {
            log.debug("Telling active player to stop playback");
            client.getPlayer().stop();
        }

        if (client.properties().getBoolean(PrefStore.Keys.debug_sage_commands, false))
        {
            client.eventbus().post(new DebugSageCommandEvent(command));
        }

        if (command.getEventCode() >= 0)
        {
            client.getCurrentConnection().postSageCommandEvent(command.getEventCode());
        }
        else
        {
            if (command == SageCommand.NAV_OSD)
            {
                client.eventbus().post(ShowNavigationEvent.INSTANCE);
            }
            else if (command == SageCommand.KEYBOARD_OSD)
            {
                client.eventbus().post(ShowKeyboardEvent.INSTANCE);
            }
            else
            {
                log.warn("Unhandled SageCommand: {}", command);
            }
        }
    }
}
