package com.logie.persona.backend.mods

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/** Real archives from GameBanana, read and planned (no disc: the disc's own file list is empty). Runs only with PERSONA_LIVE_MODS_DIR set to a folder of them. */
class LiveModsTest {
    @Test fun realArchivesAreReadAndPlanned() {
        val dir = System.getenv("PERSONA_LIVE_MODS_DIR"); assumeTrue(dir != null)
        for (f in File(dir!!).listFiles()!!.sortedBy { it.name }) {
            val entries = try { ModArchive.list(f) } catch (e: Exception) { println("LIVE ${f.name}: cannot read: ${e.message}"); continue }
            val plan = ModPlanner.plan(entries, "ULUS10432", listOf("PSP_GAME/SYSDIR/EBOOT.BIN", "PSP_GAME/PARAM.SFO"), f.name)
            println("LIVE ${f.name}: ${entries.size} files; variants=${plan.variants.map { it.name + "(" + it.files.size + ")" }} shared=${plan.shared.map { it.part.name + ":" + it.dest }.take(6)} skipped=${plan.skipped.take(4)}")
            println("      first: ${entries.take(5).map { it.first }}")
        }
    }
}
