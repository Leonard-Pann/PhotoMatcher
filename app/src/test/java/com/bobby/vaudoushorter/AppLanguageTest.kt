package com.bobby.vaudoushorter

import com.bobby.vaudoushorter.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun systemFrenchUsesFrenchAndOtherLanguagesFallBackToEnglish() {
        assertEquals("fr", AppLanguage.resolve("system", "fr"))
        listOf("en", "es", "de", "ja").forEach { assertEquals("en", AppLanguage.resolve("system", it)) }
    }
    @Test fun explicitChoiceOverridesSystemLanguage() {
        assertEquals("fr", AppLanguage.resolve("fr", "en"))
        assertEquals("en", AppLanguage.resolve("en", "fr"))
    }
}
