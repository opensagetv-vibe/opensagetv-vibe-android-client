package opensagetv.vibe.miniclient.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.IBus;
import opensagetv.vibe.miniclient.OrderedListenerRegistry;
import opensagetv.vibe.miniclient.android.events.BackPressedEvent;
import opensagetv.vibe.miniclient.android.events.ChangePlayerOneTime;
import opensagetv.vibe.miniclient.android.events.CloseAppEvent;
import opensagetv.vibe.miniclient.android.events.HideKeyboardEvent;
import opensagetv.vibe.miniclient.android.events.HideNavigationEvent;
import opensagetv.vibe.miniclient.android.events.HideSystemUIEvent;
import opensagetv.vibe.miniclient.android.events.MessageEvent;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.ui.keymaps.DebugKeyEvent;
import opensagetv.vibe.miniclient.events.ConnectedEvent;
import opensagetv.vibe.miniclient.events.ConnectionLost;
import opensagetv.vibe.miniclient.events.DebugSageCommandEvent;
import opensagetv.vibe.miniclient.events.ShowKeyboardEvent;
import opensagetv.vibe.miniclient.events.ShowNavigationEvent;
import opensagetv.vibe.miniclient.events.VideoInfoRefresh;
import opensagetv.vibe.miniclient.events.VideoInfoShow;

/** Synchronous, registration-ordered, explicitly typed application event bus. */
public final class VibeEventBus implements IBus
{
    private static final Logger log = LoggerFactory.getLogger(VibeEventBus.class);
    private final OrderedListenerRegistry<VibeEventListener> listeners =
            new OrderedListenerRegistry<VibeEventListener>();

    @Override
    public void register(Object object)
    {
        if (!(object instanceof VibeEventListener))
        {
            log.warn("Ignoring event registration without VibeEventListener: {}", object);
            return;
        }
        listeners.register((VibeEventListener) object);
    }

    @Override
    public void unregister(Object object)
    {
        if (object instanceof VibeEventListener)
            listeners.unregister((VibeEventListener) object);
    }

    @Override
    public void post(final Object event)
    {
        if (event == null)
            throw new IllegalArgumentException("event must not be null");
        final boolean known = isKnown(event);
        if (!known)
        {
            log.debug("Unhandled event type: {}", event.getClass().getName());
            return;
        }
        listeners.dispatch(new OrderedListenerRegistry.Dispatcher<VibeEventListener>()
        {
            @Override public void dispatch(VibeEventListener listener)
            {
                dispatchKnown(listener, event);
            }
        });
    }

    private static boolean isKnown(Object event)
    {
        return event instanceof ConnectedEvent || event instanceof ConnectionLost
                || event instanceof DebugSageCommandEvent || event instanceof ShowKeyboardEvent
                || event instanceof ShowNavigationEvent || event instanceof BackPressedEvent
                || event instanceof ChangePlayerOneTime || event instanceof CloseAppEvent
                || event instanceof HideKeyboardEvent || event instanceof HideNavigationEvent
                || event instanceof HideSystemUIEvent || event instanceof MessageEvent
                || event instanceof ToggleAspectRatioEvent || event instanceof DebugKeyEvent
                || event instanceof VideoInfoRefresh || event instanceof VideoInfoShow;
    }

    private static void dispatchKnown(VibeEventListener listener, Object event)
    {
        if (event instanceof ConnectedEvent) listener.onConnected((ConnectedEvent) event);
        else if (event instanceof ConnectionLost) listener.handleOnConnectionLost((ConnectionLost) event);
        else if (event instanceof DebugSageCommandEvent) listener.onDebugKey((DebugSageCommandEvent) event);
        else if (event instanceof ShowKeyboardEvent) listener.handleOnShowKeyboard((ShowKeyboardEvent) event);
        else if (event instanceof ShowNavigationEvent) listener.handleOnShowNavigation((ShowNavigationEvent) event);
        else if (event instanceof BackPressedEvent) listener.handleOnBackPressed((BackPressedEvent) event);
        else if (event instanceof ChangePlayerOneTime) listener.onChangePlayerOneTime((ChangePlayerOneTime) event);
        else if (event instanceof CloseAppEvent) listener.handleOnCloseApp((CloseAppEvent) event);
        else if (event instanceof HideKeyboardEvent) listener.handleOnHideKeyboard((HideKeyboardEvent) event);
        else if (event instanceof HideNavigationEvent) listener.handleOnHideNavigation((HideNavigationEvent) event);
        else if (event instanceof HideSystemUIEvent) listener.handleOnHideSystemUI((HideSystemUIEvent) event);
        else if (event instanceof MessageEvent) listener.onMessage((MessageEvent) event);
        else if (event instanceof ToggleAspectRatioEvent) listener.onToggleAspectRatio((ToggleAspectRatioEvent) event);
        else if (event instanceof DebugKeyEvent) listener.onDebugKey((DebugKeyEvent) event);
        else if (event instanceof VideoInfoRefresh) listener.refresh((VideoInfoRefresh) event);
        else if (event instanceof VideoInfoShow) listener.handleVideoInfoRequest((VideoInfoShow) event);
    }
}
