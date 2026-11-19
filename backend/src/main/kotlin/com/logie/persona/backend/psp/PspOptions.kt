package com.logie.persona.backend.psp

/**
 * The launcher's OPTIONS for a PSP game, in the player's words (nothing here says what plays the game). [PspSettingsFiles] turns them
 * into the emulator's own settings files; the keys are the ones its source reads (Core/Config.cpp, v1.20).
 */
data class PspOptions(
    /** How sharp the picture is: 0 = fitted to the screen, 1..10 = times the PSP's own 480x272. */
    val sharpness: Int = 3,
    /** Smoothing of textures: 0 = by the game, 1 = none, 2 = smooth, 3 = smooth at the best quality. */
    val smoothing: Int = 2,
    /** Sharpening of textures seen at a slant: 0 off, 1..4 = 2x..16x. */
    val slantedTextures: Int = 4,
    /** Edge smoothing: 0 off, 1..3 = 2x, 4x, 8x. */
    val edgeSmoothing: Int = 0,
    /** Upscaling of textures: 1 off, 2..5 = 2x..5x; [upscaleKind] picks how (0 xBRZ, 1 hybrid, 2 bicubic, 3 hybrid + bicubic). */
    val textureUpscale: Int = 1,
    val upscaleKind: Int = 0,
    /** Frame rate limit: 0 = the game's own (60), or 30 / 60 / 120. */
    val frameRate: Int = 0,
    /** Frames skipped between drawn ones (0 none, up to 9); [autoFrameSkip] skips only when it can't keep up. */
    val frameSkip: Int = 0,
    val autoFrameSkip: Boolean = false,
    /** The graphics driver: Vulkan (faster on most phones) or OpenGL (steadier on a few). */
    val vulkan: Boolean = true,
    /** Replaced textures (mods) on or off. */
    val modTextures: Boolean = true,
    val modCheats: Boolean = true,
    val sound: Boolean = true,
    val touchControls: Boolean = true,
    /** How see-through the touch buttons are, in percent (5..100). */
    val touchOpacity: Int = 65,
    /** 0 = the default look, 1 = classic, 2.. = the emulator's other styles. */
    val touchStyle: Int = 1,
    val vibration: Boolean = false,
) {
    init {
        require(sharpness in 0..10 && smoothing in 0..3 && slantedTextures in 0..4 && edgeSmoothing in 0..3 && textureUpscale in 1..5 && upscaleKind in 0..3)
        require(frameRate in setOf(0, 30, 60, 120) && frameSkip in 0..9 && touchOpacity in 5..100)
    }

    companion object {
        /** What each OPTIONS row offers, in order, as (label, value, how it's shown): the screen builds its rows from this, not from the fields. */
        val SHARPNESS = listOf(0 to "Fit to the screen", 1 to "1x", 2 to "2x", 3 to "3x", 4 to "4x", 5 to "5x", 6 to "6x", 8 to "8x", 10 to "10x")
        val FRAME_RATE = listOf(0 to "Standard", 30 to "30", 60 to "60", 120 to "120")
    }
}

/** The emulator's settings files for a game, written into its memory-stick folder (`PSP/SYSTEM/`). */
object PspSettingsFiles {
    /** `ppsspp.ini`: the settings that belong to the whole emulator (the driver, no start-up questions, nothing that asks for a screen). */
    fun global(o: PspOptions): String = ini(
        "General" to listOf("FirstRun" to "False", "CheckForNewVersion" to "False", "ScreenshotsAsPNG" to "True", "EnableCheats" to o.modCheats.toString().cap()),
        "Graphics" to listOf("GraphicsBackend" to if (o.vulkan) "VULKAN" else "OPENGL"),
    )

    /** `<DISCID>_ppsspp.ini`: this game's own settings, which win over the global ones. */
    fun game(o: PspOptions): String = ini(
        "General" to listOf("EnableCheats" to o.modCheats.toString().cap()),
        "Graphics" to listOf(
            "InternalResolution" to o.sharpness.toString(), "TextureFiltering" to o.smoothing.toString(), "AnisotropyLevel" to o.slantedTextures.toString(),
            "MultiSampleLevel" to o.edgeSmoothing.toString(), "TexScalingLevel" to o.textureUpscale.toString(), "TexScalingType" to o.upscaleKind.toString(),
            "FrameSkip" to o.frameSkip.toString(), "AutoFrameSkip" to o.autoFrameSkip.toString().cap(), "ReplaceTextures" to o.modTextures.toString().cap(),
            "SaveNewTextures" to "False",
        ),
        "Sound" to listOf("Enable" to o.sound.toString().cap()),
        "Control" to listOf("ShowTouchControls" to o.touchControls.toString().cap(), "TouchButtonOpacity" to o.touchOpacity.toString(), "TouchButtonStyle" to o.touchStyle.toString(), "HapticFeedback" to o.vibration.toString().cap()),
    ).let { if (o.frameRate == 0) it else it.replace("[Graphics]\n", "[Graphics]\nFrameRate = ${o.frameRate}\n") }

    /** The file that tells the emulator where its memory stick is (it reads this from its own data folder). */
    fun memstickPointer(dir: String): String = dir

    private fun String.cap() = replaceFirstChar { it.uppercase() }
    private fun ini(vararg sections: Pair<String, List<Pair<String, String>>>): String =
        sections.joinToString("\n") { (name, kv) -> "[$name]\n" + kv.joinToString("") { (k, v) -> "$k = $v\n" } }
}
