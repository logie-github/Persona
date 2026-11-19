package com.logie.persona.backend.touch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GesturesTest {
    /** Records every press and release with its time, and runs the gestures' clock like the screen does (a tick at each deadline). */
    private class Rig {
        val events = ArrayList<String>(); val sticks = ArrayList<Pair<Float, Float>>()
        var t = 0L
        val out = object : PadOutput {
            override fun button(button: Pad, down: Boolean) { events += "${if (down) "+" else "-"}$button@$t" }
            override fun stick(x: Float, y: Float) { sticks += x to y }
        }
        val g = Gestures(GestureConfig(dp = 1f, screenWidth = 1000f), out)
        fun at(time: Long, block: () -> Unit) { advance(time); block() }
        fun advance(time: Long) {
            while (true) { val d = g.nextDeadline(); if (d == null || d > time) break; t = d; g.tick(d) }
            t = time; g.tick(time)
        }
        fun downs(): List<Pad> = events.filter { it.startsWith("+") }.map { Pad.valueOf(it.drop(1).substringBefore('@')) }
        fun has(p: Pad) = events.any { it.startsWith("+$p@") }
    }

    @Test fun aSingleTapIsCrossAfterTheDoubleTapWindow() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.at(80) { r.g.up(1, 300f, 300f, 80) }
        r.advance(200); assertTrue(r.downs().isEmpty(), "X waits to see whether a second tap follows")
        r.advance(400); assertEquals(listOf(Pad.CROSS), r.downs()); assertTrue(r.events.last().startsWith("-CROSS"))
    }

    @Test fun aDoubleTapIsTriangleAndNotCross() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.at(70) { r.g.up(1, 300f, 300f, 70) }
        r.at(160) { r.g.down(1, 700f, 100f, 160) }; r.at(230) { r.g.up(1, 700f, 100f, 230) }          // the second one anywhere
        r.advance(800); assertEquals(listOf(Pad.TRIANGLE), r.downs())
    }

    @Test fun aTapThenAHeldTapIsOnePressOfSquare() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.at(70) { r.g.up(1, 300f, 300f, 70) }
        r.at(150) { r.g.down(1, 300f, 300f, 150) }; r.advance(1500)
        assertEquals(listOf(Pad.SQUARE), r.downs(), "one press, not Circle and not X"); assertTrue(r.events.last().startsWith("-SQUARE"))
        r.at(1600) { r.g.up(1, 300f, 300f, 1600) }; r.advance(2500); assertEquals(listOf(Pad.SQUARE), r.downs())
    }

    @Test fun aHeldTapIsCircleWhileHeld() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.advance(200); assertFalse(r.has(Pad.CIRCLE)); r.advance(300); assertTrue(r.has(Pad.CIRCLE))
        r.at(900) { r.g.up(1, 300f, 300f, 900) }; assertTrue(r.events.last().startsWith("-CIRCLE@900"))
    }

    @Test fun aQuickSwipeLeftOrRightIsOnePressOfL1OrR1() {
        val r = Rig(); r.at(0) { r.g.down(1, 500f, 300f, 0) }; r.at(40) { r.g.move(1, 440f, 302f, 40) }; r.at(90) { r.g.up(1, 380f, 305f, 90) }
        r.advance(300); assertEquals(listOf(Pad.L1), r.downs())
        r.at(400) { r.g.down(2, 300f, 300f, 400) }; r.at(450) { r.g.move(2, 380f, 300f, 450) }; r.at(500) { r.g.up(2, 430f, 300f, 500) }
        r.advance(900); assertEquals(listOf(Pad.L1, Pad.R1), r.downs())
    }

    @Test fun aHeldSwipeIsTheDpadAndTheStickAndLetsGoWhenLifted() {
        val r = Rig(); r.at(0) { r.g.down(1, 400f, 400f, 0) }; r.at(40) { r.g.move(1, 440f, 400f, 40) }; r.at(80) { r.g.move(1, 480f, 402f, 80) }
        r.advance(140); assertTrue(r.downs().isEmpty(), "not decided yet: it could still be a single swipe")
        r.advance(200); assertEquals(listOf(Pad.RIGHT), r.downs()); assertTrue(r.sticks.last().first > 0.9f)
        r.at(300) { r.g.move(1, 480f, 520f, 300) }; assertTrue(r.has(Pad.DOWN))
        r.at(400) { r.g.up(1, 480f, 520f, 400) }; assertEquals(0f to 0f, r.sticks.last()); assertTrue(r.events.last().startsWith("-DOWN"))
        assertFalse(r.has(Pad.L1) || r.has(Pad.R1), "a held swipe is not a single swipe")
    }

    @Test fun aHeldTapAndAHeldSwipeTogetherRun() {
        val r = Rig(); r.at(0) { r.g.down(1, 150f, 300f, 0) }; r.advance(300); assertTrue(r.has(Pad.CIRCLE))
        r.at(400) { r.g.down(2, 600f, 400f, 400) }; r.at(440) { r.g.move(2, 600f, 340f, 440) }; r.advance(700)
        assertTrue(r.has(Pad.UP)); r.at(800) { r.g.up(2, 600f, 340f, 800) }; r.at(900) { r.g.up(1, 150f, 300f, 900) }
        assertTrue(r.events.any { it.startsWith("-CIRCLE@900") })
    }

    @Test fun aSwipeInFromTheRightEdgeIsStartNotL1() {
        val r = Rig(); r.at(0) { r.g.down(1, 990f, 300f, 0) }; r.at(40) { r.g.move(1, 960f, 300f, 40) }; r.at(80) { r.g.move(1, 900f, 300f, 80) }; r.at(110) { r.g.up(1, 880f, 300f, 110) }
        r.advance(400); assertEquals(listOf(Pad.START), r.downs())
    }

    @Test fun aSwipeUpOrDownAloneDoesNothing() {
        val r = Rig(); r.at(0) { r.g.down(1, 500f, 500f, 0) }; r.at(40) { r.g.move(1, 502f, 440f, 40) }; r.at(90) { r.g.up(1, 505f, 380f, 90) }
        r.advance(900); assertTrue(r.downs().isEmpty())
    }

    @Test fun aThirdTapAfterADoubleTapStartsOver() {
        val r = Rig()
        r.at(0) { r.g.down(1, 5f, 5f, 0) }; r.at(60) { r.g.up(1, 5f, 5f, 60) }; r.at(140) { r.g.down(1, 5f, 5f, 140) }; r.at(200) { r.g.up(1, 5f, 5f, 200) }
        r.at(600) { r.g.down(1, 5f, 5f, 600) }; r.at(660) { r.g.up(1, 5f, 5f, 660) }; r.advance(1200)
        assertEquals(listOf(Pad.TRIANGLE, Pad.CROSS), r.downs())
    }

    @Test fun aSecondTouchThatSwipesLeavesTheFirstTapAsX() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.at(60) { r.g.up(1, 300f, 300f, 60) }
        r.at(120) { r.g.down(2, 300f, 300f, 120) }; r.at(150) { r.g.move(2, 380f, 300f, 150) }; r.at(190) { r.g.up(2, 440f, 300f, 190) }
        r.advance(800); assertEquals(listOf(Pad.CROSS, Pad.R1), r.downs())
    }

    @Test fun resetLetsGoOfEverything() {
        val r = Rig(); r.at(0) { r.g.down(1, 300f, 300f, 0) }; r.advance(400); assertTrue(r.has(Pad.CIRCLE)); r.at(500) { r.g.reset(500) }
        assertTrue(r.events.last().startsWith("-CIRCLE"))
    }
}
