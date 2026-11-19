package com.logie.persona.backend.mods

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the mod importer gets its bytes (the real network, or a fixture in tests). */
interface Web {
    fun text(url: String): String
    /** Saves [url] to [to], following redirects; [onBytes] is called with (done, total or -1). */
    fun download(url: String, to: File, onBytes: (Long, Long) -> Unit = { _, _ -> })
}

class UrlWeb(private val agent: String = "Mozilla/5.0 (Linux; Android 14)") : Web {
    private fun open(url: String, from: Long = 0): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 30000; setRequestProperty("User-Agent", agent)
                if (from > 0) setRequestProperty("Range", "bytes=$from-")
            }
            when (val code = c.responseCode) {
                200, 206 -> return c
                in 300..399 -> { val loc = c.getHeaderField("Location"); c.disconnect(); u = URL(u, loc ?: throw IOException("HTTP $code")) }
                else -> { c.disconnect(); throw IOException("HTTP $code") }
            }
        }
        throw IOException("too many redirects")
    }

    override fun text(url: String): String = open(url).let { c -> try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() } }

    override fun download(url: String, to: File, onBytes: (Long, Long) -> Unit) {
        to.parentFile?.mkdirs(); val part = File(to.path + ".part"); var stalls = 0
        while (true) {
            val have = if (part.isFile) part.length() else 0L
            try {
                val c = open(url, have)
                try {
                    val resumed = c.responseCode == 206; val len = c.contentLengthLong; val total = if (len > 0) len + (if (resumed) have else 0L) else -1L
                    var done = if (resumed) have else 0L; val buf = ByteArray(1 shl 16)
                    java.io.FileOutputStream(part, resumed).buffered(1 shl 20).use { out ->
                        c.inputStream.use { inp -> while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); done += n; onBytes(done, total) } }
                    }
                    if (total > 0 && done != total) throw IOException("The download ended early.")
                } finally { c.disconnect() }
                if (!part.renameTo(to)) throw IOException("Couldn't save the download."); return
            } catch (e: IOException) { if (part.isFile && part.length() > have) stalls = 0 else stalls++; if (stalls >= 4) throw e; Thread.sleep(500L shl stalls) }
        }
    }
}

data class GbFile(val id: Long, val name: String, val bytes: Long, val url: String, val description: String)

data class GbMod(
    val id: Long, val name: String, val category: String, val version: String, val likes: Int, val views: Int,
    val thumbnail: String?, val author: String, val updated: Long, val files: List<GbFile> = emptyList(), val text: String = "",
)

/**
 * GameBanana's public API (v11) for one game's mods. [gameId] 12961 is Shin Megami Tensei: Persona (PSP). The list calls give a mod's name, picture and
 * counts; [profile] gives its files and description.
 */
class GameBanana(private val web: Web, val gameId: Int = PERSONA_PSP) {
    enum class Sort(val key: String) { DOWNLOADS("Generic_MostDownloaded"), NEWEST("Generic_Newest"), LIKED("Generic_MostLiked") }

    fun list(sort: Sort = Sort.DOWNLOADS, perPage: Int = 50, page: Int = 1): List<GbMod> {
        val url = "$API/Mod/Index?_nPage=$page&_nPerpage=$perPage&_aFilters%5BGeneric_Game%5D=$gameId&_sSort=${sort.key}"
        return records(web.text(url))
    }

    fun search(text: String, perPage: Int = 30): List<GbMod> {
        val url = "$API/Util/Search/Results?_sSearchString=${URLEncoder.encode(text, "UTF-8")}&_idGameRow=$gameId&_sModelName=Mod&_nPage=1&_nPerpage=$perPage"
        return records(web.text(url))
    }

    fun profile(id: Long): GbMod = mod(JSONObject(web.text("$API/Mod/$id/ProfilePage")))

    private fun records(json: String): List<GbMod> = JSONObject(json).optJSONArray("_aRecords").let { a -> a?.let { List(it.length()) { i -> mod(it.getJSONObject(i)) } } ?: emptyList() }

    private fun mod(o: JSONObject): GbMod {
        val img = o.optJSONObject("_aPreviewMedia")?.optJSONArray("_aImages")?.optJSONObject(0)
        val thumb = img?.let { "${it.optString("_sBaseUrl")}/${it.optString("_sFile530").ifEmpty { it.optString("_sFile") }}" }
        val files = o.optJSONArray("_aFiles") ?: JSONArray()
        return GbMod(
            id = o.optLong("_idRow"), name = o.optString("_sName"), version = o.optString("_sVersion"),
            category = (o.optJSONObject("_aCategory") ?: o.optJSONObject("_aRootCategory"))?.optString("_sName").orEmpty(),
            likes = o.optInt("_nLikeCount"), views = o.optInt("_nViewCount"), thumbnail = thumb,
            author = o.optJSONObject("_aSubmitter")?.optString("_sName").orEmpty(), updated = o.optLong("_tsDateUpdated"),
            files = List(files.length()) { i -> files.getJSONObject(i).let { GbFile(it.optLong("_idRow"), it.optString("_sFile"), it.optLong("_nFilesize"), it.optString("_sDownloadUrl"), it.optString("_sDescription")) } },
            text = o.optString("_sText"),
        )
    }

    companion object {
        const val API = "https://gamebanana.com/apiv11"
        const val PERSONA_PSP = 12961
    }
}
