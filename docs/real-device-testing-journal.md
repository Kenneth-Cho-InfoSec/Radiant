# Real-device testing journal

## Cycle 1 — Arrangement clip hold/drag regression

**Date:** 2026-11-07  
**Build:** `0.1.0-debug`, Android API 36; source changes in `src/ui/PlaylistComponent.cpp` and `.h`  
**Device:** Samsung SM-X218U (ARM64, API 36) and `emulator-5554` (x86_64, API 36)

### Change

Added Android touch hold recognition for Playlist clips. A stationary 500 ms hold on a clip body deletes it; holding the title opens the existing clip menu. Movement beyond 8 JUCE coordinate units cancels the hold and leaves ordinary dragging active.

### Evidence

- Android debug APK build succeeded for ARM64 and x86_64.
- On QEMU, ADB-generated touch input deleted a body-held test clip, moved a clip when dragged beyond the threshold, and opened the action menu when holding the title. No fatal exception, ANR, or SIGSEGV appeared in the checked logcat tail.
- These QEMU interactions were scripted input and are useful regression evidence, but are not natural touchscreen testing.
- Physical Samsung gesture behavior remains **unverified**. An exploratory ADB-injected touch sequence changed the visible arrangement; the prior four-clip project state was restored from its 14:29 autosave. No microphone permission was granted. No further synthetic touch input will be sent to the tablet.
- The latest APK was installed in place with `adb install -r` and Radiant launched successfully; app data was preserved. Diagnostic log checks found no fatal exception, ANR, or SIGSEGV. The relaunch shows Pattern view selected; the saved four-clip Song arrangement remains in the recovered project state.
- No interactive GUI-control tool is available in this session. Use the tablet's actual touchscreen for the next physical pass, with a disposable or backed-up project.

### Next cycle

1. Install the current build without clearing user data.
2. Use a backed-up test project and actual finger input to verify body long-press delete, title long-press menu, and drag cancellation.
3. Verify undo/recovery after delete, then exercise neighboring Playlist gestures and window controls.
4. Capture device logs and screenshots through diagnostic-only tooling; record any defect and retest after a fix.

## Cycle 2 — Arrangement touch behavior and panel-menu click-through

**Date:** 2026-11-07  
**Build:** final non-instrumented `0.1.0-debug`; ARM64 and x86_64 native libraries  
**Devices:** Samsung SM-X218U (API 36, ARM64, 1920x1200) and `emulator-5554` (API 36, x86_64, 2560x1600 at density 320)

### Change

Parent workspace panel menus to the Workspace JUCE component so Android routes the Close action to the menu without delivering the same touch to the Playlist underneath.

### Evidence

- The Samsung Channel Rack and Piano Roll each closed from their own panel menu; their Close taps landed over empty Playlist rows and neither created a clip. QEMU reproduced the Channel Rack case. The existing Track 1 clips on QEMU remained unchanged.
- On the Samsung, an injected stationary body hold deleted the disposable clip, a hold that moved became a drag to Track 2, and a title hold opened the clip action menu. The test clip was removed afterward.
- `adb logcat` had no new fatal exception, ANR, or SIGSEGV. `dumpsys activity exit-info` showed prior package-update exits, not a new crash record. Returning to the app from Android's launcher while the disposable project was modified surfaced the recovery prompt; this is recorded as lifecycle behavior, not counted as a gesture failure.
- ADB was used for install, screenshots and diagnostics; all app navigation and feature interaction used injected tap, swipe or key input. Radiant was launched by tapping its app icon, not by an Activity launch command.
- Screenshots are stored under `artifacts/radiant-samsung-channel-rack-close-final.png`, `artifacts/radiant-qemu-channel-rack-close-final.png`, and the four `artifacts/radiant-samsung-arrangement-*-final.png` files.

### Result

Panel Close click-through and the requested arrangement hold/drag paths passed on the tested Samsung and QEMU configurations. This does not replace physical-finger gesture validation or confirm ARM64 behavior on an emulator.

## Cycle 3 — Arrangement gesture retest

**Date:** 2026-11-07  
**Build:** Current installed debug APK; no source changes during this retest  
**Device:** Samsung SM-X218U (Android 16/API 36, ARM64, 1920x1200)

### Evidence

- Created a disposable Pattern 1 clip with a tap at `(900,442)`.
- Dragged from `(950,450)` to `(1100,450)` over 500 ms. The clip moved along the arrangement, confirming the hold detector did not steal a drag.
- A short tap on the moved clip left it present.
- A 900 ms title hold at `(1050,418)` opened the clip action menu.
- A 900 ms body hold at `(1100,450)` deleted the clip and showed “Clip deleted / Undo from the Edit menu”. The arrangement was empty afterward.
- Inspected the last 2,500 logcat entries: no fatal exception, ANR, or native crash. `dumpsys activity exit-info` showed no new crash entry; the listed native crash was historical.
- ADB was used for injected tap/swipe/key input, screenshots and diagnostics only. The app was launched from its icon; no Activity launch, `run-as`, or app shell commands were used.

Screenshots: `artifacts/radiant-cycle3-samsung-arrangement-drag.png`, `artifacts/radiant-cycle3-samsung-arrangement-short-tap.png`, `artifacts/radiant-cycle3-samsung-arrangement-title-menu.png`, and `artifacts/radiant-cycle3-samsung-arrangement-hold-delete.png`.

### Result

The existing Android arrangement gesture implementation passed on the connected Samsung tablet. No code change was warranted by this retest. The test used injected input rather than a natural-finger interaction; the existing QEMU result remains the separate x86_64 emulator check.

## Cycle 4 — Clean install and floating-window audit (active)

**Date:** 2026-11-07 20:40 local  
**Build:** `0.1.0-debug`; initial APK SHA-256 `5bf966c234c625a57ca1ddbf8c47aa8e33e45cd1a0e6b29eb55d2c2eade3d76d`, window-fix APK SHA-256 `c3f31c3447cb352b1b1e8df4a97966241cbc4af4c8177db005d881753cb4998e`  
**Device:** Samsung SM-X218U, Android 16/API 36, ARM64, 1920x1200

### Setup and launch

- Archived the pre-clean app data, including the recovery project, outside the repository in a host-private backup location. Its local path and content hash are omitted from this shared journal.
- Uninstalled `com.radiant.daw.debug`, built the APK for ARM64 and x86_64, installed it, and launched `RadiantActivity`. The clean workspace rendered at 1920x1200. The later rebuild and reinstall with the window fix also succeeded.
- Checked the 1,000-entry logcat capture after launch for fatal exceptions, ANRs, and SIGSEGV; none matched. The clean app has no recovered project because this cycle starts from a clean install; the earlier state remains in the host archive.
- Screenshot: `artifacts/radiant-cycle4-clean-launch.png`.

### Window audit and fix

- Static inventory found `SettingsWindow` in `src/ui/ChannelSettings.cpp` constructing a `DocumentWindow` without calling the shared Android title-bar setup. Other inventoried floating windows call `configureSecondaryWindowControls()`.
- Added that shared setup after preserving the existing native-title-bar preference. On Android it replaces the unavailable native caption with Radiant's JUCE close/minimize controls; on desktop the helper retains the native title bar.
- `:app:assembleDebug` succeeded for ARM64 and x86_64, and the resulting APK was installed and launched.

### Physical interaction status

- The user confirmed a natural touchscreen pass for Channel Rack → Channel Settings: minimize, restore, and close all worked.
- On the resumed test turn, the user reconfirmed that these controls worked. ADB diagnostics then showed the tablet in `Dozing`, with NotificationShade focused and Radiant's process still alive; the last 300 logcat entries had no fatal exception, ANR, or native-crash match. Asked the user to wake the tablet and retest File → Import and File → Export on the patched build. No synthetic input was sent.
- The user reported reawakening the tablet. Diagnostics now show `Awake`, Radiant focused, and its process alive. Pulled the installed `base.apk` for comparison: its SHA-256 exactly matches the host debug APK (`0e744956e8200fbc3e1ac3910122c2b801c87e9fc3f12bf356c2f6a0778ed010`). Screenshot `artifacts/radiant-cycle4-wake-main-screen.png` (SHA-256 `bdf91ff1b517e0e4737dc7fd6c5763d8e8cb8f587b9efe3ffe0498c61ea8d6d8`) shows the main workspace with menus closed; no result for the requested Import/Export retest has been reported yet.
- Re-ran the source inventory after the fix: all 16 `DocumentWindow` constructors in `src/ui` and `src/platform` now call `configureSecondaryWindowControls()`. This is a static inventory only; it does not replace touch testing of each window type.
- The user reported that File → Import and File → Export still select the row above on the tablet. Source inspection confirmed the actions themselves are wired to the MIDI picker and audio export dialog; the defect is in Android popup row hit-testing.
- Changed the Android popup parent selection to anchor every level of the menu chain to the top-level target component, avoiding an intermediate view coordinate origin. Desktop behavior is unchanged.
- Rebuilt successfully with `JAVA_HOME=$JAVA_HOME`, `ANDROID_HOME=$ANDROID_HOME`, and `ANDROID_SDK_ROOT=$ANDROID_HOME`; ARM64 and x86_64 targets completed. Installed in place with `adb install -r`, APK SHA-256 `0e744956e8200fbc3e1ac3910122c2b801c87e9fc3f12bf356c2f6a0778ed010`.
- Relaunched Radiant on the tablet. The last 2,000 logcat lines showed no fatal exception, ANR, SIGSEGV, or fatal signal.
- Import/Export touch behavior on this patched build is **pending the user's physical retest**. No ADB input events were sent. Plugin switching and the broader production workflow also remain untested.

### ARM64 native core checks (no GUI interaction)

- Rebuilt `lynx_tests` for Android ARM64 with `PATH=$ANDROID_HOME/cmake/3.31.6/bin:$PATH $ANDROID_HOME/cmake/3.31.6/bin/cmake --build build/android-arm64 --target lynx_tests --parallel 2` (`BUILD SUCCESSFUL`). The source timestamp had been older than its object file, so CMake initially reported no work; touching `tests/tests.cpp` without changing its contents forced the current source to compile.
- Added two portable importer regressions in `tests/tests.cpp`: `midi_import_creates_track_channels_and_realigns` covers track grouping, time signature, velocity-zero note-off, CC7, and realignment; `midi_import_into_piano_roll_channel_replaces_and_blends` covers replacement in the selected channel, preserving other channels, blend behavior, and returned note indices.
- Rebuilt the ARM64 runner from that source and reran the full selected subset on the Samsung SM-X218U, Android 16/API 36, ARM64. Each case ran with `adb shell 'LYNX_TEST=<test-name> /data/local/tmp/radiant_lynx_tests'`. All passed: `scheduler_places_events_at_exact_samples` (2 checks), `playlist_clip_edges_and_time_selection` (15), `mixer_routes_mutes_solos_and_meters` (14), `mixer_track_eq_defaults_and_boost` (5), `next_free_insert_and_flp_mixer_import` (5), `audio_clips_trigger_cut_and_resume` (7), `automation_curve_and_playback` (9), `export_song_adds_silent_tail_and_never_restarts` (7), `plugin_factories_create_reuse_and_fallback` (261), `flp_import_maps_channels_and_rescales` (10), `missing_files_folder_search` (8), `project_info_history_and_flp_import` (4), `midi_import_creates_track_channels_and_realigns` (13), and `midi_import_into_piano_roll_channel_replaces_and_blends` (10): **370 checks, zero failures**.
- An initial mistyped mixer filter selected zero tests and is excluded from the total; the correctly named test above ran and passed. The binary was removed from `/data/local/tmp` after testing. Radiant process 8954 remained alive; afterward the device was `Dozing` with NotificationShade above Radiant's retained task. Filtered logcat showed no fatal exception, ANR, or native crash. Battery was 100% and temperature 25.0 °C. These core tests do not exercise the JUCE Android UI or Android audio backend.
- The portable tests start from `ParsedMidi`; they do not cover `MidiFileIo::parseBytes` or Storage Access Framework document access. The patched File Import/Export menu still needs the user's natural-touch retest.

## Cycle 5 — Phoenix launcher icon and Pixel 8 install

**Date:** 2026-11-07 22:29 to 22:34 local  
**Build:** `0.1.0-debug`; APK SHA-256 `f72c8d706c1363cf073b96b405d18770df23e405f874a54f2e477be34497fa87`  
**Devices:** Samsung SM-X218U, Android 16/API 36, ARM64; Pixel 8, Android 17/API 37, ARM64; `Radiant_Tablet_API36`, Android 16/API 36, x86_64

### Change and verification

- Used the user-supplied phoenix artwork as the launcher image. Added density mipmaps at 48, 72, 96, 144, and 192 pixels, plus an adaptive icon resource that uses the original image as its background layer. The uploaded source remains unchanged.
- `JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME ./gradlew assembleDebug` succeeded for ARM64 and x86_64. APK resources contain the adaptive icon XML and all five density PNGs.
- Reinstalled on the Samsung with `adb install -r`, without uninstalling or clearing app data. `firstInstallTime` remained 20:38:31 and `lastUpdateTime` changed to 22:28:59. Screenshot `artifacts/radiant-icon-installed-tablet.png` shows the phoenix icon in the app drawer. Radiant launched to the DAW screen; screenshot `artifacts/radiant-icon-launch-tablet.png`.
- The user authorized USB debugging on the Pixel 8. `adb install -r` returned `Success`; the device reported API 37 and `arm64-v8a`. RadiantActivity became the top resumed Activity with process 18923. Screenshot `artifacts/radiant-icon-pixel8.png` shows the app at 1080x2400 and density 420.
- The Pixel 8 portrait screenshot exposes a layout defect: the top control strip is clipped at the right edge. This cycle recorded the issue but did not modify the phone layout. A follow-up Pixel retest will be needed after a layout fix.
- The tablet AVD booted with `-gpu host` after a default auto-renderer attempt crashed with SIGSEGV following a SELinux `execheap` denial on the render thread. Host SELinux policy was not changed. The API 36 x86_64 tablet AVD installed and launched the current APK at 2560x1600, density 320. Screenshot `artifacts/radiant-icon-qemu-tablet.png` shows the expanded workspace.
- No in-app touchscreen input was injected on the Samsung, Pixel, or AVD during this cycle. The app was launched for smoke checks, and screenshots/logs were collected diagnostically. The Pixel was disconnected before a final filtered logcat check, so no Pixel crash-log conclusion is recorded.

### Next checks

1. Fix the Pixel 8 portrait top control clipping and verify it on the phone.
2. Retest File Import and Export with natural touchscreen input when the Samsung is connected.
3. Continue tablet plugin-window, audio, and project workflow checks.
