# Fire TV Playback Baseline Test Record

Copy this file into `artifacts/firetv/` for each baseline device/build.

## Build

- Date/time:
- Upstream ref:
- Upstream commit:
- Development commit/patch state:
- APK SHA-256:
- Application ID: `opensagetv.vibe.miniclient.debug`

## Fire TV

- Manufacturer:
- Model:
- Device codename:
- Fire OS / Android version:
- Android API level:
- Build fingerprint:
- Connection: Wi-Fi / Ethernet adapter:
- Display mode/resolution/refresh:

## SageTV

- Server version:
- Connection mode:
- MiniClient/backend setting: Auto / ExoPlayer / IJKPlayer:
- Placeshifter mode: Yes / No

## Test media

- SageTV title:
- File/container:
- Video codec:
- Resolution:
- Frame rate/interlaced:
- Audio codec:
- Original/remux/transcoded:
- Known problem timestamp:

## Startup

| Case | Result | Notes |
|---|---|---|
| Cold app launch → open media | | |
| Reopen same media | | |
| Stop → replay | | |
| Change recording | | |

## Seeking

| Case | Result | Audio recovered | Video visibly recovered | Notes |
|---|---|---|---|---|
| +10 s | | | | |
| +30 s | | | | |
| +60 s | | | | |
| -10 s | | | | |
| -30 s | | | | |
| Pause → seek → resume | | | | |
| Rapid sequence ×4 | | | | |

Rapid sequence: `+30, +30, -10, +60, -30`.

## Failure artifacts

- logcat:
- media/codec dump:
- focused-window dump:
- screenshot:
- screen recording:

## Symptom classification

- [ ] target position did not change
- [ ] stuck buffering
- [ ] audio continued, video froze
- [ ] video continued, audio failed
- [ ] both audio/video stopped
- [ ] decoder error/reinitialization
- [ ] surface/rendering error
- [ ] app crash/ANR
- [ ] network/data-source issue
- [ ] other:

## Notes / hypothesis

Do not mark the case fixed until the same media/test sequence passes after the change and the broader baseline matrix remains passing.
