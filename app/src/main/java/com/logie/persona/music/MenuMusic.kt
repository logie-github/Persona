package com.logie.persona.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.logie.persona.reports.SessionLog
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Each game's menu music: a song the person set (holding the game's box: Music), played on a loop while that game's menu is on the screen,
 * fading in and out as games are chosen, paused while the launcher is out of sight (a game playing, the phone's home) and while another app
 * holds the sound (a call, the person's own music).
 *
 * The song is kept as `files/music/pN.<its kind>` with its volume (`menu-music` preferences); from the phone's files, or downloaded from the
 * game's soundtrack page (the in-app browser catches the track's download link: [download]).
 */
object MenuMusic {
    /** Each game's soundtrack page (the albums the user named, 18 November 2026). */
    val albums = mapOf(
        1 to "https://downloads.khinsider.com/game-soundtracks/album/persona-1-the-complete-soundtrack",
        2 to "https://downloads.khinsider.com/game-soundtracks/album/persona-2-the-complete-soundtrack",
        3 to "https://downloads.khinsider.com/game-soundtracks/album/persona-3-reload-ps4-ps5-windows-xbox-one-xbox-series-xs-gamerip-2024",
        4 to "https://downloads.khinsider.com/game-soundtracks/album/persona-4-golden-the-complete-soundtrack",
        5 to "https://downloads.khinsider.com/game-soundtracks/album/persona-5",
    )

    private val kinds = listOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    /** Something has changed (a song set or removed): the screens read the songs again. */
    val revision = MutableStateFlow(0)

    private fun dir(context: Context) = File(context.filesDir, "music")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("menu-music", Context.MODE_PRIVATE)

    fun file(context: Context, number: Int): File? = dir(context).listFiles { f -> f.name.startsWith("p$number.") && f.extension.lowercase() in kinds }?.firstOrNull()

    /** The song's own name, as it came. */
    fun title(context: Context, number: Int): String? = prefs(context).getString("title-p$number", null) ?: file(context, number)?.name

    fun volume(context: Context, number: Int): Float = prefs(context).getFloat("volume-p$number", 0.6f)

    fun setVolume(context: Context, number: Int, volume: Float) {
        prefs(context).edit().putFloat("volume-p$number", volume.coerceIn(0f, 1f)).apply()
        if (playing == number) player?.let { if (fade == null) it.setVolume(volume, volume) }
    }

    /** Whether [url] names a song (the in-app browser's download links). */
    fun isSong(url: String): Boolean = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase() in kinds

    /** The song at [uri] (the phone's files) as game [number]'s music: false if it couldn't be copied. */
    fun set(context: Context, number: Int, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }.orEmpty()
        val kind = name.substringAfterLast('.', "").lowercase().takeIf { it in kinds }
            ?: when (resolver.getType(uri)) { "audio/mp4", "audio/x-m4a" -> "m4a"; "audio/ogg" -> "ogg"; "audio/flac" -> "flac"; "audio/wav", "audio/x-wav" -> "wav"; else -> "mp3" }
        val part = File(dir(context).also { it.mkdirs() }, "p$number.part")
        resolver.openInputStream(uri)!!.use { input -> part.outputStream().use { input.copyTo(it) } }
        keep(context, number, part, kind, name.substringBeforeLast('.').ifEmpty { null })
    }.getOrDefault(false)

    /**
     * A song downloaded from [url] (the soundtrack page's track, with the browser's [userAgent] and [cookies], [referer] the page) as game
     * [number]'s music; [progress] with the bytes so far and the whole (0 when not told). Run off the main thread.
     */
    fun download(context: Context, number: Int, url: String, userAgent: String?, cookies: String?, referer: String?, progress: (Long, Long) -> Unit): Boolean {
        val part = File(dir(context).also { it.mkdirs() }, "p$number.part")
        return runCatching {
            var address = url
            var c: HttpURLConnection
            var hops = 0
            while (true) {
                c = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = false
                    userAgent?.let { setRequestProperty("User-Agent", it) }
                    cookies?.let { setRequestProperty("Cookie", it) }
                    referer?.let { setRequestProperty("Referer", it) }
                }
                if (c.responseCode in 300..399 && hops++ < 5) { address = URL(URL(address), c.getHeaderField("Location")).toString(); c.disconnect(); continue }
                break
            }
            try {
                if (c.responseCode != 200) error("The site answered ${c.responseCode}")
                val total = c.contentLengthLong.coerceAtLeast(0)
                if (total > MAX_BYTES) error("That song is too big (${total / 1_048_576} MB)")
                var done = 0L
                c.inputStream.use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done > MAX_BYTES) error("That song is too big")
                        progress(done, total)
                    }
                } }
            } finally { c.disconnect() }
            val name = java.net.URLDecoder.decode(address.substringBefore('?').substringAfterLast('/'), "UTF-8")
            keep(context, number, part, name.substringAfterLast('.', "mp3").lowercase().takeIf { it in kinds } ?: "mp3", name.substringBeforeLast('.').ifEmpty { null })
        }.onFailure { part.delete(); SessionLog.event("music", "download for game $number failed: ${it.message}") }.getOrDefault(false)
    }

    private const val MAX_BYTES = 80L * 1024 * 1024

    private fun keep(context: Context, number: Int, part: File, kind: String, title: String?): Boolean {
        if (part.length() < 1024) { part.delete(); return false }
        // A song the phone can't play isn't kept.
        val playable = runCatching { MediaPlayer().apply { setDataSource(part.path); prepare() }.release(); true }.getOrDefault(false)
        if (!playable) { part.delete(); return false }
        main.post {
            if (playing == number) stop()
            dir(context).listFiles { f -> f.name.startsWith("p$number.") && f != part }?.forEach { it.delete() }
            part.renameTo(File(dir(context), "p$number.$kind"))
            prefs(context).edit().apply { if (title != null) putString("title-p$number", title) else remove("title-p$number") }.apply()
            revision.value++
            update(context)
        }
        return true
    }

    fun remove(context: Context, number: Int) {
        if (playing == number) stop()
        dir(context).listFiles { f -> f.name.startsWith("p$number.") }?.forEach { it.delete() }
        prefs(context).edit().remove("title-p$number").apply()
        revision.value++
        update(context)
    }

    // The player (on the main thread)

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playing: Int? = null
    private var chosen: Int? = null
    private var held: Int? = null
    private var inFront = false
    private var focusLost = false
    private var fade: Runnable? = null
    private var focus: AudioFocusRequest? = null

    /** The game whose menu is on the screen. */
    fun select(context: Context, number: Int) { chosen = number; update(context) }

    /** The launcher in sight or not (its activity started or stopped). */
    fun inFront(context: Context, shown: Boolean) { inFront = shown; update(context) }

    /** While the Music dialog of game [number] is open its song plays, whichever game is chosen; null lets go. */
    fun hold(context: Context, number: Int?) { held = number; update(context) }

    private fun update(context: Context) {
        val app = context.applicationContext
        val want = held ?: chosen
        val song = want?.let { file(app, it) }
        if (!inFront || focusLost) { pause(); if (!inFront) letGoOfFocus(app); return }
        if (song == null) { if (player != null) fadeOut { stop(); letGoOfFocus(app) }; return }
        if (playing == want && player != null) { resume(app); return }
        fadeOut {
            stop()
            if (!requestFocus(app)) return@fadeOut
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(song.path)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener { mp -> if (player === mp) { mp.start(); fadeTo(volume(app, want)) } }
                    setOnErrorListener { mp, what, extra -> SessionLog.event("music", "game $want's song failed ($what, $extra)"); if (player === mp) stop(); true }
                    prepareAsync()
                }
            }.onSuccess { player = it; playing = want }.onFailure { SessionLog.event("music", "game $want's song couldn't start: ${it.message}") }
        }
    }

    private fun resume(context: Context) {
        val p = player ?: return
        if (!p.isPlaying) runCatching { if (requestFocus(context)) { p.setVolume(0f, 0f); p.start(); fadeTo(volume(context, playing ?: return)) } }
    }

    private fun pause() {
        val p = player ?: return
        fadeOut { runCatching { if (p.isPlaying) p.pause() } }
    }

    private fun stop() {
        fade?.let { main.removeCallbacks(it) }; fade = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null; playing = null; level = 0f
    }

    private var level = 0f

    /** The volume to [target] over half a second. */
    private fun fadeTo(target: Float, then: (() -> Unit)? = null) {
        fade?.let { main.removeCallbacks(it) }
        val p = player ?: run { then?.invoke(); return }
        val start = level
        val steps = 10
        var i = 0
        val step = object : Runnable {
            override fun run() {
                i++
                level = start + (target - start) * i / steps
                runCatching { p.setVolume(level, level) }
                if (i < steps) main.postDelayed(this, 50) else { fade = null; then?.invoke() }
            }
        }
        fade = step
        main.post(step)
    }

    private fun fadeOut(then: () -> Unit) { if (player?.let { runCatching { it.isPlaying }.getOrDefault(false) } == true) fadeTo(0f, then) else then() }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { focusLost = true; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { player?.setVolume(level * 0.3f, level * 0.3f) }
                AudioManager.AUDIOFOCUS_GAIN -> { focusLost = false; appContext?.let { update(it) } }
            }
        }
    }
    private var appContext: Context? = null

    private fun letGoOfFocus(context: Context) {
        focus?.let { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
    }

    private fun requestFocus(context: Context): Boolean {
        appContext = context.applicationContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener, main).build().also { focus = it }
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}
