#include <juce_audio_utils/juce_audio_utils.h>
#include <jni.h>

#include <memory>

#include "engine/AudioEngine.h"
#include "ui/MainComponent.h"
#include "ui/Theme.h"
#include "ui/ThemeSettings.h"

namespace {

std::unique_ptr<juce::ScopedJuceInitialiser_GUI> juceGuiInitialiser;
std::unique_ptr<lynx::theme::LookAndFeel> radiantLookAndFeel;
std::unique_ptr<lynx::AudioEngine> audioEngine;
std::unique_ptr<lynx::MainComponent> mainComponent;

juce::String stringFromJava (JNIEnv* env, jstring value)
{
    if (value == nullptr)
        return {};
    const auto* chars = env->GetStringUTFChars (value, nullptr);
    if (chars == nullptr)
        return {};
    const auto result = juce::String::fromUTF8 (chars);
    env->ReleaseStringUTFChars (value, chars);
    return result;
}

void initialiseRuntime()
{
    if (juceGuiInitialiser != nullptr)
        return;

    juceGuiInitialiser = std::make_unique<juce::ScopedJuceInitialiser_GUI>();
    radiantLookAndFeel = std::make_unique<lynx::theme::LookAndFeel>();
    juce::LookAndFeel::setDefaultLookAndFeel(radiantLookAndFeel.get());

    lynx::themes::Spec radiantTheme;
    radiantTheme.name = "Radiant";
    radiantTheme.accent = 0xffd0bcff;
    radiantTheme.background = 0xff141218;
    lynx::themeui::apply(radiantTheme, false);

    // Playback output does not require microphone permission. Recording input stays opt-in and is opened only
    // after Android grants RECORD_AUDIO.
    audioEngine = std::make_unique<lynx::AudioEngine>(true);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_initialiseJuceGui(JNIEnv*, jclass)
{
    initialiseRuntime();
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_attachEditor(JNIEnv* env, jclass, jobject hostViewGroup)
{
    jassert(juceGuiInitialiser != nullptr);
    if (juceGuiInitialiser == nullptr || audioEngine == nullptr)
        return;

    if (mainComponent == nullptr)
        mainComponent = std::make_unique<lynx::MainComponent>(*audioEngine, false);

    mainComponent->beginAndroidSession();

    if (mainComponent->isOnDesktop())
        mainComponent->removeFromDesktop();

    mainComponent->addToDesktop(0, hostViewGroup);
    mainComponent->setVisible(true);
    juce::ignoreUnused(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_detachEditor(JNIEnv*, jclass)
{
    if (mainComponent != nullptr && mainComponent->isOnDesktop())
        mainComponent->removeFromDesktop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_endAndroidSession(JNIEnv*, jclass)
{
    if (mainComponent != nullptr)
        mainComponent->endAndroidSession();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isAndroidSessionActive(JNIEnv*, jclass)
{
    return mainComponent != nullptr && mainComponent->isAndroidSessionActive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_setEditorMode(JNIEnv*, jclass, jboolean compact, jint selectedPanel)
{
    if (mainComponent == nullptr)
        return;
    const auto compactMode = compact == JNI_TRUE;
    const auto wasCompact = mainComponent->isAndroidCompactMode();
    mainComponent->setAndroidCompactMode (compactMode);
    if (compactMode || ! wasCompact)
        mainComponent->setAndroidPanel (selectedPanel);
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_setEditorPalette(JNIEnv*, jclass, jint primary, jint secondary, jint tertiary,
                                                    jint background, jint surface, jint surfaceVariant, jint onSurface)
{
    if (juceGuiInitialiser == nullptr)
        return;

    lynx::themes::Spec spec;
    spec.name = "Radiant";
    spec.accent = (std::uint32_t) primary;
    spec.background = (std::uint32_t) background;
    auto palette = lynx::themes::derivePalette (spec.accent, spec.background);
    palette.bg2 = (std::uint32_t) surface;
    palette.bg3 = (std::uint32_t) surfaceVariant;
    palette.textSecondary = (std::uint32_t) secondary;
    palette.stepNote = (std::uint32_t) tertiary;
    palette.noteSoft = lynx::themes::mix (background, tertiary, 0.62);
    palette.noteHard = (std::uint32_t) tertiary;
    palette.textPrimary = (std::uint32_t) onSurface;
    lynx::themeui::apply (spec, palette, false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isEditorAttached(JNIEnv*, jclass)
{
    return mainComponent != nullptr && mainComponent->isOnDesktop() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isJuceGuiInitialised(JNIEnv*, jclass)
{
    return juceGuiInitialiser != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_radiant_daw_NativeBridge_getAudioOutputDeviceName(JNIEnv* env, jclass)
{
    if (audioEngine == nullptr)
        return env->NewStringUTF("");
    auto* device = audioEngine->getDeviceManager().getCurrentAudioDevice();
    return env->NewStringUTF(device != nullptr ? device->getName().toRawUTF8() : "");
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_radiant_daw_NativeBridge_getAudioOutputConfiguration(JNIEnv* env, jclass)
{
    auto* device = audioEngine != nullptr ? audioEngine->getDeviceManager().getCurrentAudioDevice() : nullptr;
    const jint configuration[] = {
        device != nullptr && device->getActiveOutputChannels().countNumberOfSetBits() > 0 ? 1 : 0,
        device != nullptr ? (jint) device->getCurrentSampleRate() : 0,
        device != nullptr ? device->getCurrentBufferSizeSamples() : 0,
    };
    auto result = env->NewIntArray(3);
    env->SetIntArrayRegion(result, 0, 3, configuration);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_play(JNIEnv*, jclass)
{
    if (audioEngine != nullptr)
        audioEngine->play();
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_stopPlayback(JNIEnv*, jclass)
{
    if (audioEngine != nullptr)
        audioEngine->stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isTransportPlaying(JNIEnv*, jclass)
{
    return audioEngine != nullptr && audioEngine->getTransport().playing.load(std::memory_order_relaxed)
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_setAudioRecordingArmed(JNIEnv*, jclass, jboolean armed)
{
    if (audioEngine != nullptr)
        audioEngine->setAudioRecordingArmed (armed == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isAudioRecordingArmed(JNIEnv*, jclass)
{
    return audioEngine != nullptr && audioEngine->isAudioRecordingArmed() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_requestAndroidOpen(JNIEnv*, jclass)
{
    if (mainComponent != nullptr)
        mainComponent->requestAndroidOpen();
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_saveCurrentProject(JNIEnv*, jclass)
{
    if (mainComponent != nullptr)
        mainComponent->saveFromAndroid();
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_saveCurrentProjectAs(JNIEnv*, jclass)
{
    if (mainComponent != nullptr)
        mainComponent->saveAsFromAndroid();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_radiant_daw_NativeBridge_takeAndroidStorageAction(JNIEnv*, jclass)
{
    return mainComponent != nullptr ? mainComponent->takeAndroidStorageAction() : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_openAndroidProject(JNIEnv* env, jclass, jstring path, jstring displayName)
{
    if (mainComponent == nullptr)
        return JNI_FALSE;
    return mainComponent->openAndroidProject (juce::File (stringFromJava (env, path)), stringFromJava (env, displayName))
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_saveAndroidProject(JNIEnv* env, jclass, jstring path, jstring displayName)
{
    if (mainComponent == nullptr)
        return JNI_FALSE;
    return mainComponent->saveAndroidProject (juce::File (stringFromJava (env, path)), stringFromJava (env, displayName))
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_radiant_daw_NativeBridge_getAndroidProjectPath(JNIEnv* env, jclass)
{
    const auto value = mainComponent != nullptr ? mainComponent->getAndroidProjectPath() : juce::String();
    return env->NewStringUTF (value.toRawUTF8());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_radiant_daw_NativeBridge_getAndroidProjectName(JNIEnv* env, jclass)
{
    const auto value = mainComponent != nullptr ? mainComponent->getAndroidProjectName() : juce::String();
    return env->NewStringUTF (value.toRawUTF8());
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_radiant_daw_NativeBridge_getAndroidSongBpm(JNIEnv*, jclass)
{
    return mainComponent != nullptr ? mainComponent->getAndroidSongBpm() : 0.0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiant_daw_NativeBridge_markAndroidSaveFailed(JNIEnv*, jclass)
{
    if (mainComponent != nullptr)
        mainComponent->markAndroidSaveFailed();
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_radiant_daw_NativeBridge_getEditorSize(JNIEnv* env, jclass)
{
    const jint size[] = {
        mainComponent != nullptr ? mainComponent->getWidth() : 0,
        mainComponent != nullptr ? mainComponent->getHeight() : 0,
    };
    auto result = env->NewIntArray(2);
    env->SetIntArrayRegion(result, 0, 2, size);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_radiant_daw_NativeBridge_getSelectedPanel(JNIEnv*, jclass)
{
    return mainComponent != nullptr ? mainComponent->getAndroidPanel() : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_radiant_daw_NativeBridge_getVisiblePanelCount(JNIEnv*, jclass)
{
    return mainComponent != nullptr ? mainComponent->getAndroidVisiblePanelCount() : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiant_daw_NativeBridge_isSelectedPanelMaximised(JNIEnv*, jclass)
{
    return mainComponent != nullptr && mainComponent->isAndroidSelectedPanelMaximised() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_radiant_daw_NativeBridge_getWorkspacePanelState(JNIEnv* env, jclass, jint panel)
{
    const auto values = mainComponent != nullptr ? mainComponent->getAndroidPanelState (panel) : std::array<int, 6> {};
    jint data[6];
    for (int i = 0; i < 6; ++i)
        data[i] = values[(std::size_t) i];
    auto result = env->NewIntArray (6);
    env->SetIntArrayRegion (result, 0, 6, data);
    return result;
}
