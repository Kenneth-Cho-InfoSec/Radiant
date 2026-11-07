cmake_minimum_required(VERSION 3.22)
project(RadiantAndroid LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LYNX_BUILD_APP OFF CACHE BOOL "Do not build the desktop application from Android" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../ ${CMAKE_CURRENT_BINARY_DIR}/lynx)
set(RADIANT_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")

include(FetchContent)
set(JUCE_MODULES_ONLY ON CACHE BOOL "Configure JUCE modules without its desktop app helpers" FORCE)
FetchContent_Declare(
    JUCE
    GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
    GIT_TAG 51d11a2be6d5c97ccf12b4e5e827006e19f0555a
    GIT_SHALLOW FALSE)
FetchContent_MakeAvailable(JUCE)

# JUCE's AndroidComponentPeer::contains receives peer-space coordinates after
# JUCE's global-scale conversion. The upstream implementation compares those
# coordinates against the unscaled component dimensions, which rejects taps
# near the bottom/right edge when the global UI scale is greater than 1.0.
# Keep this narrow compatibility patch tied to the pinned JUCE revision above.
if(ANDROID)
    set(RADIANT_JUCE_ANDROID_PEER_SOURCE
        "${juce_SOURCE_DIR}/modules/juce_gui_basics/native/juce_Windowing_android.cpp")
    if(NOT EXISTS "${RADIANT_JUCE_ANDROID_PEER_SOURCE}")
        message(FATAL_ERROR "Could not find JUCE Android peer source: ${RADIANT_JUCE_ANDROID_PEER_SOURCE}")
    endif()

    file(READ "${RADIANT_JUCE_ANDROID_PEER_SOURCE}" _radiant_juce_android_peer_source)
    set(_radiant_juce_peer_contains_upstream [=[    bool contains (Point<int> localPos, bool trueIfInAChildWindow) const override
    {
        return isPositiveAndBelow (localPos.x, component.getWidth())
            && isPositiveAndBelow (localPos.y, component.getHeight())
            && ((! trueIfInAChildWindow) || view.callBooleanMethod (ComponentPeerView.containsPoint,
                                                                    (float) localPos.x * scale,
                                                                    (float) localPos.y * scale));
    }]=])
    set(_radiant_juce_peer_contains_fixed [=[    bool contains (Point<int> localPos, bool trueIfInAChildWindow) const override
    {
        const auto peerBounds = getBounds();
        return isPositiveAndBelow (localPos.x, peerBounds.getWidth())
            && isPositiveAndBelow (localPos.y, peerBounds.getHeight())
            && ((! trueIfInAChildWindow) || view.callBooleanMethod (ComponentPeerView.containsPoint,
                                                                    (float) localPos.x * scale,
                                                                    (float) localPos.y * scale));
    }]=])

    string(FIND "${_radiant_juce_android_peer_source}" "${_radiant_juce_peer_contains_fixed}" _radiant_juce_peer_fixed_pos)
    if(_radiant_juce_peer_fixed_pos EQUAL -1)
        string(FIND "${_radiant_juce_android_peer_source}" "${_radiant_juce_peer_contains_upstream}" _radiant_juce_peer_upstream_pos)
        if(_radiant_juce_peer_upstream_pos EQUAL -1)
            message(FATAL_ERROR "JUCE Android peer contains() changed; review the Radiant compatibility patch")
        endif()

        string(REPLACE "${_radiant_juce_peer_contains_upstream}"
                       "${_radiant_juce_peer_contains_fixed}"
                       _radiant_juce_android_peer_source
                       "${_radiant_juce_android_peer_source}")
        file(WRITE "${RADIANT_JUCE_ANDROID_PEER_SOURCE}" "${_radiant_juce_android_peer_source}")
    endif()

    # JUCE opens a submenu after its hover timer sees the pointer over the parent row. Android touch has no persistent
    # hover, so a normal tap on a submenu row can leave the parent menu open. Open the child menu on touch release.
    set(RADIANT_JUCE_ANDROID_POPUP_SOURCE
        "${juce_SOURCE_DIR}/modules/juce_gui_basics/menus/juce_PopupMenu.cpp")
    if(NOT EXISTS "${RADIANT_JUCE_ANDROID_POPUP_SOURCE}")
        message(FATAL_ERROR "Could not find JUCE popup menu source: ${RADIANT_JUCE_ANDROID_POPUP_SOURCE}")
    endif()

    file(READ "${RADIANT_JUCE_ANDROID_POPUP_SOURCE}" _radiant_juce_android_popup_source)
    set(_radiant_juce_touch_submenu_upstream [=[        mouseUpCanTrigger |= true;
    }

    // Any move/drag after the menu is created]=])
    set(_radiant_juce_touch_submenu_fixed [=[        mouseUpCanTrigger |= true;

        if (e.source.isTouch() && ! isSubMenuVisible())
            showSubMenuFor (currentChild);
    }

    // Any move/drag after the menu is created]=])

    string(FIND "${_radiant_juce_android_popup_source}" "${_radiant_juce_touch_submenu_fixed}" _radiant_juce_touch_submenu_fixed_pos)
    if(_radiant_juce_touch_submenu_fixed_pos EQUAL -1)
        string(FIND "${_radiant_juce_android_popup_source}" "${_radiant_juce_touch_submenu_upstream}" _radiant_juce_touch_submenu_upstream_pos)
        if(_radiant_juce_touch_submenu_upstream_pos EQUAL -1)
            message(FATAL_ERROR "JUCE PopupMenu::mouseUp() changed; review the Android touch submenu patch")
        endif()

        string(REPLACE "${_radiant_juce_touch_submenu_upstream}"
                       "${_radiant_juce_touch_submenu_fixed}"
                       _radiant_juce_android_popup_source
                       "${_radiant_juce_android_popup_source}")
        file(WRITE "${RADIANT_JUCE_ANDROID_POPUP_SOURCE}" "${_radiant_juce_android_popup_source}")
    endif()
endif()

set(RADIANT_ANDROID_APP_SOURCES
    src/platform/FileTypes.cpp
    src/platform/CrashGuard.cpp
    src/platform/Paths.cpp
    src/platform/Package.cpp
    src/platform/NativeEmbed.cpp
    src/platform/LinuxRealtime.cpp
    src/engine/AudioEngine.cpp
    src/engine/ChannelRackProcessor.cpp
    src/engine/Metronome.cpp
    src/engine/SampleLibrary.cpp
    src/engine/WavPackFormat.cpp
    src/engine/Mp3Format.cpp
    src/engine/ProjectFile.cpp
    src/engine/ProjectPackage.cpp
    src/engine/SessionPack.cpp
    src/engine/LiveLink.cpp
    src/engine/LiveSessions.cpp
    src/engine/MediaPack.cpp
    src/engine/LiveTest.cpp
    src/engine/PluginHost.cpp
    src/engine/PluginSandbox.cpp
    src/engine/SandboxWorker.cpp
    src/engine/ClapFormat.cpp
    src/engine/Vst2Format.cpp
    src/engine/FlStudio.cpp
    src/engine/Exporter.cpp
    src/engine/FlStudioExport.cpp
    src/engine/StreamPrefetcher.cpp
    src/engine/MidiFileIo.cpp
    src/engine/AudioLogger.cpp
    src/engine/MidiSyncIo.cpp
    src/engine/PreviewPlayer.cpp
    src/ui/SessionDialogs.cpp
    src/ui/LiveSessionUi.cpp
    src/ui/LiveChat.cpp
    src/ui/MainComponent.cpp
    src/ui/ChannelRackComponent.cpp
    src/ui/PianoRollComponent.cpp
    src/ui/PlaylistComponent.cpp
    src/ui/PatternBlocksView.cpp
    src/ui/MixerComponent.cpp
    src/ui/QuickPick.cpp
    src/ui/PluginWindow.cpp
    src/ui/PluginPresetMenu.cpp
    src/ui/ProjectLoading.cpp
    src/ui/WindowOwner.cpp
    src/ui/ParametricEqEditor.cpp
    src/ui/FacetEqEditor.cpp
    src/ui/ProEditors.cpp
    src/ui/PluginHeader.cpp
    src/ui/PluginSkin.cpp
    src/ui/PluginEditors.cpp
    src/ui/PatcherEditor.cpp
    src/ui/DynamicsDisplays.cpp
    src/ui/ExportDialog.cpp
    src/ui/FlStudioExportDialog.cpp
    src/ui/AutoSamplerDialog.cpp
    src/ui/AutomationUi.cpp
    src/ui/ControllerEditors.cpp
    src/ui/MidiOutEditor.cpp
    src/ui/VisualEditors.cpp
    src/ui/FlExtraEditors.cpp
    src/ui/VisualGl.cpp
    src/ui/DanceView.cpp
    src/ui/VideoPlayerView.cpp
    src/ui/WaveTravellerEditor.cpp
    src/ui/NewtoneEditor.cpp
    src/ui/ChannelSettings.cpp
    src/ui/LayerEditor.cpp
    src/ui/Controllers164.cpp
    src/ui/VoltageEditor.cpp
    src/ui/SoundFontEditor.cpp
    src/ui/VfxEditors.cpp
    src/ui/BrowserComponent.cpp
    src/ui/Theme.cpp
    src/ui/ThemeSettings.cpp
    src/ui/ColourPicker.cpp
    src/ui/TrackIcons.cpp
    src/ui/Icons.cpp
    src/ui/I18n.cpp
    src/ui/EditorTools.cpp
    src/ui/Workspace.cpp
    src/ui/ValueControl.cpp
    src/ui/Dialogs.cpp
    src/ui/SetupWizard.cpp
    src/ui/Splash.cpp
    src/ui/HintBar.cpp
    src/ui/Toolbar.cpp
    src/ui/WaveformView.cpp
    src/ui/NoteToolDialogs.cpp
    src/ui/MidiClipboard.cpp
    src/ui/EventEditor.cpp
    src/ui/AudioEditor.cpp
    src/ui/AudioEditorTools.cpp
    src/ui/ToolWindow.cpp
    src/ui/SpeechDialog.cpp
    src/ui/TempoTapper.cpp
    src/ui/LoopStarterWindow.cpp
    src/ui/HistoryPanel.cpp
    src/ui/MissingFilesDialog.cpp
    src/ui/SettingsDialog.cpp
    src/ui/SettingsPages.cpp
    src/ui/PluginManager.cpp
    src/ui/ShortcutManager.cpp
    src/ui/NewProjectDialog.cpp
    src/ui/ClipProperties.cpp
    src/ui/PluginMonitorWindow.cpp
    src/ui/TouchKeyboard.cpp
    src/ui/QuickAddPalette.cpp
    src/ui/PluginPickerView.cpp
    src/ui/PluginThumbs.cpp
    src/ui/PluginPicker.cpp
    src/ui/CrashReporter.cpp
    src/ui/Updater.cpp
    src/ui/StemExtract.cpp)
list(TRANSFORM RADIANT_ANDROID_APP_SOURCES PREPEND "${RADIANT_REPO_ROOT}/")

add_library(radiant_app STATIC ${RADIANT_ANDROID_APP_SOURCES})
target_include_directories(radiant_app PRIVATE
    "${RADIANT_REPO_ROOT}/src"
    "${RADIANT_REPO_ROOT}/third_party/clap/include"
    "${RADIANT_REPO_ROOT}/third_party/onnxruntime")
target_compile_definitions(radiant_app PRIVATE
    JUCE_APPLICATION_NAME_STRING="Radiant"
    JUCE_APPLICATION_VERSION_STRING="0.1.0"
    JUCE_WEB_BROWSER=0
    JUCE_USE_CURL=0
    JUCE_USE_MP3AUDIOFORMAT=0
    JUCE_PLUGINHOST_VST3=0
    JUCE_PLUGINHOST_LV2=0
    JUCE_PLUGINHOST_AU=0
    JUCE_PLUGINHOST_ARA=0
    JUCE_USE_ANDROID_OPENSLES=0
    JUCE_USE_ANDROID_OBOE=1)
target_link_libraries(radiant_app PUBLIC
    lynx_core
    juce::juce_audio_utils
    juce::juce_audio_processors
    juce::juce_dsp
    juce::juce_opengl)

add_library(radiant SHARED RadiantJni.cpp)
target_compile_definitions(radiant PRIVATE
    JUCE_WEB_BROWSER=0
    JUCE_USE_CURL=0
    JUCE_USE_ANDROID_OPENSLES=0
    JUCE_USE_ANDROID_OBOE=1
    JUCE_APPLICATION_NAME_STRING="Radiant"
    JUCE_APPLICATION_VERSION_STRING="0.1.0")
target_link_libraries(radiant PRIVATE
    radiant_app
    lynx_core
    juce::juce_audio_utils
    juce::juce_audio_processors
    juce::juce_dsp
    juce::juce_opengl
    log android EGL GLESv3)
