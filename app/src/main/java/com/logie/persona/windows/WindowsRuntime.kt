package com.logie.persona.windows

import android.content.Context
import android.content.Intent
import com.logie.persona.backend.catalog.Version
import com.logie.persona.backend.windows.GameExe
import com.logie.persona.backend.windows.SteamEmu
import com.logie.persona.install.InstallCenter
import com.winlator.XServerDisplayActivity
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.xenvironment.RootFSInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Starts an installed Windows game (Persona 3, 4, 5) on Winlator, which runs Windows programs on the phone (Wine, Box64 and its own X server;
 * scripts/prepare-winlator.sh builds it into the app), the way the Blizzard launcher does:
 *
 * 1. Winlator's runtime is installed, the first time (and after an update that changes it);
 * 2. the games' Windows (one Winlator container, "Persona") is made the first time, with the games' folder as drive D:;
 * 3. the game's Steam libraries are swapped for the Goldberg emulator's ([SteamEmu]): the game runs without the Steam client;
 * 4. Winlator's game screen starts the game's program, full screen, with Winlator's on-screen gamepad (profile 3, "Virtual Gamepad").
 */
object WindowsRuntime {
    sealed interface State {
        data object Idle : State
        data class Preparing(val versionId: String, val step: Step, val progress: Float? = null) : State
        data class Failed(val versionId: String, val message: String) : State
    }

    enum class Step { RUNTIME, WINDOWS, STARTING }

    private const val CONTAINER_NAME = "Persona"
    /** Winlator's built-in "Virtual Gamepad" controls profile (assets/inputcontrols/profiles/controls-3.icp). */
    const val GAMEPAD_PROFILE = 3

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun play(context: Context, version: Version) {
        if (_state.value is State.Preparing) return
        val app = context.applicationContext
        _state.value = State.Preparing(version.id, Step.RUNTIME, 0f)
        scope.launch {
            try {
                run(app, version)
                _state.value = State.Idle
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = State.Idle; throw e
            } catch (e: Throwable) {
                // (also an Error: Winlator's native libraries failing to load is an UnsatisfiedLinkError)
                runCatching { File(app.filesDir, "windows.log").appendText("${java.util.Date()} ${version.id}: ${e.stackTraceToString()}\n") }
                _state.value = State.Failed(version.id, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun dismiss() { if (_state.value is State.Failed) _state.value = State.Idle }

    /** Where the games are (each in its own folder): drive D: in the games' Windows. */
    fun gamesDir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "games").also { it.mkdirs() }

    private suspend fun run(context: Context, version: Version) {
        val dir = InstallCenter.dir(context, version)
        val exe = withContext(Dispatchers.IO) { GameExe.find(dir, version.id) } ?: throw IllegalStateException("The game's program isn't in its folder. Install it again.")
        val installed = withContext(Dispatchers.IO) {
            RootFSInstaller.installIfNeeded(context) { percent -> _state.value = State.Preparing(version.id, Step.RUNTIME, percent / 100f) }
        }
        if (!installed) throw IllegalStateException("The Windows runtime could not be installed (is there enough space?).")

        _state.value = State.Preparing(version.id, Step.WINDOWS)
        val options = WindowsOptions.load(context)
        val container = container(context, options.screen)
        withContext(Dispatchers.IO) {
            emuDir(context)?.let { emu -> SteamEmu.prepare(dir, version.appId ?: 0, emu, InstallCenter.accountName(context) ?: "Persona") }
            log(context, "${version.id}: ${exe.name}${if (SteamEmu.isSteamStubbed(exe)) " (wrapped by Steam's DRM: it may not start without the Steam client)" else ""}")
        }

        _state.value = State.Preparing(version.id, Step.STARTING)
        val intent = Intent(context, XServerDisplayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("container_id", container.id)
            // The program's path (Winlator makes it a Windows path on D:).
            .putExtra("exec_path", exe.path)
            // The game's windows full screen, and the on-screen gamepad (patches/winlator/0006).
            .putExtra("force_fullscreen", true)
            .apply { if (options.gamepad) putExtra("controls_profile", GAMEPAD_PROFILE) }
        context.startActivity(intent)
    }

    /** The Goldberg emulator's libraries (the app's assets, scripts/prepare-steamemu.sh), copied out once; null when the build has none. */
    private fun emuDir(context: Context): File? {
        val out = File(context.filesDir, "steamemu")
        val names = runCatching { context.assets.list("steamemu")?.toList() }.getOrNull().orEmpty().filter { it.endsWith(".dll") || it == "VERSION" }
        if (names.none { it.endsWith(".dll") }) return null
        val version = runCatching { context.assets.open("steamemu/VERSION").bufferedReader().readText().trim() }.getOrDefault("")
        val stamp = File(out, "VERSION")
        if (!stamp.isFile || stamp.readText().trim() != version) {
            out.mkdirs()
            for (n in names) context.assets.open("steamemu/$n").use { input -> File(out, n).outputStream().use { input.copyTo(it) } }
        }
        return out
    }

    private fun log(context: Context, text: String) { runCatching { File(context.filesDir, "windows.log").appendText("${java.util.Date()} $text\n") } }

    /** The games' Windows: made the first time; its drives set every time (the games' folder as D:). */
    private suspend fun container(context: Context, screen: String): Container {
        val manager = withContext(Dispatchers.IO) { ContainerManager(context) }
        val drives = "D:" + gamesDir(context).path
        manager.containers.firstOrNull { it.name == CONTAINER_NAME }?.let { existing ->
            existing.setDrives(drives)
            existing.setScreenSize(screen)
            withContext(Dispatchers.IO) { existing.saveData() }
            return existing
        }
        val defaults = Container(0)
        val data = JSONObject()
            .put("name", CONTAINER_NAME)
            .put("screenSize", screen)
            .put("envVars", defaults.getEnvVars())
            .put("graphicsDriver", defaults.getGraphicsDriver())
            .put("dxwrapper", defaults.getDXWrapper())
            .put("dxwrapperConfig", defaults.getDXWrapperConfig())
            .put("audioDriver", defaults.getAudioDriver())
            .put("wincomponents", defaults.getWinComponents())
            .put("drives", drives)
            .put("hudMode", defaults.getHUDMode().toInt())
            .put("startupSelection", defaults.getStartupSelection().toInt())
            .put("box64Preset", defaults.getBox64Preset())
            .put("desktopTheme", defaults.getDesktopTheme())
        val created = suspendCancellableCoroutine { done -> manager.createContainerAsync(data) { c -> done.resume(c) } }
        return created ?: throw IllegalStateException("The games' Windows could not be made.")
    }
}
