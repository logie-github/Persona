package com.logie.persona.backend.mods

/** Where a mod's file goes. */
enum class Part {
    /** A PPSSPP texture pack file: into the game's texture folder (under the mod's own sub-folder). */
    TEXTURES,
    /** The mod's cheat list. */
    CHEATS,
    /** A file that replaces one on the disc (a movie, a data file). */
    DISC_FILE,
    /** An xdelta patch for the game's EBOOT.BIN. */
    EBOOT_PATCH,
}

data class PlannedFile(val from: String, val part: Part, val dest: String, val size: Long)

/** A texture pack's folder inside an archive; a mod with several (colour choices, button styles) offers them as [variants]. */
data class ModVariant(val name: String, val files: List<PlannedFile>)

/** What installing a mod does. [shared] is applied whichever variant is chosen; [skipped] lists files that nothing could be done with (and why). */
data class ModPlan(val variants: List<ModVariant>, val shared: List<PlannedFile>, val skipped: List<Pair<String, String>>) {
    val hasTextures get() = variants.isNotEmpty()
    val isEmpty get() = variants.isEmpty() && shared.isEmpty()
    fun files(variant: Int = 0): List<PlannedFile> = (variants.getOrNull(variant)?.files ?: emptyList()) + shared
}

object ModPlanner {
    private val noise = Regex("(?i)(^|/)(desktop\\.ini|thumbs\\.db|package\\.xml|preview\\.(png|jpg)|readme[^/]*\\.txt|credits[^/]*\\.txt|changelog[^/]*\\.txt|[^/]*\\.md|\\.ds_store)$")

    /**
     * Sorts the files of an archive: texture packs (a folder with a textures.ini, or one named for the disc), cheat lists, files that replace
     * the disc's, and EBOOT patches. [discFiles] are the disc's file paths (upper or lower case, as on the disc), used to find where a loose
     * file belongs by its name.
     */
    fun plan(entries: List<Pair<String, Long>>, discId: String, discFiles: Collection<String>, archiveName: String = ""): ModPlan {
        val files = entries.map { ModArchive.normal(it.first) to it.second }
        val lower = files.map { it.first.lowercase() to it }
        val skipped = ArrayList<Pair<String, String>>(); val taken = HashSet<String>()

        // 1. texture packs: the folders that hold a textures.ini
        val roots = lower.filter { it.first.substringAfterLast('/') == "textures.ini" }.map { it.second.first.substringBeforeLast('/', "") }.distinct().sortedBy { it.length }
            .let { all -> all.filter { r -> all.none { o -> o != r && o.isNotEmpty() && r.startsWith("$o/") } || r.isEmpty() } }
        var packRoots = roots
        if (packRoots.isEmpty()) {                                       // no ini: a pack of hash-named pictures, in a folder named for the disc
            val id = discId.lowercase()
            packRoots = lower.map { it.first }.mapNotNull { p -> val parts = p.split('/'); val i = parts.indexOf(id); if (i >= 0) files.first { it.first.lowercase() == p }.first.split('/').take(i + 1).joinToString("/") else null }.distinct()
        }
        val variants = packRoots.map { root ->
            val pre = if (root.isEmpty()) "" else "$root/"
            val inRoot = files.filter { (p, _) -> p.startsWith(pre, ignoreCase = true) && !noise.containsMatchIn(p) }
            taken += inRoot.map { it.first }
            val name = root.split('/').filter { !it.equals(discId, true) && !it.equals("TEXTURES", true) }.joinToString(" / ").ifEmpty { "Default" }
            ModVariant(name, inRoot.map { (p, s) -> PlannedFile(p, Part.TEXTURES, p.substring(pre.length), s) })
        }

        val shared = ArrayList<PlannedFile>()
        val eboot = (archiveName + " " + files.joinToString(" ") { it.first }).contains("eboot", ignoreCase = true)
        val discLower = discFiles.map { it.replace('\\', '/') }.groupBy { it.substringAfterLast('/').lowercase() }
        for ((p, s) in files) {
            if (p in taken) continue
            val l = p.lowercase(); val base = p.substringAfterLast('/')
            when {
                noise.containsMatchIn(p) -> {}
                l.endsWith(".ini") && base.equals("${discId}.ini", true) && "cheat" in l.substringBeforeLast('/') -> shared += PlannedFile(p, Part.CHEATS, "$discId.ini", s)
                l.endsWith(".xdelta") || l.endsWith(".xdelta3") || l.endsWith(".vcdiff") ->
                    if (eboot) shared += PlannedFile(p, Part.EBOOT_PATCH, "PSP_GAME/SYSDIR/EBOOT.BIN", s) else skipped += p to "a patch for something other than the game's program (it would need the whole disc)"
                else -> {
                    val parts = p.split('/'); val i = parts.indexOfFirst { it.equals("PSP_GAME", true) }
                    val j = parts.indexOfFirst { it.equals("USRDIR", true) || it.equals("SYSDIR", true) }
                    when {
                        i >= 0 -> shared += PlannedFile(p, Part.DISC_FILE, parts.drop(i).joinToString("/"), s)
                        j >= 0 -> shared += PlannedFile(p, Part.DISC_FILE, "PSP_GAME/" + parts.drop(j).joinToString("/"), s)
                        discLower[base.lowercase()]?.size == 1 -> shared += PlannedFile(p, Part.DISC_FILE, discLower.getValue(base.lowercase()).single(), s)
                        else -> skipped += p to "no place for it on the disc"
                    }
                }
            }
        }
        return ModPlan(variants, shared, skipped)
    }
}

/** Merges texture packs' textures.ini files into one: PPSSPP reads a single textures.ini per game. Each pack lives in its own folder; paths are prefixed with it. */
object TextureIni {
    class Ini(val sections: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap())

    fun parse(text: String): Ini {
        val ini = Ini(); var cur: LinkedHashMap<String, String>? = null
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("[") && line.endsWith("]")) { cur = ini.sections.getOrPut(line.substring(1, line.length - 1).trim().lowercase()) { LinkedHashMap() }; continue }
            val eq = line.indexOf('='); if (eq <= 0 || cur == null) continue
            cur[line.substring(0, eq).trim().lowercase()] = line.substring(eq + 1).trim()
        }
        return ini
    }

    /** [packs]: (folder the pack lives in, its textures.ini text), in priority order (a later pack wins where two replace the same texture). */
    fun merge(packs: List<Pair<String, String>>): String {
        val out = Ini()
        for ((folder, text) in packs) {
            val ini = parse(text)
            for ((name, kv) in ini.sections) {
                val target = out.sections.getOrPut(name) { LinkedHashMap() }
                for ((k, v) in kv) {
                    when (name) {
                        "options" -> target.putIfAbsent(k, v)                                          // the first pack's hashing options stand
                        "hashes" -> target[k] = if (v.equals("ignore", true)) v else "$folder/$v".replace("//", "/")
                        else -> target[k] = v
                    }
                }
            }
        }
        return out.sections.entries.joinToString("\n") { (n, kv) -> "[$n]\n" + kv.entries.joinToString("") { (k, v) -> "$k = $v\n" } }
    }
}
