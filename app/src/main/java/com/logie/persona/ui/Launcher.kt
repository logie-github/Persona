package com.logie.persona.ui

import androidx.compose.foundation.background
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
        // Each time the app comes to the front: look for a newer build, fetch it and open Android's installer.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Updater.retryInstall(context); Updater.checkAndInstall(context) }
        val update by Updater.state.collectAsState()
        BackHandler(enabled = open != null) { open = null }

        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0F)) {
            val game = open
            if (game == null) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                    Text("PERSONA", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color.White)
                    UpdateBar(update) { Updater.retryInstall(context) }
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(Catalog.all) { g -> Box(g, g == selected) { selected = g; open = g } }
                    }
                }
            } else {
                val version = Catalog.findVersion(versionIds[game.id]) ?: game.default
                val ui = states[version.id] ?: InstallUi.Idle
                val state = rememberGame(game, version, ui,
                    onVersion = { val i = game.versions.indexOf(version); versionIds[game.id] = game.versions[(i + 1) % game.versions.size].id },
                    onSteamInstall = { if (InstallCenter.isSignedIn(context)) InstallService.start(context, version.id) else signIn = version })
                if (game.number == 1) PersonaOneScreen(game, version, state, update)
                else PlainGameScreen(game, version, state, update, onVersion = { versionIds[game.id] = it.id })
                if (state.options) PspOptionsDialog(state.closeOptions)
            }
        }
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

/** The other games (their skins are not made yet): the title, the version chips and the menu as a list. */
@Composable
private fun PlainGameScreen(game: Game, version: Version, state: GameState, update: Updater.State, onVersion: (Version) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        UpdateBar(update) { Updater.retryInstall(context) }
        Text(game.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (game.versions.size > 1) Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            game.versions.forEach { v -> FilterChip(selected = v == version, onClick = { onVersion(v) }, label = { Text(v.label) }) }
        } else Text(version.label, color = Color(0xFFB0B0BC), modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        state.rows.forEach { r ->
            TextButton(enabled = r.usable, onClick = { state.perform(r.id) }) { Text(r.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp) }
        }
        state.gauge?.let { g -> Text(g.text, color = Color.White); LinearProgressIndicator(progress = { g.fraction }, Modifier.fillMaxWidth().padding(top = 8.dp)) }
        state.notice?.let { Text(it, color = Color(0xFFFFB4AB)) }
    }
}

@Composable
private fun Box(game: Game, selected: Boolean, onClick: () -> Unit) {
    val colors = boxColors.getValue(game.number)
    Box(
        Modifier.width(if (selected) 132.dp else 112.dp).aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(colors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(game.number.toString(), fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = if (selected) 1f else 0.6f))
    }
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
