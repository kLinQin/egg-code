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
        val workDir = File(extensionsRoot(ctx), eggFile.nameWithoutExtension)
        workDir.deleteRecursively()
        workDir.mkdirs()

        ZipFile(eggFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val safeName = entry.name.replace("..", "").trimStart('/')
                val out = File(workDir, safeName)
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

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

        val dexFile = File(workDir, DEX)
        val instance: ExtensionAPI? = if (dexFile.exists()) {
            dexFile.setReadOnly()
            val loader = DexClassLoader(
                dexFile.absolutePath,
                File(ctx.codeCacheDir, "ext_${manifest.id}").apply { mkdirs() }.absolutePath,
                null,
                ctx.classLoader
            )
            val cls = loader.loadClass(manifest.mainClass)
            val extCtx = ExtensionContext(ctx.applicationContext, manifest.id, workDir)
            cls.getConstructor(ExtensionContext::class.java).newInstance(extCtx) as ExtensionAPI
        } else null

        val grammarDir = File(workDir, "grammar")
        if (grammarDir.exists()) {
            grammarDir.listFiles { f -> f.isFile && (f.name.endsWith(".json") || f.name.endsWith(".tmLanguage.json") || f.name.endsWith(".tmLanguage")) }?.forEach { gf ->
                try {
                    val metaFile = File(gf.parentFile, gf.nameWithoutExtension.removeSuffix(".tmLanguage") + ".meta.json")
                    val meta = if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
                    val scope = meta.optString("scopeName", "source." + gf.nameWithoutExtension.removeSuffix(".tmLanguage").lowercase())
                    val cfgFile = File(grammarDir, gf.nameWithoutExtension.removeSuffix(".tmLanguage") + ".config.json").takeIf { it.exists() }
                    val extArr = mutableListOf<String>()
                    meta.optJSONArray("extensions")?.let { arr -> for (i in 0 until arr.length()) extArr.add(arr.getString(i)) }
                    com.egg.extension.api.GrammarRegistry.register(
                        com.egg.extension.api.GrammarSpec(
                            name = meta.optString("name", gf.nameWithoutExtension),
                            scopeName = scope,
                            grammarFile = gf,
                            languageConfigFile = cfgFile,
                            extensions = extArr,
                            extensionId = manifest.id
                        )
                    )
                    android.util.Log.d("EggGrammar", "registered: " + scope + " (" + gf.name + ")")
                } catch (e: Exception) {
                    android.util.Log.e("EggGrammar", "grammar register failed: " + gf.name, e)
                }
            }
        }

        val iconPackFile = File(workDir, "iconpack/icons.json")
        if (iconPackFile.exists()) {
            try {
                val iconJson = JSONObject(iconPackFile.readText())
                val iconsObj = iconJson.optJSONObject("icons") ?: JSONObject()
                iconsObj.keys().forEach { ext ->
                    val o = iconsObj.getJSONObject(ext)
                    com.egg.extension.api.IconPackRegistry.register(
                        com.egg.extension.api.IconOverride(
                            ext = ext,
                            drawableFile = o.optString("drawable").takeIf { it.isNotEmpty() },
                            color = o.optString("color").takeIf { it.isNotEmpty() },
                            extensionId = manifest.id
                        ),
                        workDir
                    )
                }
                android.util.Log.d("EggIconPack", "registered " + iconsObj.length() + " icons from " + manifest.id)
            } catch (e: Exception) {
                android.util.Log.e("EggIconPack", "iconpack parse failed", e)
            }
        }

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
                    syntaxPath = run {
                        val sp = themeJson.optString("syntaxPath").takeIf { it.isNotEmpty() }
                        sp?.let { File(workDir, it).takeIf { f -> f.exists() }?.absolutePath }
                    },
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
        val api: ExtensionAPI?,
        val source: File
    )
}
