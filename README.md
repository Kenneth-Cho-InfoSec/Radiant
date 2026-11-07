# Radiant

<img src="app/src/main/res/drawable-nodpi/phoenix_app_icon.png" alt="Radiant phoenix app icon" width="192" />

Radiant is an independent Android adaptation of the open-source [LYNX-SHARE](https://github.com/Ka1-S/LYNX-SHARE) digital audio workstation. It brings the upstream C++20 song engine and JUCE editing surfaces to Android through a Kotlin and Jetpack Compose app shell.

Radiant is not affiliated with or endorsed by the LYNX-SHARE maintainers. The audited upstream revision is `ab256f09fdb4fd4130235b20b4841331f0a56602`. Copyright notices and license requirements remain in the source tree.

## Built for tablets, adapted for phones

Radiant is optimized for tablet screens, especially in landscape. At widths of 840 dp and above, the workspace can show the Browser, Playlist, and other editing panels together with tablet navigation. At narrower widths, including phones and tablet portrait, Radiant uses a compact single-editor layout with bottom navigation so you can switch between Playlist, Channel Rack, Piano Roll, Mixer, and Browser.

The app is also designed to work with touch, stylus, hardware keyboards, and mouse input where available. Broad gesture and device coverage is still in progress. A current Pixel 8 portrait capture shows the top control strip clipped at the right edge, so the compact phone layout needs further tuning.

## What you can do

- Arrange patterns and audio on the Playlist.
- Build step sequences in the Channel Rack.
- Edit notes and velocities in the Piano Roll.
- Mix tracks and use the built-in instruments and effects carried by the upstream engine.
- Browse project content, instruments, samples, and presets.
- Open and save `.lynx` and `.radiant` projects with Android's document picker.
- Import MIDI files and supported FL Studio `.flp` content.
- Use Android's playback foreground service for background playback.
- Use a Material 3 Expressive Android shell with Radiant themes and optional Android dynamic colors.

The current debug app has passed launch and lifecycle checks on Android 10/API 29 and Android 16/API 36 emulators. The Samsung SM-X218U has passed a tablet layout smoke check and a short speaker playback check. The Pixel 8 has installed and launched the current debug app. These checks do not establish full production readiness.

## Make a first loop

This short walkthrough uses the main DAW workspaces. The same workspaces are available through the compact bottom navigation on phones and through the wider panel layout on tablets.

1. Open **File > New project...** to start a session. Radiant opens the project workspace with a default pattern and transport controls.
2. Set the tempo from the BPM control in the top transport area. Start with a tempo that fits the style you want to make.
3. Open **Channel Rack**. Add an instrument or drum channel from the rack's add controls, then choose an available built-in instrument, sample, or preset.
4. Tap step cells in the channel rows to create a drum pattern. Try a kick on the main beats, a snare or clap between them, and a regular hi-hat pattern. Press Play to hear the loop.
5. Open **Piano Roll**, select the melodic channel, and tap the note grid to add notes. Drag note edges to change their lengths, and adjust velocities in the lane below the grid.
6. Open **Playlist** and place the pattern on an arrangement track. Add more patterns or extend the arrangement into sections such as intro, verse, and chorus.
7. Open **Mixer** to balance track levels and panning. Add built-in effects where available, then listen again and adjust the balance.
8. Choose **File > Save as...** and save the session as `.radiant` or `.lynx` in a location provided by Android's document picker. Use **File > Open...** to reopen it later.

To bring in a MIDI file, choose **File > Import > MIDI file...**, select a document, review the import options, and confirm. Android audio export options are present under **File > Export**, but a complete Android audio render and save workflow is still being validated.

## Project and plugin compatibility

`.lynx` and `.radiant` use the same native project serializer. Android document picker save and open flows have passed basic round-trip checks for both extensions. Populated project interchange with desktop LYNX-SHARE still needs broader validation.

FL Studio `.flp` import uses the upstream parser and import logic. A synthetic tempo-only project has been imported and saved on Android. Real projects with patterns, instruments, effects, automation, plugin state, and samples have not yet been validated comprehensively.

Built-in instruments and effects use Radiant's native engine. Desktop VST, VST3, CLAP, and LV2 plug-in binaries are not loaded on Android. Projects that refer to unsupported plug-ins may need those plug-ins on a compatible desktop system. Round-tripping all third-party plug-in state has not yet been verified.

Basic audio output has been checked on a Samsung tablet. Low-latency performance, long sessions, physical audio recording, USB or Bluetooth routing, and thermal behavior still need device testing.

## Build the Android debug app

The verified toolchain uses JDK 21, Android SDK platform 37, Android NDK `28.2.13676358`, and CMake `3.31.6`. The minimum Android version is Android 10/API 29. The debug APK includes `arm64-v8a` for devices and `x86_64` for emulator testing. The release configuration targets ARM64.

Set `JAVA_HOME` to a JDK 21 installation and set `ANDROID_HOME` and `ANDROID_SDK_ROOT` to your Android SDK directory. Then run:

```bash
./gradlew :app:assembleDebug --console=plain
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected device without clearing that app's data:

```bash
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device-serial> shell am start -n com.radiant.daw.debug/com.radiant.daw.RadiantActivity
```

The debug APK is an engineering build and is not optimized or signed for public release. See [Android build instructions](docs/build-android.md) for the complete toolchain setup and [emulator testing](docs/emulator-testing.md) for AVD configuration and coverage.

## Development status

Radiant is under active development. Tablet layout, core serialization, Android host lifecycle, MIDI import, and selected native core behavior have test evidence. Phone layout, broad touchscreen editing, representative FLP compatibility, complete audio export, physical recording, and long-session audio stability need more validation.

See [test results](docs/test-results.md), the [real-device testing journal](docs/real-device-testing-journal.md), and [known limitations](docs/known-limitations.md) for current evidence and open work.

## Project documentation

- [Android port audit](docs/android-port-audit.md)
- [Architecture](docs/architecture.md)
- [Dependency matrix](docs/dependency-matrix.md)
- [Engineering decisions and sources](docs/engineering-decisions.md)
- [Project compatibility](docs/project-compatibility.md)
- [Audio performance](docs/audio-performance.md)
- [Release checklist](docs/release-checklist.md)

## License

Radiant is distributed under the upstream [GNU AGPL-3.0 license](LICENSE), together with the separate licenses of bundled dependencies and assets. The bundled Roboto font includes its SIL Open Font License text at `app/src/main/assets/fonts/OFL.txt`. Review all notices and source obligations before distributing a build.
