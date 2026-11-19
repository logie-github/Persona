package com.logie.persona.backend.touch

import kotlin.math.abs
import kotlin.math.hypot

/** The PSP's controls the gestures press. */
enum class Pad { CROSS, CIRCLE, SQUARE, TRIANGLE, L1, R1, START, UP, DOWN, LEFT, RIGHT }

/** Where the gestures' effects go (the emulator's input, or a recording in tests). */
interface PadOutput {
    fun button(button: Pad, down: Boolean)
    /** The left stick, each -1..1 (y positive downward, as Android axes are). */
    fun stick(x: Float, y: Float)
}

/**
 * Times in milliseconds and distances in pixels. [dp] is how many pixels make one dp; [screenWidth] is the touch area's width (the right
 * edge is where a swipe for Start begins).
 */
class GestureConfig(
    val dp: Float,
    val screenWidth: Float,
    val holdMs: Long = 260,            // a finger still this long is a held tap (Circle)
    val swipeHoldMs: Long = 150,       // a finger that has moved and is still down after this long is a held swipe (the stick)
    val tapMaxMs: Long = 200,          // a touch that lifts within this, without moving, is a tap
    val doubleTapMs: Long = 230,       // a second touch within this of a tap makes a double tap (or a tap and hold)
    val pulseMs: Long = 60,            // how long a single press is held down
) {
    val slop = 14f * dp                // movement under this is a still finger
    val flickMin = 56f * dp            // a single swipe goes at least this far
    val stickRadius = 56f * dp         // the stick is at full over this distance from where the finger came down
    val deadZone = 20f * dp            // and not at all under this
    val edgeZone = 32f * dp            // a swipe for Start comes down this near the right edge
}

/**
 * The touch scheme: a held swipe is the d-pad / left stick; a single swipe left or right is L1 or R1; a held tap is Circle (and can be held while
 * swiping, to run); a single tap is X; a double tap is Triangle; a swipe in from the right edge is Start; a tap and then a held tap is one
 * press of Square. Drive it with [down] / [move] / [up], and call [tick] with the time at least every frame (and at [nextDeadline]).
 */
class Gestures(private val cfg: GestureConfig, private val out: PadOutput) {
    private enum class Mode { PENDING, SWIPING, STICK, HELD, DONE }

    private class Finger(val id: Int, val x0: Float, val y0: Float, val t0: Long, val second: Boolean) {
        var x = x0; var y = y0; var mode = Mode.PENDING; var moved = false; var circle = false; var dpad: Pad? = null
    }

    private val fingers = LinkedHashMap<Int, Finger>()
    private val releases = ArrayList<Pair<Long, Pad>>()
    private val held = HashSet<Pad>()
    private var now = 0L
    private var pendingCross = NONE                     // when a single tap becomes X, unless a second touch comes first
    private var stickOwner = -1
    private var circles = 0

    fun down(id: Int, x: Float, y: Float, t: Long) {
        tick(t)
        // a touch soon after a tap (X waiting) is the second half of a double tap, or of a tap and hold; X waits for it to be settled
        val second = pendingCross != NONE && pendingCross != HOLDING
        if (second) pendingCross = HOLDING
        fingers[id] = Finger(id, x, y, t, second)
    }

    fun move(id: Int, x: Float, y: Float, t: Long) {
        tick(t); val f = fingers[id] ?: return; f.x = x; f.y = y
        val dx = x - f.x0; val dy = y - f.y0
        if (!f.moved && hypot(dx, dy) > cfg.slop) {
            f.moved = true
            if (f.second) fireCross()                                       // the second touch is going somewhere else: the first tap was a tap
            if (f.mode == Mode.PENDING) f.mode = Mode.SWIPING
        }
        if (f.mode == Mode.SWIPING && f.x0 >= cfg.screenWidth - cfg.edgeZone && dx <= -cfg.flickMin) { press(Pad.START); f.mode = Mode.DONE }
        if (f.mode == Mode.STICK || (f.mode == Mode.HELD && f.moved)) steer(f)
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        tick(t); val f = fingers.remove(id) ?: return; f.x = x; f.y = y
        val dx = x - f.x0; val dy = y - f.y0; val dur = t - f.t0
        when (f.mode) {
            Mode.PENDING -> when {
                dur <= cfg.tapMaxMs && !f.moved -> if (f.second) { pendingCross = NONE; press(Pad.TRIANGLE) } else pendingCross = t + cfg.doubleTapMs
                f.second -> fireCross()                                       // neither a tap nor a hold: the first tap stands
            }
            Mode.SWIPING -> if (dur < cfg.swipeHoldMs && abs(dx) >= cfg.flickMin && abs(dx) > abs(dy) * 1.5f) press(if (dx < 0) Pad.L1 else Pad.R1)
            else -> {}
        }
        if (f.circle) { circles--; if (circles == 0) set(Pad.CIRCLE, false) }
        if (stickOwner == id) { stickOwner = -1; f.dpad?.let { set(it, false) }; f.dpad = null; out.stick(0f, 0f) }
    }

    /** Lets go of everything (the screen was left, or the game paused). */
    fun reset(t: Long) {
        tick(t); fingers.values.toList().forEach { f -> fingers.remove(f.id); f.dpad?.let { set(it, false) } }
        stickOwner = -1; circles = 0; pendingCross = NONE; out.stick(0f, 0f)
        held.toList().forEach { set(it, false) }; releases.clear()
    }

    /** Moves time on: fingers that have been still or moving long enough become what they are; due releases and the pending X happen. */
    fun tick(t: Long) {
        now = maxOf(now, t)
        releases.removeAll { (at, b) -> (at <= now).also { if (it) set(b, false) } }
        if (pendingCross != NONE && pendingCross != HOLDING && pendingCross <= now) { pendingCross = NONE; press(Pad.CROSS) }
        for (f in fingers.values.toList()) {
            val age = now - f.t0
            if (f.mode == Mode.PENDING && !f.moved && age >= cfg.holdMs) {
                if (f.second) { pendingCross = NONE; press(Pad.SQUARE); f.mode = Mode.DONE }                    // a tap, then a held tap: one press of Square
                else { f.mode = Mode.HELD; f.circle = true; circles++; if (circles == 1) set(Pad.CIRCLE, true) }
            } else if (f.mode == Mode.SWIPING && age >= cfg.swipeHoldMs) {
                if (stickOwner == -1) { f.mode = Mode.STICK; stickOwner = f.id; steer(f) } else f.mode = Mode.DONE
            }
        }
    }

    /** The next time [tick] has something to do, or null. */
    fun nextDeadline(): Long? {
        var best = Long.MAX_VALUE
        releases.forEach { best = minOf(best, it.first) }
        if (pendingCross != NONE && pendingCross != HOLDING) best = minOf(best, pendingCross)
        for (f in fingers.values) {
            if (f.mode == Mode.PENDING && !f.moved) best = minOf(best, f.t0 + cfg.holdMs)
            if (f.mode == Mode.SWIPING) best = minOf(best, f.t0 + cfg.swipeHoldMs)
        }
        return best.takeIf { it != Long.MAX_VALUE }
    }

    private fun fireCross() { if (pendingCross != NONE) { pendingCross = NONE; press(Pad.CROSS) } }
    private fun press(b: Pad) { set(b, true); releases += (now + cfg.pulseMs) to b }
    private fun set(b: Pad, down: Boolean) { if (down) { if (held.add(b)) out.button(b, true) } else if (held.remove(b)) out.button(b, false) }

    private fun steer(f: Finger) {
        val dx = f.x - f.x0; val dy = f.y - f.y0; val mag = hypot(dx, dy)
        val k = if (mag > cfg.stickRadius) cfg.stickRadius / mag else 1f
        if (stickOwner == -1) stickOwner = f.id
        if (stickOwner != f.id) return
        out.stick(dx * k / cfg.stickRadius, dy * k / cfg.stickRadius)
        val dir: Pad? = if (mag < cfg.deadZone) null else {
            val horizontal = when (f.dpad) { Pad.LEFT, Pad.RIGHT -> abs(dx) * 1.25f >= abs(dy); Pad.UP, Pad.DOWN -> abs(dx) > abs(dy) * 1.25f; else -> abs(dx) >= abs(dy) }
            if (horizontal) (if (dx < 0) Pad.LEFT else Pad.RIGHT) else (if (dy < 0) Pad.UP else Pad.DOWN)
        }
        if (dir != f.dpad) { f.dpad?.let { set(it, false) }; dir?.let { set(it, true) }; f.dpad = dir }
    }

    private companion object {
        const val NONE = -1L
        const val HOLDING = Long.MAX_VALUE        // a second touch is deciding what the first tap was
    }
}
