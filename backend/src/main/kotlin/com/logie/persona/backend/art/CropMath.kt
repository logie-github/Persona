package com.logie.persona.backend.art

/** The arithmetic of a crop: which part of a picture a frame of a given shape shows. */
object CropMath {
    class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * The part of an [iw] x [ih] picture a frame of [aspect] (width over height) shows at [zoom] (1 is the most of the picture the frame can hold, bigger zooms
     * in), centred on ([cx], [cy]) in the picture's own pixels, kept inside the picture.
     */
    fun rect(iw: Int, ih: Int, aspect: Float, zoom: Float, cx: Float, cy: Float): Rect {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        val baseW: Float; val baseH: Float
        if (iw.toFloat() / ih > aspect) { baseH = ih.toFloat(); baseW = baseH * aspect } else { baseW = iw.toFloat(); baseH = baseW / aspect }
        val w = (baseW / z).coerceAtLeast(1f); val h = (baseH / z).coerceAtLeast(1f)
        val x = (cx - w / 2f).coerceIn(0f, (iw - w).coerceAtLeast(0f))
        val y = (cy - h / 2f).coerceIn(0f, (ih - h).coerceAtLeast(0f))
        return Rect(x.toInt(), y.toInt(), w.toInt().coerceIn(1, iw), h.toInt().coerceIn(1, ih))
    }

    const val MAX_ZOOM = 6f
}
