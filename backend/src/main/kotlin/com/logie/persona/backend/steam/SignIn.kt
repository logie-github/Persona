package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CompletableFuture

/** The one-time sign-in. Keeps a refresh token in [store]; the password is used once and dropped. */
class SignIn(private val store: LoginStore) {

    /** True when a sign-in is saved. The app shows its sign-in screen only when this is false (or after [SignInRequired]). */
    fun isSignedIn(): Boolean = store.load() != null

    fun signOut() = store.clear()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun signIn(username: String, password: CharArray, prompt: GuardPrompt) {
        require(username.isNotBlank() && password.isNotEmpty()) { "A name and a password are needed." }
        openConnection().use { conn ->
            val details = AuthSessionDetails().apply {
                this.username = username.trim()
                this.password = String(password)
                persistentSession = true
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
    suspend fun signInWithApproval(onLink: (String) -> Unit, timeoutMs: Long = 5 * 60_000L) {
        openConnection().use { conn ->
            val details = AuthSessionDetails().apply { persistentSession = true }
            val session = conn.client.authentication.beginAuthSessionViaQR(details).await()
            session.challengeUrlChanged = IChallengeUrlChanged { it?.challengeUrl?.let(onLink) }
            onLink(session.challengeUrl)
            val result = withTimeout(timeoutMs) { session.pollingWaitForResult().await() }
            store.save(StoredLogin(result.accountName, result.refreshToken, result.newGuardData))
        }
    }
}
