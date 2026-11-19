package com.logie.persona.install

import com.logie.persona.backend.steam.InstallProgress

/** Every word the player sees about an install. The store is named nowhere here (nor anywhere after the one sign-in screen). */
object Wording {
    fun step(step: InstallProgress.Step): String = when (step) {
        InstallProgress.Step.CONNECTING -> "Getting ready"
        InstallProgress.Step.CHECKING -> "Checking your game"
        InstallProgress.Step.DOWNLOADING -> "Installing"
        InstallProgress.Step.FINISHING -> "Finishing"
    }

    const val INSTALL = "Install"
    const val INSTALLED = "Installed"
    const val NOT_IN_LIBRARY = "This game isn't in your library"
    const val FAILED = "Install stopped. Tap Install to continue where it left off."
    const val NEEDS_DISC = "Add your disc image"
    val all: List<String> get() = InstallProgress.Step.values().map(::step) + listOf(INSTALL, INSTALLED, NOT_IN_LIBRARY, FAILED, NEEDS_DISC)
}
