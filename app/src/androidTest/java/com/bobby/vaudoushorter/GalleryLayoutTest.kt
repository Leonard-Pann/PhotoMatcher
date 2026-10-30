package com.bobby.vaudoushorter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.ui.GalleryLayout
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GalleryLayoutTest {
    private fun label(id: Int): String = com.bobby.vaudoushorter.i18n.UiStrings(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)(id)

    @get:Rule val compose = createComposeRule()
    private var trashOpened = false

    private fun showGallery() {
        val photo = GalleryMedia("content://fixture/1", "Fixture.jpg", "test", "Test", "Test", false, 1, 1)
        val state = GalleryUiState(access = GalleryAccess(true, true), loading = false,
            review = ReviewSnapshot(current = photo, total = 20, remaining = 13, kept = 5, discarded = 2, canUndo = true))
        compose.setContent {
            var options by remember { mutableStateOf(false) }
            VaudouShorterTheme {
                GalleryLayout(state, canChoose = true, canUndo = true, optionsEnabled = true,
                    optionsOpen = options, onOptions = { options = it }, onFolders = {},
                    onTrash = { trashOpened = true }, onPermissions = {}, onRefresh = {},
                    onFilter = {}, onUndo = {}, onDecision = {}) {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray))
                }
            }
        }
    }

    @Test fun mediaOccupiesMostOfTheScreenAndAllThreeActionsOverlayItsBottom() {
        showGallery()
        val stage = compose.onNodeWithTag("media-stage").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("The media should occupy at least 70% of the screen", stage.height >= screen.height * .70f)
        val delete = compose.onNodeWithContentDescription(label(R.string.delete)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val undo = compose.onNodeWithContentDescription(label(R.string.undo)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val keep = compose.onNodeWithContentDescription(label(R.string.keep)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(delete.center.x < undo.center.x && undo.center.x < keep.center.x)
        listOf(delete, undo, keep).forEach { action ->
            assertTrue(action.top > stage.top + stage.height * .65f)
            assertTrue(action.bottom <= stage.bottom)
        }
        compose.onNodeWithText(label(R.string.kept)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.pending)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.remaining)).assertIsDisplayed()
    }

    @Test fun trashIsOnTheMainScreenAndMenuContainsTheNewSettings() {
        showGallery()
        compose.onNodeWithText(label(R.string.trash)).performClick()
        assertTrue(trashOpened)
        compose.onNodeWithContentDescription(label(R.string.options)).performClick()
        compose.onNodeWithText(label(R.string.choose_folder)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.donate)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.refresh)).assertDoesNotExist()
    }
}
