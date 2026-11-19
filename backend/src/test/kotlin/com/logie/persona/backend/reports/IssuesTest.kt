package com.logie.persona.backend.reports

import org.json.JSONObject
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuesTest {
    private fun dir(): File = createTempDirectory("issues").toFile()

    @Test fun theSameFailureOfTheSameBuildGoesOnceADay() {
        val d = dir()
        val r = Report("Persona 4 Golden closed by itself 12 s after PLAY", "body", build = 23)
        assertTrue(Issues.queue(d, r, now = 1_000))
        assertFalse(Issues.queue(d, r.copy(title = "Persona 4 Golden closed by itself 40 s after PLAY"), now = 2_000))
        assertTrue(Issues.queue(d, r.copy(build = 24), now = 3_000), "a new build reports again")
        assertFalse(Issues.queue(d, r, now = 1_000 + 25L * 3600 * 1000), "still waiting to be sent")
        Issues.waiting(d).forEach { it.delete() } // (sent)
        assertTrue(Issues.queue(d, r, now = 1_000 + 25L * 3600 * 1000), "a day later")
        assertTrue(Issues.queue(d, r, now = 5_000, force = true))
    }

    @Test fun longLogsAreSplitAtLineEndsAndOnlyTheirEndIsKept() {
        val text = (1..40_000).joinToString("\n") { "0010:err:x:line $it" }
        val parts = Issues.chunks("Session log", text, maxParts = 3)
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.length < Issues.MAX_ISSUE })
        assertTrue(parts[0].contains("left out"))
        assertTrue(parts.last().contains("line 40000"))
        assertTrue(parts.last().trimEnd().endsWith("````"))
    }

    @Test fun anIssueThenItsCommentsAndAFailedStepGoesOnFromWhereItStopped() {
        val d = dir()
        Issues.queue(d, Report("Crash", "body", comments = listOf("one", "two"), build = 1))
        val calls = ArrayList<String>()
        var failComment = true
        val http = Http { path, _, json ->
            calls += path
            when {
                path.endsWith("/issues") -> 201 to """{"number": 7}"""
                failComment -> { failComment = false; 502 to "busy" }
                else -> { assertTrue(JSONObject(json).getString("body").isNotEmpty()); 201 to "{}" }
            }
        }
        val first = Issues.send(d, "o/r", "t", http) {}
        assertEquals(1, first.waiting); assertTrue(first.problem!!.contains("502"))
        val second = Issues.send(d, "o/r", "t", http) {}
        assertEquals(listOf(7), second.sent); assertEquals(0, second.waiting)
        assertEquals(1, calls.count { it.endsWith("/issues") }, "the issue is opened once")
        assertEquals(3, calls.count { it.endsWith("/7/comments") })
    }

    @Test fun nothingGoesWithoutAToken() {
        val d = dir()
        Issues.queue(d, Report("Crash", "body"))
        val r = Issues.send(d, "o/r", "", { _, _, _ -> error("no call") }) {}
        assertEquals(1, r.waiting)
    }

    @Test fun reportsNameNeitherTheAccountNorTheStore() {
        val t = Issues.redact("Loaded steam_api64.dll for Aki; SteamID of aki", "aki")
        assertFalse(t.contains("steam", ignoreCase = true))
        assertFalse(t.contains("aki", ignoreCase = true))
        assertTrue(t.contains("store_api64.dll"))
    }
}
