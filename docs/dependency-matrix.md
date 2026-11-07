# Dependency matrix

Versions below are selected for the first Android app build as of 2026-11-07. CMake/NDK and Gradle/JUCE/Compose pins have built successfully; emulator coverage remains x86_64 only.

| Dependency | Upstream/current choice | Android status |
|---|---|---|
| C++ standard | C++20 | `lynx_core` and `lynx_tests` compile with NDK Clang 19 for `arm64-v8a` and `x86_64`; the x86_64 full test suite passed in API 36 emulator. |
| JUCE | 8.0.4, pinned by root `FetchContent` | Android CMake shared target and Compose-hosted peer build and run on API 36; plugin formats remain disabled on Android. |
| Android Gradle Plugin | 9.5.0 | Pinned and used to produce the debug APK; supports API 37, requires Gradle 9.6.0, JDK 17 minimum, NDK 28.2 default. |
| Gradle | 9.6.1 | Pinned wrapper and used for successful APK builds. Wrapper checksum is pinned from Gradle's published checksum page. |
| Kotlin | 2.4.10 Compose compiler plugin | AGP 9.5 built-in Kotlin is enabled; Compose sources compile. |
| Compose BOM | 2026.09.00 | Pinned and used by the installed Android app. |
| Compile/target SDK | API 37 | Android 17 is stable; the current Compose BOM's Compose 1.12 libraries require compileSdk 37 and AGP 9. |
| Minimum SDK | API 29 | Product requirement. |
| NDK | 28.2.13676358 | AGP 9.5.0 default and JUCE's current README-tested NDK major. |
| CMake | 3.31.6 | Available from Android SDK package repository; exceeds root's 3.22 minimum. |
| Production ABI | `arm64-v8a` | Product requirement. |
| Emulator ABI | `x86_64` debug build | Debug-only ABI filter; release remains `arm64-v8a`. This does not validate ARM64 execution. |
| Emulator images | API 29 and API 36, Google APIs, x86_64 | API 36 phone and tablet plus API 29 phone booted with KVM and rendered the app. |
| Bundled core dependencies | PocketPy, Monocypher, LAME, WavPack, libogg/libvorbis, stb/minimp3 and other in-tree code | Compile/license audit pending. |
| JUCE Android fallback font | Roboto variable font from `google/fonts` (`OFL-1.1`) | Packaged at `app/src/main/assets/fonts/Roboto` for Android API 28 and earlier JUCE font fallback; its OFL text is included alongside it. |
| Android audio | JUCE Android backend with Oboe | Opens emulator output at 48 kHz; the API 36 phone reported a 4800-sample block. This is emulator-specific and does not establish real-device latency. |

Do not upgrade these pins without recording the compatibility evidence in `engineering-decisions.md`.
