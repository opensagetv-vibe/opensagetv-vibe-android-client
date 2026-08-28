
### Automated test client-ID rule

- Normal app operation must use the original generated/persisted client-ID method.
- Before any automated MCP/player test, first-time MiniClient setup must already be complete.
- Automated test wrappers must use `44:45:56:30:30:31` (`DEV001`) when the caller omits `--client-id`.
- An explicit `--client-id` always wins.
- Use ensure semantics: do not restart the app when the requested test ID is already configured/active.
- Do not reintroduce a hard-coded client ID into normal Android runtime source.

# FIRST-TIME SETUP REQUIRED BEFORE AUTOMATION

- `[REQUIRED]` After every fresh install, reinstall that clears app data, or Clear Data, a human must launch the Dev app, complete first-time setup, select/confirm the SageTV server, and reach the normal MiniClient UI **before any automated MCP/player test is run**.
- `[DO NOT]` Do not use player matrices, tuning matrices, Search automation, session tests, or another automated test to perform onboarding/first-time setup. If setup is incomplete, stop the test and report setup required.
- `[CLIENT ID]` Normal source behavior is generated + persisted client ID. Never reintroduce a hard-coded `DEV001` client ID in player/core code. After setup, automated test wrappers default to `44:45:56:30:30:31` unless the caller supplies `--client-id`.
- `[EXAMPLE]` `./dev.sh client-id --set DEV001` sets `44:45:56:30:30:31`; `./dev.sh client-id --generate` creates a fresh original-style ID; `./dev.sh client-id --show` reports configured and active IDs.

## v0.5.74 persistent codec queueing setting

- [x] FIX — Add `MediaCodec Queueing Mode` to Media3 and legacy ExoPlayer settings.
- [x] DEFAULT — Use `Sync (Recommended)` / `sync` as the default for both backends.
- [x] OVERRIDE — Explicit MCP tuning remains higher priority than the saved preference; tuning reset restores preference ownership on the next player creation.
- [x] MATRIX — Runtime/matrix default is Sync; add `auto_codec` profile for control testing.
- [ ] DEVICE VERIFY — After APK rebuild, confirm the settings persist across process restart and player state/logs show Sync when no MCP override is active.

## 20260828_034725 runtime tuning matrix evidence

Latest evidence reviewed: **20260828_034725_player_tuning_matrix.json**. The suite requested **27 combinations**: 3 Push codec-mode cases and 24 Pull cases. Three Pull cases ended as `INFRA_ERROR`, so only 21 Pull tuning observations are valid for comparative recovery analysis.

### Push — codec queueing mode has a useful signal

- `[~] VERIFY` Media3 Push `codecMode=sync` ranked best at **3,768 ms RECOVERED**.
- `[~] VERIFY` Media3 Push `codecMode=auto` recovered at **4,967 ms**.
- `[~] VERIFY` Media3 Push `codecMode=async` was slower at **7,103 ms SLOW_RECOVERY**.
- `[x] USER DECISION / FIX` Promote `sync` as the normal/default codec queueing mode and expose it in player settings. This intentionally acts on the current single-run signal at the user's request rather than waiting for three repeats.
- `[ ] VERIFY` Still repeat Push `sync` versus `auto` at least 3 times each after v0.5.74 is installed; if Sync does not reproduce the advantage, revisit the default rather than hiding the contrary evidence.
- `[!] DISCIPLINE` Do not infer anything about TS search, Pull read size, Pull buffering, or Pull seek policy from Push. Those dimensions are intentionally ignored by the Push path.

### Pull — no tested tuning combination fixes the long Comskip stall

Across the 21 valid Pull observations, every result was either `SLOW_RECOVERY` or `WATCHDOG_EXPIRED`. No completed Pull combination recovered under the 5 s threshold.

Aggregated from the report:

- `tsSearchMultiplier=8`: 12 valid cases, median recovery about **35.8 s**, range **34.3–38.1 s**, **0 watchdogs**.
- `tsSearchMultiplier=4`: 9 valid cases, median recovery about **38.0 s**, range **23.5–51.1 s**, **3 watchdogs**.
- `seekPolicy=closest`: median about **35.4 s**.
- `seekPolicy=next`: median about **37.5 s**.
- `pullReadKb=256`: median about **35.5 s**.
- `pullReadKb=512`: median about **37.5 s**.
- `codecMode=auto`: median about **35.4 s**; `sync` about **35.5 s**; `async` about **37.5 s**.

Interpretation:

- `[x] TEST` 512 KiB Pull reads did **not** improve the long Media3 Pull Comskip case. Do not promote 512 KiB as a performance fix from this matrix.
- `[x] TEST` `NEXT_SYNC` did **not** improve Media3 Pull versus `CLOSEST_SYNC`; median recovery was actually slower.
- `[x] TEST` MediaCodec `async` did **not** improve Media3 Pull and trends worse than `auto`/`sync`.
- `[~] VERIFY` 8x TS search is the more stable baseline: it removed watchdogs in this matrix and clustered tightly around 34–38 s, but it still does not solve the stall. Keep 8x as baseline unless a different experiment specifically needs 4x.
- `[!] DO NOT PROMOTE` `pull_combo_001` (4x/closest/256/auto) at **23.510 s** is the fastest Pull result but is still unacceptably slow and is an isolated outlier relative to the rest of the 4x population. Repeat it before treating it as meaningful.

### Tuning-matrix startup contamination must be fixed before another broad sweep

Three Pull combinations produced `INFRA_ERROR` because playback did not become healthy before the operation. At least `pull_combo_004` clearly entered the test at **end of recording** (`mediaTimeMs == durationMs == 3,089,953`) with `AskToDeleteRecording` visible and a SageTV seek request beyond EOF. This is a harness/setup failure, not a player-tuning verdict.

- `[!] HARNESS BUG` A tuning combination must never begin its measurement if the selected recording is already at EOF/delete prompt or if A/V startup is not healthy.
- `[ ] FIX` Before every tuning operation, verify `popupName != AskToDeleteRecording`, player position is safely before EOF, and A/V counters advance. If not, explicitly stop/restart the same recording from the beginning before applying the operation.
- `[ ] FIX` Record a structured startup-failure reason (`EOF`, `delete_prompt`, `no_first_frame`, `no_audio`, `player_error`, etc.) instead of only generic `INFRA_ERROR`.
- `[ ] VERIFY` Rerun only the 3 invalid combinations after the startup fix; do not rerun all 24 Pull cases unless another player parameter changes.

### Next technical priority after this matrix

Do **not** spend another broad sweep on Media3 Pull buffer/read/seek/codec knobs. The matrix strongly suggests those parameters are secondary. Priority should move to:

1. `[ ] SERVER TEST` Run the documented SageTV `use_nio_transfers=false` versus `true` A/B on the same Media3 Pull Comskip case.
2. `[ ] RECOVERY TEST` Add/execute a controlled **small SageTV-level recovery skip** after a detected Pull stall and measure recovery-to-first-frame. The user's manual observation that a tiny SageTV skip immediately restarts stalled video is now a high-value recovery clue.
3. `[ ] INSTRUMENT` Correlate SageTV MediaServer actual bytes written with Android Pull datasource bytes received around the stall.
4. `[ ] VERIFY` If server NIO or the recovery nudge changes the stall materially, repeat before changing Android defaults.

## v0.5.73 runtime tuning startup optimization

- [x] FIX — Tuning sweeps no longer repeat force-stop + Search for every combination by default. Combination 1 establishes the recording and caches its exact SageTV MediaFile ID; combinations 2+ use session disconnect/reconnect + direct Sagex Watch to create a fresh player.
- [x] VERIFY — Keep `--startup-mode isolated` as the control path and automatically fall back to it when fast replay verification fails.
- [x] INSTRUMENT — Record `startupPath` and `startupMs` per combination, plus cached MediaFile ID and fast-replay fallback count in the tuning report.
- Rule: tuning optimization must preserve a fresh player instance per combination. Reusing the same active player across tuning values is not an acceptable speed optimization because player-construction settings would not be reapplied cleanly.

## v0.5.72 tuning-matrix ADB preflight

- [x] FIX: `mcp-player-tuning-matrix` must call `adb_connect` before `dev_prepare_clean_start`.
- [x] VERIFY: preflight `adb_session_status` before entering the tuning combination loop.
- [x] VERIFY: if ADB is unavailable, fail once at suite startup; do not emit repeated per-combination `INFRA_ERROR` results.

# TASK_CODEX.md — SageTV MiniClient Client/Server Playback Diagnosis

Project baseline: **v0.5.75**  
Debug contract: **debugStatusVersion=14**  
Primary hardware target: real Fire TV hardware through the Dev MiniClient package.  
Latest evidence reviewed: **20260828_015224_player_full_matrix.json**  

## HIGH PRIORITY — SageTV MediaServer Pull partial-write investigation

A server-side code review of `google/sagetv` `java/sage/MediaServer.java` found a plausible Pull corruption/stall mechanism that must be tested before more Android-player tuning. This is especially relevant because a stalled recording can resume immediately after a small SageTV skip.

### Hypothesis

The default non-NIO MediaServer file-transfer path reads a block from disk and calls `SocketChannel.write(ByteBuffer)` once, but then advances the file offset by the number of bytes read from disk rather than the number of bytes actually written to the socket. A Java `SocketChannel.write()` is not guaranteed to drain the entire buffer in one call. If a partial write occurs, bytes can be silently omitted from the MPEG-TS stream. A later small SageTV skip may recover because it starts another READ/seek at a clean transport-stream/sync position.

Relevant server defaults/paths to verify against the running SageTV build:

- `use_nio_transfers=false` by default.
- `use_blocking_socket_for_mediaserver=true` by default.
- Normal Pull READ path uses a single socket write for the disk buffer.
- The optional read-ahead path appears to use the same single-write assumption.
- The NIO `FileChannel.transferTo()` path advances by the actual transferred byte count and is therefore a useful A/B control.
- For growing/current recordings, MediaServer may wait for more data and may pad an unsatisfied request with `0xFF`; keep this as a separate secondary hypothesis.

### Required experiment sequence

- `[ ] SERVER TEST` Run the exact same problematic completed-recording Pull case with `use_nio_transfers=false` and record recovery time, datasource READ count/bytes/wait, open positions, first post-seek frame, and whether a second small SageTV skip is required.
- `[ ] SERVER TEST` Set `use_nio_transfers=true` in `Sage.properties`, restart SageTV, and repeat the exact same case with **no Android tuning change**.
- `[ ] DISCIPLINE` The A/B test must keep player, codec mode, TS-search multiplier, Pull read size, recording, and Comskip/seek target identical. Do not combine this server test with another player change.
- `[ ] ATTRIBUTION` If NIO materially reduces/eliminates the stall, raise SageTV MediaServer transfer handling to the leading cause and pause further Android tuning until the server transfer path is instrumented.

### Server instrumentation

Instrument the default non-NIO write path to compare expected bytes with actual bytes written. At minimum record:

```java
int expected = hackBuf.remaining();
int written = s.write(hackBuf);
if (written != expected)
{
  System.out.println("MEDIASERVER PARTIAL WRITE expected=" + expected +
      " written=" + written + " offset=" + offset);
}
```

Also instrument the read-ahead write path the same way. Capture timestamp, requested READ offset/length, disk bytes read, socket bytes written, and connection identity.

### Candidate server fix if partial writes are observed

Do not advance the file position until the entire buffer has been sent. Use a bounded/full-drain loop for blocking sockets, with EOF/error handling, for example:

```java
while (hackBuf.hasRemaining())
{
  int written = s.write(hackBuf);
  if (written < 0)
    throw new java.io.EOFException();
}
```

Apply the equivalent correction to the read-ahead buffer path. Do not blindly use this exact loop for non-blocking mode without selector/backpressure handling; preserve correct behavior for both `use_blocking_socket_for_mediaserver=true/false`.

### Secondary server checks

- `[ ] SERVER INSTRUMENT` Log every Pull `READ offset length` request and the actual bytes returned, especially across the failing Comskip.
- `[ ] SERVER INSTRUMENT` Log whether the request uses normal read, read-ahead, remux/transcode, or NIO `transferTo()`.
- `[ ] SERVER TEST` Verify `media_server/readahead_optimized_file_extensions`; keep it disabled during the first A/B unless it is already part of the user's normal configuration.
- `[ ] SERVER TEST` Run the same test on a completed recording first. Only after that, separately test a growing/current recording and inspect the wait/padding behavior.
- `[ ] CORRELATE` Align server READ/write timestamps with MiniClient `health_dataSourceNetworkRead*`, player BUFFERING state, queued/rendered decoder counters, and the small SageTV recovery skip.

### Success criteria

A server-side cause is strongly supported if one or more of these are observed:

1. `written < expected` on the failing default transfer path.
2. `use_nio_transfers=true` consistently eliminates or materially shortens the same Pull stall without an Android change.
3. Server logs show missing/short transmission immediately before the client stops rendering.
4. A corrected full-drain server write removes the stall in repeated A/B tests.

Do **not** mark SageTV server code as the root cause solely because `use_nio_transfers=true` is faster; repeat the winning condition and correlate server/client byte counts first.

## v0.5.71 combined streaming tuning rule

`mcp-player-tuning-matrix` accepts `--streaming push,pull` or `pull,push`. Treat each streaming mode as a separate experimental population even when they are emitted into one report.

For Pull, use the full requested runtime tuning grid. For Push, do **not** multiply Pull-only parameters into duplicate tests: TS timestamp-search multiplier, Pull network read size, Pull load-control thresholds, Pull seek-recovery enable/delay, and Pull directional seek policy/threshold are not consumed by the Push path. The current Push tuning dimension from this surface is MediaCodec queueing mode (`auto/async/sync`).

Reports must preserve `streamingModes`, `combinationCountByStreaming`, and `ignoredTuningDimensionsByStreaming`. Compare/rank Push and Pull results with their streaming label intact; never infer that a fast Push codec result validates a Pull extractor/read/buffer setting.

No APK rebuild is required for this harness change; continue using `debugStatusVersion=13`.

## v0.5.70 runtime tuning rule

Before recompiling a player constant, use the Dev runtime tuning surface when the variable is supported. One installed v0.5.70 debug APK can test many parameter combinations through MCP.

Supported runtime variables for native Media3 and legacy Exo2: TS timestamp-search multiplier, Pull network read KiB, seek policy, min/max/playback/rebuffer thresholds, seek-recovery enable/delay, directional-sync threshold, and codec queueing `auto/async/sync`.

Use `mcp-player-tuning-matrix` for comparative experiments. Every combination must use fresh playback. Reports must retain selected/effective tuning values and rank by A/V recovery. Seek/timeline accuracy is diagnostic-only.

Do not promote a single fastest observation into compiled defaults. A candidate must: (1) recover A/V, (2) beat or equal compiled default on repeated runs, (3) not regress seek-to-zero/backward/pause-resume as applicable, and (4) not increase player errors/crashes.

Useful commands:

```bash
./dev.sh mcp-player-tune --backend media3 --show
./dev.sh mcp-player-tune --backend media3 --reset
./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player media3 --streaming pull --check comskip_right --profiles default,next_sync,large_read,async_codec,sync_codec,no_reprepare --slow-recovery-ms 5000
```

## v0.5.69 implemented corrections / next proof

The 20260828_015224 report disproved a common tuning policy across Media3 and legacy Exo2. Treat their Pull paths separately.

- `[~] VERIFY` **Media3 Pull reprepare disabled.** The fixed 10 s reprepare fired while useful decode/extractor work was still progressing, then first frame arrived ~34 s later. This inflated the long case to ~44 s. v0.5.69 leaves the existing load/extractor work intact instead of resetting it.
- `[~] VERIFY` **Media3 Pull seek policy restored to CLOSEST_SYNC.** Direction-aware NEXT/PREVIOUS did not improve the long Media3 family Comskip.
- `[~] TEST` **Legacy Exo2 keeps directional sync.** Its isolated forward Comskip changed from ~50.6 s watchdog to ~8.54 s recovery, so do not revert this policy without contrary device evidence.
- `[~] TEST` **Exo2 Pull block 256 KiB -> 512 KiB.** The latest Exo2 run showed ~6.1 s cumulative server READ wait by the 5 s milestone and ~6.96 s total READ wait for the operation. Measure whether larger blocks reduce READ count/wait and recovery.
- `[~] VERIFY` **GSY/System now fails safe to Media3.** The Android System MediaDataSource bridge remains unsuitable for SageTV on this Fire TV, but selecting `system` must no longer immediately stop the recording. Requested preference remains `system`; effective backend must be Media3 in health data.
- `[x] HARNESS` `--slow-recovery-ms`, `--slow_recover_ms`, and `--slow-recover-ms` select the SLOW_RECOVERY threshold. Preserve `slowRecoveryMs` in every report.

First v0.5.69 focused run must derive from `20260828_015224_player_full_matrix.json` and exclude unchanged native/GSY Media3 Push cases:

```bash
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "meet the press" --hardware-only --streaming push,pull --issues-only /workspace/artifacts/firetv/20260828_015224_player_full_matrix.json --exclude-case-id gsyplayer__push__hardware__gsy_media3,media3__push__hardware --slow_recover_ms 5000 --watchdog-ms 50000
```

This should select 7 cases. Do not add another playback tuning variable until those results identify which backend improved/regressed.

## v0.5.68 retest selection rule

Focused retesting must not automatically rerun every abnormal backend from the previous report. If no code, configuration, instrumentation, or server-side behavior relevant to a backend changed, exclude it unless it is intentionally required as a comparison/control.

Available filters:

- `--exclude-players exoplayer,ijkplayer` — omit whole player families.
- `--exclude-gsy-engines system` — omit only selected GSY engines while keeping the remaining GSY player cases.
- `--exclude-case-id <case-id>[,<case-id>...]` — omit exact generated cases.

For the first v0.5.69 validation, **include GSY/System Pull and Push once** because their behavior changed: the broken System MediaDataSource selection now falls back to Media3. After that proof, exclude `system` from unrelated performance retests unless fallback behavior changes again.

The matrix report must preserve the effective exclusions under `retestExclusions` so Codex can tell whether an absent result was intentionally skipped.

### New evidence from 20260828_015224

- `[!] FIX NEXT` The v0.5.67 fixed 10 s Media3 Pull reprepare can worsen a long seek: it fires at ~10 s, then the first frame may still arrive ~34 s later. Do not interpret that reprepare as an improvement; evaluate removing/disabling it for Media3 before the next Media3-specific change.
- `[~] IMPROVED` Exo2 Pull now exposes I/O telemetry and a previously watchdog-prone forward Comskip recovered in ~8.5 s. The server READ wait is a large fraction of that recovery, so transport/read-size optimization is now measurable.
- `[~] SYSTEM` The direct System MediaDataSource path remains incompatible, but v0.5.69 changes requested System to a Media3 fallback so it should no longer terminate playback. Verify once, then exclude from unrelated performance retests.


## v0.5.67 implemented code candidates

The previously proposed Pull code candidates are now implemented and must be validated on-device before further player tuning:

- `[~] TEST` Media3 and legacy Exo2 Pull now use direction-aware sync policy for seeks >=2 s: forward=`NEXT_SYNC`, backward=`PREVIOUS_SYNC`, small=`CLOSEST_SYNC`.
- `[~] TEST` one-shot 10 s Pull BUFFERING recovery reprepare is active on Media3 and Exo2. Look for `pull_seek_recovery_armed` and `pull_seek_reprepare_before/after`. It must never repeat for the same seek generation.
- `[x] FIX` Media3 and Exo2 Pull non-zero DataSource reads no longer return invalid 0 when closed or when the backing source unexpectedly yields zero bytes.
- `[x] INSTRUMENT` Exo2 Pull now exposes cumulative open/READ bytes/count/wait/error/max-request/position telemetry through the same health fields as Media3.
- `[x] REVERT` Media3 Pull TS timestamp search is back at the proven 8x baseline; do not re-test 4x unless new evidence justifies it.

Required next comparison: run `--issues-only` from `20260828_010837_player_full_matrix.json` after installing `debugStatusVersion=11`. Compare A/V recovery first; then compare datasource READ completion time, seek policy trap, first post-seek frame, and whether the fallback reprepare fired.

## 1. Purpose

This file is the source-of-truth task and reasoning guide for Codex when diagnosing SageTV MiniClient playback failures, slow recovery, seeking, Comskip, buffering, or startup problems.

The system must be treated as a **client/server pipeline**, not as an Android-player-only problem. Before changing code, determine which layer owns the failed behavior and collect correlated evidence from both sides whenever possible.

The layers are:

1. SageTV server / MiniClient protocol handling.
2. SageTV STV / Comskip marker and command semantics.
3. SageTV Pull or Push transport.
4. SageTV transcoder / FFmpeg path when Fixed or transcoding is active.
5. Android MiniClient protocol bridge and datasource.
6. Android playback backend: legacy ExoPlayer, Media3, IJK, or GSY engine.
7. Android MediaCodec / AudioTrack / Surface rendering.
8. MCP/ADB test harness.

Do **not** assign blame to a layer simply because it is where the symptom becomes visible.

---


## 1.1 Living backlog maintenance rule

`TASK_CODEX.md` is a **living diagnostic and corrective backlog**. Keep it current as new evidence is discovered.

Whenever testing, logs, code review, upstream research, or server/client correlation reveals something Codex should investigate, reproduce, instrument, test, refactor, or fix:

1. Add the finding to this file in the same change package.
2. Record the evidence that justified adding it (matrix/report name, status, timing/counter pattern, server/client event sequence, log signature, or upstream reference).
3. Classify the item as **TEST**, **INSTRUMENT**, **FIX**, **REFACTOR**, or **VERIFY**.
4. Identify the likely owning layer: SageTV server, STV/Comskip, transport, transcoder, MiniClient protocol/datasource, Android player, MediaCodec/AudioTrack/Surface, or MCP harness.
5. State the exact focused test needed to prove or disprove the hypothesis.
6. Do not mark a fix complete until the focused failing case passes and no relevant regression is introduced.
7. If later evidence disproves a hypothesis, update or close the item instead of leaving stale guidance.
8. Preserve historical findings that explain why a current workaround or test exists.
9. **Packaging dependency rule:** every delivered overwrite ZIP must be self-consistent with the user-visible prior package. Do not ship tests/docs that depend on source changes from an intermediate patch the user was never given. If work is interrupted before a patch is delivered, the next ZIP must carry forward those undelivered changed files or explicitly be labeled as requiring that prior patch.

When ChatGPT prepares a code/documentation update for this SageTV MiniClient project, it should update this file automatically whenever the work creates a new Codex-actionable test/fix item. The user should not need to ask separately.

Use these status markers for actionable items added below:

- `[ ]` pending
- `[~]` implemented or under test, awaiting device/server validation
- `[x]` validated complete
- `[!]` blocked or needs more evidence

## 2. Non-negotiable verdict rule

> **A/V recovery is the verdict. Seek accuracy is telemetry.**

For a media operation:

- PASS requires real expected video/audio output to resume and keep advancing.
- Renderer/audio counters are authoritative for recovery.
- Timeline, local player position, server anchor, wrapper flags, and landing error are diagnostic context only.
- Do not turn a proven A/V recovery PASS into a FAIL solely because the player landed several seconds away from the requested target.

Default seek landing diagnostic tolerance is currently +/-10 seconds. Larger deviations should be reported as warnings, not as recovery failures, unless the direction/position is so inconsistent that it indicates a separate protocol problem.

---

## 3. Safety and scope

Follow `AGENTS.md` first.

Important rules for server-side investigation:

- Server diagnostics are **read-only by default**.
- Do not delete recordings.
- Do not alter the SageTV media library.
- Do not restart SageTV, Docker containers, or transcoder processes unless explicitly approved or the existing test workflow already requires it.
- Do not change Sage.properties, Comskip settings, transcoder settings, or server code merely to gather evidence.
- Never modify or uninstall the protected production Android MiniClient.
- Dev Android target remains `org.opensagetv.miniclient.dev.debug`.

When a change is required, make the smallest isolated change and rerun the exact failing case.

---

## 4. Read these project files before changing playback code

Codex should review these in this order when investigating playback:

1. `AGENTS.md`
2. `TASK_CODEX.md`
3. `HANDOFF.md`
4. `MATRIX_REVIEW_20260828_010837.md` (latest)
5. `MATRIX_REVIEW_20260828_002208.md` (prior baseline)
6. `PLAYER_TEST_HARNESS_v0.5.46.md`
7. `source/dev/README_VIDEO_PLAYERS.md`
8. The exact latest matrix JSON supplied by the user.
9. Only then inspect the relevant player, datasource, protocol, or MCP source.

The current authoritative Android source tree is:

`source/dev`

---

## 5. Ownership model: Pull vs Push

### 5.1 Pull

SageTV Pull behaves roughly like a remote random-access file datasource. The Android player requests bytes and the MiniClient sends SageTV `OPEN`, `SIZE`, `READ`, and `CLOSE` commands.

Therefore a slow Pull seek can be caused by either side:

- server READ latency;
- server disk/transcoder delay;
- network/socket latency;
- excessive client READ count caused by extractor seeking;
- client-side extractor/PCR scanning;
- decoder/render recovery after bytes have already arrived.

Do not classify a Pull delay until `recoveryMs` is compared with Pull I/O timing.

### 5.2 Push

Push ownership is different and this distinction is critical.

The project design states that SageTV owns seeking for Push playback. The normal server-driven sequence is conceptually:

`SageTV seek/skip -> MEDIACMD_FLUSH -> discard client Push buffer/player state -> new pushed stream data -> new server anchor -> player reprepare/recovery`

The Push player normally should not need to implement arbitrary file-style seeking itself.

Therefore distinguish:

- **real server-originated Push seek/rebase**, and
- **MCP direct local Android player seek**.

A local MCP `player.seekTo()` failure in Push mode is not proof that SageTV's server-driven Push seek is broken.

Source reference:

`source/dev/README_VIDEO_PLAYERS.md`

---

## 6. Timeline fields: never mix these concepts

The MCP/debug state deliberately exposes separate clocks.

### `serverRequestedSeekMs`

Latest explicit SageTV `MEDIACMD_SEEK` target received by the MiniClient.

This answers:

> Where did SageTV explicitly ask the client/player to seek?

### `serverAnchorMs`

Push stream anchor established from SageTV buffer metadata after a flush/rebase.

This answers:

> Where does SageTV say this newly pushed stream starts on the absolute SageTV timeline?

### `health_playerPositionMs`

Backend-local Android player position.

This answers:

> Where does Exo/Media3/IJK think it is inside its currently loaded source?

### `sageTimelineMs`

Value the MiniClient reports to SageTV through `MEDIACMD_GETMEDIATIME`.

For Exo/Media3 Push this is normally conceptually:

`SageTV reported timeline ~= serverAnchorMs + local player position`

This is **not** an independent server desired target.

### Seek event sequence/timing

Also use:

- `serverSeekSequence`
- `serverSeekMonotonicMs`
- `serverSeekWallMs`
- `serverSeekAgeMs`
- server flush sequence/timestamps
- server anchor sequence/timestamps

These allow events from Android and SageTV to be correlated without guessing from late host polling.

---

## 7. Android exact-event traps

Before each focused action, the matrix should clear the debug event ring.

Available MCP tools include:

- `dev_player_state`
- `dev_player_events`
- `dev_clear_player_events`
- `dev_test_checkpoint`

The event ring captures state/counters at important callbacks including:

- server seek command;
- server flush command;
- server Push anchor set;
- backend seek invoke/return;
- seek discontinuity/completion;
- BUFFERING/READY/state changes;
- isPlaying changes;
- first rendered video frame;
- player pause/play;
- decoder/player errors;
- reprepare/flush lifecycle events.

At those events inspect, where available:

- video rendered count;
- video queued/input count;
- video decoder init/release count;
- audio rendered/queued count;
- audio decoder init/release count;
- AudioTrack playback head;
- AudioTrack session/state;
- local player position;
- decoder name and hardware/software type;
- Surface validity;
- playback state;
- `playWhenReady` / playing state;
- datasource state;
- event timestamp and `snapshotLagMs`.

Do not replace exact-event evidence with a later single host snapshot.

---

## 8. Long-wait/crash monitoring

The harness probes abnormal waits at approximately:

- 5 seconds;
- 15 seconds;
- 30 seconds.

A new crash/process failure should end the operation early as `PLAYER_CRASHED` or `STARTUP_PLAYER_CRASHED` rather than waiting for the full watchdog.

Crash evidence includes, where available:

- `FATAL EXCEPTION`;
- `AndroidRuntime` fatal entry;
- `SIGSEGV`;
- `SIGABRT`;
- fatal signal/tombstone evidence;
- Dev app process disappearance;
- PID change/restart.

Slow playback without a crash remains a playback/recovery observation and must not be mislabeled as an infrastructure crash.

---

## 9. Pull-side evidence Codex must correlate

For every slow/failed Pull operation record at least:

- total `recoveryMs`;
- datasource open count delta;
- datasource open wait ms delta;
- SageTV READ count delta;
- bytes requested delta;
- bytes received delta;
- cumulative READ wait ms delta;
- max READ size;
- READ error count;
- last open position;
- last network READ position;
- first/last relevant Android player events;
- time to first post-action video frame;
- time to first post-action audio progress.

### Desired additional server-boundary telemetry

If not already exposed, add or collect:

- maximum single READ wait;
- READ latency p50/p95;
- short READ count;
- zero-length READ count;
- first READ requested byte position after seek;
- first READ request timestamp;
- first READ completion timestamp;
- first data-arrival timestamp after seek.

### Pull attribution rule

Compare wall recovery time with server/socket wait.

Example A:

`recovery = 34 s`  
`SageTV READ wait = 30 s`

This strongly implicates server/network/storage/transcoder delivery.

Example B:

`recovery = 34 s`  
`SageTV READ wait = 5 s`  
`~50 MB scanned before output`

This strongly implicates client-side extractor/seek/load/decoder processing rather than raw server wait.

Do not use a fixed percentage as proof. Report the measured times and confidence.

---

## 10. Push-side evidence Codex must correlate

For each real server-originated Push seek/Comskip capture, in order if possible:

1. SageTV command/operation requested.
2. `serverRequestedSeekMs` and seek timestamp, if SageTV sends `MEDIACMD_SEEK`.
3. `MEDIACMD_FLUSH` sequence/timestamp.
4. client Push buffer flush event.
5. first Push buffer/data after flush.
6. `serverAnchorMs` and anchor-set timestamp.
7. Android player reprepare/reset event.
8. decoder input/output restart.
9. first video frame.
10. first audio progress.
11. READY/isPlaying recovery.

### Desired additional Push telemetry

Add/collect if missing:

- first Push byte timestamp after flush;
- bytes received in first Push buffer;
- time from FLUSH to first Push data;
- time from first Push data to anchor;
- time from anchor to reprepare;
- time from reprepare to decoder input;
- time from decoder input to first rendered frame.

### Push attribution examples

If:

`FLUSH 15 ms -> first Push data 45 ms -> first decoder input 63 ms -> first frame 34,000 ms`

then the server delivered promptly and Android/player recovery is the primary suspect.

If:

`FLUSH 15 ms -> first Push data 27,000 ms -> first frame 27,500 ms`

then the server/transport path is the primary suspect.

If SageTV commands a seek but no expected FLUSH/rebase follows, investigate server/protocol semantics before changing Media3.

---

## 11. Server-side evidence to collect

Whenever server access is available, correlate server evidence with the exact matrix `caseId`, operation, wall-clock window, and preferably monotonic/client event timing.

Collect read-only evidence from:

### SageTV server

- SageTV log entries around playback start/seek/skip/flush.
- MiniClient connection/protocol events if logged.
- recording/media file being served.
- server-side seek target if exposed.
- Push flush/rebase/push-data timing if exposed.
- Pull OPEN/READ/CLOSE timing if exposed.

Do not guess server log filenames. Discover the active SageTV installation/container and identify its actual log files first.

### Docker/runtime, if SageTV is containerized

Read-only checks may include:

- container status;
- container logs for the test window;
- CPU/memory pressure;
- process list;
- disk/storage wait symptoms;
- network/socket errors.

Do not restart the container solely for diagnosis without approval.

### FFmpeg/transcoder

For Fixed/transcoded playback record:

- whether transcoding was requested;
- actual FFmpeg command line;
- process start/stop time;
- selected decoder/encoder;
- stderr/errors;
- output stalls;
- CPU/GPU utilization if relevant;
- whether the transcoder restarted during seek/rebase.

A decoder problem in the Android app must not be diagnosed from a case where the server transcoder itself stopped producing data.

### Comskip / STV / plugin

The MiniClient does not own semantic commercial marker positions.

If a Comskip operation jumps to an obviously wrong location, inspect:

- marker timestamps;
- STV/plugin command mapping;
- server-side right/left command behavior;
- recording duration/timeline;
- whether the command chosen is actually the expected Comskip action.

A wrong destination may be server/STV/plugin behavior even if playback recovery afterward is healthy.

---

## 12. Current known issue classification

These are hypotheses supported by current evidence, not permanent conclusions.

### A. Media3 Pull large Comskip recovery: high confidence client-side preroll/decoder backlog after server data arrives

The v0.5.66 controlled 4x timestamp-search experiment is now **evaluated and did not materially improve the problem**. Latest report: `20260828_010837_player_full_matrix.json`.

Observed large forward Comskip results:

- native Media3 Pull: **35,044 ms** recovery;
- GSY Auto/Media3 Pull: **35,534 ms** recovery;
- explicit GSY Media3 Pull: **41,606 ms** recovery.

For native Media3 Pull, the 4x run still consumed approximately the same amount of data as the historical 8x run:

- before seek: 17,301,504 bytes / 66 READs / 2,391 ms cumulative READ wait;
- after recovery: 66,846,720 bytes / 255 READs / 6,965 ms cumulative READ wait;
- operation delta: **49,545,216 bytes**, **189 READs**, only about **4,574 ms** additional server/network READ wait.

More importantly, the long-wait probes show that the datasource had effectively finished its post-seek reads by the 15-second checkpoint while Media3 stayed BUFFERING:

- 15 s: network counters already at their final 66,846,720 bytes / 255 READs; video queued-input count **1046**; rendered count **721**;
- 30 s: network counters unchanged; video queued-input count **1632**; rendered count still **721**;
- exact event ring: seek began around queued-input **380** / rendered **721**; first post-seek frame did not occur until queued-input was about **1722**.

This is stronger evidence than the earlier generic “extractor scan” hypothesis. The leading client-side hypothesis is now:

> The MPEG-TS seek lands substantially before a usable MPEG-2 sync/random-access point, and Media3/MediaCodec spends tens of seconds feeding/decoding preroll or decode-only samples before a renderable frame is reached.

The server is not currently the primary suspect for this specific delay because data delivery stops changing long before A/V output resumes.

Action:

- `[x] TEST` 8x -> 4x TS timestamp search: **no meaningful improvement; hypothesis weakened**.
- `[~] VERIFY` Restore the known 8x baseline before changing a second behavior variable, unless a later controlled experiment explicitly needs another value.
- `[ ] INSTRUMENT` Record a bounded datasource-open history around Pull seeks (open position, time, bytes/read count per open) to separate binary-search probes from the final long sequential preroll read.
- `[ ] INSTRUMENT` Expose decoder input/output skip/preroll counters that distinguish queued input, skipped input, decode-only behavior, dropped-to-keyframe behavior, and rendered output where Media3 exposes them.
- `[ ] TEST` After restoring 8x, evaluate one sync-point strategy change at a time. A strong candidate is Pull-only `SeekParameters.NEXT_SYNC` versus current `CLOSEST_SYNC`, because A/V recovery is the verdict and a slightly-late usable sync frame is preferable to a 35-second preroll. Do not combine this with another extractor/buffer change.
- `[ ] VERIFY` Confirm seek-to-zero, +/-10 s, large Comskip-right, and Comskip-left correctness after any sync-point experiment.

### B. Media3 Push direct-local seek watchdogs: mixed, test-path sensitive

Some MCP direct local `player.seekTo()` Push operations become permanently BUFFERING while real server-driven Push FLUSH/anchor/reprepare sequences can recover normally.

Do not rewrite normal Push seeking based only on a local MCP seek failure.

Required next evidence:

Capture a **genuine server-originated Push seek/Comskip** end to end and determine whether SageTV sends the correct seek/FLUSH/new-data/anchor sequence.

### C. Legacy Exo Pull large forward Comskip: currently unresolved because datasource telemetry is missing

The latest isolated run changed this from merely slow to a **50,579 ms watchdog expiration** for native Exo2 Pull Comskip-right. The player accepted the target (`serverRequestedSeekMs=985220`) and entered BUFFERING, but video queued/rendered counters stopped advancing after the seek.

Crucially, Exo2 Pull currently reports datasource READ/open counters as `-1`, so this run cannot distinguish:

- SageTV/server/socket READ stall;
- Exo2 datasource stall;
- extractor stall before codec input;
- player/decoder stall after data arrives.

Action:

- `[ ] INSTRUMENT` Give `Exo2PullDataSource` the same network/open/position telemetry currently available from `Media3PullDataSource`.
- `[ ] TEST` Repeat only Exo2 Pull Comskip-right after instrumentation and compare total recovery against server READ wait and codec queued-input movement.
- `[!] FIX` Do not change Exo2 player behavior until the missing transport evidence exists.

`BaseMediaPlayerImpl` and the frozen legacy baseline remain protected unless the user explicitly authorizes a legacy experiment.

### D. GSY System startup failure: probably Android backend/datasource, not proven server fault

The Android `MediaPlayer` System engine has produced generic/system player errors while other backends can play the same recording from the same SageTV server.

Current captured error pattern includes:

`what=1` / `MEDIA_ERROR_UNKNOWN`  
`extra=-2147483648` / low-level system error

Continue datasource/platform investigation before changing SageTV server behavior.

### E. Wrong Comskip landing: server/STV/plugin possibility is high

Marker ownership and semantic action selection are server/STV/plugin concerns. Playback recovery after the command remains an Android/client concern.

Treat these as two separate questions:

1. Did SageTV choose the correct destination?
2. Did the client recover A/V after receiving/processing it?

---

## 13. Upstream Android/Fire TV findings Codex must know

### AndroidX Media3 is authoritative

Project upstream:

https://github.com/androidx/media

The MiniClient uses Media3 **1.11.0**.

Current Media3 1.11.0 source already contains Amazon Fire TV async MediaCodec selection:

- API >=31: asynchronous MediaCodec adapter by default.
- API >=28 plus system feature `com.amazon.hardware.tv_screen`: asynchronous MediaCodec adapter by default.

Relevant source:

https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/DefaultMediaCodecAdapterFactory.java

Therefore:

- do **not** assume Fire TV is using synchronous MediaCodec;
- do **not** propose "enable async for Fire TV" without first verifying device API/system feature/effective adapter;
- an optional `auto|async|sync` override can still be useful for controlled A/B testing.

Recommended MCP telemetry:

- manufacturer;
- model;
- SDK/API level;
- Amazon TV system-feature detection;
- requested codec adapter mode;
- effective codec adapter mode.

### MPEG-TS timestamp search

Media3 1.11.0:

`TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES = 600 * 188 = 112,800 bytes`

Media3 warns that large timestamp search values can be inefficient because the extractor buffers/searches that many bytes during duration determination or seek operations.

Relevant source:

https://github.com/androidx/media/blob/1.11.0/libraries/extractor/src/main/java/androidx/media3/extractor/ts/TsExtractor.java

and:

https://github.com/androidx/media/blob/1.11.0/libraries/extractor/src/main/java/androidx/media3/extractor/ts/TsBinarySearchSeeker.java

Current MiniClient experiment:

- historical 8x = 902,400 bytes;
- v0.5.66 4x = 451,200 bytes;
- possible later 2x = 225,600 bytes;
- Media3 default 1x = 112,800 bytes.

Do one step at a time and preserve seek correctness.

### Amazon ExoPlayer port is useful historical context

Repository:

https://github.com/amzn/exoplayer-amazon-port

Useful history includes Fire TV MediaCodec workarounds and issue #124, where a Fire TV can remain permanently `STATE_BUFFERING` after seek calls:

https://github.com/amzn/exoplayer-amazon-port/issues/124

Do not replace current Media3 with Amazon's old ExoPlayer 2.18.7 fork merely because the issue looks similar. Use it to understand historical Fire TV failure modes, then verify whether modern Media3 already contains the relevant workaround.

---

## 14. Harness rules

### Full matrix

Full matrix may share one playback session across operations in the same case.

### `--issues-only`

Issue-only mode must isolate **each individual selected operation** in a fresh playback session:

`clean stop -> configure -> connect -> Search -> recording start -> verify A/V -> clear traps -> run one operation -> collect evidence -> stop`

This prevents one poisoned BUFFERING state from contaminating the next issue.

Preferred iteration command pattern:

```bash
./dev.sh mcp-player-matrix \
  --server <server-ip> \
  --text "<recording search text>" \
  --hardware-only \
  --streaming push,pull \
  --issues-only /workspace/artifacts/firetv/<previous-report>.json \
  --watchdog-ms 70000
```

A per-check playback startup failure is:

- `STARTUP_PLAYBACK_FAILED`, or
- `STARTUP_PLAYER_CRASHED`

not `INFRA_ERROR` unless MCP/ADB/control infrastructure itself failed.

---

## 15. Cause-attribution decision tree

Use this sequence after every abnormal operation.

### Step 1 — Did the Dev app/process crash?

Yes -> `PLAYER_CRASHED`, inspect Android crash/log/event evidence first.  
No -> continue.

### Step 2 — Did MCP/ADB/control fail independently of playback?

Yes -> `INFRA_ERROR`; fix harness/infrastructure before changing player code.  
No -> continue.

### Step 3 — Did the backend produce a player error?

Yes -> inspect exact backend error code and datasource/protocol state. Determine whether error follows malformed/no data from server or occurs despite valid data.  
No -> continue.

### Step 4 — Pull or Push?

For Pull, compare server/socket READ wait against total recovery.  
For Push, compare FLUSH -> first new Push data -> anchor -> decoder -> first frame.

### Step 5 — Was data available promptly but decoder/output did not recover?

Yes -> client extractor/player/MediaCodec/AudioTrack becomes primary suspect.  
No -> server/transport/storage/transcoder becomes primary suspect.

### Step 6 — Was the destination itself wrong but A/V recovered?

Yes -> investigate SageTV/STV/Comskip marker/command semantics separately. Do not call it an A/V recovery failure.

### Step 7 — Is the failing operation a direct MCP local Push seek?

Yes -> do not generalize it to server-driven Push behavior. Capture a real SageTV-originated seek before changing the normal Push architecture.

---

## 16. Required result format from Codex after a test run

For every abnormal case, report a compact table containing:

| Field | Required value |
|---|---|
| case | player / transport / decoder / GSY engine |
| operation | absolute, forward, backward, pause/resume, Comskip right/left, startup |
| status | RECOVERED / SLOW_RECOVERY / WATCHDOG_EXPIRED / PLAYER_CRASHED / STARTUP... |
| recoveryMs | measured wall time |
| server requested seek | target + timestamp/sequence if present |
| server flush | sequence/timestamp if present |
| server anchor | value/timestamp if present |
| Pull I/O | READs, bytes, readWait, errors, positions |
| Push delivery | FLUSH-to-first-data and bytes if available |
| Android state | BUFFERING/READY/isPlaying |
| A/V counters | before/event/final advancement |
| decoder | name + hardware/software |
| error | exact Android/player/server error if any |
| crash | yes/no + signature |
| attribution | client / server / transport / transcoder / STV-Comskip / harness / mixed / unknown |
| confidence | high / medium / low |
| next experiment | one smallest controlled change or evidence request |

Never report a confident cause without showing the timing/evidence that separates the layers.

---

## 17. Current next actions

Latest evidence source: `20260828_010837_player_full_matrix.json`.

### Priority 1 — Media3 Pull: stop treating TS search-window size as the primary cause

- `[x] TEST` v0.5.66 4x timestamp-search experiment completed. Native Media3 Pull remained **35.0 s**, with Media3-backed GSY at **35.5–41.6 s**.
- `[~] VERIFY` Restore the known 8x baseline before the next independent behavior experiment.
- `[ ] INSTRUMENT` Add datasource-open history and per-open byte/read timing.
- `[ ] INSTRUMENT` Add precise decoder preroll/decode-only/skip/drop counters if accessible without continuous high-overhead listeners.
- `[ ] TEST` Run a single-variable Pull sync-point experiment, preferably `NEXT_SYNC` versus current `CLOSEST_SYNC`, then verify seek-to-zero and both Comskip directions.

Why this is now Priority 1: by 15 seconds the native Media3 datasource counters are already at their final values, yet the video input count continues increasing from 1046 to 1632 by 30 seconds while rendered output remains frozen at 721. The first new frame only appears after roughly 1340 additional queued video inputs.

### Priority 2 — Exo2 Pull: add missing server/datasource boundary telemetry

The latest isolated native Exo2 Pull Comskip-right watchdoged at **50.579 s**, but all datasource counters are `-1`.

- `[ ] INSTRUMENT` Exo2 Pull READ count/bytes/wait/max-read/errors.
- `[ ] INSTRUMENT` Exo2 Pull open count/open positions/open wait.
- `[ ] TEST` Repeat Exo2 Pull Comskip-right only.
- `[!] FIX` No Exo2 playback change until the server-vs-client boundary can be measured.

### Priority 3 — Add correlated server evidence collection

The current client instrumentation is stronger than the server instrumentation.

Add a read-only collector keyed to matrix case/operation/time window for:

- SageTV server log;
- server/container process state;
- Push/seek/flush evidence where available;
- transcoder/FFmpeg command/log/process when applicable.

Do not let failure of an optional server collector make an otherwise valid client test `INFRA_ERROR`; mark server evidence as unavailable/incomplete instead.

### Priority 4 — Capture genuine server-driven Push seek

Current Push direct-local watchdogs remain clearly local-test-path events: `serverRequestedSeekMs` stays `-1` and server seek/flush/anchor sequences do not change during the local seek.

Use an actual SageTV UI/server command path, not a direct local player seek. Capture:

`server command -> MEDIACMD_SEEK if any -> FLUSH -> first Push data -> anchor -> reprepare -> decoder -> first frame/audio`

Only after this trace should Codex recommend changing normal Media3 Push seek handling.

### Priority 5 — Improve effective codec/device telemetry

- `[ ] INSTRUMENT` Expose device manufacturer/model/API.
- `[ ] INSTRUMENT` Expose Amazon TV system feature and effective sync/async MediaCodec adapter mode.
- `[ ] VERIFY` If API >=34, determine whether Media3's decode-only MediaCodec input flag is applicable to the observed preroll backlog; do not assume availability on Fire OS.

### Priority 6 — GSY System datasource investigation

Latest isolated Pull still fails immediately with `player_error_1_-2147483648`; Push creates `android.media.MediaPlayer` but never becomes ready. No crash is detected.

- `[ ] INSTRUMENT` System `readAt`, `getSize`, read duration, requested position/size, returned bytes, EOF and first exception.
- `[ ] TEST` Compare same recording as a local file through Android `MediaPlayer` to distinguish platform/container incompatibility from the custom SageTV `MediaDataSource` bridge.
- `[!] FIX` Do not start with a SageTV server rewrite because the same content/server works with Exo/Media3/IJK.

### Priority 7 — Harness isolation validation

- `[x] VERIFY` `--issues-only` fresh-playback-per-operation isolation is working. Latest run reports **0 infrastructure failures**; prior poisoned-session `INFRA_ERROR` behavior did not recur.

## 18. Code-change discipline

Before changing playback behavior:

1. Reproduce the problem in an isolated case.
2. Collect client exact-event evidence.
3. Collect server evidence when the failure could plausibly be server/transport-related.
4. State the current attribution and confidence.
5. Change **one meaningful variable**.
6. Rerun the exact same isolated case.
7. Compare before/after raw timing and A/V evidence.
8. Revert a negative/inconclusive experiment before starting another one.

Do not bundle Media3 extractor tuning, LoadControl tuning, Push behavior changes, decoder policy, and datasource size changes into the same experiment.

---

## 19. Validation before delivering a change

Run:

```bash
python3 -m unittest discover -s tests -p 'test_*.py' -v
python3 -m compileall -q scripts mcp/src tests
SAGETV_WORKSPACE=$PWD python3 scripts/validate_project.py
```

When Android Java/Gradle source changes, also run the normal project Android build on the supported Docker/WSL build environment before claiming APK validation.

When only host/MCP/docs change, state explicitly that no APK rebuild is required.

---

## 20. Packaging rule

By default, deliver only changed/new files in a small overwrite ZIP using project-relative paths.

Do not create a full project ZIP unless explicitly requested.

For diagnostic changes, update the relevant documentation so a future Codex session does not repeat already disproven hypotheses.

---

## 21. Core principle

The goal is not to prove that Android, Media3, or SageTV is at fault.

The goal is to build a timestamped chain of evidence that answers:

1. What did SageTV request?
2. When did the server provide data?
3. What did the MiniClient receive and report back?
4. What did the Android player do with the data?
5. When did the decoder and audio/video outputs actually recover?
6. If recovery failed, which layer first stopped making forward progress?

Only then should code be changed.

## 22. Evidence-backed code-change candidates

These are candidate fixes Codex may implement as controlled experiments. Do not apply all of them together.

### Candidate A — Direction-aware sync seeking for large Pull skips (highest value)

Current Exo2 and Media3 Pull code forces `SeekParameters.CLOSEST_SYNC` globally. The latest Media3 Pull report shows network reads finishing by about 15 seconds while queued video input continues increasing through 30 seconds with rendered output frozen. This is consistent with a seek resolving to an earlier sync point and forcing a very large MPEG-2 preroll.

- `[ ] TEST/FIX` For a forward Pull seek, test `SeekParameters.NEXT_SYNC`.
- `[ ] TEST/FIX` For a backward Pull seek, test `SeekParameters.PREVIOUS_SYNC`.
- `[ ] VERIFY` Keep seek-to-zero correct.
- `[ ] VERIFY` Compare Comskip right/left landing and A/V recovery separately.
- `[!] DISCIPLINE` Test first in Media3 only. If successful, port the same policy to Exo2.

Prefer a small shared SageTV Pull seek-policy helper after behavior is proven, rather than duplicating policy logic across both backends.

### Candidate B — Pull seek recovery watchdog/reprepare safety net

A Pull seek can remain BUFFERING for tens of seconds with no rendered A/V progress even after datasource reads stop. Add a bounded, seek-specific recovery state machine rather than allowing indefinite decoder/extractor churn.

- `[ ] TEST/FIX` Arm only after a user/server Pull seek.
- `[ ] CONDITION` If BUFFERING exceeds a conservative threshold (start with 8–10 seconds), no A/V output counters advance, no player error/crash is present, and the seek target is known, perform one controlled reprepare at the same target.
- `[ ] LIMIT` Maximum one forced reprepare per seek sequence to avoid loops.
- `[ ] TRAP` Record `seek_recovery_reprepare_before/after` and the reason.
- `[!] DO NOT` Apply this to normal startup or server-driven Push without separate evidence.

This is a resilience fix, not proof of root cause. Keep it behind an experiment/config switch until validated.

### Candidate C — Fix Exo/Media3 DataSource zero-byte contract violations

Both `Media3PullDataSource.read()` and `Exo2PullDataSource.read()` currently return `0` when their backing `BufferedPullDataSource` is unexpectedly null even when `readLength > 0`. Exo/Media3 DataReader contracts expect zero only for a zero-length request. A non-zero request returning zero can cause a spin or indefinite BUFFERING state.

- `[ ] FIX` Replace non-zero read with no backing source by a deterministic EOF/error path (`RESULT_END_OF_INPUT` when intentionally closed at EOS, otherwise `IOException`).
- `[ ] TEST` Add unit tests for read-after-close/source-null behavior.
- `[ ] VERIFY` Ensure close/reopen during a legitimate progressive seek does not create a false fatal error.

This is a correctness fix worth doing even if it does not explain the current 35-second Media3 case.

### Candidate D — Add Exo2 Pull datasource telemetry by matching Media3

`Exo2PullDataSource` lacks the counters already proven useful in `Media3PullDataSource`.

- `[ ] FIX/INSTRUMENT` Port open count/wait/position and READ count/requested bytes/received bytes/wait/errors/max request/last position.
- `[ ] VERIFY` Expose them through `PlaybackHealthProbe`/MCP in the same field names used by Media3 where practical.
- `[ ] TEST` Rerun isolated Exo2 Pull Comskip-right before changing Exo2 seek behavior.

### Candidate G — SageTV MediaServer partial socket writes on Pull

See the high-priority server investigation near the top of this file. Treat the default non-NIO `MediaServer.readFile()` single-write behavior as an evidence-backed server candidate. First A/B `use_nio_transfers=false` versus `true`; then instrument actual socket bytes written. Only patch the server after observing partial writes or a repeatable NIO-vs-default difference.

- `[ ] TEST` Completed recording, same player/tuning/Comskip, NIO OFF versus ON.
- `[ ] INSTRUMENT` Expected buffer bytes versus cumulative bytes actually written.
- `[ ] FIX` If confirmed, drain each buffer completely before advancing the file offset; handle blocking and non-blocking socket modes correctly.
- `[ ] VERIFY` Repeat the original stall and the small-skip recovery case after the server fix.

### Candidate E — GSY/System transport redesign if MediaDataSource is proven incompatible

The current System backend maps SageTV PULL/PUSH into `android.media.MediaDataSource`. Pull fails immediately with `MEDIA_ERROR_UNKNOWN / MEDIA_ERROR_SYSTEM`, and PUSH is fundamentally awkward because `MediaDataSource` is random-access while SageTV PUSH is sequential/non-seekable.

- `[ ] TEST` First prove whether the same recording plays from a normal local file through Android `MediaPlayer`.
- `[ ] INSTRUMENT` Add `readAt/getSize` timing and request traces.
- `[ ] FIX` If local file works but `MediaDataSource` fails, replace the custom MediaDataSource bridge with a loopback HTTP bridge that supports Range requests for PULL and a sequential/chunked endpoint for PUSH, or disable unsupported System transport combinations.
- `[!] DO NOT` Keep patching `MediaPlayer` state transitions if the transport contract itself is incompatible.

### Candidate F — Push local-seek guard

Direct local `player.seekTo()` on an existing Media3 Push buffer can strand the player in BUFFERING, while normal server FLUSH/rebase/reprepare paths recover.

- `[ ] INSTRUMENT/TEST` Capture one genuine server-driven Push seek first.
- `[ ] FIX` If SageTV always owns Push relocation via FLUSH/new anchor, prevent production Push code from performing an independent local seek on the stale Push stream. Treat the server rebase as authoritative.
- `[ ] TEST]` Keep MCP local-seek as an explicit diagnostic-only operation if needed.

Do not make this production change solely from direct MCP local-seek watchdogs.
