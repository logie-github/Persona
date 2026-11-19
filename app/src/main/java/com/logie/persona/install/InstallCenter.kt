package com.logie.persona.install

import android.content.Context
import com.logie.persona.backend.catalog.Version
import com.logie.persona.backend.steam.GuardKind
import com.logie.persona.backend.steam.GuardPrompt
import com.logie.persona.backend.steam.InstallProgress
import com.logie.persona.backend.steam.Installer
import com.logie.persona.backend.steam.NotOwned
import com.logie.persona.backend.steam.SignIn
import com.logie.persona.backend.steam.SignInRequired
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.File

/** What a version's install looks like on screen. */
sealed interface InstallUi {
    data object Idle : InstallUi
    data class Running(val progress: InstallProgress) : InstallUi
    data object Done : InstallUi
    data object NotInLibrary : InstallUi
    data object NeedsSignIn : InstallUi
    data class Failed(val message: String) : InstallUi
}

/** A code the one-time sign-in asks for. */
data class CodeRequest(val kind: GuardKind, val previousWasWrong: Boolean, val reply: CompletableDeferred<String?>)

/** The install state of every version, shared by the screen and the service. */
object InstallCenter {
    private val _state = MutableStateFlow<Map<String, InstallUi>>(emptyMap())
    val state: StateFlow<Map<String, InstallUi>> = _state

    private val _code = MutableStateFlow<CodeRequest?>(null)
    /** Set while the one-time sign-in waits for a code; the sign-in screen shows its box for it. */
    val code: StateFlow<CodeRequest?> = _code

    fun dir(context: Context, version: Version): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "games/${version.id}")
    private fun marker(context: Context, version: Version) = File(dir(context, version), ".persona-installed")

    fun isInstalled(context: Context, version: Version): Boolean = marker(context, version).isFile

    fun refresh(context: Context, versions: List<Version>) {
        _state.update { cur -> cur + versions.filter { isInstalled(context, it) }.associate { it.id to InstallUi.Done } }
    }

    fun set(id: String, ui: InstallUi) = _state.update { it + (id to ui) }

    fun isRunning(id: String): Boolean = _state.value[id] is InstallUi.Running

    /** Null when signed in; otherwise why not, in words (and the whole story is in files/signin.log). Errors included: a failing library must not hide. */
    suspend fun signIn(context: Context, username: String, password: CharArray): String? {
        val log = File(context.filesDir, "signin.log")
        fun note(text: String) = runCatching { log.appendText("${java.util.Date()} $text\n") }
        val listener = object : LogListener {
            override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) { note("${clazz.simpleName}: $message") }
            override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) { note("ERROR ${clazz.simpleName}: $message ${throwable?.stackTraceToString().orEmpty()}") }
        }
        LogManager.addListener(listener)
        return try {
            SignIn(KeystoreLoginStore(context)).signIn(username, password, object : GuardPrompt {
                override suspend fun code(kind: GuardKind, previousWasWrong: Boolean): String? {
                    val request = CodeRequest(kind, previousWasWrong, CompletableDeferred())
                    _code.value = request
                    return request.reply.await().also { _code.value = null }
                }
                // A typed code is used when the account allows one; waiting for an approval in the phone's app is not offered here.
                override suspend fun approvedOnPhone(): Boolean = false
            })
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            _code.value = null; throw e
        } catch (t: Throwable) {
            _code.value = null
            note("sign-in failed: ${t.stackTraceToString()}")
            describe(t)
        } finally {
            LogManager.removeListener(listener)
        }
    }

    private fun describe(t: Throwable): String {
        val result = (t as? AuthenticationException)?.result
        return when (result) {
            EResult.InvalidPassword, EResult.AccessDenied -> "The name or password was not accepted."
            EResult.RateLimitExceeded, EResult.AccountLoginDeniedThrottle -> "Too many tries. Wait a while and try again."
            EResult.TwoFactorCodeMismatch -> "That code was not right."
            EResult.Expired -> "The request timed out."
            else -> "${t.javaClass.simpleName}: ${t.message ?: "no message"}" + (result?.let { " [$it]" } ?: "")
        }
    }

    fun isSignedIn(context: Context): Boolean = SignIn(KeystoreLoginStore(context)).isSignedIn()

    /** The install itself: runs inside the service. */
    suspend fun install(context: Context, version: Version, onChange: (InstallProgress) -> Unit) {
        set(version.id, InstallUi.Running(InstallProgress(InstallProgress.Step.CONNECTING, 0f)))
        try {
            val dir = dir(context, version)
            Installer(KeystoreLoginStore(context)).install(version, dir) { p ->
                set(version.id, InstallUi.Running(p))
                onChange(p)
            }
            marker(context, version).writeText("1")
            set(version.id, InstallUi.Done)
        } catch (e: SignInRequired) {
            set(version.id, InstallUi.NeedsSignIn)
        } catch (e: NotOwned) {
            set(version.id, InstallUi.NotInLibrary)
        } catch (e: Exception) {
            set(version.id, InstallUi.Failed(e.message ?: "unknown"))
        }
    }
}
