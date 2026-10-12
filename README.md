# App Inspector (Android 16)

A native Kotlin + Jetpack Compose app that lists installed applications, searches names/package IDs, separates system apps from user apps, and opens Android App Info or a launchable app.

## Features
- Installed app list with app icons, labels, and package names
- Search by app name or package ID
- Filter between all apps and user-installed apps
- Counts for all apps, user apps, and currently shown apps
- Open an app or its Android App Info screen
- GitHub Actions builds a debug APK on each push to `main`

## Build locally
Requirements: Android Studio, JDK 17, and Android SDK Platform 36.

Open the repository in Android Studio, allow Gradle sync, then choose **Build > Build APK(s)**. Or, with Gradle and Android SDK configured, run:

```bash
gradle assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Download a cloud-built APK
Open the repository's **Actions** tab, select the latest successful **Build Android APK** run, and download the `AppInspector-debug-apk` artifact. GitHub requires signing in to download workflow artifacts.

## Package visibility
This app declares `QUERY_ALL_PACKAGES` because its central purpose is to display an installed-app inventory. Android and app-store policies may restrict broad package visibility for distributed apps. This build is intended as a private test app; review the applicable policy before publishing it to an app store.

## Compatibility and limitations
- Minimum Android version: Android 8.0 (API 26); target SDK: Android 16 (API 36).
- Android may restrict visibility of some packages depending on OS version, profile, and device policy.
- System apps without a launcher activity can be listed but cannot be opened from the app.
