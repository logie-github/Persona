package com.logie.persona.backend.windows

import java.io.File

/**
 * The way GameNative starts a store game by default (its "legacy DRM" swap of the game's own library is the other): the game keeps its own
 * steam_api64.dll, and gbe_fork's loader (`steamclient_loader_x64.exe`, its ColdClientLoader) starts it with the emulator's own client library
 * (`steamclient64.dll`) where the store's client would be, so the game's library talks to the emulator. Games that check their library (Denuvo's do)
 * see their own. Everything is in the games' Windows at `C:\Program Files (x86)\Steam`: the loader, the client libraries, the loader's settings
 * (`ColdClientLoader.ini`: which program, its folder, the app's number) and the emulator's (`steam_settings`: the account, its SteamID and the
 * game's ticket, as [SteamEmu.userIni]).
 */
object ColdClient {
    /** The emulator's files the loader needs (from gbe_fork's `steamclient_experimental`). The overlay libraries are optional. */
    val REQUIRED = listOf("steamclient.dll", "steamclient64.dll", "steamclient_loader_x32.exe", "steamclient_loader_x64.exe")
    val OPTIONAL = listOf("GameOverlayRenderer.dll", "GameOverlayRenderer64.dll")

    data class Prepared(val loader: File, val ini: String)

    /** True when [emu] holds everything the loader needs. */
    fun available(emu: File?): Boolean = emu != null && REQUIRED.all { File(emu, it).isFile }

    /** The loader for [exe]: the 64-bit one for a 64-bit program, else the 32-bit one. */
    fun loaderFor(exe: File): String = if (is64(exe)) "steamclient_loader_x64.exe" else "steamclient_loader_x32.exe"

    /** A PE program's machine is x86-64 (0x8664). */
    fun is64(exe: File): Boolean = runCatching {
        val b = ByteArray(4096); val n = exe.inputStream().use { it.read(b) }
        val pe = (b[0x3c].toInt() and 0xff) or ((b[0x3d].toInt() and 0xff) shl 8)
        pe + 6 <= n && b[pe] == 'P'.code.toByte() && b[pe + 1] == 'E'.code.toByte() &&
            ((b[pe + 4].toInt() and 0xff) or ((b[pe + 5].toInt() and 0xff) shl 8)) == 0x8664
    }.getOrDefault(true)

    /** The loader's settings: [exe] and [runDir] as Windows paths (absolute, e.g. `D:\persona4_golden\P4G.exe`). */
    fun ini(exe: String, runDir: String, args: String, appId: Int): String = buildString {
        append("[SteamClient]\r\n")
        append("Exe=").append(exe).append("\r\n")
        append("ExeRunDir=").append(runDir).append("\r\n")
        append("ExeCommandLine=").append(args.replace('\n', ' ').replace('\r', ' ')).append("\r\n")
        append("AppId=").append(appId).append("\r\n")
        append("SteamClientDll=steamclient.dll\r\n")
        append("SteamClient64Dll=steamclient64.dll\r\n")
        append("\r\n[Injection]\r\n")
        append("IgnoreLoaderArchDifference=1\r\n")
    }

    /**
     * Puts the loader and the client libraries from [emu] into [steamDir] (when they differ), writes the loader's settings for [exeWindows], and the
     * emulator's settings beside the libraries ([SteamEmu.userIni]: account, SteamID, ticket). Returns the loader to start.
     */
    fun prepare(
        steamDir: File, emu: File, appId: Int, exe: File, exeWindows: String, runDirWindows: String, args: String = "",
        accountName: String = "Persona", steamId64: Long = 0, ticketBase64: String? = null,
    ): Prepared {
        steamDir.mkdirs()
        for (name in REQUIRED + OPTIONAL) {
            val from = File(emu, name); if (!from.isFile) continue
            val to = File(steamDir, name)
            if (!to.isFile || to.length() != from.length() || !to.readBytes().contentEquals(from.readBytes())) from.copyTo(to, overwrite = true)
        }
        val ini = ini(exeWindows, runDirWindows, args, appId)
        File(steamDir, "ColdClientLoader.ini").writeText(ini)
        val settings = File(steamDir, "steam_settings").also { it.mkdirs() }
        File(settings, "steam_appid.txt").writeText(appId.toString())
        File(settings, "configs.user.ini").writeText(SteamEmu.userIni(accountName, steamId64, ticketBase64))
        File(settings, "configs.main.ini").writeText("[main::connectivity]\ndisable_lan_only=1\n")
        return Prepared(File(steamDir, loaderFor(exe)), ini)
    }

    /** [file] under [drive] (the folder a Windows drive letter maps to) as that drive's Windows path, e.g. `D:\persona4_golden\P4G.exe`. */
    fun windowsPath(letter: Char, drive: File, file: File): String {
        val rel = file.absoluteFile.relativeTo(drive.absoluteFile).path.replace('/', '\\')
        return if (rel.isEmpty()) "$letter:\\" else "$letter:\\$rel"
    }
}
