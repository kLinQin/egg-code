package com.egg.codeeditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.egg.codeeditor.terminal.EggTerminalScreen

@Composable
internal fun TerminalPanel(
    vm: EggViewModel,
    onRequestEditorFocus: () -> Unit,
) {
    EggTerminalScreen(
        modifier = Modifier,
        workingDir = vm.rootFolderPath ?: "/",
        isDarkTheme = com.egg.extension.api.ThemeRegistry.get(vm.settings.themeName)?.dark ?: (vm.settings.themeName == "darcula"),
        onOpenFile = { path -> vm.openTerminalFile(path) },
        terminalFontSize = vm.settings.terminalFontSize,
        terminalCursorStyle = vm.settings.terminalCursorStyle,
        terminalScrollback = vm.settings.terminalScrollback,
        onClose = {
            vm.terminalVisible = false
            onRequestEditorFocus()
        }
    )
}
