# Android Vibe diagnostic web server

This optional web server runs in the Android client, not the SageTV server,
Client Extension or Web Client plugin. It works without any server plugin.

## Use

1. In Vibe's main startup Settings, enable **Diagnostic web server**.
2. Read **Diagnostic web address**, normally `http://DEVICE-IP:8272/`.
3. Open that address on a phone/computer on the same trusted LAN.
4. Use the HTML5 dashboard for playback, seek timeline, searchable logs,
   incident history, captions, settings and the GitHub report helper. Browser
   updates are single-flight, default5seconds and pause when hidden or when
   **Pause updates** is selected. ZIP requests within30seconds reuse a report.

The long-press playback **bug icon > Download in web browser** shows the
current device IP, full URL and an offline-generated QR code for a phone.
Web stays visible but gray when disabled or when its actual listener/address
is unavailable. Opening the chooser never enables it. The QR contains exactly
the displayed LAN URL, not a credential, token or report; scan it on the same
trusted LAN and download/review the ZIP on the webpage. Back returns to the
export chooser. No camera permission or external QR service is required.

The default is off and persists across app restarts/update installs. Leave
Vibe open: Home/exit/background stops the listener; returning to Vibe resumes
it if still enabled. Turning the setting off closes current connections and
releases the port. This does not keep the device awake or change sleep/power.
**Client diagnostic web port** is configurable1024..65535; an unavailable port
is reported without affecting playback. The address row is read-only.

Statistics reuse the existing overlay's main-thread capture, shared across
browser tabs with a2second cache and750ms wait budget. They show version,
OS/model, memory, transport, decoder, formats/dimensions/frame counters,
buffering and audio output when available. Configured offset is NOT measured
lip-sync. Missing FPS, unsupported counters and GPU utilization are unavailable,
not inferred. Detailed collected evidence remains in Logs/ZIP when available.
Enable file logging separately in Diagnostics Settings to collect app logs.
Existing log tails, playback traces, Push-stall reports and current-video tests
use their existing bounded retention/redaction. ZIPs remain in private app
storage with the existing four-bundle retention policy.

## Dashboard tools

- **Seek / Comskip timeline:**64 sampled events from existing server request
  sequence/target/timestamps and playback observations. Requested and observed
  positions are separate; old baseline requests, superseded requests and gaps
  are labeled. It does not prove frame-exact landing, identify a Comskip marker
  that the server did not transmit, or measure events while the page is hidden.
- **Logs:**load explicitly; optional independent log updates. Severity and
  component classification are best-effort. Time filtering uses original
  HH:mm:ss text (same-day range); timezone is not invented. Search/filter in
  browser memory, pause updates and copy selected/filtered lines. Last2MiB
  maximum; download ZIP for the collected bundle.
- **Incident history:**existing bounded Push-stall reports, playback traces,
  current-video tests and app logs with excerpts and individual redacted text
  downloads. Stable opaque IDs never accept filenames/paths. Live logs may
  grow/rotate; expired reports are unavailable. Crashes/reconnect/decoder failures
  appear only if actually recorded; this is not a new crash collector.
- **Captions:**type/language/service/PID, support, selection, CC1/CC2 mapping,
  STV state when known and callback activity. CEA extractor declarations without
  observed packets are labeled explicitly, not misreported as detected UK CEA.
  Broadcast CC and file/DVD subtitles are separate categories; no track changes.
  CEA synthetic extractor IDs are not TS PIDs. A PID is shown only for a
  separately packetized DVB/Teletext stream with a valid non-reserved13-bit ID;
  other PIDs/services are unavailable. The page does not embed the playing video.
  A collected frame count is not independent proof that the TV showed a picture.
  The dashboard labels BUFFERING even when the MiniClient state is Playing.
- **Settings:**explicit safe allowlist, logging/diagnostic switches, local ZIP
  count/bytes/free space and server upload status. No preference enumeration,
  paths, addresses, passwords, tokens or client identifiers are exported.
- **Issue helper:**fill reproduction/expected/actual; generate/copy a report
  from redacted observations/settings and recent timeline. Download and review
  the ZIP, open GitHub and attach it manually. Nothing submits automatically;
  the privacy confirmation starts unchecked. LAN HTTP clipboard may be blocked,
  so a selected-text fallback is provided. No external SDK/CDN/account token.

New routes are fixed local assets /diagnostics.js and /diagnostics.css,
GET /api/diagnostics and GET /incident.txt?id=OPAQUE-ID. No remote control,
configuration writes, upload, arbitrary file serving or server plugin is needed.

## Separate server uploads

**Send diagnostic logs to server** remains independent. It requires the
Client Extension upload-only receiver and stores reports in `vibe-client-logs`
inside the SageTV server directory. That receiver has no browser pages.
The identical default8272 belongs to different IP addresses: the Android
device serves browser pages; the server address accepts upload API calls only.
Missing/disabled upload receivers leave local reports and playback intact.

The bug icon also offers **Export to SageTV server**. It stays gray without a
connected session and a confirmed enabled Client Extension receiver; missing,
disabled and unreachable receivers are unavailable. Opening/Refresh performs
only a bounded off-main-thread capability check. Selecting an available receiver
opens **Send once / Back**: explicit confirmation sends one redacted ZIP and
shows a byte-count/SHA-256 receipt or error without enabling automatic uploads.
Cancel/Back and the result return to the parent chooser. The captured session is
rechecked during transfer, so reconnect/server changes cannot reroute a report.
The existing main-settings automatic-upload switch remains independent.

**Create and share ZIP** and **Export to SMB** remain separate choices. SMB's
saved Off/On-request/Always behavior is unchanged; Off is gray. Nothing uploads
merely because the chooser was opened, and unavailable services are not enabled.

All export dialog footer buttons use an explicit blue focus fill/light border,
with white text, rather than relying on the device theme's subtle/default tint.
Each dialog requests focus immediately: **Refresh** in the chooser, **Back**
in server confirmation, **Copy URL** in Web, **Back** in the receipt, and
**Cancel** in the SMB confirmation. Arrow keys move the highlight; Select
activates only the focused action. Merely opening a confirmation never sends.

## Privacy and bounds

IPv4 LAN/loopback access only, no login or encryption. Anyone on the trusted
LAN may read redacted diagnostics while enabled. Private addressing is not
authentication: do not enable on an untrusted network or forward the port.
Review downloaded reports before publishing; redaction cannot guarantee removal
of every programme title or manually logged detail.

The server has read-only fixed routes, no playback controls, uploads, arbitrary
paths, remote fetches, redirects or CORS. HTML text is escaped. Numeric local
Host checks reject DNS-rebinding hostnames. Two workers/four queued requests,
8KiB headers,2second read timeout,10second total connection deadline and8MiB
maximum response bound network work. Stats/log snapshots have smaller limits.
No foreground service, wake lock, startup scan or Core modification is added.

Current qualification belongs to **DIAG-003** in TASKS.md. Unit/local socket
results do not substitute for actual device/browser/lifecycle evidence.
Non-Pro232 native Media3/generated CEA and legacy Exo/UK caption browser/seek/
Home/return gates now pass on efbd6adf, with settings restored; see compact
results/DIAG-003/qualification.json. This remains unreleased. The listener
closes at the start of playback Activity pause, before existing slow teardown;
application-level pause callback alone is too late on the legacy path.
BUFFERING uses actual backend state, not background network loading/prefetch.
