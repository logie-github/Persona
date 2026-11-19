package com.logie.persona.install

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The sign-in runs outside the screen, held awake by SignInService for as long as it waits, and the screen sees where it is. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignInFlowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun started(): List<Intent> = generateSequence { shadowOf(app).nextStartedService }.toList()
    private suspend fun until(cond: () -> Boolean) = withTimeout(5_000) { while (!cond()) kotlinx.coroutines.delay(10) }

    @Test fun theAppIsHeldAwakeWhileTheSignInWaitsAndLetGoAfter() = runBlocking {
        val approved = CompletableDeferred<Unit>()
        InstallCenter.launchSignIn(app) { approved.await(); null }
        assertEquals(InstallCenter.SignInUi.Working, InstallCenter.signInUi.value)
        val first = started(); assertTrue(first.any { it.component?.className == SignInService::class.java.name && it.action == null })
        approved.complete(Unit)
        until { InstallCenter.signInUi.value == InstallCenter.SignInUi.Done }
        until { started().any { it.component?.className == SignInService::class.java.name && it.action == "stop" } }
        InstallCenter.resetSignIn(); assertEquals(InstallCenter.SignInUi.Idle, InstallCenter.signInUi.value)
    }

    @Test fun aFailureIsShownAndASecondSignInReplacesTheFirst() = runBlocking {
        val never = CompletableDeferred<Unit>()
        InstallCenter.launchSignIn(app) { never.await(); null }
        InstallCenter.launchSignIn(app) { "The name or password was not accepted." }
        until { InstallCenter.signInUi.value is InstallCenter.SignInUi.Failed }
        assertEquals("The name or password was not accepted.", (InstallCenter.signInUi.value as InstallCenter.SignInUi.Failed).why)
        InstallCenter.cancelSignIn(app); assertEquals(InstallCenter.SignInUi.Idle, InstallCenter.signInUi.value)
    }

    @Test fun theServiceGoesToTheForegroundAndAStopEndsIt() {
        val c = Robolectric.buildService(SignInService::class.java, Intent(app, SignInService::class.java)).create()
        c.startCommand(0, 1)
        assertTrue(shadowOf(c.get()).isForegroundStopped.not() && shadowOf(c.get()).lastForegroundNotification != null)
        c.withIntent(Intent(app, SignInService::class.java).setAction("stop")).startCommand(0, 2)
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
    }
}
