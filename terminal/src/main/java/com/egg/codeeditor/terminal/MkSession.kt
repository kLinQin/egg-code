package com.egg.codeeditor.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        sessionId: String,
    ): Pair<TerminalSession, SessionPwd> {
        val env = mutableListOf(
            "PROOT=${context.applicationInfo.nativeLibraryDir}/libproot.so",
            "PROOT_LOADER=${context.applicationInfo.nativeLibraryDir}/libloader.so",
            "PROOT_TMP_DIR=${EggTerminalEnv.getTempDir(context).absolutePath}/terminal/$sessionId",
            "WKDIR=${EggTerminalEnv.sandboxHomeDir(context).absolutePath}",
            "PUBLIC_HOME=${context.getExternalFilesDir(null)?.absolutePath}",
            "COLORTERM=truecolor",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LOCAL=${EggTerminalEnv.localDir(context).absolutePath}",
            "LD_LIBRARY_PATH=${EggTerminalEnv.localLibDir(context).absolutePath}",
            "EXT_HOME=${EggTerminalEnv.sandboxHomeDir(context).absolutePath}",
            "HOME=/home",
            "PROMPT_DIRTRIM=2",
            "NATIVE_LIB_DIR=${context.applicationInfo.nativeLibraryDir}",
            "TMP_DIR=${EggTerminalEnv.getTempDir(context).absolutePath}",
            "TMPDIR=${EggTerminalEnv.getTempDir(context).absolutePath}",
            "TZ=UTC",
            "PROMPT_COMMAND=__p=\${PWD//\\%/%25}; printf '\\e]7;file://%s%s\\a' \"\$HOSTNAME\" \"\${__p// /%20}\"",
        )

        val loader32 = "${context.applicationInfo.nativeLibraryDir}/libloader32.so"
        if (File(loader32).exists()) {
            env.add("PROOT_LOADER_32=$loader32")
        }

        env.add("PATH=${System.getenv("PATH")}:${EggTerminalEnv.localBinDir(context).absolutePath}")

        val tmpDir = File(EggTerminalEnv.getTempDir(context), "terminal/$sessionId")
        if (!tmpDir.exists()) tmpDir.mkdirs()

        val sandboxSH = File(EggTerminalEnv.localBinDir(context), "sandbox")
        val shell = "/system/bin/sh"
        val args = arrayOf("-c", sandboxSH.absolutePath)

        val workingDir = EggTerminalEnv.sandboxHomeDir(context).absolutePath

        val session = TerminalSession(
            shell,
            EggTerminalEnv.localDir(context).absolutePath,
            args,
            env.toTypedArray(),
            2000,
            sessionClient,
        )

        return session to workingDir
    }
}
