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
    /** The swipe-and-tap touch controls ([com.logie.persona.backend.touch.Gestures]); off when a game pad is used. The emulator's own on-screen buttons are never shown. */
    val touchControls: Boolean = true,
    val vibration: Boolean = false,
) {
    init {
        require(sharpness in 0..10 && smoothing in 0..3 && slantedTextures in 0..4 && edgeSmoothing in 0..3 && textureUpscale in 1..5 && upscaleKind in 0..3)
        require(frameRate in setOf(0, 30, 60, 120) && frameSkip in 0..9)
    }

    /** Saved as a small JSON file the launcher and the game's own process both read. */
    fun toJson(): String = org.json.JSONObject().put("sharpness", sharpness).put("smoothing", smoothing).put("slantedTextures", slantedTextures)
        .put("edgeSmoothing", edgeSmoothing).put("textureUpscale", textureUpscale).put("upscaleKind", upscaleKind).put("frameRate", frameRate)
        .put("frameSkip", frameSkip).put("autoFrameSkip", autoFrameSkip).put("vulkan", vulkan).put("modTextures", modTextures).put("modCheats", modCheats)
        .put("sound", sound).put("touchControls", touchControls).put("vibration", vibration).toString()

    companion object {
        /** Reads what [toJson] wrote; anything missing or not allowed is the default (a damaged file must not stop the game from starting). */
        fun fromJson(text: String?): PspOptions {
            val d = PspOptions()
            val j = try { org.json.JSONObject(text ?: return d) } catch (e: Exception) { return d }
            return try {
                PspOptions(j.optInt("sharpness", d.sharpness), j.optInt("smoothing", d.smoothing), j.optInt("slantedTextures", d.slantedTextures), j.optInt("edgeSmoothing", d.edgeSmoothing),
                    j.optInt("textureUpscale", d.textureUpscale), j.optInt("upscaleKind", d.upscaleKind), j.optInt("frameRate", d.frameRate), j.optInt("frameSkip", d.frameSkip),
                    j.optBoolean("autoFrameSkip", d.autoFrameSkip), j.optBoolean("vulkan", d.vulkan), j.optBoolean("modTextures", d.modTextures), j.optBoolean("modCheats", d.modCheats),
                    j.optBoolean("sound", d.sound), j.optBoolean("touchControls", d.touchControls), j.optBoolean("vibration", d.vibration))
            } catch (e: IllegalArgumentException) { d }
        }

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
        "Control" to listOf("ShowTouchControls" to "False", "HapticFeedback" to o.vibration.toString().cap()),     // the launcher's own gestures replace the on-screen buttons
    ).let { if (o.frameRate == 0) it else it.replace("[Graphics]\n", "[Graphics]\nFrameRate = ${o.frameRate}\n") }

    /** `controls.ini`: every button the game uses is the key the gestures send (see [PspControls]); anything not listed here has no mapping. */
    fun controls(): String = ini("ControlMapping" to PspControls.mapping.map { (name, code) -> name to "${PspControls.DEVICE}-$code" })

    /** The file that tells the emulator where its memory stick is (it reads this from its own data folder). */
    fun memstickPointer(dir: String): String = dir

    private fun String.cap() = replaceFirstChar { it.uppercase() }
    private fun ini(vararg sections: Pair<String, List<Pair<String, String>>>): String =
        sections.joinToString("\n") { (name, kv) -> "[$name]\n" + kv.joinToString("") { (k, v) -> "$k = $v\n" } }
}
