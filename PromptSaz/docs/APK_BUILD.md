# PromptSaz — APK build guide (for non-programmers)

You do **not** need this guide if you just want the APK: the GitHub repository
builds it automatically on every push and publishes it under **Releases** as
`PromptSaz.apk`. This guide is for building it yourself.

## Option A — GitHub builds it for you (recommended)

1. Open the repository on GitHub.
2. Click **Actions** → wait for the latest **Build APK** run to finish (✔ green).
3. Click **Releases** (right sidebar) → open the newest release.
4. Download **PromptSaz.apk**.
   Always-latest direct link:
   `https://github.com/<your-username>/promptsaz-android/releases/latest/download/PromptSaz.apk`

## Option B — Build on your own computer

1. Install **Android Studio** (free) from developer.android.com/studio.
2. Open Android Studio → **File ▸ Open…** → choose the `PromptSaz` folder.
3. Wait for "Gradle sync" to finish (bottom status bar; first time takes a
   few minutes — it downloads dependencies).
4. Menu **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
5. When it finishes, click **locate** in the popup. The file is at
   `app/build/outputs/apk/debug/app-debug.apk`.
   This debug APK can be installed directly on any phone.

## Install the APK on a phone

1. Copy `PromptSaz.apk` to the phone (download, Telegram, email, USB…).
2. Open it from the Files app.
3. If asked, allow **"Install unknown apps"** for your file manager.
4. Tap **Install**.

## Command line (optional)

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (unsigned)
./gradlew :app:testDebugUnitTest # run the unit tests
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "SDK location not found" | Open the project in Android Studio once; it sets up the SDK path. |
| "Unsupported class file major version" | Use JDK 17+: Android Studio ▸ Settings ▸ Build Tools ▸ Gradle ▸ Gradle JDK = 17. |
| Gradle sync needs network | The first sync downloads ~500 MB of dependencies; later builds are offline. |
| Phone says "app not installed" | Your phone is below Android 7.0 (minSdk 24), or the download was interrupted — re-download. |
