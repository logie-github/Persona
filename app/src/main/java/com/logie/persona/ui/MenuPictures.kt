package com.logie.persona.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.logie.persona.backend.art.GameLook
import com.logie.persona.backend.art.MenuPicture
import java.io.File

/**
 * The pictures the person puts in a game's menu: its **character** (a person, held on the menu's character to set) and its **logo** (in place of the
 * title's lettering, held on the title to set).
 *
 * - The picture chosen is kept as it came (`files/art-src/<kind>-pN.png`, at most 1600 px a side), and what the menu shows is made from it
 *   (`files/art/<kind>-pN.png`): the crop, a plain background cut away ([MenuPicture.cutBackground]), the empty margins trimmed (so a person
 *   always stands on the menu's bottom edge).
 * - Each one's settings (`menu-pictures` preferences): the crop, cutting the background (on), its size, facing the other way, and matching the
 *   game's look ([GameLook]: on for a character, with how much of it; for a logo, painting it in the game's colour, off).
 * - A character the person hasn't set is the app's own (`assets/characters/pN.png`), with the same settings.
 */
internal object MenuPictures {
    enum class Kind(val key: String, val title: String) { CHARACTER("character", "Character"), LOGO("logo", "Logo") }

    class Look(val crop: MenuPicture.Crop, val cut: Boolean, val size: Float, val flip: Boolean, val match: Boolean, val strength: Float = 1f) {
        fun with(crop: MenuPicture.Crop = this.crop, cut: Boolean = this.cut, size: Float = this.size, flip: Boolean = this.flip, match: Boolean = this.match, strength: Float = this.strength) =
            Look(crop, cut, size, flip, match, strength)
    }

    private const val MAX_SIDE = 1600

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("menu-pictures", Context.MODE_PRIVATE)
    private fun key(kind: Kind, number: Int, what: String) = "${kind.key}-p$number-$what"

    private fun shown(context: Context, kind: Kind, number: Int) = File(context.filesDir, "art/${kind.key}-p$number.png")
    private fun kept(context: Context, kind: Kind, number: Int) = File(context.filesDir, "art-src/${kind.key}-p$number.png")

    /** The person's picture as the menu shows it, if they set one. */
    fun file(context: Context, kind: Kind, number: Int): File? = shown(context, kind, number).takeIf { it.isFile }

    /** The picture as it came (a character set before the crop existed is its own source). */
    fun source(context: Context, kind: Kind, number: Int): File? = kept(context, kind, number).takeIf { it.isFile } ?: file(context, kind, number)

    fun look(context: Context, kind: Kind, number: Int): Look {
        val p = prefs(context)
        return Look(
            crop = MenuPicture.Crop.decode(p.getString(key(kind, number, "crop"), null)) ?: MenuPicture.Crop.WHOLE,
            cut = p.getBoolean(key(kind, number, "cut"), true),
            size = p.getFloat(key(kind, number, "size"), 1f),
            flip = p.getBoolean(key(kind, number, "flip"), false),
            match = p.getBoolean(key(kind, number, "match"), kind == Kind.CHARACTER),
            strength = p.getFloat(key(kind, number, "strength"), 1f),
        )
    }

    /** Keeps [look]; a new crop or cut makes the shown picture again. */
    fun setLook(context: Context, kind: Kind, number: Int, look: Look) {
        val before = look(context, kind, number)
        prefs(context).edit()
            .putString(key(kind, number, "crop"), look.crop.encode()).putBoolean(key(kind, number, "cut"), look.cut)
            .putFloat(key(kind, number, "size"), look.size).putBoolean(key(kind, number, "flip"), look.flip).putBoolean(key(kind, number, "match"), look.match)
            .putFloat(key(kind, number, "strength"), look.strength)
            .apply()
        if (look.crop != before.crop || look.cut != before.cut) make(context, kind, number)
        cache.evictAll()
        MenuAssets.changed()
    }

    /** The picture at [uri] as game [number]'s [kind] (the whole of it, its background cut): false if it can't be read. */
    fun set(context: Context, kind: Kind, number: Int, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }) } ?: return false
        keep(context, kind, number, bitmap)
    }.getOrDefault(false)

    /** A downloaded picture ([file]: a SteamGridDB logo) as game [number]'s [kind]. */
    fun set(context: Context, kind: Kind, number: Int, file: File): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }) ?: return false
        keep(context, kind, number, bitmap)
    }.getOrDefault(false)

    private fun sample(w: Int, h: Int): Int { var s = 1; while (maxOf(w, h) / (s * 2) >= MAX_SIDE) s *= 2; return s }

    private fun keep(context: Context, kind: Kind, number: Int, decoded: Bitmap): Boolean {
        val bitmap = fit(decoded)
        val source = kept(context, kind, number).also { it.parentFile?.mkdirs() }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val old = look(context, kind, number)
        prefs(context).edit().putString(key(kind, number, "crop"), MenuPicture.Crop.WHOLE.encode()).putBoolean(key(kind, number, "cut"), true).apply()
        val ok = make(context, kind, number)
        if (old.size != 1f || old.flip) prefs(context).edit().putFloat(key(kind, number, "size"), 1f).putBoolean(key(kind, number, "flip"), false).apply()
        cache.evictAll()
        MenuAssets.changed()
        return ok
    }

    private fun fit(bitmap: Bitmap): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= MAX_SIDE) return bitmap
        val k = MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true)
    }

    /** The shown picture from the source: cropped, its background cut if asked, trimmed. False when there is no source or nothing is left. */
    private fun make(context: Context, kind: Kind, number: Int): Boolean = runCatching {
        val src = kept(context, kind, number).takeIf { it.isFile } ?: return false
        val bitmap = BitmapFactory.decodeFile(src.path) ?: return false
        val look = look(context, kind, number)
        val r = look.crop.pixels(bitmap.width, bitmap.height)
        var w = r[2]; var h = r[3]
        var px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, r[0], r[1], w, h)
        if (look.cut) MenuPicture.cutBackground(px, w, h)
        MenuPicture.trim(px, w, h)?.let { t -> px = MenuPicture.copy(px, w, t); w = t[2]; h = t[3] } ?: return false
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val target = shown(context, kind, number).also { it.parentFile?.mkdirs() }
        val part = File(target.path + ".part")
        part.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        part.renameTo(target)
    }.getOrDefault(false)

    /** Back to the game's own: the person's picture and its settings gone. */
    fun reset(context: Context, kind: Kind, number: Int) {
        shown(context, kind, number).delete(); kept(context, kind, number).delete()
        prefs(context).edit().apply { for (w in listOf("crop", "cut", "size", "flip", "match", "strength")) remove(key(kind, number, w)) }.apply()
        cache.evictAll()
        MenuAssets.changed()
    }

    // What the menu draws

    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }

    private fun cacheKey(kind: Kind, number: Int) = "${kind.key}-$number-${MenuAssets.revision.value}"

    /** The picture already made for the menu, if it is ([menuPicture] makes it). */
    fun cached(kind: Kind, number: Int): Bitmap? = cache.get(cacheKey(kind, number))

    /**
     * Game [number]'s [kind] as the menu draws it: the person's, else (a character) the app's own; a character in the game's look when that is on.
     * Slow the first time (the look goes over every pixel): call it off the main thread, then [cached] has it.
     */
    fun menuPicture(context: Context, kind: Kind, number: Int): Bitmap? {
        val k = cacheKey(kind, number)
        cache.get(k)?.let { return it }
        val base = runCatching {
            file(context, kind, number)?.let { BitmapFactory.decodeFile(it.path) }
                ?: if (kind == Kind.CHARACTER) context.assets.open("characters/p$number.png").use { BitmapFactory.decodeStream(it) } else null
        }.getOrNull() ?: return null
        val look = look(context, kind, number)
        val out = if (kind == Kind.CHARACTER && look.match) {
            val bitmap = fit(base)
            val w = bitmap.width; val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            GameLook.apply(px, w, h, number, look.strength)
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        } else base
        cache.put(k, out)
        return out
    }

    /** Whether game [number] has a [kind] of the person's, or (a character) of the app's own. */
    fun has(context: Context, kind: Kind, number: Int): Boolean =
        file(context, kind, number) != null || (kind == Kind.CHARACTER && runCatching { context.assets.open("characters/p$number.png").close(); true }.getOrDefault(false))
}
