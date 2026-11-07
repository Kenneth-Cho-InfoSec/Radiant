package com.radiant.daw

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioRecordingForegroundServiceTest {
    @Test
    fun armingAudioInputStartsForegroundServiceAndDisarmingStopsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            "Use a fresh emulator for this test so it exercises Android's first-request dialog",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )

        ActivityScenario.launch(RadiantActivity::class.java).use { scenario ->
            scenario.onActivity { NativeBridge.setAudioRecordingArmed(true) }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val permissionButton = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "While using the app"
            } else {
                "Allow"
            }
            val allow = device.wait(Until.findObject(By.text(permissionButton)), 10_000)
            assertTrue("JUCE did not display Android's microphone permission prompt", allow != null)
            allow!!.click()

            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline &&
                (!NativeBridge.isAudioRecordingArmed() || !PlaybackForegroundService.active)
            ) {
                SystemClock.sleep(100)
            }

            assertTrue("The native engine did not arm audio input", NativeBridge.isAudioRecordingArmed())
            assertTrue("The foreground audio service did not become active", PlaybackForegroundService.active)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertTrue("The foreground service did not acquire the microphone service type", PlaybackForegroundService.microphoneActive)
            } else {
                assertFalse("Older Android versions must not report a microphone foreground-service type", PlaybackForegroundService.microphoneActive)
            }

            scenario.onActivity { NativeBridge.setAudioRecordingArmed(false) }
            val stopDeadline = SystemClock.elapsedRealtime() + 5_000
            while (SystemClock.elapsedRealtime() < stopDeadline &&
                (NativeBridge.isAudioRecordingArmed() || PlaybackForegroundService.active)
            ) {
                SystemClock.sleep(100)
            }

            assertFalse("Audio input stayed armed after disarming", NativeBridge.isAudioRecordingArmed())
            assertFalse("Foreground audio service stayed active after disarming", PlaybackForegroundService.active)
            assertFalse("Microphone foreground type stayed active after disarming", PlaybackForegroundService.microphoneActive)
        }
    }
}
