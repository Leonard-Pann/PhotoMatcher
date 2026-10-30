package com.bobby.vaudoushorter

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.bobby.vaudoushorter.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GalleryRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver

    private fun grantReadAccess() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
            else if (Build.VERSION.SDK_INT == 29) listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE")
            else listOf("READ_EXTERNAL_STORAGE")
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.$permission").close()
        }
    }

    private fun insertPhoto(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Vaudou-test-${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VaudouValidation/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally { bitmap.recycle() }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Test fun queryReturnsTheInsertedPhotoAndItsFolderOnAndroid10() = runBlocking {
        grantReadAccess()
        val uri = insertPhoto()
        try {
            val media = GalleryRepository(context).loadMedia(GalleryAccess.read(context))
            val inserted = media.first { it.uri == uri.toString() }
            assertEquals("VaudouValidation", inserted.folderName)
            assertFalse(inserted.isVideo)
            assertTrue(inserted.size > 0)
            assertTrue(inserted.folderPath.contains("Pictures/VaudouValidation"))
        } finally { resolver.delete(uri, null, null) }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun viewModelStagesUndoAndActuallyDeletesOnlyTheTestPhoto() = runBlocking {
        grantReadAccess()
        val uri = insertPhoto()
        @Suppress("DEPRECATION")
        val path = resolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)!!.use {
            check(it.moveToFirst())
            it.getString(0)
        }
        val application = context.applicationContext as Application
        val store = ViewModelStore()
        lateinit var model: GalleryViewModel
        withContext(Dispatchers.Main) {
            model = GalleryViewModel(application)
            store.put("test", model)
            model.refresh()
        }
        try {
            withTimeout(10_000) { model.state.first { !it.loading && it.error == null } }
            val item = GalleryRepository(context).loadMedia(GalleryAccess.read(context)).first { it.uri == uri.toString() }
            withContext(Dispatchers.Main) {
                model.selectFolder(item.folderKey)
                // This folder contains only fixtures created by this test.
                assertEquals(uri.toString(), model.state.value.review.current!!.uri)
                model.choose(uri.toString(), Decision.TRASH)
                assertTrue(model.state.value.review.trash.any { it.uri == uri.toString() })
            }
            resolver.openInputStream(uri)!!.close() // Staging must never delete the file.
            withContext(Dispatchers.Main) {
                model.undo()
                assertEquals(uri.toString(), model.state.value.review.current!!.uri)
                assertFalse(model.state.value.review.trash.any { it.uri == uri.toString() })
                model.choose(uri.toString(), Decision.TRASH)
                model.applyAndExit()
            }
            withTimeout(20_000) { model.state.first { !it.deleting && it.deletedHistory.any { entry -> entry.media.uri == uri.toString() } } }
            val exists = resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use { it.moveToFirst() }
            assertFalse(exists)
            assertFalse("The physical file must be deleted, not just its MediaStore row", File(path).exists())
            assertFalse(model.state.value.review.trash.any { it.uri == uri.toString() })
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            // Android 10 may reject a second delete once the URI no longer exists.
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun homeCommitsPhotoAndVideoIncludingTheLastChoiceBeforeAnimationEnds() = runBlocking {
        grantReadAccess()
        val photo = insertPhoto()
        val clip = createVideoFixture(context.cacheDir)
        val video = requireNotNull(resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Vaudou-exit-${System.nanoTime()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VaudouExitValidation/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }))
        resolver.openOutputStream(video)!!.use { output -> clip.inputStream().use { it.copyTo(output) } }
        resolver.update(video, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        clip.delete()
        @Suppress("DEPRECATION")
        fun path(uri: Uri) = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)!!.use {
            check(it.moveToFirst()); it.getString(0)
        }
        val photoPath = path(photo)
        val videoPath = path(video)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: GalleryViewModel
        scenario.onActivity { model = ViewModelProvider(it)[GalleryViewModel::class.java] }
        try {
            withTimeout(15_000) { model.state.first { !it.loading && it.error == null } }
            val media = GalleryRepository(context).loadMedia(GalleryAccess.read(context))
            val photoItem = media.first { it.uri == photo.toString() }
            val videoItem = media.first { it.uri == video.toString() }
            scenario.onActivity {
                model.selectFolder(photoItem.folderKey)
                model.choose(photoItem.uri, Decision.TRASH)
                model.selectFolder(videoItem.folderKey)
                model.stageChoice(videoItem.uri, Decision.TRASH)
                assertTrue(File(photoPath).exists())
                assertTrue(File(videoPath).exists())
            }
            // This is a real Home press: onUserLeaveHint + onStop must trigger the worker.
            val command = instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
            withTimeout(25_000) {
                ExitDeletions.get(context).state.first { status ->
                    !status.running && status.history.any { it.media.uri == photo.toString() } &&
                        status.history.any { it.media.uri == video.toString() }
                }
            }
            assertFalse(File(photoPath).exists())
            assertFalse(File(videoPath).exists())
            assertTrue(ReviewStore(context).read().choices.none { it.media.uri == photo.toString() || it.media.uri == video.toString() })
        } finally {
            scenario.close()
            runCatching { resolver.delete(photo, null, null) }
            runCatching { resolver.delete(video, null, null) }
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun backQueuesTheLastChoiceBeforeTheActivityIsDestroyed() = runBlocking {
        grantReadAccess()
        val photo = insertPhoto()
        @Suppress("DEPRECATION")
        val path = resolver.query(photo, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)!!.use {
            check(it.moveToFirst()); it.getString(0)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: GalleryViewModel
        scenario.onActivity { model = ViewModelProvider(it)[GalleryViewModel::class.java] }
        try {
            withTimeout(15_000) { model.state.first { !it.loading && it.error == null } }
            val item = GalleryRepository(context).loadMedia(GalleryAccess.read(context)).first { it.uri == photo.toString() }
            scenario.onActivity {
                model.selectFolder(item.folderKey)
                model.stageChoice(item.uri, Decision.TRASH)
                assertTrue(File(path).exists())
                it.onBackPressedDispatcher.onBackPressed()
            }
            withTimeout(25_000) {
                ExitDeletions.get(context).state.first { status -> !status.running && status.history.any { it.media.uri == photo.toString() } }
                while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED) delay(20)
            }
            assertFalse(File(path).exists())
        } finally {
            scenario.close()
            runCatching { resolver.delete(photo, null, null) }
        }
    }
}
