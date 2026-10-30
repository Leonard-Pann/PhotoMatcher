package com.bobby.vaudoushorter

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.i18n.AppLanguage
import com.bobby.vaudoushorter.i18n.UiStrings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class SettingsFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var previous = "system"
    @get:Rule(order = 0) val language = object : ExternalResource() {
        override fun before() { previous = AppLanguage.selected(context); AppLanguage.select(context, "en") }
        override fun after() { AppLanguage.select(context, previous) }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageChangeUpdatesTheWholeScreenAndSurvivesRecreation() {
        compose.onNodeWithText("Trash").assertIsDisplayed()
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithText("Français").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Corbeille").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("fr", AppLanguage.selected(context))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Corbeille").assertIsDisplayed()
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Faire un don").performClick()
        compose.onNodeWithText("Soutenir PhotoMatcher").assertIsDisplayed()
        compose.onNodeWithText("Soutenir sur GitHub").assertIsDisplayed()
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithText("Corbeille").assertIsDisplayed()
    }

    @Test fun englishResourcesIncludeFormattedListsErrorsAndAccessibility() {
        val text = UiStrings(context)
        assertEquals("To delete (12)", text(R.string.pending_count, 12))
        assertEquals("Restore photo.jpg", text(R.string.restore_media, "photo.jpg"))
        assertEquals("Last choice undone", text(R.string.undone))
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Donate").performClick()
        compose.onNodeWithText("Support PhotoMatcher").assertIsDisplayed()
        compose.onNodeWithText("Support on GitHub").assertIsDisplayed()
    }
}
