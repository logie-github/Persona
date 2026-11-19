package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.CompletableFuture

/** The one-time sign-in. Keeps a refresh token in [store]; the password is used once and dropped. */
class SignIn(private val store: LoginStore) {

    /** True when a sign-in is saved. The app shows its sign-in screen only when this is false (or after [SignInRequired]). */
    fun isSignedIn(): Boolean = store.load() != null

    fun signOut() = store.clear()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun signIn(username: String, password: CharArray, prompt: GuardPrompt) {
        require(username.isNotBlank() && password.isNotEmpty()) { "A name and a password are needed." }
        Connection().use { conn ->
            conn.connect()
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
            val session = conn.client.authentication.beginAuthSessionViaCredentials(details).await()
            val result = session.pollingWaitForResult().await()
            java.util.Arrays.fill(password, '\u0000')
            store.save(StoredLogin(result.accountName, result.refreshToken, result.newGuardData))
        }
    }
}
