package com.logie.persona.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logie.persona.backend.art.MenuPicture
import com.logie.persona.backend.art.SteamGrid
import com.logie.persona.backend.catalog.Game
import com.logie.persona.music.MenuMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// A menu's picture (hold the title: its logo; hold the character: its person)

/**
 * A game's menu picture ([kind]: its character or its logo): a picture from the phone (a logo also from SteamGridDB), its crop, its background
 * cut away, its size, facing the other way (a character), the game's look (a character: [com.logie.persona.backend.art.GameLook]; a logo: painted
 * in the game's colour), and back to the game's own. The preview is the menu's: on the game's colour, a character on the bottom edge.
 */
@Composable
internal fun MenuPictureDialog(game: Game, kind: MenuPictures.Kind, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by MenuAssets.revision.collectAsState()
    var look by remember(revision) { mutableStateOf(MenuPictures.look(context, kind, game.number)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var browsing by remember { mutableStateOf(false) }
    val custom = remember(revision) { MenuPictures.file(context, kind, game.number) != null }
    val style = MenuStyle.of(game.number)

    fun work(block: () -> Boolean, failed: String) {
        busy = true; message = null
        scope.launch {
            val ok = withContext(Dispatchers.Default) { runCatching(block).getOrDefault(false) }
            busy = false
            if (!ok) message = failed
        }
    }
    fun keep(next: MenuPictures.Look) { look = next; work({ MenuPictures.setLook(context, kind, game.number, next); true }, "That didn't work.") }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work({ MenuPictures.set(context, kind, game.number, uri) }, "That picture couldn't be read (or nothing was left of it).")
    }
    // The preview, made off the main thread as the menu makes it.
    val preview by produceState<Bitmap?>(MenuPictures.cached(kind, game.number), revision, kind, game.number) {
        value = withContext(Dispatchers.Default) { MenuPictures.menuPicture(context, kind, game.number) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Sheet(kind.title, onDismiss, subtitle = game.title, maxWidth = 560.dp) {
            Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(if (kind == MenuPictures.Kind.CHARACTER) 260.dp else 150.dp).clip(RoundedCornerShape(12.dp)).background(style.base),
                    contentAlignment = if (kind == MenuPictures.Kind.CHARACTER) Alignment.BottomCenter else Alignment.Center) {
                    val p = preview
                    if (p != null) {
                        val image = remember(p) { p.asImageBitmap() }
                        Image(image, null, contentScale = ContentScale.Fit, alignment = if (kind == MenuPictures.Kind.CHARACTER) Alignment.BottomCenter else Alignment.Center,
                            colorFilter = if (kind == MenuPictures.Kind.LOGO && look.match) ColorFilter.tint(style.ink) else null,
                            modifier = Modifier.fillMaxSize().padding(if (kind == MenuPictures.Kind.LOGO) 16.dp else 0.dp).graphicsLayer {
                                scaleX = look.size * (if (look.flip) -1f else 1f); scaleY = look.size
                                transformOrigin = if (kind == MenuPictures.Kind.CHARACTER) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
                            })
                    } else Text(if (kind == MenuPictures.Kind.CHARACTER) "The stand-in figure" else "The game's lettering", style = MaterialTheme.typography.bodyMedium.copy(color = style.ink))
                    if (busy) LinearProgressIndicator(color = Modern.AccentBright, trackColor = Color.Transparent, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                Text(
                    when {
                        custom -> "Your picture. It's cut to its crop, its plain background taken away and its empty edges trimmed${if (kind == MenuPictures.Kind.CHARACTER) ", so it stands on the menu's bottom edge" else ""}."
                        kind == MenuPictures.Kind.CHARACTER -> "The game's own. Choose a picture of a person (a plain or transparent background is best)."
                        else -> "The game's lettering. Choose a logo (a transparent one is best: SteamGridDB has them)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameButton("My files", primary = !custom) { files.launch(arrayOf("image/*")) }
                    if (kind == MenuPictures.Kind.LOGO) FrameButton("SteamGridDB", primary = false) { browsing = true }
                    if (custom) FrameButton("Crop", primary = false) {
                        scope.launch { cropping = withContext(Dispatchers.IO) { MenuPictures.source(context, kind, game.number)?.let { BitmapFactory.decodeFile(it.path) } } }
                    }
                }
                if (custom) ToggleRow("Cut out a plain background", look.cut) { keep(look.with(cut = it)) }
                if (kind == MenuPictures.Kind.CHARACTER) {
                    ToggleRow("In the game's look", look.match) { keep(look.with(match = it)) }
                    if (look.match) {
                        var strength by remember(look.strength) { mutableFloatStateOf(look.strength) }
                        Text("How much of it: ${(strength * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Modern.Text)
                        Slider(strength, { strength = it }, valueRange = 0.2f..2f, onValueChangeFinished = { keep(look.with(strength = strength)) },
                            colors = SliderDefaults.colors(thumbColor = Modern.Accent, activeTrackColor = Modern.Accent))
                    }
                    ToggleRow("Face the other way", look.flip) { keep(look.with(flip = it)) }
                } else if (custom) ToggleRow("In the game's colour", look.match) { keep(look.with(match = it)) }
                Text("Size: ${(look.size * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Modern.Text)
                var size by remember(look.size) { mutableFloatStateOf(look.size) }
                Slider(size, { size = it; look = look.with(size = it) }, valueRange = 0.5f..1.4f,
                    onValueChangeFinished = { keep(look.with(size = size)) },
                    colors = SliderDefaults.colors(thumbColor = Modern.Accent, activeTrackColor = Modern.Accent))
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Modern.Gold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (custom || look.size != 1f || look.flip) FrameButton("Reset", primary = false) { MenuPictures.reset(context, kind, game.number) }
                    FrameButton("Done", onClick = onDismiss)
                }
            }
        }
    }
    cropping?.let { bmp ->
        FreeCropDialog(bmp, look.crop, onSave = { crop -> cropping = null; keep(look.with(crop = crop)) }, onDismiss = { cropping = null })
    }
    if (browsing) SteamGridPicker(
        term = game.title, slot = SteamGrid.Slot.LOGO,
        onPicked = { file -> browsing = false; work({ MenuPictures.set(context, kind, game.number, file) }, "That logo couldn't be read.") },
        onDismiss = { browsing = false },
    )
}

/**
 * A free crop: the whole picture with a frame over it; drag a corner or a side to move it, drag inside to move the frame. What the frame holds is
 * what is kept (then trimmed of empty edges).
 */
@Composable
internal fun FreeCropDialog(bitmap: Bitmap, initial: MenuPicture.Crop, onSave: (MenuPicture.Crop) -> Unit, onDismiss: () -> Unit) {
    var crop by remember { mutableStateOf(initial) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val aspect = bitmap.width.toFloat() / bitmap.height
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.58f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Sheet("Crop", onDismiss, subtitle = "Drag a corner or a side; drag inside to move it.", maxWidth = 560.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val width = minOf(maxWidth, maxHeight * aspect)
                Canvas(
                    Modifier.size(width, width / aspect).pointerInput(bitmap) {
                        // Which edges the drag holds: 1 left, 2 top, 4 right, 8 bottom; 16 the whole frame.
                        var grab = 0
                        detectDragGestures(
                            onDragStart = { o ->
                                val l = crop.l * size.width; val t = crop.t * size.height; val r = crop.r * size.width; val b = crop.b * size.height
                                val reach = 28.dp.toPx()
                                grab = 0
                                val inY = o.y in (t - reach)..(b + reach); val inX = o.x in (l - reach)..(r + reach)
                                if (inY && abs(o.x - l) < reach) grab = grab or 1 else if (inY && abs(o.x - r) < reach) grab = grab or 4
                                if (inX && abs(o.y - t) < reach) grab = grab or 2 else if (inX && abs(o.y - b) < reach) grab = grab or 8
                                if (grab == 0 && o.x in l..r && o.y in t..b) grab = 16
                            },
                            onDrag = { change, d ->
                                change.consume()
                                val dx = d.x / size.width; val dy = d.y / size.height
                                val c = crop
                                crop = if (grab == 16) {
                                    val mx = dx.coerceIn(-c.l, 1f - c.r); val my = dy.coerceIn(-c.t, 1f - c.b)
                                    MenuPicture.Crop(c.l + mx, c.t + my, c.r + mx, c.b + my)
                                } else MenuPicture.Crop(
                                    if (grab and 1 != 0) (c.l + dx).coerceIn(0f, c.r - 0.05f) else c.l,
                                    if (grab and 2 != 0) (c.t + dy).coerceIn(0f, c.b - 0.05f) else c.t,
                                    if (grab and 4 != 0) (c.r + dx).coerceIn(c.l + 0.05f, 1f) else c.r,
                                    if (grab and 8 != 0) (c.b + dy).coerceIn(c.t + 0.05f, 1f) else c.b,
                                )
                            },
                        )
                    },
                ) {
                    // A checker under the picture shows what is transparent.
                    val cell = 12.dp.toPx()
                    var y = 0f; var row = 0
                    while (y < size.height) { var x = if (row % 2 == 0) 0f else cell; while (x < size.width) { drawRect(Color(0xFF2A2A33), Offset(x, y), Size(cell, cell)); x += cell * 2 }; y += cell; row++ }
                    drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val l = crop.l * size.width; val t = crop.t * size.height; val r = crop.r * size.width; val b = crop.b * size.height
                    val shade = Color.Black.copy(alpha = 0.55f)
                    drawRect(shade, Offset.Zero, Size(size.width, t))
                    drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(shade, Offset(0f, t), Size(l, b - t))
                    drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                    drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                    for (k in 1..2) {
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(l + (r - l) * k / 3, t), Offset(l + (r - l) * k / 3, b), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(l, t + (b - t) * k / 3), Offset(r, t + (b - t) * k / 3), strokeWidth = 1.dp.toPx())
                    }
                    val h = 9.dp.toPx()
                    for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b), Offset((l + r) / 2, t), Offset((l + r) / 2, b), Offset(l, (t + b) / 2), Offset(r, (t + b) / 2)))
                        drawCircle(Modern.AccentBright, h, p)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                FrameButton("Whole picture", primary = false) { crop = MenuPicture.Crop.WHOLE }
                FrameButton("Cancel", primary = false, onClick = onDismiss)
                FrameButton("Save") { onSave(crop.clamped()) }
            }
        }
    }
}

// A menu's music (hold the box: Music)

/**
 * Game [game]'s menu music: a song from the phone's files, or from the game's soundtrack page (the in-app browser: [SoundtrackBrowser]), its
 * volume, and taking it off. While the dialog is open the game's song plays.
 */
@Composable
internal fun MusicDialog(game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by MenuMusic.revision.collectAsState()
    val song = remember(revision) { MenuMusic.file(context, game.number) }
    val title = remember(revision) { MenuMusic.title(context, game.number) }
    var volume by remember { mutableFloatStateOf(MenuMusic.volume(context, game.number)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var browsing by remember { mutableStateOf(false) }
    DisposableEffect(game.number) { MenuMusic.hold(context, game.number); onDispose { MenuMusic.hold(context, null) } }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; message = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { MenuMusic.set(context, game.number, uri) }
                busy = false
                if (!ok) message = "The phone can't play that file."
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Sheet("Music", onDismiss, subtitle = game.title, maxWidth = 520.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (song != null) "Playing in the menu: ${title ?: song.name}" else "No music: the menu is quiet.",
                    style = MaterialTheme.typography.bodyLarge, color = if (song != null) Modern.AccentBright else Modern.Muted)
                Text("The song loops while ${game.title}'s menu is on the screen and stops while a game plays.", style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(color = Modern.AccentBright, trackColor = Modern.SurfaceTop, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (MenuMusic.albums[game.number] != null) FrameButton("Soundtrack") { browsing = true }
                    FrameButton("My files", primary = false) { files.launch(arrayOf("audio/*")) }
                }
                if (song != null) {
                    Text("Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Modern.Text)
                    Slider(volume, { volume = it; MenuMusic.setVolume(context, game.number, it) }, valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Modern.Accent, activeTrackColor = Modern.Accent))
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Modern.Gold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (song != null) FrameButton("Remove", primary = false) { MenuMusic.remove(context, game.number) }
                    FrameButton("Done", onClick = onDismiss)
                }
            }
        }
    }
    if (browsing) SoundtrackBrowser(game, onSaved = { browsing = false }, onDismiss = { browsing = false })
}

/**
 * The game's soundtrack page in an in-app browser (the site checks its visitors with a page of its own, which a browser passes): tapping a track
 * opens its page, and tapping its download link (MP3, FLAC…) downloads it as the game's menu music. Only the site's own pages open here.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SoundtrackBrowser(game: Game, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val album = MenuMusic.albums[game.number] ?: return
    var status by remember { mutableStateOf("Tap a track, then its download link (MP3).") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var taking by remember { mutableStateOf(false) }
    fun take(view: WebView, url: String) {
        if (taking) return
        taking = true; progress = 0f; status = "Downloading ${java.net.URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), "UTF-8")}…"
        val agent = view.settings.userAgentString
        val cookies = CookieManager.getInstance().getCookie(url)
        val referer = view.url
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                MenuMusic.download(context, game.number, url, agent, cookies, referer) { done, total -> if (total > 0) progress = done.toFloat() / total }
            }
            taking = false; progress = null
            if (ok) onSaved() else status = "That download didn't work (or the phone can't play it). Try the track's other link."
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(10.dp).panel()) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FrameTitle("Soundtrack")
                    Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                CloseButton(onDismiss)
            }
            progress?.let { LinearProgressIndicator(progress = { it }, color = Modern.AccentBright, trackColor = Modern.SurfaceTop, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (MenuMusic.isSong(url)) { take(view, url); return true }
                                val host = request.url.host.orEmpty()
                                // The site's own pages (and its visitor check) only.
                                return !(host == "khinsider.com" || host.endsWith(".khinsider.com") || host == "challenges.cloudflare.com")
                            }
                        }
                        setDownloadListener { url, _, _, mime, _ -> if (MenuMusic.isSong(url) || mime.orEmpty().startsWith("audio/")) take(this, url) }
                        loadUrl(album)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(10.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}
