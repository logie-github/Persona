package com.logie.persona.psp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.logie.persona.backend.mods.PspMods
import com.logie.persona.backend.psp.DiscBytes
import com.logie.persona.backend.psp.PspDisc
import com.logie.persona.backend.psp.PspDiscs
import com.logie.persona.backend.psp.PspOptions
import java.io.File
import java.io.IOException

/** Where Persona 1's disc image, its mods and its saves live, and how an image is brought in. Everything is plain files so the game's own process reads it too. */
object PspStore {
    const val VERSION_ID = "persona1_psp"

    sealed interface Imported {
        data class Ok(val disc: PspDisc) : Imported
        data class Refused(val why: String) : Imported
    }

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "games/$VERSION_ID")
    fun disc(context: Context): File? = listOf("disc.iso", "disc.cso").map { File(dir(context), it) }.firstOrNull { it.isFile }
    fun discId(context: Context): String? = File(dir(context), "disc.id").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
    fun isInstalled(context: Context): Boolean = disc(context) != null && discId(context) != null

    fun mods(context: Context): PspMods? {
        val disc = disc(context) ?: return null
        return PspMods(dir(context), discId(context) ?: return null, disc)
    }

    private fun optionsFile(context: Context) = File(context.filesDir, "psp-options.json")
    fun options(context: Context): PspOptions = PspOptions.fromJson(optionsFile(context).takeIf { it.isFile }?.readText())
    fun saveOptions(context: Context, o: PspOptions) { optionsFile(context).writeText(o.toJson()) }

    /** Copies the chosen file in, checks it is the PSP game, and keeps it as the game's disc. Blocking: run it off the main thread. [onProgress] gets 0..1. */
    fun import(context: Context, uri: Uri, onProgress: (Float) -> Unit): Imported {
        val dir = dir(context).also { it.mkdirs() }
        val part = File(dir, "disc.part")
        try {
            val total = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
            context.contentResolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20); var done = 0L
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); done += n; if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f)) }
                }
            } ?: return Imported.Refused("That file could not be opened.")
            val disc = try { DiscBytes.open(part).use { PspDiscs.identify(it) } } catch (e: IllegalArgumentException) { part.delete(); return Imported.Refused("That is not a PSP game disc.") }
            catch (e: IOException) { part.delete(); return Imported.Refused("That disc image could not be read.") }
            if (!disc.isPersona) { part.delete(); return Imported.Refused("That is ${disc.title.ifBlank { "another game" }}, not Shin Megami Tensei: Persona.") }
            val compressed = part.inputStream().use { s -> ByteArray(4).also { s.read(it) }.decodeToString() == "CISO" }
            listOf("disc.iso", "disc.cso").forEach { File(dir, it).delete() }
            if (!part.renameTo(File(dir, if (compressed) "disc.cso" else "disc.iso"))) return Imported.Refused("The disc image could not be kept.")
            File(dir, "disc.id").writeText(disc.discId)
            File(dir, "tree").deleteRecursively()                       // a copy made from an earlier image
            return Imported.Ok(disc)
        } catch (e: IOException) {
            part.delete(); return Imported.Refused("The disc image could not be copied: ${e.message ?: "the storage said no"}.")
        }
    }

    /** Removes the disc and the mods, and keeps the saves (and save states) so installing the game again carries on. */
    fun uninstall(context: Context) {
        val dir = dir(context)
        listOf("disc.iso", "disc.cso", "disc.part", "disc.id").forEach { File(dir, it).delete() }
        File(dir, "tree").deleteRecursively(); File(dir, "mods").deleteRecursively()
        File(dir, "memstick/PSP/TEXTURES").deleteRecursively(); File(dir, "memstick/PSP/Cheats").deleteRecursively()
    }
}
