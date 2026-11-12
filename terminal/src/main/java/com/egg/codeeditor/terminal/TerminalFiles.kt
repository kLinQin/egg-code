package com.egg.codeeditor.terminal

import com.egg.codeeditor.terminal.EggTerminalEnv.application
import com.egg.codeeditor.terminal.child
import com.egg.codeeditor.terminal.EggTerminalEnv.createFileIfNot
import com.egg.codeeditor.terminal.EggTerminalEnv.localBinDir
import com.egg.codeeditor.terminal.EggTerminalEnv.localDir
import com.egg.codeeditor.terminal.EggTerminalEnv.sandboxDir

fun setupTerminalFiles() {
    if (!sandboxDir().exists() || !localBinDir().exists()) return

    listOf("stat", "vmstat").forEach { name ->
        with(localDir().child(name)) { if (!exists()) createFileIfNot(this) }
    }
    with(localBinDir().child("termux-x11")) {
        if (!exists()) {
            createFileIfNot(this)
            runCatching {
                writeText(application!!.assets.open("terminal/termux-x11.sh").bufferedReader().use { it.readText() })
            }
        }
    }
    listOf("init", "sandbox", "setup", "utils").forEach { setupAssetFile(it) }
    application!!.assets.list("terminal/lsp")?.forEach {
        setupLspFile(it.removeSuffix(".sh"))
    }
}

fun setupLspFile(fileName: String) = setupAssetFile("lsp/$fileName")

fun setupAssetFile(fileName: String) {
    with(localBinDir().child(fileName)) {
        parentFile?.mkdirs()
        if (!exists()) {
            createFileIfNot(this)
            runCatching {
                writeText(application!!.assets.open("terminal/$fileName.sh").bufferedReader().use { it.readText() })
            }
            setExecutable(true)
        }
    }
}
