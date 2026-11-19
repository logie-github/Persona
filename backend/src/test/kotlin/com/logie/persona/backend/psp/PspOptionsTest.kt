package com.logie.persona.backend.psp

import com.logie.persona.backend.touch.Pad
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(g.getValue("Control")["ShowTouchControls"] == "False", "the emulator's own buttons are never shown"); assertTrue(g.getValue("Sound")["Enable"] == "True")
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
        assertFailsWith<IllegalArgumentException> { PspOptions(frameSkip = 10) }
    }

    @Test fun noRowNamesTheEmulator() {
        (PspOptions.SHARPNESS.map { it.second } + PspOptions.FRAME_RATE.map { it.second }).forEach { assertFalse(it.contains("PPSSPP", ignoreCase = true)) }
    }

    @Test fun everyPadButtonIsMappedToItsOwnKeyAndTheFileNamesTheEmulatorsButtons() {
        val keys = Pad.entries.map { PspControls.key(it) }
        assertEquals(keys.size, keys.toSet().size, "no two buttons share a key")
        val ini = PspSettingsFiles.controls()
        assertTrue(ini.startsWith("[ControlMapping]\n"))
        assertTrue("Cross = 10-96\n" in ini && "Triangle = 10-100\n" in ini && "Pause (no menu) = 10-188\n" in ini && "Start = 10-108\n" in ini)
        for (k in keys) assertTrue("= 10-$k\n" in ini, "key $k is mapped")
    }

    @Test fun optionsSurviveTheirFileAndADamagedFileIsTheDefaults() {
        val o = PspOptions(sharpness = 6, frameRate = 60, sound = false, touchControls = false, textureUpscale = 3)
        assertEquals(o, PspOptions.fromJson(o.toJson()))
        assertEquals(PspOptions(), PspOptions.fromJson("{not json")); assertEquals(PspOptions(), PspOptions.fromJson(null))
        assertEquals(PspOptions(), PspOptions.fromJson("""{"sharpness": 99}"""), "a value that is not allowed falls back to the defaults, not to a crash")
    }

    @Test fun everyRowGoesRoundToWhereItStartedWithOnlyValidValues() {
        val start = PspOptions()
        for (i in PspOptionRows.rows(start).indices) {
            var o = start; val seen = HashSet<String>()
            do { val row = PspOptionRows.rows(o)[i]; assertTrue(seen.add(row.value), "${row.label} repeats ${row.value}"); o = row.next } while (o != start && seen.size < 20)
            assertEquals(start, o, "${PspOptionRows.rows(start)[i].label} comes back to where it began")
            assertEquals(PspOptions.fromJson(o.toJson()), o)
        }
        assertTrue(PspOptionRows.rows(start).none { r -> listOf("ppsspp", "vulkan", "emulator").any { r.label.contains(it, true) && r.label != "VULKAN" } })
    }
}
