package com.logie.persona.game

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.reports.BugReports
import com.logie.persona.windows.WindowsRuntime

/**
 * A Windows game's session on Winlator's game screen, as the Blizzard launcher's GestureView keeps it (without its touch gestures:
 * the Persona games play with the on-screen gamepad): Winlator's XServerDisplayActivity makes it when the launcher names the game
 * (extra `game_id`, patches/winlator/0010) and tells it the game's first window, the session's program ending and the player's Exit.
 *
 * - The programs' lines go into the run's log ([GameLog], [com.logie.persona.reports.SessionLog]); the screen's comings and goings
 *   are noted.
 * - No game window in 90 seconds: a report, with the run's log, the session keeper's log and Android's.
 * - A minute into the game: its log, once a day (a black screen reports nothing by itself).
 * - The game ending by itself (the session's program ended without the player's Exit) within five minutes, or with a failing
 *   status: a report, and the launcher's pop-up says so with the report's issue.
 */
class GameSession(private val activity: AppCompatActivity, private val gameId: String) {
    private val handler = Handler(Looper.getMainLooper())
    private val started = SystemClock.elapsedRealtime()
    private var windowShown = false
    private var programStatus: Int? = null
    private var playerExit = false
    private var finished = false

    private val noWindowReport = Runnable { if (!windowShown && !finished) sendReport("didn't start (no window in 90 s)", "Sent by the app") }

    private val minuteReport = Runnable { if (!finished) sendReport("log after 60 s", "Sent by the app") }

    private val title: String = Catalog.findVersion(gameId)?.let { v -> "${Catalog.gameOf(v).title} (${v.label})" } ?: gameId

    init {
        alive = true
        GameLog.start()
        GameLog.note("game screen: $title")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { GameLog.note("game screen: shown (onStart)") }
            override fun onResume(owner: LifecycleOwner) { GameLog.note("game screen: in front (onResume)") }
            override fun onPause(owner: LifecycleOwner) { GameLog.note("game screen: out of front (onPause)") }
            override fun onStop(owner: LifecycleOwner) { GameLog.note("game screen: hidden (onStop: the app went to the background)") }
            override fun onDestroy(owner: LifecycleOwner) { finish() }
        })
        handler.postDelayed(noWindowReport, 90_000)
    }

    private fun seconds() = (SystemClock.elapsedRealtime() - started) / 1000

    /** The game's first window is up (its class, as the X server has it). */
    fun onGameWindowShown(windowClass: String) {
        windowShown = true
        GameLog.note("the game's first window: class '$windowClass', ${seconds()} s after the screen opened")
        handler.removeCallbacks(noWindowReport)
        handler.postDelayed(minuteReport, 60_000)
    }

    /** The session's program (Wine's desktop, and with it everything in it) ended, with [status]. */
    fun onProgramEnded(status: Int) {
        programStatus = status
        GameLog.note("the session's program ended with status $status, ${seconds()} s after the screen opened${if (windowShown) "" else ", no game window yet"}")
    }

    fun onPlayerExit() {
        playerExit = true
        GameLog.note("the player chose Exit")
    }

    private fun details(): List<Pair<String, String?>> = listOf(
        "Game" to gameId, "Device" to android.os.Build.MODEL, "Window shown" to windowShown.toString(),
        "Seconds" to seconds().toString(), "Session's program" to (programStatus?.let { "ended with status $it" } ?: "running"),
    )

    private fun sendReport(what: String, message: String, onResult: ((BugReports.SendResult) -> Unit)? = null) {
        BugReports.report(
            activity.applicationContext, "$title $what", message, details(),
            log = GameLog.snapshot().ifEmpty { null }, attach = true, onResult = onResult,
        )
    }

    /** The screen is gone: a game that ended by itself is reported, and the launcher's pop-up says what happened. */
    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(noWindowReport)
        handler.removeCallbacks(minuteReport)
        val status = programStatus
        val secs = seconds()
        if (!playerExit && status != null && (secs * 1000 < SHORT_SESSION_MS || status != 0)) {
            val what = if (windowShown) "closed by itself ${secs}s after PLAY" else "closed while starting (${secs}s after PLAY, no window)"
            val game = Catalog.findVersion(gameId)?.let { Catalog.gameOf(it).title } ?: "The game"
            val said = "$game ${if (windowShown) "closed by itself" else "closed while starting"} ($secs seconds after PLAY)."
            WindowsRuntime.closedByItself(gameId, if (BugReports.configured(activity)) "$said Sending a report with its log…" else "$said This build can't send reports; the log is kept on the phone.")
            sendReport(what, "The game's session ended without the player's Exit; Wine's program ended with status $status.") { r ->
                WindowsRuntime.closedByItself(gameId, "$said ${r.say()}.")
            }
        }
        GameLog.finish()
        alive = false
    }

    companion object {
        /** A game's screen is up (the sampler looks more often while one is). */
        @Volatile var alive = false
            private set

        /** A session that ends by itself later than this with status 0 is a game quit from its own menu, not a failure. */
        private const val SHORT_SESSION_MS = 5 * 60 * 1000L
    }
}
