package com.egg.extension.api

import java.io.File

object GrammarRegistry {
    private val grammars = LinkedHashMap<String, GrammarSpec>()
    private val extToScope = mutableMapOf<String, String>()

    fun register(spec: GrammarSpec) {
        if (spec.scopeName.isBlank() || !spec.grammarFile.exists()) return
        grammars[spec.scopeName] = spec
        spec.extensions.forEach { ext ->
            extToScope[ext.lowercase().trimStart('.')] = spec.scopeName
        }
    }

    fun unregister(extensionId: String) {
        val removed = grammars.filterValues { it.extensionId == extensionId }.keys
        removed.forEach { scope ->
            grammars.remove(scope)
            extToScope.entries.removeAll { it.value == scope }
        }
    }

    fun all(): List<GrammarSpec> = grammars.values.toList()
    fun get(scopeName: String): GrammarSpec? = grammars[scopeName]
    fun scopeForExtension(ext: String): String? = extToScope[ext.lowercase().trimStart('.')]
    fun clear() { grammars.clear(); extToScope.clear() }
}
