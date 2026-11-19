package com.logie.persona.backend.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogDigestTest {
    private val sample = """
        [BOX64] BOX64ENV: Variables overridden:
        	BOX64_DYNAREC_BIGBLOCK=3
        [BOX64] argv[1]="C:\windows\system32\explorer.exe"
        002c:trace:loaddll:build_module Loaded L"C:\\windows\\system32\\kernel32.dll" at 000000027FD00000: builtin
        0100:trace:loaddll:build_module Loaded L"D:\\persona4_golden\\steam_api64.dll" at 0000000180000000: native
        0074:err:nsi:poll_events bind failed
        0078:err:nsi:poll_events bind failed
        [BOX64] Error: unimplemented opcode 0F 0B
        [persona] the session's program ended with status 0
    """.trimIndent()

    @Test fun theNoiseGoesAndTheTroubleStays() {
        val out = LogDigest.compact(sample)
        assertFalse(out.contains("BIGBLOCK"))
        assertFalse(out.contains("kernel32.dll"))
        assertFalse(out.contains("argv[1]"))
        assertTrue(out.contains("steam_api64.dll"))
        assertTrue(out.contains("unimplemented opcode"))
        assertTrue(out.contains("ended with status 0"))
        assertEquals(1, Regex("poll_events").findAll(out).count())
        assertTrue(out.contains("(the line above 1 more times)"))
    }

    @Test fun onlyTheEndWhenTooLong() {
        val text = (1..2000).joinToString("\n") { "0010:err:x:line $it" }
        val out = LogDigest.compact(text, maxChars = 500)
        assertTrue(out.startsWith("...("))
        assertTrue(out.endsWith("line 2000"))
    }
}
