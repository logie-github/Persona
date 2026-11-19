package com.logie.persona.psp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import com.logie.persona.backend.psp.PspControls
import com.logie.persona.backend.psp.PspOptionRows
import com.logie.persona.backend.psp.PspOptions
import org.ppsspp.ppsspp.NativeApp
import org.ppsspp.ppsspp.PpssppActivity

/**
 * The game, in its own process (declared with `android:process=":psp"`, so leaving the game frees all of the emulator's memory). The emulator's screens are
 * never shown: the touch overlay drives the game, and a two-finger tap (or Back) opens [PspMenuView], where the game is paused.
 */
class PspActivity : PpssppActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TouchOverlay? = null
    private lateinit var menu: PspMenuView
    private var options = PspOptions()
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        options = PspStore.options(this)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        if (options.touchControls) { overlay = TouchOverlay(this, options.vibration, ::toggleMenu).also { addContentView(it, ViewGroup.LayoutParams(match, match)) } }
        menu = PspMenuView(this).also { addContentView(it, ViewGroup.LayoutParams(match, match)); it.hide() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { toggleMenu() } })
    }

    private val menuOpen get() = menu.visibility == android.view.View.VISIBLE

    private fun toggleMenu() { if (menuOpen) closeMenu() else openMenu() }

    private fun openMenu() {
        overlay?.releaseAll()
        press(PspControls.PAUSE)                      // the game stops, with no emulator screen
        showRows()
    }

    private fun showRows() {
        val rows = ArrayList<MenuRow>()
        rows += MenuRow("RESUME") { closeMenu() }
        rows += MenuRow("SAVE STATE") { closeMenu(then = PspControls.SAVE_STATE) }
        rows += MenuRow("LOAD STATE") { closeMenu(then = PspControls.LOAD_STATE) }
        PspOptionRows.rows(options).forEach { r ->
            rows += MenuRow(r.label, r.value) { options = r.next; changed = true; PspStore.saveOptions(this, options); showRows() }
        }
        if (changed) rows += MenuRow("RESTART") { restart() }
        rows += MenuRow("EXIT") { exit() }
        menu.show(rows)
    }

    /** Puts the game back; [then] is a key of the emulator's own that needs the game running (a saved state is written by the running game). */
    private fun closeMenu(then: Int? = null) {
        menu.hide()
        press(PspControls.PAUSE)
        if (then != null) handler.postDelayed({ press(then) }, 150)
    }

    /** Starts the game again with the changed settings, in the same process (the emulator boots the new game itself). */
    private fun restart() {
        val id = PspStore.discId(this) ?: return exit()
        val mods = PspStore.mods(this) ?: return exit()
        PspLauncher.prepare(this, mods.memstick, id)
        menu.hide(); changed = false
        NativeApp.sendMessageFromJava("shortcutParam", "\"" + mods.bootPath().absolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    private fun exit() { finishAndRemoveTask() }

    private fun press(key: Int) { NativeApp.keyDown(PspControls.DEVICE, key, false); NativeApp.keyUp(PspControls.DEVICE, key) }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())           // the emulator keeps its state in the process: a clean slate for the next start
    }
}
