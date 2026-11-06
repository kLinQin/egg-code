package com.egg.codeeditor.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class DiscordGateway(
    private val token: String,
    private val scope: CoroutineScope,
    private val onReady: () -> Unit
) : WebSocketListener() {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var sequence: Int? = null
    private var sessionId: String? = null
    private val appStartedAt = System.currentTimeMillis()

    fun connect() {
        val req = Request.Builder().url(DiscordProtocol.GATEWAY_URL).build()
        socket = client.newWebSocket(req, this)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        socket?.close(1000, "bye")
        socket = null
    }

    fun updatePresence(details: String, state: String) {
        val presence = buildJsonObject {
            put("op", DiscordProtocol.OP_PRESENCE_UPDATE)
            putJsonObject("d") {
                put("since", 0)
                putJsonArray("activities") {
                    addJsonObject {
                        put("application_id", DiscordProtocol.APP_ID.toString())
                        put("name", "Egg")
                        put("type", 0)
                        put("details", details)
                        put("state", state)
                        putJsonObject("timestamps") { put("start", (System.currentTimeMillis() - 3_600_000L) / 1000) }
                        putJsonObject("assets") {
                            put("large_image", DiscordProtocol.APP_LOGO_ASSET_ID)
                            put("large_text", "Egg Code Editor")
                        }
                    }
                }
                put("status", "online")
                put("afk", false)
            }
        }
        val ok = socket?.send(presence.toString())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val op = obj["op"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
        sequence = obj["s"]?.jsonPrimitive?.content?.toIntOrNull() ?: sequence

        when (op) {
            DiscordProtocol.OP_HELLO -> {
                val interval = obj["d"]?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.content?.toLongOrNull() ?: 41250L
                startHeartbeat(interval)
                identify()
            }
            DiscordProtocol.OP_HEARTBEAT_ACK -> {}
            DiscordProtocol.OP_DISPATCH -> {
                val t = obj["t"]?.jsonPrimitive?.content
                if (t == "READY") {
                    sessionId = obj["d"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content
                    scope.launch(Dispatchers.Main) { onReady() }
                }
            }
            DiscordProtocol.OP_RECONNECT, DiscordProtocol.OP_INVALID_SESSION -> {
                disconnect()
                scope.launch { delay(2000); connect() }
            }
        }
    }

    private fun identify() {
        val payload = buildJsonObject {
            put("op", DiscordProtocol.OP_IDENTIFY)
            putJsonObject("d") {
                put("token", token)
                putJsonObject("properties") {
                    put("os", "Android")
                    put("browser", "Egg")
                    put("device", "Egg")
                }
                put("compress", false)
            }
        }
        val ok = socket?.send(payload.toString())
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(intervalMs)
                val payload = buildJsonObject {
                    put("op", DiscordProtocol.OP_HEARTBEAT)
                    put("d", sequence)
                }
                socket?.send(payload.toString())
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        t.printStackTrace()
        scope.launch { delay(5000); connect() }
    }
}
