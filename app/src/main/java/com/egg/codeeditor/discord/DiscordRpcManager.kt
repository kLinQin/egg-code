package com.egg.codeeditor.discord

import kotlinx.coroutines.CoroutineScope

object DiscordRpcManager {
    private var gateway: DiscordGateway? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    fun start(token: String, scope: CoroutineScope) {
        if (gateway != null) return
        lateinit var g: DiscordGateway
        g = DiscordGateway(token, scope) {
            ready = true
            pending?.let { (d, s) -> g.updatePresence(d, s) }
            pending = null
        }
        gateway = g
        g.connect()
    }

    fun updatePresence(details: String, state: String) {
        if (!ready) {
            pending = details to state
            return
        }
        gateway?.updatePresence(details, state)
    }

    fun stop() {
        gateway?.disconnect()
        gateway = null
        ready = false
    }
}
