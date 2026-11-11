package com.egg.codeeditor.extension

import android.content.Context
import com.egg.extension.api.ThemeRegistry
import android.util.Log
import java.io.File

object ExtensionManager {
    private val loaded = mutableMapOf<String, ExtensionLoader.LoadedExtension>()

    fun loadAll(ctx: Context): List<Result<ExtensionLoader.LoadedExtension>> {
        Log.d("EggExtMgr", "loadAll start, root=" + ExtensionLoader.extensionsRoot(ctx).absolutePath)
        val results = mutableListOf<Result<ExtensionLoader.LoadedExtension>>()
        val eggs = ExtensionLoader.listEggFiles(ctx)
        Log.d("EggExtMgr", "found " + eggs.size + " egg files")
        eggs.forEach { Log.d("EggExtMgr", "egg: " + it.name + " size=" + it.length()) }
        ExtensionLoader.listEggFiles(ctx).forEach { file ->
            try {
                val ext = ExtensionLoader.load(ctx, file)
                ext.api.onLoad()
                Log.d("EggExtMgr", "loaded " + ext.manifest.id)
                loaded[ext.manifest.id] = ext
                results.add(Result.success(ext))
            } catch (t: Throwable) {
                Log.e("EggExtMgr", "load failed: " + file.name, t)
                results.add(Result.failure(t))
            }
        }
        return results
    }

    fun unload(id: String) {
        loaded.remove(id)?.api?.onDispose()
        ThemeRegistry.unregister(id)
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
