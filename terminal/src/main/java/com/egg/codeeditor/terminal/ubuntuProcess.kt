package com.egg.codeeditor.terminal

import android.annotation.SuppressLint
import com.egg.codeeditor.terminal.EggTerminalEnv.application
import com.egg.codeeditor.terminal.child
import com.egg.codeeditor.terminal.EggTerminalEnv.createFileIfNot
import com.egg.codeeditor.terminal.EggTerminalEnv.getTempDir
import com.egg.codeeditor.terminal.EggTerminalEnv.localBinDir
import com.egg.codeeditor.terminal.EggTerminalEnv.localDir
import com.egg.codeeditor.terminal.EggTerminalEnv.localLibDir
import com.egg.codeeditor.terminal.EggTerminalEnv.sandboxDir
import com.egg.codeeditor.terminal.EggTerminalEnv.sandboxHomeDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Binding(val outside: String, val inside: String? = null)

private fun MutableList<String>.bind(outside: String, inside: String? = null) {
    if (File(outside).exists()) {
        add("-b")
        add("$outside${if (inside != null) ":$inside" else ""}")
    }
}

fun List<Binding>.attachTo(list: MutableList<String>, excludeMounts: List<String> = listOf()) {
    forEach {
        if (!excludeMounts.contains(it.outside)) list.bind(it.outside, it.inside)
    }
}

fun getDefaultBindings(): List<Binding> {
    fun MutableList<Binding>.b(outside: String, inside: String? = null) {
        if (File(outside).exists()) add(Binding(outside, inside))
    }
    return mutableListOf<Binding>().apply {
        b(sandboxHomeDir().absolutePath, "/home")
        b(sandboxHomeDir().absolutePath, "/root")
        b("/sdcard"); b("/storage"); b("/data")
        b(application!!.filesDir.parentFile!!.absolutePath)
        b("/dev"); b("/proc"); b("/system"); b("/sys")
        b("/dev/urandom", "/dev/random")
        b("/system_ext"); b("/product"); b("/odm"); b("/apex"); b("/vendor")
        b("/linkerconfig/ld.config.txt")
        b("/linkerconfig/com.android.art/ld.config.txt")
        b("/plat_property_contexts", "/property_contexts")
        b(getTempDir().absolutePath, "/dev/shm")
    }
}

suspend fun ubuntuProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    command: List<String>,
): Process = withContext(Dispatchers.IO) {
    if (!root.exists()) throw java.io.FileNotFoundException(root.absolutePath)

    val randomInt = Random.nextInt()
    val tmpDir = getTempDir().child("$randomInt-sandbox").apply { mkdirs() }
    val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

    val args = mutableListOf<String>().apply {
        add("${application!!.applicationInfo.nativeLibraryDir}/libproot.so")
        add("--kill-on-exit")
        if (workingDir != null) { add("-w"); add(workingDir) }
        getDefaultBindings().attachTo(this, excludeMounts)
        bind(tmpDir.absolutePath)
        add("-0"); add("--link2symlink"); add("--sysvipc"); add("-L")
        add("-r"); add(root.absolutePath)
        addAll(command)
    }

    val pb = ProcessBuilder(linker, *args.toTypedArray())
    pb.environment().apply {
        put("WKDIR", workingDir.orEmpty())
        put("COLORTERM", "truecolor")
        put("TERM", "xterm-256color")
        put("LANG", "C.UTF-8")
        put("PUBLIC_HOME", application!!.getExternalFilesDir(null)?.absolutePath.orEmpty())
        put("LOCAL", localDir().absolutePath)
        put("PRIVATE_DIR", application!!.filesDir.parentFile!!.absolutePath)
        put("EXT_HOME", sandboxHomeDir().absolutePath)
        put("HOME", "/home")
        put("PROMPT_DIRTRIM", "2")
        put("LINKER", linker)
        put("NATIVE_LIB_DIR", application!!.applicationInfo.nativeLibraryDir)
        put("PROOT", "${application!!.applicationInfo.nativeLibraryDir}/libproot.so")
        put("PROOT_LOADER", "${application!!.applicationInfo.nativeLibraryDir}/libloader.so")
        put("PROOT_TMP_DIR", tmpDir.absolutePath)
        put("SANDBOX", "true")
        put("TMP_DIR", getTempDir().absolutePath)
        put("TMPDIR", getTempDir().absolutePath)
        put("TZ", "UTC")
        put("SOURCE_DIR", application!!.applicationInfo.sourceDir)
        put("DISPLAY", ":0")
        put("LD_LIBRARY_PATH", localLibDir().absolutePath)
        put("PATH", "/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:${localBinDir()}:${System.getenv("PATH")}")
    }
    return@withContext pb.start()
}

@SuppressLint("SdCardPath")
suspend fun ubuntuProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    vararg command: String,
): Process = ubuntuProcess(excludeMounts, root, workingDir, command.toMutableList())
