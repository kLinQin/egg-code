package com.egg.codeeditor.extension

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class StorePlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val type: String,
    val url: String,
    val size: Long,
    val updated: String
)

object PluginStoreService {
    private const val INDEX_URL = "https://raw.githubusercontent.com/kLinQin/egg-plugin-store/main/index.json"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchIndex(): Result<List<StorePlugin>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(INDEX_URL).header("User-Agent", "Egg").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("empty body")
                val json = JSONObject(body)
                val arr = json.optJSONArray("plugins") ?: return@runCatching emptyList()
                val list = mutableListOf<StorePlugin>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(StorePlugin(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        version = o.optString("version", "1.0.0"),
                        author = o.optString("author", "Unknown"),
                        type = o.optString("type", "unknown"),
                        url = o.getString("url"),
                        size = o.optLong("size", 0L),
                        updated = o.optString("updated", "")
                    ))
                }
                list
            }
        }
    }

    suspend fun download(ctx: Context, plugin: StorePlugin): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(plugin.url).header("User-Agent", "Egg").build()
            val target = File(ExtensionLoader.extensionsRoot(ctx), plugin.id.substringAfterLast('.') + ".egg")
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            target
        }
    }

    suspend fun installFromStore(ctx: Context, plugin: StorePlugin): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = download(ctx, plugin).getOrThrow()
            // Register directly via loader
            val ext = ExtensionLoader.load(ctx, file)
            ext.api?.onLoad()
            // Note: ExtensionManager.loadAll() will re-register on restart
            Unit
        }
    }

    fun isInstalled(ctx: Context, pluginId: String): Boolean {
        return ExtensionLoader.listEggFiles(ctx).any { it.nameWithoutExtension.endsWith(pluginId.substringAfterLast('.')) }
    }
}
