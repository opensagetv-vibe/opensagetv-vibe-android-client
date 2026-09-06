package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
//import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import opensagetv.vibe.miniclient.MACAddressResolver;
import opensagetv.vibe.miniclient.BackgroundSessionState;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.events.BackPressedEvent;
import opensagetv.vibe.miniclient.android.events.ChangePlayerOneTime;
import opensagetv.vibe.miniclient.android.events.CloseAppEvent;
import opensagetv.vibe.miniclient.android.events.HideKeyboardEvent;
import opensagetv.vibe.miniclient.android.events.HideNavigationEvent;
import opensagetv.vibe.miniclient.android.events.HideSystemUIEvent;
import opensagetv.vibe.miniclient.android.events.MessageEvent;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.android.ui.MiniClientKeyListener;
import opensagetv.vibe.miniclient.android.ui.MiniclientTouchListener;
import opensagetv.vibe.miniclient.android.ui.keymaps.DebugKeyEvent;
import opensagetv.vibe.miniclient.android.ui.keymaps.DebugKeyPressWindow;
import opensagetv.vibe.miniclient.android.ui.keymaps.KeyMapProcessor;
import opensagetv.vibe.miniclient.android.util.AudioFocusController;
import opensagetv.vibe.miniclient.android.video.PlayerSurfaceView;
import opensagetv.vibe.miniclient.events.ConnectionLost;
import opensagetv.vibe.miniclient.events.DebugSageCommandEvent;
import opensagetv.vibe.miniclient.events.ShowKeyboardEvent;
import opensagetv.vibe.miniclient.events.ShowNavigationEvent;
import opensagetv.vibe.miniclient.events.VideoInfoShow;
import opensagetv.vibe.miniclient.prefs.PrefStore.Keys;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import opensagetv.vibe.miniclient.uibridge.UIRenderer;
import opensagetv.vibe.miniclient.util.ClientIDGenerator;
import opensagetv.vibe.miniclient.video.HasVideoInfo;

import static opensagetv.vibe.miniclient.android.AppUtil.confirmExit;
import static opensagetv.vibe.miniclient.android.AppUtil.hideSystemUI;
import static opensagetv.vibe.miniclient.android.AppUtil.message;

import androidx.media.session.MediaButtonReceiver;


/**
 * Created by seans on 20/09/15.
 */
public class UIActivityLifeCycleHandler<UIRenderType extends UIRenderer> implements MACAddressResolver, AndroidUIController, BackgroundSessionOwner.Listener, VibeEventListener
{
    public interface IActivityCallback<UIRenderType extends UIRenderer>
    {
        View createAndConfigureUIView(UIActivityLifeCycleHandler<UIRenderType> handler);

        UIRenderType createUIRenderer(UIActivityLifeCycleHandler<UIRenderType> handler);

        int getLayoutViewId(UIActivityLifeCycleHandler<UIRenderType> handler);
    }

    protected boolean keyboardVisible = false;
    public static final String ARG_SERVER_INFO = "server_info";
    protected static final Logger log = LoggerFactory.getLogger(UIActivityLifeCycleHandler.class);
    protected FrameLayout uiFrameHolder;
    protected PlayerSurfaceView videoHolder;
    protected ViewGroup videoHolderParent;
    protected View pleaseWait = null;
    protected TextView plaseWaitText = null;
    protected TextView captionsText = null;
    // error stuff
    protected TextView errorMessage;
    protected TextView errorCause;
    protected ViewGroup errorContainer;

    protected MiniClient client;
    protected UIRenderType mgr;

    protected MediaSessionCompat mediaSessionCompat;

    protected View miniClientView;

    protected ChangePlayerOneTime changePlayerOneTime = null;

    protected Activity activity;
    private NavigationDialog navigationDialog;
    private MiniClientKeyListener keyListener;
    private VideoInfoDialog videoInfoDialog;
    private HelpDialog helpDialog;
    protected IActivityCallback<UIRenderType> activityCallback;
    private final AudioFocusController audioFocusController = new AudioFocusController();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong keyboardTaskGeneration = new AtomicLong();
    private Runnable pendingKeyboardTask;
    private final Object connectionTaskLock = new Object();
    private final AtomicLong connectionTaskGeneration = new AtomicLong();
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable runnable)
        {
            return new Thread(runnable, "ANDROID-MINICLIENT");
        }
    });
    private Future<?> connectionFuture;
    private volatile boolean destroyed;
    private BackgroundSessionOwner backgroundSessionOwner;
    private boolean keepSessionInBackground;
    private boolean resumeBackgroundPlayback;
    private boolean activityResumed;
    private boolean foregroundResumePending;
    private boolean backgroundCandidateKnown;
    private boolean backgroundCandidateWasPlaying;
    private Runnable pendingBackgroundResume;

    public UIActivityLifeCycleHandler(IActivityCallback<UIRenderType> activityCallback)
    {
        this.activityCallback = activityCallback;
    }

    public void onWindowFocusChanged(boolean hasFocus)
    {
        // Fire OS may dispatch MediaSession PAUSE before Activity.onPause when
        // HOME takes window focus. Snapshot the playing state at focus loss so
        // the later application-background grace can own that pause. Dialogs
        // and in-app Activity changes cancel this candidate when focus/start
        // returns without the application actually entering background.
        if (!hasFocus)
            captureBackgroundCandidate();
        else if (backgroundSessionOwner == null
                || backgroundSessionOwner.state().getState()
                == BackgroundSessionState.State.FOREGROUND)
            onApplicationBackgroundCandidateCancelled();
        AppUtil.hideSystemUI(activity);
    }

    public MiniClient getClient()
    {
        return client;
    }

    /**
     * Route physical remote keys before a decoder, subtitle, or GL surface can
     * consume them.  The view listener remains installed for compatibility,
     * but Activity.dispatchKeyEvent is the stable ownership point across
     * renderer/player focus changes on Fire OS.
     */
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        MiniClientKeyListener listener = keyListener;
        View target = miniClientView;
        return listener != null && target != null
                && listener.onKey(target, event.getKeyCode(), event);
    }

    private MiniPlayerPlugin getCurrentPlayer()
    {
        if (client == null || client.getCurrentConnection() == null
                || client.getCurrentConnection().getMediaCmd() == null)
            return null;
        return client.getCurrentConnection().getMediaCmd().getPlaya();
    }


    public void onResume(Activity activity)
    {
        this.activity = activity;
        activityResumed = true;
        UiKeyboardDebugState.resumed(activity);

        log.debug("MiniClient UI onResume() called");

        audioFocusController.request(activity, new AudioFocusController.PlaybackCallbacks()
        {
            @Override
            public boolean isPlaying()
            {
                MiniPlayerPlugin player = getCurrentPlayer();
                return player != null && player.getState() == MiniPlayerPlugin.PLAY_STATE;
            }

            @Override
            public void pauseForFocusLoss()
            {
                MiniPlayerPlugin player = getCurrentPlayer();
                if (player != null)
                    player.pause();
            }

            @Override
            public void resumeAfterFocusGain()
            {
                MiniPlayerPlugin player = getCurrentPlayer();
                if (player != null && player.getState() == MiniPlayerPlugin.PAUSE_STATE)
                    player.play();
            }
        });

        // setup to handle events
        client.eventbus().register(UIActivityLifeCycleHandler.this);

        if (keyListener != null)
            keyListener.shutdown();
        keyListener = new MiniClientKeyListener(activity, client, UIActivityLifeCycleHandler.this);

        try
        {
            miniClientView.setOnTouchListener(new MiniclientTouchListener(activity, client));
            miniClientView.setOnKeyListener(keyListener);
        }
        catch (Throwable t)
        {
            log.error("Failed to restore the key and touch handlers");
        }

        try
        {
            if (client.getUIRenderer() != null && client.getUIRenderer().isFirstFrameRendered() && mgr.getUISize().width > 0 && mgr.getUISize().height > 0)
            {
                log.debug("Telling SageTV to repaint {}x{}", mgr.getUISize().getWidth(), mgr.getUISize().getHeight());
                client.getCurrentConnection().postRepaintEvent(0, 0, mgr.getUISize().getWidth(), mgr.getUISize().getHeight());
            }
        }
        catch (Throwable t)
        {
            log.warn("Failed to do a repaint event on refresh");
        }

        hideSystemUI(activity);
        if (foregroundResumePending || (backgroundSessionOwner != null
                && (backgroundSessionOwner.state().getState() == BackgroundSessionState.State.BACKGROUND_APP_PAUSED
                || backgroundSessionOwner.state().getState() == BackgroundSessionState.State.BACKGROUND_USER_PAUSED)))
        {
            foregroundResumePending = false;
            resumePreservedBackgroundSession();
        }
    }

    public void onPause(Activity activity)
    {
        activityResumed = false;
        ActivePlayerProcessOverlay.hide();
        cancelKeyboardTask();
        dismissPlaybackDialogs();
        audioFocusController.abandon();
        UiKeyboardDebugState.paused(activity);
        if (keyListener != null)
        {
            keyListener.shutdown();
            keyListener = null;
        }

        // remove ourself from handling events
        client.eventbus().unregister(this);

        try
        {
            miniClientView.setOnTouchListener(null);
            miniClientView.setOnKeyListener(null);
        }
        catch (Throwable t)
        {
        }

        log.debug("MiniClient UI onPause() called");
        if (!keepSessionInBackground)
        {
            try
            {
                // Preserve the historical teardown policy for users who have
                // not explicitly enabled bounded background sessions.
                if (client.getCurrentConnection() != null && client.getCurrentConnection().getMediaCmd() != null)
                {
                    if (client.getCurrentConnection().getMediaCmd().getPlaya() != null)
                    {
                        log.info("We are leaving the App, Make sure Video is stopped.");
                        client.getCurrentConnection().getMediaCmd().getPlaya().pause();
                        EventRouter.postCommand(client, SageCommand.STOP);
                    }
                }
            }
            catch (Throwable t)
            {
                log.debug("Failed while attempting to stop media player");
            }
            try
            {
                cancelConnectionTask(false);
                try { client.closeConnection(); } catch (Throwable ignored) { }
                finish();
            }
            catch (Throwable t) { log.debug("Failed to close client connection"); }
        }
        else if (videoHolder != null)
        {
            // The application visibility owner will send PAUSE only after its
            // grace period proves every app Activity is stopped.
            videoHolder.setVisibility(View.INVISIBLE);
        }
    }

    /** Debug-build receiver hook; returns null rather than retaining an Activity. */
    public static Activity getResumedActivityForDebug()
    {
        return UiKeyboardDebugState.activity();
    }

    public void onCreate(Activity activity)
    {
        this.activity = activity;
        try
        {
            hideSystemUI(activity);

            activity.setContentView(activityCallback.getLayoutViewId(this));

            activity.findViewById(R.id.errorClose).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    onCloseClicked();
                }
            });

            uiFrameHolder = (FrameLayout) activity.findViewById(R.id.surface);
            videoHolder = (PlayerSurfaceView) activity.findViewById(R.id.video_surface);
            videoHolderParent = (ViewGroup) activity.findViewById(R.id.video_surface_parent);
            pleaseWait = activity.findViewById(R.id.waitforit);
            plaseWaitText = (TextView) activity.findViewById(R.id.pleaseWaitText);
            captionsText = (TextView) activity.findViewById(R.id.captionsText);
            errorMessage = (TextView) activity.findViewById(R.id.errorMessage);
            errorCause = (TextView) activity.findViewById(R.id.errorCause);
            errorContainer = (ViewGroup) activity.findViewById(R.id.errorContainer);


            client = MiniclientApplication.get().getClient();
            backgroundSessionOwner = MiniclientApplication.get().getBackgroundSessionOwner();
            UiSessionConfiguration sessionConfiguration =
                    UiSessionConfiguration.load(client.properties());
            keepSessionInBackground = sessionConfiguration.keepInBackground;
            resumeBackgroundPlayback = sessionConfiguration.resumePlayback;
            backgroundSessionOwner.setSessionTimeoutMs(sessionConfiguration.timeoutMs);
            backgroundSessionOwner.state().resetAfterExplicitExit();
            backgroundSessionOwner.setListener(this);

            mgr = activityCallback.createUIRenderer(this);

            miniClientView = activityCallback.createAndConfigureUIView(this);

            miniClientView.setFocusable(true);
            miniClientView.setFocusableInTouchMode(true);
            miniClientView.setOnTouchListener(null);
            miniClientView.setOnClickListener(null);
            miniClientView.setOnKeyListener(null);
            miniClientView.setOnDragListener(null);
            miniClientView.setOnFocusChangeListener(null);
            miniClientView.setOnGenericMotionListener(null);
            miniClientView.setOnHoverListener(null);
            miniClientView.setOnTouchListener(null);
            uiFrameHolder.addView(miniClientView);
            miniClientView.requestFocus();

            ServerInfo si = (ServerInfo) activity.getIntent().getSerializableExtra(ARG_SERVER_INFO);
            if (si == null)
            {
                log.error("Missing SERVER INFO in Intent: {}", ARG_SERVER_INFO);
                finish();
                return;
            }

            //setupNavigationDrawer();
            String connect = null;
            if (si.isLocatorOnly() || si.forceLocator)
            {
                connect = activity.getString(R.string.msg_connecting_locator, si.name);
            }
            else
            {
                connect = activity.getString(R.string.msg_connecting, si.name);
            }
            plaseWaitText.setText(connect);
            setConnectingIsVisible(true);

            startMiniClient(si);
            //VideoPlayerCaptions.

        }
        catch (Throwable t)
        {
            log.error("Failed to start/create the Main Activity for the MiniClient UI", t);
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    setErrorView(null, "MiniClient failed to initialize", null);
                }
            });
        }
    }

    public void startMiniClient(final ServerInfo si)
    {
        final long generation;
        synchronized (connectionTaskLock)
        {
            if (destroyed)
                return;
            generation = connectionTaskGeneration.incrementAndGet();
            if (connectionFuture != null)
                connectionFuture.cancel(true);
            connectionFuture = connectionExecutor.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    MiniClientConnection connection = null;
                    try
                    {
                        // Network connections must not run on the main thread.
                        connection = client.connectConnection(si, UIActivityLifeCycleHandler.this);
                        if (!isConnectionTaskActive(generation))
                        {
                            connection.close();
                            return;
                        }
                        if (!client.publishConnectedIfCurrent(connection))
                            connection.close();
                    }
                    catch (final IOException e)
                    {
                        if (connection != null && !isConnectionTaskActive(generation))
                            connection.close();
                        if (!isConnectionTaskActive(generation))
                            return;
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if (isConnectionTaskActive(generation))
                                    setErrorView(si, "Unable to connect", e.getMessage());
                            }
                        });
                    }
                    finally
                    {
                        clearConnectionTaskIfCurrent(generation);
                    }
                }
            });
        }
    }

    private boolean isConnectionTaskActive(long generation)
    {
        return !destroyed && connectionTaskGeneration.get() == generation;
    }

    private void clearConnectionTaskIfCurrent(long generation)
    {
        synchronized (connectionTaskLock)
        {
            if (connectionTaskGeneration.get() == generation)
                connectionFuture = null;
        }
    }

    private void cancelConnectionTask(boolean shuttingDown)
    {
        synchronized (connectionTaskLock)
        {
            if (shuttingDown)
                destroyed = true;
            connectionTaskGeneration.incrementAndGet();
            if (connectionFuture != null)
            {
                connectionFuture.cancel(true);
                connectionFuture = null;
            }
        }
        if (shuttingDown)
            connectionExecutor.shutdownNow();
    }

    public void showErrorMessage(String message, String cause)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Toast.makeText(activity, cause + " - " + message, Toast.LENGTH_LONG).show();
                }
                catch (Throwable t)
                {
                    log.error("MESSAGE: {}", message);
                }
            }
        });
    }

    private void setErrorView(ServerInfo si, String message, String cause)
    {
        if (plaseWaitText == null || errorMessage == null || errorCause == null
                || errorContainer == null)
        {
            log.error("MiniClient error UI is unavailable: {} ({})", message, cause);
            showErrorMessage(message, cause == null ? "Initialization failed" : cause);
            finish();
            return;
        }
        plaseWaitText.setVisibility(View.GONE);
        errorMessage.setText(message);
        errorCause.setText(cause);
        errorContainer.setVisibility(View.VISIBLE);
    }

    public void onBackPressed()
    {
        // hide system ui, in case keyboard is visible
        hideSystemUI(activity);
    }

    public void onDestroy()
    {
        log.debug("Closing MiniClient Connection");
        ActivePlayerProcessOverlay.hide();
        cancelPendingBackgroundResume();
        cancelKeyboardTask();
        dismissPlaybackDialogs();
        audioFocusController.abandon();
        if (keyListener != null)
        {
            keyListener.shutdown();
            keyListener = null;
        }
        if (backgroundSessionOwner != null)
        {
            backgroundSessionOwner.clearListener(this);
            backgroundSessionOwner.state().sessionLost();
        }
        cancelConnectionTask(true);

        if (mediaSessionCompat != null)
        {
            try
            {
                mediaSessionCompat.setActive(false);
                mediaSessionCompat.release();
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }

        try
        {

            client.closeConnection();
        }
        catch (Throwable t)
        {
            log.error("Error shutting down client", t);
        }
    }

    public void setConnectingIsVisible(final boolean connectingIsVisible)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (connectingIsVisible)
                {
                    errorContainer.setVisibility(View.GONE);
                    pleaseWait.setVisibility(View.VISIBLE);
                }
                else
                {
                    // hiding connecting is visible
                    //YoYo.with(Techniques.FadeOutLeft).duration(700).playOn(pleaseWait);
                    errorContainer.setVisibility(View.GONE);
                    pleaseWait.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public String getMACAddress()
    {
        // Android 6 generates the same MAC address, so generate and persist a MiniClient ID.
        String id = client.properties().getString(Keys.client_id);
        if (id == null)
        {
            ClientIDGenerator gen = new ClientIDGenerator();
            id = gen.generateId();
            client.properties().setString(Keys.client_id, id);
        }
        return id;
        //return AppUtil.getMACAddress(this);
    }

    public PlayerSurfaceView getVideoView()
    {
        if (videoHolder == null)
        {
            setupVideoFrame();
        }
        return videoHolder;
    }

    @Override
    public View getUIView()
    {
        return miniClientView;
    }

    public TextView getPleaseWaitText()
    {
        return this.plaseWaitText;
    }

    @Override
    public Context getContext()
    {
        return activity;
    }

    public boolean isKeyboardVisible()
    {
        return this.keyboardVisible;
    }

    /**
     * Debug/read-only keyboard state used by the debug APK control surface.
     * Returns 1 when the resumed MiniClient Activity can verify that the IME is
     * visible, 0 when it can verify that it is hidden, and -1 when no resumed
     * Activity/window is available to make that determination.
     */
    public static int getImeVisibilityForDebug()
    {
        return UiKeyboardDebugState.visibility();
    }

    public static boolean isKeyboardRequestedForDebug()
    {
        return UiKeyboardDebugState.requested();
    }

    /**
     * Debug-only IME suppression used by MCP native text automation. SageTV still
     * receives hasTextInput/menu hints normally, but Android's soft keyboard is
     * not shown while this flag is enabled. Manual/non-debug behavior is unchanged.
     */
    public static boolean setKeyboardSuppressedForDebug(boolean suppressed)
    {
        return UiKeyboardDebugState.setSuppressed(suppressed);
    }

    public static boolean isKeyboardSuppressedForDebug()
    {
        return UiKeyboardDebugState.suppressed();
    }

    /**
     * Debug-only direct IME hide used by MCP automation. This bypasses Android
     * BACK-key injection so closing the keyboard cannot accidentally become a
     * SageTV Back command.
     */
    public static boolean hideImeForDebug()
    {
        return UiKeyboardDebugState.hide();
    }

    public void showHideKeyboard(final boolean visible)
    {
        if (visible && UiKeyboardDebugState.suppressed())
        {
            UiKeyboardDebugState.requested(false);
            UIActivityLifeCycleHandler.this.keyboardVisible = false;
            hideImeForDebug();
            log.debug("Suppressing Keyboard for debug/MCP native text input");
            return;
        }

        UiKeyboardDebugState.requested(visible);

        final long generation = keyboardTaskGeneration.incrementAndGet();
        if (pendingKeyboardTask != null)
            mainHandler.removeCallbacks(pendingKeyboardTask);
        pendingKeyboardTask = new Runnable()
        {
            @Override
            public void run()
            {
                if (destroyed || keyboardTaskGeneration.get() != generation)
                    return;
                pendingKeyboardTask = null;
                android.view.inputmethod.InputMethodManager im =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                // Re-check here because a show request may already have been queued
                // when MCP enables suppression immediately before Search.
                if (visible && UiKeyboardDebugState.suppressed())
                {
                    UiKeyboardDebugState.requested(false);
                    im.hideSoftInputFromWindow(miniClientView.getWindowToken(), 0);
                    UIActivityLifeCycleHandler.this.keyboardVisible = false;
                    return;
                }

                if (visible)
                {
                    log.debug("Showing Keyboard");
                    miniClientView.requestFocus();
                    im.showSoftInput(miniClientView, InputMethodManager.SHOW_FORCED);
                    UIActivityLifeCycleHandler.this.keyboardVisible = true;
                }
                else
                {
                    im.hideSoftInputFromWindow(miniClientView.getWindowToken(), 0);
                    UIActivityLifeCycleHandler.this.keyboardVisible = false;
                }
            }
        };
        mainHandler.postDelayed(pendingKeyboardTask, 200);
    }

    private void cancelKeyboardTask()
    {
        keyboardTaskGeneration.incrementAndGet();
        if (pendingKeyboardTask != null)
        {
            mainHandler.removeCallbacks(pendingKeyboardTask);
            pendingKeyboardTask = null;
        }
        UiKeyboardDebugState.requested(false);
        keyboardVisible = false;
    }

    public void showHideSoftRemote(boolean visible)
    {
        if (visible)
        {
            showNavigationDialog();
        }
        else
        {
            hideNavigationDialog();
        }
    }

    void showNavigationDialog()
    {
        hideNavigationDialog();
        dismissDialog(videoInfoDialog);
        dismissDialog(helpDialog);
        videoInfoDialog = null;
        helpDialog = null;
        navigationDialog = NavigationDialog.showDialog(activity, new Runnable()
        {
            @Override
            public void run()
            {
                showHelpDialog();
            }
        });
    }

    private void showHelpDialog()
    {
        dismissDialog(videoInfoDialog);
        videoInfoDialog = null;
        dismissDialog(helpDialog);
        helpDialog = HelpDialog.showDialog(activity);
    }

    public void leftEdgeSwipe(MotionEvent event)
    {
        log.debug("Left Edge Swipe");
    }

    public View getRootView()
    {
        return miniClientView;
    }

    public void handleOnShowKeyboard(ShowKeyboardEvent event)
    {
        showHideKeyboard(true);
    }

    public void handleOnHideKeyboard(HideKeyboardEvent event)
    {
        showHideKeyboard(false);
    }

    public void handleOnHideSystemUI(HideSystemUIEvent event)
    {
        hideSystemUI(activity);
    }

    public void handleOnShowNavigation(ShowNavigationEvent event)
    {
        try
        {
            log.debug("MiniClient built-in Naviation is visible");
            showHideSoftRemote(true);
        }
        catch (Throwable t)
        {
            log.debug("Failed to show navigation");
        }
    }

    public void handleVideoInfoRequest(VideoInfoShow request)
    {
        if (client.getUIRenderer() instanceof HasVideoInfo)
        {
            hideNavigationDialog();
            dismissDialog(helpDialog);
            helpDialog = null;
            dismissDialog(videoInfoDialog);
            videoInfoDialog = VideoInfoDialog.showDialog(activity);
        }
    }

    public void handleOnHideNavigation(HideNavigationEvent event)
    {
        try
        {
            log.debug("MiniClient built-in Naviation is hidden");
            showHideSoftRemote(false);
            hideSystemUI(activity);
        }
        catch (Throwable t)
        {
            log.debug("Failed to hide navigation");
        }
    }

    public void handleOnCloseApp(CloseAppEvent event)
    {
        confirmExit(activity, new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });
    }

    public void handleOnConnectionLost(ConnectionLost event)
    {
        if (event.reconnecting)
        {
            message("SageTV Connection Closed.  Reconnecting...");
        }
        else
        {
            message("SageTV Connection Closed.");
            finish();
        }
    }

    boolean hideNavigationDialog()
    {
        log.debug("Hiding Navigation");
        boolean hidingOSD = navigationDialog != null && navigationDialog.isShowing();
        dismissDialog(navigationDialog);
        navigationDialog = null;
        return hidingOSD;
    }

    private void dismissPlaybackDialogs()
    {
        dismissDialog(navigationDialog);
        dismissDialog(videoInfoDialog);
        dismissDialog(helpDialog);
        navigationDialog = null;
        videoInfoDialog = null;
        helpDialog = null;
    }

    private static void dismissDialog(android.app.Dialog dialog)
    {
        if (dialog == null)
            return;
        try
        {
            dialog.dismiss();
        }
        catch (Throwable t)
        {
            // Activity teardown may already have removed the window.
        }
    }

    public void handleOnBackPressed(BackPressedEvent event)
    {
        hideSystemUI(activity);

        // prevents multiple back events from firing form different key handlers
        log.debug("on back pressed event");

        if (hideNavigationDialog())
        {
            log.debug("Just hiding navigation");
            KeyMapProcessor.skipBackOneTime = true;
        }
        else
        {
            // log.debug("Navigation wasn't visible so will process normal back");
            //EventRouter.postCommand(client, SageCommand.BACK);
        }
    }

    public void setupVideoFrame()
    {
        log.debug("Setting up the Video Frame");
        videoHolder.setVisibility(View.VISIBLE);
    }

    public void onChangePlayerOneTime(ChangePlayerOneTime changePlayerOneTime)
    {
        this.changePlayerOneTime = changePlayerOneTime;
    }

    public void onToggleAspectRatio(ToggleAspectRatioEvent ar)
    {
        log.debug("SENDING AR_TOGGLE: " + SageCommand.AR_TOGGLE);
        EventRouter.postCommand(client, SageCommand.AR_TOGGLE);
    }

    /**
     * This is a one time read.  It will return true if we need to switch the player, one time,
     * but it will reset itself AFTER this read, so, only call it once.
     *
     * @return
     */
    public boolean isSwitchingPlayerOneTime()
    {
        boolean change = changePlayerOneTime != null;
        changePlayerOneTime = null;
        return change;
    }

    public void onMessage(final MessageEvent event)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Toast.makeText(activity, event.getMessage(), Toast.LENGTH_LONG).show();
                }
                catch (Throwable t)
                {
                    log.error("MESSAGE: {}", event.getMessage());
                }
            }
        });
    }

    DebugKeyPressWindow debugKeyWindow;

    public void onDebugKey(final DebugKeyEvent event)
    {
        log.debug("DEBUG KEY: {}", event.fieldName);
        if (debugKeyWindow == null)
        {
            log.debug("Creating debugKeyWindow");
            debugKeyWindow = new DebugKeyPressWindow();
        }

        debugKeyWindow.show(activity);
        debugKeyWindow.showKey(event.fieldName, event.longPress, event.keyCode);
    }

    public void onDebugKey(final DebugSageCommandEvent event)
    {
        log.debug("DEBUG SageCommand: {}", event.command.getDisplayName());
        if (debugKeyWindow == null)
        {
            log.debug("Creating debugKeyWindow");
            debugKeyWindow = new DebugKeyPressWindow();
        }

        debugKeyWindow.show(activity);
        debugKeyWindow.showSageCommand(event.command);
    }

    public ViewGroup getVideoViewParent()
    {
        return videoHolderParent;
    }

    // @OnClick(R.id.errorClose)
    public void onCloseClicked()
    {
        // connect to server
//        if (getResources().getBoolean(R.bool.istv)) {
        finish();
//        } else {
//            Intent i = null;
//            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            startActivity(i);
//        }

    }

    public void removeVideoFrame()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                log.debug("Removing Video View");
                //videoHolderFrame.removeAllViews();
                videoHolder.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void finish()
    {
        if (backgroundSessionOwner != null)
            backgroundSessionOwner.state().explicitExit();
        activity.finish();
    }

    @Override
    public void onApplicationBackgroundCandidate()
    {
        captureBackgroundCandidate();
    }

    private void captureBackgroundCandidate()
    {
        if (!keepSessionInBackground || destroyed)
            return;
        MiniPlayerPlugin player = getCurrentPlayer();
        if (player != null)
        {
            backgroundCandidateKnown = true;
            // Preserve the earliest PLAY observation if Fire OS changes the
            // MediaSession to PAUSED before Activity.onPause is delivered.
            backgroundCandidateWasPlaying = backgroundCandidateWasPlaying
                    || player.getState() == MiniPlayerPlugin.PLAY_STATE;
        }
    }

    @Override
    public void onApplicationBackgroundCandidateCancelled()
    {
        backgroundCandidateKnown = false;
        backgroundCandidateWasPlaying = false;
    }

    @Override
    public void onApplicationBackgrounded()
    {
        if (!keepSessionInBackground || destroyed || client == null)
            return;
        cancelPendingBackgroundResume();
        MiniClientConnection connection = client.getCurrentConnection();
        MiniPlayerPlugin player = getCurrentPlayer();
        int playerState = player == null ? MiniPlayerPlugin.NO_STATE : player.getState();
        boolean wasPlayingBeforeTransition = backgroundCandidateKnown
                && backgroundCandidateWasPlaying;
        backgroundCandidateKnown = false;
        backgroundCandidateWasPlaying = false;
        BackgroundSessionState.Action action = backgroundSessionOwner.state().enterBackground(
                connection, client.isConnected(), player, playerState,
                MiniPlayerPlugin.PLAY_STATE, MiniPlayerPlugin.PAUSE_STATE,
                wasPlayingBeforeTransition);
        if (action == BackgroundSessionState.Action.PAUSE && player != null)
        {
            EventRouter.postCommand(client, SageCommand.PAUSE);
        }
        if (videoHolder != null)
            videoHolder.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onApplicationForegrounded()
    {
        if (!keepSessionInBackground || destroyed)
            return;
        foregroundResumePending = true;
        if (activityResumed)
        {
            foregroundResumePending = false;
            resumePreservedBackgroundSession();
        }
    }

    private void resumePreservedBackgroundSession()
    {
        if (videoHolder != null)
            videoHolder.setVisibility(View.VISIBLE);
        MiniClientConnection connection = client == null ? null : client.getCurrentConnection();
        MiniPlayerPlugin player = getCurrentPlayer();
        BackgroundSessionState.Action action = backgroundSessionOwner.state().enterForeground(
                connection, client != null && client.isConnected(), player,
                resumeBackgroundPlayback);
        if (action == BackgroundSessionState.Action.PLAY && player != null)
        {
            cancelPendingBackgroundResume();
            pendingBackgroundResume = new Runnable()
            {
                @Override public void run()
                {
                    pendingBackgroundResume = null;
                    if (!destroyed && activityResumed && client != null && client.isConnected()
                            && getCurrentPlayer() != null)
                        EventRouter.postCommand(client, SageCommand.PLAY);
                }
            };
            // Returning a Surface can make SageTV reopen the paused MediaFile.
            // Send PLAY after that short restore sequence so it is not overwritten
            // by the server's paused-state OPEN/SEEK/PLAY/PAUSE replay.
            mainHandler.postDelayed(pendingBackgroundResume, 1200L);
        }
        else if (action == BackgroundSessionState.Action.SESSION_LOST
                && connection != null && !connection.isConnected())
        {
            client.closeConnection();
        }
    }

    @Override
    public void onBackgroundSessionTimedOut()
    {
        cancelPendingBackgroundResume();
        if (!keepSessionInBackground || destroyed || client == null)
            return;
        backgroundSessionOwner.state().sessionLost();
        try
        {
            client.closeConnection();
        }
        catch (Throwable t)
        {
            log.warn("Failed to close an expired background SageTV session", t);
        }
    }

    private void cancelPendingBackgroundResume()
    {
        if (pendingBackgroundResume != null)
        {
            mainHandler.removeCallbacks(pendingBackgroundResume);
            pendingBackgroundResume = null;
        }
    }

    @Override
    public Object getSystemService(String windowService)
    {
        return activity.getSystemService(windowService);
    }

    @Override
    public void runOnUiThread(Runnable runnable)
    {
        activity.runOnUiThread(runnable);
    }

    public UIRenderType getUIRenderer()
    {
        return mgr;
    }

    public TextView getCaptionsText()
    {
        return this.captionsText;
    }
}
