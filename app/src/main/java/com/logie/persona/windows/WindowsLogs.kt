package com.logie.persona.windows

import android.content.Context
import android.content.Intent
import android.os.Build
import com.logie.persona.BuildConfig
import java.io.File

/**
 * What a Windows game's session left to read: the app's own notes (`windows.log`), the session's programs' output (`windows-session.log`: Wine, Box64,
 * the game; Winlator patch 0007) and any crash of the app (`crash.log`). SHARE LOG sends them as text through Android's share sheet.
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

    private fun tail(f: File, maxChars: Int): String {
        if (!f.isFile) return "(none)"
        val text = runCatching { f.readText() }.getOrDefault("")
        return if (text.length <= maxChars) text else "...(${text.length - maxChars} characters before)\n" + text.takeLast(maxChars)
    }

    fun report(context: Context): String = buildString {
        append("Persona ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Last start: ${WindowsRuntime.lastStart?.let { java.util.Date(it) } ?: "-"}\n\n")
        append("== windows.log ==\n").append(tail(notes(context), 6_000)).append("\n\n")
        append("== crash.log ==\n").append(tail(crashes(context), 12_000)).append("\n\n")
        append("== windows-session.log ==\n").append(tail(session(context), 60_000)).append('\n')
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Persona: Windows game log").putExtra(Intent.EXTRA_TEXT, report(context))
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
