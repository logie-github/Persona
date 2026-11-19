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

    suspend fun signIn(context: Context, username: String, password: CharArray): Boolean = try {
        SignIn(KeystoreLoginStore(context)).signIn(username, password, object : GuardPrompt {
            override suspend fun code(kind: GuardKind, previousWasWrong: Boolean): String? {
                val request = CodeRequest(kind, previousWasWrong, CompletableDeferred())
                _code.value = request
                return request.reply.await().also { _code.value = null }
            }
            override suspend fun approvedOnPhone(): Boolean = true
        })
        true
    } catch (e: Exception) {
        _code.value = null
        false
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
