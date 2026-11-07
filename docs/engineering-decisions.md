# Engineering decisions

Access date for linked technical sources: 2026-11-07.

## E-001 — Keep the upstream core and add an Android build path

**Decision:** First cross-compile existing `lynx_core`; do not rewrite sequencing or DSP. Build Android host files separately from the current desktop `juce_add_gui_app` target.

**Reason:** The checked-out root CMake already declares a JUCE-free static core. JUCE's current CMake API documentation says Android targets are unsupported, while JUCE's official README and Android tutorial describe the Projucer Android Studio exporter. A host proof is needed before merging the two build paths.

Sources: [JUCE CMake API](https://github.com/juce-framework/JUCE/blob/master/docs/CMake%20API.md), [JUCE README](https://github.com/juce-framework/JUCE/blob/master/README.md), [JUCE Android tutorial](https://juce.com/tutorials/tutorial_android_studio/).

## E-002 — Pin a verified stable Android toolchain

**Decision:** Use AGP 9.5.0, Gradle 9.6.1, Compose compiler plugin 2.4.10, Compose BOM 2026.09.00, Activity Compose 1.13.0, API 37, minimum API 29, NDK 28.2.13676358, and CMake 3.31.6 for the first build.

**Reason:** Current Android docs require at least Gradle 9.6.0 for AGP 9.5.0 and enable AGP's built-in Kotlin support. The official Gradle checksum page lists the 9.6.1 distribution hash. Compose docs list BOM 2026.09.00 and state Compose 1.12 requires compileSdk 37; Android 17/API 37 is stable. AndroidX Activity lists 1.13.0 stable. JUCE's current README lists NDK 28 for Android.

Sources: [AGP compatibility](https://developer.android.com/build/releases/about-agp), [built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin), [Gradle release checksums](https://gradle.org/release-checksums/), [Compose compiler and SDK requirements](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity), [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes), [JUCE README](https://github.com/juce-framework/JUCE/blob/master/README.md), [Android NDK/CMake setup](https://developer.android.com/studio/projects/install-ndk).

## E-003 — Use the supported Android Emulator and verify acceleration

**Decision:** Install official Android Emulator x86_64 API 29 and API 36 images and check its own `-accel-check` output before relying on virtualization.

**Reason:** `/dev/kvm` exists in the host, but emulator acceleration is not considered verified until the official emulator reports KVM usable. Android docs identify KVM as Linux VM acceleration and `-accel-check` as the verification command.

Source: [Android Emulator acceleration](https://developer.android.com/studio/run/emulator-acceleration).

## E-004 — Treat device storage as document streams

**Decision:** Implement Android document access through the Storage Access Framework and persistent URI permissions; stage only where the existing random-access APIs require local files.

**Reason:** The current app uses `juce::File` and path strings, while SAF provides `content://` document URIs. Android's security model does not make every URI an ordinary filesystem path.

Source: [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider).

## E-005 — Keep Android audio on JUCE first

**Decision:** Start with JUCE's Android audio backend. Do not add a second direct Oboe backend until callback and underrun measurements show a need.

**Reason:** Avoid two device/lifecycle paths before the current engine callback is validated. Profile physical hardware because emulator audio does not establish production latency.

Source: [JUCE Android tutorial](https://juce.com/tutorials/tutorial_android_studio/).

## E-006 — Do not treat desktop plugin binaries as Android plugins

**Decision:** Preserve external-plugin identity/state and show a missing-plugin placeholder when no compatible Android implementation exists.

**Reason:** The model already stores original plugin format, IDs, state, paths, and routing metadata. Desktop VST/CLAP/LV2 binaries and the desktop process sandbox are platform-specific.

Source: upstream `src/core/Song.h`, `src/core/PluginSubstitutes.cpp`, `src/engine/PluginHost.cpp`, and root `CMakeLists.txt` at the audited revision.

## E-007 — Select x87 paths by floating-point format

**Decision:** Enable the upstream inline x87 math only when the target is x86 and `long double` has a 64-bit mantissa. Android x86_64 uses its existing portable math branches.

**Reason:** Android NDK x86_64 uses IEEE binary128 `long double`, unlike the 80-bit extended format assumed by the upstream x87 register constraints. NDK builds of `lynx_core` and the test executable now succeed for both Android ARM64 and x86_64; the portable x86_64 build also passes the complete emulator suite.

Sources: [Android NDK ABI documentation](https://developer.android.com/ndk/guides/abis.html), upstream `src/core/GrossBeat.cpp` x87 feature guard pattern, and `src/core/X87Math.h`.

## E-008 — Embed a JUCE peer inside the Compose view tree

**Decision:** Prototype a JUCE component whose Android peer attaches to a Compose-owned Android `ViewGroup`; keep Compose as the Activity and app shell.

**Reason:** JUCE 8.0.4's `Component::addToDesktop` accepts `nativeWindowToAttachTo`, and its Android implementation adds the JUCE peer view to a supplied `ViewGroup`. This is a source-supported route around the unsupported Android `juce_add_gui_app` target, but still needs a built and exercised lifecycle proof.

Source: JUCE 8.0.4 `modules/juce_gui_basics/components/juce_Component.h` and `modules/juce_gui_basics/native/juce_Windowing_android.cpp`, pinned at commit `51d11a2be6d5c97ccf12b4e5e827006e19f0555a`.

## E-009 — Keep the Android debug ABI separate from the release ABI

**Decision:** Include x86_64 only in the debug variant for emulator tests; release packages ARM64 only.

**Reason:** The API 36 emulator runs x86_64. Android does not automatically execute an ARM64-only library on that image, and emulator success must not be reported as ARM64 runtime validation.

Source: [Android ABI documentation](https://developer.android.com/ndk/guides/abis.html).

## E-010 — Use SAF contracts with private staging for project files

**Decision:** Kotlin owns OpenDocument/CreateDocument and URI grants. It stages supported projects to app-private files before the existing C++ loader reads them, then copies serializer output back through the provider stream. `.radiant` and `.lynx` use the same version 1 project data.

**Reason:** The project loader and ZIP/sample code expect seekable local paths. A content URI is a provider handle, not a filesystem path. API 36 manual verification exposed that `application/json` made DocumentsUI append `.json`; the contract now uses an unregistered Radiant project MIME type, and the created file remains `Untitled.radiant`. Open, resave and changed provider size were observed on the emulator.

Sources: [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider), [CreateDocument contract](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.CreateDocument).

## E-011 — Use a media playback foreground service and audio focus

**Decision:** When transport starts, Kotlin starts a `mediaPlayback` foreground service, declares the target-required permissions and notification, requests audio focus, and stops on focus loss or stopped transport.

**Reason:** Android 14 requires declared foreground-service types and associated permissions. Newer Android versions restrict background FGS starts and audio focus, so the service starts from the visible Activity after the user starts transport. The API 29 and API 36 emulator tests now verify that transport and the foreground service remain active after Home; API 36 playback also continued with the screen asleep. Android 17/API 37 additionally requires a while-in-use-capable foreground service for background audio when targeting API 37. Radiant starts this service while its Activity is visible, but no API 37 system image is installed here, so that behavior still needs runtime verification. Physical interruption behavior is also unmeasured.

Sources: [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes), [background launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [audio focus](https://developer.android.com/media/optimize/audio-focus), [Android 17 background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio). Accessed 2026-11-07.

## E-012 — Bundle JUCE's Roboto fallback asset

**Decision:** Include the Google Fonts Roboto variable TTF as the extensionless Android asset `fonts/Roboto`, with its OFL-1.1 license in the same directory.

**Reason:** API 29 app launch rendered, but JUCE's Android font path tried `AssetManager.open("fonts/Roboto")`, emitted `FileNotFoundException`, then fell back to a system font file. Providing the expected asset avoids exception-driven fallback and ensures the font is available to JUCE. The official Google Fonts metadata identifies Roboto as OFL licensed.

Source: [Google Fonts Roboto metadata](https://github.com/google/fonts/blob/main/ofl/roboto/METADATA.pb), [Roboto OFL license](https://github.com/google/fonts/blob/main/ofl/roboto/OFL.txt). Asset SHA-256: `d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134`.

## E-013 — Tie Android recovery markers to the hosted editor lifecycle

**Decision:** Start the existing native session marker when the JUCE editor attaches. On a genuinely finishing Activity, write a final native autosave before releasing the marker; retain the marker if the project is still modified or the snapshot fails. Keep the session open across configuration changes and while background playback continues. A later process start uses the existing backup-candidate flow.

**Reason:** Android may kill a process without calling `onDestroy()`, while configuration changes do call it before recreating the Activity. The native `MainComponent` outlives Activity instances, so Android must explicitly own session begin/end. Tablet instrumentation passed clean-session and Activity-recreation assertions. A manual force-stop test left the marker intact; a staged `.lynx` backup appeared in the recovery dialog and reopened at 137 BPM. Naturally timed periodic autosave under prolonged process death still needs separate validation.

Sources: [Android Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle), [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle). Accessed 2026-11-07.

## E-014 — Request microphone access only for explicit audio input

**Decision:** Keep playback permission-free. Ask for `RECORD_AUDIO` when the user arms audio recording or explicitly opens an audio input; after permission is granted, run the `microphone` foreground-service type while capture is armed and remove it when disarmed. A denied permission leaves recording disarmed and reports the issue in the mixer.

**Reason:** Android treats microphone use as a runtime permission and requires the matching foreground-service type and permission for background capture on current releases. JUCE's Android `RuntimePermissions` API provides the request callback used by the native engine. Fresh API 29 and API 36 emulator tests exercised their version-specific permission prompts and foreground-service arm/disarm behavior. API 29 uses a generic foreground service; API 30+ also activates the microphone service type. These tests verify permission and service wiring, not captured audio samples or a physical input route.

Sources: [JUCE RuntimePermissions](https://docs.juce.com/master/classjuce_1_1RuntimePermissions.html), [Android foreground-service types](https://developer.android.com/about/versions/14/changes/fgs-types-required), [foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes), [Android audio recording](https://developer.android.com/media/platform/mediarecorder). Accessed 2026-11-07.

## E-015 — Separate arrangement long-press from clip dragging on Android

**Decision:** On Android touch sources, begin a 500 ms hold timer when a clip move starts. Cancel it when the pointer moves more than 8 JUCE coordinate units; continue the ordinary drag. If the pointer remains over the same clip until release, holding the body deletes that clip and holding its title opens the existing clip action menu.

**Reason:** Android gesture guidance distinguishes long-press from drag and cancels a hold once movement exceeds touch slop. JUCE exposes touch source identity, drag state, and screen position while Android touch is delivered through its mouse input path. Testing on API 36 QEMU confirmed body deletion, title-menu opening, and drag continuation after movement.

Sources: [Android Compose gesture recognition](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures), [Android ViewGroup touch slop](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup), [JUCE MouseInputSource](https://docs.juce.com/master/classjuce_1_1MouseInputSource.html). Accessed 2026-11-07.

## E-016 — Keep workspace popup menus in the JUCE component tree on Android

**Decision:** Create panel menus with `PopupMenu::Options::withParentComponent(&workspace)` as well as their target component. This keeps the popup attached to the workspace component tree on Android.

**Reason:** JUCE's Android native popup peer uses a separate application window for an unparented popup. Android marks that popup window not touch-modal, so a touch landing on a menu action can also reach the editor behind it. Parenting the popup avoids that separate-window route. On API 36, closing the Channel Rack and Piano Roll from their menus hid each panel without creating a Playlist clip on the same touch.

Sources: [JUCE PopupMenu::Options](https://docs.juce.com/master/classjuce_1_1PopupMenu_1_1Options.html), [Android WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams). Accessed 2026-11-07.

## E-017 — Use scaled peer bounds for Android JUCE touch containment

**Decision:** Apply a narrow configure-time compatibility patch to the pinned JUCE Android peer so `AndroidComponentPeer::contains()` checks peer-space touch coordinates against `getBounds()` (scaled peer dimensions), not the editor component's unscaled dimensions. CMake fails configuration if the pinned upstream implementation no longer matches the expected source block.

**Reason:** At global UI scale 1.1, JUCE converted the touch to peer space (for example, y=563.2) while testing it against the 540-unit unscaled component height, rejecting a valid lower-area button tap. Checking the peer's 594-unit height accepts the same point and leaves JUCE's existing scaling conversion and hit testing intact. On API 36 QEMU, the New Project Create control received mouse down/up and its callback, then closed the dialog and opened the project. The same maintenance patch rebuilt the ARM64 and x86_64 debug libraries. ARM64 runtime behavior is not established by this x86_64 emulator result.

Sources: [pinned JUCE Android windowing source](https://github.com/juce-framework/JUCE/blob/51d11a2be6d5c97ccf12b4e5e827006e19f0555a/modules/juce_gui_basics/native/juce_Windowing_android.cpp), [CMake FetchContent](https://cmake.org/cmake/help/latest/module/FetchContent.html). Accessed 2026-11-07.

## E-018 — Parent Android popup menus to the editor view

**Decision:** For Android popup menus without an explicitly selected parent component, use the target component's top-level component as the JUCE menu parent. Preserve explicit parents and retain the existing desktop behavior.

**Reason:** On the Samsung SM-X218U, a visible child item in File > Import was hit-tested as a row in its parent menu. With the Android popup attached to the editor's component tree, the same injected touch opened the MIDI document picker. The Export > MIDI action then opened the save picker through its nested menu as well.

Source: [JUCE PopupMenu::Options](https://docs.juce.com/master/classjuce_1_1PopupMenu_1_1Options.html) (documents embedding a menu in a component). Accessed 2026-11-07.

## E-019 — Read and write Android SAF documents through document streams

**Decision:** Use `FileChooser::getURLResult()` for chooser results. For Android `content://` documents, create JUCE `AndroidDocument` input/output streams; read parser input asynchronously into a bounded memory buffer and stream MIDI output asynchronously. Continue using local-file streams on desktop.

**Reason:** Android's system picker returns document URIs rather than filesystem paths, so `getResult()` and `URL::getLocalFile()` can be empty. On the Samsung tablet, the selected Final Countdown MIDI reached the Piano Roll import dialog with its track names, and exporting created `Radiant.mid` (199 bytes) in Downloads.

Sources: [JUCE AndroidDocument](https://docs.juce.com/master/classjuce_1_1AndroidDocument.html), [Android access documents and other files](https://developer.android.com/training/data-storage/shared/documents-files). Accessed 2026-11-07.

## E-020 — Open JUCE submenus on Android touch release

**Decision:** In the Android CMake configuration, apply a guarded patch to the pinned JUCE `PopupMenu::mouseUp()` so a touch release on an item with a submenu opens it directly. Keep JUCE hover behavior for mouse and pen input.

**Reason:** On the Samsung SM-X218U, `adb input tap` on File > Export left the parent menu open, while a stationary 180 ms touch gesture opened the submenu. JUCE's pinned implementation opens submenus from a hover timer, which assumes a pointer remains over the row. Touch does not provide that persistent hover. The patch is limited to touch input and fails configuration if the pinned source context changes.

Sources: [JUCE PopupMenu implementation](https://github.com/juce-framework/JUCE/blob/51d11a2be6d5c97ccf12b4e5e827006e19f0555a/modules/juce_gui_basics/menus/juce_PopupMenu.cpp), [JUCE MouseInputSource](https://docs.juce.com/master/classjuce_1_1MouseInputSource.html). Accessed 2026-11-07.

## E-021 — Use the phoenix image as an adaptive and density launcher icon

**Decision:** Keep the uploaded artwork unchanged as the adaptive icon background, use a transparent foreground layer so the image fills the system mask, and ship density-specific launcher PNGs in the mipmap resources.

**Reason:** Android launchers apply device-specific masks to adaptive icons, while mipmap density resources provide legacy and launcher resource variants. The Samsung app drawer rendered the artwork after the in-place install, and the Pixel 8 package installed and launched with the new resources.

Sources: [Create app icons](https://developer.android.com/studio/write/create-app-icons), [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive). Accessed 2026-11-07.
