package com.egg.codeeditor.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.ui.components.TerminalShortcutBar
import com.egg.codeeditor.ui.terminal.buildCdCommand
import org.connectbot.terminal.Terminal
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TerminalPanel(
    vm: EggViewModel,
    onRequestEditorFocus: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val terminalFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // set warna SEBELUM emulator dibuat supaya frame pertama sudah benar
    val bg0 = MaterialTheme.colorScheme.background
    val fg0 = MaterialTheme.colorScheme.onBackground
    androidx.compose.runtime.SideEffect {
        vm.setTerminalColors(bg0, fg0)
    }

    // Pemicu start sesi — ownership ada di VM, jadi tetap hidup lintas recompose.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }.recoverCatching {
                context.startActivity(android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
        vm.ensureTerminalStarted()
    }

    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    LaunchedEffect(bg, fg) {
        vm.setTerminalColors(bg, fg)
    }

    LaunchedEffect(vm.terminalEmulator) {
        if (vm.terminalEmulator != null) {
            delay(200.milliseconds)
            terminalFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // auto-cd saat folder root berganti
    LaunchedEffect(vm.rootFolderPath) {
        val folder = vm.rootFolderPath ?: return@LaunchedEffect
        delay(300.milliseconds)
        val cmd = buildCdCommand(folder)
        vm.writeTerminal((cmd + "\r\n").toByteArray())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Alpine Terminal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        vm.terminalVisible = false
                        onRequestEditorFocus()
                    }) {
                        Icon(Icons.Default.Close, "Close Terminal", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Text(
                    "Folder: ${vm.rootFolderPath ?: "No folder open"}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val emulator = vm.terminalEmulator
            if (emulator != null) {
                Terminal(
                    terminalEmulator = emulator,
                    backgroundColor = vm.terminalBg,
                    foregroundColor = vm.terminalFg,
                    selectionBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    selectionForegroundColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .focusRequester(terminalFocusRequester)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    scope.launch {
                                        terminalFocusRequester.requestFocus()
                                        delay(150.milliseconds)
                                        try { keyboardController?.show() } catch (_: Exception) {}
                                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                        val focused = (context as? Activity)?.currentFocus
                                        if (focused != null) {
                                            @Suppress("DEPRECATION")
                                            imm?.showSoftInput(focused, InputMethodManager.SHOW_FORCED)
                                        }
                                    }
                                }
                            }
                        },
                    keyboardEnabled = true,
                    showSoftKeyboard = true,
                    focusRequester = terminalFocusRequester
                )
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Loading terminal...", color = MaterialTheme.colorScheme.primary)
                }
            }

            TerminalShortcutBar(
                onCommand = { cmd -> vm.writeTerminal((cmd + "\r\n").toByteArray()) }
            )
        }
    }
}
