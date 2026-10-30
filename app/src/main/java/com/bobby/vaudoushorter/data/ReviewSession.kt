package com.bobby.vaudoushorter.data

import kotlin.random.Random

/** URI strings keep the sorting rules independent of Android and easy to test. */
data class GalleryMedia(
    val uri: String,
    val name: String,
    val folderKey: String,
    val folderName: String,
    val folderPath: String,
    val isVideo: Boolean,
    val size: Long,
    val dateAdded: Long,
    val duration: Long = 0
)

enum class MediaFilter(val label: String) { ALL("Tout"), PHOTOS("Photos"), VIDEOS("Vidéos") }
enum class Decision { KEEP, TRASH }
data class ReviewChoice(val media: GalleryMedia, val decision: Decision)
data class GalleryFolder(val key: String, val name: String, val path: String, val count: Int)
data class ReviewSnapshot(
    val current: GalleryMedia? = null,
    val next: GalleryMedia? = null,
    val total: Int = 0,
    val remaining: Int = 0,
    val kept: Int = 0,
    val discarded: Int = 0,
    val canUndo: Boolean = false,
    val trash: List<GalleryMedia> = emptyList(),
    val folders: List<GalleryFolder> = emptyList()
)

class ReviewSession(initialChoices: List<ReviewChoice> = emptyList(), private val random: Random = Random.Default) {
    private var catalogue = emptyList<GalleryMedia>()
    private var deck = mutableListOf<String>()
    private val choices = initialChoices.distinctBy { it.media.uri }.toMutableList()

    fun choices(): List<ReviewChoice> = choices.toList()

    fun setMedia(media: List<GalleryMedia>) {
        catalogue = media.distinctBy { it.uri }
        val available = catalogue.mapTo(hashSetOf()) { it.uri }
        deck.retainAll(available)
        val known = deck.toHashSet()
        deck.addAll(available.filterNot { it in known }.shuffled(random))
    }

    fun decide(uri: String, decision: Decision): Boolean {
        if (choices.any { it.media.uri == uri }) return false
        val media = catalogue.find { it.uri == uri } ?: return false
        choices.add(ReviewChoice(media, decision))
        return true
    }

    fun undo(): GalleryMedia? {
        // Inaccessible media stay in the ledger until access is granted again.
        val available = catalogue.mapTo(hashSetOf()) { it.uri }
        val index = choices.indexOfLast { it.media.uri in available }
        if (index < 0) return null
        return choices.removeAt(index).media.also { putFirst(it.uri) }
    }

    fun restore(uri: String) {
        choices.removeAll { it.media.uri == uri && it.decision == Decision.TRASH }
        putFirst(uri)
    }

    fun restoreAll() {
        choices.filter { it.decision == Decision.TRASH }.forEach { putFirst(it.media.uri) }
        choices.removeAll { it.decision == Decision.TRASH }
    }

    fun removeDeleted(uris: Set<String>) {
        choices.removeAll { it.media.uri in uris }
        catalogue = catalogue.filterNot { it.uri in uris }
        deck.removeAll(uris)
    }

    fun snapshot(folder: String?, filter: MediaFilter): ReviewSnapshot {
        val byUri = catalogue.associateBy { it.uri }
        val inScope = catalogue.filter { matches(it, folder, filter) }
        val scopeUris = inScope.mapTo(hashSetOf()) { it.uri }
        val reviewed = choices.associateBy { it.media.uri }
        val pending = deck.filter { it in scopeUris && it !in reviewed }
        val scopeChoices = choices.filter { it.media.uri in scopeUris }
        return ReviewSnapshot(
            current = pending.getOrNull(0)?.let(byUri::get),
            next = pending.getOrNull(1)?.let(byUri::get),
            total = inScope.size,
            remaining = pending.size,
            kept = scopeChoices.count { it.decision == Decision.KEEP },
            discarded = scopeChoices.count { it.decision == Decision.TRASH },
            canUndo = choices.any { it.media.uri in byUri },
            trash = choices.filter { it.decision == Decision.TRASH }.map { it.media },
            folders = catalogue.groupBy { it.folderKey }.map { (key, items) ->
                GalleryFolder(key, items.first().folderName, items.first().folderPath, items.size)
            }.sortedBy { it.name.lowercase() }
        )
    }

    private fun putFirst(uri: String) {
        deck.remove(uri)
        deck.add(0, uri)
    }

    private fun matches(media: GalleryMedia, folder: String?, filter: MediaFilter): Boolean =
        (folder == null || media.folderKey == folder) && when (filter) {
            MediaFilter.ALL -> true
            MediaFilter.PHOTOS -> !media.isVideo
            MediaFilter.VIDEOS -> media.isVideo
        }
}
