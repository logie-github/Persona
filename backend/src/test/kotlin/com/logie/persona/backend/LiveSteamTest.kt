package com.logie.persona.backend

import com.logie.persona.backend.steam.FileLoginStore
import com.logie.persona.backend.steam.GuardKind
import com.logie.persona.backend.steam.GuardPrompt
import com.logie.persona.backend.steam.SignIn
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Against the real service, with no account: a made-up name and password must be refused by the service itself (which proves the connection, the key
 * exchange and the password encryption all work), and the approval link must come back. Runs only with PERSONA_LIVE_STEAM=1 in the environment.
 */
class LiveSteamTest {
    private fun live() = assumeTrue("set PERSONA_LIVE_STEAM=1 to run against the real service", System.getenv("PERSONA_LIVE_STEAM") == "1")
    private val noPrompt = object : GuardPrompt {
        override suspend fun code(kind: GuardKind, previousWasWrong: Boolean): String? = null
        override suspend fun approvedOnPhone(): Boolean = false
    }

    // Called from one thread of its own, as the phone's sign-in screen calls it from the main thread: the sign-in must move its network work elsewhere.
    @Test fun aMadeUpAccountIsRefusedByTheServiceNotByTheConnection() = runBlocking(kotlinx.coroutines.newSingleThreadContext("main")) {
        live()
        val store = FileLoginStore(File.createTempFile("persona-live", ".txt").also { it.delete() })
        try {
            withTimeout(90_000) { SignIn(store).signIn("persona_test_nobody_${System.nanoTime()}", "not-a-password-1".toCharArray(), noPrompt) }
            fail("a made-up account signed in")
        } catch (e: AuthenticationException) {
            println("LIVE: refused with ${e.result}")
            assertTrue(e.result != null)
        }
        assertEquals(null, store.load())
    }

    @Test fun theApprovalLinkComesBack() = runBlocking {
        live()
        val link = CompletableDeferred<String>()
        val store = FileLoginStore(File.createTempFile("persona-live", ".txt").also { it.delete() })
        try {
            withTimeout(25_000) { SignIn(store).signInWithApproval({ link.complete(it) }, timeoutMs = 20_000) }
        } catch (e: Exception) { println("LIVE: approval wait ended: ${e.javaClass.simpleName}") }
        assertTrue(link.isCompleted, "no link from the service")
        val url = link.await(); println("LIVE: link $url")
        assertTrue(url.startsWith("https://s.team/q/"), url)
    }
}
