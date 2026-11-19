package com.logie.persona.backend.windows

/**
 * A Windows game's session log made short enough to paste: what Wine starts with every program (Box64's settings, the system libraries it
 * loads) is left out, the same line repeated is written once with a count, and only the end is kept when it is still too long. What is kept:
 * Wine's errors and faults, libraries loaded from outside Windows' own folder (the game's, the emulator's), Box64's complaints, the app's
 * own notes (`[persona]`) and anything the game writes.
 */
object LogDigest {
    private val BOX64_TROUBLE = Regex("(?i)error|fail|unimplemented|signal|sigsegv|sigill|sigbus|abort|crash|missing|not found|unsupported|cannot|can't")

    fun keep(line: String): Boolean {
        if (line.isBlank()) return false
        if (line.startsWith("\t")) return false // Box64's list of its settings
        if (line.startsWith("[BOX64]")) return BOX64_TROUBLE.containsMatchIn(line)
        if (line.contains(":trace:loaddll:")) return !(line.contains(": builtin") && line.contains("C:\\\\windows\\\\", ignoreCase = true))
        return true
    }

    fun compact(text: String, maxChars: Int = 20_000): String {
        val out = ArrayList<String>()
        var last: String? = null; var repeats = 0
        fun flush() { if (repeats > 0) out += "    (the line above $repeats more times)"; repeats = 0 }
        for (line in text.lineSequence()) {
            if (!keep(line)) continue
            // The same message from another thread or at another address is the same message.
            val key = line.replace(Regex("^[0-9a-f]{4}:"), "").replace(Regex("0x[0-9a-fA-F]+|[0-9A-F]{8,16}"), "#")
            if (key == last) { repeats++; continue }
            flush(); out += line; last = key
        }
        flush()
        val all = out.joinToString("\n")
        return if (all.length <= maxChars) all else "...(${all.length - maxChars} characters before)\n" + all.takeLast(maxChars)
    }
}
