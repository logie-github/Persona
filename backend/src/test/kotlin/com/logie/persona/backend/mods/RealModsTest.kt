package com.logie.persona.backend.mods

import com.logie.persona.backend.psp.TestDiscs
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Archives laid out as real Persona 1 mods on GameBanana are (their listings as they were read from the site's own files). */
class RealModsTest {
    private val disc = listOf("PSP_GAME/SYSDIR/EBOOT.BIN", "PSP_GAME/PARAM.SFO", "PSP_GAME/USRDIR/SOUND/SE_BOX.BIN", "PSP_GAME/USRDIR/SOUND/SE_CHR.BIN",
        "PSP_GAME/USRDIR/EVENT/ENDALL.BIN", "PSP_GAME/USRDIR/MOVIE/OP.PMF")
    private fun plan(vararg paths: String) = ModPlanner.plan(paths.map { it to 10L }, "ULUS10432", disc, "mod.7z")

    @Test fun picturesNamedByTheirHashInFoldersAreChoices() {           // "PS5 UI buttons": no textures.ini, one folder per choice
        val p = plan("ps5 buttons/L1andR1/08eec700d656af1f47c4126d.png", "ps5 buttons/L1andR1/08f1b7c0d656af1f47c4126d.png",
            "ps5 buttons/L2andR2/08eec700d656af1f47c4126d.png", "ps5 buttons/README!!!.txt")
        assertEquals(listOf("ps5 buttons / L1andR1", "ps5 buttons / L2andR2"), p.variants.map { it.name })
        assertEquals(listOf("08eec700d656af1f47c4126d.png", "08f1b7c0d656af1f47c4126d.png"), p.variants[0].files.map { it.dest }, "at the pack's root, where PPSSPP looks")
        assertTrue(p.skipped.isEmpty())
    }

    @Test fun onlyThisReleasesFilesAreTakenAndPatchesFindTheirDiscFile() {       // "Persona 1 PSX echo for PSP SFX", "Cogito, ergo sum typo fix"
        val echo = plan("Echo/JP/SE_BOX.xdelta", "Echo/US/SE_BOX.xdelta", "Echo/US/SE_CHR.xdelta")
        assertEquals(listOf("Echo/US/SE_BOX.xdelta" to "PSP_GAME/USRDIR/SOUND/SE_BOX.BIN", "Echo/US/SE_CHR.xdelta" to "PSP_GAME/USRDIR/SOUND/SE_CHR.BIN"),
            echo.shared.map { it.from to it.dest }); assertTrue(echo.shared.all { it.part == Part.DISC_PATCH })
        assertEquals(listOf("Echo/JP/SE_BOX.xdelta"), echo.skipped.map { it.first })
        val typo = plan("Ending Typo fix/Xdelta install/ENDALL_Patch_JP.xdelta", "Ending Typo fix/Xdelta install/ENDALL_Patch_PAL.xdelta",
            "Ending Typo fix/Xdelta install/ENDALL_Patch_US.xdelta", "Ending Typo fix/HXD install/US and PAL/0093.gim", "Ending Typo fix/HXD install/JP/0090.gim")
        assertEquals(listOf("PSP_GAME/USRDIR/EVENT/ENDALL.BIN"), typo.shared.map { it.dest }, "the US patch only")
        assertEquals("Ending Typo fix/Xdelta install/ENDALL_Patch_US.xdelta", typo.shared.single().from)
    }

    @Test fun anEbootPatchStillGoesToTheProgram() {
        val p = ModPlanner.plan(listOf("extracted_ebootbin_us_fixed/patch.xdelta" to 10L), "ULUS10432", disc, "extracted_ebootbin_us_fixed.zip")
        assertEquals("PSP_GAME/SYSDIR/EBOOT.BIN", p.shared.single().dest)
    }

    @Test fun releaseFoldersAreRecognised() {
        assertEquals(setOf("US", "EU"), ModPlanner.regionsOfFolder("US and PAL")); assertEquals(setOf("JP"), ModPlanner.regionsOfFolder("JP"))
        assertEquals(null, ModPlanner.regionsOfFolder("ps5 buttons")); assertEquals(null, ModPlanner.regionsOfFolder("UI"))
        assertEquals("US", ModPlanner.regionOf("ULUS10432")); assertEquals("JP", ModPlanner.regionOf("ULJM05325"))
    }

    @Test fun hashNamedPicturesAreListedInTheMergedIniWhichAlwaysNamesAHash() {
        val ini = TextureIni.merge(listOf(TextureIni.Pack("m5", null, listOf("08ea61106c8a5fc69cfafc15.png", "08ea61106c8a5fc6b5b99853_1.png", "sub/08ea61106c8a5fc6b5b9cd53.png", "Named.png"))))
        assertTrue(ini.startsWith("[options]\nversion = 1\nhash = quick\n"), ini)
        assertTrue("08ea61106c8a5fc69cfafc15 = m5/08ea61106c8a5fc69cfafc15.png" in ini && "08ea61106c8a5fc6b5b99853_1 = m5/08ea61106c8a5fc6b5b99853_1.png" in ini, ini)
        assertTrue("cd53" !in ini, "only the root is scanned, as PPSSPP does")
        val two = TextureIni.merge(listOf(TextureIni.Pack("m1", "[options]\nhash = xxh64\n[hashes]\n08ea61106c8a5fc69cfafc15 = A.png\n"), TextureIni.Pack("m2", null, listOf("08ea61106c8a5fc69cfafc15.png"))))
        assertTrue("hash = xxh64" in two && "08ea61106c8a5fc69cfafc15 = m2/08ea61106c8a5fc69cfafc15.png" in two, two)
    }

    @Test fun aPackOfHashNamedPicturesWorksWhenInstalled() {             // "Persona 3/4 tarot card replaces grey card": a folder for the disc, no ini
        val dir = File.createTempFile("persona", "dir").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        val g = PspMods(dir, "ULUS10432", TestDiscs.file(TestDiscs.persona()))
        val zip = File.createTempFile("mod", ".zip").also { f -> ZipOutputStream(f.outputStream()).use { z ->
            for (n in listOf("tarot/ULUS10432/08ea61106c8a5fc69cfafc15.png", "tarot/README!!!.txt")) { z.putNextEntry(ZipEntry(n)); z.write(ByteArray(5)); z.closeEntry() } } }
        g.install(322343, "Tarot", "1", zip)
        val merged = File(g.memstick, "PSP/TEXTURES/ULUS10432/textures.ini").readText()
        assertTrue("08ea61106c8a5fc69cfafc15 = m322343/08ea61106c8a5fc69cfafc15.png" in merged && "hash = quick" in merged, merged)
    }

    @Test fun aRarArchiveSaysSo() {
        val f = File.createTempFile("mod", ".rar").also { it.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00)); it.deleteOnExit() }
        val e = assertFailsWith<IOException> { ModArchive.list(f) }; assertTrue("RAR" in e.message!!)
    }
}
