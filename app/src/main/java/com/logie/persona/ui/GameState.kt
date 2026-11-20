package com.logie.persona.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.catalog.MenuSpec
import com.logie.persona.backend.catalog.OptionId
import com.logie.persona.backend.catalog.Origin
import com.logie.persona.backend.catalog.Runtime
import com.logie.persona.backend.catalog.Version
import com.logie.persona.install.InstallCenter
import com.logie.persona.install.InstallUi
import com.logie.persona.install.Wording
import com.logie.persona.psp.PspLauncher
import com.logie.persona.psp.PspStore
import com.logie.persona.windows.WindowsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One word of a game's menu as the screen shows it: usable or greyed. */
class MenuRow(val id: OptionId, val label: String, val usable: Boolean)

/** Where an install or import is, as a fraction, with the words for it. */
class Gauge(val text: String, val fraction: Float)

/**
 * What a game's menu is and does, whatever draws it (the Persona 1 skin, or the plain list): its rows, what is running, what went wrong, and [perform] for the
 * row that was chosen. [options] is true while the OPTIONS list is up.
 */
class GameState(
    val rows: List<MenuRow>, val gauge: Gauge?, val notice: String?, val options: Boolean,
    val perform: (OptionId) -> Unit, val closeOptions: () -> Unit, val openMods: Boolean, val closeMods: () -> Unit,
)

@Composable
fun rememberGame(game: Game, version: Version, ui: InstallUi, onVersion: () -> Unit, onSteamInstall: () -> Unit): GameState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val psp = version.runtime == Runtime.PSP_EMULATOR
    var pspInstalled by remember { mutableStateOf(PspStore.isInstalled(context)) }
    var importing by remember { mutableStateOf<Float?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showMods by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = 0f; notice = null
            val result = withContext(Dispatchers.IO) { PspStore.import(context, uri) { p -> importing = p } }
            importing = null
            when (result) { is PspStore.Imported.Ok -> pspInstalled = true; is PspStore.Imported.Refused -> notice = result.why }
        }
    }
    val installed = if (psp) pspInstalled else ui is InstallUi.Done
    val windows by WindowsRuntime.state.collectAsState()
    val windowsBusy = windows is WindowsRuntime.State.Preparing
    val busy = importing != null || ui is InstallUi.Running
    val rows = MenuSpec.optionsFor(game, version, installed, busy).map { o ->
        // Play needs a runtime (the PSP emulator, or Windows through Winlator; Persona 2's PS1 one isn't in the app yet), and a disc game other than
        // Persona 1 needs its importer.
        val usable = o.enabled && (o.id != OptionId.PLAY || version.runtime != Runtime.PS1_EMULATOR) && !(windowsBusy && o.id == OptionId.PLAY) &&
            !(o.id == OptionId.INSTALL && !psp && version.origin == Origin.DISC_IMAGE) &&
            !(o.id == OptionId.MODS && !psp)          // the PC versions' mod manager isn't made yet
        MenuRow(o.id, o.label, usable)
    }
    val gauge = importing?.let { Gauge("Installing", it) } ?: (ui as? InstallUi.Running)?.let { Gauge(Wording.step(it.progress.step), it.progress.fraction) }
        ?: (windows as? WindowsRuntime.State.Preparing)?.takeIf { it.versionId == version.id }?.let { p ->
            Gauge(when (p.step) { WindowsRuntime.Step.RUNTIME -> "Getting ready"; WindowsRuntime.Step.WINDOWS -> "Setting up"; WindowsRuntime.Step.STARTING -> "Starting" }, p.progress ?: 1f)
        }
    val words = when {
        notice != null -> notice
        ui is InstallUi.NotInLibrary -> Wording.NOT_IN_LIBRARY
        ui is InstallUi.Failed -> Wording.FAILED
        else -> null
    }
    return GameState(
        rows, gauge, words, showOptions,
        perform = { id ->
            when (id) {
                OptionId.PLAY -> if (psp) notice = PspLauncher.play(context) else { WindowsRuntime.dismiss(); WindowsRuntime.play(context, version) }
                OptionId.INSTALL -> if (psp) picker.launch(arrayOf("*/*")) else onSteamInstall()
                OptionId.UNINSTALL -> if (psp) { PspStore.uninstall(context); pspInstalled = false } else InstallCenter.uninstall(context, version)
                OptionId.VERSION -> onVersion()
                OptionId.OPTIONS -> showOptions = true
                OptionId.MODS -> showMods = true
            }
        },
        closeOptions = { showOptions = false }, openMods = showMods && psp, closeMods = { showMods = false },
    )
}
