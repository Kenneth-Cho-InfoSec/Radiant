package com.radiant.daw

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private data class EditorPanel(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val editorPanels = listOf(
    EditorPanel("Playlist", Icons.Default.PlaylistPlay),
    EditorPanel("Channel Rack", Icons.Default.GridView),
    EditorPanel("Piano Roll", Icons.Default.Piano),
    EditorPanel("Mixer", Icons.Default.Tune),
    EditorPanel("Browser", Icons.Default.FolderOpen),
)

private val radiantDark = darkColorScheme(
    primary = Color(0xffd0bcff),
    onPrimary = Color(0xff381e72),
    primaryContainer = Color(0xff4f378b),
    onPrimaryContainer = Color(0xffeaddff),
    secondary = Color(0xffccc2dc),
    onSecondary = Color(0xff332d41),
    tertiary = Color(0xffefb8c8),
    onTertiary = Color(0xff492532),
    background = Color(0xff141218),
    onBackground = Color(0xffe6e0e9),
    surface = Color(0xff211f26),
    onSurface = Color(0xffe6e0e9),
    surfaceVariant = Color(0xff2b2930),
    onSurfaceVariant = Color(0xffcac4d0),
    outline = Color(0xff938f99),
)

private val radiantLight = lightColorScheme(
    primary = Color(0xff6750a4),
    onPrimary = Color(0xffffffff),
    primaryContainer = Color(0xffeaddff),
    onPrimaryContainer = Color(0xff21005d),
    secondary = Color(0xff625b71),
    onSecondary = Color(0xffffffff),
    tertiary = Color(0xff7d5260),
    onTertiary = Color(0xffffffff),
    background = Color(0xfffffbfe),
    onBackground = Color(0xff1d1b20),
    surface = Color(0xfff3edf7),
    onSurface = Color(0xff1d1b20),
    surfaceVariant = Color(0xffe7e0ec),
    onSurfaceVariant = Color(0xff49454f),
    outline = Color(0xff79747e),
)

private const val themePreferences = "radiant-ui"
private const val projectPreferences = "radiant-projects"

private data class StagedDocument(val file: File, val displayName: String, val extension: String)

private fun contentDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun stagingFileForDocument(context: Context, uri: Uri, extension: String): File {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    val directory = File(context.filesDir, "radiant-projects").apply { mkdirs() }
    return File(directory, "project-${digest.take(24)}.$extension")
}

private suspend fun stageDocument(context: Context, uri: Uri): StagedDocument = withContext(Dispatchers.IO) {
    val displayName = contentDisplayName(context, uri) ?: "Project.lynx"
    val sourceExtension = displayName.substringAfterLast('.', "").lowercase()
    val supported = setOf("lynx", "radiant", "flp", "als", "zip", "lynxpack", "mid", "midi", "dawproj")
    require(sourceExtension in supported) { "Choose a .lynx, .radiant, .flp, .als, .zip, or MIDI document." }
    val stagingExtension = if (sourceExtension == "radiant") "lynx" else sourceExtension
    val destination = stagingFileForDocument(context, uri, stagingExtension)
    val directory = destination.parentFile ?: throw IOException("Could not create the project staging folder.")
    val temporary = File(directory, "${destination.name}.pending")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(temporary).use { output -> input.copyTo(output) }
    } ?: throw IOException("The selected document could not be read.")
    try {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    StagedDocument(destination, displayName.substringBeforeLast('.'), sourceExtension)
}

private suspend fun copyStagedDocumentToUri(context: Context, file: File, uri: Uri) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
        file.inputStream().use { input -> input.copyTo(output) }
    } ?: throw IOException("The selected document provider did not open a writable stream.")
}

class RadiantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackForegroundService.cancelDeferredCleanSession()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContent { RadiantApp() }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            if (NativeBridge.isTransportPlaying() || NativeBridge.isAudioRecordingArmed()) {
                PlaybackForegroundService.deferCleanSessionUntilAudioStops()
            } else {
                NativeBridge.endAndroidSession()
            }
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiantApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val view = LocalView.current
    val preferences = remember(context) {
        context.getSharedPreferences(themePreferences, Context.MODE_PRIVATE)
    }
    var themeMode by rememberSaveable {
        mutableStateOf(preferences.getString("mode", "system") ?: "system")
    }
    var dynamicColors by rememberSaveable {
        mutableStateOf(preferences.getBoolean("dynamic", false))
    }
    var selectedPanel by rememberSaveable { mutableIntStateOf(0) }
    var themeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var projectMenuOpen by rememberSaveable { mutableStateOf(false) }
    val projectPreferences = remember(context) {
        context.getSharedPreferences(projectPreferences, Context.MODE_PRIVATE)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, access) }
                .recoverCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            scope.launch {
                try {
                    val staged = stageDocument(context, uri)
                    if (!NativeBridge.openAndroidProject(staged.file.absolutePath, staged.displayName)) {
                        snackbarHostState.showSnackbar("Radiant could not open ${staged.displayName}.")
                    } else if (staged.extension == "lynx" || staged.extension == "radiant") {
                        projectPreferences.edit().putString("document_uri", uri.toString()).apply()
                        snackbarHostState.showSnackbar("Opened ${staged.displayName}")
                    } else {
                        projectPreferences.edit().remove("document_uri").apply()
                        snackbarHostState.showSnackbar("Imported ${staged.displayName}")
                    }
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: "Could not read the selected document.")
                }
            }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-radiant-project")) { uri ->
        if (uri != null) {
            val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, access) }
            scope.launch {
                val documentName = contentDisplayName(context, uri) ?: "Untitled.radiant"
                val extension = documentName.substringAfterLast('.', "").lowercase()
                if (extension != "lynx" && extension != "radiant") {
                    snackbarHostState.showSnackbar("Use a .lynx or .radiant filename.")
                    return@launch
                }
                val displayName = documentName.substringBeforeLast('.')
                val staged = stagingFileForDocument(context, uri, "lynx")
                try {
                    if (!NativeBridge.saveAndroidProject(staged.absolutePath, displayName)) {
                        snackbarHostState.showSnackbar("Could not serialize the Radiant project.")
                        return@launch
                    }
                    projectPreferences.edit().putString("document_uri", uri.toString()).apply()
                    copyStagedDocumentToUri(context, staged, uri)
                    snackbarHostState.showSnackbar("Saved $documentName")
                } catch (error: Exception) {
                    NativeBridge.markAndroidSaveFailed()
                    snackbarHostState.showSnackbar(error.message ?: "Could not write the selected document.")
                }
            }
        }
    }

    val systemDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val useDynamic = dynamicColors && Build.VERSION.SDK_INT >= 31
    val colors = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> radiantDark
        else -> radiantLight
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val expanded = screenWidthDp >= 840
    val singlePanel = !expanded

    LaunchedEffect(context.applicationContext) {
        var serviceRequested = false
        var serviceRequestedForPlayback = false
        var serviceRequestedForRecording = false
        while (true) {
            when (NativeBridge.takeAndroidStorageAction()) {
                1 -> openDocumentLauncher.launch(arrayOf("*/*"))
                2 -> {
                    val name = NativeBridge.getAndroidProjectName().ifBlank { "Untitled" }
                        .removeSuffix(".lynx").removeSuffix(".radiant")
                    saveAsLauncher.launch("$name.radiant")
                }
                3 -> {
                    val uri = projectPreferences.getString("document_uri", null)?.let(Uri::parse)
                    val path = NativeBridge.getAndroidProjectPath()
                    if (uri == null || path.isBlank()) {
                        NativeBridge.saveCurrentProjectAs()
                    } else {
                        scope.launch {
                            try {
                                copyStagedDocumentToUri(context, File(path), uri)
                                snackbarHostState.showSnackbar("Project saved")
                            } catch (error: Exception) {
                                NativeBridge.markAndroidSaveFailed()
                                snackbarHostState.showSnackbar(error.message ?: "Could not update the project document.")
                            }
                        }
                    }
                }
            }

            val playing = NativeBridge.isTransportPlaying()
            val recording = NativeBridge.isAudioRecordingArmed()
            val needsAudioService = playing || recording
            val serviceStateChanged = playing != serviceRequestedForPlayback || recording != serviceRequestedForRecording
            if (needsAudioService && (!serviceRequested || serviceStateChanged)) {
                try {
                    ContextCompat.startForegroundService(
                        context.applicationContext,
                        Intent(context.applicationContext, PlaybackForegroundService::class.java),
                    )
                    serviceRequested = true
                    serviceRequestedForPlayback = playing
                    serviceRequestedForRecording = recording
                } catch (error: Exception) {
                    android.util.Log.e("RadiantAudio", "Could not start the audio foreground service", error)
                    if (recording) NativeBridge.setAudioRecordingArmed(false)
                    if (playing) NativeBridge.stopPlayback()
                    serviceRequested = false
                    serviceRequestedForPlayback = false
                    serviceRequestedForRecording = false
                    snackbarHostState.showSnackbar("Android could not keep the requested audio active.")
                }
            } else if (!needsAudioService && serviceRequested) {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, PlaybackForegroundService::class.java),
                )
                serviceRequested = false
                serviceRequestedForPlayback = false
                serviceRequestedForRecording = false
            }
            delay(100)
        }
    }

    LaunchedEffect(
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.background,
        colors.surface,
        colors.surfaceVariant,
        colors.onSurface,
    ) {
        NativeBridge.setEditorPalette(
            colors.primary.toArgb(),
            colors.secondary.toArgb(),
            colors.tertiary.toArgb(),
            colors.background.toArgb(),
            colors.surface.toArgb(),
            colors.surfaceVariant.toArgb(),
            colors.onSurface.toArgb(),
        )
    }

    MaterialTheme(colorScheme = colors) {
        WindowInsetsControllerCompat(
            activity.window,
            view,
        ).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        BackHandler(enabled = singlePanel && selectedPanel != 0) {
            selectedPanel = 0
        }

        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Radiant", maxLines = 1)
                            if (expanded) {
                                Spacer(Modifier.width(12.dp))
                                editorPanels.forEachIndexed { index, panel ->
                                    IconButton(
                                        onClick = { selectedPanel = index },
                                        modifier = Modifier.semantics {
                                            contentDescription = panel.title
                                            selected = selectedPanel == index
                                        },
                                    ) {
                                        Icon(
                                            panel.icon,
                                            contentDescription = null,
                                            tint = if (selectedPanel == index) colors.primary else colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                    actions = {
                        IconButton(onClick = { projectMenuOpen = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Project actions")
                        }
                        DropdownMenu(
                            expanded = projectMenuOpen,
                            onDismissRequest = { projectMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open project or import") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    projectMenuOpen = false
                                    NativeBridge.requestAndroidOpen()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save project") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    projectMenuOpen = false
                                    NativeBridge.saveCurrentProject()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save as…") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    projectMenuOpen = false
                                    NativeBridge.saveCurrentProjectAs()
                                },
                            )
                        }
                        IconButton(onClick = { themeMenuOpen = true }) {
                            Icon(Icons.Default.BrightnessAuto, contentDescription = "Theme settings")
                        }
                        DropdownMenu(
                            expanded = themeMenuOpen,
                            onDismissRequest = { themeMenuOpen = false },
                        ) {
                            listOf("system", "light", "dark").forEach { mode ->
                                val label = when (mode) {
                                    "light" -> "Light theme"
                                    "dark" -> "Dark theme"
                                    else -> "System theme"
                                }
                                val icon = when (mode) {
                                    "light" -> Icons.Default.LightMode
                                    "dark" -> Icons.Default.DarkMode
                                    else -> Icons.Default.BrightnessAuto
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                    trailingIcon = {
                                        if (themeMode == mode) Icon(Icons.Default.Check, contentDescription = "Selected")
                                    },
                                    onClick = {
                                        themeMode = mode
                                        preferences.edit().putString("mode", mode).apply()
                                        themeMenuOpen = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Android dynamic colors") },
                                leadingIcon = { Icon(Icons.Default.BrightnessAuto, contentDescription = null) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = dynamicColors,
                                        onCheckedChange = null,
                                        enabled = Build.VERSION.SDK_INT >= 31,
                                    )
                                },
                                enabled = Build.VERSION.SDK_INT >= 31,
                                onClick = {
                                    val enabled = !dynamicColors
                                    dynamicColors = enabled
                                    preferences.edit().putBoolean("dynamic", enabled).apply()
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (singlePanel) {
                    NavigationBar(containerColor = colors.surface) {
                        editorPanels.forEachIndexed { index, panel ->
                            NavigationBarItem(
                                selected = selectedPanel == index,
                                onClick = { selectedPanel = index },
                                icon = { Icon(panel.icon, contentDescription = panel.title, modifier = Modifier.size(26.dp)) },
                                label = { Text(panel.title, fontSize = 13.sp, lineHeight = 18.sp) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                factory = { viewContext ->
                    FrameLayout(viewContext).apply {
                        NativeBridge.attachEditor(this)
                        getChildAt(0)?.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = {
                    NativeBridge.setEditorMode(!expanded, selectedPanel)
                },
                onRelease = { NativeBridge.detachEditor() },
            )
        }
    }
}
