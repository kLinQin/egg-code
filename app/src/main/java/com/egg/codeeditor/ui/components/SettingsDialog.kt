package com.egg.codeeditor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.extension.ExtensionManager
import com.egg.codeeditor.extension.ExtensionLoader
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch

private const val THEME_DARK = "darcula"
private const val THEME_LIGHT = "github-light"

@Composable
fun SettingsDialog(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    onDismiss: () -> Unit,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    discordToken: String = "",
    discordEnabled: Boolean = false,
    onDiscordTokenChange: (String) -> Unit = {},
    onDiscordEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onBackup) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestore) }

    fun getFileName(uri: Uri): String {
        var name = "Font_${System.currentTimeMillis()}.ttf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    val fontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val dir = File(context.filesDir, "fonts")
                    if (!dir.exists()) dir.mkdirs()
                    val name = getFileName(it)
                    val target = File(dir, name)
                    context.contentResolver.openInputStream(it)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    val newList = settings.editorFontList + target.absolutePath
                    val newAppList = settings.appFontList + target.absolutePath
                    onSettingsChange(settings.copy(fontFamily = "custom", customFontPath = target.absolutePath, editorFontList = newList, appFontList = newAppList))
                } catch (_: Exception) {}
            }
        }
    }

    val appFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val dir = File(context.filesDir, "fonts")
                    if (!dir.exists()) dir.mkdirs()
                    val name = getFileName(it)
                    val target = File(dir, name)
                    context.contentResolver.openInputStream(it)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    val newList = settings.appFontList + target.absolutePath
                    val newEditorList = settings.editorFontList + target.absolutePath
                    onSettingsChange(settings.copy(appFontFamily = "custom", appCustomFontPath = target.absolutePath, appFontList = newList, editorFontList = newEditorList))
                } catch (_: Exception) {}
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close Settings") }
            }
            HorizontalDivider()

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Editor") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("App") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Others") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Discord") })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Extensions") })
            }

            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (selectedTab) {
                    0 -> EditorSettingsTab(settings, onSettingsChange, fontLauncher)
                    1 -> AppSettingsTab(settings, onSettingsChange, appFontLauncher)
                    2 -> OtherSettingsTab(settings, onSettingsChange, backupLauncher, restoreLauncher)
                    3 -> DiscordSettingsTab(discordToken, discordEnabled, onDiscordTokenChange, onDiscordEnabledChange)
                    4 -> ExtensionsTab()
                }
            }
        }
    }
}

@Composable
private fun EditorSettingsTab(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    fontLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Font Size: ${settings.fontSizeSp} sp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.fontSizeSp.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it.toInt())) },
            valueRange = 10f..24f,
            steps = 13
        )
        HorizontalDivider()

        Text("Font Family", style = MaterialTheme.typography.labelLarge)
        listOf("monospace" to "Monospace", "sans" to "Sans Serif", "serif" to "Serif").forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.fontFamily == key, onClick = { onSettingsChange(settings.copy(fontFamily = key)) })
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 13.sp)
            }
        }

        settings.editorFontList.forEach { path ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.fontFamily == "custom" && settings.customFontPath == path,
                    onClick = { onSettingsChange(settings.copy(fontFamily = "custom", customFontPath = path)) }
                )
                Text(File(path).name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val newList = settings.editorFontList - path
                    val newPath = if (settings.customFontPath == path) null else settings.customFontPath
                    val newFam = if (newPath == null) "monospace" else "custom"
                    onSettingsChange(settings.copy(editorFontList = newList, customFontPath = newPath, fontFamily = newFam))
                }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Upload Custom Font", fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/vnd.ms-opentype", "*/*"))
            }) { Text("Upload") }
        }
        HorizontalDivider()

        Text("Tab Width: ${settings.tabWidth} spaces", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.tabWidth.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(tabWidth = it.toInt())) },
            valueRange = 2f..8f,
            steps = 5
        )

        SettingSwitchRow("Line Numbers", settings.showLineNumbers) { onSettingsChange(settings.copy(showLineNumbers = it)) }
        SettingSwitchRow("Word Wrap", settings.wordWrap) { onSettingsChange(settings.copy(wordWrap = it)) }
        SettingSwitchRow("Auto Indent", settings.autoIndent) { onSettingsChange(settings.copy(autoIndent = it)) }
        SettingSwitchRow("Autocomplete", settings.autocomplete) { onSettingsChange(settings.copy(autocomplete = it)) }
        SettingSwitchRow("Indent Guide", settings.indentGuide) { onSettingsChange(settings.copy(indentGuide = it)) }
        SettingSwitchRow("Highlight Active Line", settings.highlightActiveLine) { onSettingsChange(settings.copy(highlightActiveLine = it)) }
        SettingSwitchRow("Auto Close Brackets", settings.closeBracket) { onSettingsChange(settings.copy(closeBracket = it)) }
    }
}

@Composable
private fun AppSettingsTab(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    appFontLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.themeName == THEME_LIGHT, onClick = { onSettingsChange(settings.copy(themeName = THEME_LIGHT)) })
                Spacer(Modifier.width(4.dp))
                Text("Light", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.themeName == THEME_DARK, onClick = { onSettingsChange(settings.copy(themeName = THEME_DARK)) })
                Spacer(Modifier.width(4.dp))
                Text("Dark", fontSize = 13.sp)
            }
        }
        // PLUGIN_THEMES_UI
        val pluginThemes = com.egg.extension.api.ThemeRegistry.all()
        if (pluginThemes.isNotEmpty()) {
            Text("Plugin Themes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pluginThemes.forEach { spec ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeName == spec.id,
                        onClick = { onSettingsChange(settings.copy(themeName = spec.id)) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(spec.name, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider()

        Text("App Font", style = MaterialTheme.typography.labelLarge)
        listOf("sans" to "Sans Serif", "serif" to "Serif", "monospace" to "Monospace").forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.appFontFamily == key, onClick = { onSettingsChange(settings.copy(appFontFamily = key)) })
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 13.sp)
            }
        }

        settings.appFontList.forEach { path ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.appFontFamily == "custom" && settings.appCustomFontPath == path,
                    onClick = { onSettingsChange(settings.copy(appFontFamily = "custom", appCustomFontPath = path)) }
                )
                Text(File(path).name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val newList = settings.appFontList - path
                    val newPath = if (settings.appCustomFontPath == path) null else settings.appCustomFontPath
                    val newFam = if (newPath == null) "sans" else "custom"
                    onSettingsChange(settings.copy(appFontList = newList, appCustomFontPath = newPath, appFontFamily = newFam))
                }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Upload App Font", fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                appFontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/vnd.ms-opentype", "*/*"))
            }) { Text("Upload") }
        }

        Text("App Font Size: " + (settings.appFontScale * 100).toInt() + "%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.appFontScale,
            onValueChange = { onSettingsChange(settings.copy(appFontScale = it)) },
            valueRange = 0.80f..1.40f,
            steps = 11
        )
    }
}

@Composable
private fun OtherSettingsTab(
    settings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
    backupLauncher: ActivityResultLauncher<String>,
    restoreLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSwitchRow("Confirm on Exit", settings.confirmExit) { onSettingsChange(settings.copy(confirmExit = it)) }
        SettingSwitchRow("Show Bottom Bar", settings.showBottomBar) { onSettingsChange(settings.copy(showBottomBar = it)) }
        HorizontalDivider()

        Text("Backup & Restore", style = MaterialTheme.typography.labelLarge)
        Text(
            "Backup saves settings plus all open tabs to a JSON file. Restore loads them back.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { backupLauncher.launch("egg-backup.json") }) {
                Text("Backup", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text("Restore", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DiscordSettingsTab(
    token: String,
    enabled: Boolean,
    onTokenChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    var localToken by remember { mutableStateOf(token) }
    var savedMsg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Discord Rich Presence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Show the file you are editing on your Discord profile. Paste a User Token from Discord or Aliucord.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = localToken,
            onValueChange = { localToken = it },
            label = { Text("User Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            onTokenChange(localToken.trim())
            onEnabledChange(true)
            savedMsg = "Token saved, presence enabled"
        }) {
            Text("Save Token")
        }
        if (savedMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(savedMsg, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}


@Composable
private fun ExtensionsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val name = getFileNameStatic(context, it)
                    val target = File(ExtensionLoader.extensionsRoot(context), name)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                    status = "Installed: $name"
                    ExtensionManager.unloadAll()
                    ExtensionManager.loadAll(context)
                    refreshKey++
                } catch (e: Exception) {
                    status = "Install failed: ${e.message}"
                }
            }
        }
    }

    val loaded = ExtensionManager.loaded()

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Installed Extensions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Button(onClick = { installLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Install .egg", fontSize = 12.sp)
            }
        }

        if (loaded.isEmpty()) {
            Text("No extensions loaded.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            loaded.values.forEach { ext ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ext.manifest.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${ext.manifest.id} · v${ext.manifest.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ext.manifest.description.isNotEmpty()) {
                            Text(ext.manifest.description, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ext.source.delete()
                                File(ExtensionLoader.extensionsRoot(context), ext.manifest.id).deleteRecursively()
                            }
                            ExtensionManager.unload(ext.manifest.id)
                            status = "Uninstalled: ${ext.manifest.name}"
                            refreshKey++
                        }
                    }) { Text("Uninstall", fontSize = 12.sp) }
                }
            }
        }

        status?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun getFileNameStatic(context: android.content.Context, uri: Uri): String {
    var name = "extension.egg"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}
