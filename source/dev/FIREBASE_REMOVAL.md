# Firebase Removal

Firebase Analytics and Firebase Crashlytics have been removed from the MiniClient Dev source tree.

## Removed

- Google Services Gradle plugin and classpath.
- Firebase Crashlytics Gradle plugin and classpath.
- Firebase BoM, Analytics, and Crashlytics application dependencies.
- Shared-module Crashlytics dependency.
- Crashlytics initialization from `MiniclientApplication`.
- Crashlytics calls from the Android `Logger` implementation.
- Crashlytics enable/user settings and preference-store helpers.
- Crashlytics manifest collection metadata.
- Jenkins dependency on the Firebase `google-services.json` credential.
- Firebase-specific `.gitignore` entries.

## Logging behavior

The existing `ILogger` interface is unchanged to avoid unnecessary changes in shared/core code. Android `Logger` now sends messages and exceptions only to its SLF4J backend. `setCustomKey()` and `setUserID()` remain no-op compatibility methods because their only purpose was cloud crash-report metadata.

Historical Firebase entries in `CHANGELOG.md` are intentionally retained because they describe older released versions.
