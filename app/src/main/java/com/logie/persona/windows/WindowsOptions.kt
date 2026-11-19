package com.logie.persona.windows

import android.content.Context

/** OPTIONS for the Windows games, kept in the app's preferences and applied at each PLAY. */
data class WindowsOptions(val screen: String = "1280x720", val gamepad: Boolean = true) {
    companion object {
        val SCREENS = listOf("1280x720", "1600x900", "1920x1080", "960x540")
        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("windows_options", Context.MODE_PRIVATE)

        fun load(context: Context): WindowsOptions = prefs(context).let { p ->
            WindowsOptions(p.getString("screen", null)?.takeIf { it in SCREENS } ?: "1280x720", p.getBoolean("gamepad", true))
        }

        fun save(context: Context, o: WindowsOptions) { prefs(context).edit().putString("screen", o.screen).putBoolean("gamepad", o.gamepad).apply() }

        /** (name, what it is set to, the options with it moved on) for each OPTIONS row. */
        fun rows(o: WindowsOptions): List<Triple<String, String, WindowsOptions>> = listOf(
            Triple("SCREEN SIZE", o.screen.replace("x", " × "), o.copy(screen = SCREENS[(SCREENS.indexOf(o.screen) + 1) % SCREENS.size])),
            Triple("ON-SCREEN GAMEPAD", if (o.gamepad) "On" else "Off", o.copy(gamepad = !o.gamepad)),
        )
    }
}
