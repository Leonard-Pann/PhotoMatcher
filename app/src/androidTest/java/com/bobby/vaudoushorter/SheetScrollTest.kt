package com.bobby.vaudoushorter

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.i18n.UiStrings
import com.bobby.vaudoushorter.ui.FolderSheet
import com.bobby.vaudoushorter.ui.TrashSheet
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetScrollTest {
    @get:Rule val compose = createComposeRule()
    private var dismissed = false
    private val media = (0 until 80).map {
        GalleryMedia("content://scroll-fixture/$it", "Photo $it.jpg", "folder", "Folder", "Folder", false, 1, 1)
    }

    private fun show(folder: Boolean) {
        val state = GalleryUiState(loading = false, access = GalleryAccess(true, true),
            review = ReviewSnapshot(trash = media, folders = media.mapIndexed { index, _ ->
                GalleryFolder("$index", "Folder $index", "Pictures/Folder $index", 10)
            }), deletedHistory = media.map { DeletedMedia(it, 1L) })
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            VaudouShorterTheme {
                if (open) {
                    val close = { dismissed = true; open = false }
                    if (folder) FolderSheet(state, {}, {}, {}, close)
                    else TrashSheet(state, {}, {}, {}, {}, close)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun flingAndCheck(list: String, sheet: String, lastIndex: Int) {
        android.util.Log.i("SheetScrollTest", "Scrolling $list to its end")
        compose.onNodeWithTag(list).performScrollToIndex(lastIndex)
        android.util.Log.i("SheetScrollTest", "Capturing expanded position")
        val initialTop = compose.onNodeWithTag(sheet).fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        repeat(3) {
            android.util.Log.i("SheetScrollTest", "Fling $it")
            compose.onNodeWithTag(list).performTouchInput { swipeUp(durationMillis = 40) }
            android.util.Log.i("SheetScrollTest", "Sampling position")
            repeat(24) {
                compose.mainClock.advanceTimeBy(80)
                val top = compose.onNodeWithTag(sheet).fetchSemanticsNode().boundsInRoot.top
                assertEquals("Upward fling must not throw the sheet past its expanded anchor", initialTop, top, 2f)
            }
        }
        assertFalse(dismissed)
        // Downward velocity must still reach the sheet when the list is at its start.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(list).performScrollToIndex(0)
        compose.onNodeWithTag(list).performTouchInput { swipeDown(durationMillis = 80) }
        compose.runOnIdle { assertTrue("Downward swipe should still dismiss the sheet", dismissed) }
        compose.onNodeWithTag(sheet).assertDoesNotExist()
    }

    @Test fun folderListStaysStableAfterFastUpwardFlingsAndStillDismissesDownward() {
        show(folder = true)
        flingAndCheck("folder-list", "folder-sheet", 80)
    }

    @Test fun pendingListStaysStableAfterFastUpwardFlingsAndStillDismissesDownward() {
        show(folder = false)
        flingAndCheck("pending-list", "trash-sheet", 79)
    }

    @Test fun historyListStaysStableAfterFastUpwardFlingsAndStillDismissesDownward() {
        show(folder = false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(UiStrings(context)(R.string.deleted_count, 80)).performClick()
        flingAndCheck("history-list", "trash-sheet", 79)
    }
}
