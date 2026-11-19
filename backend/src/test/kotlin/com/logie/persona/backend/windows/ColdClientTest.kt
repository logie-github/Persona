package com.logie.persona.backend.windows

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColdClientTest {
    private fun pe(f: File, machine: Int) {
        val b = ByteArray(512); b[0] = 'M'.code.toByte(); b[1] = 'Z'.code.toByte(); b[0x3c] = 0x80.toByte()
        b[0x80] = 'P'.code.toByte(); b[0x81] = 'E'.code.toByte(); b[0x84] = (machine and 0xff).toByte(); b[0x85] = (machine shr 8).toByte()
        f.writeBytes(b)
    }

    @Test fun theLoaderMatchesTheProgram() {
        val d = createTempDirectory("cc").toFile()
        val x64 = File(d, "P4G.exe").also { pe(it, 0x8664) }
        val x86 = File(d, "old.exe").also { pe(it, 0x14c) }
        assertTrue(ColdClient.is64(x64)); assertFalse(ColdClient.is64(x86))
        assertEquals("steamclient_loader_x64.exe", ColdClient.loaderFor(x64))
        assertEquals("steamclient_loader_x86.exe", ColdClient.loaderFor(x86))
    }

    @Test fun theLoaderItsSettingsAndTheTicketGoIntoTheGamesWindows() {
        val root = createTempDirectory("cc").toFile()
        val emu = File(root, "emu").also { it.mkdirs() }
        (ColdClient.REQUIRED + "GameOverlayRenderer64.dll").forEach { File(emu, it).writeText(it) }
        assertTrue(ColdClient.available(emu))
        val games = File(root, "games"); val game = File(games, "persona4_golden").also { it.mkdirs() }
        val exe = File(game, "P4G.exe").also { pe(it, 0x8664) }
        val steam = File(root, "drive_c/Program Files (x86)/Steam")
        val exeWin = ColdClient.windowsPath('D', games, exe)
        assertEquals("D:\\persona4_golden\\P4G.exe", exeWin)
        val p = ColdClient.prepare(steam, emu, 1113000, exe, exeWin, ColdClient.windowsPath('D', games, game), accountName = "me", steamId64 = 7656119, ticketBase64 = "QUJD")
        assertEquals(File(steam, "steamclient_loader_x64.exe"), p.loader)
        assertTrue(p.loader.isFile && File(steam, "steamclient64.dll").isFile && File(steam, "GameOverlayRenderer64.dll").isFile)
        val ini = File(steam, "ColdClientLoader.ini").readText()
        assertTrue(ini.contains("Exe=D:\\persona4_golden\\P4G.exe\r\n")); assertTrue(ini.contains("ExeRunDir=D:\\persona4_golden\r\n")); assertTrue(ini.contains("AppId=1113000"))
        assertTrue(ini.contains("SteamClient64Dll=steamclient64.dll")); assertTrue(ini.contains("IgnoreInjectionError=1")); assertTrue(ini.contains("Mode=0"))
        assertTrue(File(steam, "steam_settings/configs.user.ini").readText().contains("ticket=QUJD"))
        assertEquals("1113000", File(steam, "steam_settings/steam_appid.txt").readText())
    }

    @Test fun nothingWithoutTheLoader() {
        val emu = createTempDirectory("cc").toFile()
        File(emu, "steamclient64.dll").writeText("x")
        assertFalse(ColdClient.available(emu)); assertFalse(ColdClient.available(null))
    }
}
