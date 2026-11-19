package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import `in`.dragonbra.javasteam.enums.EOSType
import java.util.concurrent.CompletableFuture

/**
 * The one-time sign-in. Keeps a refresh token in [store]; the password is used once and dropped. [deviceName] is what the account's list of signed-in devices
 * shows.
 *
 * Everything here runs on the IO threads, whoever calls it: the library looks up its servers (a web request, and a name lookup for each server) on the thread that
 * connects, and Android refuses network use on its main thread (NetworkOnMainThreadException). The sign-in screen calls this from the main thread; caught inside
 * the library, that refusal surfaced only as "No CM servers available to connect to".
 */
class SignIn(private val store: LoginStore, private val deviceName: String = "Persona") {

    /** True when a sign-in is saved. The app shows its sign-in screen only when this is false (or after [SignInRequired]). */
    fun isSignedIn(): Boolean = store.load() != null

    fun signOut() = store.clear()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun signIn(username: String, password: CharArray, prompt: GuardPrompt) = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotEmpty()) { "A name and a password are needed." }
        openConnection().use { conn ->
            val details = AuthSessionDetails().apply {
                this.username = username.trim()
                this.password = String(password)          // as typed: some passwords begin or end with a space
                persistentSession = true
                deviceFriendlyName = deviceName
                clientOSType = EOSType.WinUnknown           // as GameNative does: the session is a desktop client's, as the downloads are
                authenticator = object : IAuthenticator {
                    override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
                        GlobalScope.future { prompt.code(GuardKind.APP_CODE, previousCodeWasIncorrect) ?: throw SignInRequired("Cancelled.") }
                    override fun getEmailCode(email: String?, previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
                        GlobalScope.future { prompt.code(GuardKind.EMAIL_CODE, previousCodeWasIncorrect) ?: throw SignInRequired("Cancelled.") }
                    override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> =
                        GlobalScope.future { prompt.approvedOnPhone() }
                }
            }
            val result = try {
                val session = conn.client.authentication.beginAuthSessionViaCredentials(details).await()
                session.pollingWaitForResult().await()
            } finally {
                java.util.Arrays.fill(password, '\u0000'); details.password = null
            }
            store.save(StoredLogin(result.accountName, result.refreshToken, result.newGuardData))
        }
    }

    /**
     * Signing in by approval instead of typing: Steam gives a link (the page shows it as a QR code and as a button that opens the Steam app), the person approves
     * it there, and the sign-in finishes. [onLink] gets the link at once and again whenever Steam renews it (they expire after a minute or so). Nothing typed, no
     * Guard code. Gives up after [timeoutMs].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun signInWithApproval(onLink: (String) -> Unit, timeoutMs: Long = 5 * 60_000L) = withContext(Dispatchers.IO) {
        openConnection().use { conn ->
            val details = AuthSessionDetails().apply { persistentSession = true; deviceFriendlyName = deviceName; clientOSType = EOSType.WinUnknown }
            val session = conn.client.authentication.beginAuthSessionViaQR(details).await()
            session.challengeUrlChanged = IChallengeUrlChanged { it?.challengeUrl?.let(onLink) }
            onLink(session.challengeUrl)
            val result = withTimeout(timeoutMs) { session.pollingWaitForResult().await() }
            store.save(StoredLogin(result.accountName, result.refreshToken, result.newGuardData))
        }
    }
}
