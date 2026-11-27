# Lumora Launcher

Modern Material 3 Expressive Android home screen.

**Official repository:** https://github.com/3hbin/Lumora-Launcher

| | |
|---|---|
| Application ID | `com.lumora.launcher` |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 |
| versionName | 1.0.0 |
| versionCode | 2 |
| UI | Jetpack Compose + Material 3 |

## Features

- Real Android launcher (`HOME` + `DEFAULT`)
- App Drawer with search
- Home pages, Dock, folders
- Hide apps, themes, Dynamic Color
- Weather via Open-Meteo (no API key)
- Icon pack detection (CandyBar-style)
- AppWidgetHost foundation
- Optional double-tap to sleep (Device Admin)
- **Auto-update from GitHub Releases**

## Auto-update system

Lumora checks [GitHub Releases](https://github.com/3hbin/Lumora-Launcher/releases) for new versions.

- Settings → **App Updates**
- Manual “Check for updates”
- Optional daily check via WorkManager
- Download APK with progress
- SHA-256 verification when the release body contains a checksum
- Package name verification
- Opens system Package Installer (user confirms)
- Never silent-installs

Configured for:

```
Owner: 3hbin
Repository: Lumora-Launcher
API: https://api.github.com/repos/3hbin/Lumora-Launcher/releases/latest
```

### Publishing a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Push to `main`.
3. Run **Actions → Build & Release → Run workflow**.
4. Set `create_release = true`, fill `version_name` and `version_code`.
5. The workflow builds the APK, computes SHA-256, and creates a GitHub Release with the APK attached.
6. Put the SHA-256 line in the release notes (the workflow already inserts it). Example:

```
versionCode: 3
SHA-256: <hash>
```

The in-app updater parses `versionCode:` and `SHA-256:` from the release body.

**Important:** Keep the same signing key for all releases so Android allows upgrades. Debug builds use the debug keystore; for production, configure a release keystore via GitHub Secrets (never commit the keystore).

## Build locally

```bash
./gradlew --no-daemon clean assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Upload this project to GitHub

```bash
git init
git add .
git commit -m "Initial commit – Lumora Launcher 1.0.0"
git branch -M main
git remote add origin https://github.com/3hbin/Lumora-Launcher.git
git push -u origin main
```

Then open the repo → Actions → run the workflow if desired.

## Set as default launcher

Install the APK → press Home → choose **Lumora Launcher** → Always.

## License

MIT
