package com.logie.persona.backend.windows

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsGamesTest {
    private fun tmp() = File.createTempFile("persona", "dir").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
    private fun File.file(path: String, bytes: Int = 10) = File(this, path).also { it.parentFile.mkdirs(); it.writeBytes(ByteArray(bytes) { 1 }) }

    @Test fun theKnownProgramIsFoundInAnyCaseElseTheLargestThatIsNotAnInstaller() {
        val d = tmp(); d.file("p4g.EXE", 100); d.file("Huge_Setup.exe", 5000); d.file("data.cpk", 9000)
        assertEquals("p4g.EXE", GameExe.find(d, "persona4_golden")!!.name)
        val e = tmp(); e.file("UnityCrashHandler64.exe", 9000); e.file("Game.exe", 500); e.file("vc_redist.x64.exe", 9999)
        assertEquals("Game.exe", GameExe.find(e, "some_other")!!.name)
        val f = tmp(); f.file("bin/Game.exe", 10)
        assertEquals("Game.exe", GameExe.find(f, "x")!!.name)
        assertEquals(null, GameExe.find(tmp(), "x"))
    }

    @Test fun steamsLibrariesAreSwappedKeptAndPutBack() {
        val emu = tmp(); emu.file("steam_api64.dll", 77); emu.file("steam_api.dll", 66)
        val game = tmp(); game.file("P4G.exe"); val dll = game.file("steam_api64.dll", 5); game.file("sub/STEAM_API.dll", 6)
        val r = SteamEmu.prepare(game, 1113000, emu, "someone")
        assertEquals(setOf("steam_api64.dll", "sub/STEAM_API.dll"), r.swapped.toSet())
        assertEquals(77, dll.length().toInt()); assertEquals(5, File(game, "steam_api64.dll.orig").length().toInt())
        assertEquals("1113000", File(game, "steam_appid.txt").readText()); assertEquals("1113000", File(game, "steam_settings/steam_appid.txt").readText())
        assertTrue("account_name=someone" in File(game, "steam_settings/configs.user.ini").readText())
        val again = SteamEmu.prepare(game, 1113000, emu); assertEquals(0, again.swapped.size); assertEquals(2, again.already)
        assertEquals(5, File(game, "steam_api64.dll.orig").length().toInt(), "the original is kept from the first time")
        assertEquals(2, SteamEmu.restore(game)); assertEquals(5, dll.length().toInt()); assertFalse(File(game, "steam_api64.dll.orig").exists())
    }

    @Test fun aSteamStubWrappedProgramIsRecognised() {
        fun pe(section: String): File {
            val b = ByteArray(1024); b[0] = 'M'.code.toByte(); b[1] = 'Z'.code.toByte(); b[0x3c] = 0x80.toByte()
            val pe = 0x80; b[pe] = 'P'.code.toByte(); b[pe + 1] = 'E'.code.toByte(); b[pe + 6] = 2; b[pe + 20] = 0xF0.toByte()
            val first = pe + 24 + 0xF0
            ".text".toByteArray().copyInto(b, first); section.toByteArray().copyInto(b, first + 40)
            return File.createTempFile("prog", ".exe").also { it.writeBytes(b); it.deleteOnExit() }
        }
        assertTrue(SteamEmu.isSteamStubbed(pe(".bind"))); assertFalse(SteamEmu.isSteamStubbed(pe(".rdata")))
    }

    @Test fun theTicketAndTheAccountGoToTheEmulatorsSettings() {
        assertEquals("[user::general]\naccount_name=me\naccount_steamid=76561198000000001\nlanguage=english\nticket=QUJD\n", SteamEmu.userIni("me", 76561198000000001, "QUJD"))
        assertEquals("[user::general]\naccount_name=me\nlanguage=english\n", SteamEmu.userIni("me", 0, null))
    }
}
