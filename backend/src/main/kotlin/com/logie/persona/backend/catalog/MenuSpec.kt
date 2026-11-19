package com.logie.persona.backend.catalog

/** The words of a game's menu, in order. The screens style them; what each does is the app's. There is no help text: the words are the menu. */
enum class OptionId { PLAY, INSTALL, UNINSTALL, VERSION, MODS, OPTIONS }

data class MenuOption(val id: OptionId, val label: String, val enabled: Boolean)

object MenuSpec {
    /**
     * PLAY (launch the game), INSTALL (UNINSTALL once it is installed), VERSION (only where there is more than one),
     * MODS (PC versions only) and OPTIONS (the emulator's, or the game's own). The same five on every game.
     */
    fun optionsFor(game: Game, version: Version, installed: Boolean, busy: Boolean = false): List<MenuOption> = buildList {
        add(MenuOption(OptionId.PLAY, "PLAY", installed && !busy))
        add(if (installed) MenuOption(OptionId.UNINSTALL, "UNINSTALL", !busy) else MenuOption(OptionId.INSTALL, "INSTALL", !busy))
        if (game.versions.size > 1) add(MenuOption(OptionId.VERSION, "VERSION", !busy))
        if (version.runtime == Runtime.WINLATOR) add(MenuOption(OptionId.MODS, "MODS", installed && !busy))
        add(MenuOption(OptionId.OPTIONS, "OPTIONS", !busy))
    }
}
