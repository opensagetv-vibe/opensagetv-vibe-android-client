# App Store Readiness

Last reviewed: 2026-09-06

## Current conclusion

The Android client is technically close, but the current artifact should not yet
be submitted to Google Play or the Amazon Appstore.

The project is ready for its current GitHub source/APK release scope. It is not
yet production-signed or fully cleared through the Google Play and Amazon store
submission gates.

## What already passes

- The application targets API 36, exceeding the current Android TV minimum of
  API 34.
- It supports ARM 32-bit and 64-bit architectures. Previous validation reports
  16 KB page-size compliance.
- The manifest includes a Leanback launcher and declares the touchscreen feature
  as optional.
- Release permissions are limited primarily to network and Wi-Fi-state access.
- Debug and MCP functionality is excluded from release builds.
- Google Play AAB generation and bundle validation work.
- Playback, D-pad navigation, captions, lifecycle handling, and audio focus have
  received substantial physical testing on Fire TV and Fire TV Pro devices.

## Remaining code-level blockers

### 1. Protect SMB credentials from Android backup

SMB usernames and passwords are stored in ordinary `SharedPreferences`, while
Android backup is enabled. No `dataExtractionRules` or `fullBackupContent`
exclusions currently protect those preferences.

Before store submission, do one of the following:

- exclude all credential-bearing preferences from cloud backup and
  device-to-device transfer;
- move credentials into appropriate secure storage and explicitly define their
  backup behavior; or
- disable application backup if preserving other settings is not required.

Reference:
[Android Auto Backup documentation](https://developer.android.com/identity/data/autobackup)

### 2. Correct the TV launcher icon declaration

The Leanback activity currently assigns the rectangular TV banner to
`android:icon`. It should use the square launcher icon while keeping
`banner_v2` as its `android:banner` and, if desired, its logo.

The final merged manifest must also be inspected to confirm that the two
launcher activities produce the intended phone, Android TV, and Fire TV launcher
behavior without duplicate or missing entries.

Google requires a proper 320x180 TV banner and a separate square TV application
icon.

Reference:
[Android TV quality requirements](https://developer.android.com/docs/quality-guidelines/tv-app-quality)

### 3. Complete modern Android TV lifecycle verification

Predictive Back is presently disabled, and restricted resizability uses a
temporary compatibility property. Before store submission, physically verify on
API 33 or newer:

- root Back behavior;
- Home, background, and foreground recovery;
- pause and resource release when leaving playback;
- HDMI output disconnection and reconnection;
- audio-focus gain and loss;
- media-button behavior;
- decoder and MediaSession release.

Reference:
[Amazon Fire TV multimedia requirements](https://developer.amazon.com/docs/fire-tv/multimedia-app-requirements.html)

### 4. Normalize the public version identity

The project release version is currently `0.5.87`, while the Android package
reports application version `1.14.0`. This may reflect the inherited upstream
version, but the public `versionName` and monotonic `versionCode` policy must be
selected and applied consistently before the first store upload.

### 5. Regenerate and inspect final production artifacts

Existing release reports predate some package, launcher, CPU-statistics,
caption, and DVD changes. A clean final build must revalidate:

- the merged release manifest;
- ARM32 and ARM64 native-library parity;
- 16 KB native-library page alignment and APK ZIP alignment;
- absence of debug and MCP components;
- permissions and exported components;
- credential and secret scans;
- dependency and license inventory;
- production signing;
- a production AAB for Google Play;
- a production APK for Amazon Appstore testing.

## Non-code submission blockers

- Approve the production signing/upload key.
- Decide Play App Signing ownership.
- Publish a privacy policy.
- Complete the Google Play Data Safety declaration.
- Complete the Amazon privacy questionnaire.
- Prepare store descriptions, screenshots, icons, banners, content rating, and
  target-audience declarations.
- Supply reviewer instructions and a usable method for accessing a SageTV
  server during review.
- Confirm rights to the OpenSageTV Vibe name, logo, screenshots, and promotional
  media.
- Produce the final production-signed Google AAB and Amazon APK.

References:

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Google Play Data Safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Amazon Appstore privacy labels](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html)
- [Amazon Fire TV device filtering](https://developer.amazon.com/docs/app-submission/device-filtering-and-compatibility.html)

## Release judgment

The playback implementation is reasonably close to store-ready. The current
build is **GitHub-release ready, not Google Play or Amazon Appstore submission
ready**.

The backup/credential protection and launcher-manifest correction are the two
clearest source changes required before submission. Production signing, fresh
artifact validation, physical API 33+ behavior testing, and store-console
materials must then be completed before claiming compliance.
