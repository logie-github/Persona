package com.logie.persona.backend.psp

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

/** Builds tiny, valid discs for the tests: ISO 9660 (as UMDs have it), PSF files, and CSO wrappers. */
object TestDiscs {
    fun sfo(vararg kv: Pair<String, Any>): ByteArray {
        val keys = ByteArrayOutputStream(); val data = ByteArrayOutputStream(); val entries = ByteArrayOutputStream()
        kv.forEach { (k, v) ->
            val kOff = keys.size(); keys.write(k.toByteArray()); keys.write(0)
            val dOff = data.size(); val isNum = v is Int
            val bytes = if (isNum) le32(v as Int) else v.toString().toByteArray() + byteArrayOf(0)
            data.write(bytes)
            entries.write(le16(kOff)); entries.write(le16(if (isNum) 0x0404 else 0x0204)); entries.write(le32(bytes.size)); entries.write(le32(bytes.size)); entries.write(le32(dOff))
        }
        val keyStart = 20 + entries.size(); val dataStart = keyStart + keys.size()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 'P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte())); out.write(le32(0x101)); out.write(le32(keyStart)); out.write(le32(dataStart)); out.write(le32(kv.size))
        out.write(entries.toByteArray()); out.write(keys.toByteArray()); out.write(data.toByteArray()); return out.toByteArray()
    }

    /** files: path -> bytes; every parent folder is made. Folder records are packed one sector each; file data follows in path order. */
    fun iso(files: Map<String, ByteArray>): ByteArray {
        val sector = 2048
        val dirs = linkedSetOf(""); files.keys.forEach { p -> var d = p.substringBeforeLast('/', ""); while (d.isNotEmpty()) { dirs += d; d = d.substringBeforeLast('/', "") } }
        val dirList = dirs.toList().sortedBy { it.count { c -> c == '/' } }
        val dirLba = HashMap<String, Int>(); var next = 17
        dirList.forEach { dirLba[it] = next++ }
        val fileLba = HashMap<String, Int>(); files.keys.sorted().forEach { fileLba[it] = next; next += (files.getValue(it).size + sector - 1) / sector }
        val image = ByteArray(next * sector)
        fun rec(name: ByteArray, lba: Int, size: Int, dir: Boolean): ByteArray {
            val len = 33 + name.size + (if (name.size % 2 == 0) 1 else 0); val r = ByteArray(len)
            r[0] = len.toByte(); putLe(r, 2, lba); putLe(r, 10, size); r[25] = if (dir) 2 else 0; r[32] = name.size.toByte(); name.copyInto(r, 33); return r
        }
        val pvd = ByteArray(sector); pvd[0] = 1; "CD001".toByteArray().copyInto(pvd, 1); pvd[6] = 1
        rec(byteArrayOf(0), dirLba.getValue(""), sector, true).copyInto(pvd, 156); pvd.copyInto(image, 16 * sector)
        dirList.forEach { d ->
            val buf = ByteArrayOutputStream()
            buf.write(rec(byteArrayOf(0), dirLba.getValue(d), sector, true)); buf.write(rec(byteArrayOf(1), dirLba.getValue(d.substringBeforeLast('/', "")), sector, true))
            dirs.filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == d }.forEach { sub -> buf.write(rec(sub.substringAfterLast('/').toByteArray(), dirLba.getValue(sub), sector, true)) }
            files.keys.filter { it.substringBeforeLast('/', "") == d }.forEach { f -> buf.write(rec((f.substringAfterLast('/') + ";1").toByteArray(), fileLba.getValue(f), files.getValue(f).size, false)) }
            buf.toByteArray().copyInto(image, dirLba.getValue(d) * sector)
        }
        files.forEach { (p, b) -> b.copyInto(image, fileLba.getValue(p) * sector) }
        return image
    }

    fun cso(iso: ByteArray, blockSize: Int = 2048, align: Int = 0): ByteArray {
        val blocks = (iso.size + blockSize - 1) / blockSize; val packed = ArrayList<Pair<ByteArray, Boolean>>()
        for (i in 0 until blocks) {
            val chunk = iso.copyOfRange(i * blockSize, minOf(iso.size, (i + 1) * blockSize)).copyOf(blockSize)
            val d = Deflater(9, true); d.setInput(chunk); d.finish(); val buf = ByteArray(blockSize * 2); val n = d.deflate(buf); d.end()
            packed += if (n < blockSize) Pair(buf.copyOf(n), false) else Pair(chunk, true)
        }
        val headerAndIndex = 24 + (blocks + 1) * 4; val out = ByteArrayOutputStream(); val offsets = IntArray(blocks + 1)
        var pos = headerAndIndex.toLong()
        val body = ByteArrayOutputStream()
        val unit = 1 shl align
        packed.forEachIndexed { i, (b, plain) ->
            offsets[i] = ((pos shr align).toInt()) or (if (plain) Int.MIN_VALUE else 0); body.write(b); pos += b.size
            val pad = ((unit - pos % unit) % unit).toInt(); body.write(ByteArray(pad)); pos += pad        // blocks start on the alignment
        }
        offsets[blocks] = (pos shr align).toInt()
        out.write("CISO".toByteArray()); out.write(le32(24)); out.write(le32(iso.size)); out.write(le32(0)); out.write(le32(blockSize)); out.write(byteArrayOf(1, align.toByte(), 0, 0))
        offsets.forEach { out.write(le32(it)) }; out.write(body.toByteArray()); return out.toByteArray()
    }

    fun persona(title: String = "Shin Megami Tensei: Persona", id: String = "ULUS10432", extra: Map<String, ByteArray> = emptyMap()): ByteArray =
        iso(mapOf("PSP_GAME/PARAM.SFO" to sfo("DISC_ID" to id, "TITLE" to title, "PSP_SYSTEM_VER" to 0x01), "PSP_GAME/SYSDIR/EBOOT.BIN" to "EBOOT-ORIGINAL".toByteArray(),
            "PSP_GAME/USRDIR/MOVIE/MV11.PMF" to ByteArray(5000) { it.toByte() }) + extra)

    fun file(bytes: ByteArray, name: String = "disc.iso"): File = File.createTempFile("disc", name).also { it.writeBytes(bytes); it.deleteOnExit() }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun putLe(b: ByteArray, o: Int, v: Int) { le32(v).copyInto(b, o) }
}
