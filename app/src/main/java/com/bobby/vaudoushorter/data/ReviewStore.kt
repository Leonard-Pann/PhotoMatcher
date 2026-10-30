package com.bobby.vaudoushorter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReviewSave(val revision: Long, val choices: List<ReviewChoice>)
data class DeletedMedia(val media: GalleryMedia, val deletedAt: Long)
data class StoredReview(
    val revision: Long = 0,
    val choices: List<ReviewChoice> = emptyList(),
    val queue: List<GalleryMedia> = emptyList(),
    val history: List<DeletedMedia> = emptyList(),
    val running: Boolean = false,
    val needsConsent: Boolean = false,
    val total: Int = 0,
    val error: String? = null
)

/** One atomic preference file preserves old sessions and journals exit-time deletions.
 * Revisions prevent a delayed UI save from resurrecting a file a worker has deleted.
 */
class ReviewStore(context: Context, preferenceName: String = "review_session") {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun read(): StoredReview = synchronized(lock) { readLocked() }

    fun save(save: ReviewSave): StoredReview = synchronized(lock) {
        val current = readLocked()
        saveLocked(current, save).also { if (it != current) persist(it) }
    }

    fun queueExit(save: ReviewSave, needsConsent: Boolean): StoredReview = synchronized(lock) {
        val current = saveLocked(readLocked(), save)
        val trash = current.choices.filter { it.decision == Decision.TRASH }.map { it.media }
        val queue = (current.queue + trash).distinctBy { it.uri }
        current.copy(queue = queue, running = queue.isNotEmpty() && !needsConsent,
            needsConsent = queue.isNotEmpty() && needsConsent, total = queue.size, error = null).also(::persist)
    }

    fun startInteractive(): StoredReview = synchronized(lock) {
        readLocked().copy(running = true, needsConsent = false).also(::persist)
    }

    fun requireConsent(): StoredReview = synchronized(lock) {
        readLocked().copy(running = false, needsConsent = true).also(::persist)
    }

    fun completeBatch(deleted: List<GalleryMedia>, failed: List<GalleryMedia> = emptyList()): StoredReview = synchronized(lock) {
        val current = readLocked()
        val removed = deleted.mapTo(hashSetOf()) { it.uri }
        val attempted = removed + failed.map { it.uri }
        val knownHistory = current.history.mapTo(hashSetOf()) { it.media.uri }
        current.copy(revision = current.revision + 1,
            choices = current.choices.filterNot { it.media.uri in removed },
            queue = current.queue.filterNot { it.uri in attempted },
            history = current.history + deleted.filterNot { it.uri in knownHistory }.map { DeletedMedia(it, System.currentTimeMillis()) },
            error = if (failed.isNotEmpty()) "Certains médias n’ont pas pu être supprimés. Ils restent dans la liste à supprimer." else current.error
        ).also(::persist)
    }

    fun finish(error: String? = null): StoredReview = synchronized(lock) {
        readLocked().let { current -> current.copy(queue = emptyList(), running = false, needsConsent = false,
            error = error ?: current.error).also(::persist) }
    }

    private fun saveLocked(current: StoredReview, save: ReviewSave): StoredReview {
        if (save.revision < current.revision) return current
        val deleted = current.history.mapTo(hashSetOf()) { it.media.uri }
        val incoming = save.choices.filterNot { it.media.uri in deleted }.associateByTo(linkedMapOf()) { it.media.uri }
        // A committed exit cannot be undone while its worker is running.
        current.queue.forEach { incoming[it.uri] = ReviewChoice(it, Decision.TRASH) }
        return current.copy(revision = save.revision, choices = incoming.values.toList())
    }

    private fun readLocked(): StoredReview = StoredReview(
        revision = preferences.getLong("revision", 0),
        choices = rows("choices").mapNotNull { row -> runCatching { ReviewChoice(mediaFromJson(row), Decision.valueOf(row.getString("decision"))) }.getOrNull() },
        queue = rows("deleteQueue").map(::mediaFromJson),
        history = rows("deletedHistory").map { DeletedMedia(mediaFromJson(it), it.getLong("deletedAt")) },
        running = preferences.getBoolean("deleteRunning", false),
        needsConsent = preferences.getBoolean("deleteConsent", false),
        total = preferences.getInt("deleteTotal", 0), error = preferences.getString("deleteError", null)
    )

    private fun rows(key: String): List<JSONObject> {
        val array = JSONArray(preferences.getString(key, "[]"))
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun persist(value: StoredReview) {
        fun array(rows: List<JSONObject>) = JSONArray().apply { rows.forEach(::put) }.toString()
        check(preferences.edit()
            .putLong("revision", value.revision)
            .putString("choices", array(value.choices.map { mediaToJson(it.media).put("decision", it.decision.name) }))
            .putString("deleteQueue", array(value.queue.map(::mediaToJson)))
            .putString("deletedHistory", array(value.history.map { mediaToJson(it.media).put("deletedAt", it.deletedAt) }))
            .putBoolean("deleteRunning", value.running).putBoolean("deleteConsent", value.needsConsent)
            .putInt("deleteTotal", value.total).putString("deleteError", value.error).commit()) {
            "Les choix n’ont pas pu être enregistrés. Vérifiez l’espace disponible."
        }
    }

    companion object {
        private val lock = Any()
        private fun mediaToJson(media: GalleryMedia) = JSONObject().apply {
            put("uri", media.uri); put("name", media.name); put("folderKey", media.folderKey)
            put("folderName", media.folderName); put("folderPath", media.folderPath)
            put("video", media.isVideo); put("size", media.size); put("date", media.dateAdded); put("duration", media.duration)
        }
        private fun mediaFromJson(row: JSONObject) = GalleryMedia(row.getString("uri"), row.getString("name"),
            row.getString("folderKey"), row.getString("folderName"), row.getString("folderPath"), row.getBoolean("video"),
            row.getLong("size"), row.getLong("date"), row.optLong("duration"))
    }
}
