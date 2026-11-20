package com.logie.persona.backend.art

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuPictureTest {
    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF203040.toInt()

    /** A [w] x [h] picture of [bg] with a [fg] block from (x0, y0) to (x1, y1), exclusive. */
    private fun picture(w: Int, h: Int, bg: Int, fg: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x in x0 until x1 && y in y0 until y1) fg else bg }

    @Test fun aPlainBackgroundIsCutAndThePersonKept() {
        val w = 40; val h = 60
        // A little noise in the white, as a JPEG has.
        val px = picture(w, h, white, ink, 10, 8, 30, 60).mapIndexed { i, p -> if (p == white && i % 7 == 0) 0xFFF4F6F2.toInt() else p }.toIntArray()
        val cut = MenuPicture.cutBackground(px, w, h)
        assertTrue(cut > 0)
        assertEquals(0, px[0] ushr 24)                       // the corner is gone
        assertEquals(0, px[5 * w + 35] ushr 24)              // and the white beside the person
        assertEquals(0xFF, px[30 * w + 20] ushr 24)          // the person stays whole
        assertEquals(ink and 0xFFFFFF, px[30 * w + 20] and 0xFFFFFF)
        // Trimmed, the person stands on the bottom edge.
        assertContentEquals(intArrayOf(10, 8, 20, 52), MenuPicture.trim(px, w, h))
    }

    @Test fun whiteInsideThePersonIsKept() {
        // A white shirt not touching the border isn't the background.
        val w = 30; val h = 30
        val px = picture(w, h, white, ink, 5, 5, 25, 30)
        for (y in 12 until 18) for (x in 12 until 18) px[y * w + x] = white
        MenuPicture.cutBackground(px, w, h)
        assertEquals(0xFF, px[15 * w + 15] ushr 24)
    }

    @Test fun aPhotographOrAPictureAlreadyCutIsLeftAlone() {
        val w = 20; val h = 20
        val photo = IntArray(w * h) { i -> (0xFF shl 24) or ((i * 2654435761L).toInt() and 0xFFFFFF) }
        val before = photo.copyOf()
        assertEquals(0, MenuPicture.cutBackground(photo, w, h))
        assertContentEquals(before, photo)
        val cutAlready = picture(w, h, 0x00000000, ink, 5, 5, 15, 20)
        assertNull(MenuPicture.backgroundColour(cutAlready, w, h))
        assertEquals(0, MenuPicture.cutBackground(cutAlready, w, h))
    }

    @Test fun anEmptyPictureHasNothingToTrim() {
        assertNull(MenuPicture.trim(IntArray(16), 4, 4))
    }

    @Test fun cropsAreKeptInsideThePictureAndRoundTrip() {
        val c = MenuPicture.Crop(0.1f, 0.2f, 0.6f, 1f)
        assertEquals(c, MenuPicture.Crop.decode(c.encode()))
        assertContentEquals(intArrayOf(10, 40, 50, 160), c.pixels(100, 200))
        val wild = MenuPicture.Crop(-1f, 2f, 0.5f, -3f).clamped()
        assertTrue(wild.l >= 0f && wild.r <= 1f && wild.t >= 0f && wild.b <= 1f && wild.r > wild.l && wild.b > wild.t)
        assertNull(MenuPicture.Crop.decode("nonsense"))
        val px = IntArray(4 * 4) { it }
        assertContentEquals(intArrayOf(5, 6, 9, 10), MenuPicture.copy(px, 4, intArrayOf(1, 1, 2, 2)))
    }

    @Test fun eachGamesLookColoursThePictureAndLeavesTransparencyAlone() {
        val w = 90; val h = 90
        val skin = 0xFFE8C0A0.toInt()
        val looks = (1..5).map { n ->
            val px = IntArray(w * h) { i -> if (i % w < 45) skin else 0x00000000 }
            GameLook.apply(px, w, h, n)
            assertEquals(0, px[50] ushr 24)              // transparent stays transparent
            assertEquals(0xFF, px[10] ushr 24)
            px[45 * w + 10]
        }
        // Each game gives the same skin its own colour.
        assertEquals(5, looks.toSet().size)
        // Persona 3's is bluer than the skin, Persona 4's yellower (more red and green than blue), Persona 5's harder.
        val p3 = looks[2]; val p4 = looks[3]
        assertTrue((p3 and 0xFF) > (skin and 0xFF))
        assertTrue(((p4 shr 8) and 0xFF) - (p4 and 0xFF) > 0)
        // Strength 0 changes nothing.
        val px = IntArray(4) { skin }
        GameLook.apply(px, 2, 2, 5, strength = 0f)
        assertContentEquals(IntArray(4) { skin }, px)
        assertNotEquals(skin, looks[4])
    }

    @Test fun theRampRunsFromTheDarkestStopToTheLightest() {
        val ramp = GameLook.ramp(intArrayOf(0x000000, 0xFF0000, 0xFFFFFF))
        assertEquals(0x000000, ramp[0]); assertEquals(0xFFFFFF, ramp[255])
        assertEquals(0xFF, (ramp[128] shr 16) and 0xFF)
    }
}
