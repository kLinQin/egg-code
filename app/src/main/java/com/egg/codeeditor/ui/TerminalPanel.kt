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
        onOpenFile = { path -> vm.openTerminalFile(path) },
        onClose = {
            vm.terminalVisible = false
            onRequestEditorFocus()
        }
    )
}
