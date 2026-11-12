package com.egg.codeeditor.terminal

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Adapter path/env terminal (pengganti com.rk.file.* / com.rk.utils.*).
 * Semua direktori mengikuti layout Xed supaya kode inti port 1:1.
 */
object EggTerminalEnv {
    var application: Application? = null

    fun init(app: Application) { application = app }

    private fun requireApp(): Application =
        application ?: throw IllegalStateException("EggTerminalEnv not initialized")

    fun localDir(ctx: Context = requireApp()): File =
        File(ctx.filesDir.parentFile!!, "local").apply { mkdirs() }

    fun localBinDir(ctx: Context = requireApp()): File =
        File(localDir(ctx), "bin").apply { mkdirs() }

    fun localLibDir(ctx: Context = requireApp()): File =
        File(localDir(ctx), "lib").apply { mkdirs() }

    fun sandboxDir(ctx: Context = requireApp()): File =
        File(localDir(ctx), "sandbox").apply { mkdirs() }

    fun sandboxHomeDir(ctx: Context = requireApp()): File =
        File(sandboxDir(ctx), "home").apply { mkdirs() }

    fun getTempDir(ctx: Context = requireApp()): File =
        File(ctx.cacheDir, "tempFiles").apply { mkdirs() }

    fun getSourceDirOfPackage(pkg: String): String? =
        try { requireApp().packageManager.getApplicationInfo(pkg, 0).sourceDir }
        catch (_: Exception) { null }

    fun createFileIfNot(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
    }
}

fun File.child(name: String): File = File(this, name)


private val identityMounts = listOf("/sdcard", "/storage", "/data", "/system", "/vendor", "/product", "/apex")

fun terminalToHostFile(path: String): File {
    val normalized = normalizeUriToPath(path)

    fun under(prefix: String) = normalized == prefix || normalized.startsWith("$prefix/")

    return when {
        under("~") -> File(EggTerminalEnv.sandboxHomeDir(), normalized.removePrefix("~"))
        under("/root") -> File(EggTerminalEnv.sandboxHomeDir(), normalized.removePrefix("/root"))
        under("/home") -> File(EggTerminalEnv.sandboxHomeDir(), normalized.removePrefix("/home"))
        identityMounts.any(::under) -> File(normalized)
        else -> File(EggTerminalEnv.sandboxDir(), normalized.removePrefix("/"))
    }
}

fun normalizeUriToPath(path: String): String =
    if (path.startsWith("file:")) {
        runCatching { File(java.net.URI(path)).path }.getOrDefault(path)
    } else {
        path
    }