# OpenSageTV Vibe Android Client handoff

## RELEASE-001 published and independently verified (2026-10-08)

User approved related repository source updates and a new Android sideload
release. v0.5.101/versionCode2101110 Debug APK builds and passes package,
permissions/native inventory/signature inspection. SHA256
6777841db4782cc9728090f5bc52945a89de03d6cb30de55952efb89764012f7;
the signer is unchanged from independently downloaded v0.5.100 (dc531a93).
This retains the existing Dev sideload identity, not production-store signing.
Selected JVM suites: core63/shared93, zero failures; MCP161 tests pass. Required
CI-equivalent source contracts exposed stale settings/cadence/format/cleanup
oracles and missing host-analysis dependencies. Corrected82 focused and13
metadata/dependency checks pass; test-only NumPy/SciPy are pinned and isolated
from the SDK runtime. Exact release-source CI passed749 tests with one skip.

Non-Pro25/stock175 exact generated Pull startup and public Seek produce real
A/V and reviewed burned24.424s picture. First pause oracle incorrectly treated
GetPlaybackRate as pause state: stock returns myRate while paused. Corrected
pause-only121.457s proves stopped decoder clock and resumed A/V without changing
production code. Both groups restore113 preferences and original power; no
server restart/transcode or HDMI/speaker claim. Compact evidence belongs under
artifacts/results/RELEASE-001/qualification.json; completed raw/staging retires
recoverably to workspace deleteme. Final non-Pro is on Android Home with
forceStopped=true and zero175 contexts/clients. Old Fire OS still reports a
pending PID; do not infer active playback or restart/clear settings for it.

All12 existing repositories are pushed and green at their exact HEADs.
Android release: https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.101
Tag/source a541d0f4d259d1ff9ec58e65288bc8240081c0c8; all four public assets
independently downloaded and matched to GitHub digests/checksums. All1603
source files match the clean commit manifest. The post-release closure commit
changes documentation/manifest only; the published tag and runtime stay fixed.
Prototype Client Extension
source awaits the user's choice; its runtime gates remain open. Historical
archive remains local, SageMC visibility stays private, no plugin runtime/
catalog release is implied. Approved publication is complete; both orders306
remove RELEASE-001. Companion physical gates remain
recording-aware prerequisites, not work performed by this publication task.

Stock175 restart exception: user renewed four-hour permission2026-10-08
23:52:59 UTC through2026-10-09 03:52:59 UTC (10:52:59 p.m. Central), unless revoked sooner.
This replaces the expired2 p.m. window; stock Core/files stay protected.
Check clock/new user input before each restart; after expiry ask again.
Only plugin installs/updates remain authorized; no stock Core/files changes.

## Current resume point: DEVICE-002 complete with explicit limits (revision304)

DEVICE-002, TABLET-MIM-001 and GSY-CAP-001 are closed under the user's
best-effort matrix-disposition rule. Installed stream-verified Dev APK is
c62911ae76be7b3e861e0050af4cc6ea8a3e72ba9d0a2b0e94032f3f8839bf4c.
Native40 rows keep their original per-row APK provenance. Compact final results:
artifacts/results/DEVICE-002/matrix.json and owned-watch-recovery.json.

Both GSY delegates pass175 real Direct-only recovery61.328/64.402s,
232 owned2s CEA/seek/pause86.354/77.837s and175 unavailable-service ordinary
Fixed69.333/68.573s. Media3/legacy failure, normal owned controls, playing/paused
public API and latest nonzero/canceled-ticket gates pass. Fresh actual video
precedes the single final Seek; no ambiguous mutation replay or atomic Watch
claim. GSY resolves its selected delegate inventory and retains adapter control.
12 recovery/13 session/2 resolver JVM,65 affected source/backend/lifecycle/order
and15 MCP Python adapter tests pass, as do selected plugin/runtime/build gates.

232 fresh VAAPI h264_vaapi status confirms hardwareDecode/Encode=true;
intel_gpu_top unavailable, so no measured GPU-load claim.175500ms CEA stress
settled text remains malformed (NOT_WORKING, cause unproven);2s controls are
readable175/232. Original tablet UK IJK picture NOT_WORKING; native MPEG-2
DVD safely unsupported; optional transformed title controls pass. Physical
speaker A/V sync/HDMI presentation remain UNMEASURED, not software-counter PASS.

15 preferences per group and exact original power7/600000 restored. Original
175 caption listenerfalse restored/read back; both servers activeJobs/contexts
empty. Protected stock175 Core/root FFmpeg and232 Core/root FFmpeg/INI unchanged.
FFplugin efe2bb7b, MCP6fff42f0 and test Linux MIM6e80a2d9 remain installed with
qualified rollback backups; no new canonical Windows runtime or publication.
Temporary232 import/shared generated2s file removed, ignored case disabled.
366 completed/redundant outputs and staging324,482,575bytes retired recoverably
to rootdeleteme, preserving relative paths. Minimum unresolved175500ms/IJK
evidence and persistent warm cache remain active; no settings/key reset.

Both suggested orders304 remove the completed device. Continue only the next
unblocked authorized prerequisite, not an unrelated full matrix. Conditional
ONN-003/DEVICE-001 need actual affected evidence; VCE prerequisites live in their
own plugin backlog. Approved release packaging/publication remain separate.
Final task-order/source validator/manifests are the closure checks.

### Superseded commissioning notes (not current instructions)

### Revision302 qualification detail

170ad8e4 installed with15prefs restored. Both Direct+Pull68.390s and real
Direct-only59.769s fallback pass stock175 actual fresh H.264/final Seek, with
reviewed generated PTS1.335/4.538. Normal17578.483s owned A/V/FF/REW/pause
passes after moving HTTP seek outside UI progress-position lock.23283.770s
owned caption transport/seek/pause passes; independently reviewed settled
caption PTS23.5/24 at burned24.725 and fresh VAAPI hardwareDecode/Encode=true.
Both servers have FFPlugin efe2bb7b, MCP6fff42f0 and MIM6e80a2d9, protected
Core/root FFmpeg unchanged;232 INI unchanged and selects VAAPI.

CPU175 fast500ms caption transport91.165s passes state/wire but settled text
is malformed, NOT visual PASS. Generate120s2s current A53 control; ignored
TOML owns new enabled owned_cc_visual_2s case and root temp driver selects by
mode, never a magic ID. Generated temporary file/import must be retired and
case disabled after gates. Nonzero/paused/cancel custody, final cleanup/
provenance and original175 caption-listenerfalse restoration remain. No
full parent PASS, publication or broad matrix restart. Both orders302.

### Revision301 qualification detail

Forced Direct+Pull startup failure on175 passes68.390s on d9e9e500: fresh
ordinary Fixed, actual H.264 A/V, final captured Seek and independently reviewed
generated picture PTS1.335.15prefs/exact power restored. Normal74.794s exposed
a broadcast ANR while Direct HTTP seek held playbackPositionLock; the UI's
progress setter also takes that lock. Both Exo paths now execute owned HTTP
before that lock, keeping ordinary seeks and owner/session guards unchanged.
APK6ecfee4a build/inspection/install pass;63 affected source/order/lifecycle
and14 MCP adapter tests pass. Normal seek/pause rerun is active; direct-only
unsupported-track,232 GPU/caption and latest nonzero/pause/cancel gates next.
Both orders301, full TABLET-MIM-001/DEVICE-002 remain open.

### Revision300 deployment detail

Recovery API and Android fresh-Activity/latest-intent handling are wired.
Plugin-only175 FFmpeg/MCP JAR deployment passed public health; Sage.jar
d76ded98 and stock ffmpeg ff4289cd remain unchanged. Caption listener's
original false value was tested false/true/false through the bounded typed
MCP public plugin API; it is now borrowed true for active gates. Restore false
when testing finishes (watch-recovery-server-setting-checkpoint.json).

First tablet normal gate failed141.747s on APKda776596: old plugin MIM
forwarded -sagetvdirect/-sagetvdeinterlace into FFmpeg (exit8), and unsupported
MPEG-2 Pull reached audio-only READY with no player error. This is not evidence
of CPU/GPU starvation. Plugin-owned MIM/FFmpeg/ffprobe binaries now pass actual
175 execution/ABI preflight; MIM candidate6e80a2d9 advertises ownedDirectStreams.
Explicit plugin feature gating and both Exo unsupported-track recovery triggers
are undergoing affected JVM/build validation, not yet physically qualified.
Tablet still has da776596; install inspected latest candidate before rerun.
Normal owned playback, forced recovery/latest seek/pause/STOP, visual captions,
no orphan producers and232 actual GPU remain; do not claim full DEVICE-002 PASS.
Both orders300 retain this dependency order, not another broad matrix.

Use the owned Windows-to-container Python gate driver with paired Docker -w
/workspace/android-client and SAGETV_WORKSPACE=/workspace/android-client,
persistent ADB keys and explicit tablet/stock aliases. dev.sh is a host Docker
orchestrator, not an in-container runner; do not nest it inside docker exec.

### Superseded revision299 integration checkpoint

Plugin snapshot/intent/tickets/staged coordinator and injectable HTTP candidate
compile against stockJARd76ded98/Java8 and pass JDK8/11/17 guards. Bound4/120s,
exact owner/source/intent, cancel after claim/Pause, expiry/replay and listener
retirement are covered. CaptureIntent never reads decoder state/clocks during
OPENURL/SEEK. Stock Watch returns an asynchronous task marker; Pause belongs
only in the independently ready stage before Seek. Existing production HTTP
construction has no recovery service/routes; capability is not advertised.

Stock175/tablet644596ae public fresh-session Watch/42s Seek proof passes:
playing91.843s, paused98.834s, real Exynos H.264 A/V, reviewed burned PTS43.710s,
42.042s paused and46.947s resumed.15prefs/exact power restored; no live gate.
Core MCP drives only this controlled API test, not normal playback. Android's
ordinary Fixed Push clock resets at FLUSH while Core adds transcode seek offset;
use public source time plus actual burned-label pictures. Paused clock proof
must await the fresh FLUSH, not the preceding ready player. Earlier apparent
seek/pause failures were corrected oracle errors, not production-clock fixes.
Compact proof: artifacts/results/DEVICE-002/stock-watch-recovery-boundary.json.

Next: wire candidate service to production plugin and Android latest-intent/
fresh-session recovery, retaining safe ordinary Fixed until normal and forced-
failure gates pass. Validate175 stock first,232 actual GPU then captions,
seeks/pause/STOP, no orphan producers and exact settings. No MIM runtime/Core
change, new client capability, deployment, restart or publication this stage.
No full owned Transcode/DEVICE-002 PASS. Both orders299 current; no repeated
completed matrices. Stock175 restart permission through23:31:05 UTC, unless
revoked. Active compile staging /tmp/mim-watch-intent.ivvJj5 and helper
../../artifacts/temp/test_stock_watch_recovery_boundary.py remain for integration.

### Completed IJK disposition checkpoint

No live gate remains. Corrected MiniMX Breakfast Teletext183.970s PASS on
644596ae:67 new cues/20184ms in20s, gap1.697s, Off clear/re-enable, pause-clock
hold/resumed cue progress, readable independently reviewed184818 output.
32prefs/serverCC/power1/3600000 restored. TABLET-IJK-001 moved to checked ledger
with explicit original UK NOT_WORKING, qualified clean-tablet Surface lifecycle
126.416s/MiniMX original259.585s and no unqualified decoder workarounds.
Completed IJK raw252files/191,332,509bytes moved recoverably to rootdeleteme,
preserving relative paths; compact results and three minimal original-tablet
failure items retained. Open MIM recovery evidence/settings/keys untouched.
Historical raw paths now resolve under deleteme; no permanent deletion.
DEVICE-002 remains active for TABLET-MIM-001/full owned-support boundary and
final provenance/artifact retirement. Both orders294 current; stock175 restart
window expires19:00 UTC, no restart performed. No publication/commit yet.

### Superseded native-oracle checkpoint

Exec58376: same644596ae MiniMX175 IJK Breakfast Off/On20s/pause/15s final hold.
MCP now preserves already exported health_basicIsPlaying; caption_progressed
uses it only when health_probeSupported=false, requiring true/no error/new
nonempty visible cue. Exo requirement unchanged; no APK/runtime change.16
caption/first-frame and27 MCP health tests pass. Earlier243.129s Breakfast has
real68-cue/20097ms continuity and matching readable HDMI, then native pause/
PLAY resumes43s with new cues but test incorrectly used Exo-only defaultfalse.
Holby197.673/269.010s are inadequate-fixture failures: source82.688s ends after
advert, rather than proven renderer regression. Inventory report retained.
First corrected bootstrap21.347s failed before mutation; exact222 ADB reconnect
recovered same MINIMX/AM2/API23 and existing keys. Check final restoration/result
then close IJK best-effort disposition, update compact matrices and retire
superseded owned raw files. TABLET-MIM-001 full owned-support contract remains
unimplemented; safe ordinary Fixed and other40 tablet hardware rows remain valid.

### Superseded first caption checkpoint

Current installed APK on tablet/MiniMX is644596aefba787109472aca9a967e410e6b2ecc054247a8a323998f0ec6f625c.
MiniMX retry493.945s completed: valid rollback121062d7, current hash independently
verified and32 preferences restored. IJK original lifecycle259.585s PASS with
actual OMX.amlogic.avc.decoder.awesome and changing HOME/user-resume pictures,
3 exact replays, preserved manual pause, no crash/teardown;32prefs/power1/3600000
restored. Reviewed settled182012 also shows real picture. HDMI8s overlapped HOME
and is not sustained-video evidence. Tablet cleanMP4 Surface lifecycle126.416s
is physically reviewed PASS; unchanged UK original101.939s remains NOT_WORKING.

MiniMX Teletext197.673s: initially readable27 cue updates; Off/On times out45s
in a commercial break.32prefs/server CC/power restored. Exec62005 now repeats
same controls with120s cue budget and20s continuity; do not claim PASS unless
new readable cues, pause/resume and restoration actually pass. No runtime
caption change, no broader backend/matrix rerun. Stock175 restart window19:00 UTC
only; no restart performed. Next record result, reconcile compact matrices,
then close IJK best-effort disposition and investigate TABLET-MIM-001 boundary.

### Superseded transfer checkpoint (retained provenance)

644596ae original55018 FAIL101.939s: health_videoDecoderOMX.Exynos.avc.dec,
kindhardware1920x1080, firstFramefalse/clock45427;15prefs restored. Original
row remains NOT_WORKING. MiniMX75263 backup-only stopped95.867s/90s timeout;
partial /tmp/device002-before-ijk-u0mo0uy0/base.apk23,658,496bytes/SHA24caea7f
is NOT a valid rollback. No install/preferences mutation; actual remote APK
still121062d766db3c254fb1ccc0700ee433716ba43307485bfc7f9de9568aa3c8ca.
Metadata report moved to owning artifacts/active/DEVICE-003. Retry29486 active:
honor cfg.install_timeout_seconds (at least90s), verify fresh copy hash against
remote before mutation. Explicit artifact-dir now honored; expected-model
MINIMX/device222 guard. Once done, same644596ae affected IJK original
startup/STOP/HOME/TT only, no other backends/matrices. Reused tablet rollback
is known baseline566, deliberately not current intervening trial APK.
Tablet settings/power restored7/600000, both orders291 reviewed.

644596ae install23.398s/hash/15prefs restore; cleanMP4 lifecycle1161 PASS126.416s
with independently reviewed changing HOME174806/174809 and user-resume174819/
174822 pictures. Connection/user pause preserved,3 exact replays/no crash/
teardown;15prefs/power7/600000 restored. Unlike prior clock-only113.123s run,
the display is visibly restored. Original UK source/default options unchanged.
Next same-build original physical recheck via temporary driver, then affected
MiniMX IJK startup/STOP/HOME/TT on644596ae; no repeated broad codecs/delegates.
Both orders290 current, stock175 restart window19:00 UTC only; no restart yet.

bc6ab965 guard-only install21.152s/15prefs restored; strict inspection1145,
build27s/3 readiness/54 backend/26 MCP health/5 MIM policy JVM PASS. No retired
trial class remains in core JAR. CleanMP4 lifecycle13216 clock-PASS113.123s but
VISUAL_FAIL: initial173426/173430 show actual different video; HOME173441/173444
and user-resume173453/173455 blank brown while clock advances. Never count old
lifecycle oracle as hardware-picture PASS. Source proves IJK binds display at
load/STOP-resume only, no SurfaceHolder callbacks; native holder lost on HOME.
Candidate644596aefba787109472aca9a967e410e6b2ecc054247a8a323998f0ec6f625c
registers display-only holder observer, current player/session/holder guards,
valid Surface check, detach on destruction, invalidate/remove before release.
No seek/play/prepare from callback; stale events cannot touch replacement player.
Build24s/4 readiness-surface/3 TT/11 background/strict inspection PASS; guarded
install then same cleanMP4 physical pairs. General IJK readiness and Surface
code require affected MiniMX qualification too; no other codec matrix repeats.
Holby UK SD hardware TT gate92467 FAIL initial startup114.794s;15prefs/server
CC/power restored. Not TT feature PASS. Original UK startup limit remains.
Both orders289, tablet45219, stock175 restart window19:00 UTC current.

120bb6b9 normal-resume lifecycle36031 COMPLETE100.306s FAIL at initial playback;
15prefs/power7/600000 restored. No HOME/caption/picture success claimed there.
After bounded best effort, tablet UK1080i IJK hardware row is NOT_WORKING;
original-start partial positives cannot justify shipping a decoder workaround.
Removed owned prefix rewrite/probe retention/native start-seek/order/static
worker/read-reset code and its six passive bootstrap diagnostic fields. Seven
owned untracked trial sources/tests retired recoverably to rootdeleteme.
IJKPullMediaSource diff is empty; shared/native/default options unchanged.
Retain only !playerReady pending-seek guard (latest zero request otherwise native
rejected-3 and older bookmark replayed), actual first-frame/decoder diagnostics
and pre-existing commissioned Teletext retry.3 focused source contracts pass.
Build/inspection/guarded install followed by positive tablet and affected MiniMX
IJK startup/STOP/lifecycle/caption checks. Keep40 unrelated hardware rows, no
repeated original native-option sweep or dependency upgrade. Both orders288.
Tablet commissioned endpoint45219 current;175 restarts until19:00 UTC only.
Temporary six-file remux stage/import/private inputs can retire after final
compact limitation record; keep minimum native failure evidence until then.

9c6726b8 origin controls76866 COMPLETE172.531s/15prefs restored: initial plus
FF/REW/FF2/REW2 health/pictures, pause clock61620 stable/PLAY68222->72737 and
STOP/rewatch picture PASS. Replay seek0 native accepts0 instead of-3, actual
Exynos selected/no captured replay errors. Original normal-resume lifecycle
58860 FAIL100.728s at initial playback; no HOME stage reached,15prefs/exact
power7/600000 restored. Do NOT call this a fully corrected original.
Next candidate120bb6b9c16e21623d58b732aed5e4455548a786959a4edadb01f5799066a308
uses existing seek-at-start before decoder packets, captures pending Core
position and skips duplicate prepared callback seek unless newer request wins.
Exact completed tablet tuple only. Build24s/5 guard/11 background/6 scheduling
tests/strict1145 inspection PASS; guarded install then saved-position lifecycle
with opt-in --capture-phase-images. Both orders287 reviewed;175 restart cutoff
user-extended19:00 UTC, no175/Core/native/library/key changes or publication.
Guarded install completes21.872s/independent120bb6b9 hash/15prefs restored.
Only the commissioned tablet's ignored TOML serial is updated to user-provided
45219 after physical verification; active device/server aliases unchanged.
This is current endpoint maintenance, not a temporary gate selection override.

Combined86ee20a4 gate1815 COMPLETE193.024s/15prefs restored but FAIL overall.
Initial/FF/REW/pause/PLAY picture exists; FF2 sampled position182761 unchanged
then REW2 recovers. STOP-rewatch FAIL with Exynos error. Native replay log proves
start/seek0 rejected(-3) BEFORE prepared; later callback seeks stale119349.
Client seek() checked PLAY but not playerReady, discarding newer pending seek0.
Candidate9c6726b8e0e95e09785ff9b8c23fc4eb59a9aed0eb69ba917c4911054648d004
queues newest seek until prepared; commissioned static tuple enqueues it before
render start.4 source guards/build26s/strict1145-entry inspection PASS; guarded
install and exact same original controls follow. Helper now waits existing30s
recovery before screenshots, so fixed6s sampling is not a false stall verdict.
After tablet success run affected MiniMX IJK startup/STOP gate for readiness
guard; no repeated codec/delegate matrices. Both orders286; no Core/native/key
change or reliable full-original correction claimed yet.

Fresh3c806417 original test23499 COMPLETE91.036s/15prefs restored but FAIL:
actual Exynos errors0x8000100b/firstFramefalse despite retention1/filter991.
Earlier19b pictures are NOT reliable/full-control correction. Recovered owned
read-reset source/test from rootdeleteme; scoped absolute readAt correctness
returns alongside prefix protection/native retention. Original source/shared
buffer unchanged. Ten JVM/3 guards/build38s/strict1145-entry inspection PASS;
candidate86ee20a428478fed1224797784321949f7cd016cb530308a131f967ba1a23518
guarded install follows, then original controls. Temporary driver now calls
established clear_restored_playback before exact Watch, so delayed server resume
cannot race the intended fixture. No Core/plugin modifications or publication.
Tablet45219 connection/settings recovery below is current. Both orders285
reviewed; DEVICE-002/TABLET-IJK-001 remain open.

User provides new endpoint192.168.10.51:45219; dev.cmd connect with per-command
SAGETV_ADB_SERIAL override verifies actual SM-P610/API33 and persistent keys/
non-expiring authorization. All15 interrupted prefs and exact power7/600000
restored independently through same tablet; old42491 power checkpoint retired
recoverably to rootdeleteme preserving original path. Ignored active aliases
unchanged: always supply45219 override for this current tablet endpoint.
Guarded3c806417 candidate install/control qualification follows. Earlier
offline state below is history, not current restoration status. Both orders284
advance to original controls/lifecycle/captions; no repeated broad matrix.

Wireless ADB192.168.10.51:42491 dropped during candidate19b27669 original controls.
Device still pings; wrapper connect, scoped SDK devices/mdns and TCP check find
no working original port. Current Wireless debugging port requested from user.
Do not reset ADB server/keys or alter ignored active aliases. Interrupted run's
15-pref/power restore is NOT verified: restore persisted checkpoints first after
reconnection. No final report survived initial driver's cleanup exceptions;
reviewed captures show actual initial/changing video and FF/REW picture only,
not a full-controls PASS. Exact444942585142175 context already absent per Core
MCP read-only verification; no other context touched. No175 restart performed.

Emptyfflags trial175bd3ec fails before demux93.233s; numeric0L fixes that option.
a1a19c59 combined prefix/retention/read-reset original PASS real picture51.344s,
clock5343->12603/retention1/filter991/reset7/no captured codec errors,15prefs
restored. Retention-only0aa69f33 FAIL90.838s/15prefs restored. Narrowed prefix+
retention19b276698460cfcb40ba8b303e2352f707f00d6db9c7edd5d6903f2982fea7f5
installed18.22s/hash independently verified/15prefs restored. Reviewed captures
160250/160258 change, FF160304/160308 change, REW160314 visible/timeline25s;
ADB loss occurs before rest of controls. Read-reset workaround source/test
retired recoverably; static tuple/2MiB prefix protection plus native probe
retention remain pending full original seek/pause/STOP/HOME/TT/clean-prefix gates.
Async prepare now catches runtime failure on guarded UI callback; latest built
APK is newer than installed19b candidate and needs guarded reinstall.
Temporary driver now writes intermediate reports and preserves cleanup errors.
57 affected source tests and27 MCP health tests pass. Final compile25s/eight
parser/tuple JVM tests PASS. Current built APK SHA
3c806417430c1f42e23b3922dfb38306e9bdd09da58c92b2adffd1f7a8d93214
is NOT installed: tablet remains last stream-verified19b27669. Final strict
inspection PASS1145 entries/unchanged debug signer;3 caption-discovery and4
first-frame contracts also pass. Manifest regeneration is independent of the
unavailable physical device. Power checkpoint is indexed by old endpoint
192.168.10.51:42491: do not lose it or assume a new port's state_path finds it.
After verifying same tablet identity, restore its exact old checkpoint values
through the new endpoint before beginning another test; never clear app data.
Both orders283 reviewed unchanged. TABLET-MIM-001 remains next, not closed.
Rollback566/private six-file stage/import/input copies retained until qualified.

### Prior native-probe retention trial (revision281)

d04 original47917 COMPLETE90.95s/15 prefs restore: bootstrap991/state4 plus41
absolute-read resets active, but firstFramefalse. Local1000ms recovery7663 also
FAIL93.315s/15 prefs restored: clock9677 while picture remains blank. Core Watch
fromBeginningAppliedtrue/absolute seekTarget1789123215893; do not assume it
landed correctly from that acknowledgement alone. AVC+AC3 copied controls79723
COMPLETE89.544s/15prefs, both independently visible picture/Exynos+AC3. Matched
post-IDR VCL/SPS/PPS/relative timestamps2272 packets/4544 VCL, no transcoding.
Additional subtitle mux/probing remains suspect, not a proven root cause.
Native FFmpeg source verifies nobuffer discards packets used by stream-info
probe; extra subtitle streams can prolong that phase. Candidate175bd3ec now
preclassifies exact eligible source on dedicated IJK-static-bootstrap loader;
only resolved-not-growing opts out of nobuffer, preserving initial valid packets.
UI callback session/player/source guarded, late obsolete source closed, no UI
MediaServer I/O/other tuple/growing-option/native-library change. Builds25+20s,
3 passive/loader guard tests/3 TT discovery/54 backend tests/inspection PASS.
Candidate SHA175bd3ecccd2ea2e7e4a61c83685be089a87932be712eb74706e8e10974a4c86
guarded install43466; original-only physical trial follows. Inspect actual
health_ijkProbePacketsRetained plus frame/captures/errors. This is a hypothesis,
not a full-original correction. All rollback566/stage/private inputs preserved.
Both orders281 current; MiniMX already closed, TABLET-MIM-001 remains next.

### Previous absolute-read candidate (revision280; insufficient original fix)

Original-only11196 COMPLETE90.647s/15 prefs restored: actual bootstrapRequested1,
ProbeState4, filtered991, firstIDR200032; still firstFramefalse/Exynos errors.
Thus two-pass prefix activation alone is not a full-original correction. New
debug fields survive MCP whitelist;27 health tests and2 passive-getter tests pass.
Found executable JNI adapter mismatch: BufferedPullDataSource intentionally
advances sequentially even when position repeats; IMediaDataSource.readAt is
absolute. A toy payload proves read(0) yields0 then1; explicit buffer reset makes
the repeated absolute request return0. Shared legacy buffer/tests unchanged.
Scoped IJK adapter (same eligible completed hardware TS tuple) resets on any
nonsequential native read, records passive reset count; growing/other devices
unchanged.10 JVM tests (8 parser/tuple +2 buffer/policy), build27s PASS.
Candidate d04b6d64b1443a25ed573c596e58892af1af3bc82873b496ba4f54fd6da8e7a4
inspection/guarded install active; actual original picture/controls required
before retaining runtime candidates. Rollback566/roottemp unchanged. Both
orders280 reviewed; no stock/Core/native-library/publication change.

### Previous prefix-only physical evidence (revision279)

60ab trial53280 completes171.845s/15 prefs restored. Broken-prefix video-only
clip visibly renders (first/settled reviewed), original still blank15 Exynos
errors. No full original correction claimed. Bounded2MiB private offline probe
finds first parser0/original vs990/control because original PAT50196/PMT52640
arrive AFTER video. Two-pass valid-PSI resolution then re-examines early AVC;
supports initial missing-PES continuation only with a later full SPS/PPS/IDR,
rejects ambiguous/mutating programme/PID, bad/fragmented PSI, other codecs,
encrypted packets and open-GOP parameter starts. Offline original now991
filtered video packets, IDR byte200032/PID512; control990/188188/PID256.
Only source delivery's undecodeable video prefix nulls; unchanged source file,
byte positions/SIZE/audio/captions/timestamps/first IDR onward.
8 JVM/54 backend/2 passive-diagnostics contracts/build/inspection PASS.
Candidate5c27e21ba803ce739045435b96b707d8c54d6a77b2b784e1b0695308744ff32f
guarded install71497 COMPLETE32.778s/installed SHA verified/15 prefs restored.
Actual trial82642 COMPLETE176.462s: control pictures pass, original still blank
15 codec errors;15 prefs/power restored. Debug field whitelist was missing in
MCP's _compact_state, so those first public snapshots lacked activation proof.
Bridge whitelist added and passive source/bridge tests pass;26 MCP health tests
pass with explicit PYTHONPATH (first invocation without it was tooling-only).
Single original status/capture gate11196 active on unchanged installed5c27e21b,
not another full matrix. No full original correction PASS. Debug health_ijkBootstrapRequested/ProbeState/
FilteredPackets/FirstIdrOffset allow actual activation proof without I/O/locks.
ProbeState0 pending/1 ineligible/2 growing/3 no pattern/4 filtered/5 probeIO.
Next original/prefix/positive pictures, then actual seek/resume/TT/lifecycle
before closure. Rollback566 host roottemp and container temp retained. No
Core/stock175 file modification, transcoding, native replacement or publication.
Private raw inputs: rootartifacts/temp/device002-prefix-{original,control}.bin;
container /tmp/device002-prefix-check.rTYHlp holds two binary copies plus one-use
Inspector .java/.class. Retire these helpers/samples after correction; no binary
payload printed/exported. Both orders279 reviewed.

### Earlier single-pass candidate (revision278; incomplete original coverage)

Native1ms seek candidate trial72285 completed214.242s, original/prefix still
blank;15 prefs restored. Initial potentially-growing hint prevented proven
activation, so do not conclude the native option itself cannot work. Its runtime
option removed. Replacement60ab1bc8 uses IJK's loader-classified not-growing
boundary instead of guessing from OPENURL on UI. Exact SM-P610/API33 hardware
native TS Pull only;2MiB maximum probe accepts CRC-valid single-packet PAT/PMT,
AVC PID, leading non-IDR/no parameters, then a PES starting full SPS/PPS/IDR.
Only initial video TS packets are replaced by same-size null packets. File SIZE,
byte positions, all other PIDs/times and first complete IDR onward identical.
Unknown PSI/other codec/encrypted/valid or open-GOP starts no-op. IOException
in optional probe falls back to ordinary source behavior.6 JVM/54 contracts/
build28+21s PASS; strict inspection/guarded install active. No physical correction
PASS yet. Original rollback56603387 remains host roottemp and container systemtemp.
Retain stage/import/private captures until actual picture/control proof.

### Previous native-option trial (revision277; withdrawn)

Controlled follow-up75382 COMPLETE208.791s/15 prefs restored: original-PTS
clean-IDR TS shows real changing picture; normalized TS retaining11 incomplete
initial packets and full original multiplex are blank (15/6 codec0x8000100b).
Exact coded hashes/post-IDR relative timings match, confirming bootstrap
boundary rather than generic1080i/MBAFF, container or absolute-PTS inability.
No production fix proven yet. Narrow c8323282 trial uses existing IJK0.8.8
seek-at-start1ms only for SM-P610/API33/hardware/native TS Pull/not growing.
Two JVM/54 contracts/build57s/inspection1145entries PASS. Install94765 completed
24.432s/matched installed SHA/15 prefs restored; real pre-trial APK56603387,
not historical67e. Rollback host rootartifacts/temp/DEVICE002-pre-ijk-bootstrap-base.apk
SHA566033871b1323151a7d65e6e032fa1abe7cc9912c88e813998a1068a6d6f8fc;
also system temp /tmp/device002-before-ijk-1_31kp1f/base.apk in reusable container.
Physical72285 active same3 controls/captures. Verify option trap applied and
actual pictures, then resume/seek controls before retaining the source change.
If ineffective withdraw runtime candidate/restore original APK; do not claim
fix or native dependency upgrade. Temporary private test stage/import remains
needed; no cleanup until investigation complete. Both orders277 reviewed.

### DEVICE-003 closure and tablet setup history (revision276)

MiniMX final stock175 native fallback142.159s passes title Select, pause/play,
ff_2/rew_2 A/V recovery with unavailable_stock_fixed/native MPEG2 (not owned
Transcode). The previous187.356s caller tried Pause on looping root, corrected.
Owned232 DVD Transcode195.607/Copy134.345/hybrid180.557s passes real relay and
menu/return/control recovery. APIs show zero caption/Direct sessions after
teardown.32 prefs/server CC/power1/3600000 restored. Temporary clean fixture
imports removed on both175/232; only exact SHA-verified generated file and empty
directory removed remotely.149977940byte local fixture recoverable in root
deleteme/artifacts/temp; ignored case disabled. Compact matrix/task ledger retain
results. Native reverse-8 NOT_WORKING/unproven cause, unsupported HW and IJK CC
boundaries remain honest limits, not fixes or passes. ParentDEVICE-003 closed
at276; no broad rerun/Core/stock175 file modification/commit/publication.

Tablet read-only readiness PASS:51:42491 SM-P610/API33 using persistent container
keys/wrapper. TABLET-IJK-001 same-IDR stream-copy TS/MP4 prepared on shared
TABLET_IJK_BOOTSTRAP task stage using existing232 /usr/bin FFmpeg8.0.1, NO
transcoding. Both2272 packets/4544 VCL NALs, ordered VCL hash007ca26924cdf9e9,
SPSdcf875f7/PPS e3e9a505 match; complete first SPS/PPS/IDR, exact relative
PTS/DTS and firstPTS7200/DTS0. Identical1080High4.0/tt/MBAFF preserved.
Compact report artifacts/results/DEVICE-002/ijk-bootstrap-remux.json includes
commands/counts/hashes, never raw coded bytes. Video-only cannot prove audio/CC.
Stock175 exact task import added/scanned via CoreMCP; ignored-TOML cases
documented/enabled. Comparison20297 COMPLETE88.177s: TS and MP4 both show
changing actual video (all4 captures independently reviewed) and real Exynos
AVC decoder.15 prefs/power restore. This does not prove a production fix:
video-only/remux also normalizes timestamps/removes initial incomplete packets.
Controls75382 active: complete-IDR/original absolute timestamps, normalized
TS retaining11 initial non-key packets, then unchanged original multiplex.
New post-IDR coded hashes/relative timing checked by77586 verification; its
final result must be read before attribution. Two snapshots per clip and actual
codec logs, no clock/callback-only visual PASS. Remove only task-owned stage/
import after investigation. Then TABLET-MIM-001 proven stock plugin runtime
boundary. Preserve existing
40 hardware/posture/caption/DVD rows and settings, no overlapping device gates.

### Revision275 progress (superseded by current resume point)

MINIMX-MIM-001, MINIMX-MIM-002 and owning plugin MIM-DIRECT-004 are CLOSED.
Original long Copy224.062s Media3/247.007s legacy passes unchanged budgets.
Clean2s CEA owned Transcode200.781s has readable CC1 initial/post-seek and Off
clear, actual seeks/pause/strict ownership/fresh VAAPI decode+encode; CC2 blank
expected because fixture has one608 channel/708 service1. Owned legacy TT
236.949s has readable STV text/no local duplicate; controls162.469s and stock
Pull148.187s also pass. APIs/process inspection proves zero caption/Direct
sessions and no FFmpeg/MIM/transcoder processes. All32 prefs/server CC/power
restore. Installed APK121062d7/plugin232e62bee77 unchanged. No broad reruns.

Optional DVD gate9050 ended204.117s on invalid caller Skip_Fwd/Skip_Bkwd
commands, not a proven playback defect; captured native playback alone is not
owned proof. Corrected61857 runs transformed_main_feature/skip menus using
actual ff/rew commands, completed195.607s PASS active/full_gpu with4,268,728
bytes out and visible authored title. Copy13021 completed134.345s PASS active/
copy3,262,464 bytes out. Hybrid selectors59232 completed180.557s PASS:ff_2/
rew_2/audio/subpicture changes/toggle recover A/V, menu switches native and
return reactivates owned full_gpu7,418,104 bytes out. All32 prefs/power restore.
Stock175 fallback77436 failed187.356s because it tried Pause on looping native
root menu (highlighttrue), not title. No app error; unavailable_stock_fixed
and nativeMPEG2 were exposed honestly. Corrected invocation selects title
before Pause/Play and ff_2/rew_2; active guard restores32prefs/power afterwards.
Temporary clean2s fixture import removed through supported API on BOTH175/232,
existing import roots untouched.164 corrected/completed raw124435274bytes
retired recoverably under rootdeleteme; compact matrix preserves cause/results.
Remaining fallback/fixture/artifact closure precedes tablet.
Both orders275 updated; no publication/commit. The four-hour175 restart
exception above overrides historical 'always ask' notes only until expiry.

## Previous progression: revision274 (superseded by current resume point)

The user's Firestick Pro exit was collected read-only on `.29` before any
relaunch/install. At2026-10-08 06:08:07CDT Fire OS reports Wi-Fi disconnect;
both Media/GFX sockets abort at07.703, immediate reconnect gets ENETUNREACH,
and GFX logs abort at07.767. Android records app-request Activity finish at
07.789, return to startup, then Home at06:13:09. Process3769 remains alive;
no new Vibe fatal/native/ANR exit. Fire OS reports4332ms network interruption.
Pro installed0.5.100-DEV-DEBUG lastUpdate2026-10-05 07:34:23, not this session's
MiniMX candidate. The newest crash-buffer entry is unrelated Amazon Settings
on10-06. This diagnoses network-loss teardown, not a Comskip/audio/decoder
crash. Exact AP/radio cause remains unknown. No Pro settings/runtime/source
were changed. Evidence: artifacts/active/PRO-EXIT-20261008/111546; compact
result artifacts/results/PRO-EXIT-20261008/diagnosis.json. Current GFX retry
only retries rejected type5 (null); thrown connect failures bypass it, and
media worker independently closes connection on reconnect exception. Do not
claim that increasing only GFX retries would fix this paired teardown.

MiniMX install73698 completed219.46s successfully,32 prefs restored. Latest
candidate SHA121062d766db3c254fb1ccc0700ee433716ba43307485bfc7f9de9568aa3c8ca
WITHDRAWS ineffective069 fresh-HLS-source recovery (230.514s physical FAIL).
Retains legacy HTTP observer and bounded diagnostics only.14 session JVM,
54 backend contracts,34 caption oracles,26 MCP health tests/build/inspection
pass. Independent installed/built SHA matches121062d7; original long Copy
reproduction33339 fails231.986s,32 prefs/server CC restored. Actual failed
resource is CURRENT session SEGMENT9, http_404_media_not_ready, pos18924ms;
not a retired session, boundMediaItem=current. Copy Off-On continuity13 cues/
20525ms/3.155s gap passes before seek. Focused repeat60494 FAIL235.280s,
32 prefs/server CC restored; Off-On14 cues/21021ms/1.914s gap passes.
Read-only210s witness76039 COMPLETED and proves provider file-lifecycle defect:
sessionHash39699d713ddf segment9 is open/not deleted at1791458917050,
then open/(deleted) at1791458933774 while playlist still lists only0..8.
Producer later lists segment9 at1791458944200 but pathname remains absent.
This explains current-session404 media_not_ready; not stale client binding.
Provider cleanupUnlistedSegments's15s age-only deletion can unlink an
unfinished active segment; retry cannot recover deleted bytes. Protect
not-yet-published writer segments, while preserving cleanup of genuinely
retired segments, behind focused Linux/Windows unit and physical Copy gates.
User authorizes necessary plugin fixes/tests/updates as a standing rule.
Root plus all14 projects' AGENTS/WORKFLOW now share the same task-fix policy;
12 regression tests cover consistency/client-plugin-first/232-last-resort and
175 protection. MIM-DIRECT-004 cleanup correction implemented: only retire
aged files below lowest published index; protect future/referenced/empty
playlist cases. Stock-Sage.jar build/plugin/session/HTTP/caption/launcher tests
PASS. User confirms idle and adds standing232 restart authorization;175 ALWAYS
requires a restart approval. Both rules propagated to all14 workflows/agents.
Installed minimal232 overlaye62bee7731bbd0202fe3ceb2dafb12a84134db06a5b5331d100b0035eb949cbd,
only DirectSession outer/Session class bytes changed fromd2fd13c0, original
non-jar backup-direct-cleanup-20261008 recoverable. Named container restarted,
expected232 IP/API health/zero sessions verified; Core dc6891c8/stockffmpeg
bdf6aabf/liveINI4a5f3e78 unchanged. Original long Copy Media3224.062s and
legacy247.007s PASS; each20s Off-On14 cues, FF/REW5439/10571 and5937/11648ms,
real pause/resume/strict HTTP and32 prefs/CC/power restored. Provider9 exists,
18 listed/no missing files. MINIMX-MIM-001 CLOSED/moved into ledger. Current
14357 runs owned Transcode on configured120s/2s CEA visual fixture, full-GPU
deinterlaceOff, stock CC cycle/seeks/pause. Inspect captures for readable text;
no visual PASS claimed from wire alone. Then TT visual/fallback/DVD/no-orphans.
Stock175 legacy Teletext smoke148.187s PASS32 prefs/server CC/power restored;
cropped still reads 'Wide distribution anyway, you can have over200 kilometres
between'. Owned legacy Teletext162.469s passes ownership/event22579events/
21600bytes/idle-independent clock/FF7272ms/REW5263/pause/settings/power; its
single post-seek still is blank, not visual failure proof or readable PASS.
Follow-up caller used invalid --stv-state abbreviation and exits2 before
playback,21.803s/32 prefs restored; configuration only, not runtime failure.
Use real --cycle-stv-caption-states with bounded holds for visual follow-up.
Both orders274 advance remaining affected gates. No broad
rerun/publication, no test overlap with a server restart.
Unique witness minimx-copy-open-segment-witness.json must not be overwritten.
No test running. At required other-repo permission handoff, MiniMX manual
awake owner ended/restored exact captured stay_on_while_plugged_in1 and
screen_off_timeout3600000. Begin a new awake owner before resumed tests.
Completed one-use segment observer moved recoverably to root
deleteme/artifacts/temp/observe_minimx_segment_files.py. Its unique JSON
evidence remains active. Four copied older Pro traces ended before the actual
Wi-Fi exit, so retired recoverably; actual logcat/session/exit evidence stays.
New health_directErrorSession/Asset/Code/SegmentIndex reads the
actual existing exception dataSpec/url and whitelisted404 error body; no raw
URI/token/body export or extra network request. Actual boundMediaItem=current
on previous202.560s failure; actual resource attribution now proves segment9.
Power baseline is restored. Remove task-owned DEVICE003_CC_Visual
import path on BOTH175/232 at eventual closure, preserving existing imports.
Both orders274 keep remaining affected Transcode/legacyTT gates first,
remaining focused gates then tablet. No broad matrices/commit/publication.

### Historical progression below (superseded by the resume point above)

Newest experiment source/installed069beff8a24f210ef07e5425850aec76a8302c93a31d92421a4faeca29112ba9:
Media3 owned-Direct error recovery recreates only the actual current bound HLS
source/tracker, using captured relative position/play intent and existing retry
budget; no producer/SEEK/offset/default changes. Requested caption slot remains,
applied selection resets for its new group. Factory callbacks generation/player/
session guarded.32s build/55 contracts/strict inspection/214.634s install and
32 prefs restoration PASS; physical longer Copy experiment active. Withdraw
these Media3 recovery methods/test if ineffective, rather than ship speculation.
Legacy equivalent is NOT implemented pending Media3 proof.
bd77 installed217.253s/hash verified;202.560s longer Copy still fails. On-demand
snapshot proves health_directMediaItemSession=current at HTTP404/18703ms/seek57447:
not cancelled/stale rebind. Child tag unavailable is not proof of stale HTTP.
Temporary dedicated DEVICE003_CC_Visual import path added through public Core
MCP on232 and175; files resolve379913/65886221. Remove only that task-owned
exact import path on BOTH at final closure; do not change pre-existing imports.
New folder was created by this task, no prior user work. Remote SHA matches
81ddc86d9f390f6a714ee83b0f3b39925c9aeae3700dac8e307517540d2888f7.
The optional Sagex import-list read failed404 on232; no search fallback or
Core/Web-server changes, actual import/scan/resolve uses the stock MCP boundary.

Current sourcebd77ced5c1c487258b564b045ed451d979b345e03e611b4bb29355653d2fab25:
12s build/strict1145-entry APK inspection/54 contracts/26 MCP health tests PASS.
Install72552 active, private32-prefs checkpoint; wait for success/restoration and
verify installed hash, no overlapping install. Then reproduce longer Copy and
inspect health_directMediaItemSession (separate from unopened HTTP child).
Prior2d installed226.201s/32 prefs restored;206.068/206.719s repetitions fail.
Provider observation JSON shows new58469ms session stalled25s at17795ms playlist
duration then resumed26315/34715ms, still ready. No proven decoder/Core cause.
Observer helper root artifacts/temp/observe_minimx_direct_provider.py is read-only;
its120s run ended, do not overwrite the unique observation report on another run.
Bounded compare diagnostics never expose URLs/tokens; MCP whitelist now retains
both tags (26 tests). Session/live gates accept real legacy HTTP, not Pull
(33 caption tests). New regenerated120s/2s fixture149977940bytes/3595 A53 packets,
SHA81ddc86d9f390f6a714ee83b0f3b39925c9aeae3700dac8e307517540d2888f7 uploaded
to /var/media/OpenSageTV_Vibe_Tests/DEVICE003_CC_Visual/DEVICE003-CCVisual-120s-2s.ts;
configured enabled device003_cc_visual_2s. Public import/clean visual gates pending.
Both orders268 target shared failure first, legacy Teletext/stock smoke next,
remaining fallback/DVD/no-orphans/power/cleanup then requested tablet. Manual
awake remains owned/active, current32 preferences restored after every group.

Observer1bb354cd installed221.635s/hash independently verified;32 preferences
restored. Legacy longer Copy208.361s now proves real owned HTTP/13 new DVB cues
after Off-On, but FF again stalls playlist/404, reproducing MINIMX-MIM-001 on
both Exo families. Not merely Media3 or an ownership-oracle error. Added bounded
health_directSourceSession debug attribution (current/retired/foreign/inactive/
unavailable, no URL/token export); source2d587433 builds55s/13 session+four
observer JVM tests/31 caption authority tests PASS. Strict APK inspection passes.
Diagnostic candidate install51219 active, private32 preference checkpoint;
then repeat longer Copy while reading provider playlist/session/job concurrently
without client control replay. Base/server/keys unchanged. Manual awake active.

MINIMX-MIM-002 added: legacy Copy121.891s real211 frames/active_copy but blank
health source/ownership. Equivalent legacy segment-only HTTP/Teletext observer
candidate compiles68s/four JVM tests/53 source tests PASS; inspect/guarded install
next, then legacy owned DVB/Teletext controls and stock legacy smoke. Direct
Gradle must explicitly select /opt/java/jdk17; image default JDK11 is for Core.
Media3 narrower Copy143.716s passes FF6638/REW6980/pause/readable DVB screenshot;
original longer Off-On playlist stall remains open, not a successful retry fix.
Both orders267 target observer validation then original failure/remaining scope.

User2026-10-08 says wizard done/continue without stopping. Actual232 Main Menu/
automationReadytrue/connected/public matching-context SageTV7/CCOff verified.
Manual MiniMX awake remains active. Copy144.367s failure was omitted explicit
DVB under STV CCOff, not runtime proof. Corrected229.491s passes14 cues/21030ms
Off-On continuity, then FF57032 stalls playlist and gets404; MINIMX-MIM-001
cause still unproven. Original source intact3.6GB/4380.995s/startPTS69793.776.
Owned Transcode181.783s passes strict ownership/FF/REW/pause/STV state cycle;
fresh VAAPI/h264_vaapi job1791450842082 has both hardware flags true/deinterlaceOff.
CC1 screenshot readable but one malformed row, not clean-display PASS.32 actual
current prefs and server CC restored after each group. Source/installed99a47797
unchanged. Both orders266 reviewed; do not rerun completed stock/native rows.
Next investigate Copy handoff/reader with bounded evidence, then remaining
display/fallback/no-orphans/transformed DVD. Finish DEVICE-003 then
prioritize TABLET-IJK-001/TABLET-MIM-001 fixes, explicit NOT_WORKING only after
documented best effort. No commit/release/Core mutation authorized this turn.

Readiness98217 complete: actual232 connected/Configuration Wizard - Choose
Language/automationReadyfalse/playerActivefalse;27 preferences restored. User
asked asynchronously to complete wizard. Leave that UI available; do not start
media, treat wizard as playback failure, or change server setup properties.
Once done verify actual STV/public matching context, then strict owned Copy/
Transcode captions/controls/real backend/no-orphan/fallback and final power/raw
closure. Parent DEVICE-003 remains open; finish it before tablet per user.
Current installed/source APK both99a47797. Manual MiniMX awake owner ended;
actual saved owner values1/3600000 restored and independently verified by
status inactive on222. Earlier7/max notes describe test overrides/other device
history, not the saved MiniMX baseline; never restore guessed values.
Both orders264 reviewed. While waiting, only safe documentation/cleanup or
read-only preparation; no fake failed/passed optional rows or task closure.

Failed experiment/rollback/normal-DVD raw outputs now retired recoverably to
root deleteme. Compact matrix retains attempts/restoration/results; original
decoder-scan detail remains minimum unique unresolved-failure evidence, and
current232 wizard readiness stays active. Historical trial paths below are
recoverable rather than live active files. Source/install both99a47797.
Remaining closed-run snapshots/diagnostic bundles retired after ownership review;
retain only original unresolved reverse-failure detail/logcat/media/state/image,
canonical warm cache and current232 wizard readiness. No test/installer running.

MINIMX-DVD-001 investigation CLOSED with explicit NOT_WORKING reverse-8 preview,
root cause unproven; not hardware incapability or false PASS. Buffer/async
experiments withdrawn. Restored exact99a47797 APK/SDK251.622s/27 prefs/installed
hash verified. Normal ALADDIN119.207s PASS: wall7280/media7262/video166/audio227/
ratio0.997527/no drops, real HDMI cropped picture/audio-35.37dB.116 affected DVD
Python tests PASS. Working native/timed/chapter/menu/SPU rows retained.
Optional232 readiness session98217 active: connect only/saveFalse/private prefs
checkpoint, inspect actual menu/screenshot/first-time wizard before media.
Then owned Copy/Transcode/captions/actual backend/no-orphans, final power/raw
cleanup, DEVICE-003 closure then prioritized tablet. Both orders263 reviewed.

Current facts override older candidate paragraphs below:4s1be reverse96.116s
still fails-8 despite input loading/696ms buffered/render733/queued840/no error.
Existing async mode experiment105.254s also fails native seek output (render1/
audio16), not an accepted fix/default. Both experiments withdrawn from source.
Rollback61s/three original JVM tests reproduce exact99a47797 APK. Single SDK
restore251.622s PASS/27 preferences restored; actual base.apk hash verifies99a
on AM2. Guarded restored normal ALADDIN/cadence6s/15s HDMI hold now active87575;
verify actual output before closing the reverse row,
then reverse preview can be explicit NOT_WORKING with root cause unproven.
Keep working native/timed/chapter passes independent. Both orders262 reviewed;
manual awake remains; optional232/DEVICE-002 prioritized after restoration.

Actual public decoder scans123.328s fail at reverse-8: +2..64 preview output and
normal release pass, -2/-4 render, then -8 buffers2054ms/not loading/render293/
queued362/no error/raw1MiB reserve. MINIMX-DVD-001 added. Candidate pure policy
allows4s only API23/AM2/exact OMX.amlogic.mpeg2.decoder.awesome/reverse/BUFFERING,
callbacks session guarded, normal/READY/forward/other tuples retain2s/skip1s/
raw byte cap unchanged.52 backend/81 protocol/five actual Android JVM tests
PASS, build65s/strict APK inspection1145 entries/same signer PASS. Candidate
1be07b57cd95e457771e0e03d81dfa32f94e9a6a4b4141584e63fe46001a627b single
settings-preserving600s SDK update257.989s PASS/27 prefs restored; installed
base.apk hash independently verifies1be07b57 on AM2. Current guarded native
ALADDIN setup active46189, then use decoder-only reverse selection/start2700000ms
to leave room (two selection contracts PASS) and prove normal A/V release.
Revert if ineffective. No repeated forward rows are needed for reverse-only
policy change. Initial Bash stdin CRLF task-name failure
was corrected by direct argument invocation and documented in WORKFLOW.
Keep unique decoder failure raw. Both orders261 reviewed/manual
awake active. Then optional232/DEVICE-002 per user; no Core/server changes.

MINIMX-DVD-TEST-006 CLOSED: ten tests/67.077s FF/RW/pause PASS/27 prefs restored.
FF settled9607/source9588ms/ratio0.998022/maxdifference1514; RW11088/11086/
0.999820/max983; initial one seek-guess sample trimmed each, persistent/late
drift still fails2000ms bound. Pause source0ms/Play real output resumes.
Public-API decoder-only scans now running on stock175/ALADDIN, not physical
smooth-button/256x proof (actual stock dedicated keys are timed skips).
Next optional232 commissioning/owned Copy/Transcode/backend/cleanup, then
DEVICE-002 prioritized fixes or honest NOT_WORKING disposition. Both orders260.

Completed ALADDIN/cursor/chapter/timed-key raw files and four reviewed snapshots
now retired recoverably to root deleteme preserving original relative paths.
Compact DEVICE-003 matrix/ledger retain measurements; historical paths below
are not live active files. Decoder-scan output remains active and untouched.

Remote timed FF31.574s reaches FLUSH24->26/recovered A/V; first ready clock
diff2827ms then1036,-194,8,-189,73,-219ms. MINIMX-DVD-TEST-006 tracks oracle
correction: only trim initial seek-guess prefix before first agreement, retain
2000ms bound for all later samples and sustained same-epoch A/V/clocks. Ten
tests PASS incl persistent/late drift rejection. Corrected18s FF/RW/pause gate
active. No runtime clock/key/server change. Both orders259 reviewed; remaining
public decoder scans/232 then tablet closure. Manual MiniMX awake active.

MINIMX-DVD-TEST-005 CLOSED: three tests/53.773s PASS, short200ms UP/DOWN7->7,
held3400ms UP7->10/DOWN10->7, recovered1x A/V/27 prefs restored. Explicit
read-only Sagex chapter witness, unchanged authoritative Core MCP controls;
no runtime input/server change. Stock SageTV7 FF/RW are timed skips rather
than SageMC smooth-rate labels. Actual timed-key18s/pause-resume gate running;
next public-API decoder scans (separate from physical button semantics), then
optional232 commissioning/owned/GPU. Both orders258 reviewed/manual awake.

Held chapters39.763s reports one NEWCELL7->8 although UP hold jumps1297762->
2392190ms and A/V recovers. MINIMX-DVD-TEST-005 tracks an independent authored
ordinal oracle, opt-in explicit read-only Sagex GetDVDCurrentChapter (works)
alongside unchanged authoritative Core MCP controls. Three oracle tests PASS;
actual held-chapter-ordinals group active. Short taps unchanged/no seek;
repeat must change >=2 correct-direction authored chapters, no API fallback.
Next dedicated scan gate, optional232 commissioning/owned/GPU then DEVICE-002.
Both orders257 reviewed; no runtime/Core/plugin deployment/new APK.

MINIMX-DVD-TEST-004 CLOSED: five tests/57.853s physical PASS/27 preferences
restored. RIGHT target1319906/anchor error9495/FLUSH8->10; LEFT1185601/error
2819/FLUSH10->12; Back/Play cancel without seek. Explicit user-accepted15000ms
DVD allowance/default4000 unchanged. Synchronous cursor screenshot run103.950s
let stock cursor expire/no fresh flush, not production correction. Use HDMI
without pausing before Center on this slow box. Actual held-chapters gate now
running; next dedicated FF/RW then232. Both orders256 reviewed/manual awake.

ALADDIN functional native gate126.237s PASS: target540000/observed544072 with
explicit15000ms user-accepted approximate tolerance. Device-clock9329ms/media
9314/video215/audio292, ratio0.998392/no output drops. HDMI12s actual changing
picture,360 frames/248 changes above1 mean pixel, not HD200 motion equivalence.
Original2s gate159.831s rejected8.857s position delta while output was active.
Cursor27.356s prematurely failed immediate server clock and finally-PLAY could
cancel queued TS accept. Independent RIGHT/SELECT maps ACCEPT/FLUSH6->8/jump
783643->931830/later A/V recovered. MINIMX-DVD-TEST-004 added; five tests PASS,
corrected cursor async gate active with15000ms explicit tolerance/default4000
unchanged. No production keys/clock/seek change. Both orders255 reviewed.
Next held chapters/dedicated FF-RW semantics then optional232/DEVICE-002.

MINIMX-DVD-TEST-003 CLOSED: selector159.261s/title witness191.317s PASS;
27 preferences restored. HDMI2s image readable ENG DVD SPU/main_feature/
CUE016/PTS30.000/chapter2; six1s spectra all dominant440Hz/RMS-35.67..-35.02dB,
selected Spanish AC3 wire0xBD81. Enabled SPU0x40. Five CLI/hold tests PASS;
no runtime change needed for the initial wrong logical selector units.
Guarded ALADDIN native Media3 stock175 scene540000ms/cadence8s/30s visual hold
now running; watcher captures12s during title. Then remote cursor/chapters/scan
and optional232 commissioning/owned/GPU. Both orders254 reviewed. Keep unique
new motion evidence; retire completed authored-DVD raw after compact record.

Completed authored-DVD control/selector/title-witness16 raw files retired
recoverably to root deleteme with original relative paths after compact proof.
Historical paths below are no longer live active files. New ALADDIN remains.

User directs finishing DEVICE-003 first, then DEVICE-002 with prioritized
tablet fixes. After documented best effort, genuinely uncorrectable tablet
rows must be NOT_WORKING with reasons, never PASS; close the parent once each
row has an honest disposition. Preserve completed evidence/settings/identity.
No physical test remained running after interruption. Corrected narrow DVD
selectors159.261s PASS/27 preferences restored; five selector/hold tests PASS.
Next use bounded30s title hold with immediate HDMI capture to verify readable
SPU and switched440Hz tone. Current99a47797/manual MiniMX awake owner remains.

Authored full controls239.410s recovered after all8 commands but FAIL because
caller expects logical1/0 vs actual wire48513=0xBD81 AC3 track1 and64=0x40 SPU
enabled0. Core forwards packed values; client code confirms units. CLI hex/
decimal explicit help/4 tests PASS; narrow corrected selectors gate14819 now
active. Recorded snapshots have main title/A-V/counters but sampled cue gaps;
need actual readable SPU and switched tone. Later12s HDMI returned root after
finite89s title, excluded from title/subtitle proof. MINIMX-DVD-TEST-003 added,
both orders252 reviewed/99a47797/manual awake active. No runtime/Core change.
Keep unique current DVD evidence until physical oracle correction closes;
all completed caption raw retired recoverably, no whole matrix repetition.

IJK unsupported CEA93.441s PASS/playable generated picture/zero false callback
negotiation/events/27 prefs/server CC restore. Not CEA rendering or hardware
MPEG2 proof; original IJK native bitmap text path unavailable, no advertised
DVB service on Taskmaster. Guarded authored DVD controls now active:
minimx-stock-authored-dvd-controls/detail, explicit root/select/main/chapter
up/down/audio/subtitle/pause/PLAY, known generated selectors1 audio/0 SPU.
Verify actual visible SPU and switched audio, not only command acknowledgements.
Both orders251 reviewed/99a47797/manual awake active; next DVD remote/ALADDIN,
optional232 commissioning/owned/GPU. Caption children closed, retire raw.

Completed/corrected caption raw51files22938641bytes now retired recoverably
to root deleteme, preserving original relative paths. Historical caption/failure/
inspection references below are recoverable there rather than live artifacts.
Active authored-DVD outputs and canonical warm cache remain live.

MINIMX-CAPTION-TEST-003 CLOSED:5 oracle tests/IJK187.629s/M3 STV119.818s plus
all four actual local DVB pauses109.886/111.073/107.213/109.683s PASS, readable
cropped images/state3 clock hold/new PLAY cues/27 prefs/server CC restoration.
Both corrected caption children complete; no codec matrix repetition. IJK CEA
unsupported safe-negotiation gate now runs71367; this is not CEA rendering or
MPEG2 hardware proof. Then authored DVD audio/SPU/chapter controls, ALADDIN
motion/native remote controls and optional232 commissioning/owned/GPU evidence.
Both orders250 reviewed/99a47797/manual awake active. Retire corrected IJK
failure/oracle/completed caption raw after compact results, preserve active
71367 output. No server/Core/plugin/commit/release change.

Actual local Media3 DVB109.886s and legacy111.073s PASS on99a47797: state3/
clock hold, PLAY/new bitmap cue progress, readable cropped captions/27 prefs/
server CC restore. These replace only unexecuted pause claims, not original
valid Off/On/seek results. GSY-M3 then GSY-legacy remain in39671; stop on failure.
Both orders249 reviewed/manual awake active. Then close oracle child, retire
corrected/completed raw, IJK unsupported-caption boundary/DVD/optional232.

MINIMX-IJK-CC-001 CLOSED: corrected readable IJK TT187.629s plus unchanged
Media3 STV TT119.818s smoke/readable event225 text/no duplicate local renderer/
idle-independent clock/27 prefs/server CC restore. Initial smoke16.911s ADB
connect timeout before checkpoint/control; TCP/ping/wrapper proved222 AM2
ready, fresh guard passed with no ordered-command replay. Session39671 now
four local DVB pause gates (Media3 first, legacy, GSY-M3, GSY-legacy), stop on
failure. MINIMX-CAPTION-TEST-003 remains open until those complete. Both orders
248 reviewed/99a47797/manual awake active. Retire corrected TT raw after compact
record. Next IJK unsupported-caption boundary/DVD/optional232; read-only232
had no MiniMX UI context yet, not a commissioning or owned-stream PASS.

Corrected IJK TT187.629s PASS/readable cropped local rows/Off-On20s77 updates/
media20143/natural gap1.769s/FF-REW5s/actual local pause hold/new PLAY cues,
27 prefs/server CC restored. Native recoveryMs=-1 is unknown, not measured.
MINIMX-IJK-CC-001 waits representative unchanged-extractor smoke before closure.
Session76802 runs Media3 STV TT smoke first, then local DVB pause for Media3,
legacy, GSY-Media3 and GSY-legacy sequentially/stop on failure. No whole caption
or codec matrix repetition. Both orders247 reviewed/99a47797/manual awake active.
Next unsupported IJK caption boundary/DVD controls/optional232 commissioning.

Candidate128.329s selects/renders TT58 updates, then Off oracle fails because
IJK has no generic subtitleOverlayAttached field. Actual public selected-1/raw
DISABLE8192/TT false/empty proves Off, not runtime failure. New codec-specific
Off/seek helpers and actual local pause execution/5 tests PASS. Earlier four
local DVB rows requested pause but old runner ignored it: retain valid cues/
Off-On/seeks/readable images, not pause proof. MINIMX-CAPTION-TEST-003 added.
Corrected IJK TT group now active (minimx-stock-ijk-teletext-corrected-oracle).
Next targeted four local DVB pause rows/representative unchanged TT, unsupported
IJK boundary, DVD and232. Both orders246 reviewed/99a47797/manual awake active.

IJK normal-menu TT125.395s FAIL: page888/raw21504 discovered but selectedRaw8192
is DISABLE_TRACK, not a CEA fallback slot; no cues/overlay.27 prefs/server CC
restored. Candidate minimal Base inventory-only default-no-op notification,
IJK-only UI/session/player/ready guarded configured-slot retry on discovery and
prepare.3 source tests PASS; build/install/physical pending. Video transport,
clock/seek/server unchanged; existing extractor backends do not override hook.
Both orders245 prioritize fix then remaining caption/DVD/232; manual awake active.
Keep unique TT failure raw until corrected, no full positive matrix rerun.

Candidate99a47797 built74s/strict debug APK inspection/same signer1145 entries;
3 discovery/52 backend/3 source authority/31 STV authority tests PASS. Single
600s SDK update completed233.829s/hash99a47797/27 preferences restored. Full
static validation20143 PASS/local245; source APK
is explicit install target, canonical firetv artifact was not overwritten.
Physical corrected IJK Teletext now runs (minimx-stock-ijk-teletext-caption-
candidate). Raw33files72031123bytes for completed IJK controls/startup/System/
remaining safety rows retired recoverably to root deleteme; original failure
and candidate inspection/current TT remain active.

IJK UK AVC scripted controls191.423s PASS: FF/REW/large jumps/pause/STOP exact
rewatch,27 prefs restored. Independent final HDMI changing picture2/6s and
audio-35.7/-7.9dB; not a visual witness for each prior command where native
renderer counters are unavailable. IJK local normal-menu Teletext gate78985
now runs; next unsupported CEA/DVB boundary, additional DVD, optional232.
Both orders244 reviewed/F901/manual awake active, no runtime/Core/server change.

System probe76.567s PASS exactly one error fallback -> Media3/Amlogic MPEG2,
independent generated full-screen picture/audio-33.6/-7.2dB. Not System decoder
PASS.27 preferences restored. IJK UK AVC seek/jump/pause/STOP-restart runs
session27610; independent final HDMI required. Both orders243 reviewed;
next IJK Teletext/unsupported-caption safety, additional DVD and optional232.

Final GSY/legacy safety-only77.923s PASS; all four malformed AVC paths complete,
zero repeated positives/27 prefs restored. Static validation65449 completed
PASS at local240 (no root mirror mount); host task-order validates242.
System-player bounded startup probe now runs guarded; verify actual engine and
independent HDMI before crediting, one fallback is not System decoding PASS.
Next IJK controls/Teletext/unsupported-caption safety, DVD controls, optional232.
Both orders242 reviewed/F901/manual awake active. IJK's MPEG2 option defaults
off: its earlier software result is not proof the box lacks hardware MPEG2.

Legacy85.671s/GSY-Media3166.220s safety-only PASS/no new crash/death/zero
positive repetition/27 prefs restored each. Initial legacy9.344s invalid CLI
auto engine rejected before playback, corrected and not a runtime defect.
Final GSY/legacy active in session78009; next IJK controls/captions/system,
additional DVD, optional232. Both orders241 reviewed; F901/manual awake active.

IJK stock175 generated MPEG2/AC3 startup70.089s actual software mpeg2video,
not hardware PASS; independent full-screen HDMI PTS70.771->74.741 across4s,
audio-32.1/-6.7dB. UK AVC startup76.189s actual Amlogic OMX/hardware and readable
private HDMI/audio-33.5/-5.0dB.27 prefs restored each.156 MCP tests PASS.
Safety-only legacy/GSY-Media3/GSY-legacy now run sequentially in session51076,
stop on failure. Next system/IJK controls/captions/DVD/optional232 actual
commissioning and job evidence. Both orders240 reviewed/F901/manual awake active.

Media3 safety-only74.918s PASS: actual truncated AVC safe failure/recovery/no
new crash/death, zero positive rows requested,27 prefs restored. Static MPEG4
injection plan/unsupported H263 and AV1/authorized DRM absence are not physical
PASS. IJK generated long MPEG2 startup now runs session85598; independent HDMI
picture/audio capture required before crediting its output counters. Both
orders239 reviewed; F901/manual awake retained. Continue system/remaining
backend containment/DVD/optional232, no completed codec/caption reruns.

Caption/Media3 safety raw74files39183342bytes retired recoverably to root
deleteme after compact recording; all historical caption raw paths below are
recoverable there, not live artifacts. IJK active evidence remains untouched.

All four CEA/Teletext/DVB extractor paths now pass. Remaining GSY Media3 DVB
169.713s/legacy TT145.908s/legacy DVB167.712s have readable cropped private
captions, seeks/pause and verified public server CC/27 app preference restore.
Session54575 finished0. Safety-only Media3 containment runs in session30862;
new harness flag skips completed positive codec rows, retains existing negative
scope,2 selection/6 inventory/2 capture tests PASS. Static plans and unavailable
assets remain distinct from real physical tests. Next IJK/system/DVD controls,
optional232 with actual commissioning/GPU evidence. Both orders238 reviewed.
F901 installed/manual MiniMX awake active; restore power at session completion.
Startup difference is firmware phone classification; existing TV-browser option
physically verified, original false preference restored, no silent default change.
No commit/release/server/Core/runtime changes in this caption/containment phase.

GSY/Media3 Teletext145.198s PASS/readable cropped STV page888/seeks/pause/
server CC verified restore/27 app prefs. Sequential54575 now Media3DVB then
legacyTT/DVB; stop on failure. Static validation23726 finished PASS (loaded
234 locally/no workspace mount); current host task-order/mirror237 PASS.
Then IJK/system/DVD controls/optional232/containment. Both orders237 reviewed,
F901 installed/manual awake active; no completed row repetition.

Legacy DVB168.369s PASS/readable local bitmap/Off-On20s11new nonempty/
media19029/natural gap5.307s/FF-REW5s/pause/server CC verified restore/27 app
prefs. Media3 and legacy caption types complete, all four CEA paths complete.
Process54575 runs four GSY TT/DVB rows sequentially (Media3TT,Media3DVB,
legacyTT,legacyDVB), stops on first failure. Then remaining IJK/system/DVD
controls/optional232/containment. Static validation session23726 still active.
Both orders236 reviewed, F901 installed/manual awake active; no completed rerun.

Legacy Teletext146.408s PASS/readable cropped STV rows/idle-independent clock/
FF-REW5s/pause/PLAY/server CC verified restore/27 app prefs. Process68847 now
legacy DVB normal-menu/Off-On20s/seeks/pause. Next GSY TT/DVB and remaining
IJK/system/DVD/optional232/containment. Full static validation session23726
also running. Both orders235 reviewed/F901 installed/manual awake active.

GSY/legacy CEA169.713s PASS, readable PTS rows/standard states/wire/FF/REW5s/
pause/PLAY/server CC verified restore/27 app prefs restored. Four CEA paths
now complete. Sequential process68847 runs legacy Teletext then DVB, stops
on first failure. Next GSY TT/DVB, IJK/system/DVD controls/optional232/containment.
Both orders234 reviewed, F901 installed/manual awake active; no repeated
completed codec/CEA rows or runtime/server/commit/release change this phase.

GSY/Media3 CEA172.258s PASS, standard CC/wire/seeks5s/pause/PLAY, independently
readable PTS rows, server CC verified restore and27 app prefs restored.
Sequential process75787 now GSY/legacy CEA. After it, remaining TT/DVB/IJK/
system/DVD controls/optional232/containment. Read-only232 plugin API contract1
ready/zero sessions/reservation available confirmed; not GPU or MiniMX232
commissioning proof. Both orders233 reviewed/F901 installed/manual awake active.

MINIMX-STATE-001 CLOSED: actual legacy CEA167.113s passes server-CC checkpoint/
verified restore before disconnect,27 app prefs restored;5 restore/31 authority/
38 automation tests pass. Legacy CEA Off/CC1/CC2/wire/FF/REW5s/pause/PLAY and
independently readable post-seek PTS rows/no duplicate local overlay. Earlier
uncaptured pre-first-cycle server value remains unknown; do not guess reset.
GSY Media3 then legacy CEA run sequentially in session75787, stop on failure;
then remaining TT/DVB/IJK/system/DVD controls/optional232/containment. All
completed Media3 and legacy CEA rows retained, no native codec repetition.
Both orders232 reviewed, F901 installed/manual awake owner active. End owner
before prolonged handoff; no server/Core/production runtime change this phase.

Media3 native DVB168.448s PASS: explicit Android normal-menu DVB/local bitmap,
Off-On20s13new nonempty/media20032/natural gap3.305s, FF/REW5s/pause, readable
cropped bitmap,27 app prefs restored. No STV CC1/CC2 bitmap claim. New test
workflow gap found: app-only restore didn't restore persisted server CC.
Read-only Core source confirms VideoFrame.setCCState->uiMgr.putInt LAST_CC_STATE.
Caption runner now snapshots existing public API CC after connect/before controls,
restores/verifies before disconnect on success/fail; restore error nonzero.
5 restore/31 authority/38 automation tests pass. Actual legacy CEA cycle/seek/
pause/restoration active (minimx-stock-legacy-cea-caption-preserve). Earlier
original server CC before first cycle unknown; do not guess Off or claim it
recovered. From now preserve captured current value. Both orders231 reviewed,
F901 installed/manual awake active. No APK/Core/plugin/server change.

Media3 Teletext128.859s PASS on stock175 Breakfast: page888/services1/PES475/
cues65, independent clock drain63/media6502/wire28, FF/REW5s/pause/PLAY,
27 prefs restored. Cropped lower CC rows independently readable in STV, no
duplicate local overlay. Raw CEA extractor candidates are not UK service proof;
current caption UI already filters unobserved CEA (source inspected).
Media3 native DVB Taskmaster normal-menu mode/Off-On20s/FF/REW/pause now active
(minimx-stock-media3-dvb-caption). Then remaining caption backends/IJK/system/
DVD controls/optional232/containment; no repeats of completed native codecs.
Both orders230 reviewed, F901 installed/manual awake active; private broadcast
captures not for public release/docs. Compact matrix updated, no new runtime
changes/commit/release/server update this caption phase.

Media3 native CEA stock175163.958s PASS, wire/Off/CC1/CC2/Off/CC1, FF/REW5s
recovery, pause/PLAY and27 prefs restored. Independently readable CC1/postseek
PTS rows, no duplicate local overlay; CC2 blank on channel1-only data, not
extra-service certification. Corrected invocation omits incompatible local
track-codec/event225 flag (priorCLI8.551s rejected before playback).
Media3 stock175 Breakfast Teletext/STV callback/seek/pause now active
(minimx-stock-media3-teletext-caption), then DVB/remaining backends/IJK/system/
DVD controls/optional232. Both orders229 reviewed. Completed/corrected112 raw
files348297499bytes moved recoverably to root deleteme with source/destination
boundary/reparse/ownership/collision checks. Compact result preserves all
Surface/exit/DVD evidence; old raw references below are no longer active.
Manual MiniMX awake owner still active; end at session completion/prolonged
handoff. Current F901 installed; no commit/release/server/Core changes.

MINIMX-DVD-TEST-002 / MINIMX-SURFACE-001 CLOSED: actual authored title lifecycle
134.826s PASS, MediaFile65513423/new titleCell3/menuFalse, same connection1,
OMX MPEG2 init1/2/3/release0/1/2, HOME A/V/manual pause/PLAY/noPID teardown,
27 prefs restored. Four affected MPEG2 paths and authored title/menu/cadence
now verify F901's exact AM2/API23/MPEG2 replacement policy, no runtime/source/
clock/audio/seek/Core changes. Do not repeat completed codec cohorts.
Stock175 Media3 CEA event225/Off/CC1/CC2/seek/pause gate starting
(minimx-stock-media3-cea-caption-wire). Prior8.551s caption command was CLI
rejection of incompatible track-codec+event225, no playback failure;27 prefs
restored, corrected invocation omits local-track selector. Remaining IJK/system,
TT/DVB/other captions/DVD controls/optional232/GPU/containment scopes open.
Both orders228 reviewed, F901 installed/manual awake owner active. Raw from
corrected/complete children retires now after compact results; historical raw
paths below are recoverable in workspace deleteme rather than live artifacts.

Native root-menu lifecycle142.172s passes HOME video/audio return but fails
manual pause on loop. Do not treat as title playback failure or patch runtime
by inference. Existing lifecycle runner now --authored-dvd-title verifies
canonical generated volume through public context/media, activates known Play,
requires new non-menu cell/real A-V before lifecycle checks.3 subject/38
automation tests pass; guarded actual-title retry active (minimx-stock-native-
dvd-title-lifecycle). No new APK/Core/server change,27 prefs restored in failed
root trial. Both orders227 reviewed, F901 installed/manual awake active.

MINIMX-DVD-TEST-001 CLOSED: fixed context/menu cycle56.930s PASS, actual175
authored MediaFile65513423/VIDEO_TS verified before physical keys. Root/
Languages/root/main phases2.794/3.980/2.903/2.539s; independently readable
highlights/title,27 prefs restored. Actual title cadence5s/device6273ms gives
6067ms media/6083ms player/182 video/197 audio, ratio0.96716.12s HDMI independently
shows PTS72.673->78.679 over6s/audio-38.2/-7.1dB. Do not call looping-menu
trial a client slowdown. Native DVD Home/manual-pause/return gate now active
session38295 (existing lifecycle runner, dynamic/native media3, no new runtime
change). Both orders226 reviewed, F901 installed, manual awake active. Surface
parent closes only if this final affected gate passes. Then CC/IJK/system/
optional232/malformed containment remain; no repeated codec scope.

Authored native startup93.007s reaches looping root despite requested skip-
menus; ratio0.2149 is invalid title cadence, not client slowness.312 metadata
callbacks/11.36s and highlightTrue show wrong subject. Oracle now requires
actual title/playing/nonzero A-V before/after. Menu cycle then fails BEFORE
keys on context525...hex parsed as scientific float; preserve opaque
uiContextHint and discover actual public control.resolve_context(clientId)
before fixture-path check.1 new/76 ADB,4 cadence/3 menu tests pass in correct
container Python; host missing sagetv_dev_mcp invocation error is not app.
Corrected current-DVD menu cycle active session8172; no new APK/Core/server
change. Guard restores27 prefs. Both orders225 reviewed, manual awake active.
Current capture45s57,385,987bytes needs review; don't claim title until visible.

GSY/legacy135.355s PASS, actual legacy_exo, same connection1/init1/2/3/
release0/1/2, Home/manual pause/Play/noPID teardown,27 prefs restored. All four
affected MPEG2 renderer paths now recover. Stock175 generated authored native
DVD startup/cadence gate active session91395 with leave-playing for subsequent
bounded menu-cycle verification. It restores27 prefs before that second gate;
check actual active decoder/policy rather than restored preference. Then close
Surface only if DVD title/menu gates pass; captions/IJK/system/optional232
remain open. Both orders224 reviewed; manual awake owner active.

GSY/Media3 affected MPEG2 lifecycle135.395s PASS, actual delegate media3,
same connection1, OMX init1/2/3 release0/1/2, Home/manual pause/Play/teardown
and27 prefs restored. Sequential process6072 now runs GSY/legacy; then authored
DVD native Surface/menu gate. Both orders223 reviewed, parent Surface still
open. Manual MiniMX awake owner active; candidateF901 installed/no new APK.

MINIMX-EXIT-001 CLOSED: HOME once/bounded stable background/one stop,6 revised
tests and actual legacy MPEG2 lifecycle137.393s PASS; same connection1,
video init1/2/3 release0/1/2, Home/manual pause/Play, noPID/stoppedtrue/no
pending termination,27 prefs restored. GSY Media3 then legacy affected gates
active in one guarded sequential process; stop on first failure. Then authored
DVD Surface/menu gates before closing Surface child, remaining MiniMX scope.
Both orders222 reviewed; no codec cohort repeat, no new APK/server change.
Manual MiniMX awake owner active; must end before handing back prolonged idle.

Latest: browser-settle-only exit trial FAIL177.680s post-playback despite
standalone pass. Media3 HOME/manual-pause A/V recovery passes again; do not
promote the exit trial. MCP shutdown candidate now sends Android HOME once,
waits bounded stable background then one force-stop,6 revised tests pass.
Legacy affected lifecycle/post-playback gate active (minimx-stock-legacy-
lifecycle-home-exit); no new APK or changed normal runtime keys. Prior active
session29312 below has ended FAIL/27 prefs restored. Manual awake still active.

F901 Media3 targeted group202.541s passes HOME A/V return, same connection1,
codec init1->2/release0->1, user pause preserved/init3/release2 and replay.
Whole group FAILS final teardown: ActivityManager kills31943 then starts396
phone launcher from pending navigation. MCP exit now waits bounded stable
browser before one force-stop;6 new/all155 MCP tests and standalone actual
stable_browser/noPID/stoppedtrue pass. Full Media3 repeat0 post-playback gate
running session29312 (prior replay already passed); then legacy/bothGSY
affected MPEG2 lifecycle and DVD, remaining MiniMX scope. Both orders221
reviewed; manual awake owner active,27 preferences restored in prior group.
Do not falsely claim whole lifecycle PASS or a broad device matrix closure.

Long stock175 Media3 lifecycle214.298s FAIL: HOME/return preserves connection,
audio resumes, video queued468/rendered386 stop despite valid/shown Surface,
no error/crash. Logs show Amlogic MPEG2 Surface generations without release.
All27 preferences restored. HDMI5s independently shows fullscreen generated
MPEG2 PTS24.224;15s shows HOME, capture audio-37.0/-7.2dB. Unique failure raw
retained. Narrow AM2/API23/exact OMX MPEG2 codec-replacement candidate in
existing shared renderer policy; focused JVM/build active session37183. Next
inspection/guarded update using private600s budget, verify installed hash,
affected four backend MPEG2 lifecycle/Surface and DVD gates, then remaining
DEVICE-003 scope. Both orders220 reviewed; manual awake owner still active.
AM2 candidateF90128fb builds52s,2 policy JVM/52 backend/3 TV-browser/6 inventory
contracts and APK inspection/full source validation pass. Single install234.467s
uses600s SDK/630s outer, success and device hash match;27 prefs restored. Actual
Media3 targeted lifecycle session31945 now passes HOME A/V return, manual-pause
preservation and one replay; final teardown/pref restoration still pending.
No server/Core/plugin update or restart, no commit/release. Read-only wrapper
getprop verifies ro.product.device=AM2;gxbaby is board, not Build.DEVICE.
Completed four-cohort codec snapshots/full reports/logs are being retired to
workspace-root deleteme preserving relative paths; compact matrix retains
build/decoder/fixture/limits/timing/pref restoration. Unique lifecycle failure
raw and active candidate installation remain in active/DEVICE-003.
Readiness/startup/browser/completed AVC and corrected MINIMX-IO-001 raw also
retired23files/183223848bytes; compact results preserve their meaningful
findings. Historical raw references below are recoverable under deleteme,
not live active evidence. Surface/exit failures remain active until closed.
Tablet parallel read-only bootstrap review complete: first11 Taskmaster
non-IDR AUs have no in-band SPS/PPS;firstIDR199738bytes includes sets, first60
max211776.1080High4.0 MBAFF versus progressive720 positiveTS/keypacket0/max38673.
Compact sanitized evidence/command plan in results/DEVICE-002/ijk-bootstrap-
metadata.json; hashes cover first1MiB only, not whole recording. No fixtures,
watch/scan/device/server changes or native fix; next matched-IDR TS/MP4 still
needs normalized VCL/SPS/PPS/timing and actual-picture comparison.

GSY/legacy448.341s PASS12 hardware rows/3 explicit unsupported VP8/VP9,
actual legacy delegate, B69,27 preferences restored. All four native codec
cohorts complete. Long stock175 Media3 lifecycle gate active session12219,
then IJK/system/captions/DVD/optional232 and steady fullscreen HDMI picture.
Both orders219 reviewed; manual MiniMX awake owner remains active. No commit,
release, Core/plugin update or server restart in this session. Earlier active
session references below are historical and superseded by this resume point.

Native legacy445.706s and GSY/Media3466.971s PASS12 hardware rows/3 software-
only VP8/VP9 limits each, B69,27 prefs restored. GSY/legacy full scope complete
(minimx-stock-gsy-legacy-codecs,448.341s); then IJK/system capabilities,
lifecycle/captions/DVD/optional232. Completed native cohorts not repeated.
Per-device install_timeout_seconds now finite30..900/default180; Config->
CLI/server->verified single SDK install. MiniMX private600, tablet/FireTV180;
149 MCP tests pass. Outer install caller needs630s, no implicit retry/reset.
Public Core MCP captions.get on ACTUAL SDK clientId (matchescommissioned)
reports stock175 /opt/sagetv/server/STVs/SageTV7/SageTV7.xml. Visual-theme
SageMC assumption earlier in this handoff was wrong; no STV/server changed.
Compact result corrected and private stock expectedSTV saved. Verify232 later.
Both orders218 reviewed, current B69 installed, manual awake owner active.

MINIMX-IO-001 CLOSED: B69b4cf0 installed/hash/complete package status verified
despite180s SDK install timeout; no overlapping retry. Actual stock175 Media3
MPEG2 STOP->PLAY/output recovery128.306s PASS, diagnostics responsive and27
preferences restored. Getter-only change, no stream/cache/decoder/seek change
or general SIZE-timeout claim. Both orders217 reviewed; legacy full codec scope
running (minimx-stock-legacy-codecs, session45800), then remaining GSY/IJK,
lifecycle/captions/DVD/optional232. Do not repeat earlier12 Media3 codec rows.
Manual MiniMX awake owner active; must end when testing session ends. DEVICE-002
IJK/owned Transcode remain open separately; matched1080 bootstrap comparison
and optional stock-compatible runtime recovery are their next unfinished gates.
Compact provenance now artifacts/results/DEVICE-003/matrix.json; completed raw
retirement after review, unique new failures retained while open.

Prior216 checkpoint:12 native Media3 codec rows PASS (guarded807.239/209.935s), threeVP8/VP9
hardware-only rows explicitly unsupported,27 prefs restored. Long MPEG2 basic/
large seeks/pause work, retained STOP->PLAY leaves debug broadcasts result0.
ANR trace for currentPID17706: main blocked in getSessionReuseCount while
loader tid30 owns retained-source monitor in refreshSize/network reply.
Diagnostic counters now volatile/non-synchronized only; no source bytes/cache/
decoder/seek change.2 new blocked-lock/atomic64 tests+13 existing Pull JVM,
53s build and APK inspection PASS; newB69b4cf0b4faf72e425b6c3dfc9cef9c7d756c2e5002dfb4dea14791ce671b52
guarded update underway. Old private27 prefs restored after isolated Dev
force-stop/normal launch; snapshot responsive again. Do not overwrite a
pending preference checkpoint before restoration. Trace was already present
from OS ANR; attempted SIGQUIT command printed usage, not fresh trace evidence.
Next: finish install/hash/pref restore, rerun STOP->PLAY/seek/snapshot/restore
and separately determine whether underlying SIZE wait remains. Both orders216
reviewed, no repeat of12 unrelated decoded-codec rows. Manual awake owner active.
Tablet IJK read-only review finds positive controls lower-resolution; positive
Annex-B TS also logs nakedCSD. Next is matched original1080 stream-copy MP4/TS
bootstrap/VCL comparison, not another queue guess. Small native fix only if
proved; legacy scripts lack pinned source/NDK13b and use obsolete mavenlocal
consumer, current NDK21/flatAAR path needs isolated build/provenance work.

MiniMX update/UI complete: installedB7fd7dd9f04a3366a30ede934a92b076d41131dc93bd1db38156254b54619cda
matches current built APK. Installer's Android6 dex optimization exceeded
MCP caller120s but finished; private27 prefs restored, no overlapping install
or clear-data. Android6 lacks cmd and rejects implicit package MAIN; tooling
now validates installed component metadata/Leanback instead. Vendor pidof
returns all PIDs even for a nonexistent name; reject PID0/1/diagnostic text
and use exact-name legacy ps fallback.142 MCP tests pass; actual launch/status
now gives correctsinglePID. Existing TV-browser option applies immediately on
Settings BACK, actual MainActivity verified, original false preference restored.
Stock175 Media3 codec batchA (first8) active in guarded session; six initial
MPEG2/MPEG4/AVC rows pass. Early18s HDMI caught launcher/rebuild, NOT MPEG2 proof.
Later60s HDMI independently shows labelled baselineAVC in SageMC preview and
nonsilent audio(-37.2/-6.9dB); short-EOF/preview not full-screen certification.
Manual MiniMX awake owner remains active during testing (original7/2147483647);
end it after session. Both orders215 reviewed. Tablet IJK/owned Transcode stay
open: next IJK hypothesis is upstream0.8.8 naked Annex-B CSD path, unproven
without actual bootstrap comparison. Do not repeat failed queue trial or claim
another runtime fix. Continue MiniMX remaining rows/controls/captions/DVD.

User reconnected HDMI capture and requested continue. Windows now enumerates
USB Video/Digital Audio; five-second FFmpeg recording9,277,646bytes succeeds
and independently reviewed1920x1080 MiniMX server browser. Both orders214 put
MiniMX guarded update/UI/full stock175-first matrix next, optional232 after;
tablet IJK/owned Transcode remain separate/open, completed rows not repeated.
MiniMX update underway with private preference checkpoint and no clear-data.
The missing-capture notes below are historical, not the current blocker.

Caption child TABLET-CC-001 CLOSED: strict owned Copy/Media3 Taskmaster Off/On
20s progression/FF/REW passes104.151s,10 new nonempty cues,19.136s media progress,
longest natural gap5.189s. Earlier5s test was too short, not a persistent freeze.
Independent snapshot182651 is readable;232 API reports zero active sessions.
C56c631a
passes APK inspection,4 signature+6 adapter JVM/3 authority+52 backend contracts.
All corrected232 CEA rows now PASS and independently readable: Media394.269,
legacy110.676, GSY/Media3100.535, GSY/legacy104.999s,5s seek settling, CC
Off/CC1/CC2, pause/resume, no new crash signature. Native preference plus
checked malformed708 decoder-only recovery works; no producer repair claim.
GSY/legacy fallback has no local raw callbacks and still shows readable
side-source PTS rows. Stock175 native DVB smoke88.579s/readable bitmap proves
other captions retained. Public captions.get verifies232 tablet SageTV7.xml;
earlier early-context-empty query was startup timing, not plugin failure.
Fifteen prefs restored per group. Audio8 raw reports17,077bytes retired to
root deleteme after compact recording; originals recoverable. Canonical381
APK unchanged. Valid175 native authored DVD fails60.482s: audio runs with no
MPEG-2 video decoder/first frame, valid Surface and source/SPU packets.232
transformed main feature passes70.498s: actual Exynos H.264 and independently
readable fixture label, not strict owned Direct or stock native/menu/SPU proof.
TABLET-DVD-001 CLOSED on6b71b3fc,64s build/6 policy JVM/3 new+81 DVD contracts
and inspection pass, guarded update-r preserving settings. Explicit Native
uses the policy-filtered MPEG-2 selector before player creation, known-empty
only; safe175 refusal37.277s has no audio renderer/crash. Initial da25 incorrectly
rejected Core's empty native URL before first transformed title payload; source
MiniDVDPlayer1230/1804/1837 proves ordering. Auto/Hybrid now wait for actual
unsupported MPEG-2 tracks, ordinary/transformed/supported/unknown queries stay
on existing paths. Corrected232 transformed93.543s passes chapter+/chapter-/
pause/play and readable authored title with device-clock cadence0.999422.
Stock AVC76.115s on da25 plus current6b71 posture gate proves positive path.
Posture/touch/Back/layout91.196s PASS: rotation requests0/1/2/3 retain declared
landscape2000x1200 and actual A/V, all4 right-icon columns aligned, normal900ms
hold opens menu. Original two rotation settings and15 prefs restored. Initial
/dev/tty hierarchy probe was a tooling failure; corrected unique temporary
device XML is removed in finally. Four layout-oracle tests pass.63 completed
codec raw files29,112,623bytes retired recoverably to root deleteme.
IJK unchanged6b71 UK restart-from-beginning FAIL67.327s. Synchronous-queue
trial A553 startup41.816s and controls78.703s pass first-frame/clock oracles,
but settled193814 screenshot is uniformly blank and screenrecord contains no
usable frames. Do NOT call that a visual/runtime fix. Current D8A6e6d2 adds
bounded debug getMediaInfo decoder identity only (not strong renderer counters);
fresh195042 picture also blank with actual OMX.Exynos.avc.dec. Queue trial and
its owned839-byte test withdrawn, original IJK options restored. Current56603387
build48s/inspection PASS, installed update-r and independently stream-hashed:
566033871b1323151a7d65e6e032fa1abe7cc9912c88e813998a1068a6d6f8fc,
55,019,483bytes matches build. No runtime/native-library/software/Core fix claim.
Tablet manual awake owner ended: actual saved checkpoint7/600000 restored;
this returned checkpoint, not historical2147483647 observation, is authoritative.
15 app prefs restored. Canonical381 APK unchanged. Completed codec/caption/audio
raw retirement recorded; unique open-failure evidence remains. Optional user
choice asked on switching to MiniMX while tablet IJK/MIM follow-ups remain.
Both orders213 reviewed. Host task/order mirror and1583-file manifest checks,
container source validation and all135 MCP tests pass. Fresh wrapper connect
still reports authorized MINIMX at222:5555; timeout setting independently reads0.

MiniMX DEVICE-003 readiness PASS in parallel: .222:5555 MINIMX/AM2 Ugoos,
Android6.0.1/API23 ARM32 (armeabi-v7a/armeabi), S905/gxbaby, installed Dev
0.5.100-DEV-DEBUG/2101109; no TV/Leanback firmware features, phone.ServersActivity.
User confirms HDMI USB capture connected. Baseline artifact DEVICE-003/
minimx-readiness-20261007T192929676Z.png1920x1080 retained for active UI review.
Ignored config now has minimx alias and actual persisted identity (read only);
no active-device/server switch, install or settings mutation. Full matrix follows
current tablet work. Existing use_tv_ui_on_tablet can select TV browser; automatic
model/HDMI heuristics are not a safe replacement for explicit user choice.
Agent fixed Android6 PTY echo/prompt/backspace contamination with bounded new-shell
handshake (stty/PS1/PS2/ready preamble before runtime); authorization ordering and
no replay preserved.65 focused ADB tests + physical read-only222/51 connections
PASS. Config fixtures now isolate ambient aliases/serial; full130 MCP tests pass
in proper /opt/opensagetv-vibe/android-python/bin/python3 + config/PYTHONPATH,
even with tablet alias. Earlier system-Python/missing config/ambient alias attempts
are tooling failures, not app regressions. Agent-owned files adb.py,test_adb.py,
test_test_environment_config.py. Startup UI explicit-selector label/onResume
correction complete,12 focused tests/40s build; original key/default/native TV
detection and preserved-playback resume priority retained. No MiniMX install/
app input/matrix yet. Current built startup candidate differs from installed
tablet566; do not conflate APKs. Root read-only decoder query correctly named
dev_codec_capabilities now exposes Android6 Am CLI rejecting
--receiver-foreground; corrected equivalent -f0x10000000 now passes actual222/
API23 and51/API33 queries,131 MCP/49 affected contracts, no ordered retry.
Actual22 MiniMX video entries: pre-API29 name classification9Amlogic hardware-
labelled/13Google software, not runtime proof; MPEG-2/AVC/HEVC advertised.
HDMI USB capture is absent from both host DirectShow and
Windows PnP, user asked asynchronously to reconnect; only integrated camera/
Logi Capture present, do not launch/use them as substitutes. Need actual USB
presence before physical HDMI claims. First capture failed before opening any
camera; root env codec/config attempts were tooling failures, not device crash.
No live physical gate/process remains. No MiniMX install or app input occurred.
Build contains startup UI correction; tablet remains on earlier validated566.
Need MiniMX guarded install/normal-launch settings-return test and matrix after
current-device ordering choice; physical HDMI evidence waits on USB connection.
CLI cleanup now complete: dispatch closes its own AdbClient in finally on
success/early return/exceptions, four focused tests and all135 MCP tests pass.
Actual read-only MiniMX connection passes5.636s and leaves no orphan shell.
Three earlier abandoned CLI shells were verified by exact target/argv and
terminated individually; shared ADB server, authorization keys and apps remain
untouched. Both orders213 reviewed; device matrices still open, no repeated
completed playback rows. Startup candidate is built but not installed on MiniMX.

### Prior parser-boundary checkpoint (historical)

NOW: caption-only malformed708 protection candidateC56c631a builds60s,4
signature JVM tests pass. Adapter6 tests running session65840. Both factories
wrap only CEA708 default decoder; exact IllegalState/Cea708.processCurrentPacket
+ParsableBitArray.assertValidOffset becomes SubtitleDecoderException. Existing
TextRenderer handles that locally, releasing its held buffers/replacing only
caption decoder; no manual flush, broad catch, whole-player recovery or extra
logs. Media3 setOutputStartTimeUs is forwarded. Non708 default decoders/data
unchanged. Verify adapters, inspect/install, repeat affected caption row next.

Installed ABE23b37 native-source preference failed initial48.188s before CC
cycle: actual Cea708Decoder offset failure caused FAILED_RUNTIME_CHECK/A-V
reprepare. Keep174130 state/log, do not call this an app-process crash. New
guard addresses that observed path. Side-only candidate725 remains rejected;
native-only isolation76.150s/readable172011 proves native preference direction.
All four decoded audio rows on725 pass: Media357.795, legacy52.952,
GSY/Media358.402, GSY/legacy53.473; hardware AVC/decoded PCM,15 prefs restored,
no speaker audibility certification. Container source validator passes; native
Windows host invocation hit generated Linux symlink WinError1920, not source
failure. Use dev.cmd validate/container workflow, not host full-tree scanning.
After caption/Copy20s proof: native/optional transformed DVD, rotation/missing
codec handling, compact results and retirement. Both orders205 match.

### Previous native-preference implementation checkpoint (historical)

LATEST: encoded-only isolation tab232-cea-native-only-isolation PASS76.150s,
settled172011 snapshot shows clean readable PTS75.5/76.0/76.5 against burned
77.411. That is actual source-selection evidence. New implementation removes
failed side-preference from Exo sinks; FixedCaptionSideChannelClient prefers
actual native CEA reported by existing MiniPlayerPlugin.hasObservedCeaCaptionData.
Side polling/cursor continue, duplicate pending ingestion/emission suppressed;
encoding with no native CEA retains its original source tap. No new interface,
Base/Core-server change or continuous instrumentation. Build session39045;
2 authority/38 automation contracts pass. Current installed725 remains the
rejected side-preference test candidate until the running native audio batch
finishes; DO NOT install while that batch owns the tablet. Batch runs Media3,
legacy and both GSY delegates on stock175 Breakfast, decoded output/offset0,
two-track changes. Guard preserves15 prefs/borrows manual awake owner per row.
After batch/build: inspect/install new candidate, repeat affected232 CEA visual
and controls; Copy20s continuity; native DVD/rotation/missing-codec gates.
Both task/order stamps204 match. Canonical381 is unchanged; no release/commit.

### Previous source isolation checkpoint (historical)

NOW: encoded-CEA-only isolation (side channel OFF) is running under
tab232-cea-native-only-isolation.*. Current installed72565054 builds62s,
3 policy+8 bridge JVM/52 backend/2 authority contracts pass. Its attempted
side-channel preference does NOT fix malformed pre-seek captions; physical
75.024s fails first FF verification on audio_stalled_after_recovery. Preserve
20261007-171259 malformed CC1 and171315 failure state/log. Never claim source
duplication is the only cause or this trial a completed fix. Next use isolated
native callback output to decide production correction. A server decode/UDP
path defect remains possible, unproven. No Core/plugin or Base player edit.

Completed167 boundary: stock175 ordinary Fixed/Exynos H.264105.145s, explicit
unsupported_video_stock_fixed, seeks/jumps/pause/STOP-rewatch;15 restored.
Installed167 stream SHA/55019483bytes independently match;725 not yet streamed.
232 ordinary Fixed wire88.305s passes but settled165949 screenshot overlaps
PTS strings, so visual FAIL. Runtime9186 proves VAAPI encode/CPU decode, GPU
tool unavailable; not full GPU or owned Direct. GSY/Media3 native Teletext
94.872s and GSY/legacy native DVB87.817s independently readable/passing.
Owned Copy DVB70.148s short5s continuity rejects after visible cue resumes;
actual subtitle packets every1-3s in first40s, so a silence explanation is
not proved. Repeat20s, same minimum progress; native GSY/legacy20s succeeds.
Then decoded audio tracks, valid-DVD capability, rotation/missing-codec safety.
Both orders203 prioritize TABLET-CC-001. Fifteen preferences restored after
each group; manual keep-awake owner active. Canonical381 APK unchanged.

### Superseded MIM negotiation/fallback experiment details

CURRENT DECISION supersedes the experiments described below: source-capability
advertisement and full-session fallback failed their physical gates and are
withdrawn. Do not advertise MPEG-2 on this MPEG-2-less tablet. Current production
correction guards requested owned Transcode before Core negotiation when native
H.264/MPEG-2 SD/HD Pull fallback is unavailable, reports
unsupported_video_stock_fixed and uses ordinary Fixed; Copy/off unchanged.
Full owned Transcode support remains open pending a negotiated stock-plugin
watch-recovery contract, not an MCP runtime dependency. Native Exo/GSY decoder
and BaseMediaPlayer runtime were not changed by this failed experiment.

Safe candidatef934e61c builds91s (5 policy/12 session JVM) and is installed
update-r on tablet. Toast clarification is building session61450. Native
GSY/Media3 Teletext remaining gate session82466 uses the correct configured
Breakfast-26711345-0.ts on175, candidatef934. Earlier incorrect-path
Breakfast-26719737 test is a fixture invocation error, not a runtime defect;
settings15 restored. Wait for both, inspect/install latest then use existing
mcp_session_test --mim-direct-mode transcode --expect-mim-stock-fallback
unsupported_video_stock_fixed on175 and232 with normal controls. DO NOT use
the Direct-only fault on this now-unsupported tablet. Fault harness rejects
unready negotiation;175 MIM API is presently unavailable (no silent install).
Earlier175 run nevertheless proves ordinary Fixed/Exynos H.264 A/V, not fault
recovery. Full-session candidateb937 failed106.251s with connected main menu
and no active player; retain unique negative evidence. Both orders202 match.

Remaining native caption/audio/DVD/rotation and safe missing-codec handling
still run on tablet; no other-device matrix/release or Core/plugin mutation.
The detailed failed experiments below are historical provenance only.

TOUCH-001 is closed: normal unset touch-hold opens NAV_OSD; bottom-right rows
are aligned. Actual tablet stock175 SageTV7 and232 touch/menu tests pass.
The user requested continuing the full Samsung SM-P610 Android13/API33 matrix;
no new release/commit or other-device full matrix is authorized for this task.
Wireless alias tab-s6-lite uses persisted SDK keys in opensagetv-vibe-dev.
15 current preferences are restored after every guarded group. Manual awake
owner remains active; restore its exact original power settings after testing.

Forty available native hardware rows (Media3, legacy Exo and both GSY
delegates), four typed lifecycle/control paths, supplemental Media3 HEVC/VPx
visual review and native Media3/legacy/GSY caption subsets pass. No native
Android MPEG-2 decoder exists on this tablet. IJK UK AVC remains blank with
Exynos0x8000100b despite clock advancement; new debug first-frame oracle
rejects it and accepts independently visible progressive AVC MP4/TS. The
probe-packet-retention experiment failed and was fully reverted. Do not
claim IJK video PASS, software fallback or a proven interlace/CSD cause.

TABLET-MIM-001: compared stock-compatible MiniPlayer codec checks read-only.
Negotiated Transcode now adds MPEG-2 input acceptance only with native H.264
output support; Copy/off/missing plugin and existing MPEG-2 lists are unchanged.
Candidate3cc1cf3a physical232 test starts active_transcode/MIM_DIRECT, actual
Exynos H.264 video and readable generated CEA. FF/REW/caption/pause controls
recover, but settled ownership FAILS on restart HTTP502; keep exact failure
raw20261007-162109 and tab232-owned-transcode-cea-corrected.*. Fresh MIM status
shows VAAPI decode/encode for that fixture and zero jobs after cleanup, not
yet independent active GPU-load proof or full gate PASS.

Fallback candidate2120880f builds76s and installs update-r,15 prefs retained.
5 policy/13 session JVM,60 MCP ADB and6 corrected lifecycle contracts pass.
Debug fault can force only Direct creation failure and keep the real Pull
source. That physical test FAILS: pre-first-frame track detection requests one
reconnect, but native socket reconnect retains cached source capabilities and
stays audio-only. Current code switches only unsupported-video reasons to the
existing fresh-Activity/session handoff; rebuild session10731 was running.
Resume by polling build, inspect/install candidate then rerun real unsupported
fallback on232 and stock175; do not repeat the40 unaffected native rows.
Use guarded existing mcp_session_test --mim-direct-startup-fault direct-only
--streaming fixed --mim-direct-mode transcode --mim-direct-deinterlace off
--server-path ...VibeSeekTest-1080i-MPEG2-AC3-CC.ts --restart-from-beginning.
Fault cleanup and settings/awake restoration are explicit. No Sage.jar,
plugin/server restart, private event or saved preference mutation was made.

After fallback: investigate restart502 with fresh jobs/readable captions,
remaining tablet native/owned/DVD/rotation gates, actual232 STV check, compact
matrix update and retirement.175 lacks GPU/has restricted CPU, so software
transcoding there is not a GPU pass. Canonical firetv APK stays381ae608;
current build output is a test candidate, not a published release. Refresh
manifest after documents settle. Both task/order stamps201 match.

### Superseded investigation checkpoints (historical, not resume instructions)

Priority now TABLET-IJK-001: fresh settled stock175/IJK Taskmaster screenshot
and8s recording remain blank while position1316974..1327110 advances; actual
OMX.Exynos.avc.dec errors0x8000100b and repeated dequeue exceptions. Old
IJK controls/lifecycle were weaker clock-only verdicts, NOT complete video
PASS. Preserve unique failure raw under active DEVICE-002. No fault attributed
to interlace/CSD yet. Original upstream0.8.8 source review shows its distinct
MediaCodec path; no native-library rebuild, codec blacklist, SW switch or Core
change implemented. Compare progressive generated AVC MP4/TS next.
Debug-only PlaybackHealthProbe/MCP now expose existing first-video callback
and reject known blank-video IJK clock-only startup/recovery. Other backends,
audio-only IJK and older-debug weak fallback are unchanged.3 source contracts,
38 playback automation and25 MCP health tests pass. First MCP test invocation
lacked config/device environment; corrected normal TOML invocation passes.
Build33s/inspection pass, new debug-only candidate
a128cb3dd9ea6192d16692da7737e77078e5f1a4c21d8f1dd816e3b8ded2f4ed
is being guarded-installed on tablet. Typed-backend production40 rows remain
valid on recorded67 candidate; this oracle change needs only affected IJK
negative/positive proof plus representative typed-path smoke, not all devices.
All15 current preferences remain, manual keep-awake owner active. Canonical381
APK stays unchanged. Other missing tablet caption/DVD/owned/visual gates follow.
Both orders199 put targeted IJK investigation first within DEVICE-002.

The a128 debug oracle was guarded-installed; negative Taskmaster now fails
with firstVideoFrameRendered=false, while generated AVC baseline MP4 and
high/B-frame progressive TS pass with true signal, independently visible
burned labels and actual OMX.Exynos.avc.dec/no native errors. Progressive TS
also logs csd-0:naked, so do not attribute failure to container/CSD alone.
Rejected single-variable experiment retained FFmpeg find_stream_info probe packets
only on Samsung SM-P610/API33 hardware Pull. Push/software/all other devices
and OS versions retain original nobuffer option; packet-buffering remains0.
Source-reviewed FFmpeg3.4 NOBUFFER drops initial probe packets, so missing
parameter sets are a hypothesis, NOT yet proven causal correction. New3
policy JVM/52 backend contracts and57s build passed; guarded-installed B6383208
still fails exact Taskmaster startup with firstFrame=false (81.403s),15 prefs
restored. Runtime flag/call changes reverted, our experimental Java test moved
recoverably into root deleteme, rebuilding baseline runtime plus debug oracle.
Do not keep/commit that unproven runtime option. Short filtered follow-up log
was empty, so it does not independently prove an unchanged native error code;
the rejected first-frame result is the actual experimental observation.
Native Media3 extended controls79.518s and Legacy Teletext97.218s pass;
GSY/Media3 DVB73.964s and GSY/legacy Teletext98.744s pass transport,
GSY final screenshots independently show readable local DVB and STV Teletext.
15 settings restored each. Other tablet visual/DVD/owned rows remain; no new
fixtures/server mutations. Resume remaining supported-backend gates after
guarded installation of rebuilt debug-only candidate. TABLET-IJK-001 remains
open; no native hardware fix claimed.

Current tablet candidate67e07f953f11543412c7cfd8c22086c0563193638e2fba204c8556c075c1a343
is installed with15 current preferences retained (initial touch checkpoint14).
Installed base.apk streamed SHA256 matches67e07f95/55019483bytes.
TOUCH-001 default NAV_OSD is only
for absent one-finger touch mapping; explicit OPTIONS/NONE/custom and remote/
multi-finger mappings unchanged. Two right-bottom rows share four columns,
IDs/actions unchanged, notouch grid preserved.4 resolver+8 focus JVM,
2 touch+18 remote+4 layout contracts, build/inspection pass. Actual finger
hold opens175/232 navigation; final175 Media3 hardware AVC/decoded AC3 plus
seek/pause46.813s passes, Audio/Video/CC taps show correct readable panels.
Final232 main-menu screenshot confirms aligned rows and touch close works.
Compact result artifacts/results/TOUCH-001/result.json; completed raw retired
recoverably, preserving tablet codec inventory/settings/keep-awake checkpoint.

User completed both normal wizards. Actual generated tablet identity privately
persisted in ignored alias tab-s6-lite, never DEV001. Initial14 preferences
checkpointed, keep-awake manual owner remains active with original7/2147483647.
Nested gates borrow it; end once testing finishes. Installed381 baseline was
stream-hash verified before correction; do not mislabel later67 media as381.
Canonical artifact still381 until final candidate promoted; testing67 build
output directly, no Git release/commit or changes to other devices/servers.

DEVICE002 runs stock175 full supported hardware-codec rows using existing
strict matrix harness: Media3 batchesA/B ten rows pass; actual no-platform
MPEG2/MPEG4 hardware rows stay separately unsupported, not fake HW passes.
Fix proven failures, preserve settings after every row group. Then legacy Exo,
GSY Media3/legacy and IJK/lifecycle/captions/DVD/optional232 owned modes under
existing acceptance. ADB software visual evidence works; no physical speaker/
HDMI-sync certification. Broader other-device matrices remain closed.
Media3 stock175 batchA eight actual hardware rows PASS257.722s: AVC baseline/
high-B, HEVC Main/Main10, VP8, VP9 Profile0/Profile2 and AVC resolution switch.
BatchB discontinuity/audio-track switch passed under
artifacts/active/DEVICE-002/tab175-media3-codecs-b.*; no unsupported hardware
treated as positive. BatchA restored all15 preferences (one legitimate new
pref since original14 snapshot); manual awake borrowed. Both orders197 tablet
first. Active schema's codec directory is enabled; adaptive per-file storage
warm-up stays enabled, no search-first or private Core control.
Media3 batchB PASS172.166s, both discontinuity/audio-switch AVC hardware rows;
15 prefs restored. Legacy batchA eight rows PASS321.802s with new opt-in
capture-each-case; burned names/picture visually reviewed independently,
not an automatic screenshot PASS. Legacy batchB two rows PASS174.576s,
both actual AVC hardware with screenshots. GSY/Media3 batchA eight rows
PASS319.174s; resolved delegate required,15 prefs restored. Current GSY/Media3
batchB PASS174.533s, both remaining AVC rows. GSY/legacy batchA PASS450.721s
(8 rows), actual legacy delegate/Exynos hardware required; batchB PASS174.871s
under artifacts/active/DEVICE-002/tab175-gsy-legacy-codecs-b.*. All40 available
hardware codec rows now pass (10 each Media3/legacy/GSY media3/GSY legacy).
IJK stock175 Taskmaster seek/jump/pause/STOP-rewatch PASS77.527s and lifecycle
PASS83.771s (HOME/return, user-pause persistence, replay and teardown),15 prefs
restored. Standard health decoder/counter probe is unsupported for IJK, so no
hardware decoder name/counter PASS inferred. One post-controls screenshot was
solid brown with unsupported health fields; investigate independent fresh
rendered IJK video before calling visual pass (could be between-state capture).
Old lifecycle/control oracles returned success: Media375.664s, Legacy77.793s, IJK83.771s,
GSY/Media379.293s and GSY/legacy80.346s. HOME/return, Surface recreation,
user-pause persistence, exact-source replay and teardown independently pass,
15 preferences restored after each. The IJK first-frame defect invalidates
its full playback verdict, not the other four typed-output backend paths.
Stock175 Media3 DVB69.364s, Teletext STV117.232s and Legacy DVB70.872s pass;
Media3 screenshot confirms readable bitmap, CC1/CC2 text and clean Off.
Legacy DVB screenshot still needs visual review. Native controls Legacy78.174,
GSY/Media378.466 and GSY/legacy79.076 pass; Media3 extended controls pending.
Legacy/GSY A/B labels reviewed; clean EOF may show normal STV cards/preview,
not full-screen certification. Remaining visual/caption/DVD/owned gates are
open. Both orders199 reflect targeted IJK investigation first.
38 automation/source and
two new capture tests pass;1568-file manifest written before latest doc edits,
refresh/check again after recording current evidence.
Two capture-tool source tests pass, no APK change. Review burned filenames
and picture/counters; physical speaker/HDMI sync remains unmeasured. Media3
initial ten rows retained as hardware/output proof; supplementary visual MIME
controls remain before full tablet closure, not an unrelated matrix restart.
Read-only public captions.get verifies175's actual SageTV7.xml profile; ignored
tablet stv_by_server.stock saved accordingly, not the older Non-Pro SageMC.
232 STV file must be verified when its tablet context is active; ui.state does
not contain stvFile, and175 context is not queryable while connected only232.
Touch closure23 raw files retired recoverably (18 own/36141431bytes plus five
completed DEVICE002 setup/default screenshots); result stays under results,
active codec/inventory/settings/cache evidence retained. A misplaced new
screenshot was corrected before retirement; empty accidental artifacts/
TOUCH-001 remains after no-delete recovery. Keep owned moves recoverable.

### Prior commissioning checkpoint (revision194)

User explicitly starts full Tab S6 Lite tests without HDMI and asks for no
idle sleep. Actual .51:42491 SM-P610/Android13/API33 is authorized, current381
guarded package/update install succeeds (fresh Dev package, no data clear).
Installed base.apk streamed SHA256 exactly matches381ae608 (55019483bytes).
Launch normally without DEV001/server override; manual initial SageTV setup
must precede automated media tests and actual generated identity recording.
Use streamed ADB screenshots/bounded screenrecord plus independent decoder,
Surface/render/A-V/server/crash evidence. No speaker/HDMI physical-sync PASS.
Durable tablet matrix rows are now in PLAYER_SERVER_COMPATIBILITY; ADB,
install and inventory pass, all media/layout/control gates pending. Stock175 first,
optional232 owned/GPU later, preserve other completed MATRIX003/004 rows.

Tablet manual keep-awake checkpoint active; initial values7/2147483647 were
already set and recorded exactly. Nested gates borrow this owner. End it
after tests or prolonged setup pause and start fresh on resumption; do not
overwrite user power settings permanently. Raw artifacts scoped DEVICE-002.
Both orders194 tablet first, no new runtime fix/Core patch/release.
Normal launcher screenshot works (2000x1200). Actual read-only29-video/35-audio
codec inventory advertises Exynos AVC/HEVC hardware but no Android MPEG-2
decoder at all. Do not label its MPEG-2 hardware row PASS or invent a client
hardware fix; test absence/safe handling separately from native IJK/software
and optional232 GPU-transcoded AVC paths. APK namespace may have no MPEG-2
decoder even though another engine bundles software support.
User was asked to tap175/complete normal first-run until SageTV main menu.
Latest snapshot disconnected, client ID blank; no commissioning override or
automated media start until that required manual step is complete.

### Prior DVD closure checkpoint (revision193)

Current381ae608 on stock175/non-Pro25 closes timed-skip acceptance without
new production playback/Core/STV/key changes. Real remote settled FF/RW,
independent stock public skips, actual source/server clocks and HDMI elapsed
label advance. Pause holds0ms/resumes A/V. STOP/exact ALADDIN rewatch161.181s
passes target3195000/actual3180393 (approximate accepted), cadence0.999477x,
video459/audio298. Rewatch remote repeat58.322s passes FF1.000487x/max474ms
and RW1.000383x/max642ms; HDMI settled54:30..34/54:32..41 advances before
normal auto-hide. Initial seek-guess labels are transitional, not precision
proof. Historical bb2793 frozen label's root cause remains unproven; do not
restore the reverted64-to1024 history experiment or claim a new causal fix.
All113 preferences restored; existing manual keep-awake owner borrowed, not
ended. Non-Pro playback exited and Dev app stopped after verification.
Compact result artifacts/results/DVD-003/timed-skip.json;76 current completed
raw files/497959657bytes retired to root deleteme, historical DVD002 mix retained.

User Tab S6 Lite51 readiness passes: default5555 refused, user TLS connection
port42491/pairing45773; paired using persistent container identity, actual
SM-P610/gta4xlwifi Android13/API33 ARM64+ARM32, timeout0 verified. No Dev
package found, no tablet install/media/settings profile or invented ID.
Ignored alias tab-s6-lite updated with actual inventory; refresh TLS port if
changed. Pairing code not retained. Explicit ADB serial forwarded in both
Windows lists/Linuxallowlist, eight source tests; do not probe active25 by
accident. DEVICE-002 remains unchecked for settings-preserving install,
manual normal first-run and stock175 media gates when testing starts.
Both orders193 remove completed DVD003; matrices not restarted. Eight clock
oracle/22 MCP/eight wrapper/four order-retirement tests and structure pass;
1565-file source manifest refreshed. No APK rebuild needed
for these harness/filter/docs changes. No Git commit/release this turn.

### Prior active proof checkpoint (revision192)

User switched HDMI capture to non-Pro25 and added DEVICE-002 for Tab S6 Lite
51 after DVD-003, with ADB readiness in parallel only. Ignored alias tab-s6-lite
now persists .51:5555; actual model/API/authorization still unverified, no
client ID invented, no tablet APK/media/settings changes. Initial native-wrapper
SAGETV_ADB_SERIAL override selected25 instead; not .51 proof. Use proper
persisted alias and inspect forwarding before another result claim.
DVD003 current381 baseline clocks advance after both timed skips. Strict
remote-tap settled gate57.282s passes both source/server/A-V windows after
excluding old draining health_flushed=true frames: FF0.999623x/5308ms/max310ms
server-client difference, RW0.999920x/12565ms/max387ms. HDMI label contact shows
59:09/10/12/13/14 and59:16/17/18/19/20 then normal auto-hide; standalone
unpolled clip62:12/13/14 advances. Original historical freeze not reproduced.
No new production playback patch. Added bounded timed-skip regression/oracle
(eight tests) and restored already-existing dvdNormalSourceClock through MCP
compact filtering (22 bridge tests); no APK change. Prior falsely failed
strict FF row included old still-flushed output, not a proven new defect.
Finish focused repeated remote/source/visual proof, pause/resume and exact-path
rewatch before closing DVD003; keep its original source-profile/settings.
Both orders192, completed other-device matrices retained, no commit/release.

### Prior resume checkpoint (revision191)

User now requests completion of previously deferred DVD-003. Non-Pro25
has381ae608 installed,113 preferences retained and SageMC's intentionally
selected Default DVD FF/REW timed-skip profile. Use stock175, exact ALADDIN
path/normal native Media3, compare actual post-FLUSH decoded source mapping,
server GetMediaTime and visible elapsed label, not accepted seek estimates.
Original mixed/open evidence remains under active/DVD-002; current raw goes
under active/DVD-003. Do not restore unproven1024-anchor enlargement, remap
keys or change SageMC settings/Core. Current USB HDMI is ONN Pro50; supported
public SkipForward/SkipBackwards provides an independent visual control there
without changing its key profile.175 CPU/no hardware-transcoding constraints
remain part of diagnosis, not automatic attribution for native Push.
Both orders191 put this targeted task first; other completed device matrices
remain measured history. No commit/release or new code/settings change yet.

### Prior completed-matrix checkpoint (revision190)

All requested affected device rows now pass with explicit APK provenance,
not a full historical codec/player matrix repeated on one final APK. Pro
.50/SNA/API34 USB HDMI/stock175 user identity retained; exact current Dev
381ae608 is installed and promoted to canonical artifacts/firetv APK after
archiving0d recoverably. All15 preferences/3+600000 power restored, app stopped.
Stock175 corrected scan46.492s and same9min54.901s both256x/release A/V/PLAY/
TS pass; normal ALADDIN70.112s,0.999803x/10173ms/video579/audio318/pause/PLAY,
22 drops/2 skips/6 gaps/13 nonpositive releases documented. Authored startup
71.975s/menu27.578s and HDMI Languages/yellow SPU highlight pass. Earlier
caption/lifecycle/chapter evidence unchanged. Final232 Transcode115.834s,
strict HTTP/visible CEA/real VAAPI decode+encode/pause/seeks, Copy73.020s and
controlled409 retained-epoch guard29.307s/normal90s recovery pass. Scan-only
native PES change leaves successful HLS/Copy/caption paths untouched.
Targeted shared extraction follow-up on Fire TV non-Pro25/API25:381 installed
preserving113 prefs; startup168.770s (adaptive storage gates retained),
normal1.0x/6230ms/video298/audio195/zero new drops/skips. Public ±2..64
decoder-only scan66.048s and return1x A/V pass, not256x physical-key evidence.
User-selected SageMC timed skips were preserved; first2x remote oracle was
wrong for that profile. First isolation crossed an authored cell and reset
counter928→0; bounded oracle now waits for genuine new output, no production
fix for that test artifact.18 remote/222 affected Python+MCP,10 trick-mode/
12 MIM JVM/build/inspection/structure pass. Other device matrices retained.
Non-Pro app stopped; its existing manual keep-awake owner was borrowed,
not ended.232 active Direct sessions0. No server restart/resource/Core/plugin
change or application-data/key reset.175 CPU/no usable hardware-transcoding
limitation is documented; observed native scan windows had no transcode jobs/
throttling, not a broad claim that server capacity cannot cause stalls.
Compact results: artifacts/results/MATRIX-003/{onn-pro,nonpro,shield,onn-v1}-affected.json;
final inspection there.194 owned raw files/164568930bytes retired recoverably,
plus old canonical0d APK. Keep original unproven232 rejection/175 automation
timeout evidence and deferredDVD003 mixed evidence, settings/keys/caches.
Both orders190 remove completed MATRIX-003/004. Durable docs/compact device
reports and1563-file manifest reconcile affected closure and carry-forward
provenance; task-order/workspace mirror/four workflow tests/whitespace pass.
Non-Pro installed APK streamed hash matches381 (Fire OS has no sha256sum;
read Dev base.apk through qualified container SDK and hash in memory, no
temporary device APK or host ADB). Retirement helper also retired, yielding
195 raw/helper files/164572567bytes plus old canonical APK. Unique original
server/automation failure evidence remains; no continuous observer/replay.
Next Android work is conditional ONN-003/DEVICE-001 when reproduction/hardware
exists, or explicitly approved GitHub source/APK refresh. DVD-003 remains
deferred; store/EXT external prerequisites unchanged. No commit/release.

### Prior repeat-scan checkpoint (revision188)

Candidate381ae6082eda7e87030f95f4a29bae972d1f9a8970fa8e110e8cb21bfc6bde09
is installed with15 preferences preserved. Focused10 trick-mode JVM tests,
81 DVD source checks, build and APK inspection pass. Scan-only PES exclusion
passes first full scan46.492s and identical public9min Seek/repeat54.901s:
both FF/RW reach256x; release restores advancing A/V, PLAY cancel and TS
separation pass. In repeated row EMPTY samples report0ms ahead, compared
with6.020s at the prior failing RW64 probe. This is bounded evidence, not a
claim that every possible scan stall is eliminated.
Concurrent88.890s stock175 CPU11.982% mean/69.051%peak of one core,
throttling0/no transcode jobs. User-confirmed175 CPU/no usable hardware
transcoding limitation is durable in TEST_ENVIRONMENT/PLAYBACK_DIAGNOSTICS;
do not confuse native Push navigation with software video transcoding.
Normal ALADDIN cadence/pause/audio gate active under
onn-pro-175-aladdin-audio-fixed-normal; authored menu/audio and affected-only
shared DVD scan follow-up remain before correction/parent closure.15 prefs
and original power3/600000 restored after scan; canonical0d not promoted.
Both orders188 reviewed; no commit/release/Core/resource changes.

### Prior scan-only experiment checkpoint (revision187)

Drain-field retry PASS49.317s both256x, but public Seek back to543200/approx9m
reproduces RW64 stall FAIL60.028s (onn-pro-175-dvd-scan-9min-drain).
Key probes: lastPushFlags256 EMPTY, epoch18898944==read18898944, scan capacity
1048576 free, video277/input261 fixed while buffered tail1079→6020ms ages
down. Concurrent90s CPU11.710% mean/34.684%peak of one core, throttling0,
no ffmpeg/MIM/transcoder. This observed wait is not server video encoding;
do not broadly claim no host contention. Raw1048576 advertised scan capacity
is not the normal4MiB physical capacity.
Pinned Media3 ProgressiveMediaPeriod1.11.0 onLoadCompleted derives EOF
duration with includeDisabledTracks=true. Hypothesis: muted normal-speed
DVD AC-3/PES still queues future samples and prolongs the short scan EOF tail.
Experimental DvdPsExtractor now skips only scan audio before PTS adjustment/
queue emission (generic MPEG audio and private0x80..87 AC-3), leaves private
SPU and normal1x audio intact. New source regression red-before/81 DVD tests
pass; focused DvdPsExtractorTrickModeTest/build active (session70985).
Do not call this fixed or publish until identical9min scan and normal/movie/
authored menu/audio gates pass. Current installed5f remains prior code.
Both orders187; no server/Core/resource modification, no matrix restart.

### Prior drain-probe checkpoint (revision186)

Stock175 controlled ALADDIN startup PASS80.249s/543093 approximate540000,
default storage warm-up retained,15 preferences/power restored.
Instrumented scan FAIL42.234s: FF256, RW128 video303/input311 freeze until
release; normal A/V returns. Preserve102920 failure. Overlapping180s stock
container context:36.052% mean of one core/263.984%2s peak, throttling0,
no ffmpeg/MIM/transcoder processes. Not a video-transcoding bottleneck; do
not exclude host contention or assign root cause from averages.
Found MCP filter silently dropped existing dvdEpochPushedBytes and
dvdDecoderBufferedAheadMs, so prior row's ahead was null. Expose only those
already-computed debug values; new red-before regression/21 bridge tests pass.
Expanded existing bounded scan probes with raw/free/clock/reply data, no new
healthy instrumentation or APK change. Current scan retry under
onn-pro-175-dvd-scan-drain-probes (session87674) and90s server CPU helper
under onn-pro-175-drain-scan-cpu (session97745) active. Poll results; inspect
stall bufferAhead/free-space/input/drain/ack before any production change.
31 caption authority tests pass with explicit MCP config/PYTHONPATH.
Both orders186; current5f419983 remains installed. Canonical0d not promoted.

### Prior final-MIM checkpoint (revision185)

Controlled409 Copy guard passes and next normal public90000 seek passes
91535ms, active_copy/HTTP with video94/audio74 and no error; owner exited.
Final5f419983 Transcode PASS115.834s, FF2969/REW3092ms/pause, strict active
HTTP startup+settled, packets810/wire1638, HDMI video42.142/readable40.5/41/
41.5 CEA, audio mean-21.8/peak-7.1.15 preferences/power restored. Current
affected source structure passes,52 backend Python/12 MIM JVM/build/inspection.
Stock175 ALADDIN controlled retry active under onn-pro-175-aladdin-controlled-start;
concurrent180-second external CPU/process/throttle probe active under
onn-pro-175-controlled-start-cpu. Then instrumented dedicated scan/HDMI.
No server/CPU-limit change; earlier idle probe is not fault attribution.
Both orders185, parent still open for DVD scan. Canonical0d not promoted.

### Prior negative-gate checkpoint (revision184)

Controlled real409 gate PASS29.307s on5f419983: normal public60000 seek,
then3 disposable Copy slots at the verified four-session limit. Rejected
150000 request preserves old media67014→69796 (progress2782 over2999ms),
video423→603/audio245→339, advancing/error-free and safe
restart_failed_session_retained_http_409_direct_session_limit state. All3
gate-owned tokens teardown200; never retire Android-owned producer.
15 preferences/power restored. Normal90000 public seek/recovery active;
then final positive Transcode and175 scan/resource-window investigation.
This proves the client failure-path fix, NOT original server rejection cause.
Both orders184; no commit/release.

### Prior rejected-seek checkpoint (revision183)

New candidate5f419983298dfbfc8cf762c191ba2b60150970771e43eb5e13ade10f09c693ca
installed,15 preferences preserved. Correct actual client error: when Direct
restart returned null but retained ownership, Base returned false and Exo
backends applied an absolute local seek to the old HLS epoch. Now consume
that failed owned request, clear pending intent and report failure; do not
shift old stream or silently claim landing. Ordinary stock paths unchanged.
52 backend Python (new red-before regression),12 MIM JVM/build/APK inspection
pass; reviewed canonical Base hasha8655ada pinned in both validators.
Initial post-install restore RPC timed out; next guarded start/roundtrip
proved original15 preserved/restored. Current active Copy started44.387s.
Controlled real409 Direct session-limit gate active under
onn-pro-232-copy-rejection; uses3 disposable Copy slots at nonzero60s epoch,
never stops the Android-owned token, and tears down only its own tokens.
After proof, final positive MIM rows and stock175 scan remain.

Prior343de9 final Transcode PASS133.964s (FF1219/REW4692/pause, strict owned
startup+settled, packets628/wire1576, HDMI readable29/29.5/30 at30.464s;
audio mean-21.8/peak-7.4). Copy PASS73.020s. Retry is not original-cause proof.
Stock175 ALADDIN repro startup FAIL123.391s MCP responseid16 after cold
warm-up cleanup; diagnostic shows no fatal crash and app disconnected.
Do not call this a decoder or CPU fault. Post-failure idle resource probe
59.734s:5.159% of one core, throttling0, no ffmpeg/MIM/transcoder processes.
It does NOT measure the preceding failure window. Resource helper is disposable
workspace artifacts/temp/capture_server_cpu_context.py; use bounded sampling
alongside the next actual scan. Keep original open failures.
Both orders183; canonical0d remains. No commit/release/Core/plugin change.

### Prior diagnostic checkpoint (revision182)

Candidate343de9f966569fddbb1b6a938ebdaa0649ef8540cc5ff182e6218b51ef98a8b0
is now installed on Pro,15 preferences preserved. Only packaged addition since
509c is safe MIM restart rejection reporting: status/closed error vocabulary;
retention, source/seek/retry logic unchanged.12 MIM JVM tests,51 backend Python,
assembleDebug and strict APK inspection pass. Canonical0d remains unchanged.
Strict232 Transcode rerun active under onn-pro-232-transcode-diagnostic with
HDMI watcher. Independent server Direct start/39000/31000 restart probe all200
in2.297/2.797/2.828s, then all3 tokens torn down. This is not a proven fix of
the intermittent retained restart. Investigate the new safe numeric failure
category; next Copy and175 instrumented DVD scan with server CPU context.
Both task orders182; no commit/release or server/Core change.

### Prior HTTP checkpoint (revision181)

2026-10-07: user completed232 wizard; screenshot094834 and menu hint
Dynamic Menu by nielm verify actual SageMC Main Menu. No manual setup block.
User preferences now15; current runs checkpoint/restore15 plus3/600000 power.
232 Transcode commissioned gate FAIL136.230s: startup owned HTTP and real
VAAPI/h264_vaapi decode+encode, visible CEA, FF3216/REW1855ms and pause pass,
but final state restart_failed_session_retained_IOException. Playback retained
its prior session; this is not strict seek success. Preserve095119 failure.
Isolated server Direct start/39000/31000 restart probe active to distinguish
server rejection from client protocol handling before production correction.

User explicitly notes175 cannot hardware-transcode and is CPU-constrained.
Documented in TEST_ENVIRONMENT/PLAYBACK_DIAGNOSTICS: keep175 stock reference,
GPU gates232, record actual transport/jobs and resource evidence. Read-only
Docker inspection identifies175 host-network sagetvopen-sagetv-server-java11;
both containers have quota/period/NanoCpus0 and empty cpuset, so no explicit
cgroup CPU cap is proven. Both expose /dev/dri; mapping alone is not evidence
of working175 driver acceleration. Native DVD/Pull do not server-transcode,
but server navigation/I/O/Push can still be delayed by CPU contention.
Do not attribute scan64 to CPU or client without runtime measurements.
Both orders181. Current509c still installed; no new packaged change/commit.

### Prior wizard checkpoint (revision180; superseded above)

User's question whether232 setup is also needed: YES. The first232 Transcode
gate stopped32.409s BEFORE Watch, with Configuration Wizard - Choose Language
for the Pro's actual new4b5246514c48 identity. This is not a Direct decoder or
transport failure. Corrected earlier advice that175 setup alone sufficed.
Normal232 wizard is now on screen; wait for user completion before touching
physical controls. Its failed preflight restored9 preferences/power.
Then run strict232 Transcode/Copy with HDMI and independent server GPU state,
and return to stock175 for the remaining scan investigation.
The scan gate now preserves its last full bounded state and decoder/input/
drain/UI probes, and captures diagnostics BEFORE cleanup PLAY.17 remote tests
pass (new regression failed before). No production behavior or APK changed.
Existing509c remains installed, canonical0d remains until final proof.
Compact measured current results: artifacts/results/MATRIX-003/onn-pro-affected.json.
Retired70 completed independent Pro raw files/90328716 bytes recoverably to
root deleteme preserving paths; retain open scan/DVD/232 setup evidence,
warm cache, keys/config and current APK/candidate inspection. No deletions.

User now explicitly requests finishing MATRIX-003 on ONN Pro and fixing client
failures; this supersedes the deferral in revision169 below. .50 is onn/SNA/
Android14/API34, ADB authorized and non-expiring0 verified. Installed exact
current0d55918 Dev without reset; user completed manual175 first-run setup and
switched USB HDMI. Paired Main Menu captures verify route/current175 idle UI.
Save actual generated client4b:52:46:51:4c:48 in ignored onn-pro alias; do not
use DEV001 or apply the v1 decoder exception to SNA without reproduction.

First stock175 Media3 fast CEA/CC cycle/FF-REW/pause PASS114.439s:
wire1793, FF1278/REW1316ms and actual HDMI video46.046/readable44.5/45/45.5
captions.9 setup prefs restored, originalstayOn3/systemtimeout600000 restored.
GSY legacy UK AVC lifecycle PASS85.437s, actual Exo2/c2.amlogic.avc.decoder,
init1/release0, foreground outputs322, same connection/manual-pause/replay/
teardown. SNA does not need the v1 exception for this proven AVC row.
Direct legacy MPEG-2 HOME FAIL108.374s: video222/queued173 fixed while
audio1631/head2494080 advances, Surface valid/shown1920x1080, no error,
connection1 and decoderinit1/release0. Preserve032929 failure. Add only observed
SNA/API34/c2.amlogic.mpeg2.decoder to shared supported replacement policy;
Pro AVC remains normal and v1 tuple unchanged. Build/policy JUnit/139 Python
and APK inspection pass. Candidate509c01ad22ad069bcd0ac4fcf5481b0a94846f30646917f695fb1c56a0059714
installed on Pro preserving9 prefs; canonical still0d pending proof. Exact
failure rerun PASS79.957s under onn-pro-175-legacy-mpeg2-lifecycle-fixed:
same connection, video317→488, decoderrelease1/init2, audio312, user PAUSE
retained/explicit PLAY/teardown,9 prefs/power restored. Media3 MPEG-2 lifecycle
PASS77.430s under onn-pro-175-media3-mpeg2-lifecycle-fixed. GSY legacy/GDX CEA
PASS114.125s under onn-pro-175-gsy-legacy-cea-fixed: FF1017/REW1333ms,
STV cycle/pause and wire1860; HDMI picture50.150s/readable48.5/49/49.5 captions.
UK Teletext PASS131.195s (STV cycle, FF1019/REW2060ms, pause, wire491,
readable HDMI) and GSY Media3 local DVB PASS93.065s (Off/On,3 cues/4501ms,
FF1021/REW1545ms, visible bitmap) complete. Authored DVD startup active;
authored startup PASS69.277s and root/Languages/root/main PASS27.169s complete,
with actual HDMI menu/highlight. ALADDIN approximate9-minute/cadence/pause
PASS97.727s:543189ms approximate landing,0.999508x/10157ms,video579/audio318,
18 drops/5 skips/6 release gaps/13 nonpositive releases. Actual HDMI picture
captured after startup; early clip is only a Main Menu preview, not proof.
Chapters PASS56.260s. First held scan FAIL31.650s peak64x, immediate retry
FAIL24.009s missed2x ack; settled same-code retry PASS59.715s both256x and
release/PLAY/TS. Keep first failures open: retry is not a runtime correction.
Strict232 Transcode preflight blocked by STV wizard as explained above;
Copy and scan investigation next after manual setup.
Lower-API devices/v1 tuple
unchanged; do not redo their matrices. Remaining captions/DVD/HTTP follow.
All Pro raw evidence stays artifacts/active/MATRIX-003/onn-pro/.
Guard driver checkpoints all preferences and brackets keep-awake per run.
Follow affected UK/GSY/lifecycle/native authored-menu/ALADDIN-motion checks,
then232 strict Copy/Transcode. Keep old devices' completed results. New captures
stay in this Pro subdirectory; never mix its open evidence with retired v1 logs.

Current v1 closure cleanup is finished:184 raw files/138745103 bytes retired
recoverably to root deleteme, old9404 canonical archived separately,0d is now
the canonical debug APK. Compact v1 report, inspection and Pro prep retained
under results/MATRIX-003; keys/config/fixtures/warm cache/source untouched.
139 affected Python, final policy JUnit/build/inspection and source structure
pass. Task orders both180; regenerate manifest after the final tracking edit.
No commit/release performed. Prior169 deferral/install-needed wording below is
historical and no longer the current resume point.

## ONN v1 affected matrix complete; Pro ADB verified (revision169)

Current candidate/canonical debug APK:
`0d55918acbc453c999a2684176ecdd89ca893c57791e55d44bbd51e48f2eb2da`,
version0.5.100, not committed/released in this task. ONN v1 .141 is
askey/sti6140d360/Android14/API34/DEV005. Stock175 first, optional232 afterward.
Compact measured result: artifacts/results/MATRIX-003/onn-v1-affected.json.

Corrected two independently reproduced HOME failures: AVC video froze while
audio continued; MPEG-2 froze both outputs after Surface replacement. Surface
attachment retention alone failed and was removed. Both Exo generations now
use their supported codec-recreation hook only for this verified device/API and
two decoder names. Existing selector/adapter/fallback/library workarounds,
source/audio queues and clocks remain. No watchdog, server seek, remote-key,
Core or plugin change. Final affected lifecycle, visible CEA/DVB, Teletext,
native DVD/menu/ALADDIN and owned Transcode/Copy controls pass.

Copy's initial FF/REW health samples can fail during producer replacement.
Bounded sustained recovery passes without replay, and the strengthened harness
requires active_copy/MIM_DIRECT/HTTP after each control and final settled state.
The final strict row passes73.260s. This is not a zero-latency or Copy side-channel
caption claim. Transcode117.549s proves owned HTTP, visible fast STV CEA and
MIM0.4.9 VAAPI decode/encode with deinterlacingOff. ALADDIN1.000789x cadence
has0 video drops,14 skips and1 release gap; no precision/HD200 parity claim.

All58 typed settings are restored per run; a final private semantic roundtrip
passes. Initial upgrade was byte-identical, but final XML serialization byte
equality is not claimed. Original power0/900000 restored, timeout0 verified,
Dev stopped/launcher foreground and232 caption/Direct session counts0.
139 focused Python tests, vendor-policy JUnit, APK inspection/build and project
structure pass. Source manifest/task-order checks accompany this handoff.
Completed/corrected raw evidence retires recoverably to root deleteme; keep
compact results, current APK, warm cache, fixtures/config and persistent keys.
Historical raw paths below are not live dependencies.

User separately requested ONN Pro .50 ADB in parallel. Saved alias onn-pro,
authorized after approval/retry; actual manufacturer onn, model
onn. Streaming Device 4K pro, deviceSNA, Android14/API34. Non-expiring
adb_allowed_connection_time=0 verified twice. No Vibe package is installed;
no install, launch, client ID assignment or playback row occurred.
Connection record: artifacts/results/MATRIX-003/onn-pro-adb.md.
Its physical matrix remains after publication; fresh Dev setup/manual wizard
and capture commissioning are still required. Do not apply the v1 decoder
exception to SNA merely because both are ONN/API34.

Parent MATRIX-003 remains unchecked for ONN Pro; current v1/Shield/Fire TV
affected rows are complete. Both orders reviewed/reordered169: release refresh
precedes residual Pro matrix, followed by full MATRIX-004 synchronization.
No commit or publication performed.

### Resolved ONN authorization/update checkpoint (revision158)

Authorization recovered with user approval. Verified sti6140d360/Android14/API34,
DEV005 and adb_allowed_connection_time0; initial SDK screenshot and USB HDMI
capture show the same ONN Home app grid/time (promo rotated between captures).
Original Dev app was stopped, launcher foreground. Actual old0.5.91 debug
receiver rejects settings_checkpoint as unsupported_op. Safely force-stopped
only Dev, read private run-as preference XML in memory, installed inspected9404
with guarded install-r, and verified all58 entries/XML byte-identical afterward.
Preference SHA before/after a702949976afd42b14aabfcb955e1c6a1b2a8b4f1015134737bf676f051cbe2e;
no values printed or retained in public output. New private checkpoint/restore
then passes58. No clear/uninstall/new keys/server change. Run stock175 first
with the existing settings/keep-awake guarded driver and onn-v1 override.
Caption/seek, DVD/menu/cadence and lifecycle rows active; optional232 HTTP must
prove ownership. Keep ONN Pro pending; Shield/Fire TV evidence remains complete.
Both suggested orders review158.

### Resolved commissioning history (revision157)

User requests testing current9404 APK on ONN non-Pro/v1 now, before publication.
This is distinct from Fire TV non-Pro and historical September13 ONN matrices.
Ignored config has alias onn-v1, .141:5555, expected sti6140d360/API34 and
DEV005/client44:45:56:30:30:35. Identity/OS must be reverified after approval.
Native dev.cmd connect with per-command onn-v1/stock-compatibility reached the
device, but returned device unauthorized on the idempotent authorization-time
read. Do not kill the ADB server, regenerate keys, clear data or alter TOML.
Ask user to accept the on-device debugging prompt (Always allow) and switch/
confirm USB HDMI capture to this ONN. No install or physical row executed yet.
Connection-only retries disconnected/reconnected .141 with the same keys.
After the user toggled debugging Off/On, raw connect returns Connection refused
at .141:5555; adb mdns services lists none. The old approval prompt cannot be
retriggered until a network-debug endpoint listens. Confirm the device's current
IP and whether its Developer options exposes Network/Wireless debugging and
an IP:port/pairing screen; do not assume the old port or reset shared ADB.
After approval, verify identity, non-expiring authorization and original app
state, checkpoint/restore preferences and bracket test keep-awake. Update only
Dev in place using the inspected canonical9404 APK. Run scoped stock175
caption/audio/seek, native DVD and lifecycle rows; optional232 owned HTTP
must prove actual ownership, not fallback. Fix reproducible client failures
when the existing stock protocol permits. Current MATRIX-003 parent includes
this newly requested row plus independently deferred ONN Pro; previous Shield
and Fire TV results stay complete. Both suggested orders review157.

## Shield Tube MATRIX-003 affected rows complete (2026-10-06, revision 155)

User authorized Shield Tube rows now, then connected USB HDMI capture. ADB .68
is authorized, Android11/API30/sif; adb_allowed_connection_time reads0.
The existing Dev0.5.91 package was updated in place to38ef and then the rebuilt,
inspected9404 HTTP-policy APK. No app-data/settings reset. Shield identity is DEV004
(44:45:56:30:30:34), not Non-Pro DEV001. Use per-command shield-tube and
stock-compatibility/vibe-test aliases, never edit ignored config to switch.
Initial Android screenshot and USB capture match the Shield launcher. Stock175
caption/seek, UK Teletext/DVB/AC-3, DVD cadence/menu/audio and GSY-legacy
lifecycle rows pass. ALADDIN clocks0.999867x over15s without dropped/skipped
video or audio; the short authored-loop clock reset was an invalid observation,
not a decoder failure. DVB is discovered row2, not the old harness assumption0.
Every mutating row uses
the existing guarded driver (recoverable under root deleteme/artifacts/temp),
restores preferences in finally and brackets keep-awake. Non-Pro is untouched.
Shared manifest usesCleartextTraffic fixes Android11's blocked MIM HTTP:
direct Media3/232 now proves owned Transcode, active/readable CEA side channel,
FF/REW/pause and server VAAPI hardware decode/encode with deinterlace Off.
Merged APK manifest and package/signature/native inspection pass. Exact SHA:
9404b200060fe0a0cf7e7b351d9c3a47f6d602c726708fe2bacf7520a72d4e2d.
Optional defaults and HTTPS certificate validation are unchanged.

GSY/Media3's first follow-ups recorded a pause-state failure, retained producer
after restart IOException, and a separate startup Pull fallback. Do not rewrite
these as passes. Four standalone producer restarts returned200; the harness
now uses the existing lifecycle stable-idle gate before exact-path Watch and
observes one PAUSE request completing within the original budget. Two focused
GSY reruns pass startup/settled ownership, FF/REW, pause/resume, readable CEA
and no crash, without another player runtime/Core/plugin patch. Final idle HDMI
shows video18.585s against settled captions17.5/18s with non-silent audio.
This closes the bounded affected gate, not a guarantee against every I/O error.

30 caption-authority/51 backend Python checks, build/inspection and project
validation pass; the earlier20 Java caption checks retain their evidence
because only the manifest/test harness changed. All44 preferences and device
power restored per run. Compact evidence:
artifacts/results/MATRIX-003/shield-affected.json. Raw completed/corrected
captures retire recoverably to root deleteme; current APK and warm cache remain.
Shield is returned to original idle/Home with the Dev package stopped, not
Non-Pro's ALADDIN. Keep-awake is inactive, original power values are0/300000ms,
and server caption/Direct sessions read0.141 owned raw files and the old APK
(202,996,079 bytes) were retired recoverably; the canonical9404 copy matches
the inspected build. No GitHub
commit/release, server restart, Pro/Non-Pro change, reset or key replacement.

User explicitly chose **keep ONN Pro pending**. MATRIX-003 remains unchecked
only for that independent post-publication row; do not restart completed
Shield/Non-Pro evidence. Both suggested orders review155 and move remaining
MATRIX-003 after publication; DVD-003 remains deferred.

## Caption correction and Non-Pro affected matrix complete (2026-10-06, revision 152)

The user requested fixing all failing affected matrix rows. Raw CEA delivery
shared the 500 ms timeline timer, which batched nearly an entire 608 roll-up
line. Media3/legacy Exo now have a separate session-owned 33 ms active caption
clock (matching GSY delegates inherit it), 500 ms idle/paused, guarded by
session/player/runnable identity and canceled with progress updates. No
timeline extrapolation, seeking/growing recovery, decoder or audio-clock change.

Fixed/MIM's original HTTP worker also blocked presentation while fetching.
One independent presentation worker now drains prefetched packets, while the
HTTP worker keeps its 250 ms budget and rejects stale generation/bridge
responses. The new slow-HTTP test failed before and passes after separation.
Seven Fixed plus 13 cadence/bridge Java tests, 74 affected Python checks,
compile/package build and APK inspection pass. No Core/STV/plugin change.

Installed on Non-Pro .25 only, preserving all 113 settings:
38efadaac011562ac9791037ad4a36721d9383cb8463d434f94a3ad05183f5aa.
Stock175 Media3/legacy Pull,232 Media3 Pull/Off-CC1-CC2-Off-CC1, Direct
Transcode, GSY legacy/GDX Pull and GSY Media3 Direct show readable fast CEA
after FF/REW; pause/resume and crash probes pass. CC tracing remains off.
USB HDMI startup frames show video19.520/22.522/25.526s with newest complete
captions19/22/25s; post-seek video38.372s has stable37/37.5s rows. Android
screencap can stall video while captions advance, producing an apparent lead:
use --pre-seek-hold-s20 for screenshot-free HDMI evidence instead of adding
a guessed clock offset. Gray moving rectangles match a source FFmpeg frame
at19.520s and are part of testsrc2, not caption corruption. Server status
still verifies MIM0.4.9 VAAPI/h264_vaapi hardware decode/encode for this fixture.

Compact measured results: artifacts/results/MATRIX-003/nonpro-affected.json.
Corrected/completed raw captures are recoverably retired to root deleteme;
historical references in older reports are not live dependencies. Previous
owned-Copy and native-DVD rows are reused; their runtime paths were unchanged.
MATRIX-003 remains unchecked only for other-device post-publication rows;
DVD-002 stays closed and DVD-003 stays deferred. Both orders reviewed at152,
with the completed Non-Pro prerequisite removed from next-action wording.
No GitHub commit, publication, server restart, settings/key reset or Pro test.
Final project validation and 1558-file manifest verification pass; the native
Windows task-order check confirms both mirrors at152. The canonical debug APK
copy has the same38ef hash as the installed package input. Non-Pro has been
returned to stock175/exact-path ALADDIN with verified advancing A/V and one
Full Screen On command; no exact bookmark-position claim. Manual keep-awake
ownership remains unchanged.146 completed/corrected raw files and the driver
were retired recoverably; see workspace artifacts/CLEANUP_REPORT.md.

### Historical revision150 diagnosis (superseded by the correction above)

User requested completion of DVD-002, then affected Non-Pro MATRIX-003 rows on
`.175`/`.232` using its USB HDMI capture. Keep the timed-skip timeline issue
deferred separately as DVD-003. **DVD-002 is closed under the user's explicit
functional acceptance: working playback and skipping are enough; DVD need not
land on an exact spot.** Do not label the recorded stock seek overshoot a
precision PASS or claim certified59.94fps HD200 parity. Those are accepted
measurement limitations, not additional closure prerequisites. Native recovery,
normal menu/title cycles, audio/pause/restart and navigation gates pass.
MATRIX-003 affected Non-Pro rows are now authorized before publication; other
device rows remain post-publication. No Core/plugin correction is needed for
the accepted approximate DVD navigation. Historical observations below retain
their original dates and do not reopen the superseded precision/parity criteria.

MATRIX-003 has started on Non-Pro25/final candidate9c27. Stock175 generated
MPEG-2/AC-3/CEA Pull controls and visible STV captions pass.232 verified owned
Copy controls and native authored-DVD startup/root/Languages/root/main pass.
The original500ms-update fixture leaves empty/gray caption areas on232 Pull
and Direct Transcode despite resumed event225. Fresh matched120s fixtures now
isolate update cadence:2s cues are readable on Pull and Direct Transcode,
including after FF/REW;500ms Pull reproduces the blank rendering. Do not mark
that fast stress row PASS or call232 universally unable to display captions.
Fast tracing-on displayed text; identical Off/On cycling tracing-off did not.
Debug logging perturbs the rendering/timing and is not a production fix.
Generator sends14 field1 pairs per16-character timestamp line at29.97fps
(~467ms);500ms cues allow almost no settled view alongside the300ms Core
roll-up effect. Exact defective scheduling/render component is still unproven.
Normal visual controls pass; next investigate the high-rate roll-up path, not
missing transport, GPU support, or STV selection alone. No runtime fix made.
The first Copy event225-only gate was a wrong expectation: current provider
reserves a tap only for Transcode; Copy showed readable in-band CEA locally.
No claim of STV-rendered Copy captions. Installed232 modified Sage.jar SHA256
dc6891c8a9b5aac9ca724862b07d92a6b817a1365c480093d6b8b6d1dd08f654 verified.
Stored ignored-TOML Unraid credentials work via host Paramiko and existing
known-host verification. The correct installed status entrypoint is
/opt/sagetv/server/SageTVTranscoder --mim-status, not the stock ffmpeg binary.
MIM0.4.9 lastTranscodeJob confirms vaapi/h264_vaapi, hardwareDecode=true and
hardwareEncode=true for both old/slow fixture runs. The old helper's missing
temporary key was a tooling error, not absent server access or GPU support.
Compact current results:
artifacts/results/MATRIX-003/nonpro-affected.json.
Diagnostic documentation validation: project validator passes in the reusable
development container; native Windows validation hit the existing generated
FFmpeg symlink (WinError1920), not a source failure. Host task-order check
confirms both mirrors at150; manifest1556files and scoped diff check pass.
No rebuild/unit-matrix rerun needed because runtime inputs did not change.
Completed175 DVD-002 evidence is reused. Temporary guarded
driver is workspace artifacts/temp/run_android_affected_gate.py; it runs only
existing project harnesses, captures/restores preferences in finally and borrows
the manual keep-awake session. New outputs use artifacts/active/MATRIX-003,
not legacy firetv. Retire that temporary driver when these rows finish.

Session hand-back: Non-Pro returned to stock175 and exact-path ALADDIN;
advancing A/V verified, Full Screen On sent once (not a toggle). All113
preferences restored. No exact resume-position claim.17 completed-gate raw
files moved recoverably to root deleteme, six compact reports moved to results;
embedded raw screenshot references are historical.232 visual-caption evidence
remains active. Both owned120s remote fixtures and their dedicated temporary
import were removed after hash verification; the normal library scan completed.
cc_debug restored false and all113 preferences restored per guarded run; the
manual keep-awake owner was not ended. Temporary fixture cases are disabled
after retirement. The earlier175 post-STOP HDMI recording had a Stopped popup
over advancing video; not a separate UI-dismissal PASS. No test process remains.

Current installed Non-Pro debug APK (settings preserved):
`9c27f55e072d9aa7e0a188ed62714cba78405f5d49af2f5ada165dce88ab077d`.
This optional one-shot recovery candidate passed its focused physical gates.
Stable-Surface experiment bcabc919 was rejected and reverted. Prior narrowed build
was `5cbf804e7f2f34d5e57a10f721c533c015538c24122773a7744283b346443bbc`.
The unproven source-clock ring enlargement was reverted to 64 entries before
this build; bounded dvdNormalSourceClock diagnostics remain. No Sage.jar, STV,
server plugin, saved player/transport or remote mappings were changed.

Completed focused observations on stock .175:
- ALADDIN device-clock cadence: 28579ms source progress / 28578ms device health
  interval = 1.00003x; 1371 video and 893 audio outputs, zero new dropped/skipped
  frames or release gaps. This rules out uniform wall-clock slow playback in
  that interval, not all perceived-motion/HD200 parity criteria.
- Pause: both samples held mediaTimeMs=997280, playerPositionMs=454216 and
  video/audio counters 21786/14215. Resume: counters advanced to 21984/14344,
  then 22189/14478, without a player error.
- Generated DVD root, Down highlight and Languages submenu visually verified
  (closure-generated-root, root-down and submenu screenshots under active DVD-002).

The old cadence harness reported 0.811x because it compared device-captured
positions against later ADB delivery time. mcp_disc_test.cadence_window now uses
the existing health_capturedMonotonicMs; MCP passes this field through. Host
delivery time remains separately reported, and older APKs have an explicitly
labelled fallback. Three focused tests pass. Do not reinterpret all old reports
as PASS; only the newly measured interval has this corrected evidence.

Generated DVD exact path resolves to ID 65513423. Root -> Languages -> DVD Menu
-> root Select stalled at zero time; 4MiB new bytes arrived but no reads. SIGQUIT
of the verified development app PID showed ExoPlayer:Playback inside native
MediaCodec.flush via MediaCodecRenderer.onDisabled. The new native-only renderer
releases the proven OMX.MTK MPEG-2 codec before super.onDisabled and an affected
keyframe super.onPositionReset, preserving
Surface/player/Push ownership and the configured codec adapter/queueing factory.
Ordinary TV, transformed DVD and other codec families retain existing behavior.
Two pure policy tests, four source contracts, affected Java tests/build pass.
The latest affected Python run passed 107 checks (DVD protocol, remote
long-press, lifecycle, cadence and cursor-oracle contracts). Four cursor tests
retain the actual key-down intent and decoded anchor, not a late server echo.

First workaround run showed the main-feature burned PTS 00:00:00.701 / frame 21,
advancing A/V. A subsequent authored-title interval measured 0.9893x, 685 video
and 368 audio outputs, five drops and one gap; do not call that zero-drop parity.
The disable-only candidate repeat **failed**: main time=0, video=0, audio=16; source had
10,496,000 bytes read, 12.4s buffered and eight decoder inputs. Its thread dump
showed the playback thread idle (no native flush block), with the loader waiting
on ProgressiveMediaPeriod's load condition. ACodec logged nBufferCountActual=9
failure and a forced old-codec release. This is not yet a proven Surface/driver
or fixture cause. Local output refresh did not recover it; a later Media3
buffering timeout appeared. The supported keyframe-position-reset release then
passed three root -> Languages -> root -> main-feature cycles on stock .175:
video 32->288, 51->307, 36->295; audio 34->169, 44->180, 36->173; no player
error. Candidate hash was 5411ef392fe3484d89450dbe1f3495042c90debeb460e562c4bb35e1401cba83.
The final 5cbf build retains those lifecycle hooks and removes an unused manual
queue retry experiment. No broad retry loop or clock-history enlargement remains.
Media3 1.11's video flush-policy hooks are final; the implementation uses its
supported lifecycle methods, not a library fork, reflection or a final override.

Original failure checkpoint prefix:
artifacts/active/DVD-002/20261006-150930_closure-generated-title-stall;
trace: 20261006-151147_closure-title-stall-trace_playback-trace.jsonl.
The trace also spans the still-open timed-skip investigation; retain it as mixed
open-failure evidence. Main-feature screenshots: closure-title-release-fix and
closure-title-release-final (the latter was not produced because repeat failed).
Compact lifecycle/context results now exist at
artifacts/results/DVD-002/native-codec-lifecycle.json. Corrected lifecycle raw
captures can be retired after recording; the mixed timed-skip trace and unique
precision/cadence comparison evidence remain active.

Recovery check: ordinary exact-path Watch back to ALADDIN succeeded with advancing
hardware A/V (time 1088945ms, video 1708, audio 1153, no error). Therefore do not
assume a device-wide decoder failure or require an OS reboot from the menu failure.
Stock .175 final-build STOP unloaded the player; Back exited the leftover menu,
and exact-path ALADDIN rewatch rendered advancing A/V without an error.
SageMC fullscreen OSDOptions retained normal arrows. Stock STV on .232 showed
Wide OSD Options and kept ordinary RIGHT/DOWN focus navigation, no client TS.
Back reached Main Menu's 652x340 preview; RIGHT/DOWN entered TV/Watch Live TV
while the preview kept playing. Full Screen Off alone did not produce that
preview; accepted command metadata was not treated as physical proof.
Temporary cross-server switching requires dev_prepare_clean_start before
dev_connect_server(save=false): a live activity teardown otherwise closed the
newly published .232 connection. The existing clean-start workflow restored
the connection without changing Core or adding runtime reconnect behavior.
The Non-Pro was returned to .175 using that workflow and exact-path Watch.
SageMC's 452x254 preview passed RIGHT/DOWN navigation through MyTVPopup to
Recorded TV while video/audio continued and client TS stayed off. Both STVs'
completed context gates need no unconditional rerun.
The pre-existing manual keep-awake
session was already active; no new power-setting snapshot or ownership takeover.

Forward TS precision still fails on stock .175: requested 698170ms, server
dvdStc45Khz=31909878 -> destination709108ms, decoded anchor about709166ms.
The client agrees with the server-selected bytes. Stock VM Seek interpolates
sectors against elapsed time; see third_party/Ogle/java/sage/dvd/VM.java in
google/sagetv. Reverse TS anchor error -533ms and Back/Play cancellation pass.
Do not substitute a late server echo for the requested destination or fake
the client clock. An asynchronous user question asks whether to investigate
an optional Client Extension correction or explicitly defer precision; no
answer yet. No Companion or Core implementation has been authorized/changed
in this turn. The current USB capture delivers valid 30fps, not verified
59.94fps presentation; archived HD200 ALADDIN is not the same generated fixture.

Final 5cbf root/Languages/root/main smoke passed (video252->557, audio149->310),
but after a later root return, selecting main stalled for the bounded108s
observation: eight video inputs, zero video outputs, audio16, 12.4s buffered.
Current-PID SIGQUIT showed Playback idle in MessageQueue, not native flush.
The new checkpoint is 20261006-164551_closure-final-title-reopen-stall_*.
This is fresh open-failure evidence; the earlier corrected flush checkpoint
was already retired with its written result, while the mixed trace remains.

The stable-Surface candidate uses Media3's supported
codecNeedsSetOutputSurfaceWorkaround for exactly OMX.MTK.VIDEO.DECODER.MPEG2
inside the native-only renderer. No device-model profile or library fork.
Three policy tests/five contracts, affected Java build and APK inspection pass.
An early repeat appeared stalled at the short observation budget but recovered
later; do not call that permanent failure or a timely-start pass. The stronger
30s phase-aware test then failed Languages at15 inputs/no video. Report:
artifacts/active/DVD-002/stable-surface-menu-cycles.json. The new authored-cycle
harness waits for a fresh cell and advancing real A/V before issuing another
key, refuses unverified fixture volumes through existing stock MCP state, and
does not change settings. Two pure gate tests pass. Twenty MCP playback-health
tests pass, including retention of the existing device capture timestamp.

Later controlled runs: async three server-controlled menu cycles passed, then
restored sync passed three matching cycles. Do not change queueing defaults or
claim causal attribution from that result. Restored default passed three actual
Fire TV-key cycles (final-firetv-native-menu-cycles.json) and natural title end,
root and title restart (video227->480/audio136->270). The first-buffer debug
probe build2829476 then passed three further key cycles. Its normal first input
and output had matching renderer timestamps1000000000001us; output was processed
immediately. Those are normal Media3 renderer offsets, not source DVD time.

Bounded Push capture is off. FFprobe identified a14,352,384-byte MPEG program
stream, MPEG-2 720x480 at30000/1001, AC-3 and DVD NAV. It decoded1165 frames;
partial initial data produced0x0-dimension warnings. This was a healthy capture
control, not proof of the earlier stalled epoch's byte validity. Host SHA256:
6121a65e6fce8d6e5d96e5c41fff1e39ea417ed5e6f884c6f0b0617336f16b3a.
The extra external-storage device copy was removed after exact byte-count
verification; host active native-push-packet-check.ps remains available.

The current candidate adds Settings/Playback/DVD Playback/Native DVD decoder
recovery (default on, applies at next start/rebuild). Off uses the original
Media3 renderer. Eight inputs/no decoded output for4s, additional ready local
samples, valid Surface and the affected codec family allow one local codec
restart. It retains the period, sample queues, Push reader, Surface, A/V/source
clock and requested queueing/decoding mode. Consumed startup pictures cannot be
replayed; recovery begins at the next queued key picture. An output held by
scheduling and an empty/slow source do not qualify. Native factory scope stays
DVD-only/non-transformed; other codecs/transports use existing behavior.

First-input/output metadata is bounded and debug-only, with no encoded payload.
The debug-only one-shot output withholding validates the retained-queue boundary,
not the real firmware cause. Stock Sage API cannot control Android codec
callbacks: existing stock MCP still owns Watch/menu/seek; the local debug
receiver adds no Core/private event/GFX/socket fault. Always disarm in finally.
114 affected Python contracts pass; affected Java tests/build and APK inspection
pass. Physical one-shot gate uses scripts/mcp_dvd_codec_recovery_test.py and
passed: exactly two codec initializations/one release, video99->389 and
audio100->249; source FLUSH5 stayed constant during post-recovery verification,
no error, fault disarmed. Synthetic injection proves the boundary, not the
original hardware failure's cause. Final normal menu cycles3, actual audio
selection48512->48513->48512, main-title pause13334ms/counters231/139 and
resume17543ms/counters484/272 pass. ALADDIN STOP/rewatch produced1.00083x
source/device-clock progress (13307/13296ms),638 video/414 audio and no new
drops/gaps. Ordinary/transformed rendering is unchanged by the native guard.
A prior clean-start request exceeded the harness's short reply budget; read-only
inspection proved the app stopped and no server context remained. Connect was
issued only after that observation, not an implicit replay. The focused fault
harness uses45s control budgets and always closes its MCP process/disarms.

Next: the matched caption comparison is complete at150; investigate the
remaining500ms roll-up/render failure, not the passing2s controls. Public
MCP/API first, no metadata/Core mutation. Reuse completed controls/DVD
evidence unless code changes.
Do not retest deferred DVD-003. Other device rows still wait for publication.
Both task orders reviewed at150. No commit/release or server restart performed.

Prior runtime administrative verification: 114 affected Android/harness contracts and
20 MCP health tests pass. Native Windows task-order validation confirms both
project and workspace mirrors at147. Manifest regenerated/checked at1556
files. Six passing compact menu/recovery reports now live in
artifacts/results/DVD-002; their49 raw screenshots were moved recoverably to
root deleteme preserving their original workspace-relative paths. Embedded raw
paths in those reports are historical, not current active evidence locations.
Open precision/comparison evidence was not moved. No settings were cleared.

## Deferred SageMC timed-skip timeline investigation (2026-10-06, revision 142)

User requested deferral after identifying **FF/RW timed skips** as the action
that leaves the SageMC timeline stale. Do not resume this DVD-002 sub-gate
without a new request; other DVD-002 criteria remain open. No verified fix,
commit or release resulted from this investigation.

Stock `.175`, non-Pro `.25:5555`, ALADDIN media ID 42983134. The user enabled
SageMC Default DVD FF/REW and confirmed timed skips work before reporting the
timeline issue. Do not undo that choice or change mappings/settings on resume.
Actual event 8 (`ff`) was dispatched with the existing Android debug command.
HDMI `artifacts/active/DVD-002/actual-skip-timeline-hdmi.mp4` shows elapsed
`0:53:21` frozen across changing movie frames; contact sheet is alongside it.
Server observations: before 3197293ms; initial post-skip 3211531ms; three seconds
later 3204868ms; another three seconds later 3211908ms. A later experimental
rerun also fell from 4113198 to 4107202ms before advancing to 4114676ms.
These observations do not establish whether source-clock handoff, landing
precision or STV refresh is the primary defect; do not fake a timeline advance.

Experimental client-only patch retains 1024 rather than 64 normal source-clock
anchors and exposes `dvdNormalSourceClock` in existing bounded debug snapshots.
Focused DvdNormalClockHistoryTest, DvdPsExtractorTrickModeTest and
DvdScanBufferPolicyTest plus debug APK build passed. Installed APK SHA-256
`bb2793a045191196667d9a08391f949aa1c9b218d8b7bbd852d02a5b1899a8f4`, preserving
settings. **Not a proven fix:** actual diagnostic reported count=1, not a full
ring, while the symptom persisted. Do not release the history enlargement as
the correction; reconsider it before any eventual publication. Source edits
and tests remain uncommitted. No Sage.jar/STV/server-plugin change was made.

The guarded Windows installer twice timed out during ADB authorization setup,
before installing anything. Direct execution of the same guarded Python CLI in
the reusable container succeeded; it verified the development package, used
ordinary update installation and retained settings/keys. No reset or clean
install occurred. Keep both `-w /workspace/android-client` and
`SAGETV_WORKSPACE=/workspace/android-client` in container commands.

Control caveat: the existing Core MCP allowlist accepts short `Skip Fwd`/
`Skip Bkwd` names, but the inspected stock UserEvent lookup uses `ff`/`rew`
or full `Skip Fwd/Page Right`/`Skip Bkwd/Page Left` names. The full names were
rejected by the bridge allowlist. Accepted short-name calls are not proven
dispatch and must not count as skip evidence. No bridge fix was implemented.
Use the already supported Android event-8/9 diagnostic or public Skip API
media-control operations for a future bounded reproduction.

Retain unique open-failure captures under artifacts/active/DVD-002, including
actual-skip-timeline and clock-history-skip HDMI/contact files. Earlier
skip-timeline and timed-skip-clock captures include a rejected/no-op command
and are not valid successful skip gates. No test remains running from this
investigation. Deferral updates both task orders to revision 142 and does not
launch another playback test or mutate device/server settings.

## Current Last icon, focus fallback and DVD context gate (2026-10-06, revision 141)

Installed debug APK on non-Pro `.25`, settings preserved:
`3eac679559e8c2aa06e05d005a7c7dbbc3b3cc633e419637493c7892b40bf1d5`.
UI-003 and UI-004 are complete: Last/Previous Channel occupies the blank cell
between Page Up/Down in all three active layouts; its new white 24dp return-
arrow/TV vector uses existing event 60. Focus now prefers the same axis, then
the nearest icon ahead in any row/column. Eight focus JUnit tests, three Last
contracts and compile pass. Actual hierarchy: Last x960..1070/y586..696,
Page Down Up reaches Last; Page Down Right falls back to Video Info and Video
Info Up falls back to Page Down. Live two-channel recall was not tested; this
UI sends the pre-existing Previous Channel event rather than implementing a
new tuning transport. Completed raw hierarchy XML is retired recoverably.
An extra Page Up chain attempt ran after the local icon dialog closed; its
surface-only hierarchy is invalid staging, not another physical PASS row.

DVD-002 is still open. DvdInputContext now requires an OSD playback hint,
no popup and known non-preview server destination bounds. The key listener
uses ordinary mappings for DVD previews; TS, held chapters, dedicated scans,
legacy pulses, authored-menu overrides and the chapter display all guard that
context and abandon timers on leaving it. Three pure context tests and 16
remote source contracts pass. Physical final window/popup approval is pending;
the user reports arrows failing with a SageMC menu over full-screen video.
Asked whether that is the Audio/Subtitles/Aspect DVD bar or Options popup and
to leave it visible. Do not assume an inline OSD bar sets popupName, and do
not claim the visible-menu gate closed merely from geometry or unit tests.
One actual full-screen snapshot reported Multispeed FF/REW Options Menu with
1920x1080 destination; later arrows showed TS inactive, but intervening menu
changes mean this alone is not a clean visual navigation verdict. Open raw
screenshots stay under artifacts/active/DVD-002 until clarified/verified.

Two guarded installs failed before installation on the idempotent Android
settings-provider bootstrap (15s timeout). Raw scoped shell get/put/get then
verified adb_allowed_connection_time=0; an explicit guarded retry installed
successfully. No keys, data, server or OS were reset. Keep wrapper workflow.

Verified why stock STV FF/RW skips ten seconds: PseudoMenu routes events 8/9
to VideoFrame.ff/rew, whose default TIME_ADJUST values are +/-10000ms. SageMC's
DVD listener checks sagemc/default_DVD_FFREW: true calls SkipForward/Backwards;
false changes DVDPlaybackRate. A future optional Companion endpoint could call
the public Skip APIs without changing that setting, but none was implemented
or deployed. No SageMC property/STV/Core change was made. Next work: confirm
visible-menu input ownership, then remaining TS precision/menu/cadence gates.
No broad matrix, commit or release; orders reviewed at 141.

## Current DVD defaults and sparse menu navigation (2026-10-06, revision 140)

Latest debug APK installed on non-Pro `.25`, settings preserved:
`ded7be21bbe13b167fbf0e7b7e77a71d307d1bdb1afcc53d25fb5ebb3406e813`.
The user confirmed the DVD keys work perfectly. Main key mappings / DVD
Playback now expose default-on DVD arrow navigation and a separate default-on
DVD FF/RW tap and hold option. Missing preferences resolve true; stored false
choices are preserved. The scan controller honors its switch and existing
custom command overrides. Two default/persistence JUnit tests, 15 remote source
contracts and the APK build pass.

UI-002 is complete: NavigationDialog's event-only focus helper preserves
columns on Up/Down and rows on Left/Right, skips blank cells and hidden or
disabled icons, and retains focus at an axis edge. Center, Back, clicks and
playback/STV input are unchanged. Six focus JUnit tests pass. Actual `.25`
hierarchy checks: Options Right/Right reaches Down across gaps; further Right
reaches Page Down and stays there at the row edge; Left returns to Down;
Down crosses the empty row to Pause, and Up returns to Down. No polling added.

The chase-scene Up/Down regression passes four rows on stock `.175`
(`artifacts/results/DVD-002/dedicated-scan-chapter-chase.json`). A previous
mid-film run showed only one observed cell during the short repeated hold;
its compact diagnostic stays under active DVD-002. Do not silently call that
first result PASS or infer a new client/Core regression from that count alone.
The dedicated scan eight-row result remains applicable: later changes only
add the default-on preference gate and local icon focus. No full matrix was
restarted. Forward TS precision, authored menus, actual scan throughput and
motion/cadence remain open under DVD-002. Orders reviewed at 140; no Core
change/server restart/commit/release. Completed raw capture/XML is recoverably
retired to root deleteme; written results and compact final JSON remain.

## Current dedicated DVD scan buttons (2026-10-06, revision 139)

Installed debug APK SHA-256:
`62f5658ba63c54d026d9d10f2b4a8bc1cb595979bb82b855d4e79c800326993c`.
The user's newest FF/RW requirement supersedes the older 16x tap cap and local
Android rate banner. Dedicated taps change one scan rate and stay active.
Holds ramp once per acknowledged one-second step up to 256x; releasing a hold
resumes normal 1x. Opposite taps reduce the current magnitude, with logical
zero/off represented by Play (never rate zero). Play cancels an active hold.
No Android DVD/rate banner appears during scanning; the stock STV timeline
remains available. Left/Right Time Scroll and Up/Down chapters are unchanged.

DvdScanGestureController owns one foreground physical DOWN/UP identity, consumes
repeat DOWN, waits for rate acknowledgement, bounds an unacknowledged hold at
four seconds, and abandons its timer on another gesture, menu, connection,
player, foreground or teardown change. It uses existing FF/REW listeners
through 16x, and public Faster/Slower events above that range. Stock Core
VideoFrame.faster/slower doubles/halves the signed rate. SageMC's independent
DVDPlaybackRate label can remain 16x at higher rates; this is not proof of
actual scan speed. No Core/private protocol/plugin dependency was introduced.
The debug-only foreground DOWN/UP test seam additionally accepts dedicated
FF/RW; it still rejects other keys and bounds duration to 20..12000ms.

`scripts/mcp_dvd_scan_gesture_test.py` passed eight focused ALADDIN rows on
non-Pro `.25` / unmodified stock `.175`: tap +2/+4, opposite taps +2/1x,
FF and RW holds reaching signed 256x, release with independently advancing
normal video/audio, Play cancellation without timer restart, and dedicated
scan exiting the TS cursor. Result: `dedicated-scan-hold-stock.json` under
`artifacts/results/DVD-002/`. HDMI capture reviewed: no Android DVD/rate banner,
STV timeline retained. Focused policy JUnit/build, 14 remote source contracts
and four MCP held-input tests pass. The later chase-scene Up/Down recheck
passes; see revision 140 above.
Forward TS source-anchor precision, authored-menu behavior, actual high-rate
throughput and motion/cadence remain open. Do not close DVD-002 or restart a
broad device matrix. Settings preserved; no server restart/commit/release.

## Current DVD arrows: stock Time Scroll (2026-10-06, revision 138)

The user supplied a stock STV sequence that avoids a Companion seek bridge:
Time Scroll opens the seek cursor, primary Skip -/+ adjusts it, and Time Scroll
again commits through the STV's Seek. The latest requested preset maps Left/Right
to that cursor flow, Center to accept, and Back/Play to cancel without seeking.
Dedicated FF/RW exits the cursor and retains the established scan mapping.
Up/Down is explicitly unchanged: short taps do not change chapters; deliberate
one-second holds start repeating next/previous chapters until release. Authored
DVD-menu arrows remain immediate navigation. Existing dvdplaying_hold_arrows
storage is preserved; its label is now DVD arrow navigation and off keeps saved
mappings. Ordinary TV mappings are unchanged.

Added DvdTimeScrollPolicy/Controller and stock SageCommand event 10. Center/Back
key-up is consumed with its original downTime, repeated arrows are bounded,
and connection/player/menu changes abandon local cursor ownership. There is no
HTTP/runtime plugin dependency, background timer, new private event or Core
change. The STV owns the selected timeline; the Android source-position display
is hidden while its cursor is active to avoid showing a conflicting position.
The Core MCP bridge source now allowlists Time Scroll and passes stock Java 8
linkage/contracts, but that separate JAR has not been deployed to `.175`.

Focused command/policy JUnit, APK build, 80 DVD/13 remote source contracts and
three MCP cursor/held-input tests pass. The installed APK SHA-256 is
`db6fc4824dda40bcf154a517efead28a616afba30750ba8ea1150a5e8da256e1`.
Physical Up/Down preservation passes (`ts-up-down-chapters-stock.json`): no
chapter on a 200ms tap, repeated chapters on a 3.4s hold, release stops repeats,
advancing normal A/V with no error. TS repeated/opposite cursor movement,
Back/Play cancel without seek/exit, and dedicated FF/RW +2/-2x separation pass.
Reverse Center commit passes at -720ms decoded-source anchor error. Forward
Center commit performs a real FLUSH and advancing replacement A/V but remains
outside the precision gate: +10.3s near the chase, and +2.9s/+8.4s late-film
examples. Do not close the combined cursor gate or DVD-002. Next work is that
precision discrepancy, authored menus and cadence, not repeating input rows.

The bounded foreground cursor queue sends each command once at 150ms spacing;
Back/Play clears queued adjustments, teardown abandons the queue, and there
is no periodic TS keepalive or retry. MCP compact snapshots initially omitted
the new cursor fields; that adapter bug is fixed with a regression test. It
invalidates the initial cursor-failure verdict and does not establish that the
STV command spacing was the cause. Earlier TS diagnostics used an incorrect
pre-command moving/paused anchor, and one later attempt had no flush followed
by unrelated scan movement; the explicit harness now rejects concurrent remote
input. Compare the actual STV cursor/Core GetMediaTime anchor with decoded A/V,
not just accepted commands or local cursor intent. No server restart, Core
change, settings clear, commit or release was performed.

The following sections retain revision-136 history. Their held Left/Right
256x scheme and optional Companion runtime-seek proposal are superseded.

## Local artifact retention (2026-10-05)

The user authorized retiring raw evidence for completed gates and corrected
failures. Superseded Android packages, redundant screenshots, and historical
D6/audio/seek/caption/SMB/plugin-setup captures have moved to workspace-root
`deleteme/`, with their original workspace-relative paths preserved below a
retirement-pass prefix where needed to avoid collisions. Old raw run logs and
diagnostic snapshots were retired too, including corrected-failure output.
Historical
raw-image/video links below for those completed cases may therefore no longer
exist at their original location. Their written results, task ledger, structured
reports and generation procedures remain authoritative; do not reinterpret
retirement as a failed or untested gate. Workspace `artifacts/CLEANUP_REPORT.md`
records the scope and recovery path. The user may empty `deleteme/` afterward.

DVD-002 remains open and its active evidence/comparison recordings are not
retired. OSD/tablet investigations, canonical fixtures, source, settings,
persistent ADB keys, caches/databases, configuration backups, the current
release and active compiled APK are preserved. No device/server state was
changed by cleanup. Both suggested execution orders are reviewed at revision
134; DVD-002 is still the next focused playback task. Follow the end-of-test
retirement procedure in WORKFLOW/AGENTS instead of retaining every capture
forever. No commit or release was made for this cleanup.

New output belongs in `artifacts/active/<stable-task-ID>/`, with an explicit
output path for every capture helper. Disposable staging belongs only in
workspace `artifacts/temp/`; final compact reports may use
`artifacts/results/<stable-task-ID>/`. No new quarantine folders or loose root
captures. The current `legacy-extender-server-branches.txt` reference was grouped
under `artifacts/active/DVD-002/`. Local completed MIMFIX-003 written evidence is
in `artifacts/results/MIMFIX-003/HD200.md`; its old empty
project-local folder was retired. Cleanup now stages about 36.85 GiB for user
deletion, while preserving settings/keys, original handoffs, source differences,
current release/build inputs, canonical fixtures and open investigations.

## DVD-002 native scan controls in progress (2026-10-05)

Latest requirements supersede the older tap-skip/Left-Right chapter proposals
below: hold Left/Right to scan with a one-second gradual 2/4/8/16/32/64/128/256x
ramp, release to Play; hold Up/Down for one second before repeating chapters.
Short Up/Down taps do nothing. Authored menu arrows remain immediate. The
default-on DVD Playback held-arrow preference preserves saved mappings when
disabled. A non-focusable Android DVD position display stays visible during
navigation and briefly after normal playback resumes; stock NEWCELL supplies
only an offset, so it does not invent a total-title percentage. This client UI
requires no server plugin or STV change. High-speed preview timing now permits
16.667 ms intervals at 128/256x; normal title timing is untouched.

The public stock exact-seek source-clock gate passed after the normal PES
anchor-ring correction: +10s landed with 108ms source-anchor error, -10s with
-743ms, normal resumed at 0.99784/0.99377x, with advancing A/V and no player
error (`exact-seek-anchor-check.json`). A proposed production companion seek
bridge was withdrawn when requirements changed; it was never deployed.
The earlier held-input report is invalid for the DOWN tap: it began at 32x,
not normal playback. Concurrent input is suspected, not proven. The new
harness refuses that precondition. Its isolated UP tap did not change chapters.
The latest APK builds, held-arrow/extractor unit tests pass, 12 remote source
contracts and 2 MCP held-input tests pass. `held-arrows-256-stock.json` passed
six physical input rows: short Up/Down no chapter, deliberate holds repeat,
signed 256x ramp and release restore advancing A/V. HDMI capture confirms the
position display. This is input/lifecycle evidence, not throughput acceptance.
Actual 64x measured 31.47x. A full-existing-pipe experiment measured
39.81/49.19/40.74x at selected 64/128/256x and was reverted. The final client
keeps the one-MiB scan byte budget and two-second decoded cap, and removes
redundant hold-rate Toasts now covered by the position display. Optional
Companion-assisted public-seek coarse navigation has been asked about but
not implemented/deployed. Parent DVD-002 remains open for high-speed
throughput, final landing, authored menus and cadence. Both order reviews
are at 136. No Core/server deployment, commit or release.

The stricter final short-reverse oracle remains FAIL. A 1.5s Left hold restored
1x A/V but landed ahead of its starting position; do not reinterpret the prior
lifecycle-only smoke PASS as accurate reverse landing. An anchored 16x drain
experiment reached reverse previews sooner but still landed +16.9s ahead
(`held-arrows-anchored-drain-smoke.json`), showing queued input was already
ahead of the rendered starting point. That experiment was reverted too.
The final build retains the tested held-arrow/position-display changes and
original bounded reserve-drain behavior. Accurate short reverse and actual
high-rate search remain open. Await the user's choice about optional
Companion-assisted public-Seek coarse/fine navigation; no production plugin
changes or server deployment have been made for it.

Final restored debug APK installed on Non-Pro with settings preserved:
SHA-256 `1d5ab5ecc1474047143b8072a5f59e8867e202278ae26d4288462fe3b0e87ed0`.
The failed full-pipe and anchored-fast-drain experiments are not in that build.
Nine extractor timing tests, two held-arrow timing tests, affected DVD policy
tests, 80 DVD protocol contracts, 12 remote contracts and two MCP input tests
pass. Task-order validation includes the Windows workspace mirror at 136.
The position display/control preset can be tested, but do not publish or close
DVD-002 while high-rate throughput and short-reverse landing still fail.

Current resume point: Non-Pro `.25` with USB HDMI capture / unchanged stock
`.175` / SageMC. New outputs use `artifacts/active/DVD-002/`; the preserved
HD200 ALADDIN comparison is under `hd200-reference/` there. APK updates use
`dev.cmd install` and preserve all settings. No server/Core/STV mutation,
restart, commit or release was performed in this phase.

The old 4 MiB raw/12-second decoded scan reserve made rate changes sluggish
and reverse return-to-Play overshoot. Scan-only load control now caps decoded
lookahead at two seconds and advertised raw capacity at one MiB, accounting
for bytes already queued; normal title buffering remains five/twelve seconds.
A 256 KiB raw / 500 ms decoded experiment deadlocked a hardware decoder's
retained reference pictures and was rejected. Short skips now hold a bounded
4x pulse rather than escalating to 16x; repeated taps update one target.

With the one-MiB/two-second build, a forward physical-key run in a later
ALADDIN chapter measured 1.98/3.97/6.41/16.04x; the confirmed nine-minute
chase-section run measured 2.06/4.00/6.80/16.01x with advancing frames and no
player error. These focused rows passed the approximate scan-rate gate, not
the whole DVD task. Reports/captures: `scan-one-megabyte-buffer-forward.json`
and `scan-one-megabyte-buffer-chase.json`/`.mp4` in the active directory.

The chase run exposed an uncorrected 1x clock base after scan cancellation:
decoded source resumed near 19 minutes while the client initially reported
about 10.6 minutes. The latest APK adds a bounded normal-play PES/source-to-
presentation anchor ring, selects only boundaries already reached by the
player, and holds the source clock across all native DVD FLUSH transitions.
Focused extractor/policy unit tests and the APK build pass. The stock public
exact-seek source-clock gate subsequently passed as recorded above; held-arrow
release landing still requires the new physical gate.

Remaining DVD-002 acceptance: repeated/opposite tap landing and latency;
opposite scan decrease without a Play/reseek/rebuild; current STV timeline
and label agreement; normal ALADDIN motion and generated authored-cadence
comparison, chapter/menu/pause/STOP/restart and optional-extension fallback.
The existing opposite-direction policy still sends Play plus lower-rate
FF/REW events for SageMC's private DVDPlaybackRate label. Stock Faster/Slower
can avoid that reseek, but do not synchronize that STV-local variable. A
stock-supported plugin event/UI API boundary exists (PlaybackRateChange plus
SetVariableForUIComponent); no new plugin implementation or deployment is
claimed. Do not silently modify Core or treat this as a completed gate.

The following paragraphs retain the preceding failure history; their old
artifact paths may have been retired during cleanup, and their APK hash is
not the current installed build.

Non-Pro `.25` / stock `.175` / SageMC is the active DVD gate. Native ALADDIN
uses the Media3 DVD Push fallback; saved GSY/System and Fixed preferences are
unchanged. No Core, STV or server-plugin change was deployed for this work.

Stock DVD VM chooses authored forward/back VOBU-table jumps using integer
`abs(rate)/3`, clamped 1..14, assuming half-second units and about six preview
pictures/second. HD300 binary evidence independently shows discard-P/B and
STC-speed handling. The client therefore keeps normal 1x separate, suspends
audio during scan, emits I-picture previews, maps rendered preview positions
back to source PTS, and now paces previews by source distance/requested rate
with bounded repeated/reordered/missing-PTS handling.

Rate changes retire the superseded local reserve. The current uncommitted
patch holds the last source clock until the replacement first frame instead
of exposing zero/compressed position during that rebuild. Dedicated FF/RW use
SageMC's native listeners so its rate label follows; opposite keys use Play
plus the lower-rate sequence. Play/Pause during scan resolves to Play.

Focused Java tests/build and the new native-scan clock source contract pass.
`scripts/mcp_dvd_remote_test.py` measures every +/-2/4/8/16 rate using source
time and frame progress, and then checks decrease/cancellation. A pre-hold
run exposed forward-8x underspeed and reverse-16x transition/reset problems:
`artifacts/dvd002/nonpro-dvd-all-scan-rates-stock-continued.json`.
The corrected-clock/repeated-PTS physical rerun remains FAIL. Its five-second
window confirmed reverse rates about -2.24/-3.86/-8.06/-14.18 and cancellation,
but forward 4/8/16 remained about 2.19/3.08/7.05 with intermittent BUFFERING.
The latest build also bypasses normal-play telecine parsing/rewriting during
scan. Its shorter check recorded reverse -1.98/-4.23/-7.90/-14.77 but forward
4/8/16 only 2.59/2.88/8.53. One reverse step-down showed a source-clock jump
back to the 783299-ms cell instead of retaining the previously displayed
position; the rate label alone is not a passing landing gate.
Evidence: `artifacts/dvd002/nonpro-dvd-all-scan-rates-stock-clockhold.json` and
`artifacts/dvd002/nonpro-dvd-all-scan-rates-stock-no-telecine-scan.json`.
Installed latest debug APK SHA-256:
`633fd2e16880fb22882c634e552ed1ed13e91af271c4c69b0b2280ad76c76b72`.
The full focused DVD/remote source-contract run passes 87 tests, Java targeted
tests/build pass, and both task orders are synchronized at revision 132.
Do not close DVD-002 or claim all jumps work from selected-rate snapshots.

Short arrows still require resolution: combined Right/FF and Left/REW can be
consumed by SageMC's tertiary FF/REW listener. FF2/REW2 are chapters by default,
not ten-second skips. A bounded scan-and-Play pulse could approximate a stock
skip, but exact landings require a supported server seek control. The user
wants optional Client Extension support and a no-extension stock path, with
no Sage.jar modification. Keep this limitation explicit while implementing.
No commit, release or server restart has been performed in this phase.

## MIMFIX-003 legacy-extender closure (2026-10-05)

MIMFIX-003 is complete. Its final legacy row passed on replacement HD200
`001d6a4bfafe` against unmodified stock `.175`, using physical USB HDMI and
audio capture while keeping the extender inside the SageTV STV. Generated
MPEG-2/AC-3/CEA playback and post-seek CC1, H.264 codec-transition fixtures,
authored-DVD menus/title/chapter/pause/audio/subpicture/return, STOP/Home STV
repaint, and a real growing 5.1-to-7.1 transition all retained advancing A/V
and the same connected UI context.

UK H.264/AC-3 samples remained stable. The lack of Teletext/DVB rendering on
this legacy path is the directly reproduced 2010-era stock-FFmpeg limitation,
not a regression from the optional Vibe plugin. The apparent shutdown of two
HD200 units was independently traced to Automatic Power Off 1.0.7; after the
user removed it and restarted SageTV, the replacement completed the remaining
gates without another policy-driven power off. Earlier cross-player,
plugin-state/failure, Linux/Windows, completed/growing, caption, seek,
fallback, restart, and zero-orphan evidence remains applicable. Stock
`Sage.jar` was unchanged. Local evidence is indexed in
`artifacts/results/MIMFIX-003/HD200.md`.

## MIMFIX-003 Windows lifecycle closure (2026-10-05)

The remaining current-plugin stock-Windows lifecycle portion is complete on
stock `.185` with non-Pro Fire TV `.25`. SageTV runs as `LocalSystem`, so the
user-session `V:` mapping was not sufficient after power-on; the same SMB path
was mapped persistently in the service account context. The fixture remained
available after a real `SageTV64` restart.

Media3 Fixed/MIM Direct Transcode and Direct Copy both proved strict plugin
ownership through `Media3MimDirectHttpDataSource`. Transcode delivered hardware
H.264 video with AC-3 audio; Copy preserved hardware MPEG-2 video with AC-3.
Focused affected gates covered deterministic seek, FF/REW recovery,
pause/play, repeated source start, Stop/exact rewatch, crash checks, and clean
exit. The harness restored all 110 preferences and the device's prior sleep
policy after each run.

During an active Transcode session, restarting `SageTV64` removed the old
Direct session. The plugin returned `state=ready` with zero sessions, and a
fresh post-restart strict Direct Transcode session reached sustained A/V and
cleanly stopped. Final capability checks reported zero caption and Direct
sessions; Windows reported no `SageTVTranscoder`, MIM, or FFmpeg process.
Stock `Sage.jar` was unchanged. Existing Windows QSV/software-fallback,
CEA/Teletext/DVB, and caption-after-seek evidence was retained instead of
repeating unrelated matrices. Those remaining rows subsequently closed in the
legacy-extender gate above.

## SEEK-001 closure, non-Pro stock reference (2026-10-05)

SEEK-001 is complete without another Pro session. The existing Pro `.29` /
stock `.175` runs already recorded exact `180000 ms` Pull and Push landings,
an injected physical long-Right landing at the next `420000 ms` marker, fresh
Left/Right behavior, retained encoded offset, sustained A/V, and no player
error. The user's subsequent visible confirmation that long-Right/Comskip works
provides the final Pro acceptance.

The remaining affected reference passed on non-Pro `.25` against unmodified
stock `.175`. Media3 hardware Pull opened the generated
`VibeSeekTest-1080i-MPEG2-AC3-CC.ts`, positioned at `137372 ms` inside its
first generated commercial, and issued the real SageTV right/Comskip command.
The server requested `180000 ms`; video and audio both landed at `180000 ms`
with `0 ms` error. A/V recovered in `572 ms`, playback remained active, both
clocks continued advancing, and the harness reported no failure or backing-up
loop. All 110 Android settings and the device's pre-test sleep values were
restored. No model-specific workaround, server Core change, build, commit, or
publication was needed for this closure.

## GFX-002 and DVD-001 closure, Pro stock gate (2026-10-05)

Both tasks are complete on Fire TV Pro `.29` against unmodified stock `.175`.
The final debug APK is SHA-256
`4a8ccb1309e2c92c8bdd7a7e81087ab3e1608de99d6da7ea7c61e851cd3e75d3`.
Its debug-only reconnect seam now rejects the first local type-5 attempt before
opening a socket, accurately driving the bounded production retry without
making stock SageTV accept and rebuild twice. Physical connection telemetry
records `reconnect_test_first_attempt_rejected`, then socket type 5 accepted
and `reconnect_succeeded` on the following attempt.

The companion plugin also exposed a real stock DVD Push behavior: immediately
after reload, `MiniDVDPlayer.seek()` can echo the requested position before its
reader STC moves. The adapter now waits three seconds before accepting that
clock, retries only the plugin-owned public `Seek(long)` at most three times,
and never uses a private event or client-local Push seek. In the decisive
ALADDIN run it observed attempts 1 and 2 still at `19519 ms`; attempt 3 reached
`520586 ms`. Sixty seconds after the forced rejection/retry, client and server
both reported about `580.9 s`, Media3 hardware MPEG-2/AC-3 A/V was advancing,
and video/audio drop and player-error counters were zero. A separate fresh
stock gate reached the eight-minute target and sustained `0.9996x` cadence
with zero A/V drops. Prior focused gates already cover Unified Off/On,
passthrough/PCM, live encoded offset, and audio-output restart.

Stop/Back after both a normal GFX reconnect and the forced retry returned to a
fully rendered SageMC start screen. This joins the earlier non-Pro post-fix
repeats and closes GFX-002: the identified interrupted-command dispatch bug is
fixed, the bounded context-loss path remains available, and the reproducible
fault sequences no longer leave stale or garbled frames. Evidence files are
`artifacts/firetv/20261005-dvd001-pro-stock-recovery-pre.json`,
`20261005-dvd001-pro-stock-settled-retry-pre.json`,
`20261005-dvd001-pro-forced-retry-pre.json`,
`20261005-gfx002-pro-sagemc-clean.png`, and
`20261005-gfx002-pro-forced-retry-clean.png`.

Stock `Sage.jar` remains SHA-256
`d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
The installed development Client Extension JAR is SHA-256
`3b51ae250aa845903dfb5e2cc051514d10a06fda3c6fa6f11abf778d6e15b9f5`.
Both UI DVD overrides were restored blank, recovery/trace were restored false,
device preferences and sleep values were preserved, and exact temporary
on-device screenshots were removed. No commit, publication, or Core change was
made.

## GFX-002 GFX reconnect frame integrity, non-Pro stock gate (2026-10-04)

The second saved pre-fix DVD/GFX screenshot was re-inspected after the user
reported corruption: `artifacts/firetv/20261004-221824_gfx-dvd-repeat-mytv.png`
is **not clean**. It has broken SageMC text and a mostly black pane. The prior
one-pass visual assessment below was wrong. The client log also recorded a
GFX ZLIB `invalid stored block lengths` read failure and a rejected type-5
reconnect before the client returned to its server list. This is concrete
DVD/GFX-sequence evidence, but does not prove that an EGL context was lost.

Source inspection exposed a separate definite bug: after a GFX read failed
mid-command, `MiniClientConnection` installed a new GFX socket but then
published the interrupted old command buffer to the renderer. The reader now
discards that partial command and starts reading from the new stream. This is
stock-protocol-only; it changes neither server Core nor plugin negotiation.
The focused connection tests pass (24), and the debug APK built and installed
on non-Pro `.25` without clearing settings. With the same stock `.175`, exact
generated DVD, debug-only GFX socket fault, and Stop/Stop/Home sequence, two
post-fix runs returned a complete SageMC start screen; the second also opened
`My TV` with readable recording rows. Screenshots:
`artifacts/firetv/20261004-gfx-fix-stv-after-fault.png`,
`20261004-gfx-fix-stv-after-fault2.png`, and
`20261004-gfx-fix-mytv-after-fault2.png`. The fault still resets DVD to its
root menu; that is DVD-001, not a graphics pass. GFX-002 remains open for a
natural-corruption recurrence and cause confirmation. No `.175` restart,
settings reset, app-data clear, commit, or release was made.

After the earlier GFX-001 missing-handle gate, the non-Pro showed a distinct
SageMC failure: the right pane had black areas, duplicated/misplaced glyphs,
and repeated image fragments. Navigation did not repair it. A full Android
client reconnect restored complete text/artwork on stock `.175` without a
server restart, plugin selection, settings reset, or `Sage.jar` change. The
Client Extension DVD override for that UI was blank, so do not attribute the
observed rendering directly to the server plugin.

The Android OpenGL renderer now treats a *second* `onSurfaceCreated` while a
MiniClient connection is live as invalidating cached GL texture/FBO names.
It schedules the existing fresh-Activity/client handoff on the UI thread,
preserving the stock watch state rather than sending user STOP. A one-minute
process cooldown prevents a context-loss loop. A debug-build-only,
exact-server-guarded `gfx_context_recreated` trigger exercises this path;
it is not a release-client protocol command. The updated APK built and was
installed on non-Pro `.25` with settings preserved. All 23 focused connection
source-contract tests passed. On stock `.175`, the guarded trigger logged
the recovery and a new MiniClient connection; `My TV` then showed complete
SageMC text/artwork. An authored DVD root/title plus debug-only GFX-read
fault, Stop/Stop/Home, and `My TV` also returned clean artwork/text.
Screenshots: `artifacts/firetv/20261004-211412_sagemc-current-render-20261004.png`
(observed corruption), `20261004-220544_gfx-trigger-mytv-clean.png`
(guarded recovery), and `20261004-221338_gfx-dvd-poststop-mytv.png`
(DVD/GFX/Stop/Home), under this project.

The guarded context-recreation trigger proves that recovery path, not that
EGL loss caused the observed corruption. The later re-inspection and
interrupted-command correction above supersede the earlier claim that the
DVD/GFX sequence did not reproduce the corruption.

## GFX-001 SageMC image recovery (2026-10-04)

SageMC artwork was intact before the stock `.175` generated-DVD/GFX tests, but
one DVD Stop/Home sequence left image-backed menu elements missing. Android
logcat repeatedly reported `Failed to Render Texture` because the server drew
handles such as `-54` that the client `ImageCache` no longer held. A fresh
client connection restored the menu without changing SageTV or app settings.
The stock MiniClient renderer already accepts image-unload replies by clearing
its native-image pointer; a subsequent repaint reloads that image. The client
now skips the missing draw, reports each lost handle at most once per two
seconds, and coalesces a full repaint after the frame at most twice per second.
The tracked handle set is bounded and resets on successful image load or cache
cleanup. No new server event, plugin feature, or `Sage.jar` change is needed.

The debug APK built successfully and the three focused policy JUnit tests
passed. On non-Pro `.25` against stock `.175`, generated DVD Watch/Select,
Stop/Stop/Home preserved full SageMC artwork and text. A separate DVD test
closed only the GFX read socket through the debug-only, exact-server-guarded
receiver, then Stop/Stop/Home again returned to complete artwork/text. The
sampled log had no further texture-null errors. Evidence screenshots are in
`artifacts/temp/vce-physical-20261004/gfx-start.png`, `gfx-after-stop.png`,
and `gfx-after-fault.png` under the workspace, outside the project. The DVD
reader-position and actual transient type-5 rejection gates remain DVD-001;
this UI fix must not be taken as passing those distinct gates. No commit or
release was made for this checkpoint.

## MIMFIX-003 caption-after-seek visual resolution (2026-10-03)

The equal-duration generated 90-second CEA-608 A/B fixtures were exercised on
non-Pro Fire TV `.25` against stock `.175`/Pull, Vibe `.232`/MIM Direct, and
stock Windows `.185`/MIM Direct. The 2-second-cue fixture now shows complete,
legible STV-rendered caption rows after FF on all three paths. The decisive
Linux Direct screenshot is
`artifacts/firetv/20261004-025227_caption-media3-fixed-legacy-callback-settled.png`:
PTS 30/32/34 at video PTS 35.9, without an Android-local overlay. Windows
Direct shows the same rows in
`artifacts/firetv/20261004-025741_caption-media3-fixed-legacy-callback-settled.png`.
The fast 0.5-second fixture also shows correctly spelled text after seek on
`.175`, `.185`, and `.232`; individual stills can land between short cues, so
it remains a packet/cadence stress source rather than the sustained visual
oracle. These are authored, copyright-free fixtures, not a real broadcast.

On `.232`, the FFmpeg plugin's loopback caption tap originally produced
missing CEA pairs, while the authored elementary stream and remux were
complete. The default Linux UDP receive queue was ~208 KiB; a test-only
plugin class overlay requesting 4 MiB before bind, plus a decode-order
cursor correction, delivered complete raw pairs and legible slow-cue text.
The queue-overflow mechanism is strongly supported by before/after raw tap
evidence, but no kernel drop counter was captured. The client removes an
ineffective 350 ms Fixed-caption delay and corrects the bridge's flush lock
order. The caption harness records settled packet PTS and player-clock values
separately from its event-225 wire gate. The same client build passed stock
Windows fast and slow Direct captions after FF. The Android build, 20 focused
Python tests, and targeted bridge/Fixed-side-channel/Direct-clock JUnit tests
pass; FFmpeg plugin tests pass separately. No full unrelated matrix was rerun.

All 108 Android settings and device stay-awake values were restored after
each physical run. The exact generated imports/files were removed and the
libraries rescanned on all three servers. `.232`'s outdated Core MCP bridge
was updated to tested 0.1.4 to use the stock `RemoveLibraryImportPath` API for
cleanup; its prior JAR is recoverable under a non-`.jar` suffix. `.232` retains
the test-only FFmpeg caption-class overlay and a recoverable original JAR.
Stock `.175` and Windows `.185` Sage.jar/plugin binaries were not changed.
No commit, GitHub release, or plugin publication was made. The broader
MIMFIX-003 legacy/client/lifecycle matrix remains open.

## MIMFIX-003 Windows owned seek/pause follow-up (2026-10-03)

On unmodified stock Windows `.185` and non-Pro `.25`, the generated 90-second
2-second-dwell MPEG-2/AC-3/CEA fixture was staged through the stock Core MCP
import API. Media3 Fixed/MIM Direct Transcode kept the HTTP stream owned and
the caption side channel active. An STV-authoritative Off/CC1/CC2/Off/CC1
cycle and two FF/REW commands delivered event-225 updates; the two seeks
reported A/V recovery in 3.282 and 3.535 seconds. The post-seek screenshot
`artifacts/firetv/20261003-222247_caption-media3-fixed-legacy-callback.png`
shows caption text, but its rolled-up PTS rows are malformed. This is a visual
quality issue to isolate with the authored 608 generator and stock decoder;
do not equate wire recovery with correct rendered text.

The first broader session resumed near the end of the 90-second fixture, so
its FF reached EOF and made the subsequent pause observation invalid. A repeat
started with an explicit server-side `--start-ms 0` and passed owned HTTP A/V,
FF/REW, and pause/play. The session harness now rejects `afterState=5` as an
end-of-media false positive; 38 focused automation tests pass. A third,
pause-only physical run using that revised harness proved `beforeState=2`,
`afterState=2`, `outputHealthy=true`, advancing audio and video, and 3.041 s
recovery. Passing the
Windows path as `C:/ProgramData/...` resolves and starts through Core MCP,
whereas backslashes passed through the Windows-to-WSL CLI were rejected. The
server and `Sage.jar` need no path fix for this boundary.

After the gate, the temporary Windows import and exact hash-verified
112,483,408-byte file were removed and the stock library rescanned. Windows
`cc_debug=false`, SageTV64 running, stock `Sage.jar`, and the previously
working FFmpeg JAR were verified. Android restored all 108 preferences and
device sleep settings. Direct deletion of the local generated fixture was
blocked by command policy; its exact hash-verified copy was moved to
`C:/TMP_SAGETV_DOCKER/artifacts/cleanup-quarantine/mimfix003-cea-dwell-2s.ts`
and remains recoverable. No commit or release was made for this checkpoint.

## MIMFIX-003 caption visual control (2026-10-03)

The earlier visual failure was reproduced with the 0.5-second-cadence
generated CEA fixture on both stock `.175`/Media3 Pull and stock Windows
`.185`/MIM Direct Transcode, despite continuous event-225 traffic. A real
PBS broadcast recording on stock `.175` then **visibly rendered CC1** on the
same non-Pro `.25`, Media3 Pull, STV-authoritative callback path; CC2 and Off
were visually clear. This rules out a general STV/event-225 display failure.

For a controlled fixture alternative, `generate_a53_seek_fixture.py` produced
a separate 90-second, copyright-free MPEG-2/AC-3/CEA file using
`--caption-interval 2.0`. The Vibe FFmpeg output was hash-verified and
temporarily imported on unmodified stock Windows `.185` through Core MCP.
Media3 Fixed/MIM Direct Transcode with deinterlacing Off and an active caption
side channel visibly passed CC1, Off, then CC1 again. In the first CC1 capture,
the STV drew three timestamped caption rows; the final CC1 capture drew two.
The Android-local cue remained empty, so this was the stock STV renderer, not
an overlapping local overlay. The 0.5-second fixture remains suitable for
packet/seek stress but is **not** evidence that normal stock STV captions fail;
its near-continuous 608 row updates leave little stable display dwell. This
is a strong fixture-cadence inference, not yet a same-duration single-variable
proof. Retained private screenshots are
`artifacts/firetv/20261003-220805_caption-media3-fixed-stv-2-cc1.png`,
`20261003-220823_caption-media3-fixed-stv-4-off.png`, and
`20261003-220832_caption-media3-fixed-stv-5-cc1.png`. The real-broadcast
control is `20261003-215516_caption-media3-pull-stv-2-cc1.png` and must not
be used in a public report without permission.

The caption harness now exposes a temporary `--fixed-caption-side-channel`
switch for deterministic Fixed testing. Its 20 focused caption tests pass.
All 108 saved Android settings and sleep values were restored. The generated
Windows import and its 112,483,408-byte test file were removed after exact
path/hash verification and a library rescan; the local generated copy was
removed and can be recreated with the command in
`docs/PLAYBACK_DIAGNOSTICS.md`. Windows `cc_debug` is back to `false`; stock
`Sage.jar` and the known-working FFmpeg plugin JAR remained unchanged.
MIMFIX-003 is still open for same-fixture owned seek/pause stability and the
remaining stock/legacy/cross-player rows. Windows exact-path Watch through
the Windows-to-WSL wrapper returned HTTP 400 for the drive-letter path even
though Core MCP resolved the same path directly; MediaFile-ID Watch (`20737`)
passed. Resolve that automation path boundary separately, without changing
the working stock Core or mistaking it for a media-playback failure.

## MIMFIX-003 Windows/non-Pro commissioning checkpoint (2026-10-03)

At the user's request, the remaining stock-Core Windows `.185` / non-Pro `.25`
gate was brought forward. SSH was enabled, and the approved guarded deployment
installed the unreleased stock-compatible Core MCP 0.1.4 JAR. The desktop
SageTV processes that had locked the prior attempt were already absent; only
`SageTV64` was restarted. The stock `Sage.jar` stayed byte-identical. ADB
connected through `dev.cmd`, and the USB HDMI adapter showed the non-Pro at
1920x1080. The Android harness's exact-path reply budget was corrected.

The `.185` database still indexed a nonexistent `V:` drive. An original,
hash-verified generated MPEG-2/AC-3/CEA fixture was copied to a temporary
local Windows directory, registered and scanned through stock SageTV APIs,
then played by exact path. Media3 Fixed / MIM Direct Transcode with
deinterlacing Off proved plugin-owned HTTP transport and advancing A/V. A
focused FF/REW attempt failed once and passed on repetition, so seek stability
is not closed. The caption side channel was enabled, attached, and active:
the later packet counters recorded 1,807 CEA packets, and the stock server
received more than 2,000 standard event-225 callbacks. The earlier generic
session gate's zero Android-local cues was not a caption transport failure;
STV callback mode intentionally detaches the Android overlay. Explicit Android
CC1 visibly rendered the generated PTS text. However, two stock-Windows STV
CC1 screenshots after 10-second holds showed no caption text, despite the
Core MCP API reporting CC1 and continuous callback bytes. This is a **failed
visual STV caption gate**, not a pass inferred from wire counts. An experimental
STV-only renderer suppression was reverted because it would hide the working
local fallback; the settings-preserving non-Pro debug APK is back at the
known-good SHA-256 `5b6b8bd97417fea75cd7c595b4d393499a8497d0a545c58f947f7a14121958ae`.
The dedicated caption harness now labels event-225 checks as transport-only
and retains screenshots for a separate visual review. FF and REW recovered
event-225 output in 2,727 and 2,737 ms in the extended run; this does not
close the broader owned-seek stability row. The session test has an explicit
`--fixed-caption-side-channel auto|on|off` override and enables it automatically
when a Direct caption gate requests captions. Its 38 focused automation tests
pass.

A diagnostic FFmpeg plugin JAR with read-only caption packet counters passed
local Java tests but changed Direct startup behavior on `.185`. It was rolled
back to the known-working installed JAR; the diagnostic copy has a non-`.jar`
suffix and cannot load. A final strict Direct-owned A/V smoke passed after the
rollback. The temporary import was removed through Core MCP, its verified
duplicate fixture/directory was deleted (recoverable by copying the preserved
original fixture again), and the library was rescanned. The `.185` stock
`Sage.jar` and known-working FFmpeg plugin hashes remain unchanged. All
108 checkpointed client settings and Android stay-awake values were restored.
MIMFIX-003 remains open for Windows captions/seek stability and the remaining
plugin-state, codec, lifecycle, and legacy-client/extender rows. Do not rerun
already completed unrelated Linux or audio matrices.

## AUDIO-005/006 closure (2026-10-03)

Both audio tasks are closed for the current Pro `.29` / unmodified stock `.175`
build. The C920 original TV/surround, direct-HDMI PCM A/B, PBS speech, authored
pulse, dual-audio track, PMT/format, live replacement, seek, and pause gates
are documented in `docs/AV_SYNC_PHYSICAL_GATE.md`. A final focused
`mcp-lifecycle-test --encoded-offset-ms -400` passed direct Media3, direct
legacy Exo, GSY Media3, and GSY legacy Exo. Each retained the applied encoded
clock path across HOME/return, user pause, second HOME/return, explicit PLAY,
and teardown, with all 234 saved settings and Android sleep values restored.
The opt-in encoded setting remains off by default; IJK remains unsupported.
The user requested closure before MIMFIX-003, so a later shared A/V change
requires only affected-row revalidation under that compatibility task.

## Latest Pro webcam gate (2026-10-03)

With the Pro on the original TV/surround route and stock `.175`, a direct
1920x1080/30 C920 video/microphone capture proved the encoded-offset response
near both slider ends: `-3750 ms` requested measured `-3777 ms` relative to a
nearby zero baseline, and `+3750 ms` measured `+3719 ms`. Each passed the
four-corner framing gate. Both exact `-4000/+4000 ms` endpoints were accepted
and visibly displayed; exact multiples of the fixture's two-second click
period cannot independently prove their absolute physical displacement.
Reset to zero returned within one 30 fps frame of the first zero baseline;
the retry's overexposed top-left mark made it diagnostic-only, while an earlier
four-corner zero-reset row passed. The user's `-400 ms` encoded setting and
Android sleep settings were restored, and playback was exited. Close an open
calibration dialog before setting a new initial offset: that dialog does not
inherit a changed player value while open.

The capture helper already resets C920 Zoom/Pan/Tilt before and after FFmpeg
opens its DirectShow graph. Judge settled frames, not the first second. The
framing analyzer now skips the transient crop and prefers warm authored
corners over neutral bezel glare; the older truly cropped recording still
fails. `docs/AV_SYNC_PHYSICAL_GATE.md` has the full measurements and limits.
The focused cross-player lifecycle acceptance subsequently passed at revision
113, closing AUDIO-005 and AUDIO-006 on the current build.

## Reproducible physical A/V synchronization gate (updated 2026-10-03)

The embedded Media3 calibration asset and the stock-server PBS/OTA-profile
fixture now share the same authored impact/click clock. Both use a
lower-luminance ring, smooth frame-by-frame motion, and a 200 ms post-impact
hold. The embedded 1280x720 H.264/AC-3 asset is now 60 seconds long so the
complete `-4.000..+4.000 s` slider can be exercised without crossing its
repeat boundary; the server fixture is
1920x1080 top-field-first MPEG-2 at 30000/1001 with stereo 48 kHz AC-3 and a
7 Mbit/s transport mux. Stock `.175` holds the current generated file at
`/var/media/OpenSageTV_Vibe_Tests/OpenSageTV-Vibe-PBS-1080i-MPEG2-AC3-AVSync-v2.ts`
with SHA-256 `3c81a7447650e8fdc719c9d04d258131db0f1c2496401d74e249c2d5294b8391`.

`capture_av_sync_webcam.py` records the directly attached C920 video and mic
through one Vibe FFmpeg DirectShow graph and Matroska clock; `--video-only` is explicitly a clarity
preflight and cannot close an audio gate. `analyze_av_sync_webcam.py` removes
the static fixture background, locates impact holds, detects the 1 kHz clicks,
and reports signed audio-minus-video milliseconds. The expected selected
offset is supplied for values beyond half a second. Exact multiples of the
two-second event period still require the selected offset and player evidence
because identical impacts alone cannot identify an event number. Generator
controls measured 6.166 ms (embedded zero),
4.716 ms (server zero), +408.5 ms (authored +400), -393.0 ms (authored -400),
and +4009.5 ms (authored +4000).

The debug MCP surface exposes `dev_show_av_sync_test`, and `firetv.toml` has a
mode-controlled `pbs_av_sync` exact server-path fixture. Direct C920 video and
microphone capture now passes at 1280x720/30 in one FFmpeg DirectShow graph.
FFmpeg's DirectShow graph was reapplying stale Zoom 144, Pan 1, and Tilt -10
controls after the original pre-open reset. The capture helper now resets those
controls again after graph open. Zoom 100/Pan 0/Tilt 0 records the complete TV,
and all four authored registration corners pass the primary framing gate.

On Pro `.29` and stock `.175`, direct Media3 Pull measured `-416.667 ms` for a
requested `-400 ms` and `+410.000 ms` for `+400 ms`; pause/resume retained
`+389.333 ms`, and an exact seek retained `+382.667 ms`. GSY Media3 Pull
retained `+430.000 ms`. Direct legacy Exo Pull produced an endpoint-to-endpoint
slope of 1.016 across `-400/+400 ms`, and GSY legacy Exo Pull measured
`-389.333 ms` for `-400 ms`. HOME stopped the audible click sequence at the
transition. The embedded encoded test measured `-370 ms`, `+410 ms`, and
`+4034 ms` relative to its matched route baselines; decoded PCM zero was stable.
Legacy Exo Dynamic negotiated real SageTV Push and truthfully deferred the live
offset rebuild (`passthrough_offset_push_deferred`); the Pull implementation is
the validated live-adjustment path.

Complete-TV four-corner framing now passes. These cases concern the Pro's actual HDMI audio device; a matched
non-Pro zero-offset row is not required. AudioFlinger showed a live DIRECT AC-3
thread on the Pro, so `FORCE_NONE` and `mHdmiSystemAudioSupported=false` do not
prove passthrough is absent. A later diagnostic opened while the main player's
audio-output rebuild was still pending and produced
`ERROR_CODE_AUDIO_TRACK_INIT_FAILED`, then a visible recovery-limit error.
Both Media3 and legacy Exo now carry diagnostic audio suspension across player
replacement and track selection, and the MCP diagnostic normally waits for the
requested output to settle. An explicit immediate-open stress gate passed on
Pro `.29` / stock `.175`: `diagnostic_audio_suspended` preceded
`audio_output_live_rebuild`, the new diagnostic log had no AudioTrack-init
failure, and playback reached normal EOS with `audioOutputErrorCount=0` and
`health_errorState=false`. All 234 saved settings and Android stay-awake values
were restored. A repeated synchronized receiver measurement has now passed
on the user-confirmed original TV/surround route.

**AUDIO-001 is complete.** Centered C920 captures on stock `.175` measured
direct Media3, direct legacy Exo, GSY Media3, and GSY legacy Exo with encoded
`0/-400/+400 ms`; all passed the four-corner physical gate. Direct Media3
decoded PCM at zero and a live encoded zero reset passed too. On the real
PBS NewsHour 23-minute speaking interval, source-matched speech showed the
expected reduction as encoded offset moved from zero to -400/-650 ms, while
decoded PCM also retained route delay. The source TS has primary AC-3 PTS
`1380.000000 s` and nearby MPEG-2 PTS `1379.995833 s`, not a 500 ms source
gap; live player snapshots at the PBS interval and authored fixture reported
zero audio underruns, output errors, and error state. Do not introduce a
model-wide correction. The alternate direct-HDMI PCM A/B is now complete:
the full-raster authored fixture measured -91.7 ms on HDMI capture versus
+628.0 ms on the original TV/surround webcam path; PBS speech at 23 minutes
measured -179.4 ms versus +730 ms respectively. These are route-specific
observations, not proof that the TV/AVR alone accounts for the difference;
the HDMI sink/EDID and capture-card timing also changed. AUDIO-005 retains
only the final post-MIMFIX-003 lifecycle matrix; AUDIO-006 retains its
full-range/end-state physical gate. Private captures remain local; see
`docs/AV_SYNC_PHYSICAL_GATE.md` for methods and measured tables.

Supporting lifecycle evidence is now complete for format and live-source
replacement. Direct Media3, direct legacy Exo, GSY Media3, and GSY legacy Exo
each passed the generated `h264-pmt-audio-track-switch.ts` transition through
stock `.175`; a focused matrix run now terminates after the requested row and
does not append unrelated codec cases. The same four backends each passed a
real `2.1 -> 5.1` stock-server live channel/source transition with advancing
hardware audio/video, visible full-screen playback, no crash signature, and
all 234 settings restored. These rows prove lifecycle stability but cannot
replace the missing original encoded receiver/ARC measurement.

Selectable-audio lifecycle is also complete as supporting evidence. The new
debug-only `dev_set_audio_track` MCP control calls the existing active-player
API and never changes SageTV Core or the production wire protocol.
`mcp-audio-track-test` used the commissioned dual-AC-3 seek fixture to switch
5.1 -> stereo -> 5.1 under encoded `-400 ms` on direct Media3, direct legacy
Exo, GSY Media3, and GSY legacy Exo; every row retained advancing A/V and all
234 settings were restored. The Media3 selector now replaces an earlier
same-type override atomically, preventing a post-output-rebuild selection from
snapping back to the restored/default track. The mixed UK Breakfast AC-3/
MPEG-L2 pair additionally passed both directions in decoded mode through the
Media3 FFmpeg audio decoder with the same offset. Use dual AC-3 for the encoded
offset gate so codec fallback is not confused with passthrough timing.

The final 2026-10-03 camera recheck found that the camera was already positioned
to see the complete TV. The apparent crop was software state applied when the
DirectShow graph opened. A verified post-open reset restores Zoom 100, Pan 0,
and Tilt 0 on every capture. At 1920x1080/30, all four registration corners are
visible; the analyzer accepts yellow that camera exposure clips to neutral
white only inside the expected L-shaped edge masks. A zero-offset capture then
measured a stable `+611.333 ms` route baseline. Changing the same open encoded
dialog to `-400 ms` measured `+251.333 ms`, a `-360 ms` corrected effect and
`40 ms` residual. A reusable `mcp-av-sync-screen` command opens the calibration
surface through MCP without menu coordinates and can set the active diagnostic
output/offset. The earlier cropped endpoint rows remain diagnostic history.
The output route reports `mHdmiSystemAudioSupported=false` and `FORCE_NONE`,
but AudioFlinger separately confirms the active DIRECT AC-3 output. Those
flags must not be used as a proxy for passthrough. The remaining gate is on
the Pro HDMI path, including an error-free calibration-dialog open/close;
non-Pro ADB authorization is unrelated to this audio closure.

The same work found a stock DVD test-control race, not a decoder regression.
ALADDIN's first public `Seek(long)` can be accepted while MiniDVDPlayer is
replacing its startup title and then be discarded without an STC anchor. The
MCP gate now observes each attempt for a bounded interval and retries the same
public server seek at most three times when no STC is emitted. On Pro `.29`
against unmodified `.175`, the rerun landed at `481848 ms`, sustained hardware
MPEG-2/AC-3 output at `0.991x`, and passed harness-owned Stop/teardown with
Unified graphics enabled. The local C920 ALADDIN capture is private supporting
evidence only and must not be published. A subsequent debug-only forced GFX
socket fault on this same Pro/stock-server path accepted type-5 immediately,
reconnected media, and resumed A/V without a player error, but the DVD
position fell from about `512790 ms` to `14629 ms`. Stock Core's DVD reload
captures the old position yet skips `player.seek(...)` when its reloaded DVD
looks like an initial load. DVD-001 therefore remains open for stock-compatible
position preservation and the still-unproven transient type-5 rejection retry;
do not treat the successful handshake as a completed reconnect gate.

On 2026-10-04 the user restricted further DVD testing to non-Pro `.25`.
That device reproduced the same stock `.175` position reset: ALADDIN was
positioned at `481848 ms` with sustained A/V, then a debug-only graphics-socket
fault reconnected but left its timeline at `37378 ms` in the first post-fault
snapshot. Through the already-installed stock-compatible Core MCP plugin,
public `Seek(long)` was accepted. The first request did not emit a new DVD STC;
a second bounded request did, and the client reached `494745 ms` with video
and audio output advancing. Evidence begins at
`artifacts/firetv/dvd001-nonpro-baseline.json`; the manual fault and API
responses were observed in the test session. Playback was stopped, SageMC's
Stop popup dismissed, and the non-Pro's pre-existing keep-awake values were
restored. This validates the API *remedy*, not automatic recovery or a genuine
transient type-5 rejection; DVD-001 remains open. The Android client has no
existing authenticated stock-Core API channel for a production seek, and a
local MiniPlayer seek cannot reposition the server-owned DVD reader. Resolve
that stock-compatible automatic trigger without adding a private MiniClient
event or claiming the test-only manual seek is a fix.

The user physically tested **Restart audio output** on Pro and reports that
it works. SEEK-001's corresponding sub-gate is closed at checklist revision
115, paired with the earlier automated proof that passthrough, selected AC-3
stream, `-400 ms` offset, and server playback position survive a healthy live
rebuild. The final visible skip/Comskip acceptance and non-Pro reference remain
open. The scoped Pro stay-awake settings were restored after the DVD test.

## Bounded automatic Push-stall diagnostics (2026-10-02)

DIAG-001 adds a default-on switch under the main **Diagnostics Settings**
screen for release-build evidence when an already-playing ordinary Push stream
buffers for at least 1.5 seconds. The incident sampler exists only during that
bounded failure window, runs for no more than 30 seconds plus a three-second
recovery tail, retains at most 140 numeric/state samples and four incident
files, and persists on one background executor. Disabling the setting cancels
an active sample and prevents a file from being finalized.

The JSONL evidence contains no media payload, path/URI, server address,
credentials, or client identity. It correlates datasource reads/waits, Push
arrival/completion and blocked-write time, ring occupancy/free space, player
position/buffer/state, selected decoders, and connection generation/reconnect
count. Manual ZIP and Always-mode exports include the same bounded files. The
main diagnostics screen also consolidates the existing file-log, log-level,
unmapped-key, aspect, log-share, bundle-export, and independent diagnostics-SMB
controls without changing their stored keys or values.

The focused Core telemetry tests, Android compilation, 18 diagnostic/settings
tests, strict validation, and clean 60-task APK build pass. On stock `.175` /
non-Pro `.25`, an enabled fixed-Push run captured a real 1.892-second rebuffer
and its recovery tail while A/V continued and the crash check remained clean.
The same playback with capture disabled produced no additional incident file;
all 108 saved settings and the original enabled value were restored. The
recorder itself does not recover, throttle, or alter Push playback. A future
occurrence of the intermittent longer Pro stall can now be diagnosed from an
ordinary exported bundle without a special build.

## v0.5.100 publication (2026-10-01)

Commit `ff2549c` is published as v0.5.100 with grouped bullet-form release
notes and all five required assets. The release packages the bounded stock-
Push post-Comskip recovery, Fire OS stale long-press containment, manual app-
owned audio-output restart, and persistent MCP ADB authorization. The affected
source/Core tests, strict validation, 1,487-file manifest, physical stock
`.175` / non-Pro Push gate, settings-preserving Pro installation, clean
60-task APK build, and strict APK inspection pass. GitHub repository checks
and Pages deployment also pass; the release tag targets the tested commit,
the latest-release API returns v0.5.100, and the permanent Pages downloader
returns HTTP 200.

All five public assets were downloaded again and match their local SHA-256:

- APK: `04276b8df011a89169539d50d815fb4b45092166c093cc254ff48b1e8c23eea7`
- source ZIP: `63626a321a9081c620cec323400e873e3f92ab4e4bdb69f0dcf1dbdddc82fd89`
- GitHub review ZIP: `b250e0cfd5a671228caa388990bfc681e12b6b6508eb0a522c761cca26d2e466`
- release manifest: `3fb306c60bc6a2ace6c011ea024034334029c1a233253afa61895b855e92ab3a`
- SHA-256 list: `2d0962460659bbf94e6260b5542a3a92c7a28a0a16b6ead3787f2f5a23f4c2f0`

Final visible Pro Comskip landing, fresh FF/RW input, and manual audio-reset
acceptance remain explicitly open under SEEK-001; publication does not claim
those user-visible checks as complete.

## Manual app-owned audio reset (2026-10-01)

The live Audio settings menu now includes **Restart audio output** when the
active backend is Media3, legacy Exo, or a GSY delegate backed by either one.
The action uses the existing bounded live-output rebuild to release the player
and encoded/PCM Android `AudioTrack`, retain the current datasource, recreate
the player, restore the selected audio stream, and resume. Session passthrough
policy and audio offset remain unchanged; saved device defaults are not
modified.

This is the strongest safe reset available to a normal Android application.
It cannot restart privileged Fire OS AudioFlinger/HAL services or power-cycle
the HDMI receiver. Failure to clear a fault therefore identifies a lower-layer
route problem that still requires a Fire OS audio-mode toggle or device
restart. The 133 affected tests and clean 60-task APK build pass. APK SHA-256
is `f8f5e4f3d76ba185b992bf88902f1bc293d514ea27c1321a9c3405255d22943f`;
it was installed on Pro `.29` without clearing settings.

## Fire OS stale long-press containment (2026-10-01)

Pro `.29` exposed a second input defect while accepting SEEK-001: after one
long-Right commercial skip, later Left/Right presses could continue to send
the long-press action instead of their configured FF/RW actions. The key
processor previously stored long-press state globally and assumed every
physical gesture delivered ACTION_UP. Fire OS can omit or delay that release
while playback is rebuilding after a seek.

`KeyMapProcessor` now binds hold state to the active key code and Android
`downTime`. A fresh key gesture resets an abandoned hold before command
mapping, and a delayed release from an older key cannot clear a newer active
gesture. This does not change short/long mappings, remote timing, or the
server-owned Comskip target. The affected 91 tests pass, and this fix is
included in the newer manual-audio-reset APK above. Visible user acceptance
remains open under SEEK-001.

The same Pro snapshot showed encoded AC-3 passthrough with the previously
saved `-400 ms` passthrough offset actively applied. No
`post_seek_push_recovery_started` event was present, so the new automatic
second reprepare did not run in that observed session. If recovery does run,
the existing extractor retains the same atomic passthrough-offset controller
across `setMediaSource(...)/prepare()`, so the configured value continues to
apply to the replacement epoch; an output receiver can still exhibit a short
transient while its encoded queue relocks.

## One-shot post-Comskip Push recovery (2026-10-01)

The open SEEK-001 work now includes a shared, device-independent recovery path
for the reported long-Right/Comskip stall. The Android key mapper only arms the
active player; SageTV still owns the Comskip command and destination. Recovery
cannot become eligible until the stock server subsequently sends FLUSH and the
first non-empty payload of its replacement Push epoch. Media3, legacy Exo, IJK,
and their GSY delegates then observe first-frame and buffering callbacks. A
stalled local reader may be flushed/re-prepared once, but the client never
sends a second server seek. Pull, SMB Direct, plugin-owned MIM Direct, DVD Push,
and external/system-player ownership remain excluded.

The first physical Fixed gate exposed that stock transcoding can report a
valid mux timestamp of zero. `MediaCmd` now publishes the first non-empty
post-FLUSH payload as the epoch confirmation without treating zero as an
absolute timeline anchor; positive timestamps retain the existing calibration
behavior. This keeps the recovery available on an unmodified stock server.

Six focused controller tests plus a zero-mux Push-epoch protocol test, 576
repository tests, 104 MCP tests, Core JUnit, project validation, and a clean
60-task APK build pass. On non-Pro `.25`
against stock `.175`, `PBS News Hour` ran through Media3 Fixed/Push with
hardware video and advancing audio. A real Android long-Right followed by the
stock `Seek(long)` control produced `post_seek_push_recovery_armed`,
`post_seek_push_server_flush`, `post_seek_push_server_anchor` with mux time
zero, `first_video_frame`, and `post_seek_push_recovery_healthy`; it did not
emit `post_seek_push_recovery_started`. The test restored all 107 client
settings. APK SHA-256 is
`4c57abffecb9feb25638d8f56a7f1c9d212b92f15b20975b77d50af5280398a7`.
That exact final APK repeated the stock `.175` physical gate and was installed
in place on Pro `.29` as version `0.5.99-DEV-DEBUG` without clearing its data.
Final visible Pro Comskip destination/landing acceptance remains open under
SEEK-001.

## Persistent MCP ADB authorization (2026-10-01)

`adb_connect` now establishes its persistent device shell, writes
`settings put global adb_allowed_connection_time 0`, and requires a matching
readback before reporting success. The returned `adbAuthorization` object makes
the setting, prior/current value, scope, and non-expiring state visible to every
physical-test caller. This changes only the lifetime of a key the user has
already approved; it cannot bypass the initial Android/Fire OS authorization
dialog. A rejected or ignored setting stops the half-initialized shell and
fails visibly.

The focused 55-test ADB suite passes. Real MCP stdio calls on non-Pro `.25`
and Pro `.29` both returned `currentValue=0`, `nonExpiring=true`, and one live
persistent shell. No APK or playback setting changed; this is host/MCP tooling.

## v0.5.99 publication (2026-09-30)

Commits `14eef99` and `fc9aacb` are published as v0.5.99. Repository checks
and Pages deployment passed; the release tag targets the tested source commit,
the latest-release API returns v0.5.99, and the permanent Pages downloader
returns HTTP 200. All five public assets were downloaded again and matched
their GitHub SHA-256 digests. The versioned APK is
`64ff1be013e9ded1a1a5ab7c9f0c945a2205888ab9fbe2c0ed0054f6116f5af8`,
the exact source archive is
`bdad5462da339e554d301df4d43dcd0dd1f2797b5b5bfc13bee284e73ce2ba24`,
and the review bundle is
`0ec955547175dbd4ada2a6aa85be36b0332e1cacd17cf0c5905684aed7b7ed35`.

The evidence-backed diagnosis, correction, stock `.175` physical gates, and
release link were posted to issue #3 at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/issues/3#issuecomment-5915800759`.
The issue remains open for reporter confirmation as requested.

## v0.5.99 stock-server growing Pull correction (2026-09-30)

The diagnostic bundle attached to issue #3 after v0.5.98 isolated a second,
stock-only form of the startup failure. An ambiguous legacy `stv://` Pull source
still called `classifyGrowthBeforePlayerBuild()` on Android's main thread. The
temporary MediaServer connection raised `NetworkOnMainThreadException`; the
legacy datasource converted that exception into a zero-size result, so the
growth policy resolved the active recording as completed. Media3 then received
the roughly 1.2 MiB opening snapshot as the final content length and exhausted
it after only a few seconds.

The prebuild path now performs no network I/O. Explicit Vibe active/completed
metadata remains authoritative, while a legacy SageTV candidate only prepares
the builder-time timeout policy. The retained Media3 datasource performs the
bounded size-growth classification from its real loader-thread `open()` and
then reports unknown length only when the file actually grows. A completed
recording still reports its finite length and remains seekable.

Focused source and Java policy tests pass, as does the clean APK build. On
non-Pro `.25` against unmodified stock `.175`, the active Live TV Pull gate
advanced video/audio in stable fullscreen. The completed-file control reported
a finite 899,959 ms duration and passed FF/REW recovery with continued hardware
MPEG-2 video and AC-3 audio. Both physical gates restored all 103 settings and
reported no player error or crash. Publication and reporter confirmation are
recorded separately below; issue #3 remains open until affected hardware
confirms the result.

## v0.5.98 publication (2026-09-30)

Commit `5251547` is published as v0.5.98. GitHub repository and Pages checks
passed; the public APK, source archive, review bundle, release manifest, and
hash file match the local SHA-256 values. The latest-release API returns
v0.5.98, its tag targets the tested commit, and the permanent Pages downloader
returns HTTP 200. The reproduction, cause, fix, and validation were posted to
issue #3 at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/issues/3#issuecomment-5911726108`.
The issue remains open for reporter confirmation as requested.

## v0.5.98 in-progress recording recovery (2026-09-30)

Issue #3 was reproduced on non-Pro `.25` against Vibe server `.232` with the
v0.5.97 client. MIM Direct session startup exhausted its bounded playlist wait,
the original-source Pull fallback then classified growth on Android's UI
thread, and a concurrent connection replacement cleared the mutable datasource
before Media3's factory consumed it. The resulting
`NetworkOnMainThreadException`, null factory result, and fatal
`setupPlayer must create a datasource` returned the client to its server UI.

The correction trusts the already-authoritative active/completed metadata
without a UI-thread network probe, captures the selected datasource for the
current Media3 setup generation, and routes a synchronous missing-source
fallback through the existing one-shot stock-Fixed reconnect. The behavior is
bounded to pre-first-frame Direct recovery and does not change the saved
transport preference or ordinary Pull setup. Per-read stable references also
prevent the retiring Media3 loader from dereferencing a datasource or command
stream that concurrent teardown has just cleared. Focused automated and
physical gate results are recorded before publication below.

The final candidate passed three consecutive Direct Transcode starts before
the teardown hardening and one after it, plus matched ordinary-Pull controls.
Every run used Media3 hardware decoding on non-Pro `.25` against `.232`,
produced advancing video and audio on channel 2.1, reached stable fullscreen,
and restored all 103 client settings. Direct runs retained
`active_transcode`. After a clean log reset, the final Direct and Pull runs
contained no `NetworkOnMainThreadException`, missing-datasource fatal,
null-reference, app-fatal, or ANR signature. The final debug APK SHA-256 is
`86abcdd264bf3222939b4fef75f7a47d7feba19560ddc7c439dda60ff41588d2`.

## v0.5.97 release preparation (2026-09-29)

Version 0.5.97 packages the already-validated MIM Direct growing-stream client
fix described below. Release validation is intentionally impact-based: the
affected source contracts, MIM Direct client tests, MCP playback-health tests,
manifest, clean APK build, strict APK inspection, and independent source
archive gate are required; unrelated completed device/DVD matrices remain
retained evidence rather than being rerun.

## MIM Direct growing-stream checkpoint (2026-09-29)

The Android client now propagates SageTV's existing timeshifted state to the
optional MIM Direct start request and uses the plugin-returned effective seek
offset as its timeline anchor. An out-of-range growing seek therefore no longer
leaves SageTV at the impossible requested time after the owned HLS
representation is rebuilt. The existing playback trace records the clamp, and
the debug-only exact-seek receiver dispatches an owned producer restart off the
BroadcastReceiver main thread. Ordinary player seeks and stock/plugin-absent
fallback behavior are unchanged.

The focused physical gates pass on non-Pro `.25` against `.232`: Media3 Direct
Transcode produced hardware H.264 plus advancing AC-3, clamped a 24-hour seek
to playable live media and recovered, then a separate bounded run proved REW
(-29,807 ms), FF (+6,463 ms), and two 5.1/2.1 channel transitions while every
stage retained `active_transcode`. The harness restored all 103 settings and
the plugin ended with zero active sessions. The tested debug APK SHA-256 is
`78661e9f899e6cf96b46f8a30365725ff95d28c5b3599804028c9756611345ea`.
Only affected tests were run; no unrelated full matrix was repeated.

## Owned-stream DVD success-gate checkpoint (2026-09-29)

The four locally executable MIMFIX-003 owned-DVD success rows now pass on the
non-Pro Fire TV `.25` against Vibe server `.232`. Direct Copy exercised the
authored root and Languages submenu, highlight placement, activation, repeated
entry, returns, menu/title transitions, and end-of-title behavior. Main-title
controls passed STOP/restart, chapter next/previous, repeated FF/REW, exact
seek, pause/resume, and a fresh playback after clean teardown without a stale
session or incorrect timeline landing.

STV-issued DVD commands selected Spanish audio selector `48513`, English
subpicture `64`, Spanish `65`, and Spanish-off `129`; audio remained `48513`
through every subtitle selection. Each server flush/reseek recovered advancing
video and audio, while DVD sample diagnostics retained timestamp/sync evidence.
Direct Copy reported `copy` and `dvd_mim_copy_v1`. Direct Transcode reported
`full_gpu` and `dvd_mim_transcode_v1`, exposed AVC to Android hardware decode,
and completed a 30-second cadence gate at 1.009x real time with 937 additional
video outputs and 977 additional audio outputs. Every run stopped cleanly and
restored all 103 private client settings.

Evidence is retained under `artifacts/firetv/` as
`mimfix003-dvd-menu-direct-copy-20260929.json`,
`mimfix003-dvd-controls-direct-copy-20260929.json`,
`mimfix003-dvd-stop-restart-direct-copy-20260929.json`,
`mimfix003-dvd-audio-subpicture-authority-direct-copy-pass-20260929.json`, and
`mimfix003-dvd-direct-transcode-cadence-20260929.json`.

The DVD-specific failure/fallback row also passes. With the plugin unavailable
on unmodified stock `.175`, Direct Copy reported `unavailable_stock_fixed` and
native hardware MPEG-2 retained menu activation plus pause/resume. On `.232`,
a guarded DVD-only provider shim separately failed before input and after
consuming 2 MiB. Both failures set the runtime-fallback diagnostic, restored
native MPEG-2 with advancing A/V and working pause/resume, and avoided a crash,
black screen, or lost session. The original MIM executable was restored at
mode 755 and its exact SHA-256 was reverified. Failure evidence is
`mimfix003-dvd-plugin-unavailable-stock-fallback-20260929.json`,
`mimfix003-dvd-provider-start-failure-fallback-20260929.json`, and
`mimfix003-dvd-provider-runtime-failure-fallback-20260929.json` in the same
artifact directory. The broader Windows/legacy/growing/transition row remains
open. Release validation continues to rerun only affected gates unless a full
matrix is explicitly requested.

A focused follow-up on clean stock-Core Windows `.185` proved the boundary that
must remain open: the authored DVD starts through the stock-compatible Core-MCP
`Watch` path, but an old Core never queries the optional `DVD_DISC_*` contract.
The session therefore remained native DVD Push (`discTransformedTransport=false`,
hardware MPEG-2) even when Direct Transcode was requested. The no-false-pass
cadence gate rejected it at 0.168x instead of reporting plugin ownership. This
does not invalidate the already-passed Windows QSV Direct gate for ordinary
video; it proves that plugin-owned DVD transport still requires the negotiated
Core DVD contract or a future stock-compatible design. Evidence is
`mimfix003-windows-dvd-direct-transcode-20260929.json`. Client settings were
restored after the failed gate.

## v0.5.96 public release (2026-09-29)

The remaining MIMFIX-003 late-start black-screen gap is closed. If Direct
session creation fails and the original exposed Pull source also fails before
the first rendered frame, the client suppresses Direct only for the current
connection and closes the existing GFX socket through the MiniClient's native
reconnect mechanism. SageTV then renegotiates capabilities and opens ordinary
Fixed/Pull while retaining the same Activity, UI/watch session, and connection
generation. The saved Direct preference is not changed. A bounded fresh-
Activity fallback remains only for platforms where the native reconnect cannot
be requested.

The debug APK includes a release-excluded one-shot fault that forces both
failure stages. On non-Pro `.25` against `.232`, the focused physical gate
passed with `connectionGeneration=1`, reconnect activity on the GFX/media
sockets, `mimDirectSessionState=late_failure_stock_fixed_reconnect`, empty
negotiated Direct mode, `playbackSource=SAGETV_PULL`, advancing hardware video
and audio, fullscreen playback, and no process exit or crash. The harness
cleared the fault and restored all 103 private preferences. The exact-path MCP
launch verifier now ignores a transient player error only while that named
handoff is active; 18 focused playback-health tests prove the recovery case
and an unrelated-error negative control. Per the workspace release rule, only
affected gates are rerun unless a full matrix is explicitly requested or a
broad dependency change requires one with a documented reason.

The v0.5.96 release candidate was rebuilt after that physical gate. Its debug
APK SHA-256 is
`b6fb14b8262643fa5f3098d974d916a224827fa4f142df0bfa0e5e9fc5f7ecba`.
Impact-based release validation passed the 60-task APK build, 149-task AAB
build and bundletool inspection, 213 affected source-contract tests, 78
affected MCP/ADB/playback-health tests, eight changed Java policy/client test
classes, and the Core MiniDVDPlayer compile/test boundary. The unrelated full
device and legacy DVD matrices remain deferred rather than being represented
as rerun for this release.

The public v0.5.96 release contains the versioned APK, source archive, review
bundle, release manifest, and SHA-256 list with bullet-formatted release notes.
Every downloaded asset matches its local hash, current repository and Pages
workflows pass, the latest-release API returns v0.5.96, and the permanent Pages
download endpoint returns HTTP 200.

## Direct full-hardware/deinterlace checkpoint (2026-09-28)

Fixed Transcoding Settings now exposes the persistent Fixed/MIM deinterlace
baseline as `Auto`, `On`, or `Off`. The active-player Video menu exposes the
same choices as a current-session override, which survives Direct start, seek
rebind, and restart but is cleared with the playback session. With `Off`, the
plugin reported `full_gpu` using VAAPI on
Linux `.232` and QSV on stock Windows `.185`. Non-Pro Fire TV `.25` passed
owned-stream startup, MediaTek hardware AVC decode, FF/REW, pause/resume, and
crash checks against both servers. Linux also reports full-GPU VAAPI for Auto
and On; Windows Haswell Auto/On accurately retain the mixed fallback because
its QSV VPP deinterlacer rejects the surface contract.

The Linux caption gate exposed and corrected a distinct ownership race. MIM
Direct preserved the CEA track and simultaneously forwarded decoded CEA over
the Fixed side channel. In STV authority, arrival of side-channel evidence now
reapplies the server-published CC1/CC2 state and disables the local Media3 text
renderer, leaving event 225 as the only renderer. The generated fixture passed
Off/CC1/CC2/Off/CC1 cycling; debug state kept local cue text empty, CC1 was
visible, and Off was clear. Evidence is
`artifacts/firetv/20260929-025928_caption-media3-fixed-stv-1-off.png` through
`20260929-025953_caption-media3-fixed-legacy-callback.png`.

The current settings-preserving APK SHA-256 is
`ce08a5df807776859233626c9cf58fb5db6952756d5893a003192b5aceacede0`.
It was installed with settings preserved and the harness restored all 102
preferences. The persistent deinterlace selector was then physically verified
on non-Pro `.25` with `Auto (server chooses)` displayed. All 575 Android/source
tests, 99 MCP tests, Core JUnit, strict validation, the 1,483-file manifest,
and diff checks pass. `.232` ended with zero caption/Direct sessions and no MIM or
FFmpeg process. Windows `.185` AC standby was restored to ten minutes; the two
verified temporary deinterlace deployment artifacts were removed. MIMFIX-003
remains open for the DVD, growing/transition, legacy-client, and late-fallback
rows listed in `TASKS.md`.

## Stock-compatible Fixed caption and Direct transport checkpoint (2026-09-28)

MIMFIX-001 and MIMFIX-002 are complete. The optional FFmpeg Standard plugin is
discovered over its tokenless LAN-scoped API and uses opaque, bounded
reservation/session handles. Direct Copy performs no video/audio decode or
encode. Direct Transcode uses the plugin/MIM negotiated GPU, mixed, or software
fallback. Neither mode changes `Sage.jar`, and an absent or unavailable plugin
restores ordinary stock Fixed behavior.

The Direct service now publishes a bounded live playlist of MPEG-TS segments
instead of asking FFmpeg's HLS muxer to convert DVB/Teletext to WebVTT. This
preserves CEA, Teletext, DVB bitmap streams, language descriptors, and timing.
Media3 wraps only those Direct segment requests with a passive byte observer;
the established Teletext PES probe and local Teletext/DVB rendering paths
therefore receive the same source packets without continuously instrumenting
other playback modes. Playback diagnostics identify this source as
`MIM_DIRECT`.

On non-Pro Fire TV `.25` against `.232`, Direct Copy, full-GPU Direct
Transcode, CEA Off/On, Teletext CC1/CC2/Off/CC1, DVB Off/On, pause/resume,
repeated start, reconnect, teardown, and settings restoration passed. Direct
and Pull displayed the same DVB multi-region composition. The current plugin
JAR deployed to `.232` has SHA-256
`35352767f1954c49782d23646f1f38da907c8ec4adab9709ed05e16e7eb473f9`.

The cross-player portion of MIMFIX-003 is also complete. Media3, legacy Exo,
GSY Media3, and GSY legacy Exo passed both Direct modes with advancing A/V,
pause recovery, crash checks, and clean exit. IJK passed Direct Copy. Physical
Direct Transcode evidence showed IJK 0.8.8 repeatedly entering an illegal
MediaCodec state without advancing playback, so the concrete-player boundary
now rejects only that combination and reports
`mimDirectSessionState=unsupported_player_stock_fixed`. The same request then
passed through ordinary `SAGETV_PUSH`, including advancing A/V and pause
recovery. No device-specific profile was added.

The same matrix exposed a shared old-player/new-player ordering race: a late
release could reset video geometry after the next load had already received
`SETVIDEORECT`. New-load generation state now invalidates stale geometry before
queued replacement, and old-player release preserves new-generation geometry.
Legacy Exo and both GSY delegates consequently reached stable full-screen video.

Against unmodified stock `.175` with `vibe_ffmpeg=false`, an explicit Direct
Transcode request reported `mimDirectSessionState=unavailable_stock_fixed` and
continued through ordinary `SAGETV_PUSH`; hardware AVC, AAC output,
pause/resume, repeated source start, settings restoration, and the crash check
all passed. Exact-path control correctly required the optional Core MCP plugin;
the documented unique-title Sagex fallback selected
`Breakfast-26711345-0`. MIMFIX-003 remains open for Windows,
legacy-client, growing-media, transition, and the remaining media/lifecycle
matrix; the Android cross-player portion is complete.

The `.232` compatibility conditions are now physically separated and proven.
With the caption service disabled, and again with a real pre-Direct plugin JAR
that exposed caption contract v1 but no `mimDirect` member, the current client
reported `unavailable_stock_fixed`, negotiated no Direct mode, and used
ordinary `SAGETV_PUSH`. Both passed advancing A/V, pause recovery, crash/exit,
and restoration of all 102 client settings; the disabled run also passed
STOP/restart. For the distinct late-start case, the current plugin continued
to advertise Direct while `ffmpeg_MIM` was temporarily made non-executable.
Session creation failed as intended, the client reported
`start_failed_pull_fallback`, and the already exposed source played through
`SAGETV_PULL` with FF/REW and pause recovery. The original INI/JAR SHA-256,
MIM executable SHA-256/mode `755`, and API availability were verified after
restoration; temporary commissioning backups were removed.

That late-start evidence also identifies the remaining fallback gap precisely.
Direct negotiation suppresses SageTV's Fixed push-format declaration so the
server supplies an original Pull path. A session-creation failure can use that
path immediately when the Android backend supports it, but it has not restored
ordinary Fixed if that source is itself unplayable. MIMFIX-003 must retain this
open row until a bounded reconnect/re-watch path renegotiates with Direct off.

Direct-transcode caption selection and seek continuity now pass on non-Pro
`.25` against `.232`. The generated MPEG-2/AC-3/CEA fixture first exposed that
Media3/legacy Exo could accept native track `0`, then immediately replace it
with an unrelated persisted DVB choice during asynchronous track publication.
Explicit active-session selections now win that lifecycle race. A Direct seek
then exposed a second boundary: the replacement backend inherited the old
applied track number even though its renderer override and subtitle view no
longer existed. Backend release now clears only applied selection state while
preserving the requested track, which is rebound when the replacement track
map appears. Timestamped rendering, same-session Off/On, continuous cues, FF
A/V recovery, post-seek caption recovery, crash-free teardown, and restoration
of all 102 settings pass. Evidence is under
`artifacts/android/mimfix003/`; the repository-wide manifest/final gate remains
pending until the remaining MIMFIX-003 work is complete.

The user-requested owned-stream DVD gate is now explicit in `TASKS.md`: menu
and submenu navigation/highlights, repeated entry/return, main-title and
chapter/seek/STOP controls, audio/subpicture selection and Off behavior,
timestamp/sync evidence, Copy versus Transcode policy/stage proof,
menu/title/end transitions, cadence, teardown, and unavailable/failed-plugin
fallback all remain required before MIMFIX-003 can close.

The final source gate passed 573 static/scaffold tests, 97 MCP tests, Core
JUnit, complete validation, and a clean 60-task Android build. The resulting
debug APK SHA-256 is
`02f025515ca398dce35dd546f17bb838c974d2cfe2691687417d12ecc4ac12b5`.

## Growing-recording stale-duration correction (2026-09-27)

The supplied BargainHunt log and screen capture proved that the spontaneous
rewind was not a SageTV seek. Media3 1.11's
`StuckPlayingNotEndingDetector` fired after playback continued beyond the
finite TS duration discovered at initial OPEN, raised `ERROR_CODE_TIMEOUT`,
and the generic recovery reprepared from 392,261 ms at an earlier 332,272 ms
sync point.

`Media3PullDataSource` now resolves ambiguous stock-server growth before the
player builder runs, using a separate one-shot MediaServer connection that is
explicitly released and cannot mutate the real session-owned Pull datasource.
Only a proven growing, non-SMB MediaServer Pull source receives the largest
positive Media3 playing-not-ending timeout; buffering, no-progress,
suppression, and all other stuck-player detectors remain at their defaults.
Probe failure safely retains default Media3 behavior. Push, Fixed/MIM, SMB,
completed files, and explicit seeks are excluded.

The focused policy test and clean APK build pass. The completed
`BargainHunt-Naseby31-26776208-0` recording then passed Media3 Pull/hardware
playback on non-Pro `.25` against stock `.175`/SageMC with hardware AVC,
advancing AC-3 audio, completed-source classification, zero player error, and
zero retry. A real stock-server Live TV recording subsequently remained
healthy beyond the former 60-second boundary with advancing A/V and no timeout
or jump-back. Both settings transactions restored all 98 preferences. The
installed debug APK SHA-256 is
`ad6ddc256534bfaeb3ad6d8b85b925a504583bdb8fabe2762aaf327841f68651`.

## Physical-test artifact cleanup (2026-09-27)

The non-Pro Fire TV `.25` shared-storage root had accumulated 282 generated
screenshots, UI XML dumps, text traces, and two screen recordings from earlier
manual physical tests. They were verified as test artifacts and removed; Vibe
settings, private diagnostics, unrelated media, Android application folders,
and the JVL client were untouched. Available `/data` space increased from
about 122 MB to 324 MB, then to 335 MB after removing the separately verified
`/data/local/tmp/dalvik-cache` generated by test tooling.

`AdbClient.screenrecord()` now stages each capture under the unique path
`/sdcard/OpenSageTV_Vibe_Test_Temp/screenrecord-<id>.mp4`. A `finally` block
removes that file after success or failure and non-recursively removes the
directory only when empty, so concurrent or unexpected files are never
deleted. Screenshots already use `adb exec-out` and create no device file.
Fifty MCP ADB tests pass, including failed-pull cleanup, and a physical
one-second `.25` capture proved the remote directory was absent afterward.

## Default Vibe test server `.232` (2026-09-27)

The ignored local `config/firetv.toml` now selects server alias `vibe`
(`192.168.10.232`) as the default for further commissioning. Its authenticated
Core MCP plugin is enabled on the LAN listener and the non-Pro Fire TV `.25`
resolves as UI context `444556303031`. Keep `.185` configured as the stock
Windows comparison target; it is no longer the default. Real credentials and
the Core MCP token remain only in the ignored local configuration.

## Stock Windows `.185` acceptance (2026-09-26)

Non-Pro Fire TV `.25` completed the Windows FFmpeg-plugin acceptance matrix
against an unchanged stock `Sage.jar`. Fixed QSV and forced server-side
software/libx264 playback passed startup, audio, pause/resume, FF/REW, skips,
Comskip, restart, clean exit, and crash checks. Separate CEA callback,
Teletext, and DVB bitmap gates passed. Authored-DVD native menus/title and real
ALADDIN native MPEG-2/AC-3 playback also passed; stock Windows Core's public
DVD `Seek(long)` accepted but did not accurately land an exact 480-second
request and is documented as a stock DVD-reader boundary.

The DVD harness now reconnects after its deliberate cold-start discard before
performing the measured retry, and waits for server-owned title/menu state to
settle before an optional exact positioning seek. Targeted DVD tests pass 79
of 79. The `.25` user profile was restored to GSYVideoPlayer/system, Dynamic
Push, hardware decoding, native DVD, automatic timestamp repair, and automatic
caption service 1 after commissioning.

## v0.5.95 release candidate checkpoint (2026-09-22)

The reusable SMB server and media-mapping editors no longer allow their form
or scroll container to take Fire TV remote focus. The server path is explicit
for anonymous and authenticated profiles, the mapping path adapts when a
server/folder becomes available, and both dialogs initially focus their first
editable field. A settings-preserving install on Pro `.29` physically reached
the name, endpoint, share, authentication, credential, folder, and action
controls. The screenshot evidence is
`artifacts/firetv/pro-smb-add-focus-fixed.png`.

The Pro skip report was traced to ordinary Push playback returning zero while
SageTV replaced its mux timestamp after FLUSH. A rapid second skip could then
be calculated from the recording start. `PushTimelineContinuity` now retains
only the last backend-proven media time during that bounded interval. It does
not apply to a new OPENURL, initial playback, Pull/SMB, or DVD Push. Unit and
static policy tests cover those exclusions. On Pro `.29` against stock `.175`,
the corrected trace produced 10 `push_media_time_held_during_flush` events for
10 sampled FLUSHes; 20 physical FF/REW key commands retained hardware video,
audio, a valid surface, zero player errors, and zero retries. The broad Pro
bundle is `artifacts/firetv/20260921-182306_pro-seek-after-fix_*`. Non-Pro
`.25` then completed the affected Media3 hardware Fixed/Push FF, REW, large
jump, and both Comskip reference session without a crash. Fixed transcoding
restarts its local stream clock, so backend-relative values from that mode are
not misrepresented as absolute program landing evidence.

A second review of the user's exact Comskip sequence found an independent
ordinary-Push offset. Stock Core's `MiniPlayer.pushBuffer0` explicitly sends
the mux time at the *end* of the bytes in the current PUSHBUFFER command, but
Media3 and legacy Exo report a position relative to the *start* of the
post-FLUSH byte epoch. Adding those directly advances SageMC's visible time by
the queued media duration, so its correct commercial-marker calculation can
request the wrong marker. `PushTimelineAnchorEstimator` now measures the
bounded first-to-last MPEG-TS PES-PTS span and converts the raw mux-end value
to the decoder epoch start. Unknown, malformed, discontinuous, non-TS, DVD
Push, Pull, and SMB paths retain their previous behavior. Core unit tests
cover fragmented input, reset/fallback boundaries, and the complete
PUSHBUFFER-to-GETMEDIATIME result. The remote key mappings were deliberately
not changed. Physical Pro acceptance remains required after installing the
new settings-preserving build. The pre-version-bump clean 60-task debug APK
build passed; its SHA-256 was
`72a4cb0470f75fbce97c458786ed95eb43a3ac7e812af3f5563f52120a7ef035`.

Version 0.5.95 also wraps every official automated MCP playback test in a
private device-local preference transaction. It restores stale checkpoints
before a run and restores all supported preference types after both success
and failure. Credentials and preference values never leave app-private
storage. GH-008 owns the final reproducible source/APK gates and publication;
SEEK-001 remains open only for the user's final visible Pro Comskip landing
acceptance. The versioned primary gate passes the 1,473-file manifest, 571
project/static tests, 91 MCP tests, Core Java tests, full project validation,
a clean 60-task APK build, and strict debug-APK inspection. The `0.5.95` debug
APK SHA-256 is
`33dda07567abb008dcd4cec2ecff412d81f29a140f541f72701347d94586a6c4`.

Commits `f203a1f` and `98ad7f6` are public, and tag `v0.5.95` resolves to the
release-preparation commit. The exact Git-less source archive independently
passed its 1,473-file manifest, 571 tests with the expected Git-metadata-only
skip, 91 MCP tests, Core Java tests, full validation, a clean 60-task build,
and strict APK inspection. All five downloaded public assets matched their
local SHA-256 values. Both repository-check runs passed; one of the duplicate
Pages runs was superseded/cancelled and the other passed. The latest-release
API returns `v0.5.95`, and the permanent Pages downloader returns HTTP 200.
The public release is
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.95`.

## v0.5.94 release checkpoint (2026-09-21)

Version 0.5.94 packages the completed post-v0.5.93 stock-server and optional
DVD-transform compatibility work. It replaces private commissioning events
230-233 with the authenticated stock-compatible Core MCP bridge and local
Media3 Surface refresh, migrates legacy Fixed Transcoding preference types,
and uses functional, provider-neutral `DVD_DISC_*`, `VIDEO_PLAYBACK_RATE`,
`dvd_mpegts_v1`, and `transformed_main_feature` contracts. Unmodified stock
Core retains its established fallback behavior; transformed DVD playback is
advertised only to a matched updated Core with an available plugin provider.

The primary source gate passes 566 project/static tests, 91 MCP tests, Core
Java tests, project validation, the complete 1,466-file manifest, a clean
60-task APK build, and strict debug-APK inspection. The versioned candidate
APK SHA-256 is
`651ffe51a4a76cad75d1820cc9c5f166ce478c2c252c1ba875c3f6161140fd4f`.
The exact source ZIP independently passed its Git-less manifest, 566 tests
with the expected Git-metadata-only skip, 91 MCP tests, Core Java tests, full
validation, a clean 60-task build, and strict APK inspection. Commit `0e70947`
and tag `v0.5.94` are public. All five downloaded release assets match their
local SHA-256 hashes, repository checks and Pages deployment pass, the GitHub
latest-release API returns `v0.5.94`, and the permanent Pages downloader
returns HTTP 200.

## Provider-neutral DVD transform negotiation (2026-09-20)

Android now advertises `native,dvd_mpegts_v1` through
`DVD_DISC_TRANSPORTS` and sends the provider-neutral
`transformed_main_feature` policy. Updated Core selects a matching optional
plugin provider; absent, unavailable, or failed providers retain or restore
native DVD. Android has no MIM executable or server-process dependency.

Saved `mim_main_feature` preferences and the former server URL marker are
accepted only for bounded migration/receive compatibility. New preferences,
wire values, diagnostics, and MCP state use transform terminology. Focused
protocol and policy tests cover negotiation, migration, and safe fallback.
The full gate passes: 566 Python tests, 91 supplemental tests, Core Java tests,
project validation, manifest verification, and a clean 60-task debug APK build.
The APK SHA-256 is
`6e0b24ce092756ef543aad3f92c149d0c6e8a8ffc71077e0e006f689594ada4d`.

## Functional MiniClient capability names (2026-09-20)

The optional DVD and playback-rate negotiation no longer exposes Vibe-branded
wire properties. Updated Core queries `DVD_DISC_TRANSPORTS`,
`DVD_DISC_POLICY`, `DVD_DISC_SKIP_MENUS`, `DVD_DISC_SKIP_PREVIEWS`,
`DVD_DISC_NATIVE_FALLBACK`, and `VIDEO_PLAYBACK_RATE`; Android answers only
those functional names. There are deliberately no `VIBE_*` aliases, so these
optional capabilities require a matched updated Core/client pair. Unmodified
stock Core continues through the existing safe fallback paths.

Unknown capability GET requests return an empty unsupported value. Unknown
SET requests are acknowledged and ignored without throwing or closing the
connection. Ninety-seven focused protocol tests and the Android Core Java
suite pass. The clean 60-task APK has SHA-256
`ad37ccee5abd02a027641b5302edc4c4f2951ffd0ecda6bef01affe446e6c4e0`.
It was installed with `-r` and launched successfully on non-Pro `.25` and Pro
`.29`; both retained their original install dates and reported
`0.5.93-DEV-DEBUG`, with no new fatal exception in the bounded post-launch
log check.

## Stock Core MCP bridge integration (2026-09-20)

The commissioning MCP now prefers the sibling stock-compatible Core MCP bridge
when a server explicitly configures `core_mcp_enabled`, its base URL, and its
local bearer token. The adapter uses the bridge for exact paths, watch, seek,
UI commands, channel tune, caption state, library scans, diagnostics, and
watched-state clearing while retaining only operations that Sagex/Web can
actually verify when the bridge is absent. Android emitters and debug receiver
routes for private commissioning events 230-232 are removed; exact-path
requests now fail clearly if the bridge is unavailable instead of treating an
unknown event as accepted.

On non-Pro Fire TV `.25` against unmodified stock `.175`, Media3 hardware Pull
passed exact fixture start, from-beginning, advancing 1080i MPEG-2 and AC-3,
fullscreen, forward/back recovery, pause/resume, and an empty crash-log gate.
Live TV and bridge-driven exact channel `2.1` also passed. The `.175`
`Sage.jar` SHA-256 remained
`d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.

The stock plugin now resolves DVD parent roots as aliases of indexed
`VIDEO_TS` media. Public `Seek(long)` physically repositioned ALADDIN DVD Push
from 621,386 ms to 240,000 ms immediately and continued normally; additional
stable forward/backward targets passed. MCP automation therefore no longer
uses private event 233. The normal DVD display-mode recovery path is now local:
Media3 keeps the same player, Push datasource, logical clock, audio selection,
and play/pause intent while it rebinds the Android video Surface. It neither
seeks nor asks the server to recreate the stream. Private events 230-233 are
therefore removed from the Android protocol and the matching Vibe Core
handlers; stock Core is the primary gate with unified graphics disabled.

## Fixed-transcoding settings crash closure (2026-09-20)

Opening **Fixed Transcoding Settings** previously force-finished the app on
non-Pro Fire TV `.25`. The captured fatal exception was an AndroidX
`ListPreference` inflation `ClassCastException`: existing video and audio
bitrate values were stored as Android `Integer` values while the migrated
preference UI requires `String` values.

`FixedTranscodingFragment` now normalizes all list-backed fixed-transcoding
keys before XML inflation. The two numeric bitrate keys retain their exact
values, invalid legacy types on other list keys fall back only that key to its
documented default, and unrelated preferences are untouched. `AndroidPrefStore`
reads both numeric and string integer representations, while debug/MCP writes
the canonical string form going forward.

The settings-preserving APK SHA-256 is
`9698e61ea450ecf27f948d14e27bd3683d289e2eb9f2a2f50347c38b919e3f1f`.
On `.25`, the real retained `<int>` values `4000` and `128` migrated in place
to strings, the activity and bitrate chooser rendered, focus stayed on the
activity, and logcat contained no fatal exception. No preference reset or app
data clear was used.

## Historical DVD MIM plugin integration checkpoint (2026-09-20)

The first explicit `mim_main_feature` gate passed on non-Pro Fire TV `.25`
against isolated Vibe server `.232`. That Core-owned transform has since been
replaced by the provider-neutral contract above; the physical evidence remains
valid for the MIM media path. MIM reported VAAPI `h264_vaapi`; Android reported
`video/avc` through `OMX.MTK.VIDEO.DECODER.AVC`, 92,659,936 pushed bytes,
1.002x cadence, 675 video outputs, 704 audio outputs, zero dropped video
frames, and no runtime/native fallback. Pause/play, FF, REW, chapter-up, STOP,
and teardown all recovered.

Current debug state reports `discTransformedTransport`; Playback Stats and
`mcp_disc_test.py` apply the correct transport-specific
gate: hardware AVC/no runtime fallback for MIM versus MPEG-2 sequence and
interlace evidence for Native. Final evidence is
`artifacts/firetv/ffmpeg-plugin-dvd-mim-main-feature-20260920.json`; independent
generated-content HDMI evidence is
`artifacts/firetv/ffmpeg-plugin-dvd-mim-hdmi-20260920.mp4`.

Stock `.175` was not changed. Ordinary Fixed/MIM plugin playback remains
compatible with stock SageTV; transformed DVD playback needs updated Core's
optional provider SPI because stock Core has no discovery hook.

## v0.5.93 release checkpoint (2026-09-19)

The accumulated stock-server playback, captions, unified graphics, growing-
stream recovery, audio synchronization, DVD Push buffering, and consolidated
playback-control work is prepared as `0.5.93`. The primary tree passes 564
project/static tests, 87 MCP/workflow tests, Core Java tests, the complete
1,463-file source manifest, full project validation, a clean 60-task APK build,
and strict debug-APK inspection. The `0.5.93-DEV-DEBUG` APK SHA-256 is
`c85be3e468e141b20e6b83cb6421e0b9757697429a654e1c29b157c016df1d39`.

GH-006 is complete. A fresh Git-less source extraction passed its 1,463-file
manifest, 564 project/static tests with the expected Git-metadata-only skip,
87 MCP/workflow tests, Core Java tests, full validation, and a clean 60-task
Android build. Commits `3cf637d` and `6c4b890` were pushed to `main`; the public
[`v0.5.93` release](https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.93)
contains the APK, source ZIP, combined review bundle, release manifest, and
checksums. Its release page uses bullet points grouped under Changes, Fixes,
Compatibility, Validation, and Known limitations. All five downloaded public
assets match their local SHA-256 hashes, GitHub repository and Pages checks
passed, the latest-release API returns `v0.5.93`, and the permanent Pages
downloader returns HTTP 200.

## Playback-control consolidation checkpoint (2026-09-19)

The long-press playback row now keeps Aspect ratio and places dedicated Video,
Audio, and Subtitles/CC controls beside it. The obsolete standalone player
switch and Smart Remote four-arrow shortcut are removed. Video settings is a
compact two-column panel with direct Player, Decoding, Codec Queueing, Source
buffering, Display, DVD playback, decoder restart, and reset rows; it no longer
duplicates audio, caption, Playback Stats, or Test Current Video controls.
Player and setting values are left-aligned, labels remain single-line without
ellipsis, and the video panel has an independent wider bound for long player
names. GSY choices identify and control their delegate explicitly: Auto,
Media3, Android System, or Legacy ExoPlayer.

The triangle retains its direct full-screen Video Information behavior. Its
left pane now uses compact SageTV label/value rows and its right pane contains
Vibe diagnostics, refresh, and export. The Fire TV crash was caused by casting
the dialog's themed context to `Activity`; the dialog now retains its actual
constructor Activity. The focused player/DVD/caption contracts, complete
project/static tests, MCP tests, Core Java tests, and debug APK build are the
required publication gates for this checkpoint; physical menu navigation was
previously exercised on non-Pro `.25`, while the final wider-layout revision
is covered by source contracts rather than a new HDMI capture.

## Fire TV Pro native-DVD checkpoint (2026-09-19)

The ALADDIN exit was reproduced with unified HD graphics disabled and the
legacy `SEPARATE` connection path, so Unified is ruled out as its cause. The
failure combined sustained DVD Push starvation, a ZLIB graphics read failure,
a transient stock-server type-5 reconnect rejection, and a secondary
track-diagnostics callback racing player teardown.

Media3 DVD Push now builds a title-playback reserve (`5,000 ms` at startup and
after rebuffer, bounded by a `12,000 ms` maximum) instead of repeatedly
resuming near an empty decoder queue. A dedicated
load-control wrapper bypasses that reserve as soon as the active DVD reader
generation ends or a reprepare is pending, so tiny authored navigation cells
can still drain. GFX type-5 connection establishment receives bounded retries,
and Media3/legacy-Exo track diagnostics are change-driven and validate their
captured player/session before touching selectors.

The settings-preserving APK
`46730ed701f1c6b6ceb794f84d3b6140df2b1a44d289ff20743bee665ed825f9`
passed Pro `.29` against stock `.175`: ALADDIN sought to `479846 ms`, advanced
`56123 ms` over `60145 ms` (`0.933x`), rendered 1983 video and 1738 audio
outputs with zero drops, and retained its process. Applying a live encoded
`+500 ms` passthrough offset during ALADDIN also retained active playback and
zero drops. The authored test DVD physically rendered its root menu and the
Languages submenu across its short-cell transition. DVD-001 remains open for
an exact forced type-5 GFX recovery, Stop/teardown, and Unified On comparison.

The remaining opening stop/start loop was separately reproduced as real
Media3 READY/BUFFERING oscillation while stock DVD Push arrived close to the
authored average bitrate. With the five-second reserve installed, a new
ALADDIN observation exceeded 60 seconds with no post-start READY/BUFFERING
transition or AudioTrack underrun and approximately `5.7..13.5 s` of decoded
media ahead.

## Live decoded-audio output checkpoint (2026-09-19)

Audio settings now also contains an embedded **A/V sync test**. It loops the
generated `vibe_av_sync_ball.ts` asset through a standalone Media3 path using
the active session's decoded/passthrough choice. Ball impact and the 48 kHz
AC-3 click share the same authored one-second boundary. Left/Right
changes the calibration by 25 ms, Center resets to zero, and Back applies the
value to the active session before returning to Audio settings. The underlying
program continues muted during the test and its prior mute state is restored.
This is a local display/audio/HDMI/receiver calibration; server transport and
lifecycle remain separate physical gates.

The calibration retains its original full-height bounce. All explanatory text
is confined to unused left/right side columns, and the compact control panel
occupies the empty lower-right side column instead of crossing the impact
path. The fixture no longer flashes its outer border and includes a quiet
continuous 220 Hz reference tone so an HDMI/receiver path does not sleep
between the dominant one-second clicks.

The first implementation changed the displayed value and controller but could
reuse samples already extracted into the fully buffered local loop. Each
settled adjustment now recreates the embedded source at the same position so
new samples are extracted with the requested timestamps. Both decoded and
encoded calibration routes use that byte-preserving timestamp controller.
Pro `.29` on-device evidence reported `+1.125 s` as 1,742 shifted audio
samples, `-1.125 s` as 3,555 delayed video samples, and zero after Center; Back
returned to the active MiniClient without a fatal exception. The screenshot
also confirms the center ball path is unobstructed. The USB HDMI capture was
not connected to the Pro during this check, so audible TV/receiver/ARC
synchronization remains open and no quarantined capture is evidence.

The installed settings-preserving debug APK SHA-256 is
`c2554aab36f994b6e05b683b203009287cc4aa019bd9eb085cd8e5b293d2f621`.
The complete validation gate passes 562 client tests, 87 MCP tests, and the
core Java build.

The long-press playback panel now has a dedicated speaker icon that opens
**Audio settings**. Media3 and legacy ExoPlayer install a PCM-only
AudioSink when decoded mode is selected, downmix multichannel/mono PCM to
stereo, and apply a signed sample-domain `-4000..+4000 ms` offset without
changing SageTV's media clock. A live decoded/passthrough change performs one
same-position rebuild while retaining the active Pull or Push datasource and
selected audio track. GSY Media3/legacy delegates forward the same contract;
IJK reports its fixed decoded output and rejects an impossible request to
enable passthrough. Encoded mode now exposes a separate default-off
**Passthrough offset** switch for Media3 and legacy ExoPlayer. It shifts
presentation timestamps at the extractor boundary while delegating encoded
sample bytes unchanged: positive values delay audio and negative values delay
video. GSY inherits the selected delegate; IJK remains unavailable. Offset and
toggle changes update that active atomic controller in place and perform one
debounced same-position seek to flush old queued timestamps. They do not tear
down an encoded AudioTrack/player. Output-mode changes alone retain the
required same-position rebuild. Playback Stats and debug/MCP state
identify the requested and applied path plus shifted-sample counters.

The original offset implementation rebuilt the complete player on every
settled slider adjustment. On Pro `.29`, Fire OS recorded repeated 5-second
input-dispatch ANRs in `MiniClientOpenGLActivity` while encoded AudioTrack
teardown blocked the UI thread, then killed the process. There was no Java
`FATAL EXCEPTION`. The in-place controller plus lightweight re-anchor removes
that offset-only teardown path. The rebuilt APK then passed the same physical
Pro path on `.29` against stock `.175`: rapid automated changes followed by
the real on-screen slider (`8x` right, `8x` left, Back/Save) retained the same
process, kept playback active, and produced no new ANR or fatal exception.
Cleanup returned the session to decoded PCM, passthrough offset off, and
`0 ms`.

The main panel follows Kodi's concise two-column organization while retaining
Vibe's colors. Its live offset editor is a compact top slider: Left/Right moves
in `25 ms` increments, Back keeps the current-playback value and returns to the
audio parent, and **Set as default for all media** is the explicit persistent
action. Physical non-Pro validation at 1080p confirmed live `0.025 s` steps,
zero reset, focus restoration, and Back navigation.

The AUDIO-006 implementation, automated gates, and the focused Pro ANR
regression pass, but the task is not closed: its saved default remains off
until direct Media3, legacy Exo, and
their GSY delegates pass both signs, zero reset, seek, pause/resume,
track/format change, live transition, HOME/return, and teardown on a real
encoded TV/receiver/ARC route.

A focused client-path smoke also passed on non-Pro `.25` against stock `.175`
using indexed `Scream_1`: direct Media3 Pull and legacy Exo Pull each applied
`+25 ms` to audio timestamps and `-25 ms` to video timestamps, then reset to
zero. The exported shifted-sample counters identified the intended side on
both backends, playback remained active with no player error, and the session
was returned to decoded PCM with the passthrough-offset switch off. This does
not claim audible synchronization or close the real receiver/ARC lifecycle
gate.

The debug-only MCP tool `dev_set_active_audio` now changes output mode,
passthrough-offset enablement, and signed offset independently. MCP discovery
reports the tool, and the rebuilt APK accepted a live Media3 `+25 ms` request,
reported advancing shifted-audio counters, then accepted zero/off/decoded
cleanup while playback remained healthy. This is the preferred automation
surface for the remaining physical matrix; it avoids menu-coordinate input.

Physical stock-server validation passed on `.175`: Pro `.29` Media3 Pull
changed decoded -> encoded -> decoded during active playback and telemetry
changed between FFmpeg software AC-3 decode and hardware passthrough; a live
`+250 ms` offset and reset to `0 ms` did not interrupt playback. Non-Pro `.25`
passed Media3, legacy Exo, IJK, and GSY Auto startup, and legacy Exo passed the
same live output transition. The original TV/surround-path synchronized
measurement and the complete generated A/V-pulse lifecycle matrix remain open
under AUDIO-001/AUDIO-005; no model-wide automatic offset was added.

Focused Python contracts (45), the complete Python/core/Android unit gates, and
APK assembly pass.
The final debug APK was installed in place on `.25` and `.29`, preserving
settings. SHA-256:
`aa5231c52d9178fdb655c71d8c63109933f31cc759078c0be639bd0df0f53900`.

## Explicit-only DVB control checkpoint (2026-09-19)

DVB is no longer mapped through virtual CC1 or CC2. Those two profiles resolve
only CEA-608/708 and Teletext, including in Auto mode. The read-only stream
inventory still reports DVB but labels it `select DVB`; choosing the single
top-level `DVB` mode directly activates the local bitmap track. This matches
the protocol boundary: SageTV CC1/CC2 are callback caption channels, while the
original extender handled DVB through a separate subtitle/PID command.

The short-lived persisted `CC1/CC2 Type = DVB` value normalizes to Auto in the
UI, runtime resolver, and debug diagnostics. Debug configuration also rejects
new slot-DVB assignments and directs automation to
`legacy_server_caption_mode=dvb`.

Core policy tests, 92 focused Python tests, the full source contract, and a
clean APK build pass. APK SHA-256
`e5551f9b1af0dd178aae86b12f05702b2ef1352646d95f1e0b7d6eb26db31944`
was installed in place on non-Pro `.25`. Against stock `.175`, top-level DVB
selected track 2, rendered DVB bitmap cues, left the Teletext overlay inactive,
and preserved hardware H.264 video plus AC-3 audio.

## Single caption-renderer checkpoint (2026-09-19)

Stock SageTV can keep rendering the last event-225 Teletext channel after the
Android menu changes to a locally rendered DVB service. That produced two
captions: the stale SageTV/Teletext caption plus Vibe's DVB bitmap. Explicit
local `CC1`, `CC2`, and `DVB` modes now take exclusive renderer ownership when
the server has not supplied `VIDEO_CC_STATE`. Vibe clears pending Teletext,
disables both callback mappings, sends one legacy CEA screen reset, and then
selects the Android Teletext or DVB track. `STV` mode retains the original
callback path.

The active-caption inventory is also truthful for UK recordings: its heading
does not advertise CEA, synthetic CEA tracks remain hidden until actual CEA
samples are observed, and a stock STV DVB command is labeled `STV Subtitles`
rather than `CC1` or `CC2`.

Focused caption-authority tests and the full source contract check pass. APK
SHA-256 `b8dc2dd5a16e900dba06086b9b5e221c6fe8ebe83323bcadda1b8b12241249f4`
was installed in place on non-Pro `.25`. Against stock `.175`, Media3 Pull with
hardware video selected DVB track 2, decoded AC-3 audio through FFmpeg, and
rendered one bitmap-caption surface with 50 cue updates (47 non-empty, 94
bitmap cues). Visual evidence is
`artifacts/firetv/20260919-152730_caption-media3-pull-visible.png`.

## Broadcast CC versus subtitle selection checkpoint (2026-09-19)

SageTV exposes two related but different concepts. `VIDEO_CC_STATE` and the
STV `Off/CC1/CC2` choice are broadcast closed-caption authority. Vibe maps
those virtual slots only to CEA-608/708, DVB Teletext, or DVB bitmap services.
The normal subtitle-language preference is for SRT/PGS/DVD subtitle playback;
it is not a CC language fallback and does not enable broadcast captions.

The active-player menu now says **Broadcast captions (CC)** and lists only
broadcast services, with a read-only note that **SRT/DVD subtitles are
separate**. This avoids the old situation where a CC request fell through to
the generic subtitle resolver and appeared to work only after Subtitles was
selected. Auto CC language remains English-first unless the per-slot CC1/CC2
language is explicitly changed. The fix is stock-server safe and applies to
Media3, legacy ExoPlayer, IJK, and GSY through the shared caption policy.

Validation completed on APK SHA-256
`c51ce222718d4f44694c05db3ac81c5416c8d3c1666065e32ff4a3445533b6a5`:
full Python/MCP/Core/static validation passed, the APK was installed in place
on non-Pro `.25` with settings preserved, and stock `.175` Media3 Pull hardware
playback passed the Breakfast stream inventory, continuous Teletext clock
check, and SageTV Off/CC1/CC2/Off/CC1 event-225 cycle. The stream exposed
CEA608/CEA708 compatibility entries, DVB, and Teletext page 888; Auto selected
the real Teletext service rather than an empty synthetic CEA entry.

## Unified graphics capability checkpoint (2026-09-16)

`Settings > Playback Settings > Use unified HD media-player graphics
(experimental)` is an opt-in, persisted A/B switch for the stock SageTV
`GFX_YUV_IMAGE_CACHE=UNIFIED` capability. Disabled returns the prior empty
property response. Enabled negotiates the HD200/HD300 classification used by
stock Core (DVB tracks retained and MPEG-TS Push chunks aligned to packet
boundaries), and the GDX/OpenGL renderers decode format-256 Y/UV image lines
into ordinary RGBA textures. Android's MediaCodec video remains a separate
`SurfaceView`; any HD300 video-plane handle is logged and safely uses the
normal rectangle path rather than being misapplied to a decoder surface.

The debug APK exposes `dev_set_unified_graphics(enabled)` and the existing
`dev_set_player_config(unified_graphics_surfaces=...)`; both report the saved
state and `unifiedGraphicsAppliesNextConnection=true`. This was added only
after comparing `MiniClientSageRenderer`, `MiniPlayer`, `MiniDVDPlayer`,
`Global.GetRemoteUIType`, and the archived HD300 command path. It does not
select a player, decoder, buffering, seek, DVD transport, remux, or
transcoding mode. The format-256 GDX path was also corrected to upload every
subsequent Y/UV row into the existing texture rather than retaining only the
first frame.

The physical non-Pro A/B gate passed on 2026-09-16: APK SHA-256
`0f827b7b3955594e67fef4a763b5644c8fca5a555bfc81b63262b7bfe2769489`, Fire TV
`.25` (AFTMM/API 25), stock server `.175`, Media3 Pull, hardware video, and
the `Meet the Press` recording. Both OFF and ON sessions passed ordinary
playback/audio, fullscreen, seek, pause/resume, STOP/teardown, reconnect, and
error-state checks; no decoder was recreated during seeking. MCP/debug setting
control and persisted-state reporting passed as well. The ordinary title does
not exercise Core's format-256 command, so that decoder is additionally
covered by unit tests. An HD300 video-plane handle remains a deliberately safe
logged fallback to Android's normal `SurfaceView` rectangle because it cannot
be composed directly into a MediaCodec surface.

## Explicit DVB caption-mode checkpoint (2026-09-16)

The long-press caption selector now exposes five top-level modes:
`OFF`, `CC1`, `CC2`, `STV`, and `DVB`. `DVB` is an explicit Android-local
bitmap-subtitle mode. It selects the first discovered DVB track, remains active
even if a server later sends `VIDEO_CC_STATE`, and prevents generic preferred
track resolution or the legacy Teletext bridge from replacing it. If no DVB
track exists, the client disables captions and reports that fact while retaining
the selected mode for late track discovery. `STV` remains the separate
server/STV authority, and CC1/CC2 remain virtual compatibility slots.

The previous implicit fallback that could choose Teletext when an Auto slot
resolved to DVB was removed. This keeps the UI and diagnostics truthful: DVB
bitmap captions are shown as DVB, and only an actual Teletext track is emitted
through the legacy CEA callback. The Android shared tests, core compilation,
debug APK build, and non-Pro in-place installation pass.

Follow-up fix (2026-09-16): `MediaCmd.getLegacyServerCaptionMode()` now
preserves the stored `dvb` value instead of normalizing it back to `stv`.
The caption selector also limits the SageTV-authority message to explicit
`STV` selection. This was the cause of the menu appearing to revert after
choosing DVB. The rebuilt debug APK was installed in-place on non-Pro Fire TV
`.25` without clearing application data.

Playback-start follow-up (2026-09-16): Media3 and legacy Exo now reapply the
persisted caption slot from `onTracksChanged`. This covers the case where DVB
tracks are not available during the initial preference pass; the saved DVB
mode is automatically applied once the player exposes the track.

## Growing Pull position-recovery checkpoint (2026-09-15)

The supplied `TheChase-26742651-0.ts` report was traced as a growing H.264
MPEG-TS playback path: Media3 Pull, hardware H.264 video, AC-3 audio, and DVB
subtitle signaling. The original client log ended when the user stopped the
session, so it did not contain the spontaneous rewind itself. A narrowly scoped
`GrowingPlaybackPositionGuard` now detects only an unexplained backward jump
greater than two seconds in a server-proven growing Media3 Pull source. It
preserves the last stable position and re-prepares through the existing growing
Pull seek path. Explicit seeks, completed files, Push, Fixed/MIM, SMB Direct,
and DVD paths are excluded.

The guard has focused unit coverage and the Android shared Gradle test passes.
The debug APK was rebuilt and installed in place on non-Pro Fire TV `.25`
without clearing settings. After a stock-server library scan, `.175` indexed
the exact MediaFile `TheChase-26742651-0` (ID `65552026`). Direct SageX playback
then passed with hardware H.264 and AC-3, and an 18-sample/90-second idle
observation advanced monotonically from 178,129 ms to 286,274 ms with no
backward reset, error, or loss of playback. This is a focused regression pass,
not a claim that the reporter's 10--20 minute event has been reproduced; keep
the longer affected-device evidence task open until an affected session or
longer observation is available.

## Stream-aware caption-slot checkpoint (2026-09-14)

TTX-003 is complete and replaces the crowded caption chooser with two persistent virtual
SageTV slots. The five editable rows are Captions (OFF/CC1/CC2/STV), CC1 Type,
CC1 Language, CC2 Type, and CC2 Language. Type choices are Auto, Teletext,
CEA-608, and CEA-708, filtered to services discovered in the active stream;
language choices likewise come from the matching active services. Above those
controls, the dialog shows a read-only current-video inventory and the actual
underlying service resolved for each slot. Refresh updates it after late track
discovery. DVB originally appeared here but was removed by CC-007; it now uses
the single top-level DVB mode.

CC1/CC2 are profiles rather than literal CEA-608 channel numbers. Fully Auto
CC1 and CC2 resolve to distinct best services when two exist. Explicit type or
language is authoritative and a missing combination reports `No matching
service`. CEA-608 language is always shown as Unknown unless a later reliable
source is implemented; the client does not treat extractor compatibility
metadata as broadcaster signaling. STV mode continues to follow server/STV
state and does not silently apply either explicit profile. Stock-server
fallback profiles are reapplied after asynchronous player track discovery.

Physical stock-server validation passed on non-Pro `.25`. Breakfast retained
hardware Media3 Pull and continuous event-225 delivery through the full stock
STV sequence Off, CC1, CC2, Off, CC1. The CC2 capture
`artifacts/firetv/20260915-035431_caption-media3-pull-stv-3-cc2.png` visibly
contains the decoded page-888 text; the following Off capture
`artifacts/firetv/20260915-035438_caption-media3-pull-stv-4-off.png` contains no
caption. The first cycling attempt hit a transient stock web/Sagex timeout only;
the already-playing client continued decoding and sending captions, and the
immediate retry completed every state.

### Caption-scoped original-extender audit

The archived official HD300 `stp300.bin` was inspected only for the active
caption question; the broader firmware audit remains deferred as EXT-004. Its
unstripped MIPS MiniClient contains `dvbsubdecoder.c` symbols and calls the
Sigma subtitle-surface APIs from `UpdateDVBSubpicture`. It does not call
`SendSubpictureUpdate` there. Binary call sites instead show event 225 being
produced by `ProcessCC` and caption flush/push paths. This establishes two
separate original-extender contracts:

- CEA-608/708 packets travel client-to-server through event 225 for SageTV/STV
  decoding and rendering.
- DVB bitmap subtitles are decoded and rendered locally; the server selects a
  PID with media command 36/type 1 and uses bit `0x2000` to disable it.

Vibe now preserves the source PID in Media3/legacy-Exo subtitle tracks and
implements that non-DVD command-36 selector, including arrival before
asynchronous track discovery. DVD sessions retain their existing authored-SPU
path. Taskmaster on stock `.175` / non-Pro `.25` separately passed local DVB
bitmap rendering with hardware H.264 and AC-3 audio.

Stock SageTV exposes the DVB subpicture list and sends command 36 only to a
client classified as an HD300 standalone media player. That classification is
tied to `GFX_YUV_IMAGE_CACHE=UNIFIED`, which also enables unrelated high-
resolution YUV/JPEG surface behavior Android does not implement. Vibe therefore
does not claim that false graphics capability merely to unlock the server menu.
On such a stock server, the long-press client selector remains the safe DVB
bitmap control. There is no reverse GFX channel or bitmap payload accepted by
event 225, so a client cannot upload DVB pixels for the stock STV to render.

## DVB Teletext rendering checkpoint (2026-09-14)

TTX-002 is complete. Vibe now has an independently implemented DVB Teletext
Level-1 subtitle-page decoder on top of the bounded TTX-001 transport parser.
It discovers type-2/type-5 services, decodes Hamming-protected magazine,
packet, page, and row identity plus the required Latin G0/control subset,
schedules updates and clears from PES PTS, and exposes page 888 as a real
`TELETEXT` track. No GPL reference-player source, libzvbi, server JAR, MIM, or
server FFmpeg change is used.

For an unmodified SageTV server, up to two Teletext language/page services map
to the existing STV CC1/CC2 choices by emitting the proven legacy-extender
event-225 CEA records. A selected Teletext track can also use the shared local
Android overlay. The long-press CC icon now opens the full selector directly:
Follow SageTV STV, Off, mapped CEA/Teletext CC1/CC2, explicit DVB bitmap mode,
and CC1/CC2 language/page assignment. DVB bitmap and Teletext remain explicitly
different track types and cannot occupy the same virtual CC slot.

The initial physical implementation exposed a real continuity defect. Cue
delivery was drained only when SageTV sent `GETMEDIATIME`; stock STVs can stop
polling it after the timeline/OSD hides, so captions appeared to freeze and
then catch up after later UI activity. A lifecycle-bound 100 ms task now reads
the active player's own clock and drains due Teletext independently. The
bridge serializes that task with any simultaneous server-clock drain so later
event-225 batches cannot overtake earlier ones. It stops while paused, resumes
with playback, and resets on seek, flush, source replacement, EOS, and teardown.

Physical stock `.175` / non-Pro `.25` evidence passed with hardware Media3
Pull. Breakfast's decoder produced more than 1,100 timed page updates; during
an idle-OSD sample, the independent drain counter advanced from 1,888 to 2,246
and wire events from 518 to 641. The FFmpeg-only 30-second HDMI capture
`artifacts/firetv/ttx-flow-clock-fixed-30s.mp4` shows different, correctly
advancing caption text in all six five-second samples. Pause held media time,
clock drains, and wire events steady; Play resumed all three; a large seek
resumed page text at the sought program scene. Classic Holby separately
exposed English page 888 and reached 94 event-225 updates with visible text in
`artifacts/firetv/20260914-191236_ttx-classic-holby-visible.png`. The caption
selector evidence is
`artifacts/firetv/20260914-190855_ttx-caption-selector.png`.

## Teletext PES preservation checkpoint (2026-09-14)

TTX-001 is complete and adds a bounded pre-decoder Teletext gate to Test Current Video. A
dormant Core probe is activated only for the user-confirmed diagnostic, taps
the existing Pull, Push, and SMB Direct byte paths, analyzes no more than
64 MiB, and records only stream metadata and counters. It identifies MPEG-TS
packet size, PAT/PMT Teletext descriptors, subtitle services/pages, Teletext
PES/data units, PTS progression, continuity errors, source discontinuities,
and the transport that actually supplied bytes. It never retains media
payload or decoded subtitle text. Core synthetic positive/negative/dormancy
tests, Android MIME-alias tests, and the full project test/build gates pass.

Physical stock-server `.175` / non-Pro `.25` validation passed. Breakfast
preserved PID `0x157f`, English subtitle page 888, 446 timestamped PES packets,
and 1,338 data units. Classic Holby preserved PID `0x0947`, English subtitle
page 888, 76 timestamped PES packets, and 98 data units. Both had zero PTS
regressions and zero continuity errors. Taskmaster played through the stock
MediaServer exact path and correctly returned `not-detected` as the
DVB-bitmap-only negative control.

The first Breakfast attempt exposed a separate stock-server compatibility
bug: Media3's FFmpeg extension recognizes the case-sensitive alias
`audio/mpeg-L2`, while Vibe's SageTV codec model supplied
`audio/mpeg-l2`. Media3 was demonstrably able to decode the resulting MP2
stream, but the false capability reply caused stock SageTV to reject Pull and
fall back to a 352x240 MPEG-2 transcode. The client now canonicalizes only the
Media3 MPEG-L1/L2 aliases before capability negotiation. The retry used the
original 1920x1080 H.264 TS over `SAGETV_PULL`, hardware AVC video, and the
bundled Media3 FFmpeg audio decoder.

## v0.5.92 SMB release checkpoint (2026-09-13)

The completed SMB-001 implementation replaces normal raw URL and repeated
credential entry with reusable device-local SMB server/share profiles. Media
mapping, configuration-profile, and diagnostic destinations each use a saved
server chooser and read-only remote folder browser. The media editor keeps the
typed SageTV path, selected server, selected folder, Test, and Apply controls
together; editing resolves its own mapping target rather than inheriting the
last mapping. Folder rows have real folder icons and a persistent `..` entry;
at share root, `..` returns to the separate server chooser.

Server endpoints default to SMB port 445 and accept an explicit `host:port`.
Anonymous and credential authentication remain per profile; username,
password, and domain controls are enabled only when authentication is selected.
The UI materializes the established internal media, configuration, and
diagnostic settings so existing SMB consumers retain their behavior. Normal
guarded APK installation now uses an in-place update and preserves commissioned
servers, client identity, player preferences, and SMB configuration;
`install-clean` is the explicit destructive reset.

Physical acceptance passed on the non-Pro Fire TV `.25` against the stock
`.175` `sagemedia` share: server selection, root/nested browsing, parent
navigation, selected-folder Test, Apply, mapping reopen, and settings-preserving
APK update all passed. The final primary gate passed 543 project/static tests,
85 MCP/workflow tests, Core and Android JUnit, the full validator, a clean
60-task Android build, strict APK inspection, and the complete 1,431-file
manifest. The `0.5.92-DEV-DEBUG` APK SHA-256 is
`dbceca2d0fd03e9f5eb5de81f55c4a1fd65144eb8c7eb5ad2fa28f2cb8872ba5`.
GH-005 is complete. The fresh Git-less source extraction passed its 1,431-file
manifest, 543 project/static tests with the expected Git-metadata-only skip,
85 MCP/workflow tests, Core JUnit, full validation, and a clean 60-task Android
build. Commits `d7dda33` and `8a3def2` were pushed to `main`; the public
[`v0.5.92` release](https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.92)
contains the APK, source ZIP, combined review bundle, release manifest, and
checksums with grouped bullet-point notes. The downloaded public APK matches
SHA-256 `dbceca2d0fd03e9f5eb5de81f55c4a1fd65144eb8c7eb5ad2fa28f2cb8872ba5`.
GitHub source contracts passed, the latest-release API returns `v0.5.92`, and
the permanent Pages downloader returns HTTP 200 and queries that latest API.
Phase #5 remains stopped by user direction.

## Release-candidate checkpoint (2026-09-13)

ONN `MATRIX-002` is complete against stock SageTV `.175`. Applicable Media3,
legacy ExoPlayer, IJK, and GSY paths passed hardware MPEG-2/AC-3 startup,
audio, seek, pause/resume, STOP, and recovery. The authored DVD, physical
Scooby-Doo menus, and exact 8:00 Aladdin main-title checks passed native menu,
title, audio, subtitle, chapter, cadence, compatibility-fallback, and clean
teardown gates. Cross-device `MATRIX-003` remains intentionally deferred until
the GitHub source/APK release is published.

The release reporting flow is also complete in the working tree. The
long-press menu has separate Test Current Video and diagnostic-export icons;
the latter supports independent authenticated/anonymous diagnostic SMB with
Off, On request, and Always behavior. A physical ONN connection test passed
URL, DNS/connect, authentication, share, write/read/hash, cleanup, and delete
in 391 ms. Public issue instructions use tightly cropped screenshots captured
while the generated Vibe test fixture was loaded, so no broadcast or movie
frame is included.

Known-file automation now tries a supplied server path first, then indexed
MediaFile Watch, and uses the STV Search screen only as a final compatibility
fallback. `--direct-only` makes controlled fixture selection fail closed. Raw
`dev.sh adb` is scoped to the configured device unless an explicit ADB target
is supplied; this fixes the unintentional multi-device authorization prompt
found during release screenshot capture.

GH-001 is complete for v0.5.91. The consolidated changelog, handoff,
compatibility/diagnostic docs, GitHub issue form/screenshots, release metadata,
and complete project manifest are synchronized. The project test gate passes
536 scaffold/static tests, 84 MCP tests, Core JUnit, and its Gradle test build.
GH-002 is also complete. The primary tree passed the full validator, clean
60-task APK build, strict APK inspection, and handoff/GitHub bundle creation.
A fresh Windows-extracted, Git-less source checkout passed its complete
1,423-file manifest, 537 scaffold/static tests (one Git-metadata-only skip),
84 MCP tests, Core JUnit, full validation, and another clean 60-task APK build.
That gate found and fixed inherited parent-Git enumeration in extracted
manifest checks. Resume at GH-003: logical commits, clean final bundle, push,
tag/release publication, public hash verification, and Pages redirect.

## Stable master-checklist rule (2026-09-12)

At the user's request, `TASKS.md` is now a stable, revisioned master checklist
rather than an active-only backlog. Every item has a permanent ID; completion
changes its existing box to `[x]`; additions, removals, and reorders are
recorded in the checklist ledger. `AGENTS.md` enforces this rule. Future status
reports must reproduce the canonical checklist instead of reconstructing a
different list from conversation history. Release-relevant evidence continues
to be recorded here and in `CHANGELOG.md`.

Decoder-stability tasks D1 and D2 are checked complete. The new classifier
distinguishes datasource, container/parser, decoder initialization, fatal
runtime decoder, audio-output, and teardown/transition failures and bounds the
allowed recovery/presentation behavior. Media3 and legacy Exo now enable
fallback only after Vibe's Hardware/Software/Fallback policy has filtered the
candidate set. Focused Core tests, 66 static player tests, and Android Java
compilation pass. D3 decoder-attempt telemetry is the active resume point.

D3 is now also complete. `DecoderAttemptTelemetry` retains a maximum of twelve
events per playback session and exposes ordered candidates, selected decoders,
codec/audio errors, underruns, track/format counts, session exclusions, and
fallback reason/result. Both player families reset it per media load; GSY
delegates are unwrapped by diagnostics. Detailed stats, MCP, Test Current Video,
and diagnostic bundles use the same redacted data. Core tests, 40 static player
tests, and Android TV Java compilation pass. Resume at D4 session-local fatal
decoder quarantine; do not persist exclusions or quarantine parser/datasource/
seek/flush failures.

D4 and D5 are now also complete in the implementation tree. Fatal runtime
video-codec quarantine is session-local, limited to the selected decoder, and
allows one position-preserving renderer/codec reconstruction. Audio underrun,
audio output, downstream format, and track-change callbacks are bounded in the
same diagnostic telemetry. The fixture generator now creates
`mpeg2-sequence-resolution-switch.ts`, `h264-ts-timestamp-discontinuity.ts`, and
`h264-pmt-audio-track-switch.ts`; a two-second generation pass produced all 17
media fixtures and three explicit non-generatable/fault-injection records.
Forty-seven focused tests, Core tests, Android TV Java compilation, and diff
checks pass. Resume at D6 hardware-only regression; D4/D5 physical behavior is
not claimed until that matrix passes.

## Kodi/VLC decoder-stability audit (2026-09-12)

The audit-first source comparison is complete in
`docs/DECODER_STABILITY_SOURCE_COMPARISON.md`. It pins Kodi revision
`b08930bb0056b235e3b45c80113046721896694b` and VLC revision
`0a544554996ae900813ba92c70cd8c9408062497`, records the inspected demux,
clock, MediaCodec, AudioTrack, subtitle, DVD-navigation, and lifecycle paths,
and preserves the GPL/LGPL-to-Apache clean-room boundary. Reference players are
not launched, their source is not copied, and MX Player is closed-source
capability evidence only.

The audit rejects duplicate TS/MKV/DVD demuxers, master clocks, MediaCodec/CSD
state machines, AudioTrack sinks, speculative device blacklists, larger Pull
caches, and guessed byte seeks. The active dependency order is D1 error
classification, D2 policy-filtered initialization fallback, D3 decoder-attempt
telemetry, D4 session-local fatal-decoder quarantine, D5 bounded transition and
audio evidence, then D6 physical hardware validation. DVB Teletext is correctly
identified as a separate licensed decoder feature rather than mislabeled as
CEA or DVB bitmap.

## Direct stock-server MediaFile launch rule (2026-09-12)

Do not use on-screen SageTV Search as the normal way to start a known test
recording. After the MiniClient connects, MCP must resolve the requested title
to a unique SageTV MediaFile ID through Sagex or Nielm's stock-era Web
Interface, resolve the exact MiniClient UI context from its client ID, and send
the context-aware direct Watch command. For Web Interface-only servers the
command is `MediaFileCommand?command=WatchNow&context=...&MediaFileId=...`.
The Web Interface keeps recordings and imported media in separate `TVFiles`
and `MediaFiles` search indexes, so server-side resolution queries both and
deduplicates IDs. This lookup does not navigate the STV Search screen.

`mcp-playback-test --text ...` now uses that direct path first and verifies the
same MediaFile becomes active with healthy A/V. On-screen Search is permitted
only when server-side direct control is unavailable/not found, or when the
caller explicitly supplies `--force-ui-search` to test the UI itself. If a
direct request resolves the MediaFile but playback fails, MCP reports that
failure instead of masking it with Search. The stock `.175` physical gate
resolved `Taskmaster` to MediaFile `65500403`, targeted UI context
`444556303031`, started it without opening Search, and passed Media3
Pull/hardware A/V verification with `OMX.MTK.VIDEO.DECODER.AVC`.

## Bounded same-file Pull cache checkpoint (2026-09-12)

Media3 and legacy ExoPlayer now share the session-owned
`RetainedBufferedPullDataSource` behavior: one MediaServer session and an
8 MiB access-ordered cache of exact random-read bytes for stable completed
files. It never caches inferred offsets or parsed metadata. Path changes,
observed size/growth changes, disabling, and final release clear the cache, so
extractor and decoder policy changes cannot reuse parsed state.

The in-client ONN current-video test proved the useful local case: the repeated
target consumed 524,288 cached bytes, landed exactly, and recovered in 362 ms
while `c2.amlogic.mpeg2.decoder` remained initialized. A controlled distant
seek-away/return experiment then compared 8 MiB with 16 MiB using the same
`MeetthePress-65149351-0.ts`, Media3 Pull/hardware path, 985220 ms target, and
120000 ms away distance. Doubling memory did not materially improve the result:
recovery remained about 6.1--6.2 seconds with roughly 143--156 physical reads
and 37--41 MiB per target recovery. Decoder init/release stayed 1/0 for video
and audio. The 16 MiB experiment was reverted; the remaining long seek delay is
MPEG-TS timestamp/demux discovery and moves to the source-comparison phase.

## In-client current-video diagnostic checkpoint (2026-09-12)

The active playback long-press panel now has a distinct play/pulse
**Test Current Video** icon in shared, Android TV, and no-touch layouts. The
release-safe runner uses the current `MiniPlayerPlugin` and current loaded media
instead of an MCP-only path. It captures baseline/cadence health, pause/resume,
safe reversible first/away/repeat seeks, landing and recovery timing, frame
output, buffer/source reads, Pull reuse/cache, active decoder, display and sync
state. It skips unsafe DVD-menu/short-media checks and restores the original
position and play/pause state. Exceptions and player-session replacements are
contained and recorded instead of crashing playback.

Each run writes one redacted report through `CurrentVideoTestStore`; the newest
four are included in manual diagnostic ZIPs and Always/On-request SMB exports.
Physical ONN v1 validation used Media3 Pull/hardware with
`MeetthePress-65149351-0.ts`: 6 passes, zero warnings, zero skips, 9889 ms. The
first/repeat seek recovered in 360/362 ms, the away seek in 2199 ms, and the
player retained `c2.amlogic.mpeg2.decoder`. The inspected bundle
`artifacts/firetv/onn-current-video-test-diagnostics.zip` contains
`current-video-tests/test-1.txt` and no media path/server address. The current
APK SHA-256 before the documentation-only edits is
`22846c02f0066abb083dfcf6ab344d8ab7441b1f66b5e640c36863dcbc3f0f48`.

## ONN stock-server MPEG-2 seek checkpoint (2026-09-12)

The stock `.175` `Meet the Press` OTA MPEG-2/AC-3 recording has now run through
Media3, legacy ExoPlayer, GSY/Media3, and GSY/legacy-Exo on ONN v1. Each path
selected `c2.amlogic.mpeg2.decoder`, retained hardware video plus decoded audio,
and recovered from its applicable seek, skip, and pause checks.

The preserved legacy-GSY reproduction is
`artifacts/firetv/onn-meet-the-press-gsy-legacy-isolated.json`. During the right
skip, physical Pull reads continued, but the 10-second watchdog replaced the
extractor anyway. That caused 42 additional opens, about 83 MiB of reads,
18.9 seconds of read wait, and a recoverable
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`; video recovery took 21.55 seconds.
Both Exo generations now consult `PullSeekRecoveryPolicy`: a buffering watchdog
defers when the datasource has advanced since arming and completed a physical
read within the bounded grace window. Stale/no-progress cases retain the old
reprepare recovery. Core unit tests cover active, stale, unchanged, unknown, and
non-monotonic observations.

The exact post-fix repeat is
`artifacts/firetv/onn-meet-the-press-gsy-legacy-postguard-repeat1.json`: recovery
completed in 844 ms with three reads/1.5 MiB, no parser error, no reprepare, and
no video decoder release/reinitialization. The broader before/after artifact is
`onn-meet-the-press-gsy-legacy-active-io-guard.json`. Independent 15-second
full-screen HDMI evidence is
`onn-meet-the-press-post-active-io-guard.mp4` with its three-frame contact sheet.
The runtime tuning override used for diagnosis is reset; normal defaults are
active. The next active item is the bounded same-file stream/seek-hint cache,
followed by Kodi/VLC/FFmpeg/Media3/legacy-Exo source comparison. Reference
players must not be launched for this work. MX Player is closed source, so it
is excluded as a rule or code source; a user's successful MX playback remains
useful only as evidence that the device and media are capable.

## TV diagnostic export and issue-report checkpoint (2026-09-12)

The Android client now creates bounded, redacted support artifacts without
requiring email or a file-manager app on the TV. Media, configuration, and
diagnostics SMB destinations remain independent. Diagnostics has its own URL,
anonymous-or-credential authentication, test action, and Off/On request/Always
mode. On-request output is one ZIP with logs, playback traces, a live player
snapshot, redacted device/configuration metadata, and checksums. Always mode
uses one rotated UTF-8 session log, an app-private pending spool, atomic SMB
replacement, bounded local/remote retention, and checkpoints at the periodic
interval, STOP, playback error, app background, and exit.

The long-press remote panel's bug icon is immediately beside the triangular
Video Info icon in the shared, TV, and TV no-touch layouts. Physical ONN
evidence is `docs/images/diagnostics-long-press-menu.png`; selecting it opens
the dialog shown in `docs/images/diagnostics-export-dialog.png`. The GitHub
playback issue form and `docs/PLAYBACK_DIAGNOSTICS.md` explain the complete
no-email workflow.

Physical diagnostics-SMB validation on ONN reported URL 4 ms, DNS/connect
190 ms, authentication 184 ms, share open 83 ms, write/read/hash/delete
377 ms, total 916 ms, with verified cleanup. A reserved unreachable address
produced a redacted DNS/connect failure and a pending local spool whose size
and SHA-256 survived `am force-stop`. Relaunching with the real destination
automatically uploaded and removed the old pending file. The client was
restored to On request mode and all exact local/remote validation artifacts
were removed. The final gate passes 514 project/static tests, 73 MCP/workflow
tests, project validation, a clean 60-task APK build, GitHub issue-form YAML
parsing, physical icon activation, and `git diff --check`. The resulting APK
SHA-256 is
`27db1fd90d8617fdbc9d186b3d21cdd9c7425d57fbacb0bda4426b25a6258`.

## ONN long-recording and debug-reconnect checkpoint (2026-09-12)

The V1 ONN client is fully commissioned for ADB/MCP and has a unique
MiniClient ID. Debug-driven server changes no longer race the previous player
activity's teardown: the client explicitly closes the old connection, finishes
the resumed OpenGL/GDX player activity, and launches the replacement connection
after a bounded 750 ms delay. Physical switches `.175 -> .232 -> .175` each
created a new live connection generation without an immediate close request.

The reported persistent timeline/OSD was tested with the completed 5.5-hour
`ReconstructionAmericaAftertheCivilWar-48000171-0.ts` recording. Stock `.175`
and Vibe `.232`, Wait-for-playback On/Off, smooth FF, Stop, restart, and a
separate idle window all cleared the OSD normally on ONN. Vibe server logs show
`setPlaybackRate(4.0)` during shuttle and `setPlaybackRate(1.0)` at Stop and
Play, while client telemetry returned to `playbackRate=1.0`. This establishes
that recording duration and a stale normal shuttle rate are not sufficient to
cause the forum report; it is a non-reproduction, not a claim that the affected
Fire TV case is fixed. Evidence is under `artifacts/firetv` as
`onn-vibe-long-osd-shuttle-stop-restart.mp4` and
`onn-vibe-long-osd-idle-after-restart.mp4`, with matching contact sheets.

HDMI capture now uses the compiled sibling Vibe FFmpeg through
`capture_hdmi_validation.cmd` and its Python implementation. Do not launch VLC
for capture or media inspection.

## ONN encoded-audio checkpoint (2026-09-12)

ONN v1 `192.168.10.141` is commissioned as alias `onn_v1`, MiniClient ID
`44:45:56:30:30:35`, against unmodified SageTV `.175`. `Meet the Press`
reproduced a false-healthy AC-3 passthrough state: renderer counters advanced,
but the Amlogic HAL logged invalid raw frames and HDMI capture measured only
digital silence (`-91.0 dB` mean, `-84.3 dB` peak). Media3 and legacy
ExoPlayer now default to a PCM-only AudioSink capability policy and prefer the
bundled FFmpeg audio renderer, while video remains on
`c2.amlogic.mpeg2.decoder`. Passthrough is still an explicit user choice by
disabling `Decode encoded audio to PCM` in that player's settings.

Physical captures under `artifacts/firetv` prove the result:
`onn-meet-the-press-no-audio-baseline.mp4` is silent;
`onn-meet-the-press-ffmpeg-pcm-audio.mp4` measures `-27.0/-11.6 dB` mean/peak;
`onn-meet-the-press-legacy-exo-audio.mp4` is the silent legacy baseline; and
`onn-meet-the-press-legacy-exo-pcm-fixed.mp4` measures `-26.1/-10.1 dB`.
IJK and the GSY Media3/legacy delegates also have independent captures with
real program audio. Their applicable seek/FF/REW/pause/resume gates pass.

The complete video/DVD fixture matrix is intentionally deferred until the
targeted ONN and Kodi/VLC/FFmpeg-derived corrections are stable, as section 3 of
`TASKS.md`, so it is run once as a release gate rather than after every small
player change. NVIDIA Shield Tube and ONN 4K Pro must receive the same matrix
with separate aliases/client IDs; no Git commit or push is authorized yet.

## Clean-build/non-Pro stock-server closure (2026-09-12)

The complete validator and clean 60-task Dev APK build pass. The installed APK
is 50,449,749 bytes with SHA-256
`114d84471b4fd4854c8258e6a9e843bbb2c5788ab592e8114e1e3cf22f5403cf`.
After a clean install on non-Pro Fire TV `.25`, stock SageTV `.175` accepted
the normal MiniClient session and stock-compatible `ALADDIN` MediaFile launch.
Native/hardware Media3 selected `OMX.MTK.VIDEO.DECODER.MPEG2`; the bounded
cadence window advanced 36,546 ms media time over 36,306 ms wall time
(1.0066x), emitted 1,740 video and 1,134 audio outputs, and added zero video
drops, skips, or release gaps. Structured evidence is
`artifacts/firetv/nonpro-aladdin-final-clean-build.json`.

The independent USB HDMI capture is
`artifacts/firetv/nonpro-aladdin-final-clean-build.mp4`: 34.726 seconds,
1920x1080 H.264 video plus AAC audio. Its sampled frames show active main-title
video throughout the capture. The app session was then stopped cleanly.

## Native-DVD NVIDIA/Fire TV cadence closure (2026-09-12)

The Kodi-derived missing-PTS repair is now retained as a decoder-family rule,
not a device-model profile. `Auto` covers MediaTek OMX/Codec2 and NVIDIA
OMX/Codec2 MPEG-2 implementations, while explicit `On` and `Off` remain usable
for physical A/B testing. The NVIDIA Shield Tube `.68` passed stock-server
Aladdin Native/hardware playback with `OMX.Nvidia.mpeg2v.decode`: 30,530 ms of
media over 30,112 ms wall time (1.0139x), 1,098 video outputs, 941 audio
outputs, zero dropped frames, and zero sustained/non-positive release gaps.
Independent HDMI evidence is
`artifacts/firetv/shield-aladdin-auto-nvidia-final.mp4`; structured evidence is
`artifacts/firetv/shield-aladdin-auto-nvidia-final.json`.

The affected non-Pro Fire TV `.25` regression also passes with
`OMX.MTK.VIDEO.DECODER.MPEG2`: 31,042 ms of media over 31,294 ms wall time
(0.9919x), 1,501 video outputs, 978 audio outputs, and zero drops, skips,
sustained gaps, or non-positive release intervals. Evidence is
`artifacts/firetv/nonpro-aladdin-auto-post-nvidia-rule-cadence.json`. An earlier
run failed only because the stock `.175` server does not implement the private
debug-only exact 8:00 positioning event; startup, decoding, and cadence were
healthy. The production DVD/STV seek controls are not replaced by that test
hook.

## NVIDIA UK DVB checkpoint (2026-09-12)

Unmodified SageTV `.175` and NVIDIA Shield Tube `.68` now pass the supplied UK
DVB recordings through Media3, legacy ExoPlayer, GSY/Media3, and
GSY/legacy-Exo Pull hardware paths. `Breakfast` proves a leading MPEG-L2 NAR
track does not displace the English AC-3 primary track; `Classic Holby City`
proves English MPEG-L2 primary selection over NAR; `Taskmaster` provides the
long seek fixture. All three expose the real DVB bitmap track, and the long
fixture passes same-session subtitle Off/On plus FF/REW with advancing NVIDIA
H.264 output and advancing audio. Representative captures are
`20260912-163350_caption-media3-pull-visible.png`,
`20260912-163526_caption-exoplayer-pull-visible.png`,
`20260912-163719_caption-gsyplayer-pull-visible.png`, and
`20260912-163838_caption-gsyplayer-pull-visible.png` under
`artifacts/firetv`.

Two defects were corrected during that gate. Playback health now ignores
disabled renderers before reading decoder counters, so an unused extension can
no longer erase the active NVIDIA or FFmpeg renderer evidence. Completed stock
recordings now use the datasource's observed SIZE-growth result for seek
policy, not the ambiguous legacy OPENURL hint; this prevents a full TS
reprepare on ordinary seek and retains the current source for a duplicate
startup SEEK 0. Core seek-policy tests and the debug APK build pass.

At this checkpoint DVB Teletext was still an explicitly unsupported boundary:
Kodi owned a separate Teletext decoder/player path, VLC used libzvbi, and the
bundled Android FFmpeg extensions were audio-only. That boundary was
subsequently closed by the pure-Java, stock-server-compatible Level-1/page-888
implementation documented in the 2026-09-14 DVB Teletext checkpoint above.
The implementation does not copy or link Kodi/VLC decoder code and does not
disguise injected ATSC declarations as Teletext. DVB bitmap and Teletext remain
separate selectable track types. MX Player remains closed-source comparison
evidence only.

The Media3 FFmpeg audio AAR now has a complete release boundary in
`THIRD_PARTY_NOTICES.md`, `docs/DEPENDENCY_AUDIT.md`, and
`third_party/source-offers/README.md`. Its component-only rebuild script is
`source/dev/media3/buildffmpegext.sh`; it pins AndroidX Media 1.11.0, the exact
FFmpeg 6.0 commit, NDK 26.1, the decoder list, and the distinct
`libmedia3ffmpegJNI.so` package name. Static checks reject missing source/hash
records or accidental inclusion of FFmpeg video decoders.

## GitHub Pages latest-APK checkpoint (2026-09-11)

`docs/index.html` is the repository's stable latest-APK landing page. It calls
the public `releases/latest` API, accepts only the established
`OpenSageTV-Vibe-Android-Client-v*.apk` asset name, exposes a manual download
button, then performs a browser redirect. A permanent latest-release link and
safe text-only error state remain usable if automatic navigation or the API
fails. GitHub Pages is configured to serve `/docs` from `main` with HTTPS
enforced. The resulting URL is
`https://opensagetv-vibe.github.io/opensagetv-vibe-android-client/`.
This host-only addition does not change the APK; the checkout's existing
`REQUIRES_BUILD=true` still reflects the earlier v0.5.91 Android discovery
change. The completed gate passes 507 repository tests, 72 MCP/workflow tests,
Core Gradle tests, structural validation, and a clean 60-task build. The APK
remains byte-identical at SHA-256
`9d102efcbb5910df58e6286aa4e78086e770b01525be3d4bf71b1e6c951d8073`.
Repository checks and Pages deployment passed at commit `9c58061`; the live
page returned HTTP 200 and selected
`OpenSageTV-Vibe-Android-Client-v0.5.90.apk` from public release `v0.5.90`.

## Directed-broadcast server discovery checkpoint (2026-09-09)

The v0.5.91 client sends the unchanged SageTV `STV` discovery request to both
the legacy IPv4 limited broadcast and every unique directed broadcast address
reported by an active non-loopback interface. This covers Fire OS/network
stacks that do not forward `255.255.255.255` while preserving stock-server
protocol compatibility and manual/direct connection behavior. Duplicate
responses are collapsed by address, advertised port, and locator ID. A broken
or stale interface cannot prevent attempts on the remaining targets.

Focused Core tests and the complete host test suite pass. The clean APK has
SHA-256 `9d102efcbb5910df58e6286aa4e78086e770b01525be3d4bf71b1e6c951d8073`.
After a clean uninstall/install on non-Pro Fire TV `.25`, Android package
metadata reports `0.5.91-DEV-DEBUG` and a fresh launcher discovery lists stock
`.175` and isolated Vibe `.232` exactly once. Screenshot evidence is
`artifacts/firetv/20260910-001956_20260909-directed-discovery-clean-install.png`.

## Stock-server all-player MKV checkpoint (2026-09-09)

The v0.5.90 APK retains the physically proven v0.5.89 player source and plays
ordinary MKVs without a modified server. The
strict aggregate report is
`artifacts/firetv/20260909_stock_mkv_all_player_matrix.json`. On non-Pro Fire
TV `.25` against unmodified SageTV `.175`, `The Lion King` (H.264/AAC,
5,303,721 ms) passed 28/28 startup/control checks and `Scream_1`
(MPEG-2/AC-3, 6,656,404 ms) passed all 28 checks across legacy Exo, Media3,
IJK, and GSY Auto/Media3/legacy-Exo/System selections. One GSY/Media3 forward
seek on `Scream_1` recovered slowly in 15.352 seconds after 18.510 seconds of
Pull datasource read wait; no case crashed, exhausted the watchdog, or failed
to recover. MTK hardware AVC/MPEG-2 decoder identity is proven for the
Exo/Media3-backed cases. IJK's legacy health API proves control/timeline
recovery but does not report its decoder name; the existing AFTMM rule
intentionally rejects that device's broken MPEG-2 MediaCodec and falls back to
bundled FFmpeg. GSY System selection uses the datasource-compatible Media3
backend for Pull, which the report records rather than claiming native Android
System MediaPlayer playback.

Use `dev.cmd mcp-stock-mkv-matrix` (or the shell equivalent) to repeat the
gate. It reads `fixtures.mkv_searches` from the private TOML and uses stock
Sagex `Watch`, avoiding STV-dependent Search keyboard navigation. The common
matrix verifies the active post-launch configuration and retries one clean
apply/connect cycle when a stale prior setting is observed. These are host
workflow changes only; no Android player source changed. v0.5.90 rebuilds the
APK so GitHub provides an installable asset matching the current version. The
clean APK SHA-256 is
`7d2343d20bee7decb285ddb0a8631c8cf97da3c84616aecc39826b6b090a1469`;
inspection passes, and the exact artifact installs and launches on `.25` with
device package metadata `0.5.90-DEV-DEBUG`. The local gate
passes 503 project tests, 72 MCP/workflow tests, Core Gradle tests, structural
validation, manifest verification, and diff checks.

Stock SageTV supplied valid durations and seek targets. A separate `The Lion
King` entry on the modified test server was indexed as a 1 ms Matroska file
with no streams, so that server could only request a 1 ms seek. Treat that as
invalid server library metadata, not a player-side MKV failure. Prefer
stock-compatible client/protocol behavior; optional Sage.jar/MIM changes are
last-resort enhancements and must retain a safe stock fallback.

All generated video, caption, audio-codec, Kodi-codec, and authored-DVD
regression fixtures now live beneath
`/var/media/OpenSageTV_Vibe_Tests` / SMB share directory
`OpenSageTV_Vibe_Tests`. The obsolete authored-DVD backup was moved to the SMB
recycle directory; real recordings and commercial DVDs were not moved. The
post-move local gate passes 496 project tests, 72 MCP/workflow tests, and Core
Gradle tests.

## MCP connection reuse checkpoint (2026-09-09)

`dev_connect_server` now returns the existing healthy session when its address
and port already match and no renderer switch was requested. This prevents a
short follow-up MCP process from replacing the MiniClient sockets established
by the preceding process. Explicit renderer selection retains the established
reconnect path, and unavailable/older state snapshots safely fall through to
normal connect behavior. Unit tests and the physical MCP smoke test on non-Pro
Fire TV `.25` pass. This is MCP host tooling only; the v0.5.89 APK is unchanged.

## SageMC HOME and user-pause lifecycle gate (2026-09-08)

The lifecycle runner now accepts `--repeat 0` so HOME/background behavior can
be gated independently from the server's optional exact-watch replay loop. On
non-Pro `.25` against isolated `.232`, Media3/hardware Pull preserved the exact
MiniClient connection, hid/released and recreated the surface, resumed
advancing A/V after HOME only when playback had been active, preserved an
explicit user pause without emitting an automatic PLAY, and completed clean
teardown. The follow-on same-file replay remains a separate Core activation
gate rather than invalidating this lifecycle evidence.

## SageMC native DVD control completion (2026-09-08)

Native Media3/hardware DVD pause and resume pass on non-Pro Fire TV `.25` and
isolated server `.232`. Playback paused at 6,976 ms, resumed with both backend
play signals true, and advanced to 18,168 ms with renewed audio/video output.
The complete matrix, including STOP cleanup, is
`artifacts/firetv/sagemc-dvd-pause-resume-20260908-rerun.json`.

DVD Return was then verified visually on the commercial
`SCOOBY_DOO_AND_BATMAN` disc. The Sage command path opened the root menu, moved
RIGHT to Languages, selected its submenu, and returned to the root with
`dvd_return`. The four screenshots are `20260908-115225_screen.png` through
`20260908-115251_screen.png`; structured evidence is
`artifacts/firetv/sagemc-real-dvd-return-20260908-horizontal.json`. Earlier
transport-green attempts using DOWN were rejected after screenshot review
because this disc's root buttons are horizontal. Physical video evidence,
not merely an active player object, is the completion criterion.

SageMC may keep the stopped player object after HOME closes `StopPopup`.
`mcp_disc_test.py` now accepts that cleanup state only after the exact popup
was observed and dismissed and both `health_isPlaying` and
`health_playWhenReady` remain false. Full teardown is still accepted normally.
The combined playback-automation/DVD-protocol suite passes all 106 tests.
The complete post-change local gate also passes: 494 project/static tests, 70
MCP tests, Core Gradle tests, structural validation, and a clean 60-task APK
build. Because this slice changes only test automation and durable documents,
the rebuilt APK remains byte-identical to v0.5.88 with SHA-256
`f1791bf74cefc1c8ca98718c75912e2e7d4f0c12dc8de26ae7522f8cce247e3b`.

## SageMC retained-player interoperability (2026-09-08)

SageMC leaves a stopped MiniPlayer loaded behind `StopPopup`. The debug-only
exact-file helper now treats that player as stopped only when
`health_isPlaying=false` and `health_playWhenReady=false`, then sends the
neutral HOME command and waits for the popup to close before issuing the next
watch request. It reports `retainedStoppedPlayer` and `stopPopupDismissal`, and
fails closed if the old backend remains active or the popup remains open. The
focused 33-test MCP playback-automation suite and Python compilation pass.

The resulting physical run proved that the popup no longer consumes the next
request. It also exposed a separate server behavior: a redundant watch request
for the same stopped MediaFile returns success without restarting playback.
That correction belongs to the opt-in Vibe Core commissioning event and is
being physically gated on isolated server `.232`; stock `.175` remains
untouched.

## Post-release SageMC automation compatibility (2026-09-08)

The shared physical MCP root guard now accepts either stock `Main Menu` or SageMC's
historical `Dynamic Menu by nielm` as an idle automation root, while retaining
the connected/no-player/no-popup/no-text-input safety checks. Connection-order,
playback, Media3/player/tuning matrices, SMB A/B, embedded-preview, lifecycle,
and session runners all consume that one guard. The focused 38-test
playback-automation suite passes. Exact-file Media3 Pull and stock-compatible
Live TV both completed through SageMC on non-Pro Fire TV `.25` against isolated
server `.232`; the Live TV gate verified stable full-screen advancing audio and
video. Visual exact-file evidence is
`artifacts/firetv/20260908-085858_collect_screen.png` with matching trace and
system diagnostics. The first UI matrix also rendered Home, Guide, Recordings,
Schedule, Search, Video Library, Setup, Guide navigation, and recording details;
its screenshots use the `20260908-0914*` through `20260908-0917*` artifact names.
This is test-harness compatibility only; it does not alter the APK or playback
implementation.

## GitHub v0.5.88 release (2026-09-08)

Growing-file seek is corrected and physically gated on the commissioned
non-Pro AFTMM/API-25 Fire TV (`192.168.10.25:5555`, DEV001) with hardware
decoding. The completed-file Pull matrix at
`artifacts/firetv/20260907_all_player_pull_hardware_seek_matrix.json` ran seven
player/engine selections and all 42 absolute seek, forward, backward,
pause/resume, Comskip-right, and Comskip-left checks recovered. Stock `.175`
growing Live TV then passed server-owned rewind/forward on legacy ExoPlayer,
Media3, IJK, GSY Auto, GSY Media3, and GSY Legacy Exo. The guarded GSY System
selection is deliberately a compatibility fallback on this device: its native
probe records `GenericSource: Failed to init from data source` and
`android_system_player_error`, then Media3 hardware playback recovers.

IJK's native bridge must expose the current growing file size to FFmpeg; `-1`
causes the demuxer to cache the source as permanently unseekable. After a
successful seek, only a proven backward raw-clock discontinuity enables the
segment-relative clock offset. The final strict IJK run rewound 8,190 ms and
advanced 11,823 ms. The live test now promotes and verifies full-screen output
before seek measurement because its earlier timeline-only verdict passed while
HDMI still showed an STV menu. Corrected visual evidence is
`artifacts/firetv/20260908_ijk_live_seek_fullscreen.mp4` and its contact sheet.

Local release gates are green: 494 project/static tests, 70 MCP tests, Core
Gradle tests, validation, and a clean 60-task build. The APK is
`artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`, SHA-256
`f1791bf74cefc1c8ca98718c75912e2e7d4f0c12dc8de26ae7522f8cce247e3b`.
Standalone CI repairs are included. GitHub Repository checks run `34194405901`
passed from a clean standalone checkout, including the repaired
`source-contracts` job. The deterministic source archive, development-signed
APK, checksums, and review bundle were independently inspected before the
`v0.5.88` GitHub release was tagged.

## GitHub v0.5.87 release (2026-09-07)

The final source tree, deterministic source archive, development-signed APK,
release manifest, and SHA-256 checksums are published from the `main` branch
under tag `v0.5.87`. This is a GitHub source/APK release; Amazon Appstore and
Google Play submission remain explicitly deferred in `TASKS.md`.

## Final playback-recovery regression (2026-09-07)

The remaining executable playback gates are complete on the commissioned
AFTMM/API-25 Fire TV (`DEV001`). The Fire TV Pro retains its independent
`DEV002` client ID and was not used by the final automation.

STOP followed by PLAY now exercises the actual SageTV retained-session
contract. Media3, legacy Exo, IJK, and guarded GSY/System pass Pull, Push, and
Fixed with hardware MPEG-2 output. Media3 and legacy Exo also pass SMB Direct;
their SMB/shadow sessions remain open across STOP and close only on terminal
FREE/DEINIT. IJK avoids its native 0.8.8 `stop()`/`prepareAsync()` crash path by
pausing and retaining the loaded decoder/source until DEINIT. IJK and
GSY/System perform resume-time Surface work on the Android UI thread and reject
stale callbacks. The physical runner clears old logcat before each session so
an earlier tombstone cannot fail a healthy rerun.

Media3 distinct-file switching passed over Pull and SMB Direct from the
generated caption fixture to `MeetthePress-65149351-0.ts`, retained
`OMX.MTK.VIDEO.DECODER.MPEG2`, and recorded zero fallback. Full replacement for
legacy Exo, IJK, and guarded GSY/System passed across Pull, Push, and Fixed.
All four player selections passed real Vibe-server tuner transitions limited
to 2.1 -> 5.1 -> 2.1. An unmodified stock server cannot acknowledge the
test-only Vibe channel event, so `mcp-live-test --current-channel-only` now
proves ordinary Live TV independently; Media3 and legacy Exo both passed with
advancing A/V. The normal startup timeout covered the bounded legacy SIZE
growth probe and HDD wake delay without an unbounded wait.

Caption regression passed without an Android enable gate. On stock `.175`,
Media3 and legacy Exo each passed SageTV/STV Off -> CC1 -> CC2 -> Off -> CC1
while event 225 continued and the local duplicate overlay stayed detached. On
Vibe `.232`, both passed continuous event-225 delivery from the generated A/53
fixture. Automated STV-state cycling is intentionally a stock-server evidence
path here because `.232` has no configured Web/Sagex endpoint; the underlying
Vibe callback gate itself passed.

Final local results: 489 project/static tests, 70 MCP/workflow tests, Core
Gradle tests, project validation, strict debug APK inspection, and a clean
60-task build all pass. APK:
`artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`; SHA-256:
`8953aaef148c45fb035b5bad048daf062152f0ac0cb9deaebcaebcffd4e6a006`.

## Unified local commissioning environment (2026-09-07)

`config/firetv.toml` is now the only local commissioning/regression settings
file. It is ignored and excluded from packages. Its schema-2 form supports
multiple named/aliased Android clients and SageTV servers; each server keeps
its Web/Sagex details and flat `smb_*` fields in the same table, so changing
`active_server` cannot accidentally retain another server's SMB credentials or
mapping. A selected device may supply its own automated client ID. The old
root `device` format is still accepted.

All physical Python runners derive the default SageTV address and MiniClient
port from the selected server. The session, SMB A/B, fast-switch, and playback-
rate workflows also consume that server's SMB mapping/credentials. Sagex and
the stock Web Interface match credentials by the server address. Use
`SAGETV_TEST_DEVICE_ALIAS` or `SAGETV_TEST_SERVER_ALIAS` for a one-shell
selection override. `dev.cmd config-check --summary` prints only a redacted
view.

New contributors run `commission_test_environment.cmd` or
`./commission_test_environment.sh`. The first invocation creates the ignored
file from the sanitized example and exits for editing. Subsequent runs validate
the configuration and unified toolchain, create/reuse canonical regression
fixtures, and run test/validate/build; install/launch is opt-in. It never
publishes or overwrites remote SMB media automatically. Durable instructions
are `docs/TEST_ENVIRONMENT.md` and `docs/COMMISSIONING.md`. Acceptance passed
487 project tests, 70 MCP/workflow tests, Core Gradle tests, both shell syntax
checks, PowerShell parsing, and the real bounded
`commission_test_environment.cmd -SkipFixtures -SkipBuild` preflight on the
selected AFTMM/API-25 device. The completed TOML/commissioning item has been
removed from `TASKS.md`.

## STOP/restart and SageMC startup OSD diagnosis (2026-09-07)

SageTV STOP is not terminal: stock Core may later issue SEEK/PLAY against the
same loaded MiniPlayer without sending another OPENURL. Android previously
ended the playback session generation at STOP, so the later PLAY was rejected
as stale and produced a black stall. STOP now advances only the operation
token; FREE/DEINIT remains the terminal session boundary. Physical `.25`
testing against stock `.175` confirmed STOP, restart at zero, prepare from
idle, and a new first frame.

A second captured symptom was only cosmetic. On a never-watched recording,
SageMC 169 briefly displayed `1:22:00` at the end of a `1:02:00` airing before
correcting to zero. Twelve bounded startup trace replies from Android were all
exactly zero, with no EOS or end seek, while the server opened its OSD during
its initial zero-duration window. The stock SageTV7 STV did not reproduce it.
The opt-in **Wait for playback before first OSD** setting now coalesces the
first playback-OSD presentation until the active backend reports its first
presented video frame, with a hard five-second failure release. The guard is
enforced at both `flipBuffer()` and the actual
OpenGL/libGDX render boundary so an already-pending render request cannot expose
the stale frame. A successful MiniPlayer load also re-arms the guard when
SageMC keeps `MC MediaPlayer OSD` active across a file switch and therefore
does not emit a second menu-entry transition. It does not change the player
clock, seek target, protocol
reply, or STV state, and it has no per-frame player query after the bounded
startup window. The earlier clock-based release remained too early and menu-
entry-only arming missed consecutive files when SageMC retained the same OSD.
Physical `.25` testing against stock `.175` confirmed the load-triggered first-
frame release removes the visible end-to-zero jump. Evidence is also preserved
under `artifacts/firetv/20260907-234226_sage-mc-start-osd-first-frame_*`. MCP now provides
`dev_reset_media_watch_state`: its unconfirmed call is read-only and returns a
warning; `confirm=true` clears the complete Watched/resume row for one explicit
MediaFile through Sagex or Nielm's stock Web Interface. Evidence is
`artifacts/firetv/20260907-zero-start-watch.mp4`,
`artifacts/firetv/20260907-zero-start-watch-flash.png`, and
`artifacts/firetv/20260907-210419_player_playback-trace.jsonl`.

The associated backend-parity audit covers all selectable player paths.
Media3 and legacy ExoPlayer use their rendered-first-frame callbacks; IJK and
GSY/System use their native video-rendering-start callbacks; the GSY adapter
delegates the resulting state. The audit also corrected GSY forwarding for
explicit growing-file metadata and the complete extended player contract.
These code/host gates cover every backend, while the physical confirmation
above is specifically Media3/Pull/hardware on `.25`; the broader player/mode
matrix remains active in `TASKS.md`.

## SageMC Guide texture lifetime and channel-logo optimization (2026-09-07)

Stock server `.175` with SageMC exposed two related OpenGL defects on the
commissioned AFTMM/API-25 `.25`. Guide background textures were sometimes
black because `ImageCache.unloadImage()` nulled a shared holder on the protocol
thread before the GL thread executed its queued draw. Image disposal is now
owned by each renderer; OpenGL and libGDX enqueue it after earlier draws, and
OpenGL deletes its texture on the owning GL context.

SageMC's channel-logo files are commonly 1024x768 despite being displayed as
small Guide thumbnails. Full decode/upload consumed about 3 MiB per logo and
the 64 MiB logical cache repeatedly churned while paging. OpenGL now recognizes
only the stable `ChannelLogos` resource-cache path, samples those bitmaps to a
maximum 256-pixel edge, and retains the original logical dimensions for SageTV
source rectangles. Other artwork remains unchanged. The default logical image
cache is now 128 MiB; persisted user choices remain authoritative.

The exact physical build (`45b8898a8c2d47a341dde01c46853315c935f9da23f67d5e5e880b3a833012dd`)
is installed on `.25`. The initial and three-page SageMC Guide checks show the
correct blue background, sharp logos, and correct selection cells. No texture
render exception, fatal error, slow image decode, or slow texture upload was
logged. `dumpsys meminfo` reported GL memory near 39 MiB after the change,
versus about 115 MiB in the reproduced pre-optimization state. Evidence is
`artifacts/firetv/vibe-guide-opt-first.png`,
`artifacts/firetv/vibe-guide-opt-nav.png`, and
`artifacts/firetv/vibe-guide-opt-paged.png`.

## Persistent trace and stock full-file switching (2026-09-07)

A stock `.175` SageMC file switch was captured with SageTV's generic
`sage.PlaybackException`. The server log supplies the decisive cause: after
closing the prior MiniPlayer it waited for a new player socket, could not open a
connection to `.25`, requested a media reconnect, and timed out in
`MiniPlayer.load()`. Android previously waited for TCP EOF after replying to
DEINIT. It now breaks that read loop after the reply and immediately registers
a fresh media socket. A repeated switch later found that Exo media-session
release could itself block the media-command thread before that reply was
written: Media3 and legacy Exo now deactivate/release `MediaSessionCompat` on
Android's main thread, after the protocol path is free to answer DEINIT. The
same capture also proved why stock resume began at zero. Both Exo
implementations called `seekTo()` before attaching the new
MediaSource, so source attachment discarded the old-timeline seek. In addition,
stock SageTV provides no explicit active-file metadata and the compatibility
fallback marked every `.ts` as growing. Publishing unknown length for a
completed transport stream prevented the extractor from seeking even after the
source-attachment order was corrected. The queued position is now supplied to
`setMediaSource(source, position)` atomically, and all four Pull bridges verify
the legacy-only active-file guess with one bounded 750 ms SIZE-growth probe.
Explicit Vibe metadata remains authoritative.

The debug APK now writes a bounded app-private `playback-trace.jsonl` on a
low-priority worker. One 2 MiB current file and three rotations retain exact
events through player teardown; URI credentials/secrets are redacted. Every
record includes wall/monotonic timing, connection/reconnect and playback
generation, positions/duration/buffering, state, surface, and decoder counters.
Seek and OPENURL records include useful sanitized detail. Player-diag and MCP
diagnostic bundles export all rotations; MCP can query status, persistently
enable/disable new records without deleting evidence, or clear only the trace
files. Analysis sorts asynchronous records by event time while retaining an
out-of-order count and checking actual sequence loss.

Physical AFTMM/API-25 `.25` results against unmodified `.175` are complete:

- Meet the Press: requested/resumed at 1,274,317 ms; first physical read was
  byte 828,250,732 and hardware MPEG-2 A/V advanced.
- Direct switch to `VibeSeekTest-1080i-MPEG2-AC3-CC`: DEINIT received a reply,
  Android recycled the media socket, reconnect succeeded, and replacement
  OPENURL arrived 48 ms after DEINIT. It resumed at 543,079 ms and rendered its
  first frame 2.18 seconds after the server seek with no traced error.
- A stock live recording changed SIZE from 1,349,612 to 1,419,964 during the
  750 ms probe, remained open-ended, and continued hardware video/audio. The
  runner's channel-identity failure is expected because `.175` lacks the
  Vibe-only debug channel-event acknowledgement, not because playback failed.
- Six consecutive stock SageMC full switches on `.25` crossed Media3 hardware
  MPEG-2 sessions without an exception. Every DEINIT was followed immediately
  by `media_socket_recycle_after_deinit`, `.175` accepted every replacement
  socket/OPENURL, and the server recorded zero `PlaybackException` and zero
  `Did not find a player socket` timeout.

Evidence is in
`artifacts/firetv/20260907-154249_stock-resume-growth-fixed_*`,
`artifacts/firetv/20260907-154648_stock-file-switch-fixed_*`, and
`artifacts/firetv/20260907-154909_live-tv-failure_*`, and
`artifacts/firetv/20260907-185555_stock175-user-switch-test-followup_*`. The
installed APK SHA-256 for the repeated-switch fix is
`75e6c3119cb45ff3535f217be0b5f41ff251dfcc2ac1ec3e8f759c6f88383022`.

## Growing live-TV boundary and recovery fix (2026-09-07)

The Fire TV Pro live-TV rewind was reproduced and traced to Media3 1.11's
`StuckPlayingNotEndingDetector`. The active recording had been exposed as a
finite resource using its size at `OPEN`; after the decoder reached that stale
timeline end and remained there for 60 seconds, Media3 raised
`ERROR_CODE_TIMEOUT`. The generic retry then called `seekTo()` on the errored
player and `prepare()`, which restarted the recording at zero. No SageTV seek,
flush, client reconnect, datasource error, or server restart preceded it.

Both Media3 and legacy-Exo Pull adapters now return unknown length for a
growing recording and perform a bounded SIZE/growth check only at the actual
read edge. Pull/SMB error recovery reattaches the media source at the position
captured by the error callback; PUSH/FIXED position remains server-owned.
Media3 player retention is explicitly rejected while the current item is
growing so a program-boundary `OPENURL` receives a clean player/datasource.

The native IJK and GSY/System bridges received the equivalent growing-source
contract, with a bounded ten-second edge wait for tuner write gaps. Their
state-changing callbacks are now session-generation guarded. Physical testing
also found and fixed IJK's null media-clock dereference after release: a queued
DVD/SPU clock probe could otherwise crash the whole MiniClient while a live
program player was being replaced.

Physical AFTKRT/API-30 `.29` results against test server `.232`:

- Media3 Pull/hardware: six alternating 2.1/5.1 changes, PASS.
- Legacy Exo Pull/hardware: four alternating changes, PASS.
- IJK Pull: four alternating lifecycle changes, PASS with no crash. Its old
  runtime disables MPEG-2 MediaCodec on this model, so this is not recorded as
  an IJK hardware-decoder pass.
- GSY Auto (Media3), GSY legacy Exo, and GSY System: two alternating changes
  each, PASS; System was intentionally run last.

A separate Media3 Pull/hardware run against unmodified server `.175` remained
healthy for roughly two minutes, beyond the old 60-second failure point. Its
duration stayed unknown/dynamic, media position and reads advanced, the active
decoder was `OMX.MTK.VIDEO.DECODER.MPEG2`, and retry/read-error counts remained
zero. `.175` accepted but did not act on the Vibe debug channel event, so the
runner's final `channel_identity_not_confirmed` label is an automation/control
limitation, not a playback failure. Evidence is retained in
`artifacts/firetv/20260907-132101_pro-stock-live-growing-120s-post-fix_*`.
The installed APK SHA-256 is
`3ba4eb5f07be6481784d742fca306fbc62d1b2dd3aca582065e5f6d5f9454598`.
The same APK was finally installed on the normal AFTMM/API-25 `.25` target and
passed a two-change Media3 Pull/hardware 2.1/5.1 smoke; `config/firetv.toml` is
restored to `.25` for subsequent work.

## Fire OS service-lifecycle playback fix (2026-09-07)

The latest Fire TV Pro failure was reproduced before changing code. SageTV
displayed `sage.PlaybackException`, while Android logcat showed the actual
failure: `RejectedExecutionException` from
`Media3MediaPlayerImpl.resetLegacyCaptionsForDiscontinuity()` during
`BaseMediaPlayerImpl.load()`. `MiniclientService.onDestroy()` had terminated the
Application-owned `MiniClient.backgroundService`, even though Fire OS retained
the Application and foreground Activity. Every later `OPENURL` in that process
therefore failed before video could start. This was a client lifecycle defect,
not a `.175`/`.232` server failure and not a cost of sampling Playback Stats
CPU.

`MiniclientService` no longer shuts down the singleton client. Explicit
Application teardown retains ownership of `MiniClient.shutdown()`, and both
Media3 and legacy Exo defensively ignore executor rejection for optional legacy
caption flush/drain work during final teardown. Static lifecycle coverage
locks both rules.

The rebuilt APK passed full tests, validation, build, and APK inspection. It
then passed real hardware Media3 Pull playback on AFTKRT/API-30 `.29`; the user
confirmed playback and requested that all further testing return to non-Pro
`.25`. On AFTMM/API-25 `.25`, the same APK passed hardware MPEG-2 Pull against
Vibe server `.232` and an independently invoked stock-server `.175` Watch of
Meet the Press. The `.175` result reported advancing video/audio,
`OMX.MTK.VIDEO.DECODER.MPEG2`, a valid 1920x1080 surface, zero Pull read errors,
and no `RejectedExecutionException`, `PlaybackException`, or fatal exception.
Primary evidence is
`artifacts/firetv/20260907-002425_pro-video-exception-before-repro_logcat.txt`,
`artifacts/firetv/20260907-004801_pro-post-fix-second-open_screen.png`, and
`artifacts/firetv/20260907-005950_nonpro-stock-post-fix_logcat.txt`.

## Fire OS total-CPU compatibility (2026-09-06)

The Playback Stats CPU sampler no longer depends exclusively on aggregate
`/proc/stat`, which newer Fire OS application sandboxes may hide. It first uses
the original aggregate tick source and otherwise derives the same 0-100%
device-capacity value from `/proc/uptime` cumulative idle time and the available
CPU count. If Fire OS hides that file too, it uses cached read-only
`/sys/devices/system/cpu/cpu*/cpuidle/state*/time` counters. A source identifier
prevents a runtime source change from comparing different units. No permission,
shell helper, persistent process, or sampling outside the visible overlay was
added. The `/proc/uptime` fallback physically reported Vibe, Other, and Total
CPU on AFTMM/API-25. AFTKRT/API-30 denies both proc sources and physically
passed through the sysfs fallback; evidence is
`artifacts/firetv/vibe-pro-cpu-sysfs.png` (Vibe 1.4%, Other 44.5%, Total 45.9%
for the captured interval).

## Stock-server caption seek/placement correction (2026-09-06)

The standard legacy-extender callback path is now robust across MPEG-2 seeks.
The apparent STV placement problem was corrupted CEA-608 decoder state: MPEG-2
B-picture caption packets were reaching SageTV in decode order, and parallel
CEA-608/708 extractor callbacks could repeat non-adjacent packets. The bridge
now keeps a bounded exact duplicate history and sorts pending callbacks by PTS.
Both Media3 and legacy Exo extractor wrappers also discard incomplete caption
samples at every extractor seek, while the player-level reset clears read-ahead
and resets the stock SageTV decoder before new packets are delivered.

Physical AFTMM/API-25 `.25` validation against unmodified server `.175` passed
the STV Off/CC1/CC2/Off/CC1 sequence and FF/REW/FF2/REW2 for both hardware Pull
backends. The final Media3 evidence is
`artifacts/firetv/20260906-181311_caption-media3-pull-legacy-callback.png`; the
legacy Exo evidence is
`artifacts/firetv/20260906-181613_caption-exoplayer-pull-legacy-callback.png`.
Both show ordered roll-up captions in the normal lower-screen region after all
seeks, and their latest displayed caption is within two seconds of the burned
fixture PTS. Installed APK SHA-256:
`c44cacb8e9e8f6c784842e15a1b6d79cd1d819a417f37cfc387950a999a2b3ec`.

## Long-duration native DVD cadence closure (2026-09-06)

The reopened non-Pro Fire TV DVD cadence task is complete. The disc harness
previously capped every cadence observation at 180 seconds even when 600 was
requested; the bounded maximum is now 900 seconds and has regression coverage.
The exact installed APK played Aladdin from the same 8:00 motion scene for a
true 601.349-second observation. Media advanced 601.096 seconds (0.99958x),
hardware MPEG-2 rendered 28,837 frames with zero drops, only two isolated
skips, and no new long release gap or non-positive release interval. Audio had
zero drops and the final A/V sample delta was 131 ms. The final detailed overlay
reported approximately 7.9 seconds buffered, 7.7% Vibe CPU, and no transient
EOS. This does not reproduce a client buffer, CPU, decoder, or timestamp-drift
failure and therefore closes the current bounded task without another playback
tuning change.

Machine evidence:
`artifacts/firetv/dvd-aladdin-10m-true-long-cadence-20260906.json`.
Visual evidence:
`artifacts/firetv/20260906-dvd-aladdin-10m-sample-2m.png` and
`artifacts/firetv/20260906-183728_screen.png`. After the final clean build and
install, a separate 20-second startup/cadence/STOP smoke also passed in
`artifacts/firetv/dvd-aladdin-final-clean-apk-smoke-20260906.json`.

## Playback Stats and reopened DVD cadence investigation (2026-09-06)

The long-press Active Player Adjustments menu now owns a universal, mode-aware
Playback Stats panel. Compact/detailed persistent modes, a bounded 30-second
mode, hide, and redacted export are available. Sampling runs once per second
only while the panel is attached and is cancelled when the playback Activity
pauses or is destroyed. Common decoder, video/audio, timing, frame, display,
buffer, synchronization, and recovery evidence is joined only by the current
Pull, SMB Direct, Push/Fixed, caption, or DVD section. The three live bars show
measured media-byte activity, mode-scaled buffered playback time, and fixed-scale
device/Vibe CPU. Each value is shown only above its bar; the duplicate text rows
and invariant estimated link-capacity bar were removed. Consumer-only fields
from the visual reference are deliberately excluded. The long-press navigation
overlay also has a direct bar-chart toggle
beside the gear and CC controls. It is white when disabled and green when
active. MCP's `dev_set_active_player_overlay` supports `toggle`, `off`,
`compact`, `detailed`, and `detailed_30s`, while retaining the prior Boolean
contract. The CPU bar separates Vibe from the remainder of total device usage
on a 0-100% scale and does not poll either value after the panel closes.

The final simplified three-bar layout is physically verified in
`artifacts/firetv/20260906-154036_playback-stats-cpu-color-font-final-20260906_screen.png`.
It shows media activity, buffer health, and a 0-100% stacked CPU bar only. All
detail text uses the graph-label font and size; the sample color-matches blue
`Vibe 20.3%`, orange `Other 17.6%`, and neutral `Total 37.9%` to the bar. The
exact installed APK SHA-256 is
`8141957fb0a4d318ba9158616d390244c3d990431da34b2e6efbc71246841cda`.

The same build fixes a real Media3 release crash in which display-refresh
inspection called `getVideoFormat()` from SageTV's GFX worker. Display-mode
application is now marshaled to Android's main thread and the OFF/restore path
never queries player metadata. A clean hardware Media3 DVD start, seek, play,
and stop cycle passes in
`artifacts/firetv/dvd-aladdin-refresh-thread-fix-smoke-20260906.json`.

The actual submenu was exercised on AFTMM/API-25 `.25`. Hardware Media3 Pull
and native Aladdin DVD views were both readable over active video and contained
only their applicable transport rows. HOME removed the overlay and sampler.
Evidence is retained in
`artifacts/firetv/20260906-134458_screenshot_screen.png` and
`artifacts/firetv/20260906-135003_screenshot_screen.png`. The DVD snapshot at
about 8:00 showed a six-second decoder buffer, no renderer drops, five
cumulative release gaps, active timestamp correction, and a sampled +252 ms
A/V delta. Long-duration DVD cadence degradation reported after extended
playback is therefore reopened as the active investigation; short healthy
windows do not close it.

The direct icon and corrected grid were physically verified in
`artifacts/firetv/20260906-playback-stats-nav-icon-fixed.png`; its active green
state is in `artifacts/firetv/20260906-playback-stats-nav-icon-green.png`.
MCP toggled the snapshot state false and then true. Earlier CPU sampling
evidence remains in `artifacts/firetv/20260906-playback-stats-cpu.png`; the
final evidence and installed hash are recorded above.

## GitHub publication preparation (2026-09-05)

The public repository is a true fork of
`https://github.com/OpenSageTV/sagetv-miniclient` at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client`. Local
`origin` points to the Vibe fork; `upstream` fetches from OpenSageTV and has a
disabled push URL. The Vibe branch is `main`; upstream history remains
available on the fork's `master` branch.

The local prepublication gate passed 451 repository/static tests, 63 MCP
tests, Core JUnit, every `dev.cmd validate` contract, a clean 60-task debug APK
build, and the strict debug APK inspector. The inspected package is
`opensagetv.vibe.miniclient.debug`; permissions are limited to network state,
Wi-Fi state, Internet, wake lock, and Android's generated dynamic-receiver
permission. The APK is development-signed as documented. Final exact-commit
hashes and GitHub release assets are recorded after the clean-tree rerun.

Public source no longer contains a preselected SMB configuration share or SMB
test username/password. Commissioning values remain in ignored
`config/firetv.toml` and must be supplied explicitly. The protected original
`C:\TMP_SAGETV_DOCKER\SageTV-MiniClient-Dev` remains untouched.

Release `v0.5.85` is published at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.85`.
The exact APK SHA-256 is
`b143d8201496bd12039ebf5a452e591dfc2ec3684f5464e35dc93014abcbcd65`;
the verified source archive SHA-256 is
`311a88e1089ad18cc096f3317cdfa540c4ae307a0007009baf2e1d5fc546b0be`.

## Current physical DVD baseline (2026-09-05)

Android now has a physically commissioned implementation of the standard
legacy-extender caption producer. Media3/legacy Exo/GSY extractor delegates return raw CEA
packets through event 225 and advertise `GFX_SUBTITLES`; IJK advertises false.
This is intended to let an unmodified SageTV STV own and render CC exactly as
the hardware-extender path did, while retaining `VIDEO_CC_STATE` as an optional
compatibility extension. The exact APK passed `.25`/`.175` negotiation, raw
decoded and wire counters, the STV Off/CC1/CC2/Off/CC1 cycle, post-seek
recovery, and no-duplicate-overlay checks. Media3 passed the complete stock-STV
cycle; legacy Exo produced 1,017 events/35,096 bytes and recovered after FF in
1.02 seconds after its attached-overlay race was fixed. IJK stayed playable,
advertised no callback producer, and emitted zero event-225 bytes.

Physical remote long press is restored on `.25`. Key ownership is now at the
MiniClient Activity above OpenGL/GDX/player-surface focus, while the original
configured key maps remain authoritative. DVD menus consume only short D-pad
and Select presses. Both Android-TV navigation layouts contain Active Player
Adjustments, and the dialog treats that optional view defensively. The exact
installed APK passed an injected Fire OS hold and visibly opened the complete
navigation/player-controls overlay without a crash. It remained visible in
captures at 100 ms and one second after injection while Meet the Press played;
evidence is `artifacts/firetv/longpress-100ms.png` and
`artifacts/firetv/longpress-1000ms.png`.

The server-owned DVD seek and on-the-fly Active Player Adjustments gates pass
on the commissioned non-Pro Fire TV (`.25`). The submenu applies safe controls
immediately and uses a bounded server-owned same-position reload only for
decoder construction changes. Subtitle `+500 ms`, 29.970-to-59.94 Hz matching,
active AC-3 track selection, hardware decoder retention, and diagnostics export
were physically verified. Aladdin's HDMI output at 8:00 matches the raw VOB's
authored 3:2 film cadence and has no sustained freeze or decoder drops, so no
destructive timestamp change was made. The latest deterministic authored-DVD
matrix passes menus, title, chapters, audio, subtitles, pause/resume, STOP, and
teardown; the missing-disc safe-failure gate also passes. The stock-JAR caption
audit is complete: the original protocol does not publish the STV checkbox
state, while proprietary extenders returned decoder-extracted caption packets
through callback 225. Android now implements that callback for extractor-backed
players and retains explicit Off/CC1/CC2 controls only for IJK or failed
negotiation. The optional Vibe `VIDEO_CC_STATE` extension remains accepted.
All bounded legacy-extender semantics currently implementable and physically
testable in the commissioned environment are complete. The active local work
is the final manifest/test/validation/build refresh, followed by GitHub
remote/tag approval and, only after explicit authorization, publishing the
already prepared source and APK artifacts.

Capability-safe Media3 file switching is complete. Retained completed-file
Pull and SMB Direct replacements preserve the player/Surface and now have an
eight-second first-frame watchdog in addition to the existing asynchronous
error fallback. Either path makes exactly one normal full-player fallback;
success, release, or a superseding OPENURL cancels it. Exact APK
`c5658c7d0036107e0b30b4eaecdf6514bcec2cdc21d02e7c7b59ebf1fc739605`
passed same-fixture and distinct-file switches over both transports on `.25`
with `OMX.MTK.VIDEO.DECODER.MPEG2`, one attempt/one success, and zero fallback.
Core JUnit and the clean 60-task Android build also pass. The stale active
entries for this work, already-commissioned Home/background recovery, and
already-commissioned paused frame step were removed from `TASKS.md`.

Audio capability reporting is now device-derived and truthful. MediaCodec
decoder support, the currently connected encoded AudioSink formats, and the
combined ability to play a codec are separate MCP fields; the app never calls
a compressed stream "passthrough" merely because of its MIME type. SageTV
`AUDIO_OUTPUTS`/`AUDIO_OUTPUT` remain empty and
`audioPassthroughAdvertised=false`. The generated AC3/EAC3/DTS matrix passes on
AFTMM/API-25 `.25`: AC3 and EAC3 play directly over Pull, while DTS has no
decoder or encoded-sink support and is excluded from direct Pull. Evidence is
`artifacts/firetv/audio-capability-media3-pull.json`; the exact installed APK
is SHA-256
`04f6d5106d3043c93163cd1af390027e3cfd8d072190aa869a17f07f26d90c1d`.

MPEG-2 interlace reporting is also complete without inventing a desktop-style
Android deinterlacer. A bounded H.262 header scanner observes sequence and
picture-coding extensions in ordinary Media3/legacy-Exo playback and the native
DVD extractor. It reports the bitstream classification and frame/field counts;
`codecDeinterlaceControl=not_exposed_by_android` remains explicit. Media3 and
legacy Exo hardware Pull both physically report
`interlaced_sequence_interlaced_frames` for the canonical 1080i fixture.
Native DVD passed with the same classification, 172 interlaced frame-picture
headers, hardware MTK MPEG-2, real-time A/V progress, and zero drops. Evidence
is `artifacts/firetv/codec-capability-media3-pull.json`,
`codec-capability-exoplayer-pull.json`, and `dvd-interlace-observation.json`.
The exact installed APK is SHA-256
`7bcf83da0967785cf283eb60c0a042fa7f27084cce6caaa7b597a5a628cd8a58`.

Bounded GFX image-allocation recovery is complete. A failed bitmap/texture
allocation may evict exactly one ordinary LRU UI image and retry once; surfaces
are never candidates, and a missing candidate or second `OutOfMemoryError` is
re-thrown. Invalid/overflowing image dimensions fail before allocation, and a
closed or superseded connection is never sent an unload event. MCP exposes
attempt/eviction/success/failure counters. Core, protocol, and MCP tests pass.
The exact APK `58d8affb6d41ce6e1c1166cf9daa9af8c156f16bc0e2473ad54c77bc4af37efd`
passed hardware Media3 Pull on `.25`; an in-session snapshot recorded 2,577
rendered MPEG-2 frames, zero drops, zero recovery counters, and no attached
Android caption overlay. Evidence is
`artifacts/firetv/20260905-222630_caption-media3-pull-legacy-callback.png`.

That extension now includes live Media3/legacy-Exo text-caption safe area,
size, and style. The latest physical `.25` Pull session applied a 25% bottom
safe area without reloading the decoder; MCP confirmed the new value, active
hardware MPEG-2, advancing playback, and zero dropped frames. DVD bitmap SPUs
remain intentionally unaffected. It also includes a compact, opt-in 30-second
process overlay with deterministic MCP show/hide control. Physical `.25`
evidence proves the overlay reports live player/buffer/subtitle state without
reloading the decoder, then removes itself while playback remains active. A
0-1500 ms HDMI settle setting is also live and bounded; it delays only a
client-local Media3 Surface refresh after a real display-mode change. The
player, datasource, server stream, DVD clock, audio, and play/pause intent are
retained; no server seek or generic local pause/resume is used. PCM
gain/downmix/audio delay is not exposed
until an output-sink implementation can prove correct clock accounting;
encoded AC-3 and unsupported outputs continue to fail closed.

## Standard takeover

Read `AGENTS.md`, `README.md`, `TASKS.md`, `WORKFLOW.md`, and playback diagnostics
before work. Use the root dev/update/package commands from any CWD. Update ZIPs
live in `artifacts/downloads`; install remains guarded to the Dev package.

This file records only the current takeover state. Historical release details
belong in `CHANGELOG.md`; open work belongs in `TASKS.md`.

## Current state

- Checkout version: `0.5.85`.
- Machine metadata: `release.properties` with `REQUIRES_BUILD=true`.
- Incremental update preflight/resume gate: PASS (19/19), including forced
  validate failure, first-incomplete-step resumption, strict Windows
  CRLF-equivalence acceptance, and real-content-drift rejection.
- Independent v0.5.75-to-v0.5.85 update workflow: PASS. The runner applied the
  changed-files package in a detached worktree, canonicalized 483 proven text
  equivalents, removed 355 obsolete paths, passed 438 project and 57 MCP tests,
  passed Core/validation, completed a clean 60-task APK build, and
  installed/launched the Dev package on `.25`. The unified container mounted
  the independent checkout itself; it did not test the canonical tree.
- Final canonical gate: 445 project tests, 57 MCP tests, and Core Gradle tests
  pass, including Git-less GitHub-bundle provenance coverage.
- Active source: `source/dev`.
- Frozen comparison source: `source/existing`.
- Protected external reference: `../../SageTV-MiniClient-Dev` (read-only).
- Normal development image/container:
  `opensagetv-vibe-build-env:u26-j11` / `opensagetv-vibe-dev`.
- Canonical artwork is owned by sibling `opensagetv-vibe-logo`; unified Android
  gates regenerate and SHA-verify its 29 launcher/banner/in-app/store resources before
  Gradle runs. `config/logo-assets.sha256` records installed provenance.
- Android production/development IDs are `opensagetv.vibe.miniclient` and
  `opensagetv.vibe.miniclient.debug`; both labels are `OpenSageTV Vibe`.
- Every active Java package and component is under the independent
  `opensagetv.vibe.miniclient.*` root. The established launcher class endings
  remain `android.phone.ServersActivity` and `android.tv.MainActivity`; only
  frozen `source/existing` retains `sagex.miniclient.*` for comparison.
- Fire TV builds now produce one client APK with both standard `LAUNCHER` and
  TV `LEANBACK_LAUNCHER` activities. The unsuccessful API-29 companion launcher
  and its install/launch automation were removed. Launcher resource precedence
  matches the working APK: the standard launcher inherits the application
  square/adaptive icon, while the Leanback activity explicitly declares the
  320x180 banner as icon, banner, and logo. No TV-module mipmap overrides
  remain. The protected JVL package was not modified.
- Fire OS launcher-cache recovery and package identity are characterized.
  Normal launcher-artwork gates use a clean Dev-package install and identify
  the package/component rather than inferring ownership from similar artwork.
  On the commissioned AFTMM/API-25 Fire OS launcher, a newly sideloaded package
  is intentionally presented with its square application icon even though its
  Leanback activity exposes the correct 320x180 banner. Rapid update installs
  can show a temporary blank tile; a version-code change followed by library
  refresh/restart restored the visible Vibe artwork. Evidence is
  `artifacts/firetv/appsgrid25-final-vibe-focus.png`; selecting the card launched
  `opensagetv.vibe.miniclient.android.tv.MainActivity`.
- The logo pipeline also emits validated Amazon 114x114/512x512 tablet icons,
  an opaque 1280x720 Fire TV app icon with critical artwork inside the 882x448
  safe area, and a title-free opaque 1920x1080 Fire TV background under the
  Android TV module's `store-assets/`. Three to ten reviewed 1920x1080 application
  screenshots and catalog publication remain release tasks.
- Default scripted test identity: `44:45:56:30:30:31` (`DEV001`).
- Default player remains Legacy ExoPlayer.
- Native packaging is now release-consistent: only paired ARMv7/ARM64 ABIs are
  shipped, each with the same five required libraries. The rebuilt pinned
  ExoPlayer FFmpeg AAR is 16 KB ELF-aligned (SHA-256
  `e9e34c833298c1177247b3f7cfef8e8be45035ff4f8076d667b8f5c9dc9c4b12`).
  The clean 60-task APK (`5538372d6431f6419a8d152d97072c7b6142cfb80b8bb569631be56cda07cdb4`)
  passed strict ABI/alignment inspection, APK ZIP alignment, in-place install,
  and hardware MPEG-2/AC3 Pull playback on `.25`.
- Connection/UI configuration and bounded keyboard telemetry have explicit
  owners. `ConnectionCapabilityProfile` owns negotiated capability/profile
  configuration, `UiSessionConfiguration` owns the immutable background
  policy loaded for one Activity session, and `UiKeyboardDebugState` owns only
  the debug IME observation/control state. Full host gates pass. Physical
  `.25` evidence is
  `artifacts/firetv/connection-ordering-20260905-082026.json`; the companion
  retained HOME/return lifecycle gate also passed exact connection identity,
  Surface recreation, auto-resume, manual-pause preservation, and teardown.
- Android event ownership is explicit and Otto-free. `VibeEventBus` dispatches
  the typed `VibeEventListener` callbacks synchronously on the posting thread
  in registration order; `docs/EVENT_OWNERSHIP.md` is the publisher/subscriber
  inventory. The full host gate and physical `.25` connection/lifecycle gates
  pass, and direct physical probes also proved the navigation overlay,
  video-info display/refresh, and native keyboard event. The commissioned APK
  SHA-256 is
  `96074525bc2136f107e1138881dc6008c9279665f98e820090ee0898344db16a`.
- Media3 supports conservative fast replacement of compatible completed Pull
  and SMB Direct files while retaining the player and Surface. Eligibility is
  fail-closed; Push, Fixed, live/growing, circular, HTTP/external-link, DVD,
  uninitialized, and legacy-unknown loads use the established full-load path.
  Setup or asynchronous failure makes exactly one full-load attempt. The
  `mcp-fast-switch-test` physically passed on `.25` over both SageTV Pull and
  SMB Direct with hardware MPEG-2 decode, one attempt/one rendered-first-frame
  success, and zero fallbacks. Missing-file injection proved fallback without
  process death. The exact final clean APK SHA-256 is
  `15a28b08c7c3febe6e08b7ff6519ca375110c30bb3e7282ed10b0ee67b7d7ae8`;
  it repeated both physical gates after an in-place install. Manual evidence is retained under
  `artifacts/firetv/fast-switch-*-20260905.txt`.
- Negotiated command 30 now provides modest native forward playback rates
  (0.5x-2x) and seek-based forward/reverse scan (4x-256x) for completed Pull
  and SMB Direct media on Media3, legacy ExoPlayer, and their GSY delegates.
  Unsupported transports and GSY/System do not advertise the feature. A
  three-second scan interval is required to let the physical SMB path recover
  a rendered frame between seeks. Hardware-only `.25` gates passed both Exo
  engines over Pull and SMB, both GSY delegates over Pull, pause/resume, STOP
  reset, and server-driven Smooth FF/REW. The commissioned test server runs
  `Sage.jar` SHA-256
  `32563ce0ae9e174b1212a9c7fa0bb4255483bc81410276ab78e3e2d311683c48`.
  The final host gate passed 423 project/static tests, 56 MCP tests, Core JUnit,
  validation, and a clean 60-task build. The exact clean APK SHA-256 is
  `8500c503b79869bfaafa3e012916972e95c4b75fb2d6a5b0ed050898b1d10a3f`.
  That exact artifact was installed in place on `.25` and repeated both the
  server-negotiated Media3 Pull gate and the Media3 SMB Direct gate using
  `OMX.MTK.VIDEO.DECODER.MPEG2`.
- GSY/System is physically resolved rather than left as an ambiguous backend.
  Its commissioning-only real `MediaDataSource` probe fails on AFTMM/API 25
  with Android MediaPlayer error `1/-2147483648`; the new bounded fail-safe
  falls back once to Media3 and restores hardware MPEG-2 A/V in the same
  SageTV session. Normal users never enter the failing System path because the
  probe gate defaults false. Debug state records `gsyResolvedEngine`,
  `gsySystemFallbackCount`, and `gsySystemFallbackReason`.
- Preferred audio/subtitle language and CEA-608/708 service selection are
  implemented under Audio and Caption Track Settings. SageTV/STV remains the
  caption Off/On authority; there is deliberately no Android enable gate.
- Unmodified servers that do not publish `VIDEO_CC_STATE` are supported by the
  long-press CC submenu (`STV`, `Off`, `CC1`, `CC2`). A renderer-state defect
  that required restarting video after Off-to-CC1 was fixed in both Media3 and
  legacy ExoPlayer. The deterministic caption fixture passed same-session
  Off-to-On without restart on both engines; visual evidence is
  `artifacts/firetv/20260904-235030_caption-media3-dynamic-visible.png` and
  `artifacts/firetv/20260904-235234_caption-exoplayer-dynamic-visible.png`.
  The follow-up eight-second Media3 continuity gate produced 183 more non-empty
  cues with a longest progress gap of 1.691 seconds; its final screenshot is
  `artifacts/firetv/20260904-235525_caption-media3-dynamic-visible.png`.
- The bounded MediaCodec capability profile is complete. It inventories the
  platform decoders on request and both primary selectors use its hardware/
  software classification; it deliberately does not install a continuous
  analytics listener or infer deinterlace quality Android does not report.
- The generated Kodi-derived codec matrix is complete on the normal
  AFTMM/API-25 device with Media3 Pull and hardware video decoding. Eleven
  positive formats/profile/bitstream cases pass, including a real H.264
  resolution transition; malformed H.264 startup is contained. The separate
  MPEG-4 Part 2 fault plan uses eight reordered MP4 packets with independent
  PTS/DTS and passes controlled PTS-unset/DTS-fallback injection without a
  production rewrite. Canonical evidence is
  `artifacts/firetv/kodi-codec-matrix-media3-pull-aftmm.json`.
- The GitHub publication dependency/license gate is complete.
  `third_party/RUNTIME_DEPENDENCIES.csv` fail-closed maps all 122 selected
  runtime coordinates to packaged notices and full license texts;
  `third_party/source-offers/README.md` records exact native AAR hashes and
  pinned LGPL rebuild sources. The project-owned Apache-2.0 bounded circular
  buffer replaces the former Ostermiller GPL runtime dependency. The inspected
  review bundle contains hash-verified source, APK, manifests, notices,
  licenses, and source-offer material. Its clean APK also passed physical
  Media3 hardware Push playback on the normal AFTMM/API-25 device.
- Commissioned SageTV test target: Unraid container
  `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`.
- The Android 11/API-30 Amazon AFTKRT at `192.168.10.29` is now a commissioned
  compatibility device for launcher, Settings, discovery, and connection
  lifecycle checks. Fire OS's unattached-decor `WindowInsetsController` crash,
  early error-view null dereference, restart-after-shutdown discovery rejection,
  MCP focused-window omission, and background-receiver Activity-launch failure
  are fixed. Three Settings reopen cycles, discovered `.175`/`.232` servers,
  GDX connection, MCP smoke, and OpenGL hardware Media3 Pull with pause/play,
  HOME reconnect and teardown pass without a fatal exception. Evidence includes
  `artifacts/firetv/api30-mcp-smoke.log` and
  `artifacts/firetv/connection-ordering-20260904-120843.json`. Its retained-
  session matrix passed on incremental APK SHA-256
  `3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`;
  Pro testing is paused at the user's request after that PASS.
  The device is currently ADB-authorized. Its independent Vibe launcher/icon
  and STV-controlled CEA-608 caption fixes are owner-verified working. Native
  hardware DVD cadence is also commissioned: the exact clean `0.5.85` APK held
  1.0217x real time on `.29` and 1.0131x on `.25`, with zero drops, invalid
  release intervals, or long gaps, and every pause/play, 2x FF/RW, and chapter
  recovery passed. The authored DVD
  menu/audio/subtitle/chapter regression also passed.
- Normal ongoing physical automation should return to the configured
  `192.168.10.25` Fire TV after the API-30 compatibility gate; use `.29` only
  when explicitly repeating the newer-platform check.
- The current logo-integrated debug client and compatibility launcher are
  installed on that normal AFTMM/API-25 device. Saved development preferences
  were restored after the client identity migration. The client reports
  `1.14.0-DEV-DEBUG`/code `2101102`, resumes `MainActivity`, and has SHA-256
  `269b4425218d055ac26c175a521de7b347ddb18e2d429c325917b090b4d4cf92`.
  The API-29 launcher SHA-256 is
  `c0f10f80b7dd09a7cafeb0daa03572fd1870c7dce9b34999c7544c1b73d9db36`.
- The deterministic regular-video fixture now carries forward the authored-DVD
  synchronization contract: burned PTS/frame, synchronized visual/audio pulses,
  dual AC-3, CEA-608/708, and a Comskip sidecar. Unified image contract
  `u26-j11-release-v7` was rebuilt as image `27acc132879e...`; its single
  reusable container generated and probed a real 1920x1080i sample and passed
  the 14 focused regular/authored fixture tests.
- Home/background recovery is opt-in and implemented behind a dedicated Options
  submenu. Media3 hardware Pull passes same-session recovery on both the API-25
  and API-30 Fire TVs with auto-resume on and off, preserves a user pause,
  honors the configured timeout, and tears down explicitly. Automatic PLAY is
  emitted only for media the app itself paused while entering background.
  Legacy Exo now binds its `SurfaceView` instead of retaining a released raw
  `Surface`; two hardware Pull HOME/return cycles pass with advancing A/V,
  user-pause preservation, and teardown on API 25. API-30 Fire OS's early
  MediaSession pause is coalesced into application ownership, and resume repaint
  is routed through the ordered event worker; the former main-thread socket
  write caused `NetworkOnMainThreadException`, duplicate reconnects, and stale
  playback teardown. The tested incremental API-25/API-30 APK SHA-256 is
  `3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`.
  The final clean-build artifact SHA-256 is
  `c18cfdee04868b250b99381f85d45c00b6c5e2bd8fbf1e499fbe5fa27a78351b`
  and repeated the exact-session auto-resume, user-pause, replay, and teardown
  gate on API 25.
- Remote DVD playback is commissioned and its active implementation task is
  closed. Native remains the stable default; Hybrid/MIM remains opt-in and
  experimental, but its authored-fixture title/control matrix and explicit MIM
  main-feature policy physically pass on the commissioned Fire TV. Blu-ray/
  BDMV remains separately `SKIPPED` until a valid physical fixture exists.
  Updated Core selects `MiniDVDPlayer` when this client advertises
  `DVD_REMOTE_NAV`; Android implements commands 32-37 and a DVD-only Media3
  MPEG-PS/AC-3 path. DISC policy/menu/preview/fallback preferences are queried
  by Core for each client. Unsupported Hybrid/MIM fails closed when requested,
  or falls back to Native without corrupting the session when allowed.
- Native MPEG-2 DVD on the API-30 AFTKRT now carries a Kodi-derived guarded
  missing-PTS repair. At the identical 61.795-second input point the repair
  reduced recurring frame-release gaps from 31 to 10 while both sides used
  `OMX.MTK.VIDEO.DECODER.MPEG2`, consumed 47,417,344 Push bytes, and reported
  no player error. `Auto` targets MediaTek OMX/Codec2 and NVIDIA OMX, and the
  Disc submenu plus MCP expose `On`/`Off` for A/B testing. The final clean
  workflow APK installed on `.29` has SHA-256
  `d8bec749d33eb5331a5d046ac5638297a52266780808400d828b714efa6c1bb0`
  and passed exact-path native hardware start, pause/play recovery, advancing
  A/V, and STOP teardown.
  Evidence: `artifacts/firetv/dvd-firetv-pro-29-repair-off-state.json`,
  `artifacts/firetv/dvd-firetv-pro-29-repair-on-state.json`, and
  `artifacts/firetv/dvd-firetv-pro-29-native-kodi-rules-controls.json`, plus
  `artifacts/firetv/dvd-firetv-pro-29-kodi-rules-clean-build.json` for the
  exact clean artifact.
- A true mid-session MIM failure after Hybrid negotiation now falls back to
  native hardware MPEG-2 with advancing A/V and no player error. Android
  exposes `discMimRuntimeFallback=true`, retains Core's fallback URL, and shows
  a bounded user message. Evidence is
  `artifacts/test-results/vibe-authored-dvd-mim-runtime-native-fallback.json`.
  The explicit `mim_main_feature` authored-disc policy gate also passes with
  hardware startup, command recovery, and STOP/teardown on `mcp-disc-test` with
  evidence `artifacts/test-results/vibe-authored-dvd-mim-main-feature-policy-gate-attempt2.json`.
  Test-server `ffmpeg` was restored executable at SHA-256
  `8d031f6cad22867ca2b912a73cdb8695dcbc11247d10c2461462594a26b83e9c`.
- The deterministic authored fixture exposes synchronization in the picture:
  every English/Spanish normal SPU cue prints its cue number, authored PTS,
  nearest video frame, and chapter against a video-burned live PTS/frame clock.
  DVD `SetSTN` values 64/65 enable English/Spanish and 63 disables normal
  subtitles. Strict physical English and Spanish selector tests pass after the
  fixture correction; `vibe-authored-dvd-v6-spanish-subtitle-title-retry.json`
  and screenshot `20260901-134510_screen.png` are the current sync evidence.
- The same installed APK passes the preserved pre-DISC Core binary with normal
  Pull, SMB Direct, Fixed/MIM, Native DISC, and safe Hybrid-to-Native behavior.
  Old Core cannot query the new per-client DISC policy, so the client now
  exposes `discOldServerNativeFallback` plus a concise compatibility reason
  instead of claiming Hybrid. Updated Core explicit Hybrid/no-fallback fails
  closed. The updated test JAR was restored afterward at SHA-256
  `89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`.
- Backward-compatible optional-extension fallback is now a permanent
  `AGENTS.md` playback rule rather than an open one-time task. The physical
  preserved/current Core, missing/old/current MIM, FFmpeg runtime failure,
  explicit fail-closed, Native/Pull recovery, and SMB Auto-fallback evidence
  must be rerun whenever their boundaries change.
- Missing MIM and preserved MIM 0.4.5 fault injection both prevent explicit
  Fixed startup without crashing SageTV; ordinary Pull remains recoverable.
  The exact current MIM 0.4.7 `ffmpeg`, `ffmpeg_MIM`, `ffmpeg.real`, and INI were
  restored from the test backup and all four SHA-256 checks pass.
- Hardware embedded preview now physically passes Legacy Exo Push, Pull, SMB
  Direct, Fixed/MIM and Media3 Native DISC. The evidence includes a screenshot
  after a bounded settle and a second A/V state verification, so a green/blank
  Surface cannot produce a false pass. See the `embedded-preview-*` JSON and
  PNG files under `artifacts/firetv`.
- The Apache-2.0 DVD Presentation Engine v2.1 handoff archive was verified
  source-by-source, test-by-test, and document-by-document. Vibe imports only
  its bounded platform-neutral SPU/audio presentation primitives and wraps
  them with the existing Android overlay/STV policy. The archive's complete
  bridge, input/drain gate, timestamp rewriter, Surface view, and forced-only
  stream selection were intentionally rejected because they overlap the
  physically proven Vibe path or conflict with STV subtitle-off authority.
  Imported self-tests, a 5,000-fragment malformed-SPU stress test, focused
  static tests, Core JUnit, and debug APK compilation pass.
  `docs/DISC_PLAYBACK_DISCOVERY.md` contains the final component-level merge
  table, including every intentionally retained Vibe equivalent and the reason
  it is safer than replacing the physically proven path.
- Physical hardware decoding now crosses the 159,199,232-byte Scooby title
  boundary without the former audio-stop/READY-BUFFERING deadlock, then renders
  the authored root menu and buttons with continuing audio. Generation-scoped
  Push EOF is driven by MiniDVDPlayer's actual `0x100` drain protocol, and only
  complete one-picture menu sequences receive bounded 500 ms readiness samples.
  The menu remained READY/playing beyond 130 seconds and drained normally with
  no player error. Menu-less ALADDIN also has earlier bounded A/V evidence, but
  these observations do not commission the complete DVD workflow. Re-run every
  remaining gate listed in `TASKS.md`. Do not replace the DVD extractor
  with stock Media3 `PsExtractor`: it merges DVD private AC-3 substreams and
  crashes this Fire TV's Dolby decoder. No BDMV fixture was found, so physical
  Blu-ray commissioning remains SKIPPED rather than reported as passing.
- A read-only Unraid scan found 52 `VIDEO_TS` directories: 51 populated DVD
  structures and one empty invalid structure. No `BDMV` directory was found.
  Use the entire indexed set for bounded start/STOP/crash screening and retain
  the deeper representative menu/stream/chapter matrix in `TASKS.md`.
- The post-presentation-engine representative hardware matrix passes menu-less
  ALADDIN, authored LEGO, Polish/PAL, and the largest five-IFO/fifteen-VOB
  fixture. The empty `ROGUE_ONE` structure fails startup safely with no Android
  crash signature. Independent HDMI evidence captures the authored Scooby root
  menu at 1920x1080/~29.75 fps with stereo audio; see
  `artifacts/firetv/dvd-presentation-v21-hdmi-av-15s.mp4` and
  `artifacts/firetv/dvd-presentation-v21-hdmi-frame.png`.
- The first complete populated-disc sweep found three video-only starts. Raw
  telemetry proved AC-3 samples were advancing but Media3 had no audio
  TrackGroup: SPU-only `private_stream_1` had caused premature `endTracks()`.
  Track discovery now waits for a physical AC-3 substream, and retained audio
  selection is committed on the player looper only after the replacement group
  exists. All three affected titles pass the focused hardware regression in
  `artifacts/firetv/dvd-late-ac3-three-disc-regression.json`.
- The corrected extractor then passed the final bounded inventory sweep on all
  51 populated Unraid DVD structures. The empty ROGUE_ONE directory is an
  expected safe failure; no BDMV exists, so Blu-ray remains SKIPPED. See
  `artifacts/firetv/dvd-presentation-v21-all-unraid-dvds-final.json` and
  `artifacts/firetv/dvd-empty-structure-safe-failure.json`.
- The exact final installed APK/current-Core rerun also passes all 51 populated
  structures in `dvd-all-unraid-final-latest-apk-current-core.json`; the empty
  structure fails safely in
  `dvd-empty-structure-final-latest-apk-current-core.json`. Fresh HDMI evidence
  is `dvd-final-current-apk-current-core-hdmi-18s.avi` plus its ffprobe JSON,
  frame, frame hashes and volume analysis. It contains 1920x1080 moving video,
  stereo 44.1 kHz audio, 29 sampled unique frames, and non-silent audio at
  -22.5 dB mean / -3.7 dB maximum.
- The strict transport gate found that Core passed a negative time to its DVD
  VM when Skip Back crossed the beginning of a title. Vibe Core now clamps disc
  seek time to zero before `MiniDVDPlayer.seek()`. Core also selects the longest
  authored VM title for skip-menu startup, treats skip-preview as a root-menu
  jump, and enforces negotiated fail-closed/fallback policy. The latest
  rebuilt/deployed test server `Sage.jar` SHA-256 is
  `89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`;
  the prior JAR is retained on Unraid as
  `Sage.jar.backup-before-disc-seek-20260901`. The exact strict sequence now
  passes, as do five stop/restart cycles and the ALADDIN/RAYA/SOUL/
  SAVING_PRIVATE_RYAN/F9/POLAR_EXPRESS representative control matrix.
- Physical policy evidence is retained in
  `dvd-hybrid-no-mim-no-fallback-safe-failure.json`,
  `dvd-hybrid-no-mim-native-fallback.json`, and
  `dvd-scooby-native-final-strict.json`. Direct legacy Exo passed only two of
  six representative discs, so DVD now resolves to Media3 with a bounded user
  message; this is a safe compatibility fallback, not a legacy-Exo PASS.
- Final independent HDMI evidence is
  `artifacts/firetv/dvd-presentation-v21-final-hdmi-av-18s.avi`: ffprobe finds
  1920x1080 MJPEG and stereo 44.1 kHz PCM, and
  `dvd-presentation-v21-final-hdmi-frame.png` visibly confirms clean title
  video. `capture_hdmi_validation.cmd` makes this check repeatable without
  depending on PowerShell script execution policy.
- The final clean-source APK after policy/backend integration is
  `artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`, SHA-256
  `d0735a0da6e6413d13194daafda108e066cef2efee21ddbefb441080daffc110`.
  It passed 365 project/static tests, 53 MCP tests, validation, a clean Gradle
  build, exact package install, the complete strict Native DVD command matrix,
  and MCP discovery. Independent evidence
  `dvd-clean-apk-final-hdmi-av.avi` contains 1920x1080 MJPEG plus stereo 44.1
  kHz PCM; `dvd-clean-apk-final-hdmi-frame.png` visibly confirms clean title
  rendering. The final strict MCP record is
  `dvd-scooby-clean-apk-native-final-strict.json`.
- Current headless gate: PASS on 2026-08-31 with 298 project/static tests, 49
  MCP tests, Core JUnit, full validation, clean debug APK, debug and release-
  candidate AABs, lint/signing, bundletool validation, and universal APK-set
  generation. The current transport-ownership clean APK is
  `cb57f588955b07bd18a310e8de8ef20a151861f48a3ca568b50d3cd548e733e4`;
  the 289/47/Core/validator headless gate passes against that source tree. Its
  exact-install physical ordering gate passes as
  `artifacts/firetv/connection-ordering-20260831-005301.json`, including A/V
  recovery, HOME teardown, newer-generation reconnect, and explicit close.
  The preceding lifecycle clean artifact was installed and passed physical
  ordering and teardown. The preceding backend-neutral clean APK was installed and
  passed a final Media3 hardware Pull smoke with
  advancing 1080i MPEG-2/AC-3 output, captions, full-screen 1920x1080 surface,
  typed Pull counters, and no player error.
- The current preferred-track clean APK is
  `b9ee3c91ca3ad74b15bfe74efe3f6592aebbf885af9b458993f8eb99fd647841`.
  Its exact in-place install passed Media3 hardware Pull with STV-owned
  CEA-708 Service 1, 43 rendered cue updates, and 498 ms caption/timeline
  drift. Evidence is
  `artifacts/firetv/20260831-022831_caption-media3-pull-visible.png`.
- The current capability-profile clean APK is
  `ad7e89e3ec92c9116b61e39ce887706069edc6c820e8cfc29d2436172d64f005`.
  Its exact in-place install passed hardware Pull on Media3 and legacy Exo;
  both selected `OMX.MTK.VIDEO.DECODER.MPEG2` with no player error. Evidence
  is `artifacts/firetv/codec-capability-media3-pull.json` and
  `artifacts/firetv/codec-capability-exoplayer-pull.json`.

Connection startup now has bounded payload-free diagnostics and a lifecycle-
owned single-thread executor/Future rather than an unretained activity thread.
Pause/destroy invalidates the request generation, cancels the Future, and
rejects/closes a late exact connection before it can publish stale UI state.
The post-change Amazon AFTMM gate passed media-before-GFX ordering, protocol
replies, FIFO drain, all-worker HOME teardown, newer-generation reconnect,
three hardware Pull replays, surface recreation, and final no-process
teardown. Evidence is
`artifacts/firetv/connection-ordering-20260830-230236.json`. Connection-owned
worker/socket state, bounded idempotent close, explicit Media readiness, the
single event FIFO, one-frame GFX handoff, and cancellable named remote file
transfers now pass that same gate. Reconnect eligibility/event suppression and
replaceable GFX/event protocol streams are also extracted and passed the
physical gate using the canonical captioned/comskip fixture; evidence is
`artifacts/firetv/connection-ordering-20260830-231530.json`. Split `GFXCMD2`
then completed behind a stable family map. Lifecycle/frame, drawing,
image/cache, font, surface/video, and transform/batch handlers retain one
serial dispatcher and shared handle namespace. The exact split passed OpenGL
on the final clean APK as `connection-ordering-20260830-235310.json` and GDX as
`connection-ordering-20260830-235518.json`, both using the canonical
captioned/comskip fixture. Renderer, keyboard, player scheduling, Push, SMB,
and overlay ownership were subsequently completed behind the same gates.
Further large-class splitting is maintainability work and must not be mixed
with unrelated player behavior.

OpenGL and GDX renderer initialization now share `RendererReadinessGate` rather
than polling a plain boolean every 100 ms. Resize marks the one-shot gate ready;
close/deinit cancels it and releases the GFX initializer; interrupt status is
preserved. The post-change physical gates pass as
`connection-ordering-20260830-235947.json` (OpenGL) and
`connection-ordering-20260831-000156.json` (GDX).

Delayed keyboard work is now held by an explicit main-looper owner and removed
on pause/destroy. Media3 and legacy Exo progress plus seek-recovery scheduling
share one explicit main-looper handler per player; release/replacement removes
  the progress callback. Both hardware Pull lifecycle gates pass with the
captioned fixture after the change. The final OpenGL connection run passes as
`connection-ordering-20260831-002155.json` from the exact clean-installed APK,
and direct preference inspection on
the commissioned Fire TV confirms `use_opengl_ui=true`.

Push back-pressure is now notification-driven rather than sleep-polled. Core
open/data/EOS/close/release signals wake Media3, legacy Exo, GSY-adapter, and
IJK waiters; IJK additionally has release-aware datasource creation and a
correct unconditional zero-length seek probe. Hardware Push playback passes on
Media3, legacy Exo, and IJK; the IJK gate includes exact-file startup,
pause/play recovery, no crash signature, and clean exit. SMB cleanup is owned
by one retained idempotent per-session executor. Post-change Pull/SMB seek A/B
passes on Media3 at 415/772 ms and legacy Exo at 333/1013 ms, with SMB media
bytes at 92,425,984/86,396,672 and MediaServer media-read bytes at zero. Do not
remove `SimplePullDataSource`'s bounded 100 ms remote SIZE retry unless the
SageTV protocol gains an explicit growing-file notification; it is the only
intentional production transport sleep left by this pass.

The serialized `PlaybackSessionController` now owns monotonic load/session and
operation generations. Base, Media3, and legacy ExoPlayer reject delayed
listener, progress, seek-recovery, queued UI, and surface work from replaced or
stopped players. The reviewed `BaseMediaPlayerImpl` SHA-256, including its
bounded DVD presentation diagnostics accessor, is
`63fc269f13e7e5a75f4e55b8e3b39016e9056ffc6c71baf117e6c8b596ebc9dd`.
Hardware physical gates pass on both Exo backends for Pull lifecycle/surface
replacement and repeated loads, Pull and Push FF/REW with caption retention,
clean exact EOF, and a real 2.1 to 5.1 live-TV transition. Evidence is under
`artifacts/firetv/session-controller-*-20260830.log`; the accompanying caption
screenshots begin `20260830-20` and end `caption-*-visible.png`.

The proven Media3/legacy-Exo common behavior is now expressed through typed
backend-neutral components rather than duplicated policy or debug reflection:
an immutable `PlayerRuntimeConfig`, pure-Core `PlaybackSyncPointPolicy`,
generation-safe `PullSeekRecoveryMonitor`, `PlaybackHealthSnapshot`, and
`PlaybackDataSourceTelemetry`. Player creation, decoder integration, and
backend-specific reprepare actions remain separate. Hardware device gates
passed Media3 and legacy Exo Pull frame/seek behavior plus Media3 Push
rejection. Pull/SMB Direct A/B passed on both backends; SMB source counters
showed 94,508,032/95,556,608 SMB bytes, zero MediaServer media bytes, and the
expected 23-byte shadow reply. This closes the narrow backend-neutral
configuration/seek/recovery/health/telemetry extraction; do not broaden it by
merging the player implementations.

MiniPlayer protocol command 28 now has a real dispatcher, explicit result,
and backend contract. Media3, legacy ExoPlayer, and IJK support bounded signed
frame stepping only while paused on a random-access Pull/SMB source; Push,
playing, stale-session, zero-count, and GSY System cases fail safely. The
repeatable `mcp-frame-step-test` physically passed Media3 and legacy ExoPlayer
hardware Pull with a 33 ms advance, one newly rendered frame, retained pause,
and exact command/invoke/return events. It also passed playing and physical
Media3 Push rejection. The clean installed APK repeated the Media3 gate.

The canonical v0.5.85 fixture is
`VibeSeekTest-1080i-MPEG2-AC3-CC.ts`: 900 seconds of 1080i MPEG-2, dual AC-3,
burned-in time/frame markers, actual in-band ATSC A/53 GA94 CEA-608 CC1 and
CEA-708 Service 1 payloads every 0.5 seconds, plus a matching Comskip `.edl`.
The generator verified 26,971 final pictures and exactly 26,971 GA94 payloads;
the owner also user-verified working captions on 2026-08-30. Automated
Android/STV transport and retention tests remain distinct physical gates. The
commissioned canonical file now uses CEA-608 row 14 and has SHA-256
`b54b5475e4edce1dd248d263e04e54721a01d3dc4ab5b7f41ec125dc692a473e`.
The preferred-track physical gate passed on Amazon AFTMM/API 25 with hardware
Pull: Media3 rendered explicit CEA-708 Service 1 and legacy Exo2 rendered
explicit CEA-608 CC1 while the STV remained authoritative. Screenshots are
`artifacts/firetv/20260831-021518_caption-media3-pull-visible.png` and
`artifacts/firetv/20260831-021650_caption-exoplayer-pull-visible.png`. The
settings page itself is captured as
`artifacts/firetv/track-selection-settings.png`.
Hardware Fixed/MIM and Push screenshot gates pass at
`artifacts/firetv/20260830-190054_caption-media3-fixed-visible.png` and
`artifacts/firetv/20260830-190557_caption-media3-push-visible.png`: the stable
middle cue is within one second of the STV timeline and clears the time bar.
The test-only PAUSE keepalive now provides a default 12-second inspection
window without changing normal application/STV timeout behavior. The stable
middle cue is authoritative and the automated cadence tolerance is one second.
The same harness now accepts repeatable server-owned seeks. Fixed/MIM FF, REW,
and FF_2 recovered A/V and stable captions in 328-331 ms, then passed a 4 ms
cadence-drift gate; post-seek evidence is
`artifacts/firetv/20260830-191129_caption-media3-fixed-visible.png`.
Hardware Push passes the same sequence with its automatic 3,000 ms
replacement-stream settle; evidence
`artifacts/firetv/20260830-191636_caption-media3-push-visible.png` shows stable
cue 3:37.0 versus STV 3:38 and 4 ms cadence drift. Do not reduce Push to the
failed 300 ms window, which races the server's second FLUSH.

The unknown-duration buffered live-edge fallback is commissioned on both Exo
backends. On real channel 2.1 hardware Pull, a request to 86,400,000 ms emitted
`seek_clamped_live_edge`: Media3 reached 9,174 ms and recovered A/V in 9,491
ms, its independent repeat reached 5,734 ms and recovered in 6,262 ms, and
legacy ExoPlayer reached 8,643 ms and recovered in 9,222 ms. Core/static tests
prove completed media and unknown/no-buffer cases retain old behavior. The
installed APK SHA-256 is
`4f3cd3653ed7c0cf62b0ce692da88762cacf67563de7758e4f114b9089044983`.
The same seek-policy build uses a five-second completed-tail guard. A 500 ms
guard physically landed inside the final MPEG-2 GOP and caused repeated
Media3 timeout/recovery cycles. With five seconds, both Media3 and legacy
ExoPlayer hardware Pull reach the canonical fixture's natural 899,959 ms EOF,
report a clean ended state, show no delete prompt, and keep the process alive.
Evidence is `artifacts/firetv/eof-media3-tail5s-20260830.log` and
`artifacts/firetv/eof-exoplayer-tail5s-20260830.log`.

The v0.5.84 fullscreen race is closed in both runtime behavior and test
instrumentation. The Android compatibility fallback waits 2.5 seconds and
checks SageTV `MENU_HINT` before sending the toggle-style `TV` command; it
cannot undo an existing OSD or act through a popup. Debug status version 18
reports SageTV's requested video destination and UI dimensions, and MCP no
longer mistakes the full-size Android SurfaceView for full-screen video. The
physical pre-fix screenshot shows Main Menu plus preview; the post-fix initial
and three repeated starts all report `MediaPlayer OSD`, destination
`0,0,1920,1080`, advancing A/V, and no crash. The installed APK SHA-256 is
`2e6ceab87dfd17d032e10701131b0db167587a4d6638b11634c8548a6faf3451`.

GSY's unconditional Media3 Cast/Session dependencies are now excluded. The
previously selected non-Cast AndroidX/Kotlin versions are pinned explicitly so
this removal cannot silently downgrade the tested graph. Cast, MediaRouter,
DataTransport, `BluetoothValidationActivity`, and ProfileInstaller initializer/
receiver entries are absent from debug and release-candidate manifests. The
release APK inspector rejects their return. Debug/release bundles and the
release-candidate APK pass their content gates; the candidate remains
non-publishable only because the approved production identity/key are absent.
The physical API-25 Fire TV passes generated-fixture hardware Pull start,
fullscreen, ordinary FF/REW, pause, and resume with APK SHA-256
`7b90ccd6d3574213340b28827e3512f9c29721c60d55be8ef5f73635fc5efbf4`.
A 16-command rapid mixed FF/REW stress produced one intermittent active-audio/
playback-state failure; its diagnostics remain under
`artifacts/firetv/20260830-080924_mcp_rapid_output_health_*`. The harness now
supports `--rapid-only`, `--rapid-repeats`, and `--rapid-reset-ms`. Subsequent
zero-delay testing passed three fixed-position repeats on each of Media3 Pull,
legacy Exo Pull, Media3 SMB Direct, and legacy Exo SMB Direct using
`VibeSeekTest-1080i-MPEG2-AC3.ts`. Pull recovered in 0.906-1.662 seconds; SMB
Direct recovered in 2.541-3.636 seconds. Treat this gate as passing but retain
the old artifact for future soak comparison.

The generated fixture's 120-180 second marker now passes the repeatable
server-owned Comskip gate on Media3 and legacy ExoPlayer over Pull and SMB
Direct. All four runs observed SageTV requesting exactly 180000 ms, then
recovered fullscreen hardware-decoded A/V. First source-read/first-frame timing
was 625/799 ms (Media3 SMB), 539/728 ms (legacy Exo SMB), 911/1112 ms (Media3
Pull), and 2483/2560 ms (legacy Exo Pull).

The repository currently has reviewed and host-validated API 36,
FileProvider/logging, launcher branding, deterministic launch, update workflow,
dependency-locking, and documentation changes. Commissioned exact-path
prerecorded playback and background/reconnect/repeated-start/teardown are now
proven. Completed-file exact EOF and genuine growing live-TV also pass on
Media3 and legacy ExoPlayer. Ten authoritative alternating 2.1/5.1 changes per
backend passed with advancing A/V; the final exported server image then passed
two more transitions per backend. GSY Auto/Media3/legacy Exo passed four each,
and a deliberately last GSY System run passed two without crashing. Explicit
HOME surface release/hide and foreground surface recreation now pass on both
Exo backends. Audio-focus loss/recovery now passes on both Exo backends;
real CEA-608/708 caption discovery, selection, visible rendering, disable, and
crash checks now pass in Push/Dynamic and Pull on both Exo backends. Caption
On/Off is owned by the SageTV STV; there is no separate Android navigation
gate. Core sends `VIDEO_CC_STATE`, and the default physical caption test proves
visible captions without calling Android's debug track selector. Real
Meet-the-Press `.edl` marker coverage now passes over SMB Direct: Media3 RIGHT
lands at 985.220 seconds and legacy Exo LEFT lands within 22 ms of that prior
marker endpoint, with a new server seek sequence and sustained A/V.

Fixed/FFmpeg-MIM commissioning uses the server's Intel VAAPI backend and
retains machine-readable evidence in `artifacts/firetv/fixed-mim-*`. Legacy
Exo hardware/software/fallback and Media3 hardware/software/fallback pass.
GSY Auto/Media3/legacy Exo also pass; GSY System is a bounded Media3 safety
fallback rather than native System evidence. A deliberately missing render
device selected reported server software/libx264 and passed Media3 completed/
live playback. Intel Fixed is supported as an opt-in configuration; MIM
remains disabled by default and AMD/NVIDIA remain physical-hardware skips.

The latest exact 0.4.7 hardware-only all-selection rerun is
`artifacts/firetv/fixed-mim-20260830_180555/FIXED_MIM_MATRIX.json`. Legacy Exo,
Media3, IJK, GSY Auto, GSY Media3, GSY legacy Exo, and the bounded GSY System
safe delegate passed prerecorded controls and real 2.1/5.1 live gates. Every
job reported fresh Intel VAAPI/`h264_vaapi`, stopped state, matching input, and
zero orphans. Do not rerun Android software-decoder cases unless the owner
changes the current hardware-only commissioning scope.

The final supported Media3 hardware rerun used the exact deployed FFmpeg/MIM
0.4.6 artifact and is retained at
`artifacts/firetv/fixed-mim-20260829_234208/FIXED_MIM_MATRIX.json`. Both
prerecorded and four-change live gates passed. Status evidence reports fresh
`vaapi`/`h264_vaapi` jobs, `hardwareEncode=true`, matching source input,
`state=stopped`, and `activeJobs=[]` after each gate. Temporary SSH test keys
were removed from Unraid after evidence collection.

On 2026-08-30 the active appdata wrapper was found to have drifted back to MIM
0.4.5. Exact 0.4.6 Linux output was restored after backing up the mounted files
under `.commissioning-backups/mim-before-046-20260830`. With that verified
artifact, legacy Exo software decode passed the full generated prerecorded
Fixed controls/restarts. IJK passed generated prerecorded controls plus four
2.1/5.1 live changes in three consecutive runs:
`fixed-mim-20260830_085919`, `fixed-mim-20260830_090300`, and
`fixed-mim-20260830_090610`. Status was fresh VAAPI/h264_vaapi with matching
input and zero orphan jobs. The matrix's new `--caption-gate skip` is used only
because the synthetic fixture has track metadata but no non-empty cues; real
caption retention remains established by the captioned recording.

The restart persistence defect is now closed. The isolated container's active
`/opt/sagetv/server/ffmpeg`, appdata wrapper, and image-pinned `ffmpeg_MIM`
all report 0.4.6 after restart. The wrapper SHA-256 is
`668c056eb05c77e2ad3303ac1b351103f7367a93a44904e7b430b971da724f80`;
`ffmpeg.real` is
`fc36882f4c0bfd94910f15cc285c0daf29b31b11598bc3e2df9f089fb3578519`.
The matching container build context was refreshed through
`stage-artifacts.sh`. The post-restart Exo software/Intel VAAPI Fixed run is
`artifacts/firetv/fixed-mim-20260830_132155/FIXED_MIM_MATRIX.json` and passes.

Exact-path events 230/232 now enter the Core's canonical `MediaPlayer OSD`
directly after `Watch`; event 232 additionally queues the first-segment time.
MCP waits for this asynchronous STV transition before attempting its stock-
server `TV` fallback and requires 750 ms of stable fullscreen state. This
removes the observed OSD-to-Main-Menu double-toggle. Physical evidence is
`artifacts/firetv/fullscreen-regression-20260830/passive-grace-stable-2.png`.
The currently deployed isolated-server `Sage.jar` SHA-256 is
`89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`.

SMB Direct/Shadow Pull is implemented for Media3 and legacy ExoPlayer. It keeps
normal MiniPlayer control and codec declarations, negotiates ordinary Pull,
and replaces only the byte source with Apache-2.0 SMBJ. The shadow port-7818
session sends OPEN/SIZE/CLOSE plus one counted byte at the same position for
each non-sequential SMB access; physical A/B testing proved OPEN/SIZE alone and
a byte-zero-only probe do not keep stock SageTV's STV seek/Comskip position in
sync. Full generated-fixture matrices, explicit failure, Auto fallback,
60-second playback, repeated start/stop, cleanup, and real marker tests pass.
Named SMB configuration profiles are implemented with a checksummed schema,
atomic rename, credential exclusion, overwrite confirmation, and separate
duplicate-Client-ID confirmation. Physical commissioning against
`smb://192.168.10.175/sagemedia/config/` passed save/list/load/overwrite/delete,
valid-checksum schema and credential rejection, partial/corrupt file rejection,
missing-share failure, concurrent-write exclusion, credential redaction, UI
selection, and the two-step warned Client-ID replacement path. The device was
left on `DEV001`; the remote test profiles were removed.

Large-jump Pull/SMB timing and byte-ownership A/B tests use the deterministic
`VibeSeekTest-1080i-MPEG2-AC3.ts` fixture on the same Fire TV, decoder, and
player backend. Legacy Exo and Media3 both pass. The evidence correlates the
exact server seek command, first physical source read, first observed decoder
input (100 ms observation precision), exact rendered-frame callback, and
sustained recovery. Pull reports positive SageTV MediaServer media bytes; SMB
reports positive SMB bytes, zero ordinary MediaServer media bytes, and only 17
bounded same-offset shadow bytes. See the two
`artifacts/firetv/generated-pull-smb-seek-ab-*.log` files.

The generated fixture now includes deterministic `.edl` intervals at
120-180, 360-420, and 660-720 seconds. Automatic STV skipping is observed but
is not deterministic after every artificial reposition, so the harness
requires the actual server-requested marker target and does not infer a pass
from elapsed timeline alone. Pull and SMB Direct automatic-marker recovery
passed on Media3 and legacy ExoPlayer. Push must be positioned through SageTV
commands; a client-local seek is intentionally not accepted as Push evidence.

The MediaServer NIO A/B is complete. With all other variables fixed,
`use_nio_transfers=false` produced 340/434 ms sustained recovery and
`use_nio_transfers=true` produced 447/638 ms. The corresponding evidence is in
`artifacts/firetv/generated-pull-smb-nio-*.log`. No missing/partial transfer was
observed and NIO did not improve the case. The isolated Unraid test server was
restored to `use_nio_transfers=false`; a recovery copy is retained under its
`.commissioning-backups` appdata directory.

Genuine server-driven Push reposition/flush/anchor ordering is captured in
`artifacts/firetv/recovered-container-artifacts/20260830-070947_mcp_comskip_right_fail_state.json`
and the adjacent checkpoints. Multiple sequences show server flush, anchor,
backend reprepare, first video frame, and resumed A/V. These files were
recovered after correcting the stale direct-MCP artifact path; all active
configuration now writes to the bind-mounted Android repository.

Media3 Push codec-queueing commissioning is complete using exact path
`/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3.ts`. Three isolated repeats are
stored as `artifacts/firetv/generated-push-codec-repeat1.json` through
`repeat3.json`. Sync recovered in 7196/6477/7418 ms. Auto recovered in
8752/4236 ms and timed out once at 50650 ms. The Sync default is retained for
its two-of-three advantage and bounded variance, but this does not reclassify
Push seek recovery as fast.

The default, explicit override, and direct Leanback-launch client-ID paths pass
physically and the test device was restored to DEV001. A current Play audit
confirms target SDK, paired ARM native contents, 16 KB ARM64 ELF alignment, and
APK ZIP alignment pass. Store publication remains blocked by production
identity/signing and Play Console policy/listing setup. Debug and development-
signed release-candidate AABs now build and validate with bundletool 1.18.3;
the debug APK set physically installs while preserving app data. This proves
the bundle pipeline but is not a production-signed artifact. The publication
gate must be checked against Google's current
[target API policy](https://support.google.com/googleplay/android-developer/answer/11926878),
[Android App Bundle requirement](https://support.google.com/googleplay/android-developer/answer/2481797),
[16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes),
[TV quality requirements](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality),
and [Data Safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469).

## Work completed in the current review

- SageTV Core forwards the STV caption state to supporting Vibe MiniClients
  after media load, Android retains the request until tracks are ready, and
  both Exo backends passed visible CEA-608/708 hardware Push/Dynamic tests
  without invoking `dev_set_subtitle_track`. This true STV authority requires
  the Vibe-patched `Sage.jar`, because stock SageTV does not publish
  `VIDEO_CC_STATE`. For IJK or failed callback negotiation, the long-press MiniClient CC
  submenu and Audio and Caption settings expose explicit `Off`, `CC1`, and
  `CC2` compatibility choices. A later received STV state always overrides
  that fallback.
- Added root `bundle`/`bundle-install` commands backed by the unified image's
  pinned bundletool 1.18.3. Both AABs validate, the debug universal APK set was
  physically installed on AFTMM/API 25 after an exact package check, and the
  two configured servers remained present. Current hashes are debug AAB
  `6c29d43089fa7a442c2ebb57b49c10e142e7aa899da20ef0cc5ba0d7c99b950b`,
  release-candidate AAB
  `2b8570577ba0c09c10df76eb29315a0616b157fb268cc7a4802499dd35500cf1`,
  and debug APKS
  `ed0c8a99efc897ff5e1236dcc33c8d136c601521f2eafc4ab706165d9017044a`.
  The release candidate uses the development identity and key and must not be
  uploaded to Google Play.
- Restored broadcast CEA-608/708 captions in Push/Dynamic and Pull MPEG-TS. Media3 declares
  caption formats through its default extractor factory; legacy ExoPlayer 2.18
  preserves its normal extractor set/order and replaces only the TS extractor
  because that version lacks `setTsSubtitleFormats`. Added null-safe track
  reporting, SageTV disabled-track sentinel translation, MCP current-cue and
  overlay state, and `mcp-caption-test`. Caption views attach to the Activity
  content root above the SageTV GL surface. The gate now captures the Fire TV
  screen while a current cue is non-empty and the overlay is attached. Both
  backends passed Push/Dynamic and Pull with visibly rendered Meet the Press
  captions on Amazon AFTMM/API 25 without a crash. GSY System was deliberately
  not invoked. One brief user-observed runtime-check message was not reproduced
  in a subsequent 120-second recording and had no fatal logcat signature.
- Replaced `AudioUtil` with a lifecycle-owned `AudioFocusController`, with
  modern `AudioFocusRequest`/media attributes on API 26+, the required API-25
  fallback, explicit transient/duck/permanent behavior, user-pause protection,
  idempotent abandonment, and stale-callback generations. Added debug-only MCP
  focus contention plus `mcp-audio-focus-test`. Media3 and legacy ExoPlayer
  hardware Pull both passed the complete physical focus gate with advancing
  A/V recovery and no crash signature. GSY System was deliberately excluded
  and remains last in future full player matrices.
- Added negotiated SageTV Core media-state metadata and Android parsing without
  changing older-client URLs. Physical server logs prove `active=0` for Meet
  the Press and `active=1` for live 2.1/5.1 files; format/encoder hints are
  retained by the backend-owned media context.
- Added opt-in exact-channel MiniClient event 231 plus MCP
  `dev_set_live_channel`. Unlike legacy numeric UI input, it preserves dotted
  ATSC channel numbers. The negotiated URL also carries authoritative channel
  identity, so the gate distinguishes an already-active healthy channel from
  a failed transition and confirms the destination after a real switch.
  Media3 and legacy ExoPlayer hardware Pull each passed 10 alternating 2.1/5.1
  changes. GSY Auto, Media3, and legacy Exo passed four each; GSY System passed
  a bounded two-change crash-checked run on the commissioned Fire TV. After
  the exact final server export was installed, Media3, legacy ExoPlayer, and a
  deliberately last GSY System run each passed fresh A/V plus exact 5.1/2.1
  transitions again.
- Correlated successful live starts with OpenDCT. Its embedded FFmpeg probing
  completed in roughly 0.4–1.8 seconds despite transient incomplete-frame
  warnings. This path is independent of SageTV MIM; MIM remains disabled until
  its own physical tests pass.
- Added and passed `dev mcp-eof-test` on the commissioned Fire TV for Media3
  and legacy ExoPlayer using the exact 2.1 GB Meet the Press MPEG-TS recording.
  Pull sources now bound completed-file reads, recheck potentially growing
  SageTV boundaries, and report confirmed Media3 tail EOF without a crash or
  permanent buffering loop. FFprobe evidence ruled out a damaged recording
  and FFmpeg/MIM involvement in this direct Pull path.
- Added and passed `dev mcp-lifecycle-test` on the commissioned Amazon
  AFTMM/API 25. It proves advancing A/V before HOME, process survival in the
  background, a stable foreground reconnect, three repeated exact-path starts,
  crash-free operation, and final no-process teardown. The gate passed all
  four Media3/legacy-ExoPlayer and Push/Pull combinations; server logs show no
  new 30-second media-command timeout after the correction.
- Fixed the physical failure uncovered by that gate. SageTV was waiting in
  `MiniPlayer.DVDStream` for a four-byte reply that Android omitted when Media3
  rejected audio-track selection from the network thread. Media3/Exo2 track
  selection is now marshalled to the main/player thread and
  `MEDIACMD_DVD_STREAMS` replies on every branch.
- Made MiniClient connection shutdown ownership-aware so a reconnect finishing
  after close cannot publish its socket or clear a replacement session.
- Added `docs/CONNECTION_PROTOCOL_LIFECYCLE.md` and executable
  characterization for Media-before-GFX startup, GFX reader/dispatcher buffer
  ownership, serialized event replies, reconnect conditions, connection close
  order, renderer dispatch, and activity startup/pause behavior. Structural
  extraction remains physically gated; no production connection or playback
  behavior changed.
- Migrated all settings fragments and host activities to AndroidX/AppCompat,
  moved the codec dialog to the AndroidX fragment manager, and added a static
  regression guard. Remaining platform fragments are outside the settings
  package and remain an explicit task.
- Centralized fullscreen control in `AppUtil`; ConnectingActivity now uses a
  main-looper Handler and cancels delayed work during destruction.
- Removed the unused `RECORD_AUDIO` permission/feature after confirming there
  is no active microphone implementation.
- Added `scripts/inspect_apk.py` and root `inspect-apk` workflow support. The
  release content boundary passes; strict release inspection correctly blocks
  the development-signed candidate until a production key is supplied.
- Completed the `DevTestReceiver` split. The receiver is now only a debug
  broadcast dispatcher; session/UI, synchronous player, asynchronous health
  checks, executor ownership, parsing, client ID, tuning, configuration,
  snapshots, and response formatting have separate debug-only owners with
  unchanged wire strings and targeted characterization/compilation gates.
- Replaced anonymous discovery threads and the TV background Timer with named
  or explicit-main-looper lifecycle owners. Core tests now use modern
  Mockito/JUnit and pass under the unified JDK 17 toolchain.
- Hardened playback startup so expected video/audio output, surface validity,
  player errors, and `AskToDeleteRecording`/EOF generate a structured verdict
  before any tuning/player-matrix operation.
- Added `dev_play_server_path` and the debug-only MiniClient event sender for
  deterministic playback of an exact indexed server file without Search UI
  navigation. Restored the omitted stream-expectation helper that had caused
  a false MCP tool error after successful startup.
- Commissioned `/var/media/tv/MeetthePress-65149351-0.ts` against the final
  Unraid image at `192.168.10.232`. Media3 Push/Dynamic hardware mode entered
  `MediaPlayer OSD`, exposed a 1920x1080 surface, advanced MPEG-2 and AC-3
  output counters, and passed FF/REW recovery.
- Captured the resolved dependency graph under `artifacts/reports`. A trial
  removal of unused Cast dependencies was rejected because it also changed 28
  aligned AndroidX artifacts; do not retry it without explicit constraints and
  the physical playback/UI matrix.
- `docs/DEPENDENCY_AUDIT.md` records the direct families, license-review gaps,
  Cast/exported-surface finding, and an OSV scan of all 342 locked Maven
  coordinates. The 14 advisory-bearing Protobuf/Netty coordinates are absent
  from the captured application runtime graphs and belong to build/test paths.
- Started physical commissioning on the configured Amazon AFTMM/API 25 Fire TV.
  The guarded clean install touched only
  `org.opensagetv.miniclient.dev.debug`, discovery showed servers at
  `192.168.10.175` and `192.168.10.232`; the commissioned Vibe test server is
  container `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`. The
  installed APK was pulled back and verified
  byte-for-byte against the built artifact. The protected
  `jvl.sage.miniclient.android.tv.debug` package remains installed and was not
  mutated.
- Disabled automatic sleep/screensaver on the test Fire TV for long physical
  runs. The device reported `mStayOn=true`, plug modes `7`, display timeout
  `2147483647`, sleep timeout `0`, and screensaver disabled.
- Added `docs/ANDROID_FRAMEWORK_MODERNIZATION.md` and an executable inventory
  guard covering all active raw scheduling owners, legacy Fragment/settings
  owners, permission surface, audio focus, fullscreen, and dependency
  boundaries. The completed inventory item was removed from `TASKS.md`.
- Fixed raw dash-prefixed ADB argument forwarding in `dev.ps1` and added a
  regression test using the real Windows/WSL path.
- Converted the previously undiscovered pytest-style API-36 checks to
  unittest. The current host gate passes 222 scaffold/static tests, 41 MCP
  tests, and full validation in the existing unified container.
- Reviewed the other-AI changes without modifying the protected original tree.
- Consolidated task tracking into `TASKS.md` and moved durable playback rules to
  `docs/PLAYBACK_DIAGNOSTICS.md`.
- Consolidated release history into `CHANGELOG.md` and removed version-specific
  update/review/prompt documents.
- Replaced per-version update metadata with stable `release.properties`.
- Hardened changed-files ZIP preflight with path, duplicate-entry, manifest,
  payload-hash, and untouched-baseline validation.
- Added restart coverage for interrupted extraction/preparation plus corrupt,
  unsafe, duplicate, mismatched, drifted, build-required, host-only, and
  package-selection update cases.
- Added `dev.ps1` and `update.ps1` so Windows uses the sibling unified build
  environment reliably; the component-owned standalone image path was removed.
- Added `update.sh` as the clean cross-platform update entry point.
- Preserved Android's fully tested self-contained updater and guarded DEV001
  commissioning launch while aligning its root commands, metadata, package
  directory, manifests, and four resumable preparation gates with the shared
  contract.
- Locked the Gradle distribution checksum, Gradle dependency resolutions,
  artifact verification checksums, the exact Python/MCP environment, and the
  Android platform-tools revision. The unified Dockerfile keeps authoritative
  version metadata after the large SDK layer so source/metadata-only changes do
  not invalidate that toolchain layer.
- Passed the Android suite through both the component root entry point and the
  unified build-environment `android-all` entry point. The latter now exports
  its mounted build-environment root to component tests.

## Validation status

The consolidated workflow was run from Windows through `dev.cmd`, which
explicitly selected `/workspace/android-client` in the one
`opensagetv-vibe-dev` container:

```text
./dev test (Windows):           445 project/static + 57 MCP + Core JUnit PASS
./dev validate:                 PASS
./dev build:                    BUILD SUCCESSFUL, 60/60 tasks
./dev mcp-lifecycle-test:       PASS (API-25/API-30 auto-resume on/off,
                                      manual pause, timeout, replay, teardown)
./dev mcp-eof-test (Media3):    PASS (real Fire TV, exact server path)
./dev mcp-eof-test (Exo2):      PASS (real Fire TV, exact server path)
./dev mcp-audio-focus-test (Media3): PASS (real Fire TV, exact server path)
./dev mcp-audio-focus-test (Exo2):   PASS (real Fire TV, exact server path)
./dev mcp-caption-test (Media3 Push/Pull): PASS (visible CEA-608/708 cues)
./dev mcp-caption-test (Exo2 Push/Pull):   PASS (visible CEA-608/708 cues)
debug APK inspection:           PASS
release content inspection:     PASS
strict production signing:      FAIL (development signer; expected blocker)
```

Current clean debug APK SHA-256:
`92b92345ecd3328a41f917bf232a65006af8e0532770b97e94b5686fefd0a5ff`.
This long-press repair APK is installed on the non-Pro `.25` control device;
an injected Fire OS hold opened the complete navigation/player overlay. The
refreshed 1,340-file source archive passed all 445 tests, then a separate build
from that extracted source using an empty Gradle cache executed all 60 tasks
and reproduced this APK byte-for-byte. The Pro `.29` retains the physically
passing incremental lifecycle build
`3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`
and is paused from further testing by user request.

Built artifact:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256: 92b92345ecd3328a41f917bf232a65006af8e0532770b97e94b5686fefd0a5ff
package: opensagetv.vibe.miniclient.debug
min SDK: 23
target/compile SDK: 36/36
```

APK inspection confirmed the expected adaptive/legacy/round Vibe launcher
resources across all five densities, the separate TV banner, non-exported
`${applicationId}.fileprovider`, and app-private internal/external file paths.
Requested Android permissions are `INTERNET`, `ACCESS_WIFI_STATE`,
`ACCESS_NETWORK_STATE`, and `WAKE_LOCK` plus Android's
generated not-exported receiver permission. `RECORD_AUDIO` and the optional
microphone feature were removed because the active source has no recording
implementation. Merged third-party exported components remain an explicit
audit task.

The local GitHub release bundle and changed-files handoff package have been
regenerated and inspected. The final 1,340-file source ZIP was extracted into
an isolated Git-less directory and passed all 445 project/static tests. Its
60-task debug-APK build used a new Gradle user home and reproduced the canonical
APK byte-for-byte after the dependency-verification repair. Git-less bundles
record `project-manifest` provenance;
Git checkouts retain exact revision/dirty-state enforcement. Strict production
signing remains intentionally unsatisfied; no production-signed APK is claimed.

The seven new connection/protocol characterization tests also pass directly on
the host. The full root count above must be reconfirmed after the manifest is
refreshed.

Physical Fire TV commissioning has started but is incomplete. The current APK
(`393c3b0557642fb07fb7ec8a5cda145b2149139f31e858dcbcd62189c5cdc279`)
was clean-installed on Amazon AFTMM/API 25 to invalidate Fire OS launcher
artwork, after backing up and restoring the Dev-only preferences.
The installed target-SDK-36 package no longer requests `RECORD_AUDIO`; the
71-tool MCP smoke test passes, and discovery displays servers
`192.168.10.175` and `192.168.10.232`; the intended commissioned test server is
Unraid container `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`.
Screenshot evidence includes
`artifacts/firetv/20260829-044949_mcp_smoke.png`. Direct MCP connection to
`192.168.10.232` and exact-path prerecorded playback now pass without manual
server or Search-result selection. Exact EOF, teardown, genuine growing live
playback, repeated exact channel changes, and HOME/foreground surface
recreation and the Media3/legacy-Exo audio-focus matrix pass. GSY System was
not invoked by the focus or caption gates. The real CEA-608/708 Push/Pull caption matrix
passes visibly on Media3 and legacy ExoPlayer. Automated screenshot evidence is
stored under `artifacts/firetv/*_caption-*-visible.png`. Subsequent real `.edl`
marker tests pass on SMB Direct for both Exo backends; see the current-state
summary and compatibility evidence above.

The API-36 storage/share flow is physically commissioned. App-private file
logging creates `Documents/logs/sagetv-miniclient.txt`, Share Log selects the
newest file, FileProvider grants it to X-plore without storage permission, and
the test restored file logging to off. Evidence is retained as
`artifacts/firetv/share-log-chooser.png`. The generated seek fixture also fills
the Fire TV's calibrated display viewport with 1920x1080 surface telemetry;
see `artifacts/firetv/vibe-generated-fullscreen.png`.

Upgrade and cleanup are also physically commissioned. Disposable internal and
app-specific external sentinels survived an exact in-place `adb install -r`;
uninstall then removed the Dev package's entire external Android/data tree.
The exact clean APK was reinstalled after the test. This was isolated to
`org.opensagetv.miniclient.dev.debug`; no production package or media path was
used.

Centralized fullscreen is physically complete on the commissioned Fire TV.
Repeated exact playback, calibrated 1920x1080 video/destination telemetry,
OpenGL/libGDX overlays, Help/Video Info dialogs, system overlays, HOME/return,
and teardown pass. The Fire TV IME was shown over active playback without
resizing the calibrated window and was then hidden successfully; screenshot
evidence is `artifacts/firetv/keyboard-fullscreen.png`. Production system-UI
flags remain centralized in `AppUtil`.

The target-SDK-36 launcher and settings flow also passes physical navigation:
Settings opens from the Leanback Configure row, the default-player preference
dialog opens and cancels, HOME/relaunch returns without a fatal exception or
ANR, and both original server records remain. The Add Server dialog now handles
Fire OS API 25's `EditText` D-pad behavior explicitly; Server Name -> Server
Address -> Add passes using only D-pad Down. No test server entry was saved.

The Leanback launcher/server-browser batch is now AndroidX-based:
`MainActivity` and `ServersActivity` use support fragment managers,
`MainFragment` uses `BrowseSupportFragment`, Add Server and Auto Connect use
support `DialogFragment`, and the launcher layout uses
`FragmentContainerView`. Static checks and compilation pass, and the migrated
launcher, Add Server D-pad path, and temporary Auto Connect path were exercised
on the commissioned Fire TV without a fragment/runtime failure. Auto Connect
was restored to its prior `false` value after validation.

The playback overlays were then removed from the framework FragmentManager
entirely. `NavigationDialog`, `VideoInfoDialog`, and `HelpDialog` are retained
by `UIActivityLifeCycleHandler`, dismissed together during pause/destroy, and
work unchanged from both the plain OpenGL `Activity` and libGDX
`AndroidApplication`. OpenGL physically rendered all three over the canonical
fixture; libGDX rendered navigation, and overlay-visible HOME teardown had no
window/fragment/runtime failure. The exact clean APK SHA-256 is
`ef16e5a24cb1d6cb41e7aaa160bdb559cf7dec3055746adb25366d7c8056a398`;
its final ordering evidence is
`connection-ordering-20260831-013302.json` (OpenGL) and
`connection-ordering-20260831-013520.json` (GDX).

All root CMD, PowerShell, and shell entry points were audited. PowerShell and
Bash syntax pass, and `dev.cmd`/`update.cmd` resolve their own project directory
and the sibling build environment correctly when launched from outside this
repository. Normal work uses the installed
`opensagetv-vibe-build-env:u26-j11` image and existing
`opensagetv-vibe-dev` container; it does not create an Android-specific image.
The shared wrapper now detects a changed sibling-workspace root and rebinds the
same named container instead of executing against stale mounts.

An independently committed temporary sibling layout passed its own root
`test`, `validate`, and clean `build` commands without the protected original
workspace. It generated a fresh private debug key as expected, then the single
container was rebound to this real checkout and the temporary tree was removed.

## v0.5.89 release-candidate state

The SageMC interoperability gate is complete on the commissioned non-Pro
Fire TV (`192.168.10.25:5555`) against isolated Vibe server `.232`. The exact
`/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts` path passed initial
playback, same-connection HOME return and Surface recreation, manual-pause
preservation, three consecutive exact-file watch cycles, and teardown with the
staged Core redundant-watch correction active. The lifecycle runner now clears
the Android crash buffer before evaluating the current process, matching the
other physical runners and excluding stale-PID failures.

The v0.5.89 Android candidate also fixes the Media Keys settings
`SwitchPreference`/`SwitchPreferenceCompat` mismatch and applies one bounded
error-presentation rule to Media3 and legacy ExoPlayer: a transient
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` remains logged and recovered but is
not shown after the current load has already rendered its first frame. Startup
container errors, other errors, and maximum-retry failures stay user-visible.
The package version is sourced from root `VERSION`; both `dumpsys package` and
the physically rendered Settings screen confirmed the version name rather than
the inherited upstream `1.14.0`. The final clean APK reports
`0.5.89-DEV-DEBUG`, has SHA-256
`6a9fe6f7a416d4f7a42de103a0a84319372794a5e6835fdce712978344b944d6`, and is
installed on the Fire TV Pro (`192.168.10.29:5555`). Against the unmodified
`.175` server, Media3 hardware MPEG-2 Push passed seek, large-jump,
pause/resume, and six rapid FF/rewind cycles with 821-2,093 ms recovery. The
user then passed the same remote operations manually with legacy ExoPlayer
hardware Push. The captured trace contained no player error, crash,
maximum-retry failure, or visible unsupported-container warning.

Release `v0.5.89` is published at
<https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.89>.
Commit `c37e4a46bc41112b39bebc52c28e4197773f29af` passed GitHub's
`source-contracts` workflow. The manifest-exact source ZIP independently
passed all 496 project/static tests, all 70 MCP/workflow tests, Gradle core
tests, project validation, and a clean 60-task APK build from a separate
extracted checkout. All five public assets were downloaded into a fresh local
directory and matched GitHub's SHA-256 digests; the published APK retains the
physically tested SHA-256 above.

## Exact resume sequence

### Same-file MPEG-TS seek investigation (2026-09-12)

ONN v1 hardware/Pull testing with `MeetthePress-65149351-0.ts` confirmed the
video decoder remained `c2.amlogic.mpeg2.decoder` with one initialization and
zero releases across repeated seeks. The remaining delay is Media3's repeated
PCR binary search over SageTV Pull, not repeated container/track or decoder
selection. The new retained Pull session and bounded completed-file probe cache
compile and are active. Do not reduce the TS timestamp-search multiplier: the
controlled multiplier-4 run took 29,597 ms versus 8,396 ms at the proven
multiplier-16 default. A prototype that inferred a cached byte position from
the first post-search extractor read took 11,583 ms on repeat and was removed;
only an extractor-validated PCR position is acceptable for the unfinished
same-file hint task. Runtime tuning was reset to compiled defaults afterward.
The tuning runner now also accepts `--video-name` and passes the selector to
the shared startup path; its focused 14-test suite passes.

Media3's current `TsBinarySearchSeeker` reads up to the configured timestamp
search window for each probe and reports the exact successful byte position
only through `BinarySearchSeeker.onSeekOperationFinished`. The TS seeker is a
package-private final implementation owned by `TsExtractor`, so Vibe cannot
safely consume that callback through the public extractor API. Do not restore
the discarded SeekMap-wrapper experiment: `TsExtractor.seek()` starts its own
global PCR search regardless of the initial SeekMap byte estimate. The viable
next choices are a bounded cache of the real probe bytes or a narrowly tracked,
Apache-2.0-derived TS seeker/extractor implementation with explicit upstream
diff tests; reflection and guessed byte offsets are rejected.

A follow-up controlled block-size run used the same ONN v1, Vibe server,
`MeetthePress-65149351-0.ts`, Media3 Pull/hardware decoder, 985220 ms target,
and 16x PCR search window. A 128 KiB Pull block recovered in 7612 ms while the
normal 256 KiB block recovered in 6296 ms; both retained
`c2.amlogic.mpeg2.decoder` and completed without a player error. Smaller reads
therefore increase MediaServer round-trip cost and are not a safe optimization.
The report is
`artifacts/firetv/onn-meet-the-press-pull-read-128-256.json`. All Dev runtime
tuning was reset afterward; Media3 is back to 256 KiB/16x and legacy Exo to
512 KiB/16x.

Fire TV Pro testing on `192.168.10.29` was resumed by the user on 2026-09-05.
Its ADB transport is authorized. Launcher/icon, captions, Native hardware DVD
cadence, A/V progress, transport/chapter recovery, and authored-disc transitions
pass. The affected cadence/control gate was repeated successfully on `.25`.
Resume with the remaining ordered items in `TASKS.md`; do not reopen DVD
cadence unless a reproducible physical regression appears.

From Windows Command Prompt or PowerShell:

```bat
dev.cmd test
dev.cmd validate
dev.cmd build
```

From Linux/WSL:

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
```

Then record the APK SHA-256 and inspect package ID, target SDK, permissions,
exported components, FileProvider authority, and launcher resources. Only after
that should a guarded physical install/launch and the first-time setup/device
matrix proceed.

## Completed EXT-004 legacy-extender audit

EXT-004 checksum-verified the archived HD200, HD300 release, and latest HD300
beta images. The HD200 ROMFS was extracted read-only and inventoried, but its
MiniClient is inside an encoded `FNIB` kernel payload rather than a separately
inspectable executable. That limitation is recorded as a clean-room evidence
boundary. Both HD300 roots contained non-stripped MIPS MiniClient binaries, so
their symbols and properties could be compared with the surviving Apache-
licensed MiniClient/Core source and public SageTV release/firmware reports.

The completed domain and server-branch disposition is in
`docs/PLAYER_SERVER_COMPATIBILITY.md` under **EXT-004 completed evidence
ledger**. No new runtime capability was justified. The 512 KiB HD300 Push limit
remains deferred because stock Core clamps it and there is no reproduced client
defect; HDMI/HBR, RC5, unified-YUV, advanced-deinterlace and Sigma DCC/STC
implementation details remain rejected. Existing targeted reconnect, detailed-
buffer, seek-generation, caption/DVB, aspect/interlace, input, fast-switch and
DVD behavior remains independently gated rather than enabled by extender
identity.

The closing affected physical gate used non-Pro `.25` against stock `.175` on
`VibeSeekTest`: Media3 hardware playback remained fullscreen; seek, FF/REW,
large jump and pause/resume all recovered healthy A/V; no crash signature was
present; and all 103 settings were restored. Dynamic negotiation selected
stock MediaServer Pull, and the record says so rather than claiming forced
Push. The firmware extraction and generated inventories are disposable audit
data and are not project inputs. Workspace safety policy blocked direct
recursive deletion after completion, so the verified generated-only tree was
moved out of `artifacts/temp` to the recoverable
`artifacts/cleanup-quarantine/ext004-firmware-audit-20260930` location.

## Invariants

- Never modify `SageTV-MiniClient-Dev` during this migration.
- Never mutate a `jvl.sage.miniclient*` package.
- Do not make Media3 the default or change the frozen base player contract
  without explicit evidence and authorization.
- Do not enable continuous player telemetry; use bounded/on-demand diagnostics.
- Do not call a watchdog an Android/server/FFmpeg failure without correlated
  evidence.
- Do not create another task list, per-version update note, AI prompt file, or
  review document. Update the existing durable files.
- Do not claim a physical-device PASS from host-only tests.

## Known remaining gates

See `TASKS.md`. Its feature-expansion foundation is complete on the
commissioned API-25 Fire TV. Matching-device checks (adaptive icons and API
33+ predictive Back) still block release sign-off when their hardware is
unavailable, but do not block unrelated feature development. Dependency churn,
  remaining large-class splitting, signing, and publication
remain deliberately deferred. The
host-only unified workflow and deterministic clean APK gate are complete.
