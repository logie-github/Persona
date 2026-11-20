package com.logie.persona.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.logie.persona.BuildConfig
import com.logie.persona.art.ArtStore
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.catalog.Runtime
import com.logie.persona.backend.catalog.Version
import com.logie.persona.backend.psp.PspOptionRows
import com.logie.persona.game.GameLog
import com.logie.persona.install.InstallCenter
import com.logie.persona.psp.PspStore
import com.logie.persona.reports.BugReports
import com.logie.persona.update.PatchNotes
import com.logie.persona.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A game's menu (hold its row or box, or More)

/** The menu of a game held, as the Blizzard launcher's: its options, its mods (Persona 1), its box art, its log, and taking it off the phone. */
@Composable
internal fun HoldMenu(game: Game, version: Version, onDismiss: () -> Unit, onOptions: () -> Unit, onArt: () -> Unit, onMods: () -> Unit) {
    val context = LocalContext.current
    val states by InstallCenter.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().panel().padding(vertical = 12.dp)) {
            Text("${game.title}${if (game.versions.size > 1) " (${version.label})" else ""}", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp))
            HoldItem(Icons.Default.Build, "Options") { onDismiss(); onOptions() }
            if (version.runtime == Runtime.PSP_EMULATOR) HoldItem(Icons.Default.Star, "Mods") { onDismiss(); onMods() }
            HoldItem(Icons.Default.AccountBox, "Box art") { onDismiss(); onArt() }
            HoldItem(Icons.Default.Send, "Send the log") {
                onDismiss()
                sendLog(context, "${game.title} (${version.label})")
            }
            if (installed(context, version, states)) HoldItem(Icons.Default.Delete, "Uninstall") { confirm = true }
        }
    }
    if (confirm) ConfirmDialog(
        title = "Uninstall ${game.title} (${version.label})?",
        text = "The game's files are deleted from the phone. Your saves stay.",
        confirm = "Uninstall",
        onDismiss = { confirm = false },
        onConfirm = {
            confirm = false; onDismiss()
            if (version.runtime == Runtime.PSP_EMULATOR) PspStore.uninstall(context) else InstallCenter.uninstall(context, version)
        },
    )
}

/** The run's log, the last game's log and the phone's state as a report now, told in a toast what became of it (as the Blizzard launcher's). */
internal fun sendLog(context: android.content.Context, what: String) {
    val app = context.applicationContext
    if (!BugReports.configured(app)) {
        BugReports.report(app, "Log sent: $what", "The person pressed Send the log", listOf("Game" to what), log = GameLog.snapshot().ifEmpty { null }, attach = true, force = true)
        Toast.makeText(app, "This build can't send reports. The log is kept on the phone.", Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(app, "Sending the log…", Toast.LENGTH_SHORT).show()
    BugReports.report(app, "Log sent: $what", "The person pressed Send the log", listOf("Game" to what), log = GameLog.snapshot().ifEmpty { null },
        attach = true, force = true,
        onResult = { r -> android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(app, r.say(), Toast.LENGTH_LONG).show() } })
}

@Composable
private fun HoldItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Modern.Muted)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Modern.Text)
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 420.dp).panel().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Modern.Muted)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameButton("Cancel", primary = false, onClick = onDismiss)
                FrameButton(confirm, onClick = onConfirm)
            }
        }
    }
}

// Options

/** The Windows games' options: the game's screen size and the on-screen gamepad (applied at the next PLAY). */
@Composable
internal fun WindowsOptionsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(com.logie.persona.windows.WindowsOptions.load(context)) }
    OptionsSheet(onClose, com.logie.persona.windows.WindowsOptions.rows(options).map { (label, value, next) ->
        Triple(label, value) { options = next; com.logie.persona.windows.WindowsOptions.save(context, next) }
    })
}

/** Persona 1's emulator options. */
@Composable
internal fun PspOptionsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(PspStore.options(context)) }
    OptionsSheet(onClose, PspOptionRows.rows(options).map { r -> Triple(r.label, r.value) { options = r.next; PspStore.saveOptions(context, options) } })
}

@Composable
private fun OptionsSheet(onClose: () -> Unit, rows: List<Triple<String, String, () -> Unit>>) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Sheet("Options", onClose, maxWidth = 520.dp) {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, value, next) in rows) {
                    Row(Modifier.fillMaxWidth().box().clickable(onClick = next).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(value, style = MaterialTheme.typography.titleSmall.copy(color = Modern.AccentBright))
                    }
                }
                Text("Tap a line to change it. Applied at the next Play.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// Settings (the gear)

/**
 * The launcher's settings, as the Blizzard launcher's: **Launcher** (account, box art, reports, the app) and **Games** (the patch notes,
 * shown here and nowhere else, then each version with its box art and options).
 */
@Composable
internal fun SettingsDialog(versionOf: (Game) -> Version, artRevision: Int, onArt: (Game) -> Unit, onOptions: (Version) -> Unit, onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(14.dp).widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 760.dp).panel()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                FrameTitle("Settings", Modifier.weight(1f))
                CloseButton(onDismiss)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Tab("Launcher", page == 0) { page = 0 }
                Tab("Games", page == 1) { page = 1 }
            }
            Divider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (page == 0) LauncherPage() else GamesPage(versionOf, artRevision, onArt, onOptions)
            }
        }
    }
}

@Composable
private fun Tab(text: String, on: Boolean, onClick: () -> Unit) {
    val color by androidx.compose.animation.animateColorAsState(if (on) Modern.Text else Modern.Muted, label = "tab")
    Column(Modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(color = color))
        Spacer(Modifier.size(6.dp))
        Box(Modifier.size(width = 28.dp, height = 3.dp).clip(CircleShape).background(if (on) Modern.Accent else Color.Transparent))
    }
}

@Composable
private fun LauncherPage() {
    val context = LocalContext.current
    SettingCard("Account") {
        val name = remember { InstallCenter.accountName(context) }
        Text(if (name != null) "Signed in as $name. Installs and updates use this sign-in." else "Not signed in. Install on Persona 3, 4 or 5 asks for the sign-in once.",
            style = MaterialTheme.typography.bodyMedium, color = if (name != null) Modern.Text else Modern.Muted)
    }
    SettingCard("Box art") { SteamGridKeyCard() }
    ReportsCard()
    SettingCard("About") {
        Text("Persona ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
        Text("Updates come by themselves: the app looks for a newer build each time it comes to the front.", style = MaterialTheme.typography.bodySmall)
    }
}

/** The SteamGridDB key: with it, Box art browses each game's pictures there. */
@Composable
private fun SteamGridKeyCard() {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(ArtStore.key(context)) }
    var text by remember { mutableStateOf("") }
    Text(if (saved != null) "SteamGridDB connected: Box art shows its pictures for each game." else "SteamGridDB not connected: Box art asks for a picture on the phone.",
        style = MaterialTheme.typography.bodyMedium, color = if (saved != null) Modern.AccentBright else Modern.Muted)
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(if (saved != null) "Replace the key" else "SteamGridDB API key", color = Modern.Muted) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Modern.Text, unfocusedTextColor = Modern.Text, focusedBorderColor = Modern.Accent,
            unfocusedBorderColor = Modern.OutlineStrong, cursorColor = Modern.Accent),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FrameButton("Save", primary = text.isNotBlank()) { if (text.isNotBlank()) { ArtStore.setKey(context, text); saved = text.trim(); text = "" } }
        if (saved != null) FrameButton("Remove", primary = false) { ArtStore.clearKey(context); saved = null }
    }
}

/** What became of the app's reports, and a way to send the app's log now (as the Blizzard launcher's ReportsCard). */
@Composable
internal fun ReportsCard() {
    val context = LocalContext.current
    var history by remember { mutableStateOf(BugReports.history(context, 5)) }
    var waiting by remember { mutableIntStateOf(BugReports.waiting(context)) }
    SettingCard("Reports") {
        Text(
            when {
                !BugReports.configured(context) -> "This build has no way to send reports; they stay on the phone."
                waiting > 0 -> "$waiting waiting to be sent (they go again every minute)."
                else -> "None waiting."
            },
            style = MaterialTheme.typography.bodyMedium, color = if (waiting > 0) Modern.Gold else Modern.AccentBright,
        )
        if (history.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            history.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        FrameButton("Send the log now", primary = false, modifier = Modifier.fillMaxWidth()) {
            sendLog(context, "from Settings")
            history = BugReports.history(context, 5)
            waiting = BugReports.waiting(context)
        }
    }
}

/** The patch notes first (they show here and nowhere else), then every version of every game: its box art, its name, its settings. */
@Composable
private fun GamesPage(versionOf: (Game) -> Version, artRevision: Int, onArt: (Game) -> Unit, onOptions: (Version) -> Unit) {
    PatchNotesCard()
    for (game in Catalog.all) {
        SettingCard(game.title) {
            MenuLookRow(game)
            for (v in game.versions) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BoxPicture(game, v, artRevision, Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(v.label, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FrameButton("Box art", primary = false) { onArt(game) }
                            FrameButton("Options", primary = false) { onOptions(v) }
                        }
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Modern.Muted)
                }
            }
        }
    }
}

/** What each update brought, one line a change, newest first ([PatchNotes]). */
@Composable
private fun PatchNotesCard() {
    val context = LocalContext.current
    val kept by PatchNotes.kept.collectAsState()
    LaunchedEffect(Unit) { if (PatchNotes.kept.value == null) withContext(Dispatchers.IO) { PatchNotes.load(context) } }
    var all by remember { mutableStateOf(false) }
    val notes = kept.orEmpty()
    SettingCard("Patch notes") {
        if (notes.isEmpty()) {
            Text("Each update's notes show here, a line for each change, once the launcher has looked for updates.", style = MaterialTheme.typography.bodySmall)
        } else {
            val shown = if (all) notes else notes.take(3)
            for (release in shown) PatchNoteRow(release)
            if (notes.size > shown.size) Text(
                "Earlier updates (${notes.size - shown.size})", style = MaterialTheme.typography.labelLarge, color = Modern.AccentBright,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = true }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PatchNoteRow(release: PatchNotes.Release) {
    val installed = BuildConfig.VERSION_CODE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(78.dp)) {
            Text("1.0.${release.versionCode}", style = MaterialTheme.typography.labelLarge, color = if (release.versionCode == installed) Modern.AccentBright else Modern.Text)
            when {
                release.versionCode == installed -> Text("installed", style = MaterialTheme.typography.bodySmall, color = Modern.AccentBright)
                release.versionCode > installed -> Text("on its way", style = MaterialTheme.typography.bodySmall, color = Modern.Gold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in release.lines) Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// The app's update

/** The update, at the bottom centre of the launcher while it downloads; then Android's installer (the Blizzard launcher's UpdateBanner). */
@Composable
internal fun UpdateBanner() {
    val context = LocalContext.current
    // Each time the app comes to the front: look for a newer build; back from "install unknown apps": on to the installer.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Updater.retryInstall(context); Updater.checkAndInstall(context) }
    val state by Updater.state.collectAsState()
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(state !is Updater.State.Idle, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            Column(Modifier.padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().panel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is Updater.State.Downloading -> {
                        Text("Updating to ${s.update.title}", style = MaterialTheme.typography.titleMedium)
                        if (s.total > 0) {
                            LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, color = Modern.AccentBright, trackColor = Modern.SurfaceTop, modifier = Modifier.fillMaxWidth())
                            Text("${s.done / (1024 * 1024)} / ${s.total / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
                        } else LinearProgressIndicator(color = Modern.AccentBright, trackColor = Modern.SurfaceTop, modifier = Modifier.fillMaxWidth())
                    }
                    is Updater.State.Ready -> {
                        Text("${s.update.title} is ready", style = MaterialTheme.typography.titleMedium)
                        if (s.needsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            FrameButton("Allow installs") { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                        } else FrameButton("Install") { context.startActivity(Updater.installIntent(context, s.apk)) }
                    }
                    is Updater.State.Failed -> {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        FrameButton("OK", primary = false) { Updater.dismiss() }
                    }
                    Updater.State.Idle -> {}
                }
            }
        }
    }
}

/**
 * A game's menu: its character (a person picture, best with a transparent background: [characterFile]) and its lettering (a font file the
 * person has, such as the game's own: [customFontFile]); each put back with Reset.
 */
@Composable
private fun MenuLookRow(game: Game) {
    val context = LocalContext.current
    val revision by MenuAssets.revision.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val pickCharacter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = if (MenuFiles.setCharacter(context, game.number, uri)) null else "That picture couldn't be read."
    }
    val pickFont = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = if (MenuFiles.setFont(context, game.number, uri)) null else "That isn't a font file (.ttf or .otf) the phone can read."
    }
    val character = remember(revision) { characterFile(context, game.number) != null }
    val font = remember(revision) { customFontFile(context, game.number) != null }
    Text(
        listOfNotNull(if (character) "your character" else null, if (font) "your font" else null).let { if (it.isEmpty()) "The menu's own character and lettering" else "The menu has ${it.joinToString(" and ")}" },
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FrameButton("Character", primary = false) { pickCharacter.launch(arrayOf("image/*")) }
        FrameButton("Font", primary = false) { pickFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) }
        if (character || font) FrameButton("Reset", primary = false) { MenuFiles.reset(context, game.number) }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Modern.Gold) }
}
