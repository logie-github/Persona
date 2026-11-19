package com.logie.persona.backend.art

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtTest {
    @Test fun theRequestsAskForTheRightLists() {
        assertEquals("https://www.steamgriddb.com/api/v2/search/autocomplete/Persona%205%20Royal", SteamGrid.searchUrl(" Persona 5 Royal "))
        assertEquals(
            "https://www.steamgriddb.com/api/v2/grids/game/5254?dimensions=600x900,342x482,660x930&nsfw=false&humor=false&epilepsy=false&types=static",
            SteamGrid.picturesUrl(SteamGrid.Slot.BOX, 5254),
        )
        assertTrue(SteamGrid.picturesUrl(SteamGrid.Slot.HERO, 7, page = 2).endsWith("&types=static&page=2"))
        val logos = SteamGrid.picturesUrl(SteamGrid.Slot.LOGO, 9)
        assertTrue(logos.startsWith("https://www.steamgriddb.com/api/v2/logos/game/9?mimes=image/png,image/webp&nsfw=false")); assertFalse(logos.contains("types="))
    }

    @Test fun onlyTheSitesOwnAddressesAreFetched() {
        assertTrue(SteamGrid.isSiteUrl("https://cdn2.steamgriddb.com/grid/abc.png"))
        assertFalse(SteamGrid.isSiteUrl("http://cdn2.steamgriddb.com/grid/abc.png"))
        assertFalse(SteamGrid.isSiteUrl("https://steamgriddb.com.evil.example/x"))
        assertFalse(SteamGrid.isSiteUrl("https://evil.example/steamgriddb.com"))
        assertFalse(SteamGrid.isSiteUrl("file:///data/x"))
    }

    @Test fun gamesAndPicturesAreReadAndForeignAddressesDropped() {
        val games = SteamGrid.parseGames("""{"success":true,"data":[{"id":5254,"name":"Persona 5","verified":true},{"id":1,"name":""},{"id":2,"name":"Persona 4 Golden"}]}""")
        assertEquals(listOf("Persona 5", "Persona 4 Golden"), games.map { it.name })
        val page = SteamGrid.parsePictures(
            """{"success":true,"page":0,"total":120,"limit":50,"data":[
              {"id":10,"style":"alternate","width":600,"height":900,"url":"https://cdn2.steamgriddb.com/grid/a.png","thumb":"https://cdn2.steamgriddb.com/thumb/a.jpg","author":{"name":"someone"}},
              {"id":12,"url":"https://evil.example/c.png","thumb":"https://evil.example/c.jpg"}]}""")
        assertEquals(listOf(10L), page.pictures.map { it.id }); assertTrue(page.hasMore)
        assertEquals("600 × 900 · alternate", SteamGrid.resolution(page.pictures[0]))
    }

    @Test fun theSitesErrorsAreSaid() {
        val e = runCatching { SteamGrid.parsePictures("""{"success":false,"errors":["Game not found"]}""") }.exceptionOrNull()
        assertTrue(e is SteamGrid.SteamGridException); assertEquals("Game not found", e.message)
    }

    @Test fun aVersionIsLookedUpByTitleAndVersion() {
        assertEquals("Persona 5 Royal", SteamGrid.searchTerm("Persona 5", "Royal", onlyVersion = false))
        assertEquals("Persona 4", SteamGrid.searchTerm("Persona 4", "Golden", onlyVersion = true))
    }

    @Test fun theFrameHoldsTheMostOfThePictureAtZoomOneAndStaysInsideIt() {
        val r = CropMath.rect(2000, 1000, 2f / 3f, 1f, 1000f, 500f); assertEquals(1000, r.h); assertEquals(666, r.w)
        val z = CropMath.rect(2000, 1000, 1f, 2f, 1000f, 500f); assertEquals(500, z.w); assertEquals(500, z.h)
        val left = CropMath.rect(2000, 1000, 1f, 2f, -5000f, -5000f); assertEquals(0, left.x); assertEquals(0, left.y)
        val right = CropMath.rect(2000, 1000, 1f, 2f, 9000f, 9000f); assertEquals(1500, right.x); assertEquals(500, right.y)
    }
}
