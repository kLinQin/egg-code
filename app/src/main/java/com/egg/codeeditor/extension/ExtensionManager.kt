package com.egg.codeeditor.extension

import android.content.Context
import java.io.File

object ExtensionManager {
    private val loaded = mutableMapOf<String, ExtensionLoader.LoadedExtension>()

    fun loadAll(ctx: Context): List<Result<ExtensionLoader.LoadedExtension>> {
        val results = mutableListOf<Result<ExtensionLoader.LoadedExtension>>()
        ExtensionLoader.listEggFiles(ctx).forEach { file ->
            try {
                val ext = ExtensionLoader.load(ctx, file)
                ext.api.onLoad()
                loaded[ext.manifest.id] = ext
                results.add(Result.success(ext))
            } catch (t: Throwable) {
                results.add(Result.failure(t))
            }
        }
        return results
    }

    fun unload(id: String) {
        loaded.remove(id)?.api?.onDispose()
    }

    fun unloadAll() {
        loaded.values.forEach { it.api.onDispose() }
        loaded.clear()
    }

    fun loaded(): Map<String, ExtensionLoader.LoadedExtension> = loaded.toMap()

    fun install(ctx: Context, source: File, targetName: String): File {
        val dest = File(ExtensionLoader.extensionsRoot(ctx), targetName)
        source.copyTo(dest, overwrite = true)
        return dest
    }
}
