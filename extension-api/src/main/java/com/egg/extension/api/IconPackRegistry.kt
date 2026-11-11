package com.egg.extension.api

import java.io.File

object IconPackRegistry {
    private val overrides = mutableMapOf<String, IconOverride>()
    private val extensionDir = mutableMapOf<String, File>()

    fun register(override: IconOverride, pluginDir: File) {
        val ext = override.ext.lowercase().trimStart('.')
        if (ext.isBlank()) return
        overrides[ext] = override
        extensionDir[override.extensionId] = pluginDir
    }

    fun unregister(extensionId: String) {
        overrides.entries.removeAll { it.value.extensionId == extensionId }
        extensionDir.remove(extensionId)
    }

    fun get(ext: String): IconOverride? = overrides[ext.lowercase().trimStart('.')]

    /** Absolute path to drawable file, or null if not set/missing. */
    fun drawablePath(override: IconOverride): File? {
        val name = override.drawableFile ?: return null
        val dir = extensionDir[override.extensionId] ?: return null
        val f = File(dir, "iconpack/drawable/$name")
        return f.takeIf { it.exists() }
    }

    fun all(): List<IconOverride> = overrides.values.toList()
    fun clear() { overrides.clear(); extensionDir.clear() }
}
