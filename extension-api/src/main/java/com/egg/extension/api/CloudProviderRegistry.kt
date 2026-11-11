package com.egg.extension.api

object CloudProviderRegistry {
    private val providers = LinkedHashMap<String, CloudProvider>()

    fun register(provider: CloudProvider) {
        if (provider.id.isBlank()) return
        providers[provider.id] = provider
    }

    fun unregister(id: String) = providers.remove(id)

    fun unregisterByExtension(extensionId: String) {
        providers.entries.removeAll { it.value.id.startsWith(extensionId + ":") }
    }

    fun all(): List<CloudProvider> = providers.values.toList()
    fun get(id: String): CloudProvider? = providers[id]
    fun clear() = providers.clear()
}
