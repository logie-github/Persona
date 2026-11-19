package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.Closeable

/** A connected [SteamClient] with its callback pump running; [close] stops it. */
internal class Connection(val protocol: ProtocolTypes = ProtocolTypes.TCP) : Closeable {
    /** The library's own complaints while this connection was being made: what the screen shows when it can't be (no logs are needed from the phone). */
    val problems = java.util.concurrent.ConcurrentLinkedDeque<String>()
    private val listener = object : `in`.dragonbra.javasteam.util.log.LogListener {
        override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
            if (message != null && Regex("(?i)exception|timed out|failed|refused|unreachable|handshake|certificate|ssl|tls").containsMatchIn(message)) note("${clazz.simpleName}: $message")
        }
        override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) { note("${clazz.simpleName}: $message ${throwable?.javaClass?.simpleName.orEmpty()} ${throwable?.message.orEmpty()}".trim()) }
        private fun note(t: String) { problems.addLast(t.take(160)); while (problems.size > 4) problems.pollFirst() }
    }.also { `in`.dragonbra.javasteam.util.log.LogManager.addListener(it) }
    val client = SteamClient(SteamConfiguration.create { it.withProtocolTypes(protocol) })
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

    /**
     * Connects, trying again with another server when one is down (a failed server is marked bad by the library). One try per call to this: [open] goes
     * through the routes in turn.
     */
    suspend fun connect(timeoutMs: Long = 20_000, tries: Int = 3) {
        var last: Throwable? = null
        repeat(tries) { attempt ->
            try {
                val gone = CompletableDeferred<Boolean>()
                val sub = callbacks.subscribe<DisconnectedCallback> { gone.complete(it.isUserInitiated) }
                try {
                    client.connect()
                    withTimeout(timeoutMs) { kotlinx.coroutines.selects.select<Unit> { connected.onAwait { }; gone.onAwait { throw java.io.IOException("Could not reach the service (attempt ${attempt + 1})") } } }
                    return
                } finally { runCatching { sub.close() } }
            } catch (e: Exception) { last = e; runCatching { client.disconnect() } }
        }
        throw java.io.IOException("Could not connect after $tries tries: ${last?.message}", last)
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
        runCatching { `in`.dragonbra.javasteam.util.log.LogManager.removeListener(listener) }
        running = false
        runCatching { if (client.isConnected) user.logOff() }
        runCatching { client.disconnect() }
        subscriptions.forEach { runCatching { it.close() } }
    }
}

private val ROUTES = listOf(ProtocolTypes.TCP to "direct", ProtocolTypes.WEB_SOCKET to "web")

/**
 * A connection to the service, made over whichever route gets through: the direct one first (as most networks allow it), then the web one
 * (port 443). When none does, the message says how each failed and what the library complained of.
 */
internal suspend fun openConnection(timeoutMs: Long = 7_000): Connection {
    val failures = ArrayList<String>()
    for ((protocol, label) in ROUTES) {
        val c = Connection(protocol)
        try { c.connect(timeoutMs, tries = 4); return c }
        catch (e: kotlinx.coroutines.CancellationException) { c.close(); throw e }
        catch (e: Exception) { failures += "$label: ${e.message ?: e.javaClass.simpleName}" + c.problems.lastOrNull()?.let { " ($it)" }.orEmpty(); c.close() }
    }
    throw java.io.IOException("Could not reach the service. " + failures.joinToString("; "))
}
