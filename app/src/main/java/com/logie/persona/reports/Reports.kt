package com.logie.persona.reports

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.logie.persona.BuildConfig
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.reports.Issues
import com.logie.persona.backend.reports.Report
import com.logie.persona.install.InstallCenter
import com.logie.persona.windows.WindowsLogs
import com.logie.persona.windows.WindowsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The app's failures sent to its GitHub repository as issues, by themselves, as the Blizzard launcher's are: the app crashing (Java or native,
 * or Android ending it), a Windows game's session ending by itself soon after PLAY, PLAY failing. Each is written to `files/reports` first and
 * sent at once, then again every minute while any wait (offline, GitHub busy), kept until GitHub takes it. The same failure of the same build
 * goes once a day. The token is CI's PERSONA_REPORT_TOKEN secret, built in; a build without one keeps its reports on the phone. A report names
 * neither the account nor the store ([Issues.redact]).
 */
object Reports {
    const val REPO = "logie-github/Persona"
    /** A session that ends by itself later than this with status 0 is a game quit from its own menu, not a failure. */
    private const val SHORT_SESSION_MS = 5 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var looping = false

    fun dir(context: Context) = File(context.applicationContext.filesDir, "reports")
    fun configured(): Boolean = BuildConfig.REPORT_TOKEN.isNotEmpty()

    /** Once per process, as it starts. */
    fun start(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val f = File(app.filesDir, "crash.log")
                if (f.length() > 512 * 1024) f.delete()
                f.appendText("${java.util.Date()} ${android.os.Process.myPid()} ${thread.name}: ${e.stackTraceToString()}\n")
            }
            // Written now (the process is ending); sent at the next start.
            runCatching {
                queue(app, "App crash: ${e.javaClass.simpleName}: ${e.message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120)}",
                    "The app crashed on its thread \"${thread.name}\" (process ${processName()}).\n\n````\n${e.stackTraceToString().take(20_000)}\n````",
                    withSession = recentSession(app))
            }
            previous?.uncaughtException(thread, e)
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is com.winlator.XServerDisplayActivity && activity.isFinishing) scope.launch { runCatching { sessionEnded(app) } }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        // The launcher's process only (not the PSP emulator's): one sender, so no report is sent twice.
        if (!processName().contains(':')) scope.launch {
            runCatching { checkExits(app) }
            sendSoon(app)
        }
    }

    private fun processName(): String = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else "com.logie.persona"

    private fun header(): String = "Persona ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    /** A game's session wrote its log in the last [withinMs]: it belongs in a report of the app's end. */
    private fun recentSession(context: Context, withinMs: Long = 15 * 60 * 1000L, at: Long = System.currentTimeMillis()): Boolean {
        val f = WindowsLogs.session(context)
        return f.isFile && at - f.lastModified() in -60_000..withinMs
    }

    /**
     * Writes a report: [message] in the body under the phone's line, with the Windows logs' short report when [withSession], and the
     * session's full log as comments after it. Returns whether it was queued (false: the same went within the day).
     */
    fun queue(context: Context, title: String, message: String, withSession: Boolean = false): Boolean {
        val app = context.applicationContext
        val account = runCatching { InstallCenter.accountName(app) }.getOrNull()
        val body = buildString {
            append("**").append(header()).append("**\n\n").append(message).append('\n')
            if (withSession) append("\n### The logs (SHARE LOG's report)\n\n````\n").append(WindowsLogs.report(app).replace("````", "'```")).append("\n````\n")
        }
        val comments = if (withSession) {
            val log = runCatching { WindowsLogs.session(app).readText() }.getOrDefault("")
            Issues.chunks("The session's whole log (its end)", Issues.redact(log, account), maxParts = 4)
        } else emptyList()
        return Issues.queue(dir(app), Report(Issues.redact(title, account), Issues.redact(body, account), comments, BuildConfig.VERSION_CODE))
    }

    /** Sends what waits, now and then every minute while any is left (at most an hour); one send at a time, one retry loop. */
    fun sendSoon(context: Context, onFirst: ((com.logie.persona.backend.reports.SendResult) -> Unit)? = null) {
        val app = context.applicationContext
        scope.launch {
            val first = sendOnce(app)
            onFirst?.invoke(first)
            if (first.waiting == 0 || !configured() || looping) return@launch
            looping = true
            try {
                repeat(60) {
                    delay(60_000)
                    if (sendOnce(app).waiting == 0) return@launch
                }
            } finally { looping = false }
        }
    }

    private suspend fun sendOnce(app: Context): com.logie.persona.backend.reports.SendResult {
        val r = lock.withLock { Issues.send(dir(app), REPO, BuildConfig.REPORT_TOKEN) }
        if (r.sent.isNotEmpty() || r.problem != null) note(app, "reports: sent ${r.sent.joinToString { "#$it" }.ifEmpty { "none" }}, ${r.waiting} waiting${r.problem?.let { " ($it)" }.orEmpty()}")
        return r
    }

    private fun note(context: Context, text: String) { runCatching { File(context.filesDir, "windows.log").appendText("${java.util.Date()} $text\n") } }

    /**
     * Winlator's game screen closed. When its program ended by itself (patch 0008's note) and not by the player's Exit (patch 0009's), soon
     * after PLAY or with a failing status, the session failed: reported, and the game's screen says so.
     */
    private fun sessionEnded(app: Application) {
        val log = runCatching { WindowsLogs.session(app).readText() }.getOrDefault("")
        val ended = Regex("the session's program ended with status (-?\\d+)").find(log) ?: return
        if (log.contains("the player chose Exit")) return
        val status = ended.groupValues[1].toIntOrNull() ?: 0
        val started = WindowsRuntime.lastStart
        val seconds = started?.let { (System.currentTimeMillis() - it) / 1000 }
        if (status == 0 && seconds != null && seconds * 1000 > SHORT_SESSION_MS) return
        val versionId = WindowsRuntime.lastGame
        val name = Catalog.findVersion(versionId)?.let { v -> "${Catalog.gameOf(v).title} (${v.label})" } ?: (versionId ?: "A Windows game")
        val title = "$name closed by itself${seconds?.let { " ${it}s after PLAY" }.orEmpty()} (status $status)"
        val queued = queue(app, title, "The game's session ended without the player's Exit${seconds?.let { ", ${it}s after PLAY" }.orEmpty()}; Wine's program ended with status $status.", withSession = true)
        // The pop-up on the launcher (as the Blizzard launcher shows a failed start): what happened, then what became of the report.
        val shown = Catalog.findVersion(versionId)?.let { Catalog.gameOf(it).title } ?: "The game"
        val what = "$shown closed while starting${seconds?.let { if (it < 120) " (${it} seconds after PLAY)" else "" }.orEmpty()}."
        fun say(text: String) { if (versionId != null) WindowsRuntime.closedByItself(versionId, "$what $text") }
        say(when {
            !configured() -> "SHARE LOG has its log."
            queued -> "Sending a report with its log…"
            else -> "It was already reported today for this version of the app."
        })
        sendSoon(app) { r ->
            if (!queued || !configured()) return@sendSoon
            say(when {
                r.sent.isNotEmpty() -> "A report with its log was sent (issue #${r.sent.last()})."
                else -> "The report couldn't be sent yet${r.problem?.let { " ($it)" }.orEmpty()}. It stays on the phone and goes again every minute."
            })
        }
    }

    /** PLAY failed before the game's screen (the runtime, the games' Windows, the game's program). */
    fun playFailed(context: Context, versionId: String, e: Throwable) {
        val name = Catalog.findVersion(versionId)?.let { v -> "${Catalog.gameOf(v).title} (${v.label})" } ?: versionId
        val queued = queue(context, "PLAY failed for $name: ${e.message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120)}",
            "PLAY failed before the game's screen.\n\n````\n${e.stackTraceToString().take(20_000)}\n````", withSession = false)
        val what = e.message ?: e.javaClass.simpleName
        sendSoon(context) { r ->
            if (!queued || !configured()) return@sendSoon
            if (r.sent.isNotEmpty()) WindowsRuntime.closedByItself(versionId, "$what A report was sent (issue #${r.sent.last()}).")
        }
    }

    /** How Android says the app's processes ended since the last look: a native crash, an ANR, the system ending it, each reported. */
    private fun checkExits(app: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = app.getSharedPreferences("reports", Context.MODE_PRIVATE)
        val seen = prefs.getLong("exits_seen", 0L)
        val floor = if (seen > 0L) seen else System.currentTimeMillis() - 24L * 3600 * 1000
        val infos = app.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(app.packageName, 0, 10).orEmpty().filter { it.timestamp > floor }
        if (infos.isEmpty()) return
        prefs.edit().putLong("exits_seen", infos.maxOf { it.timestamp }).apply()
        val bad = setOf(
            ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        )
        infos.filter { it.reason in bad }.take(3).forEach { i ->
            val what = WindowsLogs.exitLine(i)
            queue(app, "App ended: ${WindowsLogs.reasonName(i.reason)} in ${i.processName}", "Android's record of how the app ended:\n\n````\n${WindowsLogs.exitText(i)}\n````\n\n$what",
                withSession = recentSession(app, at = i.timestamp))
        }
    }
}
