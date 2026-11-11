package com.egg.extension.api

object ThemeRegistry {
    private val themes = LinkedHashMap<String, ThemeSpec>()

    fun register(spec: ThemeSpec) {
        if (spec.id.isBlank()) return
        themes[spec.id] = spec
    }

    fun unregister(extensionId: String) {
        themes.entries.removeAll { it.value.extensionId == extensionId }
    }

    fun all(): List<ThemeSpec> = themes.values.toList()

    fun get(id: String): ThemeSpec? = themes[id]

    fun clear() = themes.clear()
}
