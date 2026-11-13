package com.egg.codeeditor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.egg.codeeditor.terminal.EggTerminalScreen
import com.egg.codeeditor.terminal.EggTerminalEnv
import com.egg.codeeditor.terminal.TerminalSessionService

@Composable
internal fun TerminalPanel(
    vm: EggViewModel,
    onRequestEditorFocus: () -> Unit,
) {
    val context = LocalContext.current
    var binder by remember { mutableStateOf<TerminalSessionService.SessionBinder?>(null) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service as TerminalSessionService.SessionBinder
            }
            override fun onServiceDisconnected(name: ComponentName) {
                binder = null
            }
        }
        val intent = Intent(context, TerminalSessionService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connection)
        }
    }

    EggTerminalScreen(
        modifier = Modifier,
        workingDir = if (vm.settings.projectAsPwd && vm.rootFolderPath != null) vm.rootFolderPath!! else EggTerminalEnv.sandboxHomeDir().absolutePath,
        isDarkTheme = com.egg.extension.api.ThemeRegistry.get(vm.settings.themeName)?.dark ?: (vm.settings.themeName == "darcula"),
        onOpenFile = { path -> vm.openTerminalFile(path) },
        terminalFontSize = vm.settings.terminalFontSize,
        terminalCursorStyle = vm.settings.terminalCursorStyle,
        terminalScrollback = vm.settings.terminalScrollback,
        serviceBinder = binder,
        onClose = {
            vm.terminalVisible = false
            onRequestEditorFocus()
        }
    )
}
