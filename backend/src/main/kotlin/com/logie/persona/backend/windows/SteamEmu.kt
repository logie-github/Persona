package com.logie.persona.backend.windows

import java.io.File

/**
 * The games run without the Steam client: every steam_api.dll / steam_api64.dll in a game's folder is swapped for the Goldberg emulator's
 * (gbe_fork, the same thing GameNative does), which answers the game's Steam calls itself. The originals are kept beside them (".orig") and put
 * back by [restore]. Each swapped library gets `steam_appid.txt` and a `steam_settings` folder beside it (the app's number, and the account's
 * name for the game's own save folders); the game's folder gets a `steam_appid.txt` too, which stops a game from relaunching itself through Steam.
 */
object SteamEmu {
    private val NAMES = setOf("steam_api.dll", "steam_api64.dll")
    private const val MARK = ".persona-steamemu"

    /** What [prepare] did: the libraries it swapped (paths inside the folder), and whether any were already the emulator's. */
    data class Result(val swapped: List<String>, val already: Int)

    /**
     * [emu] holds the emulator's own steam_api.dll and steam_api64.dll. Libraries already swapped are left as they are, unless the emulator was
     * updated (a different file): then they are swapped again (the originals stay as kept the first time).
     */
    fun prepare(dir: File, appId: Int, emu: File, accountName: String = "Persona", steamId64: Long = 0, ticketBase64: String? = null): Result {
        val swapped = ArrayList<String>(); var already = 0
        File(dir, "steam_appid.txt").writeText(appId.toString())
        dir.walkTopDown().maxDepth(8).filter { it.isFile && it.name.lowercase() in NAMES }.forEach { dll ->
            val replacement = File(emu, dll.name.lowercase())
            if (!replacement.isFile) return@forEach
            val orig = File(dll.path + ".orig")
            if (!orig.exists()) dll.copyTo(orig)
            if (dll.length() == replacement.length() && dll.readBytes().contentEquals(replacement.readBytes())) already++
            else { replacement.copyTo(dll, overwrite = true); swapped += dll.relativeTo(dir).path.replace('\\', '/') }
            val parent = dll.parentFile
            File(parent, "steam_appid.txt").writeText(appId.toString())
            val settings = File(parent, "steam_settings").also { it.mkdirs() }
            File(settings, "steam_appid.txt").writeText(appId.toString())
            File(settings, "configs.user.ini").writeText(userIni(accountName, steamId64, ticketBase64))
            // Not looking for other players on the local network (GameNative's setting for a game played online).
            File(settings, "configs.main.ini").writeText("[main::connectivity]\ndisable_lan_only=1\n")
        }
        if (swapped.isNotEmpty() || already > 0) File(dir, MARK).writeText("1")
        return Result(swapped, already)
    }

    /**
     * gbe_fork's user settings: the account's name and SteamID (the ticket's owner: Denuvo compares them) and the encrypted app ticket, when there is one.
     */
    fun userIni(accountName: String, steamId64: Long, ticketBase64: String?): String = buildString {
        append("[user::general]\n")
        append("account_name=").append(accountName).append('\n')
        if (steamId64 != 0L) append("account_steamid=").append(steamId64).append('\n')
        append("language=english\n")
        if (!ticketBase64.isNullOrEmpty()) append("ticket=").append(ticketBase64).append('\n')
    }

    /** Puts the game's own libraries back (before a repair or an uninstall); returns how many. */
    fun restore(dir: File): Int {
        var n = 0
        dir.walkTopDown().maxDepth(8).filter { it.isFile && it.name.lowercase().removeSuffix(".orig") in NAMES && it.name.endsWith(".orig", true) }.forEach { orig ->
            orig.copyTo(File(orig.path.removeSuffix(".orig")), overwrite = true); orig.delete(); n++
        }
        File(dir, MARK).delete()
        return n
    }

    /** True when a program is wrapped by Steam's own DRM (SteamStub: a ".bind" section), which the emulator alone can't start. */
    fun isSteamStubbed(exe: File): Boolean = runCatching {
        val b = ByteArray(4096).let { buf -> val n = exe.inputStream().use { it.read(buf) }; buf.copyOf(maxOf(n, 0)) }
        val pe = (b[0x3c].toInt() and 0xff) or ((b[0x3d].toInt() and 0xff) shl 8)
        if (pe + 24 > b.size || b[pe] != 'P'.code.toByte() || b[pe + 1] != 'E'.code.toByte()) return false
        val sections = (b[pe + 6].toInt() and 0xff) or ((b[pe + 7].toInt() and 0xff) shl 8)
        val optSize = (b[pe + 20].toInt() and 0xff) or ((b[pe + 21].toInt() and 0xff) shl 8)
        val first = pe + 24 + optSize
        (0 until sections).any { i -> val o = first + i * 40; o + 8 <= b.size && String(b, o, 8, Charsets.ISO_8859_1).trimEnd('\u0000') == ".bind" }
    }.getOrDefault(false)
}
