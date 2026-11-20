package com.logie.persona.backend.art

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The arithmetic of the pictures the person sets in a game's menu (a person, a logo), on ARGB pixels (`0xAARRGGBB`, row after row): the crop, a plain
 * background cut away, the empty margins trimmed (so the person stands on the menu's bottom edge), and each game's look ([GameLook]).
 */
object MenuPicture {
    /** A crop as fractions of the picture (left, top, right, bottom, each 0 to 1). */
    data class Crop(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun clamped(minSize: Float = 0.05f): Crop {
            val l2 = l.coerceIn(0f, 1f - minSize); val t2 = t.coerceIn(0f, 1f - minSize)
            return Crop(l2, t2, r.coerceIn(l2 + minSize, 1f), b.coerceIn(t2 + minSize, 1f))
        }
        fun encode() = "$l,$t,$r,$b"
        /** The crop in [w] x [h] pixels: x, y, width, height (at least 1 x 1, inside the picture). */
        fun pixels(w: Int, h: Int): IntArray {
            val c = clamped()
            val x = floor(c.l * w).toInt().coerceIn(0, w - 1); val y = floor(c.t * h).toInt().coerceIn(0, h - 1)
            val x2 = (c.r * w).toInt().coerceIn(x + 1, w); val y2 = (c.b * h).toInt().coerceIn(y + 1, h)
            return intArrayOf(x, y, x2 - x, y2 - y)
        }
        companion object {
            val WHOLE = Crop(0f, 0f, 1f, 1f)
            fun decode(text: String?): Crop? = text?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let { Crop(it[0], it[1], it[2], it[3]).clamped() }
        }
    }

    private fun alpha(p: Int) = p ushr 24
    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF

    /** How far apart two colours are: the largest difference of their channels. */
    private fun distance(p: Int, q: Int) = max(abs(red(p) - red(q)), max(abs(green(p) - green(q)), abs(blue(p) - blue(q))))

    /**
     * The background's colour: what most of the border's opaque pixels are (to a sixteenth of each channel, then their mean), when they are
     * at least [share] of the border; null when the border is mostly transparent already or has no one colour (a photograph, a scene).
     */
    fun backgroundColour(px: IntArray, w: Int, h: Int, share: Float = 0.4f): Int? {
        val border = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 1 until h - 1) { border += px[y * w]; border += px[y * w + w - 1] }
        if (border.count { alpha(it) < 128 } > border.size / 2) return null
        val buckets = HashMap<Int, MutableList<Int>>()
        for (p in border) if (alpha(p) >= 128) buckets.getOrPut(((red(p) shr 4) shl 8) or ((green(p) shr 4) shl 4) or (blue(p) shr 4)) { ArrayList() } += p
        // Neighbouring buckets count together (a colour on a bucket's edge, JPEG's noise).
        var best: List<Int>? = null
        var bestCount = 0
        for ((key, members) in buckets) {
            var count = 0
            val r = key shr 8; val g = (key shr 4) and 0xF; val b = key and 0xF
            for (dr in -1..1) for (dg in -1..1) for (db in -1..1) {
                val k = ((r + dr) shl 8) or ((g + dg) shl 4) or (b + db)
                if (r + dr in 0..15 && g + dg in 0..15 && b + db in 0..15) count += buckets[k]?.size ?: 0
            }
            if (count > bestCount || (count == bestCount && members.size > (best?.size ?: 0))) { bestCount = count; best = members }
        }
        if (best == null || bestCount < border.size * share) return null
        val n = best.size
        return (0xFF shl 24) or ((best.sumOf { red(it) } / n) shl 16) or ((best.sumOf { green(it) } / n) shl 8) or (best.sumOf { blue(it) } / n)
    }

    /**
     * A plain background cut away: every pixel like the background's colour ([backgroundColour], within [tolerance]) that the border reaches
     * through pixels like it becomes transparent; the pixels at the cut's edge are softened (partly transparent the closer they are to the
     * background, so no fringe of it stays). Pictures without a plain background are left as they are. Returns how many pixels were cut.
     */
    fun cutBackground(px: IntArray, w: Int, h: Int, tolerance: Int = 40): Int {
        if (w < 3 || h < 3) return 0
        val bg = backgroundColour(px, w, h) ?: return 0
        val like = BooleanArray(w * h) { alpha(px[it]) < 16 || distance(px[it], bg) <= tolerance }
        val cut = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun seed(i: Int) { if (like[i] && !cut[i]) { cut[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        for (i in 0 until w * h) if (cut[i]) px[i] = px[i] and 0x00FFFFFF
        // The edge: a kept pixel beside a cut one keeps less of itself the nearer its colour is to the background.
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (cut[i]) continue
            var touches = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h && cut[ny * w + nx]) touches = true
            }
            if (!touches) continue
            val near = (distance(px[i], bg) - tolerance).coerceAtLeast(0) / (2f * tolerance)
            val keep = (0.45f + 0.55f * near).coerceAtMost(1f)
            px[i] = ((alpha(px[i]) * keep).toInt() shl 24) or (px[i] and 0x00FFFFFF)
        }
        return tail
    }

    /** The smallest box holding every pixel that shows (alpha above [threshold]): x, y, width, height; null for a picture with nothing in it. */
    fun trim(px: IntArray, w: Int, h: Int, threshold: Int = 10): IntArray? {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (alpha(px[y * w + x]) > threshold) {
            if (x < x0) x0 = x; if (x > x1) x1 = x
            if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        return intArrayOf(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** The pixels of [rect] (x, y, width, height) of a [w]-wide picture. */
    fun copy(px: IntArray, w: Int, rect: IntArray): IntArray {
        val (x, y, cw, ch) = rect.toList()
        val out = IntArray(cw * ch)
        for (row in 0 until ch) System.arraycopy(px, (y + row) * w + x, out, row * cw, cw)
        return out
    }
}

/**
 * Each game's look for a person's picture, so whoever is put in a menu sits in it as the game's own would: the picture's light and dark mapped
 * onto the game's colours (a gradient map from its darkest to its lightest, mixed with the picture's own colours so faces stay faces), its contrast,
 * and the game's texture.
 *
 * - Persona 1: violet night (deep violet, lavender, moonlight).
 * - Persona 2: crimson and dusk (near black, crimson, rose, warm white), harder.
 * - Persona 3: deep water (navy, sea blue, cyan, white), darker towards the feet.
 * - Persona 4: the TV's yellow (dark brown, amber, yellow, cream) with scan lines.
 * - Persona 5: black, red and white, hard, with a halftone in the shadows.
 */
object GameLook {
    enum class Texture { NONE, DEPTH, SCANLINES, HALFTONE }

    class Look(val stops: IntArray, val mix: Float, val contrast: Float, val texture: Texture)

    fun of(number: Int): Look = when (number) {
        1 -> Look(intArrayOf(0x120822, 0x4E2C84, 0xB9A3F0, 0xFAF6FF), 0.42f, 1.06f, Texture.NONE)
        2 -> Look(intArrayOf(0x0E0306, 0x6B1F2A, 0xD69A86, 0xFFF4EA), 0.42f, 1.14f, Texture.NONE)
        3 -> Look(intArrayOf(0x03102E, 0x1C5FB8, 0x8FD8FF, 0xF2FBFF), 0.34f, 1.06f, Texture.DEPTH)
        4 -> Look(intArrayOf(0x1A1206, 0x8A5A0C, 0xF2C53D, 0xFFF8DC), 0.30f, 1.08f, Texture.SCANLINES)
        else -> Look(intArrayOf(0x000000, 0x9A0B1F, 0xF0E6E6, 0xFFFFFF), 0.40f, 1.25f, Texture.HALFTONE)
    }

    /** The gradient map: 256 colours from the darkest stop to the lightest, the stops evenly spaced. */
    fun ramp(stops: IntArray): IntArray = IntArray(256) { i ->
        val f = i / 255f * (stops.size - 1)
        val k = min(floor(f).toInt(), stops.size - 2)
        val t = f - k
        val a = stops[k]; val b = stops[k + 1]
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Game [number]'s look over the picture, in place; [strength] from 0 (none) through 1 (the game's own) to 2 (twice as much). Transparent pixels stay as they are. */
    fun apply(px: IntArray, w: Int, h: Int, number: Int, strength: Float = 1f) {
        val look = of(number)
        val map = ramp(look.stops)
        val mix = (look.mix * strength).coerceIn(0f, 0.95f)
        val contrast = 1f + (look.contrast - 1f) * strength
        val texture = strength.coerceAtMost(1.3f)
        // The halftone's cells: about ninety across the picture's shorter side, on a 45° grid.
        val cell = max(4f, min(w, h) / 90f)
        val line = max(2, h / 320)
        val deep = 0x0B2E6B
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            var r = ((((p shr 16) and 0xFF) - 128) * contrast + 128).coerceIn(0f, 255f)
            var g = ((((p shr 8) and 0xFF) - 128) * contrast + 128).coerceIn(0f, 255f)
            var b = (((p and 0xFF) - 128) * contrast + 128).coerceIn(0f, 255f)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b)
            val m = map[lum.toInt().coerceIn(0, 255)]
            r = r * (1 - mix) + ((m shr 16) and 0xFF) * mix
            g = g * (1 - mix) + ((m shr 8) and 0xFF) * mix
            b = b * (1 - mix) + (m and 0xFF) * mix
            when (look.texture) {
                Texture.DEPTH -> {
                    val t = 0.24f * texture * y / h
                    r = r * (1 - t) + ((deep shr 16) and 0xFF) * t; g = g * (1 - t) + ((deep shr 8) and 0xFF) * t; b = b * (1 - t) + (deep and 0xFF) * t
                }
                Texture.SCANLINES -> if ((y / line) % 3 == 0) { val k = 1f - 0.12f * texture; r *= k; g *= k; b *= k }
                Texture.HALFTONE -> {
                    val l = lum / 255f
                    if (l < 0.5f) {
                        val u = (x + y) * 0.70710677f / cell; val v = (y - x) * 0.70710677f / cell
                        val du = u - floor(u) - 0.5f; val dv = v - floor(v) - 0.5f
                        val radius = 0.62f * sqrt((0.5f - l) / 0.5f)
                        if (du * du + dv * dv < radius * radius) { val k = 1f - 0.7f * texture; r *= k; g *= k; b *= k }
                    }
                }
                Texture.NONE -> {}
            }
            px[i] = (a shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }
}
