package com.logie.persona.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.logie.persona.backend.art.CropMath
import java.io.File

/**
 * The pictures the person chose: a box picture for each version of each game (`art/<version>-box.jpg`), the source it was cropped from (to adjust the
 * crop later) and the SteamGridDB key. Plain files and one preferences entry.
 */
object ArtStore {
    const val BOX_ASPECT = 2f / 3f
    private const val BOX_W = 600; private const val BOX_H = 900

    private fun dir(context: Context, name: String) = File(context.filesDir, name)
    fun box(context: Context, versionId: String): File? = File(dir(context, "art"), "$versionId-box.jpg").takeIf { it.isFile }
    fun source(context: Context, versionId: String): File? = File(dir(context, "art-src"), "$versionId-box.jpg").takeIf { it.isFile }

    /** The picture at [uri] kept as the source of [versionId]'s box (scaled to at most 2400 px a side); null if it can't be read. */
    fun stage(context: Context, versionId: String, uri: Uri): File? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        stageBitmap(context, versionId, bitmap)
    }.getOrNull()

    /** A downloaded picture ([file]) kept as the source of [versionId]'s box. */
    fun stage(context: Context, versionId: String, file: File): File? = runCatching {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        stageBitmap(context, versionId, bitmap)
    }.getOrNull()

    private fun stageBitmap(context: Context, versionId: String, bitmap: Bitmap): File {
        val target = File(dir(context, "art-src"), "$versionId-box.jpg"); target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        return target
    }

    /** Keeps what the crop frame shows as [versionId]'s box picture. */
    fun saveBox(context: Context, versionId: String, cropped: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(cropped, BOX_W, BOX_H, true)
        val target = File(dir(context, "art"), "$versionId-box.jpg"); target.parentFile?.mkdirs()
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    fun resetBox(context: Context, versionId: String) {
        File(dir(context, "art"), "$versionId-box.jpg").delete(); File(dir(context, "art-src"), "$versionId-box.jpg").delete()
    }

    /** The part of [source] the frame shows at [zoom] centred on ([cx], [cy]), cut out. */
    fun cut(source: Bitmap, zoom: Float, cx: Float, cy: Float): Bitmap {
        val r = CropMath.rect(source.width, source.height, BOX_ASPECT, zoom, cx, cy)
        val w = r.w.coerceAtMost(source.width - r.x).coerceAtLeast(1); val h = r.h.coerceAtMost(source.height - r.y).coerceAtLeast(1)
        return Bitmap.createBitmap(source, r.x.coerceIn(0, source.width - 1), r.y.coerceIn(0, source.height - 1), w, h)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("steamgrid", Context.MODE_PRIVATE)
    fun key(context: Context): String? = prefs(context).getString("key", null)?.takeIf { it.isNotBlank() }
    fun setKey(context: Context, key: String) { prefs(context).edit().putString("key", key.trim()).apply() }
    fun clearKey(context: Context) { prefs(context).edit().remove("key").apply() }
}
