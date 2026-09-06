# Changelog

## Unreleased

- Removed the obsolete hard-coded v0.5.85 migration-test assertion. Repository
  identity validation now requires a semantic `VERSION` and verifies that
  `release.properties` contains that exact value, preventing later version
  updates from leaving the release gate stale.

- Classified compatibility-matrix items as normal runtime features,
  diagnostics, Dev/debug test controls, or regression test media. Exact file,
  channel, and DVD-seek automation; MCP health/assertion controls; fault
  injection; and deterministic media are now explicitly marked test-only.
  The document also clarifies that the player statistics overlay and MIM status
  query are optional diagnostics, while the playback modes and recovery
  behavior they validate remain normal user features.

- Corrected legacy-extender CEA caption corruption and misplaced/top-screen
  rows after random-access seeks. The extractor taps now discard any partial
  pre-seek text sample, and the callback bridge orders MPEG-2 B-picture caption
  packets by presentation timestamp instead of decode order. It also suppresses
  interleaved duplicate copies from the parallel CEA-608/708 extractor outputs
  with a bounded exact history. Media3 and legacy Exo hardware Pull both passed
  the stock-SageTV Off/CC1/CC2/Off/CC1 cycle plus FF/REW/FF2/REW2 recovery on
  AFTMM/API-25 `.25`. Final screenshots show complete three-line roll-up text
  in the normal lower caption region with approximately 1.1-1.3 seconds of
  offset from the fixture's burned-in PTS, inside the two-second physical gate:
  `artifacts/firetv/20260906-181311_caption-media3-pull-legacy-callback.png`
  and
  `artifacts/firetv/20260906-181613_caption-exoplayer-pull-legacy-callback.png`.
  Exact installed APK SHA-256 is
  `c44cacb8e9e8f6c784842e15a1b6d79cd1d819a417f37cfc387950a999a2b3ec`.

- Closed the reopened long-duration native-DVD cadence investigation on the
  commissioned AFTMM/API-25 `.25`. The physical harness no longer silently
  truncates an explicitly requested ten-minute cadence run to three minutes;
  it remains safely bounded at 15 minutes. Aladdin was positioned at the same
  8:00 motion scene and ran for 601.349 seconds with 601.096 seconds of media
  progress (0.99958x), hardware `OMX.MTK.VIDEO.DECODER.MPEG2`, 28,837 video
  outputs, zero dropped frames, two isolated skips, zero new long release gaps
  or non-positive intervals, and 131 ms final A/V sample delta. The persistent
  detailed overlay showed about 7.9 seconds buffered and 7.7% Vibe CPU at the
  final frame. Evidence is
  `artifacts/firetv/dvd-aladdin-10m-true-long-cadence-20260906.json`,
  `artifacts/firetv/20260906-dvd-aladdin-10m-sample-2m.png`, and
  `artifacts/firetv/20260906-183728_screen.png`. The exact final clean APK also
  passed a separate 20-second 8:00 startup/cadence/STOP smoke in
  `artifacts/firetv/dvd-aladdin-final-clean-apk-smoke-20260906.json`.

- Added a universal, mode-aware Playback Stats overlay to the long-press Active
  Player Adjustments menu. Compact and detailed persistent modes, a bounded
  30-second mode, explicit hide, and a redacted export are available. The
  overlay samples only while visible and removes itself when the playback
  Activity pauses or is destroyed. Common player, codec, actual decoder,
  resolution, frame/output, audio, timeline, display-refresh, synchronization,
  buffering, and recovery fields are joined only by applicable Pull, SMB
  Direct, Push/Fixed, caption, or DVD diagnostics. Live bars show measured media
  activity, mode-scaled buffer health, and fixed-scale CPU; unavailable inputs
  are omitted.
  The display deliberately excludes YouTube-specific video/session IDs,
  viewport/optimal-resolution labels, normalized volume, generic color labels,
  mystery text, and wall-clock date/time. On AFTMM/API-25 `.25`, the real
  long-press submenu enabled the persistent detailed view over both hardware
  Media3 Pull and native DVD. Pull showed only datasource diagnostics; DVD
  showed only Push/cadence/stream/A/V diagnostics. HOME removed the view and
  its sampler with the Activity. Focused overlay/protocol checks pass 71/71,
  and the clean 60-task APK build passes. The panel measures its longest visible
  troubleshooting line and stays only slightly wider than that content; the
  bars follow the same width. Measured media activity uses its own rolling peak,
  buffer health retains a mode-scaled time target, and CPU separates Vibe from
  the remainder of total device usage on a fixed 0-100% scale. Duplicate buffer,
  measured-activity, and CPU detail rows and the invariant estimated
  link-capacity bar are omitted. Detail rows use the same 10.5sp normal typeface
  as the graph labels. A checked/unchecked first row in Playback Stats
  submenu provides a direct persistent-detailed on/off toggle. A dedicated
  bar-chart icon now sits beside the long-press gear and CC controls, toggles
  the detailed panel directly, and changes from white to green while active.
  MCP supports the same `toggle`, `off`, `compact`, `detailed`, and
  `detailed_30s` modes without breaking the legacy Boolean operation. The
  overlay also interval-samples total Android-device CPU and Vibe-process CPU
  only while visible. The final three-bar layout and MCP modes were physically
  verified on `.25`; `Vibe` and `Other` CPU label values use the same blue and
  orange as their bar segments, while `Total` remains neutral. Evidence is
  `artifacts/firetv/20260906-154036_playback-stats-cpu-color-font-final-20260906_screen.png`
  and exact APK SHA-256 is
  `8141957fb0a4d318ba9158616d390244c3d990431da34b2e6efbc71246841cda`.

- Fixed a Media3 release crash caused when display-refresh inspection queried
  ExoPlayer from SageTV's GFX worker. Refresh application is now marshaled to
  Android's main thread and display restore no longer reads player metadata.
  The clean hardware DVD start/seek/play/stop regression passes on `.25`.

- Prepared the independently migrated Android client for its first public Vibe
  source/APK release. The GitHub repository is a true fork of
  `OpenSageTV/sagetv-miniclient`, the active implementation is isolated under
  `opensagetv.vibe.miniclient`, and the protected v0.5.75 comparison project is
  unchanged. Added the common contribution, security, third-party, source-only
  CI, deterministic source/APK packaging, and unified-workflow contracts.
  Removed the private SMB configuration-share default and test credentials;
  local commissioning values remain in ignored configuration. The unified
  prepublication gate passed 451 repository/static tests, 63 MCP tests, Core
  JUnit, all validation checks, a clean 60-task debug APK build, and strict APK
  inspection. The exact committed-artifact hash is recorded after the final
  clean-tree rebuild.
- Published the true OpenSageTV MiniClient fork with `main` as its Vibe default
  branch and retained upstream history on `master`. Tag `v0.5.85` provides the
  development-signed APK, verified 1,353-file source archive, release manifest,
  and checksums. Exact APK SHA-256 is
  `b143d8201496bd12039ebf5a452e591dfc2ec3684f5464e35dc93014abcbcd65`;
  source archive SHA-256 is
  `311a88e1089ad18cc096f3317cdfa540c4ae307a0007009baf2e1d5fc546b0be`.
- Removed obsolete Android manifest `package` attributes now that AGP uses the
  explicit Gradle `namespace` declarations. This removes misleading AGP
  warnings without changing the application ID or Java namespace.
- Made the exact-MediaFile MCP replay contract test insensitive to normal
  multiline Python formatting. The test still requires the typed
  `media_file_id` entry point and its direct `sagex.watch` behavior, but no
  longer reports a false regression when the function signature is wrapped.
- Added read-only H.262 interlace observation without claiming an Android
  deinterlacer. The bounded elementary-stream scanner distinguishes progressive
  sequences, interlaced sequences, field pictures, interlaced frame pictures,
  progressive-frame/telecine content, and unknown data; Media3, legacy Exo,
  and native DVD extractors feed it while forwarding unchanged bytes. MCP
  exposes the observation and counters and always reports selectable
  deinterlace control as unavailable. Core tests and the clean 60-task build
  pass. On AFTMM/API-25 `.25`, both Media3 and legacy Exo hardware Pull report
  `interlaced_sequence_interlaced_frames` for the 1080i fixture. Native DVD
  reports the same classification with 172 observed interlaced frame pictures,
  hardware MTK MPEG-2, advancing real-time A/V, and zero dropped frames.
- Separated Android audio decoding evidence from encoded-sink and passthrough
  claims. Capability negotiation now uses a device-local audio profile while
  debug/MCP reports platform decoders, active encoded-sink support, and
  playability independently. The active-player summary no longer labels an
  AC3/EAC3/DTS MIME type as proof of passthrough, and SageTV HBR/audio-output
  passthrough remains unadvertised. A repeatable generated-fixture MCP matrix
  physically passed AC3 and EAC3 direct Pull on AFTMM/API-25 `.25`; DTS was
  absent from both decoder and sink support and correctly forced SageTV away
  from direct Pull. Focused tests and the clean 60-task build pass; exact APK
  SHA-256 is
  `04f6d5106d3043c93163cd1af390027e3cfd8d072190aa869a17f07f26d90c1d`.
- Closed the retained Media3 fast-switch recovery gap. Completed-file Pull and
  SMB Direct replacements now arm an eight-second first-video-frame watchdog;
  an error or timeout performs exactly one normal full-player fallback, while
  a newer OPENURL, first frame, or release cancels the stale watchdog. Focused
  Python tests, Core JUnit, and the clean 60-task Android build pass. The exact
  APK SHA-256
  `c5658c7d0036107e0b30b4eaecdf6514bcec2cdc21d02e7c7b59ebf1fc739605`
  physically passed both Pull and SMB Direct retained switches on AFTMM/API-25
  `.25`, first against the same deterministic fixture and then from
  `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` to the distinct completed
  `ThisOldHouse-WalpoleADURetroVibes-63775847-0.ts`. Every run retained hardware
  MPEG-2, rendered the replacement first frame, and recorded zero fallbacks.
- Reconciled the active-only backlog with already commissioned work: optional
  Home/background recovery and paused backend-gated frame step were complete
  and documented, so their stale unchecked entries were removed.
- Added bounded GFX image-allocation recovery without weakening a real OOM.
  Android may evict exactly one disposable LRU UI image and retry an allocation
  once; no entry or a second OOM rethrows immediately. Renderer surfaces are
  excluded, closed/superseded connections receive no unload callback, and
  invalid/overflowing dimensions are rejected before allocation. Attempts,
  evictions, successes, and failures are exposed through debug/MCP state. Four
  Core recovery cases, protocol characterization, and MCP retention tests pass.
  The exact APK SHA-256
  `58d8affb6d41ce6e1c1166cf9daa9af8c156f16bc0e2473ad54c77bc4af37efd`
  then passed Media3 hardware MPEG-2 Pull on `.25` with 2,577 rendered frames,
  zero dropped frames, all recovery counters zero during normal playback, and
  no duplicate local caption overlay.
- Implemented the standard SageTV hardware-extender caption path for Android
  extractor-backed players. Media3 and legacy ExoPlayer now tap encoded CEA
  samples before local decoding, convert valid three-byte `cc_data` units into
  SageTV's eight-byte CC records, and publish event 225 after the server
  negotiates `SUBTITLES_CALLBACKS`. Duplicate 608/708 extractor copies are
  suppressed, seeks/new loads flush callback state, and local cue rendering is
  suppressed only while raw callback packets are active. GSY inherits the
  implementation through its Exo delegates; IJK now honestly advertises no
  callback producer. Added Core unit coverage and MCP telemetry for negotiated
  state, callback count, and bytes. Re-audited the other server-side extender
  branches and documented which capability combinations Android already uses
  and which firmware-only features remain deliberately unadvertised. Physical
  Commissioning now passes on the exact rebuilt APK. Against the
  stock-compatible `.175` server, Media3 completed the standard
  Off/CC1/CC2/Off/CC1 STV sequence with matching decoded/wire counters, no
  Android overlay, and post-seek callback recovery. Legacy Exo then produced
  1,017 decoded events/35,096 bytes with identical wire bytes and recovered
  after FF in 1.02 seconds. Its remaining duplicate-overlay race was fixed by
  detaching only `SubtitleView` while preserving the extractor tap. The same
  fresh-install race was subsequently reproduced and fixed in Media3. IJK stayed
  playable, negotiated no callback capability, and emitted zero event-225
  bytes. The physical harness now has an explicit unsupported-backend fallback
  gate so future capability drift fails the build/test workflow.
- Completed a truly cold-cache Android build and added the previously missing
  trusted checksums for the Guava 33.3.1 Android/JRE parent POMs and JUnit
  5.10.2 module metadata. A fresh Gradle user home now builds the APK without
  relying on artifacts that happened to exist in the reusable container. The
  refreshed 1,340-file release source archive then passed 445 tests and a
  second cold-cache 60-task build; its APK reproduced the canonical SHA-256
  `92b92345ecd3328a41f917bf232a65006af8e0532770b97e94b5686fefd0a5ff`.
- Restored physical remote long press across OpenGL/GDX render surfaces and
  authored DVD menus. MiniClient activities now own key dispatch above
  decoder/render-view focus, Fire OS platform flags, repeat timing,
  release-duration, and a bounded hold timer all retain the configured mapping,
  and a DVD menu intercepts only short navigation/select presses. Added the
  missing Active Player Adjustments gear to both Android-TV navigation resource
  variants and made the optional view null-safe; this fixes the previously
  swallowed `NavigationDialog` null dereference. An automated hold on physical
  AFTMM/API-25 `.25` opened the complete overlay after the fix.
- Completed the local GitHub/source/handoff release workflow from independent
  checkouts. Source archives now retain POSIX executable modes and validate
  from their complete manifest even when `.git` is intentionally absent.
  Android root commands pass the invoking checkout to the one unified
  `opensagetv-vibe-dev` container, preventing an independent verification tree
  from silently testing the canonical checkout. An untouched Windows v0.5.75
  worktree applied the v0.5.85 changed-files ZIP, normalized 483 files only
  after proving exact UTF-8 CRLF-to-LF equivalence, removed all 355 obsolete
  paths, passed 438 project tests, 57 MCP tests, Core Gradle tests, validation,
  and a clean 60-task APK build, then installed/launched successfully on `.25`.
  The canonical clean build reproduced the independently updated APK
  byte-for-byte at SHA-256
  `b2718f3681685458e2f322c0dfe4bd90d0f7d217dd7afe4360703b8e47e614d4`.
  Actual content drift remains a pre-extraction failure.
- Made the GitHub source/APK bundle generator work in the same Git-less
  manifest-verified checkout used by extracted source archives and the unified
  container. It records `project-manifest` provenance when `.git` is absent;
  normal Git checkouts retain exact revision and dirty-state enforcement.
  The resulting canonical gate passes 439 project tests, 57 MCP tests, and
  Core Gradle tests.
- Extracted the final publication source ZIP into an isolated Git-less
  directory and proved its complete 1,339-file manifest, validation, Core
  tests, fresh debug-keystore bootstrap, and all 62 clean APK tasks. Private
  signing material remains correctly excluded from source archives.
- Removed the duplicate Activity weak reference left behind after extracting
  keyboard/debug observation into `UiKeyboardDebugState`, registered the
  active-player overlay and display-refresh controller in the lifecycle owner
  inventory, and pinned the reviewed stock-caption/display-refresh Base player
  form in the complete unit-test package. These omissions were discovered by
  the independent changed-files workflow rather than hidden by the canonical
  checkout.

- Added a bounded 0/250/500/1000/1500 ms HDMI-settle control to Active Player
  Adjustments. A delay is used only after a real display-mode change and only
  before the existing server-owned DVD decoder replacement; ordinary Pull/SMB
  playback is never locally paused and stale delayed reloads are cancelled.
  The active value and pending delay are exported in MCP telemetry. The exact
  APK exposed and changed the value live on `.25` while hardware playback
  continued. Decoded-PCM gain/downmix/audio-delay controls remain deliberately
  absent because neither active Exo sink exposes a proven runtime-safe A/V
  offset contract; the existing Audio offset row fails closed instead of
  claiming to alter encoded AC-3 output.
- Added an opt-in compact active-player process overlay. It reports the active
  backend/source, playback state/time/rate, buffer capacity/ahead, and current
  text-subtitle presentation while video continues. The overlay updates once
  per second, owns weak Activity/player references, removes its callbacks and
  view after 30 seconds, and can be shown/hidden deterministically through the
  debug MCP surface. On physical AFTMM/API-25 `.25`, hardware MPEG-2 Pull
  playback continued with zero dropped frames; MCP proved `visible=true` and
  automatic `visible=false` after the deadline. Evidence is
  `artifacts/firetv/active-player-compact-overlay-mcp.png`.
- Completed the stock-server/hardware-extender caption audit. Stock
  `MiniPlayer.setClosedCaptioningState()` returns `false` and has no command
  that publishes the STV checkbox state. Proprietary extenders instead
  advertised `GFX_SUBTITLES`, decoded caption packets in firmware, and returned
  them to SageTV through subtitle callback type 225 for server-side rendering.
  Android has never produced those packets, so its profile now honestly
  advertises `GFX_SUBTITLES=FALSE`. The optional Vibe `VIDEO_CC_STATE` path is
  unchanged; unmodified servers retain immediate, explicit Off/CC1/CC2 client
  controls and now show a precise compatibility explanation when STV authority
  is selected.
- Added bounded on-the-fly presentation controls for locally rendered Media3
  and legacy-Exo text captions: 8-32% bottom safe-area presets, 75-150% text
  sizing, and Android-system, outlined, or black-box styles. Session-only and
  saved-device-default scopes are separate; authored DVD bitmap SPUs reject
  the control rather than pretending to change. MCP snapshot fields expose the
  exact active values. The physical `.25` hardware-Pull gate changed safe area
  from 18% to 25% without a decoder reload while video/audio continued with
  `OMX.MTK.VIDEO.DECODER.MPEG2` and zero dropped video frames.
- Added the long-press `Active Player Adjustments` surface for real, bounded
  on-the-fly playback control. It reports the resolved player/source, applies
  aspect, caption mode/track, DVD subtitle offset, active audio track, and
  refresh policy immediately, and separates session-only changes from saved
  device defaults. Backend, decoding, codec-queue, and DVD timestamp changes
  use one server-owned same-position decoder replacement; the physical Aladdin
  gate retained the DVD position and resumed hardware MPEG-2. Unsupported
  audio offset and encoded-output passthrough changes fail visibly without
  altering playback. Live diagnostics can be refreshed or exported to the
  app-private external diagnostics directory.
- Completed the reordered native DVD cadence/control gates on AFTMM/API-25
  `.25`. A 20-second HDMI capture of Aladdin at 8:00 contained no sustained
  freeze and no decoder drops. Both the raw VOB reference and physical capture
  contain the same exact 3:2 film cadence (about 20% repeated 30-fps capture
  frames), proving the client is not adding the reported high-motion cadence.
  The deterministic authored DVD then passed menu/submenu, main-title, chapter
  next/previous, audio, subtitle change/toggle, pause/resume, per-command A/V
  recovery, STOP, and teardown. A missing-disc case failed safely without a
  crash. Evidence is in `artifacts/firetv/dvd-25-aladdin-8m-active-controls.mp4`,
  `dvd-25-authored-final-control-regression.json`, and
  `dvd-25-missing-disc-safe-failure.json`.

- Added a private, backward-compatible MiniClient server-seek event for
  server-owned DVD Push sessions. The MCP disc gate now observes the exact DVD
  STC/VOBU landing and performs at most two bounded correction seeks instead of
  mistaking Media3's cell-local clock for SageTV's absolute DVD timeline.
- Added deterministic `--start-ms` disc-test positioning; the physical
  non-Pro Fire TV gate landed Aladdin at 478,380 ms for a requested 480,000 ms
  target and passed hardware audio/video recovery within the 2,500 ms limit.

- Completed the Android native ABI/page-size gate. The TV APK now deliberately
  ships the paired ARMv7 and ARM64 ABIs instead of advertising an incomplete
  x86/x86_64 legacy-IJK set. Both ARM directories contain exactly
  `libffmpegJNI.so`, `libgdx.so`, `libijkffmpeg.so`, `libijkplayer.so`, and
  `libijksdl.so`; the unused, ARM32-only GDX FreeType helper is excluded even
  from stale incremental working trees. Rebuilt the pinned ExoPlayer 2.18.0
  FFmpeg extension from FFmpeg revision
  `839f98ff6719cf2db0cbd88cd787a1b19b9cbf47` with 16 KB ELF `LOAD`
  alignment. The AAR SHA-256 is
  `e9e34c833298c1177247b3f7cfef8e8be45035ff4f8076d667b8f5c9dc9c4b12`.
  Its source-offer hash and strict Gradle verification metadata were updated.
  The APK inspector now fails closed on an unexpected ABI, unequal ARM native
  sets, invalid ELF, or sub-16-KB ARM64 `LOAD` alignment. Targeted tests passed
  56/56; the clean 60-task build and 16 KB APK ZIP check passed. The exact APK
  SHA-256 is
  `5538372d6431f6419a8d152d97072c7b6142cfb80b8bb569631be56cda07cdb4`.
  That artifact was installed in place on AFTMM/API-25 `.25` and passed a
  direct SageTV Pull hardware MPEG-2/AC3 playback gate.
- Closed the bounded GSY/System experiment without exposing an unsafe default.
  A debug-only `gsy_system_probe` gate now runs the real Android System
  `MediaPlayer` against SageTV's Pull `MediaDataSource`; normal System selection
  still resolves directly to Media3. On the commissioned AFTMM/API-25 Fire TV,
  the generated MPEG-2/AC3 fixture reproducibly caused System MediaPlayer error
  `1/-2147483648`. The adapter contained that asynchronous failure, reopened the
  same SageTV session through Media3 exactly once, and recovered advancing A/V
  with hardware `OMX.MTK.VIDEO.DECODER.MPEG2`. MCP now fails closed unless it
  observes either a genuinely active System backend or the single documented
  Media3 fallback, and exposes the resolved engine, fallback count, and reason.
- Added negotiated playback-rate command 30 for completed random-access Pull
  and SMB Direct playback. Media3, legacy ExoPlayer, and both corresponding GSY
  delegates accept native forward 0.5x-2x rates and bounded seek-based
  +/-4x-256x scan; live/growing, circular, Push, Fixed/MIM, DVD, external-link,
  and GSY/System sessions retain their established behavior. A three-second
  scan cadence lets SMB rebuffer and render between discontinuities; the
  original sub-second cadence was rejected by a physical no-false-pass test.
  SageTV Core negotiates `VIBE_PLAYBACK_RATE`, so old clients keep the original
  one-shot seek fallback. Both Exo backends now honor SageTV's trick-play mute
  command instead of silently ignoring it. The reusable hardware-only gate
  passed Media3 and legacy Exo over Pull and SMB Direct, both GSY delegates over
  Pull, and server-driven Smooth FF/REW on the commissioned AFTMM/API-25 Fire
  TV. STOP resets 1x and cancels scan callbacks. The full gate passed 423
  project/static tests, 56 MCP tests, Core JUnit, project validation, and a
  clean 60-task Android build. The exact clean APK SHA-256 is
  `8500c503b79869bfaafa3e012916972e95c4b75fb2d6a5b0ed050898b1d10a3f`;
  after an in-place install on `.25`, that artifact repeated server-negotiated
  Pull and SMB Direct playback-rate gates with the hardware MPEG-2 decoder.
- Added conservative Media3 fast file replacement for compatible completed
  Pull and SMB Direct media. A replacement retains the player and Surface,
  swaps only the MediaSource/datasource, and falls back exactly once to the
  established full-load path if setup or asynchronous playback fails. Live,
  circular, Push, Fixed, HTTP/external-link, DVD, uninitialized, and legacy-
  unknown loads remain on the full-load path with an explicit eligibility
  reason. Debug telemetry exposes attempts, first-frame successes, fallbacks,
  reason, and target URL without enabling continuous analytics. The reusable
  `mcp-fast-switch-test` passed on the commissioned AFTMM/API-25 Fire TV for
  both SageTV Pull and SMB Direct: each produced one hardware MPEG-2 switch,
  one rendered-first-frame success, and zero fallbacks. A deliberately missing
  file also proved the single full-load fallback without process death. The
  final clean 60-task APK is
  `15a28b08c7c3febe6e08b7ff6519ca375110c30bb3e7282ed10b0ee67b7d7ae8`;
  that exact in-place install repeated both physical datasource gates.
- Replaced Square Otto with the project-owned, typed `VibeEventBus` and
  `VibeEventListener` contract. Dispatch remains synchronous on the posting
  thread and preserves registration order, while listener registration and
  removal are explicit and duplicate-safe. `EVENT_OWNERSHIP.md` records every
  publisher/subscriber and lifecycle boundary, and executable tests cover
  event completeness, registration order, duplicate registration, and removal.
  The selected runtime graph consequently dropped from 123 to 122 coordinates;
  no Otto classes, Gradle declaration, lockfile entry, notice, or captured
  runtime dependency remains. The full gate passed 413 project/static tests,
  56 MCP tests, Core JUnit, and the clean 60-task Android build. The exact APK
  (`96074525bc2136f107e1138881dc6008c9279665f98e820090ee0898344db16a`)
  then passed physical Media3 hardware Push playback, connection/event FIFO,
  worker teardown/reconnect, retained HOME/return playback, navigation-overlay
  display, video-info display/refresh, and native keyboard-event dispatch on
  the commissioned AFTMM/API-25 Fire TV (`192.168.10.25`). Physical evidence
  includes `artifacts/firetv/connection-ordering-20260905-083905.json`.
- Completed the remaining connection/UI configuration and bounded debug-
  telemetry extraction. `ConnectionCapabilityProfile` now owns common-profile
  loading, codec/container discovery, prepared capability lists, and profile
  fallback while `MiniClientConnection` retains only the established wire
  ordering. `UiSessionConfiguration` owns the one-time legacy background-policy
  migration and immutable keep/resume/timeout values. `UiKeyboardDebugState`
  owns the weak resumed-Activity reference plus requested/suppressed/visibility
  observations while `UIActivityLifeCycleHandler` retains actual keyboard and
  lifecycle behavior. New ownership tests pass with the complete 409 static/
  project, 56 MCP, Core JUnit, and clean 60-task Android build gates. On the
  normal AFTMM/API-25 Fire TV, Push connection ordering passed media-before-GFX,
  FIFO pause/play recovery, HOME close of all four workers, foreground new-
  generation reconnect, and explicit teardown. The separate retained-session
  gate passed exact-connection HOME/return, Surface recreation, automatic
  resume, preservation of a manual pause, repeated playback, and teardown.
- Completed the GitHub publication dependency/license gate. The selected
  runtime graph is now a generated, fail-closed 123-coordinate inventory in
  `third_party/RUNTIME_DEPENDENCIES.csv`; every coordinate maps to a packaged
  notice/license family, and LGPL/native AAR rebuild material records the
  exact binary hashes and pinned upstream source revisions. The obsolete
  Ostermiller GPL circular buffer was replaced with the project's Apache-2.0
  `BoundedCircularByteBuffer`, removing that runtime dependency rather than
  relying on a source offer. The publication packager validates the complete
  project manifest, nested APK/source hashes, required legal files, duplicate
  and unsafe archive paths, and a clean worktree for a release invocation.
  The full gate passed 406 project tests, 56 MCP tests, all Core JUnit tests,
  project validation, a clean 60-task Android build, and independent archive
  inspection. The resulting debug APK SHA-256 is
  `41d81c69815ef6cab4ebe963680bc0aa0e232061d245ae4d4276d6a8bfa6eaf1`.
  The clean APK then passed physical Media3 Push playback on the normal
  AFTMM/API-25 Fire TV (`192.168.10.25`): `SAGETV_PUSH`, hardware
  `OMX.MTK.VIDEO.DECODER.MPEG2`, advancing audio/video/timeline and captions,
  zero retry count, and zero dropped frames during the verification window.
- Fixed Native hardware DVD film cadence on both commissioned Amazon devices.
  Some DVD MPEG-PS cells provide a PES PTS only for selected MPEG-2 pictures;
  Media3 extrapolated the missing values in decode order, and the MediaTek
  decoder then produced non-monotonic display/release times while reordering
  I/P/B pictures. `Mpeg2PictureTimestampCompleter` now scans bounded sequence,
  GOP, picture, and picture-coding metadata, preserves every authored PTS, and
  reconstructs only missing telecined-picture display times from
  `temporal_reference` and `repeat_first_field`. Metadata remains committed in
  byte order and no media payload or delayed `TrackOutput` metadata is
  buffered. The physically tested clean `0.5.85` APK (`f36e8396e428f917cbc6f912e1a370076e7b7bfdd173355c0c6945de31eac35f`)
  held 1.0217x real time on AFTKRT/API-30 with 756 video outputs over the
  20-second measurement window, zero drops, zero non-positive releases, and
  zero release gaps; AFTMM/API-25 held 1.0131x with 1,019 outputs and zero
  drops, skips, non-positive releases, or gaps. Pause/play, 2x FF/RW, and
  next/previous chapter recovered A/V on both, and the authored
  menu/audio/subtitle/chapter
  regression passed on the Pro.
- Made the old-server caption compatibility query safe during partially
  initialized/mock sessions. A missing preference store now means the legacy
  override is inactive instead of tearing down a newly created DVD player.
  This also restored all 146 Core tests as a mandatory full-suite gate.
- Closed the Fire TV Pro caption compatibility gate after the caption
  renderer re-enable and STV/explicit-service authority fixes were installed.
  The owner verified CEA-608 captions working on the AFTKRT/API-30 `.29`
  device. The later Native hardware DVD cadence, lag, A/V sync, and transport
  gate passed on the exact clean `0.5.85` APK on both commissioned devices and
  is tracked independently from captions and launcher artwork.
- Migrated every active Java class and Android component from the legacy
  `sagex.miniclient.*` namespace to the independent
  `opensagetv.vibe.miniclient.*` root. The established launcher class endings
  remain `phone.ServersActivity` and `tv.MainActivity`, making legacy-to-Vibe
  comparisons mechanical while their fully qualified component identities are
  distinct. Frozen `source/existing` and the protected JVL package were not
  changed.
- Restored the launcher resource structure used by the working legacy APK.
  The standard launcher inherits `@mipmap/ic_launcher_v2`; the Leanback
  launcher explicitly uses `@drawable/banner_v2` for icon, banner, and logo.
  Removed five TV-module mipmap overrides and the application `roundIcon`
  declaration that were absent from the working layout and could change Fire
  OS resource selection. The Vibe and legacy banners are both 320x180 8-bit
  non-interlaced RGB PNGs.
- Removed the unsuccessful separately installed Fire TV launcher package and
  reduced build, install, MCP, and logo workflows to one Vibe client APK. The
  main APK now retains both its standard and Leanback launcher activities in
  debug builds. Adaptive icon generation now places visible vector artwork in
  the foreground over an opaque background, and validation rejects a fully
  transparent foreground before Gradle runs. The Leanback activity uses the
  generated 16:9 artwork for its icon/banner/logo while the application keeps
  a conventional square icon. Physical AFTMM/API-25 testing proved that the
  current Fire OS sideload library renders the application icon in a centered
  square card even when both launcher activities declare the 320x180 banner;
  forcing the banner into `application@icon` does not produce a wide card.
  Version code `2101108`, clean installation, and launcher-cache refresh now
  produce a visible Vibe icon that launches the independent Vibe activity.
  Update installs can temporarily retain a blank/stale Fire OS card until its
  library refresh or device restart. The packaged 16:9 banner remains correct
  for Android TV and store/catalog artwork. The legacy
  `jvl.sage.miniclient.android.tv.debug` package is explicitly untouched.
- Fixed caption re-enabling without restarting playback. Selecting `Off`
  disables both ExoPlayer's text renderer and text track type; the Media3 and
  legacy Exo enable paths now restore both before applying CC1/CC2. Previously
  the UI and telemetry could report CC1 selected while the renderer remained
  disabled and emitted no cues until the video was restarted. The new physical
  `--toggle-off-on` caption gate passed without a restart on Media3 and legacy
  ExoPlayer using the timed CEA-608 fixture; screenshots are
  `artifacts/firetv/20260904-235030_caption-media3-dynamic-visible.png` and
  `artifacts/firetv/20260904-235234_caption-exoplayer-dynamic-visible.png`.
- Added an old-server caption compatibility policy and exposed it both under
  Audio and Caption settings and the MiniClient long-press navigation overlay
  as `SageTV STV`, `Off`, `CC1`, and `CC2`. The selection applies immediately
  during playback. Vibe-patched servers remain authoritative through
  `VIDEO_CC_STATE`; documentation now states clearly that true STV checkbox
  mirroring requires the updated SageTV `Sage.jar`, while explicit CC1/CC2 is
  available against an unmodified server.
- Strengthened the caption physical gate with an eight-second continuity check
  after same-session Off-to-On. The Media3 hardware Push fixture produced 183
  additional non-empty cue updates with a longest progress gap of 1.691 seconds;
  the visual result is
  `artifacts/firetv/20260904-235525_caption-media3-dynamic-visible.png`.

## v0.5.85

- Completed the Kodi-derived generated hardware-codec matrix on the normal
  AFTMM/API-25 Fire TV using Media3 Pull with hardware video decoding. MPEG-2,
  MPEG-4 Part 2, H.263, H.264 Baseline/High, HEVC Main/Main10, VP8, VP9
  Profile 0/Profile 2, and an H.264 mid-stream 640x360-to-1280x720 format
  change all passed with the expected MediaTek hardware decoder. A truncated
  H.264 startup remained process-safe. The MPEG-4 B-frame generator now records
  real reordered MP4 packets with distinct PTS/DTS values; an eight-packet
  controlled extractor model removes PTS and proves the retained-DTS fallback
  without enabling an unverified production timestamp rewrite. AV1 and secure
  DRM remain explicit hardware/asset skips. Evidence is
  `artifacts/firetv/kodi-codec-matrix-media3-pull-aftmm.json`.
- Audited the native Android hardware path against Kodi's current MediaCodec
  implementation. Added bounded MPEG-2 GOP timestamp completion for the
  Kodi-identified MediaTek OMX/Codec2 and NVIDIA OMX missing-PTS cases, kept
  tunneled playback explicitly disabled, and applied the existing user codec
  disable rule consistently to Media3, legacy Exo, and IJK. Media3/Exo retain
  their own MIME/profile/secure/capability checks; no runtime-derived automatic
  blacklist was introduced.
- Confirmed that Media3 already owns Kodi-equivalent keyframe-after-flush,
  timed frame release, output draining, and codec flush/reinitialization
  behavior. Recorded MPEG-4 Part 2 missing-PTS/DTS fallback and VC-1 MIME
  aliases as fixture-gated non-DVD follow-ups instead of applying unverified
  codec workarounds globally.
- Physically A/B tested the Kodi-derived MPEG-2 timestamp rule on the AFTKRT
  Fire TV Pro with the same real DVD, native SageTV Push bytes, synchronous
  MediaCodec queueing, and `OMX.MTK.VIDEO.DECODER.MPEG2`. At the same
  61.795-second input point, recurring frame-release gaps fell from 31 with the
  rule disabled to 10 with it enabled; both runs consumed 47,417,344 bytes,
  remained playing, and reported no player error. The final Auto APK was then
  rebuilt, installed in place, and passed the remote native-DVD gate. The
  subsequent clean-workflow APK (SHA-256
  `d8bec749d33eb5331a5d046ac5638297a52266780808400d828b714efa6c1bb0`)
  also passed exact-path native hardware start, pause/play recovery, advancing
  audio/video, and STOP teardown on the AFTKRT/API-30 device; evidence is
  `artifacts/firetv/dvd-firetv-pro-29-kodi-rules-clean-build.json`.

- Physically tested the reported Fire TV forced-banner sideload workaround on
  the AFTMM/API-25 device with clean uninstalls and reboots. Forcing the 16:9
  banner into `android:icon` removed the companion from Apps & Channels; the
  corrected single-activity `LAUNCHER` + `LEANBACK_LAUNCHER` variant was also
  omitted. Debug builds now retain the main APK's inherited square-icon
  `LAUNCHER` entry and hide only its duplicate Leanback entry. A clean install
  displayed `OpenSageTV Vibe`, launched `MainActivity`, and preserved the
  restored server configuration. The companion remains optional.
- Corrected Fire TV launcher artwork handling. The API-29 compatibility
  launcher now uses its generated square icon for Fire OS's sideload fallback
  while retaining the 320x180 banner on its Leanback activity; its sibling
  logo project also emits the opaque 1280x720 Amazon catalog asset. Debug
  builds avoid duplicate Leanback intent exposure while preserving direct and
  explicit companion-to-client launching.
- Replaced hand-maintained launcher/banner/in-app artwork with the deterministic
  `opensagetv-vibe-logo` pipeline. Unified Android commands regenerate,
  validate, atomically install, and SHA-record all 25 required resources before
  Gradle runs; no host Python or font installation is required. The resulting
  debug APK SHA-256 is
  `f265158cfe2c40ab24ff268e35efa4092ae08169bc9175cf30d6761b7499f690`;
  it was installed in place on the normal AFTMM/API-25 test Fire TV at
  `192.168.10.25`, preserving app data, and resumed `MainActivity` without a
  fatal exception.

- Completed the deterministic regular-video fixture handoff from the authored
  DVD work. The canonical MPEG-TS generator now proves burned PTS/frame,
  whole-second visual and audio sync pulses, dual 5.1/stereo AC-3, in-band
  CEA-608/708, and Comskip sidecar generation. The Ubuntu 26 unified build
  image was rebuilt as `u26-j11-release-v7` (image ID `27acc132879e...`) with
  FFmpeg/ffprobe, dvdauthor/spumux/spuunmux, ImageMagick, fontconfig, DejaVu
  fonts, and Pillow permanently installed and validated. A real four-second
  fixture was generated and probed inside the one reusable container.
- Closed the remote-DVD task after its final gate: 382 project tests, 56 MCP
  tests, Core JUnit, validation, and a clean 60-task APK build pass. The
  DVD-tested APK at that gate had SHA-256
  `75cfc4b606e0246094cab529ced7e06aa0790156d92fa2ebeb68cc9d8d8aac91`
  and was installed in place on the normal AFTMM/API-25 Fire TV with app data
  preserved; its launcher resumed without a Dev-process fatal exception.
  Blu-ray/BDMV remains separately `SKIPPED` until a valid fixture exists.
- Closed the finite compatibility/fallback implementation task and promoted
  its rules to the permanent contributor contract. The same APK has physical
  evidence for preserved/current Core, missing/old/current MIM, a real
  mid-session FFmpeg failure, explicit fail-closed behavior, Native/Pull
  recovery, SMB Auto fallback, and bounded user/MCP diagnostics. The durable
  compatibility matrix now reflects completed frame-step and DVD Hybrid/MIM
  commissioning instead of their former pre-test status.
- Fixed startup crashes on the Amazon AFTKRT Fire TV Pro running Fire OS API
  30. Fullscreen setup now obtains `WindowInsetsController` from the attached
  decor view, tolerates a temporarily unavailable controller, and falls back
  to the legacy system-UI flags instead of dereferencing Fire OS's unattached
  `DecorView`. Settings and launcher fullscreen setup now runs after content
  view attachment.
- Made initialization failure containment and discovery restart safe. An early
  renderer failure can no longer cause a second `TextView` null dereference
  before the error layout exists, and `ServerDiscovery` recreates its named
  executor after shutdown instead of rejecting a later discovery request.
- Made physical automation compatible with Android 10+ background-activity
  restrictions. `dev_connect_server` brings the Dev app to the foreground
  before its debug receiver starts the requested renderer, and focused-window
  inspection falls back to ActivityManager's resumed Activity on Fire OS
  builds that omit `mCurrentFocus`/`mFocusedApp`.
- Physically commissioned the fixes on `192.168.10.29` (Amazon AFTKRT, API
  30): three Settings open/back cycles retained one process with zero fatal
  exceptions; discovery found both test servers; GDX connected; and OpenGL
  hardware Media3 Pull passed startup, pause/play, HOME teardown, reconnect,
  and explicit shutdown. Installed APK SHA-256 was
  `75cfc4b606e0246094cab529ced7e06aa0790156d92fa2ebeb68cc9d8d8aac91`.

- Completed the explicit MIM main-title policy gate on the authored DVD fixture using
  `mcp-disc-test` with `--disc-policy mim_main_feature`. Hardware A/V startup,
  command recovery, and STOP/teardown now pass consistently with `playerActive` and
  decoder counters returning to false/inactive after stop. Evidence:
  `artifacts/test-results/vibe-authored-dvd-mim-main-feature-policy-gate-attempt2.json`.
- Completed physical Hybrid DVD main-title commissioning on the authored
  fixture. MIM title startup, pause/play, FF/REW with fresh datasource epochs,
  chapter changes, audio/subtitle selection, and subtitle toggle all recover
  with advancing A/V on the hardware Media3 path. Core now defers MIM until a
  title payload exists and releases its decoder lock while paused; Android no
  longer reuses an ended MIM reader generation after FF/REW.
- Added explicit runtime MIM-failure recovery reporting. When Hybrid has already
  been negotiated and FFmpeg fails at title startup, Android recognizes Core's
  `fallback=mim_failure` marker, tells the user that native DVD playback was
  restored, and exposes `discMimRuntimeFallback` plus a bounded compatibility
  reason to MCP. A real mid-session fault injection restored native hardware
  MPEG-2 with advancing audio/video and no player error; the test FFmpeg binary
  was immediately restored and SHA-256 verified.
- Made authored DVD subtitle synchronization directly observable. Both English
  and Spanish SPU cues now print the shared cue number, authored PTS, nearest
  video frame, and chapter while the video burns its live PTS/frame from the
  same clock. An executable generator test verifies the exact cue-2 contract
  (`00:00:02.000`, frame 60, chapter 1) instead of relying only on prose.
- Corrected the fixture's DVD `SetSTN` authoring: normal English/Spanish
  subtitles use enabled register values 64/65, while 63 remains subtitles-off.
  The previous 0/1 values selected a language but left the DVD display-enable
  bit clear. Deep authoring validation passes, and physical Media3/hardware
  runs now strictly observe selectors 64 and 65; the Spanish screenshot pairs
  burned PTS 00:00:06.573 with SPU PTS 00:00:06.000.
- Completed a second source-by-source audit of the DVD Presentation Engine
  v2.1 handoff after the subtitle and navigation fixes. Its 19 standalone tests
  and Android compile pass in the unified image. Sixteen selected portable
  source files match the handoff exactly except for trailing-newline formatting;
  every non-imported bridge, parser, drain, timestamp, overlay, Surface, and
  forced-subtitle component now has an explicit decision and verification row
  in `docs/DISC_PLAYBACK_DISCOVERY.md`.
- Completed the same-APK old/new Core compatibility matrix. The preserved
  pre-DISC `Sage.jar` passes ordinary Pull, SMB Direct, Fixed/MIM, Native DISC,
  and Hybrid Native fallback. Because old Core cannot query the new per-client
  policy, Android now reports that limitation and retains Native safely. The
  current negotiated Core fails unsupported explicit Hybrid with fallback off
  before playback. The current JAR and exact SHA-256 were restored afterward.
- Added bounded compatibility fault injection for missing MIM and preserved MIM
  0.4.5. Explicit Fixed cannot start with either unavailable/incompatible
  component, does not corrupt the server, and ordinary hardware Pull recovers.
  Exact MIM 0.4.7 files were restored and verified from their SHA-256 manifest.
- Fixed the green/blank embedded-preview regression without rebuilding the
  player or decoder. The player-owned Surface remains attached while SageTV's
  video rectangle transitions between fullscreen and the STV preview. Physical
  hardware tests pass for Push, Pull, SMB Direct, Fixed/MIM, and Native DISC;
  screenshots verify real video rather than relying on counters alone.
- Completed the released-source audit of historical extender and desktop
  behavior. The durable matrix distinguishes proven protocol/server behavior
  from closed-firmware inference for capabilities, buffering/bandwidth,
  growing files, live-edge seeking, captions, aspect/interlace, standby,
  reconnect, remote input, fast switching, DISC, and compatibility workarounds.
  Concrete proven behavior is implemented or represented by an explicit
  deferred task; no firmware-only guess was enabled globally.
- Made the long multi-disc MCP runner line-buffer its per-disc progress when it
  runs without a TTY in Docker/CI, preventing an active drive-spin/startup
  matrix from appearing stalled until the final JSON is written.
- Re-ran the complete private corpus on the final installed APK with restored
  current Core/MIM: all 51 populated Unraid DVD structures again passed real
  hardware A/V startup, STOP and crash checks, and the empty ROGUE_ONE structure
  failed safely twice. A fresh independent HDMI capture contains 1920x1080
  moving video (29 sampled unique frames) and non-silent stereo PCM
  (`mean_volume=-22.5 dB`, `max_volume=-3.7 dB`).

- Fixed legacy Exo hardware playback across HOME/background Surface recreation.
  Legacy Exo had bound the current raw `Surface`; Fire OS released it while the
  player retained and retried it, causing `ERROR_CODE_DECODING_FAILED` followed
  by `The surface has been released`/`ERROR_CODE_DECODER_INIT_FAILED`. It now
  binds the `SurfaceView`, allowing ExoPlayer to own holder callbacks and attach
  the recreated Surface. Two exact-path hardware Pull repetitions passed on the
  API-25 Fire TV with the same MiniClient connection, advancing A/V, automatic
  resume, explicit user-pause preservation, and teardown.

- Added a dedicated **Home and background recovery** options screen. Users can
  independently retain the same SageTV session, allow/deny automatic playback
  resume, and select a bounded disconnect timeout. Automatic recovery sends
  PLAY only when the background state machine itself paused active playback;
  user-paused and idle sessions remain untouched.
- Extended debug/MCP configuration and telemetry with
  `resumeBackgroundPlayback`, and extended the strict lifecycle gate with
  enabled/disabled auto-resume and timeout cases. Media3 hardware Pull passes
  advancing A/V, exact connection generation, Surface reattachment, manual
  pause, explicit PLAY, timeout disconnect, and teardown on API 25. Legacy Exo
  also passes same-session Surface recreation on API 25 after binding its
  `SurfaceView` instead of a released raw `Surface`.
- Completed the API-30 retained-session gate on the commissioned Fire TV Pro.
  Fire OS emits a MediaSession pause before the delayed application-background
  transition; the background state machine now coalesces that already-applied
  pause and retains ownership of the matching resume. MediaSession pause is no
  longer forwarded twice while background-session preservation is enabled.
  Resume repaint is also serialized through `ConnectionEventRouter` instead of
  writing the SageTV event socket from Android's main thread. This removes the
  observed `NetworkOnMainThreadException`, duplicate reconnects, stale-player
  teardown, and subsequent Pull read-position failure. Media3 hardware Pull now
  passes automatic resume, disabled automatic resume plus explicit PLAY,
  manual-pause preservation, exact connection retention, Surface recreation,
  configured timeout disconnect, replay, and teardown on both API 30 and API
  25. The tested incremental APK SHA-256 is
  `3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`.
  The final clean-build APK
  `c18cfdee04868b250b99381f85d45c00b6c5e2bd8fbf1e499fbe5fa27a78351b`
  then repeated the exact-session automatic-resume, user-pause, replay, and
  teardown gate on API 25.
- Corrected Fire OS process-status automation: a live PID remains authoritative
  during ordinary HOME even if its package stopped bit is stale from an earlier
  force-stop, while teardown separately recognizes a committed force-stop and
  reports a briefly terminating PID instead of misclassifying HOME or hanging.
- Added a negotiated server-controlled remote DVD implementation without
  replacing the player backends. Android now handles legacy MiniClient DVD
  commands 32-37, lazy `push:dvd`, CLUT/SPU highlight state, and server-owned
  navigation. Media3's DVD-only MPEG-PS extractor separates private AC-3
  substreams, honors first-access-unit pointers, and rejects false sync headers
  before hardware decode. Bounded development runs observed hardware MPEG-2/
  AC-3 on authored `SCOOBY_DOO_AND_BATMAN` and menu-less `ALADDIN`, plus several
  ALADDIN controls. The Native Media3/hardware gate is now commissioned below;
  Hybrid/MIM and compatibility fallback remain open in `TASKS.md`.
  A matching from-beginning Core request no longer inherits a saved disc
  resume point. Blu-ray physical commissioning is explicitly SKIPPED because
  no BDMV fixture exists on the supplied private share; existing main-title
  source/host behavior remains intact.
- Corrected the physical DVD MPEG-2 cadence and cell-drain failures found on the
  commissioned Amazon AFTMM/API-25 Fire TV. The DVD extractor reconstructs
  omitted telecined-picture display timestamps from bounded GOP metadata while
  preserving authored PTS and immediate byte-order `TrackOutput` delivery. DVD
  `0x100` EMPTY/PAUSE
  polls now signal generation-scoped end-of-input only after all bytes in the
  current Push epoch reach the extractor; queued audio/video drains normally,
  the server receives `-2`, and a later FLUSH or non-FLUSH segment resumes on a
  fresh reader generation without closing the MiniClient session. This removed
  the repeatable 292-second READY/BUFFERING deadlock at the end of
  `VTS_03_1.VOB`.
- Changed only complete, single-picture authored DVD menu sequences from a
  five-second to a 500 ms synthetic sample cadence. The old interval made
  Media3 declare the video renderer starved, stop menu audio at 17.45 seconds,
  and could overshoot the real A/V tail by nearly five seconds. Moving title
  video is not repeated or otherwise affected. The supplied
  `SCOOBY_DOO_AND_BATMAN` run now crosses the long-title boundary, displays the
  authored root menu and buttons, keeps menu audio/video READY through 130
  seconds, drains it cleanly, and reports no player/decoder error. All 47 DVD
  protocol tests, the Push generation unit tests, 53 MCP tests, Core tests, and
  debug APK assembly pass.
- Fixed repeated authored DVD submenu entry on Fire TV. Replacement menu cells
  now retain the existing Surface binding while `stop()` releases the prior
  decoder, preventing Fire OS from reporting a decoded one-picture buffer
  without latching it to the Surface. The DVD FLUSH path also resets its
  generation drain guard, so equal-sized authored menu cells receive their own
  transient end-of-input instead of being mistaken for an already drained
  cell. Four final root-to-Languages cycles rendered the submenu and active
  button highlight without locking.
- Corrected DVD subtitle visibility to follow SageTV's VM/STV state. The HD
  MiniDVDPlayer protocol uses bit 7 (`0x80`) for disabled title subpictures and
  bit 6 (`0x40`) for enabled subpictures; Android now decodes both forms and
  retains the selected low-five-bit stream id. Explicit `None` and the STV
  Subtitles checkbox now suppress ordinary and forced movie subtitle text,
  while an active VM button highlight remains renderable for menu navigation.
  Debug/MCP state now records the last overall, audio, and subtitle DVD stream
  values; the supplied disc physically reported `128` off and `64` on.
- Corrected DVD audio selection when private AC-3 streams are discovered in a
  different order than SageTV's language order. Media3 previously could keep
  the first discovered `0x82` Portuguese stream when the server-selected
  English `0xBD80` group was published after `STATE_READY`. Android now retains
  SageTV's packed stream request across DVD cell replacements and reapplies it
  from `onTracksChanged`. Debug/MCP telemetry records the requested, applied,
  selected, and available format IDs. On the supplied disc the main feature
  physically reported SageTV request `48512`, applied stream `48512`, and
  selected Media3 format ID `48512` while hardware MPEG-2/AC-3 output advanced.
  All 50 focused DVD protocol tests and debug APK assembly pass.
- Physically verified the authored Special Features submenu on Fire TV: root
  button navigation opens it, its Main Menu button returns to the authored root,
  and Special Features opens again with the correct background and active SPU
  highlight. The repeated navigation completed without a stale frame, malformed
  menu error, decoder error, or invalid Surface.
- Reviewed every source, test, and Markdown file in the Apache-2.0
  `SageTV-Android-DVD-Presentation-Engine-v2.1` handoff archive (SHA-256
  `7B9BF785F4547C245F3C313DAA86831A028152882739CA41AFFF6E5321053D4D`) and
  verified its complete source manifest and standalone test runner. Integrated
  its platform-neutral bounded per-stream SPU assembler, strict DVD control/
  RLE decoder, `CHG_COLCON`/CLUT/button compositor, palette utilities, physical
  audio-stream decoder, PTS helpers, diagnostics, and deterministic fixtures.
  The Android adapter deliberately preserves the physically proven Vibe
  Media3 transport, cell clock, Surface, drain, and SageTV/STV subtitle-off
  semantics; the handoff's alternative end-to-end bridge, timestamp rewriter,
  input gate, Android overlay view, and forced-only stream policy were reviewed
  but not imported. Added a 5,000-fragment deterministic malformed-SPU stress
  test, bounded debug counters, MPEG-audio PES selection support, and explicit
  stale-SPU clearing on `STP_DSP` so a later highlight cannot resurrect an old
  authored bitmap.
- Added negotiated DISC policy and option properties between Android and Core:
  `VIBE_DISC_POLICY`, `VIBE_DISC_SKIP_MENUS`, `VIBE_DISC_SKIP_PREVIEWS`, and
  `VIBE_DISC_NATIVE_FALLBACK`. An unavailable explicit Hybrid/MIM request now
  fails closed without damaging the MiniPlayer session when fallback is off;
  with fallback enabled it returns to Native Media3 playback. Both paths pass
  physical MCP commissioning against the updated Unraid server.
- Corrected the two distinct bypass behaviors. Skip menus now asks the Ogle VM
  for the longest authored title instead of assuming DVD title 1 is the main
  feature; skip previews retains menus and enters the authored root menu. The
  Scooby fixture physically shows the feature for the first case and the real
  animated root menu for the second.
- Characterized legacy Exo DVD independently. A direct DVD-only legacy
  `PsExtractor` removes generic-sniff ambiguity, but only two of six
  representative discs passed strict hardware startup; four exposed transient
  audio-track or decoder initialization failures. DVD requests for legacy Exo
  therefore resolve to the commissioned Media3 backend with visible/logged
  compatibility feedback instead of claiming unsupported parity.
- Expanded the private-disc discovery gate with a read-only Unraid scan: 52
  `VIDEO_TS` directories, 51 populated DVD structures, one intentionally empty
  invalid structure, and no `BDMV` fixture. No private media or credentials
  were copied into the repository.
- Added a reusable `mcp-disc-test` root workflow and server-path inventory for
  bounded real-device DVD startup, pushed-byte, player-error, crash, STOP, and
  teardown gates. Menu-less ALADDIN, an authored LEGO disc, a Polish/PAL disc,
  and the largest five-IFO/fifteen-VOB fixture pass the representative Media3
  hardware matrix. The empty ROGUE_ONE structure fails safely without a client
  crash. Corrected relative `--paths-file` resolution so the command works from
  the documented project-root workflow.
- Captured independent HDMI A/V evidence after the presentation-engine merge:
  1920x1080 MJPEG at approximately 29.75 fps plus stereo PCM, transcoded to a
  compact H.264/AAC evidence copy. The extracted frame visibly confirms the
  authored Scooby root menu and correctly aligned active Play button. Physical
  HDMI evidence supplements, rather than replaces, decoder/protocol/crash
  telemetry.
- Fixed late AC-3 discovery in video-first DVD cells. `private_stream_1` can
  carry only SPU data, so seeing PES id `0xBD` no longer falsely marks audio as
  discovered or calls Media3 `endTracks()` before a real `0x80..0x87` AC-3
  substream creates its TrackOutput. Also clear outgoing-cell audio overrides
  and mark a retained SageTV audio request applied only after its replacement
  TrackGroup override is committed. The three failures discovered by the first
  51-disc sweep (`RAYA_AND_THE_LAST_DRAGON`, `SOUL`, and `F9_UPD75`) now pass
  hardware A/V startup and STOP in a focused physical regression.
- Added an explicit expected-safe-startup-failure mode to `mcp-disc-test` for
  empty/invalid DVD structures, and made command sequences retain a compact
  post-command state while requiring the player to remain active/error-free.
  Fixed the root `install APK` workflow to resolve project-relative paths under
  `/workspace/android-client` just like APK inspection.
- Completed the bounded Native Media3/hardware DVD inventory gate: all 51
  populated Unraid `VIDEO_TS` structures pass real A/V startup, pushed-media,
  player-error, crash, STOP, and teardown checks. The single empty ROGUE_ONE
  structure remains an expected safe startup failure, and no BDMV fixture was
  found, so Blu-ray remains SKIPPED. Evidence is
  `artifacts/firetv/dvd-presentation-v21-all-unraid-dvds-final.json` and
  `artifacts/firetv/dvd-empty-structure-safe-failure.json`.
- Hardened `mcp-disc-test` so the MiniPlayer PAUSE state is checked as state 3,
  every command preserves its screenshot and compact state before a recovery
  assertion, and selected transport/stream commands require real post-command
  A/V counter advancement. The strict gate exposed a Core defect instead of
  accepting the wrapper's PLAY state as a false pass.
- Fixed DVD Skip Back near the beginning in Vibe Core. `VideoFrame` previously
  let a negative disc time reach the Java/Ogle VM, which calculated a negative
  sector, flushed the client, and stopped supplying media while Android still
  reported a live player. Core now clamps disc seeks to zero before
  `MiniDVDPlayer.seek()`. The formerly failing title-select, `ff_2`, `rew_2`,
  FF/PLAY, REW/PLAY, and chapter sequence passes strict A/V recovery on Fire TV.
  Five complete stop/restart cycles and a six-disc chapter/control matrix also
  pass. Evidence is
  `artifacts/firetv/dvd-scooby-verified-transport-recovery-core-clamp.json`,
  `artifacts/firetv/dvd-scooby-five-cycle-stop-restart-soak.json`, and
  `artifacts/firetv/dvd-representative-six-strict-controls.json`.
- Added a reusable Windows HDMI validation capture script with configurable
  DirectShow video/audio devices and bounded runtime. The final capture contains
  1920x1080 MJPEG title video and stereo 44.1 kHz PCM audio; its extracted frame
  shows clean Scooby title playback without a stale menu overlay. Evidence is
  `artifacts/firetv/dvd-presentation-v21-final-hdmi-av-18s.avi`, its
  `.ffprobe.json`, and `dvd-presentation-v21-final-hdmi-frame.png`.
- Corrected `mcp_session_test.py` optional streaming/decoding defaults so an
  omitted selection is not sent through argparse's value normalizer as an
  invalid empty string.

- Added `THIRD_PARTY_NOTICES.md` as the consolidated direct-runtime license
  inventory and executable checks for the opaque native bundles. The audit
  records Ostermiller Utilities as GPL-2.0, Logback Android as EPL-1.0 or
  LGPL-2.1, and Glide's component-specific BSD/Apache/MIT terms. It also
  verifies the checked-in IJK FFmpeg binary contains `--disable-gpl` and
  `--disable-nonfree`, while the active Exo FFmpeg extension is decoder-only
  and contains no GPL/nonfree enable flag. Full transitive reconciliation and
  publication packaging remain open and are not reported as complete.

- Added an on-demand `MediaCodecList` device capability profile that records
  hardware/software classification, supported MIME types, codec profile
  levels, maximum dimensions, and adaptive/secure/tunneled features without
  adding a continuous analytics listener or guessing deinterlace quality.
  Media3 and legacy Exo now use the same platform-aware classification while
  preserving explicit hardware, software, and ordered hardware-preferred
  fallback policies. The debug receiver and MCP expose the inventory plus the
  decoder actually selected for a bounded test. The clean APK SHA-256 is
  `ad7e89e3ec92c9116b61e39ce887706069edc6c820e8cfc29d2436172d64f005`.
  On Amazon AFTMM/API 25, both Media3 and legacy Exo hardware Pull selected
  `OMX.MTK.VIDEO.DECODER.MPEG2` for the canonical 1080i fixture with one
  initialization and no player error; evidence is
  `artifacts/firetv/codec-capability-media3-pull.json` and
  `artifacts/firetv/codec-capability-exoplayer-pull.json`. The complete gate
  passes 298 project/static tests, 49 MCP tests, Core JUnit, validation, and
  all 60 clean APK tasks.

- Added backend-neutral preferred track selection. Users can configure BCP-47
  audio/subtitle languages, CEA-608 CC1-CC4, or CEA-708 Service 1-63 under the
  new Audio and Caption Track Settings page. SageTV/STV remains the sole
  caption Off/On authority; the Android preference only resolves a preferred
  track after the STV enables captions. Media3 and legacy Exo2 now expose the
  caption accessibility channel, apply preferred audio language through their
  track selectors, retain pending STV selection until track discovery, and use
  the selected broadcast service in their TS extractor configuration. MCP
  configuration/telemetry and the physical caption gate cover these settings.
  Amazon AFTMM/API-25 hardware Pull passed explicit Media3 CEA-708 Service 1
  and legacy Exo2 CEA-608 CC1 with real rendered cues; evidence is
  `artifacts/firetv/20260831-021518_caption-media3-pull-visible.png` and
  `artifacts/firetv/20260831-021650_caption-exoplayer-pull-visible.png`. The
  final clean APK SHA-256 is
  `b9ee3c91ca3ad74b15bfe74efe3f6592aebbf885af9b458993f8eb99fd647841`;
  its exact install repeated the Media3 hardware Pull gate with 498 ms
  caption/timeline drift as
  `artifacts/firetv/20260831-022831_caption-media3-pull-visible.png`.

- Added bounded, payload-free connection lifecycle diagnostics for generation,
  worker start/stop state, protocol command/reply counts, event/GFX queue depth,
  reconnect, and close state, with debug/MCP visibility and host tests. Replaced
  the unretained `ANDROID-MINICLIENT` thread with a lifecycle-owned single-
  thread executor/Future. Pause/destroy now cancel and invalidate the request;
  a late result closes its exact connection and cannot publish an event or UI
  error into a destroyed activity.
- Physically proved the connection change on Amazon AFTMM/API 25. The durable
  ordering gate retained media-before-GFX startup, ordered replies, bounded FIFO
  drain, all-worker HOME teardown, a newer foreground generation, and explicit
  close. The hardware Pull lifecycle gate then passed advancing A/V,
  foreground reconnect, surface recreation, three exact-path replays, and
  final no-process teardown. Ordering evidence is
  `artifacts/firetv/connection-ordering-20260830-230236.json`. The consolidated
  gate passes 288 project/static tests, 47 MCP tests, Core JUnit, validation,
  and all 60 clean APK tasks; the final clean APK SHA-256 is
  `ee0a20389d582657bddcaaf40ca88770a2d3cff7c87deda61bb4ed201d8562b4`.
- Continued the connection extraction behind the same physical gate. A
  connection owner now retains Media/GFX sockets and all four protocol worker
  handles, makes close idempotent, and waits on one bounded deadline. Explicit
  Media-worker readiness replaces the blind 100 ms startup sleep.
  `ConnectionEventRouter` retains the single 100-event FIFO and reconnect
  suppression; `GfxFrameExchange` owns one exact header/body pair until serial
  dispatch completes; and `ConnectionFileTransferOwner` gives named concurrent
  transfers connection-owned socket/stream/file cancellation. An atomic
  diagnostics snapshot correction prevents equal queued/dequeued counts from
  being paired with a stale depth during MCP verification. Current physical
  evidence is `artifacts/firetv/connection-ordering-20260830-230236.json` from
  the exact clean-installed APK above.
- Extracted reconnect eligibility/event suppression into
  `ConnectionReconnectState` and replaceable GFX input/event output lifetime
  into `ConnectionProtocolStreams`. Focused tests cover the original
  negotiated-support, live, first-frame, encryption, replacement, and
  idempotent-close rules. The post-extraction physical gate passed using only
  the canonical captioned/comskip fixture; evidence is
  `artifacts/firetv/connection-ordering-20260830-231530.json`.
- Removed the superseded non-captioned `VibeSeekTest-1080i-MPEG2-AC3.ts` and
  matching `.edl` locally and from the commissioned Unraid test instance. The
  connection gate now defaults to
  `/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts` so future omitted-path
  runs use the newest embedded-caption fixture.
- Split `GFXCMD2` behind a characterized family map into lifecycle/frame,
  drawing, image/cache, font, surface/video, and transform/batch handlers. A
  shared allocator preserves the original handle namespace, image/cache state
  remains serial, unsupported commands retain their prior results, and raw
  native delegation remains ahead of the Java handlers. `GFXCMD2` is now about
  260 lines instead of roughly 950. The MCP connection gate gained an explicit
  `opengl|gdx` renderer selector and reports its selection. The complete split
  passed the canonical captioned/comskip fixture on OpenGL and GDX. The exact
  final clean APK was installed and repeated those gates as
  `connection-ordering-20260830-235310.json` (OpenGL) and
  `connection-ordering-20260830-235518.json` (GDX).
- Replaced the duplicated OpenGL/GDX `GFXCMD_INIT` 100 ms readiness polling
  loops with a shared, one-shot `RendererReadinessGate`. Resize publishes
  readiness, renderer close/deinit cancels and releases a waiter, late readiness
  cannot revive a cancelled renderer, and interruption is preserved. Focused
  JUnit and APK compilation pass; physical post-change evidence is
  `connection-ordering-20260830-235947.json` (OpenGL) and
  `connection-ordering-20260831-000156.json` (GDX).
- Made delayed keyboard visibility work lifecycle-owned: one explicit main-
  looper handler replaces view-local delayed work, replacement removes the
  preceding request, and pause/destroy invalidate and remove pending work.
  Media3 and legacy Exo progress/seek-recovery scheduling now share one explicit
  main-looper handler per player and remove the progress callback during release
  or replacement. Both hardware Pull lifecycle gates pass with advancing A/V,
  HOME/return surface recreation, replay, and final process teardown. The final
  exact clean-installed OpenGL connection gate is
  `connection-ordering-20260831-002155.json`, and the commissioned device
  preference was directly verified as
  `use_opengl_ui=true`.
- Replaced Push transport polling with notification-driven back-pressure. Core
  `PushBufferDataSource` now wakes producer/consumer waits on open, bytes, EOS,
  flush, close, and release; Media3, legacy Exo, and the GSY adapter consume
  that blocking contract directly. IJK now has a release-aware datasource-open
  monitor, atomic detach/release, and an unconditional zero-length seek probe
  response instead of a probe accidentally gated by verbose logging. Focused
  Core/Android tests pass. Physical hardware Push playback passes on Media3,
  legacy Exo, and IJK; IJK exact-file playback, pause/play, crash inspection,
  and clean exit all pass on Amazon AFTMM/API 25.
- Gave each `SmbDirectSession` one retained, idempotent cleanup owner instead of
  constructing untracked cleanup threads. Post-change Pull/SMB large-seek A/B
  passes on Media3 (415/772 ms) and legacy Exo (333/1013 ms). SMB supplied
  92,425,984 and 86,396,672 bytes respectively while MediaServer media reads
  remained zero; only the 17-byte shadow protocol exchange was observed. The
  bounded SageTV Pull growing-file SIZE retry remains documented and
  intentional because the protocol has no asynchronous file-growth signal.
  The consolidated post-transport gate passes 289 project/static tests, 47 MCP
  tests, Core JUnit, validation, and all 60 clean APK tasks. The clean APK
  SHA-256 is
  `cb57f588955b07bd18a310e8de8ef20a151861f48a3ca568b50d3cd548e733e4`.
  That exact installed artifact passed media-before-GFX startup, pause/play A/V
  recovery, HOME teardown of all four connection workers, newer-generation
  reconnect, and explicit close as
  `artifacts/firetv/connection-ordering-20260831-005301.json`.
- Migrated the Leanback launcher/server browser from framework fragments to
  AndroidX (`FragmentActivity`, `BrowseSupportFragment`, support
  `DialogFragment`, and `FragmentContainerView`) without changing its server
  records or connection behavior. Static regression coverage, Android compile,
  install, and physical Fire TV navigation pass. The launcher renders, Add
  Server opens and reaches its Add action using only D-pad navigation, and the
  temporary Auto Connect exercise completed without a fragment exception. The
  commissioned device's Auto Connect preference was restored to `false` after
  the test.
- Removed the remaining playback-overlay framework fragments without forcing
  libGDX into an incompatible activity hierarchy. Lifecycle-owned
  `NavigationDialog`, `VideoInfoDialog`, and `HelpDialog` provide one
  implementation for the OpenGL `Activity` and libGDX `AndroidApplication`;
  pause/destroy dismiss the retained overlay set. A static guard now fails on
  framework Fragment imports, transactions, or `getFragmentManager()` calls.
  OpenGL physically rendered all three dialogs over the canonical captioned
  fixture; libGDX rendered navigation, and HOME teardown produced no leaked-
  window, bad-token, unregister, or fatal-runtime signature. The complete
  60-task clean APK SHA-256 is
  `ef16e5a24cb1d6cb41e7aaa160bdb559cf7dec3055746adb25366d7c8056a398`.
  That exact clean-installed artifact passed the connection/lifecycle gate as
  `connection-ordering-20260831-013302.json` (OpenGL) and
  `connection-ordering-20260831-013520.json` (GDX).
- Completed the physical storage lifecycle gate on Fire OS API 25. The existing
  app-private `Documents/logs` plus FileProvider share flow remains the
  permission-free user path. Disposable sentinels in the Dev app's internal
  files directory and app-specific external log directory both survived an
  exact `adb install -r` upgrade. Uninstall then removed
  `/sdcard/Android/data/org.opensagetv.miniclient.dev.debug` completely, and
  the exact clean APK was reinstalled. No production package or user media was
  touched.
- Completed centralized fullscreen commissioning. Exact-path initial and three
  repeated starts retain the calibrated 1920x1080 SageTV destination; OpenGL
  and libGDX overlays, Video Info, Help, Fire TV system overlays, HOME/return,
  and teardown pass. The Fire TV keyboard was physically shown over active
  playback (`mInputShown=true`), remained inside the calibrated full-screen
  window, and cleared its active request after the debug IME-hide operation.
  Evidence is `artifacts/firetv/keyboard-fullscreen.png`; no active production
  class outside `AppUtil` directly changes system-UI flags.
- Closed the Android feature-foundation gate after reconciling the executable
  scheduling inventory with physical evidence. Every discovered production
  async/connection/transport/renderer/keyboard/player/SMB/overlay owner has
  explicit cancellation or teardown; the bounded SageTV growing-file SIZE
  retry is intentionally retained because its protocol has no notification.
  Remaining large-class decomposition, dependency churn, predictive-Back API
  33+ commissioning, fast switching, rates, GSY, and publication work is now
  clearly below the deferred boundary rather than falsely blocking unrelated
  feature development.
- Extracted the proven common Media3/legacy-Exo behavior into small typed,
  backend-neutral components without changing player construction or the
  protected base-player contract. `PlayerRuntimeConfig` captures one immutable
  per-load tuning snapshot; `PlaybackSyncPointPolicy` owns direction-aware
  sync-point selection; `PullSeekRecoveryMonitor` owns the cancellable,
  generation-guarded recovery timer; and typed health/datasource contracts
  replace reflection for the two primary backends while retaining a fallback
  for IJK/System diagnostics.
- Commissioned the extraction with hardware decoding on the Amazon AFTMM.
  Media3 and legacy Exo hardware Pull each retained exact paused 33 ms frame
  advance, advancing output, and safe playing rejection; Media3 Push retained
  safe unsupported behavior. Pull/SMB Direct large-seek A/B passed on both
  backends with typed source evidence. Media3 recovered in 529/564 ms and
  legacy Exo in 742/634 ms for Pull/SMB respectively. SMB read 94,508,032 and
  95,556,608 media bytes while MediaServer media-read bytes remained zero;
  only the documented 23-byte shadow protocol reply was observed. The
  consolidated gate passes 281 project/static tests, 47 MCP tests, Core JUnit,
  full validation, and all 60 clean APK tasks. The clean APK SHA-256 is
  `27cf142fd7f4b4587926cb608da54c8c277a2d2644382e4b36f4c1ebc1f0d8f2`;
  that exact APK was clean-installed and passed full-screen Media3 hardware
  Pull with advancing MPEG-2 video, AC-3 audio, captions, typed datasource
  counters, a valid 1920x1080 surface, and no player error.
- Implemented MiniPlayer protocol command 28 end to end. The Core dispatcher
  now decodes the signed frame count and returns an explicit supported result;
  Media3, legacy ExoPlayer, and IJK perform bounded exact seeks while paused,
  remain paused, and reject Push, playing, zero-count, stale-session, and GSY
  System cases without disturbing playback. Capability advertisement is
  limited to forced random-access Pull/SMB modes. The historical repeated-
  PAUSE shortcut now delegates to the same player contract and falls through
  to SageTV when unsupported.
- Added the debug/MCP `dev_frame_step` tool and durable
  `mcp-frame-step-test`. Physical hardware MPEG-2 Pull gates passed on Media3
  and legacy ExoPlayer with a 33 ms position advance, one newly rendered
  frame, retained pause state, and exact command/invoke/return event evidence.
  A playing request and a physical Media3 Push request both returned
  unsupported cleanly. No software-decoder case was used. The complete gate
  passes 276 project/static tests, 47 MCP tests, Core JUnit, full validation,
  and the clean 60-task APK build. The exact clean APK installed and rechecked
  on the Fire TV is SHA-256
  `c32a200f5eda42e1380763db513738985a5d98a081dd83d2638a38fd1d07beb6`.
- Added a serialized `PlaybackSessionController` with monotonically increasing
  session and operation generations for load, seek, play/pause, flush,
  inactive-file transition, surface replacement, recovery, stop, and free.
  Base, Media3, and legacy ExoPlayer now reject queued work, listener events,
  progress callbacks, recovery watchdogs, and surface callbacks belonging to
  an obsolete player session. The explicitly reviewed `BaseMediaPlayerImpl`
  SHA-256 is
  `acfb0c04c5dc3c967a5eaac9b814a5274fbe1534f3fd8fadf1c7067bd5f0a0a8`.
  The installed hardware-test APK SHA-256 is
  `97d29f0a7700864d7b8c9cc50fe8928a8a593758765e29aa38d247fe062b7c63`.
- Physically commissioned the session controller on Media3 and legacy
  ExoPlayer with hardware decoding: Pull background/foreground and surface
  replacement, three repeated starts, process teardown, Push and Pull FF/REW,
  caption retention, completed-file EOF, and real 2.1 to 5.1 live-TV changes
  all pass. Push replacement-stream recovery was 3.031-3.041 seconds and Pull
  seek recovery was 1.012-1.039 seconds. Evidence is retained under
  `artifacts/firetv/session-controller-*-20260830.log` and the matching
  `20260830-20*_caption-*-visible.png` screenshots. Legacy ExoPlayer remains
  the default and no server protocol or transport capability changed.
- Revalidated the completed controller tree in the unified Ubuntu 26 build
  environment: 270 project/static tests, 46 MCP tests, Core JUnit, full source
  validation, and manifest/diff integrity all pass.
- Moved Media3 and legacy ExoPlayer caption overlays above the SageTV STV
  timeline with a shared 0.18 bottom-padding fraction. The deterministic A/53
  fixture now emits CEA-608 CC1 roll-up on row 14 and was regenerated as a
  900-second canonical file (SHA-256
  `b54b5475e4edce1dd248d263e04e54721a01d3dc4ab5b7f41ec125dc692a473e`).
- Strengthened `mcp-caption-test` to restart the deterministic fixture, use the
  stable middle roll-up cue, reject caption/media-clock drift over one second,
  pause without seeking for the absolute STV-timeline screenshot, and keep the
  paused OSD visible for a default 12-second test-only evidence window. Normal SageTV OSD
  timing is unchanged. Hardware Fixed/MIM and Push both pass: the retained
  screenshots are `20260830-190054_caption-media3-fixed-visible.png` and
  `20260830-190557_caption-media3-push-visible.png`; their stable middle cue is
  0.5 and 1.0 seconds behind the displayed STV time respectively.
- Added repeatable server-owned `--seek-command` caption gates. The exact
  Fixed/MIM FF, REW, FF_2 sequence recovered real A/V and stable captions in
  328-331 ms per operation, then measured 4 ms of caption/media-clock cadence
  drift. Post-seek screenshot
  `20260830-191129_caption-media3-fixed-visible.png` remains within the
  established one-second visual STV tolerance and clears the timeline.
- The matching hardware Push FF/REW/FF_2 gate passes with a transport-aware
  3,000 ms replacement-stream settle. A 300 ms check raced the server's second
  FLUSH and correctly failed with zero replacement bytes; the durable default
  now waits for that Push restart without changing Fixed timing. Post-seek
  screenshot `20260830-191636_caption-media3-push-visible.png` shows stable
  cue 3:37.0 versus STV 3:38 and 4 ms cadence drift.
- Installed the caption-overlay build on the commissioned Fire TV. The debug
  APK SHA-256 is
  `cdd9b311e95721edfaa9cb352f6347b306d0ab55775c5419bdefd9423ceae6fb`.
- Added an unknown-duration growing-media live-edge fallback to Media3 and
  legacy ExoPlayer. When duration is unavailable, each backend clamps an
  out-of-range request against its buffered edge minus the existing two-second
  live margin; completed media and no-buffer cases retain their prior behavior.
  Focused Core/static tests pass. On real channel 2.1 hardware Pull, Media3
  clamped 86,400,000 ms to 9,174 ms and recovered advancing A/V in 9,491 ms;
  its independent repeat reached 5,734 ms and recovered in 6,262 ms. Legacy
  ExoPlayer reached 8,643 ms and recovered in 9,222 ms. All runs emitted
  `seek_clamped_live_edge`. The installed APK containing both backends is
  SHA-256 `a4b9d29788e68f0e4d24f6c0fc05b3a9ea4916f9a1a744b1b18eaf41f2f5ab7b`.
- Increased the completed-file exact-end guard from 500 ms to 5,000 ms after
  physical evidence showed that 500 ms landed inside the fixture's final GOP,
  produced repeated Media3 timeout/recovery cycles, and never reached a clean
  end state. The five-second margin leaves decodable MPEG-2 content and still
  reaches natural EOF. Hardware Pull exact-EOF gates now pass on Media3 and
  legacy ExoPlayer at the 899,959 ms fixture duration with a clean ended state,
  no delete prompt, and no process death. The installed APK SHA-256 is
  `4f3cd3653ed7c0cf62b0ce692da88762cacf67563de7758e4f114b9089044983`.
- Passed the exact MIM 0.4.7 hardware-only seven-selection matrix: legacy
  ExoPlayer, Media3, IJK, GSY Auto, GSY Media3, GSY legacy Exo, and bounded GSY
  System-safe-delegate prerecorded plus real 2.1/5.1 live gates. Every MIM job
  was fresh Intel VAAPI/`h264_vaapi`, matched the requested input, stopped
  cleanly, and left no orphan. Evidence is
  `artifacts/firetv/fixed-mim-20260830_180555/FIXED_MIM_MATRIX.json`. Android
  software-decoder cases were deliberately not rerun in this commissioning
  pass; their older results remain historical evidence only.

- Revalidated the complete v0.5.85 headless release gate in the unified
  Ubuntu 26 environment: 262 project/static tests, 46 MCP tests, Core JUnit,
  full source validation, the clean 60-task debug APK build, the 149-task
  debug/release bundle pipeline, lint, signing, bundletool validation, and
  universal APK-set generation all pass. Artifact SHA-256 values are
  `2e6ceab87dfd17d032e10701131b0db167587a4d6638b11634c8548a6faf3451`
  (debug APK),
  `54067927e00b893e6237a87d40cd12f47c2ab872041b7564024ed5c553578177`
  (debug AAB),
  `b94255cf1ecbb701a1758537dc7a186f5efe039879297baa6e78cabe924fc4c5`
  (release-candidate AAB), and
  `ec1f473d5285f9e4df932979c27ba4688797d14ea8fcf84b910f0d87998d75bb`
  (debug APKS). The release candidate remains deliberately non-publishable
  until the production identity and signer are approved.

- User-verified on 2026-08-30 that
  `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` carries working captions. This validates
  the generated media itself; automated Android/STV caption selection,
  transport, seek, retention, and teardown gates remain independent evidence.
- Added the canonical deterministic broadcast-style seek fixture generator.
  It creates 1080i MPEG-2 video, 5.1 and stereo AC-3, burned-in PTS/frame
  markers, real in-band ATSC A/53 GA94 CEA-608 CC1 plus CEA-708 Service 1
  captions every 0.5 seconds, and prerecorded Comskip `.edl` intervals.
- Made fixture generation fail if stream-copy muxing does not retain exactly
  one GA94 payload for every coded MPEG-2 picture. MCP reports the payload
  count, caption transport/services, output hash, and can publish both the
  `.ts` and `.edl` through the existing SMB fixture workflow.
- Separated synthetic fixture captions from live-TV policy: no synthetic text
  is ever injected into real media. Real HDHomeRun 2.1/5.1 channel changes
  remain the authoritative growing-live transition test.

## v0.5.84

- Fixed the client-side fullscreen compatibility fallback that could race the
  SageTV STV/Core transition and toggle `MediaPlayer OSD` back to `Main Menu`
  with video in the embedded preview. The fallback now gives the STV a bounded
  2.5-second ownership window, consumes `MENU_HINT`, never sends the toggle
  while an OSD or popup owns navigation, and remains available for a stable
  stock-server preview.
- Corrected MCP fullscreen validation to use SageTV's actual video destination
  rectangle instead of the always-fullscreen Android `SurfaceView`. Debug
  status version 18 exposes destination and UI rectangles, preventing the
  former false pass where a 572x322 Main Menu preview sat on a 1920x1080
  surface. The pre/post screenshots are
  `artifacts/firetv/fullscreen-regression-20260830/current-user-regression.png`
  and `state-aware-post-fix.png`. The rebuilt Dev APK SHA-256 is
  `2e6ceab87dfd17d032e10701131b0db167587a4d6638b11634c8548a6faf3451`.
- Physically verified the initial exact-path start plus three stop/restart
  starts on Amazon AFTMM/API 25 with legacy ExoPlayer hardware Pull. Every run
  retained `MediaPlayer OSD`, a 1920x1080 destination at 0,0, advancing MPEG-2
  video and AC-3 audio, and no crash.

## v0.5.83

- Eliminated the exact-path commissioning race that could briefly enter
  `MediaPlayer OSD` and then toggle back to the SageTV Main Menu preview. The
  Vibe Core event now enters the canonical playback menu directly, while MCP
  waits two seconds for a server/STV-driven transition before sending a
  backward-compatible `TV` command and requires 750 ms of stable fullscreen
  state. The physical Fixed/MIM rerun remained fullscreen and passed at
  `artifacts/firetv/fixed-mim-20260830_132155/FIXED_MIM_MATRIX.json`.
- Made post-restart MIM verification reproducible with
  `scripts/query_unraid_mim_status.sh`, avoiding nested-shell SSH argument
  loss. The isolated server's active, appdata, and pinned wrappers now report
  0.4.6; its selected wrapper and `ffmpeg.real` hashes match the unified build
  output. A fresh Intel VAAPI/h264_vaapi prerecorded job passed controls,
  restart, teardown, stable fullscreen, and zero-orphan validation.
- Removed GSY's unused transitive Media3 Cast and Session surface while
  explicitly retaining the already-tested non-Cast AndroidX/Kotlin versions.
  Cast, MediaRouter, DataTransport, Bluetooth validation, and ProfileInstaller
  components are absent from the rebuilt debug and release-candidate manifests;
  the release APK inspector now rejects their accidental return. The strict
  build, debug/release AAB pipeline, release-candidate APK inspection, and
  physical generated-fixture hardware Pull startup/fullscreen/FF/REW/pause/
  resume gates pass. The seek harness now supports rapid-only repeated runs
  from a fixed fixture position. After one retained intermittent failure,
  zero-delay 16-command FF/REW stress passed three fixed-position repeats on
  each combination of Media3/legacy ExoPlayer and Pull/SMB Direct. Pull
  recovered in 0.906-1.662 seconds and SMB Direct in 2.541-3.636 seconds.
  The generated fixture's 120-180 second commercial marker also passed on all
  four combinations with an exact SageTV-owned 180000 ms seek. First physical
  read/first frame timing was 625/799 ms (Media3 SMB), 539/728 ms (legacy Exo
  SMB), 911/1112 ms (Media3 Pull), and 2483/2560 ms (legacy Exo Pull).
- Restored the exact MIM 0.4.6 Linux artifacts on the isolated Unraid test
  server after detecting an appdata-mounted 0.4.5 wrapper. Added an explicit
  Fixed-matrix caption policy so cue-less synthetic fixtures record captions
  as skipped instead of producing either a false failure or a false caption
  pass. Legacy ExoPlayer software decode then passed the complete generated
  prerecorded Fixed control/restart sequence. IJK passed that sequence and
  four 2.1/5.1 live changes in three consecutive commissioning runs. Every run
  reported fresh Intel VAAPI/h264_vaapi jobs and zero MIM orphans. Intel Fixed
  is now supported as an opt-in path; the default remains disabled.
- Completed the hardened incremental-update runner gate: all 17 archive,
  metadata, full-manifest, deletion, baseline-drift, selection, idempotency,
  and resume tests pass. The forced validation failure resumes at VALIDATE
  without repeating TEST, then completes BUILD, INSTALL, and deterministic
  DEV001 launch exactly once.
- Extended the deterministic seek fixture generator to create and optionally
  publish bounded `.edl` commercial markers beside the MPEG-TS file. The MCP
  generator now reports the sidecar, publishes both files to SMB, and no
  longer contains a dead duplicate metadata function. The Comskip harness can
  wait for a delayed STV-owned automatic skip, proves the corresponding server
  seek target, reports source-read/first-frame timing, and uses server-owned
  reverse/forward positioning for Push instead of an invalid client-local seek.
- Completed the controlled `use_nio_transfers=false`/`true` MediaServer A/B on
  the same generated recording, Media3 hardware decoder, Fire TV, and large
  SageTV seek. Non-NIO Pull recovered in 340/434 ms (first read 85/167 ms;
  first frame 621/692 ms). NIO Pull recovered in 447/638 ms (first read
  558/178 ms; first frame 728/828 ms). Every run delivered positive, consistent
  MediaServer byte counts and recovered A/V; NIO did not improve this case, so
  the partial-write hypothesis is not supported by this experiment. The
  isolated server was restored to `use_nio_transfers=false`.
- Verified that legacy ExoPlayer already exposes matching Pull open/read/wait/
  byte/error telemetry physically, so the obsolete telemetry backlog item was
  removed rather than duplicating the counters.
- Captured repeated genuine SageTV-owned Push reposition operations followed
  by `server_flush_command`, `server_anchor_set`, player reprepare, first video
  frame, and sustained A/V recovery. This closes the protocol-ordering evidence
  gate without assigning local seek ownership to the Push backend. The affected
  checkpoints were recovered from the old container-only artifact path before
  correcting all active MCP defaults to the bind-mounted repository path.
- Replaced stale `/workspace/artifacts/firetv` configuration and script
  fallbacks with the unified checkout path
  `/workspace/android-client/artifacts/firetv`; direct MCP calls now retain
  diagnostics under the project just like `dev.sh` calls.
- Added exact server-path fixture support to the runtime tuning matrix and
  corrected its fast-start logic so exact-path cases do not incorrectly query
  a Search/Sagex MediaFile ID. Repeated Media3 Push Sync versus Auto three
  times on the same generated recording, Fire TV, server, and hardware decoder.
  Sync recovered in 7196/6477/7418 ms with no watchdog; Auto recovered in
  8752/4236 ms and expired the 50.650 s watchdog once. Sync won two of three,
  had far lower variance, and avoided the observed stall, so the existing Sync
  default is retained while both modes remain classified as slow recovery.
- Completed and published `docs/PLAYER_SERVER_COMPATIBILITY.md` with explicit
  stock-server, Vibe `Sage.jar`, and FFmpeg/MIM boundaries plus independent
  Media3, legacy Exo, IJK, and GSY delegate rows. PASS, FAIL, SKIPPED, and
  unsupported conditions remain distinct. Legacy ExoPlayer remains the frozen
  default pending an independently authorized measured backend change.
- Confirmed restored detailed Push telemetry physically: channel/stream/target
  bandwidth, mux/buffer time, pushed/read bytes and counts, read rate/wait,
  anchors, flushes, decoder output, and bounded MCP snapshots are present in
  the generated Push evidence. The obsolete implementation task was removed.

- Revalidated the final clean APK in the unified development container: 255
  project tests, 42 MCP tests, Core JUnit, validation, and all 60 clean Gradle
  tasks pass. The exact APK (`6368dffcccb43ebc566a25f1fcfe7fd603a5fa567a367619662aef37e2f2b34f`)
  was installed in place on Amazon AFTMM/API 25 while preserving `DEV001`.
  Legacy Exo and Media3 then passed hardware SMB Direct start, FF/REW, large
  jump, pause/play, repeated start, stop/restart, fullscreen, crash, and
  teardown gates using `VibeSeekTest-1080i-MPEG2-AC3.ts`. The physical profile
  save/list/load/overwrite/delete gate also passed and removed its test file.
- Re-enabled the previously hard-disabled app-private file logging and Share
  Log preferences, corrected their obsolete public-SD-card guidance, and made
  sharing deterministically select the newest log. On Fire TV the app created
  `Documents/logs/sagetv-miniclient.txt`; its FileProvider URI opened X-plore's
  share destination without storage permission. File logging was returned to
  off after the gate. A generated-fixture screenshot and 1920x1080 surface
  telemetry also prove full calibrated-viewport playback on the current device.
- Made the deterministic generated fixture the default for ordinary session,
  lifecycle, EOF, Pull/SMB, and Fixed/MIM examples and tests. Meet the Press is
  retained only where its real CEA captions or `.edl` markers are required.

- Implemented optional SMB Direct/Shadow Pull for Media3 and legacy
  ExoPlayer using SMBJ 0.13.0 (SMB2/SMB3), longest-prefix Windows/UNC path
  mappings, configurable read-ahead, explicit failure, and Auto fallback.
  MiniPlayer control and ordinary Pull codec/container negotiation remain
  unchanged; credentials are separate and redacted.
- Kept the stock MediaServer shadow file synchronized with exactly one counted
  byte at the matching offset for each non-sequential SMB access. Physical A/B
  tests proved `OPEN + SIZE` and a byte-zero-only probe do not cause stock
  SageTV to emit the required STV seek/Comskip command after an Android-local
  seek. Normal media bytes remain SMB-owned and continuous fake reads are not
  generated.
- Added deterministic exact-path/start support, SMB mapping configuration, and
  server-owned positioning to the MCP session/Comskip harnesses. Known-marker
  tests now require both a new server seek sequence and a target landing; the
  harness restores fullscreen before issuing RIGHT/LEFT and handles chained
  server seeks without creating a false A/V failure.
- Physically passed the complete generated `VibeSeekTest-1080i-MPEG2-AC3.ts`
  SMB matrix on Media3 and legacy ExoPlayer: start, seek, FF/REW, large jumps,
  pause/play, two repeat starts, stop/restart, crash check, and teardown. Real
  Meet the Press `.edl` tests passed Media3 RIGHT at 985.220 seconds and legacy
  Exo LEFT within 22 ms of that marker endpoint.
- Added versioned/checksummed named MiniClient profiles on a configurable SMB
  configuration share. Saves use a temporary file plus atomic rename,
  credentials are rejected from the profile, and importing a stored Client ID
  requires a separate duplicate-session warning and confirmation. Physical
  commissioning on the `.175` SMB2/SMB3 share passed create, atomic overwrite,
  list/load/delete, checksum/schema/partial/credential rejection, missing-share
  failure, concurrent-write exclusion, credential redaction, UI selection, and
  the two-step warned Client-ID replacement path while retaining `DEV001`.
- Added monotonic seek-correlation evidence for the physical source read, first
  observed queued decoder input (100 ms observation bound), exact rendered-first-
  frame callback, and sustained output recovery. The same generated MPEG-2/AC-3
  fixture passed large-jump Pull/SMB A/B tests on legacy Exo and Media3. Pull
  recorded SageTV MediaServer bytes; SMB recorded zero ordinary MediaServer
  media bytes, positive SMB bytes, and only 17 bounded shadow bytes.
- Added a fail-closed Fixed/FFmpeg-MIM commissioning matrix that records the
  exact Android backend/decoder, Fixed parameters, selected server MIM backend
  and encoder, job freshness/input, caption gate, prerecorded/live results,
  and unavailable GPU vendors. Hardware use is obtained from `ffmpeg
  --mim-status`; CPU load is never accepted as proof.
- Physically commissioned Intel VAAPI Fixed playback on Amazon AFTMM/API 25.
  Legacy ExoPlayer and Media3 hardware modes passed completed playback
  controls and four exact live changes across 2.1/5.1. Media3 software and
  fallback modes passed; legacy Exo fallback passed, while legacy Exo forced
  software failed bounded prerecorded pause/play recovery and remains FAIL.
- Independently commissioned GSY Auto, Media3, and legacy-Exo delegates.
  GSY System is intentionally a safe Media3 fallback and was run last. IJK
  completed playback passes, but two runs failed on the fourth live transition
  back to 2.1, so MIM remains disabled by default.
- Corrected IJK's Push datasource contract to block for data instead of
  returning a temporary zero-length read. This is retained as a correctness
  fix but did not conceal or resolve the independent fourth-channel failure.
- Made debug telemetry unwrap the active GSY delegate so caption and playback
  health checks observe the actual Media3/legacy-Exo engine.
- Verified server-side failed-hardware fallback by deliberately supplying a
  nonexistent render device. MIM reported software/libx264 and Media3 passed
  completed/live playback. Restored `/dev/dri/renderD128` and verified VAAPI
  selection afterward.
- Added `docs/PLAYER_SERVER_COMPATIBILITY.md` with explicit stock SageTV,
  Vibe `Sage.jar`, FFmpeg/MIM, player, transport, Android decode, server GPU,
  caption, and physical-evidence boundaries.
- Documented the implemented `OPENURL`/player/source-selector/SMB/shadow call
  path and its playback-session ownership boundary in `PLAYER_ARCHITECTURE.md`.
- Deployed the exact FFmpeg/MIM 0.4.6 Linux artifact to the isolated Unraid
  test container and reran the supported Media3 Fixed/hardware gate. Exact-path
  prerecorded playback, captions, seek/jump/pause/restart/stop, and four live
  2.1/5.1 transitions passed. Fail-closed server telemetry proved fresh
  `vaapi`/`h264_vaapi` jobs, hardware encode, matching input, stopped state,
  and an empty active-job list after both tests.

## v0.5.81

- Removed the independent Android closed-caption switch and made the SageTV
  STV's caption state authoritative. Core forwards `VIDEO_CC_STATE` after
  every media load; Android safely maps STV Off to no caption track and STV On
  to the available broadcast caption track, including a pending selection
  applied after asynchronous track discovery. The default physical caption
  gate no longer invokes the Android debug selector, preventing false passes.
  Both Media3 and legacy ExoPlayer hardware Push/Dynamic rendered visible
  CEA-608/708 Meet the Press captions on Amazon AFTMM/API 25 solely from the
  STV's saved `last_cc_state=1`.
- Rebuilt the complete Dev APK and both AAB variants in the unified container.
  The host gate passes 232 project tests, 41 MCP tests, Core JUnit, validation,
  and all 60 clean APK tasks. Current hashes are Dev APK
  `39ae41d48f71af3efab1254aa960fe566af008aad51f68359ac7bca32ec85550`,
  debug AAB
  `6c29d43089fa7a442c2ebb57b49c10e142e7aa899da20ef0cc5ba0d7c99b950b`,
  release-candidate AAB
  `2b8570577ba0c09c10df76eb29315a0616b157fb268cc7a4802499dd35500cf1`,
  and debug APKS
  `ed0c8a99efc897ff5e1236dcc33c8d136c601521f2eafc4ab706165d9017044a`.

## Unreleased — OpenSageTV Vibe migration

- Added a reproducible Android App Bundle workflow using the unified image's
  checksum-pinned bundletool 1.18.3. It builds and validates debug and
  development-signed release-candidate AABs, creates a signed universal debug
  APK set, and refuses installation unless the bundle package is exactly
  `org.opensagetv.miniclient.dev.debug`. The real AFTMM/API 25 bundletool
  installation passed and preserved both configured servers. The release
  candidate remains deliberately non-publishable pending production identity
  and signing approval.
- Added explicit D-pad/IME focus order to the Add Server dialog so Android TV
  users can move from Server Name to Server Address to Add without a touch
  screen. Added a structural regression test for the remote-navigation chain;
  physically verified the complete focus path on Amazon AFTMM/API 25. Settings,
  preference-dialog cancel, HOME, relaunch, and preserved server configuration
  also pass without an Android crash or ANR.

- Physically verified all three client-ID launch paths on Amazon AFTMM/API 25:
  the scripted default activates/persists DEV001, an explicit `--client-id`
  activates the requested value after the guarded restart, and a direct
  Leanback launcher start retains that normal persisted identity. Restored
  DEV001 after the test.
- Audited the current APK against the August 2026 Google Play TV requirements.
  Target SDK 36, four ABI directories, and 16 KB APK ZIP alignment pass. The
  app is not publication-ready: the workflow has no AAB artifact; native ABI
  contents are not paired; ARM64 and x86_64 `libffmpegJNI.so` plus x86_64
  `libgdx.so` have 4 KB ELF `LOAD` alignment; and production identity, signing,
  listing, Data Safety, and Play Console review configuration remain open.
- Restored broadcast closed captions for Push/Dynamic and Pull MPEG-TS on both Media3 and
  legacy ExoPlayer. Media3 now overrides incomplete PMT caption descriptors
  with explicit CEA-608/708 formats; ExoPlayer 2.18 uses a narrowly wrapped
  default extractor factory that replaces only the TS extractor with the same
  declarations. Fixed null-safe subtitle-track descriptions, normalized
  SageTV's `8192` disabled-track sentinel at the MCP boundary, and added
  debug-only track/cue/overlay telemetry plus an exact-path physical caption
  gate. Caption views now attach to the Activity content root above SageTV's
  GL surface; the GL surface uses media-overlay ordering instead of globally
  obscuring Android views. The gate requires a currently active cue on an
  attached overlay and captures a screenshot before disabling captions.
  Media3 and legacy ExoPlayer each passed both hardware Push/Dynamic and Pull:
  CEA-608/708 discovery, selection, visibly rendered Meet the Press captions,
  disable, and crash checks on Amazon AFTMM/API 25.
- The current physically tested Dev APK SHA-256 is
  `39ae41d48f71af3efab1254aa960fe566af008aad51f68359ac7bca32ec85550`.
  The current host gate passes 232 project tests, 41 MCP tests, Core JUnit,
  validation, and all 60 clean APK build tasks.
- Replaced the process-global no-op legacy audio-focus helper with a
  lifecycle-owned `AudioFocusController`. API 26+ uses `AudioFocusRequest`
  and media `AudioAttributes`, API 25 retains the supported stream fallback,
  transient/duck loss pauses and resumes only previously playing media,
  permanent loss does not auto-resume, and generation checks reject stale
  callbacks after abandonment. Added debug-only competing-focus MCP controls
  and a deterministic physical gate. Media3 and legacy ExoPlayer hardware
  Pull both passed transient, duck, existing-user-pause, permanent-loss,
  advancing-A/V recovery, teardown, and crash checks on Amazon AFTMM/API 25.
  GSY System was intentionally excluded from this focused gate and remains
  last in any future full player matrix.
- The audio-focus revision's clean tested Dev APK SHA-256 was
  `7719f75c1e012b0de5de161a8c662439a625c6545ad04df91f08b5a647c0c7f2`;
  the full gate passes 219 project tests, 40 MCP tests, Core JUnit, validation,
  and all 60 clean APK build tasks.
- Added a negotiated, backward-compatible SageTV media-state URL extension.
  Supporting Android clients now receive authoritative major/minor format
  hints, encoder information, completed/growing state, circular/timeshift
  state, and buffer size; older MiniClients continue receiving the unchanged
  URL. Physical completed recordings carried `active=0`, while genuine
  server-driven live recordings on 2.1 and 5.1 carried `active=1`.
- Added opt-in MiniClient event 231 and MCP `dev_set_live_channel` for exact
  dotted ATSC channel selection. The physical gate now refuses false passes
  using authoritative `channel=` identity while correctly accepting a healthy
  request for the already-active channel. Media3 and legacy ExoPlayer hardware
  Pull each passed 10 alternating 2.1/5.1 changes; GSY Auto, Media3, and legacy
  Exo passed four each, and GSY System passed a bounded two-change run on
  Amazon AFTMM/API 25.
- Added `dev mcp-live-test`, constrained by default to known-good 2.1 and 5.1.
  OpenDCT correlation measured successful embedded-FFmpeg stream detection at
  roughly 0.4–1.8 seconds. Direct Pull does not use the optional MIM addon,
  which remains disabled pending its own physical promotion matrix.

- Repeated the final live gate after commissioning the exact exported server
  image on Unraid. Media3 and legacy ExoPlayer each passed initial A/V plus
  exact 5.1/2.1 transitions. GSY System was deliberately tested last and
  bounded to two transitions; it produced advancing A/V and exited without
  crashing the Amazon AFTMM/API 25 device. The tested Dev APK SHA-256 is
  `32c76981c51da7e6997c276fb84972a425cbef2aeeaa9d6049e8e7fa1394e543`.
- Strengthened `mcp-lifecycle-test` to verify the actual playback surface
  lifecycle instead of inferring it from process state. On the commissioned
  Fire TV, both Media3 and legacy ExoPlayer proved a valid/visible initial
  surface, a hidden or released surface after HOME, a recreated valid/visible
  surface after foreground return, advancing recovered A/V, clean replay, and
  final no-process teardown.

- Fixed completed MPEG-TS end-of-file handling in Pull mode. Pull reads are
  bounded at the known SageTV file size, potentially active files briefly
  recheck `SIZE` so growth can continue, and Media3 promotes its otherwise
  permanent tail-buffering state to SageTV EOS only after a stable boundary is
  confirmed. Legacy ExoPlayer retains its native ended-state behavior.
- Added a shared tested seek-bound policy and retained SageTV load hints in
  backend-owned media contexts without changing the frozen
  `BaseMediaPlayerImpl` baseline. Exact-end seeks are clamped to a playable
  tail position, and negotiated Core metadata now replaces the old extension-
  based completed/growing guess when the server supports it.
- Added `dev mcp-eof-test` with exact server-path playback, crash checks, and
  optional controlled seek/read diagnostics. Physical exact EOF now passes on
  Amazon AFTMM/API 25 for Media3 and legacy ExoPlayer against the real
  `/var/media/tv/MeetthePress-65149351-0.ts` recording. FFprobe confirmed the
  2,111,735,004-byte recording and its final video timestamps are healthy;
  the failing path was isolated from FFmpeg/MIM before the client fix.
- Expanded the normal host gate to include Core Gradle JUnit. The current 214
  scaffold/static tests, 38 MCP tests, Core JUnit suite, and clean 60-task APK
  build pass. The physically tested debug APK SHA-256 is
  `32c76981c51da7e6997c276fb84972a425cbef2aeeaa9d6049e8e7fa1394e543`.
- Fixed a real MiniClient media-protocol deadlock found by physical lifecycle
  testing. `MEDIACMD_DVD_STREAMS` now always returns its required four-byte
  reply, including unavailable-player and track-selection failure paths,
  instead of leaving SageTV blocked until its 30-second socket timeout.
- Moved Media3 and legacy ExoPlayer audio/subtitle track selection onto the
  Android main/player thread. This removes Media3's wrong-thread exception
  during repeated playback startup without changing the default backend.
- Hardened connection teardown against late reconnect workers: connection and
  socket state is safely published, reconnect sockets cannot be installed
  after shutdown, timers are cancelled, and an obsolete connection cannot
  clear a newer active session. Corrected `ConnectionLost` so it preserves the
  supplied reconnecting state.
- Added and passed a physical completed-playback lifecycle gate on Amazon
  AFTMM/API 25. Initial A/V, HOME/background, foreground reconnect, three
  repeated exact-path starts, and final force-stop/no-process teardown pass
  against `192.168.10.232` in all four Media3/legacy-ExoPlayer and Push/Pull
  combinations. Server logs contain no new 30-second media-command timeout
  after the fix. The tested debug APK SHA-256 is
  `346126781eb851a0931f5fb6a119e20c3190410ebec21de0b7b353bc250c4245`.
- Expanded the host gate to 210 scaffold/static tests and 38 MCP tests,
  including executable guards for reconnect ownership, mandatory DVD-stream
  replies, and player-thread track selection.
- Added debug-only exact-path playback through MiniClient event 230. MCP can
  request `/var/media/tv/MeetthePress-65149351-0.ts` without Search/STV focus
  navigation while the server-side feature remains opt-in.
- Fixed the MCP playback verifier's omitted stream-expectation helper, which
  previously returned a tool exception after playback had already started.
  The commissioned Fire TV now reports PASS for advancing MPEG-2 video and
  AC-3 audio on a valid 1920x1080 full-screen surface.
- Verified exact-path startup against the final Unraid image at
  `192.168.10.232`, including automatic `MediaPlayer OSD` entry and separate
  fast-forward/rewind recovery passes.
- Characterized the existing connection/protocol/lifecycle contract before
  splitting the remaining oversized classes. The new architecture note and
  seven executable guards preserve Media-before-GFX startup, two-stage GFX
  framing/dispatch, serialized event writes, reconnect eligibility,
  close/unblock ordering, renderer ownership, and activity pause/close order.
  This is host-side evidence only; matching physical ordering is still open.
- Clean-installed the current debug APK on the guarded Amazon AFTMM target
  after detecting that the previously installed Dev APK predated the
  microphone-permission removal. The installed target-SDK-36 package now has
  no `RECORD_AUDIO` request, discovery displays `192.168.10.175` and
  `192.168.10.232`, and the 66-tool MCP smoke test passes. Deterministic
  exact-path playback now connects directly to the intended test server.
- Confirmed from the sibling container project that the commissioned Android
  test target is Unraid container `sagetv-vibe-server-u26-gpu-j11` at
  `192.168.10.232`. Corrected all five active MCP playback/matrix defaults from
  the obsolete `.175` address and made cross-project fact lookup a contributor
  requirement before asking the user.
- Completed the behavior-equivalent debug control decomposition.
  `DevTestReceiver` is now a small dispatcher; debug-only owners separately
  handle session/UI commands, synchronous player commands, asynchronous
  seek/Comskip health checks, execution, parsing, configuration, tuning,
  client identity, snapshots, and response envelopes. Contract source tests
  and Android debug compilation preserve the existing ADB/MCP wire format.
- Replaced anonymous server-discovery threads with a named single-thread
  executor whose pending request and socket are cancelled on replacement,
  close, and `MiniClient.shutdown()`. Replaced the TV background-artwork Timer
  with one explicit main-looper callback removed on replacement/destruction.
- Replaced test-only `mockito-all` 1.10.19 and JUnit 4.12 with Mockito Core
  4.11.0 and JUnit 4.13.2. Core tests now pass on the unified image's JDK 17
  without opening JDK internals.
- Hardened the MCP playback-start gate: server recording tests require video
  and audio output by default, require a valid video surface, reject player
  errors, recognize SageTV's `AskToDeleteRecording`/EOF state, and return a
  structured missing-output timeout before a matrix operation can run.
- Captured the resolved debug runtime dependency graph at
  `artifacts/reports/gradle-runtime-dependencies.txt`. The unused Cast surface
  is retained because a trial exclusion changed 28 aligned AndroidX artifacts;
  removal remains physically gated rather than silently changing the runtime.
- Added `docs/DEPENDENCY_AUDIT.md` and queried all 342 locked Maven
  coordinate/version pairs through OSV. Fourteen advisory-bearing Protobuf/
  Netty coordinates were limited to non-runtime configurations; license notice
  gaps remain explicitly open rather than being reported as complete.
- Migrated every legacy settings `PreferenceFragment` to AndroidX
  `PreferenceFragmentCompat`, converted their host activities to AppCompat,
  and migrated the codec dialog to the AndroidX fragment manager without
  changing preference resources, keys, or defaults. Added a regression scan
  that rejects platform `android.preference` APIs in the settings package.
- Centralized the last direct fullscreen flag calls from ConnectingActivity in
  `AppUtil`, bound its delayed UI work to the main looper, and cancel all
  pending callbacks on destroy. A source guard now prevents fullscreen calls
  from being scattered again.
- Removed the unused microphone permission and feature declaration after an
  active-source audit found no recording API or microphone runtime path.
- Added deterministic debug/release APK inspection for package identity,
  permissions, debug/MCP implementation leakage, private material, and signer
  identity. The locally built release candidate passes the content boundary
  and deliberately fails the production gate because only a development
  signing key is available.
- Fixed `dev inspect-apk` so component-relative APK paths resolve beneath the
  mounted Android workspace instead of the unified container's `/workspace`
  parent.
- Passed 207 scaffold/static tests, 38 MCP tests, the full validator, and a
  clean 60-task debug build. Debug APK inspection passes at SHA-256
  `5fb803855d0440e0a6efe8e793f6ee24876376d9fefd46238649636e3827b067`.
  The 88-task release build passes all content checks at SHA-256
  `afe6ec0ce4ae9ebfc3c427484cf6c9b2c0c867c176a292ab61f895b7d961f0c7`;
  strict release inspection correctly blocks its development certificate.

- Completed the active Android scheduling/lifecycle inventory across the
  `source/dev` Java tree. `docs/ANDROID_FRAMEWORK_MODERNIZATION.md` now records
  every raw Thread, Timer, sleep, Handler, and delayed-callback owner together
  with affinity, current cancellation, lifecycle risk, and a behavior-safe
  migration boundary. A unittest scan fails when a new owner is not listed.
- Fixed the Windows `dev.cmd adb` path so dash-prefixed raw ADB arguments such
  as `-s`, `-p`, and `-c` reach ADB unchanged instead of being parsed as
  abbreviated PowerShell wrapper parameters.
- Converted the API-36 static checks from pytest-style functions to the
  repository's actual unittest runner. Those three checks are no longer
  silently skipped. The expanded gate passes 180 scaffold/static and 35 MCP
  tests plus full source validation in the reused unified development
  container.
- Began physical Phase 1 commissioning on Amazon AFTMM/API 25. Guarded install
  affected only `org.opensagetv.miniclient.dev.debug`; discovery displayed
  both available SageTV servers, and the APK pulled from the device exactly
  matched SHA-256
  `60e1d19ab15968ef48e24691cfd14f8998ce0bc6e6e8bda960f6d65e8d8aa668`.
  Server selection and the remaining physical baseline are still pending and
  are not reported as PASS.

- Aligned Android with the cross-project takeover contract: added the common
  workflow document and `dev all` gate, standardized handoff ZIP creation, and
  retained the hardened Android update runner and compatible package ID.
- Completed dependency reproducibility for the unified Android workflow: the
  Gradle distribution has a pinned SHA-256, all project configurations use
  dependency lockfiles, downloaded Gradle artifacts use checksum verification
  metadata, and direct Python/MCP requirements are exact pins backed by the
  unified image's full transitive lock.
- Updated the unified Android invocation to export the mounted
  `opensagetv-vibe-build-env` root. The component-root and build-environment-
  root workflows now run the same 174 scaffold/static and 35 MCP tests without
  relying on a checkout-specific working directory.
- Verified `android-all` in the existing `opensagetv-vibe-dev` container using
  image `sha256:922da6854807a919ddf4467542e46903bda00d6f787fe6727a8c237c0216d39d`;
  validation and all 60 clean Gradle tasks passed and produced APK SHA-256
  `60e1d19ab15968ef48e24691cfd14f8998ce0bc6e6e8bda960f6d65e8d8aa668`.
- Integrated normal Android build/test/validation/MCP work with the sibling
  Ubuntu 26 unified build environment and its one `opensagetv-vibe-dev`
  container. The retired component Dockerfile and Compose path are not used.
- Kept Java 11 as the unified image default while selecting JDK 17 only for the
  active Android build and JDK 8 only for the frozen v0.5.75 comparison build.
- Reproduced the Phase 1 APK byte-for-byte in the unified image after all 151
  scaffold/static tests, 35 MCP tests, project-manifest validation, and the
  full source validator passed.

- Copied the complete known-good v0.5.75 project into the independent
  `opensagetv-vibe-android-client` repository and preserved the exact 1,201-file
  snapshot as the first Git commit.
- Proved path and SHA-256 equivalence, reran all 180 Python tests, passed the
  project validator, and completed a clean Docker APK build before restructuring.
- Added migration provenance, baseline evidence, and repository-layout
  documentation without creating a dependency on the old workspace.
- Renamed repository build identities to the `opensagetv-vibe-*` namespace.
- Identified the Android image accurately as Jammy/JDK 17; Ubuntu 26 remains
  the separate server/unified-build baseline and is not falsely claimed here.
- Pinned both Temurin base images by digest and pinned the directly installed
  Python/MCP tool versions used by the validated image.
- Changed normal Docker commands to reuse one named development container and
  one named Gradle cache instead of a disposable container per command.
- Moved debug-key preparation into the in-container build path so every build
  caller gets the same safe, non-production signing setup.
- Renamed generated APK artifacts to the OpenSageTV Vibe component identity;
  Android package IDs and playback behavior remain unchanged in this phase.
- Expanded ignore and line-ending rules while explicitly retaining required
  checked-in native and packaged dependencies.
- Added the root Apache 2.0 license already used by the active Android source.
- Verified Phase 1 with 150 scaffold/static tests, 35 MCP tests, the full
  validator, and a clean 60-task build. The renamed APK is byte-identical to
  the pre-refactor baseline artifact.


## v0.5.80

- Consolidated project documentation: release history now lives only in this
  changelog, `TASKS.md` is the only active backlog, and stable operational
  guidance lives in README/HANDOFF/AGENTS and `docs/`.
- Replaced per-version `UPDATE_v*.txt` metadata with one stable
  `release.properties` file containing the current `VERSION` and
  `REQUIRES_BUILD` values. Incremental updates no longer create version-named
  Markdown or text files.
- Added repository-owned `dev.ps1` and `update.ps1` Windows entry points. They
  pass the sibling unified-build path explicitly into WSL, preventing Docker
  Desktop's temporary bind-mount path from hiding `opensagetv-vibe-build-env`.
- Made `update.sh` the sole update implementation and removed both misspelled
  legacy workflow filenames.
- Fixed full-manifest generation so tracked files intentionally deleted in the
  working tree are omitted instead of causing a `FileNotFoundError`.
- Passed the consolidated Windows/unified workflow: 172 scaffold/static tests,
  35 MCP tests, the full validator, and a clean 60-task
  API 36 APK build. The APK SHA-256 is
  `c5528ce23aaab636e62949b2e981f6487cc33417a6400be2465544f019f086a1`.
- Hardened API 36 log sharing against a detached settings fragment and attached
  the FileProvider URI as `ClipData` so temporary read permission propagates
  reliably to the selected share target.
- Added stable `release-deletions.lst` handling so incremental updates can
  reproduce intentional cleanup. Preflight rejects symlink entries, unsafe or
  manifest-conflicting removal paths, directories, control-file removal, and
  parent-symlink escapes before deleting only explicit files.
- Made task synchronization part of the completion workflow: a proven task is
  removed immediately from component `TASKS.md`, synchronized with parent
  `task.md` when mirrored, and recorded in CHANGELOG/HANDOFF with evidence.
- Removed redundant/misleading build, compile, connect, install, and workspace
  wrapper scripts. `dev.sh`/`dev.cmd` and `update.sh`/`update.cmd` are now the
  complete host interfaces; the unit runner syntax-checks the canonical shell
  entry points directly.
- Removed the component-owned Jammy Dockerfile and Compose project. All root
  commands now require `opensagetv-vibe-build-env`, reuse
  `opensagetv-vibe-dev`, and fail clearly when the sibling workflow is absent
  instead of silently creating an old standalone image/container.
- Consolidated the obsolete standalone Phase 1 validation document into the
  migration baseline evidence and removed its retired Docker identities from
  active documentation.
- Reviewed the complete uncommitted v0.5.76-v0.5.79 API 36, FileProvider,
  deterministic-launch, resumable-update, test, documentation, and launcher-
  branding changes without modifying the protected original project.
- Fixed Windows/WSL unified-environment detection: `dev.sh` now recognizes the
  sibling wrapper even when NTFS does not expose its executable bit and invokes
  it through Bash. Normal commands again reuse `opensagetv-vibe-dev` instead of
  unexpectedly building the standalone rollback image.
- Hardened update preflight before extraction. Update ZIPs now reject unsafe or
  duplicate paths, require a complete full-checkout manifest, verify every
  packaged payload hash, and verify every untouched baseline file hash.
- Made update-runner tests callable from native Windows through WSL and added
  missing-manifest/unlisted-payload rejection coverage.
- Expanded the update-runner suite to cover corrupt archives, traversal and
  duplicate entries, symlinks, release-version mismatch, payload and baseline
  hash drift, explicit removals, host-only/build-required releases, highest-
  version/newest-package selection, interruption immediately after extraction,
  and restart at the first incomplete preparation phase. The complete host
  suite now passes 173 tests plus all 35 MCP tests.
- Consolidated the obsolete chronological and AI-specific task backlogs into
  one current `TASKS.md`; durable diagnostic rules moved to
  `docs/PLAYBACK_DIAGNOSTICS.md`.
- Synchronized the active Android workflow/framework/playback/release backlog
  with the parent workspace and corrected stale targetSdk 30 documentation.
- Kept `REQUIRES_BUILD=true` because the cumulative uncommissioned release
  still contains the API 36 and launcher-resource APK changes.
- Proved `test` and `validate` through both direct Linux/WSL Bash and the
  Windows `dev.cmd` wrapper while invoked outside the repository. Every path
  reused the installed `opensagetv-vibe-build-env:u26-j11` image and running
  `opensagetv-vibe-dev` container; no component image or container was created.
- Audited all current root CMD, PowerShell, and shell entry points. PowerShell
  parsing, Bash syntax, and out-of-directory `dev.cmd help`/`update.cmd --help`
  all passed. New provenance metadata uses stable `.properties` files rather
  than creating version-specific `.txt` or Markdown files.
- Fixed checkout-aware reuse in the shared build-environment wrappers. The
  unified container now carries a normalized sibling-workspace label; a moved
  or newly cloned project recreates the same named container with correct bind
  mounts, while Windows and WSL calls for one checkout reuse one container.
  A wrong-label regression test passed and its temporary container was removed.
- Proved a separately committed clean sibling-layout checkout from an unrelated
  host directory. Its own root wrappers reused the installed unified image,
  rebound the one named container, passed 173 scaffold/static and 35 MCP tests,
  passed full validation, and completed all 60 clean Gradle tasks without
  reading `SageTV-MiniClient-Dev`. Its APK had the expected distinct debug
  signature because the ignored private debug key was intentionally not copied.
  The container was rebound to the real workspace and the temporary checkout
  was deleted afterward.

## v0.5.79

- Replaced the Android launcher artwork with the user-provided `branding/SageTV-Vibe.png` source image; no AI-redrawn logo is used in the APK.
- Regenerated the standard launcher icon at Android density sizes 48/72/96/144/192 px (`mdpi` through `xxxhdpi`) and matching round-icon resources.
- Regenerated the adaptive-icon 108dp layers at 108/162/216/324/432 px. The supplied artwork is the adaptive background and the foreground layer is transparent, preserving the original composition while allowing the launcher mask to control the outer shape.
- Changed the Leanback `MainActivity` icon override from the old TV banner to `@mipmap/ic_launcher_v2` and declared `@mipmap/ic_launcher_v2_round` at application level. The existing 16:9 `banner_v2` remains the Android TV banner/logo.
- Added static tests for launcher icon dimensions and manifest references. Android resources/manifests changed, so this update requires BUILD and INSTALL.
- Hardened the automatic update metadata parser so only exact standalone `UPDATE_VERSION=` and `REQUIRES_BUILD=true|false` assignments are treated as metadata; human-readable prose can no longer shadow a valid assignment.
- Added a regression test reproducing the original v0.5.79 package failure and documented the mandatory finished-ZIP metadata preflight in README/HANDOFF/AGENTS.

## v0.5.78

- Renamed the canonical combined workflow to `unzip_test_valitdate_build_install_lanuch.sh`; the old script name remains as a compatibility forwarder.
- Added automatic update discovery under `artifacts/downloads`: only a `changed-files-only` ZIP with a semantic version newer than the current `VERSION` is applied.
- Update ZIPs are verified before extraction and must declare `REQUIRES_BUILD=true|false` in `UPDATE_v<version>.txt`. BUILD/INSTALL run only when required.
- Added resumable per-version state under ignored `artifacts/update_runner/state.env`; successful TEST/VALIDATE/BUILD/INSTALL steps are skipped on rerun, so a completed version goes straight to deterministic DEV001 launch after the update check.
- Standardized future update-package metadata: every patch includes `VERSION`, `UPDATE_v<version>.txt`, the complete full-checkout manifest, CHANGELOG, and affected documentation.
- Established the permanent project rule that every behavior/code/script change must update the relevant documentation in the same patch.
- Host workflow/tests/docs only; no Android APK/player/Docker change, so `REQUIRES_BUILD=false`.

## v0.5.77

- Fixed scripted launches so `./dev.sh launch` now defaults to deterministic SageTV client ID `44:45:56:30:30:31` (`DEV001`), matching the existing automated MCP/player-test wrappers.
- `./dev.sh launch --client-id ID` remains available for an explicit override. Direct/manual launches from the Fire TV/Android launcher are unchanged and continue using the app's normal persisted client ID.
- Updated `test_valitdate_build_install_lanuch.sh` to pass `44:45:56:30:30:31` explicitly on its LAUNCH step, so build/install/launch validation cannot silently use a previously persisted random client ID.
- Host/test tooling only; no Android APK/player/Docker changes are required for this fix.
- Corrected the handoff/update manifest workflow: compact handoffs preserve `PROJECT_MANIFEST_FULL.sha256` as the full-checkout baseline, incremental ZIPs update only intended manifest entries, and generated root AI-handoff/changed-files ZIP artifacts are excluded from project-manifest enumeration so packaging cannot stale the checkout.

## v0.5.76

- API-36 source/build changes remain as originally delivered. The later v0.5.77 handoff workflow supersedes the temporary v0.5.76 manual-manifest workaround: normal changed-files ZIPs again include a complete updated full-checkout `PROJECT_MANIFEST.sha256`, so the user only extracts the ZIP and runs `./dev.sh test`.
- Raised `targetSdkVersion` from 30 to 36 while keeping `compileSdkVersion=36` and Build Tools 36.0.0; Docker/build-image configuration is unchanged.
- Added explicit `android:exported=true` to the phone and Leanback launcher activities for modern Android manifest requirements.
- Added the Android 16 predictive-Back compatibility opt-out so the existing SageTV `KEYCODE_BACK` remote mapping remains intact during this target-SDK migration; a native Back-callback migration remains future work.
- Added the API-36 large-screen restricted-resizability compatibility property to preserve the existing landscape TV behavior during this migration.
- Updated immersive fullscreen handling to `WindowInsetsController` on API 30+ while retaining the legacy system-UI flags below API 30.
- Removed obsolete external-storage/phone-state permissions. File logging now uses app-specific storage and FileProvider `content://` sharing.
- No playback backend, Media3/Exo/IJK/GSY tuning, client-ID, MCP contract, or Docker changes. Rebuild/install required.

## v0.5.75
- Automated MCP/player test wrappers now default to client ID `44:45:56:30:30:31` (`DEV001`) when `--client-id` is omitted; explicit `--client-id` wins.
- The test wrapper uses client-ID ensure semantics, avoiding an app restart when the requested test ID is already configured/active.

- Restored the original OpenSageTV client-ID behavior: generate a random six-character ID only when no saved `client_id` exists, persist it, and reuse it afterward.
- Restored original `MiniClientConnection` handling so the normal ID is used unless a connection-specific `ServerInfo.macAddress` override is configured.
- Removed the source-level fixed `DEV001` / `44:45:56:30:30:31` enforcement and re-enabled the Android client-ID setting. Existing installs retain their already-saved ID across this upgrade.
- Added debug receiver/MCP client-ID read/write controls plus `./dev.sh client-id --show|--set ID|--generate`. Plain text `DEV001` maps to `44:45:56:30:30:31`; setting/generating via the CLI restarts the Dev app so the next connection uses the selected ID.
- Added configured-versus-active client-ID diagnostics to `dev_player_state`.
- Added a mandatory test rule: on fresh install/cleared data, complete first-time setup manually before running **any** automated MCP/player test.
- Bumped debug control contract to `debugStatusVersion=14`; Android/core/debug code changed, so rebuild/install the Dev APK.

## v0.5.74

- Added persistent **MediaCodec Queueing Mode** settings to both Media3 and legacy ExoPlayer player settings. Choices are `Sync (Recommended)`, `Auto`, and `Async`.
- Promoted `sync` to the normal/default codec queueing mode at the user's request, based on the latest Media3 Push matrix where Sync recovered in 3.768 s versus Auto at 4.967 s and Async at 7.103 s.
- Saved player preference is used as the baseline on each new player. Explicit MCP runtime tuning still overrides the saved preference for the active tuning session; `reset` clears that override so the next player returns to the saved preference.
- Runtime tuning/matrix compiled defaults now report `codecMode=sync`; added the `auto_codec` named profile so Auto remains easy to A/B against the new default.
- Android behavior/settings changed, so one debug APK rebuild/install is required. Debug control contract remains `debugStatusVersion=13`.

## v0.5.73

- Reduced runtime tuning-matrix startup overhead with a fast replay path. Combination 1 still uses the full deterministic clean-start + Search workflow, then MCP caches the exact SageTV MediaFile ID selected by that playback.
- Combinations 2+ default to player/session isolation without force-stopping the Android app or repeating Search: disconnect SageTV session, apply tuning/config, reconnect, and invoke Sagex `Watch` on the cached MediaFile ID. This still creates a fresh player instance for every combination.
- Added MCP tools `dev_current_media_file` and `dev_play_media_file_id` for exact deterministic replay without media-library enumeration.
- Added `--startup-mode fast|isolated` (default `fast`). `isolated` preserves the old force-stop/reconnect/Search behavior for every combination.
- Fast replay automatically falls back to the full isolated startup for that combination if reconnect/replay verification fails.
- Added `--fast-ui-stable-ms` (default 500 ms) and per-combination `startupMs` / `startupPath` report fields plus `fastReplayFallbackCount`.
- Host/MCP/test only; Android `debugStatusVersion` remains 13 and no APK rebuild is required.

## v0.5.72

- Fixed `mcp-player-tuning-matrix` startup: it now calls MCP `adb_connect` before the first device-backed tool.
- Added an `adb_session_status` preflight so an unavailable device fails once before the tuning loop instead of producing one `INFRA_ERROR` per combination.
- Host/test only; Android `debugStatusVersion` remains 13 and no APK rebuild is required.

# v0.5.71 - Combined Push/Pull runtime tuning matrix

- `mcp-player-tuning-matrix --streaming` now accepts comma-separated modes such as `push,pull` or `pull,push`.
- A combined run executes each streaming mode with fresh playback per tuning combination and records the streaming mode on every result/ranking row.
- Push automatically ignores Pull-only tuning dimensions: TS timestamp search, Pull read size, Pull load-control thresholds, Pull seek-recovery settings, and Pull directional seek policy/threshold.
- From the runtime tuning surface, Push varies only the effective MediaCodec queueing mode (`auto`, `async`, `sync`), avoiding duplicate tests for settings that the Push path never reads.
- Equivalent named profiles are deduplicated automatically for Push.
- Reports now include `streamingModes`, `combinationCountByStreaming`, and `ignoredTuningDimensionsByStreaming`.
- The example `TS 4,8 x seek closest,next x read 256,512 x codec auto,async,sync` with `--streaming push,pull` produces 24 Pull + 3 Push = 27 meaningful tests.
- Host/test/docs only. The installed v0.5.70 tuning-capable APK remains valid at `debugStatusVersion=13`; no APK rebuild is required for v0.5.71.

# v0.5.70 - Dev runtime player tuning + MCP tuning matrix

- Added `PlayerRuntimeTuning`, an in-memory override layer whose defaults exactly preserve v0.5.69 behavior and reset on process restart.
- Media3 and legacy Exo2 can now read runtime TS-search, Pull read-size, load-control, seek-policy, seek-recovery, directional-threshold, and codec queueing settings.
- Added debug receiver operations `tuning` / `tuning_get`; `dev_player_state` reports all tuning values.
- Added MCP tools `dev_set_player_tuning` / `dev_player_tuning`.
- Added `./dev.sh mcp-player-tune` for direct runtime adjustment and `mcp-player-tuning-matrix` for fresh-playback Cartesian/profile sweeps with A/V recovery ranking.
- Tuning matrix supports `--slow-recovery-ms` / `--slow_recover_ms`.
- Added named profiles: `default`, `next_sync`, `directional`, `large_read`, `async_codec`, `sync_codec`, `no_reprepare`, `fast_buffer`.
- Bumped debug contract to `debugStatusVersion=13`. One APK rebuild is required to install the hooks; tuning iterations after that do not require recompilation.

# v0.5.69 - Backend-specific recovery corrections + configurable slow threshold

- Media3 Pull returns to `CLOSEST_SYNC` for seek policy. The v0.5.67 direction-aware policy did not improve Media3's long MPEG-2 Pull Comskip path.
- Disabled Media3's blind 10-second Pull reprepare fallback. The 20260828_015224 matrix proved it could discard useful decode/extractor progress and stretch ~35 second recovery to ~44 seconds. Legacy Exo2 keeps its one-shot fallback because its affected forward Comskip recovered before the 10-second trigger.
- Kept legacy Exo2 directional Pull seeking (`NEXT_SYNC` forward / `PREVIOUS_SYNC` backward) because the isolated forward Comskip improved from a ~50.6 second watchdog to ~8.5 seconds.
- Increased only Exo2 Pull network block size from 256 KiB to 512 KiB. New Exo2 telemetry showed synchronous SageTV READ wait was a large fraction of the remaining recovery; Media3 remains 256 KiB.
- GSY `system` no longer terminates the recording through the known-broken Android `MediaDataSource` bridge. The legacy preference remains accepted but now falls back to the SageTV-aware Media3 delegate; the settings label makes the fallback explicit.
- Added matrix CLI aliases `--slow-recovery-ms`, `--slow_recover_ms`, and `--slow-recover-ms`. The selected value is still written to `slowRecoveryMs` in the report.
- Bumped debug contract to `debugStatusVersion=12`; rebuild/install the Dev APK before evaluating v0.5.69 Android changes.

# v0.5.68 - Focused retest exclusions

- Added `mcp-player-matrix --exclude-players` so issue-only reruns can omit entire unchanged player families.
- Added `--exclude-gsy-engines` so individual GSY engines such as `system` can be skipped while retaining GSY Auto/Media3/legacy Exo coverage.
- Added `--exclude-case-id` for exact case-level exclusions.
- Exclusion metadata is written under `retestExclusions` in every matrix report, including the exact generated case IDs removed from the run.
- `--list-cases` now reports the number of excluded cases.
- Codex guidance now says unchanged abnormal backends should be excluded from focused reruns unless they are intentionally being used as a comparison/control.
- Host/test/docs only. Android playback code and `debugStatusVersion=11` are unchanged from v0.5.67; no APK rebuild is required solely for v0.5.68.

# v0.5.67 - Direction-aware Pull seeks + one-shot recovery

- Restored Media3 Pull `PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER` to 8x. The v0.5.66 4x experiment did not materially improve the repeatable ~35 second Media3 Pull Comskip recovery.
- Added direction-aware Pull seek policy to Media3 and legacy Exo2. Seeks >=2 seconds forward use `NEXT_SYNC`; seeks >=2 seconds backward use `PREVIOUS_SYNC`; smaller seeks retain `CLOSEST_SYNC` so frame-step/small adjustments do not jump a full GOP.
- Added a one-shot 10 second Pull seek recovery watchdog. If the same seek generation remains `STATE_BUFFERING`, the player reprepares the existing MediaSource at the target once and preserves `playWhenReady`. Newer seeks, READY/first-frame recovery, errors, and release cancel the pending recovery.
- Added exact traps: `pull_seek_policy_*`, `pull_seek_recovery_armed`, `pull_seek_reprepare_before/after/error_*`.
- Hardened Media3/Exo2 Pull DataSource reads: non-zero reads now fail deterministically instead of returning an invalid zero when the backing source is closed or unexpectedly returns zero bytes.
- Added cumulative READ/open diagnostics to `Exo2PullDataSource`, matching Media3, so server/socket time can be separated from Exo2 decoder/extractor time.
- Bumped debug status contract to 11 and matrix APK requirement to v0.5.67.

# v0.5.66 - Isolated issue reruns + Media3 Pull 4x TS seek experiment

- `--issues-only` now gives every selected operation its own full clean playback session instead of sharing a case session and restarting only after a failure.
- Added per-operation `checkStartups` and `issueCheckIsolation=fresh_playback_per_operation` report metadata.
- A playback failure while preparing an isolated operation is now `STARTUP_PLAYBACK_FAILED` / `STARTUP_PLAYER_CRASHED`, not a misleading MCP `INFRA_ERROR`.
- Report-derived issue selection understands per-check startup failures and the `PER_CHECK_ISOLATED` case startup marker.
- Changed only Media3 Pull `PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER` from 8x to 4x as the first controlled experiment for the repeatable ~33-34 second MPEG-TS Comskip recovery.
- Constant-bitrate seeking, `CLOSEST_SYNC`, datasource read sizing, LoadControl, and all Push behavior remain unchanged so the 4x result is attributable.
- Added `MATRIX_REVIEW_20260828_002208.md` documenting the latest issues-only evidence and the deliberate non-changes.
- Android source changed; rebuild/install the Dev APK before evaluating the Media3 Pull experiment. Debug contract remains `debugStatusVersion=10`.

# v0.5.65 - Issue-only matrix reruns + v0.5.64 Android review

- Added `mcp-player-matrix --issues-only` to rerun only the current known abnormal hardware Push/Pull cases and operations.
- Added `mcp-player-matrix --issues-only <previous-matrix.json>` to derive the exact startup failures and non-`RECOVERED` operations from a prior report, so focused retesting automatically shrinks as fixes land.
- Issue-only execution supports startup-only cases (currently GSY/System Push and Pull) and per-case operation lists instead of applying one global check list to every selected backend.
- The current known profile from `20260827_232446_player_full_matrix.json` contains 9 configurations, 16 media operations, and 2 startup-only checks.
- Added `MATRIX_REVIEW_20260827_232446.md` with evidence-ranked Android recommendations. No speculative Android playback behavior was changed in v0.5.65.
- Host/test/docs only; debug APK `debugStatusVersion=10` from v0.5.64 remains current and no APK rebuild is required solely for v0.5.65.

# v0.5.64 - Long-wait crash probes + evidence-driven error diagnostics

- Added 5/15/30-second crash/process probes to operation recovery waits and startup playback verification.
- New Dev-app crash signatures, process death, or PID restart stop the wait early and are classified as `PLAYER_CRASHED` / `STARTUP_PLAYER_CRASHED`.
- Milestone records preserve compact player state, A/V counters, Pull I/O counters, event-trap state, and crash/process evidence for Codex.
- Dev-specific crash-signature fingerprints prevent unrelated Android crash-buffer changes from false-triggering a player crash.
- Reclassified player/backend startup failures such as GSY/System as media startup failures instead of MCP infrastructure failures.
- Enriched Android player-error traps with Media3/Exo error-code names and IJK/System `what`/`extra` values; setup/surface errors are also identified.
- Exo/Media3 Push flush/reprepare exceptions are logged and trapped instead of being silently swallowed.
- Bumped debug snapshot contract to `debugStatusVersion=10`; rebuild/install is required.
- Added `MATRIX_REVIEW_20260827_221623.md` documenting the four Media3-family Push local-seek watchdogs, the repeatable ~33-second Media3 Pull Comskip behavior, and GSY/System startup evidence.

# v0.5.63 - Exact-event MCP traps + timeline diagnostics

- Added debug-only exact-event playback traps for Codex/MCP. Traps capture the same video-render, audio-render, AudioTrack-head, decoder lifecycle, player-state, and surface evidence used by `PlaybackHealthProbe` at the moment important Android events occur.
- Added event traps around backend seek invocation/return, seek completion/discontinuity, player prepared/READY/state changes, is-playing changes, first rendered video frame, pause/play, push flush/reprepare, completion, and player errors. Existing Exo/Media3 listener objects are reused; no continuous AnalyticsListener or background telemetry loop is installed.
- Added non-blocking SageTV protocol traps for server seek, server flush, server play/pause, and post-flush server anchor establishment. The protocol-event timestamp is recorded immediately and the counter snapshot is captured on the Android UI thread with `snapshotLagMs` reported.
- Added MCP `dev_player_events` and `dev_clear_player_events`. The matrix clears the 32-event ring before each media action and attaches the resulting exact-event history to that check's JSON result.
- Debug snapshot contract is now `debugStatusVersion=9` and exposes `serverRequestedSeekMs`, server seek/flush/anchor sequence + monotonic timestamps/ages, server seek wall time, player-local position, SageTV-reported timeline, and event-trap summary.
- A/V output recovery remains the only seek/skip/pause/Comskip PASS/FAIL verdict. Seek landing is diagnostic-only with a default +/-10 second sanity tolerance (`NORMAL`, `WARN`, `SUSPICIOUS`) and never fails recovery.
- Hardened host A/V-counter recovery so a counter reset to zero cannot false-PASS. A reset only counts after the replacement counter has advanced above zero. Video/audio expectations are latched from the pre-action state so transient decoder/format disappearance cannot create a vacuous PASS.
- Raised the matrix's default `SLOW_RECOVERY` threshold from 2 seconds to 5 seconds so normal ~3 second host/MCP polling overhead is not mislabeled as slow; the threshold remains configurable.
- Android debug APK rebuild/install is required for status version 9 and exact-event traps.

# v0.5.62 - Host-polled A/V recovery + live single-line watchdog status

- Reworked the full player matrix so every post-startup media action uses one common host-side A/V recovery loop over the persistent ADB/MCP session.
- Absolute seek, forward/backward relative seek, pause/resume, and Comskip RIGHT/LEFT now PASS as soon as the expected video/audio output counters advance again.
- Seek/comskip landing position, wrapper READY/isPlaying/seekPending/flushed state, surface validity, buffering/loading, and player errors are diagnostic-only and cannot block PASS while real A/V counters are moving.
- Removed long blocking matrix calls to `dev_run_relative_seek_check`/`dev_run_comskip_check`; actions are issued quickly and `dev_player_state` is polled sequentially.
- Watchdog progress now rewrites one terminal line dynamically instead of appending repeated WAIT lines. The live line reports `waitingFor`, video/audio advancement, playback state, surface validity, and current error.
- Restored detailed per-step CLI output after completion: A/V counter before/after/delta values, wrapper state (diagnostic-only), decoder names/kinds, and Pull I/O deltas when available.
- Added Pull data-source counters to compact MCP player snapshots so host-side recovery diagnostics retain READ/open/wait/error metrics.
- Host/MCP-only; debug APK status version 8 remains sufficient and no APK rebuild is required.

# v0.5.60 - Hardware-only selector + host-side Comskip recovery

- Added `--hardware-only` as a convenience alias for `--decoders hardware`; hardware-only full-matrix coverage is 21 cases.
- Existing `--decoders` remains available for explicit decoder selection.
- Reworked player-matrix Comskip checks to send a short native SageTV `right`/`left` command and then use host-side A/V-counter recovery.
- Comskip no longer holds one Android `goAsync()` broadcast open for the full media watchdog, avoiding the ~60-second broadcast/transport failure seen while video was already playing.
- Comskip landing position remains diagnostic-only; recovery verdict is video/audio counters advancing.
- Because Comskip infrastructure errors should no longer be created by the long broadcast path, the same configuration session can continue through RIGHT and LEFT without an unnecessary restart when playback remains healthy.
- Host/MCP-only; no APK rebuild is required.

# v0.5.59 - A/V-counter recovery verdict

- Simplified direct absolute-seek verification: the matrix no longer cares where a seek lands.
- A seek/recovery passes when real video and audio output counters resume advancing.
- Player position, target tolerance, wrapper seek/readiness flags, and SageTV timeline are diagnostic-only for this verdict.
- Restored detailed CLI health output after every media step (`videoAdvancing`, `audioAdvancing`, `stillPlaying`, `outputHealthy`, recovery time).
- Keeps the full per-step CLI sequence visible: absolute seek, forward/backward seek, pause/resume, Comskip right/left.

# v0.5.58 - Seek verifier ignores stale wrapper flags

- Fixed another false long wait in `dev_seek_time`: Legacy Exo can leave `BaseMediaPlayerImpl.seekPending=true` after a no-op/same-position seek because Exo may not emit a seek-discontinuity callback.
- Direct seek PASS/FAIL now uses the backend-local player position plus the backend's own READY/error state. `playerReady`, `seekPending`, and `flushed` remain diagnostic only and no longer block a valid seek result.
- Added counters for stale wrapper-state samples so reports expose `stale_seek_pending_samples`, `stale_flushed_samples`, and `stale_player_ready_false_samples`.
- Host/MCP-only; debug APK status version 6 remains valid and no APK rebuild is required.

# v0.5.57 - Local player-position seek verification

- Fixed direct absolute-seek verification to compare against the backend-local player position used by `MiniPlayerPlugin.seek()` instead of SageTV's server-anchored display timeline.
- Fixes false 180-second waits in Dynamic mode where video was already playing but `serverAnchorMs + playerPositionMs` could never approach a target such as 0 ms.
- Fixed direct relative-seek debug commands to calculate targets from local player position instead of the anchored SageTV timeline.
- SageTV timeline and server anchor remain in diagnostics, but are no longer used as the coordinate system for direct player seeks.
- Android debug source changed; rebuild/reinstall is required.
- Android debug status is now version 6; direct seek tools reject older debug APKs to prevent mixed host/APK coordinate behavior.

# v0.5.55 - Fire TV HOME wake + direct app launch


## v0.5.56 - Debug/MCP IME suppression

- Added debug-only Android IME suppression for MCP native MiniClient text entry.
- MCP enables suppression before opening SageTV Search, so `hasTextInput` remains available without showing the Fire TV keyboard.
- Native SageTV text entry no longer needs Android keyboard focus.
- Keyboard dismissal is conditional: the IME is hidden directly only when it is actually visible; no BACK key is injected.
- Suppression is restored after the Search/start sequence so manual app behavior is unchanged.
- Debug status version 5 reports `imeSuppressedForDebug`.

- Fire TV wake now sends `KEYCODE_WAKEUP` followed by `KEYCODE_HOME`, so Dreaming/screensaver state is exited before automation proceeds.
- `./dev.sh launch` no longer relies on `monkey` as the primary launcher. It resolves the Dev package MAIN/LAUNCHER activity and starts it with `am start -W`.
- `monkey` remains only as a last-resort fallback.
- Fixes Fire TV builds where `monkey` exits 251 with `SYS_KEYS has no physical keys`.
- Host/MCP-only; no APK rebuild required.

# v0.5.51 - Restore one-shot Search text for matrix reliability

- Restored normal recording-name entry to the proven one-shot Android `adb input text` path.
- `--text-char-delay-ms` now defaults to `0`; positive values explicitly opt into experimental per-character diagnostics.
- Avoids visible Fire TV keyboard navigation/highlight behavior caused by stretched character-by-character injection.
- Applies consistently to playback, Media3, Comskip, and full player-matrix startup.

# v0.5.47 - true per-step watchdog + startup DOWN

- Fixed the 3-minute media watchdog so it is actually applied independently to each media action.
- Android debug recovery checks now advertise/support `MAX_RECOVERY_WATCHDOG_MS=300000` and snapshots expose `debugStatusVersion=3` plus `maxRecoveryWatchdogMs`.
- MCP refuses to trust a long watchdog when the installed APK advertises a smaller limit, preventing false `180000 ms` labels from an older APK.
- ADB debug broadcasts now derive their shell timeout from each action's recovery + verification window instead of using the old fixed 90-second transport timeout.
- Comskip and direct relative-seek results report requested/applied watchdog values and step elapsed time.
- Complete player harness applies a fresh `--watchdog-ms` to every media check; absolute seek now inherits the watchdog unless `--seek-timeout-s` is explicitly overridden.
- Recording-start navigation now sends `DOWN` after the first `play_pause`, before the second `play_pause`, as requested.
- Android debug source changed; rebuild/reinstall is required.

# v0.5.43 - explicit Search/IME startup diagnostics

- Removed `dev_send_sequence` from the normal Media3 playback startup path.
- Added `dev_open_search`, which sends the native SageTV Search command and waits for the SageTV text-input state plus verified Android IME visibility.
- Added `dev_wait_for_ime` and preserved IME fields in compact MCP status.
- Standard Search startup now calls Android OS text input, direct IME hide, verified IME hidden, then direct SageTV UI commands as separate MCP operations.
- Updated both Media3 seek/resume and Comskip matrices to use the shared explicit Search startup.
- Added phase-specific errors so Search-command failures are distinguishable from text-input and keyboard failures.
- Host/MCP-only; no Android source changed in this release.

# v0.5.42 - keyboard text packaging and dead-path cleanup

- Fixed the v0.5.41 overwrite package omission that left `scripts/mcp_playback_test.py` on the older `directtext` sequence in existing projects.
- Standard recording startup now explicitly uses `keyboardtext`, which routes through Android OS `input text` only after `waittextinput` and verified `waitimevisible`.
- Removed the obsolete debug-receiver `input_text` implementation that posted raw character values as SageTV keycodes.
- Removed the v0.5.40 in-app `KeyCharacterMap` / `dispatchKeyEvent()` debug text dispatcher because that path can block the debug BroadcastReceiver.
- `directtext` remains a host/MCP compatibility alias for `keyboardtext`; `sendtext` and `sendkey` remain diagnostic input primitives.
- Android source changed to remove the dead debug path, so rebuild/reinstall is required for a source-consistent v0.5.42 APK.

# v0.5.41 - Android OS keyboard input synchronization

- Fixed the v0.5.40 Search hang where debug-receiver-side `View.dispatchKeyEvent()` could block indefinitely after `automationReady=true`.
- `dev_input_text_keyboard` / `keyboardtext` now use Android's OS `input text` service after the automation has positively verified the Fire TV IME is visible.
- Preserved `sendtext`, `sendkey`, and `mcp-send-sequence` diagnostic primitives. `directtext` remains a compatibility alias.
- No Android source change in this release; no APK rebuild is required solely for v0.5.41.

# v0.5.40 - Android virtual-keyboard text injection

- Fixed v0.5.39 debug text entry: raw `MiniClientConnection.postKeyEvent((int) ch, ...)` used character values as keycodes, so lowercase letters could be interpreted as unrelated/numeric remote keys.
- Debug `input_text` now requires the SageTV text field and visible IME, generates Android `KeyEvent`s using `KeyCharacterMap.VIRTUAL_KEYBOARD`, and dispatches them to the currently focused MiniClient view.
- This routes text through the existing `MiniClientKeyListener` / `KeyMapProcessor`, matching real Android keyboard handling and preserving the correct Java/Sage keycode + Unicode character conversion.
- Added MCP `dev_input_text_keyboard(text)` and `keyboardtext <text>` sequence action. `dev_input_text_direct` / `directtext` remain compatibility aliases but now use the Android virtual-keyboard path.
- Legacy `sendtext` and `sendkey` remain available for diagnostics.
- Standard playback/Search automation now uses `keyboardtext`.
- Android shared/debug source changed; rebuild/reinstall is required.

# v0.5.39 - direct Android/MiniClient debug controls

- Added debug-only direct text input through `MiniClientConnection.postKeyEvent(...)`; normal automation no longer requires ADB `input text`.
- Added direct IME hide through the resumed MiniClient Activity, avoiding BACK-key injection.
- Added direct player `play`, `pause`, `stop`, absolute seek, relative seek, and explicit forward/back skip MCP controls.
- Added instrumented direct relative-seek checks and converted the Media3 Push/Pull seek matrix away from SageTV FF/REW commands.
- Converted Media3 pause/resume measurement to direct `MiniPlayerPlugin.pause()` / `play()`.
- Added dedicated Android debug Comskip and Comskip-check operations. They post SageTV RIGHT/LEFT internally via `EventRouter` because commercial-marker targets are server/STV-owned; no Android key or configurable arrow mapping is used.
- Standard Search/startup sequence now uses direct MiniClient text + direct IME hide + direct SageTV commands where possible. Legacy `sendkey` / `sendtext` sequence actions remain supported.
- Added `directtext` and `hideime` actions to `mcp-send-sequence`.
- Updated tests to cover the new direct-control broadcasts and sequence parser behavior.
- Android source changed; rebuild/reinstall is required.

# v0.5.38 - generic debug seek-to-time command

- Added debug-only Android `seek_time` control with a required caller-supplied `target_ms`; `0` means beginning and any non-negative millisecond target is supported.
- Added MCP `dev_seek_time(target_ms, tolerance_ms, timeout_s)`, which commands the Android player then verifies the SageTV-visible media timeline reaches the passed target.
- Kept `dev_local_seek_absolute` / `seek_absolute` as compatibility aliases for older backend-isolation tests.
- Added `./dev.sh mcp-seek-time --target-ms N` for direct copy/paste testing.
- Media3 Comskip matrix now performs the generic debug time seek before Comskip checks; `--start-ms` controls the passed target and defaults to `0` for deterministic beginning-of-recording tests.
- No production behavior changed; the new seek control remains debug-build-only.

# v0.5.37 - direct-connect automation startup and robust app status

- Fixed a Fire TV false-negative where `pidof org.opensagetv.miniclient.dev.debug` returned no PID even though Android reported a resumed Dev `ServersActivity`; `dev_app_status` now uses `pidof`, `ps -A`, and resumed-Activity evidence and reports `runningSource`.
- Fresh playback/session/Media3 matrix automation no longer calls generic `launch_dev_app` after clean-stop. That launcher enters `ServersActivity`; automation now goes directly: clean stop -> wake -> debug config -> `dev_connect_server` -> verify app/UI -> readiness gate.
- `launch_dev_app` remains available as a manual MCP control only.
- Added regressions for resumed-Activity and `ps` process detection, plus assertions that automated playback/session/matrix scripts do not reintroduce generic launcher startup.
- Host/MCP-only update; no Android source changed from v0.5.36, so no APK rebuild is required if the v0.5.36 debug APK is already installed.

# v0.5.36 - verify Android IME before and after Search text entry

- Android debug status now separates the MiniClient keyboard request from actual on-screen IME visibility: `imeRequested`, `imeVisibleKnown`, and `imeVisible`. A known `imeVisible=false` explicitly verifies that the keypad is not displayed.
- `mcp-send-sequence` adds `waitimevisible` and `waitimehidden` in addition to `waittextinput`.
- Native recording startup now waits for the Search text field **and** visible Fire TV/Android keyboard before `sendtext`, then waits for the keyboard to be verified hidden after BACK.
- `debugStatusVersion` is now 2.

# v0.5.35 - Android debug receiver compile repair

- Fixed the v0.5.34 Android debug build failure in `DevTestReceiver.java`. The packaged v0.5.34 receiver referenced `configuredValues(PrefStore)` and `appendPlaybackHealth(...)` without definitions, causing `:android-tv:compileDebugJavaWithJavac` to fail.
- Restored the original `configuredValues(PrefStore)` helper and directly appends `PlaybackHealthProbe.capture(player).compactWire("health_")` in the debug snapshot path.
- Added static regression assertions that the configuration helper and playback-health snapshot implementation are present and that the dangling `appendPlaybackHealth(...)` call cannot return.
- v0.5.34 deterministic startup/readiness behavior and v0.5.33 Pull seek I/O diagnostics are otherwise unchanged.
- Android debug source changed; rebuild/reinstall is required.

# v0.5.34 - deterministic Android/MCP automation startup

- Fixed the startup race exposed when a prior failed run left SageTV on `Search - All Media Types` with Android text input active. A merely stable/non-empty menu is no longer considered automation-ready.
- Debug APK snapshots now publish `debugStatusVersion=1`, `uiState`, and `automationReady`. `automationReady=true` requires a connected `Main Menu`, no popup, no active text input, and no active player.
- Added MCP `firetv_wake`, `dev_app_status`, `kill_dev_app`, and `dev_prepare_clean_start` tools.
- Clean start checks process state first, requests a normal session/task exit, re-checks, uses force-stop only if necessary, verifies the process is gone, wakes the device, then allows relaunch.
- Playback/session/Media3 matrix startup now verifies the relaunched process and trusts the Android debug readiness signal. If the fixed client ID reconnects into stale server UI, automation sends direct SageTV `home` and waits for `automationReady=true` before Search.
- The observed `MiniClientOpenGLActivity` launch is not treated as failure; the chosen UI activity follows the app's `use_opengl_ui` preference.
- Android debug source changed; rebuild/reinstall is required. v0.5.33 Pull seek I/O diagnostics are preserved.

# v0.5.33 - Pull seek I/O diagnostics

- Restored Media3 Pull's 256 KiB buffer after the v0.5.32 1 MiB experiment failed to improve the slow large-seek case.
- Added diagnostic-only counters for Pull socket opens and SageTV READ traffic: request count, requested/received bytes, READ wait time, max request size, errors, and positions.
- Exposed those counters through debug playback-health snapshots and the Media3 Comskip matrix report.
- The next device run should identify whether slow Pull Comskip recovery is dominated by network READ time or by extractor/decoder work after the reads complete.

## 0.5.32 — 2026-08-27

- Fire TV v0.5.31 proved direct SageTV Comskip commands are correct: Dynamic recovered RIGHT in 820 ms and LEFT in 566 ms; Pull recovered both directions but took about 3.63 s.
- The Pull slowdown is specific to large Comskip-style seeks; the v0.5.28 normal Pull FF/REW matrix stayed below 2 seconds. Hardware MPEG-2/AC-3 decoders, Surface, and A/V output remained healthy.
- Increased only `Media3PullDataSource.PULL_READ_BUFFER_BYTES` from 256 KiB to 1 MiB to reduce synchronous SageTV `READ` round trips during the existing MPEG-TS timestamp/PCR scan after large seeks.
- Preserved the 8x TS timestamp search window, constant-bitrate seek support, `CLOSEST_SYNC`, Pull LoadControl values, Dynamic/PUSH behavior, Legacy Exo Pull buffer size, IJK, decoder policy, and Comskip command path.
- This is an Android player/DataSource experiment and requires rebuilding/reinstalling the debug APK before device retest.

## 0.5.31 — 2026-08-27

- Corrected Comskip automation after device testing proved `mcp-send-sequence` + `command right` / `command left` is the working SageTV Comskip path.
- `android_comskip_check` no longer resolves `videoplaying_right` / `videoplaying_left`; those are normal short-press FF/REW mappings.
- Instrumented Comskip now sends the native SageTV `right` / `left` command directly through the existing `skip_check` output-health path, preserving decoder/surface/A/V recovery and landing-timeline diagnostics.
- Updated standalone `mcp-comskip-test`, Media3 Push/Pull Comskip matrix output, tests, help, README, tasks, and handoff documentation to distinguish direct Comskip commands from Android arrow mappings/long-press emulation.
- Host/MCP-only correction; no Android APK/player source changed and no APK rebuild is required.

## 0.5.30 — 2026-08-27

- Removed the hard-coded/default native Search recording title from playback automation.
- `mcp-playback-test`, `mcp-media3-matrix`, `mcp-media3-comskip-matrix`, and `mcp-search-test` now require `--text`; omitting it is an argparse error before any device/session action.
- MCP `dev_search_text` now requires its `text` argument as well, eliminating the hidden server-side fallback.
- Updated current docs/help/tests so future test-recording changes are passed explicitly instead of requiring code edits.
- No Android player implementation changes; no APK rebuild is required solely for v0.5.30.

# v0.5.29

## Media3 Push/Dynamic vs Pull Comskip matrix

- Recorded the v0.5.28 Fire TV result as a Phase-A pass: Media3 startup, single FF, single REW, and rapid mixed seek recovered real video/audio output in both Push/Dynamic and Pull. Pull's slowest seek was single REW at 1585 ms, below the 2000 ms slow-recovery threshold.
- Pause/resume measured about 5.3-5.5 seconds in both Push and Pull. Because the latency is common to both streaming modes, do not tune Pull buffering/DataSource based on that result; keep it as a separate common-path measurement item.
- Added `./dev.sh mcp-media3-comskip-matrix` to run fresh Media3 Push/Dynamic and Pull sessions, start the known recording through the proven native Search sequence, then exercise the configured video right/left arrow mappings.
- Comskip PASS/FAIL is based on real decoded video/audio recovery. The MiniClient does not receive semantic server/STV Comskip marker timestamps, so landing timeline/jump size is diagnostic rather than a guessed marker-target assertion.
- Recovery over 2000 ms captures diagnostics without automatically failing healthy output. A failed direction captures a checkpoint and stops additional arrow stress in that mode while allowing the other streaming mode to run.
- Host/MCP-only update; no Android APK/player/DataSource source changed.

# v0.5.28

## Media3 Push/Dynamic vs Pull seek-resume matrix

- Added `./dev.sh mcp-media3-matrix` to run the next Phase A device comparison without changing Media3/player code first.
- Each mode runs as a fresh MiniClient session: Media3 + Hardware -> direct server connect (`save=false`) -> wait for the v0.5.27 stable UI gate -> start `meet the press` through the exact native Search/key sequence.
- The matrix runs identical single FF, single REW, rapid `FF,FF,REW,FF,REW`, and pause -> play recovery checks in Push/Dynamic and Pull.
- Seek PASS/FAIL uses the existing real video/audio output-health probe. SageTV timeline movement remains diagnostic only.
- Recovery time is recorded side by side in a JSON report under `artifacts/firetv`. Recovery over 2000 ms is a warning and automatically captures a checkpoint so slow Pull resume can be diagnosed without mislabeling healthy playback as a seek failure.
- A failed basic FF/REW check stops further stress in that mode, captures diagnostics, then continues with the other streaming mode so one failure does not destroy the comparison.
- Host/MCP-only update: no Android APK/player source changed. The currently proven v0.5.26 debug APK can be used with the v0.5.27/v0.5.28 host tooling.

# v0.5.27

## Wait for a stable SageTV UI before native Search playback test

- Fixed an MCP startup race found on Fire TV: `connected=true` can be reported while the SageTV MiniClient UI is still loading, so v0.5.26 could send the native `Search` command too early.
- `dev_wait_for_ui` now optionally supports `menu_present` plus `stable_ms`. This preserves the old immediate behavior unless those options are explicitly requested.
- `mcp-playback-test` now requires a non-empty SageTV menu hint to remain unchanged for 2000 ms after connection before sending the exact Search/text/key sequence.
- `mcp-session-test` uses the same stable-UI readiness gate before starting video by name.
- `--ui-stable-ms` can override the 2000 ms readiness interval. The native playback input sequence itself is unchanged.
- MCP/host-only change: no Android player/APK source changed, so an APK rebuild is not required solely for v0.5.27 when v0.5.26 debug control code is already installed.

# v0.5.26

## Native end-to-end playback start test + v0.5.25 rollback revert

- Reverted the temporary v0.5.25 playback-bisect rollback after device evidence showed the Fire TV itself had entered a bad state: even the older MiniClient app failed until the Fire TV was rebooted. The v0.5.24 Android debug/session-control state is restored.
- Added `./dev.sh mcp-playback-test` for a complete native-client startup test: launch Dev app -> set player/streaming/decoder settings -> connect directly to a specified SageTV server -> run the exact native Search/key sequence -> verify real playback starts.
- Default server is `192.168.10.175`; connection uses `save=false`, so the test does not add a third saved server entry beside `192.168.10.175` and `192.168.10.232`.
- Default playback settings are Media3 + Push/Dynamic + Hardware. All are command-line selectable.
- Playback is started only through the native MiniClient input path. The test does **not** call `dev_play_video`, Sagex, HTTP, or server-side `Watch`.
- Default test recording sequence is exactly: Search -> 50 ms -> type `meet the press` -> 50 ms -> BACK -> 100 ms -> FAST_FORWARD -> RIGHT -> PLAY_PAUSE -> PLAY_PAUSE. No additional keys are inserted.
- After the sequence, MCP waits for `dev_wait_for_playback_started` and requires the existing real A/V playback-health check to pass.

# v0.5.24

## Explicit MCP input sequences

- Added first-class MCP tool `dev_send_sequence(sequence)` with four explicit line actions:
  - `command <sage-command>`
  - `sendkey <android-key>`
  - `sendtext <text>`
  - `delay <milliseconds>`
- Added host command `./dev.sh mcp-send-sequence` that reads a multiline sequence from stdin (or `--file` / `--sequence`), initializes MCP, connects ADB, then sends the whole sequence in one MCP tool call.
- Sequence execution adds no hidden keys, text, or delays. Blank lines and `#` comments are ignored.
- No Android APK/player code changed; no APK rebuild is required.

# v0.5.23

- Fixed native Search automation when the Android keyboard remained open after text injection.
- After typing, `dev_search_text` now dismisses the visible Android IME with `KEYCODE_BACK` by default. While the IME is open this closes the keyboard instead of navigating Back in SageTV.
- The default sequence is now: Search -> type `meet the press` -> dismiss keyboard -> Down -> Right -> Right -> Right -> Play/Pause -> Play/Pause.
- Removed the extra initial Play/Pause that had been incorrectly used as the keyboard-close action.
- MCP/host-only change; no APK rebuild required.

# Changelog

## v0.5.22

- Fixed the native MCP Search test to emulate the **physical Fire TV Play/Pause button** after text entry.
- The Search workflow now sends Android `KEYCODE_MEDIA_PLAY_PAUSE` (`play_pause`) instead of `KEYCODE_MEDIA_PLAY` (`play`).
- Default sequence: Search -> type `meet the press` -> Play/Pause -> Down -> Right -> Right -> Right -> Play/Pause -> Play/Pause.
- Generic MCP `PLAY` remains mapped to `KEYCODE_MEDIA_PLAY`; only this remote-emulation workflow uses `PLAY_PAUSE`.
- MCP/host-only change; no Android APK rebuild is required.

## v0.5.21 - Search submit uses Play, not Enter

- Changed the native MCP Search test default so Android keyboard Enter/Next is **not** sent after typing.
- Default sequence is now: Search -> type `meet the press` -> Play -> Down -> Right -> Right -> Right -> Play -> Play.
- `dev_search_text` now defaults `submit=false`; explicit `submit=true` remains available for diagnostics.
- `mcp-search-test --next` can explicitly add Enter/Next when needed, but the default path does not use it.

## v0.5.20 - Native Search text entry through MCP

- Added `dev_type_text` to inject text through Android's focused keyboard/input path.
- Added `dev_search_text`: native SageTV Search -> wait for `hasTextInput` -> type query -> optional Android keyboard Next/Enter.
- `./dev.sh mcp-search-test` now defaults to typing `meet the press` and pressing keyboard Next/Enter.
- Added `--text` and `--no-next` options to the Search test helper.
- No Android/player source changes; no APK rebuild is required from v0.5.19.

# v0.5.18 — MCP end-to-end client session + play-by-name

## 0.5.19

- Added first-class MCP tool `dev_search`, which sends the native SageTV `search` SageCommand to the currently connected MiniClient.
- Added `./dev.sh mcp-search-test`; it initializes MCP, calls `adb_connect`, then calls `dev_search` in the same Docker/MCP session so short-lived container ADB state cannot cause `no devices/emulators found`.
- MCP-only update: no Android/player code changed and no APK rebuild is required from v0.5.18.


- Added debug-only MiniClient session controls to launch the app, apply player/streaming/decoding/GSY settings before playback, connect to a saved/direct/last SageTV server, and cleanly exit back to the server screen or force-stop the Dev app.
- Added `dev_play_video(video_name)` as the preferred MCP playback-start tool. The caller supplies only a recording/video name. MCP reads the connected server and Dev MiniClient client ID from Android, auto-discovers the Sagex Remote API, resolves this MiniClient's SageTV UI context, finds a unique MediaFile by title, invokes SageTV `Watch` in that UI context, and verifies real A/V output starts.
- Exact case-insensitive media-title matches are preferred; a unique substring match is accepted. Ambiguous names return candidate MediaFile IDs/titles instead of starting an arbitrary recording.
- After A/V starts, MCP queries the current MediaFile in the same UI context and verifies its MediaFile ID matches the requested item when the server returns that information.
- Android debug snapshots now expose the fixed Dev `clientId` and normalized `uiContextHint`; no production runtime telemetry/listeners were added.
- `mcp-session-test` now uses `--video-name` instead of STV-specific Recordings-menu navigation. The end-to-end order is settings -> connect -> play-by-name -> verify output -> optional seek/comskip -> exit.
- Sagex endpoint discovery defaults to the connected server on ports 8080, 8081 and 80. Custom installations can set `SAGETV_SAGEX_BASE`; optional Basic Auth uses `SAGETV_SAGEX_USER` / `SAGETV_SAGEX_PASSWORD`.

# v0.5.17 — MCP Pull stability / AC-3 rewind probe correction

- Fixed a false `audio_not_advancing` failure after backward Pull seeks when Android reuses the same encoded/passthrough `AudioTrack` session but resets its playback-head counter to a smaller value. The health probe now recognizes a live same-session playback-head reset as renewed audio output, and reports reset detection explicitly.
- Long-running `skip_check` / `comskip_check` ADB broadcasts no longer use `--receiver-foreground`. Snapshot/config commands remain foreground. This avoids Android's tighter foreground-broadcast execution window from timing out slow Pull recovery checks and destabilizing Media3/GSY test sessions.
- `mcp-seek-test` now stops before the rapid 16-command stress and pause/resume checks if a basic FF/REW health check fails. The failing playback state and checkpoint are preserved instead of compounding the failure.
- Added MCP output fields `audioHeadResetDuringRecovery` and `audioHeadResetDuringVerify` so encoded-audio seek resets are visible in diagnostics.
- No Exo2/Media3/IJK/GSY playback, seek, buffering, decoder-selection, or transport implementation changed in v0.5.17. This release corrects the debug/test harness based on device evidence before changing player code.

# v0.5.16 — MCP Comskip arrow landing + A/V recovery validation

- Added debug-only `comskip_check`, which resolves the Android `videoplaying_right` / `videoplaying_left` mappings and sends the same SageTV command that the physical video arrows use. Default mappings remain FF/REW unless the user changed them.
- Added A/V recovery timeline checkpoints: first recovered video output, first recovered audio output, full output recovery, and the final recovery landing timeline.
- Added `dev_run_comskip_check` and `./dev.sh mcp-comskip-test` for dedicated Comskip testing. Output health remains authoritative; a known marker target may optionally be supplied for landing validation.
- The MiniClient does not receive semantic Comskip marker start/end metadata from the SageTV STV/plugin, so the debug API explicitly reports `comskipMarkerDataAvailable=false` rather than guessing a marker target.
- Existing decoder/output diagnostics remain attached to Comskip checks: decoder identity/type, renderer counters, AudioTrack advancement, Surface health, buffering/loading, datasource/file position, errors, and sustained playback.
- No Exo2/Media3/IJK/GSY playback, decoder, buffering, or seek implementation changed in v0.5.16.

# v0.5.15 — MCP real playback-output health checks

- Changed MCP seek/forward PASS/FAIL from timeline accuracy to **actual playback output health**. SageTV timeline values remain diagnostic only because they can advance while video is black or audio is stalled.
- Added debug-only `PlaybackHealthProbe`, invoked on demand only during MCP requests. It installs no permanent player/analytics listeners.
- Exo2/Media3/GSY-Media3 checks now inspect the live video renderer `DecoderCounters`, actual decoder name/type, Android `AudioTrack` playback-head advancement, Surface validity, player READY/playing state, datasource, buffering/loading state, retry/error state, and buffer/file position.
- After each FF/REW sequence, MCP requires video output and audio output to recover within the timeout **and continue advancing** through a verification window.
- Added explicit health failure reasons such as `video_not_rendering`, `audio_not_advancing`, `player_not_ready_playing`, `surface_not_valid`, and post-recovery stalls.
- Skip results include before/post/final decoder/output snapshots, decoder init/release deltas, datasource changes, buffer/file-read information, output counters, recovery time, and player errors.
- Diagnostic checkpoints now include normal logcat, crash buffer, MediaCodec/extractor/SurfaceFlinger dumps, audio-system dumps, focused window, screenshot, and compact player/decoder state.
- `./dev.sh mcp-seek-test` no longer performs timeline calibration by default. It runs single FF, single REW, and rapid mixed FF/REW output-health stress immediately. `--calibrate-timeline` retains the old semantic timing diagnostics when needed.
- No production/release player behavior, decoder policy, buffering, streaming transport, or IJK runtime is changed. The debug APK must be rebuilt because debug Android code changed.

# v0.5.14 — Android-side MCP skip measurement

- Moved MCP seek/skip measurement into the Android debug APK. `dev_run_seek_check` now invokes one debug `skip_check` operation instead of measuring separate host-side snapshots.
- The debug receiver records the same value used by `MediaCmd.MEDIACMD_GETMEDIATIME` / SageTV's displayed playback timeline immediately before and after the command sequence.
- Skip results now return `timelineBeforeMs`, `timelineAfterMs`, `timelineDeltaMs`, `elapsedMs`, and `playbackAdjustedDeltaMs` directly from the Android process.
- One-shot player snapshots now also expose `sageTimelineMs` and `timelineSource=MEDIACMD_GETMEDIATIME` explicitly. `mediaTimeMs` is retained for compatibility.
- Calibration uses the same Android-side skip measurement and still pauses playback, takes multiple samples, rejects wrong-direction/zero outliers, and uses a robust median/5-second preference quantum.
- The visible Streaming Mode label remains **Push/Dynamic** while the persisted value stays `dynamic`.
- No Exo2, Media3, IJK, GSY playback, decoder, buffering, or transport implementation was changed in v0.5.14. The Android debug APK must be rebuilt because the debug receiver changed.

# v0.5.13 — MCP skip calibration robustness

- Fixed `mcp-seek-test` aborting when noisy player landing measurements calibrated a nominal 10-second SageTV skip as +9/-11 seconds.
- Calibration now rounds to a 5-second preference quantum by default so small player landing error remains visible instead of being learned into the expected result.
- Added `--calibration-quantum-ms` for unusual custom SageTV skip intervals; `--ff-ms` / `--rew-ms` overrides remain supported.
- Non-divisible semantic steps no longer abort the suite: the harness reports both the requested semantic target and the closest practical command-plan target, then validates the player against the commands actually sent.
- The rapid `+30,+30,-10,+60,-30` semantic sequence remains correctly defined as net +80 seconds when primary skips calibrate to +/-10 seconds.
- No Android player, decoder, streaming, buffering, transport, or settings code changed in v0.5.13. No APK rebuild is required when updating from v0.5.12.

# v0.5.12 — MCP seek calibration correction

- Fixed the MCP seek suite's incorrect assumption that SageCommand `ff` always means +30 seconds; SageTV primary skip intervals are server-configurable.
- `mcp-seek-test` now measures the active server's actual `ff` and `rew` increments before running semantic +30/-10 checks.
- Corrected the rapid semantic sequence `+30,+30,-10,+60,-30` from the previously incorrect +50-second expectation to the correct **+80-second** net movement.
- Added `--ff-ms`, `--rew-ms`, `--calibrate-settle-ms`, and `--paced-delay-ms` controls.
- Normal semantic skip checks are paced separately from the rapid stress sequence.
- No ExoPlayer, Media3, IJK, GSY, decoder, transport, buffering, or seek implementation code changed in v0.5.12.

# v0.5.11 — MCP current-playback regression automation

- Added `DevTestReceiver` in the Android TV **debug source set only**. Release/main manifests do not expose the receiver.
- Added on-demand player snapshots (backend/config, MiniPlayer state, SageTV media time/server anchor, buffer left, last file read position, video dimensions) without player callbacks or continuous telemetry.
- Added MCP tools to set player/stream/decode/GSY preferences for the next playback, send exact SageTV commands, run command sequences, validate expected seek deltas, wait for a media position, perform a local backend-only seek, and capture diagnostic checkpoints.
- Added `./dev.sh mcp-seek-test` to automate primary +30, primary -10, rapid `+30,+30,-10,+60,-30`, and pause/resume against the currently playing known recording.
- Changed the visible Streaming Mode label from **Dynamic** to **Push/Dynamic** while preserving the persisted value `dynamic` and existing default behavior.
- Fixed stale main Settings summaries: Default Player, Decoding Method, and Streaming Mode now refresh when returning from sub-settings pages.
- No ExoPlayer, Media3, IJK, GSY playback implementation, decoder, buffering, or seek behavior changed in v0.5.11.

# v0.5.10 — Exo PUSH startup blocking-read fix

- Fixed ExoPlayer Legacy and Media3 PUSH/Dynamic startup that could remain black with no audio when the SageTV 4 MiB push buffer was briefly empty.
- Added an opt-in `PushBufferDataSource.readBlocking()` path that follows the Exo/Media3 DataReader contract: zero is returned only for a zero-length request; otherwise it waits for data or returns EOS.
- Only Exo2/Media3 PUSH adapters use the blocking read. Legacy IJK keeps the historical non-blocking push read path.
- Pull seek/timeline and v0.5.9 Pull latency tuning are unchanged.

# Changelog

## 0.5.9 - 2026-08-26

### Phase A Pull resume-latency optimization

- Fire TV v0.5.8 validation confirms Pull seek correctness is fixed for Legacy ExoPlayer, Media3, and GSY Auto: rapid seeks, seek-to-start, pause/start, and large comskip now land on the correct timeline.
- Remaining symptom is delayed visual playback after a correct Pull seek. The timeline remains correct while Exo buffers/scans before releasing new frames.
- Added a configurable `BufferedPullDataSource` buffer size while preserving the historical 32 KiB default used by IJK/System paths.
- Exo2 and Media3 Pull now use a 256 KiB SageTV network read buffer to reduce synchronous `READ` round trips during the existing 8x MPEG-TS timestamp/PCR scan.
- Added Pull-only Exo LoadControl tuning: 5 s min / 20 s max forward buffer, 500 ms resume-after-seek, and 1000 ms resume-after-rebuffer. Dynamic/PUSH uses the existing defaults.
- Added Pull playback-state diagnostics with current position, buffered position, and playWhenReady for any remaining post-seek stalls.
- Seek-map tuning from v0.5.8 is preserved unchanged. IJK and Dynamic/PUSH behavior are unchanged.

## 0.5.8 - 2026-08-26

### Phase A Pull seek-map follow-up

- Corrects the v0.5.7 diagnosis: `MEDIACMD_FLUSH` is only forwarded to the player when `MediaCmd.pushMode` is true, so the Pull-mode flush guard could not be the cause of the remaining Exo seek-to-zero behavior.
- Tunes only Exo-based **Pull** playback (Legacy ExoPlayer and Media3) with a larger MPEG-TS PCR/timestamp search window (`8 x TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES`).
- Enables constant-bitrate seeking for progressive extractors that support it and uses `SeekParameters.CLOSEST_SYNC` for Pull PVR-style seeks.
- Leaves Dynamic/PUSH extractor and seek behavior unchanged.
- Adds Pull seek diagnostics that log whether Exo considers the current media item seekable, plus duration and buffered position, immediately before each seek.
- GSY Auto inherits the Media3 Pull fix through its existing Media3 delegate.
- IJK is unchanged and remains the passing Pull control.

## 0.5.7 - 2026-08-26

### Phase A second device follow-up

- Fixed ExoPlayer Legacy and Media3 Pull seeks snapping back to `0:00`. SageTV can issue `FLUSH` around a seek even in Pull mode; the backend-specific `flush()` implementations were unconditionally replacing/re-preparing the MediaSource with reset-position enabled. Pull mode now ignores that destructive player reset and leaves repositioning to Exo's seek/DataSource reopen path. PUSH/Dynamic keeps the existing reset behavior.
- GSY Auto now resolves to the known-good Media3 SageTV-aware engine for both Dynamic/PUSH and Pull. Android System MediaPlayer remains available as an explicit GSY engine while its custom `MediaDataSource` compatibility is hardened.
- Hardened `SagePushMediaDataSource.readAt()` for Android System MediaPlayer: for a non-zero request it waits for SageTV PUSH data instead of returning `0`, which Android reserves for a zero-length request.
- Fixed the GSY/System Pull teardown crash by making `SagePullMediaDataSource.close()` idempotent and `SimplePullDataSource.close()` synchronized. This prevents two teardown threads from nulling `remoteServer` between the null check and `Socket.close()`, which caused the observed JNI pending-NPE/SIGABRT.
- Added regression coverage for Pull flush semantics, GSY Auto routing, System PUSH read semantics, and concurrent/idempotent Pull close behavior.
- Decoder policy, legacy IJK runtime, Dynamic/PUSH timeline rebasing, and future HTTP/HLS/RTMP/SRT transport reserves remain unchanged.

## 0.5.6 - 2026-08-26

### Phase A device-test follow-up

- Fixed GSY Auto / Android System MediaPlayer startup crash caused by `GSYSystemMediaPlayerImpl.setupPlayer()` releasing/hiding the just-created video frame a second time, then calling `MediaPlayer.setDisplay()` with a released Surface.
- GSY/System now registers a `SurfaceHolder.Callback`, waits for a valid Surface before `prepareAsync()`, detaches the display when the Surface is destroyed, and removes the callback when the player is released.
- Fixed legacy ExoPlayer and Media3 SageTV PULL `DataSource.open()` range accounting. Non-zero byte-range opens now return the readable length of the requested range instead of the total file size.
- Exo2/Media3 PULL readers now track `bytesRemaining` and honor bounded DataSpec requests. This prevents Exo from inflating the logical resource end across reopen/seek operations, which can lead to `ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE`.
- Added regression coverage for the GSY/System Surface lifecycle and both PULL DataSource contracts.
- Added `PHASE_A_FOLLOWUP_TEST.md` focused on the device failures found during v0.5.5 validation.
- No decoder policy changes and no PUSH/Dynamic timeline-rebase changes are included. IJK Dynamic large-comskip behavior and GSY Legacy Dynamic timeline mismatch remain Phase B/C targets.

## 0.5.5 - 2026-08-26

### Phase A seek/timeline cleanup

- ExoPlayer Legacy and Media3 now accept an exact `0 ms` seek.
- Removed the old ExoPlayer/Media3 UI-thread seek polling loop (`Thread.sleep(100)` up to one second). Seeks now return immediately and completion is observed asynchronously.
- `onTimelineChanged()` no longer clears `seekPending`; seek completion is tied to `DISCONTINUITY_REASON_SEEK`.
- On confirmed Exo/Media3 seek discontinuity, the cached/media-session position is updated from `newPosition.positionMs`.
- IJK repeated-Pause frame stepping is corrected from `+1000 ms` to approximately one 30 fps frame (`+33 ms`).
- Added regression coverage and `PHASE_A_SEEK_TEST.md` with player-by-player verification steps.
- Decoder policy, GSY routing, and PUSH/PULL timeline rebasing are intentionally unchanged in this phase.

## 0.5.4 - 2026-08-26

- Fixed `GSYSystemMediaPlayerImpl` compile failure on the project Android API where `android.media.MediaPlayer.seekTo()` accepts an `int`, while SageTV provides seek time as `long`.
- Added a saturating millisecond conversion (`0..Integer.MAX_VALUE`) before calling System MediaPlayer so normal seeks compile and very large values cannot overflow negative.
- No player routing, transport, RTMP reserve, legacy IJK, ExoPlayer, or Media3 behavior changed.

## 0.5.3 - 2026-08-26

- Fixed the v0.5.2 Android build failure caused by GSYVideoPlayer Exo2 pulling the unused `com.github.mcxinyu:LibRtmp-Client-for-Android:v3.2.0.m2` dependency from legacy Sonatype repositories.
- Excluded both the unused mcxinyu RTMP client and `androidx.media3:media3-datasource-rtmp` from the GSY Exo2 dependency. SageTV does not use RTMP for its PUSH/PULL or normal HTTP playback paths.
- Kept GSY 13.1.0, Media3 1.11.0, legacy ExoPlayer 2.18.1, and original IJK 0.8.8 otherwise unchanged.
- Added regression assertions so the unwanted RTMP dependency cannot silently return.

## 0.5.2 - 2026-08-26

- Restored the original SageTV IJK 0.8.8 Java/JNI runtime as a fully separate top-level player.
- Removed GSY `ex_so` from the APK and explicitly excluded GSY's modern IJK Java bridge to prevent namespace/JNI collisions.
- Rebuilt `GSYVideoPlayer` as an independent selectable-engine adapter: Auto, Media3/ExoPlayer, Android System MediaPlayer, or Legacy ExoPlayer.
- `Auto` uses Android System MediaPlayer for SageTV PUSH/PULL custom data sources and Media3 for normal HTTP URLs.
- Added native Android `MediaDataSource` PUSH/PULL bridges so GSY/System can consume SageTV streams without `ijkmediadatasource:`.
- Legacy IJK decoder fixes are now legacy-IJK-only; GSY no longer inherits from or configures `IJKMediaPlayerImpl`.
- Kept the AFTMM/mantis MPEG-2 MediaCodec safety fallback inside legacy IJK only.


## 0.5.1 - Fire TV IJK MPEG-2 MediaCodec safety fallback

- Fixed IJKPlayer/GSYVideoPlayer MPEG-2 playback on the confirmed Amazon AFTMM / `mantis` Fire TV after the GSY 13.1.0 runtime still reproduced the repeated Android `MediaCodec.dequeueInputBuffer()` failure loop.
- The shared IJK decoder mapper now disables only MPEG-2 MediaCodec on AFTMM/`mantis` and uses the bundled IJK/FFmpeg software decoder for that stream type. AVC/H.264, HEVC, MPEG-4, and other devices keep their normal Hardware/Hardware Preferred policy.
- Added a second guard in `CodecSelector` that rejects the same known-broken MPEG-2 hardware path if the native runtime requests a codec despite the per-codec MediaCodec switch.
- Removed the v0.4.9/v0.5.0 forced preference for `OMX.MTK.VIDEO.DECODER.MPEG2` on `mantis`; real hardware testing proved it is not stable for this SageTV custom-data-source path.
- Added regression tests/validation for the AFTMM/`mantis` safety fallback. Internal player telemetry remains disabled.

## 0.5.0 - Four-player decoding policy / GSYVideoPlayer backend

- Added a shared **Decoding Method** setting used by every player backend: **Hardware** (default), **Software**, and **Hardware Preferred**.
- Legacy ExoPlayer and Media3 apply the policy only to video decoder selection; audio decoder/passthrough behavior remains unchanged.
- **Hardware Preferred** enables decoder fallback for ExoPlayer/Media3. Legacy IJK uses its own 0.8.8-compatible fallback behavior.
- Replaced the active old local IJK 0.8.8 Java/native AAR set with GSYVideoPlayer **13.1.0** `gsyvideoplayer-java` + `gsyvideoplayer-ex_so`, so the APK contains only one IJK Java/JNI runtime and gains GSY's MPEG-capable extended native package.
- Existing SageTV **IJKPlayer** now uses the maintained GSY IJK runtime while retaining SageTV's custom push/pull `IMediaDataSource` and lifecycle.
- Added **GSYVideoPlayer** as backend #4 with stable preference ID `gsyplayer`; it uses a dedicated GSY-tuned IJK profile behind the existing SageTV `MiniPlayerPlugin` abstraction.
- Added a dedicated **GSYVideoPlayer Settings** screen for MediaCodec synchronization, resolution-change handling, rotation, packet buffering, frame drop, minimum frames, probe size, analyze duration, accurate seek, OpenSLES, infinite input buffering, and hardware codec blocking.
- Kept the Fire TV `mantis` MTK MPEG-2 hardware decoder preference for IJK/GSY hardware mode.
- Kept Legacy ExoPlayer as the default backend and kept internal player telemetry disabled.
- Added `test_valitdate_build_install_lanuch.sh` to stop on the first failed test/validate/build/install/launch step.

## 0.4.9 - IJK MPEG-2 MediaCodec compatibility

- Re-enabled IJK MPEG-2 hardware decoding by default.
- Added conservative MPEG-TS probing before IJK MediaCodec startup (`probesize=2 MiB`, `analyzeduration=3 s`) and avoids `fflags=nobuffer` in MPEG-2 hardware mode.
- Enabled IJK synchronized MediaCodec mode and keeps decoder-side rotation disabled.
- Fire TV Stick 4K `mantis` explicitly prefers `OMX.MTK.VIDEO.DECODER.MPEG2` when available.
- Filters known Android software codecs from IJK hardware selection unless the existing software-codec preference allows them.
- The existing IJK MPEG-2 setting remains the fallback switch: disable it to use the known-working bundled FFmpeg software decoder.
- Legacy ExoPlayer and Media3 runtime code are unchanged.

## 0.4.8 - IJK MPEG-2 Fire TV fallback

- Legacy ExoPlayer and Media3 remain unchanged after both were confirmed playing on the Fire TV.
- IJK now defaults MPEG-2 video to its bundled FFmpeg software decoder instead of the IJK 0.8.8 MediaCodec MPEG-2 path, which produced audio with a black screen and then stopped on the test Fire TV.
- Added **IJK: MPEG-2 Hardware Decoder** in IJK settings. It defaults OFF and can be enabled for A/B testing on devices where IJK MediaCodec MPEG-2 is reliable.
- H.264/AVC and HEVC MediaCodec acceleration remain enabled in IJK.
- IJK `OnErrorListener` now returns handled after the existing `playerFailed()` path to prevent an additional completion/stop cascade.
- No internal player telemetry hooks were added.

## v0.4.6 - Legacy ExoPlayer binary alignment

- Fixed the Fire TV Legacy ExoPlayer crash caused by a binary-incompatible legacy Exo module graph (`AbstractMethodError` from `H262Reader` to `TrackOutput.sampleData`).
- Kept the legacy baseline at ExoPlayer 2.18.1, but now forces every `com.google.android.exoplayer` dependency to that exact version under Gradle 8.
- Removed `@aar` artifact-only notation from published legacy Exo modules so Gradle can use normal module metadata and dependency alignment.
- Removed `exoplayer-testutils` from the production Android APK.
- Removed the unused `ijkplayer-exo` wrapper AAR; SageTV uses native `IjkMediaPlayer` directly.
- Kept the local FFmpeg extension AAR isolated as the legacy audio extension.
- Added tests/validation to prevent mixed legacy Exo module versions from returning.

# v0.4.3 - AGP 8 Non-Transitive Resource Repair

## 0.4.5 - GDX backend routing fix and external player diagnostics

- Fixed `MiniClientGDXRenderer` to use the same `PlayerBackend` / `PlayerFactory` three-backend routing as `OpenGLRenderer`.
- Important: before this fix, selecting `media3` while running `MiniClientGDXActivity` actually instantiated IJKPlayer, so the previous Media3 audio-only result was not a real Media3 test.
- Rolled back the unsuccessful v0.4.4 SurfaceView/Z-order experiments to the known-good v0.3.8 legacy Exo/IJK/GDX behavior.
- Added external-only `./dev.sh player-diag clear` and `./dev.sh player-diag LABEL` diagnostics for logcat, crash filtering, SurfaceFlinger, MediaCodec, focused window, package state, and screenshot capture.
- No internal player telemetry hooks were added.

## 0.4.4 - Fire TV video surface lifecycle repair

- Legacy ExoPlayer now binds the `SurfaceView` with `setVideoSurfaceView(...)` before `prepare()` instead of passing a one-time raw `Surface`.
- Media3 now binds its `SurfaceView` before `prepare()` so decoder initialization starts with a lifecycle-managed video output.
- IJK restores `video_surface` visibility after its internal `releasePlayer()` call and before `setDisplay(...)`.
- The GDX OSD now uses a translucent media-overlay SurfaceView instead of absolute `setZOrderOnTop(true)`, making video/OSD composition explicit on older Fire OS.
- No internal player telemetry was reintroduced.
- Added regression/validation guards for surface ordering and lifecycle attachment.


- Fixed the `android-tv` Java compile failure caused by AGP 8 non-transitive `R` classes.
- `iconbutton_background`, `ic_add_to_queue_white_60dp`, `ic_tv_white_60dp`, and `sage_logo_256` physically belong to `android-shared`, so TV/phone presenters now reference them through `sagex.miniclient.android.R.drawable` instead of the app module's `sagex.miniclient.android.tv.R`.
- Kept AGP 8 non-transitive resource behavior enabled rather than restoring legacy transitive `R` globally or duplicating resources into `android-tv`.
- Added unit and validation guards for the shared-resource module boundary.
- The native-library `stripDebugDebugSymbols` warning is unchanged and remains non-fatal.
- No player runtime implementation changes.

# v0.4.2 - Gradle 8 Android Shared Dependency Repair

- Fixed the first real AGP 8 / Gradle 8 Java compile failure in `android-shared`.
- Added an explicit `org.slf4j:slf4j-api:1.7.6` dependency to `android-shared`; the module imports SLF4J directly and can no longer rely on the Java `core` module's `implementation` dependency leaking transitively onto its compile classpath.
- Added an explicit `androidx.appcompat:appcompat:1.3.1` dependency to `android-shared`.
- Updated `AutoConnectDialog` to use the public `androidx.appcompat.R.style.Theme_AppCompat_Dialog` resource instead of the old transitive/internal `R.style.Base_Theme_AppCompat_Dialog` symbol that is not generated under the modern Android resource model.
- Added unit and validation guards for both dependency-boundary fixes.
- Legacy ExoPlayer, IJKPlayer, and `BaseMediaPlayerImpl` remain unchanged from the confirmed v0.3.8 playback baseline.

# Changelog

## 0.4.1 - Gradle root recovery guard

- Restores the v0.4.0 Dev root `source/dev/build.gradle` containing the Media3/toolchain pins.
- Adds a regression guard that fails explicitly if `source/dev/core/build.gradle` is ever copied over the Dev root Gradle file.
- No Android player/runtime Java changes from v0.4.0.

## v0.4.0 — Three-player refactor / Media3 modernization — 2026-08-26

- Added centralized `PlayerBackend` + `PlayerFactory` architecture with stable `exoplayer`, `media3`, and `ijkplayer` preference IDs.
- Added AndroidX Media3 ExoPlayer **1.11.0** as a new selectable backend with dedicated SageTV push/pull DataSources, MediaCodec selector, subtitle handling, renderer-index-safe track selection, and decoder fallback.
- Kept the v0.3.8 `BaseMediaPlayerImpl`, legacy `Exo2MediaPlayerImpl`, and `IJKMediaPlayerImpl` byte-for-byte unchanged and kept legacy ExoPlayer as the default.
- Kept standalone ExoPlayer **2.18.1** + local FFmpeg extension **2.18.0** frozen as the known-good baseline; 2.19.1/FFmpeg modernization is intentionally deferred to a separate experiment.
- Kept IJKPlayer **0.8.8-SNAPSHOT** as the legacy fallback; removed its codec selector's source dependency on ExoPlayer `Util`.
- Added Media3 settings/decoder information UI.
- Modernized the Dev build to JDK 17, AGP 8.13.2, Gradle 8.13, compileSdk 36, Build Tools 36.0.0, and minSdk 23 while retaining targetSdk 30 and NDK 21.0.6113669.
- Preserved untouched baseline reproducibility by bundling JDK 8 plus Android platform 29/Build Tools 29.0.2 in the same Docker image; `build-existing` explicitly uses JDK 8.
- Internal player telemetry remains disabled for every backend after Fire TV testing proved those hooks can block startup.
- Fixed Dev identity `DEV001`, Dynamic fresh-install default, clean Gradle build, Firebase removal, and uninstall-before-install behavior remain intact.
- Added player-backend and dual-toolchain regression tests and expanded project validation.

## v0.3.8 - Telemetry hook rollback confirmed

- Fire TV testing confirmed that enabling the Android player telemetry hooks prevents video startup even with clean uninstall/install and logcat-only transport.
- Restored BaseMediaPlayerImpl, Exo2MediaPlayerImpl, and IJKMediaPlayerImpl to the v0.3.6 playback-working runtime.
- PlayerTelemetry remains inert and is not called by the media players.
- MCP player telemetry is intentionally disabled so stale logcat/file events cannot be mistaken for current playback state.
- Keeps v0.3.6 fixed client ID DEV001, Dynamic default streaming mode, clean Gradle build, and uninstall-before-install behavior.
- Future playback diagnostics must be external to the player runtime unless introduced one hook at a time behind an explicit opt-in switch.

## v0.3.6 - 2026-08-25

- Fixed the Dev SageTV client identity to `DEV001` (`44:45:56:30:30:31`) at both resolver and wire-connection layers so clean reinstalls reuse the same SageTV client profile.
- Disabled editing of the global Dev client ID in Settings; generated/per-server IDs cannot override the connection identity.
- Dev APK installation now always performs `adb uninstall org.opensagetv.miniclient.dev.debug` before a fresh `adb install`; `install -r` is no longer used.

- Hard-rolled back all Android media-player telemetry hooks to the pre-telemetry v0.2.10 runtime baseline.
- Restored `BaseMediaPlayerImpl`, `Exo2MediaPlayerImpl`, and `IJKMediaPlayerImpl` byte-for-byte from the pre-telemetry source.
- Kept Docker/ADB/MCP infrastructure intact; player telemetry MCP tools may return no events until telemetry is reintroduced using a safer design.
- Dev APK builds now run `gradle clean` before `assembleDebug` to prevent stale telemetry classes from surviving incremental builds.
- Validation now fails if Android player telemetry hooks are present during baseline recovery.

## v0.3.5 - Validation alignment

- Fixed `scripts/validate_project.py` to validate the intentional v0.3.4 logcat-only Android telemetry rollback instead of requiring the removed app-private file transport.
- Added a guard that fails validation if Android `PlayerTelemetry` reintroduces app-private file I/O while the playback-safe rollback is active.
- No MiniClient runtime or Docker image changes.

## v0.3.3 - Telemetry source normalization

## v0.3.4 - Playback-safe telemetry rollback

- Restored `PlayerTelemetry.java` to the exact v0.3.0 logcat-only implementation that was confirmed to allow video playback on the Fire TV AFTMM.
- Restored `BaseMediaPlayerImpl` telemetry construction to the matching v0.3.0 constructor.
- ExoPlayer and IJKPlayer telemetry hooks were not changed; they are byte-for-byte identical to v0.3.0.
- Removed app-private telemetry file writing from the Android runtime. MCP retains logcat fallback support.
- Updated telemetry regression tests to lock this playback-safe transport until a non-invasive query transport is implemented.


- Fixed mixed telemetry source state exposed by the v0.3.2 update packaging.
- Re-ships the matched `BaseMediaPlayerImpl` and `PlayerTelemetry` implementation together so the constructor contract is consistent.
- Re-ships the telemetry MCP parser/client and telemetry regression tests as one coherent set.
- Ensures `BaseMediaPlayerImpl` constructs telemetry with the Android application context required for private-file transport.
- No playback, seek, buffering, decoder-selection, or player-state decision logic is changed.

## v0.3.2 - Non-blocking telemetry file transport

- Fixed a playback-start regression introduced in v0.3.1 by synchronous app-private telemetry file I/O.
- All telemetry file initialization and append operations now run on a dedicated background writer thread (`SageTVDevTelemetryWriter`).
- Player/UI/Exo/IJK callback threads now only format/log/enqueue telemetry; they never open, truncate, or write the telemetry file directly.
- Preserved the reliable `run-as` MCP file transport and logcat fallback.
- Added regression checks that require queued file writes and prohibit the old synchronous file-lock path.
- No seek, buffering, decoder, or playback-state decision logic was changed.

## v0.3.1 - Fire TV telemetry transport hardening

- Added app-private player telemetry file `sagetv_dev_player_telemetry.log`.
- MCP now reads telemetry with Android `run-as` first and falls back to logcat.
- Added `transport` and telemetry-file fields to MCP telemetry results.
- Updated `mcp-telemetry` output to expose the active transport.
- Added regression tests for raw private-file parsing and file-first MCP aggregation.
- Playback behavior remains unchanged.

## v0.3.0 - Player telemetry / MCP metrics phase

## v0.2.8 - Logger contract test harness fix

## v0.2.10 - MCP smoke-test dispatch fix

- Fixed `./dev.sh mcp-test` when using an existing Docker image whose baked-in entrypoint predates the `mcp-test` command.
- `dev.sh` now invokes the bind-mounted `/workspace/scripts/mcp_smoke_test.py` directly with Python, so no image rebuild is required.

## v0.2.9 - 2026-08-25

- Added `./dev.sh mcp-test` for a real end-to-end MCP stdio smoke test without Codex or Node.
- The smoke test performs MCP initialize, `tools/list`, `adb_connect`, device info, Dev package info, focused-window lookup, and screenshot capture.
- Clarified that `./dev.sh mcp` is a long-running stdio server and is expected to wait until an MCP client connects.


- Fixed `tests/test_logger_contract.py` to create its temporary `javac -d` output directory before invoking `javac`.
- This corrects a test-harness-only failure (`javac: directory not found: .../classes`); no MiniClient source or Docker image changes are required.

## v0.2.7 - SageTV debug signing alias fix

- Fixed Android debug signing for this repository: `android-tv/build.gradle` expects alias `client`, not the standard Android `androiddebugkey` alias.
- `scripts/ensure_debug_keystore.sh` now creates/verifies alias `client` with the Android debug passwords expected by AGP.
- Existing readable `adb/debug.keystore` files are preserved; if they contain `androiddebugkey` but not `client`, the script adds `client` in place rather than deleting the keystore.
- Added regression coverage tying the generated alias to the real Gradle signing configuration.

## v0.2.6 - Logger interface contract fix

- Fixed Firebase-free Android `Logger` to implement the real `ILogger.getLoggerInstance(Class/String)` methods.
- Removed incorrect `isTraceEnabled()` / `isDebugEnabled()` overrides that are not part of this project's `ILogger` interface.
- Added a Java compile regression test using the real `ILogger.java` plus lightweight SLF4J stubs.
- `./dev.sh test` now discovers all project tests automatically.
- `./dev.sh validate` now checks the Logger/ILogger contract shape.

# Changelog

## 0.2.3 - WSL bind-mount fix

- `dev.sh` now exports the absolute directory containing itself as `SAGETV_WINDOWS_ROOT` before invoking Docker Compose.
- This removes reliance on Compose interpreting `.` correctly across WSL/Docker Desktop.
- An explicitly **exported** `SAGETV_WINDOWS_ROOT` still overrides the automatic path.

## v0.2.2 - 2026-08-25

- Fixed Docker workspace bind default: the directory containing the project is now mounted to `/workspace` automatically.
- `SAGETV_WINDOWS_ROOT` remains available only as an optional explicit override.
- Added container-side project mount validation with a clear error when Docker is pointed at the wrong folder.
- Added regression coverage ensuring `scripts/run_unit_tests.sh` is packaged and the Compose default does not point at a hard-coded Windows path.
- Updated Windows/WSL environment examples and setup guidance.

## 0.2.1 — 2026-08-25 — Flattened full-project package

- Removed the confusing host-side `project/` nesting.
- Docker, MCP server, scripts, docs, source trees, artifacts, logs, ADB keys, and configuration now live directly under `C:\SageTV-MiniClient-Dev`.
- Docker now bind-mounts the single Windows root at `/workspace`.
- Updated Docker working directory, MCP `PYTHONPATH`, preflight logic, setup scripts, and documentation for the flattened layout.
- Added `.dockerignore` so bundled source/artifacts are not unnecessarily sent as Docker build context.


## 0.2.0 — 2026-08-25 — Exact source integration + complete Firebase removal

- Integrated the exact user-supplied `sagetv-miniclient-master.zip` into the full workspace.
- Added untouched `source/existing` and separately refactored `source/dev` trees.
- Recorded source archive SHA-256 `f45c930f3810b56d88f751120ca6fa692ae9b01dd2adce62a3d926b2ab336a79` outside the untouched baseline tree.
- Expanded the Dev refactor to remove all identified active Firebase/Crashlytics/Google Services build, runtime, settings, manifest, CI, and ignore-file wiring.
- Reworked Android `Logger` to remain SLF4J-only while preserving `ILogger` and exception stack traces.
- Added `source/dev/scripts/verify_no_firebase.sh`.
- Updated import/refactor validation to fail closed if active Firebase references remain.
- Source replacement now recreates the same complete Firebase-free Dev transform automatically.
- Updated documentation for bundled-source workflow; GitHub bootstrap is now fallback-only.
- Player behavior remains unchanged in Phase 0.

## 0.1.1 — 2026-08-25 — Docker-only Dev naming/workspace hardening

### Development naming

- Standardized the application/project runtime name on **Dev** rather than Codex.
- Development application ID is `org.opensagetv.miniclient.dev`; debug package is `org.opensagetv.miniclient.dev.debug`.
- Development source tree is `/workspace/source/dev`.
- Development APK is `SageTV-MiniClient-Dev-debug.apk`.
- Python MCP package/runtime naming is `sagetv_dev_mcp` / `sagetv-dev-firetv`.
- Codex terminology remains only where documentation or a command refers to the actual Codex client.

### Docker-only workspace

- Added Windows-backed workspace default `C:\\SageTV-MiniClient-Dev`.
- Added WSL and Windows `.env` examples for the same root folder.
- Persist ADB authorization keys under the Windows workspace.
- Keep Gradle cache in a Docker named volume for build performance.
- Android SDK/ADB, JDK, NDK, Python and MCP all run inside Docker.
- Host build helper names now redirect into Docker instead of invoking a second Android toolchain.
- Added Docker preflight and shell/ADB helpers.

### Source and baseline builds

- Added exact source ZIP importer that creates untouched `source/existing` and isolated `source/dev` copies.
- Added root `compile_existing_app.sh` to compile the untouched/current app without installing it.
- Added `build-existing` Docker command and baseline artifact metadata.

### Safety hardening

- Safe Fire TV installer now defaults to the Windows-backed Dev APK artifact and always verifies package ID before install.
- MCP default install path now resolves through the configured `/workspace/artifacts/firetv` directory.
- Existing `jvl.sage.miniclient...` namespace remains blocked from MCP package mutations.

## 0.1.0 — 2026-08-25 — Phase 0 hardware-in-loop development scaffold

### Code review

- Reviewed OpenSageTV SageTV MiniClient v1.14.0 project/module layout.
- Confirmed existing common player abstraction with ExoPlayer and IJKPlayer implementations.
- Documented ExoPlayer startup/seek flow and lack of dedicated first-rendered-frame telemetry in reviewed code.
- Documented IJK deferred/special seek timing behavior.
- Documented SageTV compatibility considerations in common media-time/seek handling.

### Development app isolation

- Added deterministic refactor script.
- Changed Android application ID base to `org.opensagetv.miniclient.dev`.
- Expected debug package becomes `org.opensagetv.miniclient.dev.debug` under the existing debug suffix.
- Changed visible development app names to `SageTV MiniClient Dev` / `SageTV MiniClient Dev Debug`.
- Preserved Java package namespace to avoid unnecessary first-pass source churn.
- Added project validation script.

### Firebase removal

- Remove Google Services and Crashlytics Gradle plugin wiring.
- Remove Firebase BoM, Analytics and Crashlytics app dependencies.
- Remove shared Crashlytics dependency.
- Remove `FirebaseCrashlytics` import and runtime initialization calls.
- Remove Crashlytics manifest collection metadata.
- Remove `android-tv/google-services.json` when present.
- Added scan/report of remaining textual Firebase/Crashlytics references for deliberate orphan-preference cleanup.

### Build tooling

- Added upstream bootstrap script pinned to `v1.14.0` by default.
- Records exact upstream Git commit in `UPSTREAM_BASELINE.txt`.
- Added Docker builder using JDK 8, Android platform 29, build tools 29.0.2 and NDK 21.0.6113669.
- Added Docker Compose project with Gradle cache.
- Added local build helper.
- Standardized output APK name under `artifacts/` with SHA-256 from Docker build.

### Fire TV MCP

- Added Python MCP SDK v2 project.
- Added ADB connect/device info tools.
- Added guarded dev-package install/launch/stop/uninstall.
- Added APK application-ID verification before MCP installation.
- Explicitly protects `jvl.sage.miniclient...` namespace from package mutation through MCP.
- Added Fire TV remote key and sequence tools.
- Added logcat clear/tail/wait tools.
- Added screenshot and short screen-record tools.
- Added MediaCodec/extractor/Surface diagnostic collection.
- Added combined playback-failure artifact collection.

### Documentation

- Added `README.md`.
- Added `CODE_REVIEW.md`.
- Added `TASKS.md` with phased acceptance criteria.
- Added `QUESTIONS.md` with non-blocking defaults.
- Added `AGENTS.md` Codex safety/testing rules.
- Added full `HANDOFF.md`.
- Added `CODEX_MCP_SETUP.md` and Codex MCP registration helper.
- Added `BASELINE_TEST_TEMPLATE.md` and scaffold test runner.

### Tests

- Added fixture test for separate application identity and Firebase active-code removal.
- Added MCP tests protecting production namespace and validating key mapping.
- Python compilation and included tests pass in the handoff environment.

### Deliberately not changed yet

- No ExoPlayer seek/startup algorithm changes.
- No IJKPlayer behavioral changes.
- No Media3 migration.
- No Android Gradle Plugin/SDK modernization.
- No claim of playback fixes until a real Fire TV baseline is captured.

## v0.2.5 - 2026-08-25

- Fixed first Android debug build failure when `/root/.android/debug.keystore` does not yet exist.
- Added `scripts/ensure_debug_keystore.sh` to create and verify the standard Android debug signing key (`androiddebugkey`).
- `./dev.sh build` and `./dev.sh build-existing` now create/reuse the persistent debug keystore before Gradle runs.
- The keystore is stored through the existing `/root/.android -> /workspace/adb` mapping, so it survives short-lived containers and image rebuilds.
- Added `./dev.sh ensure-debug-keystore` for explicit verification/generation.
- Added `keytool` and debug-keystore status to container preflight.

## 0.4.7 - Gradle layout self-repair

- Added `config/dev-root-build.gradle.canonical` as the canonical Dev root Gradle file.
- Added `scripts/repair_dev_gradle.py` to detect and repair the repeated `source/dev/build.gradle` / `android-shared/build.gradle` swap seen after update extraction.
- `./dev.sh test`, `./dev.sh validate`, and `./dev.sh build` now repair/verify the Dev root Gradle layout before running.
- A damaged root Gradle file is preserved as `source/dev/build.gradle.bad` before repair.
- Added regression coverage that intentionally overwrites the root file with the android-shared module file and proves repair succeeds.
- No Android player/runtime code changes.

### v0.5.20 - Native Search text/remote sequence
- Added MCP Android text injection and `dev_search_text`.
- `mcp-search-test` now defaults to Search -> `meet the press` -> Next/Enter -> Down -> Right -> Right -> Right -> Play -> Play.
- No APK/player changes.


## v0.5.44 - Restore proven Search text timing
- Standard recording startup now restores the proven Android OS `adb input text` path (`dev_type_text` / legacy `sendtext`) immediately after SageTV reports `hasTextInput=true`.
- IME visibility remains instrumented, but is verified after text injection instead of gating text entry.
- Startup no longer uses `dev_sage_command_sequence` before playback; post-Search UI commands are issued individually with `dev_sage_command`, because the sequence measurement helper requires an active player timeline.
- No Android APK source changes in this update.

## v0.5.45 - Stable verified debug seek
- Fixed a false PASS in `dev_seek_time`: `BaseMediaPlayerImpl.getMediaTimeMillis()` intentionally reports 0 while the player is not ready/flushing/seeking, which could be mistaken for a successful seek to 0 ms.
- `dev_seek_time` now ignores transient not-ready/seek-pending/flushed samples and requires Media3/Exo to return to READY before evaluating the landing.
- A landing must remain consistent with the requested target for a configurable stability window (default 1200 ms). If the player briefly reports the target and then snaps back, the seek FAILs.
- Seek results now report `landed_ms`, `stable_observed_ms`, `ready_samples`, `transient_samples_ignored`, `failure_reason`, and the final compact player state including Exo position and server anchor.
- `mcp-media3-comskip-matrix` adds `--seek-stable-ms`; no Android APK rebuild is required for this host/MCP verification fix.

## v0.5.46 - Three-minute observational watchdog + complete player matrix
- Comskip recovery observation now defaults to a 180000 ms (3 minute) configurable `--watchdog-ms`.
- Watchdog expiry is a non-fatal media observation (`WATCHDOG_EXPIRED`) with `cause=undetermined`; it does not blame Android, SageTV server, or FFmpeg and does not abort the matrix.
- Added `mcp-player-matrix`, a complete 63-case configuration harness covering Legacy Exo, Media3, IJK, and GSYVideoPlayer with Auto/Media3/System/Legacy Exo engines across Dynamic/Pull/Fixed and Hardware/Software/Hardware Preferred.
- The player matrix runs startup/A-V verification, absolute seek, forward/back direct seeks, pause/resume, and Comskip right/left checks, continuing after media recovery problems.
- Added case listing/filtering (`--list-cases`, `--case-id`, and dimension filters) for future Codex automation.
- Increased host-side playback observation ceiling to 300 seconds so a 3-minute watchdog is not truncated at 120 seconds.
- Added `PLAYER_TEST_HARNESS_v0.5.46.md` documenting matrix expansion, watchdog semantics, and future SageTV server/FFmpeg correlation.
- No Android APK source changes in this update.

## v0.5.48 - Confirm Start From Beginning with Select
- Changed the post-Search recording startup navigation from `ff`, `right`, `play_pause`, `down`, `play_pause` to `ff`, `right`, `play_pause`, `down`, `select`.
- Uses native SageTV `select` (event 20 / Enter) to confirm the highlighted Start From Beginning choice instead of toggling playback/resume with a second `play_pause`.
- Updated the legacy diagnostic sequence and regression tests to match the active startup helper.
- Host/script-only change; no Android APK rebuild required.

## v0.5.49 - Restore exact proven Search text timing
- Restored the exact historically working recording-name path: native SageTV Search command -> 50 ms -> Android OS `adb input text` -> 50 ms -> Android BACK -> 100 ms -> post-Search commands.
- Removed Search/IME readiness waits from the timing-sensitive text injection path; debug UI/IME state is collected only after injection so instrumentation cannot delay text entry.
- `dev_type_text` now reports command completion separately from field-content verification; empty ADB stdout is no longer described as proof that text was entered.
- Kept corrected Start From Beginning navigation: `ff`, `right`, `play_pause`, `down`, `select`.
- Host/MCP/script-only; no APK rebuild required.

## v0.5.50 - paced Android text entry
- Recording Search text is now injected one character at a time through Android's OS input service instead of one whole fast phrase.
- Spaces use `KEYCODE_SPACE`; normal characters use `adb shell input text <char>`.
- New CLI option `--text-char-delay-ms` controls pacing for playback, Media3, Comskip, and full player-matrix harnesses. Default is 100 ms per character; valid range is 0-2000 ms.
- The full player matrix prints the active text-character delay so repeated cases are reproducible.

## v0.5.52 - Persistent ADB shell for MCP runtime
- MCP now opens one persistent `adb shell` after `adb_connect` and reuses it for runtime test commands instead of spawning a new `adb shell` process for every status/input/debug operation.
- Runtime shell commands are framed and serialized so command output cannot interleave; the shell is automatically closed when the MCP server exits.
- Added `adb_session_status` plus persistent-shell PID/restart/command counters so Codex/test harnesses can verify reuse.
- `app_status`, wake/key/input/debug broadcasts, `dumpsys`, and other shell-based runtime operations use the persistent shell. Install/uninstall/pull/screenshot and `adb connect` remain separate ADB subcommands where required.
- Normal Search text still defaults to the proven one-shot `adb input text` path (`--text-char-delay-ms 0`); nonzero per-character pacing remains diagnostic only.
- Host/MCP-only change; no Android APK rebuild required.

## v0.5.53 - Matrix step progress / watchdog visibility
- Full `mcp-player-matrix` now prints each media check before it starts, including the per-step watchdog value.
- Long-running checks emit host-side `WAIT` heartbeats every 10 seconds with elapsed time and watchdog progress, then a `DONE` line when the MCP call returns.
- Matrix startup prints persistent ADB shell status/PID/restart count immediately after `adb_connect`, making transport reuse visible during long runs.
- This fixes the appearance of a hang after `PASS: startup ...` when the first `absolute_seek` is legitimately using its 180000 ms per-step watchdog.
- Host/script-only change; no Android APK rebuild required.

## v0.5.54
- Added debug-only native MiniClient text injection (`input_text_native` / `dev_input_text_native`) for reliable repeated Search automation.
- Native text reproduces `KeyMapProcessor.handleDefaultEvent()` keyCode/keyChar encoding instead of depending on Fire TV IME/ADB focus timing.
- Normal player-matrix startup now waits for SageTV `hasTextInput`, injects text directly over the MiniClient event channel, then hides the IME.
- Kept ADB `sendtext`/keyboard text and paced text paths for diagnostics/fallbacks.
- Debug status version advanced to 4.


## v0.5.61 - Selection names + complete Fixed encoding parameters
- Standardized user-facing streaming selections to **Push / Pull / Fixed** while preserving persisted values `dynamic / pull / fixed`.
- Standardized decoding selections to **Hardware / Software / Fallback** while preserving persisted values `hardware / software / hardware_preferred`.
- Extended `dev_set_player_config` and debug snapshots with the complete Fixed encoding parameter set: encoding policy/container, video bitrate/FPS/key-frame/B-frames/resolution, audio codec/bitrate/channels, and remux policy/container.
- Full player matrix passes and verifies every Fixed parameter before playback.
- Fixed matrix defaults force real encoding (`fixed_encoding/preference=always`, `fixed_remuxing/preference=off`) instead of allowing SageTV to skip encoding or remux instead.
- Added the same Fixed parameter CLI options to single playback/session and Media3 matrix entry points.
- Debug status version advanced to 8 because the debug receiver configuration contract changed.

## Historical entries recovered during documentation consolidation

The following summaries were previously present only in per-version update
notes. They are retained here so those obsolete files can be removed without
losing release history.

### v0.5.32

- Tested a Media3 Pull large-seek latency experiment by increasing only its
  SageTV network read buffer from 256 KiB to 1 MiB; Dynamic/Push, Legacy Exo,
  IJK, decoder policy, TS search, and Comskip commands were unchanged.

### v0.5.31

- Corrected Comskip automation to send native SageTV RIGHT/LEFT commands rather
  than normal video arrow mappings, while retaining A/V recovery, surface,
  decoder, and timeline evidence.

### v0.5.30

- Removed production defaults for Search-based recording selection. Every
  Search test must now receive explicit, non-empty, single-line `--text`.

### v0.5.19

- Added the MCP-only native Search helper (`dev_search`) and the
  `mcp-search-test` host command without requiring an APK rebuild.

### v0.5.5

- Fixed zero-position seeks and asynchronous seek completion in both Exo
  backends, removed UI-thread polling/sleeps, and corrected paused IJK frame
  stepping from one second to approximately one 30 fps frame.
- Deliberately deferred Push/Pull timeline rebasing, a shared seek coordinator,
  decoder changes, and GSY seek restructuring.

### v0.5.3

- Excluded GSY's unused legacy RTMP client and Media3 RTMP datasource from the
  GSY Exo2 dependency without adding any TLS bypass or changing player engines.

### v0.5.2

- Restored the original SageTV IJK 0.8.8 Java/JNI runtime and separated it from
  GSYVideoPlayer. GSY gained Auto, Media3, Android System, and Legacy Exo
  engines plus SageTV Push/Pull `MediaDataSource` bridges.

### v0.5.1

- Prevented the confirmed MTK MPEG-2 MediaCodec input-buffer failure loop on
  AFTMM/mantis by selecting bundled FFmpeg software decode for MPEG-2 while
  retaining hardware decode for other codecs/devices.

### v0.5.0

- Established four selectable backends (Legacy Exo, Media3, IJK, and GSY), a
  shared decoding-method preference, and a dedicated GSY settings screen.

### v0.4.9

- Added an IJK MPEG-2 hardware compatibility experiment using synchronous
  MediaCodec, a larger probe/analyze budget, MTK decoder preference, and a
  reversible software-decoding setting.

### v0.4.8

- Defaulted IJK MPEG-2 to bundled FFmpeg software decoding, retained hardware
  decode for AVC/HEVC, added an opt-in MPEG-2 hardware preference, and handled
  player errors without a duplicate completion cascade.

### v0.4.7

- Made the Dev root Gradle file self-repairing from a canonical copy and
  preserved damaged input for diagnosis.

### v0.4.5

- Connected GDX to the three-backend factory, ensured Media3 selection really
  used Media3, rolled back an unsuccessful SurfaceView experiment, and added
  external player diagnostics.

### v0.4.4

- Tested explicit SurfaceView binding/order changes across Legacy Exo, Media3,
  IJK, and the GDX overlay in response to black/video-output failures.

### v0.4.1

- Restored the Dev root Gradle file after an accidental module-file overwrite
  and added regression protection for the same layout corruption.

### v0.3.7

- Re-enabled logcat-only read-only telemetry for a clean-install experiment;
  it was subsequently removed after proving instrumentation affected startup.

### v0.2.4

- Corrected MCP unittest invocation to use discovery instead of treating the
  filesystem path as a Python module.

### v0.2.3

- Fixed WSL/Docker Desktop workspace mounting by passing the repository's
  absolute root to Compose instead of relying on a stale external path.
