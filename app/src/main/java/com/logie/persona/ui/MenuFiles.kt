package com.logie.persona.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** The person's own fonts for the games' menus (Settings, Games), in the app's files (their pictures: [MenuPictures]). */
internal object MenuFiles {
    /** A font file, checked by the phone's own font loader first: `files/fonts/pN.ttf` (or .otf). */
    fun setFont(context: Context, number: Int, uri: Uri): Boolean = runCatching {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }.orEmpty()
        val ext = if (name.endsWith(".otf", ignoreCase = true)) "otf" else "ttf"
        val dir = File(context.filesDir, "fonts").also { it.mkdirs() }
        val part = File(dir, "p$number.part")
        context.contentResolver.openInputStream(uri)!!.use { input -> part.outputStream().use { input.copyTo(it) } }
        // (Typeface.createFromFile throws, or gives the default face, for a file that isn't a font)
        val face = runCatching { Typeface.createFromFile(part) }.getOrNull()
        if (face == null || face == Typeface.DEFAULT) { part.delete(); return false }
        dir.listFiles { f -> f.name.startsWith("p$number.") && f != part }?.forEach { it.delete() }
        if (!part.renameTo(File(dir, "p$number.$ext"))) { part.delete(); return false }
        MenuAssets.changed()
        true
    }.getOrDefault(false)

    fun resetFont(context: Context, number: Int) {
        File(context.filesDir, "fonts").listFiles { f -> f.name.startsWith("p$number.") }?.forEach { it.delete() }
        MenuAssets.changed()
    }
}
