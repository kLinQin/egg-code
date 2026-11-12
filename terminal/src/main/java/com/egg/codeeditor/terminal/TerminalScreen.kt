package com.egg.codeeditor.terminal

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
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggTerminalScreen(
    modifier: Modifier = Modifier,
    workingDir: String = EggTerminalEnv.sandboxHomeDir().absolutePath,
    onClose: () -> Unit = {},
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
    }

    fun newSession() {
        val session = TerminalSession(
            "/system/bin/sh",
            workingDir,
            arrayOf("-i"),          // interactive shell
            null,
            2000,
            backend
        )
        sessions.add(session)
        currentIndex = sessions.lastIndex
    }

    LaunchedEffect(Unit) { if (sessions.isEmpty()) newSession() }

    val session = sessions.getOrNull(currentIndex)
    val bgArgb = MaterialTheme.colorScheme.background.toArgb()

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
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
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
                            setTextSize(28)
                            setBackgroundColor(bgArgb)
                            attachSession(session)
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
                        TextButton(onClick = { currentIndex = i; menuOpen = false }) {
                            Text("Session ${i+1} (pid ${s.pid})")
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

    // ===== TerminalSessionClient =====
    override fun onTextChanged(session: TerminalSession) {
        terminalViewRef.get()?.onScreenUpdated()
    }
    var onOpenFile: ((String) -> Unit)? = null

    override fun getFilePathRanges(text: String, row: Int): List<IntArray> {
        return FilePathUtils.detectFilePaths(text, getCwdForRow(row)).map { intArrayOf(it.start, it.end) }
    }

    private fun openFileFromTerminal(file: String) {
        val resolved = if (file.startsWith("/")) file else "$workingDir/$file"
        onOpenFile?.invoke(resolved)
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
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
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
                openFileFromTerminal(rowText.substring(clickedMatch.start, clickedMatch.end))
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
