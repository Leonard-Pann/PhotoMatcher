package com.bobby.vaudoushorter

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobby.vaudoushorter.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

data class DeleteConsent(val id: Long, val sender: IntentSender, val launched: Boolean = false)
data class GalleryUiState(
    val access: GalleryAccess = GalleryAccess(false, false),
    val loading: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val review: ReviewSnapshot = ReviewSnapshot(),
    val folder: String? = null,
    val filter: MediaFilter = MediaFilter.ALL,
    val deleting: Boolean = false,
    val exiting: Boolean = false,
    val deleteProgress: Int = 0,
    val deleteTotal: Int = 0,
    val deletedHistory: List<DeletedMedia> = emptyList(),
    val consent: DeleteConsent? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GalleryRepository(application)
    private val deletions = ExitDeletions.get(application)
    private val resolver = application.contentResolver
    private val mutableState = MutableStateFlow(GalleryUiState())
    val state = mutableState.asStateFlow()
    private var session = ReviewSession()
    private var catalogue = emptyList<GalleryMedia>()
    private var revision = 0L
    private var stagedUri: String? = null
    private val ready = CompletableDeferred<Unit>()
    private var foreground = false
    private var refreshJob: Job? = null
    private var interactiveJob: Deferred<Unit>? = null
    private var consentResult: CompletableDeferred<Boolean>? = null
    private var requestId = 0L
    private val saves = Channel<ReviewSave>(Channel.CONFLATED)
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        saveScope.launch {
            try {
                for (save in saves) {
                    try { repository.saveChoices(save) }
                    catch (error: Exception) { notice(error.message ?: "Enregistrement impossible") }
                }
            } finally { saveScope.cancel() }
        }
        viewModelScope.launch {
            try {
                deletions.awaitReady()
                reloadLedger()
                ready.complete(Unit)
                deletions.state.collectLatest { status ->
                    mutableState.update { it.copy(deleting = if (status.running || status.needsConsent) true else it.deleting, deletedHistory = status.history,
                        deleteTotal = status.total, deleteProgress = status.total - status.queue.size) }
                    if (!status.running) {
                        reloadLedger()
                        if (status.error != null) notice(status.error)
                        if (foreground) refresh()
                        if (status.needsConsent && foreground && !state.value.exiting) ensureInteractive()
                    } else publish()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "Impossible de relire les choix enregistrés.") }
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("Session inaccessible"))
            }
        }
    }

    private suspend fun reloadLedger() {
        val saved = repository.loadReview()
        revision = saved.revision
        val deletedUris = saved.history.mapTo(hashSetOf()) { it.media.uri }
        session = ReviewSession(saved.choices).apply {
            setMedia(catalogue.filterNot { it.uri in deletedUris })
        }
        mutableState.update { it.copy(deleting = saved.running || saved.needsConsent, deletedHistory = saved.history) }
        publish()
    }

    fun onForeground() {
        foreground = true
        viewModelScope.launch {
            ready.await()
            refresh()
            if (deletions.state.value.needsConsent) ensureInteractive()
        }
    }

    fun onBackgroundExit() {
        foreground = false
        mutableState.update { it.copy(exiting = true) }
        val save = if (ready.isCompleted && !ready.isCancelled) snapshot() else null
        deletions.launchBackgroundExit(save) { mutableState.update { it.copy(exiting = false) } }
    }

    fun onBackground() { foreground = false }

    /** Return only after the exit choices have been durably queued (or system consent handled). */
    suspend fun applyAndExit() {
        mutableState.update { it.copy(exiting = true) }
        try {
            ready.await()
            deletions.queueOnExit(snapshot())
            if (Build.VERSION.SDK_INT >= 30 && deletions.state.value.queue.isNotEmpty()) ensureInteractive().await()
        } finally { mutableState.update { it.copy(exiting = false) } }
    }

    fun refresh() {
        if (state.value.deleting) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            ready.await()
            val access = GalleryAccess.read(getApplication())
            mutableState.update { it.copy(access = access, loading = true, error = null) }
            try {
                catalogue = if (access.available) repository.loadMedia(access) else emptyList()
                ensureActive()
                session.setMedia(catalogue)
                publish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) {
                catalogue = emptyList()
                session.setMedia(emptyList())
                mutableState.update { it.copy(access = GalleryAccess.read(getApplication()),
                    error = "L’accès à la galerie a changé. Autorisez les médias puis réessayez.") }
                publish()
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "Impossible de charger la galerie. Vous pouvez réessayer.") }
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }

    /** Persist at gesture acceptance, before the exit animation, so Home cannot lose this choice. */
    fun stageChoice(uri: String, decision: Decision) {
        if (locked() || state.value.loading || state.value.review.current?.uri != uri) return
        if (session.decide(uri, decision)) {
            stagedUri = uri
            changed(updateScreen = false)
        }
    }

    fun choose(uri: String, decision: Decision) {
        if (stagedUri == uri) { stagedUri = null; publish(); return }
        if (locked() || state.value.loading || state.value.review.current?.uri != uri) return
        if (session.decide(uri, decision)) changed()
    }

    fun undo() {
        if (locked()) return
        val media = session.undo() ?: return
        stagedUri = null
        mutableState.update {
            it.copy(folder = if (it.folder != null && it.folder != media.folderKey) null else it.folder,
                filter = if ((it.filter == MediaFilter.PHOTOS && media.isVideo) ||
                    (it.filter == MediaFilter.VIDEOS && !media.isVideo)) MediaFilter.ALL else it.filter)
        }
        changed()
        notice("Dernier choix annulé")
    }

    fun selectFolder(folder: String?) { if (!locked()) { mutableState.update { it.copy(folder = folder) }; publish() } }
    fun selectFilter(filter: MediaFilter) { if (!locked()) { mutableState.update { it.copy(filter = filter) }; publish() } }
    fun restore(uri: String) { if (!locked()) { session.restore(uri); changed() } }
    fun restoreAll() { if (!locked()) { session.restoreAll(); changed() } }
    fun notice(message: String) { mutableState.update { it.copy(notice = message) } }
    fun clearNotice() { mutableState.update { it.copy(notice = null) } }
    private fun locked() = state.value.deleting || state.value.exiting

    fun markConsentLaunched(id: Long): Boolean {
        val request = state.value.consent ?: return false
        if (request.id != id || request.launched) return false
        mutableState.update { it.copy(consent = request.copy(launched = true)) }
        return true
    }

    fun onConsentResult(allowed: Boolean) {
        mutableState.update { it.copy(consent = null) }
        consentResult?.complete(allowed)
    }

    private suspend fun requestConsent(sender: IntentSender): Boolean {
        val result = CompletableDeferred<Boolean>()
        consentResult = result
        mutableState.update { it.copy(consent = DeleteConsent(++requestId, sender)) }
        return try { result.await() }
        finally { consentResult = null; mutableState.update { it.copy(consent = null) } }
    }

    private fun ensureInteractive(): Deferred<Unit> {
        interactiveJob?.takeIf { it.isActive }?.let { return it }
        return viewModelScope.async {
            refreshJob?.cancel()
            var cancelled = false
            var error: String? = null
            try {
                val pending = withContext(Dispatchers.IO) { deletions.store.startInteractive() }
                deletions.publish(pending)
                if (Build.VERSION.SDK_INT >= 30) {
                    for (batch in pending.queue.chunked(200)) {
                        val gone = withContext(Dispatchers.IO) {
                            val access = GalleryAccess.read(getApplication())
                            batch.filter { media ->
                                (if (media.isVideo) access.videos else access.photos) && !mediaExists(getApplication(), media.uri)
                            }
                        }
                        // Recover a process dying after system deletion but before its result was saved.
                        if (gone.isNotEmpty()) deletions.publish(withContext(Dispatchers.IO) { deletions.store.completeBatch(gone) })
                        val live = batch.filterNot { media -> gone.any { it.uri == media.uri } }
                        if (live.isEmpty()) continue
                        val request = withContext(Dispatchers.IO) {
                            MediaStore.createDeleteRequest(resolver, live.map { Uri.parse(it.uri) })
                        }
                        if (!requestConsent(request.intentSender)) { error = "Suppression refusée. Les médias restent à supprimer."; break }
                        deletions.publish(withContext(Dispatchers.IO) { deletions.store.completeBatch(live) })
                    }
                } else {
                    for (media in pending.queue) {
                        val removed = try { withContext(Dispatchers.IO) { deleteMedia(getApplication(), media.uri) } }
                        catch (recoverable: RecoverableSecurityException) {
                            if (!requestConsent(recoverable.userAction.actionIntent.intentSender)) { error = "Suppression refusée."; break }
                            withContext(Dispatchers.IO) { deleteMedia(getApplication(), media.uri) }
                        }
                        deletions.publish(withContext(Dispatchers.IO) {
                            if (removed) deletions.store.completeBatch(listOf(media)) else deletions.store.completeBatch(emptyList(), listOf(media))
                        })
                    }
                }
            } catch (cancel: CancellationException) {
                cancelled = true
                withContext(NonCancellable + Dispatchers.IO) { deletions.publish(deletions.store.requireConsent()) }
                throw cancel
            } catch (_: Exception) { error = "Suppression interrompue. Les médias restants sont conservés dans la liste." }
            finally {
                if (!cancelled) withContext(NonCancellable + Dispatchers.IO) { deletions.publish(deletions.store.finish(error)) }
            }
        }.also { interactiveJob = it }
    }

    private fun snapshot() = ReviewSave(revision, session.choices())
    private fun changed(updateScreen: Boolean = true) {
        revision++
        saves.trySend(snapshot())
        if (updateScreen) publish()
    }
    private fun publish() {
        mutableState.update {
            val review = session.snapshot(it.folder, it.filter)
            it.copy(review = if (it.deleting) review.copy(trash = deletions.state.value.queue) else review)
        }
    }
    override fun onCleared() { saves.close(); super.onCleared() }
}
