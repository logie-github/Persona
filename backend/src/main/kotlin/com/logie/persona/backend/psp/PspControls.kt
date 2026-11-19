package com.logie.persona.backend.psp

import com.logie.persona.backend.touch.Pad

/**
 * How the gestures reach the emulator: as a game pad (device 10) pressing Android key codes, which `controls.ini` ([PspSettingsFiles.controls]) maps to the
 * PSP's buttons. Nothing else is mapped, so no stray key does anything. The three extra keys are the emulator's own functions that the options menu uses.
 */
object PspControls {
    const val DEVICE = 10                 // NativeApp.DEVICE_ID_PAD_0

    /** Android key codes (KeyEvent.KEYCODE_*). */
    private const val BUTTON_A = 96; private const val BUTTON_B = 97; private const val BUTTON_X = 99; private const val BUTTON_Y = 100
    private const val BUTTON_L1 = 102; private const val BUTTON_R1 = 103; private const val BUTTON_START = 108; private const val BUTTON_SELECT = 109
    private const val DPAD_UP = 19; private const val DPAD_DOWN = 20; private const val DPAD_LEFT = 21; private const val DPAD_RIGHT = 22

    /** Pauses the game without any emulator screen, and resumes it when pressed again. */
    const val PAUSE = 188
    const val SAVE_STATE = 189
    const val LOAD_STATE = 190

    fun key(pad: Pad): Int = when (pad) {
        Pad.CROSS -> BUTTON_A; Pad.CIRCLE -> BUTTON_B; Pad.SQUARE -> BUTTON_X; Pad.TRIANGLE -> BUTTON_Y
        Pad.L1 -> BUTTON_L1; Pad.R1 -> BUTTON_R1; Pad.START -> BUTTON_START
        Pad.UP -> DPAD_UP; Pad.DOWN -> DPAD_DOWN; Pad.LEFT -> DPAD_LEFT; Pad.RIGHT -> DPAD_RIGHT
    }

    /** The emulator's names for its buttons (Core/KeyMap.cpp, v1.20) and the key each answers to. */
    val mapping: List<Pair<String, Int>> = listOf(
        "Up" to DPAD_UP, "Down" to DPAD_DOWN, "Left" to DPAD_LEFT, "Right" to DPAD_RIGHT,
        "Circle" to BUTTON_B, "Cross" to BUTTON_A, "Square" to BUTTON_X, "Triangle" to BUTTON_Y,
        "Start" to BUTTON_START, "Select" to BUTTON_SELECT, "L" to BUTTON_L1, "R" to BUTTON_R1,
        "Pause (no menu)" to PAUSE, "Save State" to SAVE_STATE, "Load State" to LOAD_STATE,
    )
}
