package com.egg.codeeditor.util

import org.json.JSONObject
import java.io.File

class CloudIndex(val root: File, indexDir: File? = null) {
    data class Entry(val mtime: Long, val size: Long, val sha1: String)

    private val indexFile: File = run {
        if (indexDir == null) {
            File(root, ".egg/index.json")
        } else {
            val hash = root.absolutePath.hashCode().toUInt().toString(16)
            File(indexDir, "index-$hash.json")
        }
    }
    private val entries = HashMap<String, Entry>()
    private var dirty = false

    fun get(relPath: String): Entry? = entries[relPath]

    fun put(relPath: String, e: Entry) {
        if (entries[relPath] != e) {
            entries[relPath] = e
            dirty = true
        }
    }

    fun load() {
        entries.clear()
        dirty = false
        if (!indexFile.isFile) return
        runCatching {
            val obj = JSONObject(indexFile.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = obj.getJSONObject(k)
                entries[k] = Entry(
                    mtime = o.optLong("m", 0L),
                    size = o.optLong("s", -1L),
                    sha1 = o.optString("h", "")
                )
            }
        }.onFailure { entries.clear() }
    }

    fun save() {
        if (!dirty) return
        runCatching {
            indexFile.parentFile?.mkdirs()
            val obj = JSONObject()
            for ((k, v) in entries) {
                obj.put(k, JSONObject().put("m", v.mtime).put("s", v.size).put("h", v.sha1))
            }
            indexFile.writeText(obj.toString())
            dirty = false
        }
    }
}
