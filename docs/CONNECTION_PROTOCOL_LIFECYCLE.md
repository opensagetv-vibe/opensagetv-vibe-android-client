# Connection, protocol, and lifecycle characterization

This document records the active Android MiniClient ordering contract before
`MiniClientConnection`, `GFXCMD2`, or `UIActivityLifeCycleHandler` is split.
It describes the current implementation; it does not approve every behavior
as the final design. The source of truth is `source/dev`. The frozen
`source/existing` tree and the protected `SageTV-MiniClient-Dev` project are
comparison inputs only.

`tests/test_connection_protocol_characterization.py` protects the structural
ordering below. The matching Amazon AFTMM/API-25 physical baseline and the
first lifecycle-ownership change are now commissioned; later extractions must
continue to pass the same device gate.

## Current owners

| Owner | Current responsibility | Thread or affinity |
| --- | --- | --- |
| `UIActivityLifeCycleHandler` | Activity views, lifecycle-owned connection launch, UI event subscriptions, pause/close behavior, keyboard and overlays | Android main thread plus one retained single-thread executor whose worker remains named `ANDROID-MINICLIENT` |
| `MiniClientConnection.connect()` | Codec discovery, media/GFX handshakes, connection publication, worker startup | Caller-owned connection thread |
| `ConnectionWorkerOwner` | Owns Media/GFX sockets and all four long-lived worker handles, explicit Media readiness, idempotent close, and one bounded join deadline | Connection state boundary; it does not execute protocol commands |
| `Media-*` | Reads media protocol commands, calls `MediaCmd`, writes replies, reconnects its owner-held media socket | Dedicated blocking thread |
| `GFX-*` | Dispatches queued GFX/property/file commands and writes replies | Dedicated blocking thread |
| `GFXRead` + `GfxFrameExchange` | Reads complete GFX frames and transfers exactly one header/body pair to `GFX-*`; buffers cannot be reused before exact completion | Dedicated daemon blocking reader plus one-frame synchronized exchange |
| `ConnectionEventRouter` | Serializes Android input, Sage commands, repaint, subtitle, and other outbound event work | Dedicated bounded FIFO thread |
| `ConnectionFileTransferOwner` | Retains concurrent remote upload/download threads and their socket/stream/file resources; rejects or cancels work after connection close | Named per-transfer daemon threads owned by the connection |
| `GFXCMD2` | Decodes drawing commands and invokes the renderer/image cache/player-video bounds | Called serially by `GFX-*`; renderer methods may cross to renderer affinity |

## Startup sequence

The current startup order is significant:

1. `UIActivityLifeCycleHandler.onCreate()` creates the renderer and UI view,
   resolves `ServerInfo`, shows the connecting UI, and calls
   `startMiniClient()`.
2. `startMiniClient()` submits to the lifecycle-owned executor, whose worker is
   named `ANDROID-MINICLIENT`; socket work must not run on the Android main
   thread. Each request receives a monotonically increasing generation.
3. `MiniClientConnection.connect()` discovers codec support and closes a
   different current connection, if present.
4. Connection type `1` (media) completes its SageTV handshake before
   connection type `0` (GFX).
5. The connection is published through `client.setCurrentConnection(this)` and
   `alive` becomes true.
6. `Media-*` starts first and signals an explicit one-shot readiness latch.
   Connection startup waits at most two seconds for that signal before
   launching `GFX-*`; the former blind 100 ms sleep is removed.
7. `GFX-*` creates `GFXCMD2`, probes TCP 7818 for Pull capability, starts
   `EVTRouter`, creates the event/GFX streams, and starts `GFXRead`.
8. `GFXRead` reads a four-byte header and the complete advertised body before
   publishing them through `GfxFrameExchange`. The exchange accepts one frame,
   and releases the exact pair only after `GFX-*` completes dispatch. This
   prevents the read buffers from being reused while a command is executing.
9. Receiving `GFXCMD_STARTFRAME` marks the first-frame boundary used by the
   reconnect policy.

The readiness and one-frame exchange are compatibility boundaries. Merely
submitting all workers to a pool is not equivalent.

## Command and reply ordering

- Media commands are read and executed serially by `Media-*`; a reply is
  flushed before the next command is read.
- GFX/property/file commands are framed by `GFXRead` and dispatched serially
  by `GFX-*`.
- GFX return values, property replies, filesystem replies, and outbound input
  events share `eventChannel`. Existing writers synchronize on that stream and
  flush each complete protocol message.
- `ConnectionEventRouter` uses a bounded FIFO queue of 100 `Runnable` events. During a GFX
  reconnect it discards dequeued work rather than writing it.
- `GFXCMD2` is not an independent concurrency owner. Its mutable handle/cache
  state assumes serial command dispatch.
- Renderer calls that explicitly require UI affinity continue through
  `uiRenderer.invokeLater()` or the renderer implementation. A class split
  must not silently move all GFX work to the Android main thread.
- Image unload follows the same ordering rule. `ImageCache` removes protocol
  ownership and accounting synchronously, but the renderer performs final
  holder/resource disposal. OpenGL and libGDX queue disposal behind prior draw
  commands so an `UNLOADIMAGE` cannot invalidate a texture before its queued
  frame uses it; OpenGL resource deletion occurs on the GL context thread.
  OpenGL may downsample a stable `ChannelLogos` cache resource for upload while
  retaining its original logical dimensions for SageTV source coordinates.

## Reconnect behavior

Reconnect is currently socket-driven:

- A `GFXCMD_MEDIA_RECONNECT` command closes `mediaSocket`; the media worker's
  failure/finally path then reconnects connection type `1` while `alive` is
  true.
- A GFX read error may reconnect as connection type `5` only when reconnect is
  allowed, the connection is alive, a first frame has started, and event
  encryption is off.
- While the GFX channel reconnects, `performingReconnect` suppresses outbound
  events.
- If the reconnect conditions are not met, the read failure is handed to the
  GFX dispatcher and terminates the connection path.
- Event-channel failure closes the GFX socket to force the same GFX reconnect
  path when eligible; otherwise it closes the whole connection.

These conditions and the server-visible connection type values are protocol
compatibility behavior. A future connection generation may reject stale
workers, but it must not change reconnect eligibility accidentally.

Playback generation is now a separate concern: `PlaybackSessionController`
invalidates callbacks when a player load is replaced, stopped, or freed.
Connection reconnect by itself does not create a new playback generation; the
generation changes only when SageTV supplies a replacement media load. This
preserves the reconnect rules above while preventing an old player callback
from mutating the current media session.

## Media DEINIT and replacement socket ordering

A stock server uses `MEDIACMD_DEINIT` as a hard player boundary during many
recording switches. Android must write and flush the command reply, leave the
media read loop, close the old socket, and register a replacement media socket
without waiting for TCP EOF from the server. Player cleanup must therefore not
perform potentially blocking Android framework work on the media-command
thread.

Media3 and legacy Exo capture the old player and `MediaSessionCompat`, clear
their shared references, and post framework/player release to Android's main
thread. This keeps STOP and media-session Binder callbacks ordered on their
owning thread while the protocol thread remains free to answer DEINIT and
reconnect. Static regression coverage verifies that media-session deactivation
and release remain inside the main-thread runnable; physical stock-server
coverage verifies the externally visible DEINIT/reconnect/OPENURL sequence.

## Teardown behavior and known gaps

`MiniClientConnection.close()` currently:

1. clears `alive`;
2. clears the client's current connection;
3. closes the GFX socket;
4. detaches and closes `GFXCMD2`/the renderer;
5. closes the media socket;
6. interrupts `EVTRouter`; and
7. clears its client reference.

Closing owner-held sockets remains what unblocks the Media and GFX readers.
`ConnectionWorkerOwner` retains Media, GFX, GFXRead, and event handles, makes
close idempotent, and waits on one two-second shared deadline while skipping
the calling worker. `ConnectionFileTransferOwner` closes active transfer
sockets/streams/files and interrupts their named workers. Protocol streams,
`alive`, and reconnect flags remain in `MiniClientConnection`; they are the
next transport/state extraction boundary.

At the activity layer:

- `onPause()` unregisters UI events, pauses an active player, sends SageTV
  `STOP`, and normally closes/finishes when `app_destroy_on_pause` is true.
- `onDestroy()` cancels and shuts down the connection executor before it
  releases the media session and closes the client connection.
- `ANDROID-MINICLIENT` is retained as a `Future`. Pause/destroy cancellation
  invalidates its generation; a late successful connect closes its exact
  connection instead of publishing a stale `ConnectedEvent`, and a late
  failure cannot post into a destroyed activity.
- the delayed keyboard callback is posted to the view and is not consistently
  removed at pause/destroy.

The remaining gaps are migration targets, not permission to change
pause/playback policy.

## Physical ordering baseline

The commissioned Amazon AFTMM/API-25 device passed the durable
`mcp-connection-order-test` before and after lifecycle-owned connection launch.
The clean worker/queue/transfer evidence is
`artifacts/firetv/connection-ordering-20260830-230236.json`, captured from the
exact clean-installed APK with SHA-256
`0e427addcf10f4f648d69520bcd54afacb283b4e727fa5e85c644920a9ba02d4`. The
subsequent reconnect-state/protocol-stream extraction passed incrementally as
`artifacts/firetv/connection-ordering-20260830-231530.json` using the canonical
captioned/comskip fixture. Together they prove:

- media socket/worker readiness precedes GFX worker launch;
- the first media and GFX commands each receive their ordered replies;
- pause/play input drains through the one bounded event FIFO and advancing
  hardware-decoded A/V recovers;
- HOME closes all four observed workers (`Media`, `GFX`, `GFXRead`, and
  `EVTRouter`);
- foreground return creates a newer connection generation; and
- explicit teardown closes the connection and leaves all workers stopped.

The separate completed-file lifecycle gate also passed foreground reconnect,
surface release/recreation, three repeated exact-path hardware Pull starts,
and final force-stop/no-process teardown after this ownership change.

## Required extraction order

1. **Completed:** add bounded runtime observations for connection generation,
   worker start/stop, protocol counts, and queue depth without recording media
   paths, payloads, or credentials.
2. **Completed:** replace `ANDROID-MINICLIENT` with a lifecycle-owned
   executor/future and generation check while preserving the manual first-time
   server-selection boundary.
3. **Completed:** introduce a connection state owner that retains worker
   handles, sockets, and teardown state; close is idempotent and bounded.
4. **Completed:** replace the 100 ms Media-before-GFX sleep with explicit
   readiness proven by the same physical connect/reconnect cases.
5. **Completed:** extract event serialization while keeping one FIFO and one
   synchronized protocol writer.
6. **Completed:** extract GFX framing from GFX dispatch without changing the
   header/body lifetime or command order.
7. **Completed:** extract file transfer into a connection-owned concurrent
   owner with per-operation resource cancellation.
8. **Completed:** extract negotiated reconnect eligibility/event suppression
   and replaceable GFX input/event-output stream lifetime without moving wire
   dispatch or socket ownership.
9. **Completed:** split `GFXCMD2` by lifecycle/frame, drawing, image/cache,
   font, surface/video, and transform/batch concerns behind a stable family map.
   OpenGL and GDX pass the same physical connection/playback/teardown gate.
10. **Completed:** replace both renderer readiness polling loops with the shared
    cancellable `RendererReadinessGate`; OpenGL and GDX pass the same physical
    connection/playback/teardown gate after the change.
11. **Completed:** move negotiated codec/container lists and common-profile
    fallback into `ConnectionCapabilityProfile`, and move Activity-session
    background configuration plus bounded IME debug observation into
    `UiSessionConfiguration` and `UiKeyboardDebugState`. The `.25` physical
    ordering and retained-session lifecycle gates both pass after extraction.

Every step requires host tests plus the applicable physical startup,
foreground/background, reconnect, surface, playback, and teardown cases. A
host-only characterization is recorded as such and is never a physical PASS.
