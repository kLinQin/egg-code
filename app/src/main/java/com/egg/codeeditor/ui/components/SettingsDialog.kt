package com.egg.codeeditor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.EditorSettings
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
    onRestore: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Launcher: export backup to JSON file
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onBackup) }

    // Launcher: import restore from JSON file
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestore) }

    // Launcher: pick a custom font file (.ttf/.otf) → copy to filesDir → save path
    val fontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val dir = File(context.filesDir, "fonts")
                    if (!dir.exists()) dir.mkdirs()
                    val target = File(dir, "custom_font.ttf")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    onSettingsChange(settings.copy(fontFamily = "custom", customFontPath = target.absolutePath))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                    val target = File(dir, "app_font.ttf")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    onSettingsChange(settings.copy(appFontFamily = "custom", appCustomFontPath = target.absolutePath))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Full-screen, not a popup — mirrors the terminal pattern
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close Settings") }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ══════════ APP SECTION ══════════
                Text("App", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.themeName == THEME_LIGHT,
                            onClick = { onSettingsChange(settings.copy(themeName = THEME_LIGHT)) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Light", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.themeName == THEME_DARK,
                            onClick = { onSettingsChange(settings.copy(themeName = THEME_DARK)) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Dark", fontSize = 13.sp)
                    }
                }

                HorizontalDivider()

                Text("App Font", style = MaterialTheme.typography.labelLarge)
                listOf(
                    "sans" to "Sans Serif",
                    "serif" to "Serif",
                    "monospace" to "Monospace"
                ).forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.appFontFamily == key,
                            onClick = { onSettingsChange(settings.copy(appFontFamily = key)) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 13.sp)
                    }
                }
                if (settings.appFontFamily == "custom") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Custom app font: ${settings.appCustomFontPath?.substringAfterLast('/') ?: "?"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Upload App Font", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        appFontLauncher.launch(
                            arrayOf(
                                "font/ttf", "font/otf",
                                "application/x-font-ttf", "application/x-font-opentype",
                                "application/vnd.ms-opentype", "*/*"
                            )
                        )
                    }) { Text("Upload") }
                }

                Text("App Font Size: " + (settings.appFontScale * 100).toInt() + "%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = settings.appFontScale,
                    onValueChange = { onSettingsChange(settings.copy(appFontScale = it)) },
                    valueRange = 0.80f..1.40f,
                    steps = 11
                )

                HorizontalDivider()

                Text("Font Size: ${settings.fontSizeSp} sp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = settings.fontSizeSp.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it.toInt())) },
                    valueRange = 10f..24f,
                    steps = 13
                )

                HorizontalDivider()

                Text("Font Family", style = MaterialTheme.typography.labelLarge)
                listOf(
                    "monospace" to "Monospace",
                    "sans" to "Sans Serif",
                    "serif" to "Serif"
                ).forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.fontFamily == key,
                            onClick = { onSettingsChange(settings.copy(fontFamily = key)) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 13.sp)
                    }
                }
                if (settings.fontFamily == "custom") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Custom font: ${settings.customFontPath?.substringAfterLast('/') ?: "?"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Upload Custom Font", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        fontLauncher.launch(
                            arrayOf(
                                "font/ttf", "font/otf",
                                "application/x-font-ttf", "application/x-font-opentype",
                                "application/vnd.ms-opentype", "*/*"
                            )
                        )
                    }) { Text("Upload") }
                }

                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // ══════════ EDITOR SECTION ══════════
                Text("Editor", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confirm on Exit", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(
                        checked = settings.confirmExit,
                        onCheckedChange = { onSettingsChange(settings.copy(confirmExit = it)) }
                    )
                }

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

                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // ══════════ OTHER SECTION ══════════
                Text("Other", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

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