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

    @Test fun wineHandlingItsOwnExceptionsIsOneLine() {
        val block = """
            008c:trace:seh:dispatch_exception code=6ba (RPC_S_SERVER_UNAVAILABLE) flags=0 addr=000000027FA4D127
            008c:trace:seh:dispatch_exception rip=000000027fa4d127 rsp=0000000080dee080 rbp=0000000000000000 eflags=00000202
            008c:trace:seh:call_seh_handlers TEB handler at 000000027F7A58F0 returned 1
            008c:trace:seh:RtlRestoreContext returning to 000000027F81DA5A stack 0000000080DEF740
        """.trimIndent()
        val text = listOf(block, "00dc:trace:loaddll:build_module Loaded L\"D:\\\\game\\\\P4G.exe\" at 0000000140000000: native", block, block,
            "00dc:trace:seh:dispatch_exception code=c0000005 (EXCEPTION_ACCESS_VIOLATION) flags=0 addr=0000000140001000").joinToString("\n")
        val out = LogDigest.compact(text)
        assertEquals(1, Regex("RPC_S_SERVER_UNAVAILABLE\\) flags").findAll(out).count())
        assertTrue(out.contains("3 times"))
        assertTrue(out.contains("EXCEPTION_ACCESS_VIOLATION"))
        assertFalse(out.contains("rip="))
        assertTrue(out.contains("P4G.exe"))
    }
}
