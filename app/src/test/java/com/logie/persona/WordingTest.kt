package com.logie.persona

import com.logie.persona.install.Wording
import org.junit.Assert.assertFalse
import org.junit.Test

class WordingTest {
    @Test fun noInstallTextNamesTheStore() {
        Wording.all.forEach { assertFalse(it, it.contains("steam", ignoreCase = true)) }
    }
}
