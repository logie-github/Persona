package com.logie.persona.backend.psp

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PspOptionsTest {
    private fun sections(ini: String): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, MutableMap<String, String>>(); var cur: MutableMap<String, String>? = null
        for (line in ini.lines()) {
            if (line.startsWith("[")) cur = out.getOrPut(line.trim('[', ']')) { LinkedHashMap() }
            else if ("=" in line) cur!![line.substringBefore('=').trim()] = line.substringAfter('=').trim()
        }
        return out
    }

    @Test fun theGameFileHoldsTheGraphicsChoices() {
        val g = sections(PspSettingsFiles.game(PspOptions(sharpness = 4, smoothing = 3, slantedTextures = 2, edgeSmoothing = 1, textureUpscale = 3, upscaleKind = 1, frameSkip = 2, autoFrameSkip = true)))
        assertTrue(g.getValue("Graphics").entries.containsAll(mapOf("InternalResolution" to "4", "TextureFiltering" to "3", "AnisotropyLevel" to "2", "MultiSampleLevel" to "1",
            "TexScalingLevel" to "3", "TexScalingType" to "1", "FrameSkip" to "2", "AutoFrameSkip" to "True", "ReplaceTextures" to "True").entries))
        assertTrue(g.getValue("Control")["ShowTouchControls"] == "True"); assertTrue(g.getValue("Sound")["Enable"] == "True")
    }

    @Test fun theGlobalFileSetsTheDriverAndAsksNothing() {
        val v = sections(PspSettingsFiles.global(PspOptions()))
        assertTrue(v.getValue("Graphics")["GraphicsBackend"] == "VULKAN"); assertTrue(v.getValue("General")["FirstRun"] == "False"); assertTrue(v.getValue("General")["CheckForNewVersion"] == "False")
        assertTrue(sections(PspSettingsFiles.global(PspOptions(vulkan = false))).getValue("Graphics")["GraphicsBackend"] == "OPENGL")
    }

    @Test fun aFrameRateLimitIsOnlyWrittenWhenAsked() {
        assertFalse("FrameRate" in sections(PspSettingsFiles.game(PspOptions())).getValue("Graphics"))
        assertTrue(sections(PspSettingsFiles.game(PspOptions(frameRate = 60))).getValue("Graphics")["FrameRate"] == "60")
    }

    @Test fun valuesOutOfRangeAreRefused() {
        assertFailsWith<IllegalArgumentException> { PspOptions(sharpness = 11) }
        assertFailsWith<IllegalArgumentException> { PspOptions(frameRate = 45) }
        assertFailsWith<IllegalArgumentException> { PspOptions(touchOpacity = 0) }
    }

    @Test fun noRowNamesTheEmulator() {
        (PspOptions.SHARPNESS.map { it.second } + PspOptions.FRAME_RATE.map { it.second }).forEach { assertFalse(it.contains("PPSSPP", ignoreCase = true)) }
    }
}
