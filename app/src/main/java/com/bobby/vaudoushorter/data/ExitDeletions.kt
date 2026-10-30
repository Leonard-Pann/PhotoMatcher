package com.bobby.vaudoushorter.data

import android.app.RecoverableSecurityException
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application lifetime + WorkManager: closing an Activity must not cancel deletion. */
class ExitDeletions private constructor(private val context: Context) {
    val store = ReviewStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(StoredReview())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            try {
                val saved = store.read()
                mutableState.value = saved
                ready.complete(Unit)
                if (saved.queue.isNotEmpty() && saved.running) {
                    if (Build.VERSION.SDK_INT == 29) enqueueWorker()
                    else publish(store.requireConsent())
                }
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }
    }

    suspend fun awaitReady() = ready.await()
    fun publish(value: StoredReview) { mutableState.value = value }

    suspend fun queueOnExit(save: ReviewSave): StoredReview = withContext(Dispatchers.IO) {
        awaitReady()
        val pending = store.queueExit(save, needsConsent = Build.VERSION.SDK_INT >= 30)
        publish(pending)
        if (pending.queue.isNotEmpty() && Build.VERSION.SDK_INT == 29) enqueueWorker()
        pending
    }

    fun launchBackgroundExit(save: ReviewSave?, onQueued: () -> Unit) {
        scope.launch {
            try {
                awaitReady()
                val snapshot = save ?: store.read().let { ReviewSave(it.revision, it.choices) }
                queueOnExit(snapshot)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(error = "Les choix sont conservés. La suppression sera réessayée à la prochaine sortie.") }
            finally { withContext(Dispatchers.Main) { onQueued() } }
        }
    }

    private fun enqueueWorker() {
        WorkManager.getInstance(context).enqueueUniqueWork("vaudou-exit-deletions", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ApplyDeletionsWorker>().build()).result.get()
    }

    companion object {
        // The private constructor only receives applicationContext, never an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: ExitDeletions? = null
        fun get(context: Context): ExitDeletions = instance ?: synchronized(this) {
            instance ?: ExitDeletions(context.applicationContext).also { instance = it }
        }
    }
}

class ApplyDeletionsWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val controller = ExitDeletions.get(applicationContext)
        controller.awaitReady()
        val store = controller.store
        if (Build.VERSION.SDK_INT >= 30) {
            controller.publish(store.requireConsent())
            return@withContext Result.success()
        }
        if (!GalleryAccess.read(applicationContext).canDelete) {
            controller.publish(store.finish("Autorisez la suppression des médias pour appliquer vos choix à la sortie."))
            return@withContext Result.success()
        }
        try {
            while (true) {
                ensureActive()
                val batch = store.read().queue.take(20)
                if (batch.isEmpty()) break
                val deleted = mutableListOf<GalleryMedia>()
                val failed = mutableListOf<GalleryMedia>()
                for (media in batch) {
                    try {
                        if (deleteMedia(applicationContext, media.uri)) deleted.add(media) else failed.add(media)
                    } catch (recoverable: RecoverableSecurityException) {
                        controller.publish(store.completeBatch(deleted, failed))
                        controller.publish(store.requireConsent())
                        return@withContext Result.success()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failed.add(media) }
                }
                controller.publish(store.completeBatch(deleted, failed))
            }
            controller.publish(store.finish())
            Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { Result.retry() }
    }
}

/** A missing row also covers a process dying after deletion but before journaling it. */
internal fun deleteMedia(context: Context, uriString: String): Boolean {
    val resolver = context.contentResolver
    val uri = Uri.parse(uriString)
    if (!mediaExists(context, uriString)) return true
    return resolver.delete(uri, null, null) > 0 || !mediaExists(context, uriString)
}

internal fun mediaExists(context: Context, uriString: String): Boolean = context.contentResolver.query(Uri.parse(uriString),
    arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: error("La galerie ne répond pas")
