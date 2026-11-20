package com.logie.persona.game

import android.content.Context
import com.winlator.core.WineRegistryEditor
import java.io.File

/**
 * Who ends a game that closes by itself (Persona 4 Golden, November 2026: the whole session ended 0.7 s after the game loaded, with no fault
 * and nothing in Wine's own words): after a run that closed by itself, the game's next run has Wine's relay channel on for the calls that end a
 * program or start another, and nothing else ([CALLS]: ExitProcess, TerminateProcess, exit, a message box, ExitWindowsEx, CreateProcess,
 * ShellExecute). Each such call goes into the run's log with its return address, which names the library that made it ([GameLog]: "in
 * P4G.exe", "in steam_api64.dll"...).
 *
 * The relay channel also traces every window message (Wine's own doing, whatever the list), which slows a game that runs: the trace is on only
 * while a game keeps closing by itself, and off again once a run lasts a minute or the player chooses Exit.
 */
object ExitTrace {
    /** The calls traced: `module.function`, as Wine's RelayInclude names them. Verified on the desktop rig: `Call KERNEL32.ExitProcess(00000003) ret=140001050`. */
    val CALLS = listOf(
        "kernel32.ExitProcess", "ntdll.RtlExitUserProcess", "ntdll.NtTerminateProcess", "kernelbase.TerminateProcess",
        "ucrtbase.exit", "ucrtbase._exit", "ucrtbase.abort", "ucrtbase.quick_exit", "msvcrt.exit", "msvcrt._exit",
        "user32.MessageBoxA", "user32.MessageBoxW", "user32.MessageBoxExW", "user32.MessageBoxTimeoutW", "user32.MessageBoxIndirectW",
        "user32.ExitWindowsEx", "user32.PostQuitMessage",
        "kernelbase.CreateProcessW", "kernelbase.CreateProcessA", "kernel32.WinExec",
        "shell32.ShellExecuteW", "shell32.ShellExecuteA", "shell32.ShellExecuteExW",
    )
    private const val KEY = "Software\\Wine\\Debug"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("exit-trace", Context.MODE_PRIVATE)

    /** The games whose runs closed by themselves in the reports when this was made (issues #2 to #7): traced from their first run. */
    val KNOWN = setOf("persona4_golden")

    /** Whether the game [versionId]'s next run is traced (its last run closed by itself). */
    fun armed(context: Context, versionId: String): Boolean = prefs(context).getBoolean(versionId, versionId in KNOWN)

    fun arm(context: Context, versionId: String) { prefs(context).edit().putBoolean(versionId, true).apply() }

    fun disarm(context: Context, versionId: String) { if (armed(context, versionId)) prefs(context).edit().putBoolean(versionId, false).apply() }

    /** The list Wine reads when its relay channel is on (`HKCU\Software\Wine\Debug\RelayInclude`); written at every traced start. */
    fun apply(containerRoot: File) {
        val reg = File(containerRoot, ".wine/user.reg")
        if (!reg.isFile) return
        WineRegistryEditor(reg).use { it.setStringValue(KEY, "RelayInclude", CALLS.joinToString(";")) }
    }

    /** The relay lines kept: a traced call (not a window message, a library's start or end, a thread's start: Wine traces those whatever the list says). */
    fun wanted(line: String): Boolean {
        if (!line.contains(":Call ") && !line.contains(":Ret ")) return false
        return !(line.contains("window proc") || line.contains("PE DLL") || line.contains("TLS callback") || line.contains("LDR notification") ||
            line.contains("driver init") || line.contains(":Ret "))
    }

    /** Whether [line] is the relay channel's (to be kept by [wanted] or dropped). */
    fun isRelay(line: String): Boolean =
        line.contains(":Call ") || line.contains(":Ret ") || line.contains(":trace:relay:") || line.contains(":Starting thread proc ") || line.contains(":Starting process ")

    // Which library a return address is in: the libraries' load addresses from the loaddll channel, the latest of each base.

    private val bases = java.util.TreeMap<Long, String>()

    /** A loaddll line ("Loaded L"D:\\game\\P4G.exe" at 0000000140000000: native"): its library's base, remembered. */
    fun loaded(line: String) {
        val m = LOADED.find(line) ?: return
        val base = m.groupValues[2].toLongOrNull(16) ?: return
        synchronized(bases) { bases[base] = m.groupValues[1].substringAfterLast('\\') }
    }

    /** [line] with the library its `ret=` address is in: "... ret=140001050 (in P4G.exe)". */
    fun named(line: String): String {
        val ret = RET.find(line)?.groupValues?.get(1)?.toLongOrNull(16) ?: return line
        val name = synchronized(bases) { bases.floorEntry(ret)?.value } ?: return line
        return "$line (in $name)"
    }

    fun reset() { synchronized(bases) { bases.clear() } }

    private val LOADED = Regex("""Loaded L"([^"]+)" at ([0-9A-Fa-f]+)""")
    private val RET = Regex("""ret=([0-9A-Fa-f]+)\s*$""")
}
