package com.radiant.daw

import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class SafProjectStorageInstrumentedTest {
    @Test
    fun createDocumentWritesProjectThatNativeLoaderCanReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val fileName = "RadiantSaf_${System.currentTimeMillis()}.radiant"

        ActivityScenario.launch(RadiantActivity::class.java).use { scenario ->
            val projectActions = device.wait(Until.findObject(By.desc("Project actions")), 10_000)
            assertNotNull("Radiant project actions were not accessible", projectActions)
            projectActions!!.click()

            val saveAs = device.wait(Until.findObject(By.textContains("Save as")), 5_000)
            assertNotNull("Radiant did not expose Save As", saveAs)
            saveAs!!.click()

            val fileNameField = device.wait(Until.findObject(By.clazz(EditText::class.java)), 10_000)
            assertNotNull("Android's CreateDocument picker did not show a file name field", fileNameField)
            fileNameField!!.setText(fileName)

            val saveButton = device.wait(Until.findObject(By.text(Pattern.compile("(?i)^save$"))), 5_000)
            assertNotNull("Android's CreateDocument picker did not show Save", saveButton)
            saveButton!!.click()

            assertTrue(
                "Radiant did not report a successful document save",
                device.wait(Until.hasObject(By.textContains("Saved $fileName")), 15_000),
            )

            val projectPreferences = context.getSharedPreferences("radiant-projects", 0)
            var uriValue: String? = null
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline && uriValue == null) {
                uriValue = projectPreferences.getString("document_uri", null)
                if (uriValue == null) SystemClock.sleep(100)
            }
            assertNotNull("The selected document URI was not retained", uriValue)

            val uri = Uri.parse(uriValue)
            val savedName = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            assertEquals("The provider changed the Radiant project extension", fileName, savedName)

            val projectBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            assertNotNull("The provider did not return the saved project", projectBytes)
            assertTrue("The saved Radiant project was unexpectedly empty", projectBytes!!.size > 100)

            val projectActionsAgain = device.wait(Until.findObject(By.desc("Project actions")), 5_000)
            assertNotNull("Radiant project actions disappeared after saving", projectActionsAgain)
            projectActionsAgain!!.click()

            val openProject = device.wait(Until.findObject(By.text("Open project or import")), 5_000)
            assertNotNull("Radiant did not expose Open project or import", openProject)
            openProject!!.click()

            var savedDocument = device.wait(Until.findObject(By.text(fileName)), 8_000)
            if (savedDocument == null) {
                val showRoots = device.wait(Until.findObject(By.desc("Show roots")), 3_000)
                showRoots?.click()
                val downloads = device.wait(Until.findObject(By.text("Downloads")), 5_000)
                downloads?.click()
                savedDocument = device.wait(Until.findObject(By.text(fileName)), 8_000)
            }
            assertNotNull("The saved Radiant document was not available in OpenDocument", savedDocument)
            savedDocument!!.click()

            assertTrue(
                "Radiant did not finish staging and opening the selected document",
                device.wait(
                    Until.hasObject(By.textContains("Opened ${fileName.removeSuffix(".radiant")}")),
                    15_000,
                ),
            )
            scenario.onActivity {
                assertEquals(fileName.removeSuffix(".radiant"), NativeBridge.getAndroidProjectName())
            }

            val lynxFileName = "RadiantSaf_${System.currentTimeMillis()}.lynx"
            device.wait(Until.findObject(By.desc("Project actions")), 5_000)!!.click()
            device.wait(Until.findObject(By.textContains("Save as")), 5_000)!!.click()
            device.wait(Until.findObject(By.clazz(EditText::class.java)), 10_000)!!.setText(lynxFileName)
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)^save$"))), 5_000)!!.click()

            assertTrue(
                "Radiant did not retain the selected .lynx extension when saving",
                device.wait(Until.hasObject(By.textContains("Saved $lynxFileName")), 15_000),
            )

            uriValue = projectPreferences.getString("document_uri", null)
            assertNotNull("The .lynx document URI was not retained", uriValue)
            val lynxUri = Uri.parse(uriValue)
            val lynxSavedName = context.contentResolver.query(
                lynxUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            assertEquals("The provider changed the .lynx project extension", lynxFileName, lynxSavedName)
            val lynxProjectBytes = context.contentResolver.openInputStream(lynxUri)?.use { it.readBytes() }
            assertNotNull("The provider did not return the saved .lynx project", lynxProjectBytes)
            assertTrue("The saved .lynx project was unexpectedly empty", lynxProjectBytes!!.size > 100)

            device.wait(Until.findObject(By.desc("Project actions")), 5_000)!!.click()
            device.wait(Until.findObject(By.text("Open project or import")), 5_000)!!.click()
            var savedLynxDocument = device.wait(Until.findObject(By.text(lynxFileName)), 8_000)
            if (savedLynxDocument == null) {
                device.wait(Until.findObject(By.desc("Show roots")), 3_000)?.click()
                device.wait(Until.findObject(By.text("Downloads")), 5_000)?.click()
                savedLynxDocument = device.wait(Until.findObject(By.text(lynxFileName)), 8_000)
            }
            assertNotNull("The saved .lynx document was not available in OpenDocument", savedLynxDocument)
            savedLynxDocument!!.click()

            assertTrue(
                "Radiant did not reopen the selected .lynx document",
                device.wait(
                    Until.hasObject(By.textContains("Opened ${lynxFileName.removeSuffix(".lynx")}")),
                    15_000,
                ),
            )
            scenario.onActivity {
                assertEquals(lynxFileName.removeSuffix(".lynx"), NativeBridge.getAndroidProjectName())
            }
        }
    }
}
