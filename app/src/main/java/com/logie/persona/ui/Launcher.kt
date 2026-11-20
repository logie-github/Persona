package com.logie.persona.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logie.persona.art.ArtStore
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.catalog.Runtime
import com.logie.persona.backend.catalog.Version
import com.logie.persona.install.InstallCenter
import com.logie.persona.install.InstallService
import com.logie.persona.install.InstallUi
import com.logie.persona.psp.PspStore
import com.logie.persona.windows.WindowsRuntime

/** Hold a game (its row, its box, its More button): its menu ([HoldMenu]). */
internal val LocalOpenHold = staticCompositionLocalOf<(Game) -> Unit> { {} }

/**
 * The launcher, as the Blizzard launcher's home has it: on the open Fold every game in a list on the left (its box, its name, its
 * versions, a dot when one is installed) and the chosen game's own screen on the right; on the cover screen the game's screen with a
 * row of boxes under it. The game's screen is its Persona look ([GameScreen]: its colours, its name, its versions and its menu words);
 * the gear opens the settings (patch notes, reports, account, art) and More (or holding a game) its menu. Starting a Windows game shows
 * its loading screen, and a game that fails or closes by itself says so in a pop-up with what became of its report.
 */
@Composable
fun Launcher() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home", android.content.Context.MODE_PRIVATE) }
    // The game the home shows: the one chosen last time.
    var selectedId by rememberSaveable { mutableStateOf(prefs.getString("game", null) ?: Catalog.all.first().id) }
    val selected = Catalog.all.firstOrNull { it.id == selectedId } ?: Catalog.all.first()
    LaunchedEffect(selectedId) { prefs.edit().putString("game", selectedId).apply() }
    val versionIds = remember { mutableStateMapOf<String, String>().apply { Catalog.all.forEach { g -> prefs.getString("version-${g.id}", null)?.let { put(g.id, it) } } } }
    fun versionOf(g: Game) = Catalog.findVersion(versionIds[g.id])?.takeIf { it in g.versions } ?: g.default
    fun chooseVersion(g: Game, v: Version) { versionIds[g.id] = v.id; prefs.edit().putString("version-${g.id}", v.id).apply() }
    val states by InstallCenter.state.collectAsState()
    LaunchedEffect(Unit) { InstallCenter.refresh(context, Catalog.all.flatMap { it.versions }) }
    var signIn by remember { mutableStateOf<Version?>(null) }
    var artFor by remember { mutableStateOf<Game?>(null) }
    var artRevision by remember { mutableIntStateOf(0) }
    var holdFor by remember { mutableStateOf<Game?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<Version?>(null) }
    var modsOpen by remember { mutableStateOf(false) }

    // The accent follows the chosen game's colours.
    LaunchedEffect(selected.number) { PersonaColors.of(selected.number).let { Modern.Accent = it.accent; Modern.OnAccent = it.onAccent } }

    PersonaTheme {
        CompositionLocalProvider(LocalOpenHold provides { g: Game -> holdFor = g }) {
            @Composable fun GamePane(game: Game, modifier: Modifier) {
                val version = versionOf(game)
                val ui = states[version.id] ?: InstallUi.Idle
                val state = rememberGame(game, version, ui,
                    onVersion = { val i = game.versions.indexOf(version); chooseVersion(game, game.versions[(i + 1) % game.versions.size]) },
                    onSteamInstall = { if (InstallCenter.isSignedIn(context)) InstallService.start(context, version.id) else signIn = version })
                Box(modifier) {
                    GameScreen(game, version, state, onVersion = { chooseVersion(game, it) })
                    TopBar(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 10.dp, end = 14.dp), onMore = { holdFor = game }, onSettings = { settingsOpen = true })
                }
                if (state.options) OptionsDialog(version, state.closeOptions)
                if (state.openMods) ModsScreen(state.closeMods)
            }

            Box(Modifier.fillMaxSize().background(Modern.Background)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val screenWidth = maxWidth
                    val wide = screenWidth >= 600.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            Library(selected, ::versionOf, states, artRevision, onSelect = { selectedId = it.id },
                                Modifier.width((screenWidth * 0.28f).coerceIn(250.dp, 340.dp)).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(12.dp))
                            key(selected.id) { GamePane(selected, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            key(selected.id) { GamePane(selected, Modifier.weight(1f).fillMaxWidth()) }
                            Shelf(selected, ::versionOf, artRevision, onSelect = { selectedId = it.id }, Modifier.navigationBarsPadding().padding(vertical = 14.dp))
                        }
                    }
                }
                UpdateBanner()
            }

            holdFor?.let { g ->
                HoldMenu(g, versionOf(g), onDismiss = { holdFor = null },
                    onOptions = { optionsFor = versionOf(g) }, onArt = { artFor = g }, onMods = { modsOpen = true })
            }
            optionsFor?.let { v -> OptionsDialog(v) { optionsFor = null } }
            if (modsOpen) ModsScreen { modsOpen = false }
            artFor?.let { g -> BoxArtDialog(g, versionOf(g), onChanged = { artRevision++ }, onDismiss = { artFor = null }) }
            if (settingsOpen) SettingsDialog(versionOf = ::versionOf, artRevision = artRevision, onArt = { artFor = it }, onOptions = { optionsFor = it }, onDismiss = { settingsOpen = false })
            RuntimeOverlay()
            // The one place the store is named: its sign-in, shown once.
            signIn?.let { v -> SignInDialog(onDone = { ok -> signIn = null; if (ok) InstallService.start(context, v.id) }) }
            LaunchedEffect(states) {
                states.entries.firstOrNull { it.value is InstallUi.NeedsSignIn }?.let { e ->
                    signIn = Catalog.findVersion(e.key); InstallCenter.set(e.key, InstallUi.Idle)
                }
            }
        }
    }
}

/** A version's OPTIONS: the PSP emulator's for Persona 1, the Windows games' screen size and gamepad for the others. */
@Composable
private fun OptionsDialog(version: Version, onClose: () -> Unit) {
    if (version.runtime == Runtime.PSP_EMULATOR) PspOptionsDialog(onClose) else WindowsOptionsDialog(onClose)
}

// The bar over the game's screen

@Composable
private fun TopBar(modifier: Modifier, onMore: () -> Unit, onSettings: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIcon(Icons.Default.MoreVert, "More", onMore)
        RoundIcon(Icons.Default.Settings, "Settings", onSettings)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, label = "press")
    Box(
        Modifier.size(44.dp).graphicsLayer { val k = 1f - 0.06f * press; scaleX = k; scaleY = k }
            .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f + 0.1f * press)).border(1.dp, Modern.OutlineStrong, CircleShape)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

// The library

/** A game's box: its art for the version shown, else its colours and its number. */
@Composable
internal fun BoxPicture(game: Game, version: Version, artRevision: Int, modifier: Modifier, numberSize: Int = 20) {
    val context = LocalContext.current
    val art: Bitmap? = remember(artRevision, version.id) { ArtStore.box(context, version.id)?.let { f -> BitmapFactory.decodeFile(f.path) } }
    Box(modifier.background(Brush.verticalGradient(PersonaColors.of(game.number).box)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(game.number.toString(), fontSize = numberSize.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

/** Whether [version] is on the phone (Persona 1: its disc image imported). */
internal fun installed(context: android.content.Context, version: Version, states: Map<String, InstallUi>): Boolean =
    if (version.runtime == Runtime.PSP_EMULATOR) PspStore.isInstalled(context) else states[version.id] is InstallUi.Done

/** The open Fold's library, as the Blizzard launcher's: the name, then every game as a row, the chosen one lit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Library(selected: Game, versionOf: (Game) -> Version, states: Map<String, InstallUi>, artRevision: Int, onSelect: (Game) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val openHold = LocalOpenHold.current
    Column(modifier.clip(RoundedCornerShape(Modern.SheetCorner)).background(Modern.Surface.copy(alpha = 0.92f)).border(1.dp, Modern.Outline, RoundedCornerShape(Modern.SheetCorner))) {
        Text("PERSONA", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color.White,
            modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp).graphicsLayer { rotationZ = -3f; transformOrigin = TransformOrigin(0f, 0.5f) })
        LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(Catalog.all, key = { _, g -> g.id }) { _, game ->
                val on = game.id == selected.id
                val shown = versionOf(game)
                val fill by animateColorAsState(if (on) Color.White.copy(alpha = 0.09f) else Color.Transparent, label = "row")
                val ready = game.versions.any { installed(context, it, states) }
                val running = game.versions.any { states[it.id] is InstallUi.Running }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fill)
                        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onSelect(game) },
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); openHold(game) })
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).height(30.dp).clip(CircleShape).background(if (on) PersonaColors.of(game.number).accent else Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    BoxPicture(game, shown, artRevision, Modifier.width(38.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium.copy(color = if (on) Modern.Text else Modern.Text.copy(alpha = 0.86f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(game.versions.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (running || ready) Box(Modifier.size(8.dp).clip(CircleShape).background(if (running) Modern.AccentBright else Modern.Good))
                }
            }
        }
    }
}

/** The cover screen's library: a row of boxes under the game, the chosen one raised and outlined. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Shelf(selected: Game, versionOf: (Game) -> Version, artRevision: Int, onSelect: (Game) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val openHold = LocalOpenHold.current
    val list = rememberLazyListState()
    LaunchedEffect(selected.id) {
        val index = Catalog.all.indexOfFirst { it.id == selected.id }
        if (index >= 0) list.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(modifier, state = list, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(Catalog.all, key = { _, g -> g.id }) { _, game ->
            val on = game.id == selected.id
            val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lift")
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxPicture(game, versionOf(game), artRevision, Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    .graphicsLayer { translationY = -6.dp.toPx() * lift; val s = 1f + 0.04f * lift; scaleX = s; scaleY = s }
                    .shadow((4 + 10 * lift).dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) PersonaColors.of(game.number).accent else Modern.Outline, RoundedCornerShape(12.dp))
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onSelect(game) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); openHold(game) }),
                    numberSize = 40)
                Spacer(Modifier.height(6.dp))
                Text("PERSONA ${game.number}", style = MaterialTheme.typography.bodySmall.copy(color = if (on) Modern.Text else Modern.Muted, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

// The game's own screen

/** Each game's colours: its screen, its box, the launcher's accent while it is chosen. */
internal class PersonaColors(val top: Color, val bottom: Color, val ink: Color, val accent: Color, val band: Color, val bandInk: Color, val box: List<Color>, val onAccent: Color = Color.White) {
    companion object {
        fun of(number: Int): PersonaColors = when (number) {
            1 -> PersonaColors(Color(0xFF3A1C5A), Color(0xFF12081F), Color.White, Color(0xFF9B6BFF), Color.White, Color(0xFF3A1C5A), listOf(Color(0xFF3A1C5A), Color(0xFF12081F)))
            2 -> PersonaColors(Color(0xFF6B1F2A), Color(0xFF14050A), Color(0xFFF4E9E4), Color(0xFFE0B04A), Color(0xFFF4E9E4), Color(0xFF6B1F2A), listOf(Color(0xFF6B1F2A), Color(0xFF1C080C)))
            3 -> PersonaColors(Color(0xFF1F63B8), Color(0xFF041226), Color(0xFFEAF6FF), Color(0xFF3D9BFF), Color(0xFFEAF6FF), Color(0xFF0B2E5C), listOf(Color(0xFF1F63B8), Color(0xFF07182E)))
            4 -> PersonaColors(Color(0xFFF6C623), Color(0xFFE09A12), Color(0xFF16120A), Color(0xFFF6C623), Color(0xFF16120A), Color(0xFFF6C623), listOf(Color(0xFFE8B923), Color(0xFF3A2C05)), onAccent = Color(0xFF16120A))
            else -> PersonaColors(Color(0xFFD7102C), Color(0xFF1A0206), Color.White, Color(0xFFE5112F), Color.Black, Color.White, listOf(Color(0xFFD7102C), Color(0xFF1A0206)))
        }
    }
}

/**
 * A game's screen in its own Persona look (what Persona 2 to 5 had, now every game's, Persona 1 in its violet): its colours, its number
 * huge and faint, its name large and slanted, its versions, and its menu words with the chosen one on a band; what is happening under them.
 */
@Composable
private fun GameScreen(game: Game, version: Version, state: GameState, onVersion: (Version) -> Unit) {
    val t = PersonaColors.of(game.number)
    var chosen by rememberSaveable(game.id) { mutableStateOf<String?>(null) }
    val index = state.rows.indexOfFirst { it.label == chosen }.takeIf { it >= 0 } ?: state.rows.indexOfFirst { it.usable }.coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.top, t.bottom)))) {
        Text(game.number.toString(), fontSize = 420.sp, fontWeight = FontWeight.Black, color = t.ink.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).graphicsLayer { rotationZ = -8f })
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp).verticalScroll(rememberScrollState())) {
            Text("PERSONA ${game.number}", fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = t.ink,
                modifier = Modifier.padding(end = 110.dp).graphicsLayer { rotationZ = -4f; transformOrigin = TransformOrigin(0f, 0.5f) })
            Text(game.title.removePrefix("Persona ${game.number}").removePrefix(":").trim().ifEmpty { version.label }.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, color = t.accent.takeIf { game.number != 4 } ?: t.ink, modifier = Modifier.padding(start = 6.dp))
            if (game.versions.size > 1) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                game.versions.forEach { v ->
                    val on = v == version
                    Text(v.label.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = if (on) t.bandInk else t.ink,
                        modifier = Modifier.graphicsLayer { rotationZ = -4f }.clip(RoundedCornerShape(4.dp)).background(if (on) t.band else t.ink.copy(alpha = 0.10f))
                            .clickable { onVersion(v) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            state.rows.forEachIndexed { i, r ->
                val on = i == index
                Text(r.label, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                    color = when { on -> t.bandInk; r.usable -> t.ink; else -> t.ink.copy(alpha = 0.35f) },
                    modifier = Modifier.padding(vertical = 2.dp).graphicsLayer { rotationZ = -5f; transformOrigin = TransformOrigin(0f, 0.5f) }
                        .background(if (on) t.band else Color.Transparent).clickable { if (on) { if (r.usable) state.perform(r.id) } else chosen = r.label }
                        .padding(horizontal = 14.dp))
            }
            state.gauge?.let { g ->
                Text(g.text, color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                LinearProgressIndicator(progress = { g.fraction }, color = if (game.number == 4) t.ink else t.accent, trackColor = t.ink.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth(0.6f).padding(top = 6.dp))
            }
            state.notice?.let { Text(it, color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Starting a Windows game

/** The loading screen while a Windows game is being started (the runtime's install, Windows, the game), as the Blizzard launcher's; then what went wrong, if anything. */
@Composable
private fun RuntimeOverlay() {
    val state by WindowsRuntime.state.collectAsState()
    when (val s = state) {
        is WindowsRuntime.State.Preparing -> Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val version = Catalog.findVersion(s.versionId)
            val game = version?.let { Catalog.gameOf(it) }
            val t = PersonaColors.of(game?.number ?: 1)
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.top, t.bottom)))) {
                Text((game?.number ?: 1).toString(), fontSize = 420.sp, fontWeight = FontWeight.Black, color = t.ink.copy(alpha = 0.07f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).graphicsLayer { rotationZ = -8f })
                Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                    Text("PERSONA ${game?.number ?: ""}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = t.ink,
                        modifier = Modifier.graphicsLayer { rotationZ = -4f; transformOrigin = TransformOrigin(0f, 0.5f) })
                    val step = when (s.step) {
                        WindowsRuntime.Step.RUNTIME -> "Getting the runtime ready" + (s.progress?.let { " ${(it * 100).toInt()}%" } ?: "")
                        WindowsRuntime.Step.WINDOWS -> "Setting up"
                        WindowsRuntime.Step.STARTING -> "Starting"
                    }
                    Text(step.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = t.ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                    LinearProgressIndicator(color = if (game?.number == 4) t.ink else t.accent, trackColor = t.ink.copy(alpha = 0.15f), modifier = Modifier.width(260.dp).padding(top = 12.dp, start = 4.dp))
                }
            }
        }
        is WindowsRuntime.State.Failed -> {
            val game = Catalog.findVersion(s.versionId)?.let { Catalog.gameOf(it) }
            Dialog(onDismissRequest = { WindowsRuntime.dismiss() }) {
                Column(Modifier.panel().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text((game?.title ?: "Persona").uppercase(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp))
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    FrameButton("OK", Modifier.align(Alignment.End)) { WindowsRuntime.dismiss() }
                }
            }
        }
        else -> {}
    }
}
