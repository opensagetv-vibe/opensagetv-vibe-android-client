# Player diagnostic instrumentation policy

Continuous/background playback telemetry is disabled. Fire TV testing proved
that extra player instrumentation can alter or prevent startup, so diagnostics
must be bounded, explicit, and absent from production behavior.

Current rules:

- no background telemetry hooks in `BaseMediaPlayerImpl`;
- no added AnalyticsListener polling in Exo2 or Media3;
- no continuous IJK callback collection;
- debug builds may snapshot state from callbacks that already exist;
- the debug event ring is bounded and cleared/read explicitly;
- MCP must return unavailable rather than stale data when a metric is disabled.

Preferred evidence:

- one-shot debug player state;
- bounded exact-event traps;
- logcat and crash-buffer capture;
- MediaCodec, AudioTrack, Surface, and focused-window diagnostics;
- screenshots/screen recordings;
- datasource read/wait counters;
- correlated SageTV server/FFmpeg timestamps.

If internal measurement is unavoidable, add one opt-in measurement at a time,
rebuild/install cleanly, and prove ordinary startup before interpreting its
output. See `docs/PLAYBACK_DIAGNOSTICS.md` for experiment and attribution rules.
