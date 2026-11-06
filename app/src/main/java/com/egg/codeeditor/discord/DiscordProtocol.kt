package com.egg.codeeditor.discord

internal object DiscordProtocol {
    const val APP_ID = 1568157927125164134L
    const val APP_LOGO_ASSET_ID = "1568161527763046520" // egg_logo (sudah diupload)
    const val GATEWAY_URL = "wss://gateway.discord.gg/?v=9&encoding=json"

    const val OP_DISPATCH = 0
    const val OP_HEARTBEAT = 1
    const val OP_IDENTIFY = 2
    const val OP_PRESENCE_UPDATE = 3
    const val OP_RECONNECT = 7
    const val OP_INVALID_SESSION = 9
    const val OP_HELLO = 10
    const val OP_HEARTBEAT_ACK = 11

    // Intents minimal untuk presence
    const val INTENTS = (1 shl 0) or (1 shl 12) // GUILDS + RICH_PRESENCE (opsional, presence sendiri tidak butuh banyak)
}
