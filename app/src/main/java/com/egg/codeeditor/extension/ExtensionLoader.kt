package com.egg.codeeditor.extension

import android.content.Context
import com.egg.extension.api.ExtensionAPI
import com.egg.extension.api.ExtensionContext
import com.egg.extension.api.ExtensionManifest
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object ExtensionLoader {

    private const val MANIFEST = "manifest.json"
    private const val DEX = "classes.dex"

    fun extensionsRoot(ctx: Context): File =
        File(ctx.filesDir, "extensions").apply { mkdirs() }

    fun listEggFiles(ctx: Context): List<File> =
        extensionsRoot(ctx).listFiles { f -> f.extension == "egg" }?.toList() ?: emptyList()

    /**
     * Extract .egg (zip) ke folder cache, load manifest, instantiate mainClass via DexClassLoader.
     * Returns loaded extension or throws.
     */
    fun load(ctx: Context, eggFile: File): LoadedExtension {
        val workDir = File(extensionsRoot(ctx), eggFile.nameWithoutExtension).apply { mkdirs() }

        // Extract zip
        ZipFile(eggFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                if (entry.name != MANIFEST && entry.name != DEX) return@forEach
                val out = File(workDir, entry.name)
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        // Parse manifest
        val manifestFile = File(workDir, MANIFEST)
        require(manifestFile.exists()) { "manifest.json missing in ${eggFile.name}" }
        val json = JSONObject(manifestFile.readText())
        val manifest = ExtensionManifest(
            id = json.getString("id"),
            name = json.getString("name"),
            mainClass = json.getString("mainClass"),
            version = json.optString("version", "1.0.0"),
            description = json.optString("description", ""),
            author = ExtensionManifest.Author(json.optJSONObject("author")?.optString("displayName") ?: "Unknown")
        )

        // DexClassLoader
        val dexFile = File(workDir, DEX)
        require(dexFile.exists()) { "classes.dex missing in ${eggFile.name}" }
        val loader = DexClassLoader(
            dexFile.absolutePath,
            File(ctx.codeCacheDir, "ext_${manifest.id}").apply { mkdirs() }.absolutePath,
            null,
            ctx.classLoader
        )

        val cls = loader.loadClass(manifest.mainClass)
        val extCtx = ExtensionContext(ctx.applicationContext, manifest.id, workDir)
        val instance = cls.getConstructor(ExtensionContext::class.java).newInstance(extCtx) as ExtensionAPI

        return LoadedExtension(manifest, instance, eggFile)
    }

    data class LoadedExtension(
        val manifest: ExtensionManifest,
        val api: ExtensionAPI,
        val source: File
    )
}
