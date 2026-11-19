package com.logie.persona.backend.psp

/** A PSP game's PARAM.SFO: key/value pairs (strings and numbers) in the PSF container. */
class ParamSfo(val values: Map<String, Any>) {
    val title: String? get() = values["TITLE"] as? String
    val discId: String? get() = values["DISC_ID"] as? String

    companion object {
        fun parse(b: ByteArray): ParamSfo {
            require(b.size >= 20 && b[0].toInt() == 0 && String(b, 1, 3, Charsets.ISO_8859_1) == "PSF") { "Not a PARAM.SFO." }
            fun u16(o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
            fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
            val keys = u32(8); val data = u32(12); val count = u32(16); val out = LinkedHashMap<String, Any>()
            for (i in 0 until count) {
                val e = 20 + i * 16; val kStart = keys + u16(e); var kEnd = kStart; while (kEnd < b.size && b[kEnd].toInt() != 0) kEnd++
                val key = String(b, kStart, kEnd - kStart, Charsets.UTF_8); val fmt = u16(e + 2); val len = u32(e + 4); val off = data + u32(e + 12)
                out[key] = when (fmt) {
                    0x0004, 0x0204 -> { var end = off; while (end < off + len && b[end].toInt() != 0) end++; String(b, off, end - off, Charsets.UTF_8) }
                    0x0404 -> u32(off).toLong()
                    else -> continue
                }
            }
            return ParamSfo(out)
        }
    }
}

/** What a disc image turned out to be. */
data class PspDisc(val discId: String, val title: String, val isPersona: Boolean)

object PspDiscs {
    /** Shin Megami Tensei: Persona (the PSP remake): its US disc id; other regions are told by their title. */
    val PERSONA_IDS = setOf("ULUS10432")

    /** True for "Shin Megami Tensei: Persona" / "Persona" / ペルソナ, in any case, and not for the later games ("Persona 3 Portable"...). */
    fun isPersonaTitle(title: String): Boolean {
        val t = title.lowercase().replace(Regex("[\\u2010-\\u2015]"), "-").replace(Regex("\\s+"), " ").trim()
        return Regex("^(shin megami tensei\\s*[:\\-]?\\s*)?persona$").matches(t) || t == "ペルソナ" || t == "shin megami tensei: persona (psp)"
    }

    /** Reads [bytes]'s PARAM.SFO. Throws when it isn't a PSP disc. */
    fun identify(bytes: DiscBytes): PspDisc {
        val files = DiscFiles(bytes)
        val sfo = files.find("PSP_GAME/PARAM.SFO") ?: throw IllegalArgumentException("This isn't a PSP game disc.")
        val p = ParamSfo.parse(files.read(sfo)); val id = (p.discId ?: "").replace("-", "").uppercase(); val title = p.title ?: ""
        return PspDisc(id, title, id in PERSONA_IDS || isPersonaTitle(title))
    }
}
