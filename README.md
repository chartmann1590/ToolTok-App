# ToolTok Android App

ToolTok for Android is a native Android shell around the live ToolTok web app at `https://tooltok.vercel.app`. It keeps the Android experience simple and reliable while reusing the production feed, newsletter flow, auth, and admin pages already running on Vercel.

Landing site:

`https://chartmann1590.github.io/ToolTok-App/`

## What it does

- Loads the hosted ToolTok app inside a hardened `WebView`
- Keeps `tooltok.vercel.app` in-app and sends external links to the system browser
- Supports pull-to-refresh and Android back navigation
- Builds an installable release APK with no paid services or Play Store dependency

## Local development

Requirements:

- JDK 17
- Android SDK with API 34
- A connected device or emulator

Commands:

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Testing

Unit tests live under `app/src/test/java/com/tooltok/app` and cover the URL/domain rules that decide whether pages stay inside the app or open externally.

Instrumentation tests live under `app/src/androidTest/java/com/tooltok/app` and exercise the Android shell end to end with ads disabled under the test runner for stability.

## Release flow

- Pull requests run Android CI.
- Every push to `main` runs the release workflow automatically.
- The release workflow writes a temporary `local.properties` from GitHub Actions secrets, builds the signed release APK and AAB, creates a Git tag automatically, and publishes both assets to a new GitHub Release.
- The release workflow fails fast if the production AdMob or signing secrets are missing.
- Auto-generated release tags use the format `v<versionName>-build.<run_number>`.

Required GitHub Actions secrets for automatic releases:

- `ADMOB_APP_ID`
- `ADMOB_APP_OPEN_AD_UNIT_ID`
- `ADMOB_BANNER_AD_UNIT_ID`
- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

That makes the latest download URL stable:

`https://github.com/chartmann1590/ToolTok-App/releases/latest/download/ToolTok-release.apk`

## Architecture

- `MainActivity.kt`: WebView shell, refresh, back handling, file chooser
- `AppUrlPolicy.kt`: internal vs external domain rules
- `ExternalUrlSpec.kt`: pure Kotlin URL parsing used by tests and browser intents
