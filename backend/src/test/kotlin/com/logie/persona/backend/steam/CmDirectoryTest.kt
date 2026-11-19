package com.logie.persona.backend.steam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CmDirectoryTest {
    private fun entry(endpoint: String, dc: String, load: Double) = """{"endpoint":"$endpoint","type":"websockets","dc":"$dc","load":5,"wtd_load":$load}"""

    @Test fun onlyPort443IsKeptSpreadOverDataCentresAndLeastLoadedFirst() {
        val json = """{"response":{"serverlist":[
            ${entry("a1.example:27018", "x", 0.1)}, ${entry("a1.example:443", "x", 3.0)}, ${entry("a2.example:443", "x", 2.0)},
            ${entry("b1.example:443", "y", 5.0)}, ${entry("c1.example:443", "z", 1.0)}]}}"""
        assertEquals(listOf("c1.example:443", "a2.example:443", "b1.example:443", "a1.example:443"), CmDirectory.parse(json))
    }

    @Test fun theFallbackHostsAreAllPort443WebServers() {
        assertTrue(CmDirectory.FALLBACK.isNotEmpty() && CmDirectory.FALLBACK.all { it.endsWith(".steamserver.net:443") })
        assertTrue(CmDirectory.FALLBACK.map { ServerRecordParse.host(it) }.toSet().size == CmDirectory.FALLBACK.size)
    }
}

private object ServerRecordParse { fun host(e: String) = e.substringBefore(':') }
