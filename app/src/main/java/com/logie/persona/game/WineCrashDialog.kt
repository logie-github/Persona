package com.logie.persona.game

import com.winlator.core.WineRegistryEditor
import java.io.File

/**
 * Wine's "Program Error" box, which its debugger shows when a program dies of a fault: on the phone a small dialog on a black
 * screen, for a game that is gone anyway. Off, as the Blizzard launcher has it: the program ends at once, the session's keeper
 * ends the session and the app reports the fault ([GameLog.fault]). Written at every start.
 */
object WineCrashDialog {
    const val KEY = "Software\\Wine\\WineDbg"
    const val VALUE = "ShowCrashDialog"

    fun apply(containerRoot: File) {
        val reg = File(containerRoot, ".wine/user.reg")
        if (!reg.isFile) return
        WineRegistryEditor(reg).use { it.setDwordValue(KEY, VALUE, 0) }
    }
}
