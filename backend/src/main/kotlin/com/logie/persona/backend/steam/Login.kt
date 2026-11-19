package com.logie.persona.backend.steam

import java.io.File

/** What the one-time sign-in leaves on the phone. A refresh token only: the password is never kept. */
data class StoredLogin(val username: String, val refreshToken: String, val guardData: String? = null)

/** Where the app keeps [StoredLogin] (the app gives an encrypted file). */
interface LoginStore {
    fun load(): StoredLogin?
    fun save(login: StoredLogin)
    fun clear()
}

/** A plain-file store, for tests and for the app to wrap with its own encryption. */
class FileLoginStore(private val file: File) : LoginStore {
    override fun load(): StoredLogin? {
        if (!file.isFile) return null
        val lines = file.readLines()
        if (lines.size < 2 || lines[0].isBlank() || lines[1].isBlank()) return null
        return StoredLogin(lines[0], lines[1], lines.getOrNull(2)?.takeIf { it.isNotBlank() })
    }

    override fun save(login: StoredLogin) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(listOf(login.username, login.refreshToken, login.guardData.orEmpty()).joinToString("\n"))
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "could not write the sign-in" }
    }

    override fun clear() {
        file.delete()
    }
}

/** Raised when the saved sign-in is gone or no longer accepted: the only time the person sees the sign-in screen again. */
class SignInRequired(message: String) : Exception(message)

/** The code Steam asks for at the one-time sign-in. */
enum class GuardKind { APP_CODE, EMAIL_CODE }

/** The sign-in screen's side of the one-time sign-in. */
interface GuardPrompt {
    /** The code, or null when the person gave up. */
    suspend fun code(kind: GuardKind, previousWasWrong: Boolean): String?

    /** True to wait for the person to approve the sign-in in the phone's Steam app (the call comes before the wait starts, so the screen can say so); false to ask for a code instead. */
    suspend fun approvedOnPhone(): Boolean
}
