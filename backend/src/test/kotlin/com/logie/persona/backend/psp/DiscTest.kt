package com.logie.persona.backend.psp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiscTest {
    @Test fun readsTheFileTreeOfAnIso() {
        val disc = DiscBytes.open(TestDiscs.file(TestDiscs.persona()))
        val files = DiscFiles(disc)
        val paths = files.entries().map { it.path }
        assertTrue("PSP_GAME/PARAM.SFO" in paths); assertTrue("PSP_GAME/SYSDIR/EBOOT.BIN" in paths); assertTrue("PSP_GAME/USRDIR/MOVIE/MV11.PMF" in paths)
        assertContentEquals("EBOOT-ORIGINAL".toByteArray(), files.read(files.find("psp_game/sysdir/eboot.bin")!!))      // names ignore case, ";1" is dropped
        disc.close()
    }

    @Test fun aCsoReadsLikeTheIsoItCameFrom() {
        val iso = TestDiscs.persona(extra = mapOf("PSP_GAME/USRDIR/BIG.BIN" to ByteArray(70_000) { (it % 7).toByte() }))
        for (align in listOf(0, 2)) {
            val cso = DiscBytes.open(TestDiscs.file(TestDiscs.cso(iso, align = align), "disc.cso"))
            assertTrue(cso is CsoDisc); assertEquals(iso.size.toLong(), cso.length)
            assertContentEquals(iso.copyOfRange(30_000, 41_000), cso.readFully(30_000, 11_000))
            val big = DiscFiles(cso).let { it.read(it.find("PSP_GAME/USRDIR/BIG.BIN")!!) }
            assertContentEquals(ByteArray(70_000) { (it % 7).toByte() }, big); cso.close()
        }
    }

    @Test fun identifiesPersonaByIdOrTitle() {
        val us = DiscBytes.open(TestDiscs.file(TestDiscs.persona()))
        assertEquals(PspDisc("ULUS10432", "Shin Megami Tensei: Persona", true), PspDiscs.identify(us))
        // another region: its own id, recognised by its title
        val jp = DiscBytes.open(TestDiscs.file(TestDiscs.persona(title = "Persona", id = "ULJM-05492")))
        assertTrue(PspDiscs.identify(jp).isPersona); assertEquals("ULJM05492", PspDiscs.identify(jp).discId)
    }

    @Test fun otherPspGamesAreNotPersona() {
        for (t in listOf("Persona 3 Portable", "Persona 2: Innocent Sin", "Shin Megami Tensei: Persona 3", "Gran Turismo")) {
            val d = DiscBytes.open(TestDiscs.file(TestDiscs.persona(title = t, id = "ULUS99999")))
            assertFalse(PspDiscs.identify(d).isPersona, t)
        }
    }

    @Test fun aFileThatIsNotADiscIsRefused() {
        assertFailsWith<IllegalArgumentException> { DiscFiles(DiscBytes.open(TestDiscs.file(ByteArray(50_000)))) }
        assertFailsWith<IllegalArgumentException> { PspDiscs.identify(DiscBytes.open(TestDiscs.file(TestDiscs.iso(mapOf("README.TXT" to byteArrayOf(1)))))) }
    }

    @Test fun extractsAFileToDisk() {
        val d = DiscBytes.open(TestDiscs.file(TestDiscs.persona())); val files = DiscFiles(d)
        val out = File.createTempFile("xdir", "x").also { it.delete() }.resolve("a/b/MV11.PMF")
        files.extract(files.find("PSP_GAME/USRDIR/MOVIE/MV11.PMF")!!, out)
        assertContentEquals(ByteArray(5000) { it.toByte() }, out.readBytes()); assertNotNull(out.parentFile)
    }

    @Test fun paramSfoHoldsTextAndNumbers() {
        val p = ParamSfo.parse(TestDiscs.sfo("DISC_ID" to "ULUS10432", "TITLE" to "T", "N" to 5))
        assertEquals("ULUS10432", p.discId); assertEquals("T", p.title); assertEquals(5L, p.values["N"])
    }
}
