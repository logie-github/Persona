package com.logie.persona.backend

import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.catalog.MenuSpec
import com.logie.persona.backend.catalog.OptionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuSpecTest {
    private fun ids(gameId: String, versionId: String, installed: Boolean) =
        MenuSpec.optionsFor(Catalog.all.first { it.id == gameId }, Catalog.findVersion(versionId)!!, installed).map { it.id }

    @Test fun notInstalledOffersInstallAndPlayWaits() {
        val opts = MenuSpec.optionsFor(Catalog.all[3], Catalog.findVersion("persona4_golden")!!, installed = false)
        assertEquals(listOf(OptionId.PLAY, OptionId.INSTALL, OptionId.MODS, OptionId.OPTIONS), opts.map { it.id })
        assertFalse(opts.first { it.id == OptionId.PLAY }.enabled)
        assertFalse(opts.first { it.id == OptionId.MODS }.enabled)
    }

    @Test fun installedBecomesUninstallAndPlayWorks() {
        val opts = MenuSpec.optionsFor(Catalog.all[3], Catalog.findVersion("persona4_golden")!!, installed = true)
        assertEquals(listOf(OptionId.PLAY, OptionId.UNINSTALL, OptionId.MODS, OptionId.OPTIONS), opts.map { it.id })
        assertTrue(opts.all { it.enabled })
    }

    @Test fun versionOnlyWhereThereIsMoreThanOne() {
        assertTrue(OptionId.VERSION in ids("persona3", "persona3_portable", true))   // Portable and Reload
        assertTrue(OptionId.VERSION in ids("persona5", "persona5_royal", false))
        assertFalse(OptionId.VERSION in ids("persona4", "persona4_golden", true))     // Golden only
    }

    @Test fun personaOneIsTheShinMegamiTenseiPspGameOnly() {
        val g = Catalog.all.first { it.id == "persona1" }
        assertEquals("Shin Megami Tensei: Persona", g.title)
        assertEquals(listOf("persona1_psp"), g.versions.map { it.id })
        assertEquals(listOf(OptionId.PLAY, OptionId.INSTALL, OptionId.OPTIONS), ids("persona1", "persona1_psp", false))
        assertEquals(listOf("Shin Megami", "Tensei", "Persona"), g.lines)
    }

    @Test fun modsOnlyOnPcVersions() {
        assertFalse(OptionId.MODS in ids("persona1", "persona1_psp", true))           // emulator games
        assertFalse(OptionId.MODS in ids("persona2", "persona2_ep_ps1", true))
        assertTrue(OptionId.MODS in ids("persona3", "persona3_reload", true))
    }

    @Test fun everyGameHasPlayInstallAndOptions() {
        for (g in Catalog.all) for (v in g.versions) {
            val o = ids(g.id, v.id, false)
            assertTrue(OptionId.PLAY in o && OptionId.INSTALL in o && OptionId.OPTIONS in o, v.id)
            assertTrue(OptionId.UNINSTALL in ids(g.id, v.id, true), v.id)
        }
    }
}
