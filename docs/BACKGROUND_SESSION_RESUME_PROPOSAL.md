# Background-preserved SageTV session proposal

Status: implemented and commissioned for Media3 hardware Pull on API-25 and
API-30 Fire TV, and for legacy ExoPlayer hardware Pull on API-25. It remains
opt-in and is not Picture-in-Picture.

## Implemented settings and behavior

Options now contains a dedicated **Home and background recovery** screen:

- **Keep SageTV session in background** retains the exact session when enabled;
- **Resume playback on return** defaults on, but only resumes playback that the
  background policy itself paused; user-paused and idle sessions do nothing;
- **Background session timeout** disconnects an unrecovered session after the
  selected bounded interval (default five minutes, with no-timeout available).

The original disconnect/reconnect behavior remains available by disabling the
first option. Explicit Exit always disconnects. The state machine uses exact
connection/player identity and reports its decisions through debug/MCP status.

Physical API-25 evidence on 2026-08-31 proves Media3 hardware Pull A/V before
HOME, same-generation session preservation, hidden/recreated Surface, both
enabled and disabled automatic resume, manual-pause preservation, explicit
PLAY after disabled recovery, configured timeout disconnect, and committed
force-stop teardown. Fire OS can briefly retain a terminating PID after the
package has become force-stopped; the test records that separately.

Physical API-30 evidence on 2026-09-05 proves the same Media3 hardware Pull
matrix. Fire OS delivers a MediaSession pause before the delayed application
background transition. The state machine coalesces that already-applied pause
as background-owned, and the MediaSession callback does not forward a duplicate
pause while preservation is enabled. Foreground repaint is enqueued on the
ordered connection event router; it never performs a network write from the
Android main thread. Automatic resume, disabled automatic resume plus explicit
PLAY, manual-pause preservation, exact connection retention, Surface recreation,
configured timeout disconnect, replay, and teardown all pass on API 30 and were
repeated on API 25 with the same APK.

## Goal

When the user presses Android Home during playback, pause through SageTV,
retain the same valid MiniClient session for a bounded normal-background case,
detach/hide the Activity surface safely, and resume only if the app itself
paused playback. Explicit Exit must retain the current STOP, disconnect, and
finish behavior. If Android kills the process or the socket becomes stale,
foreground return uses the normal bounded reconnect path.

## Review findings that correct the original proposal

The original Vibe baseline deliberately did more than upstream:

- `UIActivityLifeCycleHandler.onPause()` unregisters UI input/event ownership,
  pauses the local player, posts `SageCommand.STOP`, and, when
  `app_destroy_on_pause` is true (currently the UI and code default), cancels
  connection startup, closes the connection, and finishes the Activity.
- The commissioned lifecycle tests currently require HOME to close Media, GFX,
  GFXRead, and EVTRouter workers and require foreground return to use a newer
  connection generation. Those tests are correct for current behavior and
  must be versioned/replaced deliberately, not simply weakened.
- `MiniclientService` is a started, unbound service. It initializes the global
  `MiniClient`, but it does not own the Activity renderer, input handlers,
  player surface, or a resumable session state machine. Its `onDestroy()` calls
  `client.shutdown()`.
- `onPause()` is not a reliable Home signal. Settings, dialogs, permission UI,
  configuration changes, and Activity transitions can all pause an Activity.
- The player and renderer currently retain Activity/view relationships. A live
  TCP connection is not sufficient proof that the old player, GFX renderer,
  subtitle overlay, and Surface can be rebound safely.
- On API 26+ a background network session cannot assume an ordinary started
  service lives indefinitely. Short process-resident Home/return may work
  without a foreground service, but Android may kill it. Any request for
  stronger survival would require a separately approved foreground-service
  notification/power policy.

Therefore this must be a characterized application/session state machine, not
only a `STOP` to `PAUSE` replacement.

## Proposed states

```text
FOREGROUND_PLAYING
FOREGROUND_USER_PAUSED
BACKGROUND_TRANSITION_PENDING
BACKGROUND_APP_PAUSED
BACKGROUND_USER_PAUSED
BACKGROUND_SESSION_LOST
EXPLICIT_EXIT
```

Required distinctions:

- user/STV pause versus pause initiated by background policy;
- temporary internal Activity transition versus process background;
- valid same connection generation versus stale/lost session;
- Activity surface loss versus player/session destruction;
- Home/background versus explicit Exit/Standby.

State ownership should live above a single Activity instance, preferably in a
small application/session owner associated with the existing `MiniClient`.
Activity callbacks should attach/detach UI resources; they should not infer
global foreground state independently.

## Foreground/background detection

Begin with executable characterization of `onPause`, `onStop`, `onStart`,
`onResume`, `isChangingConfigurations()`, settings transitions, dialogs,
screen-off/standby, Home, app switch, and explicit exit on API 25 and API 26+.

Use process/application visibility or an Activity-started counter with a short,
cancellable transition grace period so one internal Activity replacing another
does not emit PAUSE then PLAY. Do not add another polling loop or detached
timer. The generation/cancellation pattern already used by the connection and
playback-session owners should reject stale delayed callbacks.

## Background transition

When the process truly loses foreground while an eligible session is playing:

1. snapshot connection/session/player generation and actual pre-background
   play state;
2. mark the pause as app-owned before sending commands;
3. post `SageCommand.PAUSE` through the normal serialized event path;
4. coordinate local player pause with SageTV command ordering so a toggle is
   not sent twice;
5. detach or invalidate the video Surface and Activity overlays without
   freeing the player or closing protocol workers;
6. unregister Activity-only input/UI subscriptions;
7. abandon audio focus without letting the audio-focus callback become a
   second independent resume owner;
8. retain no Activity reference, visible Surface, wake lock, dialog, keyboard,
   or background frame rendering;
9. keep the existing connection only while it remains healthy.

If playback was already user/STV-paused, preserve that state and never mark it
as app-paused. If there is no active player, retain the UI session only if the
same lifecycle contract is safe and enabled.

Live TV stays in SageTV's normal timeshift model. Background pause must not
jump to live edge or take seek ownership from the server.

## Foreground return

On return:

1. cancel any stale background transition and validate the exact connection
   and playback-session generations;
2. recreate/reattach renderer, video Surface, subtitle overlay, UI/input
   handlers, and audio focus in a defined order;
3. request a SageTV repaint only after the replacement renderer is ready;
4. if the same session is healthy and `pausedByBackground=true`, post
   `SageCommand.PLAY` once and verify advancing A/V;
5. if the user/STV paused before Home, remain paused;
6. if connection/session/surface restoration fails, close the stale generation
   and enter the existing bounded reconnect path;
7. never create a second connection while the preserved one is still current.

The acceptance criterion is the same MiniClient connection generation and
server session, not merely returning to the same media timestamp after replay.

## Explicit exit and settings

Explicit SageTV Exit, Vibe Exit, Standby when configured to exit, server
disconnect, and deliberate connection close set `EXPLICIT_EXIT` before any
Activity callback. They retain STOP, bounded connection close, player/renderer
release, and finish behavior and must suppress background auto-resume.

Replace the ambiguous user-facing `app_destroy_on_pause` label with a clear
policy such as `Keep SageTV session while app is in background`. Preserve the
stored legacy key only through an explicit migration mapping so existing users
are not silently changed. The Vibe default may become session preservation only
after API-25 and API-26+ gates pass. A bounded/background-only guarantee must be
stated; the setting cannot promise survival after Android kills the process.

Do not hold a wake lock or add a permanent foreground-service notification for
the initial bounded feature. If API-26+ evidence shows a foreground service is
required for the desired duration, stop at a design/permission/UX gate and get
separate approval.

## Telemetry and MCP

Add bounded state and transition counters/timestamps:

- `appVisibilityState`, `backgroundTransitionGeneration`;
- `pausedByBackground`, `wasPlayingBeforeBackground`, and pre-background
  player state;
- connection and playback-session generation before/after return;
- surface detached/attached and renderer readiness;
- PAUSE/PLAY/STOP request counts with owner (`user`, `background`, `exit`);
- background session preserved/lost/stale and reconnect count/reason;
- background/foreground monotonic timestamps and resume recovery time.

Do not emit continuous telemetry, media paths, credentials, or per-frame logs.

## Required tests

Host/state-machine tests:

- playing -> real background -> app pause -> same-session resume;
- manually paused -> background -> return remains paused;
- internal settings Activity/dialog/configuration change emits no pause/play;
- rapid Home/return rejects stale delayed transitions;
- explicit exit suppresses preservation and auto-resume;
- connection loss while backgrounded selects one normal reconnect;
- surface rebind failure closes stale state and reconnects cleanly;
- audio-focus loss/gain cannot race background ownership;
- repeated background/foreground and final teardown leave no callbacks,
  workers, sockets, player, Surface, or Activity references.

Physical API-25 and API-26+ gates, independently for Media3 and legacy Exo:

- completed hardware Pull playback: same connection generation, PAUSE not
  STOP, hidden/released Surface in background, reattached Surface, PLAY once,
  advancing A/V, captions retained, and no reconnect;
- pre-paused recording remains paused;
- growing Live TV on approved channels 2.1/5.1 retains timeshift and resumes
  from the paused point without jumping to live edge;
- Push and SMB Direct session preservation or explicit documented fallback;
- settings transition, Home, screen-off/standby, explicit exit, socket loss,
  process kill, repeated cycles, and final zero-process teardown;
- no crash, ANR, zombie worker, duplicate socket, stale callback, black frame,
  audio-only resume, or hidden background rendering.

The existing `mcp-lifecycle-test` currently allows reconnect/replay and asserts
HOME teardown. The new feature needs a separate strict same-session test first.
Only after it passes should the default lifecycle test be changed to require
preservation. Reconnect/replay remains a fallback test, never evidence of a
successful preserved session.

## Non-goals

- Picture-in-Picture or dual-stream PIP;
- replacing a player backend;
- indefinite background survival against Android process death;
- a second MiniClient connection;
- unrelated lifecycle/class decomposition;
- changing playback behavior before the state-machine characterization exists.
