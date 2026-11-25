# ToonForge Tagalog

On-device 1080x1920 30fps Tagalog animated storytelling studio built with Kotlin, Jetpack Compose, OkHttp, Jetpack DataStore, Media3 ExoPlayer, and EGL14 + MediaCodec + MediaMuxer hardware MP4 export.

## Building Locally (Android Studio / CLI)

1. Restore the debug signing keystore once after cloning:
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   chmod +x ./gradlew
   ```
2. Run unit tests and build the debug APK:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions CI

Pushing to `main` or `master` automatically runs `.github/workflows/android-build.yml`, executes `:app:testDebugUnitTest`, builds `app-debug.apk`, and attaches it as a downloadable workflow artifact (`toonforge-tagalog-debug-apk`).

Optional repository secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) enable automatic signed `assembleRelease` builds as well.
