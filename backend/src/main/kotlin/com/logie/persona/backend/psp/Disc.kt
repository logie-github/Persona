package com.logie.persona.backend.psp

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.Inflater

/** Random access to the bytes of a disc image, whether it's a plain ISO or a compressed CSO. */
interface DiscBytes : Closeable {
    val length: Long
    /** Reads up to [len] bytes at [pos] into [buf] at [off]; returns how many were read (0 at the end). */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int

    fun readFully(pos: Long, len: Int): ByteArray {
        val out = ByteArray(len); var done = 0
        while (done < len) { val n = read(pos + done, out, done, len - done); if (n <= 0) throw IOException("The image ends early (at ${pos + done})."); done += n }
        return out
    }

    companion object {
        /** Opens [file] as a CSO ("CISO" header) or else as a plain ISO. */
        fun open(file: File): DiscBytes {
            val head = ByteArray(4); RandomAccessFile(file, "r").use { it.read(head) }
            return if (String(head, Charsets.ISO_8859_1) == "CISO") CsoDisc(file) else IsoDisc(file)
        }
    }
}

class IsoDisc(file: File) : DiscBytes {
    private val raf = RandomAccessFile(file, "r")
    override val length: Long = raf.length()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int { raf.seek(pos); return raf.read(buf, off, len) }
    override fun close() = raf.close()
}

/** A CSO: blocks of the image, each deflated (raw) or stored, found through an index table. */
class CsoDisc(file: File) : DiscBytes {
    private val raf = RandomAccessFile(file, "r")
    private val blockSize: Int
    private val align: Int
    private val index: IntArray
    override val length: Long
    private var cachedBlock = -1
    private var cache = ByteArray(0)

    init {
        val h = ByteArray(24); raf.seek(0); raf.readFully(h)
        require(String(h, 0, 4, Charsets.ISO_8859_1) == "CISO") { "Not a CSO." }
        length = le64(h, 8); blockSize = le32(h, 16).toInt(); align = h[21].toInt()
        require(blockSize > 0 && length >= 0) { "A damaged CSO." }
        val blocks = ((length + blockSize - 1) / blockSize).toInt()
        val raw = ByteArray((blocks + 1) * 4); raf.seek(24); raf.readFully(raw)
        index = IntArray(blocks + 1) { le32(raw, it * 4).toInt() }
    }

    private fun block(n: Int): ByteArray {
        if (n == cachedBlock) return cache
        val entry = index[n]; val plain = entry < 0
        val start = (entry.toLong() and 0x7fffffffL) shl align
        val end = (index[n + 1].toLong() and 0x7fffffffL) shl align
        val packed = ByteArray((end - start).toInt()); raf.seek(start); raf.readFully(packed)
        val out = if (plain) packed.copyOf(blockSize) else {
            val inf = Inflater(true); inf.setInput(packed); val o = ByteArray(blockSize); var n2 = 0
            while (n2 < blockSize && !inf.finished()) { val r = inf.inflate(o, n2, blockSize - n2); if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break; n2 += r }
            inf.end(); o
        }
        cachedBlock = n; cache = out; return out
    }

    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= length) return 0
        val n = (pos / blockSize).toInt(); val inBlock = (pos % blockSize).toInt()
        val b = block(n); val take = minOf(len, blockSize - inBlock, (length - pos).toInt())
        System.arraycopy(b, inBlock, buf, off, take); return take
    }

    override fun close() = raf.close()
    private fun le32(b: ByteArray, o: Int) = (b[o].toLong() and 0xff) or ((b[o + 1].toLong() and 0xff) shl 8) or ((b[o + 2].toLong() and 0xff) shl 16) or ((b[o + 3].toLong() and 0xff) shl 24)
    private fun le64(b: ByteArray, o: Int) = le32(b, o) or (le32(b, o + 4) shl 32)
}

/** A file or folder of the disc. */
data class DiscEntry(val path: String, val isDir: Boolean, val lba: Long, val size: Long)

/** The ISO 9660 file system of a disc image (as UMDs have it: no Joliet, upper-case names), read in place. */
class DiscFiles(private val bytes: DiscBytes) {
    private val sector = 2048
    private val root: DiscEntry

    init {
        val pvd = bytes.readFully(16L * sector, sector)
        require(pvd[0].toInt() == 1 && String(pvd, 1, 5, Charsets.ISO_8859_1) == "CD001") { "This isn't a disc image." }
        val rec = pvd.copyOfRange(156, 190)
        root = DiscEntry("", true, le32(rec, 2), le32(rec, 10))
    }

    /** Every file and folder, paths from the disc's root with "/" (no leading slash), in directory order. */
    fun entries(): List<DiscEntry> {
        val out = ArrayList<DiscEntry>(); walk(root, out); return out
    }

    private fun walk(dir: DiscEntry, out: MutableList<DiscEntry>) {
        val data = bytes.readFully(dir.lba * sector, dir.size.toInt()); var p = 0
        while (p < data.size) {
            val len = data[p].toInt() and 0xff
            if (len == 0) { p = (p / sector + 1) * sector; continue }          // records don't cross a sector: the rest of it is empty
            val flags = data[p + 25].toInt(); val nameLen = data[p + 32].toInt() and 0xff
            val rawName = String(data, p + 33, nameLen, Charsets.ISO_8859_1)
            if (!(nameLen == 1 && (data[p + 33].toInt() == 0 || data[p + 33].toInt() == 1))) {
                val name = rawName.substringBefore(';').removeSuffix(".")
                val e = DiscEntry(if (dir.path.isEmpty()) name else dir.path + "/" + name, flags and 2 != 0, le32(data, p + 2), le32(data, p + 10))
                out += e; if (e.isDir) walk(e, out)
            }
            p += len
        }
    }

    fun find(path: String): DiscEntry? = entries().firstOrNull { it.path.equals(path, ignoreCase = true) }

    fun read(e: DiscEntry): ByteArray = bytes.readFully(e.lba * sector, e.size.toInt())

    /** Copies [e] to [target] (folders created); returns how many bytes. */
    fun extract(e: DiscEntry, target: File, onBytes: (Long) -> Unit = {}): Long {
        target.parentFile?.mkdirs(); var left = e.size; var pos = e.lba * sector; val buf = ByteArray(1 shl 18)
        target.outputStream().buffered(1 shl 20).use { out ->
            while (left > 0) { val n = bytes.read(pos, buf, 0, minOf(left, buf.size.toLong()).toInt()); if (n <= 0) throw IOException("The image ends early."); out.write(buf, 0, n); pos += n; left -= n; onBytes(n.toLong()) }
        }
        return e.size
    }

    private fun le32(b: ByteArray, o: Int): Long = (b[o].toLong() and 0xff) or ((b[o + 1].toLong() and 0xff) shl 8) or ((b[o + 2].toLong() and 0xff) shl 16) or ((b[o + 3].toLong() and 0xff) shl 24)
}
