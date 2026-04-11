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

## Release flow

- Pushes to `main` run Android CI.
- Tags like `v1.0.0` run the release workflow.
- The release workflow builds the APK and uploads `ToolTok-release.apk` to the matching GitHub Release.

That makes the latest download URL stable:

`https://github.com/chartmann1590/ToolTok-App/releases/latest/download/ToolTok-release.apk`

## Architecture

- `MainActivity.kt`: WebView shell, refresh, back handling, file chooser
- `AppUrlPolicy.kt`: internal vs external domain rules
- `ExternalUrlSpec.kt`: pure Kotlin URL parsing used by tests and browser intents
