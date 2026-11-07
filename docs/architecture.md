# Radiant architecture

Status: Android debug host builds for ARM64 plus x86_64. API 36 phone/tablet emulator checks cover lifecycle, project serialization, SAF flows, and tablet width-class changes. A Samsung SM-X218U physical ARM64 tablet passed launch, lifecycle, portrait/landscape layout, touch smoke checks, and short speaker playback; broader physical-device and full acceptance coverage remain outstanding.

## Layers

- **Android shell (Kotlin/Compose):** application navigation, settings, permissions, SAF picker, theme and dynamic colors, Activity lifecycle, and foreground playback controls.
- **JUCE editing surface:** Playlist, Piano Roll, Channel Rack, Mixer, Browser, built-in instrument/effect editors, and the existing editing semantics. Android host ownership must be established once; Compose and JUCE must not mutate the song independently.
- **Native engine (`libradiant.so`):** `lynx_core`, JUCE audio/UI integration, Android adapters, and a narrow command-oriented JNI bridge. Long-running work stays off the UI and audio callback threads.
- **Storage adapter:** maps SAF URIs and app-private paths to readable/writable streams or staged random-access files. Existing project serialization stays versioned and shared.

## Current upstream boundaries

The root CMake file separates the JUCE-free `lynx_core` from the desktop JUCE application. Android CMake builds the existing app/editor sources into one shared library. Kotlin owns the Activity/Compose shell and creates the Android peer under a host `ViewGroup`; `MainComponent` and `Workspace` own the actual editing panes. Android build integration is guarded so the desktop target remains available.

The standard JUCE CMake app helper does not support Android, so this target uses a custom host around JUCE's Android peer. API 36 phone instrumentation verified initialization, rendering, resize/orientation and Activity recreation. Hardware keyboard/IME, multi-window, background audio focus and full cleanup stress still need broader exercise.

## Ownership and threading

Kotlin owns Android lifecycle and document permissions. C++ owns the song model, engine state, and audio graph. JNI calls enqueue bounded commands or perform work on a non-real-time thread. The real-time callback never calls JNI or performs document I/O. Existing `SnapshotExchange` is an immutable UI-to-audio hand-off candidate; it still needs Android stress testing.

## Storage and recovery

Preserve `.lynx` data and serialized plugin state. `.radiant` shares the upstream version 1 serializer. SAF project import/save streams through app-private staging and persists selected document URIs. Autosave uses the upstream private backup/recovery folder and runs away from the audio callback. The Android host begins the native session marker when the JUCE peer attaches. Activity recreation preserves it; a clean finish writes a final backup before clearing the marker, while modified state, failed writes, or process death leave recovery available. Tablet force-stop testing confirmed the recovery prompt and reopening a 137 BPM snapshot. Periodic autosave interval coverage and low-memory process kills remain to be tested. Provider write failures must leave the in-app staged file available and the project marked dirty.

## Responsiveness

Use available window size and insets. Below 840 dp, show one focused editor with bottom navigation. At 840 dp and wider, use a navigation rail and restore the multi-panel workspace. Existing `Workspace` layout state remains the semantic source for panel visibility/focus, with Android-specific layout rules layered around it. At 640x400 dp, the editor has limited vertical space because the Android shell and navigation occupy fixed height.
