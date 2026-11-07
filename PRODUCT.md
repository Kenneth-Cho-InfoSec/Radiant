# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The brief targets people continuing LYNX-SHARE desktop projects and musicians creating on Android phones and tablets. **Inferred priority:** give desktop-project continuity and phone-first creation equal weight; the user did not rank them.

## Product Purpose

Radiant is an independent Android adaptation of the LYNX-SHARE digital audio workstation. It should let musicians create and edit songs on Android while retaining the upstream engine's musical workflows, editing semantics, and project data. Success means a musician can work on a phone or tablet and move supported projects between Radiant and desktop LYNX-SHARE without destructive conversion.

## Positioning

Radiant combines the existing C++20 sequencing and DSP engine with an Android-native application shell and JUCE editing surfaces, adapting a desktop DAW's workflow to touch screens without replacing its musical model.

## Operating Context

The app must work on compact phones, large phones, and tablets, in portrait and landscape, resizable and split-screen windows, with touch, stylus, keyboard, and mouse input where available. Users may open large projects, connect audio or MIDI hardware, record while the app is backgrounded, and use Android's document picker for projects and samples.

## Capabilities and Constraints

- Product requirement: Android 10 (API 29) and newer, with `arm64-v8a` as the initial release ABI.
- Product requirement: retain `.lynx` support, add interoperable `.radiant` support, and import FL Studio `.flp` projects to the extent supported by upstream.
- Product requirement: preserve unsupported plugin identifiers, state, routing, and automation references when showing a missing-plugin placeholder.
- Product requirement: support audio playback, editing, recording, mixing, automation, MIDI devices, export, autosave, and project recovery.
- Current implementation evidence: the C++ core and full upstream editor surfaces build into an Android JNI library for ARM64 and x86_64 debug. A Compose-owned `ViewGroup` hosts the JUCE peer, Material palette/dynamic colors and responsive panel navigation are wired, and SAF project staging plus media playback foreground service are implemented. API 29 launch, API 36 phone instrumentation, API 36 tablet rendering/resizing at 640, 800, and 1280 dp, emulator playback, and SAF `.radiant` save/open/resave have passed. Android instrumentation imports synthetic 137/143 BPM FLPs and verifies the 137 BPM state survives `.radiant` save/reopen; a manual DocumentsUI flow confirms the same provider-backed path. Representative FLP import remains unverified. Below 840 dp the tablet AVD uses single-editor navigation; at 840 dp and wider it restores the multi-pane workspace. Physical-device recording, low-latency audio, full import/export coverage, and broad touch editing are still unverified.
- Platform constraints: Android document URIs are not ordinary file paths; background playback must comply with foreground-service and audio-focus rules; desktop VST, CLAP, LV2, and process-sandbox binaries are not assumed to run on Android.
- Upstream revision audited: `ab256f09fdb4fd4130235b20b4841331f0a56602`.

## Brand Commitments

The user selected the name Radiant and requires Material 3 Expressive styling, a Radiant-owned palette, optional Android dynamic color, and light, dark, and system theme modes. The palette values in the port brief are provisional design tokens. Radiant is an independent fork and must not imply endorsement by the LYNX-SHARE maintainers; required copyright and license notices stay intact.

## Evidence on Hand

- Source repository: the upstream checkout and audited revision recorded in `docs/android-port-audit.md`.
- Native build and emulator evidence: `docs/test-results.md` and `docs/emulator-testing.md`.
- No physical-device audio measurements, user research, customer endorsements, or shipping release evidence are available yet. Do not invent them.

## Product Principles

1. Keep the upstream musical model and editing behavior wherever Android supports them.
2. Make phone and tablet layouts responsive while keeping the same commands and project content available.
3. Treat project files, samples, plugin state, and recovery data as user-owned and preserve them through failures and unsupported features.
4. Keep real-time audio work independent of UI, storage, JNI, and blocking operations.
5. Describe support only when builds and tests provide evidence.
