package com.egg.codeeditor.terminal

import com.egg.codeeditor.terminal.child
import com.egg.codeeditor.terminal.EggTerminalEnv.getTempDir
import com.egg.codeeditor.terminal.EggTerminalEnv.sandboxDir
import com.egg.codeeditor.terminal.EggTerminalEnv.sandboxHomeDir
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NEXT_STAGE { NONE, EXTRACTION }

suspend fun CoroutineScope.getNextStage(): NEXT_STAGE = withContext(Dispatchers.IO) {
    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
    val rootfsFiles = sandboxDir().listFiles()?.filter {
        it.absolutePath != sandboxHomeDir().absolutePath &&
            it.absolutePath != sandboxDir().child("tmp").absolutePath
    } ?: emptyList()
    if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) NEXT_STAGE.NONE
    else NEXT_STAGE.EXTRACTION
}
