package com.logie.persona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logie.persona.backend.mods.GameBanana
import com.logie.persona.backend.mods.GbFile
import com.logie.persona.backend.mods.GbMod
import com.logie.persona.backend.mods.InstalledMod
import com.logie.persona.backend.mods.ModPlan
import com.logie.persona.backend.mods.UrlWeb
import com.logie.persona.psp.PspStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Lavender = Color(0xFFC9B8FF)
private val Panel = Color(0xFF14103E)

/** What the install is waiting for the person to choose. */
private sealed interface Choice {
    class OneFile(val mod: GbMod, val files: List<GbFile>) : Choice
    class OneVariant(val mod: GbMod, val file: GbFile, val archive: File, val plan: ModPlan) : Choice
}

/**
 * Persona 1's mods, from GameBanana (game 12961): what is installed (on or off, or removed) and what can be added (the most downloaded, or a search). An archive may hold
 * several files or several texture packs; the person chooses when it does. Turning a mod off or removing it puts the game back as it was.
 */
@Composable
fun ModsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mods = remember { PspStore.mods(context) }
    var installed by remember { mutableStateOf(mods?.list().orEmpty()) }
    var tab by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<GbMod>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    val gb = remember { GameBanana(UrlWeb()) }

    fun browse(term: String) {
        scope.launch {
            busy = "Looking"; message = null
            runCatching { withContext(Dispatchers.IO) { if (term.isBlank()) gb.list() else gb.search(term) } }
                .onSuccess { found = it; if (it.isEmpty()) message = "No mods found." }
                .onFailure { message = it.message ?: "Couldn't reach the mod site." }
            busy = null
        }
    }
    LaunchedEffect(Unit) { browse("") }

    fun install(mod: GbMod, file: GbFile, archive: File, variant: Int) {
        scope.launch {
            busy = "Installing ${mod.name}"; message = null
            runCatching { withContext(Dispatchers.IO) { mods!!.install(mod.id, mod.name, mod.version, archive, variant, file.name) } }
                .onSuccess { r -> installed = mods!!.list(); message = if (r.skipped.isEmpty()) null else "${r.skipped.size} files could not be used."; tab = 1 }
                .onFailure { message = it.message ?: "The mod could not be installed." }
            busy = null
        }
    }
    fun download(mod: GbMod, file: GbFile) {
        scope.launch {
            busy = "Downloading ${mod.name}"; message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val archive = File(context.cacheDir, "mods/${mod.id}-${file.id}/${file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                    archive.parentFile?.mkdirs()
                    UrlWeb().download(file.url, archive)
                    archive to mods!!.plan(archive, file.name)
                }
            }.onSuccess { (archive, plan) ->
                if (plan.variants.size > 1) choice = Choice.OneVariant(mod, file, archive, plan) else install(mod, file, archive, 0)
            }.onFailure { message = it.message ?: "The download failed." }
            if (choice == null) busy = null
        }
    }
    fun pick(mod: GbMod) {
        scope.launch {
            busy = "Opening ${mod.name}"; message = null
            runCatching { withContext(Dispatchers.IO) { gb.profile(mod.id) } }
                .onSuccess { p ->
                    // a RAR can't be opened yet: where the mod also has a zip or 7z, those are what is offered
                    val files = p.files.filter { it.url.isNotBlank() }.let { all -> all.filter { !it.name.endsWith(".rar", true) }.ifEmpty { all } }
                    when { files.isEmpty() -> message = "That mod has no file to download."; files.size == 1 -> download(p, files[0]); else -> choice = Choice.OneFile(p, files) }
                }
                .onFailure { message = it.message ?: "Couldn't open that mod." }
            if (choice == null && busy?.startsWith("Opening") == true) busy = null
        }
    }
    fun change(block: () -> Unit) { scope.launch { busy = "Working"; withContext(Dispatchers.IO) { runCatching(block).onFailure { message = it.message } }; installed = mods?.list().orEmpty(); busy = null } }

    Dialog(onDismissRequest = { if (busy == null) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Panel).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MODS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 4.sp)
                TextButton(onClick = onDismiss, enabled = busy == null) { Text("DONE") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("ADD") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("INSTALLED (${installed.size})") })
            }
            busy?.let { Text(it, color = Lavender); LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
            message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(vertical = 4.dp)) }
            if (tab == 0) {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search mods") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { browse(text) }))
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    items(found, key = { it.id }) { m ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = busy == null && mods != null) { pick(m) }.padding(vertical = 10.dp)) {
                            Text(m.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${m.author} · ${m.category} · ${m.likes} likes · ${m.views} views", color = Lavender, fontSize = 12.sp)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(installed, key = { it.id }) { m -> InstalledRow(m, busy == null, onToggle = { on -> change { mods!!.setEnabled(m.id, on) } }, onRemove = { change { mods!!.uninstall(m.id) } }) }
                }
            }
        }
    }

    when (val c = choice) {
        is Choice.OneFile -> AlertDialog(
            onDismissRequest = { choice = null; busy = null }, title = { Text("Which file?") },
            text = { Column { c.files.forEach { f -> TextButton(onClick = { choice = null; download(c.mod, f) }) { Text("${f.name}  (${f.bytes / 1_000_000} MB)") } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { choice = null; busy = null }) { Text("CANCEL") } },
        )
        is Choice.OneVariant -> AlertDialog(
            onDismissRequest = { choice = null; busy = null }, title = { Text("Which version?") },
            text = { Column { c.plan.variants.forEachIndexed { i, v -> TextButton(onClick = { choice = null; install(c.mod, c.file, c.archive, i) }) { Text(v.name.ifBlank { "Version ${i + 1}" }) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { choice = null; busy = null }) { Text("CANCEL") } },
        )
        null -> {}
    }
}

@Composable
private fun InstalledRow(m: InstalledMod, enabled: Boolean, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, color = Color.White, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(m.version.takeIf { it.isNotBlank() }, m.variant.takeIf { it.isNotBlank() }).joinToString(" · "), color = Lavender, fontSize = 12.sp)
        }
        Switch(checked = m.enabled, onCheckedChange = onToggle, enabled = enabled)
        TextButton(onClick = onRemove, enabled = enabled) { Text("REMOVE") }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
}
