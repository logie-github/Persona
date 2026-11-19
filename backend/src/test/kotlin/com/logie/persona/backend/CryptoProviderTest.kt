package com.logie.persona.backend

import kotlin.test.Test
import kotlin.test.assertNotNull

/** Steam's password step needs a JCE provider that JavaSteam looks up by class name; without one its crypto helper cannot start. */
class CryptoProviderTest {
    @Test fun cryptoHelperStarts() {
        val c = Class.forName("in.dragonbra.javasteam.util.crypto.CryptoHelper")
        assertNotNull(c)
        val provider = c.getField("SEC_PROV").get(null)
        assertNotNull(provider)
    }
}
