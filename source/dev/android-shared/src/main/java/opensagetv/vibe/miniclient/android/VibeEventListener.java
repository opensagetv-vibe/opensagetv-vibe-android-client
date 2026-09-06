package opensagetv.vibe.miniclient.android;

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

/** Explicit event contract replacing annotation/reflection-based Otto dispatch. */
public interface VibeEventListener
{
    default void onConnected(ConnectedEvent event) { }
    default void handleOnConnectionLost(ConnectionLost event) { }
    default void onDebugKey(DebugSageCommandEvent event) { }
    default void handleOnShowKeyboard(ShowKeyboardEvent event) { }
    default void handleOnShowNavigation(ShowNavigationEvent event) { }
    default void handleOnBackPressed(BackPressedEvent event) { }
    default void onChangePlayerOneTime(ChangePlayerOneTime event) { }
    default void handleOnCloseApp(CloseAppEvent event) { }
    default void handleOnHideKeyboard(HideKeyboardEvent event) { }
    default void handleOnHideNavigation(HideNavigationEvent event) { }
    default void handleOnHideSystemUI(HideSystemUIEvent event) { }
    default void onMessage(MessageEvent event) { }
    default void onToggleAspectRatio(ToggleAspectRatioEvent event) { }
    default void onDebugKey(DebugKeyEvent event) { }
    default void refresh(VideoInfoRefresh event) { }
    default void handleVideoInfoRequest(VideoInfoShow event) { }
}
