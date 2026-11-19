package com.logie.persona.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import com.logie.persona.art.ArtStore
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.psp.PspOptionRows
import com.logie.persona.psp.PspStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.logie.persona.backend.catalog.Version
import com.logie.persona.install.InstallCenter
import com.logie.persona.install.InstallService
import com.logie.persona.install.InstallUi
import com.logie.persona.update.Updater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val boxColors = mapOf(
    1 to listOf(Color(0xFF3A1C5A), Color(0xFF12081F)),
    2 to listOf(Color(0xFF6B1F2A), Color(0xFF1C080C)),
    3 to listOf(Color(0xFF1F63B8), Color(0xFF07182E)),
    4 to listOf(Color(0xFFE8B923), Color(0xFF3A2C05)),
    5 to listOf(Color(0xFFD7102C), Color(0xFF1A0206)),
)

@Composable
fun Launcher() {
    val context = LocalContext.current
    MaterialTheme(colorScheme = darkColorScheme()) {
        // The app opens on Persona 1's own screen; Back goes to the shelf of boxes, and a box opens its game's screen.
        var open by remember { mutableStateOf<Game?>(Catalog.all.first()) }
        var selected by remember { mutableStateOf(Catalog.all.first()) }
        val versionIds = remember { mutableStateMapOf<String, String>() }
        val states by InstallCenter.state.collectAsState()
        LaunchedEffect(Unit) { InstallCenter.refresh(context, Catalog.all.flatMap { it.versions }) }
        var signIn by remember { mutableStateOf<Version?>(null) }
        var artFor by remember { mutableStateOf<Game?>(null) }
        var artRevision by remember { mutableStateOf(0) }
        // Each time the app comes to the front: look for a newer build, fetch it and open Android's installer.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Updater.retryInstall(context); Updater.checkAndInstall(context) }
        val update by Updater.state.collectAsState()

        // One game's screen, wherever it is shown: Persona 1's own skin, or the themed screen the others have until their skins are made.
        @Composable fun GamePane(game: Game, modifier: Modifier, inLibrary: Boolean) {
            val version = Catalog.findVersion(versionIds[game.id]) ?: game.default
            val ui = states[version.id] ?: InstallUi.Idle
            val state = rememberGame(game, version, ui,
                onVersion = { val i = game.versions.indexOf(version); versionIds[game.id] = game.versions[(i + 1) % game.versions.size].id },
                onSteamInstall = { if (InstallCenter.isSignedIn(context)) InstallService.start(context, version.id) else signIn = version })
            Box(modifier) {
                if (game.number == 1) PersonaOneScreen(game, version, state, update)
                else ThemedGameScreen(game, version, state, update, showUpdate = !inLibrary, onVersion = { versionIds[game.id] = it.id })
            }
            if (state.options) { if (version.runtime == com.logie.persona.backend.catalog.Runtime.PSP_EMULATOR) PspOptionsDialog(state.closeOptions) else WindowsOptionsDialog(state.closeOptions) }
            if (state.openMods) ModsScreen(state.closeMods)
        }

        Surface(Modifier.fillMaxSize(), color = Color(0xFF07070C)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 600.dp
                val libraryWidth = (maxWidth * 0.27f).coerceIn(240.dp, 340.dp)
                if (wide) {
                    // The open Fold (and tablets): every game in a list on the left, as the Blizzard launcher has it; the chosen game's own screen on the right.
                    Row(Modifier.fillMaxSize()) {
                        Library(selected, versionIds, states, artRevision, onSelect = { selected = it; open = it }, onLongPress = { artFor = it },
                            update = update, modifier = Modifier.width(libraryWidth).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(12.dp))
                        key(selected.id) { GamePane(selected, Modifier.weight(1f).fillMaxHeight(), inLibrary = true) }
                    }
                } else {
                    BackHandler(enabled = open != null) { open = null }
                    val game = open
                    if (game == null) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                            Text("PERSONA", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color.White)
                            UpdateBar(update) { Updater.retryInstall(context) }
                            Spacer(Modifier.height(16.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(Catalog.all) { g ->
                                    val shown = Catalog.findVersion(versionIds[g.id]) ?: g.default
                                    Box(g, g == selected, art = remember(artRevision, shown.id) { ArtStore.box(context, shown.id)?.let { f -> BitmapFactory.decodeFile(f.path) } },
                                        onLongClick = { artFor = g }) { selected = g; open = g }
                                }
                            }
                        }
                    } else key(game.id) { GamePane(game, Modifier.fillMaxSize(), inLibrary = false) }
                }
            }
        }
        // A Windows game that failed to start or closed by itself: a pop-up, as the Blizzard launcher shows it, with what became of its report.
        val windows by com.logie.persona.windows.WindowsRuntime.state.collectAsState()
        (windows as? com.logie.persona.windows.WindowsRuntime.State.Failed)?.let { f ->
            val title = Catalog.findVersion(f.versionId)?.let { v -> Catalog.gameOf(v).title } ?: "Persona"
            AlertDialog(
                onDismissRequest = { com.logie.persona.windows.WindowsRuntime.dismiss() },
                title = { Text(title.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                text = { Text(f.message) },
                confirmButton = { TextButton(onClick = { com.logie.persona.windows.WindowsRuntime.dismiss() }) { Text("OK") } },
                dismissButton = {
                    if (com.logie.persona.windows.WindowsLogs.exists(context)) TextButton(onClick = { com.logie.persona.windows.WindowsLogs.share(context) }) { Text("SHARE LOG") }
                },
            )
        }
        // A long press on a box: its box art, per version.
        artFor?.let { g -> BoxArtDialog(g, Catalog.findVersion(versionIds[g.id]) ?: g.default, onChanged = { artRevision++ }, onDismiss = { artFor = null }) }
        // The one place the store is named: its sign-in, shown once.
        signIn?.let { v -> SignInDialog(onDone = { ok -> signIn = null; if (ok) InstallService.start(context, v.id) }) }
        LaunchedEffect(states) {
            states.entries.firstOrNull { it.value is InstallUi.NeedsSignIn }?.let { e ->
                signIn = Catalog.findVersion(e.key); InstallCenter.set(e.key, InstallUi.Idle)
            }
        }
    }
}

/** Persona 1: the whole screen is its skin (`skins/violet`); the menu words are the game's own rows. */
@Composable
private fun PersonaOneScreen(game: Game, version: Version, state: GameState, update: Updater.State) {
    val context = LocalContext.current
    // The chosen word is remembered by its name, so it stays put when the rows change (INSTALL becomes UNINSTALL, PLAY wakes up).
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val index = state.rows.indexOfFirst { it.label == chosen }.takeIf { it >= 0 } ?: state.rows.indexOfFirst { it.usable }.coerceAtLeast(0)
    Box(Modifier.fillMaxSize()) {
        SkinScreen(
            "violet", skinStateOf(game, version.label, state.rows, index, state.gauge),
            onSelect = { i -> chosen = state.rows.getOrNull(i)?.label },
            onActivate = { i -> state.rows.getOrNull(i)?.takeIf { it.usable }?.let { state.perform(it.id) } },
        )
        state.notice?.let { Text(it, color = Color(0xFF2A1041), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp)) }
        if (update is Updater.State.Ready && update.needsPermission) TextButton(onClick = { Updater.retryInstall(context) }, Modifier.align(Alignment.TopStart).statusBarsPadding()) { Text("Allow installing updates, then tap here") }
    }
}

/** Each box's colours: the game's own look until its skin is made (Persona 1 has its own). */
private class Theme(val top: Color, val bottom: Color, val ink: Color, val accent: Color, val band: Color, val bandInk: Color)

private fun themeOf(number: Int): Theme = when (number) {
    2 -> Theme(Color(0xFF6B1F2A), Color(0xFF14050A), Color(0xFFF4E9E4), Color(0xFFE0B04A), Color(0xFFF4E9E4), Color(0xFF6B1F2A))
    3 -> Theme(Color(0xFF1F63B8), Color(0xFF041226), Color(0xFFEAF6FF), Color(0xFF9AD7FF), Color(0xFFEAF6FF), Color(0xFF0B2E5C))
    4 -> Theme(Color(0xFFF6C623), Color(0xFFE09A12), Color(0xFF16120A), Color(0xFF16120A), Color(0xFF16120A), Color(0xFFF6C623))
    5 -> Theme(Color(0xFFD7102C), Color(0xFF1A0206), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF000000), Color(0xFFFFFFFF))
    else -> Theme(Color(0xFF3A1C5A), Color(0xFF12081F), Color.White, Color(0xFFC9B8FF), Color.White, Color(0xFF3A1C5A))
}

/** Persona 2 to 5: the game's colours, its name large and slanted, its versions, and its menu words with the chosen one on a band. */
@Composable
private fun ThemedGameScreen(game: Game, version: Version, state: GameState, update: Updater.State, showUpdate: Boolean, onVersion: (Version) -> Unit) {
    val context = LocalContext.current
    val t = themeOf(game.number)
    var chosen by rememberSaveable(game.id) { mutableStateOf<String?>(null) }
    val index = state.rows.indexOfFirst { it.label == chosen }.takeIf { it >= 0 } ?: state.rows.indexOfFirst { it.usable }.coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.top, t.bottom)))) {
        // the box's number, huge and faint, behind everything
        Text(game.number.toString(), fontSize = 420.sp, fontWeight = FontWeight.Black, color = t.ink.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).graphicsLayer { rotationZ = -8f })
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
            if (showUpdate) UpdateBar(update) { Updater.retryInstall(context) }
            Text("PERSONA ${game.number}", fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = t.ink,
                modifier = Modifier.graphicsLayer { rotationZ = -4f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) })
            Text(game.title.removePrefix("Persona ${game.number}").trim().ifEmpty { version.label }.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, color = t.accent, modifier = Modifier.padding(start = 6.dp))
            if (game.versions.size > 1) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                game.versions.forEach { v ->
                    val on = v == version
                    Text(v.label.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = if (on) t.bandInk else t.ink,
                        modifier = Modifier.graphicsLayer { rotationZ = -4f }.clip(RoundedCornerShape(4.dp)).background(if (on) t.band else t.ink.copy(alpha = 0.10f))
                            .clickable { onVersion(v) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            state.rows.forEachIndexed { i, r ->
                val on = i == index
                Text(r.label, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                    color = when { on -> t.bandInk; r.usable -> t.ink; else -> t.ink.copy(alpha = 0.35f) },
                    modifier = Modifier.padding(vertical = 2.dp).graphicsLayer { rotationZ = -5f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .background(if (on) t.band else Color.Transparent).clickable { if (on) { if (r.usable) state.perform(r.id) } else chosen = r.label }
                        .padding(horizontal = 14.dp))
            }
            state.gauge?.let { g ->
                Text(g.text, color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                LinearProgressIndicator(progress = { g.fraction }, color = t.accent, trackColor = t.ink.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth(0.6f).padding(top = 6.dp))
            }
            state.notice?.let { Text(it, color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            state.shareLog?.let { share ->
                Text("SHARE LOG", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = t.ink.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 12.dp).clickable { share() }.padding(vertical = 6.dp))
            }
        }
    }
}

/** The library, as the Blizzard launcher's: the name, then every game as a row (its box, its name, which versions, a dot when one is installed), the chosen one lit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Library(selected: Game, versionIds: Map<String, String>, states: Map<String, InstallUi>, artRevision: Int, onSelect: (Game) -> Unit,
                    onLongPress: (Game) -> Unit, update: Updater.State, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF0F0D18).copy(alpha = 0.9f)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))) {
        Text("PERSONA", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color.White, modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 4.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { UpdateBar(update) { Updater.retryInstall(context) } }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(Catalog.all) { game ->
                val on = game.id == selected.id
                val shown = Catalog.findVersion(versionIds[game.id]) ?: game.default
                val art = remember(artRevision, shown.id) { ArtStore.box(context, shown.id)?.let { f -> BitmapFactory.decodeFile(f.path) } }
                val ready = game.versions.any { v -> if (v.runtime == com.logie.persona.backend.catalog.Runtime.PSP_EMULATOR) PspStore.isInstalled(context) else states[v.id] is InstallUi.Done }
                val running = game.versions.any { states[it.id] is InstallUi.Running }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                        .combinedClickable(onLongClick = { onLongPress(game) }, onClick = { onSelect(game) }).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(50)).background(if (on) themeOf(game.number).top.let { if (game.number == 4) it else it.copy(alpha = 1f) } else Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(38.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(boxColors.getValue(game.number))), contentAlignment = Alignment.Center) {
                        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text(game.number.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Color.White.copy(alpha = 0.86f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(game.versions.joinToString(" · ") { it.label }, fontSize = 12.sp, color = Color(0xFF9A93AE), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (running || ready) Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (running) Color(0xFF45B4FF) else Color(0xFF39D98A)))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Box(game: Game, selected: Boolean, art: Bitmap?, onLongClick: () -> Unit, onClick: () -> Unit) {
    val colors = boxColors.getValue(game.number)
    Box(
        Modifier.width(if (selected) 132.dp else 112.dp).aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(colors)).combinedClickable(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(game.number.toString(), fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = if (selected) 1f else 0.6f))
    }
}

/** OPTIONS for the Windows games: the game's screen size and the on-screen gamepad (applied at the next PLAY). */
@Composable
private fun WindowsOptionsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(com.logie.persona.windows.WindowsOptions.load(context)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("OPTIONS") },
        text = {
            Column {
                com.logie.persona.windows.WindowsOptions.rows(options).forEach { (label, value, next) ->
                    Row(Modifier.fillMaxWidth().clickable { options = next; com.logie.persona.windows.WindowsOptions.save(context, next) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(value, color = Color(0xFFC9B8FF))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("DONE") } },
    )
}

@Composable
private fun PspOptionsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(PspStore.options(context)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("OPTIONS") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PspOptionRows.rows(options).forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { options = r.next; PspStore.saveOptions(context, options) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(r.value, color = Color(0xFFC9B8FF))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("DONE") } },
    )
}

@Composable
private fun UpdateBar(state: Updater.State, onRetry: () -> Unit) {
    when (state) {
        is Updater.State.Downloading -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Updating the app", color = Color(0xFFB0B0BC), fontSize = 12.sp)
            LinearProgressIndicator(progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f }, Modifier.fillMaxWidth())
        }
        is Updater.State.Ready -> if (state.needsPermission) {
            TextButton(onClick = onRetry) { Text("Allow installing updates, then tap here") }
        }
        is Updater.State.Failed -> Text(state.message, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        else -> {}
    }
}
