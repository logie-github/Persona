package com.logie.persona.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logie.persona.art.ArtStore
import com.logie.persona.backend.art.CropMath
import com.logie.persona.backend.art.SteamGrid
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.catalog.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Lavender = Color(0xFFC9B8FF)
private val Panel = Color(0xFF14103E)

/** Box art for each version of [game]: from SteamGridDB, from the phone's own files, or put back. [onChanged] runs when a picture was kept or removed. */
@Composable
fun BoxArtDialog(game: Game, initial: Version, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(initial) }
    var revision by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { val kept = ArtStore.stage(context, version.id, uri); if (kept != null) cropping = kept else message = "That picture could not be read." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).clip(RoundedCornerShape(16.dp)).background(Panel).border(2.dp, Lavender, RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("BOX ART", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp)
            if (game.versions.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(game.versions) { v -> FilterChip(selected = v == version, onClick = { version = v }, label = { Text(v.label) }) }
            }
            key(version.id, revision) {
                val current = remember { ArtStore.box(context, version.id)?.let { BitmapFactory.decodeFile(it.path) } }
                Box(Modifier.height(220.dp).aspectRatio(2f / 3f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                    if (current != null) Image(current.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(game.number.toString(), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 64.sp)
                }
            }
            message?.let { Text(it, color = Color(0xFFFFB4AB)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { picking = true }, Modifier.weight(1f)) { Text("STEAMGRIDDB") }
                OutlinedButton(onClick = { files.launch(arrayOf("image/*")) }, Modifier.weight(1f)) { Text("MY FILES") }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { ArtStore.resetBox(context, version.id); revision++; onChanged() }, enabled = ArtStore.box(context, version.id) != null) { Text("RESET") }
                ArtStore.source(context, version.id)?.let { src -> TextButton(onClick = { cropping = src }) { Text("CROP") } }
                TextButton(onClick = onDismiss) { Text("DONE") }
            }
        }
    }
    if (picking) SteamGridPicker(
        term = SteamGrid.searchTerm(game.title, version.label, game.versions.size == 1),
        onPicked = { file -> picking = false; val kept = ArtStore.stage(context, version.id, file); if (kept != null) cropping = kept else message = "That picture could not be read." },
        onDismiss = { picking = false },
    )
    cropping?.let { src ->
        CropDialog(src, onSave = { bmp -> ArtStore.saveBox(context, version.id, bmp); cropping = null; revision++; onChanged() }, onDismiss = { cropping = null })
    }
}

/**
 * A game's pictures from SteamGridDB ([slot]: its boxes, or its logos for a menu's title): the game found by name (the others as chips), its
 * pictures in a grid, a tap taking one. Asks for the key first if there is none.
 */
@Composable
internal fun SteamGridPicker(term: String, onPicked: (File) -> Unit, onDismiss: () -> Unit, slot: SteamGrid.Slot = SteamGrid.Slot.BOX) {
    val box = slot == SteamGrid.Slot.BOX
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(ArtStore.key(context)) }
    var typedKey by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(term) }
    var games by remember { mutableStateOf<List<SteamGrid.Game>>(emptyList()) }
    var chosen by remember { mutableStateOf<SteamGrid.Game?>(null) }
    val pictures = remember { mutableStateListOf<SteamGrid.Picture>() }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var taking by remember { mutableStateOf<Long?>(null) }

    fun load(g: SteamGrid.Game, next: Int) {
        scope.launch {
            busy = true; message = null
            runCatching { withContext(Dispatchers.IO) { SteamGrid.pictures(key!!, slot, g.id, next) } }
                .onSuccess { r -> if (next == 0) pictures.clear(); pictures += r.pictures; page = next; hasMore = r.hasMore; if (pictures.isEmpty()) message = "No ${if (box) "box pictures" else "logos"} for ${g.name}." }
                .onFailure { message = it.message ?: "Couldn't reach SteamGridDB." }
            busy = false
        }
    }
    fun search(t: String) {
        scope.launch {
            busy = true; message = null
            runCatching { withContext(Dispatchers.IO) { SteamGrid.search(key!!, t) } }
                .onSuccess { found -> games = found.take(8); pictures.clear(); chosen = found.firstOrNull(); if (chosen == null) message = "SteamGridDB doesn't know \"$t\"." else load(chosen!!, 0) }
                .onFailure { message = it.message ?: "Couldn't reach SteamGridDB." }
            busy = false
        }
    }
    LaunchedEffect(key) { if (key != null) search(text) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(0.96f).clip(RoundedCornerShape(16.dp)).background(Panel).border(2.dp, Lavender, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (key == null) {
                Text("STEAMGRIDDB KEY", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                OutlinedTextField(typedKey, { typedKey = it.trim() }, singleLine = true, label = { Text("Key") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    TextButton(enabled = typedKey.isNotEmpty(), onClick = { ArtStore.setKey(context, typedKey); key = typedKey }) { Text("SAVE") }
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Game name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search(text) }))
                TextButton(onClick = { ArtStore.clearKey(context); key = null }) { Text("KEY") }
            }
            if (games.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games, key = { it.id }) { g ->
                    val on = g.id == chosen?.id
                    Text(g.name, color = if (on) Color.White else Lavender, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF7B4FD6) else Color.White.copy(alpha = 0.08f)).clickable { chosen = g; load(g, 0) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            message?.let { Text(it, color = Color(0xFFFFB4AB)) }
            if (busy && pictures.isEmpty()) Text("Looking…", color = Lavender)
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 460.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pictures, key = { it.id }) { pic ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(if (box) 2f / 3f else 1.5f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (box) 0.05f else 0.12f))
                                .border(1.dp, if (taking == pic.id) Lavender else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .clickable(enabled = taking == null) {
                                    taking = pic.id; message = null
                                    scope.launch {
                                        val dest = File(context.cacheDir, "steamgrid/${pic.id}.img")
                                        runCatching { withContext(Dispatchers.IO) { SteamGrid.download(pic.url, dest) } }
                                            .onSuccess { onPicked(dest) }
                                            .onFailure { message = it.message ?: "Couldn't download that picture."; taking = null }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteThumb(pic.thumb, Modifier.fillMaxSize().padding(if (box) 0.dp else 6.dp), if (box) ContentScale.Crop else ContentScale.Fit)
                            if (taking == pic.id) Text("Downloading…", color = Color.White, fontSize = 12.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(6.dp))
                        }
                        Text(SteamGrid.resolution(pic), color = Lavender, fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(onClick = { chosen?.let { if (!busy) load(it, page + 1) } }, Modifier.fillMaxWidth()) { Text(if (busy) "Looking…" else "MORE") }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Pictures by the SteamGridDB community.", color = Lavender.copy(alpha = 0.7f), fontSize = 11.sp)
                TextButton(onClick = onDismiss) { Text("CLOSE") }
            }
        }
    }
}

private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }

/** A picture from SteamGridDB's own address, loaded once and kept for the session (no image library in the app). */
@Composable
private fun RemoteThumb(url: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(thumbs.get(url), url) {
        if (value == null && SteamGrid.isSiteUrl(url)) value = withContext(Dispatchers.IO) {
            runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
            }.getOrNull()?.also { thumbs.put(url, it) }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = scale) } ?: Spacer(modifier)
}

/** Crop to the box's shape: drag to move, pinch or slide to zoom; what the frame shows is what is kept. */
@Composable
private fun CropDialog(source: File, onSave: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, source) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(source.path) }.getOrNull() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).clip(RoundedCornerShape(16.dp)).background(Panel).border(2.dp, Lavender, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bmp = bitmap
            if (bmp == null) { Text("Opening the picture…", color = Lavender); return@Column }
            var zoom by remember { mutableFloatStateOf(1f) }
            var cx by remember { mutableFloatStateOf(bmp.width / 2f) }
            var cy by remember { mutableFloatStateOf(bmp.height / 2f) }
            val aspect = ArtStore.BOX_ASPECT
            val r = CropMath.rect(bmp.width, bmp.height, aspect, zoom, cx, cy)
            val image = remember(bmp) { bmp.asImageBitmap() }
            val maxHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.5f
            Canvas(
                Modifier.align(Alignment.CenterHorizontally).width(minOf(maxHeight * aspect, 420.dp)).aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).clipToBounds()
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, CropMath.MAX_ZOOM)
                            val now = CropMath.rect(bmp.width, bmp.height, aspect, zoom, cx, cy)
                            cx = (now.x + now.w / 2f) - pan.x * now.w / size.width
                            cy = (now.y + now.h / 2f) - pan.y * now.h / size.height
                        }
                    },
            ) { drawImage(image, srcOffset = IntOffset(r.x, r.y), srcSize = IntSize(r.w, r.h), dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            Slider(zoom, { zoom = it }, valueRange = 1f..CropMath.MAX_ZOOM)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                TextButton(onClick = { zoom = 1f; cx = bmp.width / 2f; cy = bmp.height / 2f }) { Text("RESET") }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
                Button(onClick = { onSave(ArtStore.cut(bmp, zoom, cx, cy)) }) { Text("SAVE") }
            }
        }
    }
}
