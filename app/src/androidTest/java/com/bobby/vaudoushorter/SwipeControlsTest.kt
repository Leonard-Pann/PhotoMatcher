package com.bobby.vaudoushorter

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.ui.*
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic URIs only: these tests never delete or inspect the user's gallery. */
class SwipeControlsTest {
    @get:Rule val compose = createComposeRule()
    private val session = ReviewSession().apply {
        setMedia((1..3).map {
            GalleryMedia("content://com.bobby.vaudoushorter.test/$it", "Souvenir $it", "test", "Tests", "Tests", false, 1024, 1000)
        })
    }

    private fun showDeck() {
        compose.setContent {
            var review by remember { mutableStateOf(session.snapshot(null, MediaFilter.ALL)) }
            var command by remember { mutableStateOf<SwipeCommand?>(null) }
            var commandId by remember { mutableIntStateOf(0) }
            var busy by remember { mutableStateOf(false) }
            VaudouShorterTheme {
                Column(Modifier.fillMaxSize()) {
                    ReviewDeck(review, true, command,
                        onDecision = { uri, decision ->
                            command = null
                            session.decide(uri, decision)
                            review = session.snapshot(null, MediaFilter.ALL)
                        }, onBusy = { busy = it }, modifier = Modifier.weight(1f).testTag("deck"))
                    DecisionButtons(!busy && review.current != null, canUndo = !busy && review.canUndo,
                        compact = true, onUndo = { session.undo(); review = session.snapshot(null, MediaFilter.ALL) }) { decision ->
                        review.current?.let { command = SwipeCommand(++commandId, it.uri, decision) }
                    }
                }
            }
        }
    }

    @Test fun keepButtonConsumesOneCardAndUndoDoesNotReplayTheCommand() {
        showDeck()
        compose.onNodeWithContentDescription("Garder").performClick()
        compose.waitUntil(5000) { session.choices().size == 1 }
        compose.waitForIdle()
        assertEquals(Decision.KEEP, session.choices().single().decision)
        compose.onNodeWithContentDescription("Annuler").performClick()
        compose.waitForIdle()
        assertEquals(0, session.choices().size)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(0, session.choices().size)
        assertEquals(3, session.snapshot(null, MediaFilter.ALL).remaining)
    }

    @Test fun leftSwipeStagesExactlyOneMediaInTrash() {
        showDeck()
        compose.onNodeWithTag("deck").performTouchInput { swipeLeft(durationMillis = 450) }
        compose.waitUntil(5000) { session.choices().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(Decision.TRASH, session.choices().single().decision)
        assertEquals(1, session.snapshot(null, MediaFilter.ALL).trash.size)
    }

    @Test fun shortDragSpringsBackWithoutMakingADecision() {
        showDeck()
        compose.onNodeWithTag("deck").performTouchInput {
            swipe(center, center.copy(x = center.x + width * .1f), durationMillis = 350)
        }
        compose.waitForIdle()
        assertEquals(0, session.choices().size)
        assertEquals(3, session.snapshot(null, MediaFilter.ALL).remaining)
    }
}
