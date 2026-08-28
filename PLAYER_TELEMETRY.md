# Player Telemetry Status — v0.5.63

Continuous/background playback telemetry remains **disabled**. v0.5.63 permits only the bounded debug exact-event trap mechanism described below.

Fire TV AFTMM testing established that adding instrumentation callbacks inside the legacy player runtime can prevent video startup. Clean uninstall/install testing confirmed the hooks themselves—not APK replacement behavior—were responsible.

Current rule:

- `BaseMediaPlayerImpl`: no telemetry hooks
- `Exo2MediaPlayerImpl`: no AnalyticsListener telemetry instrumentation; debug builds can call bounded exact-event traps from the existing listener
- `IJKMediaPlayerImpl`: no continuous telemetry; debug builds can record bounded event snapshots
- `Media3MediaPlayerImpl`: no AnalyticsListener/background telemetry; debug builds can call bounded exact-event traps from the existing listener
- MCP player-telemetry result remains disabled rather than returning stale data

Use external diagnostics instead:

- MCP `collect_playback_diagnostics`
- logcat capture
- MediaCodec diagnostics
- screenshots
- screen recordings
- ADB remote-key/seek sequences

If an internal measurement is unavoidable later, introduce **one explicitly opt-in hook at a time**, rebuild with uninstall-before-install, and prove ordinary playback startup after each individual hook.

## v0.5.63 debug exact-event traps

Continuous player telemetry remains disabled. v0.5.63 adds a narrower debug-only mechanism for MCP/Codex diagnosis: event traps reuse already-registered player callbacks and take one `PlaybackHealthProbe` snapshot only when an important event occurs. They do not install an AnalyticsListener, do not run a background polling loop, and are absent as an implementation class from release builds.

The trap ring is bounded to 32 events and can be cleared/read explicitly through MCP. SageTV protocol-thread traps timestamp the event immediately and schedule the counter snapshot on the Android main thread rather than blocking the media command. This is intentionally different from the earlier continuous instrumentation that affected startup.
