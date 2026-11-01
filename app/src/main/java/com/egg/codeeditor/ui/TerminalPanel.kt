package com.egg.codeeditor.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import id.or.oo.pr.engine.*
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory

/** Seluruh UI terminal + lifecycle sesi (instal alpine, PTY, IME, notifikasi via vm). */
@Composable
internal fun TerminalPanel(
    vm: EggViewModel,
    onRequestEditorFocus: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var terminalEmulator by remember { mutableStateOf<TerminalEmulator?>(null) }
    var terminalSession by remember { mutableStateOf<PdnTerminalSession?>(null) }

    val terminalFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val prootHost = remember {
        object : ProotHost {
            override val nativeLibDir: File get() = File(context.applicationInfo.nativeLibraryDir)
            override val prefixDir: File get() = File(context.filesDir, "pdn")
            override val homeDir: File get() = File(context.filesDir, "home")
            override val packageName: String get() = context.packageName
            override val cacheDir: File get() = context.cacheDir
        }
    }
    val pdnRuntime = remember { PdnRuntime(prootHost) }
    val pdnOps = remember { PdnOperations(pdnRuntime) }
    val pdnTerminal = remember { PdnTerminal(pdnRuntime) }

    // Mulai sesi terminal (sekali per pembukaan panel)
    LaunchedEffect(Unit) {
        if (terminalEmulator == null) {
            val emulator = TerminalEmulatorFactory.create(
                onKeyboardInput = { data ->
                    terminalSession?.let { s ->
                        runCatching { s.write(data) }.onFailure { it.printStackTrace() }
                    }
                },
                onResize = { dims ->
                    scope.launch {
                        runCatching { terminalSession?.resize(dims.rows, dims.columns) }
                            .onFailure { it.printStackTrace() }
                    }
                }
            )
            terminalEmulator = emulator
        }

        val emulator = terminalEmulator ?: return@LaunchedEffect

        fun printToScreen(msg: String) {
            val b = msg.toByteArray(Charsets.UTF_8)
            emulator.writeInput(b, 0, b.size)
        }

        val alpineDir = File(context.filesDir, "pdn/var/lib/pdn/rootfs/alpine")
        if (!alpineDir.exists()) {
            try {
                printToScreen("Installing Alpine (automatic)...\r\n")
                val listener = object : PdnListener {
                    override fun onEvent(event: PdnEvent) {}
                }
                val installBuilder = pdnRuntime.install("alpine")
                pdnOps.run(installBuilder, listener)
                printToScreen("Installation complete. Starting shell...\r\n")
            } catch (e: Exception) {
                printToScreen("Install failed: ${e.message}\r\n")
            }
        }

        if (terminalSession == null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    printToScreen("Grant All Files Access to see your storage...\r\n")
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }.recoverCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }

                printToScreen("Starting Alpine shell...\r\n")
                val pdnConfig = PdnConfiguration(
                    "root",
                    "/workspace",
                    listOf(PdnBind(File("/storage/emulated/0"), "/mnt/sdcard")),
                    emptyMap()
                )
                val loginBuilder = pdnRuntime.login("alpine", pdnConfig)
                val listener = object : PdnTerminalListener {}
                val session = pdnTerminal.start(loginBuilder, 80, 24, listener)
                terminalSession = session

                try {
                    session.resize(emulator.dimensions.rows, emulator.dimensions.columns)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                vm.rootFolderPath?.let { folder ->
                    val cdCmd = buildCdCommand(folder)
                    scope.launch {
                        delay(600.milliseconds)
                        try {
                            terminalSession?.write((cdCmd + "\r\n").toByteArray())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                scope.launch(Dispatchers.IO) {
                    val buffer = ByteArray(4096)
                    try {
                        while (!session.isClosed) {
                            val read = session.read(buffer)
                            if (read > 0) {
                                emulator.writeInput(buffer, 0, read)
                            } else if (read < 0) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        val msg = "\r\n[Terminal closed: ${e.message}]\r\n"
                        val b = msg.toByteArray(Charsets.UTF_8)
                        emulator.writeInput(b, 0, b.size)
                    }
                }
            } catch (e: Exception) {
                printToScreen("Failed to start shell: ${e.message}\r\n")
            }
        }
    }

    // Paksa keyboard (IME) muncul saat terminal terbuka
    LaunchedEffect(terminalEmulator) {
        if (terminalEmulator != null) {
            delay(200.milliseconds)
            terminalFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Header terminal
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

            val emulator = terminalEmulator
            if (emulator != null) {
                Terminal(
                    terminalEmulator = emulator,
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
                                        try { keyboardController?.show() } catch (e: Exception) { e.printStackTrace() }
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
                onCommand = { cmd ->
                    terminalSession?.let { session ->
                        runCatching { session.write((cmd + "\r\n").toByteArray()) }
                    }
                }
            )
        }
    }
}