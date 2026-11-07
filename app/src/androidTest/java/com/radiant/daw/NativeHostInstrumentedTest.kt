package com.radiant.daw

import android.os.SystemClock
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class NativeHostInstrumentedTest {
    @Test
    fun microphonePermissionIsDeclaredForOptInAudioRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = packageInfo.requestedPermissions.orEmpty()

        assertTrue(
            "Audio recording is an explicit feature; the app must declare Android's microphone permission",
            requested.contains(Manifest.permission.RECORD_AUDIO),
        )
        assertTrue(
            "Android 14+ requires a microphone foreground-service permission for background input capture",
            requested.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
        )
    }

    @Test
    fun playbackServiceDeclaresBothSupportedAudioForegroundTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, PlaybackForegroundService::class.java)
        val service = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        val expectedTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

        assertEquals(
            "The audio service must be allowed to keep playback and user-started recording alive in the background",
            expectedTypes,
            service.foregroundServiceType,
        )
    }

    @Test
    fun nativeBridgeExposesRecordingStateToAndroidLifecycle() {
        val methods = NativeBridge::class.java.methods
        val canArmRecording = methods.any { method ->
            method.name == "setAudioRecordingArmed" &&
                method.parameterCount == 1 && method.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        val canReadRecordingState = methods.any { method ->
            method.name == "isAudioRecordingArmed" && method.parameterTypes.isEmpty()
        }

        assertTrue("The Android host needs a command to arm or disarm native audio capture", canArmRecording)
        assertTrue("The Android host needs native capture state to select foreground-service types", canReadRecordingState)
    }

    @Test
    fun juceHostAttachesResizesAndSurvivesActivityLifecycleChanges() {
        ActivityScenario.launch(RadiantActivity::class.java).use { scenario ->
            assertTrue("JUCE GUI runtime was not initialised", NativeBridge.isJuceGuiInitialised())
            assertTrue("JUCE peer was not attached", NativeBridge.isEditorAttached())
            assertTrue("Android session marker was not started", NativeBridge.isAndroidSessionActive())
            var widthDp = 0
            scenario.onActivity { widthDp = it.resources.configuration.screenWidthDp }
            val expectedInitialPanels = if (widthDp >= 840) 3 else 1
            waitForVisiblePanelCount(scenario, expectedInitialPanels)
            android.util.Log.i(
                "RadiantLayoutTest",
                "width=${widthDp}dp initialWorkspacePanels=$expectedInitialPanels",
            )
            val audio = NativeBridge.getAudioOutputConfiguration()
            assertEquals("Expected output status, sample rate and callback size", 3, audio.size)
            android.util.Log.i(
                "RadiantAudioTest",
                "output=${NativeBridge.getAudioOutputDeviceName()} active=${audio[0]} rate=${audio[1]} buffer=${audio[2]}",
            )

            scenario.onActivity {
                NativeBridge.setEditorMode(false, 0)
                assertEquals("Desktop layout did not restore its three default panes", 3, NativeBridge.getVisiblePanelCount())
                val desktopLayout = (0 until 5).map(NativeBridge::getWorkspacePanelState)

                NativeBridge.setEditorMode(true, 0)
                assertEquals("Compact mode should show one editor", 1, NativeBridge.getVisiblePanelCount())
                assertEquals("Playlist tab was not selected", 0, NativeBridge.getSelectedPanel())
                assertTrue("Compact editor should fill the workspace", NativeBridge.isSelectedPanelMaximised())

                NativeBridge.setEditorMode(true, 4)
                assertEquals("Browser navigation did not switch the editor", 4, NativeBridge.getSelectedPanel())
                assertEquals("Compact mode exposed multiple panes", 1, NativeBridge.getVisiblePanelCount())

                NativeBridge.setEditorMode(false, 4)
                assertEquals("Desktop panes were not restored", 3, NativeBridge.getVisiblePanelCount())
                desktopLayout.forEachIndexed { index, state ->
                    assertArrayEquals("Workspace panel $index changed after compact mode", state, NativeBridge.getWorkspacePanelState(index))
                }

                val project = java.io.File(it.filesDir, "android-project-roundtrip.radiant")
                assertTrue("Radiant project serialization failed", NativeBridge.saveAndroidProject(project.absolutePath, "Android Round Trip"))
                assertTrue("Radiant project file was empty", project.isFile && project.length() > 100)
                assertEquals("Saved project name was not retained", "Android Round Trip", NativeBridge.getAndroidProjectName())
                assertTrue("Radiant project could not be reopened", NativeBridge.openAndroidProject(project.absolutePath, "Android Round Trip"))
                assertEquals("Reopened project name was not retained", "Android Round Trip", NativeBridge.getAndroidProjectName())
            }

            var importedFlp: java.io.File? = null
            var importedProject: java.io.File? = null
            var alternateFlp: java.io.File? = null
            scenario.onActivity {
                importedFlp = java.io.File(it.cacheDir, "android-flp-137.flp").apply { writeBytes(flpFixture(137_000)) }
                importedProject = java.io.File(it.filesDir, "android-flp-roundtrip.radiant")
                alternateFlp = java.io.File(it.cacheDir, "android-flp-143.flp").apply { writeBytes(flpFixture(143_000)) }
                assertTrue("Android FLP import did not start", NativeBridge.openAndroidProject(importedFlp!!.absolutePath, "Android FLP 137 BPM"))
            }
            waitForSongBpm(scenario, "Android FLP 137 BPM", 137.0)
            scenario.onActivity {
                assertTrue(
                    "Imported FLP did not serialize as Radiant",
                    NativeBridge.saveAndroidProject(importedProject!!.absolutePath, "Android FLP 137 BPM"),
                )
                assertTrue("Radiant round-trip project was empty", importedProject!!.length() > 100)
                assertTrue("Alternate FLP import did not start", NativeBridge.openAndroidProject(alternateFlp!!.absolutePath, "Android FLP 143 BPM"))
            }
            waitForSongBpm(scenario, "Android FLP 143 BPM", 143.0)
            scenario.onActivity {
                assertTrue(
                    "Saved Radiant project did not reopen",
                    NativeBridge.openAndroidProject(importedProject!!.absolutePath, "Android FLP 137 BPM"),
                )
            }
            waitForSongBpm(scenario, "Android FLP 137 BPM", 137.0)

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            val portraitSize = waitForSize(scenario) { width, height -> height > width }
            assertTrue("Portrait host has no usable height", portraitSize.second > 0)

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val landscapeSize = waitForSize(scenario) { width, height -> width > height }
            assertTrue("Landscape host has no usable width", landscapeSize.first > 0)

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitForSize(scenario) { width, height -> height > width }

            scenario.recreate()
            waitForAttached(scenario)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("JUCE peer was lost after background/foreground", NativeBridge.isEditorAttached())
            assertTrue("Activity recreation ended the active Android session", NativeBridge.isAndroidSessionActive())
        }

        waitForAndroidSession(false)
    }

    private fun waitForSize(
        scenario: ActivityScenario<RadiantActivity>,
        predicate: (Int, Int) -> Boolean,
    ): Pair<Int, Int> {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var size = 0 to 0
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity {
                val current = NativeBridge.getEditorSize()
                if (current.size >= 2) size = current[0] to current[1]
            }
            if (predicate(size.first, size.second)) return size
            SystemClock.sleep(50)
        }
        return size
    }

    private fun waitForAttached(scenario: ActivityScenario<RadiantActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var attached = false
            scenario.onActivity { attached = NativeBridge.isEditorAttached() }
            if (attached) return
            SystemClock.sleep(50)
        }
        assertTrue("JUCE peer was not reattached after Activity recreation", false)
    }

    private fun waitForAndroidSession(expected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var active = !expected
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                active = NativeBridge.isAndroidSessionActive()
            }
            if (active == expected) return
            SystemClock.sleep(50)
        }
        assertEquals("Android session lifecycle did not reach the expected state", expected, active)
    }

    private fun waitForVisiblePanelCount(scenario: ActivityScenario<RadiantActivity>, expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var actual = -1
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { actual = NativeBridge.getVisiblePanelCount() }
            if (actual == expected) return
            SystemClock.sleep(50)
        }
        assertEquals("Workspace panel count did not match the responsive width class", expected, actual)
    }

    private fun waitForSongBpm(scenario: ActivityScenario<RadiantActivity>, projectName: String, expectedBpm: Double) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var actualName = ""
        var actualBpm = 0.0
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity {
                actualName = NativeBridge.getAndroidProjectName()
                actualBpm = NativeBridge.getAndroidSongBpm()
            }
            if (actualName == projectName && kotlin.math.abs(actualBpm - expectedBpm) < 0.001) return
            SystemClock.sleep(50)
        }
        assertEquals("Imported project name did not finish loading", projectName, actualName)
        assertEquals("FLP tempo was not retained", expectedBpm, actualBpm, 0.001)
    }

    private fun flpFixture(tempoMilliBpm: Int): ByteArray {
        val bytes = ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("FLhd".toByteArray(Charsets.US_ASCII))
        bytes.putInt(6)
        bytes.putShort(0)
        bytes.putShort(1)
        bytes.putShort(96)
        bytes.put("FLdt".toByteArray(Charsets.US_ASCII))
        bytes.putInt(0)
        bytes.put(0x9c.toByte())
        bytes.putInt(tempoMilliBpm)
        return bytes.array()
    }

}
