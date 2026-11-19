package com.logie.persona.backend.catalog

/** The words of a game's menu, in order. The screens style them; what each does is the app's. */
enum class OptionId { PLAY, INSTALL, UNINSTALL, VERSION, MODS, OPTIONS }

data class MenuOption(val id: OptionId, val label: String, val hint: String, val enabled: Boolean)

object MenuSpec {
    /**
     * PLAY (launch the game), INSTALL (UNINSTALL once it is installed), VERSION (only where there is more than one),
     * MODS (PC versions only) and OPTIONS (the emulator's, or the game's own). The same five on every game.
     */
    fun optionsFor(game: Game, version: Version, installed: Boolean, busy: Boolean = false): List<MenuOption> = buildList {
        add(MenuOption(OptionId.PLAY, "PLAY", if (installed) "Start the game" else "Install it first", installed && !busy))
        if (installed) add(MenuOption(OptionId.UNINSTALL, "UNINSTALL", "Take the game off this phone; saves are kept", !busy))
        else add(MenuOption(OptionId.INSTALL, "INSTALL",
            if (version.origin == Origin.DISC_IMAGE) "Add your disc image" else "Download and install the game", !busy))
        if (game.versions.size > 1) add(MenuOption(OptionId.VERSION, "VERSION", "Change version: ${version.label}", !busy))
        if (version.runtime == Runtime.WINLATOR) add(MenuOption(OptionId.MODS, "MODS", "Add or remove mods", installed && !busy))
        add(MenuOption(OptionId.OPTIONS, "OPTIONS",
            if (version.runtime == Runtime.WINLATOR) "Graphics, controls and launch options" else "Emulator options", !busy))
    }
}
