package com.logie.persona.backend.mods

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameBananaTest {
    private class Fake(val answers: Map<String, String>) : Web {
        val asked = ArrayList<String>()
        override fun text(url: String): String { asked += url; return answers.entries.first { url.contains(it.key) }.value }
        override fun download(url: String, to: File, onBytes: (Long, Long) -> Unit) { to.writeText("x") }
    }

    private val list = """{"_aMetadata":{"_nRecordCount":2,"_bIsComplete":true},"_aRecords":[
      {"_idRow":309876,"_sName":"HD UI for Shin Megami Tensei: Persona","_sVersion":"3.33","_nLikeCount":40,"_nViewCount":9000,"_tsDateUpdated":1700000000,
       "_aRootCategory":{"_sName":"Textures"},"_aSubmitter":{"_sName":"Someone"},
       "_aPreviewMedia":{"_aImages":[{"_sBaseUrl":"https://images.gamebanana.com/img/ss/mods","_sFile":"a.jpg","_sFile530":"530-90_a.jpg"}]}},
      {"_idRow":2,"_sName":"Plain","_aRootCategory":{"_sName":"Video"}}]}"""

    private val profile = """{"_idRow":309876,"_sName":"HD UI","_sVersion":"3.33","_aCategory":{"_sName":"GUIs"},"_sText":"<b>HD</b>",
      "_aFiles":[{"_idRow":670697,"_sFile":"ulus10432_a2a01.zip","_nFilesize":195964512,"_sDownloadUrl":"https://gamebanana.com/dl/670697","_sDescription":"Version 3.33 (Newest)"}]}"""

    @Test fun listsTheGamesModsWithTheirPictures() {
        val web = Fake(mapOf("Mod/Index" to list)); val mods = GameBanana(web).list()
        assertEquals(2, mods.size); assertEquals("HD UI for Shin Megami Tensei: Persona", mods[0].name); assertEquals("Textures", mods[0].category); assertEquals(40, mods[0].likes)
        assertEquals("https://images.gamebanana.com/img/ss/mods/530-90_a.jpg", mods[0].thumbnail); assertEquals(null, mods[1].thumbnail)
        assertTrue("_aFilters%5BGeneric_Game%5D=12961" in web.asked.single() && "Generic_MostDownloaded" in web.asked.single())
    }

    @Test fun aProfileHasItsFiles() {
        val m = GameBanana(Fake(mapOf("ProfilePage" to profile))).profile(309876)
        assertEquals("GUIs", m.category); assertEquals(1, m.files.size); assertEquals("ulus10432_a2a01.zip", m.files[0].name); assertEquals(195964512L, m.files[0].bytes); assertEquals("https://gamebanana.com/dl/670697", m.files[0].url)
    }

    @Test fun searchAsksForThisGamesModsOnly() {
        val web = Fake(mapOf("Util/Search" to list)); GameBanana(web).search("eboot patch")
        assertTrue("_idRow=12961".replace("_idRow", "_idGameRow") in web.asked.single() && "_sSearchString=eboot+patch" in web.asked.single())
    }
}
