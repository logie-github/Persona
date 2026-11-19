package com.logie.persona.backend.mods

import com.logie.persona.backend.psp.TestDiscs
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModsTest {
    private fun tmp(): File = File.createTempFile("persona", "dir").let { it.delete(); it.mkdirs(); it.deleteOnExit(); it }
    private fun zip(files: Map<String, ByteArray>): File = File.createTempFile("mod", ".zip").also { f ->
        ZipOutputStream(f.outputStream()).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }; f.deleteOnExit()
    }
    private fun sevenZ(files: Map<String, ByteArray>): File = File.createTempFile("mod", ".7z").also { f ->
        SevenZOutputFile(f).use { z -> files.forEach { (n, b) -> z.putArchiveEntry(SevenZArchiveEntry().also { e -> e.name = n; e.size = b.size.toLong() }); z.write(b); z.closeArchiveEntry() } }; f.deleteOnExit()
    }
    private fun game(): PspMods {
        val iso = TestDiscs.file(TestDiscs.persona()); return PspMods(tmp(), "ULUS10432", iso)
    }
    private val ini1 = "[options]\nhash = xxh64\nversion = 1\n\n[hashes]\n000000008327c4ac00000000 = Text/Letters.png\n"
    private val ini2 = "[options]\nhash = xxh32\n\n[hashes]\n000000008327c4ac00000000 = Other.png\naaaa = Only2.png\n"
    private fun png(n: Int) = ByteArray(n) { 7 }

    @Test fun aRootLevelTexturePackGoesUnderItsOwnFolderAndIsMerged() {
        val g = game()
        g.install(101, "HD UI", "3.3", zip(mapOf("textures.ini" to ini1.toByteArray(), "Text/Letters.png" to png(10), "Preview.png" to png(3), "readme.txt" to "hi".toByteArray())))
        assertTrue(File(g.memstick, "PSP/TEXTURES/ULUS10432/m101/Text/Letters.png").isFile)
        assertFalse(File(g.memstick, "PSP/TEXTURES/ULUS10432/m101/readme.txt").exists(), "readmes aren't textures")
        val merged = File(g.memstick, "PSP/TEXTURES/ULUS10432/textures.ini").readText()
        assertTrue("000000008327c4ac00000000 = m101/Text/Letters.png" in merged, merged); assertTrue("hash = xxh64" in merged)
        assertTrue(g.bootPath().isFile, "no mod needs the disc's files changed: the image itself is played")
    }

    @Test fun theFullFolderLayoutAndItsCheatsAreUnderstood() {
        val g = game(); val plan = g.plan(zip(mapOf("Purple/Cheats/ULUS10432.ini" to "_S ULUS-10432\n_G Persona\n_C0 God\n_L 0x1 0x2\n".toByteArray(),
            "Purple/TEXTURES/ULUS10432/textures.ini" to ini1.toByteArray(), "Purple/TEXTURES/ULUS10432/Text/Letters.png" to png(4))))
        assertEquals(1, plan.variants.size); assertEquals(listOf(Part.CHEATS), plan.shared.map { it.part })
        g.install(102, "Purple", "1", zip(mapOf("Purple/Cheats/ULUS10432.ini" to "_S ULUS-10432\n_G Persona\n_C0 God\n_L 0x1 0x2\n".toByteArray(),
            "Purple/TEXTURES/ULUS10432/textures.ini" to ini1.toByteArray(), "Purple/TEXTURES/ULUS10432/Text/Letters.png" to png(4))))
        assertTrue(File(g.memstick, "PSP/Cheats/ULUS10432.ini").readText().contains("_C0 God")); assertTrue(File(g.memstick, "PSP/TEXTURES/ULUS10432/m102/Text/Letters.png").isFile)
    }

    @Test fun severalVariantsAreOfferedAndOneChosen() {
        val g = game(); val z = zip(mapOf("PS4/ULUS10432/textures.ini" to ini1.toByteArray(), "PS4/ULUS10432/A.png" to png(2), "Xbox/ULUS10432/textures.ini" to ini2.toByteArray(), "Xbox/ULUS10432/B.png" to png(2)))
        val plan = g.plan(z); assertEquals(setOf("PS4", "Xbox"), plan.variants.map { it.name }.toSet())
        g.install(103, "Buttons", "1", z, variant = plan.variants.indexOfFirst { it.name == "Xbox" })
        assertTrue(File(g.memstick, "PSP/TEXTURES/ULUS10432/m103/B.png").isFile); assertFalse(File(g.memstick, "PSP/TEXTURES/ULUS10432/m103/A.png").exists())
    }

    @Test fun twoPacksMergeAndTheLaterWinsAndOffWorks() {
        val g = game()
        g.install(1, "A", "1", zip(mapOf("textures.ini" to ini1.toByteArray(), "Text/Letters.png" to png(2))))
        g.install(2, "B", "1", zip(mapOf("textures.ini" to ini2.toByteArray(), "Other.png" to png(2), "Only2.png" to png(2))))
        val m = File(g.memstick, "PSP/TEXTURES/ULUS10432/textures.ini").readText()
        assertTrue("000000008327c4ac00000000 = m2/Other.png" in m, m); assertTrue("aaaa = m2/Only2.png" in m); assertTrue("hash = xxh64" in m, "the first pack's options stand")
        g.setEnabled(2, false)
        val m2 = File(g.memstick, "PSP/TEXTURES/ULUS10432/textures.ini").readText(); assertTrue("m1/Text/Letters.png" in m2); assertFalse("m2/" in m2)
        g.setEnabled(1, false); assertFalse(File(g.memstick, "PSP/TEXTURES/ULUS10432/textures.ini").exists())
        g.uninstall(2); assertFalse(File(g.memstick, "PSP/TEXTURES/ULUS10432/m2").exists()); assertEquals(listOf(1L), g.list().map { it.id })
    }

    @Test fun aMovieReplacesTheDiscsOneAndRemovingItPutsTheGameBack() {
        val g = game(); val movie = ByteArray(3000) { 9 }
        val z = zip(mapOf("Persona undub/USRDIR/MOVIE/MV11.pmf" to movie, "Persona undub/Package.xml" to "x".toByteArray()))
        g.install(5, "Undub", "1.2", z)
        assertEquals("tree", g.bootPath().name); assertContentEquals(movie, File(g.bootPath(), "PSP_GAME/USRDIR/MOVIE/MV11.PMF").readBytes())
        assertContentEquals("EBOOT-ORIGINAL".toByteArray(), File(g.bootPath(), "PSP_GAME/SYSDIR/EBOOT.BIN").readBytes(), "the rest of the disc is as it was")
        g.setEnabled(5, false); assertFalse(File(g.dir, "tree").exists()); assertTrue(g.bootPath().isFile)
    }

    @Test fun aLooseFileIsPlacedByItsNameOnTheDisc() {
        val g = game(); val plan = g.plan(zip(mapOf("undub patch movies/MV11.pmf" to ByteArray(10), "undub patch movies/Notes.bin" to ByteArray(2))))
        assertEquals(listOf("PSP_GAME/USRDIR/MOVIE/MV11.PMF"), plan.shared.filter { it.part == Part.DISC_FILE }.map { it.dest })
        assertEquals(1, plan.skipped.size, "a file with no place on the disc is reported")
    }

    @Test fun anEbootPatchIsAppliedToTheDiscsEbootAndRefusedForAnotherOne() {
        val g = game(); val original = "EBOOT-ORIGINAL".toByteArray(); val patched = "EBOOT-PATCHED!!!".toByteArray()
        g.install(7, "FPS", "1", zip(mapOf("extracted ebootbin US/SYSDIR/" to ByteArray(0), "extracted ebootbin US/USpatch.xdelta" to TestVcdiff.encode(original, patched))))
        assertContentEquals(patched, File(g.bootPath(), "PSP_GAME/SYSDIR/EBOOT.BIN").readBytes())
        g.uninstall(7); assertFalse(File(g.dir, "tree").exists())
        val wrong = TestVcdiff.encode(ByteArray(500) { it.toByte() }, ByteArray(500) { (it + 1).toByte() })
        val e = assertFailsWith<IOException> { g.install(8, "Other region", "1", zip(mapOf("eboot/Ebootpatch.xdelta" to wrong))) }
        assertTrue("can't be used on this copy" in e.message!!, e.message)
    }

    @Test fun aSevenZipModIsReadToo() {
        val g = game(); val r = g.install(9, "Packed", "1", sevenZ(mapOf("textures.ini" to ini1.toByteArray(), "Text/Letters.png" to png(5))))
        assertTrue(r.mod.textures); assertTrue(File(g.memstick, "PSP/TEXTURES/ULUS10432/m9/Text/Letters.png").isFile)
    }

    @Test fun aModWithNothingUsableIsRefusedSaying() {
        val g = game(); val e = assertFailsWith<IOException> { g.install(10, "Junk", "1", zip(mapOf("a/b/c.dat" to ByteArray(3)))) }
        assertTrue("nothing in this mod" in e.message!!)
    }

    @Test fun reinstallingReplacesAndTheListSurvivesAReload() {
        val g = game(); val z = zip(mapOf("textures.ini" to ini1.toByteArray(), "Text/Letters.png" to png(2)))
        g.install(11, "A", "1", z); g.install(11, "A", "2", z)
        val again = PspMods(g.dir, "ULUS10432", File(g.dir, "../x")); assertEquals(listOf("2"), again.list().map { it.version })
    }
}
