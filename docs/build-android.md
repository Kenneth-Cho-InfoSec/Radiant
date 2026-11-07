# Android build

Status: Android SDK, NDK, CMake, JDK, Gradle app, JNI/Compose/JUCE host, and debug APK are present. ARM64 and x86_64 debug native targets build; the x86_64 full core test suite and API 29/API 36 app host instrumentation pass. No optimized or signed release has been produced.

## Selected local toolchain

- Android SDK root: `$ANDROID_HOME` (local-only; do not commit `local.properties`).
- Android SDK command-line tools: `15859902`, SHA-256 verified against the Android Developers download page; Android CLI reports `1.1.16717390`.
- NDK: `28.2.13676358`.
- CMake: `3.31.6`.
- JDK: Eclipse Temurin `21.0.13+9`, downloaded to a user-local directory and SHA-256 verified (`d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c`).
- API 29 and API 36 platforms; production ABI `arm64-v8a`.
- Android pins: AGP 9.5.0 / Gradle 9.6.1 / Compose compiler plugin 2.4.10 / Compose BOM 2026.09.00 / Activity Compose 1.13.0. AGP's built-in Kotlin support is enabled; no `org.jetbrains.kotlin.android` plugin is applied.
- Keep machine-specific SDK and JDK paths out of tracked Gradle configuration.

Set `ANDROID_HOME` and `JAVA_HOME` to your local Android SDK and JDK 21 installations before running the commands below. For example, use your SDK's `platform-tools`, `cmake/3.31.6`, and `ndk/28.2.13676358` directories.

## Verified native core build

Command:

```bash
PATH=$ANDROID_HOME/cmake/3.31.6/bin:$PATH \
  $ANDROID_HOME/cmake/3.31.6/bin/cmake -S . -B build/android-arm64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/28.2.13676358/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DLYNX_BUILD_APP=OFF \
  -DCMAKE_BUILD_TYPE=Release

PATH=$ANDROID_HOME/cmake/3.31.6/bin:$PATH \
  $ANDROID_HOME/cmake/3.31.6/bin/cmake --build build/android-arm64 --target lynx_core --parallel 6
```

Result: configure and all 279 initial build steps succeeded for `lynx_core`; after the x87 portability guard was added, the complete ARM64 test target rebuilt and linked successfully. The x86_64 `lynx_tests` target compiled after the same guard. Two upstream warnings remain: shift/subtract precedence in `src/core/PhysicalModels.cpp:1986`, and unhandled `program` enum in `src/core/VfxScript.cpp:1259`.

The Android x86_64 executable is a 252 MiB test-only build artifact. ELF inspection confirmed the ARM64 executable uses `AArch64` and the emulator build uses `Advanced Micro Devices X86-64`; only the x86_64 executable was run in the emulator.

## x86_64 emulator test build

```bash
PATH=$ANDROID_HOME/cmake/3.31.6/bin:$PATH \
  $ANDROID_HOME/cmake/3.31.6/bin/cmake -S . -B build/android-x86_64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/28.2.13676358/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-29 -DLYNX_BUILD_APP=OFF \
  -DCMAKE_BUILD_TYPE=Release

PATH=$ANDROID_HOME/cmake/3.31.6/bin:$PATH \
  $ANDROID_HOME/cmake/3.31.6/bin/cmake --build build/android-x86_64 --target lynx_tests --parallel 6
```

For the full emulator command and source fixtures, see `docs/test-results.md` and `docs/emulator-testing.md`.

## Build and install the debug APK

```bash
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:assembleDebug --console=plain

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W \
  -n com.radiant.daw.debug/com.radiant.daw.RadiantActivity
```

The current debug APK packages `arm64-v8a` and `x86_64` to support physical ARM64 devices and emulator testing. The release variant filters to ARM64. A recent debug artifact measured about 365 MiB; this is unoptimized and not a release-size estimate. Run `./gradlew :app:connectedDebugAndroidTest --console=plain` with a booted compatible AVD for the native/UI instrumentation checks.

Do not copy absolute SDK paths into tracked Gradle or CMake files. Use the Gradle wrapper for project builds.

## Build a signed release APK

The release build reads its signing settings from `RADIANT_SIGNING_STORE_FILE`, `RADIANT_SIGNING_STORE_PASSWORD`, `RADIANT_SIGNING_KEY_ALIAS`, and `RADIANT_SIGNING_KEY_PASSWORD`. Keep the keystore and credentials outside the checkout. Never put their values in Gradle properties, source control, or shell history.

After exporting those variables in a protected local environment, build and verify:

```bash
./gradlew :app:assembleRelease --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The release APK targets ARM64. The signed release workflow uses the same environment names and reads them from repository Actions secrets.
