package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.Closeable

/** A connected [SteamClient] with its callback pump running; [close] stops it. */
internal class Connection : Closeable {
    val client = SteamClient()
    val callbacks = CallbackManager(client)
    val user: SteamUser = requireNotNull(client.getHandler<SteamUser>())

    private val connected = CompletableDeferred<Unit>()
    private val disconnected = CompletableDeferred<Boolean>()
    private val subscriptions = mutableListOf<Closeable>()
    @Volatile private var running = true
    private val pump = Thread({
        while (running) callbacks.runWaitCallbacks(500L)
    }, "persona-callbacks").apply { isDaemon = true }

    init {
        subscriptions += callbacks.subscribe<ConnectedCallback> { connected.complete(Unit) }
        subscriptions += callbacks.subscribe<DisconnectedCallback> { disconnected.complete(it.isUserInitiated) }
        pump.start()
    }

    suspend fun connect(timeoutMs: Long = 30_000) {
        client.connect()
        withTimeout(timeoutMs) { connected.await() }
    }

    /** Logs on with a saved refresh token; returns the licences the account holds. */
    suspend fun logOn(login: StoredLogin, timeoutMs: Long = 30_000): List<License> {
        val loggedOn = CompletableDeferred<LoggedOnCallback>()
        val licences = CompletableDeferred<List<License>>()
        subscriptions += callbacks.subscribe<LoggedOnCallback> { loggedOn.complete(it) }
        subscriptions += callbacks.subscribe<LicenseListCallback> { licences.complete(it.licenseList) }
        user.logOn(LogOnDetails(username = login.username, accessToken = login.refreshToken, shouldRememberPassword = true))
        val result = withTimeout(timeoutMs) { loggedOn.await() }
        when (result.result) {
            EResult.OK -> Unit
            EResult.InvalidPassword, EResult.AccessDenied, EResult.Expired, EResult.Revoked, EResult.LoggedInElsewhere ->
                throw SignInRequired("The saved sign-in is no longer accepted (${result.result}).")
            else -> error("Could not sign in (${result.result}).")
        }
        return withTimeout(timeoutMs) { licences.await() }
    }

    override fun close() {
        running = false
        runCatching { if (client.isConnected) user.logOff() }
        runCatching { client.disconnect() }
        subscriptions.forEach { runCatching { it.close() } }
    }
}
