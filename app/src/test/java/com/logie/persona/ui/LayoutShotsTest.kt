package com.logie.persona.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the launcher on the open Fold's screen (the library on the left, the chosen game on the right), for a person to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h600dp-land-xhdpi")
class LayoutShotsTest {
    @get:Rule val compose = createComposeRule()
    private fun save(name: String) {
        val dir = File(System.getProperty("persona.shots") ?: "build/shots").also { it.mkdirs() }
        // captureToImage waits for a frame Robolectric's paused looper never runs: the view draws itself instead (as the Blizzard launcher's UiShots does).
        val view = compose.onRoot().fetchSemanticsNode().root as android.view.View
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theLibraryAndEachGamesOwnScreen() {
        compose.mainClock.autoAdvance = false                      // (a progress spinner never lets the clock be idle)
        compose.setContent { Launcher() }
        repeat(8) { compose.mainClock.advanceTimeBy(250) }        // (the menu's entrance takes 0.9 s)
        save("wide-p1")
        compose.onNodeWithText("Persona 2").performClick(); repeat(4) { compose.mainClock.advanceTimeBy(250) }      // (the first click of a test isn't drawn until the next)
        for (n in listOf(5, 4, 3, 2)) {
            compose.onNodeWithText("Persona $n").performClick(); repeat(8) { compose.mainClock.advanceTimeBy(250) }; save("wide-p$n")
        }
    }

    /**
     * The person's own pictures in every game: a person on a white background (the app's Persona 3 picture put on white, as a photo would come)
     * cut out and put in each game's look, and a logo in place of Persona 4's and Persona 5's lettering.
     */
    @Test fun aPersonAndALogoInEachGame() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val person = context.assets.open("characters/p3.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        val photo = Bitmap.createBitmap(person.width + 120, person.height + 80, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(photo).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(person, 60f, 40f, null) }
        val photoFile = File(context.cacheDir, "photo.png").also { f -> f.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val logo = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(logo).apply {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 150f; isFakeBoldText = true; color = android.graphics.Color.rgb(255, 214, 0) }
            drawText("LOGO", 20f, 160f, paint)
        }
        val logoFile = File(context.cacheDir, "logo.png").also { f -> f.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        for (n in 1..5) {
            check(MenuPictures.set(context, MenuPictures.Kind.CHARACTER, n, photoFile)) { "the person for $n wasn't kept" }
        }
        for (n in listOf(4, 5)) check(MenuPictures.set(context, MenuPictures.Kind.LOGO, n, logoFile))
        // The pictures made before the screens draw (the menu makes them off the main thread).
        for (n in 1..5) { MenuPictures.menuPicture(context, MenuPictures.Kind.CHARACTER, n); MenuPictures.menuPicture(context, MenuPictures.Kind.LOGO, n) }
        compose.mainClock.autoAdvance = false
        compose.setContent { Launcher() }
        repeat(8) { compose.mainClock.advanceTimeBy(250) }
        compose.onNodeWithText("Persona 2").performClick(); repeat(4) { compose.mainClock.advanceTimeBy(250) }
        for (n in listOf(5, 4, 3, 2, 1)) {
            compose.onNodeWithText(if (n == 1) "Shin Megami Tensei: Persona" else "Persona $n", substring = true).performClick(); repeat(8) { compose.mainClock.advanceTimeBy(250) }; save("person-p$n")
        }
    }

    /** The cover screen: the game's menu over a row of boxes. */
    @Config(qualifiers = "w411dp-h860dp-port-xhdpi")
    @Test fun theCoverScreen() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Launcher() }
        repeat(8) { compose.mainClock.advanceTimeBy(250) }
        for (n in listOf(5, 3)) {
            compose.onNodeWithText("PERSONA $n").performClick(); repeat(8) { compose.mainClock.advanceTimeBy(250) }; save("narrow-p$n")
        }
    }
}
