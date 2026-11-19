package com.logie.persona.backend.psp

/** One OPTIONS row: its name, what it is set to, and the options with it moved on to its next value (the last goes round to the first). */
class OptionRow(val label: String, val value: String, val next: PspOptions)

/** The OPTIONS rows for a PSP game, shared by the launcher's OPTIONS screen and the menu a two-finger tap opens in the game. Words are the player's, never the emulator's. */
object PspOptionRows {
    private fun <T> cycle(values: List<Pair<T, String>>, current: T): Pair<String, T> {
        val i = values.indexOfFirst { it.first == current }.coerceAtLeast(0)
        return values[i].second to values[(i + 1) % values.size].first
    }

    fun rows(o: PspOptions): List<OptionRow> {
        fun pick(label: String, values: List<Pair<Int, String>>, current: Int, set: (Int) -> PspOptions): OptionRow {
            val (shown, next) = cycle(values, current); return OptionRow(label, shown, set(next))
        }
        fun flag(label: String, current: Boolean, set: (Boolean) -> PspOptions) = OptionRow(label, if (current) "On" else "Off", set(!current))
        return listOf(
            pick("SHARPNESS", PspOptions.SHARPNESS, o.sharpness) { o.copy(sharpness = it) },
            pick("FRAME RATE", PspOptions.FRAME_RATE, o.frameRate) { o.copy(frameRate = it) },
            pick("SMOOTHING", listOf(0 to "By the game", 1 to "Off", 2 to "Smooth", 3 to "Smoothest"), o.smoothing) { o.copy(smoothing = it) },
            pick("SLANTED SURFACES", listOf(0 to "Off", 1 to "2x", 2 to "4x", 3 to "8x", 4 to "16x"), o.slantedTextures) { o.copy(slantedTextures = it) },
            pick("EDGE SMOOTHING", listOf(0 to "Off", 1 to "2x", 2 to "4x", 3 to "8x"), o.edgeSmoothing) { o.copy(edgeSmoothing = it) },
            pick("TEXTURE UPSCALING", listOf(1 to "Off", 2 to "2x", 3 to "3x", 4 to "4x", 5 to "5x"), o.textureUpscale) { o.copy(textureUpscale = it) },
            flag("VULKAN", o.vulkan) { o.copy(vulkan = it) },
            flag("SOUND", o.sound) { o.copy(sound = it) },
            flag("TOUCH CONTROLS", o.touchControls) { o.copy(touchControls = it) },
            flag("VIBRATION", o.vibration) { o.copy(vibration = it) },
            flag("MOD TEXTURES", o.modTextures) { o.copy(modTextures = it) },
            flag("MOD CHEATS", o.modCheats) { o.copy(modCheats = it) },
        )
    }
}
