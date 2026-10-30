package com.bobby.vaudoushorter

import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ReviewStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "test-review-${System.nanoTime()}"
    private val store = ReviewStore(context, preferenceName)
    private val photo = GalleryMedia("content://fixture/photo", "Photo.jpg", "test", "Test", "Test", false, 1024, 1)
    private val video = photo.copy(uri = "content://fixture/video", name = "Video.mp4", isVideo = true)

    @After fun clearFixture() { context.deleteSharedPreferences(preferenceName) }

    @Test fun markedMediaStayPendingUntilExitAndBothKindsArePersisted() {
        val choices = listOf(ReviewChoice(photo, Decision.TRASH), ReviewChoice(video, Decision.TRASH))
        store.save(ReviewSave(1, choices))
        assertTrue(store.read().queue.isEmpty())
        assertTrue(store.read().history.isEmpty())
        store.queueExit(ReviewSave(1, choices), needsConsent = false)
        val reopened = ReviewStore(context, preferenceName).read()
        assertEquals(listOf(photo, video), reopened.queue)
        assertTrue(reopened.running)
        store.completeBatch(listOf(photo, video))
        store.finish()
        val completed = ReviewStore(context, preferenceName).read()
        assertEquals(2, completed.history.size)
        assertTrue(completed.history.all { it.deletedAt > 0 })
        assertTrue(completed.choices.isEmpty())
        assertFalse(completed.running)
    }

    @Test fun staleSavesCannotResurrectAFileOrUndoAnActiveExit() {
        val choices = listOf(ReviewChoice(photo, Decision.TRASH))
        store.queueExit(ReviewSave(7, choices), needsConsent = false)
        store.save(ReviewSave(6, emptyList()))
        assertEquals(choices, store.read().choices)
        store.completeBatch(listOf(photo))
        store.save(ReviewSave(7, choices))
        assertTrue(store.read().choices.isEmpty())
        assertEquals(photo, store.read().history.single().media)
    }

    @Test fun failedDeletionRemainsMarkedAndIsNeverAddedToHistory() {
        val choices = listOf(ReviewChoice(photo, Decision.TRASH))
        store.queueExit(ReviewSave(1, choices), needsConsent = false)
        store.completeBatch(emptyList(), listOf(photo))
        store.finish()
        assertEquals(choices, store.read().choices)
        assertTrue(store.read().history.isEmpty())
        assertFalse(store.read().running)
        assertNotNull(store.read().error)
    }

    @Test fun declinedSystemConsentPreservesTheEntirePendingList() {
        val choices = listOf(ReviewChoice(photo, Decision.TRASH), ReviewChoice(video, Decision.TRASH))
        store.queueExit(ReviewSave(1, choices), needsConsent = true)
        assertTrue(store.read().needsConsent)
        store.startInteractive()
        store.finish("Refus")
        assertEquals(choices, store.read().choices)
        assertTrue(store.read().history.isEmpty())
    }

    @Test fun sessionsFromVersion11AreMigratedWithoutLosingKeptOrDiscardedChoices() {
        val legacy = """[{"uri":"content://fixture/photo","name":"Photo.jpg","folderKey":"test","folderName":"Test","folderPath":"Test","video":false,"size":1024,"date":1,"duration":0,"decision":"KEEP"}]"""
        context.getSharedPreferences(preferenceName, 0).edit().putString("choices", legacy).commit()
        val saved = store.read()
        assertEquals(0L, saved.revision)
        assertEquals(Decision.KEEP, saved.choices.single().decision)
        assertTrue(saved.history.isEmpty())
        store.save(ReviewSave(1, saved.choices + ReviewChoice(video, Decision.TRASH)))
        val reloaded = ReviewStore(context, preferenceName).read()
        assertEquals(2, reloaded.choices.size)
        assertEquals(Decision.KEEP, reloaded.choices.first().decision)
        assertEquals(Decision.TRASH, reloaded.choices.last().decision)
    }
}
