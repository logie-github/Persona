package com.logie.persona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.logie.persona.backend.catalog.Origin
import com.logie.persona.backend.catalog.Version
import com.logie.persona.install.InstallCenter
import com.logie.persona.install.InstallService
import com.logie.persona.install.InstallUi
import com.logie.persona.install.Wording
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
        var selected by remember { mutableStateOf(Catalog.all.last()) }
        val versionIds = remember { mutableStateMapOf<String, String>() }
        val version = Catalog.findVersion(versionIds[selected.id]) ?: selected.default
        val states by InstallCenter.state.collectAsState()
        LaunchedEffect(Unit) { InstallCenter.refresh(context, Catalog.all.flatMap { it.versions }) }
        var signIn by remember { mutableStateOf<Version?>(null) }
        // Each time the app comes to the front: look for a newer build, fetch it and open Android's installer.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Updater.retryInstall(context); Updater.checkAndInstall(context) }
        val update by Updater.state.collectAsState()

        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0F)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text("PERSONA", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = Color.White)
                UpdateBar(update) { Updater.retryInstall(context) }
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(Catalog.all) { g -> Box(g, g == selected) { selected = g } }
                }
                Spacer(Modifier.height(16.dp))
                Text(selected.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (selected.versions.size > 1) {
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.versions.forEach { v ->
                            FilterChip(selected = v == version, onClick = { versionIds[selected.id] = v.id }, label = { Text(v.label) })
                        }
                    }
                } else Text(version.label, color = Color(0xFFB0B0BC), modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                Action(version, states[version.id] ?: InstallUi.Idle) {
                    if (InstallCenter.isSignedIn(context)) InstallService.start(context, version.id) else signIn = version
                }
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
private fun Action(version: Version, ui: InstallUi, onInstall: () -> Unit) {
    when {
        version.origin == Origin.DISC_IMAGE -> Button(onClick = {}, enabled = false) { Text(Wording.NEEDS_DISC) }
        ui is InstallUi.Running -> Column {
            Text(Wording.step(ui.progress.step), color = Color.White)
            LinearProgressIndicator(progress = { ui.progress.fraction }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        ui is InstallUi.Done -> Button(onClick = {}, enabled = false) { Text("Play (the runtime isn't in the app yet)") }
        ui is InstallUi.NotInLibrary -> Text(Wording.NOT_IN_LIBRARY, color = Color(0xFFFFB4AB))
        else -> Column {
            if (ui is InstallUi.Failed) Text(Wording.FAILED, color = Color(0xFFFFB4AB), modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onInstall) { Text(Wording.INSTALL) }
        }
    }
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
