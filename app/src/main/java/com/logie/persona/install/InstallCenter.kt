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
import kotlinx.coroutines.launch
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

    /** Removes a downloaded game's files. */
    fun uninstall(context: Context, version: Version) {
        dir(context, version).deleteRecursively()
        _state.update { it - version.id }
    }

    fun isRunning(id: String): Boolean = _state.value[id] is InstallUi.Running

    /** Runs [block] with the library's own log lines going to files/signin.log (the whole story, for when a sign-in fails), and turns what it throws into words. Null when it worked. */
    private suspend fun logged(context: Context, block: suspend () -> Unit): String? {
        val log = File(context.filesDir, "signin.log")
        fun note(text: String) = runCatching { log.appendText("${java.util.Date()} $text\n") }
        val listener = object : LogListener {
            override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) { note("${clazz.simpleName}: $message") }
            override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) { note("ERROR ${clazz.simpleName}: $message ${throwable?.stackTraceToString().orEmpty()}") }
        }
        LogManager.addListener(listener)
        return try {
            block(); null
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

    /** Where the one-time sign-in is, for the sign-in screen. */
    sealed interface SignInUi {
        data object Idle : SignInUi
        data object Working : SignInUi
        /** Approving: the link to show as a QR code (it is renewed every so often). */
        data class Link(val url: String) : SignInUi
        data class Failed(val why: String) : SignInUi
        data object Done : SignInUi
    }

    private val _signIn = MutableStateFlow<SignInUi>(SignInUi.Idle)
    val signInUi: StateFlow<SignInUi> = _signIn
    // The sign-in runs here, not in the screen's scope: the person leaves the app to approve in the Steam app, and the sign-in must carry on meanwhile
    // (SignInService keeps the app from being frozen; an activity Android recreates on return finds the same sign-in still running).
    private val signInScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var signInJob: kotlinx.coroutines.Job? = null
    private var approving = false

    /** True while a sign-in by approval is running (a screen shown again joins it instead of starting another). */
    fun isApproving(): Boolean = approving && signInJob?.isActive == true

    internal fun launchSignIn(context: Context, block: suspend (Context) -> String?) {
        val app = context.applicationContext
        signInJob?.cancel()
        _code.value = null; _waitingForApproval.value = false
        _signIn.value = SignInUi.Working
        SignInService.start(app)
        lateinit var job: kotlinx.coroutines.Job
        job = signInScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val why = block(app)
                _signIn.value = if (why == null) SignInUi.Done else SignInUi.Failed(why)
            } finally {
                if (signInJob === job) SignInService.stop(app)
            }
        }
        signInJob = job; job.start()
    }

    /** Starts signing in by approval (a QR code, or the Steam app opened on its link); a sign-in already running is replaced. */
    fun startApproval(context: Context) { launchSignIn(context) { app -> signInWithApproval(app) { url -> _signIn.value = SignInUi.Link(url) } }; approving = true }

    /** Starts signing in with a name and password; a sign-in already running is replaced. */
    fun startAccount(context: Context, username: String, password: String, preferCode: Boolean) {
        launchSignIn(context) { app -> signIn(app, username, password.toCharArray(), preferCode) }; approving = false
    }

    /** Stops whatever sign-in is running (the screen was closed, or the other way in was chosen). */
    fun cancelSignIn(context: Context) {
        signInJob?.cancel(); signInJob = null
        _code.value = null; _waitingForApproval.value = false; _signIn.value = SignInUi.Idle
        SignInService.stop(context.applicationContext)
    }

    /** After the screen has seen the outcome. */
    fun resetSignIn() { if (signInJob?.isActive != true) _signIn.value = SignInUi.Idle }

    private val _waitingForApproval = MutableStateFlow(false)
    /** True while an account's sign-in waits for the person to approve it in the Steam app (the screen offers a code instead). */
    val waitingForApproval: StateFlow<Boolean> = _waitingForApproval

    /** Null when signed in; otherwise why not, in words. Errors included: a failing library must not hide. [preferCode] asks for a typed code even where approving in the app is possible. */
    suspend fun signIn(context: Context, username: String, password: CharArray, preferCode: Boolean = false): String? = logged(context) {
      try {
        SignIn(KeystoreLoginStore(context), deviceName()).signIn(username, password, object : GuardPrompt {
            override suspend fun code(kind: GuardKind, previousWasWrong: Boolean): String? {
                val request = CodeRequest(kind, previousWasWrong, CompletableDeferred())
                _code.value = request
                return request.reply.await().also { _code.value = null }
            }
            override suspend fun approvedOnPhone(): Boolean { if (!preferCode) _waitingForApproval.value = true; return !preferCode }
        })
      } finally { _waitingForApproval.value = false }
    }

    /** Signing in by approving a link (shown as a QR code, or opened in the Steam app): [onLink] gets the link, and again when it is renewed. Null when signed in. */
    suspend fun signInWithApproval(context: Context, onLink: (String) -> Unit): String? = logged(context) {
        SignIn(KeystoreLoginStore(context), deviceName()).signInWithApproval(onLink)
    }

    private fun describe(t: Throwable): String {
        val result = (t as? AuthenticationException)?.result
        return when (result) {
            EResult.InvalidPassword, EResult.AccessDenied -> "The name or password was not accepted."
            EResult.RateLimitExceeded, EResult.AccountLoginDeniedThrottle -> "Too many tries. Wait a while and try again."
            EResult.TwoFactorCodeMismatch -> "That code was not right."
            EResult.Expired -> "The request timed out."
            EResult.ServiceUnavailable, EResult.Timeout, EResult.NoConnection -> "The service isn't answering right now. Try again in a minute."
            else -> when (t) {
                is kotlinx.coroutines.TimeoutCancellationException -> "Nothing was approved in time. Try again."
                is java.io.IOException -> t.message ?: "The connection failed."                  // already says which routes failed and why
                else -> "${t.javaClass.simpleName}: ${t.message ?: "no message"}" + (result?.let { " [$it]" } ?: "")
            }
        }
    }

    /** What the account's list of signed-in devices shows for this phone. */
    private fun deviceName(): String = "Persona on ${android.os.Build.MODEL ?: "Android"}"

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
