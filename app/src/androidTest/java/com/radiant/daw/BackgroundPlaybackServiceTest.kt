package com.radiant.daw

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackServiceTest {
    @Test
    fun playbackRemainsActiveAfterActivityGoesToBackground() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch(RadiantActivity::class.java).use { scenario ->
            scenario.onActivity { NativeBridge.play() }

            try {
                val startDeadline = SystemClock.elapsedRealtime() + 10_000
                while (SystemClock.elapsedRealtime() < startDeadline &&
                    (!NativeBridge.isTransportPlaying() || !PlaybackForegroundService.active)
                ) {
                    SystemClock.sleep(100)
                }
                assertTrue("The native transport did not start", NativeBridge.isTransportPlaying())
                assertTrue("Playback did not promote its service to the foreground", PlaybackForegroundService.active)

                device.pressHome()
                SystemClock.sleep(2_000)

                assertTrue("Playback stopped when Radiant went to the background", NativeBridge.isTransportPlaying())
                assertTrue("The playback foreground service stopped in the background", PlaybackForegroundService.active)
            } finally {
                device.wakeUp()
                scenario.onActivity { NativeBridge.stopPlayback() }

                val stopDeadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < stopDeadline && PlaybackForegroundService.active) {
                    SystemClock.sleep(100)
                }
                assertFalse("The playback foreground service stayed active after stopping transport", PlaybackForegroundService.active)
            }
        }
    }
}
