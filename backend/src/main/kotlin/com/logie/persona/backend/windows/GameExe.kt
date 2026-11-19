package com.logie.persona.backend.windows

import java.io.File

/** Which program in an installed game's folder starts it. */
object GameExe {
    /** The programs the store's own launch entries name for each version (checked against the depots' file lists where known). */
    val KNOWN = mapOf(
        "persona4_golden" to listOf("P4G.exe"),
        "persona3_portable" to listOf("P3P.exe"),
        "persona3_reload" to listOf("P3R.exe"),
        "persona5_royal" to listOf("P5R.exe"),
        "persona5_strikers" to listOf("P5S.exe"),
        "persona5_tactica" to listOf("P5T.exe"),
    )

    /** Programs that are never the game: installers, crash reporters, redistributables. */
    private val NOT_THE_GAME = Regex("(?i)(unins|setup|install|redist|vc_?redist|vcredist|dxsetup|directx|crash|report|prereq|helper|uploader|launcherhelper|easyanticheat|eac|dotnet|physx)")

    /**
     * The game's program in [dir]: a known name for [versionId] (in the folder's root, any case), else the largest program in the root that is not an
     * installer or a crash reporter, else the largest one a level down. Null when there is none.
     */
    fun find(dir: File, versionId: String): File? {
        val root = dir.listFiles()?.filter { it.isFile } ?: return null
        KNOWN[versionId]?.forEach { name -> root.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it } }
        fun pick(files: List<File>) = files.filter { it.name.endsWith(".exe", true) && !NOT_THE_GAME.containsMatchIn(it.name) }.maxByOrNull { it.length() }
        pick(root)?.let { return it }
        return pick(dir.listFiles()?.filter { it.isDirectory }?.flatMap { d -> d.listFiles()?.filter { it.isFile }.orEmpty() }.orEmpty())
    }
}
