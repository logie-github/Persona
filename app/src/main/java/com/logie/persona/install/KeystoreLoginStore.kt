package com.logie.persona.install

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.logie.persona.backend.steam.LoginStore
import com.logie.persona.backend.steam.StoredLogin
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The saved sign-in, encrypted with a key that stays in the Android Keystore. Only the refresh token is kept. */
class KeystoreLoginStore(context: Context) : LoginStore {
    private val file = File(context.filesDir, "account.bin")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    override fun load(): StoredLogin? = runCatching {
        if (!file.isFile) return null
        val raw = file.readBytes()
        val iv = raw.copyOfRange(0, IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val lines = String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN)).split('\n')
        StoredLogin(lines[0], lines[1], lines.getOrNull(2)?.takeIf { it.isNotBlank() })
    }.getOrNull()

    override fun save(login: StoredLogin) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(listOf(login.username, login.refreshToken, login.guardData.orEmpty()).joinToString("\n").toByteArray())
        file.writeBytes(cipher.iv + data)
    }

    override fun clear() {
        file.delete()
    }

    private companion object {
        const val ALIAS = "persona_account"
        const val IV_LEN = 12
    }
}
