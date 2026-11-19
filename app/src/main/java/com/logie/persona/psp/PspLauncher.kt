package com.logie.persona.psp

import android.content.Context
import android.content.Intent
import com.logie.persona.backend.psp.PspSettingsFiles
import org.ppsspp.ppsspp.PpssppActivity
import java.io.File

/** Writes what the emulator reads (where its memory stick is, its settings, the control mapping) and starts the game in its own process. */
object PspLauncher {
    /** Null when the game was started; otherwise why not. */
    fun play(context: Context): String? {
        val mods = PspStore.mods(context) ?: return "The game isn't installed."
        val id = PspStore.discId(context) ?: return "The game isn't installed."
        prepare(context, mods.memstick, id)
        val boot = mods.bootPath()
        context.startActivity(Intent(context, PspActivity::class.java).putExtra(PpssppActivity.SHORTCUT_EXTRA_KEY, boot.absolutePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    /** The files for the next start, from the saved OPTIONS. Also used by the in-game menu, which restarts the game with new options. */
    fun prepare(context: Context, memstick: File, discId: String) {
        val options = PspStore.options(context)
        val system = File(memstick, "PSP/SYSTEM").also { it.mkdirs() }
        File(context.filesDir, "memstick_dir.txt").writeText(PspSettingsFiles.memstickPointer(memstick.absolutePath))
        File(system, "ppsspp.ini").writeText(PspSettingsFiles.global(options))
        File(system, "${discId}_ppsspp.ini").writeText(PspSettingsFiles.game(options))
        File(system, "controls.ini").writeText(PspSettingsFiles.controls())
    }
}
