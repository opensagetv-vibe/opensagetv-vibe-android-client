# Android framework modernization inventory

This document is the behavior-preserving inventory for Android modernization.
It covers the active `source/dev` tree only. The frozen `source/existing` tree
is a comparison baseline and must not be changed. Desktop-only code is outside
the Android runtime inventory.

No player, transport, timing, or protocol behavior may be changed merely to
remove a legacy API. Structural changes are made one owner at a time after the
physical baseline in `TASKS.md` has been captured.

## Scheduling and lifecycle inventory

`tests/test_framework_modernization_inventory.py` scans the active Java tree
for raw threads, timers, sleeps, handlers, and delayed callbacks. It fails when
a scheduling owner is not listed here.

| Owner | Current work and affinity | Cancellation / lifecycle today | Risk and migration boundary |
| --- | --- | --- | --- |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java` | Media/GFX/GFXRead workers use owner-held sockets; explicit Media readiness replaces the blind sleep; event FIFO, one-frame GFX exchange, remote file-transfer ownership, reconnect policy, replaceable protocol streams, and negotiated capability/profile configuration are extracted; the currently unused compatibility `java.util.Timer` remains connection-owned | Bounded diagnostics expose generation, workers, protocol counts, and queue depth. Close is idempotent, cancels the compatibility timer, closes transfer resources, and waits one shared bounded worker deadline | Host and physical ordering pass after every extraction in `CONNECTION_PROTOCOL_LIFECYCLE.md`; `ConnectionCapabilityProfile` now owns codec/container lists and common-profile fallback |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/GFXCMD2.java` and `Gfx*Commands.java` | One serial dispatcher retains native delegation, logging, and cursor policy; typed handlers own lifecycle/frame, drawing, image/cache, font, surface/video, and transform/batch families | No handler starts a thread; image/cache state and the shared handle allocator remain serialized by the GFX worker | Host characterization and physical OpenGL/GDX playback/reconnect/teardown PASS |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionWorkerOwner.java` | Retains four long-lived worker handles, Media readiness latch, Media/GFX sockets, and bounded join waits | Connection close closes sockets first, interrupts only the queue worker directly, skips joining the calling worker, and uses one two-second total deadline | Physically commissioned; preserve socket-before-worker-unblock order and the one shared deadline |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionEventRouter.java` | One named thread drains the original bounded 100-event FIFO | Connection owner interrupts and joins it; reconnect state still suppresses a dequeued event exactly as before | Physically commissioned; do not add parallel event writers or bypass `eventChannel` synchronization |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionReconnectState.java` | Owns negotiated reconnect support, first-frame eligibility, and the active reconnect flag | Volatile state is shared by GFX read and event producer threads; encrypted events remain ineligible | Focused JUnit plus physical startup/reconnect/teardown PASS |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionProtocolStreams.java` | Owns the replaceable GFX input and event-output wrappers | Replacing or closing a pair closes both directions idempotently; socket ownership remains with `ConnectionWorkerOwner` | Focused JUnit plus physical startup/reconnect/teardown PASS |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionFileTransferOwner.java` | Named daemon workers preserve concurrent remote file transfers | Connection close closes every retained socket/stream/file and interrupts each worker; completion unregisters itself | Host tested and covered by connection teardown; add a real remote-transfer physical gate before changing FS protocol behavior |
| `source/dev/core/src/test/java/opensagetv/vibe/miniclient/ConnectionWorkerOwnerTest.java` | Test-only sleeping workers exercise event interruption and one bounded join deadline | Every started worker is interrupted/joined before test return | Test-only owner |
| `source/dev/core/src/test/java/opensagetv/vibe/miniclient/ConnectionFileTransferOwnerTest.java` | Test-only polling waits for a four-byte transfer to unregister | Two-second deadline; production transfer owns and closes all supplied resources | Test-only owner |
| `source/dev/core/src/test/java/opensagetv/vibe/miniclient/GfxFrameExchangeTest.java` | Test-only reader blocks until the in-flight frame is completed | One-second latch/join bounds the test worker | Test-only owner |
| `source/dev/core/src/test/java/opensagetv/vibe/miniclient/uibridge/RendererReadinessGateTest.java` | Test-only waiter proves ready, cancel, late-ready, and interrupt behavior | Every test releases or interrupts and joins its worker within one second | Test-only owner |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/ServerDiscovery.java` | One named single-thread executor serializes asynchronous discovery requests and is recreated after shutdown | A replacement request or `close()` cancels pending work and closes the active socket; `MiniClient.shutdown()` terminates the executor without making later discovery reject work | Host tests and physical API-30 discovery/restart acceptance complete |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/PushBufferDataSource.java` | Producer/open and consumer/data waits use one notification monitor; non-zero reads block until bytes, EOS, close, or interruption | Open, data, flush, EOS, close, and release wake waiters; interruption is restored and returned as `IOException` | Focused JUnit plus physical Media3 and legacy-Exo hardware Push telemetry PASS |
| `source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/SimplePullDataSource.java` | The player-owned loader thread performs a bounded 100 ms file-size poll only after reaching a potentially growing SageTV file boundary | The poll is capped at two seconds and interruption is restored; datasource/socket close remains the lifecycle owner | Keep this synchronous with the media read so bytes cannot be reordered. Replace it only after the server protocol supplies explicit completed/growing state and growing-live tests exist |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java` | Retained single-thread connection executor preserves the `ANDROID-MINICLIENT` worker name; explicit main-looper Handler owns delayed keyboard work | Pause/destroy cancels the connection `Future`, invalidates its generation, rejects/closes late results, removes keyboard work, and destroy shuts down the executor | Connection and keyboard ownership pass host checks; connection launch physically passes reconnect, repeated playback, surface recreation, and teardown |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/BackgroundSessionOwner.java` | One explicit main-looper Handler serializes foreground/background transitions, the grace period, and the optional session timeout | Foreground return cancels pending background/timeout work; callbacks verify the retained connection and configured policy before acting | Host lifecycle tests pass; physical HOME/resume/timeout behavior remains an explicit compatibility gate |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerProcessOverlay.java` | One main-looper Handler owns the compact active-playback status overlay refresh and its bounded auto-hide deadline | Hide, replacement, or deadline removes both refresh and hide callbacks; every callback rechecks the current Activity and player snapshot | Debug/MCP visibility and deadline tests plus physical playback acceptance pass; the overlay does not own or alter playback |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/keymaps/KeyMapProcessor.java` plus the OpenGL/GDX MiniClient Activities | Activity-level dispatch owns physical remote keys above changing GL/decoder/subtitle focus; one main-looper Handler recognizes holds even when Fire OS emits no repeat DOWN event | Key UP cancels pending work; `MiniClientKeyListener.shutdown()` is called on replacement, pause, and destroy; DVD menu interception is short-press-only | Platform-flag, elapsed-repeat, release-duration, and bounded timer paths preserve the configured long-press mapping; physical AFTMM validation opens the complete navigation/player-controls dialog |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/connect/ConnectingActivity.java` | Explicit main-looper Handler drives delayed fullscreen/control changes through `AppUtil` | All callbacks are cleared during destroy | Completed host-side migration; physical activity/overlay acceptance remains |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/util/AudioFocusController.java` | Explicit main-looper Handler serializes audio-focus callbacks with playback lifecycle state | `abandon()` clears ownership, increments a generation, and rejects stale callbacks; transient loss resumes only when playback was active before loss | Modern API plus API-25 fallback are implemented and physically passed transient, duck, permanent loss, user-pause preservation, resumed A/V, and teardown on Media3 and legacy Exo. GSY System remains outside this gate |
| `source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainFragment.java` | Explicit main-looper Handler owns one delayed background-artwork Runnable | Replacement and `onDestroy()` remove the callback | Completed host-side ownership migration; physical selection/recreation acceptance remains |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/gdx/MiniClientGDXRenderer.java` | GFX initialization awaits shared `RendererReadinessGate`; resize publishes readiness | Close/deinit cancels and releases a waiter; interruption is preserved | Focused JUnit plus physical GDX startup/reconnect/HOME/teardown PASS |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/opengl/OpenGLRenderer.java` | GFX initialization awaits the same one-shot gate | Cancel cannot be reversed by a late resize | Focused JUnit plus physical OpenGL startup/reconnect/HOME/teardown PASS |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java` keyboard scheduling | Explicit main-looper owner replaces delayed view callback | Replacement, pause, and destroy remove/invalidate pending keyboard work | Static ownership test and APK compile PASS |
| Media3 and legacy Exo progress/recovery scheduling | One explicit main-looper handler per player owns progress and seek recovery | Release/replacement removes progress work; session generation remains the stale-callback guard | Static ownership test plus both hardware Pull lifecycle gates PASS |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/SagePushMediaDataSource.java` | Delegates non-zero reads to the Core notification-driven blocking path | Release/EOS/close wake the shared Core wait; interruption becomes `IOException` | Static/compile coverage passes; direct GSY System remains the deliberately last physical backend gate |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKPushMediaSource.java` | Datasource-open and producer waits use a release-aware monitor; media reads use the Core blocking path and zero-length seek probes return immediately | Release atomically detaches/releases the datasource and wakes producer and consumer waits | Hardware IJK Push exact-file playback, pause/play, crash probe, and clean exit PASS on Amazon AFTMM/API 25 |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java` | Main-looper Handler drives 500 ms progress and supplies the backend-specific recovery action | Listener, progress, queued UI, and first-frame work capture the current session/player and reject obsolete callbacks; shared recovery ownership is delegated to `PullSeekRecoveryMonitor` | Session ownership is complete and physically commissioned; retain backend-specific timeline/reprepare rules |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java` | Main-looper Handler drives 500 ms progress and supplies the backend-specific recovery action | Same generation/player rejection as Media3; shared recovery ownership is delegated to `PullSeekRecoveryMonitor` | Legacy default physically passes the same session-controller matrix; preserve its reprepare action while sharing only measured policy |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PullSeekRecoveryMonitor.java` | Uses the backend-supplied main-looper Handler for one delayed recovery callback | `cancel()` removes the callback and increments an internal generation; the callback also requires the backend's current-session/current-state guard before invoking its action | Backend-neutral timer ownership is physically commissioned on Media3 and legacy Exo; keep the actual reprepare decision/action inside each backend |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlaybackRateController.java` | Uses the backend-supplied main-looper Handler for native-rate application and one bounded seek-scan tick | Pause removes the scan callback; play re-arms it; reset/STOP/free remove it, clear the driver, restore 1x, and stale drivers reject work when unavailable | Completed Pull/SMB hardware gates pass on both Exo backends and both GSY delegates; preserve the three-second recovery-safe cadence and never advertise it for live, Push, Fixed, DVD, or GSY/System |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DisplayRefreshController.java` | One main-looper Handler applies display-mode selection and, only after a real DVD mode change, schedules the bounded HDMI-settle controlled reload | Session replacement and teardown cancel pending settle work; the callback rechecks the DVD session generation before requesting SageTV's existing controlled reload | Host policy tests and physical settings acceptance pass; never use this timer to pause or locally reconstruct ordinary Pull/SMB playback |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java` | Shared `PlaybackSessionController` owns load/operation generations and the bounded preview-to-fullscreen transition | Stop/free invalidate the generation; queued load and surface/fullscreen work reject obsolete sessions | Completed and protected by the reviewed Base hash; reconnect retains the generation until a replacement load |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/smb/SmbDirectSession.java` | One retained per-session daemon executor owns main-looper cleanup; non-main release remains synchronous | An atomic release request makes teardown idempotent; cleanup always shuts down the retained executor | Physical Media3 and legacy-Exo Pull/SMB seek A/B passes retain zero MediaServer media reads and clean session teardown |
| `source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/SmbProfileSettingsFragment.java` | One single-thread I/O executor performs bounded SMB profile list/load/save; an explicit main-looper Handler applies UI results | `onDestroy()` rejects queued UI callbacks, clears Handler work, and calls `shutdownNow()` | Preserve serialized remote writes and atomic-rename behavior; future lifecycle ownership must not retain a dead Fragment or expose credentials |
| `source/dev/core/src/test/java/opensagetv/vibe/miniclient/net/ShadowMediaServerSessionTest.java` | One named test-only server thread provides deterministic MediaServer protocol replies | Future completion is joined before the test exits and the loop terminates on CLOSE | Test-only owner; retain the join/close contract while extending shadow protocol coverage |
| `source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugAsyncExecutor.java` | Named cached debug executor owns bounded seek/Comskip checks while preserving concurrent broadcast behavior | `PendingResult` completes in `finally`; process teardown has explicit `shutdownNow` support | Completed host-side ownership migration; keep the executor debug-only and preserve the MCP contract |
| `source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugAsyncPlayerChecks.java` | Bounded debug seek/Comskip command pacing and A/V recovery polling use `SystemClock.sleep` on `DebugAsyncExecutor` workers | Every path completes its ordered-broadcast `PendingResult` in `finally`; maximum watchdog duration is bounded | Completed behavior-equivalent extraction; physical MCP acceptance remains |
| `source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackEventTraps.java` | One-shot post to an explicit main-looper Handler | No delayed work; callback is bounded and debug-only | Retain main affinity; share a debug main handler when debug classes are split |
| `source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java` | One-shot post to an explicit main-looper Handler | Probe is bounded by its request and debug watchdog | Retain main affinity; share a debug main handler without changing measurements |

The implementation order is debug command execution, server discovery, TV
background work, connecting/fullscreen work, activity/client startup, then
connection transport. Push/GFX back-pressure now uses explicit notification
owners. The bounded 100 ms `SimplePullDataSource` SIZE retry remains intentional:
the remote SageTV growing-file protocol supplies no asynchronous growth signal,
so removing it would convert a bounded live-file retry into premature EOF.

## Fragment and settings inventory

No production framework `android.app.Fragment`, `DialogFragment`,
`FragmentTransaction`, or `getFragmentManager()` use remains. Playback
navigation, Help, and Video Info are host-neutral `Dialog` classes retained by
`UIActivityLifeCycleHandler`, which dismisses the complete overlay set during
pause and destroy. This preserves one implementation for both the plain
OpenGL `Activity` and libGDX `AndroidApplication` hosts.

All settings fragments now use `PreferenceFragmentCompat`, all settings hosts
use AppCompat, and `CodecDialogFragment` uses the AndroidX fragment manager.
The Leanback launcher/server browser is also migrated: `MainActivity` and
`ServersActivity` are support-fragment hosts, `MainFragment` is a
`BrowseSupportFragment`, Add Server and Auto Connect are support dialog
fragments, and the launcher layout uses `FragmentContainerView`. The source
regression test preserves that boundary; physical launcher, Add Server D-pad,
and temporary Auto Connect checks pass on Fire OS API 25, with Auto Connect
restored OFF afterward. Physical validation must still prove every XML
preference key, default, summary, navigation target, and saved/restored value.
The remaining platform fragments are playback overlays shared with the libGDX
activity, so their migration must preserve both OpenGL and GDX host behavior.

## Permissions and exported surface

The application source currently has no `AudioRecord`, `MediaRecorder`,
`SpeechRecognizer`, runtime microphone-permission request, or other production
use corresponding to the TV manifest's `RECORD_AUDIO` declaration. The
permission and optional microphone feature have been removed. Merged debug and
release APK inspection confirms they are absent; physical remote/keyboard and
log-sharing acceptance remains.

Legacy global-storage permissions have already been removed. Log sharing uses
app-specific external storage and `FileProvider`. Physical validation proves
share, exact in-place upgrade preservation, and uninstall cleanup on Fire OS
API 25. Release-APK inspection must separately verify
the merged permissions, exported activities/receivers/providers, debug MCP
actions, implementation class names, and packaged secrets.

## Audio focus

The former global `AudioUtil` has been replaced by a lifecycle-owned
`AudioFocusController` with:

- `AudioFocusRequest` plus media `AudioAttributes` on API 26 and newer;
- a compatible legacy request below API 26 (the current Fire TV baseline is
  API 25);
- explicit gain, transient loss, permanent loss, and duck behavior;
- user-pause tracking so focus gain cannot resume playback the user paused;
- idempotent release/abandon and a session generation to reject stale focus
  callbacks.

The debug-only competing focus owner and `mcp-audio-focus-test` provide the
physical acceptance gate without shipping an exported focus-control surface in
the production APK. GSY System remains the final separately bounded player
test because it has historically failed video initialization and may destabilize
the Fire TV.

The gate passed on the commissioned Amazon AFTMM/API 25 device for Media3 and
legacy ExoPlayer hardware Pull against the exact Meet the Press recording.
Transient and duck losses paused playback and resumed advancing A/V after
focus return; an existing user pause remained paused; permanent loss paused
without auto-resume; and neither run produced a crash signature.

## Fullscreen and window handling

`AppUtil` owns `WindowInsetsController` behavior on API 30 and newer and the
legacy system-UI fallback below API 30. `ConnectingActivity` now delegates to
that implementation, uses an explicit main-looper Handler, and clears delayed
work at teardown. Activity recreation, dialogs, software keyboard, Fire TV
overlays, Home/background return, and screen geometry remain physical
acceptance gates.

Amazon's AFTKRT API-30 build throws from `PhoneWindow.getInsetsController()`
when the decor view has not yet attached. The controller is therefore obtained
from `window.getDecorView().getWindowInsetsController()` only after activities
install their content view. A null controller or runtime platform exception
uses the bounded legacy system-UI fallback. Three Settings open/back cycles,
GDX connection, and an OpenGL hardware Pull lifecycle ran on that device with a
stable process during the Settings cycles and no fatal/WindowInsets exception.

The physical run also established two automation/platform rules. Fire OS may
omit `mCurrentFocus` and `mFocusedApp`, so MCP uses the resumed Activity as a
fallback. Android 10+ blocks renderer Activity launches from a background debug
receiver, so the Dev app is foregrounded before `dev_connect_server` broadcasts
the connection request. These changes affect debug automation only and do not
weaken production Activity export restrictions.

## Dependency boundary

Dependency modernization is intentionally separate from player changes. The
current inventory includes old AppCompat/Preference/Leanback/Glide, duplicate
Guava families, legacy ExoPlayer, Media3, libGDX, Square Otto, SLF4J/Logback,
and older test libraries. Produce a resolved dependency/license/vulnerability
report first, then update one non-player family per tested change. Square Otto
is not removed until publisher/subscriber ordering has characterization tests.
