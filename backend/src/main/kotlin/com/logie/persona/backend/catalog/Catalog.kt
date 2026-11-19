package com.logie.persona.backend.catalog

/** How a version gets onto the phone. */
enum class Origin {
    /** Downloaded and installed by the launcher itself, from the account that signed in once. */
    ACCOUNT_DOWNLOAD,

    /** The person's own disc image or ISO, played in an emulator. */
    DISC_IMAGE,
}

/** What runs it. */
enum class Runtime { WINLATOR, PS1_EMULATOR, PSP_EMULATOR }

/** One playable release inside a box (a box with more than one has a version chooser, like Warcraft III's). */
data class Version(
    val id: String,
    val label: String,
    val year: Int,
    val origin: Origin,
    val runtime: Runtime,
    /** The store's app number; set where [origin] is [Origin.ACCOUNT_DOWNLOAD]. Checked against the store's own names (4 and 19 November 2026). */
    val appId: Int? = null,
) {
    init {
        require((origin == Origin.ACCOUNT_DOWNLOAD) == (appId != null)) { "$id: an app number belongs to downloaded versions only" }
    }
}

/** A box on the shelf. */
data class Game(val id: String, val number: Int, val title: String, val versions: List<Version>) {
    val default: Version get() = versions.first()
}

object Catalog {
    val all: List<Game> = listOf(
        Game("persona1", 1, "Persona", listOf(
            Version("persona1_psp", "Shin Megami Tensei: Persona (PSP)", 2009, Origin.DISC_IMAGE, Runtime.PSP_EMULATOR),
            Version("persona1_ps1", "Revelations: Persona (PS1)", 1996, Origin.DISC_IMAGE, Runtime.PS1_EMULATOR),
        )),
        Game("persona2", 2, "Persona 2", listOf(
            Version("persona2_is_psp", "Innocent Sin (PSP)", 2011, Origin.DISC_IMAGE, Runtime.PSP_EMULATOR),
            Version("persona2_ep_ps1", "Eternal Punishment (PS1)", 2000, Origin.DISC_IMAGE, Runtime.PS1_EMULATOR),
        )),
        // The first version is the box's default: Portable (light on a phone), Reload beside it.
        Game("persona3", 3, "Persona 3", listOf(
            Version("persona3_portable", "Portable", 2023, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 1809700),
            Version("persona3_reload", "Reload", 2024, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 2161700),
        )),
        Game("persona4", 4, "Persona 4", listOf(
            Version("persona4_golden", "Golden", 2020, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 1113000),
        )),
        Game("persona5", 5, "Persona 5", listOf(
            Version("persona5_royal", "Royal", 2022, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 1687950),
            Version("persona5_strikers", "Strikers", 2020, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 1382330),
            Version("persona5_tactica", "Tactica", 2023, Origin.ACCOUNT_DOWNLOAD, Runtime.WINLATOR, appId = 2254740),
        )),
    )

    fun findVersion(id: String?): Version? = all.firstNotNullOfOrNull { g -> g.versions.firstOrNull { it.id == id } }
    fun gameOf(version: Version): Game = all.first { g -> version in g.versions }
}
