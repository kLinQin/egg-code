package com.egg.codeeditor.terminal

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import java.util.Properties
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggTerminalScreen(
    modifier: Modifier = Modifier,
    workingDir: String = EggTerminalEnv.sandboxHomeDir().absolutePath,
    isDarkTheme: Boolean = true,
    onClose: () -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    terminalFontSize: Int = 14,
    terminalCursorStyle: String = "block",
    terminalScrollback: Int = 2000,
) {
    val sessions = remember { mutableStateListOf<TerminalSession>() }
    var currentIndex by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var ctrlDown by remember { mutableStateOf(false) }
    var altDown by remember { mutableStateOf(false) }
    val backend = remember { Backend() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // update flag ctrl/alt ke backend
    SideEffect {
        backend.ctrl = ctrlDown
        backend.alt = altDown
        backend.onOpenFile = onOpenFile
    }

    fun newSession() {
        val session = TerminalSession(
            "/system/bin/sh",
            workingDir,
            arrayOf("-i"),          // interactive shell
            null,
            terminalScrollback,
            backend
        )
        sessions.add(session)
        currentIndex = sessions.lastIndex
    }

    LaunchedEffect(Unit) { if (sessions.isEmpty()) newSession() }

    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    val session = sessions.getOrNull(currentIndex)
    val bgArgb = MaterialTheme.colorScheme.background.toArgb()
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val terminalColors = remember(isDarkTheme, surfaceArgb, onSurfaceArgb) {
        buildTerminalColors(isDarkTheme, surfaceArgb, onSurfaceArgb)
    }

    fun writeSeq(seq: String) {
        val s = session ?: return
        if (ctrlDown && seq.length == 1) {
            s.writeCtrl(seq[0])
        } else if (altDown && seq.length == 1) {
            s.writeStr("\u001B" + seq)
        } else {
            s.writeStr(seq)
        }
        if (ctrlDown) ctrlDown = false
        if (altDown) altDown = false
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Menu") }
                Text("Terminal", Modifier.weight(1f), fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace)
                IconButton(onClick = { newSession() }) { Icon(Icons.Default.Add, "New session") }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }

            if (session != null) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            setTerminalViewClient(backend)
                            setTextSize(terminalFontSize * 2)
                            val fontFile = File(EggTerminalEnv.sandboxHomeDir().parentFile, "etc/font.ttf")
                            if (fontFile.exists()) setTypeface(Typeface.createFromFile(fontFile))
                            setBackgroundColor(bgArgb)
                            applyTerminalColors(surfaceArgb, onSurfaceArgb, terminalColors)
                            attachSession(session)
                            backend.cursorStyle = when (terminalCursorStyle) {
                                "bar" -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
                                "underline" -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
                                else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
                            }
                            terminalViewRef = java.lang.ref.WeakReference(this)
                            isFocusableInTouchMode = true
                            isFocusable = true
                            post {
                                keepScreenOn = true
                                isFocusableInTouchMode = true
                                requestFocus()
                            }
                        }
                    },
                    update = { view ->
                        if (view.currentSession !== session) view.attachSession(session)
                        view.applyTerminalColors(surfaceArgb, onSurfaceArgb, terminalColors)
                    }
                )
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Starting…")
                }
            }

            // Extra keys: CTRL toggle + ALT toggle + tombol escape sequences
            Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = ctrlDown,
                        onClick = { ctrlDown = !ctrlDown },
                        label = { Text("CTRL", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.width(4.dp))
                    FilterChip(
                        selected = altDown,
                        onClick = { altDown = !altDown },
                        label = { Text("ALT", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.width(8.dp))
                    listOf(
                        "ESC" to "\u001B", "TAB" to "\u0009", "/" to "/", "-" to "-",
                        "HOME" to "\u001B[H", "↑" to "\u001B[A", "END" to "\u001B[F",
                        "←" to "\u001B[D", "↓" to "\u001B[B", "→" to "\u001B[C",
                        "PGUP" to "\u001B[5~", "PGDN" to "\u001B[6~"
                    ).forEach { (label, seq) ->
                        TextButton(onClick = { writeSeq(seq) },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        if (menuOpen) {
            ModalBottomSheet(onDismissRequest = { menuOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Sessions", fontSize = 14.sp)
                    sessions.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { currentIndex = i; menuOpen = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Session ${i+1} (pid ${s.pid})")
                            }
                            IconButton(onClick = {
                                runCatching { s.writeStr("exit\n") }
                                sessions.removeAt(i)
                                if (sessions.isEmpty()) {
                                    newSession()
                                } else if (currentIndex >= sessions.size) {
                                    currentIndex = sessions.lastIndex
                                }
                            }) {
                                Icon(Icons.Default.Close, "Close session")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { newSession(); menuOpen = false }) {
                        Text("+ New session")
                    }
                }
            }
        }
    }
}


private fun TerminalSession.writeStr(s: String) {
    val b = s.toByteArray(Charsets.UTF_8)
    write(b, 0, b.size)
}

private fun TerminalSession.writeCtrl(ch: Char) {
    val code = ch.uppercaseChar().code - 'A'.code + 1
    if (code in 1..26) write(byteArrayOf(code.toByte()), 0, 1)
}

@Volatile
private var terminalViewRef: java.lang.ref.WeakReference<TerminalView?> = java.lang.ref.WeakReference(null)

class Backend : TerminalSessionClient, TerminalViewClient {
    @Volatile var ctrl: Boolean = false
    @Volatile var alt: Boolean = false
    @Volatile var currentCwd: String = ""

    // ===== TerminalSessionClient =====
    override fun onTextChanged(session: TerminalSession) {
        terminalViewRef.get()?.onScreenUpdated()
    }
    var onOpenFile: ((String) -> Unit)? = null

    override fun getFilePathRanges(text: String, row: Int): List<IntArray> = emptyList()

    private fun openFileFromTerminal(file: File) {
        onOpenFile?.invoke(file.absolutePath)
    }


    private fun getCwdForRow(row: Int): File {
        val guest = terminalViewRef.get()?.mEmulator?.getCwdForRow(row) ?: return getCwd()
        return terminalToHostFile(guest)
    }

    private fun getCwd(): File = EggTerminalEnv.sandboxHomeDir()

    override fun onCwdChanged(session: TerminalSession, guestPath: String) {
        currentCwd = guestPath
    }
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cm = EggTerminalEnv.application?.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cm = EggTerminalEnv.application?.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
        val txt = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        session?.writeStr(txt)
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    var cursorStyle: Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
    override fun getTerminalCursorStyle(): Int = cursorStyle
    override fun logError(tag: String?, message: String?) { android.util.Log.e(tag ?: "terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}

    // ===== TerminalViewClient =====
    override fun onScale(scale: Float): Float = scale.coerceIn(11f, 45f)
    override fun onSingleTapUp(e: MotionEvent) {
        val tv = terminalViewRef.get()
        if (tv == null) {
            showSoftInput()
            return
        }

        val colAndRow = tv.getColumnAndRow(e, true)
        val col = colAndRow[0]
        val row = colAndRow[1]
        val emulator = tv.mEmulator
        if (emulator == null) {
            showSoftInput()
            return
        }

        runCatching {
            val screen = emulator.screen
            val internalRow = screen.externalToInternalRow(row)
            val lineObj = screen.allocateFullLineIfNecessary(internalRow)
            val rowText = String(lineObj.mText, 0, lineObj.spaceUsed)
            val matches = FilePathUtils.detectFilePaths(rowText, getCwdForRow(row))
            val clickedMatch = matches.find { col >= it.start && col < it.end }
            if (clickedMatch != null) {
                openFileFromTerminal(clickedMatch.file)
                return
            }
        }
        showSoftInput()
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrl
    override fun readAltKey(): Boolean = alt
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun shouldSupportClipboardKeybindings(): Boolean = true

    private fun showSoftInput() {
        val tv = terminalViewRef.get() ?: return
        tv.requestFocus()
        val imm = EggTerminalEnv.application
            ?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager ?: return
        imm.showSoftInput(tv, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
}

private val DARK_ANSI_PALETTE = mapOf(
    "color0" to "#393552", "color8" to "#6e6a86",
    "color1" to "#eb6f92", "color9" to "#eb6f92",
    "color2" to "#3e8fb0", "color10" to "#3e8fb0",
    "color3" to "#f6c177", "color11" to "#f6c177",
    "color4" to "#9ccfd8", "color12" to "#9ccfd8",
    "color5" to "#c4a7e7", "color13" to "#c4a7e7",
    "color6" to "#ea9a97", "color14" to "#ea9a97",
    "color7" to "#e0def4", "color15" to "#e0def4"
)

private val LIGHT_ANSI_PALETTE = mapOf(
    "color0" to "#1d1f21", "color8" to "#969896",
    "color1" to "#CC342B", "color9" to "#CC342B",
    "color2" to "#198844", "color10" to "#198844",
    "color3" to "#FBA922", "color11" to "#FBA922",
    "color4" to "#3971ED", "color12" to "#3971ED",
    "color5" to "#A36AC7", "color13" to "#A36AC7",
    "color6" to "#3971ED", "color14" to "#3971ED",
    "color7" to "#c5c8c6", "color15" to "#ffffff",
    "color16" to "#F96A38", "color17" to "#3971ED",
    "color18" to "#282a2e", "color19" to "#373b41",
    "color20" to "#b4b7b4", "color21" to "#e0e0e0"
)

private fun Int.toHexColor(): String {
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}

private fun buildTerminalColors(isDarkTheme: Boolean, surfaceArgb: Int, onSurfaceArgb: Int): Properties =
    Properties().apply {
        put("background", surfaceArgb.toHexColor())
        put("foreground", onSurfaceArgb.toHexColor())
        put("cursor", onSurfaceArgb.toHexColor())
        (if (isDarkTheme) DARK_ANSI_PALETTE else LIGHT_ANSI_PALETTE).forEach { (k, v) -> put(k, v) }
    }

private fun TerminalView.applyTerminalColors(surfaceColor: Int, onSurfaceColor: Int, terminalColors: Properties) {
    onScreenUpdated()
    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(terminalColors)
    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }
    invalidate()
}
