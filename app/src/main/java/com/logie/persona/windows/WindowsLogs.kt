package com.logie.persona.windows

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.logie.persona.BuildConfig
import com.logie.persona.backend.windows.LogDigest
import java.io.File

/**
 * What a Windows game's session left to read: the app's own notes (`windows.log`), the session's programs' output (`windows-session.log`: Wine
 * and the game, Winlator patches 0007 and 0008), any crash of the app (`crash.log`) and how Android says the app's last runs ended
 * (ApplicationExitInfo: a native crash, the system ending it, its memory). SHARE LOG sends a short digest as text, which pastes whole, and
 * the full logs as a file beside it.
 */
object WindowsLogs {
    fun session(context: Context) = File(context.filesDir, "windows-session.log")
    private fun notes(context: Context) = File(context.filesDir, "windows.log")
    private fun crashes(context: Context) = File(context.filesDir, "crash.log")

    /** Starts a new session's log (the last one kept beside it as .1). */
    fun newSession(context: Context): File {
        val f = session(context)
        if (f.isFile) f.renameTo(File(f.path + ".1"))
        return f
    }

    fun exists(context: Context): Boolean = session(context).isFile || crashes(context).isFile || notes(context).isFile

    private fun read(f: File): String = if (f.isFile) runCatching { f.readText() }.getOrDefault("") else ""

    private fun tail(text: String, maxChars: Int): String =
        if (text.isEmpty()) "(none)" else if (text.length <= maxChars) text else "...(${text.length - maxChars} characters before)\n" + text.takeLast(maxChars)

    /** How Android says the app's last runs ended, newest first. */
    fun exits(context: Context, max: Int = 4): String {
        if (Build.VERSION.SDK_INT < 30) return "(Android 11 or newer only)"
        val infos = runCatching { context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(context.packageName, 0, max) }.getOrNull().orEmpty()
        if (infos.isEmpty()) return "(none)"
        return infos.joinToString("\n") { i ->
            val head = "${java.util.Date(i.timestamp)} ${reason(i.reason)} status ${i.status}, ${i.processName}, pss ${i.pss / 1024} MB" +
                (i.description?.let { ", $it" } ?: "")
            val trace = if (i.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || i.reason == ApplicationExitInfo.REASON_ANR) traceText(i) else ""
            if (trace.isEmpty()) head else "$head\n$trace"
        }
    }

    /** A native crash's tombstone (or an ANR's dump) as its readable words: the signal, the abort message, the libraries and functions of the stack. */
    @androidx.annotation.RequiresApi(30)
    private fun traceText(i: ApplicationExitInfo, maxChars: Int = 4_000): String {
        val bytes = runCatching {
            i.traceInputStream?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16_384)
                while (out.size() < 400_000) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                out.toByteArray()
            }
        }.getOrNull() ?: return ""
        val words = StringBuilder(); val run = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xff
            if (c in 0x20..0x7e) run.append(c.toChar()) else { if (run.length >= 6) words.append("  ").append(run).append('\n'); run.setLength(0) }
            if (words.length > maxChars) break
        }
        return words.toString().take(maxChars).trimEnd()
    }

    @androidx.annotation.RequiresApi(30)
    private fun reason(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_CRASH -> "crash (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "too many resources"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited by itself"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by the user"
        ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by the user"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
        else -> "reason $r"
    }

    private fun header(context: Context) =
        "Persona ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "Last start: ${WindowsRuntime.lastStart?.let { java.util.Date(it) } ?: "-"}\n"

    /** The short report: pastes whole into a message. */
    fun report(context: Context): String = buildString {
        append(header(context)).append('\n')
        append("== windows.log ==\n").append(tail(read(notes(context)), 2_500)).append("\n\n")
        append("== how the app ended (Android) ==\n").append(exits(context)).append("\n\n")
        append("== crash.log ==\n").append(tail(read(crashes(context)), 4_000)).append("\n\n")
        append("== windows-session.log (digest) ==\n").append(LogDigest.compact(read(session(context)), 14_000).ifEmpty { "(none)" }).append('\n')
    }

    /** Everything, for the file. */
    private fun full(context: Context): String = buildString {
        append(header(context)).append('\n')
        append("== windows.log ==\n").append(tail(read(notes(context)), 50_000)).append("\n\n")
        append("== how the app ended (Android) ==\n").append(exits(context, 8)).append("\n\n")
        append("== crash.log ==\n").append(tail(read(crashes(context)), 100_000)).append("\n\n")
        append("== windows-session.log ==\n").append(tail(read(session(context)), 4_000_000)).append("\n\n")
        append("== windows-session.log.1 (the start before) ==\n").append(tail(read(File(session(context).path + ".1")), 1_000_000)).append('\n')
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Persona: Windows game log")
            .putExtra(Intent.EXTRA_TEXT, report(context))
        runCatching {
            val dir = File(context.cacheDir, "logs").also { it.mkdirs() }
            val file = File(dir, "persona-windows-log.txt").also { it.writeText(full(context)) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".filesprovider", file)
            send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri("log", uri)
        }
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
