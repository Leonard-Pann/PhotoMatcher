package com.bobby.vaudoushorter

import com.bobby.vaudoushorter.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ReviewSessionTest {
    private fun media(id: Int, folder: String = "camera", video: Boolean = false) = GalleryMedia(
        "content://media/external_primary/${if (video) "video" else "images"}/media/$id", "$id.jpg",
        folder, folder, "DCIM/$folder", video, 1024, 1000
    )

    @Test fun randomDeckVisitsEveryItemExactlyOnce() {
        val input = (1..100).map { media(it) }
        val session = ReviewSession().apply { setMedia(input) }
        val seen = mutableSetOf<String>()
        repeat(input.size) {
            val current = session.snapshot(null, MediaFilter.ALL).current!!
            assertTrue(seen.add(current.uri))
            assertTrue(session.decide(current.uri, Decision.KEEP))
        }
        val result = session.snapshot(null, MediaFilter.ALL)
        assertNull(result.current)
        assertEquals(0, result.remaining)
        assertEquals(100, result.kept)
        assertEquals(input.map { it.uri }.toSet(), seen)
    }

    @Test fun undoRestoresTheLastDecisionToTheTopOfTheDeck() {
        val session = ReviewSession().apply { setMedia((1..4).map { media(it) }) }
        val first = session.snapshot(null, MediaFilter.ALL).current!!
        session.decide(first.uri, Decision.TRASH)
        val second = session.snapshot(null, MediaFilter.ALL).current!!
        session.decide(second.uri, Decision.KEEP)
        assertEquals(second, session.undo())
        assertEquals(second, session.snapshot(null, MediaFilter.ALL).current)
        assertEquals(first, session.undo())
        assertEquals(first, session.snapshot(null, MediaFilter.ALL).current)
        assertTrue(session.snapshot(null, MediaFilter.ALL).trash.isEmpty())
    }

    @Test fun folderAndTypeFiltersDoNotLoseChoicesOrCountOtherFolders() {
        val photo = media(1)
        val video = media(2, video = true)
        val other = media(3, "screenshots")
        val session = ReviewSession().apply { setMedia(listOf(photo, video, other)) }
        session.decide(photo.uri, Decision.KEEP)
        session.decide(other.uri, Decision.TRASH)
        val result = session.snapshot("camera", MediaFilter.VIDEOS)
        assertEquals(video, result.current)
        assertEquals(1, result.total)
        assertEquals(0, result.discarded)
        assertEquals(listOf(other), result.trash)
        assertEquals(2, result.folders.size)
    }

    @Test fun savedChoicesSurviveRestartAndUndoStillWorks() {
        val photo = media(1)
        val session = ReviewSession(listOf(ReviewChoice(photo, Decision.TRASH)))
        session.setMedia(listOf(photo, media(2)))
        assertEquals(1, session.snapshot(null, MediaFilter.ALL).remaining)
        assertEquals(listOf(photo), session.snapshot(null, MediaFilter.ALL).trash)
        session.undo()
        assertEquals(photo, session.snapshot(null, MediaFilter.ALL).current)
    }

    @Test fun refreshPreservesTheCurrentCardAndAddsNewMedia() {
        val initial = (1..5).map { media(it) }
        val session = ReviewSession().apply { setMedia(initial) }
        val current = session.snapshot(null, MediaFilter.ALL).current
        session.setMedia(initial + media(6))
        assertEquals(current, session.snapshot(null, MediaFilter.ALL).current)
        assertEquals(6, session.snapshot(null, MediaFilter.ALL).remaining)
    }

    @Test fun restoringTrashDoesNotModifyKeptChoices() {
        val first = media(1)
        val second = media(2)
        val session = ReviewSession().apply { setMedia(listOf(first, second)) }
        session.decide(first.uri, Decision.KEEP)
        session.decide(second.uri, Decision.TRASH)
        session.restoreAll()
        val result = session.snapshot(null, MediaFilter.ALL)
        assertEquals(1, result.kept)
        assertEquals(1, result.remaining)
        assertEquals(second, result.current)
        assertTrue(result.trash.isEmpty())
    }

    @Test fun confirmedDeletionRemovesOnlySuccessfulItemsFromTheLedger() {
        val first = media(1)
        val second = media(2)
        val session = ReviewSession().apply { setMedia(listOf(first, second)) }
        session.decide(first.uri, Decision.TRASH)
        session.decide(second.uri, Decision.TRASH)
        session.removeDeleted(setOf(first.uri))
        val result = session.snapshot(null, MediaFilter.ALL)
        assertEquals(listOf(second), result.trash)
        assertEquals(1, result.total)
        assertEquals(second, session.undo())
        assertNull(session.undo())
    }

    @Test fun reviewedPhotosAndVideosStayExcludedAcrossFiltersRefreshAndRestart() {
        val items = (1..20).map { media(it, if (it % 3 == 0) "screenshots" else "camera", video = it % 2 == 0) }
        val session = ReviewSession().apply { setMedia(items) }
        val seen = hashSetOf<String>()
        listOf(MediaFilter.PHOTOS, MediaFilter.VIDEOS).forEach { filter ->
            repeat(4) {
                val current = session.snapshot(null, filter).current!!
                assertTrue(seen.add(current.uri))
                session.decide(current.uri, if (it % 2 == 0) Decision.KEEP else Decision.TRASH)
            }
        }
        val resumed = ReviewSession(session.choices()).apply { setMedia(items.reversed()) }
        resumed.setMedia(items)
        repeat(12) {
            val current = resumed.snapshot(null, MediaFilter.ALL).current!!
            assertTrue("A reviewed URI must never return", seen.add(current.uri))
            resumed.decide(current.uri, Decision.KEEP)
        }
        assertEquals(20, seen.size)
        assertNull(resumed.snapshot(null, MediaFilter.ALL).current)
        resumed.setMedia(items)
        assertNull(resumed.snapshot(null, MediaFilter.ALL).current)
    }

    @Test fun shuffleUsesRandomnessInsteadOfFollowingGalleryOrder() {
        val items = (1..40).map { media(it) }
        fun draw(seed: Int): List<String> {
            val session = ReviewSession(random = Random(seed)).apply { setMedia(items) }
            return List(items.size) {
                val current = session.snapshot(null, MediaFilter.ALL).current!!
                session.decide(current.uri, Decision.KEEP)
                current.uri
            }
        }
        assertEquals(draw(42), draw(42))
        assertNotEquals(draw(42), draw(87))
        assertNotEquals(items.map { it.uri }, draw(42))
    }

    @Test fun duplicateDecisionsAndMissingMediaCannotCreateExtraChoices() {
        val photo = media(1)
        val session = ReviewSession().apply { setMedia(listOf(photo, photo)) }
        assertTrue(session.decide(photo.uri, Decision.KEEP))
        assertFalse(session.decide(photo.uri, Decision.TRASH))
        assertFalse(session.decide(media(2).uri, Decision.TRASH))
        assertEquals(1, session.choices().size)
        assertEquals(1, session.snapshot(null, MediaFilter.ALL).total)
    }

    @Test fun temporaryLossOfMediaAccessDoesNotEraseTrash() {
        val photo = media(1)
        val session = ReviewSession(listOf(ReviewChoice(photo, Decision.TRASH)))
        session.setMedia(emptyList())
        assertFalse(session.snapshot(null, MediaFilter.ALL).canUndo)
        assertEquals(listOf(photo), session.snapshot(null, MediaFilter.ALL).trash)
        session.setMedia(listOf(photo))
        assertEquals(photo, session.undo())
    }
}
