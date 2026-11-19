package com.logie.persona.backend.mods

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** A mod's archive (zip or 7z), read the same way. Paths are normalised: "/" between parts, no leading "./" or "/". */
object ModArchive {
    fun normal(path: String): String = path.replace('\\', '/').trimStart('/').removePrefix("./")

    private fun is7z(f: File): Boolean { val b = ByteArray(6); f.inputStream().use { it.read(b) }; return b[0] == '7'.code.toByte() && b[1] == 'z'.code.toByte() && b[2] == 0xBC.toByte() && b[3] == 0xAF.toByte() }
    private fun isRar(f: File): Boolean { val b = ByteArray(4); f.inputStream().use { it.read(b) }; return String(b, Charsets.ISO_8859_1) == "Rar!" }
    private fun notReadable(f: File): Nothing = throw IOException(if (isRar(f)) "This mod is a RAR archive, which the app can't open yet." else "This isn't a zip or 7z archive.")
    private fun isZip(f: File): Boolean { val b = ByteArray(2); f.inputStream().use { it.read(b) }; return b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }

    /** Every file in [file] with its size (folders left out). */
    fun list(file: File): List<Pair<String, Long>> = when {
        is7z(file) -> SevenZFile.builder().setFile(file).get().use { z -> generateSequence { z.nextEntry }.filter { !it.isDirectory }.map { normal(it.name) to it.size }.toList() }
        isZip(file) -> ZipFile(file).use { z -> z.entries().asSequence().filter { !it.isDirectory }.map { normal(it.name) to it.size }.toList() }
        else -> notReadable(file)
    }

    /** Copies out every file for which [target] gives a destination (null: skip it); returns how many were written. */
    fun extract(file: File, target: (String) -> File?, onBytes: (Long) -> Unit = {}): Int {
        var n = 0
        fun copy(path: String, open: () -> java.io.InputStream) {
            val t = target(path) ?: return; t.parentFile?.mkdirs()
            open().use { inp -> t.outputStream().buffered(1 shl 20).use { out -> val buf = ByteArray(1 shl 16); while (true) { val r = inp.read(buf); if (r < 0) break; out.write(buf, 0, r); onBytes(r.toLong()) } } }; n++
        }
        when {
            is7z(file) -> SevenZFile.builder().setFile(file).get().use { z ->
                while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) copy(normal(e.name)) { z.getInputStream(e) } }
            }
            isZip(file) -> ZipFile(file).use { z -> z.entries().asSequence().filter { !it.isDirectory }.forEach { e -> copy(normal(e.name)) { z.getInputStream(e) } } }
            else -> notReadable(file)
        }
        return n
    }
}
