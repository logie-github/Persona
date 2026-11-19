package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Base64

/**
 * What a game started without the Steam client needs from the account: its SteamID and the game's encrypted app ticket, which the Steam emulator hands to
 * the game (gbe_fork's `ticket`, as GameNative does). A game with Denuvo checks that ticket with its server before it starts; without it, it closes.
 */
data class GameTicket(val accountName: String, val steamId64: Long, val ticketBase64: String?, val result: String)

object GameTickets {
    /** Signs in with the saved sign-in and asks for [appId]'s encrypted app ticket. Throws [SignInRequired] when the saved sign-in is refused. */
    suspend fun fetch(store: LoginStore, appId: Int, timeoutMs: Long = 20_000): GameTicket = withContext(Dispatchers.IO) {
        val login = store.load() ?: throw SignInRequired("Not signed in.")
        openConnection().use { conn ->
            conn.logOn(login)
            val steamId = conn.client.steamID?.convertToUInt64() ?: 0L
            val apps = requireNotNull(conn.client.getHandler<SteamApps>())
            val reply = withTimeout(timeoutMs) { apps.requestEncryptedAppTicket(appId).await() }
            val ticket = reply.encryptedAppTicket?.takeIf { reply.result == EResult.OK }?.toByteArray()
            GameTicket(login.username, steamId, ticket?.let { Base64.getEncoder().encodeToString(it) }, reply.result.name)
        }
    }
}
