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
        // FRESH_EXTRACT: hapus sisa extract lama (bisa read-only)
        val workDir = File(extensionsRoot(ctx), eggFile.nameWithoutExtension)
        workDir.deleteRecursively()
        workDir.mkdirs()

        // Extract zip
        // EXTRACT_ALL: extract semua entry (.dex, manifest, theme/, assets/)
        ZipFile(eggFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                // security: reject path traversal
                val safeName = entry.name.replace("..", "").trimStart('/')
                val out = File(workDir, safeName)
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
        dexFile.setReadOnly()
        val loader = DexClassLoader(
            dexFile.absolutePath,
            File(ctx.codeCacheDir, "ext_${manifest.id}").apply { mkdirs() }.absolutePath,
            null,
            ctx.classLoader
        )

        val cls = loader.loadClass(manifest.mainClass)
        val extCtx = ExtensionContext(ctx.applicationContext, manifest.id, workDir)
        val instance = cls.getConstructor(ExtensionContext::class.java).newInstance(extCtx) as ExtensionAPI

        // Auto-scan theme/compose.json → register
        val themeFile = File(workDir, "theme/compose.json")
        if (themeFile.exists()) {
            try {
                val themeJson = JSONObject(themeFile.readText())
                val colorsObj = themeJson.optJSONObject("colors") ?: JSONObject()
                val colors = mutableMapOf<String, String>()
                colorsObj.keys().forEach { k -> colors[k] = colorsObj.getString(k) }
                val spec = com.egg.extension.api.ThemeSpec(
                    id = themeJson.optString("id", manifest.id),
                    name = themeJson.optString("name", manifest.name + " Theme"),
                    base = themeJson.optString("base", "dark"),
                    colors = colors,
                    syntaxPath = themeJson.optString("syntaxPath").takeIf { it.isNotEmpty() },
                    dark = themeJson.optBoolean("dark", themeJson.optString("base", "dark") == "dark"),
                    extensionId = manifest.id
                )
                com.egg.extension.api.ThemeRegistry.register(spec)
                android.util.Log.d("EggThemeLoad", "registered theme: " + spec.id)
            } catch (e: Exception) {
                android.util.Log.e("EggThemeLoad", "theme parse failed", e)
            }
        }

        return LoadedExtension(manifest, instance, eggFile)
    }

    data class LoadedExtension(
        val manifest: ExtensionManifest,
        val api: ExtensionAPI,
        val source: File
    )
}
