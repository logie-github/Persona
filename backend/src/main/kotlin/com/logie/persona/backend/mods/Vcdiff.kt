package com.logie.persona.backend.mods

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A decoder for VCDIFF (RFC 3284), the format of the `.xdelta` patches that Persona's EBOOT mods ship as: it rebuilds the patched file from the
 * original and the patch. It reads the default code table, windows with a source or target segment, and the address cache. A patch that
 * compresses its sections with a secondary compressor (xdelta's `-S`) or brings its own code table is refused with a clear message.
 */
object Vcdiff {
    private const val NOOP = 0; private const val ADD = 1; private const val RUN = 2; private const val COPY = 3

    /** index -> (type1, size1, mode1, type2, size2, mode2), as in RFC 3284 section 5.6. */
    private val table: Array<IntArray> = run {
        val t = ArrayList<IntArray>()
        t += intArrayOf(RUN, 0, 0, NOOP, 0, 0)
        for (s in 0..17) t += intArrayOf(ADD, s, 0, NOOP, 0, 0)
        for (m in 0..8) { t += intArrayOf(COPY, 0, m, NOOP, 0, 0); for (s in 4..18) t += intArrayOf(COPY, s, m, NOOP, 0, 0) }
        for (m in 0..5) for (a in 1..4) for (c in 4..6) t += intArrayOf(ADD, a, 0, COPY, c, m)
        for (m in 6..8) for (a in 1..4) t += intArrayOf(ADD, a, 0, COPY, 4, m)
        for (m in 0..8) t += intArrayOf(COPY, 4, m, ADD, 1, 0)
        check(t.size == 256) { "code table is ${t.size} entries" }
        t.toTypedArray()
    }

    private class Reader(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun byte(): Int { if (p >= end) throw IOException("The patch ends early."); return b[p++].toInt() and 0xff }
        fun varint(): Int { var v = 0L; while (true) { val c = byte(); v = (v shl 7) or (c and 0x7f).toLong(); if (c and 0x80 == 0) break; if (v > Int.MAX_VALUE) throw IOException("A number in the patch is too big.") }; return v.toInt() }
        fun skip(n: Int) { if (p + n > end) throw IOException("The patch ends early."); p += n }
    }

    /** Applies [patch] to [source]; returns the patched bytes. */
    fun apply(source: ByteArray, patch: ByteArray): ByteArray {
        val r = Reader(patch)
        if (patch.size < 5 || r.byte() != 0xD6 || r.byte() != 0xC3 || r.byte() != 0xC4) throw IOException("This isn't an xdelta patch.")
        if (r.byte() != 0) throw IOException("This patch is of a newer format than the importer reads.")
        val hdr = r.byte()
        if (hdr and 0x01 != 0) throw IOException("This patch is compressed in a way the importer doesn't read (xdelta -S); make it with -S none.")
        if (hdr and 0x02 != 0) throw IOException("This patch brings its own code table, which the importer doesn't read.")
        if (hdr and 0x04 != 0) r.skip(r.varint())                                   // the patch tool's own note
        val out = ByteArrayOutputStream(source.size)
        var produced = ByteArray(0)
        while (r.p < r.end) {
            val win = r.byte(); var segment = ByteArray(0)
            if (win and 0x03 != 0) {
                val len = r.varint(); val pos = r.varint()
                val from = if (win and 0x01 != 0) source else produced
                if (pos.toLong() + len > from.size) throw IOException("The patch asks for more of the original than there is: this isn't the file it was made for.")
                segment = from.copyOfRange(pos, pos + len)
            }
            val deltaLen = r.varint(); val deltaStart = r.p
            val targetLen = r.varint()
            if (r.byte() != 0) throw IOException("This patch compresses its sections, which the importer doesn't read.")
            val addLen = r.varint(); val instLen = r.varint(); val addrLen = r.varint()
            if (win and 0x04 != 0) r.skip(4)                                         // an Adler-32 of the window, not checked
            val data = Reader(patch, r.p, r.p + addLen); val inst = Reader(patch, data.end, data.end + instLen); val addr = Reader(patch, inst.end, inst.end + addrLen)
            if (addr.end > patch.size) throw IOException("The patch ends early.")
            val target = ByteArray(targetLen); var tp = 0
            val near = IntArray(4); var next = 0; val same = IntArray(768)
            fun decodeAddr(mode: Int, here: Int): Int {
                val a: Int = when {
                    mode == 0 -> addr.varint()
                    mode == 1 -> here - addr.varint()
                    mode in 2..5 -> near[mode - 2] + addr.varint()
                    else -> same[(mode - 6) * 256 + addr.byte()]
                }
                near[next] = a; next = (next + 1) % 4; same[a % 768] = a
                return a
            }
            fun run(type: Int, size0: Int, mode: Int) {
                if (type == NOOP) return
                val size = if (size0 == 0 && type != NOOP) inst.varint() else size0
                if (tp + size > targetLen) throw IOException("The patch writes past the end of a window.")
                when (type) {
                    ADD -> { for (i in 0 until size) target[tp + i] = data.byte().toByte() }
                    RUN -> { val v = data.byte().toByte(); for (i in 0 until size) target[tp + i] = v }
                    COPY -> {
                        val here = segment.size + tp; val a = decodeAddr(mode, here)
                        for (i in 0 until size) {
                            val src = a + i
                            target[tp + i] = if (src < segment.size) segment[src] else target[src - segment.size].also { if (src - segment.size >= tp + i) throw IOException("A copy reads what isn't written yet.") }
                        }
                    }
                }
                tp += size
            }
            while (inst.p < inst.end) { val e = table[inst.byte()]; run(e[0], e[1], e[2]); run(e[3], e[4], e[5]) }
            if (tp != targetLen) throw IOException("A window of the patch came out ${tp} bytes instead of $targetLen.")
            out.write(target); produced = out.toByteArray()
            r.p = deltaStart + deltaLen
        }
        return out.toByteArray()
    }
}
