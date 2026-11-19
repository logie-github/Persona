package com.logie.persona.backend.mods

/** Where a mod's file goes. */
enum class Part {
    /** A PPSSPP texture pack file: into the game's texture folder (under the mod's own sub-folder). */
    TEXTURES,
    /** The mod's cheat list. */
    CHEATS,
    /** A file that replaces one on the disc (a movie, a data file). */
    DISC_FILE,
    /** An xdelta patch for one of the disc's files ([PlannedFile.dest]): the program (EBOOT.BIN) or any other (a sound bank, a script). */
    DISC_PATCH,
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
    /** A file named by the hash PPSSPP gives a texture (16 + 8 hex digits, with "_level" for a mip level): it needs no textures.ini line. */
    val HASH_NAMED = Regex("(?i)^[0-9a-f]{24}(_\\d+)?\\.(png|dds|zim|ktx2)$")

    /** The release a disc id belongs to: US, EU or JP (null when unknown). */
    fun regionOf(discId: String): String? = when (discId.uppercase().take(4)) {
        "ULUS", "UCUS", "NPUH", "NPUG", "NPUZ" -> "US"; "ULES", "UCES", "NPEH", "NPEG", "NPEZ" -> "EU"; "ULJM", "ULJS", "UCJS", "UCJM", "NPJH", "NPJG", "NPJJ" -> "JP"; else -> null
    }

    private val REGION_WORDS = mapOf("us" to "US", "usa" to "US", "na" to "US", "ntscu" to "US", "eu" to "EU", "eur" to "EU", "europe" to "EU", "pal" to "EU",
        "jp" to "JP", "jpn" to "JP", "japan" to "JP", "ntscj" to "JP", "japanese" to "JP")

    /** The releases a folder's name is for ("US and PAL" is both), or null when it is not a release's folder. */
    fun regionsOfFolder(name: String): Set<String>? {
        val words = name.lowercase().replace("ntsc-u", "ntscu").replace("ntsc-j", "ntscj").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.any { it !in REGION_WORDS && it != "and" && it != "version" && it != "ver" }) return null
        return words.mapNotNull { REGION_WORDS[it] }.toSet().ifEmpty { null }
    }

    fun plan(entries: List<Pair<String, Long>>, discId: String, discFiles: Collection<String>, archiveName: String = ""): ModPlan {
        val skipped = ArrayList<Pair<String, String>>(); val taken = HashSet<String>()
        // a mod made for several releases (folders named US, PAL, JP...) keeps only this disc's
        val region = regionOf(discId)
        val files = entries.map { ModArchive.normal(it.first) to it.second }.filter { (p, _) ->
            val tail = p.substringAfterLast('/').substringBeforeLast('.').split(Regex("[ _.-]+")).lastOrNull()?.lowercase()   // "ENDALL_Patch_JP"
            val other = region != null && (p.split('/').dropLast(1).any { seg -> regionsOfFolder(seg)?.let { region !in it } == true } ||
                (tail != null && REGION_WORDS[tail]?.let { it != region } == true))
            if (other) skipped += p to "for another release of the game"
            !other
        }
        val lower = files.map { it.first.lowercase() to it }

        // 1. texture packs: the folders that hold a textures.ini
        val roots = lower.filter { it.first.substringAfterLast('/') == "textures.ini" }.map { it.second.first.substringBeforeLast('/', "") }.distinct().sortedBy { it.length }
            .let { all -> all.filter { r -> all.none { o -> o != r && o.isNotEmpty() && r.startsWith("$o/") } || r.isEmpty() } }
        var packRoots = roots
        if (packRoots.isEmpty()) {                                       // no ini: a pack of hash-named pictures, in a folder named for the disc
            val id = discId.lowercase()
            packRoots = lower.map { it.first }.mapNotNull { p -> val parts = p.split('/'); val i = parts.indexOf(id); if (i >= 0) files.first { it.first.lowercase() == p }.first.split('/').take(i + 1).joinToString("/") else null }.distinct()
        }
        val hashGroups = if (packRoots.isEmpty()) files.filter { (p, _) -> HASH_NAMED.matches(p.substringAfterLast('/')) }.groupBy { it.first.substringBeforeLast('/', "") } else emptyMap()
        val variants = packRoots.map { root ->
            val pre = if (root.isEmpty()) "" else "$root/"
            val inRoot = files.filter { (p, _) -> p.startsWith(pre, ignoreCase = true) && !noise.containsMatchIn(p) }
            taken += inRoot.map { it.first }
            val name = root.split('/').filter { !it.equals(discId, true) && !it.equals("TEXTURES", true) }.joinToString(" / ").ifEmpty { "Default" }
            ModVariant(name, inRoot.map { (p, s) -> PlannedFile(p, Part.TEXTURES, p.substring(pre.length), s) })
        } + hashGroups.map { (folder, list) ->
            // no textures.ini and no folder for the disc: pictures named by their hashes, each folder of them a choice (L1/R1 or L2/R2 buttons)
            taken += list.map { it.first }
            ModVariant(folder.split('/').filter { it.isNotEmpty() }.joinToString(" / ").ifEmpty { "Default" }, list.map { (p, s) -> PlannedFile(p, Part.TEXTURES, p.substringAfterLast('/'), s) })
        }.sortedBy { it.name }

        val shared = ArrayList<PlannedFile>()
        val eboot = (archiveName + " " + files.joinToString(" ") { it.first }).contains("eboot", ignoreCase = true)
        val discLower = discFiles.map { it.replace('\\', '/') }.groupBy { it.substringAfterLast('/').lowercase() }
        for ((p, s) in files) {
            if (p in taken) continue
            val l = p.lowercase(); val base = p.substringAfterLast('/')
            when {
                noise.containsMatchIn(p) -> {}
                l.endsWith(".ini") && base.equals("${discId}.ini", true) && "cheat" in l.substringBeforeLast('/') -> shared += PlannedFile(p, Part.CHEATS, "$discId.ini", s)
                l.endsWith(".xdelta") || l.endsWith(".xdelta3") || l.endsWith(".vcdiff") -> {
                    val target = patchTarget(base, discFiles) ?: if (eboot) "PSP_GAME/SYSDIR/EBOOT.BIN" else null
                    if (target != null) shared += PlannedFile(p, Part.DISC_PATCH, target, s) else skipped += p to "a patch for a file the disc does not have"
                }
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

    /**
     * The disc file an xdelta patch is for, from the patch's own name: "SE_BOX.xdelta" patches the disc's SE_BOX.* , "ENDALL_Patch_US.xdelta" its ENDALL.* ,
     * "EBOOT.xdelta" the program. Null when no single file of the disc fits.
     */
    fun patchTarget(patchName: String, discFiles: Collection<String>): String? {
        var stem = patchName.substringBeforeLast('.').lowercase()
        if (stem.contains("eboot")) return discFiles.firstOrNull { it.endsWith("SYSDIR/EBOOT.BIN", true) } ?: "PSP_GAME/SYSDIR/EBOOT.BIN"
        val byStem = discFiles.groupBy { it.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.').lowercase() }
        repeat(4) {
            byStem[stem]?.singleOrNull()?.let { return it }
            byStem.entries.singleOrNull { (k, _) -> k == stem.substringBeforeLast('.') }?.value?.singleOrNull()?.let { return it }
            val trimmed = stem.replace(Regex("[ _.-]*(patch(ed)?|fix(ed)?|us|usa|pal|eu|jp|jpn|v?\\d+(\\.\\d+)*)$"), "")
            if (trimmed == stem || trimmed.isEmpty()) return null
            stem = trimmed
        }
        return null
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

    /** One installed pack: the folder it lives in (under the game's texture folder), its own textures.ini (if it has one) and its files (paths inside the folder). */
    class Pack(val folder: String, val ini: String?, val files: List<String> = emptyList())

    /**
     * [packs] in priority order (a later pack wins where two replace the same texture). Each pack's [hashes] lines are pointed into its folder; pictures named
     * by their hash at a pack's root (which PPSSPP finds by name only in the texture folder's own root) are listed as lines too; the first pack's [options]
     * stand, and a hash type is always named (PPSSPP refuses an ini without one).
     */
    fun merge(packs: List<Pack>): String {
        val out = Ini()
        for (pack in packs) {
            val ini = pack.ini?.let { parse(it) } ?: Ini()
            val own = LinkedHashMap<String, String>()
            for ((name, kv) in ini.sections) {
                if (name == "hashes") { for ((k, v) in kv) own[k] = if (v.equals("ignore", true) || v.isEmpty()) v else "${pack.folder}/$v".replace("//", "/"); continue }
                val target = out.sections.getOrPut(name) { LinkedHashMap() }
                for ((k, v) in kv) if (name == "options") target.putIfAbsent(k, v) else target[k] = v
            }
            for (f in pack.files) if ('/' !in f && ModPlanner.HASH_NAMED.matches(f)) own.putIfAbsent(f.substringBeforeLast('.').lowercase(), "${pack.folder}/$f")
            if (own.isNotEmpty()) out.sections.getOrPut("hashes") { LinkedHashMap() }.putAll(own)
        }
        val options = LinkedHashMap<String, String>().apply { put("version", "1"); put("hash", "quick") }
        out.sections["options"]?.let { options.putAll(it) }
        val ordered = LinkedHashMap<String, LinkedHashMap<String, String>>(); ordered["options"] = options
        out.sections.forEach { (k, v) -> if (k != "options") ordered[k] = v }
        out.sections.clear(); out.sections.putAll(ordered)
        return out.sections.entries.joinToString("\n") { (n, kv) -> "[$n]\n" + kv.entries.joinToString("") { (k, v) -> "$k = $v\n" } }
    }
}
