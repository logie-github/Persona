package com.logie.persona.backend

import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.catalog.Origin
import com.logie.persona.backend.steam.FileLoginStore
import com.logie.persona.backend.steam.Installer
import com.logie.persona.backend.steam.InstallProgress
import com.logie.persona.backend.steam.StoredLogin
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendTest {
    @Test fun fiveBoxesInOrder() {
        assertEquals(listOf(1, 2, 3, 4, 5), Catalog.all.map { it.number })
    }

    @Test fun versionIdsAreUniqueAndFound() {
        val ids = Catalog.all.flatMap { g -> g.versions.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, Catalog.findVersion(it)?.id) }
    }

    @Test fun appNumbersBelongToDownloadedVersionsOnly() {
        Catalog.all.flatMap { it.versions }.forEach { v ->
            assertEquals(v.origin == Origin.ACCOUNT_DOWNLOAD, v.appId != null, v.id)
        }
        assertEquals(1113000, Catalog.findVersion("persona4_golden")?.appId)
        assertEquals(1687950, Catalog.findVersion("persona5_royal")?.appId)
    }

    @Test fun boxesOneAndTwoTakeYourOwnDiscs() {
        assertTrue(Catalog.all.take(2).flatMap { it.versions }.all { it.origin == Origin.DISC_IMAGE })
    }

    @Test fun loginStoreRoundTrips() {
        val f = File.createTempFile("login", ".txt").also { it.delete() }
        val s = FileLoginStore(f)
        assertNull(s.load())
        s.save(StoredLogin("name", "token", "guard"))
        assertEquals(StoredLogin("name", "token", "guard"), s.load())
        s.save(StoredLogin("name", "token2", null))
        assertEquals(StoredLogin("name", "token2", null), s.load())
        s.clear()
        assertNull(s.load())
        assertFalse(f.exists())
    }

    @Test fun progressAveragesDepotsAndCompletes() {
        val seen = ArrayList<InstallProgress>()
        val done = CompletableDeferred<Throwable?>()
        val t = Installer.Tracker({ seen += it }, done)
        t.onChunkCompleted(1, 0.5f, 0, 0)
        t.onChunkCompleted(2, 0.0f, 0, 0)
        assertEquals(0.25f, seen.last().fraction)
        t.onDepotCompleted(1, 0, 0)
        assertEquals(0.5f, seen.last().fraction)
        assertTrue(seen.all { it.step == InstallProgress.Step.DOWNLOADING })
        assertFalse(done.isCompleted)
    }
}
