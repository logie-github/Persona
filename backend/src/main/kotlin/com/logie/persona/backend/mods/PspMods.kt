package com.logie.persona.backend.mods

import com.logie.persona.backend.psp.DiscBytes
import com.logie.persona.backend.psp.DiscFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class InstalledMod(
    val id: Long, val name: String, val version: String, val variant: String, val enabled: Boolean,
    val textures: Boolean, val cheats: Boolean, val discFiles: List<String>, val patches: List<String>, val note: String = "",
)

/** What came of installing a mod. */
data class ModResult(val mod: InstalledMod, val skipped: List<Pair<String, String>>)

/**
 * One game's mods: where they sit, what is on, and how the emulator's files are rebuilt from them. Everything is rebuilt from the mods that are
 * on and the untouched original disc ([isoFile]), so turning a mod off or removing it puts the game back exactly. Layout under [dir]:
 * `memstick/` (the emulator's memory stick: `PSP/TEXTURES/<id>/m<mod>/`, the merged `textures.ini`, `PSP/Cheats/<id>.ini`, saves),
 * `mods/` (each mod's cheats, disc files and patches, and `index.json`), and `tree/` (a copy of the disc's files with mods applied, only while
 * a mod needs one).
 */
class PspMods(val dir: File, private val discId: String, private val isoFile: File) {
    val memstick = File(dir, "memstick")
    private val store = File(dir, "mods")
    private val tree = File(dir, "tree")
    private val index = File(store, "index.json")
    private val textures get() = File(memstick, "PSP/TEXTURES/$discId")

    fun list(): List<InstalledMod> {
        if (!index.isFile) return emptyList()
        val a = JSONArray(index.readText())
        return List(a.length()) { i -> a.getJSONObject(i).let { o ->
            InstalledMod(o.getLong("id"), o.getString("name"), o.optString("version"), o.optString("variant"), o.optBoolean("enabled", true), o.optBoolean("textures"), o.optBoolean("cheats"),
                strings(o.optJSONArray("discFiles")), strings(o.optJSONArray("patches")), o.optString("note"))
        } }
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun save(mods: List<InstalledMod>) {
        store.mkdirs()
        val a = JSONArray(); mods.forEach { m -> a.put(JSONObject().put("id", m.id).put("name", m.name).put("version", m.version).put("variant", m.variant).put("enabled", m.enabled)
            .put("textures", m.textures).put("cheats", m.cheats).put("discFiles", JSONArray(m.discFiles)).put("patches", JSONArray(m.patches)).put("note", m.note)) }
        index.writeText(a.toString(1))
    }

    /** What installing [archive] would do, without doing it (the screen can offer the variants). */
    fun plan(archive: File, archiveName: String = archive.name): ModPlan = DiscBytes.open(isoFile).use { d ->
        ModPlanner.plan(ModArchive.list(archive), discId, DiscFiles(d).entries().filter { !it.isDir }.map { it.path }, archiveName)
    }

    /** Installs [archive] as mod [id] (replacing an earlier install of it), with [variant] of its texture packs, and rebuilds the game's files. */
    fun install(id: Long, name: String, version: String, archive: File, variant: Int = 0, archiveName: String = archive.name, onBytes: (Long) -> Unit = {}): ModResult {
        val plan = plan(archive, archiveName)
        if (plan.isEmpty) throw IOException("There was nothing in this mod that the game can use." + plan.skipped.firstOrNull()?.let { " (${it.first}: ${it.second})" }.orEmpty())
        uninstallFiles(id)
        val files = plan.files(variant.coerceIn(0, maxOf(0, plan.variants.size - 1))).associateBy { it.from }
        val modDir = File(store, "m$id"); val texDir = File(textures, "m$id")
        val patchNames = ArrayList<String>(); var cheats = false
        ModArchive.extract(archive, { path ->
            val f = files[path] ?: return@extract null
            when (f.part) {
                Part.TEXTURES -> File(texDir, f.dest)
                Part.CHEATS -> { cheats = true; File(modDir, "cheats.ini") }
                Part.DISC_FILE -> File(modDir, "disc/${f.dest}")
                Part.DISC_PATCH -> File(modDir, "patch/${patchNames.size}.xdelta").also { patchNames += f.dest }
            }
        }, onBytes)
        val tex = files.values.any { it.part == Part.TEXTURES }
        val mod = InstalledMod(id, name, version, plan.variants.getOrNull(variant)?.name.orEmpty(), true, tex, cheats,
            files.values.filter { it.part == Part.DISC_FILE }.map { it.dest }, patchNames)
        save(list().filter { it.id != id } + mod); rebuild()
        return ModResult(mod, plan.skipped)
    }

    fun setEnabled(id: Long, on: Boolean) { save(list().map { if (it.id == id) it.copy(enabled = on) else it }); rebuild() }

    fun uninstall(id: Long) { uninstallFiles(id); save(list().filter { it.id != id }); rebuild() }

    private fun uninstallFiles(id: Long) { File(store, "m$id").deleteRecursively(); File(textures, "m$id").deleteRecursively() }

    /** What the emulator is to be started with: the disc image, or the disc's files with the mods applied when one needs it. */
    fun bootPath(): File = if (File(tree, "PSP_GAME").isDirectory) tree else isoFile

    /** Regenerates the merged texture list, the merged cheat list and the disc's files from the mods that are on. */
    fun rebuild() {
        val on = list().filter { it.enabled }
        // textures: one textures.ini over every pack that is on, each pack in its own folder
        val packs = on.filter { it.textures }.mapNotNull { m ->
            val dir = File(textures, "m${m.id}").takeIf { it.isDirectory } ?: return@mapNotNull null
            TextureIni.Pack("m${m.id}", File(dir, "textures.ini").takeIf { it.isFile }?.readText(), dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path.replace('\\', '/') }.toList())
        }
        val merged = File(textures, "textures.ini")
        if (packs.isNotEmpty()) { textures.mkdirs(); merged.writeText(TextureIni.merge(packs)) } else merged.delete()
        // cheats: the lists joined, the header lines of all but the first dropped
        val cheatFile = File(memstick, "PSP/Cheats/$discId.ini")
        val lists = on.filter { it.cheats }.mapNotNull { File(store, "m${it.id}/cheats.ini").takeIf { f -> f.isFile }?.readLines() }
        if (lists.isNotEmpty()) {
            cheatFile.parentFile.mkdirs()
            cheatFile.writeText(lists.withIndex().flatMap { (i, l) -> if (i == 0) l else l.filter { !it.startsWith("_S ") && !it.startsWith("_G ") } }.joinToString("\n") + "\n")
        } else cheatFile.delete()
        rebuildTree(on)
    }

    private fun rebuildTree(on: List<InstalledMod>) {
        val overlays = LinkedHashMap<String, File>(); val patches = ArrayList<Pair<String, File>>()
        on.forEach { m ->
            m.discFiles.forEach { d -> overlays[d.lowercase()] = File(store, "m${m.id}/disc/$d") }
            m.patches.forEachIndexed { i, target -> patches += target to File(store, "m${m.id}/patch/$i.xdelta") }
        }
        if (overlays.isEmpty() && patches.isEmpty()) { tree.deleteRecursively(); return }
        DiscBytes.open(isoFile).use { d ->
            val files = DiscFiles(d); val stamp = File(tree, ".persona-base")
            if (!stamp.isFile || stamp.readText() != isoFile.length().toString()) {            // a fresh copy of the disc's files
                tree.deleteRecursively(); files.entries().filter { !it.isDir }.forEach { files.extract(it, File(tree, it.path)) }
                stamp.writeText(isoFile.length().toString())
            } else {                                                                            // put back whatever an earlier set of mods changed
                File(tree, ".persona-changed").takeIf { it.isFile }?.readLines()?.forEach { p -> files.find(p)?.let { e -> files.extract(e, File(tree, e.path)) } }
            }
            val changed = ArrayList<String>()
            overlays.values.forEachIndexed { i, src ->
                val path = on.flatMap { it.discFiles }.first { it.lowercase() == overlays.keys.elementAt(i) }
                val entry = files.find(path); val target = File(tree, entry?.path ?: path)
                target.parentFile.mkdirs(); src.copyTo(target, overwrite = true); changed += entry?.path ?: path
            }
            patches.forEach { (path, patch) ->
                val entry = files.find(path) ?: throw IOException("The game has no $path to patch.")
                val patched = try { Vcdiff.apply(files.read(entry), patch.readBytes()) } catch (e: IOException) { throw IOException("A patch from one of the mods can't be used on this copy of the game: ${e.message}", e) }
                File(tree, entry.path).writeBytes(patched); changed += entry.path
            }
            File(tree, ".persona-changed").writeText(changed.joinToString("\n"))
        }
    }
}
