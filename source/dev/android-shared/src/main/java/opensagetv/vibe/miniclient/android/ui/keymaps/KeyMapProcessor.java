package opensagetv.vibe.miniclient.android.ui.keymaps;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.AndroidKeyEventMapper;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.UIActivityLifeCycleHandler;
import opensagetv.vibe.miniclient.android.preferences.MediaMappingPreferences;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.EventRouter;
import opensagetv.vibe.miniclient.uibridge.Keys;
import opensagetv.vibe.miniclient.util.VerboseLogging;


/**
 * Modified by jvl711 on 7/8/2018 - Add customizable mappings.  Allow repeat on the key presses.
 *
 * Created by seans on 26/09/15.
 */
public class KeyMapProcessor {
    // hack from the onBackPressed so that we don't process it twice
    public static boolean skipBackOneTime = false;
    private final AudioManager am;
    private final boolean soundEffects;

    /**
     * NOTE:
     * HOME cannot be easily mapped to another Event.  Used to be able to do that, not any more.
     *
     * jvl711
     * NOTE: I thik there are some other keys that might also be difficult to remap.  For instance Volume Up/Volume Down
     */

    protected Logger log = LoggerFactory.getLogger(this.getClass());
    protected static String PUNCTUATION = "`~!@#$%^&*()_+{}|:\" <>?-=[];'./\\,";
    protected static AndroidKeyEventMapper keyEventMapper = new AndroidKeyEventMapper();

    protected final MiniClient client;
    protected int flircMeta = 0;
    boolean skipUp = false;
    boolean longPress = false;
    boolean longPressCancel = false;
    long longPressTime = 0;

    protected Context context;
    private MediaMappingPreferences prefs;
    private final MediaMappingPreferences dvdPlaybackPrefs;
    private UIActivityLifeCycleHandler uiHandler;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingLongPressTask;
    private int pendingLongPressKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private Runnable pendingDvdSkipPulse;
    private MiniPlayerPlugin dvdSkipPlayer;
    private opensagetv.vibe.miniclient.MiniClientConnection dvdSkipConnection;
    private int dvdSkipGeneration;
    private DvdSkipPulsePolicy dvdSkipPolicy;
    private Runnable pendingDvdHeldInput;
    private int heldDvdKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int suppressedDvdKeyUp = KeyEvent.KEYCODE_UNKNOWN;
    private long suppressedDvdDownTime;
    private long heldDvdDownTime;
    private MiniPlayerPlugin heldDvdPlayer;
    private opensagetv.vibe.miniclient.MiniClientConnection heldDvdConnection;
    private final DvdNavigationOverlay dvdNavigationOverlay;
    private final DvdTimeScrollController dvdTimeScrollController;
    private final DvdScanGestureController dvdScanGestureController;
    // Long-press state belongs to one physical key gesture. Fire OS can
    // occasionally omit ACTION_UP when playback is rebuilding after a seek;
    // without this identity, the next Left/Right DOWN inherits the previous
    // gesture's long-press mapping (for example, Comskip instead of FF/RW).
    private int activeGestureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long activeGestureDownTime = -1L;
    private static volatile long inputEventSequence;
    private static volatile int inputLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private static volatile int inputLastScanCode;
    private static volatile int inputLastAction = -1;
    private static volatile int inputLastRepeatCount;
    private static volatile int inputLastSource;
    private static volatile int inputLastDeviceId = -1;
    private static volatile boolean inputLastLongPress;
    private static volatile String inputLastKeyName = "KEYCODE_UNKNOWN";
    private static volatile String inputLastMappedCommand = "";
    private static volatile boolean dvdVirtualSkipActive;
    private static volatile long dvdVirtualSkipTargetMs;
    private static volatile String dvdVirtualSkipResult = "idle";

    public KeyMapProcessor(MiniClient client, MediaMappingPreferences prefs, AudioManager am, UIActivityLifeCycleHandler uiHandler)
    {
        this.client = client;
        this.prefs = prefs;
        this.dvdPlaybackPrefs = new MediaMappingPreferences("dvdplaying",
                client.properties());
        this.am = am;
        this.uiHandler = uiHandler;
        this.soundEffects = prefs.isSoundEffectsEnabled();
        this.dvdNavigationOverlay = new DvdNavigationOverlay(client);
        this.dvdTimeScrollController = new DvdTimeScrollController(client, dvdPlaybackPrefs, uiHandler);
        this.dvdScanGestureController = new DvdScanGestureController(client, uiHandler, dvdPlaybackPrefs);
        
    }

    public boolean onKey(KeyMap keyMap, int keyCode, KeyEvent event)
    {
        recordInputEvent(keyCode, event);
        if (dvdTimeScrollController.handle(keyMap, keyCode, event))
        {
            dvdScanGestureController.abandon();
            cancelDvdHeldInput(false);
            cancelDvdSkipPulse(false);
            dvdNavigationOverlay.hide(); // STV owns the selected position.
            resetInputGestureState();
            recordMappedCommandName(dvdTimeScrollController.action(), false);
            return true;
        }
        if (dvdScanGestureController.handle(keyMap, keyCode, event))
        {
            cancelDvdHeldInput(false);
            cancelDvdSkipPulse(false);
            dvdNavigationOverlay.hide(); // Only the STV timeline belongs on scans.
            resetInputGestureState();
            recordMappedCommandName(dvdScanGestureController.action(), false);
            return true;
        }
        if (handleDvdHeldArrow(keyCode, event)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN)
            beginInputGesture(keyCode, event);
        else if (event.getAction() == KeyEvent.ACTION_UP
                && activeGestureKeyCode != KeyEvent.KEYCODE_UNKNOWN
                && activeGestureKeyCode != keyCode)
        {
            // A delayed UP from an abandoned gesture must not clear the newer
            // key that is now active.
            log.debug("Ignoring stale key-up {} while gesture {} is active",
                    keyCode, activeGestureKeyCode);
            return true;
        }

        if(!longPress && getDvdMenuCommand(keyCode) == null
                && keyMap.getNormalPressCommand(keyCode) == SageCommand.NONE)
        {
            log.debug("Key is mapped to none...");
            if (event.getAction() == KeyEvent.ACTION_UP)
                finishInputGesture();
            //handleDefaultEvent(keyCode, event);
            return false;
        }
        
        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            if (longPressCancel) return true;

            if (VerboseLogging.LOG_KEYS)
                log.debug("LONG DOWN: {} - {} -- {}", event.getEventTime(), event.getDownTime(), event);

            // Android/Fire OS can report a held remote button in two valid
            // forms: repeated DOWN events with a stable downTime, or a DOWN
            // event carrying FLAG_LONG_PRESS. Keep the historical elapsed-time
            // fallback, but also honor the platform signal so remotes that do
            // not preserve the older repeat timing can still open the OSD.
            boolean platformLongPress = event.isLongPress()
                    || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0;
            boolean elapsedLongPress = event.getEventTime() - event.getDownTime()
                    > keyMap.getKeyRepeatDelayMS(keyCode);
            if (!longPress && (platformLongPress || elapsedLongPress)) {
                cancelPendingLongPress();
                longPress = true;
                longPressTime = event.getEventTime();

                if (VerboseLogging.LOG_KEYS)
                    log.debug("FIRE: LongPress {} {}", longPressTime, event);

                handleKeyPress(keyMap, keyCode, event, longPress);

                // some long press actions might only want to be processed once, like, back, or select
                if (keyMap.shouldCancelLongPress(keyCode)
                        || isDvdTitleDirectionGesture(keyCode)) {
                    if (VerboseLogging.LOG_KEYS)
                        log.debug("Cancel LongPress Repeats {}", event);
                    longPressCancel = true;
                }

                return true;
            }

            if (!longPress && event.getRepeatCount() == 0)
                schedulePendingLongPress(keyMap, keyCode, event);

            // if longpress has started, check for repeats
            if (longPress && event.getEventTime() - longPressTime > keyMap.getKeyRepeatRateMS(keyCode)) {
                if (VerboseLogging.LOG_KEYS)
                    log.debug("FIRE: LongPress {} Repeat {} - {}", longPressTime, event.getEventTime(), event);
                longPressTime = event.getEventTime();
                handleKeyPress(keyMap, keyCode, event, longPress);
                return true;
            } else {
                // for navigation keys we fire them once, and then start the delay/repeat loops
                // ie, down will move down, but when held it will start repeating after the delay
                if (!skipUp && keyMap.isNavigationKey(keyCode)
                        && !isDvdTitleDirectionGesture(keyCode)) {
                    skipUp = true;
                    handleKeyPress(keyMap, keyCode, event, false);
                    return true;
                } else {
                    // waiting for longpress/repeat
                    return true;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            // Some Fire TV remotes emit one DOWN and one delayed UP for the
            // center button, without a repeat DOWN or FLAG_LONG_PRESS. Detect
            // that valid hold form on release so it is not misclassified as a
            // normal Select. Navigation keys intentionally retain their first
            // normal DOWN action, matching the established repeat behavior.
            boolean releaseLongPress = !longPress
                    && keyMap.hasLongPress(keyCode)
                    && event.getEventTime() - event.getDownTime()
                    > keyMap.getKeyRepeatDelayMS(keyCode);
            cancelPendingLongPress();
            if (releaseLongPress)
            {
                handleKeyPress(keyMap, keyCode, event, true);
                longPressTime = 0;
                longPress = false;
                longPressCancel = false;
                skipUp = false;
                finishInputGesture();
                return true;
            }
            if (longPress || skipUp) {
                if (VerboseLogging.LOG_KEYS)
                    log.debug("Long Press UP: Do Nothing.");
                longPressTime = 0;
                longPress = false;
                longPressCancel = false;
                skipUp = false;
            } else {
                if (VerboseLogging.LOG_KEYS)
                    log.debug("UP: {}", event);

                // pretty much all normal keyboard keys will get handled here
                handleKeyPress(keyMap, keyCode, event, false);
            }
            finishInputGesture();
        }

        return true;
    }

    private void schedulePendingLongPress(final KeyMap keyMap, final int keyCode,
                                          final KeyEvent event)
    {
        cancelPendingLongPress();
        if (!keyMap.hasLongPress(keyCode))
            return;

        final int delayMs = keyMap.getKeyRepeatDelayMS(keyCode);
        if (delayMs < 0)
            return;

        final KeyEvent heldEvent = new KeyEvent(event);
        pendingLongPressKeyCode = keyCode;
        pendingLongPressTask = new Runnable()
        {
            @Override
            public void run()
            {
                if (pendingLongPressTask != this || pendingLongPressKeyCode != keyCode)
                    return;
                pendingLongPressTask = null;
                pendingLongPressKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                if (longPress || longPressCancel)
                    return;

                longPress = true;
                longPressTime = android.os.SystemClock.uptimeMillis();
                handleKeyPress(keyMap, keyCode, heldEvent, true);
                if (keyMap.shouldCancelLongPress(keyCode)
                        || isDvdTitleDirectionGesture(keyCode))
                    longPressCancel = true;
            }
        };
        longPressHandler.postDelayed(pendingLongPressTask, delayMs);
    }

    private void cancelPendingLongPress()
    {
        if (pendingLongPressTask != null)
            longPressHandler.removeCallbacks(pendingLongPressTask);
        pendingLongPressTask = null;
        pendingLongPressKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    }

    private void beginInputGesture(int keyCode, KeyEvent event)
    {
        boolean firstDown = event.getRepeatCount() == 0;
        boolean differentKey = activeGestureKeyCode != KeyEvent.KEYCODE_UNKNOWN
                && activeGestureKeyCode != keyCode;
        boolean restartedKey = firstDown
                && activeGestureKeyCode == keyCode
                && activeGestureDownTime != event.getDownTime();

        if (differentKey || restartedKey)
        {
            log.warn("Resetting stale key gesture {} before key {} down",
                    activeGestureKeyCode, keyCode);
            resetInputGestureState();
        }

        if (activeGestureKeyCode == KeyEvent.KEYCODE_UNKNOWN || firstDown)
        {
            activeGestureKeyCode = keyCode;
            activeGestureDownTime = event.getDownTime();
        }
    }

    private void finishInputGesture()
    {
        activeGestureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        activeGestureDownTime = -1L;
    }

    private void resetInputGestureState()
    {
        cancelPendingLongPress();
        longPressTime = 0;
        longPress = false;
        longPressCancel = false;
        skipUp = false;
        finishInputGesture();
    }

    public void shutdown()
    {
        // Home/Activity teardown owns playback. Never send a late Play from
        // a queued skip timer into a background or replacement session.
        cancelDvdSkipPulse(false);
        cancelDvdHeldInput(false);
        dvdNavigationOverlay.hide();
        dvdTimeScrollController.shutdown();
        dvdScanGestureController.shutdown();
        resetInputGestureState();
    }

    private boolean handleDvdHeldArrow(int keyCode, KeyEvent event)
    {
        boolean arrow = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == suppressedDvdKeyUp
                && event.getDownTime() == suppressedDvdDownTime)
        {
            suppressedDvdKeyUp = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (pendingDvdHeldInput != null)
        {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == heldDvdKeyCode
                    && event.getDownTime() == heldDvdDownTime)
            {
                cancelDvdHeldInput(true);
                suppressedDvdKeyUp = KeyEvent.KEYCODE_UNKNOWN;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == heldDvdKeyCode
                    && event.getDownTime() == heldDvdDownTime) return true;
            if (event.getAction() == KeyEvent.ACTION_UP) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN)
            {
                boolean otherScan = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
                boolean terminating = keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK
                        || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND;
                cancelDvdHeldInput(!otherScan && !terminating);
            }
        }
        if (!arrow || !dvdPlaybackPrefs.isDvdHeldArrowControlsEnabled()
                || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0)
            return false;
        MiniPlayerPlugin active = client.getPlayer();
        opensagetv.vibe.miniclient.MiniClientConnection connection = client.getCurrentConnection();
        if (active == null || connection == null || connection.getMediaCmd() == null
                || !connection.getMediaCmd().isDvdSessionPending() || !client.isVideoVisible()
                || active.isDvdMenuNavigationActive() || !active.supportsNativeDvdSkipPulse()
                || active.getState() != MiniPlayerPlugin.PLAY_STATE
                || !DvdInputContext.isFullscreen(client)) return false;
        cancelDvdSkipPulse(false);
        resetInputGestureState();
        heldDvdKeyCode = keyCode;
        heldDvdDownTime = event.getDownTime();
        heldDvdPlayer = active;
        heldDvdConnection = connection;
        final long started = android.os.SystemClock.elapsedRealtime();
        final long[] lastChapterAt = {started};
        recordMappedCommandName(keyCode == KeyEvent.KEYCODE_DPAD_UP
                ? "DVD_HOLD_CHAPTER_NEXT_PENDING" : "DVD_HOLD_CHAPTER_PREV_PENDING", false);
        pendingDvdHeldInput = new Runnable()
        {
            @Override public void run()
            {
                if (pendingDvdHeldInput != this) return;
                if (client.getCurrentConnection() != connection || client.getPlayer() != active
                        || !client.isConnected() || !client.isVideoVisible() || active.isDvdMenuNavigationActive()
                        || !DvdInputContext.isFullscreen(client)
                        || active.getState() == MiniPlayerPlugin.PAUSE_STATE
                        || active.getState() == MiniPlayerPlugin.STOPPED_STATE
                        || active.getState() == MiniPlayerPlugin.EOS_STATE)
                {
                    cancelDvdHeldInput(false);
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                if (DvdHeldArrowPolicy.chapterDue(now - started, now - lastChapterAt[0]))
                {
                    lastChapterAt[0] = now;
                    dvdNavigationOverlay.show(keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ? "Next chapter" : "Previous chapter", true);
                    SageCommand command = keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ? SageCommand.DVD_CHAPTER_NEXT : SageCommand.DVD_CHAPTER_PREV;
                    recordMappedCommandName(keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ? "DVD_HOLD_CHAPTER_NEXT" : "DVD_HOLD_CHAPTER_PREV", true);
                    EventRouter.postCommand(client, command);
                }
                longPressHandler.postDelayed(this, 50L);
            }
        };
        longPressHandler.postDelayed(pendingDvdHeldInput, 50L);
        return true;
    }

    private void cancelDvdHeldInput(boolean resume)
    {
        if (pendingDvdHeldInput == null) return;
        longPressHandler.removeCallbacks(pendingDvdHeldInput);
        pendingDvdHeldInput = null;
        int key = heldDvdKeyCode;
        suppressedDvdKeyUp = key;
        suppressedDvdDownTime = heldDvdDownTime;
        heldDvdKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        heldDvdPlayer = null;
        heldDvdConnection = null;
        if (resume) dvdNavigationOverlay.release();
        else dvdNavigationOverlay.hide();
    }

    private void handleKeyPress(KeyMap keyMap, int keyCode, KeyEvent event, boolean longPress) {

        if (pendingDvdSkipPulse != null
                && (longPress || (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)))
        {
            // Dedicated scan/Play/Stop takes ownership directly; other UI
            // actions first end the short scan. Arrow holds stay chapters.
            boolean resume = keyCode != KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    && keyCode != KeyEvent.KEYCODE_MEDIA_REWIND
                    && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY
                    && keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE
                    && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    && keyCode != KeyEvent.KEYCODE_MEDIA_STOP;
            cancelDvdSkipPulse(resume);
        }

        SageCommand dvdMenuCommand = getDvdMenuCommand(keyCode);
        if(!longPress && dvdMenuCommand == null
                && keyMap.getNormalPressCommand(keyCode) == SageCommand.NONE)
        {
            log.debug("Key is mapped to none...");
            //handleDefaultEvent(keyCode, event);
            return;
        }

        if(uiHandler.isKeyboardVisible())
        {
            log.debug("KEYBOARD IS VISIBLE");
            if(keyCode == KeyEvent.KEYCODE_ENTER)
            {
                uiHandler.showHideKeyboard(false);
                return;
            }
        }
        else if(!uiHandler.isKeyboardVisible() && client.getCurrentConnection().getMenuHint().hasMenuLike("Main Menu")
                && client.getCurrentConnection().getMenuHint().hasTextInput == true)
        {
            if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            {
                uiHandler.showHideKeyboard(true);
                return;
            }
        }
    
        // this is really a hack because of the on screen controls
        // when we close them, we don't want to process the "back"
        if (skipBackOneTime)
        {
            skipBackOneTime = false;
            if (keyCode == KeyEvent.KEYCODE_BACK)
            {
                log.debug("Skipping Back Event one time.");
                return;
            }
        }

        if (prefs.debugKeyPresses())
        {
            client.eventbus().post(new DebugKeyEvent(keyCode, event, longPress, keyEventMapper.getFieldName(event.getKeyCode())));
        }

        playClickSound();

        // A video playback key map normally assigns left/right to skips.  An
        // authored DVD menu uses the same physical keys for button navigation.
        // Override only while a real server-supplied button highlight is
        // active; once NEWCELL/clear-highlight arrives, normal mappings apply.
        // A short center/arrow press belongs to the authored DVD menu, but a
        // long press must retain the configured Android key-map action (the
        // navigation/player-options dialog by default).  Consuming both forms
        // here made the remote long-press menu inaccessible during DVD menus.
        if (!longPress && dvdMenuCommand != null)
        {
            if (VerboseLogging.LOG_KEYS)
                log.debug("Sending DVD menu command {} for Event {}", dvdMenuCommand, event);
            recordMappedCommand(dvdMenuCommand, false);
            EventRouter.postCommand(client, dvdMenuCommand);
            return;
        }

        // A DVD title and an authored DVD menu need different meanings for
        // the same Android-TV direction keys.  Keep the local highlight gate
        // above authoritative for menus.  Outside a highlighted menu, use the
        // historical combined extender events for a short Left/Right press.
        // Stock PseudoMenu has ordinary +/- skip fallbacks, but an STV can
        // consume their tertiary FF/REW event first (SageMC does). Never
        // describe the fallback as a guaranteed STV-independent seek. Holds
        // use the explicit chapter commands; non-DVD playback retains the
        // user's configured mappings.
        SageCommand dvdTitleCommand = getDvdTitlePlaybackCommand(keyCode, longPress);
        if (dvdTitleCommand != null)
        {
            if (!longPress && (dvdTitleCommand == SageCommand.RIGHT_FF
                    || dvdTitleCommand == SageCommand.LEFT_REW)
                    && beginDvdSkipPulse(dvdTitleCommand == SageCommand.RIGHT_FF ? 1 : -1))
                return;
            if (VerboseLogging.LOG_KEYS)
                log.debug("Sending DVD title command {} for Event {}",
                        dvdTitleCommand, event);
            recordMappedCommand(dvdTitleCommand, longPress);
            if (dvdTitleCommand != SageCommand.NONE)
                EventRouter.postCommand(client, dvdTitleCommand);
            return;
        }

        SageCommand command = null;

        if (keyMap.hasSageCommandOverride(keyCode, longPress))
        {
            recordMappedCommandName("CLIENT_OVERRIDE", longPress);
            keyMap.performSageCommandOverride(keyCode, client, longPress);
            return;
        }

        if (longPress && keyMap.hasLongPress(keyCode)) {
            command = keyMap.getLongPressCommand(keyCode);
        }

        if (!longPress || command == null) {
            command = keyMap.getNormalPressCommand(keyCode);
        }

        if (command != null) {
            if (command == SageCommand.FF || command == SageCommand.REW)
                dvdNavigationOverlay.hide();
            if (command == SageCommand.DVD_CHAPTER_NEXT || command == SageCommand.DVD_CHAPTER_PREV)
            {
                MiniPlayerPlugin active = client.getPlayer();
                if (active != null && active.supportsNativeDvdSkipPulse()
                        && client.getCurrentConnection() != null
                        && client.getCurrentConnection().getMediaCmd().isDvdSessionPending())
                    dvdNavigationOverlay.show("Chapter", false);
            }
            if (sendDvdOppositeScanSequence(command, longPress))
                return;
            // Keep SageMC's existing FF/REW listeners authoritative for its
            // rate label. Generic Faster/Slower changes only Core's rate and
            // leaves DVDPlaybackRate (the STV label) unchanged.
            command = resolveDvdScanCommand(command);
            if (command == SageCommand.NONE)
            {
                recordMappedCommand(command, longPress);
                return;
            }
            // NAV_OSD is a client-local Android dialog, not a SageTV server
            // command.  Invoke its lifecycle owner directly so showing it
            // cannot be lost when an event-bus listener is between lifecycle
            // registration transitions.  Other local gestures may continue
            // using EventRouter; physical remote long press needs the
            // deterministic foreground UI path.
            if (longPress && command == SageCommand.NAV_OSD)
            {
                recordMappedCommand(command, true);
                uiHandler.showHideSoftRemote(true);
                return;
            }
            if (VerboseLogging.LOG_KEYS)
                log.debug("Sending Sage Command {} for Event {}", command, event);
            recordMappedCommand(command, longPress);
            // SageMC commonly maps long-Right to commercial skip. The client
            // must not guess its destination or emit another command. Merely
            // arm the active player here; recovery becomes eligible only if
            // stock SageTV subsequently sends both FLUSH and a new Push anchor.
            if (longPress && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
            {
                MiniPlayerPlugin activePlayer = client.getPlayer();
                if (activePlayer != null)
                    activePlayer.armPostSeekPushRecovery(command);
            }
            EventRouter.postCommand(client, command);
            return;
        }

        // this is normal keys like a,b,c, etc.
        handleDefaultEvent(keyCode, event);
    }

    private static synchronized void recordInputEvent(int keyCode, KeyEvent event)
    {
        inputEventSequence++;
        inputLastKeyCode = keyCode;
        inputLastKeyName = KeyEvent.keyCodeToString(keyCode);
        inputLastScanCode = event == null ? 0 : event.getScanCode();
        inputLastAction = event == null ? -1 : event.getAction();
        inputLastRepeatCount = event == null ? 0 : event.getRepeatCount();
        inputLastSource = event == null ? 0 : event.getSource();
        inputLastDeviceId = event == null ? -1 : event.getDeviceId();
        inputLastLongPress = false;
        inputLastMappedCommand = "";
    }

    private static void recordMappedCommand(SageCommand command, boolean longPress)
    {
        recordMappedCommandName(command == null ? "" : command.name(), longPress);
    }

    private static synchronized void recordMappedCommandName(String command, boolean longPress)
    {
        inputLastMappedCommand = command == null ? "" : command;
        inputLastLongPress = longPress;
    }

    public static long getInputEventSequenceForDebug() { return inputEventSequence; }
    public static int getInputLastKeyCodeForDebug() { return inputLastKeyCode; }
    public static String getInputLastKeyNameForDebug() { return inputLastKeyName; }
    public static int getInputLastScanCodeForDebug() { return inputLastScanCode; }
    public static int getInputLastActionForDebug() { return inputLastAction; }
    public static int getInputLastRepeatCountForDebug() { return inputLastRepeatCount; }
    public static int getInputLastSourceForDebug() { return inputLastSource; }
    public static int getInputLastDeviceIdForDebug() { return inputLastDeviceId; }
    public static boolean isInputLastLongPressForDebug() { return inputLastLongPress; }
    public static String getInputLastMappedCommandForDebug() { return inputLastMappedCommand; }
    public static boolean isDvdVirtualSkipActiveForDebug() { return dvdVirtualSkipActive; }
    public static long getDvdVirtualSkipTargetMsForDebug() { return dvdVirtualSkipTargetMs; }
    public static String getDvdVirtualSkipResultForDebug() { return dvdVirtualSkipResult; }
    public static boolean isDvdTimeScrollActiveForDebug() { return DvdTimeScrollController.isActiveForDebug(); }
    public static long getDvdTimeScrollEntryPositionForDebug() { return DvdTimeScrollController.entryPositionForDebug(); }
    public static int getDvdTimeScrollStepsForDebug() { return DvdTimeScrollController.stepsForDebug(); }

    private SageCommand getDvdMenuCommand(int keyCode)
    {
        try
        {
            if (client == null || !client.isVideoVisible() || !DvdInputContext.isFullscreen(client)
                    || !client.getPlayer().isDvdMenuNavigationActive())
                return null;
        }
        catch (RuntimeException unavailable)
        {
            return null;
        }

        switch (keyCode)
        {
            // A dedicated DVD remote labels this key Return. Android TV
            // remotes expose only Back, so route it to the authored-disc VM
            // while a real DVD button highlight is active. Falling through to
            // SageCommand.BACK exits fullscreen/SageMC instead of returning
            // from the submenu and leaves the disc stranded in preview.
            case KeyEvent.KEYCODE_BACK:
                return SageCommand.DVD_RETURN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return SageCommand.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return SageCommand.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return SageCommand.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return SageCommand.DOWN;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return SageCommand.SELECT;
            default:
                return null;
        }
    }

    private SageCommand getDvdTitlePlaybackCommand(int keyCode, boolean longPress)
    {
        try
        {
            if (client == null || !client.isVideoVisible() || !DvdInputContext.isFullscreen(client)
                    || client.getCurrentConnection() == null
                    || client.getCurrentConnection().getMediaCmd() == null
                    || !client.getCurrentConnection().getMediaCmd().isDvdSessionPending()
                    || client.getPlayer() == null
                    || client.getPlayer().isDvdMenuNavigationActive())
                return null;
        }
        catch (RuntimeException unavailable)
        {
            return null;
        }

        switch (keyCode)
        {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return longPress ? dvdPlaybackPrefs.getLeftLongPress()
                        : dvdPlaybackPrefs.getLeft();
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return longPress ? dvdPlaybackPrefs.getRightLongPress()
                        : dvdPlaybackPrefs.getRight();
            default:
                return null;
        }
    }

    private SageCommand resolveDvdScanCommand(SageCommand command)
    {
        try
        {
            MiniPlayerPlugin active = client.getPlayer();
            if (active == null || !client.isVideoVisible() || !DvdInputContext.isFullscreen(client)
                    || active.isDvdMenuNavigationActive()
                    || client.getCurrentConnection() == null
                    || client.getCurrentConnection().getMediaCmd() == null
                    || !client.getCurrentConnection().getMediaCmd().isDvdSessionPending())
                return command;
            return DvdRemoteScanPolicy.resolve(command, active.getPlaybackRate());
        }
        catch (RuntimeException unavailable)
        {
            return command;
        }
    }

    private boolean beginDvdSkipPulse(final int direction)
    {
        final MiniPlayerPlugin active = client.getPlayer();
        if (active == null || !active.supportsNativeDvdSkipPulse()) return false;
        if (pendingDvdSkipPulse != null && active == dvdSkipPlayer
                && client.getCurrentConnection() == dvdSkipConnection)
        {
            dvdSkipPolicy.enqueue(direction, active.getMediaTimeMillis(0L),
                    android.os.SystemClock.elapsedRealtime());
            dvdVirtualSkipTargetMs = dvdSkipPolicy.targetMs();
            recordMappedCommandName(direction > 0 ? "DVD_SKIP_PULSE_RIGHT" : "DVD_SKIP_PULSE_LEFT", false);
            return true;
        }
        cancelDvdSkipPulse(true);
        final opensagetv.vibe.miniclient.MiniClientConnection connection = client.getCurrentConnection();
        final long startMs = active.getMediaTimeMillis(0L);
        if (startMs < 0L) return false;
        final DvdSkipPulsePolicy policy = new DvdSkipPulsePolicy(startMs, direction,
                android.os.SystemClock.elapsedRealtime());
        if (policy.reached(startMs)) return true;
        final int generation = ++dvdSkipGeneration;
        dvdSkipPlayer = active;
        active.setNativeDvdSkipPulseActive(true);
        dvdSkipConnection = connection;
        dvdSkipPolicy = policy;
        dvdVirtualSkipTargetMs = policy.targetMs();
        dvdVirtualSkipResult = "running";
        dvdVirtualSkipActive = true;
        final int[] requestedDirection = {direction};
        recordMappedCommandName(direction > 0 ? "DVD_SKIP_PULSE_RIGHT" : "DVD_SKIP_PULSE_LEFT", false);
        // Smooth FF/REW are ordinary stock Core rate controls. Unlike SageMC's
        // FF listener they do not set DisplayDVDControls or the FF scan OSD.
        EventRouter.postCommand(client, direction > 0 ? SageCommand.SMOOTH_FF : SageCommand.SMOOTH_REW);
        pendingDvdSkipPulse = new Runnable()
        {
            @Override public void run()
            {
                if (generation != dvdSkipGeneration) return;
                if (!client.isConnected() || client.getCurrentConnection() != connection
                        || client.getPlayer() != active || !client.isVideoVisible()
                        || !DvdInputContext.isFullscreen(client)
                        || active.isDvdMenuNavigationActive()
                        || active.getState() == MiniPlayerPlugin.PAUSE_STATE
                        || active.getState() == MiniPlayerPlugin.EOS_STATE
                        || active.getState() == MiniPlayerPlugin.STOPPED_STATE)
                {
                    cancelDvdSkipPulse(false);
                    return;
                }
                long sourceMs = active.getMediaTimeMillis(0L);
                boolean expired = policy.expired(android.os.SystemClock.elapsedRealtime());
                if (policy.reached(sourceMs) || expired)
                {
                    dvdVirtualSkipResult = policy.reached(sourceMs) ? "target_reached" : "timeout";
                    log.info("DVD skip pulse ended: requested={} observed={} timedOut={}",
                            policy.targetMs(), sourceMs, expired);
                    cancelDvdSkipPulse(true);
                    return;
                }
                int nextDirection = policy.direction();
                if (nextDirection != requestedDirection[0])
                {
                    requestedDirection[0] = nextDirection;
                    EventRouter.postCommand(client, nextDirection > 0 ? SageCommand.SMOOTH_FF : SageCommand.SMOOTH_REW);
                }
                // A short skip is not a high-speed scan. Keep the stock 4x
                // pulse so VOBU granularity and queued decoder references do
                // not turn a 10-second request into a 16x overshoot.
                longPressHandler.postDelayed(this, 50L);
            }
        };
        longPressHandler.postDelayed(pendingDvdSkipPulse, 50L);
        return true;
    }

    private void cancelDvdSkipPulse(boolean resume)
    {
        if (pendingDvdSkipPulse == null) return;
        longPressHandler.removeCallbacks(pendingDvdSkipPulse);
        pendingDvdSkipPulse = null;
        dvdVirtualSkipActive = false;
        if ("running".equals(dvdVirtualSkipResult)) dvdVirtualSkipResult = "cancelled";
        ++dvdSkipGeneration;
        MiniPlayerPlugin oldPlayer = dvdSkipPlayer;
        opensagetv.vibe.miniclient.MiniClientConnection oldConnection = dvdSkipConnection;
        dvdSkipPlayer = null;
        dvdSkipConnection = null;
        dvdSkipPolicy = null;
        if (!resume && oldPlayer != null) oldPlayer.setNativeDvdSkipPulseActive(false);
        if (resume && client.isConnected() && client.getCurrentConnection() == oldConnection
                && client.getPlayer() == oldPlayer && oldPlayer != null
                && oldPlayer.getState() != MiniPlayerPlugin.PAUSE_STATE
                && oldPlayer.getState() != MiniPlayerPlugin.EOS_STATE
                && oldPlayer.getState() != MiniPlayerPlugin.STOPPED_STATE)
            EventRouter.postCommand(client, SageCommand.PLAY);
    }

    private boolean sendDvdOppositeScanSequence(SageCommand requested, boolean longPress)
    {
        try
        {
            MiniPlayerPlugin active = client.getPlayer();
            if (active == null || !client.isVideoVisible() || !DvdInputContext.isFullscreen(client)
                    || active.isDvdMenuNavigationActive()
                    || client.getCurrentConnection() == null
                    || client.getCurrentConnection().getMediaCmd() == null
                    || !client.getCurrentConnection().getMediaCmd().isDvdSessionPending())
                return false;
            SageCommand[] sequence = DvdRemoteScanPolicy.sequence(requested,
                    active.getPlaybackRate());
            if (sequence.length <= 1) return false;
            recordMappedCommandName("DVD_SCAN_STEP_DOWN", longPress);
            for (SageCommand command : sequence)
                EventRouter.postCommand(client, command);
            return true;
        }
        catch (RuntimeException unavailable)
        {
            return false;
        }
    }

    /**
     * DVD title Left/Right has two mutually exclusive actions: a tap seeks and
     * a deliberate hold changes chapter.  Ordinary navigation sends its short
     * action on ACTION_DOWN for responsive menus, but doing that here made one
     * hold send both a seek and a chapter command.  Defer only these title
     * gestures until release or the hold timer; authored menu arrows remain
     * immediate through {@link #getDvdMenuCommand(int)}.
     */
    private boolean isDvdTitleDirectionGesture(int keyCode)
    {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)
            return false;
        return getDvdTitlePlaybackCommand(keyCode, false) != null;
    }

    private void handleDefaultEvent(int keyCode, KeyEvent event) {
        if (VerboseLogging.LOG_KEYS)
            log.debug("Handle Default Key Event: {} {}", keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            flircMeta += Keys.CTRL_MASK;
            if (VerboseLogging.LOG_KEYS)
                log.debug("FLIRC Meta Ctrl");
            return;
        }

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            flircMeta += Keys.SHIFT_MASK;
            if (VerboseLogging.LOG_KEYS)
                log.debug("FLIRC Meta Shift");
            return;
        }

        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            flircMeta += Keys.ALT_MASK;
            if (VerboseLogging.LOG_KEYS)
                log.debug("FLIRC Meta Alt");
            return;
        }

        if (flircMeta > 0) {
            try {
                processFlircKeyMetaKey(keyCode, event);
                return;
            } finally {
                if (VerboseLogging.LOG_KEYS)
                    log.debug("Resetting Flirc Meta");
                flircMeta = 0;
            }
        }

        // Check to see if this is a keyboard command
        if ((keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) || (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_TAB || PUNCTUATION.indexOf(event.getUnicodeChar()) != -1) {
            char toSend = (char) event.getUnicodeChar();

            if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                toSend = (char) event.getUnicodeChar(KeyEvent.META_SHIFT_LEFT_ON);
            }

            client.getCurrentConnection().postKeyEvent(toSend, androidToSageKeyModifier(event), (char) event.getUnicodeChar());

            return;
        }

        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            //F1 Virtual Code = 112
            //F1 KeyCode = 131

            //KeyEvent.KEYCODE_PAGE_DOWN = 93
            //Keys.VK_PAGE_DOWN = 34

            client.getCurrentConnection().postKeyEvent((keyCode - 19), 0, (char) (keyCode - 19));
            return;
        }

        if (client.properties().getBoolean(PrefStore.Keys.debug_log_unmapped_keypresses, false)) {
            if (VerboseLogging.LOG_KEYS)
                log.debug("KEYS: Unmapped Key Code: {}", event);

            try {
                Toast.makeText(MiniclientApplication.get(), "UNMAPPED KEY: " + keyEventMapper.getFieldName(event.getKeyCode()), Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
            }
        }
    }

    private void processFlircKeyMetaKey(int keyCode, KeyEvent event)
    {
        if ((keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) || (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_TAB || PUNCTUATION.indexOf(event.getUnicodeChar()) != -1)
        {
            char toSend = (char) event.getUnicodeChar();

            if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z)
            {
                toSend = (char) event.getUnicodeChar(KeyEvent.META_SHIFT_LEFT_ON);
            }

            if (VerboseLogging.LOG_KEYS)
                log.debug("FLIRC: Sending {} with meta: {}", String.valueOf(toSend), flircMeta);
            client.getCurrentConnection().postKeyEvent(toSend, flircMeta, (char) event.getUnicodeChar());
        }
    }

    protected int androidToSageKeyModifier(KeyEvent event)
    {
        int modifiers = 0;

        if (event.isShiftPressed())
        {
            modifiers = modifiers | Keys.SHIFT_MASK;
        }
        if (event.isCtrlPressed())
        {
            modifiers = modifiers | Keys.CTRL_MASK;
        }
        if (event.isAltPressed())
        {
            modifiers = modifiers | Keys.ALT_MASK;
        }

        return modifiers;
    }

    void playClickSound() {
        if (soundEffects) {
            float vol = 0.3f; //This will be half of the default system sound
            am.playSoundEffect(AudioManager.FX_KEY_CLICK, vol);
        }
    }
}
